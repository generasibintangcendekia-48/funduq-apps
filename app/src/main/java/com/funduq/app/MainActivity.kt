package com.funduq.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funduq.app.ui.theme.FunduqAppTheme
import androidx.compose.ui.text.style.TextAlign


enum class Screen { AUTH, SELECT_BOOK, DASHBOARD, QUIZ }

class MainActivity : ComponentActivity() {
    private val repo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("funduq_session", MODE_PRIVATE)
        val savedCode = sharedPref.getString("saved_access_code", null)

        setContent {
            FunduqAppTheme {
                val currentContext = LocalContext.current
                var currentScreen by remember { mutableStateOf(if (savedCode != null) Screen.DASHBOARD else Screen.AUTH) }
                var currentUser by remember { mutableStateOf<UserProfile?>(null) }

                var lessonsList by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentActiveLessonId by remember { mutableStateOf("") }
                var isBypassExamActive by remember { mutableStateOf(false) }
                var currentHearts by remember { mutableIntStateOf(3) }
                var examQuestionsPool by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentExamQuestionIndex by remember { mutableIntStateOf(0) }

                // State Data Real untuk TuratsGo Landing Screen
                var totalUsers by remember { mutableStateOf(0L) }
                var topSantriList by remember { mutableStateOf<List<TopSantri>>(emptyList()) }
                // State untuk memicu Pop-up Selamat Kelulusan Unit
                var showUnitCompletionCelebration by remember { mutableStateOf<String?>(null) }


                // Auto-login sesi tersimpan
                LaunchedEffect(Unit) {
                    if (savedCode != null) {
                        repo.getUser(savedCode) { loadedUser ->
                            if (loadedUser != null) {
                                currentUser = loadedUser
                            } else {
                                sharedPref.edit().clear().apply()
                                currentScreen = Screen.AUTH
                            }
                        }
                    }
                }

                // Ambil data real Firestore untuk ditampilkan di Landing Page
                LaunchedEffect(currentScreen) {
                    if (currentScreen == Screen.AUTH) {
                        totalUsers = repo.getTotalRegisteredUsers()
                        topSantriList = repo.getTop5Santri()
                    }
                }

                // Ambil materi untuk kitab yang sedang dipilih santri
                LaunchedEffect(currentScreen, currentUser?.selectedBookTitle) {
                    val bookTitle = currentUser?.selectedBookTitle
                    if (currentScreen == Screen.DASHBOARD && !bookTitle.isNullOrBlank()) {
                        repo.getLessonsByBook(bookTitle) { fetched ->
                            lessonsList = fetched
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF131F24)
                ) {
                    when (currentScreen) {
                        // 1. LAYAR UTAMA: TURATSGO LANDING SCREEN
                        Screen.AUTH -> {
                            TuratsGoLandingScreen(
                                totalUsers = totalUsers,
                                topUsers = topSantriList,
                                registeredAccessCode = savedCode,
                                onLoginWithCode = { kodeInput ->
                                    val cleanedCode = kodeInput.trim()
                                    if (cleanedCode.isBlank()) {
                                        Toast.makeText(currentContext, "Masukkan kode akses terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repo.getUser(cleanedCode) { user ->
                                            if (user != null) {
                                                sharedPref.edit().putString("saved_access_code", user.accessCode).apply()
                                                currentUser = user
                                                currentScreen = Screen.DASHBOARD
                                                Toast.makeText(currentContext, "Ahlan wa Sahlan, ${user.name}! 👋", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(currentContext, "Kode akses tidak ditemukan di database", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                onRegisterNewUser = { name, city ->
                                    // Generate kode unik FDQ-XXXX otomatis
                                    val randomNum = (1000..9999).random()
                                    val generatedCode = "FDQ-$randomNum"
                                    val newUser = UserProfile(
                                        accessCode = generatedCode,
                                        name = name,
                                        city = city,
                                        totalXp = 0,
                                        currentStreak = 1
                                    )
                                    repo.registerUser(newUser) { success ->
                                        if (success) {
                                            sharedPref.edit().putString("saved_access_code", generatedCode).apply()
                                            currentUser = newUser
                                            currentScreen = Screen.SELECT_BOOK
                                            Toast.makeText(currentContext, "Akun berhasil dibuat! Kode Anda: $generatedCode", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(currentContext, "Gagal membuat akun, periksa internet Anda", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                        }

                        // 2. PILIH KELAS & KITAB
                        Screen.SELECT_BOOK -> {
                            currentUser?.let { user ->
                                SelectBookPureDatabaseScreen(
                                    currentLevel = user.selectedLevelId,
                                    currentBook = user.selectedBookTitle,
                                    repo = repo,
                                    currentUser = user,
                                    onConfirm = { chosenLevel, chosenBook ->
                                        val updated = user.copy(selectedLevelId = chosenLevel, selectedBookTitle = chosenBook)
                                        repo.registerUser(updated) {
                                            currentUser = updated
                                            currentScreen = Screen.DASHBOARD
                                        }
                                    },
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                            }
                        }



                        // 3. DASBOR UTAMA
                        Screen.DASHBOARD -> {
                            currentUser?.let { user ->
                                DashboardScreen(
                                    user = user,
                                    lessons = lessonsList,
                                    repo = repo,
                                    onOpenSelectBook = { currentScreen = Screen.SELECT_BOOK },
                                    onStartSpecificLesson = { lessonId ->
                                        currentActiveLessonId = lessonId
                                        isBypassExamActive = false
                                        currentHearts = 3
                                        currentScreen = Screen.QUIZ
                                    },
                                    onStartBypassExam = { targetUnitTitle ->
                                        val exams = lessonsList.filter {
                                            (it.unitTitle.equals(targetUnitTitle, ignoreCase = true) || it.chapterTitle.contains("Ujian", ignoreCase = true)) &&
                                                    (it.isExam || it.title.contains("Ujian", ignoreCase = true))
                                        }.ifEmpty {
                                            lessonsList.filter { it.unitTitle.equals(targetUnitTitle, ignoreCase = true) }.take(5)
                                        }.ifEmpty {
                                            lessonsList.shuffled().take(3)
                                        }

                                        if (exams.isNotEmpty()) {
                                            examQuestionsPool = exams
                                            currentExamQuestionIndex = 0
                                            currentActiveLessonId = exams.first().id
                                            isBypassExamActive = true
                                            currentHearts = 3
                                            currentScreen = Screen.QUIZ
                                        } else {
                                            Toast.makeText(currentContext, "Soal ujian unit ini belum disiapkan admin", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onUpdateAvatar = { newAvatar ->
                                        val updated = user.copy(avatar = newAvatar)
                                        repo.registerUser(updated) {
                                            currentUser = updated
                                            Toast.makeText(currentContext, "Foto avatar berhasil diperbarui! 🎉", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLogout = {
                                        sharedPref.edit().clear().apply()
                                        currentUser = null
                                        currentScreen = Screen.AUTH
                                    }, // <--- Pastikan ada tanda koma di sini sebelum parameter baru!
                                    showUnitCelebrationInDashboard = showUnitCompletionCelebration,
                                    onDismissCelebration = { showUnitCompletionCelebration = null }
                                )

                            }
                        }

                        // 4. KUIS & UJIAN
                        Screen.QUIZ -> {
                            val activeLesson = if (isBypassExamActive) {
                                examQuestionsPool.getOrNull(currentExamQuestionIndex)
                            } else {
                                lessonsList.firstOrNull { it.id == currentActiveLessonId }
                            }

                            if (activeLesson != null) {
                                LessonQuizScreen(
                                    lesson = activeLesson,
                                    isExamMode = isBypassExamActive,
                                    initialHearts = currentHearts,
                                    onComplete = { isCorrect, remainingHearts ->
                                        currentHearts = remainingHearts
                                        currentUser?.let { user ->
                                            if (isBypassExamActive) {
                                                if (remainingHearts <= 0) {
                                                    Toast.makeText(currentContext, "Ujian Gagal. Coba lagi!", Toast.LENGTH_SHORT).show()
                                                    currentScreen = Screen.DASHBOARD
                                                } else if (isCorrect) {
                                                    if (currentExamQuestionIndex + 1 < examQuestionsPool.size) {
                                                        currentExamQuestionIndex++
                                                        currentActiveLessonId = examQuestionsPool[currentExamQuestionIndex].id
                                                    } else {
                                                        // UJIAN BYPASS SELESAI -> PICU POP-UP SELEBRASI UNIT!
                                                        val targetUnitLessons = lessonsList.filter { it.unitTitle == activeLesson.unitTitle }.map { it.id }
                                                        val updated = user.copy(
                                                            completedLessons = (user.completedLessons + targetUnitLessons).distinct(),
                                                            totalXp = user.totalXp + 50
                                                        )
                                                        repo.registerUser(updated) {
                                                            currentUser = updated
                                                            showUnitCompletionCelebration = activeLesson.unitTitle // <--- PICU SELEBRASI
                                                            currentScreen = Screen.DASHBOARD
                                                        }
                                                    }
                                                } else {
                                                    if (remainingHearts > 0 && currentExamQuestionIndex + 1 < examQuestionsPool.size) {
                                                        currentExamQuestionIndex++
                                                        currentActiveLessonId = examQuestionsPool[currentExamQuestionIndex].id
                                                    } else {
                                                        currentScreen = Screen.DASHBOARD
                                                    }
                                                }
                                            } else {
                                                // MODE SOAL BIASA BERURUTAN
                                                repo.updateLessonResult(user, activeLesson.id, isCorrect) { updatedUser ->
                                                    currentUser = updatedUser
                                                    if (isCorrect) {
                                                        val regularLessons = lessonsList.filter { !it.isExam }
                                                        val nextUnfinishedLesson = regularLessons.firstOrNull { item ->
                                                            (!updatedUser.completedLessons.contains(item.id) || updatedUser.failedLessons.contains(item.id)) && item.id != activeLesson.id
                                                        }

                                                        if (nextUnfinishedLesson != null) {
                                                            currentActiveLessonId = nextUnfinishedLesson.id
                                                            Toast.makeText(currentContext, "+25 XP! Lanjut 🔥", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            // UNIT SELESAI SECARA BERURUTAN -> PICU POP-UP SELEBRASI UNIT!
                                                            showUnitCompletionCelebration = activeLesson.unitTitle // <--- PICU SELEBRASI
                                                            currentScreen = Screen.DASHBOARD
                                                        }
                                                    } else {
                                                        currentScreen = Screen.DASHBOARD
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                            }
                        }
                    }
                }


            }

        }
    }
}

// 1. LAYAR PILIH KELAS & KITAB (100% DATABASE DINAMIS)
@Composable
fun SelectBookPureDatabaseScreen(
    currentLevel: String,
    currentBook: String,
    repo: FirebaseRepository,
    currentUser: UserProfile? = null,
    onConfirm: (level: String, book: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var dbCurriculumMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var booksProgressList by remember { mutableStateOf<List<BookProgressInfo>>(emptyList()) }
    var selectedLevelName by remember { mutableStateOf(currentLevel) }
    var selectedBookTitle by remember { mutableStateOf(currentBook) }

    // Dialog State
    var selectedBookForInfo by remember { mutableStateOf<BookProgressInfo?>(null) }
    var bookToPrintCertificate by remember { mutableStateOf<BookProgressInfo?>(null) }

    LaunchedEffect(Unit) {
        repo.getAvailableCurriculumSummary { map ->
            dbCurriculumMap = map
            if (selectedLevelName.isBlank() || !map.containsKey(selectedLevelName)) {
                selectedLevelName = map.keys.firstOrNull() ?: ""
            }
            if (selectedBookTitle.isBlank() && selectedLevelName.isNotBlank()) {
                selectedBookTitle = map[selectedLevelName]?.firstOrNull() ?: ""
            }
        }
        repo.getBooksWithProgress(currentUser?.completedLessons ?: emptyList()) { list ->
            booksProgressList = list
        }
    }

    val availableBooks = dbCurriculumMap[selectedLevelName] ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131F24))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Tombol Kembali
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🐪", fontSize = 52.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36))
            ) {
                Text(
                    text = "Pilih Jenjang & Kitab Kurikulum:",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("1. JENJANG / KELAS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(8.dp))

        if (dbCurriculumMap.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Memuat kurikulum...", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        } else {
            dbCurriculumMap.keys.forEach { levelKey ->
                val isSelected = selectedLevelName == levelKey
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF202F36)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedLevelName = levelKey
                            selectedBookTitle = dbCurriculumMap[levelKey]?.firstOrNull() ?: ""
                        }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isSelected) "🟢" else "⚪", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(levelKey, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text("${dbCurriculumMap[levelKey]?.size} Kitab Tersedia", fontSize = 11.sp, color = Color(0xFF81C784))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text("2. DAFTAR KITAB & STATUS PROGRES", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(8.dp))

        availableBooks.forEach { bookName ->
            val isChosen = selectedBookTitle == bookName
            val bookInfo = booksProgressList.firstOrNull { it.bookTitle.equals(bookName, ignoreCase = true) }
                ?: BookProgressInfo(bookTitle = bookName)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChosen) Color(0xFF1E382B) else Color(0xFF202F36)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .border(
                        width = if (isChosen) 2.dp else 0.dp,
                        color = if (isChosen) Color(0xFF58CC02) else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { selectedBookTitle = bookName }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Bagian Atas: Label FieldType & Icon Info (!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF131F24)
                        ) {
                            Text(
                                text = "🏷️ ${bookInfo.fieldType}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Icon Info (!) di pojok kanan atas
                        IconButton(
                            onClick = { selectedBookForInfo = bookInfo },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("ℹ️", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bagian Tengah: Judul & Badge Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("📖", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = bookName,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        // Badge Progres Status
                        when {
                            bookInfo.isCompleted -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = "🏅 Tamat",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            bookInfo.isNew -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7)
                                ) {
                                    Text(
                                        text = "✨ NEW!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "💧 ${bookInfo.progressPercentage}% (${bookInfo.completedCount}/${bookInfo.totalCount})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tombol Unduh Sertifikat (Disable jika belum tamat)
                    Button(
                        onClick = { bookToPrintCertificate = bookInfo },
                        enabled = bookInfo.isCompleted,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            disabledContainerColor = Color(0xFF2E383F)
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(
                            text = if (bookInfo.isCompleted) "📜 Unduh Sertifikat Kelulusan" else "🔒 Selesaikan Kitab untuk Buka Sertifikat",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (bookInfo.isCompleted) Color.Black else Color.Gray
                        )
                    }
                }
            }
        }

        // ==========================================
// DIALOG SELEBRASI KELULUSAN UNIT MEGAH 🎉
// ==========================================
        @Composable
        fun UnitCompletionCelebrationDialog(
            unitTitle: String,
            onDismiss: () -> Unit
        ) {
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(22.dp),
                containerColor = Color(0xFF0F172A),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉 ALHAMDULILLAH! 🎉", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFFACC15))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Unit Selesai Sempurna!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🌴", fontSize = 48.sp)
                        Text(
                            text = "Selamat! Kamu telah menuntaskan seluruh butir materi latihan dan ujian pada:\n\n\"$unitTitle\"",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E382B)
                        ) {
                            Text(
                                text = "🎁 Bonus Khatam: +50 XP & Progres Air Terisi! 💧",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("KEMBALI KE BERANDA ➔", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            )
        }


        Spacer(modifier = Modifier.height(26.dp))
        Button(
            onClick = { onConfirm(selectedLevelName, selectedBookTitle) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
            enabled = selectedBookTitle.isNotBlank()
        ) {
            Text("LANJUTKAN KE JALUR BELAJAR ➔", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    // --- POP-UP 1: INFORMASI DETAIL KITAB DARI DATA CSV ---
    selectedBookForInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { selectedBookForInfo = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(info.bookTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Disiplin Ilmu:", fontSize = 11.sp, color = Color.Gray)
                        Text("📚 ${info.fieldType}", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Column {
                        Text("Pengarang Kitab:", fontSize = 11.sp, color = Color.Gray)
                        Text("✍️ ${info.author}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Column {
                        Text("Ringkasan Informasi / Sinopsis:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = info.description,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    HorizontalDivider(color = Color(0xFF334155))
                    Text("Total Pelajaran: ${info.totalCount} Soal/Hadits", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBookForInfo = null }) {
                    Text("Tutup", color = Color(0xFF58CC02), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- POP-UP 2: TEMPLATE SERTIFIKAT KELULUSAN ---
    bookToPrintCertificate?.let { certBook ->
        AlertDialog(
            onDismissRequest = { bookToPrintCertificate = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF0F172A),
            title = {
                Text("🎓 Sertifikat Tamat Belajar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    modifier = Modifier.fillMaxWidth().border(3.dp, Color(0xFFD97706), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📜 TURATSGO CERTIFICATE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF92400E))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Diberikan kepada Santri:", fontSize = 10.sp, color = Color.DarkGray)
                        Text(
                            text = currentUser?.name ?: "Santri TuratsGo",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color(0xFF78350F)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Telah tuntas mengkaji dan mengkhatamkan kitab:",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "\"${certBook.bookTitle}\"",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⭐ Predikat: Mumtaz (100% Selesai) ⭐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PdfCertificateHelper.generateAndDownloadPdf(
                            context = context,
                            studentName = currentUser?.name ?: "Santri TuratsGo",
                            bookTitle = certBook.bookTitle
                        )
                        bookToPrintCertificate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Unduh PDF", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToPrintCertificate = null }) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        )
    }
}




// 2. DASBOR DENGAN STICKY HEADER & ACCORDION
@Composable
fun DashboardScreen(
    user: UserProfile,
    lessons: List<Lesson>,
    repo: FirebaseRepository,
    onOpenSelectBook: () -> Unit,
    onStartSpecificLesson: (lessonId: String) -> Unit,
    onStartBypassExam: (targetUnitTitle: String) -> Unit,
    onUpdateAvatar: (String) -> Unit,
    onLogout: () -> Unit,
    showUnitCelebrationInDashboard: String? = null,
    onDismissCelebration: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SoundManager.startBgm(context)
    }
    var selectedBottomNavIndex by remember { mutableIntStateOf(0) }
    var showTableOfContentsDialog by remember { mutableStateOf(false) }
    var showBypassExamDialog by remember { mutableStateOf<String?>(null) }
    var lessonToConfirmRedo by remember { mutableStateOf<Lesson?>(null) }

    val regularLessons = remember(lessons) { lessons.filter { !it.isExam } }
    val totalCount = regularLessons.size.coerceAtLeast(1)
    val completedCount = regularLessons.count { user.completedLessons.contains(it.id) }
    val waterPercent = ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt().coerceIn(0, 100)

    val activeLessonIndex = regularLessons.indexOfFirst { lesson ->
        !user.completedLessons.contains(lesson.id)
    }.let { if (it == -1) regularLessons.size - 1 else it }

    val activeLesson = regularLessons.getOrNull(activeLessonIndex)
    val listState = rememberLazyListState()

    LaunchedEffect(activeLessonIndex, lessons) {
        if (activeLessonIndex > 0) {
            listState.animateScrollToItem((activeLessonIndex - 1).coerceAtLeast(0))
        }
    }

    val unitsGrouped = remember(regularLessons) { regularLessons.groupBy { it.unitTitle.ifBlank { "Unit 1" } } }
    val allUnitKeys = unitsGrouped.keys.toList()

    val currentUnitKey = activeLesson?.unitTitle ?: allUnitKeys.firstOrNull() ?: ""
    val currentUnitIndex = allUnitKeys.indexOf(currentUnitKey).coerceAtLeast(0)
    val allowedUnits = allUnitKeys.take(currentUnitIndex + 2)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131F24))
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF202F36),
                    modifier = Modifier.clickable { onOpenSelectBook() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📖", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(user.selectedBookTitle.take(12), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 16.sp)
                    Text(" ${user.currentStreak}", color = Color(0xFFFF9600), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💎", fontSize = 16.sp)
                    Text(" ${user.totalXp}", color = Color(0xFF1CB0F6), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 16.sp)
                    Text(" ${user.dailyMinutesLearned}m", color = Color(0xFFFF4B4B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                var isMutedDashboard by remember { mutableStateOf(SoundManager.isMuted) }
                IconButton(
                    onClick = { isMutedDashboard = SoundManager.toggleMute(context) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (isMutedDashboard) "🔇" else "🔊", fontSize = 18.sp)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF131F24)) {
                NavigationBarItem(selected = selectedBottomNavIndex == 0, onClick = { selectedBottomNavIndex = 0 }, icon = { Text("🏠", fontSize = 20.sp) }, label = { Text("Home", fontSize = 10.sp, color = Color.White) })
                NavigationBarItem(selected = selectedBottomNavIndex == 1, onClick = { selectedBottomNavIndex = 1 }, icon = { Text("🎯", fontSize = 20.sp) }, label = { Text("Target", fontSize = 10.sp, color = Color.White) })
                NavigationBarItem(selected = selectedBottomNavIndex == 2, onClick = { selectedBottomNavIndex = 2 }, icon = { Text("🏆", fontSize = 20.sp) }, label = { Text("Peringkat", fontSize = 10.sp, color = Color.White) })
                NavigationBarItem(selected = selectedBottomNavIndex == 3, onClick = { selectedBottomNavIndex = 3 }, icon = { Text("👤", fontSize = 20.sp) }, label = { Text("Profil", fontSize = 10.sp, color = Color.White) })
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131F24))
                .padding(padding)
        ) {
            when (selectedBottomNavIndex) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Card(
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF58CC02)),
                            modifier = Modifier.fillMaxWidth().clickable { showTableOfContentsDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = (activeLesson?.unitTitle ?: "UNIT 1").uppercase(), color = Color(0xFF265700), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                    Text(text = "${user.selectedBookTitle}: ${activeLesson?.chapterTitle ?: "Materi Utama"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("💧 Progres Air: $waterPercent% Terisi ($completedCount/$totalCount Hadits)", color = Color(0xFFE8F5E9), fontSize = 11.sp)
                                }
                                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF46A302), modifier = Modifier.size(42.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text("📋", fontSize = 20.sp) }
                                }
                            }
                        }

                        if (regularLessons.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Materi kitab ini belum tersedia di database Firestore.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                allowedUnits.forEachIndexed { unitIdx, unitKey ->
                                    val unitItems = unitsGrouped[unitKey] ?: emptyList()

                                    if (unitIdx > 0) {
                                        item {
                                            Spacer(modifier = Modifier.height(18.dp))
                                            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1E2E28), modifier = Modifier.padding(horizontal = 20.dp)) {
                                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("🌴", fontSize = 24.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Alhamdulillah! Gerbang Oasis $unitKey", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(18.dp))
                                        }
                                    }

                                    itemsIndexed(unitItems) { _, lesson ->
                                        val globalIdx = regularLessons.indexOf(lesson)
                                        val isCompleted = user.completedLessons.contains(lesson.id)
                                        val isFailed = user.failedLessons.contains(lesson.id)
                                        val isCurrent = globalIdx == activeLessonIndex
                                        val isLocked = !isCompleted && !isFailed && globalIdx > activeLessonIndex

                                        val horizontalOffset = when (globalIdx % 4) {
                                            0 -> (-50).dp
                                            1 -> 0.dp
                                            2 -> 50.dp
                                            else -> 0.dp
                                        }

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("•", fontSize = 14.sp, color = Color(0xFF37474F))

                                            Box(modifier = Modifier.offset(x = horizontalOffset).wrapContentSize(), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    val mainColor = when {
                                                        isCompleted -> Color(0xFF58CC02)
                                                        isFailed -> Color(0xFFFFC800)
                                                        isCurrent -> Color(0xFF58CC02)
                                                        else -> Color(0xFF37474F)
                                                    }
                                                    val bottomShadowColor = when {
                                                        isCompleted -> Color(0xFF46A302)
                                                        isFailed -> Color(0xFFE5B200)
                                                        isCurrent -> Color(0xFF46A302)
                                                        else -> Color(0xFF263238)
                                                    }

                                                    Surface(
                                                        shape = CircleShape,
                                                        color = bottomShadowColor,
                                                        modifier = Modifier
                                                            .size(if (isCurrent) 74.dp else 64.dp)
                                                            .clickable {
                                                                if (isLocked) {
                                                                    showBypassExamDialog = lesson.unitTitle
                                                                } else if (isCompleted) {
                                                                    lessonToConfirmRedo = lesson
                                                                } else {
                                                                    onStartSpecificLesson(lesson.id)
                                                                }
                                                            }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(bottom = 6.dp).fillMaxSize().clip(CircleShape).background(mainColor),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    isCompleted -> "✓"
                                                                    isFailed -> "⚠️"
                                                                    isLocked -> "🔒"
                                                                    isCurrent -> "⭐"
                                                                    else -> "${globalIdx + 1}"
                                                                },
                                                                fontSize = if (isCompleted) 28.sp else 22.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(lesson.title.ifBlank { "Pos ${globalIdx + 1}" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isLocked) Color.Gray else Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(50.dp)) }
                            }
                        }
                    }
                }
                1 -> TargetsTab(user)
                2 -> LeaderboardTab(user)
                3 -> ProfileTab(user = user, onUpdateAvatar = onUpdateAvatar, onLogout = onLogout)
            }
        }
    }

    // POP-UP 1: REDO SOAL
    lessonToConfirmRedo?.let { lesson ->
        AlertDialog(
            onDismissRequest = { lessonToConfirmRedo = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = { Text("🔄 Kerjakan Ulang Soal?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = { Text(text = "Soal \"${lesson.title}\" ini sudah pernah kamu selesaikan. Apakah kamu yakin ingin mengerjakannya ulang?", color = Color.LightGray, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = lesson.id
                        lessonToConfirmRedo = null
                        onStartSpecificLesson(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Ya, Kerjakan Ulang", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { lessonToConfirmRedo = null }) { Text("Batal", color = Color.Gray) } }
        )
    }

    // POP-UP 2: DAFTAR ISI ACCORDION
    if (showTableOfContentsDialog) {
        AlertDialog(
            onDismissRequest = { showTableOfContentsDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = { Text("📋 Daftar Isi: ${user.selectedBookTitle}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            text = {
                val chaptersGrouped = remember(regularLessons) { regularLessons.groupBy { it.chapterTitle.ifBlank { "Bab Utama" } } }
                var expandedChapterKey by remember { mutableStateOf<String?>(null) }
                var expandedUnitKey by remember { mutableStateOf<String?>(null) }

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chaptersGrouped.forEach { (chapterName, chapterItems) ->
                        val isChapterOpen = expandedChapterKey == chapterName
                        val chapDone = chapterItems.count { user.completedLessons.contains(it.id) }
                        val chapPercent = (chapDone * 100) / chapterItems.size.coerceAtLeast(1)

                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isChapterOpen) Color(0xFF16252B) else Color(0xFF131F24)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        expandedChapterKey = if (isChapterOpen) null else chapterName
                                        expandedUnitKey = null
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(chapterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("💧 $chapPercent% ($chapDone/${chapterItems.size} Soal Selesai)", color = Color(0xFF81C784), fontSize = 11.sp)
                                    }
                                    Text(text = if (isChapterOpen) "▲" else "▼", color = if (isChapterOpen) Color(0xFF58CC02) else Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                if (isChapterOpen) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFF263A42))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val subUnits = chapterItems.groupBy { it.unitTitle.ifBlank { "Unit 1" } }
                                    subUnits.forEach { (unitName, unitLessons) ->
                                        val isUnitOpen = expandedUnitKey == unitName
                                        val unitDone = unitLessons.count { user.completedLessons.contains(it.id) }

                                        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (isUnitOpen) Color(0xFF20323B) else Color(0xFF1A282E)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable { expandedUnitKey = if (isUnitOpen) null else unitName },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(unitName, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                        Text("$unitDone/${unitLessons.size} Butir Materi", color = Color.Gray, fontSize = 10.sp)
                                                    }
                                                    Text(text = if (isUnitOpen) "▲" else "▼", color = if (isUnitOpen) Color(0xFFFFD54F) else Color.LightGray, fontSize = 12.sp)
                                                }

                                                if (isUnitOpen) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    unitLessons.forEach { lesson ->
                                                        val isDone = user.completedLessons.contains(lesson.id)
                                                        val isFailed = user.failedLessons.contains(lesson.id)
                                                        val isLock = !isDone && !isFailed && regularLessons.indexOf(lesson) > activeLessonIndex

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                                                                showTableOfContentsDialog = false
                                                                if (isLock) showBypassExamDialog = lesson.unitTitle
                                                                else if (isDone) lessonToConfirmRedo = lesson
                                                                else onStartSpecificLesson(lesson.id)
                                                            },
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(text = if (isDone) "✓" else if (isFailed) "⚠️" else if (isLock) "🔒" else "💧", fontSize = 13.sp, color = if (isDone) Color(0xFF58CC02) else if (isFailed) Color(0xFFFFC800) else Color.White)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(text = lesson.title, color = if (isLock) Color.Gray else Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTableOfContentsDialog = false }) { Text("Tutup", color = Color(0xFF58CC02), fontWeight = FontWeight.Bold) } }
        )
    }

    // POP-UP 3: BYPASS EXAM
    showBypassExamDialog?.let { targetUnit ->
        AlertDialog(
            onDismissRequest = { showBypassExamDialog = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = { Text("⚡ Ujian Loncat ke $targetUnit?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ikuti ujian kenaikan untuk langsung menerobos unit ini tanpa mengerjakan bab sebelumnya!", color = Color.LightGray, fontSize = 13.sp)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C20))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚠️ KETENTUAN UJIAN KENAIKAN:", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• Berisi 3-5 Pertanyaan Ujian Evaluasi", color = Color.White, fontSize = 12.sp)
                            Text("• Dibekali 3 Hati (❤️❤️❤️)", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val unit = targetUnit
                        showBypassExamDialog = null
                        onStartBypassExam(unit)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Mulai Ujian 3 Hati 🚀", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBypassExamDialog = null }) { Text("Nanti Saja", color = Color.Gray) } }
        )
    }

    // POP-UP 4: DIALOG SELEBRASI POP-UP MENYATU DI BAWAH DASHBOARD
    showUnitCelebrationInDashboard?.let { completedUnit ->
        UnitCompletionCelebrationDialog(
            unitTitle = completedUnit,
            onDismiss = onDismissCelebration
        )
    }
}



// Model Tantangan Dinamis dari Firebase
data class ChallengeItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val targetType: String = "STREAK", // STREAK, XP, MINUTES
    val targetCount: Int = 10,
    val rewardXp: Int = 50,
    val iconEmoji: String = "🎯"
)

@Composable
fun TargetsTab(user: UserProfile) {
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var challengesList by remember { mutableStateOf<List<ChallengeItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("challenges")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(ChallengeItem::class.java)
                challengesList = if (list.isNotEmpty()) list else listOf(
                    ChallengeItem("1", "Istiqomah 10 Hari Streak", "Belajar hadits berturut-turut", "STREAK", 10, 50, "🔥"),
                    ChallengeItem("2", "Belajar 10 Menit Harian", "Fokus membaca dan telaah materi", "MINUTES", 10, 25, "⚡")
                )
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Target & Misi Harian 🎯", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Text("Selesaikan misi dari pengajar untuk raih bonus XP melimpah:", fontSize = 12.sp, color = Color.Gray)

        challengesList.forEach { challenge ->
            val currentProgress = when (challenge.targetType) {
                "STREAK" -> user.currentStreak
                "XP" -> user.totalXp
                "MINUTES" -> user.dailyMinutesLearned
                else -> user.completedLessons.size
            }
            val progressPercent = (currentProgress.toFloat() / challenge.targetCount.toFloat()).coerceIn(0f, 1f)
            val isDone = currentProgress >= challenge.targetCount

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDone) Color(0xFF1B3D2F) else Color(0xFF202F36)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(challenge.iconEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(challenge.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                        Text(
                            text = "+${challenge.rewardXp} XP ⭐",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(challenge.description, color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDone) "Tuntas! 🎉" else "Progres: $currentProgress / ${challenge.targetCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) Color(0xFF58CC02) else Color(0xFFFF9600)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (isDone) Color(0xFF58CC02) else Color(0xFFFF9600),
                        trackColor = Color(0xFF37474F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// 3. TAB PERINGKAT SANTRI (REAL-TIME DARI FIRESTORE)
@Composable
fun LeaderboardTab(user: UserProfile) {
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    var rankingsList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("users")
            .orderBy("totalXp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                rankingsList = snap.toObjects(UserProfile::class.java)
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Papan Peringkat Santri 🏆", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Text("Santri paling istiqomah belajar dari seluruh Nusantara:", fontSize = 12.sp, color = Color.Gray)

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF58CC02))
            }
        } else if (rankingsList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada santri yang terdaftar di database.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            rankingsList.forEachIndexed { rank, santri ->
                val isCurrentUser = santri.accessCode == user.accessCode
                val cardBorder = if (isCurrentUser) Color(0xFF58CC02) else Color.Transparent
                val cardBg = when {
                    isCurrentUser -> Color(0xFF1E382B)
                    rank == 0 -> Color(0xFF2E7D32)
                    rank == 1 -> Color(0xFF1C3238)
                    rank == 2 -> Color(0xFF382B1C)
                    else -> Color(0xFF202F36)
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (isCurrentUser) 2.dp else 0.dp, cardBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (rank) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "#${rank + 1}"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = santri.avatar.ifBlank { "👳‍♂️" },
                            fontSize = 26.sp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = santri.name.ifBlank { "Santri Funduq" },
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                if (isCurrentUser) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF58CC02)
                                    ) {
                                        Text(
                                            text = "Kamu",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Kota: ${santri.city.ifBlank { "Indonesia" }} • 🔥 ${santri.currentStreak} Hari",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }

                        Text(
                            text = "⭐ ${santri.totalXp} XP",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD54F),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// 4. TAB PROFIL
@Composable
fun ProfileTab(
    user: UserProfile,
    onUpdateAvatar: (String) -> Unit,
    onLogout: () -> Unit
) {
    val currentContext = LocalContext.current
    var showAvatarPicker by remember { mutableStateOf(false) }
    val avatarOptions = listOf("👳‍♂️", "🧕", "🎓", "📚", "🕌", "🐪", "⭐", "🌿", "🦁", "📖")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2E7D32),
                modifier = Modifier.size(90.dp).clickable { showAvatarPicker = true }
            ) {
                Box(contentAlignment = Alignment.Center) { Text(user.avatar.ifBlank { "👳‍♂️" }, fontSize = 48.sp) }
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFF58CC02),
                modifier = Modifier.size(28.dp).clickable { showAvatarPicker = true }
            ) {
                Box(contentAlignment = Alignment.Center) { Text("✏️", fontSize = 14.sp) }
            }
        }

        Text(text = "Klik foto untuk ganti avatar 📷", fontSize = 11.sp, color = Color(0xFF81C784), modifier = Modifier.clickable { showAvatarPicker = true })
        Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Kota: ${user.city} • Tingkat: ${user.selectedLevelId}", fontSize = 13.sp, color = Color.LightGray)

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("KODE AKSES LOGIN PRIBADI:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Text(user.accessCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF58CC02))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gunakan kode ini jika ingin berpindah ke perangkat HP lain.", fontSize = 11.sp, color = Color.LightGray)
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val url = "https://api.whatsapp.com/send?phone=628567688848&text=Halo%20Admin%20TuratsGo,%20saya%20butuh%20bantuan%20seputar%20akun%20saya%20(${user.name}%20-%20${user.accessCode})."
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    currentContext.startActivity(intent)
                }
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFF25D366), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("💬", fontSize = 18.sp) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ada Kendala Akun / Pertanyaan?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                    Text("Hubungi WhatsApp: 0856 76 88848", fontSize = 12.sp, color = Color(0xFF2E7D32))
                }
                Text("Chat ➔", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 12.sp)
            }
        }

        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Keluar dari Akun (Logout)", fontWeight = FontWeight.Bold)
        }
    }

    if (showAvatarPicker) {
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = { Text("Pilih Karakter Santri 👳‍♂️", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pilih avatar yang mencerminkan semangat belajarmu:", color = Color.LightGray, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        avatarOptions.take(5).forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = if (user.avatar == emoji) Color(0xFF58CC02) else Color(0xFF131F24),
                                modifier = Modifier.size(48.dp).clickable {
                                    onUpdateAvatar(emoji)
                                    showAvatarPicker = false
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        avatarOptions.drop(5).take(5).forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = if (user.avatar == emoji) Color(0xFF58CC02) else Color(0xFF131F24),
                                modifier = Modifier.size(48.dp).clickable {
                                    onUpdateAvatar(emoji)
                                    showAvatarPicker = false
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAvatarPicker = false }) { Text("Tutup", color = Color(0xFF58CC02), fontWeight = FontWeight.Bold) } }
        )
    }
}

// ==========================================
// DIALOG SELEBRASI KELULUSAN UNIT MEGAH 🎉
// ==========================================
@Composable
fun UnitCompletionCelebrationDialog(
    unitTitle: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF0F172A),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎉 ALHAMDULILLAH! 🎉", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFFACC15))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Unit Selesai Sempurna!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🌴", fontSize = 48.sp)
                Text(
                    text = "Selamat! Kamu telah menuntaskan seluruh butir materi latihan dan ujian pada:\n\n\"$unitTitle\"",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E382B)
                ) {
                    Text(
                        text = "🎁 Bonus Khatam: +50 XP & Progres Air Terisi! 💧",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF69F0AE),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("KEMBALI KE BERANDA ➔", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

