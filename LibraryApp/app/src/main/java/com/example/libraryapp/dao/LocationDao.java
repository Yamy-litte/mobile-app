package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Location;
import java.util.ArrayList;
import java.util.List;

public class LocationDao {
    private final LibraryDbHelper helper;
    public LocationDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(Location x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_LOCATIONS,null,values(x));}
    public int update(Location x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_LOCATIONS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_LOCATIONS,"id=?",new String[]{String.valueOf(id)});}
    public Location getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LOCATIONS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<Location> getAll(){List<Location> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LOCATIONS,null,null,null,null,null,"building ASC, floor ASC, shelf_number ASC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(Location x){ContentValues v=new ContentValues();v.put("building",x.getBuilding());v.put("floor",x.getFloor());v.put("shelf_number",x.getShelfNumber());return v;}
    private Location read(Cursor c){return new Location(c.getInt(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("building")),c.getString(c.getColumnIndexOrThrow("floor")),c.getString(c.getColumnIndexOrThrow("shelf_number")));}
    public void close(){helper.close();}
}
