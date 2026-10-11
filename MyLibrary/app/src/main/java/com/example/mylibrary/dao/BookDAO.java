
package com.example.mylibrary.dao;

import com.example.mylibrary.models.Book;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BookDAO {

    private final FirebaseFirestore db;

    public BookDAO() {
        db = FirebaseFirestore.getInstance();
    }

    public interface BookCallback {
        void onSuccess(List<Book> books);

        void onFailure(String errorMessage);
    }

    // Lấy sách đang có sẵn
    public void getAvailableBooks(BookCallback callback) {
        db.collection("books")
                .whereEqualTo("status", "Available")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Book> books = new ArrayList<>();

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Book book = document.toObject(Book.class);
                        book.setId(document.getId());
                        books.add(book);
                    }

                    callback.onSuccess(books);
                })
                .addOnFailureListener(exception ->
                        callback.onFailure(getErrorMessage(exception))
                );
    }

    // Lấy sách phổ biến, popularity cao nhất xếp trước
    public void getPopularBooks(
            int limit,
            BookCallback callback
    ) {
        getAvailableBooks(new BookCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                Collections.sort(
                        books,
                        (book1, book2) ->
                                Integer.compare(
                                        book2.getPopularity(),
                                        book1.getPopularity()
                                )
                );

                int resultSize = Math.min(
                        Math.max(limit, 0),
                        books.size()
                );

                callback.onSuccess(
                        new ArrayList<>(books.subList(0, resultSize))
                );
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    // Lấy sách mới nhất, createdAt gần nhất xếp trước
    public void getNewestBooks(
            int limit,
            BookCallback callback
    ) {
        getAvailableBooks(new BookCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                Collections.sort(
                        books,
                        (book1, book2) -> {
                            if (book1.getCreatedAt() == null) {
                                return book2.getCreatedAt() == null ? 0 : 1;
                            }

                            if (book2.getCreatedAt() == null) {
                                return -1;
                            }

                            return book2.getCreatedAt().compareTo(
                                    book1.getCreatedAt()
                            );
                        }
                );

                int resultSize = Math.min(
                        Math.max(limit, 0),
                        books.size()
                );

                callback.onSuccess(
                        new ArrayList<>(books.subList(0, resultSize))
                );
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    // Lọc sách theo danh mục
    public void getBooksByCategory(
            String categoryId,
            BookCallback callback
    ) {
        db.collection("books")
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("status", "Available")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Book> books = new ArrayList<>();

                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Book book = document.toObject(Book.class);
                        book.setId(document.getId());
                        books.add(book);
                    }

                    callback.onSuccess(books);
                })
                .addOnFailureListener(exception ->
                        callback.onFailure(getErrorMessage(exception))
                );
    }
        // tim sach
    public void searchBooks(
            String keyword,
            BookCallback callback
    ) {
        getAvailableBooks(new BookCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                String query = keyword == null
                        ? ""
                        : keyword.trim().toLowerCase(
                        java.util.Locale.ROOT
                );

                List<Book> results = new ArrayList<>();

                for (Book book : books) {
                    String title = book.getTitle() == null
                            ? ""
                            : book.getTitle().toLowerCase(
                            java.util.Locale.ROOT
                    );

                    String author = book.getAuthor() == null
                            ? ""
                            : book.getAuthor().toLowerCase(
                            java.util.Locale.ROOT
                    );

                    if (title.contains(query)
                            || author.contains(query)) {
                        results.add(book);
                    }
                }

                callback.onSuccess(results);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    public void getBookById(
            String bookId,
            BookCallback callback
    ) {
        if (bookId == null || bookId.trim().isEmpty()) {
            callback.onFailure("Mã sách không hợp lệ.");
            return;
        }

        db.collection("books")
                .document(bookId.trim())
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onFailure("Không tìm thấy sách.");
                        return;
                    }

                    Book book = document.toObject(Book.class);

                    if (book == null) {
                        callback.onFailure(
                                "Không thể đọc thông tin sách."
                        );
                        return;
                    }

                    book.setId(document.getId());

                    callback.onSuccess(
                            new ArrayList<>(
                                    java.util.Collections.singletonList(book)
                            )
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onFailure(getErrorMessage(exception))
                );
    }

    private String getErrorMessage(Exception exception) {
        return exception.getLocalizedMessage() != null
                ? exception.getLocalizedMessage()
                : "Không thể tải dữ liệu sách.";
    }
}
