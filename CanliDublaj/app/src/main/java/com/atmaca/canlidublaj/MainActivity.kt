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
 private lateinit var dubbing: MicrophoneDubbing
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  speech=TurkishSpeech(this)
  dubbing=MicrophoneDubbing(this) { message -> runOnUiThread { findViewById<TextView>(12345)?.text=message } }
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,64,32,32) }
  val status=TextView(this).apply { id=12345; text="ATMACA Canlı Dublaj — prototip. Türkçe ses testi ve ekran yakalama izni."; textSize=18f }
  val button=Button(this).apply { text="Ses yakalama izni iste"; setOnClickListener {
   val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   startActivityForResult(manager.createScreenCaptureIntent(),100)
  } }
  val test=Button(this).apply { text="Türkçe seslendirmeyi test et"; setOnClickListener {
   val ok=speech.speak("ATMACA Türkçe seslendirme testi")
   status.text=if(ok) "Türkçe seslendirme başlatıldı" else "Türkçe ses motoru henüz hazır değil"
  } }
  val start=Button(this).apply { text="Mikrofondan Türkçe dublaj başlat";setOnClickListener {
   if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),200)
   else dubbing.start()
  } }
  val stop=Button(this).apply { text="Dublajı durdur";setOnClickListener { dubbing.stop() } }
  layout.addView(status);layout.addView(button);layout.addView(test);layout.addView(start);layout.addView(stop);setContentView(layout)
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==100) {
   if(resultCode==RESULT_OK && data!=null) {
    val service=Intent(this,CaptureService::class.java)
    service.putExtra("projection_code",resultCode)
    service.putExtra("projection_data",data)
    startForegroundService(service)
    android.widget.Toast.makeText(this,"Ses yakalama testi başladı",android.widget.Toast.LENGTH_LONG).show()
   } else android.widget.Toast.makeText(this,"İzin verilmedi",android.widget.Toast.LENGTH_LONG).show()
  }
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==200 && grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)dubbing.start() }
 override fun onDestroy(){dubbing.release();speech.release();super.onDestroy()}
}