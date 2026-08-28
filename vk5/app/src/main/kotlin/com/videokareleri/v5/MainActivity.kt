package com.videokareleri.v5

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import java.util.LinkedHashSet

class MainActivity : Activity() {
    private val selected = LinkedHashSet<String>()
    private lateinit var folders: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var choose: Button
    private lateinit var start: Button
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            val v=i.getIntExtra("videos",0); val t=i.getIntExtra("total",0)
            val s=i.getIntExtra("saved",0); val k=i.getIntExtra("skipped",0); val e=i.getIntExtra("errors",0)
            val cur=i.getStringExtra("current").orEmpty(); val fin=i.getBooleanExtra("finished",false)
            progress.isIndeterminate=t<=0
            if(t>0){ progress.max=t; progress.progress=v }
            status.text="$cur\nVideo: $v/$t   Kaydedilen: $s   Zaten var: $k   Hata: $e"
            if(fin){ start.isEnabled=true; choose.isEnabled=true }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi(); loadSelection(); ensurePermission()
    }

    private fun buildUi() {
        val navy=Color.rgb(0,35,102); val yellow=Color.rgb(255,220,0)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(24),dp(20),dp(20)); setBackgroundColor(navy) }
        root.addView(TextView(this).apply { text="VİDEO KARELERİ V5"; textSize=26f; setTextColor(yellow); setTypeface(null,1) }, LinearLayout.LayoutParams(-1,-2))
        root.addView(TextView(this).apply { text="Native Kotlin • Hafif mod • Her saniyeden 1 JPEG"; setTextColor(Color.WHITE); textSize=14f; setPadding(0,dp(8),0,dp(18)) })
        choose=button("VİDEO KLASÖRLERİNİ SEÇ",yellow,navy).also { it.setOnClickListener { chooseFolders() } }
        root.addView(choose,LinearLayout.LayoutParams(-1,dp(54)))
        folders=TextView(this).apply { setTextColor(Color.WHITE); textSize=15f; setPadding(0,dp(14),0,dp(14)) }
        root.addView(folders)
        start=button("KARELERİ ÇIKAR",yellow,navy).also { it.setOnClickListener { startJob() } }
        root.addView(start,LinearLayout.LayoutParams(-1,dp(54)))
        val stop=button("DURDUR",Color.WHITE,navy).apply { setOnClickListener { startService(Intent(this@MainActivity,FrameExtractService::class.java).setAction(FrameExtractService.ACTION_CANCEL)) } }
        root.addView(stop,LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(10) })
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal)
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(12)).apply { topMargin=dp(20) })
        status=TextView(this).apply { text="Hazır"; setTextColor(Color.WHITE); textSize=15f; setPadding(0,dp(14),0,0) }
        root.addView(status)
        setContentView(root)
    }

    private fun button(text:String,bg:Int,fg:Int)=Button(this).apply { this.text=text; setTextColor(fg); textSize=16f; setTypeface(null,1); setBackgroundColor(bg) }
    private fun requiredPermission()=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun hasPermission()=checkSelfPermission(requiredPermission())==PackageManager.PERMISSION_GRANTED
    private fun ensurePermission(){ if(!hasPermission()) requestPermissions(arrayOf(requiredPermission()),REQ_MEDIA) }

    private fun chooseFolders() {
        if(!hasPermission()){ ensurePermission(); return }
        choose.isEnabled=false; status.text="Video klasörleri sayılıyor..."
        scope.launch {
            val map=withContext(Dispatchers.IO){ MediaStoreVideoRepository(contentResolver).loadFolderCounts() }
            choose.isEnabled=true; showFolderDialog(map)
        }
    }

    private fun showFolderDialog(map: LinkedHashMap<String,Int>) {
        if(map.isEmpty()){ status.text="Erişilebilir video bulunamadı."; return }
        val paths=map.keys.toTypedArray(); val labels=Array<CharSequence>(paths.size){ i -> "${paths[i]}  (${map[paths[i]]})" }; val checked=BooleanArray(paths.size){ selected.contains(paths[it]) }
        AlertDialog.Builder(this).setTitle("Video klasörlerini seç")
            .setMultiChoiceItems(labels,checked){_,which,isChecked-> if(isChecked) selected.add(paths[which]) else selected.remove(paths[which]) }
            .setPositiveButton("TAMAM"){_,_->saveSelection();refreshFolders()}.setNegativeButton("İPTAL",null).show()
    }

    private fun startJob() {
        if(selected.isEmpty()){ Toast.makeText(this,"Önce en az bir klasör seç",Toast.LENGTH_SHORT).show(); return }
        val i=Intent(this,FrameExtractService::class.java).setAction(FrameExtractService.ACTION_START).putExtra(FrameExtractService.EXTRA_PATHS,selected.toTypedArray())
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
        start.isEnabled=false; choose.isEnabled=false; status.text="İşlem başlatıldı..."
    }

    private fun saveSelection()=getSharedPreferences("vk5",MODE_PRIVATE).edit().putStringSet("folders",selected).apply()
    private fun loadSelection(){ selected.clear(); selected.addAll(getSharedPreferences("vk5",MODE_PRIVATE).getStringSet("folders",emptySet()).orEmpty()); refreshFolders() }
    private fun refreshFolders(){ folders.text=if(selected.isEmpty()) "Seçili klasör: yok" else "Seçili klasör: ${selected.size}" }

    override fun onStart(){ super.onStart(); val f=IntentFilter(FrameExtractService.ACTION_PROGRESS); if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,f) }
    override fun onStop(){ runCatching{unregisterReceiver(receiver)}; super.onStop() }
    override fun onDestroy(){ scope.cancel(); super.onDestroy() }
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)
    companion object { const val REQ_MEDIA=41 }
}
