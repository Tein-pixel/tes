package com.teinzz.xrecover

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmsFailed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val PREFS = "xrecover_local"
private const val HISTORY = "history"
private const val THEME = "theme"
private val Accent = Color(0xFFF5F5F5)
private val IssueTypes = listOf("Banned sementara", "Banned permanen", "Akun dibatasi (Restricted)", "OTP / kode tidak masuk", "Coba lagi nanti", "Akun diretas / diambil alih", "Masalah lain")
private val StatusTypes = listOf("Belum dikirim", "Dikirim", "Selesai")

private data class RequestRecord(val id: String, val time: Long, val issue: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
            val dark = true
            val scheme = darkColorScheme(
                primary = Color(0xFFF5F5F5),
                onPrimary = Color(0xFF080808),
                primaryContainer = Color(0xFF292929),
                onPrimaryContainer = Color(0xFFF8F8F8),
                secondary = Color(0xFFD4D4D4),
                onSecondary = Color(0xFF080808),
                background = Color(0xFF080808),
                surface = Color(0xFF131313),
                surfaceVariant = Color(0xFF202020),
                onBackground = Color(0xFFF7F7F7),
                onSurface = Color(0xFFF7F7F7),
                onSurfaceVariant = Color(0xFFB5B5B5),
                outline = Color(0xFF393939)
            )
            MaterialTheme(colorScheme = scheme) {
                XRecover(prefs, dark)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XRecover(prefs: android.content.SharedPreferences, dark: Boolean) {
    val records = remember { mutableStateListOf<RequestRecord>().apply { addAll(loadRecords(prefs)) } }
    var page by remember { mutableStateOf("Pulihkan") }
    var settings by remember { mutableStateOf(false) }
    var draftDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf(IssueTypes[0]) }
    var phone by remember { mutableStateOf("") }
    var appType by remember { mutableStateOf("Belum dipilih") }
    var automationUse by remember { mutableStateOf("Belum dipilih") }
    var screenMessage by remember { mutableStateOf("") }
    var startWhen by remember { mutableStateOf("") }
    var triedSteps by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun notifyUser(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun openUrl(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { notifyUser("Browser tidak dapat dibuka.") } }
    fun openWhatsApp() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) context.startActivity(intent) else notifyUser("WhatsApp belum terpasang.")
    }
    fun saveRecord() {
        records.add(0, RequestRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), issue, "Belum dikirim"))
        storeRecords(prefs, records)
    }
    fun updateRecord(id: String, state: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index >= 0) records[index] = records[index].copy(status = state)
        storeRecords(prefs, records)
    }
    fun buildDraft() {
        val number = phone.trim().takeIf { it.isNotEmpty() }
            ?.let { "Nomor WhatsApp: $it" }
            ?: "Nomor WhatsApp: [masukkan nomor dengan kode negara, jika diperlukan]"
        fun valueOrPrompt(value: String, prompt: String): String =
            value.trim().takeIf { it.isNotEmpty() } ?: "[$prompt]"
        val chronologyItems = mutableListOf(
            "Pesan persis di layar: " + valueOrPrompt(screenMessage, "belum diisi; salin teks yang tampil jika ada"),
            "Mulai terjadi: " + valueOrPrompt(startWhen, "tanggal/jam belum diketahui"),
            "Langkah yang sudah dicoba: " + valueOrPrompt(triedSteps, "belum dijelaskan"),
            "Keterangan tambahan: " + valueOrPrompt(detail, "tidak ada keterangan tambahan")
        )
        if (issue == "Banned sementara" || issue == "Banned permanen" || issue == "Akun dibatasi (Restricted)") {
            chronologyItems.add("Aplikasi yang digunakan: " + if (appType == "Belum dipilih") "[belum dikonfirmasi]" else appType)
            chronologyItems.add("Alat otomatis / scraping: " + if (automationUse == "Belum dipilih") "[belum dikonfirmasi]" else automationUse)
        }
        val chronology = chronologyItems.joinToString("\n")
        val description = when (issue) {
            "Banned sementara" -> "WhatsApp menampilkan pemberitahuan pembatasan sementara pada akun saya. Saya ingin memahami alasan dan langkah yang diperbolehkan untuk menyelesaikan masalah ini. Saya akan mengikuti waktu tunggu atau instruksi yang ditampilkan di aplikasi resmi dan tidak akan mencoba menghindari pembatasan."
            "Banned permanen" -> "WhatsApp menampilkan pemberitahuan bahwa nomor saya tidak dapat menggunakan layanan. Saya meminta peninjauan atas keputusan tersebut apabila tombol Request review tersedia di aplikasi. Mohon periksa kasus ini berdasarkan informasi yang saya berikan; saya memahami bahwa keputusan akhir berada pada WhatsApp."
            "Akun dibatasi (Restricted)" -> "Akun saya menunjukkan pembatasan fitur. Mohon jelaskan langkah resmi yang dapat saya tempuh untuk memahami pembatasan ini dan memulihkan fitur jika akun memenuhi ketentuan."
            "OTP / kode tidak masuk" -> "Saya tidak menerima kode registrasi yang diperlukan. Saya akan memastikan nomor dan kode negara benar, memastikan SIM dapat menerima SMS atau panggilan, dan menunggu penghitung waktu sebelum meminta kode lagi. Mohon panduan tambahan jika masalah masih berlanjut."
            "Coba lagi nanti" -> "Aplikasi menampilkan pesan agar saya mencoba lagi nanti. Saya akan mengikuti waktu tunggu yang diberikan dan tidak meminta kode berulang kali untuk menghindari pembatasan tambahan. Mohon saran langkah resmi jika pesan tetap muncul setelah waktu tunggu selesai."
            "Akun diretas / diambil alih" -> "Saya khawatir akun saya diakses oleh orang lain. Mohon arahkan saya ke langkah resmi untuk mengamankan dan mendaftarkan kembali nomor saya. Saya tidak akan membagikan kode registrasi atau PIN kepada pihak mana pun."
            else -> "Saya mengalami kendala pada akun atau aplikasi WhatsApp dan ingin mengetahui penyebab yang mungkin serta langkah pemecahan masalah yang sesuai."
        }
        draft = "Yth. Tim Dukungan WhatsApp,\n\n" +
            "Saya menghubungi tim dukungan untuk meminta bantuan terkait kendala akun WhatsApp yang saya alami.\n\n" +
            "JENIS MASALAH\n$issue\n\n" +
            "INFORMASI AKUN\n$number\n\n" +
            "KRONOLOGI DAN RINCIAN\n$chronology\n\n" +
            "PENJELASAN MASALAH\n$description\n\n" +
            "PERMOHONAN\nMohon tinjau informasi ini dan arahkan saya ke prosedur resmi yang sesuai dengan jenis masalah di atas. Apabila ada informasi tambahan yang diperlukan, mohon beri tahu melalui saluran dukungan resmi. Saya akan mengikuti ketentuan dan proses verifikasi resmi yang berlaku.\n\n" +
            "Terima kasih atas waktu dan bantuannya.\n\nHormat saya,\nPengguna WhatsApp"
        draftDialog = true
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("XRecover", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("Panduan akun WhatsApp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, "Pengaturan") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val tabs = listOf("Pulihkan" to Icons.Outlined.Shield, "Status" to Icons.Outlined.FactCheck, "FAQ" to Icons.Outlined.HelpOutline, "Riwayat" to Icons.Outlined.History)
                tabs.forEach { (label, icon) ->
                    NavigationBarItem(selected = page == label, onClick = { page = label }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "Pulihkan" -> RecoverScreen(
                    dark, issue, { issue = it }, phone, { phone = it.take(25) },
                    appType, { appType = it },
                    automationUse, { automationUse = it },
                    screenMessage, { screenMessage = it.take(220) },
                    startWhen, { startWhen = it.take(100) },
                    triedSteps, { triedSteps = it.take(220) },
                    detail, { detail = it.take(300) },
                    { buildDraft() }, { openWhatsApp() },
                    { openUrl("https://faq.whatsapp.com/465883178708358/") },
                    { openUrl("https://faq.whatsapp.com/684051319521343/") },
                    { openUrl("https://www.whatsapp.com/contact/?lang=id&subject=messenger") }
                )
                "Status" -> StatusScreen(records, { id, state -> updateRecord(id, state) }, { openWhatsApp() }, { openUrl("https://faq.whatsapp.com/465883178708358/") })
                "FAQ" -> FaqScreen(
                    { openUrl("https://faq.whatsapp.com/465883178708358/") },
                    { openUrl("https://faq.whatsapp.com/684051319521343/") },
                    { openUrl("https://faq.whatsapp.com/1848531392146538/") },
                    { openUrl("https://faq.whatsapp.com/1131652977717250/") }
                )
                else -> HistoryScreen(records, { id, state -> updateRecord(id, state) }, { id -> records.removeAll { it.id == id }; storeRecords(prefs, records) })
            }
        }
    }

    if (draftDialog) AlertDialog(
        onDismissRequest = { draftDialog = false },
        title = { Text("Teks bantuan siap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (issue) {
                        "Banned sementara", "Banned permanen" -> "Periksa dan edit teks sebelum dipakai. Untuk banding ban, gunakan Request review di aplikasi WhatsApp jika tersedia. Form kontak atau aplikasi pihak ketiga tidak dapat mempercepat atau menjamin hasil banding."
                        "OTP / kode tidak masuk", "Coba lagi nanti" -> "Periksa detail dan ikuti timer resmi. Jangan pernah memasukkan OTP, PIN, kata sandi, atau kode verifikasi ke teks ini."
                        "Akun diretas / diambil alih" -> "Edit detail sesuai keadaan sebenarnya. Jangan berikan kode registrasi atau PIN kepada siapa pun. Gunakan langkah pemulihan resmi."
                        else -> "Periksa semua detail dan hapus bagian [ ] yang belum diisi. XRecover hanya menyusun teks; pesan tidak terkirim otomatis."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth().height(260.dp), textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Teks bantuan XRecover", draft))
                saveRecord(); draftDialog = false; notifyUser("Teks disalin dan catatan disimpan.")
            }) { Text("Salin & simpan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, draft) }
                    context.startActivity(Intent.createChooser(share, "Bagikan teks bantuan")); saveRecord(); draftDialog = false
                }) { Text("Bagikan") }
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Teks bantuan XRecover", draft))
                    saveRecord()
                    draftDialog = false
                    notifyUser("Teks disalin. Tempel di jalur dukungan resmi.")
                    when (issue) {
                        "Banned sementara", "Banned permanen" -> openWhatsApp()
                        "OTP / kode tidak masuk" -> openUrl("https://faq.whatsapp.com/1369114327051380/?lang=id_ID")
                        "Coba lagi nanti" -> openUrl("https://faq.whatsapp.com/1369114327051380/?lang=id_ID")
                        else -> openUrl("https://www.whatsapp.com/contact/?lang=id&subject=messenger")
                    }
                }) { Text("Salin & buka resmi") }
                TextButton(onClick = { draftDialog = false }) { Text("Tutup") }
            }
        }
    )

    if (settings) AlertDialog(
        onDismissRequest = { settings = false }, title = { Text("Pengaturan & privasi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Column {
                        Text("Mode gelap hitam-putih", fontWeight = FontWeight.SemiBold)
                        Text("Kontras jelas • aksen glossy tipis", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text("Privasi", fontWeight = FontWeight.SemiBold)
                Text("XRecover tidak meminta kata sandi email, kata sandi WhatsApp, OTP, PIN, atau API key. Teks dibuat di perangkat. Riwayat hanya menyimpan jenis masalah, tanggal, dan status manual.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pengiriman dilakukan oleh kamu melalui WhatsApp atau formulir dukungan resmi. XRecover tidak menyatakan pesan terkirim sebelum saluran resmi mengonfirmasinya.", fontSize = 13.sp)
                Text("Versi 1.3.0 • Android 10+", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Selesai") } }
    )
}

@Composable
private fun RecoverScreen(dark: Boolean, issue: String, setIssue: (String) -> Unit, phone: String, setPhone: (String) -> Unit, appType: String, setAppType: (String) -> Unit, automationUse: String, setAutomationUse: (String) -> Unit, screenMessage: String, setScreenMessage: (String) -> Unit, startWhen: String, setStartWhen: (String) -> Unit, triedSteps: String, setTriedSteps: (String) -> Unit, detail: String, setDetail: (String) -> Unit, generate: () -> Unit, openApp: () -> Unit, openBan: () -> Unit, openOtp: () -> Unit, openContact: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AKUN WHATSAPP\nBERMASALAH?", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-.5).sp)
        Text("Pilih masalah dan ikuti langkah resmi.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .75f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .065f), Color.White.copy(alpha = .012f))), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha = .17f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Shield, null, tint = if (dark) Accent else Color(0xFF315EA8), modifier = Modifier.size(24.dp)) }
                    Column { Text("Pulihkan akses", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Panduan lokal • tanpa login", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("Jenis masalah", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                IssueMenu(issue, setIssue)
                OutlinedTextField(value = phone, onValueChange = setPhone, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nomor WhatsApp (opsional)") }, placeholder = { Text("+62…") }, shape = RoundedCornerShape(13.dp))
                Text("Nomor tidak disimpan dalam riwayat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (issue == "Banned sementara" || issue == "Banned permanen" || issue == "Akun dibatasi (Restricted)") {
                    Text("INFORMASI TAMBAHAN UNTUK PEMERIKSAAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoChoice(
                        selected = appType,
                        choose = setAppType,
                        label = "Aplikasi yang digunakan",
                        options = listOf("Belum dipilih", "WhatsApp resmi", "Aplikasi modifikasi / tidak resmi", "Tidak yakin")
                    )
                    InfoChoice(
                        selected = automationUse,
                        choose = setAutomationUse,
                        label = "Penggunaan alat otomatis / scraping",
                        options = listOf("Belum dipilih", "Tidak menggunakan alat otomatis", "Menggunakan alat otomatis / scraping", "Tidak yakin")
                    )
                    Text("Jawab sesuai keadaan sebenarnya. Pilihan ini hanya masuk ke draf lokal dan tidak dikirim otomatis.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = screenMessage, onValueChange = setScreenMessage,
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                    label = { Text("Pesan persis di layar (opsional)") },
                    placeholder = { Text("Salin teks pemberitahuan. Jangan masukkan OTP atau PIN.") },
                    shape = RoundedCornerShape(13.dp)
                )
                OutlinedTextField(
                    value = startWhen, onValueChange = setStartWhen,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Mulai terjadi (opsional)") },
                    placeholder = { Text("Contoh: 28 Okt, sekitar pukul 14.00") },
                    shape = RoundedCornerShape(13.dp)
                )
                OutlinedTextField(
                    value = triedSteps, onValueChange = setTriedSteps,
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                    label = { Text("Langkah yang sudah dicoba (opsional)") },
                    placeholder = { Text(when (issue) {
                        "Banned sementara" -> "Contoh: membaca pesan aplikasi dan memeriksa penggunaan WhatsApp resmi"
                        "Banned permanen" -> "Contoh: memeriksa apakah tombol Request review tersedia"
                        "OTP / kode tidak masuk", "Coba lagi nanti" -> "Contoh: memeriksa kode negara dan menunggu timer resmi"
                        "Akun diretas / diambil alih" -> "Contoh: mencoba mendaftarkan nomor kembali di aplikasi resmi"
                        else -> "Tuliskan hanya tindakan yang benar-benar sudah dilakukan"
                    }) },
                    shape = RoundedCornerShape(13.dp)
                )
                OutlinedTextField(
                    value = detail, onValueChange = setDetail,
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                    label = { Text("Keterangan tambahan (opsional)") },
                    placeholder = { Text("Informasi lain yang relevan; jangan isi OTP/PIN.") },
                    maxLines = 3, shape = RoundedCornerShape(13.dp)
                )
                Text(detail.length.toString() + "/300", modifier = Modifier.align(Alignment.End), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = generate, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.Article, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Susun teks bantuan", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = openApp, modifier = Modifier.fillMaxWidth().height(45.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Buka WhatsApp") }
            }
        }
        NextStepsCard(
            issue = issue,
            openWhatsApp = openApp,
            openBanGuide = openBan,
            openOtpGuide = openOtp,
            openContact = openContact
        )
                Text("AKSES CEPAT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard("Banding ban", "Panduan resmi", Icons.Outlined.Shield, Modifier.weight(1f), openBan)
            QuickCard("Kode OTP", "Kode tidak masuk", Icons.Outlined.SmsFailed, Modifier.weight(1f), openOtp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("XRecover tidak bisa membatalkan ban atau mempercepat peninjauan. Hasil resmi hanya dapat dikonfirmasi WhatsApp.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun InfoChoice(selected: String, choose: (String) -> Unit, label: String, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text("⌄", fontSize = 18.sp)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option, fontSize = 13.sp) }, onClick = { choose(option); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun NextStepsCard(
    issue: String,
    openWhatsApp: () -> Unit,
    openBanGuide: () -> Unit,
    openOtpGuide: () -> Unit,
    openContact: () -> Unit
) {
    val (title, body, action, click) = when (issue) {
        "Banned sementara" -> listOf(
            "Langkah aman berikutnya",
            "Gunakan aplikasi WhatsApp resmi. Jika memakai aplikasi tidak resmi, beralihlah ke aplikasi resmi; jika ada aktivitas scraping/otomatis, hentikan. Ikuti timer yang tampil dan jangan mencoba mengakali pembatasan.",
            "Buka WhatsApp",
            openWhatsApp
        )
        "Banned permanen" -> listOf(
            "Ajukan peninjauan resmi",
            "Jika tombol Request review tersedia di aplikasi, gunakan jalur itu untuk nomor tersebut. Teks bantuan tidak menjamin ban dicabut dan menghubungi pihak lain tidak mempercepat keputusan.",
            "Buka WhatsApp",
            openWhatsApp
        )
        "Akun dibatasi (Restricted)" -> listOf(
            "Periksa pembatasan akun",
            "Baca pesan pembatasan di aplikasi resmi dan gunakan pilihan bantuan yang ditampilkan. Jangan gunakan alat untuk menghindari pembatasan.",
            "Buka panduan resmi",
            openBanGuide
        )
        "OTP / kode tidak masuk" -> listOf(
            "Periksa sebelum meminta kode lagi",
            "Pastikan nomor dan kode negara benar, SIM bisa menerima SMS/panggilan, lalu tunggu timer di layar. Jangan bagikan kode registrasi atau PIN kepada siapa pun.",
            "Buka panduan kode",
            openOtpGuide
        )
        "Coba lagi nanti" -> listOf(
            "Ikuti waktu tunggu",
            "Jangan meminta kode berulang kali. Tunggu durasi yang ditampilkan aplikasi, lalu coba kembali melalui aplikasi resmi.",
            "Buka panduan kode",
            openOtpGuide
        )
        "Akun diretas / diambil alih" -> listOf(
            "Amankan pendaftaran akun",
            "Gunakan aplikasi resmi dan prosedur registrasi nomor. Jangan pernah membagikan kode SMS atau PIN verifikasi kepada orang yang mengaku sebagai dukungan.",
            "Buka WhatsApp",
            openWhatsApp
        )
        else -> listOf(
            "Gunakan saluran bantuan resmi",
            "Tulis pesan kesalahan yang tepat dan langkah yang sudah dilakukan. Jangan sertakan kata sandi, OTP, PIN, atau kode pemulihan.",
            "Buka kontak resmi",
            openContact
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
            OutlinedButton(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(action, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IssueMenu(selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(Modifier.fillMaxWidth().clickable { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(issueIcon(selected), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(selected, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("⌄", fontSize = 18.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { IssueTypes.forEach { value -> DropdownMenuItem(text = { Text(value, fontSize = 13.sp) }, onClick = { choose(value); expanded = false }) } }
    }
}

private fun issueIcon(issue: String): ImageVector = when {
    issue.contains("OTP") || issue.contains("kode", true) -> Icons.Outlined.SmsFailed
    issue.contains("sementara", true) || issue.contains("Coba", true) -> Icons.Outlined.Timer
    issue.contains("retas", true) -> Icons.Outlined.Security
    issue.contains("dibatasi", true) -> Icons.Outlined.FactCheck
    else -> Icons.Outlined.Shield
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, click: () -> Unit) {
    Card(modifier.clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusScreen(records: List<RequestRecord>, update: (String, String) -> Unit, openApp: () -> Unit, openGuide: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Status permintaan", "Catatan lokal dari perangkat ini.")
        InfoPanel("Status resmi bukan data XRecover", "XRecover tidak terhubung ke sistem WhatsApp. Untuk mengetahui hasil banding, buka WhatsApp dan periksa pemberitahuan resmi.", Icons.Outlined.Info)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(records.size.toString(), "Catatan", Modifier.weight(1f)); Metric(records.count { it.status == "Dikirim" }.toString(), "Dikirim", Modifier.weight(1f)); Metric(records.count { it.status == "Selesai" }.toString(), "Selesai", Modifier.weight(1f))
        }
        Button(onClick = openApp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Lihat WhatsApp resmi") }
        Text("Catatan terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (records.isEmpty()) Empty("Belum ada catatan", "Susun teks bantuan di tab Pulihkan untuk mulai mencatat.")
        records.take(8).forEach { RecordCard(it, { state -> update(it.id, state) }, null) }
        OutlinedButton(onClick = openGuide, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Panduan banding resmi") }
    }
}

@Composable
private fun HistoryScreen(records: List<RequestRecord>, update: (String, String) -> Unit, delete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Riwayat", "Jenis masalah, waktu, dan status manual saja.")
        InfoPanel("Privasi lokal", "Nomor akun dan isi teks bantuan tidak disimpan di riwayat. Data catatan tersimpan di perangkat.", Icons.Outlined.Security)
        if (records.isEmpty()) Empty("Riwayat masih kosong", "Susun teks bantuan lalu pilih Salin & simpan atau Bagikan.")
        records.forEach { RecordCard(it, { state -> update(it.id, state) }, { delete(it.id) }) }
    }
}

@Composable
private fun RecordCard(record: RequestRecord, update: (String) -> Unit, delete: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text(record.issue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(formatTime(record.time), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (delete != null) IconButton(onClick = delete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.DeleteOutline, "Hapus", modifier = Modifier.size(18.dp)) }
            }
            StatusMenu(record.status, update)
        }
    }
}

@Composable
private fun StatusMenu(status: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val c = when (status) { "Selesai" -> Color(0xFF58C99A); "Dikirim" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Box {
        Surface(Modifier.clickable { expanded = true }, color = c.copy(alpha = .12f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, c.copy(alpha = .4f))) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Box(Modifier.size(6.dp).clip(CircleShape).background(c)); Text(status, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c); Text("⌄", color = c) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { StatusTypes.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { choose(s); expanded = false }) } }
    }
}

@Composable
private fun FaqScreen(openBan: () -> Unit, openOtp: () -> Unit, openTemp: () -> Unit, openCompromised: () -> Unit) {
    val entries = listOf(
        Triple("Ban permanen: apa langkahnya?", "Periksa apakah Request review tersedia di WhatsApp. Jika tersedia, ajukan peninjauan dari aplikasi dan tunggu pemberitahuan. Jika tidak tersedia, WhatsApp menyatakan ban tidak dapat diajukan banding melalui alur tersebut.", openBan),
        Triple("Kode OTP tidak masuk", "Periksa nomor dengan kode negara, pastikan SIM bisa menerima SMS/panggilan, dan tunggu timer berakhir sebelum mencoba kembali. Jangan menebak-nebak kode.", openOtp),
        Triple("Ban sementara", "Pesan ini bisa terkait aplikasi tidak resmi atau scraping. Beralih ke aplikasi resmi dan hentikan aktivitas yang melanggar ketentuan.", openTemp),
        Triple("Akun diretas atau diambil alih", "Daftarkan ulang nomor milikmu melalui WhatsApp resmi dan masukkan kode yang diterima hanya di aplikasi tersebut. Jangan pernah membagikan kode kepada orang lain.", openCompromised),
        Triple("Bisakah XRecover membuka ban otomatis?", "Tidak. XRecover tidak dapat mengakses sistem WhatsApp, membatalkan ban, atau memeriksa hasil banding secara otomatis.", null),
        Triple("Apa yang tidak boleh dibagikan?", "Jangan pernah memberikan kode registrasi, kata sandi, atau PIN verifikasi dua langkah kepada siapa pun, termasuk orang yang mengaku sebagai petugas bantuan.", null)
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Heading("FAQ & panduan", "Jawaban ringkas dan tautan resmi.")
        entries.forEach { (question, answer, link) -> FaqCard(question, answer, link) }
        OutlinedButton(onClick = openBan, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Pusat bantuan resmi WhatsApp") }
    }
}

@Composable
private fun FaqCard(question: String, answer: String, open: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f))) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text(question, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(if (expanded) "−" else "+", fontSize = 20.sp) }
            if (expanded) { Text(answer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (open != null) TextButton(onClick = open, contentPadding = PaddingValues(0.dp)) { Text("Buka panduan resmi"); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp)) } }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.4).sp); Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun InfoPanel(title: String, body: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)); Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f))) { Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun Empty(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f))) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp)); Text(title, fontWeight = FontWeight.Bold); Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, click: () -> Unit) {
    Surface(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = click), color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .15f) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) { Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
}

private fun formatTime(time: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))

private fun loadRecords(prefs: android.content.SharedPreferences): List<RequestRecord> = try {
    val json = JSONArray(prefs.getString(HISTORY, "[]") ?: "[]")
    (0 until json.length()).mapNotNull { i ->
        val item = json.optJSONObject(i) ?: return@mapNotNull null
        RequestRecord(item.optString("id"), item.optLong("time"), item.optString("issue"), item.optString("status", "Belum dikirim")).takeIf { it.id.isNotBlank() && it.issue.isNotBlank() }
    }
} catch (_: Exception) { emptyList() }

private fun storeRecords(prefs: android.content.SharedPreferences, records: List<RequestRecord>) {
    val json = JSONArray()
    records.take(100).forEach { r -> json.put(JSONObject().apply { put("id", r.id); put("time", r.time); put("issue", r.issue); put("status", r.status) }) }
    prefs.edit().putString(HISTORY, json.toString()).apply()
}
