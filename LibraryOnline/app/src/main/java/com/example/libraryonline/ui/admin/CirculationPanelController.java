package com.example.libraryonline.ui.admin;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.repository.CirculationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Binds the manual barcode fallback to the transactional local circulation repository. */
final class CirculationPanelController implements AutoCloseable {
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CirculationRepository repository;
    private final EditText readerBarcode;
    private final EditText copyBarcode;
    private final EditText loanDays;
    private final EditText penaltyId;
    private final Button collectFineButton;
    private final TextView result;
    private final Button borrowButton;
    private final Button returnButton;
    private final CounterOnlineLoanController online;
    private final View readerPanel, copyPanel, onlinePanel, onlineActions, directPanel, penaltyPanel;
    private final Button modeOnline, modeDirect, modePenalty;

    CirculationPanelController(Activity activity) {
        this.activity = activity;
        repository = new CirculationRepository(activity);
        readerPanel = activity.findViewById(R.id.circulationReaderPanel);
        copyPanel = activity.findViewById(R.id.circulationCopyPanel);
        onlinePanel = activity.findViewById(R.id.circulationOnlinePanel);
        onlineActions = activity.findViewById(R.id.circulationOnlineActions);
        directPanel = activity.findViewById(R.id.circulationDirectPanel);
        penaltyPanel = activity.findViewById(R.id.circulationPenaltyPanel);
        modeOnline = activity.findViewById(R.id.circulationModeOnline);
        modeDirect = activity.findViewById(R.id.circulationModeDirect);
        modePenalty = activity.findViewById(R.id.circulationModePenalty);
        modeOnline.setOnClickListener(v -> showMode("online"));
        modeDirect.setOnClickListener(v -> showMode("direct"));
        modePenalty.setOnClickListener(v -> showMode("penalty"));
        readerBarcode = activity.findViewById(R.id.inputCirculationReaderBarcode);
        copyBarcode = activity.findViewById(R.id.inputCirculationCopyBarcode);
        loanDays = activity.findViewById(R.id.inputCirculationLoanDays);
        penaltyId = activity.findViewById(R.id.inputCirculationPenaltyId);
        collectFineButton = activity.findViewById(R.id.buttonCirculationCollectFine);
        online = activity instanceof AdminMainActivity
                ? new CounterOnlineLoanController((AdminMainActivity) activity,
                        readerBarcode, copyBarcode)
                : null;
        result = activity.findViewById(R.id.textCirculationResult);
        borrowButton = activity.findViewById(R.id.buttonCirculationBorrow);
        returnButton = activity.findViewById(R.id.buttonCirculationReturn);
        activity.findViewById(R.id.scanCirculationReaderBarcode)
                .setOnClickListener(v -> {
                    if (activity instanceof AdminMainActivity)
                        ((AdminMainActivity) activity).scanBarcodeInto(
                                R.id.inputCirculationReaderBarcode, null, "Quét mã thẻ độc giả");
                });
        activity.findViewById(R.id.scanCirculationCopyBarcode)
                .setOnClickListener(v -> {
                    if (activity instanceof AdminMainActivity)
                        ((AdminMainActivity) activity).scanBarcodeInto(
                                R.id.inputCirculationCopyBarcode, null, "Quét mã cuốn sách");
                });
        borrowButton.setOnClickListener(v -> borrow());
        returnButton.setOnClickListener(v -> returnCopy());
        collectFineButton.setOnClickListener(v -> collectFine());
        showMode(activity instanceof AdminMainActivity
                ? ((AdminMainActivity) activity).circulationMode() : "online");
    }

