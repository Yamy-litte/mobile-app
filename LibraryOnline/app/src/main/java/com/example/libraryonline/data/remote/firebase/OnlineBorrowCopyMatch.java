package com.example.libraryonline.data.remote.firebase;

/** Stateless first-pass scan validation; Firestore transaction verifies again atomically. */
public final class OnlineBorrowCopyMatch {
    public enum Result { INVALID_DATA, WRONG_TITLE, WRONG_COPY, MATCH }

    private OnlineBorrowCopyMatch() {}

    /** New approvals reserve title quantity; the physical copy is chosen on scan. */
    public static Result compareUnassigned(String requestedTitleId,
                                            String scannedTitleId, String scannedCopyId) {
        if (requestedTitleId == null || requestedTitleId.isEmpty()
                || scannedTitleId == null || scannedTitleId.isEmpty()
                || scannedCopyId == null || scannedCopyId.isEmpty())
            return Result.INVALID_DATA;
        return requestedTitleId.equals(scannedTitleId) ? Result.MATCH : Result.WRONG_TITLE;
    }

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
