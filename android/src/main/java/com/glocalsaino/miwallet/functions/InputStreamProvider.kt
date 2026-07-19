package com.glocalsaino.miwallet.functions

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.model.InputStreamWithSource
import java.io.BufferedInputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

const val IPHONE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 7_0 like Mac OS X; en-us) AppleWebKit/537.51.1 (KHTML, like Gecko) Version/7.0 Mobile/11A465 Safari/9537.53"

// Thrown instead of returning pass content when a scanned/opened URL turns out to be a
// regular web page rather than a pass download link (even after trying the "download=true"
// landing-page workaround) - callers should open it in the browser instead of showing an error.
class NotAPassUrlException(val url: String) : Exception("Not a pass: $url")

// A single shared client (instead of one per request) avoids paying for a fresh
// DNS lookup/TLS handshake on every pass download, and the explicit callTimeout
// caps the worst case so a slow/dead server fails fast instead of leaving the
// "please wait" screen stuck for an indefinite amount of time.
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

fun fromURI(context: Context, uri: Uri, tracker: Tracker): InputStreamWithSource? {
    tracker.trackEvent("protocol", "to_inputstream", uri.scheme, null)
    return when (uri.scheme) {
        "content" -> fromContent(context, uri)

        "http", "https" ->
            // TODO check if SPDY should be here
            return fromOKHttp(uri, tracker)

        "file" -> getDefaultInputStreamForUri(uri)
        else -> {
            tracker.trackException("unknown scheme in ImportAsyncTask" + uri.scheme, false)
            getDefaultInputStreamForUri(uri)
        }
    }
}

private fun fromOKHttp(uri: Uri, tracker: Tracker): InputStreamWithSource? {
    val client = httpClient
    val url = URL("$uri")
    val requestBuilder = Request.Builder().url(url)

    // fake to be an iPhone in some cases when the server decides to send no passbook
    // to android phones - but only do it then - we are proud to be Android ;-)
    val iPhoneFakeMap = mapOf(
            "air_canada" to "//m.aircanada.ca/ebp/",
            "air_canada2" to "//services.aircanada.com/ebp/",
            "air_canada3" to "//mci.aircanada.com/mci/bp/",
            "icelandair" to "//checkin.si.amadeus.net",
            "mbk" to "//mbk.thy.com/",
            "heathrow" to "//passbook.heathrow.com/",
            "eventbrite" to "//www.eventbrite.com/passes/order"
    )

    for ((key, value) in iPhoneFakeMap) {
        if ("$uri".contains(value)) {
            tracker.trackEvent("quirk_fix", "ua_fake", key, null)
            requestBuilder.header("User-Agent", IPHONE_USER_AGENT)
        }
    }

    val request = requestBuilder.build()

    // If we can't even establish the connection (expired/untrusted TLS chain on an old
    // Android version, timeout, DNS hiccup...) we have no way to tell whether this was a
    // pass link or not - the device's browser keeps its own, independently-updated
    // certificate/network stack and may well succeed where this raw client can't, so hand
    // it off there instead of showing a dead-end "invalid pass" error.
    var response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        tracker.trackException("pass url fetch failed, falling back to browser", e, false)
        throw NotAPassUrlException("$uri")
    }

    // Some pass-hosting providers (e.g. PassSource) serve an HTML "Add to Wallet"
    // landing page at this same URL instead of the actual file, unless "download=true"
    // is present in the query string - follow that automatically, the same way tapping
    // the page's own download button would, instead of failing with "invalid format".
    val contentType = response.header("Content-Type").orEmpty()
    if (contentType.startsWith("text/html") && !url.query.orEmpty().contains("download=true")) {
        response.close()
        val separator = if (url.query.isNullOrEmpty()) "?" else "&"
        val downloadUrl = URL("$uri$separator" + "download=true")
        val downloadRequest = requestBuilder.url(downloadUrl).build()
        response = try {
            client.newCall(downloadRequest).execute()
        } catch (e: IOException) {
            tracker.trackException("pass url download=true retry failed, falling back to browser", e, false)
            throw NotAPassUrlException("$uri")
        }
    }

    // Still HTML even after the download=true retry - this is just a regular web page that
    // happened to start with http(s)://, not a pass at all (e.g. a QR pointing to a normal
    // website). Let the caller send the user to their browser instead of failing.
    if (response.header("Content-Type").orEmpty().startsWith("text/html")) {
        response.close()
        throw NotAPassUrlException("$uri")
    }

    val body = response.body

    if (body != null) {
        return InputStreamWithSource("$uri", body.byteStream())
    }

    return null
}

private fun fromContent(ctx: Context, uri: Uri) = InputStreamWithSource("$uri", ctx.contentResolver.openInputStream(uri)!!)

private fun getDefaultInputStreamForUri(uri: Uri) = InputStreamWithSource("$uri", BufferedInputStream(URL("$uri").openStream(), 4096))
