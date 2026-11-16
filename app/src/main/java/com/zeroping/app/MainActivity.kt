package com.zeroping.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val WORKER_BASE = "https://zero-vpn-v31.reza-rahbari-iphone.workers.dev"
private const val APP_VERSION_NAME = "0.6.3"
private const val APP_VERSION_CODE = 11

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF0B429),
                    background = Color(0xFF0B1020),
                    surface = Color(0xFF151B2B),
                ),
            ) {
                ZeroPingDemoApp()
            }
        }
    }
}

data class DemoServer(
    val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val latencyMs: Int? = null,
)

data class UpdateInfo(
    val latestName: String,
    val latestCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val force: Boolean,
)

private fun httpJson(method: String, urlStr: String, body: String? = null): Pair<Int, String> {
    return try {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        conn.disconnect()
        Pair(code, text)
    } catch (e: Exception) {
        Pair(-1, e.message ?: "network error")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroPingDemoApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val servers = remember {
        mutableStateListOf(
            DemoServer("1", "Germany · FRA", "VLESS", "de.example.zero", 42),
            DemoServer("2", "Netherlands · AMS", "VMess", "nl.example.zero", 55),
            DemoServer("3", "Turkey · IST", "Trojan", "tr.example.zero", 68),
        )
    }
    var selectedId by remember { mutableStateOf("1") }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf("آماده · v$APP_VERSION_NAME") }
    var subUrl by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sessionPassword by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("—") }
    var topupText by remember { mutableStateOf("—") }
    var spendText by remember { mutableStateOf("—") }
    var servicesText by remember { mutableStateOf("—") }
    var ordersText by remember { mutableStateOf("—") }
    var refsText by remember { mutableStateOf("—") }
    var brandText by remember { mutableStateOf("ZERO VPN") }
    var authBusy by remember { mutableStateOf(false) }
    var authMsg by remember { mutableStateOf("رمز را از بات → حساب کاربری تنظیم کن") }
    var log by remember { mutableStateOf("Session started · $APP_VERSION_NAME ($APP_VERSION_CODE)") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var syncBusy by remember { mutableStateOf(false) }

    fun appendLog(msg: String) {
        log = msg + "\n" + log.take(1000)
    }

    fun applyProfile(j: JSONObject) {
        accountName = listOf(j.optString("firstName"), j.optString("lastName")).joinToString(" ").trim()
        balanceText = j.opt("balance")?.toString() ?: "0"
        topupText = j.opt("totalTopup")?.toString() ?: "0"
        spendText = j.opt("totalSpend")?.toString() ?: "0"
        servicesText = j.optInt("deliveredOrders", 0).toString()
        ordersText = j.optInt("ordersCount", 0).toString()
        refsText = j.optInt("referrals", 0).toString()
        brandText = j.optString("brand").ifBlank { "ZERO VPN" }
        statusNote = "همگام · موجودی $balanceText · سرویس $servicesText · رفرال $refsText"
    }

    LaunchedEffect(Unit) {
        val (_, text) = withContext(Dispatchers.IO) {
            httpJson("GET", "$WORKER_BASE/api/apk")
        }
        try {
            val j = JSONObject(text.ifBlank { "{}" })
            val code = j.optInt("versionCode", 0)
            val name = j.optString("versionName", "")
            val url = j.optString("downloadUrl").ifBlank { j.optString("url") }
            if (code > APP_VERSION_CODE && url.isNotBlank()) {
                updateInfo = UpdateInfo(
                    latestName = name.ifBlank { code.toString() },
                    latestCode = code,
                    downloadUrl = url,
                    changelog = j.optString("changelog"),
                    force = j.optBoolean("forceUpdate"),
                )
                appendLog("Update available: $name ($code)")
            } else {
                appendLog("Version OK · server=$name ($code)")
            }
        } catch (_: Exception) {
            appendLog("Version check failed")
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("خانه", "سرویس‌ها", "اشتراک", "حساب").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(if (tab == i) "●" else "○") },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("ZEROPiNG", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (loggedIn) "$brandText · $userId · v$APP_VERSION_NAME" else "v$APP_VERSION_NAME · همگام با بات/مینی‌اپ",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))

            updateInfo?.let { u ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("آپدیت موجود: v${u.latestName}", fontWeight = FontWeight.Bold, color = Color(0xFFF0B429))
                        if (u.changelog.isNotBlank()) Text(u.changelog, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.downloadUrl)))
                            } catch (_: Exception) {
                                appendLog("Cannot open update URL")
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("دانلود نسخه جدید") }
                    }
                }
            }

            when (tab) {
                0 -> {
                    val selected = servers.find { it.id == selectedId }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    !loggedIn -> "نیاز به ورود"
                                    connecting -> "در حال اتصال…"
                                    connected -> "متصل"
                                    else -> "قطع"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = when {
                                    connected -> Color(0xFF2EE59D)
                                    loggedIn -> Color(0xFFFFB020)
                                    else -> Color(0xFFFF6B6B)
                                },
                            )
                            Text(selected?.name ?: "سرویسی انتخاب نشده")
                            Text(statusNote, style = MaterialTheme.typography.bodySmall)
                            if (loggedIn) {
                                Text("موجودی: $balanceText · سرویس: $servicesText · رفرال: $refsText", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (!loggedIn) { tab = 3; statusNote = "اول وارد شو"; return@Button }
                                    if (selected == null) return@Button
                                    connecting = true; connected = false
                                    appendLog("Connect → ${selected.name}")
                                    connecting = false; connected = true
                                    statusNote = "متصل (UI) · تونل واقعی در بیلد بعدی"
                                }, enabled = !connecting) { Text(if (connected) "اتصال مجدد" else "اتصال") }
                                OutlinedButton(onClick = {
                                    connected = false; connecting = false; statusNote = "قطع شد"; appendLog("Disconnect")
                                }, enabled = connected || connecting) { Text("قطع") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("لاگ", fontWeight = FontWeight.Bold)
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers, key = { it.id }) { s ->
                            Card(onClick = { selectedId = s.id; tab = 0 }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(s.name, fontWeight = FontWeight.Bold)
                                    Text("${s.protocol} · ${s.address}")
                                    if (s.id == selectedId) Text("✓ انتخاب‌شده", color = Color(0xFFF0B429))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = subUrl, onValueChange = { subUrl = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("لینک اشتراک / کانفیگ") })
                        Button(onClick = {
                            val text = subUrl.trim()
                            if (text.isBlank()) return@Button
                            val name = when {
                                text.startsWith("vless://") -> "Imported VLESS"
                                text.startsWith("vmess://") -> "Imported VMess"
                                text.startsWith("trojan://") -> "Imported Trojan"
                                text.startsWith("ss://") -> "Imported SS"
                                text.startsWith("http") -> "Subscription"
                                else -> "Imported node"
                            }
                            val id = (servers.size + 1).toString()
                            servers.add(0, DemoServer(id, name, name.substringAfter(" "), "imported.node", null))
                            selectedId = id; subUrl = ""; appendLog("Imported $name")
                        }, modifier = Modifier.fillMaxWidth()) { Text("ورود اشتراک") }
                    }
                }
                else -> {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ورود و همگام‌سازی", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = userId, onValueChange = { userId = it.filter { ch -> ch.isDigit() } }, label = { Text("شناسه عددی تلگرام") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("رمز ورود اپ") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text(authMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA6BD))
                        Button(onClick = {
                            if (authBusy) return@Button
                            val uid = userId.trim(); val pass = password
                            if (uid.length < 5 || pass.length < 4) { authMsg = "شناسه و رمز را درست وارد کن"; return@Button }
                            authBusy = true; authMsg = "در حال ورود و همگام‌سازی…"
                            scope.launch {
                                val body = JSONObject().put("userId", uid).put("password", pass).toString()
                                val (code, text) = withContext(Dispatchers.IO) { httpJson("POST", "$WORKER_BASE/api/app/login", body) }
                                authBusy = false
                                try {
                                    val j = JSONObject(text.ifBlank { "{}" })
                                    if (code in 200..299 && j.optBoolean("ok")) {
                                        loggedIn = true; sessionPassword = pass; applyProfile(j)
                                        authMsg = "✅ ورود و همگام‌سازی موفق"
                                        appendLog("Login+sync OK uid=$uid bal=$balanceText"); password = ""
                                    } else {
                                        loggedIn = false; sessionPassword = ""
                                        authMsg = "❌ " + (j.optString("error").ifBlank { "ورود ناموفق ($code)" })
                                        appendLog("Login fail: $authMsg")
                                    }
                                } catch (_: Exception) { loggedIn = false; authMsg = "❌ پاسخ نامعتبر" }
                            }
                        }, enabled = !authBusy, modifier = Modifier.fillMaxWidth()) { Text(if (authBusy) "…" else "ورود و همگام‌سازی") }

                        if (loggedIn) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("پروفایل همگام‌شده", fontWeight = FontWeight.Bold)
                                    Text("نام: ${accountName.ifBlank { "—" }}")
                                    Text("موجودی: $balanceText")
                                    Text("شارژ کل: $topupText · خرج: $spendText")
                                    Text("سرویس‌ها: $servicesText · فاکتورها: $ordersText")
                                    Text("زیرمجموعه/رفرال: $refsText")
                                }
                            }
                            Button(onClick = {
                                if (syncBusy || sessionPassword.isBlank()) return@Button
                                syncBusy = true
                                scope.launch {
                                    val body = JSONObject().put("userId", userId.trim()).put("password", sessionPassword).toString()
                                    val (code, text) = withContext(Dispatchers.IO) { httpJson("POST", "$WORKER_BASE/api/app/profile", body) }
                                    syncBusy = false
                                    try {
                                        val j = JSONObject(text.ifBlank { "{}" })
                                        if (code in 200..299 && j.optBoolean("ok")) {
                                            applyProfile(j); authMsg = "✅ همگام‌سازی مجدد انجام شد"; appendLog("Resync OK")
                                        } else authMsg = "❌ " + (j.optString("error").ifBlank { "همگام‌سازی ناموفق" })
                                    } catch (_: Exception) { authMsg = "❌ خطا در همگام‌سازی" }
                                }
                            }, enabled = !syncBusy, modifier = Modifier.fillMaxWidth()) { Text(if (syncBusy) "…" else "تازه‌سازی از Worker") }
                            OutlinedButton(onClick = {
                                loggedIn = false; sessionPassword = ""; connected = false; authMsg = "خارج شدی"; appendLog("Logout")
                            }, modifier = Modifier.fillMaxWidth()) { Text("خروج") }
                        }
                        Text("نسخه اپ: $APP_VERSION_NAME ($APP_VERSION_CODE) · Worker از /api/apk بررسی می‌شود.")
                    }
                }
            }
        }
    }
}
