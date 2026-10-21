package com.example.libraryonline.data.inventory;

import com.example.libraryonline.data.model.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Authoritative online-only inventory writes. All stock writes include the parent title. */
public final class InventoryRepository {
    private static final Executor DIRECT=Runnable::run;
    private final FirebaseFirestore db;
    public InventoryRepository(){this(FirebaseFirestore.getInstance());}
    public InventoryRepository(FirebaseFirestore db){this.db=db;}
    private DocumentReference titleRef(String id){return db.collection("BookTitles").document(InventoryPolicy.required(id,"mã đầu sách"));}
    public Task<Void> createTitle(BookTitle book,StockIntake intake) {
        try {InventoryPolicy.validateTitle(book);titleRef(book.bookTitleId);intake.allocate(book.bookTitleId,1);}
        catch(Exception e){return Tasks.forException(e);}
        return stock(book.bookTitleId,book,intake,null,null);
    }
    public Task<Void> addCopies(String titleId,StockIntake intake) {return stock(titleId,null,intake,null,null);}
    public Task<Void> hideCopy(String id){return changeCopy(id,"DangSuaChua");}
    public Task<Void> retireCopy(String id){return changeCopy(id,"ThanhLy");}
    private Task<Void> changeCopy(String id,String state) {
        return db.collection("BookCopies").document(id).get(Source.SERVER).onSuccessTask(DIRECT,doc -> {
            if(!doc.exists())return Tasks.forException(new IllegalArgumentException("Không tìm thấy cuốn sách."));
            return stock(doc.getString("bookTitleId"),null,null,id,state);
        });
    }
    private Task<Void> stock(String id,BookTitle creating,StockIntake intake,String copyId,String target) {
        DocumentReference parent=titleRef(id);
        // Read parent BEFORE the query. Transaction rechecks this version plus every queried copy.
        return parent.get(Source.SERVER).onSuccessTask(DIRECT,before ->
            db.collection("BookCopies").get(Source.SERVER).onSuccessTask(DIRECT,all -> {
                List<DocumentSnapshot> related=new ArrayList<>();List<BookCopy> allCopies=new ArrayList<>();
                for(DocumentSnapshot d:all) {
                    allCopies.add(readCopy(d));
                    if(id.equals(d.getString("bookTitleId")))related.add(d);
                }
                if(intake!=null)intake.allocateFromCopies(id,allCopies);
                return db.runTransaction(tx -> {
                    // Resolve operation identity before category/version checks: a prior response may have been lost.
                    if(intake!=null && completedIntake(tx,id,intake))return (Void)null;
                    DocumentSnapshot current=tx.get(parent);
                    if(!Objects.equals(before.getData(),current.getData()))
                        throw new IllegalArgumentException("Kho vừa thay đổi. Hãy thử lại để đọc dữ liệu mới.");
                    if(!current.exists() && creating==null)throw new IllegalArgumentException("Đầu sách không còn tồn tại.");
                    BookTitle book=current.exists()?readTitle(current):creating;
                    if(intake!=null) {
                        DocumentSnapshot category=tx.get(db.collection("Categories").document(book.categoryId));
                        if(!category.exists() || !Boolean.TRUE.equals(category.getBoolean("isActive")))
                            throw new IllegalArgumentException("Thể loại không còn hoạt động.");
                    }
                    List<BookCopy> copies=new ArrayList<>();
                    Set<String> readIds=new HashSet<>();
                    for(DocumentSnapshot pre:related) {
                        DocumentSnapshot fresh=tx.get(pre.getReference());readIds.add(pre.getId());
                        if(!Objects.equals(pre.getData(),fresh.getData()))
                            throw new IllegalArgumentException("Bản sách vừa thay đổi. Hãy thử lại.");
                        copies.add(readCopy(fresh));
                    }
                    if(intake!=null)for(int i=0;i<intake.quantity;i++) {
                        // completedIntake already read every destination before any writes.
                        for(DocumentSnapshot other:all)if(!id.equals(other.getString("bookTitleId"))
                                && intake.barcode(i).equals(other.getString("barcode")))
                            throw new IllegalArgumentException("Barcode trùng đầu sách khác. Cần đối soát.");
                    }
                    if(creating!=null && current.exists()) {
                        boolean completed=true;
                        for(int i=0;i<intake.quantity;i++)if(!readIds.contains(intake.copyId(i)))completed=false;
                        if(!completed)throw new IllegalArgumentException("Mã đầu sách đã tồn tại.");
                    }
                    Map<String,Map<String,Object>> writes=intake!=null
                            ?InventoryChanges.intake(book,copies,intake):InventoryChanges.transition(book,copies,copyId,target);
                    for(Map.Entry<String,Map<String,Object>> entry:writes.entrySet()) {
                        Map<String,Object> data=new HashMap<>(entry.getValue());data.put("updatedAt",FieldValue.serverTimestamp());
                        boolean newCopy=intake!=null && entry.getKey().startsWith("BookCopies/");
                        if(newCopy || !current.exists())data.put("createdAt",FieldValue.serverTimestamp());
                        if(!current.exists() && entry.getKey().startsWith("BookTitles/")) {
                            data.putAll(InventoryChanges.metadata(book));data.put("isHidden",false);
                        }
                        DocumentReference ref=db.document(entry.getKey());
                        if(newCopy || !current.exists())tx.set(ref,data);else tx.update(ref,data);
                    }
                    return (Void)null;
                });
            }));
    }
    private boolean completedIntake(Transaction tx,String id,StockIntake intake) throws FirebaseFirestoreException {
        int count=0;
        for(int i=0;i<intake.quantity;i++) {
            DocumentSnapshot copy=tx.get(db.collection("BookCopies").document(intake.copyId(i)));
            if(copy.exists()) {
                if(!id.equals(copy.getString("bookTitleId")))throw new IllegalStateException("Mã thao tác gắn đầu sách khác. Cần đối soát.");
                count++;
            }
        }
        if(count>0 && count<intake.quantity)throw new IllegalStateException("Thao tác chỉ có một phần bản sách. Cần đối soát; không nhập lại bằng mã mới.");
        return count==intake.quantity;
    }
    /**
     * Resolve a definitively rejected retry without dropping an ambiguous operation ID.
     * If absent, touching the category version fences older outstanding stock transactions:
     * every intake read that category. No counters, copies or business states are changed.
     */
    public Task<Boolean> reconcileIntake(String id,BookTitle draft,StockIntake intake) {
        return db.runTransaction(tx -> {
            if(completedIntake(tx,id,intake))return true;
            DocumentSnapshot title=tx.get(titleRef(id));
            String categoryId=title.exists()?title.getString("categoryId"):(draft==null?null:draft.categoryId);
            InventoryPolicy.required(categoryId,"thể loại để đối chiếu thao tác");
            DocumentReference ref=db.collection("Categories").document(categoryId);
            DocumentSnapshot category=tx.get(ref);
            // A missing category already invalidates transactions that read its former version.
            if(category.exists())tx.update(ref,"updatedAt",FieldValue.serverTimestamp());
            return false;
        });
    }
    public Task<Void> updateTitle(BookTitle book) {
        final Map<String,Object> metadata;
        try {metadata=InventoryChanges.metadata(book);}catch(Exception e){return Tasks.forException(e);}
        return db.runTransaction(tx -> {
            DocumentReference ref=titleRef(book.bookTitleId);DocumentSnapshot old=tx.get(ref);
            DocumentSnapshot category=tx.get(db.collection("Categories").document(book.categoryId));
            if(!old.exists())throw new IllegalArgumentException("Không tìm thấy đầu sách.");
            if(!category.exists() || !Boolean.TRUE.equals(category.getBoolean("isActive")))throw new IllegalArgumentException("Thể loại không còn hoạt động.");
            Map<String,Object> changes=new HashMap<>(metadata);changes.put("updatedAt",FieldValue.serverTimestamp());
            tx.update(ref,changes);return null;
        });
    }
    public Task<Void> hideTitle(String id) {
        return db.runTransaction(tx -> {
            DocumentReference ref=titleRef(id);DocumentSnapshot old=tx.get(ref);
            if(!old.exists())throw new IllegalArgumentException("Không tìm thấy đầu sách.");
            tx.update(ref,"isHidden",true,"updatedAt",FieldValue.serverTimestamp());return null;
        });
    }
    public Task<Void> updateShelf(String id,String shelf) {
        try {InventoryPolicy.required(shelf,"vị trí kệ");}catch(Exception e){return Tasks.forException(e);}
        return db.runTransaction(tx -> {
            DocumentReference ref=db.collection("BookCopies").document(id);DocumentSnapshot old=tx.get(ref);
            if(!old.exists())throw new IllegalArgumentException("Không tìm thấy cuốn sách.");
            tx.update(ref,"shelfLocation",shelf.trim(),"updatedAt",FieldValue.serverTimestamp());return null;
        });
    }
    public static BookTitle readTitle(DocumentSnapshot d) {
        BookTitle b=new BookTitle();b.bookTitleId=d.getId();b.title=d.getString("title");b.author=d.getString("author");
        b.categoryId=d.getString("categoryId");b.isbn=d.getString("isbn");b.publisher=d.getString("publisher");
        b.description=d.getString("description");b.coverUrl=d.getString("coverUrl");
        Long year=d.getLong("publishYear");b.publishYear=year==null?null:Math.toIntExact(year);
        Long total=d.getLong("totalCopies"),available=d.getLong("availableCopies");
        if(total==null || available==null)throw new IllegalArgumentException("Thiếu số lượng đầu sách. Cần đối soát.");
        b.totalCopies=Math.toIntExact(total);b.availableCopies=Math.toIntExact(available);
        b.isHidden=Boolean.TRUE.equals(d.getBoolean("isHidden"));return b;
    }
    public static BookCopy readCopy(DocumentSnapshot d) {
        BookCopy c=new BookCopy();c.copyId=d.getId();c.bookTitleId=d.getString("bookTitleId");
        c.barcode=d.getString("barcode");c.shelfLocation=d.getString("shelfLocation");c.copyStatus=d.getString("copyStatus");
        c.priorityUserId=d.getString("priorityUserId");c.isHidden=Boolean.TRUE.equals(d.getBoolean("isHidden"));return c;
    }
}
