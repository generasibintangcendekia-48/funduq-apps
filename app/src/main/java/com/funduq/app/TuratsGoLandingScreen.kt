package com.funduq.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

@Composable
fun TuratsGoLandingScreen(
    totalUsers: Long,
    topUsers: List<TopSantri>,
    registeredAccessCode: String?,
    onLoginWithCode: (String) -> Unit,
    onRegisterNewUser: (name: String, city: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }
    var showRegisterDialog by remember { mutableStateOf(false) }

    // Dialog input santri baru
    var newName by remember { mutableStateOf("") }
    var newCity by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Tema Dark slate TuratsGo
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. HEADER DENGAN LOGO & JUDUL ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_turatsgo_logo),
                contentDescription = "TuratsGo Logo",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "TuratsGo",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "TuratsGo - Gamified Kitab Klasik",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFFFACC15) // Kuning Emas
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- 2. BANNER SUB-HEADLINE ---
        Text(
            text = "Belajar kitab lebih mudah dan menyenangkan",
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(22.dp))

        // --- 3. KARTU STATISTIK USER & TOP 5 HIGHLIGHT ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "👥 $totalUsers+ Santri Terdaftar Aktif",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFF334155)
                )

                Text(
                    text = "🏆 Top 5 Santri Berprestasi:",
                    style = MaterialTheme.typography.labelLarge.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (topUsers.isEmpty()) {
                    Text(
                        text = "Memuat data peringkat santri...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    topUsers.forEachIndexed { index, user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${user.name}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE2E8F0)),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "⭐ ${user.totalXp} XP | 📖 ${user.completedBooksCount} Kitab",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF22C55E)),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. FORM MASUK BELAJAR ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Masuk Belajar 📖",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Masukkan kode akses santri Anda",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            text = "Kode Akses (Contoh: FDQ-1234)",
                            color = Color(0xFF22C55E) // Hint Berwarna Hijau
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF22C55E),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF22C55E)
                    ),
                    singleLine = true
                )

                // Tombol Lupa Kode Akses
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Lupa Kode Akses?",
                        color = Color(0xFFFF9600),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            DeviceSupportHelper.openWhatsAppSupport(
                                context = context,
                                registeredAccessCode = registeredAccessCode ?: inputCode
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Masuk Belajar
                Button(
                    onClick = { onLoginWithCode(inputCode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text(text = "Masuk Belajar Sekarang ➔", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tombol Santri Baru? Buat Kode Akses Gratis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Santri Baru? Buat Kode Akses Gratis",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showRegisterDialog = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 5. KARTU BANTUAN WHATSAPP & KODE AKSES DEVICE ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E28)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    DeviceSupportHelper.openWhatsAppSupport(
                        context = context,
                        registeredAccessCode = registeredAccessCode ?: inputCode
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF25D366),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("💬", fontSize = 20.sp) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ada Pertanyaan atau Kendala?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Hubungi via WhatsApp: 0856 76 88848", fontSize = 11.sp, color = Color(0xFF81C784))
                }
                Text("Chat ➔", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // --- POPUP DIALOG BUAT KODE AKSES SANTRI BARU ---
    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E293B),
            title = {
                Text("Daftar Santri Baru 📝", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Masukkan nama dan kota asal Anda untuk membuat kode akses gratis:", color = Color.LightGray, fontSize = 12.sp)

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Lengkap / Panggilan", color = Color(0xFF81C784)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF22C55E),
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    OutlinedTextField(
                        value = newCity,
                        onValueChange = { newCity = it },
                        label = { Text("Kota Asal (Contoh: Bandung)", color = Color(0xFF81C784)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF22C55E),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRegisterNewUser(newName.trim(), newCity.trim().ifBlank { "Indonesia" })
                            showRegisterDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    enabled = newName.isNotBlank()
                ) {
                    Text("Buat Akun", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}

// --- HELPER BANTUAN WHATSAPP & DEVICE ID ---
object DeviceSupportHelper {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
    }

    fun openWhatsAppSupport(context: Context, registeredAccessCode: String?, adminPhone: String = "628567688848") {
        val deviceId = getDeviceId(context)
        val codeText = if (!registeredAccessCode.isNullOrEmpty()) {
            "Kode Akses Terdaftar: $registeredAccessCode\nDevice ID: $deviceId"
        } else {
            "Device ID: $deviceId (Belum Terdaftar)"
        }

        val message = """
            Assalamu'alaikum Admin TuratsGo, saya mengalami kendala pada aplikasi.
            
            Informasi Perangkat:
            $codeText
            
            Kendala yang dihadapi: 
        """.trimIndent()

        val url = "https://api.whatsapp.com/send?phone=$adminPhone&text=${URLEncoder.encode(message, "UTF-8")}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
