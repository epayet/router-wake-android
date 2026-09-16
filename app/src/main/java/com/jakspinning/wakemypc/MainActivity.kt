package com.jakspinning.wakemypc

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jakspinning.wakemypc.network.FritzTr064Client
import kotlinx.coroutines.launch

// Android 17+ (API 37+) requires this permission to reach devices on the
// LAN at all. Referenced as a literal string rather than
// Manifest.permission.ACCESS_LOCAL_NETWORK since that typed constant may
// not exist on the SDK platform actually installed locally.
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                WakeMyPcScreen()
            }
        }
    }
}

@Composable
fun WakeMyPcScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // v0: credentials/host/MAC/IP come from the gitignored Config.kt
    // (copy Config.kt.example to Config.kt and fill in real values — see README).
    val client = remember {
        FritzTr064Client(
            host = Config.FRITZBOX_HOST,
            port = Config.TR064_PORT,
            username = Config.FRITZBOX_USERNAME,
            password = Config.FRITZBOX_PASSWORD,
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
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
                        val result = client.wakeOnLan(Config.PC_MAC_ADDRESS)
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
                    isRefreshing = true
                    scope.launch {
                        val result = client.getHostStatus(Config.PC_LAN_IP)
                        isRefreshing = false
                        status = result.fold(
                            onSuccess = { active -> if (active) "on" else "off" },
                            onFailure = { "error: ${it.message}" },
                        )
                    }
                },
            ) {
                Text(if (isRefreshing) "Checking…" else "Refresh status")
            }
        }
    }
}
