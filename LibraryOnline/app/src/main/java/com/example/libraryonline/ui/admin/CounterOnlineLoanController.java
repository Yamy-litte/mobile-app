package com.example.libraryonline.ui.admin;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.libraryonline.R;
import com.example.libraryonline.data.remote.firebase.OnlineLoanRequestRepository;
import com.example.libraryonline.data.remote.firebase.OnlineBorrowCopyMatch;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Online handover/return at the physical counter.
 * Scanning a Reader card loads only that Reader's approved holds and real loans.
 * A barcode can complete a transaction only if it belongs to the requested
 * BookTitle AND is the exact physical copy already assigned by Admin approval.
 *
 * UI markup belongs in view_admin_circulation and item_admin_counter_online_loan.
 * The Firestore transaction in OnlineLoanRequestRepository remains authoritative.
 */
final class CounterOnlineLoanController implements AutoCloseable {
    private final AdminMainActivity activity;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final OnlineLoanRequestRepository repository = new OnlineLoanRequestRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EditText readerBarcode, copyBarcode;
    private final LinearLayout requestsList;
    private final TextView readerInfo, summary, verification;
    private final Button handover, returnBook;
    private final List<DocumentSnapshot> requests = new ArrayList<>();

    private ListenerRegistration loansListener;
    private Runnable readerLookup, copyLookup;
    private String readerUid, verifiedCardCode, selectedId;
    private DocumentSnapshot selected, matchedCopy;
    private boolean freshRequests, freshCopy, processing, closed;
    private long generation;

    CounterOnlineLoanController(AdminMainActivity activity,
                                EditText readerBarcode, EditText copyBarcode) {
        this.activity = activity;
        this.readerBarcode = readerBarcode;
        this.copyBarcode = copyBarcode;
        this.requestsList = activity.findViewById(R.id.containerCounterOnlineLoans);
        this.readerInfo = activity.findViewById(R.id.textCounterOnlineReader);
        this.summary = activity.findViewById(R.id.textCounterOnlineSummary);
        this.verification = activity.findViewById(R.id.textCounterOnlineVerification);
        this.handover = activity.findViewById(R.id.buttonCounterOnlineCollect);
        this.returnBook = activity.findViewById(R.id.buttonCounterOnlineReturn);

        readerBarcode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleReaderLookup();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        copyBarcode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleCopyLookup();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        handover.setOnClickListener(v -> confirm(false));
        returnBook.setOnClickListener(v -> confirm(true));
        // Text may have been restored after a camera scan before controller creation.
        if (readerBarcode.length() > 0) scheduleReaderLookup();
    }

    boolean hasSelection() { return selected != null; }

