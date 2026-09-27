package com.atmaca.teraboxfast;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private Handler handler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b){
        super.onCreate(b);

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

        Button enable=new Button(this);
        enable.setText("1) OKUMA SERVİSİNİ AÇ");
        enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enable,new LinearLayout.LayoutParams(-1,-2));

        Button tera=new Button(this);
        tera.setText("2) TERABOX'I AÇ VE İNDEKSLE");
        tera.setOnClickListener(v->{
            IndexDb.setEnabled(this,true);
            Intent i=getPackageManager().getLaunchIntentForPackage("com.dubox.drive");
            if(i!=null) startActivity(i);
            else Toast.makeText(this,"TeraBox uygulaması bulunamadı.",Toast.LENGTH_LONG).show();
        });
        root.addView(tera,new LinearLayout.LayoutParams(-1,-2));

        Button stop=new Button(this);
        stop.setText("İNDEKSLEMEYİ DURDUR");
        stop.setOnClickListener(v->IndexDb.setEnabled(this,false));
        root.addView(stop,new LinearLayout.LayoutParams(-1,-2));

        Button clear=new Button(this);
        clear.setText("KAYITLI İNDEKSİ TEMİZLE");
        clear.setOnClickListener(v->{
            new IndexDb(this).clear();
            refresh();
        });
        root.addView(clear,new LinearLayout.LayoutParams(-1,-2));

        TextView info=new TextView(this);
        info.setText("\nBu ilk test sürümünde TeraBox ekranında görünen dosya/klasör kayıtları telefonun kendi SQLite veritabanına yazılır. TeraBox listesini otomatik aşağı kaydırır. Klasörlerin içine sen girdikçe onları da aynı indekse ekler.\n\nİndirme aşamasını bu indeks doğrulandıktan sonra bağlayacağız.");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        root.addView(info,new LinearLayout.LayoutParams(-1,-2));

        setContentView(sv);
        handler.post(refreshLoop);
    }

    private final Runnable refreshLoop=new Runnable(){
        @Override public void run(){
            refresh();
            handler.postDelayed(this,1000);
        }
    };

    private void refresh(){
        IndexDb db=new IndexDb(this);
        int count=db.count();
        boolean on=IndexDb.isEnabled(this);
        status.setText("İndeksleme: "+(on?"AÇIK":"KAPALI")+"\nSeçili klasör kuyruğu: "+db.selectedCount()+"\nToplam görülen kayıt: "+count+"\n\nSelect All / Deselect All ekranında görülen klasörler otomatik olarak telefon hafızasına kaydedilir.");
    }

    @Override protected void onDestroy(){
        handler.removeCallbacks(refreshLoop);
        super.onDestroy();
    }
}
