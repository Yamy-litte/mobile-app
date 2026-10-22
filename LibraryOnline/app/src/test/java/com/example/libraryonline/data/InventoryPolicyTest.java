package com.example.libraryonline.data;

import com.example.libraryonline.data.inventory.InventoryPolicy;
import com.example.libraryonline.data.inventory.StockIntake;
import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.BookTitle;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryPolicyTest {
    private BookCopy copy(String status) {
        BookCopy c=new BookCopy(); c.copyStatus=status; c.barcode="BOOK-009"; return c;
    }
    private BookTitle title() {
        BookTitle b=new BookTitle(); b.title="Sách";b.author="Tác giả";b.categoryId="cat";
        b.publisher="NXB";b.coverUrl="";return b;
    }
    @Test public void onlyVisibleReadyUnreservedCopiesAreAvailable() {
        assertTrue(InventoryPolicy.isAvailable(copy("SanSang")));
        for(String s:Arrays.asList("DangSuaChua","ThanhLy","DaMuon","ChoLay","DaDatTruoc","DaMat"))
            assertFalse(InventoryPolicy.isAvailable(copy(s)));
        BookCopy c=copy("SanSang");c.isHidden=true;assertFalse(InventoryPolicy.isAvailable(c));
        c.isHidden=false;c.priorityUserId="reader";assertFalse(InventoryPolicy.isAvailable(c));
    }
    @Test public void retirementCannotDiscardBorrowedHeldHiddenOrPriorityCopies() {
        InventoryPolicy.requireRetirable(copy("SanSang"));
        for(String s:Arrays.asList("DaMuon","ChoLay","DaDatTruoc","DangSuaChua","ThanhLy","DaMat"))
            assertThrows(IllegalArgumentException.class,()->InventoryPolicy.requireRetirable(copy(s)));
        BookCopy c=copy("SanSang");c.priorityUserId="reader";
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.requireRetirable(c));
    }
    @Test public void onlyHiddenRepairCopiesWithoutPriorityCanBeRestored() {
        BookCopy candidate=copy("DangSuaChua");
        candidate.isHidden=true;
        assertTrue(InventoryPolicy.canRestore(candidate));
        InventoryPolicy.requireRestorable(candidate);
        candidate.priorityUserId="reader";
        assertFalse(InventoryPolicy.canRestore(candidate));
        assertThrows(IllegalArgumentException.class,
                ()->InventoryPolicy.requireRestorable(candidate));
        candidate.priorityUserId=null;
        for(String state:Arrays.asList("ThanhLy","DaMat","DaMuon","ChoLay","DaDatTruoc","SanSang")) {
            candidate.copyStatus=state;
            assertFalse(InventoryPolicy.canRestore(candidate));
        }
        candidate.copyStatus="DangSuaChua";candidate.isHidden=false;
        assertFalse(InventoryPolicy.canRestore(candidate));
    }
    @Test public void validatesBoundedIntakeAndShelf() {
        InventoryPolicy.validateIntake(1,"A");InventoryPolicy.validateIntake(5,"A");
        for(int n:new int[]{0,-1,6,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->InventoryPolicy.validateIntake(n,"A"));
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.validateIntake(1," "));
    }
    @Test public void requiresRealCategoryInsteadOfDerivingDisplayName() {
        BookTitle b=title();InventoryPolicy.validateTitle(b);b.categoryId="";b.category="Văn học";
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.validateTitle(b));
    }
    @Test public void invalidYearAndNonWebCoverRejected() {
        BookTitle b=title();b.publishYear=-1;
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.validateTitle(b));
        b.publishYear=2020;b.coverUrl="file:///private";
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.validateTitle(b));
    }
    @Test public void stockAuditIncludesRetiredHistoryAndDetectsMismatch() {
        BookTitle b=title();b.totalCopies=2;b.availableCopies=1;
        InventoryPolicy.audit(b,Arrays.asList(copy("SanSang"),copy("ThanhLy")));
        b.availableCopies=2;
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.audit(b,Arrays.asList(copy("SanSang"),copy("ThanhLy"))));
    }
    @Test public void barcodeNeverReusesRetiredOrNonSequentialSuffix() {
        assertEquals(10,InventoryPolicy.nextSequence("BOOK",Arrays.asList("BOOK-001","BOOK-009","legacy")));
        assertEquals(1,InventoryPolicy.nextSequence("BOOK",Collections.singletonList("OTHER-099")));
        assertThrows(IllegalArgumentException.class,()->InventoryPolicy.nextSequence("BOOK",Collections.singletonList("BOOK-2147483647")));
    }
    @Test public void preparedIntakeKeepsIdsStableAcrossRetryAndDistinctAcrossOperations() {
        StockIntake a=new StockIntake("op-a",2,"A"); StockIntake b=new StockIntake("op-b",2,"A");
        assertEquals(a.copyId(0),a.copyId(0)); assertNotEquals(a.copyId(0),b.copyId(0));
        a.allocate("BOOK",10);assertEquals("BOOK-010",a.barcode(0));assertEquals("BOOK-011",a.barcode(1));
        a.allocate("BOOK",20);assertEquals("BOOK-010",a.barcode(0));
    }
    @Test public void intakeRecoversAllocatedBarcodeFromOwnCopyAfterProcessDeath() {
        StockIntake restored=new StockIntake("op",2,"A");
        BookCopy own=copy("SanSang");own.copyId=restored.copyId(0);own.bookTitleId="BOOK";own.barcode="BOOK-008";
        restored.allocateFromCopies("BOOK",Arrays.asList(own));
        assertEquals("BOOK-008",restored.barcode(0));assertEquals("BOOK-009",restored.barcode(1));
    }
}
