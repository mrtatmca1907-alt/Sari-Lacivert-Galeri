package com.atmaca.teraboxfast;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

public class IndexDb extends SQLiteOpenHelper {
    private static final String DB="terabox_index.db";
    public IndexDb(Context c){ super(c,DB,null,4); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE ui_text(id INTEGER PRIMARY KEY AUTOINCREMENT, text_value TEXT NOT NULL, first_seen INTEGER, last_seen INTEGER, UNIQUE(text_value))");
        db.execSQL("CREATE TABLE folders(id INTEGER PRIMARY KEY AUTOINCREMENT, folder_name TEXT NOT NULL UNIQUE, first_seen INTEGER, last_seen INTEGER)");
        db.execSQL("CREATE TABLE image_queue(id INTEGER PRIMARY KEY AUTOINCREMENT, folder_name TEXT NOT NULL, frame_sig TEXT NOT NULL, left_px INTEGER, top_px INTEGER, right_px INTEGER, bottom_px INTEGER, confidence REAL, state TEXT NOT NULL DEFAULT 'SEEN', first_seen INTEGER, last_seen INTEGER, UNIQUE(folder_name,frame_sig,left_px,top_px,right_px,bottom_px))");
        db.execSQL("CREATE INDEX idx_image_queue_folder ON image_queue(folder_name)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<4){
            db.execSQL("DROP TABLE IF EXISTS items");
            db.execSQL("DROP TABLE IF EXISTS selected_folders");
            db.execSQL("DROP TABLE IF EXISTS folders");
            db.execSQL("DROP TABLE IF EXISTS ui_text");
            db.execSQL("DROP TABLE IF EXISTS image_queue");
            onCreate(db);
        }
    }

    public void saveUiText(String s){
        s=clean(s); if(s.isEmpty())return;
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("text_value",s); v.put("first_seen",now); v.put("last_seen",now);
        SQLiteDatabase db=getWritableDatabase();
        long id=db.insertWithOnConflict("ui_text",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1){
            ContentValues u=new ContentValues();u.put("last_seen",now);
            db.update("ui_text",u,"text_value=?",new String[]{s});
        }
    }

    public void saveFolder(String name){
        name=clean(name); if(name.isEmpty())return;
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("folder_name",name);v.put("first_seen",now);v.put("last_seen",now);
        SQLiteDatabase db=getWritableDatabase();
        long id=db.insertWithOnConflict("folders",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1){
            ContentValues u=new ContentValues();u.put("last_seen",now);
            db.update("folders",u,"folder_name=?",new String[]{name});
        }
    }

    public void saveImageCandidate(String folder,String sig,int l,int t,int r,int b,float conf){
        folder=clean(folder); sig=clean(sig);
        if(folder.isEmpty()||sig.isEmpty())return;
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("folder_name",folder);v.put("frame_sig",sig);
        v.put("left_px",l);v.put("top_px",t);v.put("right_px",r);v.put("bottom_px",b);
        v.put("confidence",conf);v.put("state","SEEN");v.put("first_seen",now);v.put("last_seen",now);
        SQLiteDatabase db=getWritableDatabase();
        long id=db.insertWithOnConflict("image_queue",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id==-1){
            ContentValues u=new ContentValues();u.put("last_seen",now);u.put("confidence",conf);
            db.update("image_queue",u,"folder_name=? AND frame_sig=? AND left_px=? AND top_px=? AND right_px=? AND bottom_px=?",
                new String[]{folder,sig,String.valueOf(l),String.valueOf(t),String.valueOf(r),String.valueOf(b)});
        }
    }

    public int folderCount(){ return scalar("SELECT COUNT(*) FROM folders"); }
    public int imageCount(){ return scalar("SELECT COUNT(*) FROM image_queue"); }
    public int uiTextCount(){ return scalar("SELECT COUNT(*) FROM ui_text"); }

    private int scalar(String sql){
        Cursor c=getReadableDatabase().rawQuery(sql,null);
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    public void clear(){
        SQLiteDatabase db=getWritableDatabase();
        db.delete("ui_text",null,null);
        db.delete("folders",null,null);
        db.delete("image_queue",null,null);
    }

    private static String clean(String s){
        if(s==null)return "";
        s=s.replace('\n',' ').replace('\r',' ').trim();
        while(s.contains("  "))s=s.replace("  "," ");
        if(s.length()>500)s=s.substring(0,500);
        return s;
    }
}
