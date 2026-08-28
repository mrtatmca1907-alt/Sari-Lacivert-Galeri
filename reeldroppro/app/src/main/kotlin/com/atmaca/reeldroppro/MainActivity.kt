package com.atmaca.reeldroppro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atmaca.reeldroppro.core.InputParser
import com.atmaca.reeldroppro.data.AppDatabase
import com.atmaca.reeldroppro.data.JobEntity
import com.atmaca.reeldroppro.model.Platform
import com.atmaca.reeldroppro.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var mode: Spinner
    private lateinit var status: TextView
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        setContentView(buildUi())
        askNotificationPermission()
        lifecycleScope.launch {
            db.jobs().observeAll().collectLatest { jobs ->
                status.text = if (jobs.isEmpty()) "Kuyruk boş" else jobs.take(30).joinToString("\n\n") { job ->
                    val p = if (job.progress > 0) " • %${job.progress.toInt()}" else ""
                    val counts = if (job.downloadedCount > 0) " • ${job.photoCount} foto / ${job.videoCount} video" else ""
                    val err = job.lastErrorMessage?.let { "\n${it.take(140)}" }.orEmpty()
                    "${job.sourceKey} — ${job.state}$p$counts$err"
                }
            }
        }
    }

    private fun buildUi(): ScrollView {
        val navy = Color.rgb(6, 28, 72)
        val yellow = Color.rgb(255, 215, 0)
        val scroll = ScrollView(this).apply { setBackgroundColor(navy) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "REELDROP PRO"
            textSize = 28f
            setTextColor(yellow)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Instagram Profil", "Instagram Hashtag", "Facebook"))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(mode, lp())

        input = EditText(this).apply {
            hint = "@kullanici, #etiket veya Facebook bağlantısı"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            minLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(input, lp(dp(12)))

        val start = Button(this).apply {
            text = "KUYRUĞA EKLE VE BAŞLAT"
            setOnClickListener { enqueue() }
        }
        root.addView(start, lp(dp(12)))

        val stop = Button(this).apply {
            text = "İNDİRME MOTORUNU DURDUR"
            setOnClickListener { startService(Intent(this@MainActivity, DownloadService::class.java).setAction(DownloadService.ACTION_STOP)) }
        }
        root.addView(stop, lp(dp(8)))

        status = TextView(this).apply {
            text = "Kuyruk yükleniyor..."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(status, lp())
        return scroll
    }

    private fun enqueue() {
        val platform = when (mode.selectedItemPosition) {
            0 -> Platform.INSTAGRAM_PROFILE
            1 -> Platform.INSTAGRAM_HASHTAG
            else -> Platform.FACEBOOK
        }
        val parsed = InputParser.parse(platform, input.text.toString())
        if (parsed.isEmpty()) {
            Toast.makeText(this, "Geçerli giriş bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            parsed.forEach { item ->
                runCatching {
                    db.jobs().insert(JobEntity(platform = item.platform.name, sourceKey = item.sourceKey, inputValue = item.value, state = "QUEUED", createdAt = now, updatedAt = now))
                }
            }
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, DownloadService::class.java))
                Toast.makeText(this@MainActivity, "${parsed.size} iş kuyruğa eklendi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
        }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
