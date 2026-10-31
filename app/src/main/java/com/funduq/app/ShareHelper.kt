package com.funduq.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

object ShareHelper {

    private fun getLocalBitmapUri(context: Context, bmp: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "turatsgo_screenshot.png")
            val stream = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    // 1. FITUR LAPOR SOAL KE WHATSAPP ADMIN
    fun reportQuestionToAdmin(context: Context, screenshot: Bitmap, questionText: String) {
        val uri = getLocalBitmapUri(context, screenshot) ?: return
        val message = """
            Assalamu'alaikum Admin TuratsGo, ijin melaporkan kendala/masalah pada soal berikut:
            
            " $questionText "
            
            (Tangkapan layar kuis terlampir di pesan ini)
        """.trimIndent()

        val url = "https://api.whatsapp.com/send?phone=628567688848&text=${URLEncoder.encode(message, "UTF-8")}"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra("jid", "628567688848@s.whatsapp.net")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Laporkan via WhatsApp"))
        } catch (e: Exception) {
            Toast.makeText(context, "Aplikasi WhatsApp tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. FITUR SHARE SOAL VIRAL
    fun shareQuestionToFriends(context: Context, screenshot: Bitmap) {
        val uri = getLocalBitmapUri(context, screenshot) ?: return
        val message = """
            Yuk bantu saya untuk menjawab soal kitab kuning ini! 📚🤔
            
            Biar belajar kitab klasik jadi lebih mudah dan menyenangkan, yuk download aplikasi TuratsGo sekarang di Play Store! 🏆✨
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan ke Teman"))
    }
}
