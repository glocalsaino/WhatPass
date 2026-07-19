package com.glocalsaino.miwallet.ui.pass_view_holder

import android.app.Activity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import android.text.format.DateUtils
import android.view.View
import android.view.View.*
import android.widget.ImageView
import android.widget.TextView
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.functions.fastBlur
import com.glocalsaino.miwallet.model.PassBitmapCache
import com.glocalsaino.miwallet.functions.tryAddDateToCalendar
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.model.pass.PassField
import com.glocalsaino.miwallet.model.pass.PassType
import com.glocalsaino.miwallet.passkit.PassNotificationPrefs
import com.glocalsaino.miwallet.ui.Visibility
import com.glocalsaino.miwallet.ui.showNavigateToLocationsDialog
import com.glocalsaino.miwallet.ui.views.BaseCategoryIndicatorView
import com.glocalsaino.miwallet.ui.views.CategoryIndicatorViewWithIcon
import com.glocalsaino.miwallet.ui.views.TimeAndNavBar
import org.threeten.bp.ZonedDateTime
import java.io.File
import java.text.DateFormat
import java.util.Date

abstract class PassViewHolder(val view: CardView) : RecyclerView.ViewHolder(view) {

    private val timeAndNavBar = view.findViewById<TimeAndNavBar>(R.id.timeAndNavBar)
    open fun apply(pass: Pass, passStore: PassStore, activity: Activity) {
        setupButtons(activity, pass)

        refresh(pass, passStore)
    }

    open fun setupButtons(activity: Activity, pass: Pass) {
        val timeButton = timeAndNavBar.findViewById<TextView>(R.id.timeButton)
        val locationButton = timeAndNavBar.findViewById<TextView>(R.id.locationButton)

        timeButton.text = view.context.getString(R.string.pass_to_calendar)
        locationButton.text = view.context.getString(R.string.pass_directions)

        // These buttons sit directly on the pass's background (color, or a blurred photo for
        // eventTicket), so they need the pass's own foreground color to stay legible instead of
        // the default dark theme text/icon color.
        if (pass.foregroundColor != 0) {
            val tint = android.content.res.ColorStateList.valueOf(pass.foregroundColor)
            timeButton.setTextColor(pass.foregroundColor)
            timeButton.compoundDrawableTintList = tint
            locationButton.setTextColor(pass.foregroundColor)
            locationButton.compoundDrawableTintList = tint
        }

        timeButton.setOnClickListener {
            getDateOrExtraText(pass)?.let { tryAddDateToCalendar(pass, view, it) }
        }

        locationButton.setOnClickListener {
            activity.showNavigateToLocationsDialog(pass, false)
        }
    }

    protected fun refresh(pass: Pass, passStore: PassStore) {
        val dateOrExtraText = getDateOrExtraText(pass)

        val noButtons = dateOrExtraText == null && pass.locations.isEmpty()

        view.findViewById<View>(R.id.actionsSeparator).visibility = getVisibilityForGlobalAndLocal(noButtons, true)
        timeAndNavBar.findViewById<TextView>(R.id.locationButton).visibility = getVisibilityForGlobalAndLocal(noButtons, pass.locations.isNotEmpty())

        timeAndNavBar.findViewById<TextView>(R.id.timeButton).visibility = getVisibilityForGlobalAndLocal(noButtons, dateOrExtraText != null)

        val iconBitmap = pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_ICON)

        val categoryView = view.findViewById<CategoryIndicatorViewWithIcon>(R.id.categoryView)
        iconBitmap?.let { categoryView.setIcon(it) }

        categoryView.setImageByCategory(pass.type)

        categoryView.setAccentColor(pass.accentColor)

        val cardContent = view.findViewById<View>(R.id.pass_card_content)
        val backgroundImageView = view.findViewById<ImageView>(R.id.background_img_view)

