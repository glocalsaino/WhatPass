package com.glocalsaino.miwallet.ui

import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.RelativeLayout
import android.widget.LinearLayout
import androidx.core.text.parseAsHtml
import androidx.core.text.util.LinkifyCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.startActivityFromClass
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.maps.PassbookMapsFacade
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.ui.pass_view_holder.VerbosePassViewHolder
import android.util.Log

class PassViewPKFragment : Fragment() {

    private val passViewHelper by lazy { PassViewHelper(requireActivity()) }
    internal val passStore: PassStore by inject()
    lateinit var pass: Pass

    private fun processImage(view: ImageView, name: String, pass: Pass) {
        val bitmap = pass.getBitmap(passStore, name)
        if (bitmap != null && bitmap.width > 300) {
            view.setOnClickListener {
                val intent = Intent(view.context, TouchImageActivity::class.java)
                intent.putExtra("IMAGE", name)
                startActivity(intent)
            }
        }
        // The strip banner must always span the full card width per Apple's PassKit layout,
        // regardless of the source image's native resolution - so it's exempt from the
        // minimum touch-target height enforcement applied to small tappable images.
        val enforceMinHeight = name != PassBitmapDefinitions.BITMAP_STRIP
        passViewHelper.setBitmapSafe(view, bitmap, enforceMinHeight)
    }

    override fun onResume() {
        super.onResume()
        renderPass()
    }

    // Re-runs the whole binding, so this fragment can be refreshed in place after
    // the pass it's showing gets updated - not just the next time it's created fresh.
    private fun renderPass() {
        val backFields = requireView().findViewById<TextView>(R.id.back_fields)
        val moreTextView = requireView().findViewById<ImageView>(R.id.moreTextView)

        // Without this, the arrow stays a fixed white and disappears on light-background
        // passes - tint it with the pass's own foreground color, which is always chosen
        // by the issuer to contrast against their background.
        if (pass.foregroundColor != 0) {
            moreTextView.imageTintList = android.content.res.ColorStateList.valueOf(pass.foregroundColor)
        }

        moreTextView.setOnClickListener {
            if (backFields.visibility == View.VISIBLE) {
                backFields.visibility = View.GONE
                moreTextView.animate().rotation(0f)
            } else {
                backFields.visibility = View.VISIBLE
                moreTextView.animate().rotation(180f)
            }
        }

        val fieldMap : Map<String, ViewGroup> = mapOf(
            "primaryFields" to requireView().findViewById(R.id.primary_field_container),
            "auxiliaryFields" to requireView().findViewById(R.id.auxiliary_field_container),
            "headerFields" to requireView().findViewById(R.id.header_field_container),
            "secondaryFields" to requireView().findViewById(R.id.secondary_field_container)
        )

        val fieldCount = mutableMapOf<String, Int>()

        fieldMap.forEach {
            fieldCount[it.key] = 0
        }

        requireView().findViewById<View>(R.id.barcode_img).setOnClickListener {
            activity?.startActivityFromClass(FullscreenBarcodeActivity::class.java)
        }

        BarcodeUIController(requireView(), pass.barCode, requireActivity(), passViewHelper)

        processImage(requireView().findViewById(R.id.logo_img_view), PassBitmapDefinitions.BITMAP_LOGO, pass)
        processImage(requireView().findViewById(R.id.footer_img_view), PassBitmapDefinitions.BITMAP_FOOTER, pass)

        requireView().findViewById<TextView>(R.id.logo_text_view).apply {
            text = pass.logoText
            visibility = if (pass.logoText.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (pass.foregroundColor != 0) setTextColor(pass.foregroundColor)
        }

        // A strip banner and a thumbnail are mutually exclusive: per Apple's PassKit rules the
        // full-width strip, when present, always takes priority and the thumbnail is hidden.
        val stripBitmap = if (pass.type in PassDefinitions.STRIP_VISIBLE_TYPES) {
            pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_STRIP)
        } else null

        val stripView = requireView().findViewById<ImageView>(R.id.strip_img_view)
        if (stripBitmap != null) {
            processImage(stripView, PassBitmapDefinitions.BITMAP_STRIP, pass)
        } else {
            stripView.visibility = View.GONE
        }

        val thumbnailView = requireView().findViewById<ImageView>(R.id.thumbnail_img_view)
        if (stripBitmap == null && pass.type in PassDefinitions.THUMBNAIL_VISIBLE_TYPES) {
            processImage(thumbnailView, PassBitmapDefinitions.BITMAP_THUMBNAIL, pass)
        } else {
            thumbnailView.visibility = View.GONE
        }

        val mapContainer = requireView().findViewById<View>(R.id.map_container)
        if (mapContainer != null) {
            if (!(pass.locations.isNotEmpty() && PassbookMapsFacade.init(activity as FragmentActivity))) {
                @Suppress("PLUGIN_WARNING")
                mapContainer.visibility = View.GONE
            }
        }

        val backStrBuilder = StringBuilder()

        fieldMap.forEach {
            it.value.removeAllViews()
        }

        val labelColor = if (pass.labelColor != 0) pass.labelColor else null
        val valueColor = if (pass.foregroundColor != 0) pass.foregroundColor else null

        for (field in pass.fields) {
            val hint = field.hint
            if (field.hide) {
                backStrBuilder.append(field.toHtmlSnippet(labelColor, valueColor))
            } else if (hint != null) {
                val v = requireActivity().layoutInflater.inflate(R.layout.vertical_field_item, requireView().findViewById(R.id.header_field_container), false)
                val key = v?.findViewById<TextView>(R.id.key)
                key?.text = field.label
                labelColor?.let { key?.setTextColor(it) }
                val value = v?.findViewById<TextView>(R.id.value)
                value?.text = field.value
                valueColor?.let { value?.setTextColor(it) }
                Log.i("MiWallet", "creating header with tag = " + field.label + " value = " + field.value)

                // Type hierarchy by text size: auxiliaryFields < secondaryFields < primaryFields.
                when (hint) {
                    "primaryFields" -> {
                        value?.textSize = 40f
                        key?.textSize = 20f
                    }
                    "secondaryFields" -> {
                        value?.textSize = 18f
                        key?.textSize = 14f
                    }
                    "auxiliaryFields" -> {
                        value?.textSize = 14f
                        key?.textSize = 12f
                    }
                }

                if (hint.equals("primaryFields")) {
                    val params = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    )
                    if (fieldCount[hint]!! == 0) {
                        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                        value?.setGravity(Gravity.LEFT)
                        key?.setGravity(Gravity.LEFT)
                        v?.setLayoutParams(params)
                    } else {
                        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                        value?.setGravity(Gravity.RIGHT)
                        key?.setGravity(Gravity.RIGHT)
                        v?.setLayoutParams(params)
                    }
                }
                fieldMap[hint]!!.addView(v)
                fieldCount[hint] = 1 + fieldCount[hint]!!
                    
            } 
        }

