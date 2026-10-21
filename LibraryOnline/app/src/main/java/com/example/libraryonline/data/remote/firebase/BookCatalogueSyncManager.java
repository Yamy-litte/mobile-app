package com.example.libraryonline.data.remote.firebase;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.dao.BookCopyDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.data.model.BookCopy;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the local SQLite book catalogue in sync with the authoritative Firestore collection.
 * Cloud changes are applied incrementally so an offline/empty first snapshot cannot wipe local data.
 * Local-only records and repository create/update operations are uploaded under admin-only Firestore rules.
 */
public final class BookCatalogueSyncManager implements AutoCloseable {
    public interface SyncListener {
        void onSyncCompleted(int changedRows);
        void onSyncFailed(String message);
        default void onCatalogueSnapshot(boolean fromCache, int documentCount) {}
    }

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ListenerRegistration registration;
    private ListenerRegistration copiesRegistration;
    private ListenerRegistration categoriesRegistration;
    private volatile boolean closed;

    public BookCatalogueSyncManager(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
        firestore = FirebaseFirestore.getInstance();
    }

    public synchronized void start(SyncListener listener) {
        if (closed) throw new IllegalStateException("Sync manager is closed");
        if (registration != null) return;

        categoriesRegistration = firestore.collection("Categories").addSnapshotListener((snapshot, error) -> {
            if (closed) return;
            if (error != null) {
                if (listener != null) listener.onSyncFailed("Không thể tải thể loại: " + error.getLocalizedMessage());
                return;
            }
            if (snapshot != null) executeIfOpen(() -> {
                if (closed) return;
                try {
                    com.example.libraryonline.data.local.dao.CategoryDao dao =
                            new com.example.libraryonline.data.local.dao.CategoryDao(databaseHelper.getWritableDatabase());
                    for (DocumentSnapshot category : snapshot.getDocuments()) {
                        String name = category.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            dao.upsert(category.getId(), name, Boolean.TRUE.equals(category.getBoolean("isActive")));
                        }
                    }
                } catch (Exception failure) {
                    if (listener != null) listener.onSyncFailed("Không thể lưu Categories vào SQLite: "
                            + failure.getLocalizedMessage());
                    return;
                }
                // Re-resolve display labels if the titles snapshot arrived before its parents.
                firestore.collection("BookTitles").get()
                        .addOnSuccessListener(books -> {
                            if (!closed) executeIfOpen(() -> applySnapshot(books, listener));
                        }).addOnFailureListener(failure -> {
                            if (listener != null) listener.onSyncFailed(failure.getLocalizedMessage());
                        });
            });
        });

