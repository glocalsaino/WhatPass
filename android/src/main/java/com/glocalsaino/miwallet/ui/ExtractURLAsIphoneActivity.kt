package com.glocalsaino.miwallet.ui

import android.app.ProgressDialog
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.Request
import com.glocalsaino.miwallet.dismissIfShowing
import com.glocalsaino.miwallet.functions.IPHONE_USER_AGENT
import com.glocalsaino.miwallet.ui.quirk_fix.OpenIphoneWebView
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

class ExtractURLAsIphoneActivity : WhatPassActivity() {

    private val progressDialog by lazy { ProgressDialog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.data != null) {
            progressDialog.show()
            tracker.trackEvent("quirk_fix", "unpack_attempt", intent?.data?.host, null)

            DownloadExtractAndStartImportTask().execute()
        }
    }

    private inner class DownloadExtractAndStartImportTask : AsyncTask<Void, Void, String>() {

        override fun doInBackground(vararg params: Void): String? {

            val client = OkHttpClient()
            try {
                val requestBuilder = Request.Builder().url(URI(intent?.data.toString()).toURL())
                requestBuilder.header("User-Agent", IPHONE_USER_AGENT)

                val body = client.newCall(requestBuilder.build()).execute().body

                if (body != null) {
                    val bodyString = body.string()
                    body.close()

                    val url = extractURL(bodyString) ?: return null

                    if (!url.startsWith("http")) {
                        return intent?.data?.scheme + "://" + intent?.data?.host + "/" + url
                    }

                    return url
                }
            } catch (e: IOException) {
                tracker.trackException("ExtractURLAsIphoneActivity", e, false)
            } catch (e: URISyntaxException) {
                tracker.trackException("ExtractURLAsIphoneActivity", e, false)
            }

            return null
        }

        private fun extractURL(body: String): String? {
            val patterns = arrayOf("href=\"(.*\\.pkpass.*?)\"", "window.location = \'(.*\\.pkpass.*?)\'")

            return patterns
                    .map { Pattern.compile(it).matcher(body) }
                    .firstOrNull { it.find() }
                    ?.group(1)
        }

        override fun onPostExecute(s: String?) {
            super.onPostExecute(s)
            if (s == null) {
                val intent = Intent(this@ExtractURLAsIphoneActivity, OpenIphoneWebView::class.java)
                intent.data = getIntent().data
                startActivity(intent)
                tearDown()
                return
            }

            tracker.trackEvent("quirk_fix", "unpack_success", intent?.data?.host, null)

            val intent = Intent(this@ExtractURLAsIphoneActivity, PassImportActivity::class.java)
            intent.data = s.toUri()

            startActivity(intent)
            tearDown()
        }
    }

    fun tearDown() {
        progressDialog.dismissIfShowing()
        finish()
    }
}