        if (backStrBuilder.isNotEmpty()) {
            backFields.text = "$backStrBuilder".parseAsHtml()
            moreTextView.visibility = View.VISIBLE
        } else {
            moreTextView.visibility = View.GONE
        }

        // Boarding passes show a transit icon (plane/train/bus/boat/generic arrow) between the
        // origin and destination primaryFields, matching the declared transitType.
        val transitIconRes = when (pass.transitType) {
            "PKTransitTypeAir" -> R.drawable.ic_transit_air
            "PKTransitTypeTrain" -> R.drawable.ic_transit_train
            "PKTransitTypeBus" -> R.drawable.ic_transit_bus
            "PKTransitTypeBoat" -> R.drawable.ic_transit_boat
            "PKTransitTypeGeneric" -> R.drawable.ic_transit_generic
            else -> null
        }
        val transitIconView = requireView().findViewById<ImageView>(R.id.transit_icon_view)
        if (transitIconRes != null) {
            transitIconView.setImageResource(transitIconRes)
            valueColor?.let { transitIconView.setColorFilter(it) }
            transitIconView.visibility = View.VISIBLE
        } else {
            transitIconView.visibility = View.GONE
        }

        LinkifyCompat.addLinks(backFields, Linkify.ALL)
        backFields.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        val passViewHolder = VerbosePassViewHolder(requireView().findViewById(R.id.pass_card))
        passViewHolder.apply(pass, passStore, requireActivity())

        // This full-page detail view already shows branding via the pass artwork,
        // so the title/category badge used by the list row is redundant here.
        requireView().findViewById<View>(R.id.passTitle).visibility = View.GONE
        requireView().findViewById<View>(R.id.categoryView).visibility = View.GONE
        requireView().findViewById<View>(R.id.last_updated_text).visibility = View.GONE
        requireView().findViewById<View>(R.id.notifications_state_icon).visibility = View.GONE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {

        val rootView = inflater.inflate(R.layout.activity_pass_view_page, container, false)
        arguments?.takeIf { it.containsKey(PassViewActivityBase.EXTRA_KEY_UUID) }?.apply {
            val uuid = getString(PassViewActivityBase.EXTRA_KEY_UUID)
            pass = if (uuid != null) {
                passStore.getPassbookForId(uuid) ?: passStore.currentPass!!
            } else {
                passStore.currentPass!!
            }
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val passExtrasContainer = view.findViewById<LinearLayout>(R.id.passExtrasContainer)
        val passExtrasView = layoutInflater.inflate(R.layout.pkpass_view_extra_data, passExtrasContainer, false)
        passExtrasContainer.addView(passExtrasView)

        // A manual/push update overwrites this pass's data on disk while this fragment may
        // already be on screen - without this, you'd only see the change after navigating
        // away and back in.
        viewLifecycleOwner.lifecycleScope.launch {
            for (update in passStore.updateChannel.openSubscription()) {
                val refreshed = passStore.getPassbookForId(pass.id) ?: continue
                pass = refreshed
                renderPass()
            }
        }
    }
}
