package com.example.libraryonline.remote.firebase;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.data.local.dao.BookCopyDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.data.model.BookCopy;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
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
    }

    private final DatabaseHelper databaseHelper;
    private final FirebaseFirestore firestore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ListenerRegistration registration;
    private ListenerRegistration copiesRegistration;
    private volatile boolean closed;

    public BookCatalogueSyncManager(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
        firestore = FirebaseFirestore.getInstance();
    }

    public synchronized void start(SyncListener listener) {
        if (closed) throw new IllegalStateException("Sync manager is closed");
        if (registration != null) return;

        registration = firestore.collection("BookTitles").addSnapshotListener((snapshot, error) -> {
            if (closed) return;
            if (error != null) {
                if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                        ? "Không thể đồng bộ danh mục sách." : error.getLocalizedMessage());
                return;
            }
            if (snapshot != null) executor.execute(() -> applySnapshot(snapshot, listener));
        });
        copiesRegistration = firestore.collection("BookCopies").addSnapshotListener((snapshot, error) -> {
            if (closed) return;
            if (error != null) {
                if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                        ? "Không thể đồng bộ cuốn sách vật lý. Kiểm tra quyền Firestore BookCopies."
                        : error.getLocalizedMessage());
                return;
            }
            if (snapshot != null) executor.execute(() -> applyCopiesSnapshot(snapshot, listener));
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
                .addOnSuccessListener(cloudSnapshot -> executor.execute(() -> {
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
                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    for (BookTitle book : missing) {
                        batch.set(firestore.collection("BookTitles").document(book.bookTitleId), toCloudMap(book));
                    }
                    batch.commit().addOnSuccessListener(unused -> syncCopiesLocalToCloud(listener))
                            .addOnFailureListener(error -> {
                                if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                                        ? "Không thể đẩy dữ liệu SQLite lên Firestore." : error.getLocalizedMessage());
                            });
                }))
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onSyncFailed(error.getLocalizedMessage() == null
                            ? "Không thể đọc danh mục từ Firestore." : error.getLocalizedMessage());
                });
    }

    private void syncCopiesLocalToCloud(SyncListener listener) {
        firestore.collection("BookCopies").get()
                .addOnSuccessListener(cloudSnapshot -> executor.execute(() -> {
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
                        syncCategoriesToCloud(listener, 0);
                        return;
                    }
                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    for (BookCopy copy : missing) {
                        batch.set(firestore.collection("BookCopies").document(copy.copyId), toCloudMap(copy));
                    }
                    batch.commit().addOnSuccessListener(unused -> {
                        syncCategoriesToCloud(listener, missing.size());
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


    /** Upload distinct catalogue categories to Firestore. Caller must be an active admin. */
    private void syncCategoriesToCloud(SyncListener listener, int changedRows) {
        executor.execute(() -> {
            if (closed) return;
            List<BookTitle> localBooks = new BookTitleDao(databaseHelper.getReadableDatabase()).getAll();
            Map<String, String> categories = new java.util.LinkedHashMap<>();
            String[] defaults = {"Văn học", "Khoa học", "Lịch sử", "Tâm lý", "Kinh tế", "Công nghệ", "Khác"};
            for (String category : defaults) categories.put(categoryKey(category), category);
            for (BookTitle book : localBooks) {
                if (book != null && book.category != null && !book.category.trim().isEmpty()) {
                    String category = book.category.trim();
                    categories.put(categoryKey(category), category);
                }
            }
            if (categories.isEmpty()) {
                if (listener != null) listener.onSyncCompleted(changedRows);
                return;
            }
            com.google.firebase.firestore.WriteBatch batch = firestore.batch();
            for (Map.Entry<String, String> entry : categories.entrySet()) {
                Map<String, Object> data = new HashMap<>();
                data.put("categoryId", entry.getKey());
                data.put("name", entry.getValue());
                data.put("isActive", true);
                data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                batch.set(firestore.collection("Categories").document(entry.getKey()), data, SetOptions.merge());
            }
            batch.commit().addOnSuccessListener(unused -> {
                if (listener != null) listener.onSyncCompleted(changedRows + categories.size());
            }).addOnFailureListener(error -> {
                if (listener != null) listener.onSyncFailed("Sách đã đồng bộ nhưng tải thể loại lên Categories thất bại: "
                        + (error.getLocalizedMessage() == null ? "Không rõ nguyên nhân" : error.getLocalizedMessage()));
            });
        });
    }

    private static String categoryKey(String name) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "category" : normalized;
    }

    public static Task<Void> uploadCopyToCloud(BookCopy copy) {
        if (copy == null || copy.copyId == null || copy.copyId.trim().isEmpty()) {
            throw new IllegalArgumentException("copyId is required");
        }
        return FirebaseFirestore.getInstance().collection("BookCopies")
                .document(copy.copyId).set(toCloudMap(copy), SetOptions.merge());
    }

    private static Map<String, Object> toCloudMap(BookCopy copy) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookTitleId", copy.bookTitleId);
        data.put("barcode", copy.barcode == null ? "" : copy.barcode);
        data.put("shelfLocation", copy.shelfLocation == null ? "" : copy.shelfLocation);
        data.put("copyStatus", copy.copyStatus == null ? "SanSang" : copy.copyStatus);
        data.put("priorityUserId", copy.priorityUserId);
        data.put("isHidden", copy.isHidden);
        data.put("createdAt", copy.createdAt == null || copy.createdAt.isEmpty()
                ? com.google.firebase.firestore.FieldValue.serverTimestamp() : copy.createdAt);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return data;
    }

    /** Upload one local create/update. Firestore rules enforce the active-admin requirement. */
    public static Task<Void> uploadBookToCloud(BookTitle book) {
        if (book == null || book.bookTitleId == null || book.bookTitleId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookTitleId is required");
        }
        return FirebaseFirestore.getInstance().collection("BookTitles")
                .document(book.bookTitleId).set(toCloudMap(book), SetOptions.merge());
    }

    private static Map<String, Object> toCloudMap(BookTitle book) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", book.title == null ? "" : book.title);
        data.put("author", book.author == null ? "" : book.author);
        data.put("isbn", book.isbn == null ? "" : book.isbn);
        data.put("categoryId", book.categoryId == null ? CategoryDao.idForName(book.category) : book.categoryId);
        data.put("category", book.category == null ? "" : book.category);
        data.put("publisher", book.publisher == null ? "" : book.publisher);
        data.put("publishYear", book.publishYear);
        data.put("description", book.description == null ? "" : book.description);
        data.put("coverUrl", book.coverUrl == null ? "" : book.coverUrl);
        data.put("totalCopies", book.totalCopies);
        data.put("availableCopies", book.availableCopies);
        data.put("isHidden", book.isHidden);
        data.put("createdAt", book.createdAt == null || book.createdAt.isEmpty()
                ? com.google.firebase.firestore.FieldValue.serverTimestamp() : book.createdAt);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return data;
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
        if (listener != null) listener.onSyncCompleted(changed);
        // Ensure parent BookTitles are persisted before enforcing the BookCopies foreign key.
        firestore.collection("BookCopies").get()
                .addOnSuccessListener(copies -> executor.execute(() -> applyCopiesSnapshot(copies, listener)));
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

    private static BookTitle fromDocument(DocumentSnapshot doc) {
        BookTitle book = new BookTitle();
        book.bookTitleId = doc.getId();
        book.title = string(doc, "title");
        book.author = string(doc, "author");
        book.isbn = string(doc, "isbn");
        book.category = string(doc, "category");
        book.categoryId = string(doc, "categoryId");
        if (book.categoryId.isEmpty()) book.categoryId = CategoryDao.idForName(book.category);
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

    @Override
    public synchronized void close() {
        closed = true;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (copiesRegistration != null) {
            copiesRegistration.remove();
            copiesRegistration = null;
        }
        executor.shutdownNow();
        databaseHelper.close();
    }
}
