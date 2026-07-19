package com.glocalsaino.miwallet.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.doAfterEdit
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.databinding.EditBinding
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.BarCode
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.model.pass.PassBarCodeFormat
import com.glocalsaino.miwallet.model.pass.PassImpl
import com.glocalsaino.miwallet.ui.edit.FieldsEditFragment
import com.glocalsaino.miwallet.ui.edit.ImageEditHelper
import com.glocalsaino.miwallet.ui.edit.dialogs.showBarcodeEditDialog
import com.glocalsaino.miwallet.ui.edit.dialogs.showCategoryPickDialog
import com.glocalsaino.miwallet.ui.edit.dialogs.showColorPickDialog
import com.glocalsaino.miwallet.ui.pass_view_holder.EditViewHolder
import java.util.*

class PassEditActivity : AppCompatActivity() {

    private lateinit var binding: EditBinding
    private lateinit var currentPass: PassImpl
    private val imageEditHelper by lazy { ImageEditHelper(this, passStore) }

    internal val passStore: PassStore by inject()

    private val passViewHelper: PassViewHelper by lazy { PassViewHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.categoryView.setOnClickListener {
            AlertDialog.Builder(this).setItems(R.array.category_edit_options) { _, i ->
                when (i) {
                    0 -> showCategoryPickDialog(this@PassEditActivity, currentPass, refreshCallback)
                    1 -> showColorPickDialog(this@PassEditActivity, currentPass, refreshCallback)
                    2 -> pickWithPermissionCheck(ImageEditHelper.REQ_CODE_PICK_ICON)
                }
            }.show()
        }
        binding.passTitle.doAfterEdit {
            currentPass.description = "$it"
        }

        val currentPass = passStore.currentPass
        if (currentPass != null) {
            this.currentPass = currentPass as PassImpl
        } else {
            finish()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.commit {
            add(R.id.container_for_primary_fields, FieldsEditFragment.create(false))
            add(R.id.container_for_secondary_fields, FieldsEditFragment.create(true))
        }

        binding.addBarcodeButton.setOnClickListener {
            showBarcodeEditDialog(this@PassEditActivity,
                    refreshCallback,
                    this@PassEditActivity.currentPass,
                    BarCode(PassBarCodeFormat.QR_CODE, UUID.randomUUID().toString().uppercase(Locale.ROOT)))
        }
    }

    private fun pickWithPermissionCheck(requestCode: Int) {
        imageEditHelper.startPick(requestCode)
    }

    val refreshCallback = { refresh(currentPass) }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        imageEditHelper.onActivityResult(requestCode, resultCode, data)
    }

    private fun refresh(pass: Pass) {
        val passViewHolder = EditViewHolder(binding.passCard)

        passViewHolder.apply(pass, passStore, this)

        prepareImageUI(R.id.logo_img, R.id.add_logo, ImageEditHelper.REQ_CODE_PICK_LOGO)
        prepareImageUI(R.id.strip_img, R.id.add_strip, ImageEditHelper.REQ_CODE_PICK_STRIP)
        prepareImageUI(R.id.footer_img, R.id.add_footer, ImageEditHelper.REQ_CODE_PICK_FOOTER)

        binding.addBarcodeButton.visibility = if (pass.barCode == null) View.VISIBLE else View.GONE
        val barcodeUIController = BarcodeUIController(window.decorView, pass.barCode, this, passViewHelper)
        barcodeUIController.getBarcodeView().setOnClickListener { showBarcodeEditDialog(this@PassEditActivity, refreshCallback, currentPass, currentPass.barCode!!) }
    }

    @Pass.PassBitmap
    private fun prepareImageUI(@IdRes logo_img: Int, @IdRes add_logo: Int, requestCode: Int) {
        val imageString = ImageEditHelper.getImageStringByRequestCode(requestCode)!!

        val bitmap = currentPass.getBitmap(passStore, imageString)

        val addButton = findViewById<Button>(add_logo)!!
        addButton.visibility = if (bitmap == null) View.VISIBLE else View.GONE

        val listener = View.OnClickListener {
            pickWithPermissionCheck(requestCode)
        }

        val logoImage = findViewById<ImageView>(logo_img)
        passViewHelper.setBitmapSafe(logoImage, bitmap)
        logoImage.setOnClickListener(listener)
        addButton.setOnClickListener(listener)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        refresh(currentPass)
    }


    override fun onPause() {
        passStore.save(currentPass)
        passStore.notifyChange()
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
