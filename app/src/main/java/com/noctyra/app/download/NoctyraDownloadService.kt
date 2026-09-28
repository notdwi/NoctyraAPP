package com.noctyra.app.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.noctyra.app.R

@OptIn(UnstableApi::class)
class NoctyraDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadCenter.CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    override fun getDownloadManager(): DownloadManager {
        DownloadCenter.init(this)
        return DownloadCenter.manager
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        DownloadCenter.notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_stat_download,
            DownloadCenter.openAppIntent(this),
            getString(R.string.download_in_progress),
            downloads,
            notMetRequirements
        )

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 4201
    }
}
