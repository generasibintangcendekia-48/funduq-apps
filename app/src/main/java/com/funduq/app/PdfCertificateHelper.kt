package com.funduq.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfCertificateHelper {

    fun generateAndDownloadPdf(
        context: Context,
        studentName: String,
        bookTitle: String
    ) {
        val pdfDocument = PdfDocument()

        // Ukuran kertas standar A4 Landscape: 842 x 595 poin
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Latar Belakang Piagam Klasik (Ivory / Krem Mewah)
        val bgPaint = Paint().apply { color = Color.parseColor("#FFFDF7") }
        canvas.drawRect(0f, 0f, 842f, 595f, bgPaint)

        // 2. Bingkai Ganda Emas Klasik (Double Gold Border)
        val outerBorder = Paint().apply {
            color = Color.parseColor("#B45309") // Emas tembaga
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(24f, 24f, 818f, 571f, outerBorder)

        val innerBorder = Paint().apply {
            color = Color.parseColor("#D97706") // Emas terang
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(32f, 32f, 810f, 563f, innerBorder)

        // 3. LOGO TURATSGO DI ATAS SERTIFIKAT
        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_turatsgo_logo)
            if (logoBitmap != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 64, 64, true)
                // Gambar tepat di tengah atas (Center: 421 - 32 = 389f)
                canvas.drawBitmap(scaledLogo, 389f, 42f, null)
            }
        } catch (_: Exception) { }

        // 4. PEMBUKAAN: BISMILLAHIRROHMANIRROHIM (Arab & Latin)
        val bismillahArabPaint = Paint().apply {
            color = Color.parseColor("#15803D") // Hijau Syar'i
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", 421f, 130f, bismillahArabPaint)

        val bismillahLatinPaint = Paint().apply {
            color = Color.parseColor("#78350F")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Bismillahirrahmanirrahim", 421f, 145f, bismillahLatinPaint)

        // 5. JUDUL BESAR SERTIFIKAT
        val titlePaint = Paint().apply {
            color = Color.parseColor("#78350F")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SERTIFIKAT KHATAM BELAJAR", 421f, 180f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#B45309")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TURATSGO — GAMIFIED LEARNING PLATFORM  KITAB KLASIK ", 421f, 200f, subTitlePaint)

        // Garis Pemisah Emas di Bawah Judul
        canvas.drawLine(220f, 212f, 622f, 212f, innerBorder)

        // 6. PERNYATAAN KELULUSAN
        val labelPaint = Paint().apply {
            color = Color.parseColor("#4B5563")
            textSize = 13f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dengan penuh rasa syukur dan bangga dianugerahkan kepada:", 421f, 240f, labelPaint)

        // 7. NAMA SANTRI
        val namePaint = Paint().apply {
            color = Color.parseColor("#15803D")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(studentName.uppercase(Locale.getDefault()), 421f, 280f, namePaint)

        // Garis Penanda di Bawah Nama
        val nameUnderline = Paint().apply {
            color = Color.parseColor("#86EFAC")
            strokeWidth = 2f
        }
        canvas.drawLine(240f, 292f, 602f, 292f, nameUnderline)

        // 8. KETERANGAN KITAB YANG DIKHATAMKAN
        canvas.drawText("Telah menyelesaikan seluruh ujian pada kitab:", 421f, 325f, labelPaint)

        val bookPaint = Paint().apply {
            color = Color.parseColor("#92400E")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("« $bookTitle »", 421f, 360f, bookPaint)

        // Predikat Kelulusan (Mumtaz)
        val gradeBadgePaint = Paint().apply {
            color = Color.parseColor("#16A34A")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("★ PREDIKAT: MUMTAZ (100% SELESAI) ★", 421f, 395f, gradeBadgePaint)

        // 9. KOLOM KIRI: TANGGAL & ID VERIFIKASI DIGITAL
        val todayStr = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
        val datePaint = Paint().apply {
            color = Color.parseColor("#374151")
            textSize = 11f
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("Diterbitkan di: Indonesia", 70f, 470f, datePaint)
        canvas.drawText("Pada Tanggal: $todayStr", 70f, 490f, datePaint)
        canvas.drawText("ID Verifikasi: TRG-${System.currentTimeMillis().toString().takeLast(8)}", 70f, 510f, datePaint)

        // 10. KOLOM KANAN: TANDA TANGAN RESMI UST. MATLAUL ANWAR, SE. MM
        val ttdLabelPaint = Paint().apply {
            color = Color.parseColor("#374151")
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Mudir & Pengasuh Program,", 670f, 440f, ttdLabelPaint)

        // Render Gambar Tanda Tangan
        try {
            val signBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_signature_ma)
            if (signBitmap != null) {
                // Tanda tangan di-scale proporsional dan ditaruh tepat di atas nama pengasuh
                val scaledSign = Bitmap.createScaledBitmap(signBitmap, 140, 75, true)
                canvas.drawBitmap(scaledSign, 600f, 445f, null)
            }
        } catch (_: Exception) { }

        // Nama & Gelar Ust. Matlaul Anwar, SE. MM
        val signNamePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Ust. Matlaul Anwar, SE. MM", 670f, 530f, signNamePaint)

        val signRolePaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dewan Pembina TuratsGo", 670f, 546f, signRolePaint)

        pdfDocument.finishPage(page)

        // --- SIMPAN OTOMATIS KE STORAGE MENGGUNAKAN MEDIASTORE ---
        val cleanBookName = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "Sertifikat_TuratsGo_${cleanBookName}.pdf"

        try {
            var outputStream: OutputStream? = null
            var fileUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TuratsGo")
                }
                val resolver = context.contentResolver
                fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (fileUri != null) {
                    outputStream = resolver.openOutputStream(fileUri)
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TuratsGo")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                outputStream = FileOutputStream(file)
            }

            if (outputStream != null) {
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
                outputStream.close()

                Toast.makeText(context, "Alhamdulillah! Sertifikat resmi tersimpan di folder Download/TuratsGo 📄✅", Toast.LENGTH_LONG).show()

                fileUri?.let { uri ->
                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (openIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(openIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mengunduh PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }
}
