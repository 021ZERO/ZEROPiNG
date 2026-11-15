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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var statusNote by remember { mutableStateOf("آماده · نسخه تست رابط") }
    var subUrl by remember { mutableStateOf("") }
    var workerBase by remember { mutableStateOf("https://zero-vpn-v31.reza-rahbari-iphone.workers.dev") }
    var userId by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("Session started") }

    fun appendLog(msg: String) {
        log = msg + "\n" + log.take(800)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("خانه", "سرورها", "اشتراک", "تنظیمات").forEachIndexed { i, label ->
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
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
        ) {
            Text("ZEROPiNG", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("خدای اپ‌های VPN · نسخه تست قابل استفاده", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> {
                    val selected = servers.find { it.id == selectedId }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    connecting -> "در حال اتصال…"
                                    connected -> "متصل"
                                    else -> "قطع"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = if (connected) Color(0xFF2EE59D) else Color(0xFFFFB020),
                            )
                            Text(selected?.name ?: "سروری انتخاب نشده")
                            Text("${selected?.protocol ?: "—"} · ${selected?.address ?: "—"}")
                            Text(statusNote, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (selected == null) {
                                            statusNote = "اول از تب سرورها یکی را انتخاب کن"
                                            return@Button
                                        }
                                        connecting = true
                                        connected = false
                                        statusNote = "شبیه‌سازی اتصال به ${selected.name}…"
                                        appendLog("Connect → ${selected.name}")
                                        connecting = false
                                        connected = true
                                        statusNote = "متصل (حالت تست UI) · در بیلد کامل تونل واقعی فعال می‌شود"
                                    },
                                    enabled = !connecting,
                                ) { Text(if (connected) "اتصال مجدد" else "اتصال") }
                                OutlinedButton(
                                    onClick = {
                                        connected = false
                                        connecting = false
                                        statusNote = "قطع شد"
                                        appendLog("Disconnect")
                                    },
                                    enabled = connected || connecting,
                                ) { Text("قطع") }
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
                            Card(onClick = {
                                selectedId = s.id
                                appendLog("Select ${s.name}")
                                tab = 0
                            }, modifier = Modifier.fillMaxWidth()) {
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
                        Text("لینک اشتراک / کانفیگ را بچسبان")
                        OutlinedTextField(
                            value = subUrl,
                            onValueChange = { subUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            label = { Text("vless:// … یا لینک ساب") },
                        )
                        Button(
                            onClick = {
                                val text = subUrl.trim()
                                if (text.isBlank()) {
                                    appendLog("Import empty")
                                    statusNote = "متن خالی است"
                                    return@Button
                                }
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
                                selectedId = id
                                appendLog("Imported: $name")
                                statusNote = "وارد شد · از تب سرورها ببین"
                                subUrl = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("ورود اشتراک") }
                        OutlinedButton(
                            onClick = {
                                servers.clear()
                                selectedId = ""
                                appendLog("Cleared servers")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("پاک کردن لیست") }
                    }
                }
                else -> {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = workerBase,
                            onValueChange = { workerBase = it },
                            label = { Text("آدرس Worker") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = { Text("شناسه تلگرام") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {
                            appendLog("Saved settings worker=$workerBase uid=$userId")
                            statusNote = "تنظیمات ذخیره شد (محلی)"
                        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
                        Text("این بیلد برای تست رابط و جریان کار است.")
                        Text("تونل واقعی در بیلد کامل با libbox فعال می‌شود.")
                        Text("بات: دریافت اپ / آپلود اپ · Worker 1.6.55+")
                    }
                }
            }
        }
    }
}
