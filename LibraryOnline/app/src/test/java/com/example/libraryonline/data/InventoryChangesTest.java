package com.example.libraryonline.data;
import com.example.libraryonline.data.inventory.*;
import com.example.libraryonline.data.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class InventoryChangesTest {
    private BookTitle title(){BookTitle b=new BookTitle();b.bookTitleId="BOOK";b.title="Title";b.author="Author";b.publisher="NXB";b.categoryId="cat";b.totalCopies=1;b.availableCopies=1;return b;}
    private BookCopy copy(){BookCopy c=new BookCopy();c.copyId="old";c.bookTitleId="BOOK";c.barcode="BOOK-001";c.copyStatus="SanSang";c.shelfLocation="A";return c;}
    @Test public void intakeWritesCopiesAndCountersTogetherAndRetryIsNoop(){
        BookTitle b=title();StockIntake intake=new StockIntake("op",2,"A");intake.allocate("BOOK",2);
        Map<String,Map<String,Object>> writes=InventoryChanges.intake(b,Arrays.asList(copy()),intake);
        assertEquals(3,writes.size());assertEquals(3,writes.get("BookTitles/BOOK").get("totalCopies"));
        List<BookCopy> after=new ArrayList<>();after.add(copy());
        for(int i=0;i<2;i++){BookCopy c=copy();c.copyId=intake.copyId(i);c.barcode=intake.barcode(i);after.add(c);}
        b.totalCopies=3;b.availableCopies=3;
        assertTrue(InventoryChanges.intake(b,after,intake).isEmpty());
    }
    @Test public void differentOperationCannotReuseBarcode(){
        StockIntake intake=new StockIntake("op",1,"A");intake.allocate("BOOK",1);
        assertThrows(IllegalArgumentException.class,()->InventoryChanges.intake(title(),Arrays.asList(copy()),intake));
    }
    @Test public void partialPreviousIntakeRequiresReconciliation(){
        StockIntake intake=new StockIntake("op",2,"A");intake.allocate("BOOK",1);BookCopy c=copy();c.copyId=intake.copyId(0);
        assertThrows(IllegalArgumentException.class,()->InventoryChanges.intake(title(),Arrays.asList(c),intake));
    }
    @Test public void retireKeepsTotalAndHistoryAndSecondCallDoesNotDecrement(){
        BookTitle b=title();BookCopy c=copy();
        Map<String,Map<String,Object>> w=InventoryChanges.transition(b,Arrays.asList(c),"old","ThanhLy");
        assertEquals(0,w.get("BookTitles/BOOK").get("availableCopies"));
        assertFalse(w.get("BookTitles/BOOK").containsKey("totalCopies"));
        assertEquals("ThanhLy",w.get("BookCopies/old").get("copyStatus"));
        c.copyStatus="ThanhLy";c.isHidden=true;b.availableCopies=0;
        assertTrue(InventoryChanges.transition(b,Arrays.asList(c),"old","ThanhLy").isEmpty());
    }
    @Test public void restoringRepairCopyUpdatesParentExactlyOnceAndKeepsHistory(){
        BookTitle b=title();BookCopy c=copy();
        c.copyStatus="DangSuaChua";c.isHidden=true;b.availableCopies=0;
        Map<String,Map<String,Object>> writes=InventoryChanges.restore(b,Arrays.asList(c),"old");
        assertEquals(2,writes.size());
        assertEquals(1,writes.get("BookTitles/BOOK").get("availableCopies"));
        assertFalse(writes.get("BookTitles/BOOK").containsKey("totalCopies"));
        assertEquals("SanSang",writes.get("BookCopies/old").get("copyStatus"));
        assertEquals(false,writes.get("BookCopies/old").get("isHidden"));
        assertFalse(writes.get("BookCopies/old").containsKey("barcode"));
        c.copyStatus="SanSang";c.isHidden=false;b.availableCopies=1;
        assertTrue(InventoryChanges.restore(b,Arrays.asList(c),"old").isEmpty());
    }
    @Test public void restoreRefusesRetiredLostBorrowedOrHiddenParent(){
        BookTitle b=title();b.availableCopies=0;
        BookCopy c=copy();c.isHidden=true;
        for(String state:Arrays.asList("ThanhLy","DaMat","DaMuon","ChoLay","DaDatTruoc")){
            c.copyStatus=state;
            assertThrows(IllegalArgumentException.class,
                    ()->InventoryChanges.restore(b,Arrays.asList(c),"old"));
        }
        c.copyStatus="DangSuaChua";
        c.priorityUserId="reader";
        assertThrows(IllegalArgumentException.class,
                ()->InventoryChanges.restore(b,Arrays.asList(c),"old"));
        c.priorityUserId=null;b.isHidden=true;
        assertThrows(IllegalArgumentException.class,
                ()->InventoryChanges.restore(b,Arrays.asList(c),"old"));
        b.isHidden=false;b.availableCopies=1;
        assertThrows(IllegalArgumentException.class,
                ()->InventoryChanges.restore(b,Arrays.asList(c),"old"));
        assertThrows(IllegalArgumentException.class,
                ()->InventoryChanges.restore(b,Arrays.asList(c),"unknown"));
    }
    @Test public void approvedOnlineSlotProtectsPhysicalReadyCopyFromRetirement() {
        BookTitle b=title();b.availableCopies=0;b.reservedOnlineCopies=1;
        assertThrows(IllegalArgumentException.class,
                ()->InventoryChanges.transition(b,Arrays.asList(copy()),"old","ThanhLy"));
    }
    @Test public void inconsistentCountersBlockStockChanges(){
        BookTitle b=title();b.availableCopies=0;
        assertThrows(IllegalArgumentException.class,()->InventoryChanges.transition(b,Arrays.asList(copy()),"old","ThanhLy"));
    }
    @Test public void metadataPatchCannotOverwriteCountersOrVisibility(){
        Map<String,Object> patch=InventoryChanges.metadata(title());
        assertFalse(patch.containsKey("totalCopies"));assertFalse(patch.containsKey("availableCopies"));
        assertFalse(patch.containsKey("isHidden"));assertFalse(patch.containsKey("category"));
        assertEquals("cat",patch.get("categoryId"));
    }
}
