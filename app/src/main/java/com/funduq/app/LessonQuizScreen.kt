package com.funduq.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LessonQuizScreen(
    lesson: Lesson,
    onComplete: (isCorrect: Boolean) -> Unit,
    onBack: () -> Unit
) {
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }

    val targetCorrectIndex = lesson.correctIndex
    val isAnswerCorrect = selectedOptionIndex != null && selectedOptionIndex == targetCorrectIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                Text("⬅ Keluar", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
            }
            AssistChip(
                onClick = {},
                label = { Text("Misi: +${lesson.rewardXp} XP ⭐", fontWeight = FontWeight.Bold, color = Color(0xFFE65100)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
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
                    color = Color(0xFF1B5E20)
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFAED581))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Artinya:\n\"${lesson.translation}\"",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF33691E)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Pertanyaan:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF757575))
        Text(text = lesson.question, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF212121))

        Spacer(modifier = Modifier.height(14.dp))

        lesson.options.forEachIndexed { index, option ->
            val isThisSelected = selectedOptionIndex == index
            val isThisTheCorrectAnswer = index == targetCorrectIndex

            val cardBg = when {
                !isSubmitted && isThisSelected -> Color(0xFFDCEDC8)
                isSubmitted && isThisTheCorrectAnswer -> Color(0xFFC8E6C9)
                isSubmitted && isThisSelected && !isThisTheCorrectAnswer -> Color(0xFFFFCDD2)
                else -> Color.White
            }

            val borderColor = when {
                !isSubmitted && isThisSelected -> Color(0xFF2E7D32)
                isSubmitted && isThisTheCorrectAnswer -> Color(0xFF2E7D32)
                isSubmitted && isThisSelected && !isThisTheCorrectAnswer -> Color(0xFFD32F2F)
                else -> Color(0xFFE0E0E0)
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
                        color = Color(0xFF212121)
                    )
                    if (isSubmitted) {
                        if (isThisTheCorrectAnswer) Text("✅", fontSize = 16.sp)
                        else if (isThisSelected) Text("❌", fontSize = 16.sp)
                    }
                }
            }
        }

        if (isSubmitted && !isAnswerCorrect) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("❌ Jawaban Anda Sebelumnya Kurang Tepat", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Jawaban yang benar: \"${lesson.options.getOrNull(targetCorrectIndex)}\"",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                    if (lesson.explanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Penjelasan: ${lesson.explanation}", fontSize = 13.sp, color = Color(0xFF5D4037))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isSubmitted) {
            Button(
                onClick = { isSubmitted = true },
                enabled = selectedOptionIndex != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Periksa Jawaban", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = {
                    onComplete(isAnswerCorrect)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAnswerCorrect) Color(0xFF2E7D32) else Color(0xFFE65100)
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isAnswerCorrect) "Luar Biasa! Lanjut (+25 XP) 🚀" else "Saya Mengerti, Lanjutkan (-5 XP) ➔",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
