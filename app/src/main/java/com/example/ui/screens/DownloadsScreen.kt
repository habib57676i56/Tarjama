package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedVideoEntity
import com.example.ui.components.FreeBadgePill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassPillButton
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.LiquidCyan
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun DownloadsScreen(
    downloads: List<DownloadedVideoEntity>,
    speedMap: Map<Long, Long>,
    viewModel: MainViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }
    val totalSavedBytes = downloads
        .filter { it.status == DownloadStatus.COMPLETED }
        .sumOf { it.fileSizeBytes }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "التنزيلات السريعة",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FreeBadgePill()
                }
            }

            // Summary Glass Card
            item {
                LiquidGlassCard(accentGlow = LiquidCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "$completedCount فيديو محفوظ بجودة عالية",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "إجمالي المساحة: ${formatBytes(totalSavedBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (downloads.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HighQuality,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "لا توجد فيديوهات منزّلة بعد",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الصق رابط أي فيديو في تبويب الفيديو واضغط (تنزيل سريع) لحفظه بجودة عالية ومشاهدته مع الترجمة النصية في أي وقت.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(downloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        speedBytesPerSec = speedMap[item.id] ?: 0L,
                        onWatch = { viewModel.watchDownloadedVideo(item) },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onRetry = { viewModel.retryDownload(item) },
                        onDelete = { viewModel.deleteDownload(item) },
                        onShareSrt = {
                            if (item.subtitleSrtContent.isNotBlank()) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${item.title}.srt")
                                    putExtra(Intent.EXTRA_TEXT, item.subtitleSrtContent)
                                }
                                context.startActivity(Intent.createChooser(share, "مشاركة ملف الترجمة"))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadedVideoEntity,
    speedBytesPerSec: Long,
    onWatch: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onShareSrt: () -> Unit
) {
    val isDownloading = item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED
    val isCompleted = item.status == DownloadStatus.COMPLETED
    val isFailed = item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED
    val progress = if (item.fileSizeBytes > 0L) {
        (item.downloadedBytes.toFloat() / item.fileSizeBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LiquidGlassCard(
        modifier = Modifier.testTag("download_item_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.qualityLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    if (item.fileSizeBytes > 0L) {
                        Text(
                            text = formatBytes(item.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.subtitleSrtContent.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = "تتوفر ترجمة محفوظة",
                            tint = EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_download_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "حذف الفيديو",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isDownloading) {
            Spacer(modifier = Modifier.height(12.dp))
            if (item.fileSizeBytes > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (speedBytesPerSec > 0L) {
                            "${formatBytes(speedBytesPerSec)}/ث • ${(progress * 100).toInt()}%"
                        } else {
                            "جاري الاتصال بأقصى سرعة..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LiquidGlassPillButton(
                    text = "إلغاء",
                    icon = Icons.Rounded.Cancel,
                    onClick = onCancel,
                    isPrimary = false,
                    testTag = "cancel_download_${item.id}"
                )
            }
        } else if (isCompleted) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassPillButton(
                    text = "مشاهدة بالترجمة الفورية",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onWatch,
                    isPrimary = true,
                    testTag = "watch_download_${item.id}",
                    modifier = Modifier.weight(1f)
                )
                if (item.subtitleSrtContent.isNotBlank()) {
                    IconButton(
                        onClick = onShareSrt,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("share_srt_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "مشاركة ملف الترجمة SRT",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "مكتمل",
                        tint = EmeraldMint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else if (isFailed) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.errorMessage.ifBlank { "تعذر إكمال التنزيل" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralAlert,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LiquidGlassPillButton(
                    text = "إعادة",
                    icon = Icons.Rounded.Refresh,
                    onClick = onRetry,
                    isPrimary = false,
                    testTag = "retry_download_${item.id}"
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
