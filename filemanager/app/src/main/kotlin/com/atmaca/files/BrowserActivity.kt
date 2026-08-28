package com.atmaca.files

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class BrowserActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var current: DocumentFile
    private lateinit var adapter: DocAdapter
    private lateinit var title: TextView
    private val selected = linkedSetOf<String>()
    private var pendingAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = Uri.parse(intent.getStringExtra("uri") ?: run { finish(); return })
        current = if (intent.getBooleanExtra("isRoot", false)) DocumentFile.fromTreeUri(this, uri) else DocumentFile.fromSingleUri(this, uri)
            ?: run { finish(); return }
        buildUi(); load()
    }

    private fun buildUi() {
        val navy = Color.rgb(7,25,58); val yellow=Color.rgb(255,220,0)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(navy)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(10),dp(8),dp(10),dp(8))}
        top.addView(Button(this).apply{text="‹";textSize=25f;setOnClickListener{finish()}},LinearLayout.LayoutParams(dp(58),dp(52)))
        title=TextView(this).apply{textSize=20f;setTextColor(yellow);setTypeface(null,1);setPadding(dp(10),dp(12),0,0)}
        top.addView(title,LinearLayout.LayoutParams(0,dp(52),1f));root.addView(top)
        val rv=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@BrowserActivity);setHasFixedSize(true)}
        adapter=DocAdapter(emptyList(),selected,{f->onClick(f)},{f->toggle(f)})
        rv.adapter=adapter;root.addView(rv,LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(4),dp(5),dp(4),dp(7))}
        fun add(label:String, action:()->Unit){bar.addView(Button(this).apply{text=label;textSize=11f;setOnClickListener{action()}},LinearLayout.LayoutParams(0,dp(54),1f))}
        add("YENİ") { newFolder() };add("YENİDEN AD") { rename() };add("KOPYALA") { pickDestination("copy") };add("TAŞI") { pickDestination("move") };add("SİL") { deleteSelected() }
        root.addView(bar);setContentView(root)
    }

    private fun load(){
        title.text=current.name ?: "Depolama"
        scope.launch { val list=withContext(Dispatchers.IO){current.listFiles().sortedWith(compareByDescending<DocumentFile>{it.isDirectory}.thenBy{it.name?.lowercase()})};adapter.submit(list) }
    }
    private fun onClick(f:DocumentFile){
        if(selected.isNotEmpty()){toggle(f);return}
        if(f.isDirectory) startActivity(Intent(this,BrowserActivity::class.java).putExtra("uri",f.uri.toString()).putExtra("isRoot",false))
        else openFile(f)
    }
    private fun toggle(f:DocumentFile){val k=f.uri.toString();if(!selected.add(k))selected.remove(k);adapter.refreshSelection();title.text=if(selected.isEmpty()) current.name?:"Depolama" else "${selected.size} seçili"}
    private fun docsSelected()=selected.mapNotNull{DocumentFile.fromSingleUri(this,Uri.parse(it))}

    private fun newFolder(){
        val e=EditText(this).apply{hint="Klasör adı"}
        AlertDialog.Builder(this).setTitle("Yeni klasör").setView(e).setPositiveButton("OLUŞTUR"){_,_->val n=e.text.toString();if(OperationRules.validName(n)){if(current.createDirectory(n)==null) toast("Oluşturulamadı");load()}else toast("Geçersiz ad")}.setNegativeButton("İPTAL",null).show()
    }
    private fun rename(){
        val docs=docsSelected();if(docs.size!=1){toast("Yeniden adlandırmak için 1 öğe seç");return};val e=EditText(this).apply{setText(docs[0].name)}
        AlertDialog.Builder(this).setTitle("Yeniden adlandır").setView(e).setPositiveButton("KAYDET"){_,_->val n=e.text.toString();if(OperationRules.validName(n)&&docs[0].renameTo(n)){selected.clear();load()}else toast("Ad değiştirilemedi")}.setNegativeButton("İPTAL",null).show()
    }
    private fun deleteSelected(){val docs=docsSelected();if(docs.isEmpty()){toast("Öğe seç");return};AlertDialog.Builder(this).setTitle("${docs.size} öğe silinsin mi?").setPositiveButton("SİL"){_,_->scope.launch{withContext(Dispatchers.IO){docs.forEach{it.delete()}};selected.clear();load()}}.setNegativeButton("İPTAL",null).show()}
    private fun pickDestination(action:String){if(selected.isEmpty()){toast("Öğe seç");return};pendingAction=action;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),77)}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==77&&c==RESULT_OK){val u=d?.data?:return;val dest=DocumentFile.fromTreeUri(this,u)?:return;val docs=docsSelected();val action=pendingAction;scope.launch{val ok=withContext(Dispatchers.IO){docs.all{if(action=="move")FileOps.move(this@BrowserActivity,it,dest) else FileOps.copy(this@BrowserActivity,it,dest)}};toast(if(ok)"İşlem tamam" else "Bazı dosyalar işlenemedi");selected.clear();load()}}}
    private fun openFile(f:DocumentFile){runCatching{startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(f.uri,f.type?:"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{toast("Bu dosyayı açacak uygulama bulunamadı")}}
    override fun onResume(){super.onResume();if(::adapter.isInitialized)load()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)
}
