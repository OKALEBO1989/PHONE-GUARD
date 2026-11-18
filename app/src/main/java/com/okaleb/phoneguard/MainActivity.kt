package com.okaleb.phoneguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val prefs = getSharedPreferences("guard", MODE_PRIVATE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val pin = EditText(this).apply {
            hint = "Enter PIN (4+ digits)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val status = TextView(this).apply { text = "Phone Guard" }

        fun add(label: String, action: () -> Unit) {
            box.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        box.addView(status)
        box.addView(pin)

        add("Save PIN") {
            if (pin.text.length >= 4) {
                prefs.edit().putString("pin", pin.text.toString()).apply()
                status.text = "PIN saved"
            } else status.text = "PIN too short"
        }
        add("Arm Guard") {
            if (prefs.getString("pin", null) == null) {
                status.text = "Save a PIN first"
            } else {
                val i = Intent(this, AlarmService::class.java).setAction("ARM")
                startForegroundService(i)
                status.text = "Armed. Put the phone down within 10 seconds."
            }
        }
        add("Stop Alarm") {
            if (pin.text.toString() == prefs.getString("pin", null)) {
                startService(Intent(this, AlarmService::class.java).setAction("STOP"))
                status.text = "Alarm stopped"
            } else status.text = "Wrong PIN"
        }
        setContentView(box)
    }
}
