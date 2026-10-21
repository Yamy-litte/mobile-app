package com.example.libraryonline.data;

import com.example.libraryonline.ui.reader.CatalogueLoadStatus;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogueLoadStatusTest {
    @Test public void permissionErrorSurvivesLocalReloadAndFilterChanges() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteFailed("PERMISSION_DENIED");
        status.localLoaded();
        assertTrue(status.describe(3, 3).contains("PERMISSION_DENIED"));
        assertTrue(status.describe(3, 0).contains("PERMISSION_DENIED"));
    }
    @Test public void emptyOfflineSnapshotIsNotEvidenceThatFirebaseIsEmpty() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteSnapshot(true, 0);
        assertFalse(status.describe(0, 0).contains("Firebase chưa có đầu sách"));
        status.remoteSnapshot(false, 0);
        assertTrue(status.describe(0, 0).contains("Firebase chưa có đầu sách"));
    }
    @Test public void successfulServerSnapshotClearsNetworkFailureButNotSqliteFailure() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteFailed("UNAVAILABLE");
        status.localFailed("disk error");
        status.remoteSnapshot(false, 2);
        assertTrue(status.describe(0, 0).contains("disk error"));
        status.localLoaded();
        assertFalse(status.describe(2, 2).contains("UNAVAILABLE"));
        assertTrue(status.describe(2, 2).contains("2 đầu sách"));
    }
    @Test public void zeroVisibleBooksExplainsHiddenInventoryInsteadOfClaimingNoData() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteSnapshot(false, 4);
        status.localLoaded(4, 0);
        String message = status.describe(0, 0);
        assertTrue(message.contains("4 đầu sách đang ẩn"));
        assertFalse(message.contains("Firebase chưa có đầu sách"));
    }
    @Test public void aServerSnapshotWithoutLocalRowsReportsTheMismatch() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteSnapshot(false, 4);
        status.localLoaded(0, 0);
        String message = status.describe(0, 0);
        assertTrue(message.contains("Firebase: 4"));
        assertTrue(message.contains("SQLite: 0"));
        assertFalse(message.contains("Đã nhận danh mục"));
    }
    @Test public void aSearchWithNoMatchesDoesNotClaimThatPublicBooksAreHidden() {
        CatalogueLoadStatus status = new CatalogueLoadStatus();
        status.remoteSnapshot(false, 4);
        status.localLoaded(4, 4);
        assertFalse(status.describe(4, 0).contains("đang ẩn"));
    }

}
