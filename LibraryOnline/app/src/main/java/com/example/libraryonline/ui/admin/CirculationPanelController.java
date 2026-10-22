package com.example.libraryonline.ui.admin;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.repository.CirculationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Binds the manual barcode fallback to the transactional local circulation repository. */
final class CirculationPanelController implements AutoCloseable {
    private final Activity activity;
    private final CirculationRepository repository;
    private final EditText readerBarcode;
    private final EditText copyBarcode;
    private final EditText loanDays;
    private final TextView result;
    private final Button borrowButton;
    private final Button returnButton;

    CirculationPanelController(Activity activity) {
        this.activity = activity;
        repository = new CirculationRepository(activity);
        readerBarcode = activity.findViewById(R.id.inputCirculationReaderBarcode);
        copyBarcode = activity.findViewById(R.id.inputCirculationCopyBarcode);
        loanDays = activity.findViewById(R.id.inputCirculationLoanDays);
        result = activity.findViewById(R.id.textCirculationResult);
        borrowButton = activity.findViewById(R.id.buttonCirculationBorrow);
        returnButton = activity.findViewById(R.id.buttonCirculationReturn);
        borrowButton.setOnClickListener(v -> borrow());
        returnButton.setOnClickListener(v -> returnCopy());
    }

    private void borrow() {
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
        runOperation(() -> repository.returnCopy(reader, copy, uid).message);
    }

    private String currentAdminUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private void runOperation(Operation operation) {
        borrowButton.setEnabled(false);
        returnButton.setEnabled(false);
        try {
            String message = operation.run();
            result.setText(message);
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } catch (RuntimeException ex) {
            showError(ex.getMessage() == null ? "Không thể thực hiện giao dịch." : ex.getMessage());
        } finally {
            borrowButton.setEnabled(true);
            returnButton.setEnabled(true);
        }
    }

    private void showError(String message) {
        result.setText(message);
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    @Override public void close() { repository.close(); }

    private interface Operation { String run(); }
}
