package com.sarilacivert.atmaca;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadWorker extends Worker {
    private static final int CHUNK = 4 * 1024 * 1024;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build();

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Context app = getApplicationContext();
        String base = app.getSharedPreferences("atmaca", Context.MODE_PRIVATE).getString("url", "");
        String token = app.getSharedPreferences("atmaca", Context.MODE_PRIVATE).getString("token", "");
        if (base.isEmpty() || token.isEmpty()) {
            saveError("Sunucu adresi veya anahtar boş");
            return Result.failure();
        }

        while (!isStopped()) {
            QueueDb q = new QueueDb(app);
            try (Cursor c = q.nextPending()) {
                if (!c.moveToFirst()) {
                    saveError("");
                    return Result.success();
                }

                long id = c.getLong(0);
                String uriText = c.getString(1);
                String path = c.getString(2);
                long queuedSize = c.getLong(3);
                long mtime = c.getLong(4);
                Uri uri = Uri.parse(uriText);

                try {
                    long realSize = resolveSize(uri, queuedSize);
                    upload(base, token, uri, path, realSize, mtime);
                    q.done(id);
                    saveError("");
                } catch (Exception e) {
                    q.retry(id);
                    saveError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    return Result.retry();
                }
            }
        }
        return Result.retry();
    }

    private long resolveSize(Uri uri, long hinted) throws Exception {
        if (hinted > 0) return hinted;

        try (AssetFileDescriptor afd = getApplicationContext().getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (afd != null && afd.getLength() >= 0) return afd.getLength();
        } catch (Exception ignored) {}

        long total = 0;
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream in = getApplicationContext().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Dosya açılamadı");
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) total += n;
            }
        }
        return total;
    }

    private void upload(String base, String token, Uri uri, String path, long size, long mtime) throws Exception {
        base = base.replaceAll("/+$", "");

        JSONObject init = new JSONObject();
        init.put("path", path);
        init.put("size", size);
        init.put("mtime", mtime);

        Request req = new Request.Builder()
                .url(base + "/api/upload/session")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(init.toString(), MediaType.parse("application/json")))
                .build();

        JSONObject session;
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new Exception("Oturum hatası HTTP " + r.code());
            if (r.body() == null) throw new Exception("Sunucu boş yanıt verdi");
            session = new JSONObject(r.body().string());
        }

        if (session.optBoolean("complete")) return;

        String uploadId = session.getString("upload_id");
        long offset = session.getLong("offset");
        int chunk = session.optInt("chunk_size", CHUNK);

        try (InputStream in = getApplicationContext().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Dosya açılamadı");

            long skipped = 0;
            while (skipped < offset) {
                long n = in.skip(offset - skipped);
                if (n <= 0) {
                    if (in.read() == -1) break;
                    n = 1;
                }
                skipped += n;
            }
            if (skipped != offset) throw new Exception("Kaldığı yerden devam konumu uyuşmadı");

            byte[] buf = new byte[chunk];
            int n;
            while ((n = readChunk(in, buf)) > 0) {
                byte[] data = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
                Request cr = new Request.Builder()
                        .url(base + "/api/upload/chunk/" + uploadId + "?offset=" + offset)
                        .header("Authorization", "Bearer " + token)
                        .put(RequestBody.create(data, MediaType.parse("application/octet-stream")))
                        .build();

                try (Response rr = client.newCall(cr).execute()) {
                    if (rr.code() == 409) {
                        if (rr.body() == null) throw new Exception("Ofset hatası");
                        JSONObject j = new JSONObject(rr.body().string());
                        long expected = j.getLong("expected_offset");
                        throw new Exception("Yükleme konumu değişti: " + expected);
                    }
                    if (!rr.isSuccessful()) throw new Exception("Parça yükleme hatası HTTP " + rr.code());
                }
                offset += n;
            }
        }

        Request fin = new Request.Builder()
                .url(base + "/api/upload/finish/" + uploadId)
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], null))
                .build();

        try (Response r = client.newCall(fin).execute()) {
            if (!r.isSuccessful()) throw new Exception("Tamamlama hatası HTTP " + r.code());
        }
    }

    private int readChunk(InputStream in, byte[] b) throws Exception {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) break;
            off += n;
            if (n == 0) break;
        }
        return off;
    }

    private void saveError(String msg) {
        getApplicationContext().getSharedPreferences("atmaca", Context.MODE_PRIVATE)
                .edit().putString("last_upload_error", msg == null ? "" : msg).apply();
    }
}
