package dev.murat.appcenter.sdk;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reports crashes and an unresponsive foreground main thread to the local center. */
public final class InitProvider extends ContentProvider {
    private static final AtomicBoolean installed=new AtomicBoolean(false);
    private static volatile long beat=SystemClock.uptimeMillis(),lastReport;
    public boolean onCreate(){
        Context context=getContext().getApplicationContext();if(!installed.compareAndSet(false,true))return true;
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));
            // Synchronous local Binder storage before the process exits; no network.
            report(context,"uncaught_exception",text.toString());
            if(previous!=null)previous.uncaughtException(thread,error);
            else {android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
        Handler main=new Handler(Looper.getMainLooper());
        Thread watch=new Thread(()->{
            while(!Thread.currentThread().isInterrupted()){
                main.post(()->beat=SystemClock.uptimeMillis());
                try{Thread.sleep(15000);}catch(InterruptedException e){return;}
                long now=SystemClock.uptimeMillis();ActivityManager.RunningAppProcessInfo info=new ActivityManager.RunningAppProcessInfo();ActivityManager.getMyMemoryState(info);
                if(info.importance==ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND&&now-beat>30000&&now-lastReport>1800000){
                    lastReport=now;StringBuilder trace=new StringBuilder("Ön plandaki ana iş parçacığı 30 saniyeden uzun yanıt vermedi.\n");
                    for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace())trace.append(frame).append('\n');
                    report(context,"main_thread_stalled",trace.toString());
                }
            }
        },"app-center-watchdog");watch.setDaemon(true);watch.start();return true;
    }
    static void report(Context c,String operation,String error){try{
        Bundle data=new Bundle();data.putString("operation",operation);data.putString("error",error.substring(0,Math.min(error.length(),10000)));
        c.getContentResolver().call(Uri.parse("content://dev.murat.appcenter.telemetry"),"report",c.getPackageName(),data);
    }catch(Exception ignored){}}
    public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new SecurityException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}
    public int delete(Uri u,String s,String[] a){throw new SecurityException();}
}
