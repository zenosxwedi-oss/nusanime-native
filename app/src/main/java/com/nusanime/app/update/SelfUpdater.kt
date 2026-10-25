package com.nusanime.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app self-update: checks GitHub releases for a newer versionCode,
 * downloads the APK and opens the installer prompt.
 */
object SelfUpdater {
    private const val REPO = "zenosxwedi-oss/nusanime-native"

    data class ReleaseInfo(val versionName: String, val versionCode: Int, val apkUrl: String)

    suspend fun latest(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.getString("name")
                    if (name.endsWith(".apk")) { apkUrl = a.getString("browser_download_url"); break }
                }
                val tag = json.getString("tag_name").removePrefix("v")
                // parse versionCode from release body? simpler: compare versionName tag
                ReleaseInfo(versionName = tag, versionCode = -1, apkUrl = apkUrl ?: return@withContext null)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun currentVersionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: Exception) { "0" }

    /** semantic-ish compare: 2.2.0 vs 3.0.0 */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun downloadApk(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir("updates"), "").apply { mkdirs() }
            val out = File(dir, "nusanime-update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.use { ins -> out.outputStream().use { fos -> ins.copyTo(fos) } }
                out
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun install(context: Context, apk: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= 24) {
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
            } else {
                Uri.fromFile(apk)
            }
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        } catch (_: Exception) {}
    }
}
