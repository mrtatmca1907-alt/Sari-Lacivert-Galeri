package com.sarilacivert.atmaca;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BackupWorker extends Worker {
    public BackupWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c,p); }
    @NonNull @Override public Result doWork() {
        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Fotoğraflar");
        scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Videolar");
        MainActivity.startUploader(getApplicationContext());
        return Result.success();
    }
    private void scan(Uri collection, String type) {
        String[] proj={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.BUCKET_DISPLAY_NAME};
        try(Cursor c=getApplicationContext().getContentResolver().query(collection,proj,null,null,MediaStore.MediaColumns.DATE_MODIFIED+" ASC")){
            if(c==null)return; QueueDb q=new QueueDb(getApplicationContext());
            int idI=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID), nameI=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME), sizeI=c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE), timeI=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED), bucketI=c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME);
            while(c.moveToNext()){
                long id=c.getLong(idI), size=c.getLong(sizeI), mt=c.getLong(timeI)*1000L; String name=safe(c.getString(nameI)); String bucket=bucketI>=0?safe(c.getString(bucketI)):"Galeri";
                Uri uri=ContentUris.withAppendedId(collection,id); q.enqueue(uri.toString(),"Telefon/"+type+"/"+bucket+"/"+name,size,mt);
            }
        } catch(Exception ignored){}
    }
    private String safe(String s){ if(s==null||s.trim().isEmpty())return "Bilinmeyen"; return s.replace("/","_").replace("\\","_"); }
}
