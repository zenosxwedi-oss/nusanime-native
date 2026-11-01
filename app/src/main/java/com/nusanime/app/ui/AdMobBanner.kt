package com.nusanime.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** AdMob adaptive banner (unit = Google test banner; swap with real unit when approved). */
@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    val unitId = "ca-app-pub-3946939950557724/6300978111" // test banner
    AndroidView(
        modifier = modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = unitId
                setAdSize(AdSize.BANNER)
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
