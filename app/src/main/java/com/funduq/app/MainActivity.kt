package com.funduq.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.funduq.app.ui.theme.FunduqAppTheme
import kotlinx.coroutines.delay

enum class Screen { SPLASH, AUTH, SELECT_BOOK, DASHBOARD, QUIZ }

// ==========================================
// PALET WARNA PADANG PASIR & OASE TIMUR TENGAH
// ==========================================
private object DesertOasisPalette {
    val SandSkyTop = Color(0xFFF7CF98)
    val SandDuneLight = Color(0xFFF4C382)
    val SandDuneDark = Color(0xFFE5A965)
    val DesertText = Color(0xFF3D2110)
    val HeaderBrownDark = Color(0xFF4A2816)
    val HeaderBrownMid = Color(0xFF6B3C22)
    val GoldBorder = Color(0xFFD49B54)
    val OasisGreenTop = Color(0xFF65C81C)
    val OasisGreenBottom = Color(0xFF479C0C)
    val OasisGreenDark = Color(0xFF2D6604)
    val WaterBlue = Color(0xFF38BDF8)
}

private object CurriculumPalette {
    val Background = Color(0xFFF5EFE6)
    val Card = Color(0xFFFFFDF9)
    val Mint = Color(0xFFF0FAF6)
    val Sand = Color(0xFFEBD9C3)
    val Teal = Color(0xFF1E5E7A)
    val Gold = Color(0xFFF2A922)
    val Terracotta = Color(0xFFE0623A)
    val Green = Color(0xFF2D9F75)
    val GreenDark = Color(0xFF1E7554)
    val Text = Color(0xFF2C221E)
    val Muted = Color(0xFF8C776B)
    val Border = Color(0xFF4A3728)
}

class MainActivity : ComponentActivity() {
    private val repo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("funduq_session", MODE_PRIVATE)
        val savedCode = sharedPref.getString("saved_access_code", null)

