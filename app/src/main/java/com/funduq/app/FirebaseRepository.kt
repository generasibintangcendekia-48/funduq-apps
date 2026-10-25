package com.funduq.app

import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersRef = db.collection("users")
    private val lessonsRef = db.collection("lessons")

    // 1. Simpan Pendaftaran User Baru
    fun registerUser(user: UserProfile, onResult: (Boolean) -> Unit) {
        usersRef.document(user.accessCode).set(user)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // 2. Ambil Data User berdasarkan Kode Akses
    fun getUser(accessCode: String, onResult: (UserProfile?) -> Unit) {
        usersRef.document(accessCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResult(doc.toObject(UserProfile::class.java))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    // 3. Logika Hasil Belajar: Jika Benar (+25 XP, Hijau), Jika Salah (-5 XP, Kuning ⚠️)
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

    // 4. Ambil Daftar Semua Materi Hadits dari Firestore
    fun getLessons(onResult: (List<Lesson>) -> Unit) {
        lessonsRef.orderBy("order").get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(Lesson::class.java)
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
