package com.atmaca.teraboxfast;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private EditText link;
    private TextView status;
    private Button start;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.WHITE);

        status=new TextView(this);
        status.setText("TeraBox paylaşım bağlantısını yapıştır. Giriş gerekmez.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setBackgroundColor(Color.rgb(7,26,82));
        status.setPadding(24,20,24,20);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        link=new EditText(this);
        link.setHint("https://www.terabox.com/s/...");
        link.setSingleLine(true);
        root.addView(link,new LinearLayout.LayoutParams(-1,-2));

        start=new Button(this);
        start.setText("BAĞLANTIYI ÇÖZ VE TÜMÜNÜ İNDİR");
        start.setOnClickListener(v -> resolve());
        root.addView(start,new LinearLayout.LayoutParams(-1,-2));

        TextView info=new TextView(this);
        info.setText("\n• Gmail/Google girişi yok\n• Dosya boyutu sınırı yok\n• Wi-Fi ve mobil veri kullanılabilir\n• Dosyalar Download/ATMACA-TeraBox içine iner\n• Sunucu Range desteklerse çok parçalı indirme kullanılır");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        root.addView(info,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
        handleShare(getIntent());
    }

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        handleShare(i);
    }

    private void handleShare(Intent i){
        if(i!=null && Intent.ACTION_SEND.equals(i.getAction())){
            String t=i.getStringExtra(Intent.EXTRA_TEXT);
            if(t!=null){ link.setText(t); }
        }
    }

    private void resolve(){
        final String u=link.getText().toString().trim();
        if(u.isEmpty()){ Toast.makeText(this,"Paylaşım bağlantısı yapıştır.",Toast.LENGTH_LONG).show(); return; }
        start.setEnabled(false);
        status.setText("TeraBox bağlantısı çözülüyor...");
        new Thread(() -> {
            try{
                ShareResolver.Result r=ShareResolver.resolveAll(u);
                if(r.files.isEmpty()) throw new Exception("İndirilebilir dosya bulunamadı.");
                int queued=0;
                for(ShareResolver.FileItem f:r.files){
                    if(f.downloadUrl==null || f.downloadUrl.isEmpty()) continue;
                    Intent i=new Intent(this,FastDownloadService.class);
                    i.putExtra("url",f.downloadUrl);
                    i.putExtra("ua",ShareResolver.UA);
                    i.putExtra("cookie",r.cookie);
                    i.putExtra("name",f.name);
                    i.putExtra("mime","application/octet-stream");
                    if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
                    queued++;
                }
                final int q=queued;
                runOnUiThread(() -> {
                    status.setText(q+" dosya indirme kuyruğuna eklendi.");
                    start.setEnabled(true);
                });
            }catch(Exception e){
                runOnUiThread(() -> {
                    status.setText("Hata: "+e.getMessage());
                    start.setEnabled(true);
                });
            }
        }).start();
    }
}