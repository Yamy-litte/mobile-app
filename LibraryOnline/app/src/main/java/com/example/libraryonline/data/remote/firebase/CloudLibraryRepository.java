package com.example.libraryonline.data.remote.firebase;

import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.BookTitle;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Firestore data-access boundary for the library catalogue.
 *
 * This class only performs cloud operations. SQLite persistence and reconciliation stay in
 * BookTitleDao, BookCopyDao, and BookCatalogueSyncManager so there is one place responsible
 * for applying incoming snapshots to the local database.
 */
public final class CloudLibraryRepository {
    private static final String BOOK_TITLES = "BookTitles";
    private static final String BOOK_COPIES = "BookCopies";
    private static final String CATEGORIES = "Categories";

    private final FirebaseFirestore firestore;

    public CloudLibraryRepository() {
        this(FirebaseFirestore.getInstance());
    }

    CloudLibraryRepository(FirebaseFirestore firestore) {
        if (firestore == null) throw new IllegalArgumentException("Firestore is required");
        this.firestore = firestore;
    }

    public Task<DocumentSnapshot> getBookTitle(String bookTitleId) {
        requireId(bookTitleId, "bookTitleId");
        return firestore.collection(BOOK_TITLES).document(bookTitleId).get();
    }

    public Task<QuerySnapshot> getBookTitles() {
        return firestore.collection(BOOK_TITLES).get();
    }

    public ListenerRegistration listenBookTitles(EventListener<QuerySnapshot> listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        return firestore.collection(BOOK_TITLES).addSnapshotListener(listener);
    }

    public Task<Void> saveBookTitle(BookTitle book) {
        if (book == null) throw new IllegalArgumentException("BookTitle is required");
        requireId(book.bookTitleId, "bookTitleId");
        return firestore.collection(BOOK_TITLES).document(book.bookTitleId)
                .set(toCloudMap(book), SetOptions.merge());
    }

    public Task<QuerySnapshot> getBookCopies() {
        return firestore.collection(BOOK_COPIES).get();
    }

    public ListenerRegistration listenBookCopies(EventListener<QuerySnapshot> listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        return firestore.collection(BOOK_COPIES).addSnapshotListener(listener);
    }

    public Task<Void> saveBookCopy(BookCopy copy) {
        if (copy == null) throw new IllegalArgumentException("BookCopy is required");
        requireId(copy.copyId, "copyId");
        if (copy.bookTitleId == null || copy.bookTitleId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookTitleId is required");
        }
        return firestore.collection(BOOK_COPIES).document(copy.copyId)
                .set(toCloudMap(copy), SetOptions.merge());
    }

    public Task<Void> saveCategory(String categoryId, String name, boolean active) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên thể loại không được để trống");
        }
        String normalizedName = name.trim();
        String id = categoryId == null || categoryId.trim().isEmpty()
                ? CategoryDao.idForName(normalizedName) : categoryId.trim();
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", id);
        data.put("name", normalizedName);
        data.put("isActive", active);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return firestore.collection(CATEGORIES).document(id).set(data, SetOptions.merge());
    }

    public ListenerRegistration listenCategories(EventListener<QuerySnapshot> listener) {
        if (listener == null) throw new IllegalArgumentException("listener is required");
        return firestore.collection(CATEGORIES).addSnapshotListener(listener);
    }

    /** Convert the current model to the established Firestore BookTitles schema. */
    public static Map<String, Object> toCloudMap(BookTitle book) {
        if (book == null) throw new IllegalArgumentException("BookTitle is required");
        String category = book.category == null || book.category.trim().isEmpty()
                ? "Khác" : book.category.trim();
        String categoryId = book.categoryId == null || book.categoryId.trim().isEmpty()
                ? CategoryDao.idForName(category) : book.categoryId.trim();

        Map<String, Object> data = new HashMap<>();
        data.put("title", safe(book.title));
        data.put("author", safe(book.author));
        data.put("isbn", safe(book.isbn));
        data.put("categoryId", categoryId);
        data.put("publisher", safe(book.publisher));
        data.put("publishYear", book.publishYear);
        data.put("description", safe(book.description));
        data.put("coverUrl", safe(book.coverUrl));
        data.put("totalCopies", book.totalCopies);
        data.put("availableCopies", book.availableCopies);
        data.put("isHidden", book.isHidden);
        data.put("createdAt", book.createdAt == null || book.createdAt.isEmpty()
                ? com.google.firebase.firestore.FieldValue.serverTimestamp() : book.createdAt);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return data;
    }

    /** Convert the current model to the established Firestore BookCopies schema. */
    public static Map<String, Object> toCloudMap(BookCopy copy) {
        if (copy == null) throw new IllegalArgumentException("BookCopy is required");
        Map<String, Object> data = new HashMap<>();
        data.put("bookTitleId", safe(copy.bookTitleId));
        data.put("barcode", safe(copy.barcode));
        data.put("shelfLocation", safe(copy.shelfLocation));
        data.put("copyStatus", copy.copyStatus == null || copy.copyStatus.trim().isEmpty()
                ? "SanSang" : copy.copyStatus);
        data.put("priorityUserId", copy.priorityUserId);
        data.put("isHidden", copy.isHidden);
        data.put("createdAt", copy.createdAt == null || copy.createdAt.isEmpty()
                ? com.google.firebase.firestore.FieldValue.serverTimestamp() : copy.createdAt);
        data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        return data;
    }

    /** Stable category IDs must use the same Vietnamese normalization as SQLite. */
    public static String categoryIdForName(String name) {
        if (name == null || name.trim().isEmpty()) return "khac";
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "khac" : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void requireId(String id, String field) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
