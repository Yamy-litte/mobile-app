package com.example.libraryonline.data.session;

import com.example.libraryonline.data.model.User;
import com.example.libraryonline.data.model.Admin;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import static com.example.libraryonline.data.session.SessionException.Reason.*;

/** No Intent, SQLite role, registration claim or client-selected role grants access here. */
public final class SessionPolicy {
    private SessionPolicy() {}
    public static SessionProfile resolve(String uid, String authEmail, Map<String,Object> admin,
                                         Map<String,Object> user) {
        if (uid==null || uid.isEmpty()) throw new SessionException(SIGNED_OUT,"Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
        if (admin!=null) {
            String status=text(admin,"adminStatus");
            if (!"Active".equals(status) && !"HoatDong".equals(status)) {
                throw new SessionException(INACTIVE_ADMIN,"Tài khoản quản trị chưa hoạt động hoặc đã bị vô hiệu hóa.");
            }
            if ((admin.containsKey("userId") && !uid.equals(text(admin,"userId")))
                    || (admin.containsKey("email") && !sameEmail(authEmail,text(admin,"email")))) invalid();
            User local=null;
            if (user!=null) {
                if (!"Admin".equals(text(user,"role")) || !uid.equals(text(user,"userId"))) invalid();
                local=profile(uid,user);
                if (!sameEmail(authEmail,local.email) || local.isDeleted) invalid();
            }
            String name=text(admin,"fullName");
            if (name.isEmpty() && local!=null) name=local.fullName;
            if (name.isEmpty()) name=authEmail==null?"Quản trị viên":authEmail;
            // Bootstrap/legacy Admin metadata may be incomplete; never invent SQL fields.
            if(local!=null && (local.fullName.isEmpty() || local.createdAt.isEmpty() || local.updatedAt.isEmpty()
                    || !("ChoDuyet".equals(local.cardStatus) || "HoatDong".equals(local.cardStatus)
                        || "BiKhoa".equals(local.cardStatus) || "HetHan".equals(local.cardStatus)))) local=null;
            Admin localAdmin=null;
            if (local!=null && !text(admin,"employeeCode").isEmpty()) {
                localAdmin=new Admin(); localAdmin.adminId=uid; localAdmin.userId=uid;
                localAdmin.employeeCode=text(admin,"employeeCode"); localAdmin.adminStatus="Active";
                localAdmin.createdAt=text(admin,"createdAt"); localAdmin.updatedAt=text(admin,"updatedAt");
            }
            return new SessionProfile(uid,SessionProfile.Role.ADMIN,name,authEmail,local,localAdmin,false);
        }
        if (user==null) throw new SessionException(MISSING_PROFILE,"Tài khoản chưa có hồ sơ độc giả đã duyệt.");
        if (!uid.equals(text(user,"userId")) || !"Reader".equals(text(user,"role"))) invalid();
        User local=profile(uid,user);
        if (local.isDeleted || !"HoatDong".equals(local.cardStatus)) {
            throw new SessionException(BLOCKED,"Thẻ độc giả chưa hoạt động, đã khóa hoặc hết hạn. Vui lòng liên hệ thư viện.");
        }
        if (!sameEmail(authEmail,local.email) || local.fullName.isEmpty() || local.dateOfBirth.isEmpty()
                || local.phone.isEmpty() || local.barcode.isEmpty() || local.createdAt.isEmpty()) invalid();
        Object flag=user.get("mustChangePassword");
        if (flag!=null && !(flag instanceof Boolean)) invalid();
        return new SessionProfile(uid,SessionProfile.Role.READER,local.fullName,local.email,local,null,Boolean.TRUE.equals(flag));
    }
    private static User profile(String uid,Map<String,Object> data) {
        User u=new User(); u.userId=uid; u.role=text(data,"role"); u.fullName=text(data,"fullName");
        u.email=text(data,"email"); u.dateOfBirth=text(data,"dateOfBirth"); u.phone=text(data,"phone");
        u.barcode=text(data,"barcode"); u.cardStatus=text(data,"cardStatus");
        u.cardExpiryDate=text(data,"cardExpiryDate"); u.createdAt=text(data,"createdAt"); u.updatedAt=text(data,"updatedAt");
        Object deleted=data.get("isDeleted");
        if (deleted!=null && !(deleted instanceof Boolean)
                && !(deleted instanceof Number && (((Number)deleted).intValue()==0 || ((Number)deleted).intValue()==1))) invalid();
        u.isDeleted=Boolean.TRUE.equals(deleted) || (deleted instanceof Number && ((Number)deleted).intValue()==1);
        return u;
    }
    public static String text(Map<String,Object> data,String key) {
        Object value=data.get(key);
        if (value instanceof Timestamp) return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(((Timestamp)value).toDate());
        return value instanceof String?((String)value).trim():"";
    }
    private static boolean sameEmail(String a,String b) { return a!=null && !a.trim().isEmpty() && a.trim().equalsIgnoreCase(b); }
    private static void invalid() { throw new SessionException(INVALID_PROFILE,"Hồ sơ không hợp lệ hoặc không khớp tài khoản. Vui lòng liên hệ thư viện."); }
}
