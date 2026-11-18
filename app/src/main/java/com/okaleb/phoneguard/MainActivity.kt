package com.okaleb.phoneguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(perms.toTypedArray(), 1)

        val prefs = getSharedPreferences("guard", MODE_PRIVATE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val status = TextView(this).apply { text = "Phone Guard" }
        val pin = EditText(this).apply {
            hint = "Enter PIN (4+ digits)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val phone = EditText(this).apply {
            hint = "Trusted number, e.g. +2348012345678"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("phone", "") ?: "")
        }

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

        box.addView(phone)
        add("Save Trusted Number") {
            val num = phone.text.toString().trim()
            if (num.length >= 7) {
                prefs.edit().putString("phone", num).apply()
                status.text = "Trusted number saved"
            } else status.text = "Number too short"
        }

        add("Show My Location") {
            status.text = "Finding location..."
            Locator.get(this) { loc ->
                if (loc == null) {
                    status.text = "Location not available. Turn on GPS and allow Location."
                } else {
                    status.text = "Lat " + loc.latitude + ", Lng " + loc.longitude
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Locator.link(loc))))
                    } catch (e: Exception) {
                    }
                }
            }
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
