package com.example.services

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.TransactionEntity
import com.example.data.network.GeminiContent
import com.example.data.network.GeminiGenerateContentRequest
import com.example.data.network.GeminiGenerationConfig
import com.example.data.network.GeminiPart
import com.example.data.network.NetworkClient
import com.example.data.network.WebhookData
import com.example.data.network.WebhookPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * ServiceStateCoordinator: Jembatan komunikasi state & pemicu simulasi layaknya aslinya.
 */
object ServiceStateCoordinator {
    private const val TAG = "ServiceCoordinator"

    // Konfigurasi
    var serverBaseUrl = MutableStateFlow("https://your-backend-api.com")
    var isSandboxMode = MutableStateFlow(true) // Default ke Sandbox murni agar lancar berjalan di emulator!

    // Logs Berjalan (Terminal Log)
    private val _terminalLogs = MutableStateFlow<List<String>>(
        listOf(
            "[18:48:42] 🤖 Finance AI Agent diinisialisasi.",
            "[18:48:43] State: Sandbox Offline Mode Aktif (Menggunakan Direct Gemini 3.5 Flash & Room)."
        )
    )
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // Status Layanan Aktif
    val isNotificationServiceRunning = MutableStateFlow(false)
    val isAccessibilityServiceRunning = MutableStateFlow(false)
    val isLocationServiceRunning = MutableStateFlow(false)

    // WhatsApp SIDOBE Bot mock state untuk UI
    private val _whatsappBotPrompts = MutableStateFlow<List<WhatsAppMockPrompt>>(emptyList())
    val whatsappBotPrompts: StateFlow<List<WhatsAppMockPrompt>> = _whatsappBotPrompts.asStateFlow()

    fun addTerminalLog(message: String) {
        val timeStamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val msg = "[$timeStamp] $message"
        _terminalLogs.value = _terminalLogs.value.takeLast(100) + msg
        Log.d(TAG, message)
    }

