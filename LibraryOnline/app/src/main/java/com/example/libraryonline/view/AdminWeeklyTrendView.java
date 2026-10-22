package com.example.libraryonline.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;

import java.util.Arrays;
import java.util.Locale;

/** Renders the seven weekdays from real checkout dates in the local SQLite cache. */
public final class AdminWeeklyTrendView extends View {
    private static final String[] DAYS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int primary, muted, grid;
    private int[] counts = new int[7];
    private boolean loaded;

    public AdminWeeklyTrendView(Context context) { this(context, null); }

    public AdminWeeklyTrendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        primary = ContextCompat.getColor(context, R.color.admin_screen_primary);
        muted = ContextCompat.getColor(context, R.color.admin_screen_muted);
        grid = ContextCompat.getColor(context, R.color.admin_screen_border);
        setContentDescription("Biểu đồ mượn trong tuần, đang tải dữ liệu trên thiết bị.");
    }

    public void setCounts(int[] values) {
        if (values == null || values.length != 7)
            throw new IllegalArgumentException("Seven daily values required");
        counts = Arrays.copyOf(values, values.length);
        int total = 0;
        for (int n : counts) total += n;
        loaded = true;
        setContentDescription(String.format(Locale.getDefault(),
                "Biểu đồ tuần, tổng %d lượt mượn đã ghi nhận trên thiết bị", total));
        invalidate();
    }

    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        float left = dp(15), right = width - dp(15);
        float top = dp(16), bottom = height - dp(39);

        paint.setColor(grid);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        for (int i = 0; i < 4; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(muted);
        paint.setTextSize(dp(12));
        for (int i = 0; i < 7; i++) {
            float x = left + (right - left) * i / 6f;
            canvas.drawText(DAYS[i], x, height - dp(12), paint);
        }

        if (!loaded) {
            canvas.drawText("Đang tải lượt mượn...", width / 2f, (top + bottom) / 2f, paint);
            return;
        }
        int peak = 1, total = 0;
        for (int count : counts) {
            peak = Math.max(peak, count);
            total += count;
        }
        if (total == 0) {
            paint.setTextSize(dp(12));
            canvas.drawText("Chưa có lượt mượn trong tuần trên thiết bị",
                    width / 2f, (top + bottom) / 2f, paint);
            return;
        }
        Path line = new Path();
        for (int i = 0; i < 7; i++) {
            float x = left + (right - left) * i / 6f;
            float y = bottom - (counts[i] / (float) peak) * (bottom - top);
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        paint.setColor(primary);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.4f));
        canvas.drawPath(line, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 7; i++) {
            float x = left + (right - left) * i / 6f;
            float y = bottom - counts[i] / (float) peak * (bottom - top);
            canvas.drawCircle(x, y, dp(3.5f), paint);
        }
    }
}