        registration = firestore.collection("BookTitles").addSnapshotListener((snapshot, error) -> {
            if (closed) return;
            if (error != null) {
                if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                        ? "Không thể đồng bộ danh mục sách." : error.getLocalizedMessage());
                return;
            }
            if (snapshot != null) executeIfOpen(() -> applySnapshot(snapshot, listener));
        });
        copiesRegistration = firestore.collection("BookCopies").addSnapshotListener((snapshot, error) -> {
            if (closed) return;
            if (error != null) {
                if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                        ? "Không thể đồng bộ cuốn sách vật lý. Kiểm tra quyền Firestore BookCopies."
                        : error.getLocalizedMessage());
                return;
            }
            if (snapshot != null) executeIfOpen(() -> applyCopiesSnapshot(snapshot, listener));
        });
    }

    /** Push local-only catalogue rows; existing cloud documents remain authoritative on initial sync. */
    public void syncLocalToCloud(SyncListener listener) {
        if (closed) throw new IllegalStateException("Sync manager is closed");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (listener != null) listener.onSyncFailed("Bạn cần đăng nhập trước khi đồng bộ.");
            return;
        }
        firestore.collection("BookTitles").get()
                .addOnSuccessListener(cloudSnapshot -> executeIfOpen(() -> {
                    if (closed) return;
                    syncCategoriesToCloud(cloudSnapshot, listener, () ->
                            migrateLegacyBookTitles(cloudSnapshot.getDocuments(), 0, listener, () ->
                                    executeIfOpen(() -> uploadMissingLocalBooks(cloudSnapshot, listener))));
                }))
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                            ? "Không thể đọc danh mục từ Firestore." : error.getLocalizedMessage());
                });
    }

    /** Explicit admin sync: convert legacy category names without overwriting concurrent edits. */
    private void migrateLegacyBookTitles(List<DocumentSnapshot> documents, int start,
                                         SyncListener listener, Runnable onComplete) {
        if (closed) return;
        if (start >= documents.size()) {
            onComplete.run();
            return;
        }
        // getAfter parent validation has a per-request access limit; keep chunks small.
        final int nextIndex = Math.min(start + 10, documents.size());
        firestore.runTransaction(transaction -> {
            List<DocumentSnapshot> current = new ArrayList<>();
            for (int i = start; i < nextIndex; i++) {
                current.add(transaction.get(documents.get(i).getReference()));
            }
            for (DocumentSnapshot document : current) {
                if (!document.exists()) continue;
                String oldName = document.getString("category");
                String currentId = document.getString("categoryId");
                if ((currentId == null || currentId.trim().isEmpty()) || document.contains("category")) {
                    if ((currentId == null || currentId.trim().isEmpty())
                            && (oldName == null || oldName.trim().isEmpty())) {
                        throw new IllegalStateException("Đầu sách thiếu thể loại: " + document.getId());
                    }
                    Map<String, Object> changes = new HashMap<>();
                    changes.put("categoryId", currentId == null || currentId.trim().isEmpty()
                            ? categoryKey(oldName) : currentId.trim());
                    if (document.contains("category")) {
                        changes.put("category", com.google.firebase.firestore.FieldValue.delete());
                    }
                    transaction.update(document.getReference(), changes);
                }
            }
            return null;
        }).addOnSuccessListener(unused -> {
            if (nextIndex >= documents.size()) onComplete.run();
            else migrateLegacyBookTitles(documents, nextIndex, listener, onComplete);
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onSyncFailed(
                    "Không thể chuyển BookTitles.category sang categoryId: " + error.getLocalizedMessage());
        });
    }

    private void uploadMissingLocalBooks(QuerySnapshot cloudSnapshot, SyncListener listener) {
        if (closed) return;
        List<BookTitle> localBooks = new BookTitleDao(
                databaseHelper.getReadableDatabase()).getAll();
        Map<String, Boolean> cloudIds = new HashMap<>();
        for (DocumentSnapshot document : cloudSnapshot.getDocuments()) {
            cloudIds.put(document.getId(), true);
        }
        List<BookTitle> missing = new ArrayList<>();
        for (BookTitle book : localBooks) {
            if (book.bookTitleId != null && !cloudIds.containsKey(book.bookTitleId)) missing.add(book);
        }
        if (missing.isEmpty()) {
            syncCopiesLocalToCloud(listener);
            return;
        }
        if (missing.size() > 450) {
            if (listener != null) listener.onSyncFailed("Có hơn 450 đầu sách mới; hãy chia thành nhiều đợt.");
            return;
        }
        Map<String, Map<String, Object>> newBooks = new java.util.LinkedHashMap<>();
        for (BookTitle book : missing) newBooks.put(book.bookTitleId, toCloudMap(book));
        createMissingInventory("BookTitles", newBooks).addOnSuccessListener(unused -> syncCopiesLocalToCloud(listener))
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                            ? "Không thể đẩy dữ liệu SQLite lên Firestore." : error.getLocalizedMessage());
                });
    }
    private void syncCopiesLocalToCloud(SyncListener listener) {
        firestore.collection("BookCopies").get()
                .addOnSuccessListener(cloudSnapshot -> executeIfOpen(() -> {
                    if (closed) return;
                    List<BookCopy> localCopies = new BookCopyDao(
                            databaseHelper.getReadableDatabase()).getAll();
                    Map<String, Boolean> cloudIds = new HashMap<>();
                    for (DocumentSnapshot document : cloudSnapshot.getDocuments()) {
                        cloudIds.put(document.getId(), true);
                    }
                    List<BookCopy> missing = new ArrayList<>();
                    for (BookCopy copy : localCopies) {
                        if (copy.copyId != null && !cloudIds.containsKey(copy.copyId)) missing.add(copy);
                    }
                    if (missing.size() > 450) {
                        if (listener != null) listener.onSyncFailed("Có hơn 450 cuốn sách mới; hãy chia thành nhiều đợt.");
                        return;
                    }
                    if (missing.isEmpty()) {
                        if (listener != null) listener.onSyncCompleted(0);
                        return;
                    }
                    Map<String, Map<String, Object>> newCopies = new java.util.LinkedHashMap<>();
                    for (BookCopy copy : missing) newCopies.put(copy.copyId, toCloudMap(copy));
                    createMissingInventory("BookCopies", newCopies).addOnSuccessListener(unused -> {
                        if (listener != null) listener.onSyncCompleted(missing.size());
                    }).addOnFailureListener(error -> {
                        if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                                ? "Không thể đẩy các cuốn sách từ SQLite lên Firestore." : error.getLocalizedMessage());
                    });
                }))
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                            ? "Không thể đọc BookCopies từ Firestore." : error.getLocalizedMessage());
                });
    }


    /** Small independent transactions fit Rules access limits and never overwrite a concurrent insert. */
    private Task<Void> createMissingInventory(String collection, Map<String, Map<String, Object>> data) {
        List<String> ids = new ArrayList<>(data.keySet());
        List<Task<?>> writes = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 5) {
            final List<String> chunk = ids.subList(start, Math.min(start + 5, ids.size()));
            writes.add(firestore.runTransaction(transaction -> {
                List<String> missing = new ArrayList<>();
                for (String id : chunk) {
                    if (!transaction.get(firestore.collection(collection).document(id)).exists()) missing.add(id);
                }
                for (String id : missing) {
                    transaction.set(firestore.collection(collection).document(id), data.get(id));
                }
                return null;
            }));
        }
        return com.google.android.gms.tasks.Tasks.whenAll(writes);
    }

    /** Create missing parents first; never rename or reactivate existing cloud categories. */
    private void syncCategoriesToCloud(QuerySnapshot books, SyncListener listener, Runnable onComplete) {
        if (closed) return;
        List<BookTitle> localBooks = new BookTitleDao(databaseHelper.getReadableDatabase()).getAll();
        Map<String, String> categories = new java.util.LinkedHashMap<>();
        for (BookTitle book : localBooks) {
            if (book != null && book.category != null && !book.category.trim().isEmpty()) {
                String category = book.category.trim();
                String categoryId = book.categoryId == null || book.categoryId.trim().isEmpty()
                        ? categoryKey(category) : book.categoryId.trim();
                categories.put(categoryId, category);
            }
        }
        for (DocumentSnapshot book : books.getDocuments()) {
            String id = book.getString("categoryId");
            String name = book.getString("category");
            if ((id == null || id.trim().isEmpty()) && (name == null || name.trim().isEmpty())) {
                if (listener != null) listener.onSyncFailed("Đầu sách " + book.getId()
                        + " thiếu thể loại; cần kiểm tra trước khi đồng bộ.");
                return;
            }
            if (id == null || id.trim().isEmpty()) id = categoryKey(name);
            if (!categories.containsKey(id) || (name != null && !name.trim().isEmpty())) {
                categories.put(id, name);
            }
        }
        if (categories.size() > 450) {
            if (listener != null) listener.onSyncFailed("Có hơn 450 thể loại; hãy chia thành nhiều đợt.");
            return;
        }
        firestore.runTransaction(transaction -> {
          Map<String, String> missing = new java.util.LinkedHashMap<>();
          for (Map.Entry<String, String> entry : categories.entrySet()) {
            if (!transaction.get(firestore.collection("Categories").document(entry.getKey())).exists()) {
                String name = entry.getValue();
                if (name == null || name.trim().isEmpty() || name.equals(entry.getKey())) {
                    throw new IllegalStateException("Thiếu tên thể loại cho " + entry.getKey());
                }
                missing.put(entry.getKey(), name);
            }
          }
          for (Map.Entry<String, String> entry : missing.entrySet()) {
            Map<String, Object> data = new HashMap<>();
            data.put("categoryId", entry.getKey());
            data.put("name", entry.getValue());
            data.put("isActive", true);
            data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            transaction.set(firestore.collection("Categories").document(entry.getKey()), data);
          }
          return null;
        }).addOnSuccessListener(unused -> {
            executeIfOpen(onComplete);
        }).addOnFailureListener(error -> {
            if (listener != null) listener.onSyncFailed("Sách đã đồng bộ nhưng tải thể loại lên Categories thất bại: "
                    + (error.getLocalizedMessage() == null ? "Không rõ nguyên nhân" : error.getLocalizedMessage()));
        });
    }


    private static String categoryKey(String name) {
        return CloudLibraryRepository.categoryIdForName(name);
    }

    public static Task<Void> uploadCopyToCloud(BookCopy copy) {
        return new CloudLibraryRepository().saveBookCopy(copy);
    }

    private static Map<String, Object> toCloudMap(BookCopy copy) {
        return CloudLibraryRepository.toCloudMap(copy);
    }

    /** Upload one local create/update. Firestore rules enforce the active-admin requirement. */
    public static Task<Void> uploadBookToCloud(BookTitle book) {
        return new CloudLibraryRepository().saveBookTitle(book);
    }

    private static Map<String, Object> toCloudMap(BookTitle book) {
        return CloudLibraryRepository.toCloudMap(book);
    }

    private void applySnapshot(QuerySnapshot snapshot, SyncListener listener) {
        if (closed) return;
        int changed = 0;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            BookTitleDao dao = new BookTitleDao(db);
            // Upsert every document in the complete snapshot into SQLite.
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                dao.upsertFromCloud(fromDocument(document));
                changed++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            if (listener != null) {
                String message = e.getLocalizedMessage();
                listener.onSyncFailed(message == null ? "Không thể lưu danh mục vào SQLite." : message);
            }
            return;
        } finally {
            db.endTransaction();
        }
        if (listener != null) {
            listener.onCatalogueSnapshot(snapshot.getMetadata().isFromCache(), snapshot.size());
            listener.onSyncCompleted(changed);
        }
        // Ensure parent BookTitles are persisted before enforcing the BookCopies foreign key.
        firestore.collection("BookCopies").get()
                .addOnSuccessListener(copies -> executeIfOpen(() -> applyCopiesSnapshot(copies, listener)));
    }

    private void applyCopiesSnapshot(QuerySnapshot snapshot, SyncListener listener) {
        if (closed) return;
        int changed = 0;
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            BookCopyDao dao = new BookCopyDao(db);
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                BookCopy copy = new BookCopy();
                copy.copyId = document.getId();
                copy.bookTitleId = string(document, "bookTitleId");
                copy.barcode = string(document, "barcode");
                copy.shelfLocation = string(document, "shelfLocation");
                copy.copyStatus = string(document, "copyStatus");
                copy.priorityUserId = document.getString("priorityUserId");
                copy.isHidden = Boolean.TRUE.equals(document.getBoolean("isHidden"));
                copy.createdAt = timestampString(document.get("createdAt"));
                copy.updatedAt = timestampString(document.get("updatedAt"));
                dao.upsertFromCloud(copy);
                changed++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            if (listener != null) listener.onSyncFailed(e.getLocalizedMessage() == null
                    ? "Không thể lưu BookCopies vào SQLite." : e.getLocalizedMessage());
            return;
        } finally {
            db.endTransaction();
        }
        if (listener != null) listener.onSyncCompleted(changed);
    }

    private BookTitle fromDocument(DocumentSnapshot doc) {
        BookTitle book = new BookTitle();
        book.bookTitleId = doc.getId();
        book.title = string(doc, "title");
        book.author = string(doc, "author");
        book.isbn = string(doc, "isbn");
        String legacyCategoryName = string(doc, "category");
        book.categoryId = string(doc, "categoryId");
        if (book.categoryId.trim().isEmpty()) {
            book.categoryId = com.example.libraryonline.data.local.dao.CategoryDao.idForName(
                    legacyCategoryName.trim().isEmpty() ? "Khác" : legacyCategoryName);
        }
        String localCategoryName = new com.example.libraryonline.data.local.dao.CategoryDao(
                databaseHelper.getReadableDatabase()).getName(book.categoryId);
        book.category = !legacyCategoryName.trim().isEmpty() ? legacyCategoryName
                : (localCategoryName == null || localCategoryName.trim().isEmpty()
                    ? book.categoryId : localCategoryName);
        book.publisher = string(doc, "publisher");
        book.description = string(doc, "description");
        book.coverUrl = string(doc, "coverUrl");
        Long year = doc.getLong("publishYear");
        book.publishYear = year == null ? null : year.intValue();
        Long total = doc.getLong("totalCopies");
        Long available = doc.getLong("availableCopies");
        book.totalCopies = total == null ? 0 : total.intValue();
        book.availableCopies = available == null ? 0 : available.intValue();
        book.isHidden = Boolean.TRUE.equals(doc.getBoolean("isHidden"));
        book.createdAt = timestampString(doc.get("createdAt"));
        book.updatedAt = timestampString(doc.get("updatedAt"));
        return book;
    }

    private static String string(DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "" : value;
    }

    private static String timestampString(Object value) {
        if (value instanceof Timestamp) {
            Date date = ((Timestamp) value).toDate();
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(date);
        }
        return value == null ? "" : String.valueOf(value);
    }

    /** Firebase callbacks may arrive after their Activity has stopped. */
    private synchronized void executeIfOpen(Runnable work) {
        if(closed)return;
        executor.execute(() -> {if(!closed)work.run();});
    }

    @Override
    public synchronized void close() {
        if(closed)return;
        closed = true;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (copiesRegistration != null) {
            copiesRegistration.remove();
            copiesRegistration = null;
        }
        if (categoriesRegistration != null) {
            categoriesRegistration.remove();
            categoriesRegistration = null;
        }
        // Serialize resource closure after running SQLite work; do not close its DB underneath it.
        executor.execute(databaseHelper::close);
        executor.shutdown();
    }
}
