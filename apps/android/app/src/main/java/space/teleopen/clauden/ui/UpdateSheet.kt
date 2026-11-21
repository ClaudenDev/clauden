package space.teleopen.clauden.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.UpdateState

@Composable
fun UpdateSheet(
    update: UpdateState,
    installedVersion: String,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = update.latest
    val shown = update.visible && release != null
    val colors = LocalClaudenColors.current

    BackHandler(enabled = shown, onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(220)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(160)),
            ) {
                if (release == null) return@AnimatedVisibility
                Surface(
                    color = colors.popupBg,
                    contentColor = colors.textPrimary,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.56f)

                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(colors.borderStrong.copy(alpha = 0.55f)),
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 10.dp, top = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.control),
                                contentAlignment = Alignment.Center,
                            ) {
                                ClaudenLogo(
                                    modifier = Modifier.size(32.dp),
                                    tint = colors.clay,
                                    variant = ClaudenLogoVariant.Jelly,
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ДОСТУПНО ОБНОВЛЕНИЕ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.1.sp,
                                    ),
                                    color = colors.clay,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Clauden ${release.versionName}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = LocalClaudenFonts.current.serif,
                                        fontSize = 24.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Сейчас установлена $installedVersion",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        HorizontalDivider(
                            color = colors.border,
                            modifier = Modifier.padding(top = 16.dp),
                        )

                        Text(
                            text = "Что нового",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                            ),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp),
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 22.dp,
                                end = 22.dp,
                                top = 2.dp,
                                bottom = 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (release.notes.isEmpty()) {
                                item {
                                    NoteRow("Улучшения стабильности и мелкие исправления")
                                }
                            } else {
                                items(release.notes) { note ->
                                    NoteRow(note)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        ) {
                            if (update.downloading) {
                                DownloadProgress(
                                    progress = update.progress,
                                    sizeBytes = release.sizeBytes,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            } else {
                                Text(
                                    text = buildString {
                                        append(formatSize(release.sizeBytes))
                                        append(" · Android")
                                        if (update.downloaded) append(" · загружено")
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = colors.textTertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }

                            if (update.error != null) {
                                Text(
                                    text = update.error,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    color = colors.danger,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }

                            UpdateButton(
                                text = when {
                                    update.downloading -> "Загружаем ${update.progress}%"
                                    update.downloaded -> "Установить"
                                    else -> "Обновить"
                                },
                                enabled = !update.downloading,
                                onClick = onStart,
                            )
                            Text(
                                text = "Позже",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .height(38.dp)
                                    .clickable(onClick = onDismiss)
                                    .padding(top = 9.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(text: String) {
    val colors = LocalClaudenColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.clay),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DownloadProgress(progress: Int, sizeBytes: Long, modifier: Modifier = Modifier) {
    val colors = LocalClaudenColors.current
    val fraction = animateFloatAsState(
        targetValue = (progress.coerceIn(0, 100) / 100f),
        animationSpec = tween(220),
        label = "update-progress",
    ).value
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.control),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.clay),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Скачиваем ${progress}% · ${formatSize(sizeBytes)}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun UpdateButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    val background = if (enabled) colors.textPrimary else colors.buttonDisabled
    val content = if (enabled) colors.bg else colors.buttonDisabledText
    Surface(
        color = background,
        contentColor = content,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 КБ"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.1) String.format(java.util.Locale.US, "%.1f МБ", mb) else "${bytes / 1024} КБ"
}
