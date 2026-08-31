package com.atmaca.files;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class CatalogDb extends SQLiteOpenHelper {
    public CatalogDb(Context context) { super(context, "atmaca.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries(path TEXT PRIMARY KEY,type TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,date TEXT,ext TEXT)");
        db.execSQL("CREATE INDEX idx_entries_path ON entries(path)");
        db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT,json TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void replaceAll(List<CatalogEntry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            for (CatalogEntry e : entries) put(db, e);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void put(SQLiteDatabase db, CatalogEntry e) {
        ContentValues v = new ContentValues();
        v.put("path", e.path); v.put("type", e.type); v.put("name", e.name);
        v.put("size", e.size); v.put("date", e.date); v.put("ext", e.extension);
        db.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<CatalogEntry> listChildren(String parent, String query, int limit, int offset) {
        String p = PathUtil.normalize(parent);
        String prefix = "/".equals(p) ? "/" : p + "/";
        String q = query == null ? "" : query.trim();
        String sql = "SELECT type,name,path,size,date,ext FROM entries WHERE path LIKE ? AND path NOT LIKE ?";
        ArrayList<String> args = new ArrayList<>();
        args.add(prefix + "%"); args.add(prefix + "%/%");
        if (!q.isEmpty()) { sql += " AND name LIKE ?"; args.add("%" + q + "%"); }
        sql += " ORDER BY CASE WHEN type='KLASOR' THEN 0 ELSE 1 END, name COLLATE NOCASE LIMIT ? OFFSET ?";
        args.add(String.valueOf(limit)); args.add(String.valueOf(offset));
        return read(sql, args.toArray(new String[0]));
    }

    public List<CatalogEntry> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        return read("SELECT type,name,path,size,date,ext FROM entries WHERE name LIKE ? ORDER BY name COLLATE NOCASE LIMIT ?", new String[]{"%" + q + "%", String.valueOf(limit)});
    }

    private List<CatalogEntry> read(String sql, String[] args) {
        ArrayList<CatalogEntry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(new CatalogEntry(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5)));
        }
        return out;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void addQueue(String json) {
        ContentValues v = new ContentValues(); v.put("json", json);
        getWritableDatabase().insert("queue", null, v);
    }

    public List<String> pendingQueue() {
        ArrayList<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM queue ORDER BY id", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public void clearQueue() { getWritableDatabase().delete("queue", null, null); }
}
