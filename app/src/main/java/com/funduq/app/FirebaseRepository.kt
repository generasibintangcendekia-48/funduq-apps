package com.funduq.app

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersRef = db.collection("users")
    private val lessonsRef = db.collection("lessons")

    // Menghubungkan data ringkasan kurikulum, metadata CSV, dan progres user
    fun getBooksWithProgress(
        userCompletedLessonIds: List<String>,
        onResult: (List<BookProgressInfo>) -> Unit
    ) {
        // Database Metadata Kitab berbasis data CSV Anda
        val bookMetaDirectory = mapOf(
            "Tuhfatul Athfal" to Triple(
                "Tajwid & Qira'at",
                "Syaikh Sulaiman bin Hasan al-Jamzuri rahimahullah",
                "Disusun pada tahun 1198 H (sekitar 1784 M). Merupakan bait nadham ringkas bagi pemula dalam kaedah tajwid al-Qur'an (nun/mim mati, mad, dan idgham)."
            ),
            "Arbain Nawawi" to Triple(
                "Hadits",
                "Al-Imam Abu Zakariyya Yahya bin Syaraf an-Nawawi rahimahullah",
                "Diselesaikan sekitar tahun 668 H – 676 H (sekitar 1270 – 1277 M). Himpunan 42 hadits sahih pilihan yang merangkum pokok akidah, ibadah, akhlak, dan syariat Islam."
            ),
            "Al-Arba'in An-Nawawiyyah" to Triple(
                "Hadits",
                "Al-Imam Abu Zakariyya Yahya bin Syaraf an-Nawawi rahimahullah",
                "Diselesaikan sekitar tahun 668 H – 676 H (sekitar 1270 – 1277 M). Himpunan 42 hadits sahih pilihan yang merangkum pokok akidah, ibadah, akhlak, dan syariat Islam."
            ),
            "Safinatun Naja" to Triple(
                "Fiqh (Madzhab Syafi'i)",
                "Syaikh Salim bin Sumair al-Hadhrami rahimahullah",
                "Ditulis pada kurun ke-13 H / abad ke-19 M. Matan fiqh ringkas madzhab Syafi'i yang mengupas rukun iman, rukun Islam, thaharah, solat, dan puasa."
            ),
            "Riyadhus Shalihin" to Triple(
                "Hadits & Adab/Tazkiyatun Nufus",
                "Al-Imam Abu Zakariyya Yahya bin Syaraf an-Nawawi rahimahullah",
                "Selesai ditulis pada Ramadhan tahun 670 H di Damsyik. Kompilasi hadits Rasulullah ﷺ bertema adab, zuhud, dan tazkiyatun nufus."
            )
        )

        lessonsRef.get().addOnSuccessListener { snap ->
            val booksGroup = mutableMapOf<String, MutableList<Lesson>>()
            for (doc in snap.documents) {
                val lesson = doc.toObject(Lesson::class.java) ?: continue
                val title = lesson.bookTitle.ifBlank { doc.getString("bookTitle") ?: "" }
                if (title.isNotBlank()) {
                    booksGroup.getOrPut(title) { mutableListOf() }.add(lesson)
                }
            }

            val list = booksGroup.map { (bookTitle, lessons) ->
                val meta = bookMetaDirectory[bookTitle]
                    ?: bookMetaDirectory.entries.firstOrNull { bookTitle.contains(it.key, ignoreCase = true) }?.value
                    ?: Triple("Kajian Turats", "Ulama Salaf", "Kajian kitab kuning klasik rujukan santri nusantara.")

                val total = lessons.size
                val done = lessons.count { userCompletedLessonIds.contains(it.id) }

                BookProgressInfo(
                    bookTitle = bookTitle,
                    fieldType = meta.first,
                    author = meta.second,
                    description = meta.third,
                    completedCount = done,
                    totalCount = total
                )
            }
            onResult(list)
        }.addOnFailureListener {
            onResult(emptyList())
        }
    }


    // 1. Menghitung total santri terdaftar (Efisien Free Tier Spark)
    suspend fun getTotalRegisteredUsers(): Long {
        return try {
            val countQuery = usersRef.count()
            val snapshot = countQuery.get(AggregateSource.SERVER).await()
            snapshot.count
        } catch (e: Exception) {
            0L
        }
    }

    // 2. Mengambil Top 5 Santri berdasarkan XP Tertinggi (Data Asli Firestore)
    suspend fun getTop5Santri(): List<TopSantri> {
        return try {
            usersRef
                .orderBy("totalXp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .await()
                .documents.mapNotNull { doc ->
                    val rawName = doc.getString("name") ?: doc.getString("displayName") ?: "Santri"
                    val city = doc.getString("city") ?: ""
                    val formattedName = if (city.isNotBlank()) "$rawName ($city)" else rawName
                    val xp = doc.getLong("totalXp")?.toInt() ?: 0
                    val completedList = doc.get("completedBooks") as? List<*>

                    TopSantri(
                        name = formattedName,
                        totalXp = xp,
                        completedBooksCount = completedList?.size ?: 0
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 3. Simpan Pendaftaran User Baru
    fun registerUser(user: UserProfile, onResult: (Boolean) -> Unit) {
        usersRef.document(user.accessCode).set(user)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // 4. Ambil Profil User Berdasarkan Kode Akses
    fun getUser(accessCode: String, onResult: (UserProfile?) -> Unit) {
        usersRef.document(accessCode).get()
            .addOnSuccessListener { doc ->
                onResult(if (doc.exists()) doc.toObject(UserProfile::class.java) else null)
            }
            .addOnFailureListener { onResult(null) }
    }

    // 5. Logika Hasil Belajar: Jika Benar (+25 XP), Jika Salah (-5 XP, Pos Kuning)
    fun updateLessonResult(user: UserProfile, lessonId: String, isCorrect: Boolean, onResult: (UserProfile) -> Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val updatedUser = if (isCorrect) {
            val newXp = user.totalXp + 25
            val newStreak = if (user.lastStudyDate == today) user.currentStreak else user.currentStreak + 1
            val newCompleted = if (user.completedLessons.contains(lessonId)) user.completedLessons else user.completedLessons + lessonId
            val newFailed = user.failedLessons.filter { it != lessonId }

            user.copy(
                totalXp = newXp,
                level = (kotlin.math.sqrt(newXp.toDouble()) / 10).toInt() + 1,
                currentStreak = newStreak,
                lastStudyDate = today,
                completedLessons = newCompleted,
                failedLessons = newFailed
            )
        } else {
            val newXp = (user.totalXp - 5).coerceAtLeast(0)
            val newFailed = if (user.failedLessons.contains(lessonId)) user.failedLessons else user.failedLessons + lessonId
            val newCompleted = user.completedLessons.filter { it != lessonId }

            user.copy(
                totalXp = newXp,
                level = (kotlin.math.sqrt(newXp.toDouble()) / 10).toInt() + 1,
                failedLessons = newFailed,
                completedLessons = newCompleted
            )
        }

        usersRef.document(user.accessCode).set(updatedUser).addOnSuccessListener { onResult(updatedUser) }
    }

    // 6. Ambil Materi Kitab yang Sedang Dipilih Santri
    fun getLessonsByBook(bookTitle: String, onResult: (List<Lesson>) -> Unit) {
        lessonsRef
            .whereEqualTo("bookTitle", bookTitle)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(Lesson::class.java).sortedBy { it.order }
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // 7. Ambil Ringkasan Kurikulum & Kitab yang Tersedia di Firestore
    fun getAvailableCurriculumSummary(onResult: (Map<String, List<String>>) -> Unit) {
        lessonsRef.get().addOnSuccessListener { snap ->
            val map = mutableMapOf<String, MutableSet<String>>()
            for (doc in snap.documents) {
                val level = doc.getString("levelCategory") ?: "Umum"
                val book = doc.getString("bookTitle") ?: ""
                if (book.isNotBlank()) {
                    map.getOrPut(level) { mutableSetOf() }.add(book)
                }
            }
            onResult(map.mapValues { it.value.toList() })
        }.addOnFailureListener {
            onResult(emptyMap())
        }
    }

    // 8. Query Papan Peringkat Real-time (50 Santri XP Tertinggi)
    fun getLeaderboard(onResult: (List<UserProfile>) -> Unit) {
        db.collection("users")
            .orderBy("totalXp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(UserProfile::class.java)
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // 9. Query Tantangan & Misi Harian Santri
    fun getActiveChallenges(onResult: (List<ChallengeItem>) -> Unit) {
        db.collection("challenges")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(ChallengeItem::class.java)
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}
