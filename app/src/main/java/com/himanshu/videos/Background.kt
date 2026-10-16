package com.himanshu.videos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        FeedWorker.schedule(this)
    }
}

object Notifier {
    const val CHANNEL = "uploads"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "New uploads", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a creator you follow uploads"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission")
    suspend fun notify(ctx: Context, v: Video) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val bmp: Bitmap? = try {
            val req = ImageRequest.Builder(ctx)
                .data("https://i.ytimg.com/vi/${v.id}/hqdefault.jpg")
                .allowHardware(false)
                .build()
            ((ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }

        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_VIDEO, v.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, v.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F7A55.toInt())
            .setContentTitle("${v.channelName} uploaded")
            .setContentText(v.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setGroup("hv_uploads")
        if (bmp != null) {
            b.setLargeIcon(bmp)
            b.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bmp)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(v.title)
            )
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(v.title))
        }
        NotificationManagerCompat.from(ctx).notify(v.id.hashCode(), b.build())
    }
}

/** Runs every ~15 minutes, checks every followed creator's feed, notifies about fresh uploads. */
class FeedWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Look for app updates at most every 6 hours
        val prefs = applicationContext.getSharedPreferences("hv", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("update_checked", 0) > 6 * 3_600_000L) {
            prefs.edit().putLong("update_checked", System.currentTimeMillis()).apply()
            runCatching { Updater.notifyIfNew(applicationContext) }
        }

        val store = Store(applicationContext)
        val creators = store.creators()
        if (creators.isEmpty()) return@withContext Result.success()

        val seen = store.seen().toMutableSet()
        val known = store.known().toMutableSet()
        val fresh = ArrayList<Video>()
        val fetched = ArrayList<Video>()
        val okIds = HashSet<String>()
        val now = System.currentTimeMillis()

        for (c in creators) {
            val vids = try { YouTube.feed(c.id).second } catch (e: Exception) { continue }
            okIds += c.id
            fetched += vids
            if (c.id in known) {
                fresh += vids.filter { it.id !in seen && now - it.published < 3L * 86_400_000L }
            }
            known += c.id
            seen += vids.map { it.id }
        }
        store.saveSeen(seen)
        store.saveKnown(known)

        if (fetched.isNotEmpty()) {
            val ids = creators.map { it.id }.toSet()
            val keep = store.cachedFeed().filter { it.channelId in ids && it.channelId !in okIds }
            store.saveFeed((fetched + keep).distinctBy { it.id }.sortedByDescending { it.published })
        }

        fresh.sortedBy { it.published }.takeLast(6).forEach { Notifier.notify(applicationContext, it) }
        Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<FeedWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("hv_feed", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
