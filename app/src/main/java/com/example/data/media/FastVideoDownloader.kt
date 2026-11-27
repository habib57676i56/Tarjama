package com.example.data.media

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedVideoDao
import com.example.data.local.DownloadedVideoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ActiveDownloadSpeed(
    val downloadId: Long,
    val bytesPerSecond: Long
)

class FastVideoDownloader(
    private val context: Context,
    private val dao: DownloadedVideoDao
) {
    companion object {
        private const val TAG = "FastVideoDownloader"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB high-throughput buffer
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _speedMap = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speedMap: StateFlow<Map<Long, Long>> = _speedMap.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun startDownload(
        title: String,
        sourceUrl: String,
        streamUrl: String,
        thumbnailUrl: String,
        qualityLabel: String,
        mimeType: String,
        subtitleSrtContent: String,
        sourceLangCode: String,
        targetLangCode: String
    ): Long {
        val safeTitle = title
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .ifBlank { "video_${System.currentTimeMillis()}" }
            .take(45)

        val downloadDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "tarjuman_videos"
        ).apply { if (!exists()) mkdirs() }

        val ext = when {
            streamUrl.contains(".webm", ignoreCase = true) -> "webm"
            streamUrl.contains(".mov", ignoreCase = true) -> "mov"
            else -> "mp4"
        }
        val targetFile = File(downloadDir, "${safeTitle}_${System.currentTimeMillis()}.$ext")

        val placeholderId = System.currentTimeMillis()
        scope.launch {
            val entity = DownloadedVideoEntity(
                title = title.ifBlank { safeTitle },
                sourceUrl = sourceUrl,
                streamUrl = streamUrl,
                localFilePath = targetFile.absolutePath,
                thumbnailUrl = thumbnailUrl,
                qualityLabel = qualityLabel,
                mimeType = mimeType,
                status = DownloadStatus.QUEUED,
                subtitleSrtContent = subtitleSrtContent,
                sourceLangCode = sourceLangCode,
                targetLangCode = targetLangCode
            )
            val id = dao.insert(entity)
            launchDownloadJob(id, streamUrl, targetFile)
        }
        return placeholderId
    }

    fun retryDownload(entity: DownloadedVideoEntity) {
        if (activeJobs[entity.id]?.isActive == true) return
        val file = File(entity.localFilePath).apply { parentFile?.mkdirs() }
        launchDownloadJob(entity.id, entity.streamUrl, file)
    }

    fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            dao.updateProgress(id, 0L, 0L, DownloadStatus.CANCELLED, "تم إلغاء التنزيل")
            _speedMap.value = _speedMap.value - id
        }
    }

    fun deleteDownload(entity: DownloadedVideoEntity) {
        activeJobs[entity.id]?.cancel()
        activeJobs.remove(entity.id)
        scope.launch {
            runCatching { File(entity.localFilePath).delete() }
            dao.deleteById(entity.id)
            _speedMap.value = _speedMap.value - entity.id
        }
    }

    private fun launchDownloadJob(id: Long, streamUrl: String, targetFile: File) {
        val job = scope.launch {
            try {
                dao.updateProgress(id, 0L, 0L, DownloadStatus.DOWNLOADING)

                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("فشل الخادم برمز HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("ملف الفيديو غير متاح")
                    val totalBytes = body.contentLength().coerceAtLeast(0L)

                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReportTime = System.currentTimeMillis()
                    var bytesSinceLastReport = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                bytesSinceLastReport += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastReportTime
                                if (elapsed >= 350L) {
                                    val speed = (bytesSinceLastReport * 1000L) / elapsed.coerceAtLeast(1L)
                                    _speedMap.value = _speedMap.value + (id to speed)
                                    dao.updateProgress(
                                        id = id,
                                        downloadedBytes = downloaded,
                                        totalBytes = maxOf(totalBytes, downloaded),
                                        status = DownloadStatus.DOWNLOADING
                                    )
                                    lastReportTime = now
                                    bytesSinceLastReport = 0L
                                }
                            }
                            output.flush()
                        }
                    }

                    _speedMap.value = _speedMap.value - id
                    dao.updateProgress(
                        id = id,
                        downloadedBytes = downloaded,
                        totalBytes = maxOf(totalBytes, downloaded),
                        status = DownloadStatus.COMPLETED
                    )
                }
            } catch (e: CancellationException) {
                _speedMap.value = _speedMap.value - id
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for id=$id", e)
                _speedMap.value = _speedMap.value - id
                runCatching { targetFile.delete() }
                dao.updateProgress(
                    id = id,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "تعذر تنزيل الفيديو"
                )
            } finally {
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }
}
