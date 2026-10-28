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
private val Accent = Color(0xFF9AB9FF)
private val IssueTypes = listOf("Banned sementara", "Banned permanen", "Akun dibatasi (Restricted)", "OTP / kode tidak masuk", "Coba lagi nanti", "Akun diretas / diambil alih", "Masalah lain")
private val StatusTypes = listOf("Belum dikirim", "Dikirim", "Selesai")

private data class RequestRecord(val id: String, val time: Long, val issue: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
            var theme by remember { mutableStateOf(prefs.getString(THEME, "Gelap") ?: "Gelap") }
            val dark = when (theme) { "Gelap" -> true; "Terang" -> false; else -> isSystemInDarkTheme() }
            val scheme = if (dark) darkColorScheme(
                primary = Accent, onPrimary = Color(0xFF0D1424), background = Color(0xFF0C0D0F),
                surface = Color(0xFF15171A), surfaceVariant = Color(0xFF202328),
                onBackground = Color(0xFFF4F5F6), onSurface = Color(0xFFF4F5F6),
                onSurfaceVariant = Color(0xFFACB0B7), outline = Color(0xFF383B42)
            ) else lightColorScheme(
                primary = Color(0xFF315EA8), onPrimary = Color.White, background = Color(0xFFF4F5F7),
                surface = Color.White, surfaceVariant = Color(0xFFE9ECF1), onBackground = Color(0xFF191B1F),
                onSurface = Color(0xFF191B1F), onSurfaceVariant = Color(0xFF60656D), outline = Color(0xFFD0D4DB)
            )
            MaterialTheme(colorScheme = scheme) {
                XRecover(prefs, dark, theme) { next -> theme = next; prefs.edit().putString(THEME, next).apply() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XRecover(prefs: android.content.SharedPreferences, dark: Boolean, theme: String, setTheme: (String) -> Unit) {
    val records = remember { mutableStateListOf<RequestRecord>().apply { addAll(loadRecords(prefs)) } }
    var page by remember { mutableStateOf("Pulihkan") }
    var settings by remember { mutableStateOf(false) }
    var draftDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf(IssueTypes[0]) }
    var phone by remember { mutableStateOf("") }
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
        val number = phone.trim().takeIf { it.isNotEmpty() }?.let { "Nomor WhatsApp terkait: " + it + "\\n" } ?: "[Tambahkan nomor WhatsApp dengan kode negara, misalnya +62…, jika formulir dukungan memintanya.]"
        val extra = detail.trim().takeIf { it.isNotEmpty() }?.let { it } ?: "[Jelaskan pesan kesalahan persis seperti yang muncul, kapan masalah mulai terjadi, dan langkah yang sudah dicoba. Jangan sertakan kode OTP atau PIN.]"
        val issueDescription = when (issue) {
            "Banned sementara" -> "Akun saya menampilkan pemberitahuan pembatasan atau ban sementara. Saya ingin mengetahui penyebab yang mungkin dan langkah resmi untuk kembali menggunakan aplikasi. Jika ada instruksi atau hitung mundur pada layar, saya akan mengikuti instruksi tersebut."
            "Banned permanen" -> "Akun saya menampilkan pemberitahuan bahwa akun tidak diizinkan menggunakan WhatsApp. Jika opsi peninjauan tersedia di aplikasi, saya ingin mengajukan pemeriksaan atas keputusan tersebut. Mohon tinjau kasus ini dan beri tahu apakah ada langkah resmi lain yang dapat saya lakukan."
            "Akun dibatasi (Restricted)" -> "Akun saya tampak dibatasi dan beberapa fitur tidak dapat digunakan sebagaimana mestinya. Mohon jelaskan alasan pembatasan dan langkah resmi untuk memulihkan akses fitur jika memungkinkan."
            "OTP / kode tidak masuk" -> "Saya belum menerima kode registrasi atau kode verifikasi yang diperlukan untuk masuk. Saya akan memastikan nomor dan kode negara benar serta mengikuti waktu tunggu yang ditampilkan. Mohon sarankan langkah pemecahan masalah resmi berikutnya."
            "Coba lagi nanti" -> "Saya melihat pesan untuk mencoba lagi nanti saat meminta kode atau menggunakan akun. Saya akan mengikuti waktu tunggu yang ditampilkan dan menghindari permintaan berulang. Mohon informasikan langkah yang tepat jika masalah tetap terjadi setelah waktu tunggu berakhir."
            "Akun diretas / diambil alih" -> "Saya khawatir akun saya diakses atau diambil alih pihak lain. Mohon panduan untuk mengamankan kembali akun melalui proses verifikasi resmi. Saya tidak akan membagikan kode registrasi atau PIN kepada siapa pun."
            else -> "Saya mengalami masalah pada akun atau aplikasi WhatsApp dan memerlukan bantuan untuk menentukan langkah penanganan yang benar."
        }
        draft = "Yth. Tim Dukungan WhatsApp,\\n\\nSaya menghubungi tim dukungan untuk meminta bantuan terkait masalah akun saya.\\n\\nJENIS MASALAH\\n" + issue + "\\n\\nKRONOLOGI DAN KETERANGAN\\n" + extra + "\\n\\nINFORMASI AKUN\\n" + number + "\\n\\nRINCIAN MASALAH\\n" + issueDescription + "\\n\\nPERMOHONAN\\nMohon periksa informasi yang saya sampaikan dan arahkan saya ke langkah pemulihan atau peninjauan resmi yang sesuai. Jika ada informasi tambahan yang diperlukan, mohon beri tahu melalui saluran dukungan resmi. Saya bersedia mengikuti proses verifikasi yang tersedia di aplikasi resmi.\\n\\nTerima kasih atas waktu dan bantuannya.\\n\\nHormat saya,\\nPengguna WhatsApp"
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
                    dark, issue, { issue = it }, phone, { phone = it.take(25) }, detail, { detail = it.take(300) },
                    { buildDraft() }, { openWhatsApp() },
                    { openUrl("https://faq.whatsapp.com/465883178708358/") },
                    { openUrl("https://faq.whatsapp.com/684051319521343/") }
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
                Text("Periksa dan edit sebelum dikirim. XRecover tidak mengirim apa pun ke WhatsApp secara otomatis.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick = { draftDialog = false }) { Text("Tutup") }
            }
        }
    )

    if (settings) AlertDialog(
        onDismissRequest = { settings = false }, title = { Text("Pengaturan & keamanan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tema tampilan", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Gelap", "Terang", "Sistem").forEach { ChoiceChip(it, theme == it) { setTheme(it) } } }
                HorizontalDivider()
                Text("Privasi", fontWeight = FontWeight.SemiBold)
                Text("Nomor dan keterangan hanya digunakan untuk menyusun teks selama layar ini aktif. Riwayat hanya menyimpan jenis masalah, tanggal, dan status manual.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tidak ada login, OTP, kata sandi, API key, izin internet, atau koneksi server XRecover. Aplikasi ini bukan produk resmi WhatsApp.", fontSize = 13.sp)
                Text("Versi 1.0.1 • Android 10+", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Selesai") } }
    )
}

@Composable
private fun RecoverScreen(dark: Boolean, issue: String, setIssue: (String) -> Unit, phone: String, setPhone: (String) -> Unit, detail: String, setDetail: (String) -> Unit, generate: () -> Unit, openApp: () -> Unit, openBan: () -> Unit, openOtp: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AKUN WHATSAPP\nBERMASALAH?", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-.5).sp)
        Text("Pilih masalah dan ikuti langkah resmi.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha = .17f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Shield, null, tint = if (dark) Accent else Color(0xFF315EA8), modifier = Modifier.size(24.dp)) }
                    Column { Text("Pulihkan akses", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Panduan lokal • tanpa login", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("Jenis masalah", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                IssueMenu(issue, setIssue)
                OutlinedTextField(value = phone, onValueChange = setPhone, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nomor WhatsApp (opsional)") }, placeholder = { Text("+62…") }, shape = RoundedCornerShape(13.dp))
                Text("Nomor tidak disimpan dalam riwayat.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = detail, onValueChange = setDetail, modifier = Modifier.fillMaxWidth().height(110.dp), label = { Text("Keterangan tambahan") }, placeholder = { Text("Kapan masalah mulai terjadi…") }, maxLines = 4, shape = RoundedCornerShape(13.dp))
                Text(detail.length.toString() + "/300", modifier = Modifier.align(Alignment.End), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = generate, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.Article, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Susun teks bantuan", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = openApp, modifier = Modifier.fillMaxWidth().height(45.dp), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Buka WhatsApp") }
            }
        }
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
