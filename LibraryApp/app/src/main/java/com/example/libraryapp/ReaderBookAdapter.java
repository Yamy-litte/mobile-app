package com.example.libraryapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.libraryapp.model.Book;
import java.util.List;

public class ReaderBookAdapter extends ArrayAdapter<Book> {
    public ReaderBookAdapter(Context context, List<Book> books) {
        super(context, 0, books);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = LayoutInflater.from(getContext()).inflate(R.layout.row_reader_book, parent, false);
        Book book = getItem(position);
        if (book != null) {
            ImageView cover = row.findViewById(R.id.imageBookCover);
            BookCoverLoader.load(book.getImageUrl(), cover);
            ((TextView) row.findViewById(R.id.textBookTitle)).setText(book.getTitle());
            ((TextView) row.findViewById(R.id.textBookAuthor)).setText(book.getAuthor());
            String category = categoryName(book.getCategoryId());
            String availability = book.getAvailableQuantity() > 0
                    ? "Còn " + book.getAvailableQuantity() + "/" + book.getTotalQuantity() + " cuốn"
                    : "Tạm hết sách";
            ((TextView) row.findViewById(R.id.textBookMeta)).setText(category + "  •  " + availability);
        }
        return row;
    }

    private String categoryName(int id) {
        switch (id) {
            case 1: return "Văn học";
            case 2: return "Khoa học";
            case 3: return "Công nghệ";
            case 4: return "Kỹ năng sống";
            case 5: return "Lịch sử";
            case 6: return "Thiếu nhi";
            default: return "Danh mục khác";
        }
    }
}
