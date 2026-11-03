package com.worldtv.app.data

import com.worldtv.app.domain.UserNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

interface DatasetClient {
    suspend fun fetchText(relativePath: String): String
}

class OkHttpDatasetClient(
    private val baseUrl: String = DatasetPaths.BASE,
    private val http: OkHttpClient = defaultClient(),
) : DatasetClient {
    override suspend fun fetchText(relativePath: String): String = withContext(Dispatchers.IO) {
        var lastIo: IOException? = null
        repeat(2) { attempt ->
            try {
                return@withContext execute(relativePath)
            } catch (error: IOException) {
                lastIo = error
                if (attempt == 0) Thread.sleep(400)
            }
        }
        throw DatasetException(ioMessage(lastIo), lastIo, ioNotice(lastIo))
    }

    private fun execute(relativePath: String): String {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + relativePath.removePrefix("/"))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val (message, notice) = httpFailure(response.code)
                throw DatasetException(message, notice = notice, noticeArg = response.code.toString())
            }
            if (body.isBlank()) {
                throw DatasetException("The dataset file was empty.", notice = UserNotice.DATASET_EMPTY)
            }
            return body
        }
    }

    companion object {
        const val USER_AGENT = "Zenvu/1.0 (Android)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun ioMessage(error: IOException?): String = when (error) {
            is UnknownHostException -> "No internet connection, or the dataset host could not be found."
            is SocketTimeoutException -> "The dataset host took too long to respond."
            else -> "The download was interrupted. Check your connection and try again."
        }

        fun ioNotice(error: IOException?): UserNotice = when (error) {
            is UnknownHostException -> UserNotice.NO_INTERNET
            is SocketTimeoutException -> UserNotice.TIMEOUT
            else -> UserNotice.DOWNLOAD_INTERRUPTED
        }

        fun httpMessage(code: Int): String = httpFailure(code).first

        fun httpFailure(code: Int): Pair<String, UserNotice> = when (code) {
            404 -> "That listing file is not in the dataset right now." to UserNotice.LISTING_MISSING
            403, 429 -> "The dataset host refused the request (HTTP $code). Try again shortly." to UserNotice.HOST_REFUSED
            in 500..599 -> "The dataset host had a problem (HTTP $code)." to UserNotice.HOST_PROBLEM
            else -> "The dataset request failed (HTTP $code)." to UserNotice.REQUEST_FAILED
        }
    }
}
