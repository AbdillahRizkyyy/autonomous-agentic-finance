package com.example.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FinanceNotificationListenerService : NotificationListenerService() {
    private val TAG = "FinanceNotificationListener"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Tersambung")
        ServiceStateCoordinator.isNotificationServiceRunning.value = true
        ServiceStateCoordinator.addTerminalLog("NotificationListenerService: AKTIF & Menyimak.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification Listener Terputus")
        ServiceStateCoordinator.isNotificationServiceRunning.value = false
        ServiceStateCoordinator.addTerminalLog("NotificationListenerService: NONAKTIF.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Filter e-wallet / bank populer di Indonesia atau kata kunci transaksi keuangan
        val isFinancialApp = packageName.contains("dana", ignoreCase = true) ||
                packageName.contains("shopee", ignoreCase = true) ||
                packageName.contains("gojek", ignoreCase = true) ||
                packageName.contains("grab", ignoreCase = true) ||
                packageName.contains("ovo", ignoreCase = true) ||
                packageName.contains("linkaja", ignoreCase = true) ||
                packageName.contains("bca", ignoreCase = true) ||
                packageName.contains("mandiri", ignoreCase = true)

        val containsFinancialKeywords = text.contains("berhasil", ignoreCase = true) ||
                text.contains("bayar", ignoreCase = true) ||
                text.contains("transfer", ignoreCase = true) ||
                text.contains("pembayaran", ignoreCase = true) ||
                text.contains("saldo", ignoreCase = true) ||
                text.contains("kirim", ignoreCase = true) ||
                text.contains("diterima", ignoreCase = true) ||
                text.contains("sukses", ignoreCase = true) ||
                text.contains("nominal", ignoreCase = true) ||
                text.contains("Rp", ignoreCase = true)

        if (isFinancialApp || containsFinancialKeywords) {
            val rawText = "[$packageName] $title: $text"
            Log.d(TAG, "Menangkap Notifikasi Keuangan: $rawText")
            ServiceStateCoordinator.processRawFinancialEvent(this, "NOTIFIKASI", rawText)
        }
    }
}
