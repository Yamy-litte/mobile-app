package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.ReviewDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Review;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ReviewsActivity extends AppCompatActivity {
    private LinearLayout reviewList;
    private TextView emptyReviews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reviews);
        reviewList = findViewById(R.id.layoutReviews);
        emptyReviews = findViewById(R.id.textReviewsEmpty);
        findViewById(R.id.buttonReviewsBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReviews();
    }

    private void loadReviews() {
        ReviewDao dao = new ReviewDao(getApplicationContext());
        List<Review> reviews;
        try {
            reviews = dao.getAllReviews();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "Không thể tải đánh giá.", Toast.LENGTH_LONG).show();
            reviews = java.util.Collections.emptyList();
        } finally {
            dao.close();
        }

        reviewList.removeAllViews();
        NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        for (Review review : reviews) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(14, 14, 14, 14);
            card.setBackgroundColor(0xFFFFFFFF);

            TextView customer = new TextView(this);
            customer.setText(displayName(review.getCustomerEmail()) + "  •  Đơn #" + review.getBillId());
            customer.setTextColor(0xFF6F3E25);
            customer.setTextSize(16);
            customer.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView rating = new TextView(this);
            rating.setText(stars(review.getRating()) + "  " + review.getRating() + "/5");
            rating.setTextSize(16);
            rating.setTextColor(0xFFB7791F);

            TextView items = new TextView(this);
            items.setText("Món đã đặt: " + review.getMenuSummary()
                    + "\nTổng đơn: " + money.format(review.getOrderTotal()) + " VND");
            items.setTextColor(0xFF555555);
            items.setTextSize(14);

            TextView comment = new TextView(this);
            comment.setText("“" + review.getComment() + "”");
            comment.setTextColor(0xFF333333);
            comment.setTextSize(15);
            comment.setPadding(0, 8, 0, 6);

            TextView date = new TextView(this);
            date.setText(review.getCreatedAt());
            date.setTextColor(0xFF777777);
            date.setTextSize(12);

            card.addView(customer);
            card.addView(rating);
            card.addView(items);
            card.addView(comment);
            card.addView(date);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 12);
            reviewList.addView(card, params);
        }
        emptyReviews.setVisibility(reviews.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String displayName(String email) {
        if (email == null || email.trim().isEmpty()) return "Khách hàng";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String stars(int rating) {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i <= 5; i++) value.append(i <= rating ? "★" : "☆");
        return value.toString();
    }
}
