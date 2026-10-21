package com.example.libraryonline.ui.admin.inventory;

import android.content.Context;
import android.view.*;
import android.widget.*;

import com.example.libraryonline.R;
import com.example.libraryonline.data.inventory.InventoryPolicy;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.data.model.BookTitle;

import java.util.*;

public final class InventoryForm {
    public final View view;
    private final List<CategoryDao.Category> categories;
    private final int[] fields = {
        R.id.formTitle,
        R.id.formAuthor,
        R.id.formPublisher,
        R.id.formYear,
        R.id.formIsbn,
        R.id.formCover,
        R.id.formDescription
    };

    public InventoryForm(Context context, List<CategoryDao.Category> categories, BookTitle book) {
        this.categories = new ArrayList<>(categories);
        view = LayoutInflater.from(context).inflate(R.layout.dialog_admin_inventory_title, null);
        List<String> names = new ArrayList<>();
        for (CategoryDao.Category c : categories) names.add(c.name);
        Spinner spinner = view.findViewById(R.id.formCategory);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (book != null) {
            String[] values = {
                book.title,
                book.author,
                book.publisher,
                book.publishYear == null ? "" : book.publishYear.toString(),
                book.isbn,
                book.coverUrl,
                book.description
            };
            for (int i = 0; i < fields.length; i++)
                ((EditText) view.findViewById(fields[i])).setText(values[i]);
            spinner.setSelection(-1);
            for (int i = 0; i < categories.size(); i++)
                if (categories.get(i).categoryId.equals(book.categoryId)) spinner.setSelection(i);
        }
    }

    public BookTitle read(String id) {
        BookTitle b = new BookTitle();
        b.bookTitleId = id;
        b.title = value(R.id.formTitle);
        b.author = value(R.id.formAuthor);
        int position = ((Spinner) view.findViewById(R.id.formCategory)).getSelectedItemPosition();
        if (position < 0 || position >= categories.size())
            throw new IllegalArgumentException(
                    "Chưa có thể loại hoạt động. Vui lòng kiểm tra Categories.");
        b.categoryId = categories.get(position).categoryId;
        b.category = categories.get(position).name;
        b.publisher = value(R.id.formPublisher);
        b.isbn = value(R.id.formIsbn);
        b.coverUrl = value(R.id.formCover);
        b.description = value(R.id.formDescription);
        try {
            b.publishYear =
                    value(R.id.formYear).isEmpty() ? null : Integer.valueOf(value(R.id.formYear));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Năm xuất bản không hợp lệ.");
        }
        InventoryPolicy.validateTitle(b);
        return b;
    }

    private String value(int id) {
        return ((EditText) view.findViewById(id)).getText().toString().trim();
    }

    public android.os.Bundle saveDraft() {
        android.os.Bundle draft = new android.os.Bundle();
        for (int id : fields) draft.putString(Integer.toString(id), value(id));
        int position = ((Spinner) view.findViewById(R.id.formCategory)).getSelectedItemPosition();
        if (position >= 0 && position < categories.size())
            draft.putString("categoryId", categories.get(position).categoryId);
        return draft;
    }

    public void restoreDraft(android.os.Bundle draft) {
        if (draft == null) return;
        for (int id : fields)
            ((EditText) view.findViewById(id)).setText(draft.getString(Integer.toString(id), ""));
        Spinner spinner = view.findViewById(R.id.formCategory);
        spinner.setSelection(-1);
        for (int i = 0; i < categories.size(); i++)
            if (categories.get(i).categoryId.equals(draft.getString("categoryId")))
                spinner.setSelection(i);
    }

    public void watchDraft(Runnable changed) {
        android.text.TextWatcher watcher =
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        changed.run();
                    }

                    public void afterTextChanged(android.text.Editable e) {}
                };
        for (int id : fields) ((EditText) view.findViewById(id)).addTextChangedListener(watcher);
        ((Spinner) view.findViewById(R.id.formCategory))
                .setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                                changed.run();
                            }

                            public void onNothingSelected(AdapterView<?> p) {
                                changed.run();
                            }
                        });
    }

    public void setEnabled(boolean enabled) {
        for (int id : fields) view.findViewById(id).setEnabled(enabled);
        view.findViewById(R.id.formCategory).setEnabled(enabled);
    }
}
