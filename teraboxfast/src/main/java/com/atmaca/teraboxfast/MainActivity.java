package com.atmaca.teraboxfast;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE=9001;
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);

        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,28,28,28);
        root.setBackgroundColor(Color.WHITE);
        sv.addView(root);

        TextView title=new TextView(this);
        title.setText("ATMACA TERA KOPYALAYICI");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setPadding(22,22,22,22);
        title.setBackgroundColor(Color.rgb(7,26,82));
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        status=new TextView(this);
        status.setTextSize(17);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(8,28,8,28);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        Button start=new Button(this);
        start.setText("1) EKRAN OKUMAYI BAŞLAT");
        start.setOnClickListener(v->requestCapture());
        root.addView(start,new LinearLayout.LayoutParams(-1,-2));

        Button tera=new Button(this);
        tera.setText("2) TERABOX'I AÇ");
        tera.setOnClickListener(v->{
            Intent i=getPackageManager().getLaunchIntentForPackage("com.dubox.drive");
            if(i!=null)startActivity(i);
            else Toast.makeText(this,"TeraBox uygulaması bulunamadı.",Toast.LENGTH_LONG).show();
        });
        root.addView(tera,new LinearLayout.LayoutParams(-1,-2));

        Button stop=new Button(this);
        stop.setText("EKRAN OKUMAYI DURDUR");
        stop.setOnClickListener(v->stopService(new Intent(this,ScreenIndexService.class)));
        root.addView(stop,new LinearLayout.LayoutParams(-1,-2));

        Button clear=new Button(this);
        clear.setText("KAYITLI İNDEKSİ TEMİZLE");
        clear.setOnClickListener(v->{new IndexDb(this).clear(); refresh();});
        root.addView(clear,new LinearLayout.LayoutParams(-1,-2));

        TextView info=new TextView(this);
        info.setText("\nBu sürüm Erişilebilirlik izni istemez. Android'in ekran paylaşımı izniyle TeraBox ekranını görüntü olarak okur.\n\nİlk test: TeraBox'ta seçili klasör listesinde aşağı kaydır. Uygulama gördüğü klasör adlarını telefon hafızasındaki veritabanına ekler.");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        root.addView(info,new LinearLayout.LayoutParams(-1,-2));

        setContentView(sv);
        handler.post(loop);
    }

    private void requestCapture(){
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==REQ_CAPTURE && result==RESULT_OK && data!=null){
            Intent s=new Intent(this,ScreenIndexService.class);
            s.putExtra("resultCode",result);
            s.putExtra("resultData",data);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(s); else startService(s);
        }
    }

    private final Runnable loop=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,1000);}
    };

    private void refresh(){
        IndexDb db=new IndexDb(this);
        status.setText("Kayıtlı seçili klasör: "+db.selectedCount()+"\nToplam OCR kaydı: "+db.count()+"\n\nEkran okuma açıksa TeraBox listesini kaydırdıkça sayı yükselir.");
    }

    @Override protected void onDestroy(){handler.removeCallbacks(loop);super.onDestroy();}
}
