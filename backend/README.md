# Autonomous Personal Finance AI Agent Backend (Node.js & Express)

Bagian ini berisi sistem backend server untuk memproses data dari Mobile App (Android), melakukan parsing otomatis berbasis Artificial Intelligence menggunakan **Gemini 1.5/3.5 Flash**, serta mengirimkan bot interaktif WhatsApp menggunakan **SIDOBE API Gateway**.

## 🚀 Persiapan Menjalankan Backend

1. **Instalasi Dependensi**
   Pastikan Anda berada di direktori ini atau memindahkan file ini ke folder proyek node tersendiri, lalu jalankan:
   ```bash
   npm init -y
   npm install express dotenv axios @google/generative-ai
   ```

2. **Konfigurasi Lingkungan (`.env`)**
   Buat file `.env` di sebelah `server.js` Anda:
   ```properties
   PORT=3000
   GEMINI_API_KEY=AIzaSy... # Cari di Google AI Studio
   SIDOBE_API_KEY=your_sidobe_token # Token API SIDOBE WhatsApp Gateway
   SIDOBE_SENDER=62822...  # Nomor Anda terdaftar di SIDOBE
   SIDOBE_TARGET=62812...  # Nomor WhatsApp tujuan (User)
   ```

3. **Jalankan Aplikasi**
   ```bash
   node server.js
   ```

## 🔌 Detail Endpoint API Webhook & Dashboard

* **`POST /webhook/android-agent`**  
  Menjadi jembatan bagi aplikasi Android Anda untuk mengirimkan peristiwa scraping layar, deteksi push notifikasi perbankan offline, atau pemicu aktivitas tunai lewat GPS.
  
* **`POST /webhook/whatsapp`**  
  Hubungkan webhook ini di Dashboard SIDOBE Anda dengan mengarahkan URL Callback ke `https://domain-anda.com/webhook/whatsapp`. Setiap balasan "Ya" atau koreksi verbal dari pengguna akan langsung diproses oleh AI Agen secara reaktif.

* **`GET /api/transactions`**  
  Menyuplai daftar transaksi, rekap bulanan, dan log sensor AI ke antarmuka Dashboard utama di aplikasi Android secara real-time.