        setContent {
            FunduqAppTheme {
                val currentContext = LocalContext.current

                // 1. Awal buka aplikasi -> Tampilkan Layar SPLASH Video
                var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
                var currentUser by remember { mutableStateOf<UserProfile?>(null) }

                var lessonsList by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentActiveLessonId by remember { mutableStateOf("") }
                var isBypassExamActive by remember { mutableStateOf(false) }
                var currentHearts by remember { mutableIntStateOf(3) }
                var examQuestionsPool by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentExamQuestionIndex by remember { mutableIntStateOf(0) }

                var totalUsers by remember { mutableLongStateOf(0L) }
                var topSantriList by remember { mutableStateOf<List<SantriActivity>>(emptyList()) }
                var activityFeedError by remember { mutableStateOf<String?>(null) }
                var showUnitCompletionCelebration by remember { mutableStateOf<String?>(null) }

                // Auto-login sesi tersimpan
                LaunchedEffect(Unit) {
                    if (savedCode != null) {
                        repo.getUser(savedCode) { loadedUser ->
                            if (loadedUser != null) {
                                currentUser = repo.recordUserActivity(loadedUser, "masuk untuk belajar")
                            } else {
                                sharedPref.edit().clear().apply()
                            }
                        }
                    }
                }

                LaunchedEffect(currentScreen) {
                    if (currentScreen == Screen.AUTH) {
                        totalUsers = repo.getTotalRegisteredUsers()
                    }
                }

                DisposableEffect(currentScreen) {
                    val activityListener = if (currentScreen == Screen.AUTH) {
                        repo.listenRecentSantriActivities { activities, error ->
                            topSantriList = activities
                            activityFeedError = error
                        }
                    } else {
                        null
                    }
                    onDispose { activityListener?.remove() }
                }

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
                    color = Color(0xFF2B1D12)
                ) {
                    when (currentScreen) {
                        // 0. LAYAR INTRO / SPLASH VIDEO
                        Screen.SPLASH -> {
                            SplashVideoScreen(
                                onVideoFinished = {
                                    // Selesai video: jika sudah login masuk DASHBOARD, jika belum masuk AUTH
                                    currentScreen = if (savedCode != null && currentUser != null) {
                                        Screen.DASHBOARD
                                    } else if (savedCode != null) {
                                        Screen.DASHBOARD
                                    } else {
                                        Screen.AUTH
                                    }
                                }
                            )
                        }

                        // 1. LAYAR UTAMA: TURATSGO LANDING SCREEN
                        Screen.AUTH -> {
                            TuratsGoLandingScreen(
                                totalUsers = totalUsers,
                                topUsers = topSantriList,
                                activityFeedError = activityFeedError,
                                registeredAccessCode = savedCode,
                                onLoginWithCode = { kodeInput ->
                                    val cleanedCode = kodeInput.trim()
                                    if (cleanedCode.isBlank()) {
                                        Toast.makeText(currentContext, "Masukkan kode akses terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repo.getUser(cleanedCode) { user ->
                                            if (user != null) {
                                                sharedPref.edit().putString("saved_access_code", user.accessCode).apply()
                                                currentUser = repo.recordUserActivity(user, "masuk untuk belajar")
                                                currentScreen = Screen.DASHBOARD
                                                Toast.makeText(currentContext, "Ahlan wa Sahlan, ${user.name}! 👋", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(currentContext, "Kode akses tidak ditemukan di database", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                onRegisterNewUser = { name, province, city, phoneNumber, onResult ->
                                    val randomNum = (1000..9999).random()
                                    val generatedCode = "FDQ-$randomNum"
                                    val newUser = UserProfile(
                                        accessCode = generatedCode,
                                        name = name,
                                        city = city,
                                        province = province,
                                        phoneNumber = phoneNumber,
                                        deviceId = DeviceSupportHelper.getDeviceId(currentContext),
                                        totalXp = 0,
                                        currentStreak = 1,
                                        lastActivityAt = System.currentTimeMillis(),
                                        lastActivityMessage = "bergabung di TuratsGo"
                                    )
                                    repo.createUser(newUser) { result ->
                                        result.fold(
                                            onSuccess = { created ->
                                                if (created) {
                                                    sharedPref.edit().putString("saved_access_code", generatedCode).apply()
                                                    currentUser = newUser
                                                    onResult(Result.success(newUser))
                                                } else {
                                                    onResult(
                                                        Result.failure(
                                                            IllegalStateException("Kode akses bentrok. Silakan tekan Buat Akun lagi.")
                                                        )
                                                    )
                                                }
                                            },
                                            onFailure = { error ->
                                                onResult(Result.failure(error))
                                            }
                                        )
                                    }
                                },
                                onContinueAfterRegistration = {
                                    currentScreen = Screen.SELECT_BOOK
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
                                    },
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
                                                val activityAt = System.currentTimeMillis()
                                                val activityMessage = "mengerjakan soal: ${activeLesson.title}"
                                                repo.recordUserActivity(user, activityMessage)
                                                if (remainingHearts <= 0) {
                                                    Toast.makeText(currentContext, "Ujian Gagal. Coba lagi!", Toast.LENGTH_SHORT).show()
                                                    currentScreen = Screen.DASHBOARD
                                                } else if (isCorrect) {
                                                    if (currentExamQuestionIndex + 1 < examQuestionsPool.size) {
                                                        currentExamQuestionIndex++
                                                        currentActiveLessonId = examQuestionsPool[currentExamQuestionIndex].id
                                                    } else {
                                                        val targetUnitLessons = lessonsList.filter { it.unitTitle == activeLesson.unitTitle }.map { it.id }
                                                        val updated = user.copy(
                                                            completedLessons = (user.completedLessons + targetUnitLessons).distinct(),
                                                            totalXp = user.totalXp + 50,
                                                            lastActivityAt = activityAt,
                                                            lastActivityMessage = activityMessage
                                                        )
                                                        repo.registerUser(updated) {
                                                            currentUser = updated
                                                            showUnitCompletionCelebration = activeLesson.unitTitle
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
                                                repo.updateLessonResult(
                                                    user,
                                                    activeLesson.id,
                                                    isCorrect,
                                                    activeLesson.title
                                                ) { updatedUser ->
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
                                                            showUnitCompletionCelebration = activeLesson.unitTitle
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

// ==========================================
// 0. KOMPONEN INTRO VIDEO SPLASH
// ==========================================
@Composable
fun SplashVideoScreen(
    onVideoFinished: () -> Unit
) {
    val context = LocalContext.current
    var isFinished by remember { mutableStateOf(false) }

    // TIMER CADANGAN (FAIL-SAFE):
    // Jika video berdurasi misal 3-4 detik, paksa tunggu durasi tersebut agar tidak langsung tertutup
    LaunchedEffect(Unit) {
        delay(3500) // 3.5 detik (sesuaikan dengan panjang video Anda)
        if (!isFinished) {
            isFinished = true
            onVideoFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val videoUri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.splash_video}")
                    setVideoURI(videoUri)

                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        // Nonaktifkan audio jika ingin sunyi atau biarkan bersuara
                        start()
                    }

                    setOnCompletionListener {
                        if (!isFinished) {
                            isFinished = true
                            onVideoFinished()
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("SPLASH_VIDEO", "Video error code: $what, extra: $extra")
                        // Jangan langsung pindah instan, biarkan timer delay di atas yang mengurusnya
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tombol Lewati Manual
        TextButton(
            onClick = {
                if (!isFinished) {
                    isFinished = true
                    onVideoFinished()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Lewati ➔",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}


// ==========================================
// 1. LAYAR PILIH KELAS & KITAB
// ==========================================
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
            .background(CurriculumPalette.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = CurriculumPalette.Card,
                border = androidx.compose.foundation.BorderStroke(2.dp, CurriculumPalette.Border),
                shadowElevation = 4.dp,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", color = CurriculumPalette.Border, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CurriculumPalette.Teal),
                border = androidx.compose.foundation.BorderStroke(2.dp, CurriculumPalette.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🐪", fontSize = 36.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "🌟 JALUR KURIKULUM",
                            color = CurriculumPalette.Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Pilih Jenjang & Kitab",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = CurriculumPalette.Gold,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            "1",
                            color = CurriculumPalette.Text,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        "JENJANG / KELAS SANTRI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CurriculumPalette.Border
                    )
                }
                Text(
                    "Pilih salah satu",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CurriculumPalette.Teal,
                    modifier = Modifier
                        .background(CurriculumPalette.Teal.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (dbCurriculumMap.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CurriculumPalette.Card),
                    border = androidx.compose.foundation.BorderStroke(2.dp, CurriculumPalette.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Memuat kurikulum...",
                        color = CurriculumPalette.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                dbCurriculumMap.keys.forEach { levelKey ->
                    val isSelected = selectedLevelName == levelKey
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CurriculumPalette.Mint else CurriculumPalette.Card
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) CurriculumPalette.Green else CurriculumPalette.Sand
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 5.dp else 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedLevelName = levelKey
                                selectedBookTitle = dbCurriculumMap[levelKey]?.firstOrNull() ?: ""
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) CurriculumPalette.Green else Color(0xFFE6E2DD),
                                border = androidx.compose.foundation.BorderStroke(2.dp, CurriculumPalette.Border),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    levelKey,
                                    fontWeight = FontWeight.Bold,
                                    color = CurriculumPalette.Text,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "📖 ${dbCurriculumMap[levelKey]?.size} Kitab Tersedia",
                                    fontSize = 11.sp,
                                    color = CurriculumPalette.GreenDark
                                )
                            }
                            if (isSelected) Text("🌴", fontSize = 20.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = CurriculumPalette.Terracotta,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            "2",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        "DAFTAR KITAB & STATUS PROGRES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CurriculumPalette.Border
                    )
                }
                Text(
                    "Tersedia ${availableBooks.size} kitab",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CurriculumPalette.Muted
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            availableBooks.forEach { bookName ->
                val isChosen = selectedBookTitle == bookName
                val bookInfo = booksProgressList.firstOrNull { it.bookTitle.equals(bookName, ignoreCase = true) }
                    ?: BookProgressInfo(bookTitle = bookName)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChosen) CurriculumPalette.Mint else CurriculumPalette.Card
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isChosen) 5.dp else 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(
                            width = if (isChosen) 2.dp else 1.dp,
                            color = if (isChosen) CurriculumPalette.Green else CurriculumPalette.Sand,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedBookTitle = bookName }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFF1D5),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0D49A))
                            ) {
                                Text(
                                    text = "🏷️ ${bookInfo.fieldType.uppercase()}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF795012),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }

                            Surface(
                                onClick = { selectedBookForInfo = bookInfo },
                                shape = RoundedCornerShape(9.dp),
                                color = CurriculumPalette.Teal.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    CurriculumPalette.Teal.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("i", color = CurriculumPalette.Teal, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isChosen) Color(0xFFDDF2E9) else Color(0xFFF2E8DA),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        CurriculumPalette.Sand.copy(alpha = 0.8f)
                                    ),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📖", fontSize = 19.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = bookName,
                                    fontWeight = FontWeight.Bold,
                                    color = CurriculumPalette.Text,
                                    fontSize = 15.sp
                                )
                            }

                            when {
                                bookInfo.isCompleted -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CurriculumPalette.Green,
                                        shadowElevation = 2.dp
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
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1687B8),
                                        shadowElevation = 2.dp
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
                                        color = CurriculumPalette.GreenDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { bookToPrintCertificate = bookInfo },
                            enabled = bookInfo.isCompleted,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CurriculumPalette.Gold,
                                disabledContainerColor = Color(0xFFEDEAE5)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (bookInfo.isCompleted) Color(0xFFC7830F) else Color(0xFFD9D3CA)
                            ),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text(
                                text = if (bookInfo.isCompleted) "📜 Unduh Sertifikat Kelulusan" else "🔒 Selesaikan Kitab untuk Buka Sertifikat",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bookInfo.isCompleted) CurriculumPalette.Text else Color(0xFF8C8984)
                            )
                        }
                    }
                }
            }
        }

        Surface(
            color = CurriculumPalette.Background,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = { onConfirm(selectedLevelName, selectedBookTitle) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, CurriculumPalette.Border),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CurriculumPalette.Terracotta),
                    enabled = selectedBookTitle.isNotBlank()
                ) {
                    Text("LANJUTKAN KE JALUR BELAJAR ➔", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }

    selectedBookForInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { selectedBookForInfo = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = CurriculumPalette.Card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(info.bookTitle, color = CurriculumPalette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Disiplin Ilmu:", fontSize = 11.sp, color = CurriculumPalette.Muted)
                        Text("📚 ${info.fieldType}", color = CurriculumPalette.Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column {
                        Text("Pengarang Kitab:", fontSize = 11.sp, color = CurriculumPalette.Muted)
                        Text("✍️ ${info.author}", color = CurriculumPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Column {
                        Text("Ringkasan Informasi / Sinopsis:", fontSize = 11.sp, color = CurriculumPalette.Muted)
                        Text(text = info.description, color = CurriculumPalette.Text, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    HorizontalDivider(color = CurriculumPalette.Sand)
                    Text("Total Pelajaran: ${info.totalCount} Soal/Hadits", color = CurriculumPalette.Teal, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBookForInfo = null }) {
                    Text("Tutup", color = CurriculumPalette.Teal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    bookToPrintCertificate?.let { certBook ->
        AlertDialog(
            onDismissRequest = { bookToPrintCertificate = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = CurriculumPalette.Card,
            title = {
                Text("🎓 Sertifikat Tamat Belajar", color = CurriculumPalette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = CurriculumPalette.Green)
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

// ==========================================
// 2. DASBOR PADANG PASIR & OASE (DESERT THEME)
// ==========================================
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DesertOasisPalette.HeaderBrownDark,
                                DesertOasisPalette.HeaderBrownMid
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, DesertOasisPalette.GoldBorder, Color.Transparent)
                        ),
                        shape = RectangleShape
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF2D180C).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder.copy(alpha = 0.7f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { onOpenSelectBook() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = user.selectedBookTitle.ifBlank { "Pilih Kitab" },
                            color = Color(0xFFFEF3C7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9600).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${user.currentStreak}", color = Color(0xFFFFB74D), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💎", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${user.totalXp}", color = Color(0xFF7DD3FC), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }

                var isMutedDashboard by remember { mutableStateOf(SoundManager.isMuted) }
                Surface(
                    onClick = { isMutedDashboard = SoundManager.toggleMute(context) },
                    shape = CircleShape,
                    color = Color(0xFF2D180C).copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isMutedDashboard) "🔇" else "🔊", fontSize = 15.sp)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF4A2816),
                modifier = Modifier.border(
                    width = 2.dp,
                    color = DesertOasisPalette.GoldBorder.copy(alpha = 0.8f),
                    shape = RectangleShape
                )
            ) {
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 0,
                    onClick = { selectedBottomNavIndex = 0 },
                    icon = { Text("🕌", fontSize = 20.sp) },
                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedBottomNavIndex == 0) Color(0xFFFDE68A) else Color(0xFFD4A373)) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 1,
                    onClick = { selectedBottomNavIndex = 1 },
                    icon = { Text("🎯", fontSize = 20.sp) },
                    label = { Text("Target", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedBottomNavIndex == 1) Color(0xFFFDE68A) else Color(0xFFD4A373)) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 2,
                    onClick = { selectedBottomNavIndex = 2 },
                    icon = { Text("🏆", fontSize = 20.sp) },
                    label = { Text("Peringkat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedBottomNavIndex == 2) Color(0xFFFDE68A) else Color(0xFFD4A373)) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 3,
                    onClick = { selectedBottomNavIndex = 3 },
                    icon = { Text("👳🏽‍♂️", fontSize = 20.sp) },
                    label = { Text("Profil", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedBottomNavIndex == 3) Color(0xFFFDE68A) else Color(0xFFD4A373)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DesertOasisPalette.SandSkyTop,
                            DesertOasisPalette.SandDuneLight,
                            DesertOasisPalette.SandDuneDark
                        )
                    )
                )
                .padding(padding)
        ) {
            when (selectedBottomNavIndex) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Card(
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = DesertOasisPalette.OasisGreenTop),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF9CE85C)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTableOfContentsDialog = true }
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                DesertOasisPalette.OasisGreenTop,
                                                DesertOasisPalette.OasisGreenBottom
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFA8F06C).copy(alpha = 0.8f)
                                        ) {
                                            Text(
                                                text = (activeLesson?.unitTitle ?: "UNIT 1").uppercase(),
                                                color = Color(0xFF1E4803),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${user.selectedBookTitle}: ${activeLesson?.chapterTitle ?: "Materi Utama"}",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF388E3C),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8EE04E)),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text("📋", fontSize = 20.sp) }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DesertOasisPalette.OasisGreenDark.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8BE04B).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("💧", fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Progres Air: $waterPercent% ($completedCount/$totalCount Hadits)",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0284C7).copy(alpha = 0.4f)
                                            ) {
                                                Text(
                                                    "Oase Aktif",
                                                    color = Color(0xFFE0F2FE),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        LinearProgressIndicator(
                                            progress = { waterPercent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = DesertOasisPalette.WaterBlue,
                                            trackColor = Color(0xFF1B3F02)
                                        )
                                    }
                                }
                            }
                        }

                        if (regularLessons.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Materi kitab ini belum tersedia di database Firestore.",
                                    color = DesertOasisPalette.DesertText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                allowedUnits.forEachIndexed { unitIdx, unitKey ->
                                    val unitItems = unitsGrouped[unitKey] ?: emptyList()

                                    if (unitIdx > 0) {
                                        item {
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color(0xFF4A2816).copy(alpha = 0.85f),
                                                border = androidx.compose.foundation.BorderStroke(1.5.dp, DesertOasisPalette.GoldBorder),
                                                shadowElevation = 4.dp,
                                                modifier = Modifier.padding(horizontal = 20.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("🌴", fontSize = 22.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Alhamdulillah! Gerbang Oase $unitKey",
                                                        color = Color(0xFFFEF3C7),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
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
                                            0 -> (-45).dp
                                            1 -> 0.dp
                                            2 -> 45.dp
                                            else -> 0.dp
                                        }

                                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                        val pulseScale by infiniteTransition.animateFloat(
                                            initialValue = 1f,
                                            targetValue = if (isCurrent) 1.08f else 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1100, easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "scale"
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = horizontalOffset)
                                                    .scale(pulseScale)
                                                    .wrapContentSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    val mainColor = when {
                                                        isCompleted -> Color(0xFF58CC02)
                                                        isFailed -> Color(0xFFFFC800)
                                                        isCurrent -> Color(0xFFF59E0B)
                                                        else -> Color(0xFF8C7355)
                                                    }
                                                    val bottomShadowColor = when {
                                                        isCompleted -> Color(0xFF388E3C)
                                                        isFailed -> Color(0xFFC7830F)
                                                        isCurrent -> Color(0xFFB45309)
                                                        else -> Color(0xFF5A4632)
                                                    }

                                                    Surface(
                                                        shape = CircleShape,
                                                        color = bottomShadowColor,
                                                        shadowElevation = if (isCurrent) 8.dp else 4.dp,
                                                        modifier = Modifier
                                                            .size(if (isCurrent) 76.dp else 64.dp)
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
                                                            modifier = Modifier
                                                                .padding(bottom = 6.dp)
                                                                .fillMaxSize()
                                                                .clip(CircleShape)
                                                                .background(mainColor)
                                                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
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

                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFFFF8EB).copy(alpha = 0.9f),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder.copy(alpha = 0.6f)),
                                                        shadowElevation = 2.dp
                                                    ) {
                                                        Text(
                                                            text = lesson.title.ifBlank { "Pos ${globalIdx + 1}" },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = DesertOasisPalette.DesertText,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(70.dp)) }
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

    // POP-UP REDO SOAL
    lessonToConfirmRedo?.let { lesson ->
        AlertDialog(
            onDismissRequest = { lessonToConfirmRedo = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF3B2010),
            title = { Text("🔄 Kerjakan Ulang Soal?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = { Text(text = "Soal \"${lesson.title}\" ini sudah pernah kamu selesaikan. Apakah kamu yakin ingin mengerjakannya ulang?", color = Color(0xFFFDE68A), fontSize = 13.sp) },
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
            dismissButton = { TextButton(onClick = { lessonToConfirmRedo = null }) { Text("Batal", color = Color.LightGray) } }
        )
    }

    // POP-UP DAFTAR ISI ACCORDION
    if (showTableOfContentsDialog) {
        AlertDialog(
            onDismissRequest = { showTableOfContentsDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF3B2010),
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

                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isChapterOpen) Color(0xFF4A2816) else Color(0xFF2D180C)), modifier = Modifier.fillMaxWidth()) {
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
                                    HorizontalDivider(color = Color(0xFF5A3825))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val subUnits = chapterItems.groupBy { it.unitTitle.ifBlank { "Unit 1" } }
                                    subUnits.forEach { (unitName, unitLessons) ->
                                        val isUnitOpen = expandedUnitKey == unitName
                                        val unitDone = unitLessons.count { user.completedLessons.contains(it.id) }

                                        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (isUnitOpen) Color(0xFF5A3118) else Color(0xFF3D2110)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable { expandedUnitKey = if (isUnitOpen) null else unitName },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(unitName, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                        Text("$unitDone/${unitLessons.size} Butir Materi", color = Color.LightGray, fontSize = 10.sp)
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

    // POP-UP BYPASS EXAM
    showBypassExamDialog?.let { targetUnit ->
        AlertDialog(
            onDismissRequest = { showBypassExamDialog = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF3B2010),
            title = { Text("⚡ Ujian Loncat ke $targetUnit?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ikuti ujian kenaikan untuk langsung menerobos unit ini tanpa mengerjakan bab sebelumnya!", color = Color(0xFFFDE68A), fontSize = 13.sp)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF5A2518))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚠️ KETENTUAN UJIAN KENAIKAN:", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            dismissButton = { TextButton(onClick = { showBypassExamDialog = null }) { Text("Nanti Saja", color = Color.LightGray) } }
        )
    }

    showUnitCelebrationInDashboard?.let { completedUnit ->
        UnitCompletionCelebrationDialog(
            unitTitle = completedUnit,
            onDismiss = onDismissCelebration
        )
    }
}

// ==========================================
// 3. TARGET TAB
// ==========================================
data class ChallengeItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val targetType: String = "STREAK",
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
                    ChallengeItem("2", "Kumpulkan 100 Poin Hikmah", "Selesaikan materi dan kuis", "XP", 100, 25, "💎")
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
        Text("Target & Misi Harian 🎯", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = DesertOasisPalette.DesertText)
        Text("Selesaikan misi untuk mengisi sumur oase ilmu:", fontSize = 12.sp, color = Color(0xFF6B4226))

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
                    containerColor = if (isDone) Color(0xFFE8F5E9) else Color(0xFFFFF8EB)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DesertOasisPalette.GoldBorder),
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
                            Text(challenge.title, fontWeight = FontWeight.Bold, color = DesertOasisPalette.DesertText, fontSize = 14.sp)
                        }
                        Text(
                            text = "+${challenge.rewardXp} XP ⭐",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(challenge.description, color = Color(0xFF7C4D2B), fontSize = 11.sp)
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
                            color = if (isDone) Color(0xFF2E7D32) else Color(0xFFD97706)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (isDone) Color(0xFF2E7D32) else Color(0xFFD97706),
                        trackColor = Color(0xFFF3D6B5)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

// ==========================================
// 4. LEADERBOARD TAB
// ==========================================
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
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2816)),
            border = androidx.compose.foundation.BorderStroke(2.dp, DesertOasisPalette.GoldBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🏆", fontSize = 32.sp)
                Text("Liga Kafilah Madinah", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFFEF3C7))
                Text("3 Penuntut Ilmu teratas naik ke Liga Makkah!", fontSize = 11.sp, color = Color(0xFFD4A373))
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFB45309))
            }
        } else if (rankingsList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EB)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada santri yang terdaftar di database.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            rankingsList.forEachIndexed { rank, santri ->
                val isCurrentUser = santri.accessCode == user.accessCode
                val cardBorder = if (isCurrentUser) Color(0xFF2E7D32) else DesertOasisPalette.GoldBorder.copy(alpha = 0.5f)
                val cardBg = if (isCurrentUser) Color(0xFFE8F5E9) else Color(0xFFFFF8EB)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (isCurrentUser) 2.dp else 1.dp, cardBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (rank) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "${rank + 1}"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DesertOasisPalette.DesertText
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = santri.avatar.ifBlank { "👳‍♂️" }, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = santri.name.ifBlank { "Santri Funduq" },
                                    fontWeight = FontWeight.Bold,
                                    color = DesertOasisPalette.DesertText,
                                    fontSize = 13.sp
                                )
                                if (isCurrentUser) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF2E7D32)
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
                                color = Color(0xFF7C4D2B)
                            )
                        }
                        Text(
                            text = "💧 ${santri.totalXp}",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0284C7),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

// ==========================================
// 5. PROFIL TAB
// ==========================================
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFDE68A),
                border = androidx.compose.foundation.BorderStroke(3.dp, DesertOasisPalette.HeaderBrownDark),
                shadowElevation = 4.dp,
                modifier = Modifier.size(90.dp).clickable { showAvatarPicker = true }
            ) {
                Box(contentAlignment = Alignment.Center) { Text(user.avatar.ifBlank { "👳‍♂️" }, fontSize = 46.sp) }
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFF58CC02),
                modifier = Modifier.size(28.dp).clickable { showAvatarPicker = true }
            ) {
                Box(contentAlignment = Alignment.Center) { Text("✏️", fontSize = 14.sp) }
            }
        }

