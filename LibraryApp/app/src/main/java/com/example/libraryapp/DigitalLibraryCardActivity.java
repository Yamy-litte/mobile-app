package com.example.libraryapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import java.util.EnumMap;
import java.util.Map;

public class DigitalLibraryCardActivity extends AppCompatActivity {
    public static final String EXTRA_NAME = "digital_card_name";
    public static final String EXTRA_EMAIL = "digital_card_email";
    public static final String EXTRA_CARD_NUMBER = "digital_card_number";
    public static final String EXTRA_ISSUE_DATE = "digital_card_issue_date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digital_library_card);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        String cardNumber = getIntent().getStringExtra(EXTRA_CARD_NUMBER);
        String issueDate = getIntent().getStringExtra(EXTRA_ISSUE_DATE);

        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            Toast.makeText(this, "Mã thẻ không hợp lệ.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.digitalCardName)).setText(name == null ? "" : name);
        ((TextView) findViewById(R.id.digitalCardEmail)).setText(email == null ? "" : email);
        ((TextView) findViewById(R.id.digitalCardNumber)).setText(cardNumber);
        ((TextView) findViewById(R.id.digitalCardIssueDate))
                .setText("Ngày cấp: " + (issueDate == null ? "" : issueDate));

        ImageView barcodeView = findViewById(R.id.digitalCardBarcode);
        try {
            barcodeView.setImageBitmap(createBarcode(cardNumber.trim()));
            barcodeView.setContentDescription("Barcode thẻ độc giả " + cardNumber);
        } catch (WriterException error) {
            Toast.makeText(this, "Không thể tạo barcode cho thẻ.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        findViewById(R.id.btnCloseDigitalCard).setOnClickListener(v -> finish());
    }

    private Bitmap createBarcode(String value) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // Keep clear white space at both ends so the scanner can detect Code 128.
        hints.put(EncodeHintType.MARGIN, 24);
        BitMatrix matrix = new MultiFormatWriter().encode(
                value, BarcodeFormat.CODE_128, 1800, 460, hints);
        Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }
}
