package com.example.libraryonline.ui.reader;

/** Keeps loading errors separate from the visible search-result count. */
public final class CatalogueLoadStatus {
    private String remoteError;
    private String localError;
    private boolean receivedServerSnapshot;
    private int serverBookCount;
    private int localBookCount = -1;
    private int hiddenBookCount;

    public void remoteFailed(String message) { remoteError = message; }
    public void localFailed(String message) { localError = message; }
    public void localLoaded() { localError = null; }
    public void localLoaded(int totalCount, int publicCount) {
        localLoaded();
        localBookCount = totalCount;
        hiddenBookCount = totalCount - publicCount;
    }

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
        if (localBookCount > 0 && hiddenBookCount == localBookCount) {
            return "Có " + hiddenBookCount + " đầu sách đang ẩn. Thư viện chưa có sách công khai.";
        }
        if (localBookCount == 0) {
            return "Firebase: " + serverBookCount + " đầu sách; SQLite: 0. Chưa đọc được danh mục cục bộ.";
        }
        return count + " · Đã nhận " + serverBookCount + " đầu sách từ Firebase";
    }
}
