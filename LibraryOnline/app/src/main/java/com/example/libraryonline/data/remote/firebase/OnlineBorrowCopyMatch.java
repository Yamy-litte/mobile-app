package com.example.libraryonline.data.remote.firebase;

/** Stateless first-pass scan validation; Firestore transaction verifies again atomically. */
public final class OnlineBorrowCopyMatch {
    public enum Result { INVALID_DATA, WRONG_TITLE, WRONG_COPY, MATCH }

    private OnlineBorrowCopyMatch() {}

    public static Result compare(String requestedTitleId, String heldCopyId,
                                 String scannedTitleId, String scannedCopyId) {
        if (requestedTitleId == null || requestedTitleId.isEmpty()
                || heldCopyId == null || heldCopyId.isEmpty()
                || scannedTitleId == null || scannedTitleId.isEmpty()
                || scannedCopyId == null || scannedCopyId.isEmpty())
            return Result.INVALID_DATA;
        if (!requestedTitleId.equals(scannedTitleId)) return Result.WRONG_TITLE;
        if (!heldCopyId.equals(scannedCopyId)) return Result.WRONG_COPY;
        return Result.MATCH;
    }
}
