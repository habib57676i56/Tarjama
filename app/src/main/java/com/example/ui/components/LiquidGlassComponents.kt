package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.AppLanguage
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LiquidIceBlue
import com.example.ui.theme.LocalIsLiquidDark
import com.example.ui.viewmodel.AppTab

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsLiquidDark.current
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                val topGlowColor = if (isDark) LiquidCyan.copy(alpha = 0.16f) else LiquidCyan.copy(alpha = 0.14f)
                val sideGlowColor = if (isDark) AuroraViolet.copy(alpha = 0.15f) else AuroraViolet.copy(alpha = 0.10f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topGlowColor, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = size.width * 0.75f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(sideGlowColor, Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.55f),
                        radius = size.width * 0.65f
                    )
                )
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_liquid_glass_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .alpha(if (isDark) 0.20f else 0.09f)
        )
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    accentGlow: Color? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalIsLiquidDark.current
    val glassBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.11f),
                Color(0xFF0E1730).copy(alpha = 0.78f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFFEAF2FC).copy(alpha = 0.80f)
            )
        )
    }

    val specularBorder = Brush.verticalGradient(
        colors = listOf(
            (accentGlow ?: Color.White).copy(alpha = if (isDark) 0.36f else 0.85f),
            Color.White.copy(alpha = if (isDark) 0.07f else 0.35f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(glassBrush)
            .border(width = 1.dp, brush = specularBorder, shape = shape)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun LiquidGlassPillButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    testTag: String = ""
) {
    val isDark = LocalIsLiquidDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_scale"
    )

    val backgroundBrush = when {
        !enabled -> Brush.horizontalGradient(
            listOf(Color.Gray.copy(alpha = 0.25f), Color.Gray.copy(alpha = 0.18f))
        )
        isPrimary -> Brush.horizontalGradient(
            listOf(LiquidCyan, LiquidIceBlue, AuroraViolet)
        )
        isDark -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.92f), Color(0xFFE4ECF8).copy(alpha = 0.85f))
        )
    }

    val borderColor = when {
        isPrimary -> Color.White.copy(alpha = 0.45f)
        isDark -> Color.White.copy(alpha = 0.22f)
        else -> Color(0xFF0B132B).copy(alpha = 0.14f)
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isPrimary -> Color(0xFF041024)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiquidGlassFloatingDock(
    selectedTab: AppTab,
    activeDownloadCount: Int,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsLiquidDark.current
    val items = listOf(
        Triple(AppTab.VIDEO_STUDIO, "الفيديو", Icons.Rounded.PlayCircle),
        Triple(AppTab.INSTANT_TRANSLATE, "ترجمة فورية", Icons.Rounded.Translate),
        Triple(AppTab.DOWNLOADS, "التنزيلات", Icons.Rounded.Download)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val dockBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF1A2542).copy(alpha = 0.90f),
                    Color(0xFF0A1022).copy(alpha = 0.94f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color(0xFFE9F1FB).copy(alpha = 0.92f)
                )
            )
        }

        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(dockBrush)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.35f else 0.95f),
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.45f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (tab, label, icon) ->
                val isSelected = selectedTab == tab
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (isDark) LiquidCyan.copy(alpha = 0.22f) else LiquidCyan.copy(alpha = 0.24f)
                    } else {
                        Color.Transparent
                    },
                    label = "tab_bg"
                )
                val contentTint by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tab_tint"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(pillColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                                    CircleShape
                                )
                            } else Modifier
                        )
                        .clickable { onSelectTab(tab) }
                        .testTag("nav_tab_${tab.route}")
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tab == AppTab.DOWNLOADS && activeDownloadCount > 0) {
                            "$label ($activeDownloadCount)"
                        } else {
                            label
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = contentTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DualSubtitleGlassOverlay(
    translatedText: String,
    originalText: String,
    showDual: Boolean,
    fontScale: Float,
    isLiveSpeech: Boolean,
    modifier: Modifier = Modifier
) {
    if (translatedText.isBlank() && originalText.isBlank()) return

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xCC0A1124),
                        Color(0xE6050812)
                    )
                )
            )
            .border(
                1.dp,
                Color.White.copy(alpha = 0.24f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("live_subtitle_overlay"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLiveSpeech) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = "ترجمة نصية مباشرة",
                    tint = LiquidCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ترجمة نصية مباشرة • بدون دبلجة",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiquidCyan
                )
            }
        }
        if (showDual && originalText.isNotBlank() && originalText != translatedText) {
            Text(
                text = originalText,
                fontSize = (12.5f * fontScale).sp,
                lineHeight = (17f * fontScale).sp,
                color = Color(0xFFBBD0EB),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = translatedText.ifBlank { originalText },
            fontSize = (16.5f * fontScale).sp,
            lineHeight = (23f * fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerBottomSheet(
    title: String,
    options: List<AppLanguage>,
    selectedCode: String,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, options) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) options
        else options.filter {
            it.arabicName.lowercase().contains(q) ||
                it.englishName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "إغلاق"
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث عن لغة...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "بحث"
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.code }) { lang ->
                    val selected = lang.code == selectedCode
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onSelect(lang)
                                onDismiss()
                            }
                            .testTag("language_option_${lang.code}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = lang.arabicName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = lang.englishName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "مختارة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FreeBadgePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "مجاني تماماً • بدون اشتراك • ترجمة نصية بدون دبلجة",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
