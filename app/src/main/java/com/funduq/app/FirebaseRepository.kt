package com.funduq.app

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import kotlinx.coroutines.tasks.await
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseRepository {
    // Inisialisasi Firestore dengan Offline Persistence aktif secara otomatis
    private val db = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            isPersistenceEnabled = true // Otomatis simpan data kuis, materi & profil di HP (Bisa Offline)
        }
    }
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

    // 2. Mengambil lima santri dengan aktivitas belajar terbaru
    fun listenRecentSantriActivities(
        onResult: (List<SantriActivity>, String?) -> Unit
    ): ListenerRegistration {
        val latestActivities = mutableMapOf<String, SantriActivity>()
        val legacyActivities = mutableMapOf<String, SantriActivity>()
        var latestError: String? = null
        var legacyError: String? = null

        fun emitResults() {
            val combined = (legacyActivities + latestActivities).values
                .sortedByDescending { it.lastActivityAt }
                .take(5)
            onResult(combined, latestError ?: legacyError)
        }

        val latestListener = usersRef
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepository", "Gagal memuat aktivitas santri terbaru", error)
                    latestError = "Aktivitas login tidak dapat dimuat."
                } else if (snapshot != null) {
                    latestError = null
                    latestActivities.clear()
                    snapshot.documents.mapNotNull(::toSantriActivity).forEach { activity ->
                        latestActivities[activity.id] = activity
                    }
                }
                emitResults()
            }

        val legacyListener = usersRef
            .orderBy("lastStudyDate", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepository", "Gagal memuat histori belajar lama", error)
                    legacyError = "Histori belajar lama tidak dapat dimuat."
                } else if (snapshot != null) {
                    legacyError = null
                    legacyActivities.clear()
                    snapshot.documents.mapNotNull(::toLegacyStudyActivity).forEach { activity ->
                        legacyActivities[activity.id] = activity
                    }
                }
                emitResults()
            }

        return object : ListenerRegistration {
            override fun remove() {
                latestListener.remove()
                legacyListener.remove()
            }
        }
    }

    private fun toSantriActivity(document: DocumentSnapshot): SantriActivity? {
        val activityAt = document.getLong("lastActivityAt") ?: 0L
        val message = document.getString("lastActivityMessage").orEmpty()
        if (activityAt <= 0L || message.isBlank()) return null

        return SantriActivity(
            id = document.id,
            name = document.getString("name") ?: document.getString("displayName") ?: "Santri",
            message = message,
            lastActivityAt = activityAt
        )
    }

    private fun toLegacyStudyActivity(document: DocumentSnapshot): SantriActivity? {
        val studyDate = document.getString("lastStudyDate").orEmpty()
        val activityAt = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                .parse(studyDate)?.time
        } catch (_: ParseException) {
            null
        } ?: return null

        return SantriActivity(
            id = document.id,
            name = document.getString("name") ?: document.getString("displayName") ?: "Santri",
            message = "belajar terakhir pada $studyDate",
            lastActivityAt = activityAt
        )
    }

    fun recordUserActivity(user: UserProfile, message: String): UserProfile {
        val updatedUser = user.copy(
            lastActivityAt = System.currentTimeMillis(),
            lastActivityMessage = message
        )
        usersRef.document(user.accessCode).update(
            mapOf(
                "lastActivityAt" to updatedUser.lastActivityAt,
                "lastActivityMessage" to updatedUser.lastActivityMessage
            )
        ).addOnFailureListener { error ->
            Log.w("FirebaseRepository", "Gagal menyimpan aktivitas santri", error)
        }
        return updatedUser
    }

    // 3. Simpan Pendaftaran User Baru
    fun registerUser(user: UserProfile, onResult: (Boolean) -> Unit) {
        usersRef.document(user.accessCode).set(user)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun createUser(user: UserProfile, onResult: (Result<Boolean>) -> Unit) {
        val userDocument = usersRef.document(user.accessCode)
        db.runTransaction { transaction ->
            if (transaction.get(userDocument).exists()) {
                false
            } else {
                transaction.set(userDocument, user)
                true
            }
        }.addOnSuccessListener { created -> onResult(Result.success(created)) }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
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
    fun updateLessonResult(
        user: UserProfile,
        lessonId: String,
        isCorrect: Boolean,
        lessonTitle: String,
        onResult: (UserProfile) -> Unit
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val activityAt = System.currentTimeMillis()
        val activityMessage = "mengerjakan soal: $lessonTitle"

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
                lastActivityAt = activityAt,
                lastActivityMessage = activityMessage,
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
                lastActivityAt = activityAt,
                lastActivityMessage = activityMessage,
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
