package com.atmaca.teraboxfast;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private EditText link;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        status=new TextView(this);
        status.setText("Google girişi normal tarayıcıda. TeraBox paylaşım linkini buraya gönder/aç; indirme ATMACA motoruna geçer.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setBackgroundColor(Color.rgb(7,26,82));
        status.setPadding(24,20,24,20);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        Button loginBtn=new Button(this);
        loginBtn.setText("TeraBox'a Tarayıcıda Giriş");
        loginBtn.setOnClickListener(v -> {
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.terabox.com/wap/outlogin"));
            startActivity(i);
        });
        root.addView(loginBtn,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        link=new EditText(this);
        link.setHint("TeraBox paylaşım bağlantısını yapıştır");
        link.setSingleLine(true);
        row.addView(link,new LinearLayout.LayoutParams(0,-2,1));

        Button openBtn=new Button(this);
        openBtn.setText("Aç");
        openBtn.setOnClickListener(v -> openShared(link.getText().toString()));
        row.addView(openBtn,new LinearLayout.LayoutParams(-2,-2));
        root.addView(row,new LinearLayout.LayoutParams(-1,-2));

        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);

        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){ return false; }
            @Override public void onPageFinished(WebView v,String u){ status.setText("Açıldı: "+u); }
        });
        web.setWebChromeClient(new WebChromeClient());

        web.setDownloadListener((url,ua,cd,mime,len)->{
            String cookie=CookieManager.getInstance().getCookie(url);
            String name=URLUtil.guessFileName(url,cd,mime);
            Intent i=new Intent(this,FastDownloadService.class);
            i.putExtra("url",url);
            i.putExtra("ua",ua);
            i.putExtra("cookie",cookie);
            i.putExtra("name",name);
            i.putExtra("mime",mime);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            Toast.makeText(this,"ATMACA indirme motoruna aktarıldı: "+name,Toast.LENGTH_LONG).show();
        });

        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        handleShare(getIntent());
    }

    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        setIntent(i);
        handleShare(i);
    }

    private void handleShare(Intent i){
        if(i==null) return;
        if(Intent.ACTION_SEND.equals(i.getAction())){
            String t=i.getStringExtra(Intent.EXTRA_TEXT);
            if(t!=null){
                link.setText(t);
                openShared(t);
            }
        }
    }

    private void openShared(String t){
        if(t==null) return;
        t=t.trim();
        int p=t.indexOf("http");
        if(p>0) t=t.substring(p);
        if(!t.startsWith("http")){
            Toast.makeText(this,"Geçerli bir TeraBox bağlantısı değil.",Toast.LENGTH_LONG).show();
            return;
        }
        link.setText(t);
        web.loadUrl(t);
    }

    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}