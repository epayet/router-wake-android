# RouterWake

Sends Wake-on-LAN to a home PC through a FritzBox and shows whether it's
on. Talks to the FritzBox over **TR-064** (SOAP/UPnP), not the web portal
login — no port forwarding, no relay device. You connect the phone's
WireGuard VPN manually before using it; the app doesn't manage the VPN.

## FritzBox-side setup

The app's first-run onboarding covers this, but for reference:

- Enable TR-064: FritzBox web UI → *Home Network → Network → Network
  Settings* → "Allow access for applications" (reboots the FritzBox).
- Create a **dedicated FritzBox user** (Home Network → FRITZ!Box Users)
  with "FRITZ!Box Settings" access — the granular checkboxes don't map
  cleanly onto TR-064's Hosts service.
- TR-064 should only be reachable from LAN/VPN, never WAN.
- Note the PC's MAC address and LAN IP.

## Running it

No config file — host/port/credentials/MAC/IP are entered in-app on
first launch and stored Keystore-encrypted on-device.

1. Run on a real device (the emulator can't join the WireGuard VPN, so
   TR-064 calls will just fail to connect — UI iteration only).
2. Connect the VPN before opening the app.
3. First launch: onboarding wizard → details form (`fritz.box` is the
   default host). Reachable again via "Edit setup".
4. Grant local network access (Android 17+ requires it separately from
   internet access; see Troubleshooting).
5. "Turn On" wakes the PC. Status auto-polls every 15s — mascot plate is
   yellow (on) / blue (off/unknown) / red (check failed). "Refresh
   status" forces an immediate check.

## Troubleshooting

- **Connect times out but the address works in the phone's browser**:
  missing `ACCESS_LOCAL_NETWORK` — grant via the in-app button, or the
  system Settings page if denied once already.
- **`Cleartext communication ... not permitted`**: manifest needs
  `android:usesCleartextTraffic="true"` (TR-064 over the VPN is
  intentionally plain HTTP).
- **HTTP 401 / digest auth failure**: recheck credentials via "Edit
  setup" and the dedicated user's permissions. `adb logcat` filtered on
  `com.jakspinning.wakemypc` shows the raw response.
- **Connection refused/timeout**: VPN not actually connected, or
  `fritz.box` doesn't resolve from the phone.
- **SOAP fault in the response**: TR-064 disabled, or action names don't
  match your firmware version.
- **Gradle sync complains about `org.jetbrains.kotlin.android`**: AGP 9's
  built-in Kotlin support replaces it — accept the migration prompt.
- Sanity-check the FritzBox side independent of the app:
  ```sh
  curl --digest -u <user>:<pass> \
    http://fritz.box:49000/upnp/control/hosts \
    -H 'Content-Type: text/xml; charset="utf-8"' \
    -H 'SOAPAction: urn:dslforum-org:service:Hosts:1#X_AVM-DE_WakeOnLANByMACAddress' \
    -d '<?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:X_AVM-DE_WakeOnLANByMACAddress xmlns:u="urn:dslforum-org:service:Hosts:1">
              <NewMACAddress>AA:BB:CC:DD:EE:FF</NewMACAddress>
            </u:X_AVM-DE_WakeOnLANByMACAddress>
          </s:Body>
        </s:Envelope>'
  ```
  Fails here → fix here first, not in the app.

## Project layout

```
app/src/main/java/com/jakspinning/wakemypc/
  MainActivity.kt          Compose UI: status mascot, "Turn On"/refresh buttons
  OnboardingScreen.kt      First-run tutorial + the FritzBox/PC details form
  SettingsRepository.kt    Reads/writes config, encrypted via Android Keystore
  Theme.kt                 App-wide blue color scheme
  RouterMascotIndicator.kt Live status indicator (reuses the launcher icon's artwork)
  network/
    DigestAuthenticator.kt Hand-rolled OkHttp Digest Auth (MD5, RFC 2617)
    SoapEnvelope.kt         Builds/parses the small TR-064 SOAP XML
    FritzTr064Client.kt     wakeOnLan() and getHostStatus()
```

## Privacy

Everything entered is encrypted at rest with an Android Keystore-backed
key (`SettingsRepository.kt`) — never leaves the device. `INTERNET`
permission exists only because OkHttp needs it to make an HTTP request at
all; the only host contacted is the FritzBox address entered. No
analytics, no accounts, no server this project runs.

## Not in scope yet

- In-app WireGuard VPN connect/disconnect.
- SSH-based remote shutdown.
- Biometric prompt gating the buttons.
- TLS to the FritzBox (port 49443, self-signed cert).
