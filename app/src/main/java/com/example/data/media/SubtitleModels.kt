package com.example.data.media

import java.util.Locale

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String = ""
)

object SubtitleParser {
    private val timeRegex = Regex(
        """(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{3})"""
    )

    fun parse(rawContent: String): List<SubtitleCue> {
        val cleaned = rawContent
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = cleaned.split(Regex("\n{2,}"))
        val cues = mutableListOf<SubtitleCue>()
        var nextIndex = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            if (lines.first().startsWith("WEBVTT", ignoreCase = true) ||
                lines.first().startsWith("NOTE", ignoreCase = true)
            ) {
                continue
            }

            val timeLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIdx == -1) continue

            val match = timeRegex.find(lines[timeLineIdx]) ?: continue
            val startMs = parseTimestamp(
                hours = match.groupValues[1].trimEnd(':').toLongOrNull() ?: 0L,
                minutes = match.groupValues[2].toLongOrNull() ?: 0L,
                seconds = match.groupValues[3].toLongOrNull() ?: 0L,
                millis = match.groupValues[4].toLongOrNull() ?: 0L
            )
            val endMs = parseTimestamp(
                hours = match.groupValues[5].trimEnd(':').toLongOrNull() ?: 0L,
                minutes = match.groupValues[6].toLongOrNull() ?: 0L,
                seconds = match.groupValues[7].toLongOrNull() ?: 0L,
                millis = match.groupValues[8].toLongOrNull() ?: 0L
            )

            val textLines = lines.drop(timeLineIdx + 1)
                .joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (textLines.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        index = nextIndex++,
                        startMs = startMs,
                        endMs = maxOf(endMs, startMs + 500L),
                        originalText = textLines
                    )
                )
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun findActiveCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        return cues.lastOrNull { positionMs in it.startMs..it.endMs }
    }

    fun exportToSrt(cues: List<SubtitleCue>, useTranslated: Boolean = true): String {
        val sb = StringBuilder()
        cues.forEachIndexed { idx, cue ->
            val text = if (useTranslated && cue.translatedText.isNotBlank()) {
                cue.translatedText
            } else {
                cue.originalText
            }
            sb.append(idx + 1).append('\n')
            sb.append(formatSrtTime(cue.startMs))
                .append(" --> ")
                .append(formatSrtTime(cue.endMs))
                .append('\n')
            sb.append(text).append("\n\n")
        }
        return sb.toString()
    }

    private fun parseTimestamp(hours: Long, minutes: Long, seconds: Long, millis: Long): Long {
        return (hours * 3600L + minutes * 60L + seconds) * 1000L + millis
    }

    fun formatSrtTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val millis = (ms % 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatDisplayDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
