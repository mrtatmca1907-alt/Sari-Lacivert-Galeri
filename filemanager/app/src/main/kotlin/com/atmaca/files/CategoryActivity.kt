package com.atmaca.files

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.*

class CategoryActivity : Activity() {
    data class Item(val name:String,val uri:Uri,val mime:String)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val items=mutableListOf<Item>()
    private lateinit var list:ListView
    private lateinit var adapter:ArrayAdapter<String>

    override fun onCreate(b:Bundle?){super.onCreate(b);val key=intent.getStringExtra("category")?:"documents";val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(7,25,58));setPadding(14,14,14,14)};root.addView(TextView(this).apply{text=titleFor(key);textSize=23f;setTextColor(Color.rgb(255,220,0));setTypeface(null,1)});list=ListView(this);adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,mutableListOf<String>());list.adapter=adapter;list.setOnItemClickListener{_,_,p,_->open(items[p])};root.addView(list,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);scope.launch{load(key)}}

    private suspend fun load(key:String){val found=withContext(Dispatchers.IO){query(key)};items.clear();items.addAll(found);adapter.clear();adapter.addAll(found.map{it.name});adapter.notifyDataSetChanged()}
    private fun query(key:String):List<Item>{
        val out=mutableListOf<Item>()
        val collection=if(key=="downloads" && android.os.Build.VERSION.SDK_INT>=29) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
        val projection=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE)
        val (sel,args)=when(key){
            "images"->"${MediaStore.MediaColumns.MIME_TYPE} LIKE ?" to arrayOf("image/%")
            "videos"->"${MediaStore.MediaColumns.MIME_TYPE} LIKE ?" to arrayOf("video/%")
            "audio"->"${MediaStore.MediaColumns.MIME_TYPE} LIKE ?" to arrayOf("audio/%")
            "apks"->"${MediaStore.MediaColumns.MIME_TYPE} = ?" to arrayOf("application/vnd.android.package-archive")
            "documents"->"(${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?)" to arrayOf("text/%","application/pdf","application/zip")
            else->null to null
        }
        contentResolver.query(collection,projection,sel,args,"${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);val name=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);val mime=c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);while(c.moveToNext()){val m=c.getString(mime)?:"application/octet-stream";out+=Item(c.getString(name)?:"Adsız",ContentUris.withAppendedId(collection,c.getLong(id)),m)}}
        return out
    }
    private fun open(i:Item){runCatching{startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(i.uri,i.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}}
    private fun titleFor(k:String)=mapOf("images" to "Görseller","videos" to "Videolar","audio" to "Ses","documents" to "Belgeler","apks" to "APK","downloads" to "İndirilenler")[k]?:"Dosyalar"
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
