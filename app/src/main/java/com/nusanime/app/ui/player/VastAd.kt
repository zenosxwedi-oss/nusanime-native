package com.nusanime.app.ui.player

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Fetches a VAST response and extracts the ad media URL (CDATA in first MediaFile).
 * Runs on a background thread; callback on main thread. Null = no fill / failure.
 */
object VastAd {
    @Volatile private var cachedMedia: Pair<String, Long>? = null // url to impression? just media
    private const val H_ZONE = "https://s.magsrv.com/v1/vast.php?idz=6065738&ex-av=name"
    private const val V_ZONE = "https://s.magsrv.com/v1/vast.php?idzone=6065846&ex-av=name"

    fun fetch(onResult: (String?) -> Unit) {
        Thread {
            val zone = if (Math.random() < 0.5) H_ZONE else V_ZONE
            var result: String? = null
            try {
                val conn = URL(zone).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/126 Mobile")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.contains("<Ad")) {
                    val p = Pattern.compile("<MediaFile[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</MediaFile>", Pattern.DOTALL)
                    val m = p.matcher(body)
                    if (m.find()) result = m.group(1)?.trim()
                }
            } catch (_: Exception) {}
            val r = result
            Handler(Looper.getMainLooper()).post { onResult(r) }
        }.start()
    }
}
