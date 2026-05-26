package com.example.data.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// --- WEBHOOK SCHEMAS ---

@JsonClass(generateAdapter = true)
data class WebhookPayload(
    val type: String, // "notification", "screen", "location"
    val data: WebhookData,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class WebhookData(
    val text: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val durationMinutes: Int? = null
)

// --- GEMINI REST API SCHEMAS ---

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponseFormat(
    val responseMimeType: String? = "application/json"
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = "application/json"
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

// --- RETROFIT INTERFACES ---

interface ApiService {
    // 1. Direct Webhook Sender (supports dynamic URLs)
    @POST
    suspend fun sendWebhook(
        @Url url: String,
        @Body payload: WebhookPayload
    ): Response<ResponseBody>

    // 2. Fetch Transactions from Node.js backend
    @POST
    suspend fun getTransactions(
        @Url url: String
    ): Response<ResponseBody> // We will parse manually to be flexible or use simple object

    // 3. Direct Gemini Call (Option B: Default for Prototypes)
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun callGemini(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

// --- CLIENT CREATORS ---

object NetworkClient {
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val geminiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // Dynamic creator for custom Backend URLs
    fun getDynamicService(customBaseUrl: String): ApiService {
        // Enforce safe slash
        val safeBaseUrl = if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        return Retrofit.Builder()
            .baseUrl(safeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
