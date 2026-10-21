package com.example.libraryonline.ui.admin.inventory;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.libraryonline.R;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.ui.reader.BookCoverLoader;

/** Bind real values to XML cards shared by the inventory pages. */
public final class InventoryViews {
    private InventoryViews() {}

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static void text(View root, int id, String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }

    public static void bindSummary(View root, BookTitle book) {
        text(root, R.id.summaryTitle, safe(book.title));
        text(root, R.id.summaryAuthor, safe(book.author));
        text(
                root,
                R.id.summaryCounts,
                book.availableCopies + " sẵn sàng / " + book.totalCopies + " đã nhập");
        BookCoverLoader.load(
                book.coverUrl,
                root.findViewById(R.id.summaryCover),
                root.findViewById(R.id.summaryCoverFallback));
    }

    public static void bindBook(View root, BookTitle book) {
        text(root, R.id.inventoryBookTitle, safe(book.title));
        text(
                root,
                R.id.inventoryBookState,
                book.isHidden
                        ? "Đã ẩn"
                        : book.availableCopies > 0 ? "Sẵn sàng" : "Hết sách sẵn sàng");
        ((TextView) root.findViewById(R.id.inventoryBookState))
                .setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                                root.getContext(),
                                book.isHidden
                                        ? R.color.library_danger
                                        : book.availableCopies > 0
                                                ? R.color.library_success
                                                : R.color.library_warning));
        text(root, R.id.inventoryBookMeta, safe(book.author) + "\n" + safe(book.category));
        text(
                root,
                R.id.inventoryBookIsbn,
                "ISBN: " + (safe(book.isbn).isEmpty() ? "Chưa cập nhật" : book.isbn));
        text(
                root,
                R.id.inventoryBookCount,
                "Tổng đã nhập " + book.totalCopies + " / Sẵn sàng " + book.availableCopies);
        BookCoverLoader.load(
                book.coverUrl,
                root.findViewById(R.id.inventoryBookCover),
                root.findViewById(R.id.inventoryBookFallback));
    }

    public static void error(View root, int id, String message) {
        TextView error = root.findViewById(id);
        error.setText(message);
        error.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    public static void barcode(View root, int id, String value) {
        ImageView image = root.findViewById(id);
        try {
            image.setImageBitmap(BarcodeLabels.encode(value));
            image.setContentDescription(value);
        } catch (IllegalArgumentException error) {
            image.setImageDrawable(null);
            image.setContentDescription("Barcode không hợp lệ");
        }
    }
}
