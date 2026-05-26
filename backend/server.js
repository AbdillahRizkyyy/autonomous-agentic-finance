/**
 * Autonomous Personal Finance AI Agent - Backend Node.js Server
 * Tech Stack: Express.js, Firebase-Admin (atau PostgreSQL), @google/generative-ai, Axios.
 * Bot WhatsApp Gateway via SIDOBE API.
 */

require('dotenv').config();
const express = require('express');
const axios = require('axios');
const { GoogleGenerativeAI } = require('@google/generative-ai');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const SIDOBE_API_KEY = process.env.SIDOBE_API_KEY;
const SIDOBE_SENDER_NUMBER = process.env.SIDOBE_SENDER; // No WhatsApp pengirim
const SIDOBE_TARGET_NUMBER = process.env.SIDOBE_TARGET; // No WhatsApp user penerima

// In-Memory Database sederhana untuk Demo/Prototype (bisa diconfigure ke PostgreSQL/Firebase)
let transactions = [
  {
    id: 1,
    nominal: 50000,
    category: "Makanan",
    type: "EXPENSE",
    description: "Beli Makan Siang Nasi Padang (DANA)",
    source: "NOTIFIKASI",
    timestamp: Date.now() - 3600000 * 2
  },
  {
    id: 2,
    nominal: 150000,
    category: "Transportasi",
    type: "EXPENSE",
    description: "Pengisian Saldo GoPay untuk Gocar",
    source: "LAYAR",
    timestamp: Date.now() - 3600000 * 5
  },
  {
    id: 3,
    nominal: 1000000,
    category: "Gaji",
    type: "INCOME",
    description: "Transfer Masuk dari Kantor",
    source: "NOTIFIKASI",
    timestamp: Date.now() - 3600000 * 24
  }
];

// Menampung log aktivitas agent untuk ditampilkan di layar debug
let logs = [];

// Menampung antrean konfirmasi menggantung (ambigu / tunai)
let pendingConfirmations = {};

// Inisialisasi Gemini AI
const ai = new GoogleGenerativeAI(GEMINI_API_KEY);

// Logger Helper
function logAgent(message) {
  const time = new Date().toISOString();
  const entry = `[${time}] ${message}`;
  console.log(entry);
  logs.push(entry);
  if (logs.length > 100) logs.shift();
}

/**
 * 1. AI Parser (Gemini API) untuk mengekstrak informasi detail dari raw text
 */
async function parseTransactionWithGemini(rawText, source) {
  try {
    const model = ai.getGenerativeModel({ model: "gemini-1.5-flash" }); // atau gemini-3.5-flash
    
    const prompt = `
    Anda adalah asisten keuangan pribadi cerdas bernama "Finance AI Agent".
    Tugas Anda adalah mengekstrak nominal, kategori, tipe (INCOME/EXPENSE), dan deskripsi singkat dari data mentah berikut yang didapat melalui sumber: ${source}.
    
    Data mentah: "${rawText}"
    
    Ekstrak data ini dan kembalikan HANYA dalam format JSON dengan key berikut:
    {
      "nominal": (angka murni tanpa currency, contoh: 50000),
      "category": (kategori logis seperti Makanan, Transportasi, Hiburan, Belanja, Utilitas, Gaji, dll),
      "type": ("INCOME" atau "EXPENSE"),
      "description": "Deskripsi singkat yang bersih dan informatif",
      "isAmbiguous": (true jika teks ambigu atau nominal tidak jelas, false jika data sudah pasti)
    }
    
    Kembalikan JSON bersih tanpa markdown formatting, tanpa block \`\`\`json. Jika nominal tidak dapat diidentifikasi secara pasti, set "isAmbiguous" ke true.
    `;

    const result = await model.generateContent(prompt);
    const responseText = result.response.text().trim();
    logAgent(`Gemini raw response: ${responseText}`);
    
    // Parse hasil JSON
    const parsedData = JSON.parse(responseText.replace(/```json/g, '').replace(/```/g, ''));
    return parsedData;
  } catch (error) {
    console.error("Error Gemini Parsing:", error);
    logAgent(`Gagal mem-parsing teks dengan Gemini: ${error.message}`);
    return null;
  }
}

/**
 * Send WhatsApp Notification / Prompt via SIDOBE API
 */
