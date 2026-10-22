package com.example.libraryonline.ui.admin.inventory;

import android.os.Bundle;

import androidx.lifecycle.*;

import com.example.libraryonline.data.inventory.*;
import com.example.libraryonline.data.model.BookTitle;
import com.google.android.gms.tasks.Task;

/** Keeps an unresolved write's identity across rotation and saved-state restoration. */
public final class InventoryViewModel extends ViewModel {
    private InventoryRepository repository;

    public InventoryViewModel() {}

    public InventoryViewModel(InventoryRepository repository) {
        this.repository = repository;
    }

    private InventoryRepository repository() {
        if (repository == null) repository = new InventoryRepository();
        return repository;
    }

    public final MutableLiveData<String> message = new MutableLiveData<>("");
    public boolean busy;
    public String selectedId, ownerUid;
    private String action, id, shelf;
    private BookTitle book;
    private StockIntake intake;
    private boolean restored;
    private PendingInventoryStore store;
    private String journalId;
    public boolean journalError;
    // UI receipt: published only after a successful server write and journal cleanup.
    public String completionId, completedAction;
    public BookTitle completedBook;
    public StockIntake completedIntake;

    public void attach(android.content.Context context, String uid) {
        if (busy) return;
        store = new PendingInventoryStore(context);
        try {
            Bundle persisted = store.load(uid);
            if (persisted != null
                    && (!hasPending()
                            || !java.util.Objects.equals(
                                    journalId, persisted.getString("journalId")))) {
                restored = false;
                restore(persisted);
            }
            journalError = false;
        } catch (Exception error) {
            journalError = true;
            message.setValue(error.getMessage());
        }
    }

    public String draftKind, draftId;
    public Bundle formDraft, intakeDraft;
    public BookTitle draftBook;
    public boolean draftCreating;

    public boolean hasDraft() {
        return draftKind != null;
    }

    public void clearDraft() {
        draftKind = null;
        draftId = null;
        formDraft = null;
        intakeDraft = null;
        draftBook = null;
    }

    public boolean hasPending() {
        return action != null;
    }

    public void submit(
            String action,
            String id,
            BookTitle book,
            StockIntake intake,
            String shelf,
            String uid) {
        if (busy || hasPending()) return;
        completionId = null;
        completedAction = null;
        completedBook = null;
        completedIntake = null;
        this.journalId = java.util.UUID.randomUUID().toString();
        this.action = action;
        this.id = id;
        this.book = book;
        this.intake = intake;
        this.shelf = shelf;
        this.ownerUid = uid;
        retry(uid);
    }

    public void retry(String uid) {
        if (busy || action == null) return;
        if (!uid.equals(ownerUid)) {
            message.setValue("Thao tác thuộc phiên khác; không thể tiếp tục.");
            return;
        }
        try {
            if (store == null) throw new IllegalStateException("Chưa xác minh phiên để ghi kho.");
            Bundle state = new Bundle();
            save(state);
            store.save(uid, state);
            journalError = false;
        } catch (Exception error) {
            journalError = true;
            message.setValue(error.getMessage());
            return;
        }
        if (!store.claim(uid)) {
            message.setValue("Thao tác đang chạy ở màn hình khác. Hãy chờ kết quả rồi thử lại.");
            return;
        }
        busy = true;
        message.setValue("Đang lưu… Không đóng ứng dụng khi thao tác chưa có kết quả.");
        try {
            InventoryRepository repo = repository();
            Task<Void> task;
            switch (action) {
                case "create":
                    task = repo.createTitle(book, intake);
                    break;
                case "intake":
                    task = repo.addCopies(id, intake);
                    break;
                case "edit":
                    task = repo.updateTitle(book);
                    break;
                case "shelf":
                    task = repo.updateShelf(id, shelf);
                    break;
                case "hide":
                    task = repo.hideCopy(id);
                    break;
                case "retire":
                    task = repo.retireCopy(id);
                    break;
                case "restore":
                    task = repo.restoreCopy(id);
                    break;
                case "hideTitle":
                    task = repo.hideTitle(id);
                    break;
                case "restoreTitle":
                    task = repo.restoreTitle(id);
                    break;
                default:
                    throw new IllegalArgumentException("Thao tác không hợp lệ.");
            }
            task.addOnCompleteListener(
                    result -> {
                        if (result.isSuccessful()) succeed();
                        else fail(result.getException());
                    });
        } catch (Exception error) {
            fail(error);
        }
    }

    private void succeed() {
        busy = false;
        store.release(ownerUid);
        if (clearJournal()) {
            completionId = journalId;
            completedAction = action;
            completedBook = book;
            completedIntake = intake;
            clearPending();
            clearDraft();
            message.setValue("Đã lưu trên Firebase. Đang cập nhật danh sách…");
        } else
            message.setValue(
                    "Firebase đã lưu; chưa xóa được trạng thái local. Thử lại để xác nhận.");
    }

    private boolean clearJournal() {
        try {
            return store.clear(ownerUid, journalId);
        } catch (Exception error) {
            journalError = true;
            return false;
        }
    }

