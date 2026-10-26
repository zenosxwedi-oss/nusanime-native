package com.nusanime.app.update

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Monetag interstitial (mobile fullscreen) shown via a fullscreen WebView.
 * Shows at most once every AD_INTERVAL opens (default every 3rd open), never more than once per AD_COOLDOWN ms.
 */
object InterstitialAd {
    private const val PREFS = "interstitial_ad"
    private const val KEY_COUNT = "open_count"
    private const val KEY_LAST = "last_shown"
    private const val EVERY_N_OPENS = 2
    private const val AD_COOLDOWN_MS = 5L * 60 * 1000
    private const val AD_URL = "https://nusanime.web.id/interstitial-ad"

    fun maybeShow(activity: Activity, onClosed: (() -> Unit)? = null) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, count).apply()
        val last = prefs.getLong(KEY_LAST, 0L)
        val now = System.currentTimeMillis()
        if (count % EVERY_N_OPENS != 0) return
        if (now - last < AD_COOLDOWN_MS) return
        prefs.edit().putLong(KEY_LAST, now).apply()

        val root = activity.window.decorView as ViewGroup
        val overlay = FrameLayout(activity).apply { setBackgroundColor(0xFF10141E.toInt()) }
        val wv = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // ad clicks open externally, keep ad page inside
                    return false
                }
            }
        }
        // close button
        val close = android.widget.Button(activity).apply {
            text = "✕ Lewati"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            val dp = (14 * resources.displayMetrics.density).toInt()
            setPadding(dp, dp / 2, dp, dp / 2)
        }
        val closeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        closeParams.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        val m = (10 * activity.resources.displayMetrics.density).toInt()
        closeParams.setMargins(0, m, m, 0)
        close.setOnClickListener {
            root.removeView(overlay)
            wv.destroy()
            onClosed?.invoke()
        }
        overlay.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay.addView(close, closeParams)
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // close automatically after 20s if user does not (interstitials self-close via ad)
        Handler(Looper.getMainLooper()).postDelayed({
            if (overlay.isAttachedToWindow) {
                root.removeView(overlay)
                wv.destroy()
                onClosed?.invoke()
            }
        }, 20000)
        wv.loadUrl(AD_URL)
    }
}