    private boolean live(long token) {
        return !closed && token == generation && activity.isSessionReady()
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void setMessage(String value) { verification.setText(value); }

    private void blockActions() {
        handover.setEnabled(false);
        returnBook.setEnabled(false);
    }

    private void scheduleReaderLookup() {
        if (closed) return;
        ++generation;
        if (readerLookup != null) handler.removeCallbacks(readerLookup);
        if (copyLookup != null) handler.removeCallbacks(copyLookup);
        if (loansListener != null) { loansListener.remove(); loansListener = null; }
        freshRequests = false;
        freshCopy = false;
        matchedCopy = null;
        readerUid = null;
        verifiedCardCode = null;
        selected = null;
        selectedId = null;
        requests.clear();
        requestsList.removeAllViews();
        blockActions();
        if (copyBarcode.length() > 0) copyBarcode.setText("");
        summary.setText("");
        String code = readerBarcode.getText().toString().trim();
        if (code.isEmpty()) {
            readerInfo.setText("Quét mã thẻ để tải các yêu cầu đã duyệt.");
            setMessage("Chưa xác minh độc giả.");
            return;
        }
        readerInfo.setText("Đang đối chiếu mã thẻ với hồ sơ độc giả trên Firestore…");
        setMessage("Đang xác minh. Chưa thể giao/trả Online.");
        final long token = generation;
        readerLookup = () -> findReader(code, token);
        handler.postDelayed(readerLookup, 350L);
    }

    private void findReader(String barcode, long token) {
        if (!live(token)) return;
        firestore.collection("Users").whereEqualTo("barcode", barcode).get(Source.SERVER)
                .addOnCompleteListener(activity, result -> {
                    if (!live(token)) return;
                    if (!result.isSuccessful()) {
                        readerInfo.setText("Không thể xác minh mã thẻ: "
                                + error(result.getException()));
                        return;
                    }
                    List<DocumentSnapshot> matches = result.getResult().getDocuments();
                    if (matches.size() != 1) {
                        readerInfo.setText(matches.isEmpty()
                                ? "Không có độc giả nào mang mã thẻ này."
                                : "Mã thẻ trùng nhiều hồ sơ. Cần quản trị viên đối soát.");
                        return;
                    }
                    DocumentSnapshot user = matches.get(0);
                    String expiry = user.getString("cardExpiryDate");
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                            .format(new Date());
                    if (!"Reader".equals(user.getString("role"))
                            || !"HoatDong".equals(user.getString("cardStatus"))
                            || (expiry != null && !expiry.isEmpty()
                                    && expiry.compareTo(today) < 0)) {
                        readerInfo.setText("Thẻ độc giả bị khóa, hết hạn hoặc không hoạt động.");
                        return;
                    }
                    readerUid = user.getId();
                    verifiedCardCode = barcode;
                    String name = user.getString("fullName");
                    readerInfo.setText("Độc giả: "
                            + (name == null || name.trim().isEmpty() ? readerUid : name)
                            + " · Mã thẻ: " + barcode);
                    listenLoans(token);
                });
    }

    private void listenLoans(long token) {
        if (!live(token) || readerUid == null) return;
        loansListener = firestore.collection(OnlineLoanRequestRepository.REQUESTS)
                .whereEqualTo("userId", readerUid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, exception) -> {
                    if (!live(token)) return;
                    if (exception != null) {
                        freshRequests = false;
                        blockActions();
                        summary.setText("Không tải được yêu cầu Online: " + error(exception));
                        return;
                    }
                    if (snapshot == null) return;
                    freshRequests = !snapshot.getMetadata().isFromCache()
                            && !snapshot.getMetadata().hasPendingWrites();
                    displayRequests(snapshot);
                });
    }

