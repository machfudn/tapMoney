package id.tapmoney.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object ApkDownloader {

    fun downloadApk(context: Context, downloadUrl: String, apkFileName: String): Long? {
        return try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle(apkFileName)
                setDescription("Mengunduh pembaruan Tap Money")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkFileName)
                setMimeType("application/vnd.android.package-archive")
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(request)
        } catch (e: Exception) {
            null
        }
    }
}
