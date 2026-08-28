package com.facebookdrop

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    private val receiver = object: BroadcastReceiver(){
        override fun onReceive(c:Context?,i:Intent?){
            i?:return
            val p=i.getFloatExtra("percent",0f)
            progress.progress=p.toInt()
            status.text="${i.getIntExtra("index",0)}/${i.getIntExtra("total",0)}  ${i.getStringExtra("message").orEmpty()}\n${i.getStringExtra("url").orEmpty()}"
        }
    }

    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        buildUi()
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),50)
    }

    private fun buildUi(){
        val navy=Color.rgb(6,26,68); val yellow=Color.rgb(255,215,0)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(22),dp(18),dp(18));setBackgroundColor(navy)}
        root.addView(TextView(this).apply{text="FACEBOOK DROP";textSize=27f;setTextColor(yellow);setTypeface(null,1)})
        root.addView(TextView(this).apply{text="Reel • Video • Çoklu bağlantı • Herkese açık sayfalar";setTextColor(Color.WHITE);setPadding(0,dp(6),0,dp(14))})
        input=EditText(this).apply{hint="Facebook linklerini buraya yapıştır\nHer satıra bir link de olabilir";minLines=6;gravity=android.view.Gravity.TOP;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setBackgroundColor(Color.rgb(15,45,95));setPadding(dp(12),dp(12),dp(12),dp(12))}
        root.addView(input,LinearLayout.LayoutParams(-1,dp(190)))
        val start=Button(this).apply{text="İNDİRMEYİ BAŞLAT";setTextColor(navy);setBackgroundColor(yellow);setOnClickListener{startDownloads()}}
        root.addView(start,LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(12)})
        val stop=Button(this).apply{text="DURDUR";setOnClickListener{startService(Intent(this@MainActivity,DownloadService::class.java).setAction(DownloadService.ACTION_STOP))}}
        root.addView(stop,LinearLayout.LayoutParams(-1,dp(50)).apply{topMargin=dp(8)})
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100}
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(12)).apply{topMargin=dp(16)})
        status=TextView(this).apply{text="Hazır";setTextColor(Color.WHITE);setPadding(0,dp(12),0,0)}
        root.addView(status)
        setContentView(root)
    }

    private fun startDownloads(){
        val urls=UrlUtils.parseUrls(input.text.toString())
        if(urls.isEmpty()){Toast.makeText(this,"Geçerli Facebook bağlantısı bulunamadı",Toast.LENGTH_SHORT).show();return}
        val it=Intent(this,DownloadService::class.java).setAction(DownloadService.ACTION_START).putExtra(DownloadService.EXTRA_URLS,urls.toTypedArray())
        if(Build.VERSION.SDK_INT>=26) startForegroundService(it) else startService(it)
        status.text="${urls.size} bağlantı kuyruğa alındı"
    }

    override fun onStart(){super.onStart(); val f=IntentFilter(DownloadService.ACTION_PROGRESS); if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,f)}
    override fun onStop(){runCatching{unregisterReceiver(receiver)};super.onStop()}
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)
}
