package com.glocalsaino.miwallet.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.ImageView
import com.glocalsaino.miwallet.R

class CategoryIndicatorViewWithIcon(context: Context, attrs: AttributeSet) : BaseCategoryIndicatorView(context, attrs, R.layout.category_indicator) {

    fun setIcon(iconBitmap: Bitmap) {
        findViewById<ImageView>(R.id.iconImageView).setImageBitmap(iconBitmap)
    }

}
