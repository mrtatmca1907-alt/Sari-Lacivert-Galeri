package com.atmaca.keeper

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var selectedText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        requestNotificationPermissionIfNeeded()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    private fun buildUi() {
        val navy = Color.rgb(0, 35, 102)
        val yellow = Color.rgb(255, 220, 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(navy)
        }

        root.addView(TextView(this).apply {
            text = "ATMACA KORUMA"
            textSize = 27f
            setTextColor(yellow)
            setTypeface(null, 1)
        })
        root.addView(TextView(this).apply {
            text = "Seçili uygulamalar için foreground koruma, CPU/Wi‑Fi kilidi ve açılışta geri başlatma"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(16))
        })

        status = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(status)

        selectedText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(selectedText)

        root.addView(button("UYGULAMA SEÇ", yellow, navy) { chooseApps() })
        root.addView(button("KORUMAYI BAŞLAT", yellow, navy) { startKeeper() }, params(top = 10))
        root.addView(button("KORUMAYI DURDUR", Color.WHITE, navy) { stopKeeper() }, params(top = 10))
        root.addView(button("SEÇİLİ UYGULAMAYI AÇ", Color.WHITE, navy) { openSelected() }, params(top = 10))
        root.addView(button("PİL OPTİMİZASYONU AYARLARI", Color.WHITE, navy) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }, params(top = 10))

        root.addView(TextView(this).apply {
            text = "Not: Root olmadan Android başka bir uygulamanın force-stop edilmesini %100 engellemeye izin vermez. Bu uygulama kullanıcı seviyesinde mümkün olan koruma katmanlarını uygular."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(18), 0, 0)
        })

        setContentView(root)
    }

    private fun chooseApps() {
        val apps = AppRepository(this).launcherApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = prefs.targets().toMutableSet()
        val labels = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { selected.contains(apps[it].packageName) }
        AlertDialog.Builder(this)
            .setTitle("Korunacak uygulamaları seç")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) selected += pkg else selected -= pkg
            }
            .setPositiveButton("KAYDET") { _, _ -> prefs.setTargets(selected); refresh() }
            .setNegativeButton("İPTAL", null)
            .show()
    }

    private fun openSelected() {
        val apps = AppRepository(this).launcherApps().filter { prefs.targets().contains(it.packageName) }
        if (apps.isEmpty()) {
            Toast.makeText(this, "Önce uygulama seç", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Uygulamayı aç")
            .setItems(apps.map { it.label }.toTypedArray()) { _, which -> launchPackage(apps[which].packageName) }
            .show()
    }

    private fun launchPackage(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show()
        else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startKeeper() {
        if (prefs.targets().isEmpty()) {
            Toast.makeText(this, "Önce en az bir uygulama seç", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, KeeperService::class.java).setAction(KeeperService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        prefs.enabled = true
        refresh()
    }

    private fun stopKeeper() {
        startService(Intent(this, KeeperService::class.java).setAction(KeeperService.ACTION_STOP))
        prefs.enabled = false
        refresh()
    }

    private fun refresh() {
        status.text = if (prefs.enabled) "Durum: KORUMA AKTİF" else "Durum: Kapalı"
        val count = prefs.targets().size
        val network = if (prefs.networkOnline) "bağlı" else "bekleniyor"
        selectedText.text = "Seçili uygulama: $count   •   İnternet: $network"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 91)
        }
    }

    private fun button(text: String, bg: Int, fg: Int, click: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(fg)
        setTypeface(null, 1)
        setBackgroundColor(bg)
        setOnClickListener { click() }
    }

    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(top) }
    private fun dp(v: Int) = Math.round(v * resources.displayMetrics.density)
}
