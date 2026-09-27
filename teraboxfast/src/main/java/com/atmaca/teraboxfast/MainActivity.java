package com.atmaca.teraboxfast;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
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
        status.setText("TeraBox'a giriş yap. İndir'e bastığın dosya ATMACA motoruna aktarılır.");
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(7,26,82));
        status.setPadding(24,20,24,20);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString(s.getUserAgentString()+" ATMACA-FastDownloader/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);

        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){ return false; }
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
            Toast.makeText(this,"İndirme motoruna aktarıldı: "+name,Toast.LENGTH_LONG).show();
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        web.loadUrl("https://www.terabox.com/");
    }

    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}