    private void displayRequests(QuerySnapshot snapshot) {
        requests.clear();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String status = doc.getString("status");
            if (OnlineLoanRequestRepository.APPROVED.equals(status)
                    || OnlineLoanRequestRepository.COLLECTED.equals(status))
                requests.add(doc);
        }
        Collections.sort(requests, (a, b) -> {
            Timestamp left = a.getTimestamp("createdAt");
            Timestamp right = b.getTimestamp("createdAt");
            return Long.compare(right == null ? 0 : right.toDate().getTime(),
                    left == null ? 0 : left.toDate().getTime());
        });
        int waiting = 0, borrowed = 0;
        for (DocumentSnapshot doc : requests) {
            if (OnlineLoanRequestRepository.APPROVED.equals(doc.getString("status"))) waiting++;
            else borrowed++;
        }
        summary.setText(waiting + " yêu cầu đã duyệt đang chờ lấy · "
                + borrowed + " cuốn Online đang mượn"
                + (freshRequests ? "" : " · Chờ dữ liệu máy chủ"));
        requestsList.removeAllViews();
        DocumentSnapshot previous = null;
        for (DocumentSnapshot doc : requests)
            if (doc.getId().equals(selectedId)) { previous = doc; break; }
        selected = previous;
        // Select the sole matching request automatically after scanning the card.
        if (selected == null && requests.size() == 1) {
            selected = requests.get(0);
            selectedId = selected.getId();
        }
        for (DocumentSnapshot doc : requests) {
            View row = activity.getLayoutInflater().inflate(
                    R.layout.item_admin_counter_online_loan, requestsList, false);
            requestsList.addView(row);
            String title = doc.getString("bookTitle");
            ((TextView) row.findViewById(R.id.counterOnlineBookTitle)).setText(
                    title == null || title.isEmpty() ? "Đầu sách chưa đặt tên" : title);
            Long days = doc.getLong("loanDays");
            String state = doc.getString("status");
            ((TextView) row.findViewById(R.id.counterOnlineBookDetails)).setText(
                    "Mã đầu sách: " + value(doc, "bookTitleId")
                    + " · Thời hạn yêu cầu: " + (days == null ? "—" : days) + " ngày"
                    + "\nCuốn đã được giữ/giao (ID): " + value(doc, "copyId"));
            ((TextView) row.findViewById(R.id.counterOnlineBookStatus)).setText(
                    OnlineLoanRequestRepository.APPROVED.equals(state)
                            ? "Đã duyệt · Chờ lấy · Nhận trước: "
                                    + time(doc.getTimestamp("holdUntil"))
                            : "Đã giao · Hạn trả: " + time(doc.getTimestamp("dueAt")));
            Button select = row.findViewById(R.id.counterOnlineSelect);
            boolean chosen = doc.getId().equals(selectedId);
            select.setText(chosen ? "Đang chọn yêu cầu này"
                    : OnlineLoanRequestRepository.APPROVED.equals(state)
                        ? "Chọn để giao sách" : "Chọn để trả sách");
            select.setSelected(chosen);
            select.setOnClickListener(v -> choose(doc.getId()));
        }
        if (requests.isEmpty()) {
            selected = null;
            selectedId = null;
            blockActions();
            setMessage("Độc giả chưa có yêu cầu Online đã duyệt hoặc sách Online đang mượn.");
            return;
        }
        checkCopyAgainstSelected();
        if (copyBarcode.length() > 0) scheduleCopyLookup();
    }

    private static String value(DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "—" : value;
    }

    private static String time(Timestamp stamp) {
        return stamp == null ? "Chưa cập nhật"
                : new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                .format(stamp.toDate());
    }

    private static String error(Exception error) {
        return error == null || error.getMessage() == null
                ? "Kiểm tra mạng hoặc quyền Firestore." : error.getMessage();
    }

    private void choose(String id) {
        if (closed) return;
        selectedId = id;
        selected = null;
        for (DocumentSnapshot doc : requests)
            if (doc.getId().equals(id)) { selected = doc; break; }
        freshCopy = false;
        matchedCopy = null;
        blockActions();
        if (selected == null) return;
        setMessage("Đã chọn: " + value(selected, "bookTitle")
                + ". Quét đúng cuốn sách vật lý đã được giữ.");
        if (copyBarcode.length() > 0) scheduleCopyLookup();
        // Update the selected marker without refetching data.
        for (int i = 0; i < requestsList.getChildCount(); i++) {
            Button select = requestsList.getChildAt(i).findViewById(R.id.counterOnlineSelect);
            if (select != null) {
                boolean selectedHere = requests.get(i).getId().equals(id);
                select.setSelected(selectedHere);
                select.setText(selectedHere ? "Đang chọn yêu cầu này"
                        : "Chọn " + (OnlineLoanRequestRepository.APPROVED.equals(
                            requests.get(i).getString("status")) ? "để giao" : "để trả"));
            }
        }
    }

    private void scheduleCopyLookup() {
        if (closed) return;
        if (copyLookup != null) handler.removeCallbacks(copyLookup);
        matchedCopy = null;
        freshCopy = false;
        blockActions();
        String code = copyBarcode.getText().toString().trim();
        if (code.isEmpty()) {
            setMessage("Chọn yêu cầu rồi quét mã vạch của cuốn sách.");
            return;
        }
        if (readerUid == null || !freshRequests) {
            setMessage("Đã nhận mã cuốn. Đang chờ xác minh thẻ và các đơn đã duyệt.");
            return;
        }
        String uid = readerUid;
        final long token = generation;
        setMessage("Đang đối chiếu mã cuốn với đầu sách đã yêu cầu…");
        copyLookup = () -> firestore.collection("BookCopies")
                .whereEqualTo("barcode", code).get(Source.SERVER)
                .addOnCompleteListener(activity, result -> {
                    if (!live(token) || !uid.equals(readerUid)
                            || !code.equals(copyBarcode.getText().toString().trim())) return;
                    if (!result.isSuccessful()) {
                        setMessage("Không tra cứu được mã cuốn: " + error(result.getException()));
                        return;
                    }
                    List<DocumentSnapshot> copies = result.getResult().getDocuments();
                    if (copies.size() != 1) {
                        setMessage(copies.isEmpty()
                                ? "Mã cuốn không tồn tại trong BookCopies."
                                : "Barcode cuốn bị trùng. Cần đối soát kho trước khi giao.");
                        return;
                    }
                    matchedCopy = copies.get(0);
                    freshCopy = true;
                    if (selected == null) {
                        // With several approved loans, infer only when the assigned
                        // BookCopy id AND BookTitle id both match exactly.
                        for (DocumentSnapshot doc : requests)
                            if (matchedCopy.getId().equals(doc.getString("copyId"))
                                    && matchedCopy.getString("bookTitleId") != null
                                    && matchedCopy.getString("bookTitleId").equals(
                                            doc.getString("bookTitleId"))) {
                                choose(doc.getId());
                                matchedCopy = copies.get(0);
                                freshCopy = true;
                                break;
                            }
                    }
                    checkCopyAgainstSelected();
                });
        handler.postDelayed(copyLookup, 300L);
    }

    private void checkCopyAgainstSelected() {
        blockActions();
        if (processing || readerUid == null || !freshRequests) return;
        if (selected == null) {
            setMessage("Chọn một yêu cầu đã duyệt trong danh sách.");
            return;
        }
        if (copyBarcode.length() == 0) {
            setMessage("Yêu cầu: " + value(selected, "bookTitle")
                    + ". Quét mã cuốn đã được Admin giữ để giao/trả.");
            return;
        }
        if (!freshCopy || matchedCopy == null) {
            setMessage("Đang xác minh mã cuốn trên Firestore…");
            return;
        }
        OnlineBorrowCopyMatch.Result match = OnlineBorrowCopyMatch.compare(
                selected.getString("bookTitleId"), selected.getString("copyId"),
                matchedCopy.getString("bookTitleId"), matchedCopy.getId());
        if (match == OnlineBorrowCopyMatch.Result.INVALID_DATA) {
            setMessage("Yêu cầu hoặc mã cuốn thiếu dữ liệu. Cần đối soát kho.");
            return;
        }
        if (match == OnlineBorrowCopyMatch.Result.WRONG_TITLE) {
            setMessage("SAI ĐẦU SÁCH: Cuốn đã quét không thuộc đầu sách “"
                    + value(selected, "bookTitle") + "”. Không thể giao/trả.");
            return;
        }
        if (match == OnlineBorrowCopyMatch.Result.WRONG_COPY) {
            setMessage("ĐÚNG ĐẦU SÁCH, SAI CUỐN: Đây không phải bản sao đã được"
                    + " giữ cho yêu cầu này. Hãy quét đúng cuốn đã duyệt.");
            return;
        }
        if (Boolean.TRUE.equals(matchedCopy.getBoolean("isHidden"))) {
            setMessage("Cuốn sách đã bị ẩn. Cần đối soát kho.");
            return;
        }
        String state = selected.getString("status");
        if (OnlineLoanRequestRepository.APPROVED.equals(state)) {
            Timestamp deadline = selected.getTimestamp("holdUntil");
            if (deadline == null || !deadline.toDate().after(new Date())) {
                setMessage("Đơn đã quá 24 giờ giữ sách. Không thể giao.");
                return;
            }
            if (!"ChoLay".equals(matchedCopy.getString("copyStatus"))
                    || !readerUid.equals(matchedCopy.getString("priorityUserId"))) {
                setMessage("Cuốn chưa được giữ cho đúng độc giả, hoặc trạng thái không phải ChoLay.");
                return;
            }
            handover.setEnabled(true);
            setMessage("Đã đối chiếu đúng đầu sách, đúng cuốn được giữ và đúng độc giả."
                    + " Có thể xác nhận giao · " + value(selected, "bookTitle") + ".");
        } else if (OnlineLoanRequestRepository.COLLECTED.equals(state)) {
            if (!"DaMuon".equals(matchedCopy.getString("copyStatus"))) {
                setMessage("Cuốn không ở trạng thái DaMuon. Không thể nhận trả Online.");
                return;
            }
            returnBook.setEnabled(true);
            setMessage("Đã đối chiếu đúng cuốn đang mượn của độc giả."
                    + " Có thể xác nhận nhận trả sách.");
        }
    }

    private void confirm(boolean returning) {
        if (closed || processing || !activity.isSessionReady()
                || selected == null || matchedCopy == null || !freshRequests || !freshCopy)
            return;
        if (returning ? !returnBook.isEnabled() : !handover.isEnabled()) return;
        final long operationToken = generation;
        final String requestId = selected.getId();
        final String cardCode = readerBarcode.getText().toString().trim();
        final String copyCode = copyBarcode.getText().toString().trim();
        final String adminUid = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getUid();
        if (adminUid == null || !cardCode.equals(verifiedCardCode)
                || !copyCode.equals(matchedCopy.getString("barcode"))) return;

        new AlertDialog.Builder(activity)
                .setTitle(returning ? "Nhận trả sách Online" : "Giao sách Online")
                .setMessage("Độc giả: " + verifiedCardCode
                        + "\nĐầu sách: " + value(selected, "bookTitle")
                        + "\nCuốn: " + matchedCopy.getId()
                        + "\nSố ngày mượn: " + selected.getLong("loanDays")
                        + "\nĐã đối chiếu đúng mã vạch thực tế?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    if (closed || !activity.isSessionReady()) return;
                    processing = true;
                    blockActions();
                    setMessage("Đang ghi nhận giao dịch lên Firestore…");
                    com.google.android.gms.tasks.Task<Void> task = returning
                            ? repository.returnOnline(requestId, adminUid, cardCode, copyCode)
                            : repository.collect(requestId, adminUid, cardCode, copyCode);
                    task.addOnCompleteListener(activity, completed -> {
                        processing = false;
                        // Never overwrite the fields for a different Reader if the
                        // counter switched cards while the server was processing.
                        if (!live(operationToken)) return;
                        if (completed.isSuccessful()) {
                            copyBarcode.setText("");
                            selected = null;
                            selectedId = null;
                            matchedCopy = null;
                            freshCopy = false;
                            setMessage(returning
                                    ? "Đã ghi nhận trả sách Online. Tồn kho được cập nhật."
                                    : "Đã giao sách Online. Hạn trả tính từ thời điểm giao.");
                            Toast.makeText(activity, returning
                                    ? "Đã nhận trả sách Online." : "Đã giao sách Online.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            setMessage("Không thể hoàn tất: " + error(completed.getException()));
                            checkCopyAgainstSelected();
                        }
                    });
                }).show();
    }

    @Override public void close() {
        closed = true;
        generation++;
        if (readerLookup != null) handler.removeCallbacks(readerLookup);
        if (copyLookup != null) handler.removeCallbacks(copyLookup);
        if (loansListener != null) { loansListener.remove(); loansListener = null; }
        requests.clear();
    }
}
