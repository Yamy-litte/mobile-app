package com.example.libraryapp;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.model.Customer;

public class RegisterActivity extends AppCompatActivity {
    private EditText nameInput, emailInput, phoneInput, passwordInput, confirmInput;
    private CheckBox terms;
    private FirebaseAuthManager auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        auth = new FirebaseAuthManager();
        nameInput = findViewById(R.id.editName);
        emailInput = findViewById(R.id.editEmail);
        phoneInput = findViewById(R.id.editPhone);
        passwordInput = findViewById(R.id.editPassword);
        confirmInput = findViewById(R.id.editConfirmPassword);
        terms = findViewById(R.id.checkTerms);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLogin).setOnClickListener(v -> finish());
        findViewById(R.id.btnRegister).setOnClickListener(v -> register());
    }

    private void register() {
        String name=nameInput.getText().toString().trim(), email=emailInput.getText().toString().trim();
        String phone=phoneInput.getText().toString().trim(), password=passwordInput.getText().toString();
        if(name.isEmpty()){nameInput.setError("Vui lòng nhập họ tên");return;}
        if(email.isEmpty()){emailInput.setError("Vui lòng nhập email");return;}
        if(password.length()<6){passwordInput.setError("Mật khẩu cần ít nhất 6 ký tự");return;}
        if(!password.equals(confirmInput.getText().toString())){confirmInput.setError("Mật khẩu xác nhận không khớp");return;}
        if(!terms.isChecked()){Toast.makeText(this,"Vui lòng đồng ý với điều khoản.",Toast.LENGTH_LONG).show();return;}

        auth.register(email,password).addOnSuccessListener(result -> {
            String uid=result.getUser().getUid();
            Customer customer=new Customer(0,uid,email,name,phone,null,"active",null,null);
            CustomerDao dao=new CustomerDao(this);
            long id=dao.insert(customer);
            dao.close();
            if(id<0) {
                auth.logout();
                Toast.makeText(this,"Tạo tài khoản Firebase thành công nhưng chưa lưu được hồ sơ. Hãy thử lại hoặc liên hệ quản trị viên.",Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this,"Đăng ký thành công. Vui lòng đăng nhập.",Toast.LENGTH_LONG).show();
            auth.logout();
            finish();
        }).addOnFailureListener(e->{
            String message=e.getMessage();
            if(message!=null && message.contains("already in use")) message="Email này đã được đăng ký.";
            Toast.makeText(this,message==null?"Không thể đăng ký tài khoản.":message,Toast.LENGTH_LONG).show();
        });
    }

}
