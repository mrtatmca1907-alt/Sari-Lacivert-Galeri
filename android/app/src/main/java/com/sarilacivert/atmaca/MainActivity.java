package com.sarilacivert.atmaca;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private EditText serverUrl, token;
    private TextView status;
    private Switch autoBackup, wifiOnly;
    private SharedPreferences prefs;
    private ActivityResultLauncher<Intent> filesPicker, treePicker;
    private ActivityResultLauncher<String[]> mediaPermission;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        serverUrl=findViewById(R.id.serverUrl); token=findViewById(R.id.token); status=findViewById(R.id.status);
        autoBackup=findViewById(R.id.autoBackup); wifiOnly=findViewById(R.id.wifiOnly);
        Button save=findViewById(R.id.save), pickFiles=findViewById(R.id.pickFiles), pickFolder=findViewById(R.id.pickFolder), backupGallery=findViewById(R.id.backupGallery), openPanel=findViewById(R.id.openPanel);
        prefs=getSharedPreferences("atmaca",MODE_PRIVATE);
        serverUrl.setText(prefs.getString("url","")); token.setText(prefs.getString("token",""));
        autoBackup.setChecked(prefs.getBoolean("auto",false)); wifiOnly.setChecked(prefs.getBoolean("wifi",true));

        filesPicker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result->{
            if(result.getResultCode()!=RESULT_OK || result.getData()==null)return; Intent data=result.getData();
            int count=0;
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++){ Uri u=data.getClipData().getItemAt(i).getUri(); persist(data,u); enqueueUri(u,"Yuklemeler"); count++; }
            } else if(data.getData()!=null){ Uri u=data.getData(); persist(data,u); enqueueUri(u,"Yuklemeler"); count=1; }
            status.setText(count+" dosya kuyruğa eklendi. Toplam kuyruk: "+new QueueDb(this).pendingCount()); startUploader(this);
        });

        treePicker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result->{
            if(result.getResultCode()!=RESULT_OK || result.getData()==null || result.getData().getData()==null)return;
            Uri tree=result.getData().getData(); persist(result.getData(),tree); scanTree(tree);
        });

        mediaPermission=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result-> runGalleryBackup());

        save.setOnClickListener(v->{ savePrefs(); status.setText("Bağlantı kaydedildi."); });
        pickFiles.setOnClickListener(v->openFiles());
        pickFolder.setOnClickListener(v->openTree());
        backupGallery.setOnClickListener(v->requestGalleryBackup());
        openPanel.setOnClickListener(v->openPanel());
        autoBackup.setOnCheckedChangeListener((b,checked)->{ prefs.edit().putBoolean("auto",checked).apply(); scheduleAuto(); });
        wifiOnly.setOnCheckedChangeListener((b,checked)->{ prefs.edit().putBoolean("wifi",checked).apply(); scheduleAuto(); });
        if(autoBackup.isChecked()) scheduleAuto();
    }

    private void savePrefs(){
        String url=serverUrl.getText().toString().trim().replaceAll("/+$","");
        prefs.edit().putString("url",url).putString("token",token.getText().toString().trim()).putBoolean("wifi",wifiOnly.isChecked()).putBoolean("auto",autoBackup.isChecked()).apply();
    }

    private void openFiles(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); filesPicker.launch(i);
    }
    private void openTree(){ treePicker.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)); }
    private void persist(Intent data, Uri u){ try{ int f=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION); getContentResolver().takePersistableUriPermission(u,f&Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception ignored){} }

    private void enqueueUri(Uri u,String base){
        String name="dosya"; long size=0,mtime=System.currentTimeMillis();
        try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){ int ni=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=c.getColumnIndex(OpenableColumns.SIZE); if(ni>=0)name=c.getString(ni); if(si>=0&&!c.isNull(si))size=c.getLong(si); }
        }catch(Exception ignored){}
        name=safe(name); new QueueDb(this).enqueue(u.toString(),base+"/"+name,size,mtime);
    }

    private void scanTree(Uri treeUri){
        status.setText("Klasör taranıyor…");
        new Thread(()->{
            DocumentFile root=DocumentFile.fromTreeUri(this,treeUri); if(root==null)return; QueueDb q=new QueueDb(this); long count=0;
            ArrayDeque<Node> stack=new ArrayDeque<>(); stack.push(new Node(root,"Yuklemeler/"+safe(root.getName())));
            while(!stack.isEmpty()){
                Node n=stack.pop(); DocumentFile[] kids=n.file.listFiles();
                for(DocumentFile f:kids){
                    String rp=n.path+"/"+safe(f.getName());
                    if(f.isDirectory()) stack.push(new Node(f,rp));
                    else if(f.isFile()){ q.enqueue(f.getUri().toString(),rp,Math.max(0,f.length()),Math.max(0,f.lastModified())); count++; if(count%250==0){ long c=count; runOnUiThread(()->status.setText(c+" dosya kuyruğa eklendi…")); } }
                }
            }
            long total=count; runOnUiThread(()->{ status.setText(total+" dosya kuyruğa eklendi. Toplam: "+q.pendingCount()); startUploader(this); });
        }).start();
    }

    private void requestGalleryBackup(){
        if(Build.VERSION.SDK_INT>=33){
            boolean img=ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;
            boolean vid=ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
            if(!img||!vid){ mediaPermission.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO}); return; }
        } else if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            mediaPermission.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}); return;
        }
        runGalleryBackup();
    }

    private void runGalleryBackup(){
        savePrefs(); status.setText("Galeri tarama/yedekleme başladı…");
        WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(BackupWorker.class).build());
        Toast.makeText(this,"Galeri yedekleme kuyruğu başlatıldı",Toast.LENGTH_SHORT).show();
    }

    private void scheduleAuto(){
        savePrefs();
        if(!autoBackup.isChecked()){ WorkManager.getInstance(this).cancelUniqueWork("atmaca_auto_backup"); return; }
        NetworkType net=wifiOnly.isChecked()?NetworkType.UNMETERED:NetworkType.CONNECTED;
        Constraints c=new Constraints.Builder().setRequiredNetworkType(net).build();
        PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(BackupWorker.class,6,TimeUnit.HOURS).setConstraints(c).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("atmaca_auto_backup", ExistingPeriodicWorkPolicy.UPDATE,req);
    }

    private void openPanel(){
        savePrefs(); String url=prefs.getString("url",""); if(url.isEmpty()){Toast.makeText(this,"Önce sunucu adresini gir",Toast.LENGTH_SHORT).show();return;}
        try{ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }catch(Exception e){ Toast.makeText(this,"Tarayıcı açılamadı",Toast.LENGTH_SHORT).show(); }
    }

    public static void startUploader(Context c){
        boolean wifi=c.getSharedPreferences("atmaca",Context.MODE_PRIVATE).getBoolean("wifi",true);
        Constraints cons=new Constraints.Builder().setRequiredNetworkType(wifi?NetworkType.UNMETERED:NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueue(new OneTimeWorkRequest.Builder(UploadWorker.class).setConstraints(cons).build());
    }

    private String safe(String s){ if(s==null||s.trim().isEmpty())return "Bilinmeyen"; return s.replace("/","_").replace("\\","_"); }
    static class Node{ final DocumentFile file; final String path; Node(DocumentFile f,String p){file=f;path=p;} }
}
