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
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenIndexService extends Service {
    private static final int NOTIF=73;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private VisualAiEngine visualAi;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private long startedAt,lastFrame;

    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("tera_screen","ATMACA Görsel AI",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification n=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"tera_screen":"")
            .setContentTitle("ATMACA Tera Kopyalayıcı")
            .setContentText("Görsel AI TeraBox ekranını inceliyor")
            .setSmallIcon(android.R.drawable.ic_menu_gallery).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIF,n,0x00000020); else startForeground(NOTIF,n);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        visualAi=new VisualAiEngine();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
        Intent data=intent.getParcelableExtra("resultData");
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(code,data);
        startedAt=SystemClock.uptimeMillis();
        startCapture();
        return START_STICKY;
    }

    private void startCapture(){
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int w=dm.widthPixels,h=dm.heightPixels,dpi=dm.densityDpi;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        display=projection.createVirtualDisplay("ATMACA-Tera-AI",w,h,dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
        reader.setOnImageAvailableListener(r->{
            Image img=r.acquireLatestImage();
            if(img==null)return;
            long now=SystemClock.uptimeMillis();
            if(now-startedAt<1800 || now-lastFrame<350 || !busy.compareAndSet(false,true)){img.close();return;}
            lastFrame=now;
            Bitmap bmp=null;
            try{bmp=toBitmap(img);}catch(Exception ignored){}
            img.close();
            if(bmp==null){busy.set(false);return;}
            analyzeFrame(bmp);
        },new Handler(Looper.getMainLooper()));
    }

    private void analyzeFrame(Bitmap bmp){
        final IndexDb db=new IndexDb(this);
        final String[] folder={""};
        final boolean[] textDone={false}, aiDone={false};
        final java.util.ArrayList<VisualAiEngine.Hit>[] hits=new java.util.ArrayList[]{new java.util.ArrayList<>()};

        Runnable finish=()->{
            if(!textDone[0]||!aiDone[0])return;
            if(!folder[0].isEmpty() && !hits[0].isEmpty()){
                db.saveFolder(folder[0]);
                String sig=frameSig(bmp);
                int h=bmp.getHeight();
                for(VisualAiEngine.Hit hit:hits[0]){
                    Rect q=hit.box;
                    if(q.centerY()<h*0.20f || q.centerY()>h*0.92f)continue;
                    db.saveImageCandidate(folder[0],sig,q.left,q.top,q.right,q.bottom,hit.confidence);
                }
            }
            bmp.recycle();
            busy.set(false);
        };

        recognizer.process(InputImage.fromBitmap(bmp,0))
            .addOnSuccessListener(text->{
                String best="";
                int bestY=Integer.MAX_VALUE;
                for(Text.TextBlock b:text.getTextBlocks()) for(Text.Line line:b.getLines()){
                    String s=line.getText()==null?"":line.getText().trim();
                    if(s.isEmpty())continue;
                    db.saveUiText(s);
                    Rect r=line.getBoundingBox();
                    if(r!=null && r.top<bestY && looksLikeFolderTitle(s)){
                        best=s;bestY=r.top;
                    }
                }
                folder[0]=best;
                textDone[0]=true;finish.run();
            })
            .addOnFailureListener(e->{textDone[0]=true;finish.run();});

        visualAi.analyze(bmp,list->{
            hits[0].addAll(list);
            aiDone[0]=true;finish.run();
        });
    }

    private boolean looksLikeFolderTitle(String s){
        String x=s.toLowerCase(java.util.Locale.ROOT);
        if(x.contains("terabox")||x.contains("selected")||x.contains("file")||x.contains("dosya"))return false;
        return s.length()<=80 && (s.matches(".*\\d.*") || s.matches(".*[A-Za-zÇĞİÖŞÜçğıöşü].*"));
    }

    private String frameSig(Bitmap b){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            int w=b.getWidth(),h=b.getHeight();
            for(int y=h/5;y<h;y+=Math.max(1,h/16)){
                for(int x=0;x<w;x+=Math.max(1,w/12)){
                    int p=b.getPixel(x,y);
                    md.update((byte)(p));md.update((byte)(p>>8));md.update((byte)(p>>16));
                }
            }
            byte[] d=md.digest();
            StringBuilder s=new StringBuilder();
            for(int i=0;i<8;i++)s.append(String.format("%02x",d[i]));
            return s.toString();
        }catch(Exception e){return String.valueOf(SystemClock.uptimeMillis());}
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
        try{if(visualAi!=null)visualAi.close();}catch(Exception ignored){}
        super.onDestroy();
    }

    @Nullable @Override public android.os.IBinder onBind(Intent intent){return null;}
}
