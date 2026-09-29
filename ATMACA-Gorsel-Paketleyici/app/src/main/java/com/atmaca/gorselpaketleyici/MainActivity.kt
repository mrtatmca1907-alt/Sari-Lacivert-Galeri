package com.atmaca.gorselpaketleyici

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : AppCompatActivity() {
 private lateinit var status: TextView
 private val pickTree=7001
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);status=findViewById(R.id.status);findViewById<Button>(R.id.select).setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},pickTree)}}
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r!=pickTree||c!=RESULT_OK||d?.data==null)return;val u=d.data!!;contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);val root=DocumentFile.fromTreeUri(this,u)?:return;Thread{val files=mutableListOf<DocumentFile>();collect(root,files);var moved=0;var no=nextNo(root);files.chunked(1000).forEach{g->val dir=root.createDirectory("Paket_1000_"+no.toString().padStart(4,'0'))?:return@forEach;g.forEach{if(move(it,dir))moved++};no++;runOnUiThread{status.text=moved.toString()+" / "+files.size+" dosya ayrıldı"}};runOnUiThread{status.text="Bitti: "+moved+" dosya 1000'erli gruplara ayrıldı."}}.start()}
 private fun excluded(f:DocumentFile):Boolean{val n=(f.name?:"").lowercase(Locale.ROOT).trim();return n.startsWith("paket_1000_")}
 private fun collect(d:DocumentFile,o:MutableList<DocumentFile>){d.listFiles().forEach{f->if(f.isDirectory){if(!excluded(f))collect(f,o)}else if(f.isFile)o+=f}}
 private fun nextNo(r:DocumentFile):Int{val a=r.listFiles().mapNotNull{Regex("""(?i)^paket_1000_(\d+)$""").find(it.name?:"")?.groupValues?.getOrNull(1)?.toIntOrNull()};return(a.maxOrNull()?:0)+1}
 private fun move(s:DocumentFile,d:DocumentFile):Boolean{
  val name=s.name?:"dosya_"+System.nanoTime()
  val target=d.createFile(s.type?:"application/octet-stream",unique(d,name))?:return false
  return try{
   val expected=s.length()
   contentResolver.openInputStream(s.uri).use{input->
    contentResolver.openOutputStream(target.uri,"w").use{output->
     if(input==null||output==null)throw Exception("Akis acilamadi")
     val buf=ByteArray(1024*1024)
     var total=0L
     while(true){val n=input.read(buf);if(n<0)break;output.write(buf,0,n);total+=n}
     output.flush()
     if(expected>0L&&total!=expected)throw Exception("Boyut dogrulanamadi")
    }
   }
   val written=target.length()
   if(expected>0L&&written>0L&&written!=expected)throw Exception("Hedef boyutu farkli")
   if(!s.delete())throw Exception("Kaynak silinemedi")
   true
  }catch(e:Exception){target.delete();false}
 }
 private fun unique(d:DocumentFile,n:String):String{if(d.findFile(n)==null)return n;val x=n.lastIndexOf('.');val b=if(x>0)n.substring(0,x)else n;val e=if(x>0)n.substring(x)else"";var i=1;while(d.findFile(b+"_"+i+e)!=null)i++;return b+"_"+i+e}
}
