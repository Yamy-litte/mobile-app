package com.example.libraryapp;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.libraryapp.model.Book;
import java.util.ArrayList;
import java.util.List;

public class AdminBookInventoryAdapter extends BaseAdapter {
    public interface Actions {
        void onEdit(Book book);
        void onToggleActive(Book book);
    }
    private final Context context;
    private final Actions actions;
    private final List<Book> books = new ArrayList<>();

    public AdminBookInventoryAdapter(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
    }
    public void replace(List<Book> values) {
        books.clear();
        books.addAll(values);
        notifyDataSetChanged();
    }
    @Override public int getCount() { return books.size(); }
    @Override public Book getItem(int position) { return books.get(position); }
    @Override public long getItemId(int position) { return getItem(position).getId(); }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null
                ? LayoutInflater.from(context).inflate(R.layout.row_admin_book, parent, false)
                : convertView;
        Book book = getItem(position);
        ImageView cover = row.findViewById(R.id.adminBookCover);
        BookCoverLoader.load(book.getImageUrl(), cover);
        ((TextView) row.findViewById(R.id.adminBookTitle)).setText(safe(book.getTitle()));
        ((TextView) row.findViewById(R.id.adminBookAuthor)).setText(safe(book.getAuthor()));
        ((TextView) row.findViewById(R.id.adminBookIsbn)).setText("Ảnh bìa: " +
                (safe(book.getImageUrl()).isEmpty() ? "chưa cập nhật" : "đã cập nhật"));
        ((TextView) row.findViewById(R.id.adminBookStock)).setText(
                "Tổng: " + book.getTotalQuantity() + " | Còn: " + book.getAvailableQuantity()
                        + (book.isActive() ? "" : "  ·  Đã ẩn"));
        row.findViewById(R.id.adminBookEdit).setOnClickListener(v -> actions.onEdit(book));
        row.findViewById(R.id.adminBookDelete).setOnClickListener(v ->
                new AlertDialog.Builder(context)
                        .setTitle(book.isActive() ? "Ẩn sách khỏi Reader?" : "Hiện lại sách?")
                        .setMessage(book.isActive()
                                ? "Sách sẽ không còn xuất hiện trong danh mục độc giả. Dữ liệu và lịch sử vẫn được giữ."
                                : "Cho phép độc giả nhìn thấy lại cuốn sách này?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xác nhận", (dialog, which) -> actions.onToggleActive(book))
                        .show());
        return row;
    }
    private String safe(String value) { return value == null ? "" : value.trim(); }
}