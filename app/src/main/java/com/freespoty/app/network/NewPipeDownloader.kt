package com.freespoty.app.network

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

        // NewPipe doesn't always set a User-Agent; YouTube blocks empty UAs.
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", USER_AGENT)
        }

        val body = dataToSend?.toRequestBody(null)
        builder.method(httpMethod, body)

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha required", url)
                }
                val bodyString = response.body?.string() ?: ""
                return NpResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    bodyString,
                    response.request.url.toString()
                )
            }
        } catch (e: IOException) {
            throw IOException("Network error: ${e.message}", e)
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"
    }
}
