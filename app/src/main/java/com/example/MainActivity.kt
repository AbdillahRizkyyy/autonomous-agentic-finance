package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.services.ServiceStateCoordinator
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Registrasi Peminta Izin Gabungan (Fine & Coarse GPS Location, Post Notifications)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val postNotifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false

        if (fineGranted || coarseGranted) {
            ServiceStateCoordinator.addTerminalLog("Izin Lokasi GPS diberikan.")
        } else {
            ServiceStateCoordinator.addTerminalLog("⚠ Izin Lokasi GPS ditolak oleh pengguna.")
        }

        if (postNotifGranted) {
            ServiceStateCoordinator.addTerminalLog("Izin Notifikasi diberikan.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Periksa dan minta izin di awal
        checkAndRequestPermissions()

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        onOpenSystemSettings = { openServiceActivationSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cek kembali status keaktifan sensor sistem setiap kali user membuka kembali aplikasi
        checkSystemServicesState()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * Memeriksa apakah Notification Listener & Accessibility Service diaktifkan di Android
     */
    private fun checkSystemServicesState() {
        val isNotificationEnabled = isNotificationListenerServiceEnabled(this)
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, com.example.services.FinanceAccessibilityService::class.java)

        ServiceStateCoordinator.isNotificationServiceRunning.value = isNotificationEnabled
        ServiceStateCoordinator.isAccessibilityServiceRunning.value = isAccessibilityEnabled

        Log.d("MainActivity", "NotifListenerActive: $isNotificationEnabled, AccessibilityActive: $isAccessibilityEnabled")
    }

    /**
     * Membuka pengaturan sistem bagi user untuk mengaktifkan sensor accessibility / notification listener
     */
    private fun openServiceActivationSettings() {
        val isNotifEnabled = isNotificationListenerServiceEnabled(this)
        val isAccEnabled = isAccessibilityServiceEnabled(this, com.example.services.FinanceAccessibilityService::class.java)

        if (!isNotifEnabled) {
            Toast.makeText(this, "Silakan aktifkan Finance AI Agent di akses notifikasi", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            ServiceStateCoordinator.addTerminalLog("Navigasi: Membuka Pengaturan Akses Notifikasi.")
        } else if (!isAccEnabled) {
            Toast.makeText(this, "Silakan aktifkan Finance AI Agent di aksesibilitas", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            ServiceStateCoordinator.addTerminalLog("Navigasi: Membuka Pengaturan Aksesibilitas Sistem.")
        } else {
            Toast.makeText(this, "Semua izin sensor systems telah aktif! 👍", Toast.LENGTH_SHORT).show()
            ServiceStateCoordinator.addTerminalLog("Info: Semua izin sensor systems valid.")
        }
    }

    // Helper: Periksa Accessibility Service status
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    // Helper: Periksa Notification Listener Service status
    private fun isNotificationListenerServiceEnabled(context: Context): Boolean {
        val packageNames = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(packageNames) && packageNames.contains(context.packageName)
    }
}
