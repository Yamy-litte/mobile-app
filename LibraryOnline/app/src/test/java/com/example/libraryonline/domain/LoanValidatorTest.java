package com.example.libraryonline.domain;

import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.User;
import com.example.libraryonline.domain.validator.LoanValidator;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LoanValidatorTest {
    private User activeReader() {
        User user = new User();
        user.userId = "reader-1";
        user.role = "Reader";
        user.cardStatus = "HoatDong";
        return user;
    }

    @Test public void allowsEligibleBorrow() {
        assertNull(LoanValidator.validateBorrow(activeReader(), 1, 3, 7, 14, false,
                Arrays.asList("title-1"), "title-2"));
    }

    @Test public void blocksAdminProfileFromReaderCheckout() {
        User admin = activeReader();
        admin.role = "Admin";
        assertNotNull(LoanValidator.validateBorrow(admin, 0, 3, 7, 14, false, null, "title-1"));
    }

    @Test public void blocksLockedCardAndOverdueReader() {
        User user = activeReader();
        user.cardStatus = "BiKhoa";
        assertNotNull(LoanValidator.validateBorrow(user, 0, 3, 7, 14, false, null, "title-1"));
        user.cardStatus = "HoatDong";
        assertNotNull(LoanValidator.validateBorrow(user, 0, 3, 7, 14, true, null, "title-1"));
    }

    @Test public void blocksLimitAndDuplicateTitle() {
        assertNotNull(LoanValidator.validateBorrow(activeReader(), 3, 3, 7, 14, false, null, "title-1"));
        assertNotNull(LoanValidator.validateBorrow(activeReader(), 1, 3, 7, 14, false,
                Arrays.asList("title-1"), "title-1"));
    }

    @Test public void blocksInvalidLoanDays() {
        assertNotNull(LoanValidator.validateBorrow(activeReader(), 0, 3, 0, 14, false, null, "title-1"));
        assertNotNull(LoanValidator.validateBorrow(activeReader(), 0, 3, 15, 14, false, null, "title-1"));
    }

    @Test public void permitsAvailableCopy() {
        BookCopy copy = new BookCopy();
        copy.copyId = "copy-1";
        copy.copyStatus = "SanSang";
        assertNull(LoanValidator.validateCopyCheckout(copy, "reader-1"));
    }

    @Test public void onlineHoldAlwaysRequiresCloudHandover() {
        BookCopy copy = new BookCopy();
        copy.copyId = "copy-1";
        copy.copyStatus = "ChoLay";
        copy.priorityUserId = "reader-1";
        assertNotNull(LoanValidator.validateCopyCheckout(copy, "reader-1"));
        assertNotNull(LoanValidator.validateCopyCheckout(copy, "reader-2"));
    }

    @Test public void legacyPreorderCannotBeCheckedOutDirectly() {
        BookCopy copy = new BookCopy();
        copy.copyId = "copy-1";
        copy.copyStatus = "DaDatTruoc";
        copy.priorityUserId = "reader-1";
        assertNotNull(LoanValidator.validateCopyCheckout(copy, "reader-1"));
    }

    @Test public void blocksHiddenParentTitleEvenIfCopyIsAvailable() {
        BookCopy copy = new BookCopy();
        copy.copyId = "copy-1";
        copy.copyStatus = "SanSang";
        copy.parentTitleHidden = true;
        assertNotNull(LoanValidator.validateCopyCheckout(copy, "reader-1"));
    }

    @Test public void blocksHiddenCopyEvenIfAvailable() {
        BookCopy copy = new BookCopy();
        copy.copyId = "copy-1";
        copy.copyStatus = "SanSang";
        copy.isHidden = true;
        assertNotNull(LoanValidator.validateCopyCheckout(copy, "reader-1"));
    }
}
