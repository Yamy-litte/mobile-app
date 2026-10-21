package com.example.libraryonline.ui.admin.inventory;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;

import com.example.libraryonline.R;
import com.example.libraryonline.data.inventory.*;
import com.example.libraryonline.data.local.dao.*;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.*;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.admin.AdminHomeView;
import com.example.libraryonline.ui.admin.AdminNavigation;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.*;
import java.util.concurrent.*;

public class InventoryActivity extends SessionActivity {
    private InventoryViewModel model;
    private DatabaseHelper helper;
    private BookCatalogueSyncManager sync;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<BookTitle> books = new ArrayList<>();
    private List<BookCopy> copies = new ArrayList<>();
    private List<CategoryDao.Category> categories = new ArrayList<>();
    private LinearLayout content;
    private TextView status;
    private EditText search;
    private String uid;
    private long generation;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_inventory);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.inventoryRoot);
        int padding = root.getPaddingLeft();
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(
                            padding + bars.left, bars.top, padding + bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
        helper = new DatabaseHelper(getApplicationContext());
        model = new ViewModelProvider(this).get(InventoryViewModel.class);
        model.restore(state);
        content = findViewById(R.id.inventoryContent);
        status = findViewById(R.id.inventoryStatus);
        search = findViewById(R.id.inventorySearch);
        BottomNavigationView navigation = findViewById(R.id.adminBottomNavigation);
        navigation.getMenu().findItem(R.id.navAdminInventory).setChecked(true);
        navigation.setOnItemSelectedListener(
                item -> {
                    if (item.getItemId() == R.id.navAdminInventory) return true;
                    if (!isSessionReady() || leaving) return false;
                    int tab = AdminHomeView.HOME;
                    if (item.getItemId() == R.id.navAdminRequests) tab = AdminHomeView.REQUESTS;
                    else if (item.getItemId() == R.id.navAdminAccount) tab = AdminHomeView.ACCOUNT;
                    else if (item.getItemId() == R.id.navAdminCirculation)
                        tab = AdminHomeView.CIRCULATION;
                    leaving = true;
                    AdminNavigation.returnToMain(this, tab);
                    return false;
                });
        navigation.setOnItemReselectedListener(
                item -> {
                    model.selectedId = null;
                    render();
                });
        findViewById(R.id.inventoryBack).setOnClickListener(v -> back());
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                back();
                            }
                        });
        findViewById(R.id.inventoryRefresh)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady()) startSync();
                        });
        findViewById(R.id.inventoryAdd)
                .setOnClickListener(
                        v -> {
                            if (canWrite()) showForm(null);
                        });
        findViewById(R.id.inventoryRetry)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady()) {
                                if (model.hasPending()) model.retry(uid);
                                else resumeDraft();
                            }
                        });
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        render();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        model.message.observe(
                this,
                message -> {
                    if (isSessionReady()) {
                        if (message != null && !message.isEmpty()) status.setText(message);
                        render();
                        if (!model.busy) loadLocal(generation);
                    }
                });
    }

    @Override
    protected SessionProfile.Role expectedRole() {
        return SessionProfile.Role.ADMIN;
    }

    @Override
    protected void onSessionAuthorized(SessionProfile profile) {
        uid = profile.uid;
        model.attach(getApplicationContext(), uid);
        startSync();
    }

    private boolean current(long token) {
        return token == generation && isSessionReady() && !isDestroyed();
    }

    private boolean canWrite() {
        return isSessionReady() && !model.journalError && !model.busy && !model.hasPending();
    }

    private void back() {
        if (model.selectedId != null) {
            model.selectedId = null;
            render();
        } else if (!leaving && isSessionReady()) {
            leaving = true;
            AdminNavigation.returnToMain(this, AdminHomeView.HOME);
        }
    }

    private void startSync() {
        stopSync();
        long token = generation;
        status.setText(model.hasPending() ? "Có thao tác đang chờ xác nhận." : "Đang tải kho…");
        loadLocal(token);
        sync = new BookCatalogueSyncManager(getApplicationContext());
        sync.start(
                new BookCatalogueSyncManager.SyncListener() {
                    public void onSyncCompleted(int changed) {
                        runOnUiThread(
                                () -> {
                                    if (current(token)) loadLocal(token);
                                });
                    }

                    public void onSyncFailed(String message) {
                        runOnUiThread(
                                () -> {
                                    if (current(token) && !model.busy)
                                        status.setText("Lỗi đồng bộ: " + message);
                                });
                    }

                    public void onCatalogueSnapshot(boolean cached, int count) {
                        runOnUiThread(
                                () -> {
                                    if (current(token) && !model.hasPending())
                                        status.setText(
                                                cached
                                                        ? "Dữ liệu đã lưu — cần mạng để thay đổi"
                                                                + " kho"
                                                        : "Đã nhận danh mục Firebase");
                                });
                    }
                });
    }

    private void loadLocal(long token) {
        if (!current(token)) return;
        worker.execute(
                () -> {
                    try {
                        List<BookTitle> b = new BookTitleDao(helper.getReadableDatabase()).getAll();
                        List<BookCopy> c = new BookCopyDao(helper.getReadableDatabase()).getAll();
                        List<CategoryDao.Category> g =
                                new CategoryDao(helper.getReadableDatabase()).getAll();
                        runOnUiThread(
                                () -> {
                                    if (current(token)) {
                                        books = b;
                                        copies = c;
                                        categories = g;
                                        render();
                                    }
                                });
                    } catch (Exception error) {
                        runOnUiThread(
                                () -> {
                                    if (current(token))
                                        status.setText(
                                                "Không đọc được kho SQLite: " + error.getMessage());
                                });
                    }
                });
    }

    private void label(String value) {
        TextView text =
                (TextView)
                        getLayoutInflater()
                                .inflate(R.layout.item_admin_inventory_message, content, false);
        text.setText(value);
        content.addView(text);
    }

    private void bindAction(View root, int id, Runnable action) {
        View button = root.findViewById(id);
        button.setEnabled(canWrite());
        button.setOnClickListener(
                v -> {
                    if (canWrite()) action.run();
                });
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        findViewById(R.id.inventoryAdd).setEnabled(canWrite());
        findViewById(R.id.inventoryRetry)
                .setVisibility(model.hasPending() || model.hasDraft() ? View.VISIBLE : View.GONE);
        ((Button) findViewById(R.id.inventoryRetry))
                .setText(
                        model.hasPending()
                                ? "Thử lại thao tác đang chờ"
                                : "Tiếp tục dữ liệu đang nhập");
        findViewById(R.id.inventoryRetry)
                .setEnabled(
                        !model.busy
                                && isSessionReady()
                                && (!model.hasPending() || Objects.equals(model.ownerUid, uid)));
        search.setVisibility(model.selectedId == null ? View.VISIBLE : View.GONE);
        if (model.selectedId == null) {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (BookTitle b : books) {
                if (!(safe(b.title)
                                + " "
                                + safe(b.author)
                                + " "
                                + safe(b.isbn)
                                + " "
                                + safe(b.category))
                        .toLowerCase(Locale.ROOT)
                        .contains(query)) continue;
                View row =
                        getLayoutInflater()
                                .inflate(R.layout.item_admin_inventory_book, content, false);
                ((TextView) row.findViewById(R.id.inventoryBookTitle)).setText(b.title);
                ((TextView) row.findViewById(R.id.inventoryBookMeta))
                        .setText(
                                safe(b.author)
                                        + " · "
                                        + safe(b.category)
                                        + (b.isHidden ? " · Đang ẩn" : ""));
                ((TextView) row.findViewById(R.id.inventoryBookCount))
                        .setText(b.availableCopies + " sẵn sàng / " + b.totalCopies + " đã nhập");
                row.setOnClickListener(
                        v -> {
                            if (isSessionReady()) {
                                model.selectedId = b.bookTitleId;
                                render();
                            }
                        });
                content.addView(row);
                count++;
            }
            if (count == 0)
                label(
                        books.isEmpty()
                                ? "Kho chưa có dữ liệu. Làm mới để tải từ thư viện."
                                : "Không tìm thấy đầu sách phù hợp.");
            return;
        }
        BookTitle selected = null;
        for (BookTitle b : books) if (model.selectedId.equals(b.bookTitleId)) selected = b;
        if (selected == null) {
            label("Không tìm thấy đầu sách. Hãy làm mới danh mục.");
            return;
        }
        final BookTitle book = selected;
        View detail =
                getLayoutInflater().inflate(R.layout.view_admin_inventory_detail, content, false);
        ((TextView) detail.findViewById(R.id.inventoryDetailTitle)).setText(book.title);
        ((TextView) detail.findViewById(R.id.inventoryDetailMetadata))
                .setText(
                        safe(book.author)
                                + " · "
                                + safe(book.category)
                                + (book.isHidden ? " · Đang ẩn" : ""));
        ((TextView) detail.findViewById(R.id.inventoryDetailCounts))
                .setText(
                        book.availableCopies
                                + " cuốn sẵn sàng / "
                                + book.totalCopies
                                + " bản đã nhập");
        bindAction(detail, R.id.inventoryEditTitle, () -> showForm(book));
        bindAction(detail, R.id.inventoryIntake, () -> showIntake(book, false));
        detail.findViewById(R.id.inventoryHideTitle)
                .setVisibility(book.isHidden ? View.GONE : View.VISIBLE);
        bindAction(
                detail,
                R.id.inventoryHideTitle,
                () ->
                        confirm(
                                "Ẩn đầu sách?",
                                "Đầu sách sẽ không còn xuất hiện cho Reader. Giữ nguyên các bản"
                                        + " sách và lịch sử.",
                                () -> submit("hideTitle", book.bookTitleId, null, null, null)));
        content.addView(detail);
        int count = 0;
        List<BookCopy> ordered = new ArrayList<>(copies);
        Collections.sort(
                ordered, (left, right) -> safe(left.barcode).compareTo(safe(right.barcode)));
        for (BookCopy c : ordered)
            if (book.bookTitleId.equals(c.bookTitleId)) {
                count++;
                View row =
                        getLayoutInflater()
                                .inflate(R.layout.item_admin_inventory_copy, content, false);
                ((TextView) row.findViewById(R.id.copyLabel)).setText(c.barcode);
                ((TextView) row.findViewById(R.id.copyMetadata))
                        .setText(
                                "Kệ "
                                        + safe(c.shelfLocation)
                                        + " · "
                                        + stateName(c.copyStatus)
                                        + (c.isHidden ? " · Ẩn" : ""));
                row.findViewById(R.id.copyShelf).setEnabled(canWrite());
                row.findViewById(R.id.copyShelf)
                        .setOnClickListener(
                                v -> {
                                    if (canWrite()) showShelf(c);
                                });
                row.findViewById(R.id.copyBarcode)
                        .setOnClickListener(
                                v -> {
                                    if (isSessionReady()) showBarcode(c);
                                });
                boolean ready = canWrite() && InventoryPolicy.isAvailable(c);
                row.findViewById(R.id.copyHide).setEnabled(ready);
                row.findViewById(R.id.copyRetire).setEnabled(ready);
                row.findViewById(R.id.copyHide)
                        .setOnClickListener(
                                v ->
                                        confirm(
                                                "Ẩn cuốn sách?",
                                                c.barcode
                                                        + " sẽ chuyển sang đang sửa chữa và giảm số"
                                                        + " sẵn sàng.",
                                                () -> submit("hide", c.copyId, null, null, null)));
                row.findViewById(R.id.copyRetire)
                        .setOnClickListener(
                                v ->
                                        confirm(
                                                "Thanh lý cuốn sách?",
                                                c.barcode
                                                        + " sẽ ngừng phục vụ. Bản ghi và lịch sử"
                                                        + " được giữ nguyên.",
                                                () ->
                                                        submit(
                                                                "retire", c.copyId, null, null,
                                                                null)));
                content.addView(row);
            }
        if (count == 0)
            label("Chưa có dữ liệu BookCopies cho đầu sách này. Không tự tạo bản thay thế.");
    }

    private void showForm(BookTitle existing) {
        String draftId = existing == null ? null : existing.bookTitleId;
        if (!"title".equals(model.draftKind) || !Objects.equals(draftId, model.draftId))
            model.formDraft = null;
        model.draftKind = "title";
        model.draftId = draftId;
        InventoryForm form = new InventoryForm(this, categories, existing);
        form.restoreDraft(model.formDraft);
        form.watchDraft(() -> model.formDraft = form.saveDraft());
        model.formDraft = form.saveDraft();
        AlertDialog dialog =
                privateSupportDialog()
                        .setTitle(existing == null ? R.string.inventory_add : R.string.stock_edit)
                        .setView(form.view)
                        .setNegativeButton(
                                R.string.stock_cancel,
                                (d, w) -> {
                                    model.clearDraft();
                                    render();
                                })
                        .setPositiveButton(
                                existing == null ? R.string.stock_intake : R.string.stock_save,
                                null)
                        .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                BookTitle b =
                                        form.read(
                                                existing == null
                                                        ? "BOOK-"
                                                                + UUID.randomUUID()
                                                                        .toString()
                                                                        .toUpperCase(Locale.ROOT)
                                                        : existing.bookTitleId);
                                if (existing == null) {
                                    dialog.dismiss();
                                    showIntake(b, true);
                                } else {
                                    submit("edit", b.bookTitleId, b, null, null);
                                    dialog.dismiss();
                                }
                            } catch (Exception error) {
                                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
    }

    private void showIntake(BookTitle book, boolean creating) {
        if (!"intake".equals(model.draftKind)
                || model.draftBook == null
                || !book.bookTitleId.equals(model.draftBook.bookTitleId)) model.intakeDraft = null;
        model.draftKind = "intake";
        model.draftBook = book;
        model.draftCreating = creating;
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_inventory_intake, null);
        EditText quantityInput = view.findViewById(R.id.intakeQuantity),
                shelfInput = view.findViewById(R.id.intakeShelf);
        if (model.intakeDraft != null) {
            quantityInput.setText(model.intakeDraft.getString("quantity"));
            shelfInput.setText(model.intakeDraft.getString("shelf"));
        }
        TextWatcher draftWatcher =
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void afterTextChanged(Editable e) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        model.intakeDraft = new Bundle();
                        model.intakeDraft.putString("quantity", quantityInput.getText().toString());
                        model.intakeDraft.putString("shelf", shelfInput.getText().toString());
                    }
                };
        quantityInput.addTextChangedListener(draftWatcher);
        shelfInput.addTextChangedListener(draftWatcher);
        AlertDialog dialog =
                privateSupportDialog()
                        .setTitle(R.string.stock_intake)
                        .setView(view)
                        .setNegativeButton(
                                R.string.stock_cancel,
                                (d, w) -> {
                                    model.clearDraft();
                                    render();
                                })
                        .setPositiveButton(R.string.stock_save, null)
                        .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                int quantity =
                                        Integer.parseInt(
                                                ((EditText) view.findViewById(R.id.intakeQuantity))
                                                        .getText()
                                                        .toString()
                                                        .trim());
                                StockIntake intake =
                                        new StockIntake(
                                                quantity,
                                                ((EditText) view.findViewById(R.id.intakeShelf))
                                                        .getText()
                                                        .toString());
                                model.selectedId = book.bookTitleId;
                                submit(
                                        creating ? "create" : "intake",
                                        book.bookTitleId,
                                        book,
                                        intake,
                                        null);
                                dialog.dismiss();
                            } catch (NumberFormatException error) {
                                Toast.makeText(
                                                this,
                                                "Nhập số lượng nguyên từ 1 đến 5.",
                                                Toast.LENGTH_LONG)
                                        .show();
                            } catch (Exception error) {
                                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
    }

    private void resumeDraft() {
        if (!canWrite()) return;
        if ("intake".equals(model.draftKind) && model.draftBook != null) {
            showIntake(model.draftBook, model.draftCreating);
            return;
        }
        BookTitle existing = null;
        for (BookTitle b : books) if (b.bookTitleId.equals(model.draftId)) existing = b;
        if (model.draftId != null && existing == null) {
            status.setText("Đầu sách đang sửa không còn trong cache. Hãy làm mới.");
            return;
        }
        showForm(existing);
    }

    private void showShelf(BookCopy copy) {
        EditText field =
                (EditText) getLayoutInflater().inflate(R.layout.dialog_admin_inventory_shelf, null);
        field.setText(copy.shelfLocation);
        AlertDialog dialog =
                privateSupportDialog()
                        .setTitle(R.string.stock_location)
                        .setView(field)
                        .setNegativeButton(R.string.stock_cancel, null)
                        .setPositiveButton(R.string.stock_save, null)
                        .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                String shelf =
                                        InventoryPolicy.required(
                                                field.getText().toString(), "vị trí kệ");
                                submit("shelf", copy.copyId, null, null, shelf);
                                dialog.dismiss();
                            } catch (Exception error) {
                                field.setError(error.getMessage());
                            }
                        });
    }

    private void showBarcode(BookCopy copy) {
        try {
            ImageView image =
                    (ImageView)
                            getLayoutInflater()
                                    .inflate(R.layout.dialog_admin_inventory_barcode, null);
            image.setImageBitmap(BarcodeLabels.encode(copy.barcode));
            image.setContentDescription(copy.barcode);
            privateSupportDialog()
                    .setTitle(copy.barcode)
                    .setView(image)
                    .setNegativeButton("Đóng", null)
                    .setPositiveButton(
                            "In / Lưu PDF",
                            (d, w) -> {
                                if (isSessionReady()) BarcodeLabels.print(this, copy.barcode);
                            })
                    .show();
        } catch (Exception error) {
            Toast.makeText(this, "Không tạo được mã vạch: " + error.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void confirm(String title, String message, Runnable action) {
        if (canWrite())
            privateSupportDialog()
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton(R.string.stock_cancel, null)
                    .setPositiveButton(
                            "Xác nhận",
                            (d, w) -> {
                                if (canWrite()) action.run();
                            })
                    .show();
    }

    private void submit(
            String action, String id, BookTitle book, StockIntake intake, String shelf) {
        if (canWrite()) model.submit(action, id, book, intake, shelf, uid);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String stateName(String s) {
        if (s == null) return "Chưa cập nhật";
        switch (s) {
            case "SanSang":
                return "Sẵn sàng";
            case "DaMuon":
                return "Đang mượn";
            case "ChoLay":
                return "Chờ lấy";
            case "DaDatTruoc":
                return "Đã đặt trước";
            case "DangSuaChua":
                return "Đang sửa chữa";
            case "ThanhLy":
                return "Đã thanh lý";
            case "DaMat":
                return "Đã mất";
            default:
                return s;
        }
    }

    private void stopSync() {
        generation++;
        if (sync != null) {
            sync.close();
            sync = null;
        }
    }

    @Override
    protected void onSessionStopped() {
        stopSync();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        model.save(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        stopSync();
        worker.execute(helper::close);
        worker.shutdown();
        super.onDestroy();
    }
}
