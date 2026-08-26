package com.sarilacivert.atmaca;

import android.content.Context;
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
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).build();

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        String base = getApplicationContext().getSharedPreferences("atmaca", Context.MODE_PRIVATE).getString("url", "");
        String token = getApplicationContext().getSharedPreferences("atmaca", Context.MODE_PRIVATE).getString("token", "");
        if (base.isEmpty() || token.isEmpty()) return Result.failure();
        while (!isStopped()) {
            QueueDb q = new QueueDb(getApplicationContext());
            try (Cursor c = q.nextPending()) {
                if (!c.moveToFirst()) return Result.success();
                long id=c.getLong(0); String uri=c.getString(1), path=c.getString(2); long size=c.getLong(3), mtime=c.getLong(4);
                try {
                    upload(base, token, Uri.parse(uri), path, size, mtime);
                    q.done(id);
                } catch (Exception e) {
                    q.retry(id);
                    return Result.retry();
                }
            }
        }
        return Result.retry();
    }

    private void upload(String base, String token, Uri uri, String path, long size, long mtime) throws Exception {
        base = base.replaceAll("/+$", "");
        JSONObject init = new JSONObject(); init.put("path", path); init.put("size", size); init.put("mtime", mtime);
        Request req = new Request.Builder().url(base+"/api/upload/session").header("Authorization","Bearer "+token)
                .post(RequestBody.create(init.toString(), MediaType.parse("application/json"))).build();
        JSONObject session;
        try (Response r=client.newCall(req).execute()) { if(!r.isSuccessful()) throw new Exception("session "+r.code()); session=new JSONObject(r.body().string()); }
        if (session.optBoolean("complete")) return;
        String uploadId=session.getString("upload_id"); long offset=session.getLong("offset"); int chunk=session.optInt("chunk_size", CHUNK);

        try (InputStream in=getApplicationContext().getContentResolver().openInputStream(uri)) {
            if(in==null) throw new Exception("uri açılamadı");
            long skipped=0; while(skipped<offset){ long n=in.skip(offset-skipped); if(n<=0) { if(in.read()==-1) break; n=1; } skipped+=n; }
            if(skipped!=offset) throw new Exception("resume offset");
            byte[] buf=new byte[chunk]; int n;
            while((n=readChunk(in,buf))>0){
                byte[] data = n==buf.length ? buf : java.util.Arrays.copyOf(buf,n);
                Request cr=new Request.Builder().url(base+"/api/upload/chunk/"+uploadId+"?offset="+offset).header("Authorization","Bearer "+token)
                        .put(RequestBody.create(data, MediaType.parse("application/octet-stream"))).build();
                try(Response rr=client.newCall(cr).execute()){
                    if(rr.code()==409){ JSONObject j=new JSONObject(rr.body().string()); offset=j.getLong("expected_offset"); throw new Exception("offset changed; retry"); }
                    if(!rr.isSuccessful()) throw new Exception("chunk "+rr.code());
                }
                offset += n;
            }
        }
        Request fin=new Request.Builder().url(base+"/api/upload/finish/"+uploadId).header("Authorization","Bearer "+token).post(RequestBody.create(new byte[0],null)).build();
        try(Response r=client.newCall(fin).execute()){ if(!r.isSuccessful()) throw new Exception("finish "+r.code()); }
    }

    private int readChunk(InputStream in, byte[] b) throws Exception {
        int off=0; while(off<b.length){ int n=in.read(b,off,b.length-off); if(n<0) break; off+=n; if(n==0) break; } return off;
    }
}
