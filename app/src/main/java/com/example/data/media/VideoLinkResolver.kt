package com.example.data.media

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

data class VideoStreamOption(
    val url: String,
    val qualityLabel: String,
    val mimeType: String = "video/mp4",
    val isHls: Boolean = false,
    val isDownloadableDirect: Boolean = true
)

data class SubtitleTrackOption(
    val url: String,
    val label: String,
    val languageCode: String
)

data class ResolvedVideoInfo(
    val originalUrl: String,
    val title: String,
    val thumbnailUrl: String = "",
    val streams: List<VideoStreamOption> = emptyList(),
    val subtitleTracks: List<SubtitleTrackOption> = emptyList(),
    val pageTextPreview: String = "",
    val requiresWebViewFallback: Boolean = false
)

class VideoLinkResolver {
    companion object {
        private const val TAG = "VideoLinkResolver"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun extractFirstUrl(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.startsWith("content://") || trimmed.startsWith("file://")) {
                return trimmed
            }
            val urlRegex = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)
            val found = urlRegex.find(trimmed)?.value?.trimEnd('.', ',', ';', ')', ']')
            if (found != null) return found
            if (trimmed.contains('.') && !trimmed.contains(' ')) {
                return "https://$trimmed"
            }
            return null
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolve(rawInput: String): Result<ResolvedVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = extractFirstUrl(rawInput)
                ?: return@withContext Result.failure(IllegalArgumentException("يرجى إدخال رابط صحيح."))

            if (cleanUrl.startsWith("content://") || cleanUrl.startsWith("file://")) {
                val uri = Uri.parse(cleanUrl)
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "فيديو محلي"
                return@withContext Result.success(
                    ResolvedVideoInfo(
                        originalUrl = cleanUrl,
                        title = name,
                        streams = listOf(
                            VideoStreamOption(
                                url = cleanUrl,
                                qualityLabel = "الأصلية (محلي)",
                                isDownloadableDirect = false
                            )
                        )
                    )
                )
            }

