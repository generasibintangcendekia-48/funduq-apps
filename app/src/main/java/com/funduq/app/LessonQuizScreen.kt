package com.funduq.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign


// --- CAPTURE SCREENSHOT CONTROLLER ---
class CaptureController(val view: View) {
    fun capture(): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}

@Composable
fun rememberCaptureController(): CaptureController {
    val view = LocalView.current
    return remember(view) { CaptureController(view) }
}

@Composable
fun LessonQuizScreen(
    lesson: Lesson,
    isExamMode: Boolean = false,
    initialHearts: Int = 3,
    onComplete: (isCorrect: Boolean, remainingHearts: Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val captureController = rememberCaptureController()

    var selectedOptionIndex by remember(lesson) { mutableStateOf<Int?>(null) }
    var isSubmitted by remember(lesson) { mutableStateOf(false) }
    var hearts by remember { mutableIntStateOf(initialHearts) }

    val targetCorrectIndex = lesson.correctIndex
    val isAnswerCorrect = selectedOptionIndex != null && selectedOptionIndex == targetCorrectIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131F24))
            .statusBarsPadding()
            .navigationBarsPadding() // 🛡️ BANTALAN PENGAMAN BAWAH AGAR TIDAK BENTROK DENGAN TOMBOL HOME HP
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Bar Atas: Tombol Keluar, Kontrol Suara, Lapor, & Bagikan
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("✕ Keluar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))

                // 🔊 CONTROL SUARA / MUTE
                var isMutedState by remember { mutableStateOf(SoundManager.isMuted) }
                IconButton(onClick = {
                    isMutedState = SoundManager.toggleMute(context)
                }) {
                    Text(if (isMutedState) "🔇" else "🔊", fontSize = 16.sp)
                }

                // ⚠️ TOMBOL LAPORKAN KENDALA SOAL (!)
                IconButton(onClick = {
                    try {
                        val screenshot: Bitmap = captureController.capture()
                        ShareHelper.reportQuestionToAdmin(context, screenshot, lesson.question)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal mengambil tangkapan layar", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("⚠️", fontSize = 16.sp)
                }

                // 📢 TOMBOL BAGIKAN SOAL (SHARE)
                IconButton(onClick = {
                    try {
                        val screenshot: Bitmap = captureController.capture()
                        ShareHelper.shareQuestionToFriends(context, screenshot)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membagikan gambar", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("📢", fontSize = 16.sp)
                }
            }

            if (isExamMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { index ->
                        Text(text = if (index < hearts) "❤️" else "🖤", fontSize = 18.sp)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF202F36)
                ) {
                    Text("⭐ +${lesson.rewardXp} XP", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Kartu Teks Arab Hadits
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2B32)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = lesson.arabicText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 36.sp,
                    color = Color(0xFF81C784)
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF2A3C44))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Artinya:\n\"${lesson.translation}\"",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Pertanyaan:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF9E9E9E))
        Text(text = lesson.question, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

        Spacer(modifier = Modifier.height(14.dp))

        // Pilihan Ganda
        lesson.options.forEachIndexed { index, option ->
            val isThisSelected = selectedOptionIndex == index
            val isThisTheCorrectAnswer = index == targetCorrectIndex

            val cardBg = when {
                !isSubmitted && isThisSelected -> Color(0xFF2A424F)
                isSubmitted && isThisTheCorrectAnswer -> Color(0xFF1B5E20)
                isSubmitted && isThisSelected && !isThisTheCorrectAnswer -> Color(0xFF7F1D1D)
                else -> Color(0xFF202F36)
            }

            val borderColor = when {
                !isSubmitted && isThisSelected -> Color(0xFF58CC02)
                isSubmitted && isThisTheCorrectAnswer -> Color(0xFF4CAF50)
                isSubmitted && isThisSelected && !isThisTheCorrectAnswer -> Color(0xFFEF5350)
                else -> Color(0xFF2C3E47)
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable(enabled = !isSubmitted) { selectedOptionIndex = index },
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = if (isThisSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White
                    )
                    if (isSubmitted) {
                        if (isThisTheCorrectAnswer) Text("✅", fontSize = 16.sp)
                        else if (isThisSelected) Text("❌", fontSize = 16.sp)
                    }
                }
            }
        }

        // Kotak Penjelasan
        if (isSubmitted && !isAnswerCorrect) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("❌ Jawaban Kurang Tepat", fontWeight = FontWeight.Bold, color = Color(0xFFFF8A80))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Jawaban yang benar: \"${lesson.options.getOrNull(targetCorrectIndex)}\"",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF81C784)
                    )
                    if (lesson.explanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Penjelasan: ${lesson.explanation}", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Aksi (Dengan Jarak Aman Naik)
        if (!isSubmitted) {
            Button(
                onClick = {
                    isSubmitted = true
                    if (isAnswerCorrect) {
                        SoundManager.playSuccess() // 🔔 Musik Sukses!
                    } else {
                        SoundManager.playWrong()   // Salah
                        if (isExamMode) hearts = (hearts - 1).coerceAtLeast(0)
                    }
                },
                enabled = selectedOptionIndex != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02))
            ) {
                Text("PERIKSA JAWABAN", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
            }
        } else {
            Button(
                onClick = {
                    onComplete(isAnswerCorrect, hearts)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAnswerCorrect) Color(0xFF58CC02) else Color(0xFFE65100)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isAnswerCorrect) "Luar Biasa! Lanjut ➔" else "Saya Mengerti, Coba Lagi",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }

        // Jarak Tepi Bawah Tambahan untuk Jaminan Bebas Bentrok di Segala Resolusi HP
        Spacer(modifier = Modifier.height(12.dp))
    }
}
