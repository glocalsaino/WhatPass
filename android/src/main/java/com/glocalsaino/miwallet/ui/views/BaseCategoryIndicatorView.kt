package com.glocalsaino.miwallet.ui.views

import android.content.Context
import androidx.annotation.LayoutRes
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.functions.getCategoryTopImageRes
import com.glocalsaino.miwallet.model.pass.PassType

open class BaseCategoryIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet, @LayoutRes val layoutRes: Int = R.layout.category_indicator_base) : LinearLayout(context, attrs) {


    override fun onFinishInflate() {
        super.onFinishInflate()
        LayoutInflater.from(context).inflate(layoutRes, this, true)
    }

    fun setImageByCategory(category: PassType?) {
        val topImageView = findViewById<ImageView>(R.id.topImageView)
        if (category == null) {
            topImageView.visibility = View.GONE
        } else {
            topImageView.visibility = View.VISIBLE
            topImageView.setImageResource(getCategoryTopImageRes(category))
        }
    }

    fun setAccentColor(color: Int) = setBackgroundColor(color)
}
