package com.atmaca.canlidublaj
import android.app.Activity
import android.os.Bundle
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.speech.tts.TextToSpeech

class MainActivity: Activity() {
 private lateinit var speech: TurkishSpeech
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  speech=TurkishSpeech(this)
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,64,32,32) }
  val status=TextView(this).apply { text="ATMACA Canlı Dublaj — prototip. Türkçe ses testi ve ekran yakalama izni."; textSize=18f }
  val button=Button(this).apply { text="Ses yakalama izni iste"; setOnClickListener {
   val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   startActivityForResult(manager.createScreenCaptureIntent(),100)
  } }
  val test=Button(this).apply { text="Türkçe seslendirmeyi test et"; setOnClickListener {
   val ok=speech.speak("ATMACA Türkçe seslendirme testi")
   status.text=if(ok) "Türkçe seslendirme başlatıldı" else "Türkçe ses motoru henüz hazır değil"
  } }
  layout.addView(status);layout.addView(button);layout.addView(test);setContentView(layout)
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==100) android.widget.Toast.makeText(this,if(resultCode==RESULT_OK) "İzin verildi; Facebook ses erişimi ayrıca test edilmeli" else "İzin verilmedi",android.widget.Toast.LENGTH_LONG).show()
 }
 override fun onDestroy(){speech.release();super.onDestroy()}
}