    /**
     * Memproses teks mentah (dari listener notifikasi atau accessibility)
     * Menggunakan Gemini AI Direct (Prototyping Mode) jika sandbox aktif, atau mengirim webhook jika server aktif.
     */
    fun processRawFinancialEvent(context: Context, source: String, rawText: String) {
        addTerminalLog("Sensor [$source] menangkap teks: \"${rawText.take(55)}...\"")

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            // KIRIM WEBHOOK (Skenario Utama)
            val currentServerUrl = serverBaseUrl.value
            if (currentServerUrl.isNotEmpty() && currentServerUrl.startsWith("http")) {
                try {
                    addTerminalLog("Mengirim Webhook ke: $currentServerUrl/webhook/android-agent")
                    val dynamicService = NetworkClient.getDynamicService(currentServerUrl)
                    val payload = WebhookPayload(
                        type = source.lowercase(),
                        data = WebhookData(text = rawText)
                    )
                    val response: Response<ResponseBody> = dynamicService.sendWebhook(
                        url = "$currentServerUrl/webhook/android-agent",
                        payload = payload
                    )
                    if (response.isSuccessful) {
                        addTerminalLog("Webhook Sukses Terkirim! Status: ${response.code()}")
                    } else {
                        addTerminalLog("Webhook Gagal: HTTP ${response.code()} ${response.message()}")
                    }
                } catch (e: Exception) {
                    addTerminalLog("Kejutan Webhook error: ${e.message}")
                }
            }

            // PROSES PARSING GEMINI LOKAL (Simulator/Sandbox Mode)
            if (isSandboxMode.value) {
                addTerminalLog("Menganalisis data transaksi lokal dengan Gemini 3.5 Flash...")
                val parsedResult = parseWithLocalGemini(rawText, source)
                if (parsedResult != null) {
                    if (parsedResult.isAmbiguous == true || parsedResult.nominal == 0.0) {
                        // Skenario Ambigu: Munculkan Prompt WA Bot Interaktif di aplikasi
                        addTerminalLog("⚠ AI mendeteksi pesan ambigu. Menyiapkan klarifikasi proaktif WhatsApp Bot via SIDOBE...")
                        val refId = "TX_" + System.currentTimeMillis()
                        val botPrompt = WhatsAppMockPrompt(
                            id = refId,
                            rawText = rawText,
                            source = source,
                            predictedCategory = parsedResult.category ?: "Lainnya",
                            predictedNominal = parsedResult.nominal ?: 0.0,
                            systemMessage = "Halo! Saya mendeteksi aktivitas dari $source: \n\"$rawText\"\n\nApakah ini transaksi *${parsedResult.category}* sebesar *Rp ${parsedResult.nominal}*?\n\nBalas 'Ya' untuk merekam, atau balas dengan detail koreksi verbal Anda.",
                            isAnswered = false
                        )
                        _whatsappBotPrompts.value = _whatsappBotPrompts.value + botPrompt
                    } else {
                        // Skenario Jelas: Catat langsung ke Room Database
                        val newTx = TransactionEntity(
                            nominal = parsedResult.nominal ?: 0.0,
                            category = parsedResult.category ?: "Lainnya",
                            type = parsedResult.type?.uppercase() ?: "EXPENSE",
                            description = parsedResult.description ?: rawText,
                            source = source.uppercase()
                        )
                        AppDatabase.getDatabase(context).transactionDao().insertTransaction(newTx)
                        addTerminalLog("✅ Sukses Mencatat Transaksi ke Room: Rp ${newTx.nominal} (${newTx.description})")

                        // Simulasikan pesan WhatsApp rekapan masuk dari SIDOBE
                        val successRef = "TX_" + System.currentTimeMillis()
                        val autoBotMsg = WhatsAppMockPrompt(
                            id = successRef,
                            rawText = rawText,
                            source = source,
                            predictedCategory = newTx.category,
                            predictedNominal = newTx.nominal,
                            systemMessage = "[SIDOBE BOT WhatsApp] 🤖 *Transaksi Tercatat Otomatis!*\n━━━━━━━━\n• Nominal: Rp ${newTx.nominal}\n• Kategori: ${newTx.category}\n• Detail: ${newTx.description}\n• Sumber: ${source.uppercase()}",
                            isAnswered = true,
                            userReply = "(Tercatat Otomatis oleh Agent AI)"
                        )
                        _whatsappBotPrompts.value = _whatsappBotPrompts.value + autoBotMsg
                    }
                } else {
                    addTerminalLog("❌ Gagal parsing atau parsing mengembalikan objek kosong.")
                }
            }
        }
    }

    /**
     * Memproses simulasi GPS Geofencing tunai
     */
    fun processLocationEvent(context: Context, lat: Double, lon: Double, address: String, durationMin: Int) {
        addTerminalLog("Sensor [GPS] User terdeteksi menetap di \"$address\" selama $durationMin menit.")

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val currentServerUrl = serverBaseUrl.value
            if (currentServerUrl.isNotEmpty() && currentServerUrl.startsWith("http")) {
                try {
                    addTerminalLog("Mengirim Webhook GPS ke $currentServerUrl/webhook/android-agent")
                    val dynamicService = NetworkClient.getDynamicService(currentServerUrl)
                    val payload = WebhookPayload(
                        type = "location",
                        data = WebhookData(lat = lat, lon = lon, address = address, durationMinutes = durationMin)
                    )
                    dynamicService.sendWebhook("$currentServerUrl/webhook/android-agent", payload)
                } catch (e: Exception) {
                    addTerminalLog("Kejutan Webhook error: ${e.message}")
                }
            }

            if (isSandboxMode.value) {
                // Tanyakan pengeluaran tunai proaktif di WA Console
                val refId = "LOC_" + System.currentTimeMillis()
                val botPrompt = WhatsAppMockPrompt(
                    id = refId,
                    rawText = "Lokasi: $address",
                    source = "TUNAI",
                    predictedCategory = "Tunai",
                    predictedNominal = 0.0,
                    systemMessage = "Halo! Saya mendeteksi Anda baru di sekitar *$address* selama > 15 menit. 💸 Apakah ada pengeluaran TUNAI yang mau dicatatkan di sana di akhir kunjungan?\n\nBalas detail pengeluaran Anda (contoh: \"jajan bakso 25000\"), atau ketik \"Tidak\" jika tidak ada pengeluaran.",
                    isAnswered = false
                )
                _whatsappBotPrompts.value = _whatsappBotPrompts.value + botPrompt
                addTerminalLog("💼 Simulasi WhatsApp Proactive GPS dikirim ke WA Mock console.")
            }
        }
    }

    /**
     * Memproses balasan user di WhatsApp mock console (untuk merespons prompt ambigu atau lokasi)
     */
    fun processWhatsAppReply(context: Context, promptId: String, userReplyText: String) {
        val prompts = _whatsappBotPrompts.value.toMutableList()
        val index = prompts.indexOfFirst { it.id == promptId }
        if (index == -1) return

        val promptObj = prompts[index]
        prompts[index] = promptObj.copy(isAnswered = true, userReply = userReplyText)
        _whatsappBotPrompts.value = prompts

        addTerminalLog("WhatsApp Console menerima balasan: \"$userReplyText\"")

        // Proses balasan dengan AI
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            if (userReplyText.equals("tidak", ignoreCase = true) || userReplyText.equals("t", ignoreCase = true)) {
                addTerminalLog("Konfirmasi dibatalkan: Transaksi tidak dicatat.")
                return@launch
            }

            if (userReplyText.equals("ya", ignoreCase = true) || userReplyText.equals("y", ignoreCase = true)) {
                // Konfirmasi ya: Catat data estimasi awal
                val newTx = TransactionEntity(
                    nominal = promptObj.predictedNominal,
                    category = promptObj.predictedCategory,
                    type = "EXPENSE",
                    description = "Konfirmasi WhatsApp: " + promptObj.rawText,
                    source = promptObj.source
                )
                AppDatabase.getDatabase(context).transactionDao().insertTransaction(newTx)
                addTerminalLog("✅ Selesai mencatat transaksi (Konfirmasi: Ya) sebesar Rp ${newTx.nominal}")
            } else {
                // Koreksi verbal bebas: Tanya Gemini untuk mem-parsing koreksi teks dicampur dengan konteks awal!
                addTerminalLog("Menerjemahkan koreksi verbal user dengan AI...")
                val correctionPrompt = """
                    User mengoreksi data transaksi sebelumnya.
                    Data Mentah Awal: "${promptObj.rawText}" (Sumber: ${promptObj.source})
                    Estimasi Awal: Kategori=${promptObj.predictedCategory}, Nominal=${promptObj.predictedNominal}
                    
                    Koreksi/Balasan dari User: "$userReplyText"
                    
                    Harap analisis pesan di atas dan kembalikan data transaksi yang paling tepat dalam format JSON bersih:
                    {
                      "nominal": (nominal angka murni sesuai koreksi),
                      "category": (kategori logis),
                      "type": ("INCOME" atau "EXPENSE"),
                      "description": "Keterangan transaksi final yang informatif",
                      "isAmbiguous": false
                    }
                """.trimIndent()

                val parsed = parseLocalGeminiJson(correctionPrompt)
                if (parsed != null) {
                    val newTx = TransactionEntity(
                        nominal = parsed.nominal ?: 0.0,
                        category = parsed.category ?: "Lainnya",
                        type = parsed.type?.uppercase() ?: "EXPENSE",
                        description = parsed.description ?: userReplyText,
                        source = promptObj.source
                    )
                    AppDatabase.getDatabase(context).transactionDao().insertTransaction(newTx)
                    addTerminalLog("✅ Transaksi Hasil Koreksi Dicatat: Rp ${newTx.nominal} (${newTx.description})")
                } else {
                    addTerminalLog("❌ Gagal menerjemahkan koreksi verbal. Data tidak dicatat.")
                }
            }
        }
    }

    /**
     * Memanggil REST API Gemini secara langsung menggunakan apiKey di BuildConfig
     */
    private suspend fun parseWithLocalGemini(rawText: String, source: String): GeminiParsedTransaction? {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            addTerminalLog("⚠ API Key Gemini belum terkonfigurasi di Secrets AI Studio. Mencoba parsing lokal berbasis aturan fallback...")
            return parseFallbackRegexRules(rawText, source)
        }

        val prompt = """
            Ekstrak data transaksi nominal, kategori pengeluaran/pemasukan, tipe (INCOME/EXPENSE), dan deskripsi singkat dari string berikut:
            
            Text: "$rawText" (didapat dari sumber: $source)
            
            Kembalikan JSON murni dengan format tepat seperti ini:
            {
              "nominal": (nilai uang berupa angka Double, misal 15000.0. Berikan nilai 0.0 jika nominal tidak terdeteksi sama sekali),
              "category": (nama kategori logis seperti: Makanan, Transportasi, Belanja, Hiburan, Utilitas, Gaji),
              "type": ("INCOME" atau "EXPENSE"),
              "description": "Keterangan ringkas transaksi yang rapi dan bersih",
              "isAmbiguous": (true jika isi pesan tidak lengkap, tidak ada nominal uang, atau nilainya meragukan sehingga butuh konfirmasi. sebaliknya set false jika data ini sudah jelas)
            }
        """.trimIndent()

        return parseLocalGeminiJson(prompt)
    }

    private suspend fun parseLocalGeminiJson(prompt: String): GeminiParsedTransaction? {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        try {
            val request = GeminiGenerateContentRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(temperature = 0.1f, responseMimeType = "application/json")
            )
            val response = NetworkClient.geminiService.callGemini(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (responseText != null) {
                Log.d(TAG, "Gemini Response: $responseText")
                val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                
                // Parse dengan database/moshi
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(GeminiParsedTransaction::class.java)
                return adapter.fromJson(cleanJson)
            }
        } catch (e: Exception) {
            addTerminalLog("Kekurangan Koneksi Gemini: ${e.message}")
        }
        return null
    }

    /**
     * Fallback Regex parser murni untuk demo cepat jika user sama sekali tidak memasukkan Gemini Key
     */
    private fun parseFallbackRegexRules(rawText: String, source: String): GeminiParsedTransaction {
        var nominal = 0.0
        var type = "EXPENSE"
        var category = "Belanja"
        var desc = rawText
        var isAmbiguous = false

        try {
            // Cari angka nominal uang (Contoh: Rp. 50.000 atau Rp 20000 atau 75000)
            val regex = Regex("(?:Rp\\.?\\s*)?(\\d{1,3}(?:\\.\\d{3})+|\\d{4,})")
            val match = regex.find(rawText)
            if (match != null) {
                val numStr = match.groupValues[1].replace(".", "")
                nominal = numStr.toDoubleOrNull() ?: 0.0
            } else {
                isAmbiguous = true
            }

            val textLower = rawText.lowercase()
            if (textLower.contains("gaji") || textLower.contains("transfer masuk") || textLower.contains("diterima")) {
                type = "INCOME"
                category = "Gaji"
                desc = "Penerimaan Pemasukan Dana"
            } else if (textLower.contains("makan") || textLower.contains("kopi") || textLower.contains("resto") || textLower.contains("boba")) {
                category = "Makanan"
                desc = "Makan / Jajan Kuliner"
            } else if (textLower.contains("go-car") || textLower.contains("gocar") || textLower.contains("go-ride") || textLower.contains("goride") || textLower.contains("ojek") || textLower.contains("grab") || textLower.contains("bensin")) {
                category = "Transportasi"
                desc = "Perjalanan / Transportasi"
            } else if (textLower.contains("shopee") || textLower.contains("tokopedia") || textLower.contains("belanja") || textLower.contains("beli")) {
                category = "Belanja"
                desc = "Belanja Online / Retail"
            } else {
                category = "Lainnya"
                desc = "Pengeluaran terarsip otomatis"
                isAmbiguous = true // Ambigu jika tidak dikenali tipenya
            }
        } catch (e: Exception) {
            isAmbiguous = true
        }

        return GeminiParsedTransaction(
            nominal = nominal,
            category = category,
            type = type,
            description = desc,
            isAmbiguous = isAmbiguous
        )
    }
}

/**
 * Representasi visual pesan WhatsApp tertunda di dashboard mock screen.
 */
data class WhatsAppMockPrompt(
    val id: String,
    val rawText: String,
    val source: String,
    val predictedCategory: String,
    val predictedNominal: Double,
    val systemMessage: String,
    val isAnswered: Boolean,
    val userReply: String? = null
)

/**
 * Schema parsing JSON terstandarisasi untuk Gemini
 */
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class GeminiParsedTransaction(
    val nominal: Double? = 0.0,
    val category: String? = "Lainnya",
    val type: String? = "EXPENSE",
    val description: String? = "",
    val isAmbiguous: Boolean? = false
)
