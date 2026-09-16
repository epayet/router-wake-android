package com.jakspinning.wakemypc.network

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Hand-rolled RFC 2617 HTTP Digest Authenticator.
 *
 * OkHttp only ships Basic-auth helpers out of the box, not Digest — this is
 * a small, dependency-free implementation of just what the FritzBox TR-064
 * endpoint needs (MD5, qop=auth).
 */
class DigestAuthenticator(
    private val username: String,
    private val password: String,
) : Authenticator {

    private val nonceCount = AtomicInteger(0)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops if our credentials are simply wrong.
        if (response.request.header("Authorization") != null) return null

        val challenge = response.header("WWW-Authenticate") ?: return null
        if (!challenge.startsWith("Digest", ignoreCase = true)) return null

        val params = parseChallenge(challenge)
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val qop = params["qop"] // may be null on older/simpler servers
        val opaque = params["opaque"]

        val uri = response.request.url.encodedPath
        val method = response.request.method
        val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val nc = "%08x".format(nonceCount.incrementAndGet())

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        val responseHash = if (qop != null) {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val header = buildString {
            append("Digest ")
            append("username=\"$username\", ")
            append("realm=\"$realm\", ")
            append("nonce=\"$nonce\", ")
            append("uri=\"$uri\", ")
            append("response=\"$responseHash\", ")
            append("algorithm=MD5")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            if (opaque != null) append(", opaque=\"$opaque\"")
        }

        return response.request.newBuilder()
            .header("Authorization", header)
            .build()
    }

    private fun parseChallenge(header: String): Map<String, String> {
        val withoutScheme = header.removePrefix("Digest").trim()
        val result = mutableMapOf<String, String>()
        // Splits on commas that separate key=value pairs, tolerating commas
        // inside quoted values.
        Regex("""(\w+)=("[^"]*"|[^,]*)""").findAll(withoutScheme).forEach { match ->
            val key = match.groupValues[1].trim()
            val value = match.groupValues[2].trim().removeSurrounding("\"")
            result[key] = value
        }
        return result
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
