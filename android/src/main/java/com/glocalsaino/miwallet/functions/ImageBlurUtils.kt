package com.glocalsaino.miwallet.functions

import android.graphics.Bitmap

// Cheap, dependency-free blur: downscale heavily then upscale back, letting the
// bilinear filter do the blurring. Good enough for a blurred pass background.
fun Bitmap.fastBlur(scaleFactor: Float = 0.1f): Bitmap {
    val width = (width * scaleFactor).toInt().coerceAtLeast(1)
    val height = (height * scaleFactor).toInt().coerceAtLeast(1)
    val scaledDown = Bitmap.createScaledBitmap(this, width, height, true)
    return Bitmap.createScaledBitmap(scaledDown, this.width, this.height, true)
}
