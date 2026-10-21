package com.example.libraryonline.data.inventory;

import java.io.Serializable;
import java.util.Locale;
import java.util.UUID;

/** Identity is preserved for retries/configuration changes; allocation is fixed before any write. */
public final class StockIntake implements Serializable {
    private static final long serialVersionUID=1L;
    public final String operationId, shelf;
    public final int quantity;
    private String titleId;
    private int firstSequence;
    public StockIntake(int quantity,String shelf){this(UUID.randomUUID().toString(),quantity,shelf);}
    public StockIntake(String operationId,int quantity,String shelf) {
        InventoryPolicy.validateIntake(quantity,shelf);
        this.operationId=InventoryPolicy.required(operationId,"mã thao tác");
        if(!operationId.matches("[A-Za-z0-9-]+"))throw new IllegalArgumentException("Mã thao tác không hợp lệ.");
        this.quantity=quantity;this.shelf=shelf.trim();
    }
    public synchronized void allocate(String id,int first) {
        if(titleId!=null) {
            if(!titleId.equals(id))throw new IllegalArgumentException("Thao tác nhập thuộc đầu sách khác.");
            return;
        }
        if(first<1 || first>Integer.MAX_VALUE-quantity)throw new IllegalArgumentException("Dải mã không hợp lệ.");
        titleId=InventoryPolicy.required(id,"mã đầu sách");firstSequence=first;
    }
    public synchronized void allocateFromCopies(String id,java.util.List<com.example.libraryonline.data.model.BookCopy> copies) {
        if(titleId!=null){allocate(id,firstSequence);return;}
        java.util.List<String> barcodes=new java.util.ArrayList<>();
        for(com.example.libraryonline.data.model.BookCopy c:copies) {
            barcodes.add(c.barcode);
            if(copyId(0).equals(c.copyId)) {
                if(!id.equals(c.bookTitleId) || c.barcode==null || !c.barcode.startsWith(id+"-"))
                    throw new IllegalArgumentException("Mã thao tác đã được dùng bởi dữ liệu khác.");
                try {allocate(id,Integer.parseInt(c.barcode.substring(id.length()+1)));return;}
                catch(NumberFormatException e){throw new IllegalArgumentException("Barcode của lần nhập trước không hợp lệ.");}
            }
        }
        allocate(id,InventoryPolicy.nextSequence(id,barcodes));
    }
    public String copyId(int index) {checkIndex(index);return "copy-"+operationId+"-"+index;}
    public synchronized String barcode(int index) {
        checkIndex(index);
        if(titleId==null)throw new IllegalStateException("Chưa cấp dải mã.");
        return titleId+"-"+String.format(Locale.ROOT,"%03d",firstSequence+index);
    }
    private void checkIndex(int i){if(i<0 || i>=quantity)throw new IllegalArgumentException("Vị trí bản sách không hợp lệ.");}
}
