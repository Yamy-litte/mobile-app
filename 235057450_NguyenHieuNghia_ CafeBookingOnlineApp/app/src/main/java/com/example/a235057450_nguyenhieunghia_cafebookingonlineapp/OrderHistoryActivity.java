package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter.OrderAdapter;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OrderHistoryActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private OrderAdapter adapter;
    private TextView textEmpty;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        ListView listOrders = findViewById(R.id.listOrders);
        textEmpty = findViewById(R.id.textOrdersEmpty);
        Button back = findViewById(R.id.buttonOrdersBack);
        adapter = new OrderAdapter(this);
        listOrders.setAdapter(adapter);
        listOrders.setEmptyView(textEmpty);

        back.setOnClickListener(v -> finish());
        listOrders.setOnItemClickListener((parent, view, position, id) -> {
            Bill bill = adapter.getItem(position);
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra(OrderDetailActivity.EXTRA_BILL_ID, bill.getBillId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || !firebaseUser.isEmailVerified()
                || firebaseUser.getEmail() == null) {
            Toast.makeText(this, "Vui lòng đăng nhập bằng email đã xác minh.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        textEmpty.setText("Đang tải lịch sử đơn hàng...");
        executor.execute(() -> {
            List<Bill> bills = Collections.emptyList();
            String error = null;
            UserDao userDao = new UserDao(getApplicationContext());
            BillDao billDao = new BillDao(getApplicationContext());
            try {
                User user = userDao.getByEmail(firebaseUser.getEmail());
                if (user == null) {
                    long id = userDao.saveProfile(firebaseUser.getEmail(), "Customer");
                    user = id > 0 ? userDao.getById((int) id) : null;
                }
                if (user == null || !"Customer".equals(user.getRole())) {
                    error = "Không tìm thấy hồ sơ khách hàng hợp lệ.";
                } else {
                    bills = billDao.getBillsByUserId(user.getUserId());
                }
            } catch (RuntimeException ex) {
                error = "Không thể tải lịch sử đơn hàng.";
            } finally {
                userDao.close();
                billDao.close();
            }

            List<Bill> result = bills;
            String resultError = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (resultError != null) {
                    textEmpty.setText(resultError);
                    Toast.makeText(this, resultError, Toast.LENGTH_LONG).show();
                } else {
                    textEmpty.setText("Bạn chưa có đơn hàng nào.");
                    adapter.setBills(result);
                    textEmpty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}