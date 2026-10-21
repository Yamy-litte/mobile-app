package com.example.libraryonline.ui.auth;

import com.example.libraryonline.data.session.SessionException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class AuthErrors {
    private AuthErrors() {}

    public static boolean rejectsSession(Exception error) {
        return error instanceof SessionException
                || error instanceof FirebaseAuthInvalidUserException
                || (error instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) error).getCode()
                                == FirebaseFirestoreException.Code.UNAUTHENTICATED);
    }

    public static String message(Exception error) {
        if (error instanceof SessionException) return error.getMessage();
        if (error instanceof FirebaseNetworkException
                || (error instanceof FirebaseFirestoreException
                        && (((FirebaseFirestoreException) error).getCode()
                                        == FirebaseFirestoreException.Code.UNAVAILABLE
                                || ((FirebaseFirestoreException) error).getCode()
                                        == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))) {
            return "Không thể kết nối. Hãy kiểm tra mạng và thử lại.";
        }
        if (error instanceof FirebaseTooManyRequestsException)
            return "Bạn đã thử quá nhiều lần. Vui lòng đợi rồi thử lại.";
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code))
                return "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ thư viện.";
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code))
                return "Đăng nhập Email/Password chưa được bật trong Firebase Authentication.";
            if ("ERROR_REQUIRES_RECENT_LOGIN".equals(code)
                    || "ERROR_USER_TOKEN_EXPIRED".equals(code))
                return "Phiên đăng nhập đã hết. Hãy đăng nhập lại.";
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) return "Email này đã được sử dụng.";
            return "Email hoặc mật khẩu không chính xác.";
        }
        if (error instanceof FirebaseFirestoreException)
            return "Không xác minh được hồ sơ. Hãy kiểm tra kết nối hoặc liên hệ thư viện.";
        return "Không thể hoàn tất thao tác. Vui lòng thử lại.";
    }
}
