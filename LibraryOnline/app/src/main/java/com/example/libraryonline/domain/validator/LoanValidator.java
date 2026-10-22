package com.example.libraryonline.domain.validator;

import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.User;

import java.util.Collection;

/**
 * Pure business rules for in-person circulation. Callers must include online holds
 * in activeLoanCount and activeTitleIds so local and remote reservations count.
 */
public final class LoanValidator {
    private LoanValidator() {}

    public static final String CARD_ACTIVE = "HoatDong";
    public static final String COPY_AVAILABLE = "SanSang";
    public static final String COPY_ONLINE_HOLD = "ChoLay";
    public static final String COPY_RESERVED = "DaDatTruoc";

    public static String validateBorrow(User user, int activeLoanCount, int maxCopies,
                                        int requestedDays, int maxLoanDays,
                                        boolean hasOverdueLoan,
                                        Collection<String> activeBookTitleIds,
                                        String requestedBookTitleId) {
        if (user == null || user.userId == null || user.userId.trim().isEmpty()) {
            return "Không tìm thấy hồ sơ độc giả.";
        }
        if (user.isDeleted) return "Thẻ độc giả đã ngừng sử dụng.";
        if (!CARD_ACTIVE.equals(user.cardStatus)) return "Thẻ độc giả chưa hoạt động hoặc đang bị khóa.";
        if (hasOverdueLoan) return "Độc giả đang có sách quá hạn chưa trả.";
        if (maxCopies < 1 || activeLoanCount >= maxCopies) return "Độc giả đã đạt hạn mức mượn sách.";
        if (requestedDays < 1 || maxLoanDays < 1 || requestedDays > maxLoanDays) {
            return "Số ngày mượn không hợp lệ hoặc vượt cấu hình tối đa.";
        }
        if (requestedBookTitleId == null || requestedBookTitleId.trim().isEmpty()) {
            return "Không xác định được đầu sách.";
        }
        if (activeBookTitleIds != null && activeBookTitleIds.contains(requestedBookTitleId)) {
            return "Độc giả đã mượn hoặc giữ một cuốn thuộc đầu sách này.";
        }
        return null;
    }

    /** Return null when the copy can be checked out by this reader; otherwise a user-facing reason. */
    public static String validateCopyCheckout(BookCopy copy, String readerId) {
        if (copy == null || copy.copyId == null || copy.copyId.trim().isEmpty()) {
            return "Không tìm thấy cuốn sách.";
        }
        if (copy.isHidden) return "Cuốn sách đang bị ẩn khỏi phục vụ.";
        if (COPY_AVAILABLE.equals(copy.copyStatus)) return null;
        if (COPY_ONLINE_HOLD.equals(copy.copyStatus) && sameId(copy.priorityUserId, readerId)) return null;
        if (COPY_RESERVED.equals(copy.copyStatus) && sameId(copy.priorityUserId, readerId)) return null;
        if (COPY_ONLINE_HOLD.equals(copy.copyStatus)) return "Cuốn sách đang được giữ cho độc giả khác đến nhận.";
        if (COPY_RESERVED.equals(copy.copyStatus)) return "Cuốn sách được ưu tiên cho độc giả đặt trước khác.";
        return "Cuốn sách hiện không ở trạng thái sẵn sàng cho mượn.";
    }

    private static boolean sameId(String first, String second) {
        return first != null && second != null && first.equals(second);
    }
}
