package com.noctyra.app.data.remote.http

import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

object HttpClient {
    private val chromeSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .cipherSuites(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
        )
        .build()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .connectionSpecs(listOf(chromeSpec, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .followRedirects(false)
        .build()

    fun fetchDoc(url: String, referer: String = "", userAgent: String = MOBILE_UA): Document {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
        if (referer.isNotEmpty()) reqBuilder.header("Referer", referer)
        val response = okHttp.newCall(reqBuilder.build()).execute()
        if (response.code in 300..399) throw Exception("Redirect: not found")
        if (response.code == 404 || response.code == 410) throw Exception("Not found")
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return Jsoup.parse(response.body?.string() ?: "")
    }

    const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
}
