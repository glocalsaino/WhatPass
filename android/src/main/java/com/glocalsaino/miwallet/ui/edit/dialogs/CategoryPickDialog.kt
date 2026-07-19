package com.glocalsaino.miwallet.ui.edit.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.functions.getCategoryDefaultBG
import com.glocalsaino.miwallet.functions.getHumanCategoryString
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.model.pass.PassType
import com.glocalsaino.miwallet.ui.views.BaseCategoryIndicatorView

private val passTypes = arrayOf(PassType.BOARDING, PassType.EVENT, PassType.GENERIC, PassType.LOYALTY, PassType.VOUCHER, PassType.COUPON)

fun showCategoryPickDialog(context: Context, pass: Pass, refreshCallback: () -> Unit) {

    val adapter = object : BaseAdapter() {

        override fun getCount() = passTypes.size

        override fun getItem(position: Int) = passTypes[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(context)
            val inflate = inflater.inflate(R.layout.item_nav_pass_category, parent, false)

            val categoryIndicatorView = inflate.findViewById(R.id.categoryView) as BaseCategoryIndicatorView

            val type = getItem(position)
            categoryIndicatorView.setImageByCategory(type)
            categoryIndicatorView.setAccentColor(getCategoryDefaultBG(type))
            val tv = inflate.findViewById(R.id.navCategoryLabel) as TextView
            tv.setText(getHumanCategoryString(type))

            return inflate
        }
    }

    val builder = AlertDialog.Builder(context)
    builder.setAdapter(adapter) { _, position ->
        pass.type = passTypes[position]
        refreshCallback.invoke()
    }
    builder.setTitle(R.string.select_category_dialog_title)
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.show()
}
