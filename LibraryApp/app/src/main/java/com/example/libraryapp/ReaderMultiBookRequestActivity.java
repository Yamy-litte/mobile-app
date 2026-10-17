package com.example.libraryapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.model.Book;
import com.example.libraryapp.service.CloudLibraryRepository;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReaderMultiBookRequestActivity extends AppCompatActivity {
    private final List<Book> books = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private CloudLibraryRepository cloud;
    private BookDao bookDao;
    private ArrayAdapter<String> adapter;
    private Button pickupButton, dueButton, submitButton;
    private TextView selectedText;
    private long pickupAt;
    private long dueAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new FirebaseAuthManager().isLoggedIn()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_reader_multi_book_request);
        cloud = new CloudLibraryRepository();
        pickupButton = findViewById(R.id.btnPickupDate);
        dueButton = findViewById(R.id.btnDueDate);
        submitButton = findViewById(R.id.btnSubmitMultiBookRequest);
        selectedText = findViewById(R.id.textSelectedBooksCount);
        ListView list = findViewById(R.id.listMultiBookSelection);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        pickupAt = startOfDay(calendar);
        calendar.add(Calendar.DAY_OF_MONTH, 13);
        dueAt = startOfDay(calendar);
        updateDateLabels();

        bookDao = new BookDao(this);
        books.addAll(bookDao.getAll());
        List<String> labels = new ArrayList<>();
        for (Book book : books) {
            labels.add(book.getTitle() + "  •  " + book.getAuthor()
                    + "\nCòn " + book.getAvailableQuantity() + "/" + book.getTotalQuantity() + " cuốn");
        }
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels);
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        for (int i = 0; i < books.size(); i++) {
            if (!books.get(i).isActive() || books.get(i).getAvailableQuantity() <= 0) {
                list.setItemChecked(i, false);
                list.getChildAt(i);
            }
        }
        list.setOnItemClickListener((parent, view, position, id) -> {
            Book book = books.get(position);
            if (!book.isActive() || book.getAvailableQuantity() <= 0) {
                list.setItemChecked(position, false);
                selectedPositions.remove(position);
                Toast.makeText(this, "Sách đã hết tồn kho. Hãy dùng chức năng Đặt trước ở trang chi tiết sách.", Toast.LENGTH_LONG).show();
            } else if (list.isItemChecked(position)) {
                selectedPositions.add(position);
            } else {
                selectedPositions.remove(position);
            }
            updateSelectedCount();
        });

        pickupButton.setOnClickListener(v -> chooseDate(true));
        dueButton.setOnClickListener(v -> chooseDate(false));
        findViewById(R.id.btnCancelMultiBookRequest).setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> submitRequest());
        updateSelectedCount();
    }

    private void chooseDate(boolean pickup) {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(pickup ? pickupAt : dueAt);
        Calendar minimum = Calendar.getInstance();
        if (!pickup) minimum.setTimeInMillis(pickupAt);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day, 0, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            if (pickup) {
                pickupAt = chosen.getTimeInMillis();
                if (dueAt <= pickupAt) {
                    Calendar next = (Calendar) chosen.clone();
                    next.add(Calendar.DAY_OF_MONTH, 14);
                    dueAt = next.getTimeInMillis();
                }
            } else {
                dueAt = chosen.getTimeInMillis();
            }
            updateDateLabels();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(pickup ? System.currentTimeMillis() : pickupAt + 86400000L);
        dialog.show();
    }

    private long startOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void updateDateLabels() {
        pickupButton.setText("Ngày nhận: " + android.text.format.DateFormat.format("dd/MM/yyyy", pickupAt));
        dueButton.setText("Ngày trả: " + android.text.format.DateFormat.format("dd/MM/yyyy", dueAt));
    }

    private void updateSelectedCount() {
        selectedText.setText("Đã chọn " + selectedPositions.size() + " cuốn sách");
        submitButton.setEnabled(!selectedPositions.isEmpty() && dueAt > pickupAt);
    }

    private void submitRequest() {
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "Hãy chọn ít nhất một cuốn sách.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (Integer position : selectedPositions) {
            Book book = books.get(position);
            ids.add(String.valueOf(book.getId()));
            titles.add(book.getTitle());
        }
        submitButton.setEnabled(false);
        cloud.submitMultiBookReservation(ids, titles, pickupAt, dueAt)
                .addOnSuccessListener(reference -> {
                    Toast.makeText(this, "Đã gửi yêu cầu mượn " + titles.size()
                            + " cuốn. Vui lòng chờ Admin duyệt.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    submitButton.setEnabled(true);
                    Toast.makeText(this, "Không gửi được yêu cầu: "
                            + (error.getMessage() == null ? "Lỗi kết nối" : error.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override protected void onDestroy() {
        if (bookDao != null) bookDao.close();
        super.onDestroy();
    }
}
