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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
                var activeLesson by remember { mutableStateOf<Lesson?>(null) }
                var lessonsList by remember { mutableStateOf<List<Lesson>>(emptyList()) }

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

                // Ambil materi kitab aktif
                LaunchedEffect(currentScreen, currentUser?.selectedBookTitle) {
                    if (currentScreen == Screen.DASHBOARD) {
                        repo.getLessons { fetched ->
                            if (fetched.isNotEmpty()) {
                                lessonsList = fetched
                            } else {
                                lessonsList = listOf(
                                    Lesson(
                                        id = "arbain_01",
                                        levelCategory = "Yafi'",
                                        fieldType = "Hadits",
                                        bookTitle = currentUser?.selectedBookTitle ?: "Arbain Nawawi",
                                        chapterTitle = "Bab 1: Niat dan Keikhlasan",
                                        unitTitle = "BAGIAN 1, UNIT 1",
                                        order = 1,
                                        title = "Hadits 1: Niat Amalan",
                                        arabicText = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
                                        translation = "Sesungguhnya setiap perbuatan tergantung niatnya...",
                                        explanation = "Niat menjadi penentu utama sah atau tidaknya amal ibadah.",
                                        question = "Apa penentu utama sah dan diterimanya amal ibadah?",
                                        options = listOf("Pujian Manusia", "Niat yang Ikhlas", "Kecepatan Waktu", "Banyaknya Harta"),
                                        correctIndex = 1,
                                        rewardXp = 25
                                    ),
                                    Lesson(
                                        id = "arbain_02",
                                        levelCategory = "Yafi'",
                                        fieldType = "Hadits",
                                        bookTitle = currentUser?.selectedBookTitle ?: "Arbain Nawawi",
                                        chapterTitle = "Bab 2: Islam, Iman, dan Ihsan",
                                        unitTitle = "BAGIAN 1, UNIT 2",
                                        order = 2,
                                        title = "Hadits 2: Rukun Islam",
                                        arabicText = "بَيْنَمَا نَحْنُ عِنْدَ رَسُولِ اللَّهِ صلى الله عليه وسلم...",
                                        translation = "Ketika kami sedang bersama Rasulullah SAW...",
                                        explanation = "Tiga pilar pokok agama Islam.",
                                        question = "Siapakah sosok yang datang menguji pemahaman para sahabat?",
                                        options = listOf("Malaikat Jibril", "Malaikat Mikail", "Sahabat Abu Bakar", "Penduduk Desa"),
                                        correctIndex = 0,
                                        rewardXp = 25
                                    )
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF9FAF7)
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
                                            Toast.makeText(context, "Gagal terhubung ke database", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        // LAYAR PILIH LEVEL & KITAB (GAMBAR 1)
                        Screen.SELECT_BOOK -> {
                            currentUser?.let { user ->
                                SelectBookScreen(
                                    currentLevel = user.selectedLevelId,
                                    currentBook = user.selectedBookTitle,
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

                        // LAYAR BERANDA DASBOR 3D PATH (GAMBAR 2)
                        Screen.DASHBOARD -> {
                            currentUser?.let { user ->
                                DashboardScreen(
                                    user = user,
                                    lessons = lessonsList,
                                    onOpenSelectBook = { currentScreen = Screen.SELECT_BOOK },
                                    onSelectLesson = { lesson ->
                                        activeLesson = lesson
                                        currentScreen = Screen.QUIZ
                                    },
                                    onLogout = {
                                        sharedPref.edit().clear().apply()
                                        currentUser = null
                                        currentScreen = Screen.AUTH
                                    }
                                )
                            }
                        }

                        Screen.QUIZ -> {
                            val lessonToPlay = activeLesson ?: Lesson(
                                id = "arbain_01",
                                title = "Hadits 1: Niat",
                                arabicText = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ",
                                translation = "Setiap amalan tergantung pada niatnya.",
                                explanation = "Niat adalah penentu sahnya suatu amal perbuatan.",
                                question = "Apa penentu utama sahnya amal perbuatan?",
                                options = listOf("Pujian Manusia", "Niat yang Ikhlas", "Kecepatan Waktu", "Banyak Harta"),
                                correctIndex = 1,
                                rewardXp = 25
                            )

                            LessonQuizScreen(
                                lesson = lessonToPlay,
                                onComplete = { isCorrect ->
                                    currentUser?.let { user ->
                                        repo.updateLessonResult(user, lessonToPlay.id, isCorrect) { updated ->
                                            currentUser = updated
                                            currentScreen = Screen.DASHBOARD
                                            val notif = if (isCorrect) "+25 XP Diraih! Kubah Hijau Terbuka 🐪" else "-5 XP (Tetap Semangat, Ulangi Pos Kuning! ⚠️)"
                                            Toast.makeText(context, notif, Toast.LENGTH_SHORT).show()
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

// ==========================================
// 1. LAYAR PILIH LEVEL & KITAB (PERSIS GAMBAR 1)
// ==========================================
@Composable
fun SelectBookScreen(
    currentLevel: String,
    currentBook: String,
    onConfirm: (level: String, book: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedLevel by remember { mutableStateOf(currentLevel) }
    var selectedBook by remember { mutableStateOf(currentBook) }
    var isLevelAccordionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131F24))
            .statusBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🐪", fontSize = 56.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36))
            ) {
                Text(
                    text = "Ta'lim apa yang ingin kamu pelajari hari ini?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("PILIH KITAB PEMBELAJARAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(10.dp))

        MASTER_AVAILABLE_BOOKS.forEach { book ->
            val isChosen = selectedBook == book.title
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChosen) Color(0xFF2E7D32) else Color(0xFF202F36)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { selectedBook = book.title }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(book.iconEmoji, fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Text("${book.category} • Rekomendasi: ${book.levelAge}", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                    }
                    if (isChosen) Text("✓", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("PILIH LEVEL USIA SANTRI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isLevelAccordionExpanded = !isLevelAccordionExpanded }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tingkat: $selectedLevel", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (isLevelAccordionExpanded) "▲" else "▼", color = Color.Gray)
                }

                if (isLevelAccordionExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF37474F))
                    Spacer(modifier = Modifier.height(8.dp))
                    MASTER_AGE_LEVELS.forEach { levelName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLevel = levelName
                                    isLevelAccordionExpanded = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLevel == levelName,
                                onClick = {
                                    selectedLevel = levelName
                                    isLevelAccordionExpanded = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(levelName, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { onConfirm(selectedLevel, selectedBook) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58CC02))
        ) {
            Text("LANJUTKAN ➔", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
        }
    }
}

// ==========================================
// 2. LAYAR BERANDA 3D JALUR DUOLINGO (PERSIS GAMBAR 2)
// ==========================================
@Composable
fun DashboardScreen(
    user: UserProfile,
    lessons: List<Lesson>,
    onOpenSelectBook: () -> Unit,
    onSelectLesson: (Lesson) -> Unit,
    onLogout: () -> Unit
) {
    var selectedBottomNavIndex by remember { mutableIntStateOf(0) }

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
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(user.selectedBookTitle.take(10), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 0,
                    onClick = { selectedBottomNavIndex = 0 },
                    icon = { Text("🏠", fontSize = 20.sp) },
                    label = { Text("Home", fontSize = 10.sp, color = Color.White) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 1,
                    onClick = { selectedBottomNavIndex = 1 },
                    icon = { Text("🎯", fontSize = 20.sp) },
                    label = { Text("Target", fontSize = 10.sp, color = Color.White) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 2,
                    onClick = { selectedBottomNavIndex = 2 },
                    icon = { Text("🏆", fontSize = 20.sp) },
                    label = { Text("Peringkat", fontSize = 10.sp, color = Color.White) }
                )
                NavigationBarItem(
                    selected = selectedBottomNavIndex == 3,
                    onClick = { selectedBottomNavIndex = 3 },
                    icon = { Text("👤", fontSize = 20.sp) },
                    label = { Text("Profil", fontSize = 10.sp, color = Color.White) }
                )
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
                0 -> HomePathTab(user, lessons, onSelectLesson)
                1 -> TargetsTab(user)
                2 -> LeaderboardTab()
                3 -> ProfileTab(user, onLogout)
            }
        }
    }
}

// TAB 1: PETA JALUR 3D DUOLINGO
@Composable
fun HomePathTab(
    user: UserProfile,
    lessons: List<Lesson>,
    onSelectLesson: (Lesson) -> Unit
) {
    val activeLessonIndex = lessons.indexOfFirst { lesson ->
        !user.completedLessons.contains(lesson.id) && !user.completedSectionIds.contains(lesson.id)
    }.let { if (it == -1) lessons.size - 1 else it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // UNIT HEADER HIJAU TEBAL DUOLINGO
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF58CC02)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BAGIAN 1, UNIT 1", color = Color(0xFF265700), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Text(
                            text = "${user.selectedBookTitle}: Bab Niat dan Ikhlas",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF46A302),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("📋", fontSize = 20.sp) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // PETA BERKELOK DENGAN TOMBOL 3D
        itemsIndexed(lessons) { index, lesson ->
            val isCompleted = user.completedLessons.contains(lesson.id)
            val isFailed = user.failedLessons.contains(lesson.id)
            val isCurrent = index == activeLessonIndex
            val isLocked = !lesson.isPublished || (!isCompleted && !isFailed && index > activeLessonIndex)

            val horizontalOffset = when (index % 4) {
                0 -> (-50).dp
                1 -> 0.dp
                2 -> 50.dp
                else -> 0.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (index > 0) {
                    Text("•", fontSize = 14.sp, color = Color(0xFF37474F))
                    Text("•", fontSize = 14.sp, color = Color(0xFF37474F))
                }

                Box(
                    modifier = Modifier
                        .offset(x = horizontalOffset)
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                                .clickable(enabled = !isLocked) { onSelectLesson(lesson) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(mainColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        isCompleted -> "✓"
                                        isFailed -> "⚠️"
                                        isLocked -> "🔒"
                                        isCurrent -> "⭐"
                                        else -> "${index + 1}"
                                    },
                                    fontSize = if (isCompleted) 28.sp else 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lesson.title.ifBlank { "Pos ${index + 1}" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) Color.Gray else Color.White
                        )
                    }
                }
            }
        }

        // Penutup Scroll Bawah (Berada di LUAR itemsIndexed)
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// TAB 2: TARGET / ACHIEVEMENTS
@Composable
fun TargetsTab(user: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Target & Misi Harian 🎯", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Text("Selesaikan target untuk raih lencana kemuliaan santri:", fontSize = 13.sp, color = Color.Gray)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔥 Istiqomah 10 Hari Streak", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Progres Anda saat ini: ${user.currentStreak}/10 Hari", fontSize = 12.sp, color = Color(0xFFFF9600))
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (user.currentStreak / 10f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFFF9600),
                    trackColor = Color(0xFF37474F)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚡ Belajar 10 Menit Setiap Hari", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Status: Tuntas hari ini (${user.dailyMinutesLearned}m)", fontSize = 12.sp, color = Color(0xFF58CC02))
            }
        }
    }
}

// TAB 3: LEADERBOARD / PENCAPAIAN SANTRI LAIN
@Composable
fun LeaderboardTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Papan Peringkat Santri 🏆", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Text("Santri paling istiqomah belajar pekan ini:", fontSize = 13.sp, color = Color.Gray)

        val sampleRankings = listOf(
            Triple("Ahmad Syarif", "Bandung", 1450),
            Triple("Muhammad Rizky", "Surabaya", 1280),
            Triple("Fatimah Azzahra", "Yogyakarta", 950),
            Triple("Santri Funduq", "Indonesia", 60)
        )

        sampleRankings.forEachIndexed { rank, (name, city, xp) ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (rank == 0) Color(0xFF2E7D32) else Color(0xFF202F36)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (rank) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${rank + 1}" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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

// TAB 4: PROFIL SANTRI
@Composable
fun ProfileTab(user: UserProfile, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF58CC02),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("👳‍♂️", fontSize = 42.sp) }
        }

        Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Kota: ${user.city} • Tingkat: ${user.selectedLevelId}", fontSize = 13.sp, color = Color.LightGray)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("KODE AKSES LOGIN PRIBADI:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Text(user.accessCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF58CC02))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gunakan kode ini jika ingin berpindah ke perangkat HP lain.", fontSize = 11.sp, color = Color.LightGray)
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202F36)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥 ${user.currentStreak}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9600))
                    Text("Hari Streak", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💎 ${user.totalXp}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1CB0F6))
                    Text("Total XP", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚡ ${user.dailyMinutesLearned}m", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4B4B))
                    Text("Hari Ini", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Keluar dari Akun (Logout)", fontWeight = FontWeight.Bold)
        }
    }
}
