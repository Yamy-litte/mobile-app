package com.example.libraryonline.data.inventory;

import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.BookTitle;

import java.util.*;

/** Transaction write plans. Maps contain only fields this operation owns. No I/O. */
public final class InventoryChanges {
    private InventoryChanges() {}

    private static String text(String v) {
        return v == null ? "" : v.trim();
    }

    public static Map<String, Object> metadata(BookTitle b) {
        InventoryPolicy.validateTitle(b);
        Map<String, Object> d = new HashMap<>();
        d.put("title", text(b.title));
        d.put("author", text(b.author));
        d.put("categoryId", b.categoryId);
        d.put("isbn", text(b.isbn));
        d.put("publisher", text(b.publisher));
        d.put("publishYear", b.publishYear);
        d.put("description", text(b.description));
        d.put("coverUrl", text(b.coverUrl));
        return d;
    }

    public static Map<String, Map<String, Object>> intake(
            BookTitle b, List<BookCopy> copies, StockIntake intake) {
        int existing = 0;
        for (int i = 0; i < intake.quantity; i++)
            for (BookCopy c : copies)
                if (intake.copyId(i).equals(c.copyId)) {
                    if (!b.bookTitleId.equals(c.bookTitleId)
                            || !intake.barcode(i).equals(c.barcode))
                        throw new IllegalArgumentException(
                                "Mã thao tác trùng dữ liệu khác. Cần đối soát.");
                    existing++;
                }
        if (existing == intake.quantity) return Collections.emptyMap();
        if (existing > 0)
            throw new IllegalArgumentException("Lần nhập trước chưa đầy đủ. Cần đối soát dữ liệu.");
        InventoryPolicy.audit(b, copies);
        Set<String> barcodes = new HashSet<>();
        for (BookCopy c : copies) barcodes.add(c.barcode);
        Map<String, Map<String, Object>> writes = new LinkedHashMap<>();
        for (int i = 0; i < intake.quantity; i++) {
            if (!barcodes.add(intake.barcode(i)))
                throw new IllegalArgumentException(
                        "Dải mã đã được dùng. Tải lại kho trước khi nhập.");
            Map<String, Object> c = new HashMap<>();
            c.put("bookTitleId", b.bookTitleId);
            c.put("barcode", intake.barcode(i));
            c.put("shelfLocation", intake.shelf);
            c.put("copyStatus", "SanSang");
            c.put("priorityUserId", null);
            c.put("isHidden", false);
            writes.put("BookCopies/" + intake.copyId(i), c);
        }
        Map<String, Object> counts = new HashMap<>();
        try {
            counts.put("totalCopies", Math.addExact(b.totalCopies, intake.quantity));
            counts.put("availableCopies", Math.addExact(b.availableCopies, intake.quantity));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Số lượng vượt giới hạn.");
        }
        writes.put("BookTitles/" + b.bookTitleId, counts);
        return writes;
    }

    public static Map<String, Map<String, Object>> transition(
            BookTitle b, List<BookCopy> copies, String copyId, String target) {
        if (!"ThanhLy".equals(target) && !"DangSuaChua".equals(target))
            throw new IllegalArgumentException("Trạng thái không được hỗ trợ.");
        BookCopy copy = null;
        for (BookCopy c : copies) if (copyId.equals(c.copyId)) copy = c;
        if (copy == null) throw new IllegalArgumentException("Không tìm thấy bản sách.");
        if (target.equals(copy.copyStatus) && copy.isHidden) return Collections.emptyMap();
        InventoryPolicy.requireRetirable(copy);
        InventoryPolicy.audit(b, copies);
        Map<String, Map<String, Object>> writes = new LinkedHashMap<>();
        Map<String, Object> change = new HashMap<>();
        change.put("copyStatus", target);
        change.put("isHidden", true);
        writes.put("BookCopies/" + copyId, change);
        Map<String, Object> counts = new HashMap<>();
        counts.put("availableCopies", b.availableCopies - 1);
        writes.put("BookTitles/" + b.bookTitleId, counts);
        return writes;
    }
}
