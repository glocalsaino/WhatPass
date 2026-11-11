package com.glocalsaino.miwallet.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.glocalsaino.miwallet.R

/**
 * Edge-to-edge support for Android 15+ (API 35), where apps targeting SDK 35+ are always
 * drawn behind the status and navigation bars.
 *
 * Screens that use the window-decor ActionBar (AppTheme, no own Toolbar) are fixed globally:
 * on API 35+ AppCompat places that ActionBar below the status bar but lays the content out
 * from y=0, underneath it, and leaves the status bar area showing the window background.
 * [DecorActionBarInsetsCallbacks] pushes the content below the ActionBar, keeps it clear of
 * the navigation bar, and paints a primary-colored strip behind the status bar.
 *
 * Screens with their own header and no ActionBar call [padForSystemBars] on their root view.
 * Screens with a CoordinatorLayout/AppBarLayout already handle the top inset through
 * fitsSystemWindows and use app:statusBarForeground in XML.
 */
object EdgeToEdge {

    val isEnforced: Boolean get() = Build.VERSION.SDK_INT >= 35

    fun register(app: Application) {
        if (isEnforced) app.registerActivityLifecycleCallbacks(DecorActionBarInsetsCallbacks)
    }

    /** Pads [view] by the system bar (and display cutout) insets, on top of its XML padding. */
    fun padForSystemBars(view: View) {
        if (!isEnforced) return
        val start = view.paddingLeft
        val top = view.paddingTop
        val end = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(SYSTEM_BARS)
            v.updatePadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Lifts a floating view (e.g. a FAB) above the navigation bar, on top of its XML margins. */
    fun marginAboveNavigationBar(view: View) {
        if (!isEnforced) return
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val bottom = lp.bottomMargin
        val end = lp.rightMargin
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(SYSTEM_BARS)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottom + bars.bottom
                rightMargin = end + bars.right
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Lets a scrolling view draw behind the navigation bar while its last item can scroll clear of it. */
    fun scrollClearOfNavigationBar(view: ViewGroup) {
        if (!isEnforced) return
        val bottom = view.paddingBottom
        view.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = bottom + insets.getInsets(SYSTEM_BARS).bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private val SYSTEM_BARS =WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    private object DecorActionBarInsetsCallbacks : Application.ActivityLifecycleCallbacks {

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            // Only the window-decor ActionBar has this container; screens with their own
            // Toolbar or with no ActionBar at all are left alone.
            val actionBarContainer = activity.findViewById<View>(androidx.appcompat.R.id.action_bar_container)
                ?: return
            if (actionBarContainer.visibility != View.VISIBLE) return
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val decor = activity.window.decorView as? FrameLayout ?: return

            val statusBarStrip = View(activity).apply {
                setBackgroundColor(ContextCompat.getColor(activity, R.color.primary))
            }
            decor.addView(statusBarStrip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))

            val actionBarSize = TypedValue().let {
                if (activity.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, it, true))
                    TypedValue.complexToDimensionPixelSize(it.data, activity.resources.displayMetrics)
                else 0
            }

            ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                // AppCompat's decor already adds the ActionBar height to the top inset it hands
                // to the content, so the top inset alone is normally enough. The max() covers
                // AppCompat versions that pass only the raw status bar inset.
                val bars = insets.getInsets(SYSTEM_BARS)
                val statusBarHeight = ViewCompat.getRootWindowInsets(decor)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
                val actionBarHeight = actionBarContainer.height.takeIf { it > 0 } ?: actionBarSize
                val top = maxOf(bars.top, statusBarHeight + actionBarHeight)
                v.updatePadding(bars.left, top, bars.right, bars.bottom)
                statusBarStrip.updateLayoutParams { height = statusBarHeight }
                insets
            }
            ViewCompat.requestApplyInsets(content)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
