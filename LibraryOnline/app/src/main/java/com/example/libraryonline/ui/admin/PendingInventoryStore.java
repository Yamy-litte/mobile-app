package com.example.libraryonline.ui.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;
import com.example.libraryonline.data.inventory.StockIntake;
import java.util.Objects;

/** Non-secret, UID-scoped retry identity. Persist before network writes, never store auth authority. */
public final class PendingInventoryStore {
    private static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    public PendingInventoryStore(Context context){preferences=context.getApplicationContext().getSharedPreferences("inventory_pending_v1",Context.MODE_PRIVATE);}
    private static final java.util.Set<String> running=new java.util.HashSet<>();
    public boolean claim(String uid){synchronized(LOCK){return running.add(uid);}}
    public void release(String uid){synchronized(LOCK){running.remove(uid);}}
    public Bundle load(String uid) {
        synchronized(LOCK) {
            String value=preferences.getString(uid,null);if(value==null)return null;
            Parcel parcel=Parcel.obtain();
            try {byte[] bytes=Base64.decode(value,Base64.NO_WRAP);parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);return parcel.readBundle(StockIntake.class.getClassLoader());}
            catch(Exception error){throw new IllegalStateException("Không đọc được thao tác kho đang chờ. Cần kiểm tra dữ liệu local trước khi nhập tiếp.",error);}
            finally {parcel.recycle();}
        }
    }
    public void save(String uid,Bundle state) {
        synchronized(LOCK) {
            Bundle old=load(uid);
            if(old!=null && !Objects.equals(old.getString("journalId"),state.getString("journalId")))
                throw new IllegalStateException("Có thao tác kho khác chưa xác nhận. Mở lại màn hình kho để tiếp tục.");
            Parcel parcel=Parcel.obtain();
            try {parcel.writeBundle(state);String encoded=Base64.encodeToString(parcel.marshall(),Base64.NO_WRAP);
                if(!preferences.edit().putString(uid,encoded).commit())throw new IllegalStateException("Không lưu được trạng thái thao tác trên thiết bị. Chưa gửi dữ liệu lên Firebase.");}
            finally {parcel.recycle();}
        }
    }
    public boolean clear(String uid,String journalId) {
        synchronized(LOCK) {
            Bundle old=load(uid);if(old==null)return true;
            return Objects.equals(journalId,old.getString("journalId")) && preferences.edit().remove(uid).commit();
        }
    }
}
