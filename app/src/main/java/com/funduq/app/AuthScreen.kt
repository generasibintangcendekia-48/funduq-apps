package com.funduq.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@Composable
fun AuthScreen(
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var accessCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // State untuk Pop-Up Konfirmasi Sukses Daftar
    var showRegistrationSuccessDialog by remember { mutableStateOf(false) }
    var newlyRegisteredUser by remember { mutableStateOf<UserProfile?>(null) }

    val repo = remember { FirebaseRepository() }

    val uniqueDeviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_UNKNOWN"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAF7))
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1B5E20),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("🕌", fontSize = 24.sp) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Funduq App", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                Text("Gamified Kitab & Hadits Platform", fontSize = 11.sp, color = Color(0xFF558B2F), fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047))
                        )
                    )
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨ Program Santri Ramadhan", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("GRATIS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Jelajahi Kafilah Ilmu Kitab Kuning semudah bermain game!",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌟", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("1.420+ Santri Belajar Hari Ini", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
                    Text("Ahmad (Bandung) baru saja capai 🔥 7 Hari Streak (1.250 XP)!", fontSize = 11.sp, color = Color(0xFF795548))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (isRegister) {
                    Text("Daftar Santri Baru 📝", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    Text("Dapatkan kode akses gratis untuk mulai belajar", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Lengkap") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Kota Asal") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("No. WhatsApp (Opsional - Pemulihan)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            val generatedCode = "FDQ-${Random.nextInt(1000, 9999)}"
                            val user = UserProfile(
                                accessCode = generatedCode,
                                name = name.trim(),
                                city = city.trim(),
                                phoneNumber = phoneNumber.trim(),
                                deviceId = uniqueDeviceId
                            )
                            repo.registerUser(user) { success ->
                                isLoading = false
                                if (success) {
                                    newlyRegisteredUser = user
                                    showRegistrationSuccessDialog = true
                                } else {
                                    Toast.makeText(context, "Koneksi lambat, coba lagi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = !isLoading && name.isNotBlank() && city.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Daftar & Dapatkan Kode Akses 🚀", fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = { isRegister = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Sudah punya kode akses? Masuk di sini", color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }
                } else {
                    Text("Masuk Belajar 📖", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    Text("Masukkan kode akses santri Anda", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = accessCodeInput,
                        onValueChange = { accessCodeInput = it },
                        label = { Text("Kode Akses (Contoh: FDQ-1234)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val pesan = "Halo Admin Funduq App, saya lupa kode akses santri saya.%0A%0A*Referensi Perangkat:*%0AID Perangkat: $uniqueDeviceId%0A%0AMohon bantu cek kode akses saya. Terima kasih."
                                val url = "https://api.whatsapp.com/send?phone=628567688848&text=$pesan"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Lupa Kode Akses?", fontSize = 12.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val cleanCode = accessCodeInput.trim().uppercase()
                            isLoading = true
                            repo.getUser(cleanCode) { loadedUser ->
                                isLoading = false
                                if (loadedUser != null) {
                                    onLoginSuccess(loadedUser)
                                } else {
                                    Toast.makeText(context, "Kode akses tidak ditemukan. Periksa kembali atau klik Lupa Kode.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = !isLoading && accessCodeInput.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Masuk Belajar Sekarang ➔", fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = { isRegister = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Santri Baru? Buat Kode Akses Gratis", color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val url = "https://api.whatsapp.com/send?phone=628567688848&text=Halo%20Admin%20Funduq%20App,%20saya%20ingin%20bertanya%20seputar%20pembelajaran."
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
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
                    Text("Ada Pertanyaan atau Kendala?", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                    Text("Hubungi via WhatsApp: 0856 76 88848", fontSize = 12.sp, color = Color(0xFF2E7D32))
                }
                Text("Chat ➔", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // ==========================================
    // POP-UP KONFIRMASI SUKSES PENDAFTARAN
    // ==========================================
    if (showRegistrationSuccessDialog && newlyRegisteredUser != null) {
        AlertDialog(
            onDismissRequest = { /* Pengguna harus menekan tombol konfirmasi */ },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("🎉", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pendaftaran Berhasil!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF1B5E20),
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Ahlan wa Sahlan! Akun belajar Anda telah aktif. Mohon simpan atau screenshot informasi ini:",
                        fontSize = 13.sp,
                        color = Color(0xFF424242),
                        textAlign = TextAlign.Center
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Nama: ${newlyRegisteredUser!!.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Asal Kota: ${newlyRegisteredUser!!.city}", fontSize = 13.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFAED581))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("KODE AKSES ANDA:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF558B2F))
                            Text(
                                text = newlyRegisteredUser!!.accessCode,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📸 PENTING: Silakan CAPTURE / SCREENSHOT layar ini untuk keperluan masuk kembali jika aplikasi tertutup!",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegistrationSuccessDialog = false
                        onLoginSuccess(newlyRegisteredUser!!)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Lanjut Belajar Sekarang 🚀", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
