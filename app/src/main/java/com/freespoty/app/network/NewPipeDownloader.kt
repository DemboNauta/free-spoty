package com.freespoty.app.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [Downloader] for NewPipeExtractor. A single instance is held by the
 * application and reused across all extractor calls.
 */
class NewPipeDownloader : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = Request.Builder().url(url)

        headers.forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { v -> builder.addHeader(name, v) }
        }

        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", USER_AGENT)
        }
        // Bypass YouTube's EU consent wall (which replaces the normal HTML with a
        // consent.youtube.com page that has no `ytInitialData`). NewPipe app uses the
        // same trick: pre-set the consent cookie so we get the regular video page.
        val existingCookie = request.headers()["Cookie"]?.firstOrNull().orEmpty()
        val mergedCookie = if (existingCookie.contains("CONSENT=")) {
            existingCookie
        } else if (existingCookie.isBlank()) {
            CONSENT_COOKIE
        } else {
            "$existingCookie; $CONSENT_COOKIE"
        }
        builder.header("Cookie", mergedCookie)

        if (restrictedMode) {
            builder.header("YouTube-Restrict", "Strict")
        }

        val body = dataToSend?.toRequestBody(null)
        builder.method(httpMethod, body)

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha required", url)
                }
                val bodyString = response.body?.string() ?: ""
                if (response.code !in 200..299) {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                } else if (url.contains("youtube.com/results") || url.contains("youtube.com/watch")) {
                    val hasInitial = bodyString.contains("ytInitialData")
                    val isConsent = bodyString.contains("consent.youtube.com") ||
                        response.request.url.host.contains("consent")
                    Log.i(TAG, "GET $url -> ${response.code}, len=${bodyString.length}, ytInitialData=$hasInitial, consentWall=$isConsent, finalUrl=${response.request.url}")
                }
                return NpResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    bodyString,
                    response.request.url.toString()
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error for $url: ${e.message}")
            throw IOException("Network error: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "NewPipeDownloader"

        // Updated by AppContainer whenever the kids-mode preference changes.
        @Volatile var restrictedMode: Boolean = false
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"
        // PENDING+ marks an opt-out from personalised tracking; YES+ accepts it. Either
        // works to skip the EU consent wall. We use PENDING to minimise tracking.
        private const val CONSENT_COOKIE = "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODE1LjA0X3AwGgJlbiACGgYIgL_KpwY"
    }
}
