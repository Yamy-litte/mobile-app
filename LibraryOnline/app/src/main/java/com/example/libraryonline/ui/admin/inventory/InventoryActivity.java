package com.example.libraryonline.ui.admin.inventory;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.scanner.LibraryBarcodeScanner;
import com.example.libraryonline.data.inventory.*;
import com.example.libraryonline.data.local.dao.*;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.*;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.ui.admin.AdminHomeView;
import com.example.libraryonline.ui.admin.AdminNavigation;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.*;
import java.util.concurrent.*;

public class InventoryActivity extends SessionActivity {
    private static final String LIST = "list",
            EDIT = "edit",
            INTAKE = "intake",
            COPIES = "copies",
            COPY = "copy",
            LABELS = "labels";
    private LibraryBarcodeScanner barcodeScanner;
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
    private String uid, page = LIST, selectedCopyId, categoryId = "", handledCompletion;
    private String renderedKey, shelfDraft, renderedCategories;
    private long generation;
    private boolean leaving;
    private String pendingScanCode, scanOwnerUid;
    private InventoryForm editor;
    private InventoryQuantity quantity;
    private View pageView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        barcodeScanner = new LibraryBarcodeScanner(this, this::onScannedInventory);
        if (state != null) {
            pendingScanCode = state.getString("pendingScanCode");
            scanOwnerUid = state.getString("scanOwnerUid");
        }
        setContentView(R.layout.activity_admin_inventory);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.inventoryRoot),
                (view, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
        helper = new DatabaseHelper(getApplicationContext());
        model = new ViewModelProvider(this).get(InventoryViewModel.class);
        model.restore(state);
        if (state != null) {
            page = state.getString("stockPage", LIST);
            selectedCopyId = state.getString("stockCopy");
            categoryId = state.getString("stockCategory", "");
            handledCompletion = state.getString("stockCompletion");
            shelfDraft = state.getString("stockShelfDraft");

        }
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
                    final int target = tab;
                    leavePage(
                            () -> {
                                leaving = true;
                                AdminNavigation.returnToMain(this, target);
                            });
                    return false;
                });
        navigation.setOnItemReselectedListener(item -> leavePage(() -> go(LIST)));
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
                            if (canWrite()) openEditor(null);
                        });
        findViewById(R.id.inventoryScanBarcode).setOnClickListener(v -> {
            if (!isSessionReady()) return;
            scanOwnerUid = uid;
            barcodeScanner.scan("Quét barcode cuốn hoặc ISBN");
        });
        findViewById(R.id.inventoryRetry)
                .setOnClickListener(
                        v -> {
                            if (!isSessionReady()) return;
                            if (model.hasPending()) model.retry(uid);
                            else resumeDraft();
                        });
        search.addTextChangedListener(
                watch(
                        () -> {
                            if (LIST.equals(page)) render();
                        }));
        model.message.observe(
                this,
                message -> {
                    if (!isSessionReady()) return;
                    if (message != null && !message.isEmpty()) status.setText(message);
                    if (model.completionId != null
                            && !Objects.equals(handledCompletion, model.completionId)) {
                        handledCompletion = model.completionId;
                        if (model.completedIntake != null) {
                            page = LABELS;
                            model.selectedId = model.completedBook.bookTitleId;
                        } else if ("edit".equals(model.completedAction)) {
                            page = COPIES;
                        }
                        if ("shelf".equals(model.completedAction)) shelfDraft = null;
                        renderedKey = null;
                    }
                    render();
                    if (!model.busy) loadLocal(generation);
                });
        render();
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
        render();
        applyPendingInventoryBarcode();
    }

    private boolean current(long token) {
        return token == generation && isSessionReady() && !isDestroyed();
    }

    private boolean canWrite() {
        return isSessionReady() && !model.journalError && !model.busy && !model.hasPending();
    }

    private void go(String destination) {
        page = destination;
        renderedKey = null;
        editor = null;
        quantity = null;
        pageView = null;
        render();
        ((ScrollView) findViewById(R.id.inventoryScroll)).scrollTo(0, 0);
    }

    private void back() {
        if (!isSessionReady()) return;
        if (EDIT.equals(page) || INTAKE.equals(page)) {
            leavePage(() -> go(LIST));
            return;
        }
        if (COPY.equals(page)) {
            go(COPIES);
            return;
        }
        if (!LIST.equals(page)) {
            go(LIST);
            return;
        }
        if (!leaving) {
            leaving = true;
            AdminNavigation.returnToMain(this, AdminHomeView.HOME);
        }
    }

    private void leavePage(Runnable next) {
        if ((EDIT.equals(page) || INTAKE.equals(page)) && model.hasDraft() && !model.hasPending()) {
            privateSupportDialog()
                    .setTitle("Rời màn hình nhập liệu?")
                    .setMessage(
                            "Dữ liệu chưa lưu sẽ được giữ để bạn tiếp tục khi còn ở màn hình kho."
                                + " Nếu chuyển sang tab khác, hãy lưu trước để tránh mất dữ liệu.")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Rời màn hình", (d, w) -> next.run())
                    .show();
        } else next.run();
    }

    private static TextWatcher watch(Runnable changed) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed.run();
            }

            public void afterTextChanged(Editable e) {}
        };
    }

    private BookTitle book() {
        for (BookTitle b : books) if (Objects.equals(model.selectedId, b.bookTitleId)) return b;
        return model.draftBook != null
                        && Objects.equals(model.selectedId, model.draftBook.bookTitleId)
                ? model.draftBook
                : null;
    }

    private BookCopy copy() {
        for (BookCopy c : copies) if (Objects.equals(selectedCopyId, c.copyId)) return c;
        return null;
    }

    private void label(String value) {
        TextView v =
                (TextView)
                        getLayoutInflater()
                                .inflate(R.layout.item_admin_inventory_message, content, false);
        v.setText(value);
        content.addView(v);
    }

    private View inflate(int layout) {
        View v = getLayoutInflater().inflate(layout, content, false);
        content.addView(v);
        return v;
    }

    private void bindAction(View root, int id, Runnable action) {
        View v = root.findViewById(id);
        v.setEnabled(canWrite());
        v.setOnClickListener(
                clicked -> {
                    if (canWrite()) action.run();
                });
    }

    private void heading(String value) {
        ((TextView) findViewById(R.id.inventoryHeading)).setText(value);
    }

    private void render() {
        if (content == null) return;
        findViewById(R.id.inventoryListTools)
                .setVisibility(LIST.equals(page) ? View.VISIBLE : View.GONE);
        findViewById(R.id.inventoryAdd).setVisibility(LIST.equals(page) ? View.VISIBLE : View.GONE);
        findViewById(R.id.inventoryAdd).setEnabled(canWrite());
        findViewById(R.id.inventoryRetry)
                .setVisibility(
                        model.hasPending() || (LIST.equals(page) && model.hasDraft())
                                ? View.VISIBLE
                                : View.GONE);
        ((TextView) findViewById(R.id.inventoryRetry))
                .setText(
                        model.hasPending()
                                ? "Thử lại thao tác đang chờ"
                                : "Tiếp tục dữ liệu đang nhập");
        findViewById(R.id.inventoryRetry)
                .setEnabled(
                        !model.busy
                                && isSessionReady()
                                && (!model.hasPending() || Objects.equals(model.ownerUid, uid)));
        String key = page + ":" + model.selectedId + ":" + selectedCopyId;
        StringBuilder categoryVersion = new StringBuilder();
        for (CategoryDao.Category c : categories)
            categoryVersion.append(c.categoryId).append(':').append(c.name).append(';');
        String signature = categoryVersion.toString();
        if (key.equals(renderedKey)
                && (!EDIT.equals(page) || signature.equals(renderedCategories))
                && (EDIT.equals(page) || INTAKE.equals(page) || COPY.equals(page))) {
            updateControls();
            return;
        }
        content.removeAllViews();
        editor = null;
        quantity = null;
        pageView = null;
        renderedKey = null;
        switch (page) {
            case EDIT:
                renderEditor();
                break;
            case INTAKE:
                renderIntake();
                break;
            case COPY:
                renderCopy();
                break;
            case LABELS:
                renderLabels();
                break;
            case COPIES:
                renderCopies();
                break;
            default:
                page = LIST;
                heading("Quản lý kho sách");
                renderCategories();
                renderList();
                break;
        }
        // Missing local data may arrive later; don't freeze an empty page.
        if (pageView != null) {
            renderedKey = key;
            renderedCategories = signature;
        }
        updateControls();
    }

    private void renderCategories() {
        LinearLayout host = findViewById(R.id.inventoryCategories);
        host.removeAllViews();
        Map<String, String> names = new LinkedHashMap<>();
        names.put("", "Tất cả");
        for (CategoryDao.Category c : categories) names.put(c.categoryId, c.name);
        for (BookTitle b : books)
            if (b.categoryId != null)
                names.put(b.categoryId, safe(b.category).isEmpty() ? b.categoryId : b.category);
        if (!names.containsKey(categoryId)) categoryId = "";
        for (Map.Entry<String, String> entry : names.entrySet()) {
            TextView chip =
                    (TextView)
                            getLayoutInflater()
                                    .inflate(R.layout.item_admin_inventory_category, host, false);
            chip.setText(entry.getValue());
            chip.setSelected(categoryId.equals(entry.getKey()));
            chip.setOnClickListener(
                    v -> {
                        categoryId = entry.getKey();
                        render();
                    });
            host.addView(chip);
        }
    }

    private void renderList() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (BookTitle b : books) {
            if (!categoryId.isEmpty() && !categoryId.equals(b.categoryId)) continue;
            if (!(safe(b.title)
                            + " "
                            + safe(b.author)
                            + " "
                            + safe(b.isbn)
                            + " "
                            + safe(b.category))
                    .toLowerCase(Locale.ROOT)
                    .contains(query)) continue;
            View row = inflate(R.layout.item_admin_inventory_book);
            InventoryViews.bindBook(row, b);
            count++;
            row.setOnClickListener(
                    v -> {
                        if (!isSessionReady()) return;
                        model.selectedId = b.bookTitleId;
                        go(COPIES);
                    });
            bindAction(row, R.id.inventoryBookEdit, () -> openEditor(b));
            bindAction(row, R.id.inventoryBookIntake, () -> openIntake(b));
            row.findViewById(R.id.inventoryBookCopies)
                    .setOnClickListener(
                            v -> {
                                if (isSessionReady()) {
                                    model.selectedId = b.bookTitleId;
                                    go(COPIES);
                                }
                            });
            TextView titleVisibility = row.findViewById(R.id.inventoryBookHide);
            titleVisibility.setText(
                    b.isHidden ? R.string.inventory_ui_restore_title : R.string.inventory_ui_hide_title);
            titleVisibility.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            this, b.isHidden ? R.color.library_success : R.color.library_danger));
            bindAction(
                    row,
                    R.id.inventoryBookHide,
                    () -> confirmTitleVisibility(b));
        }
        if (count == 0)
            label(
                    books.isEmpty()
                            ? "Chưa có dữ liệu kho. Làm mới để tải từ thư viện."
                            : "Không tìm thấy đầu sách phù hợp.");
    }

    /** Same confirmation for the catalogue card and title details, with no deletion. */
    private void confirmTitleVisibility(BookTitle title) {
        if (title.isHidden) {
            confirm(
                    "Hiện lại đầu sách?",
                    "Đầu sách sẽ xuất hiện trở lại cho Reader. Các cuốn đã ẩn vẫn giữ nguyên "
                            + "trạng thái cho đến khi được hiện lại riêng.",
                    () -> submit("restoreTitle", title.bookTitleId, null, null, null));
        } else {
            confirm(
                    "Ẩn đầu sách?",
                    "Đầu sách không còn xuất hiện cho Reader. Giữ nguyên các cuốn và lịch sử.",
                    () -> submit("hideTitle", title.bookTitleId, null, null, null));
        }
    }

    private void openEditor(BookTitle existing) {
        String id = existing == null ? null : existing.bookTitleId;
        if (!"title".equals(model.draftKind) || !Objects.equals(model.draftId, id)) {
            model.clearDraft();
        }
        model.draftKind = "title";
        model.draftId = id;
        model.draftBook = existing;
        model.selectedId = id;
        go(EDIT);
    }

    private void renderEditor() {
        BookTitle existing = model.draftId == null ? null : book();
        heading(existing == null ? "Thêm đầu sách" : "Chỉnh sửa đầu sách");
        if (model.draftId != null && existing == null) {
            label("Đang tải thông tin đầu sách…");
            return;
        }
        editor = new InventoryForm(this, categories, existing);
        editor.restoreDraft(model.formDraft);
        pageView = editor.view;
        content.addView(pageView);
        InventoryForm activeForm = editor;
        editor.watchDraft(
                () -> {
                    if (editor == activeForm) model.formDraft = activeForm.saveDraft();
                });
        model.formDraft = editor.saveDraft();
        if (existing == null) {
            quantity =
                    new InventoryQuantity(
                            pageView,
                            model.intakeDraft,
                            () -> {
                                if (quantity != null) model.intakeDraft = quantity.save();
                            });
            model.intakeDraft = quantity.save();
        }
        pageView.findViewById(R.id.editorSave)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                String id =
                                        existing == null
                                                ? "BOOK-"
                                                        + UUID.randomUUID()
                                                                .toString()
                                                                .toUpperCase(Locale.ROOT)
                                                : existing.bookTitleId;
                                BookTitle draft = editor.read(id);
                                StockIntake intake = existing == null ? quantity.read() : null;
                                InventoryViews.error(pageView, R.id.editorError, null);
                                model.selectedId = id;
                                submit(
                                        existing == null ? "create" : "edit",
                                        id,
                                        draft,
                                        intake,
                                        null);
                            } catch (Exception error) {
                                InventoryViews.error(
                                        pageView, R.id.editorError, error.getMessage());
                            }
                        });
        pageView.findViewById(R.id.editorManageCopies)
                .setOnClickListener(v -> leavePage(() -> go(COPIES)));
    }

    private void openIntake(BookTitle b) {
        if (!"intake".equals(model.draftKind) || !Objects.equals(model.draftId, b.bookTitleId))
            model.clearDraft();
        model.draftKind = "intake";
        model.draftId = b.bookTitleId;
        model.draftBook = b;
        model.draftCreating = false;
        model.selectedId = b.bookTitleId;
        go(INTAKE);
    }

    private void renderIntake() {
        heading("Nhập thêm cuốn");
        BookTitle b = book();
        if (b == null) {
            label("Đang tải đầu sách…");
            return;
        }
        pageView = inflate(R.layout.view_admin_inventory_intake);
        InventoryViews.bindSummary(pageView, b);
        quantity =
                new InventoryQuantity(
                        pageView,
                        model.intakeDraft,
                        () -> {
                            if (quantity != null) {
                                model.intakeDraft = quantity.save();
                                forecast();
                            }
                        });
        model.intakeDraft = quantity.save();
        forecast();
        pageView.findViewById(R.id.intakeSubmit)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                StockIntake intake = quantity.read();
                                InventoryViews.error(pageView, R.id.intakeError, null);
                                submit("intake", b.bookTitleId, b, intake, null);
                            } catch (Exception error) {
                                InventoryViews.error(
                                        pageView, R.id.intakeError, error.getMessage());
                            }
                        });
    }

    private void forecast() {
        if (!INTAKE.equals(page) || pageView == null || quantity == null) return;
        BookTitle b = book();
        if (b == null) return;
        int n = quantity.count();
        InventoryViews.text(
                pageView,
                R.id.intakeForecast,
                n < 1 || n > 5
                        ? "Nhập số lượng từ 1 đến 5 cuốn."
                        : "Tổng đã nhập: "
                                + b.totalCopies
                                + " → "
                                + ((long) b.totalCopies + n)
                                + " cuốn\nSẵn sàng: "
                                + b.availableCopies
                                + " → "
                                + ((long) b.availableCopies + n)
                                + " cuốn");
    }

    private void renderCopies() {
        heading("Các cuốn sách");
        BookTitle b = book();
        if (b == null) {
            label("Đang tải đầu sách và các cuốn…");
            return;
        }
        View summary = inflate(R.layout.view_admin_inventory_detail);
        InventoryViews.text(summary, R.id.inventoryDetailTitle, b.title);
        InventoryViews.text(
                summary, R.id.inventoryDetailMetadata, safe(b.author) + " · " + safe(b.category));
        InventoryViews.text(
                summary,
                R.id.inventoryDetailCounts,
                b.availableCopies + " sẵn sàng / " + b.totalCopies + " đã nhập");
        bindAction(summary, R.id.inventoryEditTitle, () -> openEditor(b));
        bindAction(summary, R.id.inventoryIntake, () -> openIntake(b));
        TextView titleVisibility = summary.findViewById(R.id.inventoryHideTitle);
        titleVisibility.setText(
                b.isHidden ? R.string.inventory_ui_restore_title : R.string.inventory_ui_hide_title);
        titleVisibility.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                        this, b.isHidden ? R.color.library_success : R.color.library_danger));
        bindAction(summary, R.id.inventoryHideTitle, () -> confirmTitleVisibility(b));
        List<BookCopy> ordered = new ArrayList<>(copies);
        Collections.sort(ordered, (l, r) -> safe(l.barcode).compareTo(safe(r.barcode)));
        int count = 0;
        for (BookCopy c : ordered)
            if (b.bookTitleId.equals(c.bookTitleId)) {
                count++;
                View row = inflate(R.layout.item_admin_inventory_copy);
                InventoryViews.text(row, R.id.copyLabel, c.barcode);
                InventoryViews.text(
                        row,
                        R.id.copyMetadata,
                        "Kệ "
                                + safe(c.shelfLocation)
                                + " · "
                                + stateName(c.copyStatus)
                                + (c.isHidden ? " · Ẩn" : ""));
                row.setOnClickListener(v -> openCopy(c));
                row.findViewById(R.id.copyShelf).setOnClickListener(v -> openCopy(c));
            }
        if (count == 0) label("Chưa có dữ liệu cuốn sách. Hãy làm mới để đồng bộ.");
    }

    private void openCopy(BookCopy c) {
        if (!isSessionReady()) return;
        model.selectedId = c.bookTitleId;
        selectedCopyId = c.copyId;
        shelfDraft = null;
        go(COPY);
    }

    private void renderCopy() {
        heading("Chi tiết cuốn sách");
        BookCopy c = copy();
        BookTitle b = book();
        if (c == null || b == null) {
            label("Đang tải dữ liệu cuốn sách…");
            return;
        }
        pageView = inflate(R.layout.view_admin_inventory_copy_detail);
        InventoryViews.bindSummary(pageView, b);
        InventoryViews.text(pageView, R.id.copyTitleId, c.bookTitleId);
        InventoryViews.text(pageView, R.id.copyIdentity, c.copyId);
        InventoryViews.text(pageView, R.id.copyBarcodeText, c.barcode);
        InventoryViews.barcode(pageView, R.id.copyBarcodeImage, c.barcode);
        EditText shelf = pageView.findViewById(R.id.copyShelfInput);
        shelf.setText(shelfDraft == null ? c.shelfLocation : shelfDraft);
        shelf.addTextChangedListener(watch(() -> shelfDraft = shelf.getText().toString()));
        pageView.findViewById(R.id.copySaveShelf)
                .setOnClickListener(
                        v -> {
                            if (!canWrite()) return;
                            try {
                                String value =
                                        InventoryPolicy.required(
                                                shelf.getText().toString(), "vị trí kệ");
                                submit("shelf", c.copyId, null, null, value);
                            } catch (Exception error) {
                                InventoryViews.error(pageView, R.id.copyError, error.getMessage());
                            }
                        });
        pageView.findViewById(R.id.copyPrint)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady()) printLabels(Collections.singletonList(c.barcode));
                        });
        pageView.findViewById(R.id.copyHide)
                .setOnClickListener(
                        v ->
                                confirm(
                                        "Ẩn cuốn sách?",
                                        c.barcode
                                                + " sẽ chuyển sang đang sửa chữa và giảm số sẵn"
                                                + " sàng.",
                                        () -> submit("hide", c.copyId, null, null, null)));
        pageView.findViewById(R.id.copyRetire)
                .setOnClickListener(
                        v ->
                                confirm(
                                        "Thanh lý cuốn sách?",
                                        c.barcode
                                                + " sẽ ngừng phục vụ. Bản ghi và lịch sử được giữ"
                                                + " nguyên.",
                                        () -> submit("retire", c.copyId, null, null, null)));
        pageView.findViewById(R.id.copyRestore)
                .setOnClickListener(
                        v -> {
                            BookCopy currentCopy = copy();
                            BookTitle currentTitle = book();
                            if (!canWrite() || currentTitle == null || currentTitle.isHidden
                                    || !InventoryPolicy.canRestore(currentCopy)) return;
                            confirm(
                                    "Hiện lại cuốn sách?",
                                    currentCopy.barcode
                                            + " sẽ trở về trạng thái sẵn sàng và tăng số cuốn "
                                            + "sẵn sàng của đầu sách lên một.",
                                    () -> submit("restore", currentCopy.copyId, null, null, null));
                        });
    }

    private List<BookCopy> receiptCopies() {
        List<BookCopy> result = new ArrayList<>();
        if (model.completedIntake == null) return result;
        for (int i = 0; i < model.completedIntake.quantity; i++)
            for (BookCopy c : copies)
                if (model.completedIntake.copyId(i).equals(c.copyId)
                        && model.completedBook.bookTitleId.equals(c.bookTitleId)) result.add(c);
        return result;
    }

    private void renderLabels() {
        heading("Nhãn mã vạch mới");
        if (model.completedIntake == null || model.completedBook == null) {
            label("Chưa có lần nhập được xác nhận trong phiên này.");
            return;
        }
        pageView = inflate(R.layout.view_admin_inventory_labels);
        BookTitle b = book();
        InventoryViews.bindSummary(pageView, b == null ? model.completedBook : b);
        InventoryViews.text(
                pageView,
                R.id.summaryCounts,
                model.completedIntake.quantity + " cuốn đã được xác nhận nhập");
        List<BookCopy> receipt = receiptCopies();
        LinearLayout items = pageView.findViewById(R.id.labelItems);
        for (BookCopy c : receipt) {
            View label =
                    getLayoutInflater().inflate(R.layout.item_admin_inventory_label, items, false);
            InventoryViews.text(label, R.id.labelTitle, model.completedBook.title);
            InventoryViews.text(label, R.id.labelBarcode, c.barcode);
            InventoryViews.text(label, R.id.labelShelf, "Vị trí kệ: " + safe(c.shelfLocation));
            InventoryViews.barcode(label, R.id.labelBarcodeImage, c.barcode);
            items.addView(label);
        }
        boolean complete = receipt.size() == model.completedIntake.quantity;
        pageView.findViewById(R.id.labelsPrint).setEnabled(complete && isSessionReady());
        if (!complete)
            label(
                    "Đang đồng bộ barcode đã lưu. Nếu chưa thấy nhãn, hãy làm mới; không cần nhập"
                            + " lại sách.");
        pageView.findViewById(R.id.labelsPrint)
                .setOnClickListener(
                        v -> {
                            if (!isSessionReady() || !complete) return;
                            List<String> values = new ArrayList<>();
                            for (BookCopy c : receipt) values.add(c.barcode);
                            printLabels(values);
                        });
        pageView.findViewById(R.id.labelsDone).setOnClickListener(v -> go(LIST));
    }

    private void updateControls() {
        if (editor != null) {
            editor.setEnabled(canWrite());
            pageView.findViewById(R.id.editorSave).setEnabled(canWrite());
        }
        if (quantity != null) quantity.setEnabled(canWrite());
        if (INTAKE.equals(page) && pageView != null) {
            pageView.findViewById(R.id.intakeSubmit).setEnabled(canWrite());
            forecast();
        }
        if (COPY.equals(page) && pageView != null) {
            BookCopy c = copy();
            InventoryViews.text(
                    pageView,
                    R.id.copyState,
                    c == null
                            ? "Không còn trong dữ liệu đã lưu"
                            : stateName(c.copyStatus) + (c.isHidden ? " · Đã ẩn" : ""));
            pageView.findViewById(R.id.copySaveShelf).setEnabled(canWrite() && c != null);
            pageView.findViewById(R.id.copyShelfInput).setEnabled(canWrite() && c != null);
            boolean ready = canWrite() && InventoryPolicy.isAvailable(c);
            pageView.findViewById(R.id.copyHide).setEnabled(ready);
            pageView.findViewById(R.id.copyRetire).setEnabled(ready);
            boolean concealed = c != null && c.isHidden;
            pageView.findViewById(R.id.copyHide)
                    .setVisibility(concealed ? View.GONE : View.VISIBLE);
            pageView.findViewById(R.id.copyRetire)
                    .setVisibility(concealed ? View.GONE : View.VISIBLE);
            BookTitle title = book();
            boolean canRestore = InventoryPolicy.canRestore(c);
            View restore = pageView.findViewById(R.id.copyRestore);
            restore.setVisibility(canRestore ? View.VISIBLE : View.GONE);
            restore.setEnabled(canWrite() && canRestore && title != null && !title.isHidden);
            TextView hint = pageView.findViewById(R.id.copyRestoreHint);
            hint.setVisibility(concealed ? View.VISIBLE : View.GONE);
            if (concealed)
                hint.setText(
                        canRestore
                                ? title != null && title.isHidden
                                        ? R.string.inventory_ui_restore_title_first
                                        : R.string.inventory_ui_restore_copy_hint
                                : R.string.inventory_ui_restore_unavailable);
        }
    }

    private void resumeDraft() {
        if (model.draftKind == null) return;
        model.selectedId = model.draftId;
        go("intake".equals(model.draftKind) ? INTAKE : EDIT);
    }

    private void onScannedInventory(String code) {
        pendingScanCode = code;
        applyPendingInventoryBarcode();
    }

    private void applyPendingInventoryBarcode() {
        if (!isSessionReady() || pendingScanCode == null) return;
        if (!java.util.Objects.equals(uid, scanOwnerUid)) {
            pendingScanCode = null;
            return;
        }
        String code = pendingScanCode;
        // Keep the code in the visible search input even when the matching copy
        // needs another inventory sync cycle to appear.
        categoryId = "";
        if (!LIST.equals(page)) {
            page = LIST;
            renderedKey = null;
            render();
        }
        search.setText(code);
        search.setSelection(code.length());
        if (books.isEmpty() && copies.isEmpty()) {
            status.setText("Đã tự điền mã " + code + ". Đang đợi kho đồng bộ để tìm sách.");
            return;
        }
        pendingScanCode = null;
        for (BookCopy copy : copies) {
            if (code.equals(copy.barcode)) {
                openCopy(copy);
                return;
            }
        }
        for (BookTitle title : books) {
            if (code.equals(title.isbn)) {
                model.selectedId = title.bookTitleId;
                selectedCopyId = null;
                go(COPIES);
                return;
            }
        }
        status.setText("Đã điền mã " + code
                + " nhưng chưa tìm thấy cuốn/ISBN trong kho trên thiết bị. Hãy làm mới.");
        Toast.makeText(this, "Barcode chưa có trong kho đã đồng bộ.",
                Toast.LENGTH_LONG).show();
        render();
    }

    private void printLabels(List<String> barcodes) {
        try {
            BarcodeLabels.print(this, barcodes);
        } catch (Exception error) {
            Toast.makeText(this, "Không thể in mã vạch: " + error.getMessage(), Toast.LENGTH_LONG)
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
                                        applyPendingInventoryBarcode();
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
        if (editor != null) model.formDraft = editor.saveDraft();
        if (quantity != null) model.intakeDraft = quantity.save();
        state.putString("stockPage", page);
        state.putString("stockCopy", selectedCopyId);
        state.putString("stockCategory", categoryId);
        state.putString("stockCompletion", handledCompletion);
        state.putString("stockShelfDraft", shelfDraft);
        state.putString("pendingScanCode", pendingScanCode);
        state.putString("scanOwnerUid", scanOwnerUid);
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
