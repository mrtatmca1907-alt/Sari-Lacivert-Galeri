package com.atmaca.keeper

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class KeeperService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var cm: ConnectivityManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkState()
        override fun onLost(network: Network) = refreshNetworkState()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshNetworkState()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        cm = getSystemService(ConnectivityManager::class.java)
        createChannel()
        acquireCpuLock()
        registerNetwork()
        refreshNetworkState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.enabled = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> prefs.enabled = true
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun acquireCpuLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ATMACAKeeper:cpu").apply {
            setReferenceCounted(false)
            if (!isHeld) acquire()
        }
    }

    private fun refreshNetworkState() {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        prefs.networkOnline = online
        if (wifi) acquireWifiLock() else releaseWifiLock()
        if (prefs.enabled) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ATMACAKeeper:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
    }

    private fun registerNetwork() {
        runCatching {
            cm.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ATMACA Koruma", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 11, Intent(this, KeeperService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val onlineText = if (prefs.networkOnline) "İnternet bağlı" else "İnternet bekleniyor"
        val count = prefs.targets().size
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("ATMACA Koruma aktif")
            .setContentText("$onlineText • $count uygulama seçili")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Durdur", stopPi)
            .build()
    }

    override fun onDestroy() {
        if (callbackRegistered) runCatching { cm.unregisterNetworkCallback(networkCallback) }
        releaseWifiLock()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.atmaca.keeper.START"
        const val ACTION_STOP = "com.atmaca.keeper.STOP"
        private const val CHANNEL_ID = "atmaca_keeper"
        private const val NOTIFICATION_ID = 9001
    }
}
