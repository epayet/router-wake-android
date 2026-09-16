package com.jakspinning.wakemypc.network

private const val HOSTS_SERVICE = "urn:dslforum-org:service:Hosts:1"

/** Builds the SOAP envelope for TR-064's X_AVM-DE_WakeOnLANByMACAddress action. */
fun buildWakeOnLanEnvelope(macAddress: String): String = soapEnvelope(
    action = "X_AVM-DE_WakeOnLANByMACAddress",
    paramsXml = "<NewMACAddress>$macAddress</NewMACAddress>",
)

private fun soapEnvelope(action: String, paramsXml: String): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <s:Body>
        <u:$action xmlns:u="$HOSTS_SERVICE">
          $paramsXml
        </u:$action>
      </s:Body>
    </s:Envelope>
""".trimIndent()

fun soapActionHeader(action: String): String = "$HOSTS_SERVICE#$action"
