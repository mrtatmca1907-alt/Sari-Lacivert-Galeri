package com.atmaca.files;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CatalogDb extends SQLiteOpenHelper {
    private static final int DB_VERSION = 2;

    public CatalogDb(Context context) { super(context, "atmaca.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries(path TEXT PRIMARY KEY,type TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,date TEXT,ext TEXT)");
        db.execSQL("CREATE INDEX idx_entries_path ON entries(path)");
        db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT,json TEXT NOT NULL)");
        createUploadQueue(db);
    }

    private static void createUploadQueue(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS upload_queue(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "local_path TEXT NOT NULL," +
                "remote_dir TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "mime TEXT NOT NULL," +
                "size INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_upload_created ON upload_queue(created_at,id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createUploadQueue(db);
    }

    public void replaceAll(List<CatalogEntry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            for (CatalogEntry e : entries) put(db, e);
            for (PendingUpload p : pendingUploads(db)) put(db, stagedCatalogEntry(p));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void upsertEntry(CatalogEntry e) { put(getWritableDatabase(), e); }

    private static CatalogEntry stagedCatalogEntry(PendingUpload p) {
        String ext = "";
        int dot = p.name.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < p.name.length()) ext = p.name.substring(dot + 1);
        return new CatalogEntry("DOSYA", p.name, p.remotePath(), p.size, "BEKLIYOR", ext);
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
        getWritableDatabase().insertOrThrow("queue", null, v);
        try {
            JSONObject o = new JSONObject(json);
            String op = o.optString("op", "");
            String path = o.optString("path", "/");
            if ("mkdir".equals(op)) applyLocalMkdir(path);
            else if ("delete".equals(op)) applyLocalDelete(path);
            else if ("rename".equals(op)) applyLocalRename(path, o.optString("newName", ""));
            else if ("move".equals(op)) applyLocalMove(path, o.optString("dest", "/"));
        } catch (Exception ignored) {}
    }

    public List<String> pendingQueue() {
        ArrayList<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM queue ORDER BY id", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    public void clearQueue() { getWritableDatabase().delete("queue", null, null); }

    public long addUpload(String localPath, String remoteDir, String name, String mime, long size) {
        ContentValues v = new ContentValues();
        v.put("local_path", localPath);
        v.put("remote_dir", PathUtil.normalize(remoteDir));
        v.put("name", name);
        v.put("mime", mime == null ? "application/octet-stream" : mime);
        v.put("size", Math.max(0L, size));
        v.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertOrThrow("upload_queue", null, v);
        PendingUpload p = new PendingUpload(id, localPath, remoteDir, name, mime, size, System.currentTimeMillis());
        upsertEntry(stagedCatalogEntry(p));
        return id;
    }

    public List<PendingUpload> pendingUploads() { return pendingUploads(getReadableDatabase()); }

    private static List<PendingUpload> pendingUploads(SQLiteDatabase db) {
        ArrayList<PendingUpload> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,local_path,remote_dir,name,mime,size,created_at FROM upload_queue ORDER BY created_at,id", null)) {
            while (c.moveToNext()) out.add(new PendingUpload(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getLong(6)));
        }
        return out;
    }

    public int pendingUploadCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM upload_queue", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public void deleteUpload(long id) {
        getWritableDatabase().delete("upload_queue", "id=?", new String[]{String.valueOf(id)});
    }

    public void applyLocalMkdir(String path) {
        String p = PathUtil.normalize(path);
        String name = p.substring(p.lastIndexOf('/') + 1);
        upsertEntry(new CatalogEntry("KLASOR", name, p, 0L, "BEKLIYOR", ""));
    }

    public void applyLocalDelete(String path) {
        String p = PathUtil.normalize(path);
        getWritableDatabase().delete("entries", "path=? OR path LIKE ?", new String[]{p, p + "/%"});
    }

    public void applyLocalRename(String path, String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        String p = PathUtil.normalize(path);
        String newRoot = PathUtil.child(PathUtil.parent(p), newName);
        rewriteSubtree(p, newRoot, newName);
    }

    public void applyLocalMove(String path, String destDir) {
        String p = PathUtil.normalize(path);
        String name = p.substring(p.lastIndexOf('/') + 1);
        String newRoot = PathUtil.child(destDir, name);
        rewriteSubtree(p, newRoot, name);
    }

    private void rewriteSubtree(String oldRoot, String newRoot, String rootName) {
        SQLiteDatabase db = getWritableDatabase();
        List<CatalogEntry> rows = read("SELECT type,name,path,size,date,ext FROM entries WHERE path=? OR path LIKE ? ORDER BY LENGTH(path)", new String[]{oldRoot, oldRoot + "/%"});
        db.beginTransaction();
        try {
            db.delete("entries", "path=? OR path LIKE ?", new String[]{oldRoot, oldRoot + "/%"});
            for (CatalogEntry e : rows) {
                String suffix = e.path.length() == oldRoot.length() ? "" : e.path.substring(oldRoot.length());
                String path = newRoot + suffix;
                String name = suffix.isEmpty() ? rootName : e.name;
                put(db, new CatalogEntry(e.type, name, path, e.size, e.date, e.extension));
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
