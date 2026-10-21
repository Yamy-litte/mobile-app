package com.example.libraryonline.ui.reader;

/** Keeps loading errors separate from the visible search-result count. */
public final class CatalogueLoadStatus {
    private String remoteError;
    private String localError;
    private boolean receivedServerSnapshot;
    private int serverBookCount;

    public void remoteFailed(String message) { remoteError = message; }
    public void localFailed(String message) { localError = message; }
    public void localLoaded() { localError = null; }

    public void remoteSnapshot(boolean fromCache, int count) {
        if (!fromCache) {
            receivedServerSnapshot = true;
            serverBookCount = count;
            remoteError = null;
        }
    }

    public String describe(int localCount, int displayedCount) {
        String count = displayedCount + " đầu sách đang hiển thị từ SQLite";
        if (localError != null) return "Không đọc được SQLite: " + localError;
        if (remoteError != null) return "Lỗi đồng bộ Firebase: " + remoteError + ". " + count;
        if (!receivedServerSnapshot) return "Đang chờ dữ liệu Firebase. " + count;
        if (serverBookCount == 0) return "Firebase chưa có đầu sách. SQLite có " + localCount + " đầu sách.";
        return count + " · Đã nhận danh mục Firebase";
    }
}
