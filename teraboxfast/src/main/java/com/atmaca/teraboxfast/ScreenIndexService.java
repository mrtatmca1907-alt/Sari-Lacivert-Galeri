package com.atmaca.teraboxfast;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenIndexService extends Service {
    private static final int NOTIF=73;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private long lastOcr=0;

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("tera_screen","TeraBox ekran okuma",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification n=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"tera_screen":"")
            .setContentTitle("ATMACA Tera Kopyalayıcı")
            .setContentText("TeraBox ekranı indeksleniyor")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIF,n,0x00000020); else startForeground(NOTIF,n);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
        Intent data=intent.getParcelableExtra("resultData");
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(code,data);
        startCapture();
        return START_STICKY;
    }

    private void startCapture(){
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int w=dm.widthPixels,h=dm.heightPixels,dpi=dm.densityDpi;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        display=projection.createVirtualDisplay("ATMACA-Tera-Index",w,h,dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
        reader.setOnImageAvailableListener(r->{
            long now=SystemClock.uptimeMillis();
            Image img=r.acquireLatestImage();
            if(img==null)return;
            if(now-lastOcr<700||!busy.compareAndSet(false,true)){img.close();return;}
            lastOcr=now;
            Bitmap bmp=null;
            try{bmp=toBitmap(img);}catch(Exception ignored){}
            img.close();
            if(bmp==null){busy.set(false);return;}
            final Bitmap finalBmp=bmp;
            recognizer.process(InputImage.fromBitmap(finalBmp,0))
                .addOnSuccessListener(this::consumeText)
                .addOnCompleteListener(t->{finalBmp.recycle();busy.set(false);});
        },new Handler(Looper.getMainLooper()));
    }

    private void consumeText(Text text){
        IndexDb db=new IndexDb(this);
        for(Text.TextBlock b:text.getTextBlocks()){
            for(Text.Line line:b.getLines()){
                String s=line.getText()==null?"":line.getText().trim();
                if(s.isEmpty())continue;
                db.save(s,"OCR","Text.Line","TeraBox Screen");
                if(isFolderName(s))db.saveSelectedFolder(s);
            }
        }
    }

    private boolean isFolderName(String s){
        s=s.trim();
        if(s.matches("^\\d+\\s*\\(\\d+\\)$"))return true;
        if(s.matches("^\\d{1,5}$"))return true;
        return false;
    }

    private Bitmap toBitmap(Image image){
        Image.Plane[] p=image.getPlanes();
        ByteBuffer buffer=p[0].getBuffer();
        int pixelStride=p[0].getPixelStride();
        int rowStride=p[0].getRowStride();
        int rowPadding=rowStride-pixelStride*image.getWidth();
        Bitmap full=Bitmap.createBitmap(image.getWidth()+rowPadding/pixelStride,image.getHeight(),Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buffer);
        Bitmap cropped=Bitmap.createBitmap(full,0,0,image.getWidth(),image.getHeight());
        if(cropped!=full)full.recycle();
        return cropped;
    }

    @Override public void onDestroy(){
        try{if(display!=null)display.release();}catch(Exception ignored){}
        try{if(reader!=null)reader.close();}catch(Exception ignored){}
        try{if(projection!=null)projection.stop();}catch(Exception ignored){}
        try{if(recognizer!=null)recognizer.close();}catch(Exception ignored){}
        super.onDestroy();
    }

    @Nullable @Override public android.os.IBinder onBind(Intent intent){return null;}
}
