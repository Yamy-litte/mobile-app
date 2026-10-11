
package com.example.mylibrary.dao;

import com.example.mylibrary.model.Customer;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthDAO {

    private final FirebaseFirestore db;

    public AuthDAO() {
        db = FirebaseFirestore.getInstance();
    }

    // Lưu thông tin khách hàng vào collection customers
    public void saveCustomer(Customer customer,
                             OnCustomerSaveListener listener) {
        if (customer == null || customer.getId() == null
                || customer.getId().isEmpty()) {
            listener.onFailure("Thông tin khách hàng không hợp lệ.");
            return;
        }

        db.collection("customers")
                .document(customer.getId())
                .set(customer)
                .addOnSuccessListener(unused -> {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onFailure(e.getMessage());
                    }
                });
    }

    // Callback thông báo kết quả lưu dữ liệu
    public interface OnCustomerSaveListener {
        void onSuccess();
        void onFailure(String error);
    }
}
