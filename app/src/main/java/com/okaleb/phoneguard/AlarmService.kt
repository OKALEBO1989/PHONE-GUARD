package com.okaleb.phoneguard

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.ServiceCompat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

class AlarmService : Service(), SensorEventListener, TextToSpeech.OnInitListener {
    private lateinit var sm: SensorManager
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ringing = false
    private var armedAt = 0L
    private val rate = 22050

    private val loop = object : Runnable {
        override fun run() {
            if (!ringing) return
            tts?.speak("This phone is stolen from the owner!",
                TextToSpeech.QUEUE_FLUSH, null, "alarm")
            handler.postDelayed(this, 9000)
        }
    }

    private fun addHit(mix: FloatArray, start: Int, type: Int, rnd: java.util.Random) {
        if (type == 0) return
        val len = (rate * 0.3).toInt()
        var phase = 0.0
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= mix.size) break
            val t = n.toDouble() / rate
            val v = when (type) {
                1 -> {
                    val f = 60.0 + 110.0 * exp(-t * 18.0)
                    phase += 2 * PI * f / rate
                    sin(phase) * exp(-t * 9.0)
                }
                2 -> {
                    val f = 200.0 + 120.0 * exp(-t * 25.0)
                    phase += 2 * PI * f / rate
                    sin(phase) * exp(-t * 14.0)
                }
                else -> (rnd.nextDouble() * 2 - 1) * exp(-t * 35.0) * 0.8
            }
            mix[idx] += v.toFloat()
        }
    }

    private fun buildPeriod(): ShortArray {
        val step = (rate * 0.15).toInt()
        val pattern = intArrayOf(1, 0, 3, 2, 0, 3, 1, 0, 3, 2, 3, 3)
        val total = rate * 9
        val mix = FloatArray(total)
        val rnd = java.util.Random(7)
        val cycle = step * pattern.size
        var start = 0
        while (start < cycle * 3) {
            for (i in pattern.indices) {
                addHit(mix, start + i * step, pattern[i], rnd)
            }
            start += cycle
        }
        return ShortArray(total) { (tanh(mix[it] * 2.5) * 30000).toInt().toShort() }
    }

    private fun startSiren() {
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        Thread {
            val period = buildPeriod()
            track.play()
            while (ringing) {
                var pos = 0
                while (pos < period.size && ringing) {
                    val n = minOf(2205, period.size - pos)
                    track.write(period, pos, n)
                    pos += n
                }
            }
            track.stop()
            track.release()
        }.start()
    }

    override fun onCreate() {
        super.onCreate()
        sm = getSystemService(SENSOR_SERVICE) as SensorManager
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        t.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        val r = t.setLanguage(Locale("en", "NG"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.ENGLISH)
        }
        t.setSpeechRate(0.9f)
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
            startSiren()
            handler.postDelayed(loop, 5600)
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun stopAlarm() {
        ringing = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        sm.unregisterListener(this)
    }

    override fun onDestroy() {
        stopAlarm()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
