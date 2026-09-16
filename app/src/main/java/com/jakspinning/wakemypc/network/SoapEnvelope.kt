package com.jakspinning.wakemypc.network

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

private const val HOSTS_SERVICE = "urn:dslforum-org:service:Hosts:1"

/** Builds the SOAP envelope for TR-064's X_AVM-DE_WakeOnLANByMACAddress action. */
fun buildWakeOnLanEnvelope(macAddress: String): String = soapEnvelope(
    action = "X_AVM-DE_WakeOnLANByMACAddress",
    paramsXml = "<NewMACAddress>$macAddress</NewMACAddress>",
)

/** Builds the SOAP envelope for TR-064's X_AVM-DE_GetSpecificHostEntryByIP action. */
fun buildGetHostStatusEnvelope(ipAddress: String): String = soapEnvelope(
    action = "X_AVM-DE_GetSpecificHostEntryByIP",
    paramsXml = "<NewIPAddress>$ipAddress</NewIPAddress>",
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

/** Pulls a single top-level text field (e.g. "NewActive") out of a SOAP response body. */
fun extractSoapField(xmlBody: String, fieldName: String): String? {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xmlBody))
    }

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == fieldName) {
            return parser.nextText()
        }
        event = parser.next()
    }
    return null
}
