package com.funduq.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

enum class Screen { AUTH, SELECT_BOOK, DASHBOARD, QUIZ }

class MainActivity : ComponentActivity() {
    private val repo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("funduq_session", MODE_PRIVATE)
        val savedCode = sharedPref.getString("saved_access_code", null)

        setContent {
            FunduqAppTheme {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf(if (savedCode != null) Screen.DASHBOARD else Screen.AUTH) }
                var currentUser by remember { mutableStateOf<UserProfile?>(null) }

                // State Materi 100% Real-time Firestore
                var lessonsList by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentActiveLessonId by remember { mutableStateOf("") }
                var isBypassExamActive by remember { mutableStateOf(false) }
                var currentHearts by remember { mutableIntStateOf(3) }
                var examQuestionsPool by remember { mutableStateOf<List<Lesson>>(emptyList()) }
                var currentExamQuestionIndex by remember { mutableIntStateOf(0) }

                // Auto-login
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

                // Ambil materi HANYA untuk kitab yang sedang dipilih santri
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
                        Screen.AUTH -> {
                            AuthScreen(
                                onLoginSuccess = { userDraft ->
                                    repo.registerUser(userDraft) { success ->
                                        if (success) {
                                            sharedPref.edit().putString("saved_access_code", userDraft.accessCode).apply()
                                            currentUser = userDraft
                                            currentScreen = Screen.SELECT_BOOK
                                        } else {
                                            Toast.makeText(context, "Gagal koneksi database", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        // LAYAR PILIH KELAS & KITAB (HANYA MENGAMBIL APA YANG ADA DI FIRESTORE)
                        Screen.SELECT_BOOK -> {
                            currentUser?.let { user ->
                                SelectBookPureDatabaseScreen(
                                    currentLevel = user.selectedLevelId,
                                    currentBook = user.selectedBookTitle,
                                    repo = repo,
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

                        Screen.DASHBOARD -> {
                            currentUser?.let { user ->
                                DashboardScreen(
                                    user = user,
                                    lessons = lessonsList,
                                    onOpenSelectBook = { currentScreen = Screen.SELECT_BOOK },
                                    onStartSpecificLesson = { lessonId ->
                                        currentActiveLessonId = lessonId
                                        isBypassExamActive = false
                                        currentHearts = 3
                                        currentScreen = Screen.QUIZ
                                    },
                                    onStartBypassExam = { targetUnitTitle ->
                                        // Cari soal ujian berdasarkan flag isExam ATAU judul yang mengandung kata "Ujian"
                                        val examQuestions = lessonsList.filter {
                                            (it.unitTitle.equals(targetUnitTitle, ignoreCase = true) || it.chapterTitle.contains("Ujian", ignoreCase = true)) &&
                                                    (it.isExam || it.title.contains("Ujian", ignoreCase = true))
                                        }.ifEmpty {
                                            // Fallback: Jika tidak ada flag khusus, ambil 3-5 materi dari unit tersebut untuk dijadikan ujian
                                            lessonsList.filter { it.unitTitle.equals(targetUnitTitle, ignoreCase = true) }.take(5)
                                        }.ifEmpty {
                                            // Fallback terakhir: Ambil 3 soal acak dari kitab ini
                                            lessonsList.shuffled().take(3)
                                        }

                                        if (examQuestions.isNotEmpty()) {
                                            examQuestionsPool = examQuestions
                                            currentExamQuestionIndex = 0
                                            currentActiveLessonId = examQuestions.first().id
                                            isBypassExamActive = true
                                            currentHearts = 3
                                            currentScreen = Screen.QUIZ
                                        } else {
                                            Toast.makeText(context, "Materi kitab ini belum tersedia di database", Toast.LENGTH_SHORT).show()
                                        }
                                    },

                                    onLogout = {
                                        sharedPref.edit().clear().apply()
                                        currentUser = null
                                        currentScreen = Screen.AUTH
                                    }
                                )
                            }
                        }

                        // LAYAR KUIS DENGAN LOGIKA CERDAS: LANJUT KE SOAL YANG BELUM / MASIH SALAH
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
                                                // MODE UJIAN LONCAT
                                                if (remainingHearts <= 0) {
                                                    Toast.makeText(context, "Ujian Gagal (3 Hati Habis). Silakan coba lagi besok!", Toast.LENGTH_LONG).show()
                                                    currentScreen = Screen.DASHBOARD
                                                } else if (isCorrect) {
                                                    if (currentExamQuestionIndex + 1 < examQuestionsPool.size) {
                                                        currentExamQuestionIndex++
                                                        currentActiveLessonId = examQuestionsPool[currentExamQuestionIndex].id
                                                        Toast.makeText(context, "Benar! Soal Ujian ${currentExamQuestionIndex + 1} dari ${examQuestionsPool.size} 📝", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        // Lulus Ujian -> Buka semua soal di unit ini
                                                        val targetUnitLessons = lessonsList.filter { it.unitTitle == activeLesson.unitTitle }.map { it.id }
                                                        val updated = user.copy(
                                                            completedLessons = (user.completedLessons + targetUnitLessons).distinct(),
                                                            totalXp = user.totalXp + 50
                                                        )
                                                        repo.registerUser(updated) {
                                                            currentUser = updated
                                                            Toast.makeText(context, "LULUS UJIAN! Seluruh Soal Unit Terbuka! 🔓🎉", Toast.LENGTH_LONG).show()
                                                            currentScreen = Screen.DASHBOARD
                                                        }
                                                    }
                                                } else {
                                                    if (remainingHearts > 0 && currentExamQuestionIndex + 1 < examQuestionsPool.size) {
                                                        currentExamQuestionIndex++
                                                        currentActiveLessonId = examQuestionsPool[currentExamQuestionIndex].id
                                                        Toast.makeText(context, "Jawaban salah (Sisa $remainingHearts Hati). Lanjut ke soal berikutnya.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Hati habis. Ujian gagal!", Toast.LENGTH_LONG).show()
                                                        currentScreen = Screen.DASHBOARD
                                                    }
                                                }
                                            } else {
                                                // MODE KUIS REGULER: LANJUT HANYA KE SOAL YANG BELUM / MASIH KUNING
                                                repo.updateLessonResult(user, activeLesson.id, isCorrect) { updatedUser ->
                                                    currentUser = updatedUser
                                                    if (isCorrect) {
                                                        val regularLessons = lessonsList.filter { !it.isExam }
                                                        // Cari soal berikutnya di unit ini yang BELUM DIKERJAKAN atau MASIH SALAH (KUNING)
                                                        val nextUnfinishedLesson = regularLessons.firstOrNull { item ->
                                                            (!updatedUser.completedLessons.contains(item.id) || updatedUser.failedLessons.contains(item.id)) && item.id != activeLesson.id
                                                        }

                                                        if (nextUnfinishedLesson != null) {
                                                            currentActiveLessonId = nextUnfinishedLesson.id
                                                            Toast.makeText(context, "+25 XP! Lanjut ke soal berikutnya 🔥", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Alhamdulillah! Seluruh materi unit ini telah tuntas! 🏆", Toast.LENGTH_LONG).show()
                                                            currentScreen = Screen.DASHBOARD
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "-5 XP. Pos menjadi kuning untuk diulang ⚠️", Toast.LENGTH_LONG).show()
                                                        currentScreen = Screen.DASHBOARD
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. LAYAR PILIH KELAS & KITAB (100% DINAMIS DARI DATABASE)
// ==========================================
@Composable
fun SelectBookPureDatabaseScreen(
    currentLevel: String,
    currentBook: String,
    repo: FirebaseRepository,
    onConfirm: (level: String, book: String) -> Unit,
    onBack: () -> Unit
) {
    var dbCurriculumMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var selectedLevelName by remember { mutableStateOf(currentLevel) }
    var selectedBookTitle by remember { mutableStateOf(currentBook) }

    LaunchedEffect(Unit) {
        repo.getAvailableCurriculumSummary { map ->
            dbCurriculumMap = map
            // Pilih otomatis level pertama yang ada di database jika belum dipilih
            if (selectedLevelName.isBlank() || !map.containsKey(selectedLevelName)) {
                selectedLevelName = map.keys.firstOrNull() ?: ""
            }
            if (selectedBookTitle.isBlank() && selectedLevelName.isNotBlank()) {
                selectedBookTitle = map[selectedLevelName]?.firstOrNull() ?: ""
            }
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🐪", fontSize = 52.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36))) {
                Text("Pilih Kelas & Kitab yang tersedia di database:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("1. KELAS TERSEDIA DI DATABASE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(8.dp))

        if (dbCurriculumMap.isEmpty()) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)), modifier = Modifier.fillMaxWidth()) {
                Text("Memuat data kurikulum dari Firestore...", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        } else {
            // HANYA MENAMPILKAN KELAS YANG BENAR-BENAR ADA DI DATABASE FIRESTORE
            dbCurriculumMap.keys.forEach { levelKey ->
                val isSelected = selectedLevelName == levelKey
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF202F36)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
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

        Spacer(modifier = Modifier.height(20.dp))
        Text("2. KITAB TERSEDIA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(8.dp))

        availableBooks.forEach { bookName ->
            val isChosen = selectedBookTitle == bookName
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isChosen) Color(0xFF2E7D32) else Color(0xFF202F36)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selectedBookTitle = bookName }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(bookName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (isChosen) Text("✓ Terpilih", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
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
}

// ==========================================
// 2. DASBOR DENGAN STICKY HEADER & POP-UP KONFIRMASI RESET ULANG
// ==========================================
@Composable
fun DashboardScreen(
    user: UserProfile,
    lessons: List<Lesson>,
    onOpenSelectBook: () -> Unit,
    onStartSpecificLesson: (lessonId: String) -> Unit,
    onStartBypassExam: (targetUnitTitle: String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedBottomNavIndex by remember { mutableIntStateOf(0) }
    var showTableOfContentsDialog by remember { mutableStateOf(false) }
    var showBypassExamDialog by remember { mutableStateOf<String?>(null) }
    var lessonToConfirmRedo by remember { mutableStateOf<Lesson?>(null) } // Pop-up konfirmasi ulang

    val regularLessons = remember(lessons) { lessons.filter { !it.isExam } }
    val totalCount = regularLessons.size.coerceAtLeast(1)
    val completedCount = regularLessons.count { user.completedLessons.contains(it.id) }
    val waterPercent = ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt().coerceIn(0, 100)

    val activeLessonIndex = regularLessons.indexOfFirst { lesson ->
        !user.completedLessons.contains(lesson.id)
    }.let { if (it == -1) regularLessons.size - 1 else it }

    val activeLesson = regularLessons.getOrNull(activeLessonIndex)
    val listState = rememberLazyListState()

    // Auto-scroll ke soal terakhir yang sedang dikerjakan
    LaunchedEffect(activeLessonIndex, lessons) {
        if (activeLessonIndex > 0) {
            listState.animateScrollToItem((activeLessonIndex - 1).coerceAtLeast(0))
        }
    }

    val unitsGrouped = remember(regularLessons) { regularLessons.groupBy { it.unitTitle.ifBlank { "Unit 1" } } }
    val allUnitKeys = unitsGrouped.keys.toList()

    // Batasi scroll maksimal ke 1 Unit berikutnya yang terkunci
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
                        // STICKY HEADER TETAP DI ATAS
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
                                    Text(
                                        text = (activeLesson?.unitTitle ?: "UNIT 1").uppercase(),
                                        color = Color(0xFF265700),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${user.selectedBookTitle}: ${activeLesson?.chapterTitle ?: "Materi Utama"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
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
                                                                    // Pop-up konfirmasi ulang jika sudah tuntas
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
                2 -> LeaderboardTab()
                3 -> ProfileTab(user, onLogout)
            }
        }
    }

    // ==========================================
    // POP-UP 1: KONFIRMASI MENGERJAKAN ULANG SOAL HIJAU
    // ==========================================
    lessonToConfirmRedo?.let { lesson ->
        AlertDialog(
            onDismissRequest = { lessonToConfirmRedo = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = {
                Text("🔄 Kerjakan Ulang Soal?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Soal \"${lesson.title}\" ini sudah pernah kamu selesaikan. Apakah kamu yakin ingin mengerjakannya ulang? Mengerjakan ulang akan mereset status hijau di soal ini.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = lesson.id
                        lessonToConfirmRedo = null
                        onStartSpecificLesson(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ya, Kerjakan Ulang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { lessonToConfirmRedo = null }) { Text("Batal", color = Color.Gray) }
            }
        )
    }

    // ==========================================
    // POP-UP 2: DAFTAR ISI DENGAN ACCORDION LENGKAP
    // ==========================================
    if (showTableOfContentsDialog) {
        AlertDialog(
            onDismissRequest = { showTableOfContentsDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = {
                Text("📋 Daftar Isi: ${user.selectedBookTitle}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            },
            text = {
                val chaptersGrouped = remember(regularLessons) { regularLessons.groupBy { it.chapterTitle.ifBlank { "Bab Utama" } } }

                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chaptersGrouped.forEach { (chapterName, chapterItems) ->
                        var isChapterExpanded by remember { mutableStateOf(true) }
                        val chapDone = chapterItems.count { user.completedLessons.contains(it.id) }
                        val chapPercent = (chapDone * 100) / chapterItems.size.coerceAtLeast(1)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131F24)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { isChapterExpanded = !isChapterExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(chapterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("💧 $chapPercent% ($chapDone/${chapterItems.size} Soal)", color = Color(0xFF81C784), fontSize = 11.sp)
                                    }
                                    Text(if (isChapterExpanded) "▲" else "▼", color = Color.Gray, fontSize = 16.sp)
                                }

                                if (isChapterExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val subUnits = chapterItems.groupBy { it.unitTitle.ifBlank { "Unit 1" } }

                                    subUnits.forEach { (unitName, unitLessons) ->
                                        var isUnitExpanded by remember { mutableStateOf(true) }

                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable { isUnitExpanded = !isUnitExpanded },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(unitName, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    Text(if (isUnitExpanded) "▲" else "▼", color = Color.LightGray, fontSize = 12.sp)
                                                }

                                                if (isUnitExpanded) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    unitLessons.forEach { lesson ->
                                                        val isDone = user.completedLessons.contains(lesson.id)
                                                        val isFailed = user.failedLessons.contains(lesson.id)
                                                        val isLock = !isDone && !isFailed && regularLessons.indexOf(lesson) > activeLessonIndex

                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp)
                                                                .clickable {
                                                                    showTableOfContentsDialog = false
                                                                    if (isLock) showBypassExamDialog = lesson.unitTitle
                                                                    else if (isDone) lessonToConfirmRedo = lesson
                                                                    else onStartSpecificLesson(lesson.id)
                                                                },
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(if (isDone) "✓" else if (isFailed) "⚠️" else if (isLock) "🔒" else "💧", fontSize = 13.sp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(lesson.title, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
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
            confirmButton = {
                TextButton(onClick = { showTableOfContentsDialog = false }) { Text("Tutup", color = Color(0xFF58CC02), fontWeight = FontWeight.Bold) }
            }
        )
    }

    // ==========================================
    // POP-UP 3: UJIAN LONCAT BAB (BYPASS TEST)
    // ==========================================
    showBypassExamDialog?.let { targetUnit ->
        AlertDialog(
            onDismissRequest = { showBypassExamDialog = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF1E2D33),
            title = {
                Text("⚡ Ujian Loncat ke $targetUnit?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ikuti ujian kenaikan untuk langsung menerobos unit ini tanpa mengerjakan bab sebelumnya!", color = Color.LightGray, fontSize = 13.sp)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C20))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚠️ KETENTUAN UJIAN KENAIKAN:", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• Berisi 3-5 Pertanyaan Ujian Evaluasi", color = Color.White, fontSize = 12.sp)
                            Text("• Dibekali 3 Hati (❤️❤️❤️)", color = Color.White, fontSize = 12.sp)
                            Text("• Jika 3 hati habis, kamu harus menunggu reset 24 jam!", color = Color(0xFFFFAB91), fontSize = 11.sp)
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
                ) {
                    Text("Mulai Ujian 3 Hati 🚀", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBypassExamDialog = null }) { Text("Nanti Saja", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun TargetsTab(user: UserProfile) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Target & Misi Harian 🎯", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔥 Istiqomah 10 Hari Streak", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Progres Anda saat ini: ${user.currentStreak}/10 Hari", fontSize = 12.sp, color = Color(0xFFFF9600))
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (user.currentStreak / 10f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFFFF9600), trackColor = Color(0xFF37474F))
            }
        }
    }
}

@Composable
fun LeaderboardTab() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Papan Peringkat Santri 🏆", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        val sampleRankings = listOf(Triple("Ahmad Syarif", "Bandung", 1450), Triple("Muhammad Rizky", "Surabaya", 1280), Triple("Santri Funduq", "Indonesia", 60))
        sampleRankings.forEachIndexed { rank, (name, city, xp) ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (rank == 0) Color(0xFF2E7D32) else Color(0xFF202F36)), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (rank) { 0 -> "🥇"; 1 -> "🥈"; else -> "#${rank + 1}" }, fontSize = 20.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text("Asal: $city", fontSize = 12.sp, color = Color.LightGray)
                    }
                    Text("⭐ $xp XP", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD54F))
                }
            }
        }
    }
}

@Composable
fun ProfileTab(user: UserProfile, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = Color(0xFF58CC02), modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("👳‍♂️", fontSize = 42.sp) }
        }
        Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Kota: ${user.city} • Tingkat: ${user.selectedLevelId}", fontSize = 13.sp, color = Color.LightGray)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("KODE AKSES LOGIN PRIBADI:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Text(user.accessCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF58CC02))
            }
        }
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Keluar dari Akun (Logout)", fontWeight = FontWeight.Bold)
        }
    }
}
