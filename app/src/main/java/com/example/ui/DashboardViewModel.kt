package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.TransactionEntity
import com.example.data.database.TransactionRepository
import com.example.services.BackgroundLocationService
import com.example.services.ServiceStateCoordinator
import com.example.services.WhatsAppMockPrompt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository: TransactionRepository

    val transactions: StateFlow<List<TransactionEntity>>
    val terminalLogs = ServiceStateCoordinator.terminalLogs
    val whatsappBotPrompts = ServiceStateCoordinator.whatsappBotPrompts

    val serverUrl = ServiceStateCoordinator.serverBaseUrl
    val isSandboxMode = ServiceStateCoordinator.isSandboxMode

    val isNotificationServiceRunning = ServiceStateCoordinator.isNotificationServiceRunning
    val isAccessibilityServiceRunning = ServiceStateCoordinator.isAccessibilityServiceRunning
    val isLocationServiceRunning = ServiceStateCoordinator.isLocationServiceRunning

    init {
        val database = AppDatabase.getDatabase(context)
        repository = TransactionRepository(database.transactionDao())
        
        transactions = repository.allTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun updateServerUrl(url: String) {
        ServiceStateCoordinator.serverBaseUrl.value = url
        ServiceStateCoordinator.addTerminalLog("Perubahan URL Server: \"$url\"")
    }

    fun toggleSandboxMode(enabled: Boolean) {
        ServiceStateCoordinator.isSandboxMode.value = enabled
        ServiceStateCoordinator.addTerminalLog("Sandbox Mode diubah ke: ${if (enabled) "AKTIF (Lokal/Gemini)" else "NONAKTIF (Remote murni)"}")
    }

    fun toggleLocationService(enabled: Boolean) {
        val intent = Intent(context, BackgroundLocationService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    // --- FITUR SIMULASI TRIGGER (Mempermudah Pengujian di Sisi Emulator Browser) ---

    fun simulateNotificationReceived(appName: String, rawText: String) {
        ServiceStateCoordinator.addTerminalLog("Simulasi Notifikasi Masuk: [$appName] $rawText")
        ServiceStateCoordinator.processRawFinancialEvent(context, "NOTIFIKASI", "[$appName] $rawText")
    }

    fun simulateScrapeScreen(appName: String, layoutText: String) {
        ServiceStateCoordinator.addTerminalLog("Simulasi Membaca Layar Aktif [$appName]: \"$layoutText\"")
        ServiceStateCoordinator.processRawFinancialEvent(context, "LAYAR", "[$appName Screen] $layoutText")
    }

    fun simulateLocationVisit(address: String, lat: Double, lon: Double, durationMin: Int) {
        ServiceStateCoordinator.addTerminalLog("Simulasi Geofencing: Menetap di \"$address\" selama $durationMin Menit.")
        ServiceStateCoordinator.processLocationEvent(context, lat, lon, address, durationMin)
    }

    fun replyToWhatsAppBot(promptId: String, userReply: String) {
        ServiceStateCoordinator.processWhatsAppReply(context, promptId, userReply)
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAll()
            ServiceStateCoordinator.addTerminalLog("Dashboard: Riwayat transaksi lokal dibersihkan.")
        }
    }

    fun insertManualDemoTransaction(nominal: Double, category: String, type: String, desc: String, source: String) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                nominal = nominal,
                category = category,
                type = type,
                description = desc,
                source = source
            )
            repository.insert(tx)
            ServiceStateCoordinator.addTerminalLog("Sukses mencatat manual: Rp $nominal ($desc)")
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
            ServiceStateCoordinator.addTerminalLog("Transaksi dengan ID $id dihapus.")
        }
    }
}
