package com.example.libraryonline.data.remote.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Submits a request for staff review; this does not check out or reserve a copy. */
public final class OnlineBorrowRequestRepository {
    public interface Callback { void onSuccess(String requestId); void onFailure(Exception error); }
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void submit(String bookTitleId, int requestedDays, Callback callback) {
        if (bookTitleId == null || bookTitleId.trim().isEmpty() || bookTitleId.contains("/")) {
            callback.onFailure(new IllegalArgumentException("Đầu sách không hợp lệ.")); return;
        }
        if (requestedDays < 1 || requestedDays > 60) {
            callback.onFailure(new IllegalArgumentException("Số ngày mượn không hợp lệ.")); return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onFailure(new IllegalStateException("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.")); return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String requestId = UUID.randomUUID().toString();
        DocumentReference userRef = firestore.collection("Users").document(uid);
        DocumentReference titleRef = firestore.collection("BookTitles").document(bookTitleId);
        DocumentReference configRef = firestore.collection("SystemConfigs").document("default");
        DocumentReference requestRef = firestore.collection("OnlineBorrowRequests").document(requestId);

        firestore.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot user = transaction.get(userRef);
            com.google.firebase.firestore.DocumentSnapshot title = transaction.get(titleRef);
            com.google.firebase.firestore.DocumentSnapshot config = transaction.get(configRef);
            if (!user.exists() || !"Reader".equals(user.getString("role"))
                    || !"HoatDong".equals(user.getString("cardStatus"))
                    || Boolean.TRUE.equals(user.getBoolean("isDeleted")))
                throw new IllegalStateException("Thẻ độc giả chưa hoạt động hoặc đã bị khóa.");
            String expiry = user.getString("cardExpiryDate");
            if (expiry != null && !expiry.trim().isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    dateFormat.setLenient(false);
                    dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                    Date expiryDate = dateFormat.parse(expiry);
                    String today = dateFormat.format(new Date());
                    if (expiryDate == null || expiry.compareTo(today) < 0)
                        throw new IllegalStateException("Thẻ độc giả đã hết hạn.");
                } catch (java.text.ParseException ex) {
                    throw new IllegalStateException("Ngày hết hạn thẻ không hợp lệ. Vui lòng liên hệ thư viện.");
                }
            }
            if (!title.exists() || Boolean.TRUE.equals(title.getBoolean("isHidden")))
                throw new IllegalStateException("Đầu sách không tồn tại hoặc không còn được công khai.");
            Long available = title.getLong("availableCopies");
            if (available == null || available <= 0) throw new IllegalStateException("Hết sách.");
            Long maxDays = config.getLong("maxLoanDays");
            if (!config.exists() || maxDays == null || maxDays < 1 || maxDays > 60)
                throw new IllegalStateException("Thư viện chưa cấu hình thời hạn mượn hợp lệ.");
            if (requestedDays > maxDays)
                throw new IllegalStateException("Thời hạn mượn vượt quá giới hạn " + maxDays + " ngày.");

            Map<String, Object> request = new HashMap<>();
            request.put("requestId", requestId);
            request.put("readerUid", uid);
            request.put("bookTitleId", bookTitleId);
            request.put("bookTitle", title.getString("title") == null ? "" : title.getString("title"));
            request.put("requestedDays", requestedDays);
            request.put("status", "Pending");
            request.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            request.put("reviewedAt", null);
            request.put("reviewedBy", null);
            request.put("rejectionReason", "");
            transaction.set(requestRef, request);
            return requestId;
        }).addOnSuccessListener(callback::onSuccess).addOnFailureListener(callback::onFailure);
    }
}