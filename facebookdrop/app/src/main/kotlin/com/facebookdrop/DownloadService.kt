package com.facebookdrop

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)
    private lateinit var engine: YtDlpEngine
    private lateinit var store: QueueStore

    override fun onCreate() {
        super.onCreate(); engine = YtDlpEngine(this); store = QueueStore(this)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "FacebookDrop indirme", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_STOP -> { cancelled.set(true); engine.cancel(); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> Unit
            else -> return START_NOT_STICKY
        }
        val urls = intent.getStringArrayExtra(EXTRA_URLS)?.toList().orEmpty().ifEmpty { store.load() }
        if (urls.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        cancelled.set(false); store.save(urls)
        startForeground(NOTIFICATION_ID, notification("Hazırlanıyor…", 0, 0, 0f))
        scope.launch { process(urls) }
        return START_NOT_STICKY
    }

    private suspend fun process(urls: List<String>) {
        val remaining = urls.toMutableList()
        try {
            for ((index, url) in urls.withIndex()) {
                if (cancelled.get()) break
                update(url, index + 1, urls.size, 0f, "Bağlantı hazırlanıyor")
                val result = engine.download(url) { p, eta -> update(url, index + 1, urls.size, p, "ETA ${eta}s") }
                if (result.isSuccess) {
                    remaining.remove(url); store.save(remaining)
                    update(url, index + 1, urls.size, 100f, "Tamamlandı")
                } else update(url, index + 1, urls.size, 0f, "Hata: ${result.exceptionOrNull()?.message.orEmpty()}")
            }
            if (!cancelled.get() && remaining.isEmpty()) store.clear()
        } finally {
            broadcast("", urls.size - remaining.size, urls.size, 0f, if(cancelled.get()) "Durduruldu" else "Bitti", true)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun update(url:String, index:Int, total:Int, percent:Float, message:String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message,index,total,percent))
        broadcast(url,index,total,percent,message,false)
    }

    private fun notification(message:String,index:Int,total:Int,percent:Float):Notification {
        val stop = PendingIntent.getService(this, 5, Intent(this, DownloadService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL) else Notification.Builder(this)
        return b.setContentTitle("FacebookDrop ${if(total>0) "$index/$total" else ""}").setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true)
            .addAction(android.R.drawable.ic_delete,"Durdur",stop)
            .apply { if(percent>0f) setProgress(100, percent.toInt(), false) else setProgress(0,0,true) }.build()
    }

    private fun broadcast(url:String,index:Int,total:Int,percent:Float,message:String,finished:Boolean) {
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).apply {
            putExtra("url",url); putExtra("index",index); putExtra("total",total); putExtra("percent",percent); putExtra("message",message); putExtra("finished",finished)
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy(){ cancelled.set(true); engine.cancel(); scope.cancel(); super.onDestroy() }
    companion object {
        const val ACTION_START="com.facebookdrop.START"; const val ACTION_STOP="com.facebookdrop.STOP"; const val ACTION_PROGRESS="com.facebookdrop.PROGRESS"; const val EXTRA_URLS="urls"
        private const val CHANNEL="facebook_drop_downloads"; private const val NOTIFICATION_ID=6201
    }
}
