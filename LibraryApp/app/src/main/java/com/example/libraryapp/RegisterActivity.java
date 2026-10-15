package com.example.libraryapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.model.Customer;

public class RegisterActivity extends AppCompatActivity {
    private static final int CREAM=Color.rgb(255,249,240), TEAL=Color.rgb(28,96,88), INK=Color.rgb(36,57,53);
    private EditText nameInput,emailInput,phoneInput,passwordInput,confirmInput;
    private CheckBox terms;
    private FirebaseAuthManager auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth=new FirebaseAuthManager();
        getWindow().getDecorView().setBackgroundColor(CREAM);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(22),dp(24),dp(22));

        TextView title=text("‹  Tạo tài khoản mới",24,INK,true);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));
        title.setOnClickListener(v->finish());

        nameInput=addField(root,"Họ và tên","Nhập họ và tên",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        emailInput=addField(root,"Email thư viện","Nhập email",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        phoneInput=addField(root,"Số điện thoại","Nhập số điện thoại",InputType.TYPE_CLASS_PHONE);
        passwordInput=addField(root,"Mật khẩu","Tối thiểu 6 ký tự",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmInput=addField(root,"Xác nhận mật khẩu","Nhập lại mật khẩu",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);

        terms=new CheckBox(this);
        terms.setText("Tôi đồng ý với Điều khoản sử dụng và Quy định mượn trả sách của thư viện MyLibrary");
        terms.setTextColor(0xFF78827F);
        terms.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
        terms.setPadding(0,dp(12),0,dp(12));
        root.addView(terms);

        android.widget.Space spacer=new android.widget.Space(this);
        root.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        Button submit=new Button(this);
        submit.setText("Đăng ký tài khoản"); submit.setAllCaps(false); submit.setTextColor(Color.WHITE);
        submit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL));
        root.addView(submit,new LinearLayout.LayoutParams(-1,dp(54)));
        submit.setOnClickListener(v->register());

        TextView login=text("Đã có tài khoản?  Đăng nhập",14,INK,false);
        login.setGravity(Gravity.CENTER); login.setPadding(0,dp(14),0,dp(8));
        root.addView(login); login.setOnClickListener(v->finish());
        setContentView(root);
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

    private EditText addField(LinearLayout root,String label,String hint,int type) {
        TextView l=text(label,15,INK,true); l.setPadding(0,dp(10),0,dp(5)); root.addView(l);
        EditText e=new EditText(this); e.setHint(hint); e.setTextSize(15); e.setSingleLine(true); e.setInputType(type);
        e.setPadding(dp(15),0,dp(15),0); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE5E8E5));
        root.addView(e,new LinearLayout.LayoutParams(-1,dp(48))); return e;
    }
    private TextView text(String s,int size,int color,boolean bold) { TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t; }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}