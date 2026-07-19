package com.glocalsaino.miwallet

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

// ── KAXT replacements ──────────────────────────────────────────────────────

private val RGB_PATTERN = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*[\d.]+\s*)?\)""")

fun String.parseColor(default: Int = Color.BLACK): Int = try {
    val rgbMatch = RGB_PATTERN.matchEntire(trim())
    if (rgbMatch != null) {
        val (r, g, b) = rgbMatch.destructured
        Color.rgb(r.toInt(), g.toInt(), b.toInt())
    } else {
        Color.parseColor(this)
    }
} catch (e: Exception) {
    default
}

fun WindowManager.getSmallestSide(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = currentWindowMetrics.bounds
        minOf(bounds.width(), bounds.height())
    } else {
        @Suppress("DEPRECATION")
        val size = Point()
        @Suppress("DEPRECATION")
        defaultDisplay.getSize(size)
        minOf(size.x, size.y)
    }
}

fun WindowManager.getSizeAsPointCompat(): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        val size = Point()
        @Suppress("DEPRECATION")
        defaultDisplay.getSize(size)
        size
    }
}

fun Dialog.dismissIfShowing() {
    if (isShowing) dismiss()
}

fun Activity.lockOrientation(orientation: Int) {
    requestedOrientation = when (orientation) {
        Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
}

fun Activity.disableRotation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
}

fun Context.startActivityFromClass(cls: Class<*>) {
    startActivity(Intent(this, cls))
}

fun Context.startActivityFromURL(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun EditText.doAfterEdit(action: (CharSequence?) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { action(s) }
    })
}

fun Uri.loadImage(context: Context) = context.contentResolver.openInputStream(this)?.use {
    BitmapFactory.decodeStream(it)
}

fun Context.inflate(layoutId: Int): View =
    LayoutInflater.from(this).inflate(layoutId, null, false)

// ── KAXTUI replacement ─────────────────────────────────────────────────────

fun Context.alert(message: Int, title: Int, onOK: () -> Unit = {}) {
    AlertDialog.Builder(this)
        .setMessage(message)
        .setTitle(title)
        .setPositiveButton(android.R.string.ok) { _, _ -> onOK() }
        .show()
}
