package com.atmaca.teraboxfast;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

public class IndexDb extends SQLiteOpenHelper {
    private static final String DB="terabox_index.db";
    private static final String PREF="tera_index_pref";
    private static final String KEY="enabled";

    public IndexDb(Context c){ super(c,DB,null,2); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, text_value TEXT NOT NULL, content_desc TEXT, class_name TEXT, screen_hint TEXT, first_seen INTEGER, last_seen INTEGER, UNIQUE(text_value,content_desc,class_name,screen_hint))");
        db.execSQL("CREATE INDEX idx_items_text ON items(text_value)");
        db.execSQL("CREATE TABLE selected_folders(id INTEGER PRIMARY KEY AUTOINCREMENT, folder_name TEXT NOT NULL UNIQUE, first_seen INTEGER, last_seen INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2){
            db.execSQL("CREATE TABLE IF NOT EXISTS selected_folders(id INTEGER PRIMARY KEY AUTOINCREMENT, folder_name TEXT NOT NULL UNIQUE, first_seen INTEGER, last_seen INTEGER)");
        }
    }

    public void save(String text,String desc,String cls,String screen){
        text=clean(text); desc=clean(desc); cls=clean(cls); screen=clean(screen);
        if(text.isEmpty() && desc.isEmpty()) return;
        long now=System.currentTimeMillis();
        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();
        v.put("text_value",text); v.put("content_desc",desc); v.put("class_name",cls); v.put("screen_hint",screen);
        v.put("first_seen",now); v.put("last_seen",now);
        long id=db.insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1){
            ContentValues u=new ContentValues(); u.put("last_seen",now);
            db.update("items",u,"text_value=? AND content_desc=? AND class_name=? AND screen_hint=?",
                new String[]{text,desc,cls,screen});
        }
    }

    public void saveSelectedFolder(String name){
        name=clean(name);
        if(name.isEmpty()) return;
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("folder_name",name); v.put("first_seen",now); v.put("last_seen",now);
        SQLiteDatabase db=getWritableDatabase();
        long id=db.insertWithOnConflict("selected_folders",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1){
            ContentValues u=new ContentValues(); u.put("last_seen",now);
            db.update("selected_folders",u,"folder_name=?",new String[]{name});
        }
    }

    public int count(){
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items",null);
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    public int selectedCount(){
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM selected_folders",null);
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    public void clear(){
        SQLiteDatabase db=getWritableDatabase();
        db.delete("items",null,null);
        db.delete("selected_folders",null,null);
    }

    private static String clean(String s){
        if(s==null)return "";
        s=s.replace('\n',' ').replace('\r',' ').trim();
        while(s.contains("  "))s=s.replace("  "," ");
        if(s.length()>500)s=s.substring(0,500);
        return s;
    }

    public static void setEnabled(Context c,boolean e){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(KEY,e).apply();
    }
    public static boolean isEnabled(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY,false);
    }
}