    private void fail(Exception error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        boolean definitive = cause instanceof IllegalArgumentException;
        if (error instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code code =
                    ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode();
            definitive =
                    definitive
                            || code
                                    == com.google.firebase.firestore.FirebaseFirestoreException.Code
                                            .PERMISSION_DENIED
                            || code
                                    == com.google.firebase.firestore.FirebaseFirestoreException.Code
                                            .INVALID_ARGUMENT;
        }
        final String reason = cause == null ? "Không thể lưu." : cause.getMessage();
        if (definitive && intake != null) {
            message.setValue("Đang đối chiếu lần nhập trên server…");
            repository()
                    .reconcileIntake(id, book, intake)
                    .addOnCompleteListener(
                            check -> {
                                if (check.isSuccessful()
                                        && Boolean.TRUE.equals(check.getResult())) {
                                    succeed();
                                    return;
                                }
                                finishFailure(reason, check.isSuccessful());
                            });
        } else finishFailure(reason, definitive && intake == null);
    }

    private void finishFailure(String reason, boolean confirmedRejected) {
        busy = false;
        store.release(ownerUid);
        if (confirmedRejected && clearJournal()) clearPending();
        message.setValue(
                reason
                        + (hasPending()
                                ? "\n"
                                        + "Chưa xác nhận kết quả. Giữ nguyên mã thao tác và thử lại"
                                        + " khi có mạng/quyền truy cập."
                                : "\n"
                                      + "Không ghi thêm dữ liệu. Kiểm tra đầu vào, schema và Rules"
                                      + " trước khi thử lại."));
    }

    private void clearPending() {
        action = null;
        book = null;
        intake = null;
        id = null;
        shelf = null;
    }

    public void restore(Bundle state) {
        if (restored) return;
        restored = true;
        if (state == null) return;
        completionId = state.getString("completionId");
        completedAction = state.getString("completedAction");
        completedBook = unpack(state.getBundle("completedBook"));
        completedIntake = (StockIntake) state.getSerializable("completedIntake");
        journalId = state.getString("journalId");
        draftKind = state.getString("draftKind");
        draftId = state.getString("draftId");
        formDraft = state.getBundle("formDraft");
        intakeDraft = state.getBundle("intakeDraft");
        draftCreating = state.getBoolean("draftCreating");
        draftBook = unpack(state.getBundle("draftBook"));
        selectedId = state.getString("inventorySelected");
        ownerUid = state.getString("inventoryOwner");
        action = state.getString("inventoryAction");
        id = state.getString("inventoryId");
        shelf = state.getString("inventoryShelf");
        intake = (StockIntake) state.getSerializable("inventoryIntake");
        book = unpack(state.getBundle("inventoryBook"));
        if (hasPending())
            message.setValue("Có thao tác chưa xác nhận. Hãy thử lại để kiểm tra kết quả.");
    }

    public void save(Bundle state) {
        state.putString("completionId", completionId);
        state.putString("completedAction", completedAction);
        state.putBundle("completedBook", pack(completedBook));
        state.putSerializable("completedIntake", completedIntake);
        state.putString("journalId", journalId);
        state.putString("inventorySelected", selectedId);
        state.putString("inventoryOwner", ownerUid);
        state.putString("inventoryAction", action);
        state.putString("inventoryId", id);
        state.putString("inventoryShelf", shelf);
        state.putSerializable("inventoryIntake", intake);
        state.putString("draftKind", draftKind);
        state.putString("draftId", draftId);
        state.putBundle("formDraft", formDraft);
        state.putBundle("intakeDraft", intakeDraft);
        state.putBoolean("draftCreating", draftCreating);
        state.putBundle("draftBook", pack(draftBook));
        state.putBundle("inventoryBook", pack(book));
    }

    private static Bundle pack(BookTitle book) {
        if (book == null) return null;
        Bundle b = new Bundle();
        b.putString("id", book.bookTitleId);
        b.putString("title", book.title);
        b.putString("author", book.author);
        b.putString("categoryId", book.categoryId);
        b.putString("category", book.category);
        b.putInt("totalCopies", book.totalCopies);
        b.putInt("availableCopies", book.availableCopies);
        b.putBoolean("isHidden", book.isHidden);
        b.putString("publisher", book.publisher);
        b.putString("isbn", book.isbn);
        b.putString("cover", book.coverUrl);
        b.putString("description", book.description);
        if (book.publishYear != null) b.putInt("year", book.publishYear);
        return b;
    }

    private static BookTitle unpack(Bundle b) {
        if (b == null) return null;
        BookTitle book = new BookTitle();
        book.bookTitleId = b.getString("id");
        book.title = b.getString("title");
        book.author = b.getString("author");
        book.categoryId = b.getString("categoryId");
        book.category = b.getString("category");
        book.totalCopies = b.getInt("totalCopies");
        book.availableCopies = b.getInt("availableCopies");
        book.isHidden = b.getBoolean("isHidden");
        book.publisher = b.getString("publisher");
        book.isbn = b.getString("isbn");
        book.coverUrl = b.getString("cover");
        book.description = b.getString("description");
        if (b.containsKey("year")) book.publishYear = b.getInt("year");
        return book;
    }
}
