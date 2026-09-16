# Wake My PC

A small personal-use Android app that sends a Wake-on-LAN command to your
home PC through your FritzBox router, and shows whether the PC is
currently on. It talks to the FritzBox over **TR-064** (a SOAP/UPnP control
interface), not the FritzBox web portal login.

You connect to your home WireGuard VPN **manually**, outside the app,
before using it — the app does not manage the VPN connection itself.

This README assumes you've never used Android Studio before.

## 1. Prerequisites

- **Install Android Studio**: <https://developer.android.com/studio>.
  It bundles its own compatible JDK, so you don't need to worry about the
  system's Java version.
- **On your Android phone**: enable Developer Options and USB debugging.
  - Settings → About phone → tap "Build number" 7 times.
  - Settings → System → Developer options → enable "USB debugging".
- A USB cable to connect the phone to your computer (for the "real
  device" path below), or willingness to use an emulator (see caveats
  in step 4).

## 2. FritzBox-side setup (do this once, before touching the app)

- [ ] Enable TR-064: on the FritzBox web UI, go to *Home Network →
      Network → Network Settings* and turn on "Allow access for
      applications". The FritzBox will need to reboot.
- [ ] Create a **dedicated FritzBox user** for this app (Home Network →
      FRITZ!Box Users) — don't reuse your main admin login. TR-064's
      "Hosts" service doesn't map cleanly onto the granular permission
      checkboxes, so you may need to grant it fuller "FRITZ!Box
      Settings" rights; if calls fail with a permissions error, that's
      the first thing to check.
- [ ] Confirm TR-064 is **not** reachable from the WAN side — it should
      only be usable from the LAN or through the VPN tunnel. This is
      what makes it reasonably safe to build casually.
- [ ] Note your PC's MAC address (for Wake-on-LAN) and its LAN IP (for
      the status check).

## 3. First-time project setup

1. Open Android Studio → "Open" → select this project's folder
   (`android-wlan`). Let it sync Gradle (first sync downloads the
   Android SDK components / Gradle distribution it needs — this can
   take a few minutes).
2. Copy `Config.kt.example` to
   `app/src/main/java/com/jakspinning/wakemypc/Config.kt` and fill in:
   - `FRITZBOX_HOST` — usually `fritz.box`, reachable once the VPN is up.
   - `TR064_PORT` — `49000` (plain HTTP; fine over the VPN tunnel).
   - `FRITZBOX_USERNAME` / `FRITZBOX_PASSWORD` — the dedicated user from
     step 2.
   - `PC_MAC_ADDRESS` / `PC_LAN_IP` — from step 2.

   `Config.kt` is gitignored — your real credentials never get committed.
   Re-sync Gradle if Android Studio doesn't pick up the new file
   automatically (File → Sync Project with Gradle Files).

## 4. Running the app

### On a real device (recommended — this is what actually reaches your FritzBox)

1. Connect your phone via USB. Accept the "Allow USB debugging?" prompt
   on the phone.
2. Confirm Android Studio sees it: the device should appear in the
   device dropdown in the toolbar. (From a terminal you can also check
   with `adb devices`.)
3. Click the green Run ▶ button in Android Studio (or press
   Shift+F10). This builds and installs the app to your phone.
4. **Before tapping "Turn On" in the app**, manually connect your
   phone's WireGuard VPN (outside the app, as usual) so it can actually
   reach the FritzBox.
5. The first time, tap **"Grant local network access"** and allow it —
   Android 17+ requires this permission just to open a connection to any
   device on your LAN (see Troubleshooting below for why). The "Turn On"
   / "Refresh status" buttons stay disabled until it's granted.
6. Tap "Turn On" and confirm the PC wakes up. Tap "Refresh status" to
   check on/off state.

### On the emulator

You can create a virtual device (Tools → Device Manager → Create
Device) and run the app there to look at the UI. **This won't let you
test the actual FritzBox calls** — the emulator can't join your real
home WireGuard VPN, so `wakeOnLan()` / `getHostStatus()` calls will just
fail to connect. Use it only for UI iteration.

## 5. Troubleshooting

- **`SocketTimeoutException: failed to connect ... after 10000ms`, even
  though the same address works fine in the phone's browser**: Android
  17+ (API 37+, which this app targets) requires apps to hold the new
  `ACCESS_LOCAL_NETWORK` runtime permission just to open a connection to
  *any* device on the LAN — separate from the regular internet
  permission. Without it, a TCP connect to a private IP just times out
  silently rather than raising a clear permission error, which makes it
  look exactly like a network problem. The app requests this permission
  via a "Grant local network access" button that appears until it's
  granted (see step 5 above). If you denied it once, Android may require
  granting it from the app's system Settings page instead of showing the
  prompt again.
- **`Cleartext communication to fritz.box not permitted by network
  security policy`**: Android 9+ blocks plain HTTP by default. TR-064
  over the VPN tunnel (port 49000) is intentionally plain HTTP for this
  app (see Phase 2+ backlog re: TLS), so the manifest carries
  `android:usesCleartextTraffic="true"` on the `<application>` element to
  allow it. If you see this error, check that attribute is still there —
  it's easy to lose if you regenerate/merge the manifest.
- **Gradle sync fails mentioning `org.jetbrains.kotlin.android` / "built-in
  Kotlin"**: this project targets AGP 9+, which bundles Kotlin support
  directly and no longer wants the separate `kotlin-android` plugin
  applied. If Android Studio's AGP upgrade assistant offers to "migrate to
  built-in Kotlin," accept it; otherwise this repo's `build.gradle.kts`
  files already reflect that setup — re-sync and it should resolve. The
  Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is a
  separate thing and stays applied.

- **HTTP 401 / digest auth failures**: double check the dedicated
  FritzBox user's username/password in `Config.kt`, and that user's
  permissions (step 2). Use `adb logcat` (filter on the app's package,
  `com.jakspinning.wakemypc`) to see the raw response.
- **Connection refused / timeout**: confirm the phone's WireGuard VPN is
  actually connected, and that `FRITZBOX_HOST` resolves/responds from
  the phone (e.g. by opening `http://fritz.box` in the phone's browser
  while on the VPN).
- **SOAP fault in the response body**: usually means TR-064 isn't
  enabled, or the action/parameter names don't match your FritzBox
  firmware version — re-check step 2's checklist.
- You can sanity-check the FritzBox side independently of the app with
  `curl`, from any machine on the VPN/LAN:
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
  If this doesn't work from a terminal, it won't work from the app
  either — fix it here first.

## 6. Project layout

```
app/src/main/java/com/jakspinning/wakemypc/
  MainActivity.kt          Compose UI: "Turn On" button, status text, refresh button
  Config.kt                Your real config (gitignored, not in this repo)
  network/
    DigestAuthenticator.kt Hand-rolled OkHttp Digest Auth (MD5, RFC 2617)
    SoapEnvelope.kt         Builds/parses the small TR-064 SOAP XML
    FritzTr064Client.kt     wakeOnLan() and getHostStatus()
Config.kt.example           Template — copy to Config.kt and fill in
```

## Not in scope yet

These are deliberately left out of this version:

- In-app WireGuard VPN connect/disconnect (you connect manually).
- SSH-based remote shutdown.
- Biometric prompt gating the buttons.
- A settings screen (config is currently a hardcoded, gitignored file —
  fine for single-user personal use; a settings UI backed by
  `EncryptedSharedPreferences` is a natural next step if that stops
  being convenient).
- TLS to the FritzBox (currently plain HTTP over the VPN tunnel; port
  49443 with the FritzBox's self-signed cert is a possible later
  upgrade).
