package com.nusanime.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.nusanime.app.MainActivity
import com.nusanime.app.R
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.db.AppDatabase
import java.util.concurrent.TimeUnit

object EpisodeNotifier {

    const val CHANNEL_ID = "new_episodes"
    private const val WORK_NAME = "episode_check"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Episode Baru", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<CheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, animeId: String, title: String, episodeId: String, poster: String?) {
        if (!hasPermission(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("openEpisode", episodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, episodeId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Episode baru: $title")
            .setContentText(episodeId)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context)
            .notify(episodeId.hashCode(), n)
    }

    class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val db = AppDatabase.get(applicationContext)
            val followed = db.followedDao().allOnce()
            if (followed.isEmpty()) return Result.success()
            return try {
                val env = ApiClient.api.ongoing(1)
                val list = env.data?.animeList.orEmpty()
                for (f in followed) {
                    val item = list.firstOrNull { it.animeId == f.animeId } ?: continue
                    val newEp = item.episodeId
                    if (newEp != null && newEp != f.lastEpisodeId) {
                        notify(applicationContext, f.animeId, f.title, newEp, f.poster)
                        db.followedDao().upsert(f.copy(lastEpisodeId = newEp))
                    }
                }
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }
}
