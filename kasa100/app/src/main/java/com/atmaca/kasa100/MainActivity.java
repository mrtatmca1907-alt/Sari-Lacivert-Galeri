package com.atmaca.kasa100;
import android.app.*;import android.os.*;import android.content.*;import android.provider.Settings;import android.net.Uri;import android.widget.*;import android.graphics.Color;
public class MainActivity extends Activity {
TextView status; EditText path; Handler h=new Handler(); Runnable tick=new Runnable(){public void run(){status.setText(getSharedPreferences("state",0).getString("status","Hazır")+"\n\nKasada: "+VaultService.count(MainActivity.this)+" fotoğraf");h.postDelayed(this,1000);}};
public void onCreate(Bundle b){super.onCreate(b);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(28,38,28,20);l.setBackgroundColor(Color.rgb(9,35,77));setContentView(l);
TextView title=new TextView(this);title.setText("ATMACA KASA 100");title.setTextSize(26);title.setTextColor(0xffffd43b);l.addView(title);
status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(18);l.addView(status);
button(l,"Dosya erişimini aç",()->permission());
button(l,"Tüm fotoğrafları kasaya taşı",()->{if(!allowed()){permission();return;}new AlertDialog.Builder(this).setTitle("Fotoğrafları taşı").setMessage("Erişilebilen telefon ve SD kart klasörleri taranır. Her fotoğraf doğrulanarak kasaya alınır, ardından eski yerinden silinir. Uygulamayı kaldırmak veya verilerini temizlemek kasadaki fotoğrafları siler.").setPositiveButton("Taşı",(d,w)->run("import")).setNegativeButton("Vazgeç",null).show();});
TextView label=new TextView(this);label.setText("100'lük paketlerin çıkış klasörü");label.setTextColor(Color.WHITE);l.addView(label);
path=new EditText(this);path.setSingleLine(true);path.setTextColor(Color.WHITE);path.setText(getSharedPreferences("state",0).getString("output",Environment.getExternalStorageDirectory()+"/ATMACA-100"));l.addView(path);
button(l,"100'lü ver",()->{if(!allowed()){permission();return;}run("export");});
button(l,"Son kalanları ver (100'den az)",()->{if(!allowed()){permission();return;}run("remainder");});
button(l,"İşlemi durdur",()->VaultService.cancel=true);
TextView note=new TextView(this);note.setText("Her paket ayrı klasöre çıkar. Başarıyla verilenler kasadan silinir; yeniden toplanmaz. Fotoğraflar sıkıştırılmaz. Başka uygulamaların korumalı özel alanlarına Android erişim vermez.");note.setTextColor(Color.LTGRAY);l.addView(note);
}
void button(LinearLayout l,String t,Runnable r){Button b=new Button(this);b.setText(t);l.addView(b);b.setOnClickListener(v->r.run());}
boolean allowed(){return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")==android.content.pm.PackageManager.PERMISSION_GRANTED;}
void permission(){if(Build.VERSION.SDK_INT>=30)startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));else requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE"},1);}
void run(String op){String out=path.getText().toString().trim();if(!out.startsWith("/")){status.setText("Çıkış yolu / ile başlamalı.");return;}getSharedPreferences("state",0).edit().putString("output",out).apply();startForegroundService(new Intent(this,VaultService.class).putExtra("op",op).putExtra("out",out));}
public void onResume(){super.onResume();h.post(tick);}public void onPause(){super.onPause();h.removeCallbacks(tick);}
}