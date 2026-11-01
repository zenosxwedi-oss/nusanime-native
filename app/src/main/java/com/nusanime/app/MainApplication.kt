package com.nusanime.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * AdMob integration (rewarded interstitial).
 * IDs are the Google sample/test publisher — replace with real IDs when account is verified.
 */
object AdMobAds {
    const val REWARDED_INTERSTITIAL_UNIT = "ca-app-pub-3946939950557724/4379301426"

    @Volatile private var cached: RewardedInterstitialAd? = null
    @Volatile private var loading = false

    fun init(app: Application) {
        try { MobileAds.initialize(app) {} } catch (_: Exception) { }
    }

    /** Preload a rewarded interstitial ad. Safe to call repeatedly. */
    fun preload(context: android.content.Context) {
        if (cached != null || loading) return
        loading = true
        try {
            RewardedInterstitialAd.load(context, REWARDED_INTERSTITIAL_UNIT,
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        cached = ad; loading = false
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                cached = null
                                preload(context)
                            }
                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                cached = null
                            }
                        }
                    }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        loading = false; cached = null
                    }
                })
        } catch (_: Exception) { loading = false }
    }

    /** Show if available; onEarned fires when reward earned. Returns false when no ad ready. */
    fun show(activity: android.app.Activity, onEarned: () -> Unit = {}): Boolean {
        val ad = cached ?: return false
        cached = null
        ad.show(activity) { onEarned() }
        return true
    }
}

class MainApplication : Application() {
    companion object {
        @Volatile lateinit var instance: MainApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AdMobAds.init(this)
    }
}
