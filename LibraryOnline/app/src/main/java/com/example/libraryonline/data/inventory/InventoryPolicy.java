package com.example.libraryonline.data.inventory;

import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.BookTitle;
import java.net.URI;
import java.util.Collection;
import java.util.List;

/** Shared, side-effect-free inventory validation. Does not invent or repair cloud data. */
public final class InventoryPolicy {
    public static final int MAX_INTAKE=5;
    private InventoryPolicy() {}
    public static String required(String value,String label) {
        if(value==null || value.trim().isEmpty())throw new IllegalArgumentException("Vui lòng nhập " + label + ".");
        return value.trim();
    }
    public static void validateTitle(BookTitle book) {
        if(book==null)throw new IllegalArgumentException("Thiếu đầu sách.");
        required(book.title,"tên sách");required(book.author,"tác giả");required(book.categoryId,"thể loại");
        required(book.publisher,"nhà xuất bản");
        if(book.publishYear!=null && (book.publishYear<1 || book.publishYear>9999))
            throw new IllegalArgumentException("Năm xuất bản phải từ 1 đến 9999.");
        if(book.coverUrl!=null && !book.coverUrl.trim().isEmpty()) {
            try {
                URI uri=new URI(book.coverUrl.trim());
                if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null)
                    throw new IllegalArgumentException("Ảnh bìa phải là địa chỉ HTTPS hợp lệ.");
            } catch(java.net.URISyntaxException e) {throw new IllegalArgumentException("Địa chỉ ảnh bìa không hợp lệ.");}
        }
    }
    public static void validateIntake(int quantity,String shelf) {
        if(quantity<1 || quantity>MAX_INTAKE)throw new IllegalArgumentException("Mỗi lần nhập từ 1 đến 5 cuốn.");
        required(shelf,"vị trí kệ");
    }
    public static boolean isAvailable(BookCopy c) {
        return c!=null && "SanSang".equals(c.copyStatus) && !c.isHidden
                && (c.priorityUserId==null || c.priorityUserId.isEmpty());
    }
    public static void requireRetirable(BookCopy c) {
        if(!isAvailable(c))throw new IllegalArgumentException("Chỉ được ẩn/thanh lý cuốn sẵn sàng, không có người đang giữ hoặc được ưu tiên.");
    }
    public static void audit(BookTitle title,List<BookCopy> copies) {
        long available=0;for(BookCopy copy:copies)if(isAvailable(copy))available++;
        if(title.totalCopies!=copies.size() || title.availableCopies!=available)
            throw new IllegalArgumentException("Số lượng đầu sách không khớp các bản sách. Cần đối soát dữ liệu trước khi thay đổi kho.");
    }
    public static int nextSequence(String titleId,Collection<String> barcodes) {
        long maximum=0;String prefix=titleId+"-";
        for(String barcode:barcodes) {
            if(barcode==null || !barcode.startsWith(prefix))continue;
            String tail=barcode.substring(prefix.length());
            if(!tail.matches("[0-9]+"))continue;
            try {maximum=Math.max(maximum,Long.parseLong(tail));}
            catch(NumberFormatException e){throw new IllegalArgumentException("Dải mã sách vượt giới hạn.");}
        }
        if(maximum>=Integer.MAX_VALUE-MAX_INTAKE)throw new IllegalArgumentException("Dải mã sách vượt giới hạn.");
        return (int)maximum+1;
    }
}
