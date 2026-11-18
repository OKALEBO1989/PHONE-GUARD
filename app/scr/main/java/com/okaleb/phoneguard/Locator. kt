package com.okaleb.phoneguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

object Locator {
    fun hasPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun link(l: Location): String =
        "https://maps.google.com/?q=${l.latitude},${l.longitude}"

    @SuppressLint("MissingPermission")
    fun get(c: Context, done: (Location?) -> Unit) {
        if (!hasPermission(c)) {
            done(null)
            return
        }
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = lm.getLastKnownLocation(p) ?: continue
            val cur = best
            if (cur == null || l.time > cur.time) best = l
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            done(best)
            return
        }
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (finished) return
                finished = true
                lm.removeUpdates(this)
                done(loc)
            }
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        handler.postDelayed({
            if (!finished) {
                finished = true
                lm.removeUpdates(listener)
                done(best)
            }
        }, 20000)
    }
}
