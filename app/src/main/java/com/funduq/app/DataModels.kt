package com.funduq.app

// 1. Profil Pengguna
data class UserProfile(
    val accessCode: String = "",
    val name: String = "",
    val city: String = "",
    val phoneNumber: String = "",
    val deviceId: String = "",
    val selectedLevelId: String = "Yafi' (12-15 Tahun)",
    val selectedBookTitle: String = "Arbain Nawawi",
    val totalXp: Int = 0,
    val level: Int = 1,
    val currentStreak: Int = 0,
    val dailyMinutesLearned: Int = 10,
    val lastStudyDate: String = "",
    val completedSectionIds: List<String> = emptyList(),
    val passedExamChapterIds: List<String> = emptyList(),
    val completedLessons: List<String> = emptyList(),
    val failedLessons: List<String> = emptyList()
)

// 2. Model Pembelajaran
data class Lesson(
    val id: String = "",
    val levelCategory: String = "Yafi'",
    val fieldType: String = "Hadits",
    val bookTitle: String = "Arbain Nawawi",
    val chapterTitle: String = "Bab 1: Niat",
    val unitTitle: String = "BAGIAN 1, UNIT 1",
    val order: Int = 1,
    val title: String = "",
    val arabicText: String = "",
    val translation: String = "",
    val explanation: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val rewardXp: Int = 25,
    val isPublished: Boolean = true,
    val isExam: Boolean = false
)

// 3. Model Butir Soal & Section
data class QuestionItem(
    val id: String = "",
    val arabicText: String = "",
    val translation: String = "",
    val explanation: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0
)

data class ChapterSection(
    val sectionId: String = "",
    val sectionNumber: Int = 1,
    val title: String = "",
    val rewardXp: Int = 25,
    val questions: List<QuestionItem> = emptyList()
)

data class ChapterExam(
    val title: String = "Ujian Kenaikan Chapter",
    val passingScorePercent: Int = 80,
    val rewardXp: Int = 100,
    val questions: List<QuestionItem> = emptyList()
)

// 4. Model Bab & Kitab (YANG DICARI OLEH FIREBASE REPOSITORY)
data class Chapter(
    val chapterId: String = "",
    val bookId: String = "",
    val chapterNumber: Int = 1,
    val title: String = "",
    val isPublished: Boolean = true,
    val sections: List<ChapterSection> = emptyList(),
    val exam: ChapterExam = ChapterExam()
)

data class BookItem(
    val bookId: String = "",
    val title: String = "",
    val levelAgeId: String = "yafi",
    val categoryField: String = "Hadits",
    val description: String = "",
    val isPublished: Boolean = true,
    val chapters: List<Chapter> = emptyList()
)

// 5. Pilihan Kitab & Level untuk UI Layar
data class BookOption(
    val title: String,
    val category: String,
    val levelAge: String,
    val iconEmoji: String
)

val MASTER_AVAILABLE_BOOKS = listOf(
    BookOption("Arbain Nawawi", "Hadits", "Yafi' (12-15 Tahun)", "📖"),
    BookOption("Jurumiyah", "Arabiyah", "Yafi' (12-15 Tahun)", "📜"),
    BookOption("Safinatun Najah", "Fiqih", "Yafi' (12-15 Tahun)", "⛵"),
    BookOption("Riyadhus Shalihin", "Hadits", "Syabab (18-21 Tahun)", "🌿"),
    BookOption("Imrithi", "Arabiyah", "Futuwwah (15-18 Tahun)", "📝"),
    BookOption("Rotibul Haddad", "Dzikir", "Umum / Semua Usia", "📿")
)

val MASTER_AGE_LEVELS = listOf(
    "Athfal (3-5 Tahun)",
    "Shibyan (6-9 Tahun)",
    "Mumayyiz (9-12 Tahun)",
    "Yafi' (12-15 Tahun)",
    "Futuwwah (15-18 Tahun)",
    "Syabab (18-21 Tahun)",
    "Umum / Semua Usia"
)