async function sendWhatsAppMessage(to, message) {
  if (!SIDOBE_API_KEY) {
    logAgent("WhatsApp SIDOBE_API_KEY belum terkonfigurasi. Pengiriman pesan dilewati.");
    return false;
  }
  try {
    const response = await axios.post("https://api.sidobe.com/v1/messages", {
      sender: SIDOBE_SENDER_NUMBER,
      to: to,
      message: message
    }, {
      headers: { 'Authorization': `Bearer ${SIDOBE_API_KEY}` }
    });
    logAgent(`Pesan WA terkirim ke ${to}: "${message}"`);
    return response.data;
  } catch (error) {
    console.error("Error SIDOBE WhatsApp API:", error);
    logAgent(`Gagal mengirim pesan WA ke ${to}: ${error.message}`);
    return false;
  }
}

/**
 * 2. HOME / API STATUS: Friendly entry point so Vercel doesn't show Cannot GET /
 */
app.get('/', (req, res) => {
  res.json({
    status: "ONLINE",
    agent_name: "Personal Finance AI Agent Backend",
    version: "1.5.0",
    message: "Welcome! The autonomous finance agent backend is running successfully.",
    endpoints: {
      get_transactions: "/api/transactions",
      clear_transactions: "/api/transactions/clear [POST]",
      webhook_android_agent: "/webhook/android-agent [POST]",
      webhook_whatsapp_gateway: "/webhook/whatsapp [POST]"
    },
    system_time: new Date()
  });
});

/**
 * 3. WEBHOOK: Menerima Aliran Data Sensor dari Android Agent (Notification, Screen, GPS)
 */
app.post('/webhook/android-agent', async (req, res) => {
  const { type, data, timestamp } = req.body;
  logAgent(`Webhook Android Diterima: Tipe [${type}]`);

  if (!type || !data) {
    return res.status(400).json({ error: "Invalid payload format" });
  }

  // Normalisasi bahasa: Dukung tipe bahasa Indonesia vs Inggris
  let normalizedType = type.toLowerCase();
  if (normalizedType === 'notifikasi') normalizedType = 'notification';
  if (normalizedType === 'layar') normalizedType = 'screen';

  // Skenario A: Notifikasi atau Screen-scraping
  if (normalizedType === 'notification' || normalizedType === 'screen') {
    const rawText = data.text;
    if (!rawText) return res.status(400).json({ error: "Missing text data" });

    logAgent(`Memproses teks mentah [${normalizedType}]: "${rawText.substring(0, 50)}..."`);
    const parsed = await parseTransactionWithGemini(rawText, normalizedType.toUpperCase());

    if (!parsed) {
      return res.status(500).json({ status: "error", message: "AI parsing failed" });
    }

    if (parsed.isAmbiguous) {
      // Skenario Ambigu: Tanyakan ke user via WhatsApp proaktif
      logAgent(`Transaksi dari ${normalizedType} ambigu. Menanyakan klarifikasi ke user via WhatsApp.`);
      
      const referenceId = "TX_" + Date.now();
      pendingConfirmations[referenceId] = {
        rawText,
        source: normalizedType.toUpperCase(),
        parsedPreview: parsed,
        timestamp: Date.now()
      };

      const questionPrompt = `Halo! Saya mendeteksi aktivitas keuangan dari ${normalizedType === 'notification' ? 'notifikasi' : 'layar'}:
"${rawText}"

Sepertinya data ini kurang lengkap atau ambigu.
Apakah ini pengeluaran untuk *${parsed.category || 'Belanja'}* sebesar *Rp ${parsed.nominal || 0}*? 
Balas *"Ya"* atau sebutkan detailnya (contoh: "Bukan, itu pengeluaran makan siang sebesar 45000").
[Ref: ${referenceId}]`;

      await sendWhatsAppMessage(SIDOBE_TARGET_NUMBER || "USER_WA_NUMBER", questionPrompt);
      return res.json({ status: "pending_confirmation", referenceId, preview: parsed });
    } else {
      // Skenario Jelas: Otomatis catat & kirim notifikasi rekapan
      const newTx = {
        id: transactions.length + 1,
        nominal: parsed.nominal,
        category: parsed.category,
        type: parsed.type,
        description: parsed.description,
        source: normalizedType.toUpperCase(),
        timestamp: timestamp || Date.now()
      };
      transactions.unshift(newTx);
      logAgent(`Transaksi Auto-Record: +Rp ${parsed.nominal} (${parsed.description})`);

      const notificationMsg = `[Finance AI Agent] 🤖 *Transaksi Tercatat Otomatis!*\n` +
        `━━━━━━━━━━━━━━━━━━━\n` +
        `• Tipe: ${parsed.type === 'INCOME' ? '🟢 Pemasukan' : '🔴 Pengeluaran'}\n` +
        `• Nominal: Rp ${parsed.nominal.toLocaleString('id-ID')}\n` +
        `• Kategori: ${parsed.category}\n` +
        `• Keterangan: ${parsed.description}\n` +
        `• Sumber: ${normalizedType.toUpperCase()}`;

      await sendWhatsAppMessage(SIDOBE_TARGET_NUMBER || "USER_WA_NUMBER", notificationMsg);
      return res.json({ status: "recorded", transaction: newTx });
    }
  }

  // Skenario B: Geofencing & GPS Background (Mendeteksi Transaksi Tunai)
  if (normalizedType === 'location') {
    const { lat, lon, address, durationMinutes } = data;
    logAgent(`User terdeteksi menetap di koordinat (${lat}, ${lon}) - ${address || "Lokasi Toko"} - selama ${durationMinutes} menit.`);
    
    // Skenario proaktif bertanya aktivitas tunai
    const referenceId = "LOC_" + Date.now();
    pendingConfirmations[referenceId] = {
      lat, lon, address,
      source: "TUNAI",
      timestamp: Date.now()
    };

    const questionPrompt = `Halo! Saya mendeteksi Anda baru saja berada di sekitar *${address || 'koordinat toko'}* selama lebih dari ${durationMinutes} menit.
Apakah ada pengeluaran TUNAI yang perlu dicatat di sana? 💸
Balas dengan detail pengeluaran, contoh: "Ya, beli kopi tunai 25000" atau balas "Tidak" jika tidak ada.
[Ref: ${referenceId}]`;

    await sendWhatsAppMessage(SIDOBE_TARGET_NUMBER || "USER_WA_NUMBER", questionPrompt);
    return res.json({ status: "location_triggered", referenceId });
  }

  res.status(400).json({ error: "Unknown webhook event type" });
});

