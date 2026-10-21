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
    private String draftCategoryId;
    private final int[] fields = {
        R.id.formTitle,
        R.id.formAuthor,
        R.id.formPublisher,
        R.id.formYear,
        R.id.formIsbn,
        R.id.formCover,
        R.id.formDescription
    };

    private static final String[] DRAFT_KEYS = {
        "title", "author", "publisher", "year", "isbn", "coverUrl", "description"
    };

    public InventoryForm(Context context, List<CategoryDao.Category> categories, BookTitle book) {
        this.categories = new ArrayList<>(categories);
        draftCategoryId = book == null ? null : book.categoryId;
        view = LayoutInflater.from(context).inflate(R.layout.view_admin_inventory_editor, null);
        List<String> names = new ArrayList<>();
        for (CategoryDao.Category c : categories) names.add(c.name);
        Spinner spinner = view.findViewById(R.id.formCategory);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        view.findViewById(R.id.editorSummary)
                .setVisibility(book == null ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.editorIdentity)
                .setVisibility(book == null ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.editorInitialStock)
                .setVisibility(book == null ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.editorManageCopies)
                .setVisibility(book == null ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.editorBookId))
                .setText(book == null ? "Được tạo sau khi lưu" : book.bookTitleId);
        ((TextView) view.findViewById(R.id.editorSave))
                .setText(book == null ? R.string.inventory_ui_save_create : R.string.stock_save);
        view.findViewById(R.id.editorCoverPreview).setOnClickListener(v -> previewCover());
        if (book != null) InventoryViews.bindSummary(view, book);
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
        for (int i = 0; i < fields.length; i++) draft.putString(DRAFT_KEYS[i], value(fields[i]));
        int position = ((Spinner) view.findViewById(R.id.formCategory)).getSelectedItemPosition();
        if (position >= 0 && position < categories.size())
            draftCategoryId = categories.get(position).categoryId;
        draft.putString("categoryId", draftCategoryId);
        return draft;
    }

    public void restoreDraft(android.os.Bundle draft) {
        if (draft == null) return;
        draftCategoryId = draft.getString("categoryId");
        for (int i = 0; i < fields.length; i++)
            ((EditText) view.findViewById(fields[i]))
                    .setText(
                            draft.getString(
                                    DRAFT_KEYS[i],
                                    draft.getString(Integer.toString(fields[i]), "")));
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

    private void previewCover() {
        String url = value(R.id.formCover);
        try {
            if (!url.isEmpty()) {
                java.net.URI parsed = new java.net.URI(url);
                if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null)
                    throw new IllegalArgumentException("Ảnh bìa phải là địa chỉ HTTPS hợp lệ.");
            }
            view.findViewById(R.id.editorSummary).setVisibility(View.VISIBLE);
            BookTitle preview = new BookTitle();
            preview.title = value(R.id.formTitle);
            preview.author = value(R.id.formAuthor);
            preview.coverUrl = url;
            InventoryViews.bindSummary(view, preview);
            view.findViewById(R.id.summaryCounts).setVisibility(View.GONE);
            ((EditText) view.findViewById(R.id.formCover)).setError(null);
        } catch (Exception error) {
            ((EditText) view.findViewById(R.id.formCover)).setError("Nhập URL ảnh HTTPS hợp lệ.");
        }
    }

    public void setEnabled(boolean enabled) {
        for (int id : fields) view.findViewById(id).setEnabled(enabled);
        view.findViewById(R.id.formCategory).setEnabled(enabled);
    }
}