        // Per Apple's PassKit guidelines, eventTicket may define a "background" image that
        // is shown blurred behind the whole pass, replacing the background color - and taking
        // priority over both the strip banner and the thumbnail, which get hidden in that case.
        val backgroundBitmap = if (pass.type == PassType.EVENT) {
            pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_BACKGROUND)
        } else null

        if (backgroundBitmap != null) {
            val blurred = PassBitmapCache.get(pass.id, "background_blurred")
                ?: backgroundBitmap.fastBlur().also { PassBitmapCache.put(pass.id, "background_blurred", it) }
            cardContent.background = null
            backgroundImageView.setImageBitmap(blurred)
            backgroundImageView.visibility = View.VISIBLE
            view.findViewById<View>(R.id.strip_img_view)?.visibility = View.GONE
            view.findViewById<View>(R.id.thumbnail_img_view)?.visibility = View.GONE
        } else {
            backgroundImageView.visibility = View.GONE
            if (pass.accentColor != 0) {
                cardContent.setBackgroundColor(pass.accentColor)
            }
        }

        val titleTextColor = if (pass.foregroundColor != 0) pass.foregroundColor else null
        val labelTextColor = if (pass.labelColor != 0) pass.labelColor else titleTextColor

        view.findViewById<TextView>(R.id.passTitle).apply {
            text = pass.description
            titleTextColor?.let { setTextColor(it) }
        }

        view.findViewById<TextView>(R.id.date).apply {
            labelTextColor?.let { setTextColor(it) }
        }

        val lastUpdatedView = view.findViewById<TextView>(R.id.last_updated_text)
        val lastUpdatedMillis = File(passStore.getPathForID(pass.id), "pass.json").lastModified()
        if (lastUpdatedMillis > 0) {
            val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastUpdatedMillis))
            lastUpdatedView.text = view.context.getString(R.string.last_updated, formatted)
            lastUpdatedView.visibility = View.VISIBLE
            labelTextColor?.let { lastUpdatedView.setTextColor(it) }
        } else {
            lastUpdatedView.visibility = View.GONE
        }

        val isMuted = PassNotificationPrefs.isMuted(view.context, pass.passIdent, pass.serial)
        view.findViewById<ImageView>(R.id.notifications_state_icon).apply {
            setImageResource(if (isMuted) R.drawable.ic_notifications_off else R.drawable.ic_notifications_active)
            // Fixed black would disappear on dark-background passes - tint with the pass's
            // own foreground color, same as the title/date text right next to it.
            imageTintList = titleTextColor?.let { android.content.res.ColorStateList.valueOf(it) }
        }
    }

    fun getExtraString(pass: Pass) = pass.fields.firstOrNull()?.let { getExtraStringForField(it) }


    private fun getExtraStringForField(passField: PassField): String {
        val stringBuilder = StringBuilder()

        if (passField.label != null) {
            stringBuilder.append(passField.label)

            if (passField.value != null) {
                stringBuilder.append(": ")
            }
        }

        if (passField.value != null) {
            stringBuilder.append(passField.value)
        }

        return "$stringBuilder"
    }

    private fun setDateTextFromDateAndPrefix(prefix: String, relevantDate: ZonedDateTime): String {
        val relativeDateTimeString = DateUtils.getRelativeDateTimeString(
            view.context,
            relevantDate.toEpochSecond() * 1000,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0
        )

        return prefix + relativeDateTimeString
    }

    protected fun getTimeInfoString(pass: Pass) = when {
        pass.calendarTimespan?.from != null -> setDateTextFromDateAndPrefix("", pass.calendarTimespan!!.from!!)

        pass.validTimespans.orEmpty().isNotEmpty() && pass.validTimespans!![0].to != null -> {
            val to = pass.validTimespans!![0].to
            setDateTextFromDateAndPrefix(if (to!!.isAfter(ZonedDateTime.now())) "expires " else " expired ", to)
        }
        else -> null
    }

    private fun getDateOrExtraText(pass: Pass) = when {
        pass.calendarTimespan != null -> pass.calendarTimespan
        pass.validTimespans.orEmpty().isNotEmpty() -> pass.validTimespans!![0]
        else -> null
    }

    @Visibility
    protected open fun getVisibilityForGlobalAndLocal(global: Boolean, local: Boolean) = when {
        global -> GONE
        local -> VISIBLE
        else -> INVISIBLE
    }

}
