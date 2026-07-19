package com.glocalsaino.miwallet.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.model.PassStore

class TouchImageActivity : AppCompatActivity() {

    val passStore: PassStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        setContentView(imageView)

        val bitmap = intent.getStringExtra("IMAGE")?.let { imageKey ->
            passStore.currentPass?.getBitmap(passStore, imageKey)
        }

        if (bitmap == null) {
            finish()
        } else {
            imageView.setImageBitmap(bitmap)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
