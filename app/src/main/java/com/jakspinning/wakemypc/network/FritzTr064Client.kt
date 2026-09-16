package com.jakspinning.wakemypc.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to a FritzBox's TR-064 control interface (SOAP/UPnP), NOT the web
 * portal login — see README for why. Assumes the device is already on the
 * FritzBox's LAN (e.g. via a manually-connected WireGuard VPN).
 */
class FritzTr064Client(
    host: String,
    port: Int,
    username: String,
    password: String,
) {
    private val endpoint = "http://$host:$port/upnp/control/hosts"

    private val client = OkHttpClient.Builder()
        .authenticator(DigestAuthenticator(username, password))
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun wakeOnLan(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            post(action = "X_AVM-DE_WakeOnLANByMACAddress", body = buildWakeOnLanEnvelope(macAddress))
            Unit
        }
    }

    suspend fun getHostStatus(ipAddress: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val responseBody = post(
                action = "X_AVM-DE_GetSpecificHostEntryByIP",
                body = buildGetHostStatusEnvelope(ipAddress),
            )
            val active = extractSoapField(responseBody, "NewActive")
                ?: error("Response did not contain NewActive")
            // TR-064 encodes SOAP booleans as "0"/"1", not "true"/"false".
            active == "1" || active.equals("true", ignoreCase = true)
        }
    }

    private fun post(action: String, body: String): String {
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("SOAPAction", soapActionHeader(action))
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("TR-064 call failed: HTTP ${response.code} — $responseBody")
            }
            return responseBody
        }
    }
}
