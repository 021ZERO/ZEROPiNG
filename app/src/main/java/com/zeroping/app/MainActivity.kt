package com.zeroping.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroPingDemoApp() {
    val scope = rememberCoroutineScope()
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
    var statusNote by remember { mutableStateOf("آماده · ورود با شناسه + رمز") }
    var subUrl by remember { mutableStateOf("") }
    var workerBase by remember {
        mutableStateOf("https://zero-vpn-v31.reza-rahbari-iphone.workers.dev")
    }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var accountName by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("—") }
    var authBusy by remember { mutableStateOf(false) }
    var authMsg by remember { mutableStateOf("رمز را از بات → حساب کاربری تنظیم کن") }
    var log by remember { mutableStateOf("Session started") }

    fun appendLog(msg: String) {
        log = msg + "\n" + log.take(900)
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
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
        ) {
            Text("ZEROPiNG", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (loggedIn) "وارد شده · $userId" else "همگام با بات و مینی‌اپ",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
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
                            Text("${selected?.protocol ?: "—"} · ${selected?.address ?: "—"}")
                            Text(statusNote, style = MaterialTheme.typography.bodySmall)
                            if (loggedIn && accountName.isNotBlank()) {
                                Text("حساب: $accountName · موجودی: $balanceText", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (!loggedIn) { statusNote = "اول از تب حساب وارد شو"; tab = 3; return@Button }
                                    if (selected == null) { statusNote = "اول سرویس را انتخاب کن"; return@Button }
                                    connecting = true; connected = false
                                    appendLog("Connect → ${selected.name}")
                                    connecting = false; connected = true
                                    statusNote = "متصل (تست UI) · تونل واقعی در بیلد libbox"
                                }, enabled = !connecting) { Text(if (connected) "اتصال مجدد" else "اتصال") }
                                OutlinedButton(onClick = {
                                    connected = false; connecting = false; statusNote = "قطع شد"; appendLog("Disconnect")
                                }, enabled = connected || connecting) { Text("قطع") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("لاگ نشست", fontWeight = FontWeight.Bold)
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers, key = { it.id }) { s ->
                            Card(onClick = { selectedId = s.id; appendLog("Select ${s.name}"); tab = 0 }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(s.name, fontWeight = FontWeight.Bold)
                                    Text("${s.protocol} · ${s.address}")
                                    Text(s.latencyMs?.let { "$it ms" } ?: "—")
                                    if (s.id == selectedId) Text("✓ انتخاب‌شده", color = Color(0xFFF0B429))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لینک اشتراک / کانفیگ")
                        OutlinedTextField(value = subUrl, onValueChange = { subUrl = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("vless:// یا لینک ساب") })
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
                            selectedId = id; appendLog("Imported: $name"); subUrl = ""
                        }, modifier = Modifier.fillMaxWidth()) { Text("ورود اشتراک") }
                    }
                }
                else -> {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ورود امن (همگام با بات/مینی‌اپ)", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = workerBase, onValueChange = { workerBase = it }, label = { Text("آدرس Worker") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = userId, onValueChange = { userId = it.filter { ch -> ch.isDigit() } }, label = { Text("شناسه عددی تلگرام") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("رمز ورود اپ") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text(authMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA6BD))
                        Button(onClick = {
                            if (authBusy) return@Button
                            val uid = userId.trim(); val pass = password; val base = workerBase.trim().trimEnd('/')
                            if (uid.length < 5 || pass.length < 4) { authMsg = "شناسه و رمز را درست وارد کن"; return@Button }
                            authBusy = true; authMsg = "در حال بررسی…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val url = URL("$base/api/app/login")
                                        val conn = (url.openConnection() as HttpURLConnection).apply {
                                            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
                                            setRequestProperty("Content-Type", "application/json")
                                        }
                                        val body = JSONObject().put("userId", uid).put("password", pass).toString()
                                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                                        val code = conn.responseCode
                                        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                                        conn.disconnect(); Pair(code, text)
                                    } catch (e: Exception) { Pair(-1, e.message ?: "network error") }
                                }
                                authBusy = false
                                val (code, text) = result
                                try {
                                    val j = JSONObject(text.ifBlank { "{}" })
                                    if (code in 200..299 && j.optBoolean("ok")) {
                                        loggedIn = true
                                        accountName = listOf(j.optString("firstName"), j.optString("lastName")).joinToString(" ").trim()
                                        balanceText = j.opt("balance")?.toString() ?: "—"
                                        val delivered = j.optInt("deliveredOrders", 0)
                                        val orders = j.optInt("ordersCount", 0)
                                        authMsg = "✅ ورود موفق · سرویس $delivered · فاکتور $orders"
                                        statusNote = "وارد شدی · موجودی $balanceText · سرویس $delivered"
                                        appendLog("Login OK uid=$uid"); password = ""
                                    } else {
                                        loggedIn = false
                                        authMsg = "❌ " + (j.optString("error").ifBlank { "ورود ناموفق ($code)" })
                                        appendLog("Login fail: $authMsg")
                                    }
                                } catch (_: Exception) {
                                    loggedIn = false; authMsg = "❌ پاسخ نامعتبر از سرور"; appendLog("Login parse error: $text")
                                }
                            }
                        }, enabled = !authBusy, modifier = Modifier.fillMaxWidth()) { Text(if (authBusy) "…" else "ورود") }
                        if (loggedIn) {
                            OutlinedButton(onClick = {
                                loggedIn = false; connected = false; accountName = ""; balanceText = "—"; authMsg = "خارج شدی"; appendLog("Logout")
                            }, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
                        }
                        Text("همگام: فروشگاه · سرویس‌ها · کیف پول · حساب · پشتیبانی · اپ. رمز از بات یا مینی‌اپ → حساب.")
                    }
                }
            }
        }
    }
}
