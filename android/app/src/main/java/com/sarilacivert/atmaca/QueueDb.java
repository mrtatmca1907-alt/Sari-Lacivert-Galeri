package com.sarilacivert.atmaca;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class QueueDb extends SQLiteOpenHelper {
    public QueueDb(Context c) { super(c, "atmaca_queue.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL, remote_path TEXT NOT NULL, size INTEGER NOT NULL, mtime INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', attempts INTEGER NOT NULL DEFAULT 0, UNIQUE(uri,remote_path))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public void enqueue(String uri, String remotePath, long size, long mtime) {
        ContentValues v = new ContentValues();
        v.put("uri", uri); v.put("remote_path", remotePath); v.put("size", size); v.put("mtime", mtime); v.put("status", "pending");
        getWritableDatabase().insertWithOnConflict("queue", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public Cursor nextPending() {
        return getReadableDatabase().rawQuery("SELECT id,uri,remote_path,size,mtime FROM queue WHERE status IN ('pending','retry') ORDER BY id LIMIT 1", null);
    }
    public void done(long id) { getWritableDatabase().delete("queue", "id=?", new String[]{String.valueOf(id)}); }
    public void retry(long id) { getWritableDatabase().execSQL("UPDATE queue SET status='retry', attempts=attempts+1 WHERE id=?", new Object[]{id}); }
    public long pendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM queue", null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
}
