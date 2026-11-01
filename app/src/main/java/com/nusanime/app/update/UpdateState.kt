package com.nusanime.app.update

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-wide update download state so the download keeps running in background
 * (user can navigate anywhere / watch anime) and progress is visible everywhere.
 */
object UpdateState {
    data class St(val active: Boolean = false, var progress: Float = 0f, var note: String? = null, var ready: java.io.File? = null, var error: String? = null)

    var st = mutableStateOf(St())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Start download once; ignores repeated calls while active. Returns true if started. */
    @Synchronized
    fun start(context: android.content.Context, info: SelfUpdater.ReleaseInfo): Boolean {
        if (st.value.active) return false
        st.value = St(active = true, note = "Mengunduh v${info.versionName}…")
        val appCtx = context.applicationContext
        scope.launch {
            val f = SelfUpdater.downloadApk(appCtx, info.apkUrl) { done, total ->
                if (total > 0) st.value = st.value.copy(progress = (done.toFloat() / total).coerceIn(0f, 1f))
            }
            if (f != null) {
                st.value = st.value.copy(active = false, ready = f, note = "Update siap di-install ✅", progress = 1f)
            } else {
                st.value = st.value.copy(active = false, error = "Gagal mengunduh update")
            }
        }
        return true
    }

    fun install(context: android.content.Context) {
        val f = st.value.ready ?: return
        try { SelfUpdater.install(context, f) } catch (_: Exception) {}
    }
}
