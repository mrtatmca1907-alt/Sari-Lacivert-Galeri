package com.atmaca.teraboxfast;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView status;
    private ImageView qr;
    private Button start, scan;
    private TeraboxSessionClient client;
    private volatile boolean polling=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);

        client=new TeraboxSessionClient();

        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.WHITE);
        sv.addView(root);

        status=new TextView(this);
        status.setText("TeraBox gerçek oturum testi\nQR ile giriş yapacağız.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(24,20,24,20);
        status.setBackgroundColor(Color.rgb(7,26,82));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        qr=new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setVisibility(View.GONE);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,700);
        qp.setMargins(0,20,0,20);
        root.addView(qr,qp);

        start=new Button(this);
        start.setText("QR GİRİŞİNİ BAŞLAT");
        start.setOnClickListener(v->beginQr());
        root.addView(start,new LinearLayout.LayoutParams(-1,-2));

        scan=new Button(this);
        scan.setText("HESABIMDAKİ DOSYALARI TARA");
        scan.setEnabled(false);
        scan.setOnClickListener(v->scanFiles());
        root.addView(scan,new LinearLayout.LayoutParams(-1,-2));

        TextView info=new TextView(this);
        info.setText("\n1) QR kodu TeraBox uygulamasından okut.\n2) Telefonda giriş onayını ver.\n3) Oturum başarıyla alınırsa bu APK hesabın kök dizinini doğrudan okuyacak.\n\nŞifre bu APK'ya yazılmayacak.");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        root.addView(info,new LinearLayout.LayoutParams(-1,-2));

        setContentView(sv);
    }

    private void beginQr(){
        if(polling)return;
        start.setEnabled(false);
        status.setText("QR oturumu hazırlanıyor...");
        new Thread(()->{
            try{
                TeraboxSessionClient.QrStart q=client.startQr();
                runOnUiThread(()->{
                    try{
                        String b64=q.qrDataUrl;
                        int comma=b64.indexOf(',');
                        if(comma>=0)b64=b64.substring(comma+1);
                        byte[] raw=Base64.decode(b64,Base64.DEFAULT);
                        Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);
                        qr.setImageBitmap(bm);
                        qr.setVisibility(View.VISIBLE);
                        status.setText("QR hazır. TeraBox uygulamasıyla okut ve girişi onayla.");
                    }catch(Exception e){status.setText("QR gösterilemedi: "+e.getMessage());}
                });
                pollQr();
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("QR başlatma hatası: "+e.getMessage());
                    start.setEnabled(true);
                });
            }
        }).start();
    }

    private void pollQr(){
        polling=true;
        new Thread(()->{
            long end=System.currentTimeMillis()+180000;
            try{
                while(System.currentTimeMillis()<end){
                    TeraboxSessionClient.QrStatus s=client.checkQr();
                    if(s.success){
                        polling=false;
                        runOnUiThread(()->{
                            status.setText("TeraBox oturumu alındı. Şimdi hesabı tarayabilirsin.");
                            qr.setVisibility(View.GONE);
                            scan.setEnabled(true);
                            start.setEnabled(true);
                        });
                        return;
                    }
                    final String msg=s.confirmed?"QR okundu. TeraBox uygulamasında girişi ONAYLA.":"QR okutulması bekleniyor...";
                    runOnUiThread(()->status.setText(msg));
                    Thread.sleep(2000);
                }
                throw new Exception("QR süresi doldu.");
            }catch(Exception e){
                polling=false;
                runOnUiThread(()->{
                    status.setText("QR giriş hatası: "+e.getMessage());
                    start.setEnabled(true);
                });
            }
        }).start();
    }

    private void scanFiles(){
        scan.setEnabled(false);
        status.setText("Hesaptaki dosyalar taranıyor...");
        new Thread(()->{
            try{
                ArrayList<TeraboxSessionClient.FileEntry> files=client.listAllFiles();
                long total=0;
                for(TeraboxSessionClient.FileEntry f:files) total+=Math.max(0,f.size);
                final long bytes=total;
                runOnUiThread(()->{
                    status.setText("BAŞARILI\nDosya sayısı: "+files.size()+"\nToplam: "+human(bytes)+"\n\nGerçek TeraBox oturumuyla hesap okunabildi.");
                    scan.setEnabled(true);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("Hesap tarama hatası: "+e.getMessage());
                    scan.setEnabled(true);
                });
            }
        }).start();
    }

    private static String human(long b){
        double v=b;
        String[] u={"B","KB","MB","GB","TB"};
        int i=0;
        while(v>=1024&&i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US,"%.2f %s",v,u[i]);
    }
}
