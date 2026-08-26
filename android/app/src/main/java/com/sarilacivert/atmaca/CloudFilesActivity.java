package com.sarilacivert.atmaca;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CloudFilesActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private String baseUrl, token, path="";
    private TextView title, disk;
    private ListView list;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences("atmaca",MODE_PRIVATE);
        baseUrl=prefs.getString("url","");
        token=prefs.getString("token","");
        buildUi();
        load();
    }

    private void buildUi(){
        int pad=dp(14);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(getColor(R.color.navy_900));

        TextView logo=new TextView(this);
        logo.setText("ATMACA • DOSYALARIM");
        logo.setTextSize(24);
        logo.setTextColor(getColor(R.color.yellow_500));
        logo.setGravity(Gravity.START);
        root.addView(logo,new LinearLayout.LayoutParams(-1,-2));

        disk=new TextView(this);
        disk.setTextColor(getColor(R.color.text_secondary));
        disk.setText("Depolama bilgisi alınıyor…");
        root.addView(disk,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0,dp(10),0,dp(8));
        Button up=new Button(this); up.setText("⬆ ÜST");
        Button refresh=new Button(this); refresh.setText("YENİLE");
        Button folder=new Button(this); folder.setText("+ KLASÖR");
        bar.addView(up,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(folder,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        title=new TextView(this);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setPadding(0,dp(5),0,dp(7));
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        list=new ListView(this);
        list.setDividerHeight(1);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>());
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        up.setOnClickListener(v->{
            if(path.isEmpty()) return;
            int i=path.lastIndexOf('/');
            path=i<0?"":path.substring(0,i);
            load();
        });
        refresh.setOnClickListener(v->load());
        folder.setOnClickListener(v->askNewFolder());
        list.setOnItemClickListener((p,v,pos,id)->{
            Entry e=entries.get(pos);
            if(e.dir){ path=e.path; load(); }
            else openDownload(e);
        });
        list.setOnItemLongClickListener((p,v,pos,id)->{
            showActions(entries.get(pos));
            return true;
        });
    }

    private void load(){
        title.setText("/"+path);
        new Thread(()->{
            try{
                JSONObject s=new JSONObject(get("/api/status"));
                String diskText=human(s.optLong("used"))+" kullanılıyor • "+human(s.optLong("free"))+" boş • "+human(s.optLong("total"))+" toplam";
                JSONObject o=new JSONObject(get("/api/list?path="+enc(path)));
                JSONArray a=o.getJSONArray("items");
                List<Entry> temp=new ArrayList<>();
                List<String> labels=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject x=a.getJSONObject(i);
                    Entry e=new Entry(x.getString("name"),x.getString("path"),x.getBoolean("is_dir"),x.optLong("size"));
                    temp.add(e);
                    labels.add((e.dir?"📁  ":"📄  ")+e.name+(e.dir?"":"   •   "+human(e.size)));
                }
                runOnUiThread(()->{
                    entries.clear(); entries.addAll(temp);
                    adapter.clear(); adapter.addAll(labels); adapter.notifyDataSetChanged();
                    disk.setText(diskText);
                    title.setText("/"+path+"   •   "+entries.size()+" öğe");
                });
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,"Bağlantı hatası: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openDownload(Entry e){
        try{
            String u=baseUrl+"/api/download?path="+enc(e.path)+"&token="+enc(token);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        }catch(Exception ex){ Toast.makeText(this,"Dosya açılamadı",Toast.LENGTH_SHORT).show(); }
    }

    private void askNewFolder(){
        EditText input=new EditText(this);
        input.setHint("Klasör adı");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Yeni klasör").setView(input)
                .setPositiveButton("Oluştur",(d,w)->{
                    String n=input.getText().toString().trim();
                    if(n.isEmpty())return;
                    String p=path.isEmpty()?n:path+"/"+n;
                    post("/api/folder",new JSONObjectBuilder().put("path",p).json(),"Klasör oluşturuldu");
                }).setNegativeButton("İptal",null).show();
    }

    private void showActions(Entry e){
        String[] actions={"Yeniden adlandır","Sil"};
        new AlertDialog.Builder(this).setTitle(e.name).setItems(actions,(d,which)->{
            if(which==0) askRename(e); else askDelete(e);
        }).show();
    }

    private void askRename(Entry e){
        EditText input=new EditText(this);
        input.setText(e.name);
        input.setSelection(e.name.length());
        new AlertDialog.Builder(this).setTitle("Yeniden adlandır").setView(input)
                .setPositiveButton("Kaydet",(d,w)->{
                    String n=input.getText().toString().trim();
                    if(n.isEmpty())return;
                    String parent="";
                    int i=e.path.lastIndexOf('/');
                    if(i>=0) parent=e.path.substring(0,i);
                    String to=parent.isEmpty()?n:parent+"/"+n;
                    post("/api/rename",new JSONObjectBuilder().put("from",e.path).put("to",to).json(),"Ad değiştirildi");
                }).setNegativeButton("İptal",null).show();
    }

    private void askDelete(Entry e){
        new AlertDialog.Builder(this).setTitle("Silinsin mi?").setMessage(e.name)
                .setPositiveButton("Sil",(d,w)->post("/api/delete",new JSONObjectBuilder().put("path",e.path).json(),"Silindi"))
                .setNegativeButton("Vazgeç",null).show();
    }

    private void post(String endpoint,String json,String ok){
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+endpoint).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(20000);
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Authorization","Bearer "+token);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                try(OutputStream out=c.getOutputStream()){ out.write(json.getBytes(StandardCharsets.UTF_8)); }
                int code=c.getResponseCode();
                if(code<200||code>=300) throw new Exception("HTTP "+code);
                runOnUiThread(()->{ Toast.makeText(this,ok,Toast.LENGTH_SHORT).show(); load(); });
            }catch(Exception ex){ runOnUiThread(()->Toast.makeText(this,"İşlem hatası: "+ex.getMessage(),Toast.LENGTH_LONG).show()); }
        }).start();
    }

    private String get(String endpoint)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+endpoint).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(20000);
        c.setRequestProperty("Authorization","Bearer "+token);
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new Exception("HTTP "+code);
        StringBuilder b=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
            String line; while((line=r.readLine())!=null)b.append(line);
        }
        return b.toString();
    }

    private String enc(String s)throws Exception{ return URLEncoder.encode(s,"UTF-8"); }

    private String human(long n){
        if(n<1024)return n+" B";
        double x=n; String[] u={"KB","MB","GB","TB"}; int i=-1;
        do{x/=1024;i++;}while(x>=1024&&i<u.length-1);
        return String.format(java.util.Locale.getDefault(),"%.1f %s",x,u[i]);
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    static class Entry{
        final String name,path; final boolean dir; final long size;
        Entry(String n,String p,boolean d,long s){name=n;path=p;dir=d;size=s;}
    }

    static class JSONObjectBuilder{
        final JSONObject o=new JSONObject();
        JSONObjectBuilder put(String k,String v){ try{o.put(k,v);}catch(Exception ignored){} return this; }
        String json(){ return o.toString(); }
    }
}
