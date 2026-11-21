package com.funduq.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.URLEncoder

private object LandingPalette {
    val Sky = Color(0xFF87CEEB)
    val SkyLight = Color(0xFFCBE4EE)
    val Sand = Color(0xFFFDFBF7)
    val SandCard = Color(0xFFFDF8F5)
    val Border = Color(0xFFEAD5C3)
    val DomeBlue = Color(0xFF1E5E7A)
    val Gold = Color(0xFFD49B35)
    val Terracotta = Color(0xFFC86D51)
    val Green = Color(0xFF2E7D6A)
    val GreenDark = Color(0xFF164235)
    val Text = Color(0xFF2C221E)
    val Muted = Color(0xFF7A6A65)
    val Error = Color(0xFFB42318)
}

@Composable
fun TuratsGoLandingScreen(
    totalUsers: Long,
    topUsers: List<SantriActivity>,
    activityFeedError: String?,
    registeredAccessCode: String?,
    onLoginWithCode: (String) -> Unit,
    onRegisterNewUser: (
        name: String,
        province: String,
        city: String,
        phoneNumber: String,
        onResult: (Result<UserProfile>) -> Unit
    ) -> Unit,
    onContinueAfterRegistration: () -> Unit
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }
    var showLoginForm by remember { mutableStateOf(false) }
    var activityIndex by remember { mutableIntStateOf(0) }

    var newName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var provinces by remember { mutableStateOf<List<RegionOption>>(emptyList()) }
    var cities by remember { mutableStateOf<List<RegionOption>>(emptyList()) }
    var selectedProvince by remember { mutableStateOf<RegionOption?>(null) }
    var selectedCity by remember { mutableStateOf<RegionOption?>(null) }
    var isOtherProvince by remember { mutableStateOf(false) }
    var isOtherCity by remember { mutableStateOf(false) }
    var otherProvince by remember { mutableStateOf("") }
    var otherCity by remember { mutableStateOf("") }
    var isLoadingProvinces by remember { mutableStateOf(true) }
    var isLoadingCities by remember { mutableStateOf(false) }
    var provinceLoadError by remember { mutableStateOf<String?>(null) }
    var cityLoadError by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var registrationError by remember { mutableStateOf<String?>(null) }
    var newlyRegisteredUser by remember { mutableStateOf<UserProfile?>(null) }
    var hasSavedAccessCode by remember { mutableStateOf(false) }

    LaunchedEffect(topUsers) {
        activityIndex = 0
        while (topUsers.size > 1) {
            delay(3_000)
            activityIndex = (activityIndex + 1) % topUsers.size
        }
    }

    LaunchedEffect(Unit) {
        try {
            provinces = IndonesianRegionRepository.getProvinces()
        } catch (error: IOException) {
            provinceLoadError = "Data provinsi tidak dapat dimuat. Pilih “Lainnya” untuk mengisi manual."
        } finally {
            isLoadingProvinces = false
        }
    }

    LaunchedEffect(selectedProvince?.id) {
        val provinceId = selectedProvince?.id ?: return@LaunchedEffect
        cities = emptyList()
        selectedCity = null
        isOtherCity = false
        otherCity = ""
        cityLoadError = null
        isLoadingCities = true
        try {
            cities = IndonesianRegionRepository.getCities(provinceId)
        } catch (error: IOException) {
            cityLoadError = "Data kota/kabupaten tidak dapat dimuat. Pilih “Lainnya” untuk mengisi manual."
        } finally {
            isLoadingCities = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = LandingPalette.Terracotta,
        unfocusedBorderColor = LandingPalette.Border,
        focusedLabelColor = LandingPalette.DomeBlue,
        unfocusedLabelColor = LandingPalette.Muted,
        focusedTextColor = LandingPalette.Text,
        unfocusedTextColor = LandingPalette.Text,
        cursorColor = LandingPalette.Terracotta,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedPlaceholderColor = LandingPalette.Muted,
        unfocusedPlaceholderColor = LandingPalette.Muted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LandingPalette.Sand)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(LandingPalette.Sky, LandingPalette.SkyLight, LandingPalette.Sand)
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_turatsgo_logo),
                    contentDescription = "TuratsGo Logo",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(2.dp, LandingPalette.Gold, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "TuratsGo",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = LandingPalette.DomeBlue,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "TuratsGo - Gamified Kitab Klasik",
                        style = MaterialTheme.typography.labelMedium.copy(color = LandingPalette.Gold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Belajar kitab lebih mudah dan menyenangkan",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = LandingPalette.Text.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LandingPalette.Border, RoundedCornerShape(16.dp))
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
                            color = LandingPalette.DomeBlue,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = LandingPalette.Border.copy(alpha = 0.65f)
                )
                Text(
                    text = "🔔 Aktivitas Santri Terbaru",
                    style = MaterialTheme.typography.labelLarge.copy(color = LandingPalette.Text)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (topUsers.isEmpty()) {
                    Text(
                        text = activityFeedError ?: "Belum ada aktivitas belajar terbaru.",
                        color = if (activityFeedError != null) LandingPalette.Error else LandingPalette.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Crossfade(
                        targetState = topUsers.getOrNull(activityIndex),
                        label = "recent_santri_activity"
                    ) { activity ->
                        if (activity != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📚", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${activity.name} ${activity.message}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = LandingPalette.Text,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = activityAgeLabel(activity.lastActivityAt),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = LandingPalette.Muted
                                        )
                                    )
                                }
                            }
                            if (activityFeedError != null) {
                                Text(
                                    text = activityFeedError,
                                    color = LandingPalette.Error,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = LandingPalette.SandCard),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LandingPalette.Border, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Buat Akun Santri Baru 📝",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = LandingPalette.Text,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Daftar gratis untuk mulai belajar dan menyimpan progres Anda.",
                    style = MaterialTheme.typography.bodySmall.copy(color = LandingPalette.Muted)
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; registrationError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama Lengkap / Panggilan") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = fieldColors
                )

                RegionDropdownField(
                    label = "Provinsi",
                    selectedName = selectedProvince?.name,
                    options = provinces,
                    isLoading = isLoadingProvinces,
                    onSelected = { region ->
                        selectedProvince = region
                        isOtherProvince = region == null
                        otherProvince = ""
                        registrationError = null
                    },
                    onOtherSelected = {
                        selectedProvince = null
                        isOtherProvince = true
                        otherProvince = ""
                        selectedCity = null
                        isOtherCity = false
                        otherCity = ""
                        cities = emptyList()
                        registrationError = null
                    }
                )
                if (provinceLoadError != null) {
                    Text(provinceLoadError!!, color = LandingPalette.Error, fontSize = 11.sp)
                }
                if (isOtherProvince) {
                    OutlinedTextField(
                        value = otherProvince,
                        onValueChange = { otherProvince = it; registrationError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama provinsi") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = fieldColors
                    )
                }

                RegionDropdownField(
                    label = "Kota / Kabupaten",
                    selectedName = selectedCity?.name,
                    options = cities,
                    isLoading = isLoadingCities,
                    enabled = selectedProvince != null || isOtherProvince,
                    onSelected = { region ->
                        selectedCity = region
                        isOtherCity = region == null
                        otherCity = ""
                        registrationError = null
                    },
                    onOtherSelected = {
                        selectedCity = null
                        isOtherCity = true
                        otherCity = ""
                        registrationError = null
                    }
                )
                if (cityLoadError != null) {
                    Text(cityLoadError!!, color = LandingPalette.Error, fontSize = 11.sp)
                }
                if (isOtherCity) {
                    OutlinedTextField(
                        value = otherCity,
                        onValueChange = { otherCity = it; registrationError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama kota / kabupaten") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = fieldColors
                    )
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("No. HP untuk pemulihan (opsional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = fieldColors
                )

                if (registrationError != null) {
                    Text(registrationError!!, color = LandingPalette.Error, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val province = if (isOtherProvince) otherProvince.trim() else selectedProvince?.name.orEmpty()
                        val city = if (isOtherCity) otherCity.trim() else selectedCity?.name.orEmpty()
                        if (newName.isBlank() || province.isBlank() || city.isBlank()) {
                            registrationError = "Lengkapi nama, provinsi, dan kota/kabupaten."
                        } else {
                            isRegistering = true
                            registrationError = null
                            onRegisterNewUser(
                                newName.trim(),
                                province,
                                city,
                                phoneNumber.trim()
                            ) { result ->
                                isRegistering = false
                                result.onSuccess { user ->
                                    newlyRegisteredUser = user
                                    hasSavedAccessCode = false
                                }.onFailure { error ->
                                    registrationError = when (error) {
                                        is IllegalStateException -> error.message
                                            ?: "Kode akses tidak tersedia. Silakan coba lagi."
                                        else -> "Akun gagal dibuat. Periksa koneksi internet lalu coba lagi."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isRegistering,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LandingPalette.Terracotta,
                        contentColor = Color.White
                    )
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Buat Akun Gratis ➔", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                HorizontalDivider(color = LandingPalette.Border)
                Text(
                    text = if (showLoginForm) "Tutup masuk dengan kode akses" else
                        "Sudah punya kode akses santri? Masukkan kode akses",
                    color = LandingPalette.DomeBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        showLoginForm = !showLoginForm
                    }.padding(vertical = 4.dp)
                )

                if (showLoginForm) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Kode Akses (Contoh: FDQ-1234)") },
                        label = { Text("Kode akses santri") },
                        colors = fieldColors,
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Lupa Kode Akses?",
                            color = LandingPalette.Terracotta,
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
                    Button(
                        onClick = { onLoginWithCode(inputCode) },
                        enabled = inputCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LandingPalette.Terracotta,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Masuk dengan Kode Akses ➔", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LandingPalette.GreenDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, LandingPalette.Green.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                    Text("Hubungi via WhatsApp: 0856 76 88848", fontSize = 11.sp, color = Color(0xFFC5E8D6))
                }
                Text("Chat ➔", fontWeight = FontWeight.Bold, color = Color(0xFF7ED7A5), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    newlyRegisteredUser?.let { user ->
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            containerColor = LandingPalette.SandCard,
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎉", fontSize = 40.sp)
                    Text(
                        "Akun Berhasil Dibuat!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = LandingPalette.Text,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Simpan atau screenshot kode ini untuk login jika berganti perangkat atau keluar dari akun.",
                        fontSize = 13.sp,
                        color = LandingPalette.Muted,
                        textAlign = TextAlign.Center
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4EA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("NAMA SANTRI", fontSize = 10.sp, color = LandingPalette.Muted)
                            Text(user.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LandingPalette.Text)
                            Text(
                                "${user.city}, ${user.province}",
                                fontSize = 12.sp,
                                color = LandingPalette.Muted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("KODE AKSES LOGIN", fontSize = 11.sp, color = LandingPalette.Green)
                            Text(
                                user.accessCode,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = LandingPalette.Green
                            )
                            TextButton(
                                onClick = {
                                    val clipboardManager =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    if (clipboardManager != null) {
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText("Kode akses", user.accessCode)
                                        )
                                        Toast.makeText(context, "Kode akses disalin", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Salin kode akses", color = LandingPalette.DomeBlue)
                            }
                        }
                    }
                    Text(
                        "📸 Jangan lupa capture/screenshot popup ini dan catat kode akses Anda.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LandingPalette.Terracotta,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { hasSavedAccessCode = !hasSavedAccessCode }
                    ) {
                        Checkbox(
                            checked = hasSavedAccessCode,
                            onCheckedChange = { hasSavedAccessCode = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LandingPalette.Green,
                                uncheckedColor = LandingPalette.Muted
                            )
                        )
                        Text(
                            "Saya sudah mencatat / menyimpan kode akses",
                            color = LandingPalette.Text,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        newlyRegisteredUser = null
                        onContinueAfterRegistration()
                    },
                    enabled = hasSavedAccessCode,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LandingPalette.Terracotta,
                        contentColor = Color.White
                    )
                ) {
                    Text("Lanjut ke Pilih Kitab 🚀", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun activityAgeLabel(activityAt: Long): String {
    val elapsedMillis = (System.currentTimeMillis() - activityAt).coerceAtLeast(0L)
    return when {
        elapsedMillis < 60_000L -> "Baru saja"
        elapsedMillis < 3_600_000L -> "${elapsedMillis / 60_000L} menit lalu"
        elapsedMillis < 86_400_000L -> "${elapsedMillis / 3_600_000L} jam lalu"
        else -> "${elapsedMillis / 86_400_000L} hari lalu"
    }
}

@Composable
private fun RegionDropdownField(
    label: String,
    selectedName: String?,
    options: List<RegionOption>,
    isLoading: Boolean,
    enabled: Boolean = true,
    onSelected: (RegionOption?) -> Unit,
    onOtherSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .border(1.dp, LandingPalette.Border, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LandingPalette.Text,
                disabledContentColor = LandingPalette.Muted
            )
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = LandingPalette.Muted, fontSize = 11.sp)
                Text(
                    text = when {
                        selectedName != null -> selectedName
                        isLoading -> "Memuat data wilayah..."
                        else -> "Pilih $label"
                    },
                    color = if (selectedName == null) LandingPalette.Muted else LandingPalette.Text,
                    fontSize = 14.sp
                )
            }
            Text(if (isLoading) "…" else "⌄", color = LandingPalette.Green, fontSize = 18.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 320.dp)
                .background(LandingPalette.SandCard)
        ) {
            options.forEach { region ->
                DropdownMenuItem(
                    text = { Text(region.name, color = LandingPalette.Text) },
                    onClick = {
                        expanded = false
                        onSelected(region)
                    }
                )
            }
            HorizontalDivider(color = LandingPalette.Border)
            DropdownMenuItem(
                text = { Text("Lainnya / tidak terdaftar", color = LandingPalette.DomeBlue) },
                onClick = {
                    expanded = false
                    onOtherSelected()
                }
            )
        }
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
