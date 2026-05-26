package com.example.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class BackgroundLocationService : Service() {
    private val TAG = "BackgroundLocation"
    private val CHANNEL_ID = "finance_location_channel"
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(101, getNotification("Sistem GPS Pelacakan Tunai Aktif"))
        
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            setupLocationUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal inisialisasi Fused Location: ${e.message}")
        }
        
        ServiceStateCoordinator.isLocationServiceRunning.value = true
        ServiceStateCoordinator.addTerminalLog("LocationService: GPS Background tracking berhasil diaktifkan.")
    }

    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
            .setMinUpdateIntervalMillis(15000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lon = location.longitude
                    Log.d(TAG, "GPS Update: $lat, $lon")
                    
                    // Di dunia nyata, di sini terjadi penghitungan geofence:
                    // Apakah koordinat di luar radius rumah dan menetap > 15 menit?
                    // Untuk purposes prototype, kita log di UI:
                    ServiceStateCoordinator.addTerminalLog("GPS: Koordinat update (${String.format("%.4f", lat)}, ${String.format("%.4f", lon)}).")
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            ServiceStateCoordinator.addTerminalLog("LocationService: Error izin lokasi belum lengkap.")
        }
    }

    private fun getNotification(text: String): Notification {
        val intent = Intent(this, com.example.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Finance AI Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan GPS Agent Keuangan",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        ServiceStateCoordinator.isLocationServiceRunning.value = false
        ServiceStateCoordinator.addTerminalLog("LocationService: GPS Background dihentikan.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
