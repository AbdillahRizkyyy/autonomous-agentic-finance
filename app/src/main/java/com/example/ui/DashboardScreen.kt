package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.TransactionEntity
import com.example.services.WhatsAppMockPrompt
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Professional Polish - Tech Cyber terminal neon green theme
val ThemeBlack = Color(0xFF050505)       // Background utama pekat
val ThemeDarkGray = Color(0xFF0D0D0D)    // Background kartu utama / header
val ThemeLightGray = Color(0xFF1E293B)   // Slate-800 border utama
val ThemeCardBg = Color(0xFF111111)      // Background kartu riwayat / item
val NeonGreen = Color(0xFF00FF41)        // Cyber terminal green accent
val NeonCyan = Color(0xFF00E5FF)         // Cyber terminal cyan accent
val NeonOrange = Color(0xFFFF9800)       // Cyber warning orange
val NeonPurple = Color(0xFFA855F7)       // SIDOBE AI Bot purple
val TechWhite = Color(0xFFECEFF4)        // Slate-200 text color
val TechGray = Color(0xFF94A3B8)         // Slate-400 subtitle text

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val logs by viewModel.terminalLogs.collectAsState()
    val waPrompts by viewModel.whatsappBotPrompts.collectAsState()

    val serverUrl by viewModel.serverUrl.collectAsState()
    val isSandboxMode by viewModel.isSandboxMode.collectAsState()

    val isNotificationActive by viewModel.isNotificationServiceRunning.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityServiceRunning.collectAsState()
    val isLocationActive by viewModel.isLocationServiceRunning.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var inputServerUrl by remember { mutableStateOf(serverUrl) }

    // Hitung total finansial
    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.nominal }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.nominal }
    val balance = totalIncome - totalExpense

    // Efek pulse untuk teks status 'Monitoring'
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Badge AI visual logo
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonGreen.copy(alpha = 0.1f))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AI",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column {
                                Text(
                                    text = "FINANCE_AGENT_v1.5",
                                    color = NeonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "System Status: ",
                                        color = TechGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Monitoring",
                                        color = NeonGreen.copy(alpha = pulseAlpha),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Buka Pengaturan",
                                tint = if (showSettings) NeonCyan else TechWhite
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeDarkGray)
                )
                // Bottom divider border-b matching the HTML template
                Divider(color = NeonGreen.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth())
            }
        },
        containerColor = ThemeBlack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Skenario A: Panel Pengaturan Server Webhook & Mode AI
            item {
                AnimatedVisibility(
                    visible = showSettings,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                        border = BorderStroke(1.dp, NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "// KONFIGURASI WEBHOOK & AI",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // URL Webhook Node.js
                            OutlinedTextField(
                                value = inputServerUrl,
                                onValueChange = { inputServerUrl = it },
                                label = { Text("URL Webhook Node.js", color = TechWhite.copy(0.6f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = TechWhite,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = ThemeLightGray,
                                    focusedLabelColor = NeonCyan
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sandbox AI Mode",
                                        color = TechWhite,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Memproses scraping & notifikasi offline via model Gemini di HP jika server belum diaktifkan.",
                                        color = TechWhite.copy(0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isSandboxMode,
                                    onCheckedChange = { viewModel.toggleSandboxMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonGreen,
                                        checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = ThemeLightGray,
                                        uncheckedTrackColor = ThemeDarkGray
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showSettings = false }) {
                                    Text("Batal", color = TechWhite.copy(0.6f))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.updateServerUrl(inputServerUrl)
                                        showSettings = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("Simpan", color = ThemeBlack, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Active Sensor Banner (Polished & themed based on the requested template)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonGreen.copy(alpha = 0.05f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Glowing green indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonGreen.copy(alpha = pulseAlpha))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Dynamically build text representing current active sensors
                            val activeIndicators = mutableListOf<String>()
                            if (isLocationActive) activeIndicators.add("GPS")
                            if (isAccessibilityActive) activeIndicators.add("SCR")
                            if (isNotificationActive) activeIndicators.add("NTF")
                            val indicatorsText = if (activeIndicators.isEmpty()) "NONE ACTIVE" else activeIndicators.joinToString(", ")

                            Text(
                                text = "Services Active: $indicatorsText",
                                color = TechWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "0.02ms lag",
                            color = TechGray.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Skenario B: Indikator Sensor Aktivitas Background murni
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                    border = BorderStroke(1.dp, ThemeLightGray),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "// STATUS BACKGROUND AGENT SENSORS",
                            color = TechGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusBadge(
                                name = "NOTIFIKASI",
                                isActive = isNotificationActive,
                                activeColor = NeonPurple
                            )
                            StatusBadge(
                                name = "ACCESSIBILITY",
                                isActive = isAccessibilityActive,
                                activeColor = NeonCyan
                            )
                            StatusBadge(
                                name = "LOCATION (GPS)",
                                isActive = isLocationActive,
                                activeColor = NeonOrange,
                                onClick = { viewModel.toggleLocationService(!isLocationActive) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tombol akses ke Settings Sistem Android
                        Button(
                            onClick = onOpenSystemSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeLightGray),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Buka Pengaturan Aksesibilitas",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AKTIFKAN LAYANAN SENSOR SISTEM",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Skenario C: Summary Keuangan Bulanan
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                    border = BorderStroke(1.dp, ThemeLightGray),
                    shape = RoundedCornerShape(24.dp), // 3xl round shape as per the HTML design
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Thin glowing top border line reflecting the gradient
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, NeonGreen.copy(alpha = 0.3f), Color.Transparent)
                                    )
                                )
                        )

                        Column(modifier = Modifier.padding(20.dp)) {
                            // Top Row: DASHBOARD_SUMMARY & REAL_TIME Tag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DASHBOARD_SUMMARY",
                                    color = TechGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, NeonCyan, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "REAL_TIME",
                                        color = NeonCyan,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Available Balance Title & Amount
                            Text(
                                text = "SALDO TERSEDIA",
                                color = TechGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatRupiah(balance),
                                color = if (balance >= 0) NeonGreen else Color.Red,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Grid columns: Pemasukan & Pengeluaran
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF10B981)) // emerald-500
                                        )
                                        Text(
                                            text = "Pemasukan",
                                            color = TechGray,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+ " + formatRupiah(totalIncome).replace("Rp ", ""),
                                        color = TechWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFFEF4444)) // red-500
                                        )
                                        Text(
                                            text = "Pengeluaran",
                                            color = TechGray,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "- " + formatRupiah(totalExpense).replace("Rp ", ""),
                                        color = TechWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skenario D: TERMINAL CONSOLE LOGS (Aktivitas Sensor AI Berjalan real-time)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⌨ REAL-TIME SENSOR TERMINAL LOGS",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            // Glowing terminal dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonGreen.copy(alpha = pulseAlpha))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF030303)) // Pitch black terminal screens
                                .border(1.dp, ThemeLightGray, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            val listState = rememberLazyListState()
                            
                            // Setiap ada log baru masuk, auto scroll ke paling bawah
                            LaunchedEffect(logs.size) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }

                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(logs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("✅") || log.contains("Sukses")) NeonGreen 
                                                else if (log.contains("⚠") || log.contains("ambigu")) NeonOrange 
                                                else if (log.contains("❌") || log.contains("Gagal")) Color(0xFFEF4444)
                                                else TechWhite.copy(0.85f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skenario E: WA CONSOLE / SIDOBE BOT INTERACT DECK
            // Memungkinkan user melakukan interaksi dua arah dengan AI Agent layaknya chatbot WA proaktif SIDOBE
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "WhatsApp Bot Console",
                                tint = NeonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🤖 INTERACTIVE WHATSAPP BOT CONSOLE",
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "Jika AI mendeteksi aktivitas ambigu (misalnya nominal tidak terbaca) atau pemicu GPS tunai, dia akan mengirim chat proaktif WhatsApp. Anda dapat berdialog interaktif dengannya disini!",
                            color = TechGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        val unansweredPrompts = waPrompts.filter { !it.isAnswered }

                        if (unansweredPrompts.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF070707))
                                    .border(BorderStroke(1.dp, ThemeLightGray), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "[ Tidak ada antrean konfirmasi WhatsApp ]\nTrigger di bawah untuk memunculkan chat proaktif.",
                                    color = TechGray.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                unansweredPrompts.forEach { prompt ->
                                    WhatsAppPromptChatItem(
                                        prompt = prompt,
                                        onSendReply = { reply -> viewModel.replyToWhatsAppBot(prompt.id, reply) }
                                    )
                                }
                            }
                        }

                        // Tampilkan riwayat chat yang telah direspon
                        val answeredPrompts = waPrompts.filter { it.isAnswered }.takeLast(2)
                        if (answeredPrompts.isNotEmpty()) {
                            Divider(color = ThemeLightGray, modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "Riwayat Percakapan Terakhir:",
                                color = TechGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            answeredPrompts.forEach { oldPrompt ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ThemeCardBg)
                                        .border(1.dp, ThemeLightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Bot: " + oldPrompt.systemMessage.substringBefore("\n\n"),
                                        color = NeonPurple.copy(0.9f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Anda: " + (oldPrompt.userReply ?: ""),
                                        color = NeonGreen.copy(0.9f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Skenario F: PANAL PEMICU SIMULASI (SANGAT KRUSIAL: Memungkinkan pengujian fungsionalitas murni asisten di emulator)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkGray),
                    border = BorderStroke(1.dp, ThemeLightGray),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚡ QUICK SIMULATION CONTROL DECK",
                            color = TechWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 1. Simulasi Notifikasi
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text("1. Simulasi Pembayaran Masuk (Notifikasi)", color = TechGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.simulateNotificationReceived("DANA", "DANA: Pembayaran Berhasil Rp 120.000 ke Kopi Kenangan.") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("DANA (Jelas)", color = TechWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { viewModel.simulateNotificationReceived("SHOPEEPAY", "ShopeePay: Transfer terkirim kepada pedagang tapi limit sisa nol.") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("Shopee (Ambigu)", color = NeonOrange, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // 2. Simulasi Screen Scraping
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text("2. Simulasi Membaca Layar (Scrape Aksesibilitas)", color = TechGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.simulateScrapeScreen("Gojek", "Konfirmasi Pengiriman | Tujuan: Gocar | Total Tagihan: Rp 45.000 | Metode: GoPay") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("Gojek Gocar (Jelas)", color = TechWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { viewModel.simulateScrapeScreen("Livin Mandiri", "Detail Terkirim | Rekening: 124-xx | Nominal: Rp 72.000 | Berhasil.") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("Livin Transfer (Jelas)", color = TechWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // 3. Simulasi Kunjungan GPS Toko (Aktivitas Tunai)
                        Column {
                            Text("3. Simulasi Transit & GPS (Mendeteksi Transaksi Tunai)", color = TechGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.simulateLocationVisit("Pasar Tradisional Kebayoran Lama", -6.2305, 106.7797, 20) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("Simulasi GPS Tunai", color = NeonGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                Button(
                                    onClick = { viewModel.insertManualDemoTransaction(50000.0, "Makanan", "EXPENSE", "Beli Nasi Goreng Tunai", "TUNAI") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                    border = BorderStroke(1.dp, ThemeLightGray),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text("+ Riwayat manual", color = TechWhite, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            // Skenario G: RIWAYAT TRANSAKSI (List Utama)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOG_TRANSAKSI_TERAKHIR (${transactions.size})",
                        color = TechWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    
                    if (transactions.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllTransactions() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Bersihkan Semua",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ThemeDarkGray)
                            .border(BorderStroke(1.dp, ThemeLightGray), RoundedCornerShape(16.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Tidak Ada Transaksi Tersimpan",
                                color = TechGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lakukan pemicuan simulasi di atas untuk merefresh daftar secara real-time.",
                                color = TechGray.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            } else {
                items(transactions, key = { it.id }) { item ->
                    TransactionItemRow(
                        transaction = item,
                        onDeleteClick = { viewModel.deleteTransaction(item.id) }
                    )
                }
            }
        }
    }
}

// Sub Composable: Baris Item Transaksi Tunggal
@Composable
fun TransactionItemRow(
    transaction: TransactionEntity,
    onDeleteClick: () -> Unit
) {
    val badgeColors = when (transaction.source.uppercase()) {
        "NOTIFIKASI" -> Triple(NeonPurple, "NTF", "NOTIFIKASI")
        "LAYAR" -> Triple(NeonCyan, "SCR", "LAYAR ACC")
        else -> Triple(NeonOrange, "GPS", "TUNAI (GPS)")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg), // Charcoal cards
        border = BorderStroke(1.dp, ThemeLightGray.copy(alpha = 0.5f)), // Subtle deep slate border
        shape = RoundedCornerShape(16.dp), // modern rounded borders as per the HTML guidelines
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left square thumbnail source code badge as per the HTML design
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThemeBlack)
                    .border(1.dp, ThemeLightGray, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeColors.second,
                    color = badgeColors.first,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = transaction.category,
                        color = TechWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    Text(
                        text = sdf.format(Date(transaction.timestamp)),
                        color = TechGray.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = transaction.description,
                    color = TechWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = if (transaction.type == "INCOME") "+ " + formatRupiah(transaction.nominal).replace("Rp ", "")
                           else "- " + formatRupiah(transaction.nominal).replace("Rp ", ""),
                    color = if (transaction.type == "INCOME") NeonGreen else TechWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Riwayat",
                        tint = TechGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// Sub Composable: Interactive WhatsApp prompt chat dialog
@Composable
fun WhatsAppPromptChatItem(
    prompt: WhatsAppMockPrompt,
    onSendReply: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ThemeCardBg)
            .border(BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📩 PESAN CHAT DARI CHATBOT:",
                color = NeonPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "REF: " + prompt.id.takeLast(7),
                color = TechGray.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = prompt.systemMessage,
            color = TechWhite,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ThemeBlack)
                .border(1.dp, ThemeLightGray, RoundedCornerShape(6.dp))
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = { Text("Ketik balasan Anda (contoh: 'Ya' atau koreksi)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(color = TechWhite, fontSize = 11.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (replyText.isNotBlank()) {
                            onSendReply(replyText)
                            replyText = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = ThemeLightGray,
                    focusedPlaceholderColor = TechGray.copy(0.4f),
                    unfocusedPlaceholderColor = TechGray.copy(0.4f),
                    focusedContainerColor = ThemeBlack
                )
            )

            Button(
                onClick = {
                    if (replyText.isNotBlank()) {
                        onSendReply(replyText)
                        replyText = ""
                        keyboardController?.hide()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Kirim Balasan WA",
                    tint = ThemeBlack,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Sub Composable: Badge status sensor dinamis
@Composable
fun StatusBadge(
    name: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val mod = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(if (isActive) activeColor.copy(alpha = 0.15f) else ThemeLightGray.copy(alpha = 0.2f))
        .border(
            1.dp,
            if (isActive) activeColor else ThemeLightGray,
            RoundedCornerShape(4.dp)
        )
        .clickable(enabled = onClick != null) { onClick?.invoke() }
        .padding(horizontal = 8.dp, vertical = 4.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = mod
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isActive) activeColor.copy(alpha = alphaAnim) else Color.Gray)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            color = if (isActive) activeColor else TechWhite.copy(0.5f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Helper formatting uang Rupiah sederhana
fun formatRupiah(number: Double): String {
    val localeId = Locale("in", "ID")
    val numberFormat = NumberFormat.getCurrencyInstance(localeId)
    numberFormat.maximumFractionDigits = 0
    return numberFormat.format(number).replace("Rp", "Rp ")
}