        Text(text = "Klik foto untuk ganti avatar 📷", fontSize = 11.sp, color = DesertOasisPalette.DesertText, modifier = Modifier.clickable { showAvatarPicker = true })
        Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DesertOasisPalette.DesertText)
        Text("Kota: ${user.city} • Tingkat: ${user.selectedLevelId}", fontSize = 13.sp, color = Color(0xFF6B4226))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EB)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 20.sp)
                    Text("${user.currentStreak} Hari", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DesertOasisPalette.DesertText)
                    Text("Streak", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EB)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💧", fontSize = 20.sp)
                    Text("${user.completedLessons.size}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DesertOasisPalette.DesertText)
                    Text("Hadits", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EB)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesertOasisPalette.GoldBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💎", fontSize = 20.sp)
                    Text("${user.totalXp}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DesertOasisPalette.DesertText)
                    Text("Permata XP", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EB)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, DesertOasisPalette.GoldBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("KODE AKSES LOGIN PRIBADI:", fontSize = 11.sp, color = Color(0xFF7C4D2B), fontWeight = FontWeight.SemiBold)
                Text(user.accessCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gunakan kode ini jika ingin berpindah ke perangkat HP lain.", fontSize = 11.sp, color = Color(0xFF5A3825))
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF81C784)),
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

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Keluar dari Akun (Logout)", fontWeight = FontWeight.Bold)
        }
    }

    if (showAvatarPicker) {
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF3B2010),
            title = { Text("Pilih Karakter Santri 👳‍♂️", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pilih avatar yang mencerminkan semangat belajarmu:", color = Color(0xFFFDE68A), fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        avatarOptions.take(5).forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = if (user.avatar == emoji) Color(0xFF58CC02) else Color(0xFF2D180C),
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
                                color = if (user.avatar == emoji) Color(0xFF58CC02) else Color(0xFF2D180C),
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
// 6. POP-UP SELEBRASI KHATAM UNIT
// ==========================================
@Composable
fun UnitCompletionCelebrationDialog(
    unitTitle: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF3B2010),
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
                    color = Color(0xFFFDE68A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2E6F1E)
                ) {
                    Text(
                        text = "🎁 Bonus Khatam: +50 XP & Progres Air Terisi! 💧",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8F06C),
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
