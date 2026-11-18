package com.okaleb.phoneguard

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.ServiceCompat
import kotlin.math.abs
import kotlin.math.sqrt

class AlarmService : Service(), SensorEventListener, TextToSpeech.OnInitListener {
    private lateinit var sm: SensorManager
    private var tts: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringing = false
    private var armedAt = 0L

    private val loop = object : Runnable {
        override fun run() {
            if (!ringing) return
            tone?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000)
            tts?.speak("THIS PERSON IS A THIEF. OONO MUBBI!",
                TextToSpeech.QUEUE_FLUSH, null, "alarm")
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        tts = TextToSpeech(this, this)
        tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    }

    override fun onInit(status: Int) {
        tts?.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }
        val ch = NotificationChannel("guard", "Phone Guard", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "guard")
            .setContentTitle("Phone Guard is armed")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
        ServiceCompat.startForeground(this, 1, n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        armedAt = System.currentTimeMillis()
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (ringing || System.currentTimeMillis() - armedAt < 10000) return
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] +
            e.values[2] * e.values[2]) - 9.8f
        if (abs(g) > 3f) {
            ringing = true
            val am = getSystemService(AudioManager::class.java)
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            handler.post(loop)
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun stopAlarm() {
        ringing = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tone?.stopTone()
        sm.unregisterListener(this)
    }

    override fun onDestroy() {
        stopAlarm()
        tts?.shutdown()
        tone?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
