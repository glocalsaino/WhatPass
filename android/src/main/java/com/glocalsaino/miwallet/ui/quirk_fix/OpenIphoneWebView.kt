package com.glocalsaino.miwallet.ui.quirk_fix

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import com.glocalsaino.miwallet.functions.IPHONE_USER_AGENT

class OpenIphoneWebView : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data ?: return
        val webView = WebView(this)
        webView.settings.userAgentString = IPHONE_USER_AGENT
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("$data")
        setContentView(webView)

        val loadingToast = Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT)

        webView.webViewClient = object : WebViewClientCompat() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                loadingToast.cancel()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceErrorCompat) {
                loadingToast.cancel()
                Toast.makeText(this@OpenIphoneWebView, "Error loading page", Toast.LENGTH_SHORT).show()
            }
        }

        loadingToast.show()
    }
}
