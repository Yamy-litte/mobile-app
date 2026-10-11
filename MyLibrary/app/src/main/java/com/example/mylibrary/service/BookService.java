
package com.example.mylibrary.service;

import com.example.mylibrary.dao.BookDAO;
import com.example.mylibrary.model.Book;

import java.util.List;

public class BookService {

    private final BookDAO bookDAO;

    public BookService() {
        bookDAO = new BookDAO();
    }

    // Lấy tất cả sách đang có sẵn
    public void getAvailableBooks(BookServiceCallback callback) {
        bookDAO.getAvailableBooks(createDaoCallback(callback));
    }

    // Lấy danh sách sách phổ biến
    public void getPopularBooks(
            int limit,
            BookServiceCallback callback
    ) {
        bookDAO.getPopularBooks(limit, createDaoCallback(callback));
    }

    public void searchBooks(
            String keyword,
            BookServiceCallback callback
    ) {
        bookDAO.searchBooks(
                keyword,
                new BookDAO.BookCallback() {
                    @Override
                    public void onSuccess(
                            List<Book> books
                    ) {
                        callback.onSuccess(books);
                    }

                    @Override
                    public void onFailure(
                            String errorMessage
                    ) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }


    // Lấy danh sách sách mới nhất
    public void getNewestBooks(
            int limit,
            BookServiceCallback callback
    ) {
        bookDAO.getNewestBooks(limit, createDaoCallback(callback));
    }

    // Lấy sách theo danh mục
    public void getBooksByCategory(
            String categoryId,
            BookServiceCallback callback
    ) {
        bookDAO.getBooksByCategory(
                categoryId,
                createDaoCallback(callback)
        );
    }

    private BookDAO.BookCallback createDaoCallback(
            BookServiceCallback callback
    ) {
        return new BookDAO.BookCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                callback.onSuccess(books);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        };
    }


    public void getBookById(
            String bookId,
            BookServiceCallback callback
    ) {
        bookDAO.getBookById(
                bookId,
                new BookDAO.BookCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        callback.onSuccess(books);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }

    public interface BookServiceCallback {
        void onSuccess(List<Book> books);

        void onFailure(String errorMessage);
    }
}
