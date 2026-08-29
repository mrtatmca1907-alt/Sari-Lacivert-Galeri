package com.atmaca.gorselpaketleyici.v2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class ScanDb extends SQLiteOpenHelper {
    public ScanDb(Context c) { super(c, "scan_v2.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE files(path TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE)");
        db.execSQL("CREATE INDEX idx_name ON files(name COLLATE NOCASE, path)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void reset() { getWritableDatabase().delete("files", null, null); }

    public boolean add(String path, String name) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("path", path); v.put("name", name);
        return getWritableDatabase().insertWithOnConflict("files", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public long count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM files", null);
        try { return c.moveToFirst() ? c.getLong(0) : 0; } finally { c.close(); }
    }

    public Cursor ordered() {
        return getReadableDatabase().rawQuery("SELECT path,name FROM files ORDER BY name COLLATE NOCASE, path", null);
    }
}
