package com.atmaca.reeldroppro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import com.atmaca.reeldroppro.engine.SlotStateText
import com.atmaca.reeldroppro.model.Platform
import com.atmaca.reeldroppro.service.DownloadService
import com.atmaca.reeldroppro.storage.MediaBucket
import com.atmaca.reeldroppro.storage.OutputPathPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private val slots = linkedMapOf<Int, SlotViews>()
    private val navy = Color.rgb(5, 24, 63)
    private val yellow = Color.rgb(255, 215, 0)
    private val panel = Color.rgb(13, 42, 88)

    data class SlotViews(
        val mode: Spinner,
        val input: EditText,
        val status: TextView,
        val details: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        setContentView(buildUi())
        askNotificationPermission()
        observeSlots()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(navy) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(30))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "REELDROP PRO V2"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(yellow)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "5 BAĞIMSIZ İNDİRME MOTORU"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        })

        for (slotId in 1..5) root.addView(buildSlot(slotId), lp(dp(10)))

        root.addView(TextView(this).apply {
            text = "İndirme sürerken uygulamadan çıksan veya ekran kapansa da aktif motorlar foreground servis ile çalışmaya devam eder."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(dp(4), dp(16), dp(4), 0)
        })
        return scroll
    }

    private fun buildSlot(slotId: Int): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        box.addView(TextView(this).apply {
            text = "MOTOR $slotId"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(yellow)
        })

        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Instagram Profil", "Instagram Hashtag", "Facebook")
            )
            setBackgroundColor(Color.WHITE)
        }
        box.addView(mode, lp(dp(8)))

        val input = EditText(this).apply {
            hint = "@kullanici / #etiket / Facebook bağlantısı"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            isSingleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        box.addView(input, lp(dp(8)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "BAŞLAT"
            setOnClickListener { startSlot(slotId) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        buttons.addView(Button(this).apply {
            text = "DURDUR"
            setOnClickListener { stopSlot(slotId) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        box.addView(buttons, lp(dp(8)))

        val status = TextView(this).apply {
            text = "Hazır"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(yellow)
            setPadding(0, dp(8), 0, 0)
        }
        box.addView(status, lp())

        val details = TextView(this).apply {
            text = "Foto 0 • Video 0 • Hata 0"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, 0)
        }
        box.addView(details, lp())

        slots[slotId] = SlotViews(mode, input, status, details)
        return box
    }

    private fun observeSlots() {
        lifecycleScope.launch {
            db.jobs().observeAll().collectLatest { jobs ->
                val latest = jobs.filter { it.slotId in 1..5 }
                    .groupBy { it.slotId }
                    .mapValues { (_, values) -> values.maxByOrNull { it.createdAt } }
                for (slotId in 1..5) render(slotId, latest[slotId])
            }
        }
    }

    private fun render(slotId: Int, job: JobEntity?) {
        val views = slots[slotId] ?: return
        if (job == null) {
            views.status.text = "Hazır"
            views.details.text = "Foto 0 • Video 0 • Hata 0"
            return
        }
        val progress = if (job.progress > 0f && job.progress < 100f) " • %${job.progress.toInt()}" else ""
        views.status.text = "${SlotStateText.turkish(job.state)}$progress • ${job.sourceKey}"

        val speed = if (job.speedBytesPerSec > 0) " • ${formatSpeed(job.speedBytesPerSec)}" else ""
        val elapsed = ((job.updatedAt - job.createdAt).coerceAtLeast(0L) / 1000L)
        val current = job.currentFile?.takeIf { it.isNotBlank() }?.let { "\nDosya: $it" }.orEmpty()
        val error = job.lastErrorMessage?.takeIf { it.isNotBlank() }?.let { "\nHata: ${it.take(180)}" }.orEmpty()
        val destination = OutputPathPolicy.relativePath(job.platform, job.sourceKey, MediaBucket.PHOTO)
            .substringBeforeLast('/')
        views.details.text = "Foto ${job.photoCount} • Video ${job.videoCount} • Hata ${job.failedCount}" +
            "$speed • Süre ${formatElapsed(elapsed)}$current$error\nHedef: $destination"
    }

    private fun startSlot(slotId: Int) {
        val views = slots[slotId] ?: return
        val platform = when (views.mode.selectedItemPosition) {
            0 -> Platform.INSTAGRAM_PROFILE
            1 -> Platform.INSTAGRAM_HASHTAG
            else -> Platform.FACEBOOK
        }
        val parsed = InputParser.parse(platform, views.input.text.toString()).firstOrNull()
        if (parsed == null) {
            Toast.makeText(this, "Motor $slotId için geçerli kaynak gir", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val latest = db.jobs().latestForSlot(slotId)
            if (latest?.state in setOf("QUEUED", "RESOLVING", "DOWNLOADING", "POST_PROCESSING", "RETRY_WAIT")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Motor $slotId zaten aktif; önce durdur", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            db.jobs().clearSlot(slotId)
            val now = System.currentTimeMillis()
            db.jobs().insert(
                JobEntity(
                    platform = parsed.platform.name,
                    sourceKey = parsed.sourceKey,
                    inputValue = parsed.value,
                    state = "QUEUED",
                    createdAt = now,
                    updatedAt = now,
                    slotId = slotId
                )
            )
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, DownloadService::class.java)
                        .setAction(DownloadService.ACTION_START_SLOT)
                        .putExtra(DownloadService.EXTRA_SLOT_ID, slotId)
                )
                Toast.makeText(this@MainActivity, "Motor $slotId başladı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopSlot(slotId: Int) {
        startService(
            Intent(this, DownloadService::class.java)
                .setAction(DownloadService.ACTION_STOP_SLOT)
                .putExtra(DownloadService.EXTRA_SLOT_ID, slotId)
        )
    }

    private fun formatSpeed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024L * 1024L -> String.format("%.1f MB/sn", bytesPerSecond / 1048576.0)
        bytesPerSecond >= 1024L -> String.format("%.0f KB/sn", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/sn"
    }

    private fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