            if (isDirectMediaExtension(cleanUrl)) {
                val isHls = cleanUrl.contains(".m3u8", ignoreCase = true)
                val title = Uri.parse(cleanUrl).lastPathSegment?.substringBefore('?') ?: "فيديو مباشر"
                return@withContext Result.success(
                    ResolvedVideoInfo(
                        originalUrl = cleanUrl,
                        title = title,
                        streams = listOf(
                            VideoStreamOption(
                                url = cleanUrl,
                                qualityLabel = inferQualityFromUrl(cleanUrl, isHls),
                                mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                                isHls = isHls,
                                isDownloadableDirect = !isHls
                            )
                        )
                    )
                )
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*;q=0.8,*/*;q=0.5")
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty().lowercase()

                if (contentType.startsWith("video/") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("dash+xml")
                ) {
                    val isHls = contentType.contains("mpegurl") || finalUrl.contains(".m3u8", true)
                    return@withContext Result.success(
                        ResolvedVideoInfo(
                            originalUrl = finalUrl,
                            title = Uri.parse(finalUrl).lastPathSegment ?: "فيديو مباشر",
                            streams = listOf(
                                VideoStreamOption(
                                    url = finalUrl,
                                    qualityLabel = inferQualityFromUrl(finalUrl, isHls),
                                    mimeType = contentType.substringBefore(';'),
                                    isHls = isHls,
                                    isDownloadableDirect = !isHls
                                )
                            )
                        )
                    )
                }

                val html = response.body?.string().orEmpty()
                if (html.isBlank()) {
                    return@withContext Result.success(
                        ResolvedVideoInfo(
                            originalUrl = finalUrl,
                            title = Uri.parse(finalUrl).host ?: "فيديو ويب",
                            requiresWebViewFallback = true
                        )
                    )
                }

                val parsed = parseHtmlForVideo(finalUrl, html)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve video link", e)
            Result.failure(e)
        }
    }

    suspend fun fetchSubtitleText(subtitleUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(subtitleUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Result.failure(IllegalStateException("ملف الترجمة فارغ"))
                } else {
                    Result.success(body)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch subtitle track", e)
            Result.failure(e)
        }
    }

    private fun parseHtmlForVideo(baseUrl: String, html: String): ResolvedVideoInfo {
        val unescapedHtml = html
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")

        val title = extractMetaContent(html, "og:title")
            ?: extractMetaContent(html, "twitter:title")
            ?: Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
            ?: Uri.parse(baseUrl).host
            ?: "فيديو ويب"

        val thumbnail = resolveRelativeUrl(
            baseUrl,
            extractMetaContent(html, "og:image")
                ?: extractMetaContent(html, "twitter:image")
        ).orEmpty()

        val foundStreams = linkedMapOf<String, VideoStreamOption>()

        val ogVideoCandidates = listOfNotNull(
            extractMetaContent(html, "og:video:secure_url"),
            extractMetaContent(html, "og:video:url"),
            extractMetaContent(html, "og:video"),
            extractMetaContent(html, "twitter:player:stream")
        )
        ogVideoCandidates.forEach { raw ->
            resolveRelativeUrl(baseUrl, raw)?.let { resolved ->
                if (isLikelyMediaUrl(resolved)) {
                    val isHls = resolved.contains(".m3u8", ignoreCase = true)
                    foundStreams.putIfAbsent(
                        resolved,
                        VideoStreamOption(
                            url = resolved,
                            qualityLabel = inferQualityFromUrl(resolved, isHls),
                            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                            isHls = isHls,
                            isDownloadableDirect = !isHls
                        )
                    )
                }
            }
        }

        // HTML5 <video> and <source>
        val sourceRegex = Regex(
            """<(?:video|source)[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        sourceRegex.findAll(unescapedHtml).forEach { match ->
            resolveRelativeUrl(baseUrl, match.groupValues[1])?.let { resolved ->
                val isHls = resolved.contains(".m3u8", ignoreCase = true)
                foundStreams.putIfAbsent(
                    resolved,
                    VideoStreamOption(
                        url = resolved,
                        qualityLabel = inferQualityFromUrl(resolved, isHls),
                        mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                        isHls = isHls,
                        isDownloadableDirect = !isHls
                    )
                )
            }
        }

        // JSON-LD & inline script media URLs
        val directMediaRegex = Regex(
            """https?://[^\s"'<>\\]+?\.(?:mp4|m3u8|webm|mov|mpd)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE
        )
        directMediaRegex.findAll(unescapedHtml).take(15).forEach { match ->
            val candidate = match.value
            val isHls = candidate.contains(".m3u8", ignoreCase = true)
            foundStreams.putIfAbsent(
                candidate,
                VideoStreamOption(
                    url = candidate,
                    qualityLabel = inferQualityFromUrl(candidate, isHls),
                    mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
                    isHls = isHls,
                    isDownloadableDirect = !isHls
                )
            )
        }

        // Subtitle <track> elements
        val subtitleTracks = mutableListOf<SubtitleTrackOption>()
        val trackRegex = Regex(
            """<track[^>]+src=["']([^"']+)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        trackRegex.findAll(unescapedHtml).forEach { match ->
            val tag = match.value
            val src = resolveRelativeUrl(baseUrl, match.groupValues[1]) ?: return@forEach
            val label = Regex("""label=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1) ?: "Subtitles"
            val srclang = Regex("""srclang=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1) ?: "auto"
            subtitleTracks.add(SubtitleTrackOption(url = src, label = label, languageCode = srclang))
        }

        val description = extractMetaContent(html, "og:description")
            ?: extractMetaContent(html, "description")
            ?: ""

        val sortedStreams = foundStreams.values.sortedWith(
            compareByDescending<VideoStreamOption> { it.qualityScore() }
                .thenByDescending { it.isDownloadableDirect }
        )

        return ResolvedVideoInfo(
            originalUrl = baseUrl,
            title = decodeHtmlEntities(title),
            thumbnailUrl = thumbnail,
            streams = sortedStreams,
            subtitleTracks = subtitleTracks,
            pageTextPreview = decodeHtmlEntities(description),
            requiresWebViewFallback = sortedStreams.isEmpty()
        )
    }

    private fun extractMetaContent(html: String, key: String): String? {
        val pattern1 = Regex(
            """<meta[^>]+(?:property|name)=["']${Regex.escape(key)}["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val pattern2 = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${Regex.escape(key)}["']""",
            RegexOption.IGNORE_CASE
        )
        return pattern1.find(html)?.groupValues?.getOrNull(1)
            ?: pattern2.find(html)?.groupValues?.getOrNull(1)
    }

    private fun resolveRelativeUrl(baseUrl: String, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            URL(URL(baseUrl), raw.trim()).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isDirectMediaExtension(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mp4") ||
            path.endsWith(".m3u8") ||
            path.endsWith(".mpd") ||
            path.endsWith(".webm") ||
            path.endsWith(".mov") ||
            path.endsWith(".mkv")
    }

    private fun isLikelyMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return isDirectMediaExtension(url) ||
            lower.contains("video") ||
            lower.contains("stream") ||
            lower.contains(".mp4") ||
            lower.contains(".m3u8")
    }

    private fun inferQualityFromUrl(url: String, isHls: Boolean): String {
        val lower = url.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") -> "4K Ultra HD"
            lower.contains("1080") || lower.contains("fhd") -> "1080p Full HD"
            lower.contains("720") || lower.contains("hd") -> "720p HD"
            lower.contains("480") || lower.contains("sd") -> "480p"
            lower.contains("360") -> "360p"
            isHls -> "HLS جودة تكيفية"
            else -> "MP4 جودة عالية"
        }
    }

    private fun VideoStreamOption.qualityScore(): Int {
        val label = qualityLabel.lowercase()
        return when {
            label.contains("4k") -> 100
            label.contains("1080") -> 90
            label.contains("720") -> 80
            label.contains("mp4") -> 70
            label.contains("hls") -> 60
            label.contains("480") -> 50
            else -> 40
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