/**
 * 3. WEBHOOK: Menerima Pesan Balasan User dari WhatsApp Gateway (SIDOBE API)
 */
app.post('/webhook/whatsapp', async (req, res) => {
  logAgent(`Menerima pesan WA Body: ${JSON.stringify(req.body)}`);

  const from = req.body.from || req.body.sender || req.body.phone || (req.body.data && req.body.data.from) || (req.body.data && req.body.data.sender);
  let message = req.body.message || req.body.msg || req.body.text || req.body.body || (req.body.data && req.body.data.message);

  if (message && typeof message === 'object') {
    message = message.text || message.body || JSON.stringify(message);
  }

  logAgent(`Menerima pesan WA ter-ekstrak: dari=[${from}], pesan=[${message}]`);

  if (!message) {
    return res.sendStatus(200);
  }

  // Ekstrak referensi transaksi dari text jika ada [Ref: TX_XXXX]
  const refMatch = message.match(/\[Ref:\s*(TX_\d+|LOC_\d+)\]/i);
  let referenceId = refMatch ? refMatch[1] : null;

  // Jika user melampirkan reply dengan ID referensi yang disimpan
  if (referenceId && pendingConfirmations[referenceId]) {
    const pending = pendingConfirmations[referenceId];
    delete pendingConfirmations[referenceId]; // Hapus dari antrean setelah diproses

    if (message.toLowerCase().trim() === 'ya' || message.toLowerCase().trim() === 'y') {
      // User setuju dengan hasil estimasi parsing Gemini sebelumnya
      const preview = pending.parsedPreview;
      const newTx = {
        id: transactions.length + 1,
        nominal: preview.nominal,
        category: preview.category,
        type: preview.type,
        description: preview.description,
        source: pending.source,
        timestamp: Date.now()
      };
      transactions.unshift(newTx);
      logAgent(`Konfirmasi WA Diterima: Mengonfirmasi transaksi otomatis Rp ${preview.nominal}`);
      await sendWhatsAppMessage(from, `Mantap! Transaksi belanja Rp ${preview.nominal.toLocaleString('id-ID')} telah sukses dicatat.`);
    } else if (message.toLowerCase().trim() === 'tidak' || message.toLowerCase().trim() === 't') {
      logAgent(`User membatalkan konfirmasi transaksi [Ref: ${referenceId}]`);
      await sendWhatsAppMessage(from, `Oke, transaksi dibatalkan dan tidak dicatat.`);
    } else {
      // User membalas dengan koreksi verbal manual (contoh: "Bukan, ganti jadi jajan boba 30000")
      logAgent(`Melakukan koreksi transaksi verbal menggunakan Gemini...`);
      const correctionPrompt = `
      User ingin mengoreksi data transaksi keuangan.
      Konteks awal transaksi:
      - Raw text asal: "${pending.rawText || "Lokasi: " + (pending.address || "")}"
      - Estimasi awal: ${JSON.stringify(pending.parsedPreview || {})}
      
      Koreksi/Balasan manual dari user: "${message}"
      
      Harap kombinasikan koreksi user ini dengan data awal dan kembalikan JSON bersih dengan format yang sama:
      {
        "nominal": (nominal angka murni sesuai koreksi),
        "category": (kategori logis),
        "type": ("INCOME" atau "EXPENSE"),
        "description": "Keterangan transaksi final yang lebih detail",
        "isAmbiguous": false
      }
      `;

      const model = ai.getGenerativeModel({ model: "gemini-1.5-flash" });
      const result = await model.generateContent(correctionPrompt);
      const outputText = result.response.text().trim();
      
      try {
        const corrected = JSON.parse(outputText.replace(/```json/g, '').replace(/```/g, ''));
        const newTx = {
          id: transactions.length + 1,
          nominal: corrected.nominal,
          category: corrected.category,
          type: corrected.type,
          description: corrected.description,
          source: pending.source,
          timestamp: Date.now()
        };
        transactions.unshift(newTx);
        logAgent(`Koreksi Berhasil: Transaksi dicatat sebagai Rp ${corrected.nominal} (${corrected.description})`);
        await sendWhatsAppMessage(from, `Selesai diperbarui! Transaksi *${corrected.description}* sebesar *Rp ${corrected.nominal.toLocaleString('id-ID')}* telah dicatat.`);
      } catch (e) {
        await sendWhatsAppMessage(from, `Maaf, saya gagal memproses koreksi tersebut. Silakan ketik transaksi secara manual.`);
      }
    }
    return res.sendStatus(200);
  }

  // Skenario C: Pesan WhatsApp bebas/tanpa referensi (catat manual verbal)
  logAgent(`User mengirim perintah transaksi bebas secara langsung via WA.`);
  const parsed = await parseTransactionWithGemini(message, "VERBAL_WA");

  if (parsed && !parsed.isAmbiguous) {
    const newTx = {
      id: transactions.length + 1,
      nominal: parsed.nominal,
      category: parsed.category,
      type: parsed.type,
      description: parsed.description,
      source: "TUNAI",
      timestamp: Date.now()
    };
    transactions.unshift(newTx);
    logAgent(`Transaksi Manual WA Berhasil: Rp ${parsed.nominal}`);
    await sendWhatsAppMessage(from, `✅ *Berhasil dicatat!*\n• *Rp ${parsed.nominal.toLocaleString('id-ID')}* untuk *${parsed.description}*.`);
  } else {
    // Kurang jelas
    await sendWhatsAppMessage(from, `Halo! Saya tidak dapat mengidentifikasi pengeluaran secara pasti dari pesan: "${message}". Bisa tolong sebutkan nominal dan keterangannya secara jelas? (Contoh: "Beli bensin motor 20000")`);
  }

  res.sendStatus(200);
});

/**
 * 4. DASHBOARD API: Mengambil riwayat transaksi terbaru untuk disuplai ke UI Android
 */
app.get('/api/transactions', (req, res) => {
  res.json({
    status: "success",
    summary: {
      totalIncome: transactions.filter(t => t.type === 'INCOME').reduce((acc, c) => acc + c.nominal, 0),
      totalExpense: transactions.filter(t => t.type === 'EXPENSE').reduce((acc, c) => acc + c.nominal, 0),
      currentMonth: new Date().toLocaleString('id-ID', { month: 'long', year: 'numeric' })
    },
    transactions,
    logs
  });
});

app.post('/api/transactions/clear', (req, res) => {
  transactions = [];
  logAgent("Clear transaksi dari dashboard dilakukan.");
  res.json({ status: "success", message: "Transaksi dibersihkan" });
});

// Menjalankan server Node.js
if (process.env.NODE_ENV !== 'production' || !process.env.VERCEL) {
  app.listen(PORT, () => {
    console.log(`Server Personal Finance AI Agent berjalan di port ${PORT}`);
  });
}

module.exports = app;
