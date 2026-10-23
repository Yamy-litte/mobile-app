package com.example.libraryonline.data;

import static org.junit.Assert.assertEquals;

import com.example.libraryonline.data.remote.firebase.OnlineBorrowCopyMatch;

import org.junit.Test;

/** The correct title alone is not sufficient: only the copy held at approval can be handed over. */
public class OnlineBorrowCopyMatchTest {
    @Test public void acceptsOnlyReservedPhysicalCopyOfRequestedTitle() {
        assertEquals(OnlineBorrowCopyMatch.Result.MATCH,
                OnlineBorrowCopyMatch.compare("title-1", "copy-1", "title-1", "copy-1"));
    }

    @Test public void blocksDifferentBookTitleEvenWithScannedBarcode() {
        assertEquals(OnlineBorrowCopyMatch.Result.WRONG_TITLE,
                OnlineBorrowCopyMatch.compare("title-1", "copy-1", "title-2", "copy-2"));
    }

    @Test public void blocksAnotherCopyOfSameTitle() {
        assertEquals(OnlineBorrowCopyMatch.Result.WRONG_COPY,
                OnlineBorrowCopyMatch.compare("title-1", "copy-1", "title-1", "copy-2"));
    }

    @Test public void newApprovalCanScanAnyReadyCopyOfRequestedTitle() {
        assertEquals(OnlineBorrowCopyMatch.Result.MATCH,
                OnlineBorrowCopyMatch.compareUnassigned("title-1", "title-1", "copy-2"));
    }

    @Test public void newApprovalCannotScanDifferentTitleOrMissingCopy() {
        assertEquals(OnlineBorrowCopyMatch.Result.WRONG_TITLE,
                OnlineBorrowCopyMatch.compareUnassigned("title-1", "title-2", "copy-2"));
        assertEquals(OnlineBorrowCopyMatch.Result.INVALID_DATA,
                OnlineBorrowCopyMatch.compareUnassigned("title-1", "title-1", null));
    }

    @Test public void missingAssignmentCannotBeApprovedForHandover() {
        assertEquals(OnlineBorrowCopyMatch.Result.INVALID_DATA,
                OnlineBorrowCopyMatch.compare("title-1", null, "title-1", "copy-1"));
    }
}