    private void showMode(String mode) {
        if (!"direct".equals(mode) && !"penalty".equals(mode)) mode = "online";
        boolean onlineMode = "online".equals(mode);
        boolean directMode = "direct".equals(mode);
        boolean fineMode = "penalty".equals(mode);
        modeOnline.setSelected(onlineMode);
        modeDirect.setSelected(directMode);
        modePenalty.setSelected(fineMode);
        readerPanel.setVisibility(fineMode ? View.GONE : View.VISIBLE);
        copyPanel.setVisibility(fineMode ? View.GONE : View.VISIBLE);
        onlinePanel.setVisibility(onlineMode ? View.VISIBLE : View.GONE);
        onlineActions.setVisibility(onlineMode ? View.VISIBLE : View.GONE);
        directPanel.setVisibility(directMode ? View.VISIBLE : View.GONE);
        penaltyPanel.setVisibility(fineMode ? View.VISIBLE : View.GONE);
        if (activity instanceof AdminMainActivity)
            ((AdminMainActivity) activity).setCirculationMode(mode);
    }

    private void borrow() {
        if (online != null && online.hasSelection()) {
            showError("Đã chọn đơn mượn Online. Hãy đối chiếu đúng cuốn và dùng nút Xác nhận giao sách Online.");
            return;
        }
        String reader = readerBarcode.getText().toString().trim();
        String copy = copyBarcode.getText().toString().trim();
        int days;
        try {
            days = Integer.parseInt(loanDays.getText().toString().trim());
        } catch (NumberFormatException ex) {
            showError("Nhập số ngày mượn hợp lệ.");
            return;
        }
        if (reader.isEmpty() || copy.isEmpty()) {
            showError("Cần nhập cả mã thẻ độc giả và mã cuốn sách.");
            return;
        }
        String uid = currentAdminUid();
        if (uid == null) {
            showError("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        runOperation(() -> repository.borrow(reader, copy, days, uid).message);
    }

    private void returnCopy() {
        if (online != null && online.hasSelection()) {
            showError("Đã chọn phiếu mượn Online. Hãy dùng nút Xác nhận nhận trả sách Online.");
            return;
        }
        String reader = readerBarcode.getText().toString().trim();
        String copy = copyBarcode.getText().toString().trim();
        if (reader.isEmpty() || copy.isEmpty()) {
            showError("Cần nhập cả mã thẻ độc giả và mã cuốn sách.");
            return;
        }
        String uid = currentAdminUid();
        if (uid == null) {
            showError("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        runOperation(() -> {
            CirculationRepository.OperationResult operation = repository.returnCopy(reader, copy, uid);
            if (operation.relatedId != null) activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    penaltyId.setText(operation.relatedId);
                    showMode("penalty");
                }
            });
            return operation.message;
        });
    }

    private void collectFine() {
        String id = penaltyId.getText().toString().trim();
        if (id.isEmpty()) {
            showError("Nhập mã khoản phạt cần thu.");
            return;
        }
        String uid = currentAdminUid();
        if (uid == null) {
            showError("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        runOperation(() -> repository.payPenalty(id, uid).message);
    }

    private String currentAdminUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private void runOperation(Operation operation) {
        borrowButton.setEnabled(false);
        returnButton.setEnabled(false);
        collectFineButton.setEnabled(false);
        worker.execute(() -> {
            String message;
            boolean failed = false;
            try {
                message = operation.run();
            } catch (RuntimeException ex) {
                failed = true;
                message = ex.getMessage() == null ? "Không thể thực hiện giao dịch." : ex.getMessage();
            }
            final String output = message;
            final boolean error = failed;
            activity.runOnUiThread(() -> {
                borrowButton.setEnabled(true);
                returnButton.setEnabled(true);
                collectFineButton.setEnabled(true);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (activity instanceof com.example.libraryonline.ui.auth.SessionActivity
                        && !((com.example.libraryonline.ui.auth.SessionActivity) activity).isSessionReady()) return;
                result.setText(output);
                Toast.makeText(activity, output, Toast.LENGTH_LONG).show();
                if (!error && activity instanceof AdminMainActivity)
                    ((AdminMainActivity) activity).refreshDashboardAfterCirculation();
            });
        });
    }

    private void showError(String message) {
        result.setText(message);
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    @Override public void close() {
        if (online != null) online.close();
        worker.shutdown();
        Thread closer = new Thread(() -> {
            try {
                if (!worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                    worker.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                worker.shutdownNow();
            } finally {
                repository.close();
            }
        }, "circulation-db-close");
        closer.start();
    }

    private interface Operation { String run(); }
}
