package com.example.libraryonline.session;

import com.example.libraryonline.data.session.SessionPolicy;
import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.data.session.SessionException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionPolicyTest {
    static Map<String, Object> reader() {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", "uid-reader"); p.put("role", "Reader"); p.put("cardStatus", "HoatDong");
        p.put("fullName", "Nguyen Lan"); p.put("email", "reader@example.com");
        p.put("phone", "0912345678"); p.put("dateOfBirth", "2000-01-01");
        p.put("barcode", "TV-READER"); p.put("createdAt", "2026-01-01"); p.put("updatedAt", "2026-01-01");
        return p;
    }
    @Test public void activeReaderUsesTheAuthenticatedUidAndRetainsPasswordRequirement() {
        Map<String, Object> p = reader(); p.put("mustChangePassword", true);
        SessionProfile profile = SessionPolicy.resolve("uid-reader", "reader@example.com", null, p);
        assertEquals(SessionProfile.Role.READER, profile.role);
        assertEquals("uid-reader", profile.uid);
        assertEquals("uid-reader", profile.user.userId);
        assertTrue(profile.mustChangePassword);
    }
    @Test public void lockedPendingExpiredDeletedAndWrongUidAreDenied() {
        for (String status : new String[]{"BiKhoa", "ChoDuyet", "HetHan"}) {
            Map<String, Object> p = reader(); p.put("cardStatus", status);
            assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, p));
        }
        Map<String, Object> deleted = reader(); deleted.put("isDeleted", true);
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, deleted));
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("someone-else", "reader@example.com", null, reader()));
    }
    @Test public void aUsersRoleAloneCannotGrantAdminRights() {
        Map<String, Object> p = reader(); p.put("role", "Admin");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, p));
    }
    @Test public void missingOrIncompleteProfilesAreDenied() {
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, null));
        Map<String, Object> p = reader(); p.remove("barcode");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, p));
        assertThrows(SessionException.class, () -> SessionPolicy.resolve(null, "reader@example.com", null, reader()));
    }
    @Test public void activeBootstrapAdminDoesNotRequireInventedUsersMetadata() {
        Map<String, Object> admin = new HashMap<>(); admin.put("adminStatus", "Active");
        admin.put("fullName", "Thu thu"); admin.put("email", "admin@example.com");
        SessionProfile profile = SessionPolicy.resolve("uid-admin", "admin@example.com", admin, null);
        assertEquals(SessionProfile.Role.ADMIN, profile.role);
        assertEquals("Thu thu", profile.displayName);
        assertNull(profile.user);
    }
    @Test public void inactiveAdminCannotFallBackToAReaderProfile() {
        Map<String, Object> admin = new HashMap<>(); admin.put("adminStatus", "Inactive");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", admin, reader()));
    }
    @Test public void adminEmailOrUidConflictIsRejected() {
        Map<String, Object> admin = new HashMap<>(); admin.put("adminStatus", "Active"); admin.put("email", "other@example.com");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-admin", "admin@example.com", admin, null));
        admin.put("email", "admin@example.com"); admin.put("userId", "other-uid");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-admin", "admin@example.com", admin, null));
    }
    @Test public void malformedPasswordFlagsAndConflictingRolesDoNotGrantAccess() {
        Map<String, Object> p = reader(); p.put("mustChangePassword", "false");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", null, p));
        Map<String, Object> admin = new HashMap<>(); admin.put("adminStatus", "Active");
        assertThrows(SessionException.class, () -> SessionPolicy.resolve("uid-reader", "reader@example.com", admin, reader()));
    }
}
