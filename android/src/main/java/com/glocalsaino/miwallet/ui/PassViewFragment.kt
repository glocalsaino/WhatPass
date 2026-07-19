package com.glocalsaino.miwallet.ui

import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.parseAsHtml
import androidx.core.text.util.LinkifyCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.startActivityFromClass
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.databinding.ActivityPassViewPageBinding
import com.glocalsaino.miwallet.maps.PassbookMapsFacade
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.ui.pass_view_holder.VerbosePassViewHolder

class PassViewFragment : Fragment() {

    private val passViewHelper by lazy { PassViewHelper(requireActivity()) }
    internal val passStore: PassStore by inject()
    lateinit var pass: Pass

    private var _binding: ActivityPassViewPageBinding? = null
    private val binding get() = _binding!!


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
        val moreTextView = requireView().findViewById<ImageView>(R.id.moreTextView)
        val back_fields = requireView().findViewById<TextView>(R.id.back_fields)
        // Without this, the arrow stays a fixed white and disappears on light-background
        // passes - tint it with the pass's own foreground color, which is always chosen
        // by the issuer to contrast against their background.
        if (pass.foregroundColor != 0) {
            moreTextView.imageTintList = android.content.res.ColorStateList.valueOf(pass.foregroundColor)
        }
        moreTextView.setOnClickListener {

            if (back_fields.visibility == View.VISIBLE) {
                back_fields.visibility = View.GONE
                moreTextView.animate().rotation(0f)
            } else {
                back_fields.visibility = View.VISIBLE
                moreTextView.animate().rotation(180f)
            }
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

        // Unlike the other pass images, the thumbnail keeps a fixed small square size next to
        // the fields (per Apple's layout), so it bypasses processImage()'s auto-sizing.
        val thumbnailView = requireView().findViewById<ImageView>(R.id.thumbnail_img_view)
        val thumbnailBitmap = if (stripBitmap == null && pass.type in PassDefinitions.THUMBNAIL_VISIBLE_TYPES) {
            pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_THUMBNAIL)
        } else null

        if (thumbnailBitmap != null) {
            thumbnailView.setImageBitmap(thumbnailBitmap)
            thumbnailView.visibility = View.VISIBLE
            thumbnailView.setOnClickListener {
                val intent = Intent(thumbnailView.context, TouchImageActivity::class.java)
                intent.putExtra("IMAGE", PassBitmapDefinitions.BITMAP_THUMBNAIL)
                startActivity(intent)
            }
        } else {
            thumbnailView.visibility = View.GONE
        }

        val map_container = requireView().findViewById<View>(R.id.map_container)
        if (map_container != null) {
            if (!(pass.locations.isNotEmpty() && PassbookMapsFacade.init(activity as FragmentActivity))) {
                @Suppress("PLUGIN_WARNING")
                map_container.visibility = View.GONE
            }
        }

        val backStrBuilder = StringBuilder()

        val front_field_container = requireView().findViewById<LinearLayout>(R.id.front_field_container)
        front_field_container.removeAllViews()

        val header_field_container = requireView().findViewById<LinearLayout>(R.id.header_field_container)
        header_field_container.removeAllViews()

        val auxiliary_field_container = requireView().findViewById<LinearLayout>(R.id.auxiliary_field_container)
        auxiliary_field_container.removeAllViews()

        val secondary_field_container = requireView().findViewById<LinearLayout>(R.id.secondary_field_container)
        secondary_field_container.removeAllViews()

        val stripPrimaryFieldContainer = requireView().findViewById<LinearLayout>(R.id.strip_primary_field_container)
        stripPrimaryFieldContainer.removeAllViews()

        val labelColor = if (pass.labelColor != 0) pass.labelColor else null
        val valueColor = if (pass.foregroundColor != 0) pass.foregroundColor else null

        // When a strip banner is present on these types, primaryFields are overlaid on top of
        // it (always in white, value above and much bigger than the label) instead of in the
        // normal field flow below the image.
        val overlayPrimaryOnStrip = stripBitmap != null

        for (field in pass.fields) {
            if (field.hide) {
                backStrBuilder.append(field.toHtmlSnippet(labelColor, valueColor))
            } else if (overlayPrimaryOnStrip && field.hint == "primaryFields" && (field.label != null || field.value != null)) {
                val v = requireActivity().layoutInflater.inflate(R.layout.strip_overlay_field_item, stripPrimaryFieldContainer, false)
                v.findViewById<TextView>(R.id.key).text = field.label
                v.findViewById<TextView>(R.id.value).text = field.value
                stripPrimaryFieldContainer.addView(v)
            } else {
                val isHeaderField = field.hint == "headerFields"
                val isAuxiliaryField = field.hint == "auxiliaryFields"
                // secondaryFields share a row (same as auxiliaryFields) instead of each
                // wrapping to its own line; primaryFields keep one full-width row each.
                val isSecondaryField = field.hint == "secondaryFields"
                val isRowSharedField = isAuxiliaryField || isSecondaryField
                val container = when {
                    isHeaderField -> header_field_container
                    isAuxiliaryField -> auxiliary_field_container
                    isSecondaryField -> secondary_field_container
                    else -> front_field_container
                }
                // Type hierarchy by text size: auxiliaryFields < secondaryFields < primaryFields.
                val layoutRes = when {
                    isHeaderField -> R.layout.vertical_field_item
                    isAuxiliaryField -> R.layout.auxiliary_field_item
                    isSecondaryField -> R.layout.secondary_field_item
                    else -> R.layout.main_field_item
                }

                val v = requireActivity().layoutInflater.inflate(layoutRes, container, false)
                if (isHeaderField) {
                    (v.layoutParams as LinearLayout.LayoutParams).weight = 0f
                }
                val key = v?.findViewById<TextView>(R.id.key)
                key?.text = field.label
                labelColor?.let { key?.setTextColor(it) }
                val value = v?.findViewById<TextView>(R.id.value)
                value?.text = field.value
                valueColor?.let { value?.setTextColor(it) }

                // Apple's Wallet convention: when several fields share a row (same container),
                // the first is left-aligned and the rest are right-aligned against the pass edge.
                if (isRowSharedField && container.childCount > 0) {
                    key?.gravity = android.view.Gravity.END
                    value?.gravity = android.view.Gravity.END
                }

                container.addView(v)
            }
        }

        if (backStrBuilder.isNotEmpty()) {
            back_fields.text = "$backStrBuilder".parseAsHtml()
            moreTextView.visibility = View.VISIBLE
        } else {
            moreTextView.visibility = View.GONE
        }

        LinkifyCompat.addLinks(back_fields, Linkify.ALL)
        back_fields.movementMethod = android.text.method.LinkMovementMethod.getInstance()

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

        val passExtrasView = layoutInflater.inflate(R.layout.pass_view_extra_data, passExtrasContainer, false)
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