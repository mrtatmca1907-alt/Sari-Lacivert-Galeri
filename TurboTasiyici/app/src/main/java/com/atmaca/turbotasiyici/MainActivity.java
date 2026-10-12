package com.atmaca.turbotasiyici;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  TextView status;
  Button start;
  volatile boolean running=false;
  final String[] ext={".jpg",".jpeg",".png",".webp",".gif",".bmp",".heic",".heif",".avif",".tif",".tiff",".dng",".raw",".cr2",".nef",".arw"};
  @Override public void onCreate(Bundle b){super.onCreate(b);
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(32,64,32,32);layout.setBackgroundColor(Color.rgb(245,248,255));
    TextView title=new TextView(this);title.setText("ATMACA TURBO TAŞIYICI");title.setTextSize(24);title.setTextColor(Color.rgb(10,36,100));title.setGravity(Gravity.CENTER);layout.addView(title);
    status=new TextView(this);status.setText("Hedef: Pictures/ATMACA-Gorseller\nÖnce erişim izni ver.");status.setTextSize(17);status.setPadding(0,36,0,36);layout.addView(status);
    start=new Button(this);start.setText("TÜM GÖRSELLERİ TAŞI");layout.addView(start);setContentView(layout);
    start.setOnClickListener(v->{if(running)return;if(!Environment.isExternalStorageManager()){Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);return;}new Thread(this::moveAll).start();});
  }
  boolean isImage(String n){String s=n.toLowerCase(Locale.ROOT);for(String e:ext)if(s.endsWith(e))return true;return false;}
  void show(String s){runOnUiThread(()->status.setText(s));}
  void moveAll(){
    running=true;runOnUiThread(()->start.setEnabled(false));
    int moved=0,failed=0;File base=Environment.getExternalStorageDirectory();File target=new File(base,"Pictures/ATMACA-Gorseller");target.mkdirs();
    ArrayDeque<File> dirs=new ArrayDeque<>();dirs.push(base);
    HashSet<String> skip=new HashSet<>(Arrays.asList("Android",".thumbnails",".Trash","LOST.DIR"));
    while(!dirs.isEmpty()){
      File dir=dirs.pop();File[] children=dir.listFiles();if(children==null)continue;
      for(File f:children){
        if(f.isDirectory()){if(f.equals(target)||skip.contains(f.getName()))continue;dirs.push(f);continue;}
        if(!isImage(f.getName()))continue;
        try{
          String name=f.getName();int dot=name.lastIndexOf('.');String stem=dot<0?name:name.substring(0,dot),suffix=dot<0?"":name.substring(dot);
          File out=new File(target,name);int idx=1;while(out.exists()){out=new File(target,stem+"+"+(idx++)+suffix);}
          if(!f.renameTo(out)){
            File tmp=new File(target,".partial_"+System.nanoTime());
            try(InputStream in=new BufferedInputStream(new FileInputStream(f));OutputStream os=new BufferedOutputStream(new FileOutputStream(tmp))){
              byte[] buf=new byte[1024*1024];int n;while((n=in.read(buf))!=-1)os.write(buf,0,n);
            }
            if(tmp.length()!=f.length()||!tmp.renameTo(out)){tmp.delete();throw new IOException("copy failed");}
            if(!f.delete())throw new IOException("source not deleted");
          }
          moved++;if(moved%100==0)show("Taşınan: "+moved+" | Hata: "+failed);
        }catch(Exception ex){failed++;}
      }
    }
    final int a=moved,b=failed;show("Bitti. Taşınan: "+a+" | Hata: "+b+"\nHedef: "+target.getAbsolutePath());running=false;runOnUiThread(()->start.setEnabled(true));
  }
}
