package com.jakspinning.wakemypc

import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jakspinning.wakemypc.network.FritzTr064Client
import com.jakspinning.wakemypc.network.pingHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Android 17+ (API 37+) requires this permission to reach devices on the
// LAN at all. Referenced as a literal string rather than
// Manifest.permission.ACCESS_LOCAL_NETWORK since that typed constant may
// not exist on the SDK platform actually installed locally.
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

// How often to silently re-check status in the background. Without this,
// e.g. turning off Wi-Fi just leaves the last-known status on screen
// (looks "on" forever) instead of the next check surfacing the failure.
private const val AUTO_REFRESH_INTERVAL_MS = 5_000L

// The in-app status mascot's whole plate swaps color with PC state (unlike
// the launcher icon, which stays a fixed blue).
private val StatusOnColor = Color(0xFFFFC72C)
private val StatusIdleColor = Color(0xFF0B5FA5)
private val StatusErrorColor = Color(0xFFD64550)
private val DarkLineColor = Color(0xFF12181F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App content is always light (see Theme.WakeMyPc), so force dark
        // status/nav bar icons regardless of system dark mode — otherwise
        // "auto" can pick light icons that vanish against our white background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            RouterWakeTheme {
                WakeMyPcScreen()
            }
        }
    }
}

@Composable
fun WakeMyPcScreen() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    var config by remember { mutableStateOf(settingsRepository.load()) }
    var editingSetup by remember { mutableStateOf(false) }

    val currentConfig = config
    if (currentConfig == null || editingSetup) {
        OnboardingScreen(
            initial = if (editingSetup) currentConfig else null,
            onSave = { newConfig ->
                settingsRepository.save(newConfig)
                config = newConfig
                editingSetup = false
            },
            onCancel = if (editingSetup) { { editingSetup = false } } else null,
        )
    } else {
        MainScreen(config = currentConfig, onEditSetup = { editingSetup = true })
    }
}

@Composable
private fun MainScreen(config: FritzBoxConfig, onEditSetup: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val client = remember(config) {
        FritzTr064Client(
            host = config.host,
            port = config.port,
            username = config.username,
            password = config.password,
        )
    }

    var isWaking by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("unknown") }

    var hasLocalNetworkPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestLocalNetworkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocalNetworkPermission = granted }

    suspend fun refreshStatus() {
        isRefreshing = true
        // Pinged directly rather than asked of the FritzBox: TR-064's host
        // table reports link presence, and a PC with "Wake on Magic Packet"
        // enabled (required for WOL to work) keeps its Ethernet link up
        // even when fully shut down, so the FritzBox would report it as
        // connected forever.
        val reachable = pingHost(config.pcLanIp)
        isRefreshing = false
        status = if (reachable) "on" else "off"
    }

    // Checks immediately once permission is available (instead of making the
    // user tap "Refresh status" first), then keeps polling in the background
    // so the screen doesn't show a stale status indefinitely.
    LaunchedEffect(hasLocalNetworkPermission, config) {
        while (true) {
            if (hasLocalNetworkPermission) refreshStatus()
            delay(AUTO_REFRESH_INTERVAL_MS)
        }
    }

    val mascotBackground = when {
        status == "on" -> StatusOnColor
        status.startsWith("error") -> StatusErrorColor
        else -> StatusIdleColor
    }
    val mascotLineColor = if (status == "on") DarkLineColor else Color.White

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            RouterMascotIndicator(backgroundColor = mascotBackground, lineColor = mascotLineColor)
            Text(text = "Status: $status", style = MaterialTheme.typography.headlineSmall)

            if (!hasLocalNetworkPermission) {
                Text(
                    text = "This app needs local network access to reach your FritzBox.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { requestLocalNetworkPermission.launch(LOCAL_NETWORK_PERMISSION) }) {
                    Text("Grant local network access")
                }
            }

            Button(
                enabled = !isWaking && hasLocalNetworkPermission,
                onClick = {
                    isWaking = true
                    scope.launch {
                        val result = client.wakeOnLan(config.pcMacAddress)
                        isWaking = false
                        val message = result.fold(
                            onSuccess = { "Wake-on-LAN sent" },
                            onFailure = { "Failed: ${it.message}" },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                },
            ) {
                Text(if (isWaking) "Sending…" else "Turn On")
            }

            OutlinedButton(
                enabled = !isRefreshing && hasLocalNetworkPermission,
                onClick = {
                    scope.launch {
                        refreshStatus()
                        Toast.makeText(context, "Status refreshed", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(if (isRefreshing) "Checking…" else "Refresh status")
            }

            TextButton(onClick = onEditSetup) {
                Text("Edit setup")
            }
        }
    }
}
