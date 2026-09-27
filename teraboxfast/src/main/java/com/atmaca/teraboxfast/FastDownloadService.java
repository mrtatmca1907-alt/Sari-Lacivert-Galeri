package com.atmaca.teraboxfast;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class FastDownloadService extends Service {
    private static final String CH="atmaca_dl";
    private ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CH,"ATMACA İndirmeler",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    @Override public int onStartCommand(Intent in,int flags,int id){
        startForeground(77,notification("Hazırlanıyor...",0));
        final String url=in.getStringExtra("url"), ua=in.getStringExtra("ua"), cookie=in.getStringExtra("cookie");
        final String name=safe(in.getStringExtra("name")), mime=in.getStringExtra("mime");
        worker.submit(()->download(url,ua,cookie,name,mime));
        return START_NOT_STICKY;
    }
    private Notification notification(String text,int p){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
        b.setContentTitle("ATMACA Tera Hızlı İndir").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(p<100);
        if(p>=0) b.setProgress(100,Math.max(0,Math.min(100,p)),false);
        return b.build();
    }
    private void note(String t,int p){ ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(77,notification(t,p)); }

    private HttpURLConnection conn(String u,String ua,String cookie) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000); c.setReadTimeout(30000);
        if(ua!=null)c.setRequestProperty("User-Agent",ua);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("Referer","https://www.terabox.com/");
        return c;
    }

    private void download(String url,String ua,String cookie,String name,String mime){
        Uri out=null;
        try{
            HttpURLConnection probe=conn(url,ua,cookie);
            probe.setRequestMethod("GET");
            probe.setRequestProperty("Range","bytes=0-0");
            int code=probe.getResponseCode();
            long total=-1;
            boolean ranges=(code==206);
            String cr=probe.getHeaderField("Content-Range");
            if(cr!=null && cr.contains("/")) total=Long.parseLong(cr.substring(cr.lastIndexOf('/')+1));
            if(total<0) total=probe.getContentLengthLong();
            try(InputStream x=probe.getInputStream()){ byte[] z=new byte[1]; x.read(z); }
            probe.disconnect();

            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,name);
            v.put(MediaStore.Downloads.MIME_TYPE,(mime==null||mime.isEmpty())?"application/octet-stream":mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/ATMACA-TeraBox");
            v.put(MediaStore.Downloads.IS_PENDING,1);
            out=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(out==null) throw new IOException("Hedef dosya açılamadı");

            if(total>0 && ranges){
                int parts= total>8L*1024*1024*1024?16 : total>1024L*1024*1024?12 : total>128L*1024*1024?8 : 4;
                segmented(url,ua,cookie,out,total,parts,name);
            }else{
                single(url,ua,cookie,out,total,name);
            }

            ContentValues done=new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING,0);
            getContentResolver().update(out,done,null,null);
            note("Tamamlandı: "+name,100);
        }catch(Exception e){
            note("Hata: "+e.getClass().getSimpleName()+" - "+e.getMessage(),0);
            if(out!=null) try{ getContentResolver().delete(out,null,null); }catch(Exception ignored){}
        }finally{ stopForeground(false); stopSelf(); }
    }

    private void segmented(String url,String ua,String cookie,Uri out,long total,int parts,String name) throws Exception{
        try(android.os.ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(out,"rw")){
            if(pfd==null) throw new IOException("Dosya açılamadı");
            android.system.Os.ftruncate(pfd.getFileDescriptor(),total);
        }
        ExecutorService pool=Executors.newFixedThreadPool(parts);
        AtomicLong done=new AtomicLong(0);
        ArrayList<Future<?>> fs=new ArrayList<>();
        long chunk=(total+parts-1)/parts;
        for(int i=0;i<parts;i++){
            long start=i*chunk, end=Math.min(total-1,start+chunk-1);
            if(start>end) continue;
            fs.add(pool.submit(()->{
                long pos=start;
                int retry=0;
                while(pos<=end){
                    try{
                        HttpURLConnection c=conn(url,ua,cookie);
                        c.setRequestProperty("Range","bytes="+pos+"-"+end);
                        int rc=c.getResponseCode();
                        if(rc!=206) throw new IOException("Sunucu parça indirmeyi reddetti: "+rc);
                        try(InputStream in=new BufferedInputStream(c.getInputStream(),256*1024);
                            android.os.ParcelFileDescriptor p=getContentResolver().openFileDescriptor(out,"rw");
                            FileOutputStream fo=new FileOutputStream(p.getFileDescriptor());
                            FileChannel ch=fo.getChannel()){
                            ch.position(pos);
                            byte[] buf=new byte[512*1024];
                            int n;
                            while((n=in.read(buf))>0){
                                ch.write(java.nio.ByteBuffer.wrap(buf,0,n));
                                pos+=n;
                                long d=done.addAndGet(n);
                                if((d & ((1<<22)-1))<n) note(name+"  "+(d*100/total)+"%",(int)(d*100/total));
                            }
                        } finally { c.disconnect(); }
                        retry=0;
                    }catch(Exception e){
                        if(++retry>8) throw new RuntimeException(e);
                        try{Thread.sleep(Math.min(15000,500L*(1L<<Math.min(retry,5))));}catch(InterruptedException x){Thread.currentThread().interrupt();}
                    }
                }
            }));
        }
        pool.shutdown();
        for(Future<?> f:fs) f.get();
        if(!pool.awaitTermination(30,TimeUnit.SECONDS)) pool.shutdownNow();
        if(done.get()!=total) throw new IOException("Eksik veri: "+done.get()+"/"+total);
    }

    private void single(String url,String ua,String cookie,Uri out,long total,String name) throws Exception{
        HttpURLConnection c=conn(url,ua,cookie);
        int rc=c.getResponseCode();
        if(rc<200||rc>=300) throw new IOException("HTTP "+rc);
        long d=0;
        try(InputStream in=new BufferedInputStream(c.getInputStream(),256*1024);
            OutputStream o=getContentResolver().openOutputStream(out,"w")){
            if(o==null) throw new IOException("Çıktı açılamadı");
            byte[] buf=new byte[512*1024]; int n;
            while((n=in.read(buf))>0){
                o.write(buf,0,n); d+=n;
                if(total>0 && (d & ((1<<22)-1))<n) note(name+"  "+(d*100/total)+"%",(int)(d*100/total));
            }
            o.flush();
        } finally { c.disconnect(); }
        if(total>0 && d!=total) throw new IOException("Eksik veri: "+d+"/"+total);
    }

    private static String safe(String s){
        if(s==null||s.trim().isEmpty()) return "terabox_"+System.currentTimeMillis();
        return s.replaceAll("[\\/:*?\"<>|]","_");
    }
    @Override public android.os.IBinder onBind(Intent i){ return null; }
}