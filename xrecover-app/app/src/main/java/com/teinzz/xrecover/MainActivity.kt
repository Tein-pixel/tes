package com.teinzz.xrecover

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

private const val PREFS = "xrecover_settings"
private const val HISTORY = "history"
private const val THEME = "theme"
private val Accent = Color(0xFF9AB9FF)
private val Issues = listOf("Banned sementara", "Banned permanen", "Akun dibatasi", "OTP / kode tidak masuk", "Coba lagi nanti", "Akun diretas", "Masalah lain")
private val Statuses = listOf("Belum dikirim", "Dikirim", "Selesai")
private data class Record(val id: String, val time: Long, val issue: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { XRecoverApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XRecoverApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString(THEME, "Gelap") ?: "Gelap") }
    val dark = when (theme) { "Gelap" -> true; "Terang" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(
        primary = Accent, onPrimary = Color(0xFF101828), background = Color(0xFF0C0D0F),
        surface = Color(0xFF15171A), surfaceVariant = Color(0xFF202328),
        onBackground = Color(0xFFF4F5F6), onSurface = Color(0xFFF4F5F6),
        onSurfaceVariant = Color(0xFFACB0B7), outline = Color(0xFF383B42)
    ) else lightColorScheme(
        primary = Color(0xFF315EA8), background = Color(0xFFF4F5F7), surface = Color.White,
        surfaceVariant = Color(0xFFE9ECF1), onSurface = Color(0xFF191B1F),
        onSurfaceVariant = Color(0xFF60656D), outline = Color(0xFFD0D4DB)
    )
    MaterialTheme(colorScheme = colors) {
        val records = remember { mutableStateListOf<Record>().apply { addAll(loadRecords(prefs)) } }
        val scope = rememberCoroutineScope()
        var page by remember { mutableStateOf("Pulihkan") }
        var issue by remember { mutableStateOf(Issues.first()) }
        var phone by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        var startedAt by remember { mutableStateOf("") }
        var triedSteps by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var draft by remember { mutableStateOf("") }
        var previewOpen by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        var smtpOpen by remember { mutableStateOf(false) }
        var smtpHost by remember { mutableStateOf(prefs.getString("smtp_host", "") ?: "") }
        var smtpPort by remember { mutableStateOf(prefs.getString("smtp_port", "465") ?: "465") }
        var smtpUser by remember { mutableStateOf(prefs.getString("smtp_user", "") ?: "") }
        var smtpTo by remember { mutableStateOf(prefs.getString("smtp_to", "") ?: "") }
        var smtpPassword by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }

        fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        fun openUrl(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { toast("Tautan tidak dapat dibuka.") }
        }
        fun saveHistory() { storeRecords(prefs, records) }
        fun addRecord(status: String = "Belum dikirim") {
            records.add(0, Record(UUID.randomUUID().toString(), System.currentTimeMillis(), issue, status))
            while (records.size > 100) records.removeAt(records.lastIndex)
            saveHistory()
        }
        fun makeDraft() {
            val lines = buildList {
                add("Jenis masalah: " + issue)
                if (phone.isNotBlank()) add("Nomor akun: " + phone.trim())
                if (errorMessage.isNotBlank()) add("Pesan layar: " + errorMessage.trim())
                if (startedAt.isNotBlank()) add("Mulai terjadi: " + startedAt.trim())
                if (triedSteps.isNotBlank()) add("Langkah yang sudah dicoba: " + triedSteps.trim())
                if (notes.isNotBlank()) add("Keterangan tambahan: " + notes.trim())
            }.joinToString("\n")
            draft = "Yth. Tim Dukungan WhatsApp,\n\nSaya meminta bantuan untuk masalah akun berikut.\n" +
                lines + "\n\nMohon periksa masalah ini dan informasikan langkah resmi yang perlu dilakukan.\n\nTerima kasih."
            previewOpen = true
        }
        fun sendSmtp() {
            val portValue = smtpPort.toIntOrNull()
            if (smtpHost.isBlank() || smtpUser.isBlank() || smtpTo.isBlank() || smtpPassword.isBlank() ||
                portValue == null || (portValue != 465 && portValue != 587)) {
                previewOpen = false
                smtpOpen = true
                toast("Isi konfigurasi SMTP dan app password terlebih dahulu.")
                return
            }
            val sender = smtpUser.trim()
            val recipient = smtpTo.trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(sender).matches() || !Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
                toast("Alamat pengirim atau penerima tidak valid.")
                return
            }
            val host = smtpHost.trim()
            val password = smtpPassword
            val body = draft
            prefs.edit().putString("smtp_host", host).putString("smtp_port", portValue.toString())
                .putString("smtp_user", sender).putString("smtp_to", recipient).apply()
            sending = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { sendEmailSmtp(host, portValue, sender, password, recipient, "Permintaan bantuan akun WhatsApp", body) }
                }
                sending = false
                smtpPassword = ""
                result.onSuccess {
                    addRecord("Dikirim")
                    previewOpen = false
                    toast("Server SMTP menerima email.")
                }.onFailure {
                    toast("SMTP gagal. Periksa host, port, jaringan, penerima, dan app password.")
                }
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("XRecover", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Panduan akun WhatsApp • v1.5.0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } },
                    actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Outlined.Settings, "Pengaturan") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf("Pulihkan" to Icons.Outlined.Shield, "Status" to Icons.Outlined.TaskAlt,
                        "FAQ" to Icons.Outlined.HelpOutline, "Riwayat" to Icons.Outlined.History).forEach { pair ->
                        NavigationBarItem(selected = page == pair.first, onClick = { page = pair.first },
                            icon = { Icon(pair.second, pair.first) }, label = { Text(pair.first, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            when (page) {
                "Pulihkan" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AKUN WHATSAPP\nBERMASALAH?", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 29.sp)
                    Text("Susun laporan yang rapi, lalu periksa sebelum mengirim.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Jenis masalah", fontWeight = FontWeight.SemiBold)
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Surface(Modifier.fillMaxWidth().clickable { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(13.dp)) {
                                    Text(issue + "   ▾", Modifier.padding(14.dp), fontSize = 14.sp)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    Issues.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { issue = value; expanded = false }) }
                                }
                            }
                            OutlinedTextField(phone, { phone = it.take(25) }, Modifier.fillMaxWidth(), label = { Text("Nomor akun (opsional)") }, placeholder = { Text("+62…") }, singleLine = true)
                            OutlinedTextField(errorMessage, { errorMessage = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Pesan yang muncul") }, placeholder = { Text("Salin teks error…") }, minLines = 2, maxLines = 3)
                            OutlinedTextField(startedAt, { startedAt = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Kapan mulai terjadi") }, placeholder = { Text("Contoh: 2 hari lalu") }, singleLine = true)
                            OutlinedTextField(triedSteps, { triedSteps = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Langkah yang sudah dicoba") }, minLines = 2, maxLines = 3)
                            OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Keterangan tambahan") }, minLines = 2, maxLines = 3)
                            Button(onClick = { makeDraft() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Outlined.Send, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Susun teks bantuan")
                            }
                        }
                    }
                    OutlinedButton(onClick = { openUrl("https://www.whatsapp.com/contact/?lang=id&subject=messenger") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kontak resmi WhatsApp")
                    }
                    Text("XRecover bukan aplikasi resmi WhatsApp. Aplikasi ini tidak bisa membatalkan ban atau menjamin permintaan disetujui.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "Status" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Status permintaan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Catatan lokal; bukan hasil resmi WhatsApp.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Total " + records.size + " • Dikirim " + records.count { it.status == "Dikirim" } + " • Selesai " + records.count { it.status == "Selesai" }, fontSize = 13.sp)
                    records.take(8).forEach { record ->
                        RecordCard(record, onStatus = { state ->
                            val i = records.indexOfFirst { it.id == record.id }
                            if (i >= 0) records[i] = record.copy(status = state)
                            saveHistory()
                        }, onDelete = null)
                    }
                }
                "FAQ" -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FAQ & panduan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    FaqItem("Bagaimana mengajukan peninjauan?", "Buka WhatsApp resmi dan periksa apakah opsi Request review tersedia. Ikuti alur dalam aplikasi atau gunakan formulir kontak resmi.")
                    FaqItem("Kode OTP tidak masuk", "Periksa nomor dan kode negara, pastikan SIM aktif, lalu tunggu timer. Jangan bagikan kode registrasi kepada siapa pun.")
                    FaqItem("Aplikasi ini bisa membuka ban otomatis?", "Tidak. XRecover tidak terhubung ke sistem keputusan WhatsApp dan tidak dapat membatalkan ban.")
                    FaqItem("Data apa yang perlu dirahasiakan?", "Jangan masukkan kata sandi, OTP, PIN verifikasi dua langkah, atau kode pemulihan ke laporan.")
                    OutlinedButton(onClick = { openUrl("https://faq.whatsapp.com/") }, modifier = Modifier.fillMaxWidth()) { Text("Buka Pusat Bantuan") }
                }
                else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Riwayat", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Hanya jenis masalah, waktu, dan status yang disimpan; nomor serta isi laporan tidak disimpan.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (records.isEmpty()) Text("Belum ada catatan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    records.forEach { record ->
                        RecordCard(record, onStatus = { state ->
                            val i = records.indexOfFirst { it.id == record.id }
                            if (i >= 0) records[i] = record.copy(status = state)
                            saveHistory()
                        }, onDelete = { records.removeAll { it.id == record.id }; saveHistory() })
                    }
                }
            }
        }

        if (previewOpen) AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text("Periksa teks bantuan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Periksa isi dan alamat penerima sebelum mengirim.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth().height(250.dp), maxLines = 12)
                    if (smtpTo.isNotBlank()) Text("Penerima SMTP: " + smtpTo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("XRecover", draft))
                    addRecord()
                    previewOpen = false
                    toast("Teks disalin.")
                }) { Text("Salin") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, draft) }
                        context.startActivity(Intent.createChooser(intent, "Bagikan teks"))
                    }) { Text("Bagikan") }
                    TextButton(onClick = { sendSmtp() }, enabled = !sending) { Text("Kirim SMTP") }
                }
            }
        )

        if (settingsOpen) AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Pengaturan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tema")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Gelap", "Terang", "Sistem").forEach { value ->
                            FilterChip(selected = theme == value, onClick = { theme = value; prefs.edit().putString(THEME, value).apply() }, label = { Text(value) })
                        }
                    }
                    HorizontalDivider()
                    Text("SMTP manual", fontWeight = FontWeight.SemiBold)
                    Text("Server dan penerima harus kamu tentukan sendiri. Tidak ada penerima SMTP tersembunyi. Password tidak disimpan.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { settingsOpen = false; smtpOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Konfigurasi SMTP") }
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Selesai") } }
        )

        if (smtpOpen) AlertDialog(
            onDismissRequest = { smtpOpen = false; if (draft.isNotBlank()) previewOpen = true },
            title = { Text("Konfigurasi SMTP") },
            text = {
                Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gunakan kredensial milikmu sendiri. Password hanya dipakai untuk koneksi TLS ke host yang terlihat dan tidak ditulis ke penyimpanan.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(smtpHost, { smtpHost = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Host SMTP") }, placeholder = { Text("smtp.gmail.com") })
                    OutlinedTextField(smtpPort, { smtpPort = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Port 465 SSL / 587 STARTTLS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(smtpUser, { smtpUser = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Email pengirim / username") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(smtpPassword, { smtpPassword = it.take(300) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("App password SMTP") }, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(smtpTo, { smtpTo = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Alamat penerima") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    Text("Hanya port 465/587 dengan TLS yang diterima. Jangan gunakan kata sandi utama atau OTP.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val port = smtpPort.toIntOrNull()
                    if (smtpHost.isBlank() || port == null || (port != 465 && port != 587) ||
                        !Patterns.EMAIL_ADDRESS.matcher(smtpUser.trim()).matches() ||
                        !Patterns.EMAIL_ADDRESS.matcher(smtpTo.trim()).matches()) {
                        toast("Host, port, pengirim, atau penerima tidak valid.")
                    } else {
                        prefs.edit().putString("smtp_host", smtpHost.trim()).putString("smtp_port", port.toString())
                            .putString("smtp_user", smtpUser.trim()).putString("smtp_to", smtpTo.trim()).apply()
                        smtpOpen = false
                        if (draft.isNotBlank()) previewOpen = true
                        toast("Konfigurasi disimpan tanpa password.")
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { smtpPassword = ""; smtpOpen = false; if (draft.isNotBlank()) previewOpen = true }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun RecordCard(record: Record, onStatus: (String) -> Unit, onDelete: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.issue, fontWeight = FontWeight.SemiBold)
                    Text(formatTime(record.time), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onDelete?.let { IconButton(onClick = it) { Icon(Icons.Outlined.DeleteOutline, "Hapus") } }
            }
            FilterChip(selected = false, onClick = { expanded = true }, label = { Text(record.status) })
            if (expanded) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Statuses.forEach { state -> TextButton(onClick = { onStatus(state); expanded = false }) { Text(state, fontSize = 11.sp) } }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(question, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (expanded) "−" else "+")
            }
            if (expanded) Text(answer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sendEmailSmtp(host: String, port: Int, username: String, password: String, recipient: String, subject: String, body: String) {
    require(port == 465 || port == 587)
    val props = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.host", host)
        put("mail.smtp.port", port.toString())
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "15000")
        put("mail.smtp.writetimeout", "15000")
        if (port == 465) {
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.ssl.checkserveridentity", "true")
        } else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.checkserveridentity", "true")
        }
    }
    val session = Session.getInstance(props)
    val message = MimeMessage(session).apply {
        setFrom(InternetAddress(username, true))
        setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, true))
        setSubject(subject, "UTF-8")
        setText(body, "UTF-8")
    }
    Transport.send(message, username, password)
}

private fun loadRecords(prefs: android.content.SharedPreferences): List<Record> = try {
    val array = JSONArray(prefs.getString(HISTORY, "[]") ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        Record(item.optString("id"), item.optLong("time"), item.optString("issue"), item.optString("status", "Belum dikirim"))
            .takeIf { it.id.isNotBlank() && it.issue.isNotBlank() }
    }
} catch (_: Exception) { emptyList() }

private fun storeRecords(prefs: android.content.SharedPreferences, records: List<Record>) {
    val array = JSONArray()
    records.take(100).forEach { record ->
        array.put(JSONObject().apply {
            put("id", record.id)
            put("time", record.time)
            put("issue", record.issue)
            put("status", record.status)
        })
    }
    prefs.edit().putString(HISTORY, array.toString()).apply()
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))
