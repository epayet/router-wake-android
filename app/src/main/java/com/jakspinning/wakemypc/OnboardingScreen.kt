package com.jakspinning.wakemypc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private enum class OnboardingStep {
    WELCOME,
    ENABLE_TR064,
    CREATE_USER,
    FIND_MAC_AND_IP,
    ENTER_DETAILS,
}

private val TUTORIAL_STEPS = OnboardingStep.entries

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")
private val IP_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

private fun isValidMac(value: String) = MAC_REGEX.matches(value.trim())
private fun isValidIp(value: String) = IP_REGEX.matches(value.trim())
private fun isValidPort(value: String) = value.toIntOrNull()?.let { it in 1..65535 } == true

/**
 * First-run tutorial + config form (when [initial] is null), or a bare edit form
 * reachable from the main screen's "Edit setup" button (when [initial] is non-null,
 * in which case [onCancel] must be provided to get back out without saving).
 */
@Composable
fun OnboardingScreen(
    initial: FritzBoxConfig?,
    onSave: (FritzBoxConfig) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var stepIndex by remember { mutableStateOf(if (initial != null) TUTORIAL_STEPS.lastIndex else 0) }
    val step = TUTORIAL_STEPS[stepIndex]

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (initial == null) {
                Text(
                    "Step ${stepIndex + 1} of ${TUTORIAL_STEPS.size}",
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { (stepIndex + 1f) / TUTORIAL_STEPS.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.ENABLE_TR064 -> EnableTr064Step()
                OnboardingStep.CREATE_USER -> CreateUserStep()
                OnboardingStep.FIND_MAC_AND_IP -> FindMacAndIpStep()
                OnboardingStep.ENTER_DETAILS -> DetailsFormStep(
                    initial = initial,
                    onSave = onSave,
                    secondaryLabel = if (initial != null) "Cancel" else "Back",
                    onSecondary = if (initial != null) onCancel else fun() { stepIndex-- },
                )
            }

            if (step != OnboardingStep.ENTER_DETAILS) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (stepIndex > 0) {
                        OutlinedButton(onClick = { stepIndex-- }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                    }
                    Button(onClick = { stepIndex++ }, modifier = Modifier.weight(1f)) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("Let's connect your FritzBox", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Wake My PC talks directly to your FritzBox router to wake your PC and check whether " +
            "it's on — no VPN or port-forwarding needed once this is set up.",
    )
    Text("First, two quick one-time things on the FritzBox itself:")
    Text("1. Turn on TR-064 (the FritzBox's control interface)")
    Text("2. Create a dedicated FritzBox user for this app")
    Text("Then note your PC's MAC address and LAN IP, and enter everything on the last step. Takes about 5 minutes.")
}

@Composable
private fun EnableTr064Step() {
    Text("Step 1: Enable TR-064", style = MaterialTheme.typography.headlineSmall)
    Text("On a device already connected to your FritzBox's Wi-Fi or LAN, open the FritzBox web interface (usually http://fritz.box), then:")
    Text("• Go to Home Network → Network → Network Settings")
    Text("• Find \"Access for Applications\" and enable \"Allow access for applications\"")
    Text("• Save — the FritzBox may reboot briefly")
    Text("This only lets apps talk to your router from your own home network or VPN — it's never reachable from the open internet.")
}

@Composable
private fun CreateUserStep() {
    Text("Step 2: Create a dedicated user", style = MaterialTheme.typography.headlineSmall)
    Text("Don't reuse your main FritzBox admin login for this. In the FritzBox web interface:")
    Text("• Go to Home Network → FRITZ!Box Users")
    Text("• Click \"Add User\"")
    Text("• Give it a name (e.g. \"wakemypc\") and a strong, unique password")
    Text("• Under permissions, grant \"FRITZ!Box Settings\" access")
    Text("  (the fine-grained checkboxes don't map cleanly onto this app's API — if calls fail with a permission error later, this is the first thing to check)")
    Text("• Save. You'll enter this username and password on the last step.")
}

@Composable
private fun FindMacAndIpStep() {
    Text("Step 3: Find your PC's MAC address and IP", style = MaterialTheme.typography.headlineSmall)
    Text("Easiest: open the FritzBox web interface → Home Network → Network, find your PC in the device list, and note its MAC address and IPv4 address shown there.")
    Text("Or, on the PC itself:")
    Text("• Windows: Command Prompt → \"ipconfig /all\" → look for \"Physical Address\" (MAC) and \"IPv4 Address\"")
    Text("• macOS: System Settings → Wi-Fi/Network → Details, or \"ifconfig\" in Terminal")
    Text("• Linux: \"ip addr\"")
    Text("Also make sure the FritzBox always gives this PC the same IP (Home Network → Network → edit the device → \"Always assign this device the same IP\") — otherwise the address can change later and the status check will point at the wrong device.")
}

/**
 * A condensed recap of steps 1–3, reachable from the details form itself so
 * "Edit setup" (which skips straight to the form) still has a way back to
 * these instructions without losing whatever the user has already typed.
 */
@Composable
private fun SetupHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("Where to find these") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("1. Enable TR-064", style = MaterialTheme.typography.titleSmall)
                Text(
                    "FritzBox web UI (usually http://fritz.box) → Home Network → Network → " +
                        "Network Settings → enable \"Allow access for applications\". The " +
                        "FritzBox may reboot.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("2. Create a dedicated user", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Home Network → FRITZ!Box Users → Add User. Give it a name and password, " +
                        "and grant \"FRITZ!Box Settings\" access. Don't reuse your main admin " +
                        "login.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("3. Find your PC's MAC address and IP", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Easiest: FritzBox web UI → Home Network → Network → find your PC in the " +
                        "device list. Or on the PC itself: Windows \"ipconfig /all\", macOS " +
                        "Wi-Fi/Network → Details, Linux \"ip addr\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

@Composable
private fun DetailsFormStep(
    initial: FritzBoxConfig?,
    onSave: (FritzBoxConfig) -> Unit,
    secondaryLabel: String,
    onSecondary: (() -> Unit)?,
) {
    var host by remember { mutableStateOf(initial?.host ?: SettingsRepository.DEFAULT_HOST) }
    var port by remember { mutableStateOf((initial?.port ?: SettingsRepository.DEFAULT_PORT).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var macAddress by remember { mutableStateOf(initial?.pcMacAddress ?: "") }
    var lanIp by remember { mutableStateOf(initial?.pcLanIp ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val hostValid = host.isNotBlank()
    val portValid = isValidPort(port)
    val usernameValid = username.isNotBlank()
    val passwordValid = password.isNotBlank()
    val macValid = isValidMac(macAddress)
    val ipValid = isValidIp(lanIp)

    var showHelpDialog by remember { mutableStateOf(false) }

    Text("Your details", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Encrypted on-device via Android's Keystore. Never sent anywhere but your FritzBox.",
        style = MaterialTheme.typography.bodySmall,
    )

    TextButton(onClick = { showHelpDialog = true }) {
        Text("Lost on how to get these values? Tap for a refresher")
    }
    if (showHelpDialog) {
        SetupHelpDialog(onDismiss = { showHelpDialog = false })
    }

    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("FritzBox address") },
        supportingText = { Text("Usually \"fritz.box\" — only change this if that doesn't work (e.g. 192.168.178.1).") },
        isError = showErrors && !hostValid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter(Char::isDigit) },
        label = { Text("TR-064 port") },
        supportingText = { Text("Default is 49000.") },
        isError = showErrors && !portValid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("FritzBox username") },
        supportingText = { Text("The dedicated user from step 2 — not your main admin login.") },
        isError = showErrors && !usernameValid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("FritzBox password") },
        isError = showErrors && !passwordValid,
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                Text(if (passwordVisible) "Hide" else "Show")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = macAddress,
        onValueChange = { macAddress = it },
        label = { Text("PC MAC address") },
        supportingText = { Text("Format AA:BB:CC:DD:EE:FF, from step 3.") },
        isError = showErrors && !macValid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = lanIp,
        onValueChange = { lanIp = it },
        label = { Text("PC LAN IP address") },
        supportingText = { Text("E.g. 192.168.178.34, from step 3.") },
        isError = showErrors && !ipValid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (onSecondary != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) {
                Text(secondaryLabel)
            }
        }
        Button(
            onClick = {
                if (hostValid && portValid && usernameValid && passwordValid && macValid && ipValid) {
                    onSave(
                        FritzBoxConfig(
                            host = host.trim(),
                            port = port.toInt(),
                            username = username.trim(),
                            password = password,
                            pcMacAddress = macAddress.trim(),
                            pcLanIp = lanIp.trim(),
                        ),
                    )
                } else {
                    showErrors = true
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Save and finish")
        }
    }
}
