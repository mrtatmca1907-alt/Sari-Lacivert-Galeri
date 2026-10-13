package com.atmaca.canlidublaj

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    @Volatile private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "atmaca_capture"
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(channel, "ATMACA ses yakalama", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channel)
            .setContentTitle("ATMACA ses yakalama testi")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        @Suppress("DEPRECATION")
        val permission = intent?.getParcelableExtra<Intent>("projection_data")
        val code = intent?.getIntExtra("projection_code", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        if (permission == null || code != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val session = manager.getMediaProjection(code, permission)
            projection = session
            session.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
            val config = AudioPlaybackCaptureConfiguration.Builder(session)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minSize = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minSize, 8192)
            val audio = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            recorder = audio
            audio.startRecording()
            active = true
            Thread {
                val pcm = ShortArray(4096)
                while (active) {
                    val count = audio.read(pcm, 0, pcm.size)
                    if (count <= 0) break
                    Log.d("ATMACA", "Captured audio samples: $count")
                }
            }.start()
        } catch (e: Exception) {
            Log.e("ATMACA", "Audio capture failed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        active = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        projection?.stop()
        super.onDestroy()
    }
}
