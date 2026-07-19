package com.glocalsaino.miwallet.ui

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.glocalsaino.miwallet.getSizeAsPointCompat
import com.glocalsaino.miwallet.R

class PassViewHelper(private val context: Activity) {

    val fingerSize by lazy { context.resources.getDimensionPixelSize(R.dimen.finger) }

    fun setBitmapSafe(imageView: ImageView, bitmap: Bitmap?, enforceMinHeight: Boolean = true) {

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
            if (enforceMinHeight) {
                imageView.layoutParams = getLayoutParamsMinHeight(imageView, bitmap)
            }
        } else {
            imageView.visibility = View.GONE
        }
    }

    private fun getLayoutParamsMinHeight(imageView: ImageView, bitmap: Bitmap)
            = imageView.layoutParams!!.apply {
        height = if (bitmap.height < fingerSize) {
            fingerSize
        } else {
            LinearLayout.LayoutParams.WRAP_CONTENT
        }
    }

    val windowWidth by lazy { context.windowManager.getSizeAsPointCompat().x }
}
