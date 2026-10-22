package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.view.View;
import android.widget.*;

import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;
import com.example.libraryonline.data.model.BookTitle;

/** Binding independent from Firebase so empty/error/cache states can be verified. */
public final class BookDetailView {
    private final Activity activity;
    private boolean missing;

    public BookDetailView(Activity activity) {
        this.activity = activity;
        activity.findViewById(R.id.detailRetry).setVisibility(View.GONE);
    }

    private void text(int id, String text) {
        ((TextView) activity.findViewById(id)).setText(text);
    }

    private String present(String s) {
        return s == null || s.trim().isEmpty() ? "Chưa cập nhật" : s;
    }

    public void bind(BookTitle book) {
        boolean visible = book != null && !book.isHidden;
        activity.findViewById(R.id.detailContent).setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            missing = true;
            text(R.id.detailStatus, "Đầu sách không tồn tại hoặc không còn được công khai.");
            return;
        }
        if (missing) {
            text(R.id.detailStatus, "");
            missing = false;
        }
        text(R.id.detailTitle, present(book.title));
        text(R.id.detailAuthor, present(book.author));
        text(R.id.detailCategory, present(book.category));
        text(R.id.detailDescription, present(book.description));
        text(
                R.id.detailAvailability,
                book.availableCopies > 0
                        ? "Có " + book.availableCopies + " cuốn sẵn sàng"
                        : "Tạm hết sách sẵn sàng");
        ((TextView) activity.findViewById(R.id.detailAvailability))
                .setTextColor(
                        ContextCompat.getColor(
                                activity,
                                book.availableCopies > 0
                                        ? R.color.library_success
                                        : R.color.library_muted));
        activity.findViewById(R.id.detailRequestOnline).setEnabled(book.availableCopies > 0);
        text(
                R.id.detailMetadata,
                "Nhà xuất bản: "
                        + present(book.publisher)
                        + "\nNăm xuất bản: "
                        + (book.publishYear == null ? "Chưa cập nhật" : book.publishYear)
                        + "\nISBN: "
                        + present(book.isbn)
                        + "\nTổng bản đã nhập: "
                        + book.totalCopies);
        BookCoverLoader.load(
                book.coverUrl,
                activity.findViewById(R.id.detailCover),
                activity.findViewById(R.id.detailCoverFallback));
    }

    public void status(String message) {
        text(R.id.detailStatus, message);
        activity.findViewById(R.id.detailRetry).setVisibility(View.GONE);
    }

    public void error(String message) {
        text(R.id.detailStatus, message);
        activity.findViewById(R.id.detailRetry).setVisibility(View.VISIBLE);
    }
}
