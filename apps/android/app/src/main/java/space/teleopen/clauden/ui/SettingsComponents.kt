package space.teleopen.clauden.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.ButtonStyle
import space.teleopen.clauden.data.MotionStyle
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeIconMode
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.data.ToggleStyle
import space.teleopen.clauden.data.UiStyle

@Composable
fun Modifier.blockClicks(): Modifier = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() },
) { }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalClaudenColors.current
    if (LocalUiStyle.current == UiStyle.Material3) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                when {
                    onBack != null -> ThemeIconButton(
                        id = "header.back",
                        icon = ClaudenIcons.arrowBack,
                        contentDescription = "Назад",
                        onClick = onBack,
                        size = 44.dp,
                        iconSize = 24.dp,
                    )

                    onMenu != null -> ThemeIconButton(
                        id = "header.menu",
                        icon = ClaudenIcons.menu,
                        contentDescription = "Меню",
                        onClick = onMenu,
                        size = 44.dp,
                        iconSize = 24.dp,
                    )
                }
            },
            actions = { trailing() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = headerBackground(colors),
                titleContentColor = colors.textPrimary,
                navigationIconContentColor = colors.textPrimary,
                actionIconContentColor = colors.textPrimary,
            ),
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(88.dp),
    ) {
        when {
            onBack != null -> IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = ClaudenIcons.arrowBack,
                    contentDescription = "Назад",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            onMenu != null -> IconButton(
                onClick = onMenu,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = ClaudenIcons.menu,
                    contentDescription = "Меню",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing()
        }
    }
}

@Composable
fun SettingsPage(content: @Composable () -> Unit) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 1.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val glass = LocalThemeStyle.current == ThemeStyle.Glass
    val shape = LocalClaudenShapes.current.card
    Surface(
        color = if (glass) Color.Transparent else colors.bg300,
        shape = shape,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(shape, colors.bg300.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = if (glass) {
                Modifier.background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.15f),
                        0.5f to Color.White.copy(alpha = 0.05f),
                        1f to Color.White.copy(alpha = 0.10f),
                    ),
                )
            } else {
                Modifier
            },
        ) { content() }
    }
}

@Composable
fun SettingsDivider() {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (material3) 1.dp else 2.dp)
            .background(if (material3) colors.border else colors.bg),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val themeId = "row:" + title.trim().lowercase()
    val style = LocalCustomTheme.current.elements[themeId]
    val iconPath = LocalCustomTheme.current.icons[themeId]
    val selecting = LocalThemeEditState.current.selectionMode
    val customContent = style?.content?.let(::Color)
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    val inlineIcon = !fullImage && iconPath != null
    val rowClick: (() -> Unit)? = if (selecting) {
        { ThemeController.selectElement(themeId, title) }
    } else {
        onClick
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (rowClick != null) Modifier.clickable(onClick = rowClick) else Modifier,
        )

    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = Color.Transparent,
        shape = RectangleShape,
        modifier = rowModifier,
    ) {
        if (material3) {

            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = customContent ?: colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = if (subtitle != null) {
                    {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = (customContent ?: colors.textSecondary).copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    null
                },
                leadingContent = if (icon != null || inlineIcon) {
                    {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.control),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                inlineIcon -> ThemeAssetImage(
                                    path = iconPath.orEmpty(),
                                    modifier = Modifier.size(26.dp),
                                    contentDescription = null,
                                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                                        ContentScale.Crop
                                    } else {
                                        ContentScale.Fit
                                    },
                                )

                                icon != null -> Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = customContent ?: colors.textPrimary,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                trailingContent = { trailing() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val height = if (subtitle == null) 58.dp else 66.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inlineIcon) {
                    ThemeAssetImage(
                        path = iconPath.orEmpty(),
                        modifier = Modifier.size(24.dp),
                        contentDescription = null,
                        contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = customContent ?: colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                        ),
                        color = customContent ?: colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                            color = (customContent ?: colors.textTertiary).copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

@Composable
fun SettingsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalClaudenColors.current
    if (LocalUiStyle.current == UiStyle.Material3) {

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onClay,
                checkedTrackColor = colors.clay,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.switchOff,
                uncheckedBorderColor = colors.borderStrong,
                disabledCheckedThumbColor = colors.textTertiary,
                disabledCheckedTrackColor = colors.switchOff,
                disabledUncheckedThumbColor = colors.textTertiary.copy(alpha = 0.6f),
                disabledUncheckedTrackColor = colors.switchOff.copy(alpha = 0.6f),
                disabledUncheckedBorderColor = colors.border,
            ),
        )
        return
    }
    val style = LocalToggleStyle.current
    val motion = LocalMotionStyle.current
    val duration = when (motion) {
        MotionStyle.Full -> 220
        MotionStyle.Soft -> 160
        MotionStyle.Off -> 1
    }
    val width = when (style) {
        ToggleStyle.Ios -> 50.dp
        ToggleStyle.Compact -> 38.dp
        ToggleStyle.Classic -> 44.dp
    }
    val height = when (style) {
        ToggleStyle.Ios -> 30.dp
        ToggleStyle.Compact -> 22.dp
        ToggleStyle.Classic -> 25.dp
    }
    val thumb = height - 4.dp
    val travel = width - thumb - 4.dp
    val track by animateColorAsState(
        targetValue = when {
            !enabled && checked -> colors.blue.copy(alpha = 0.42f)
            checked -> colors.blue
            else -> colors.switchOff
        },
        animationSpec = tween(durationMillis = duration),
        label = "track",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(durationMillis = duration),
        label = "thumb",
    )
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(CircleShape)
            .background(track)
            .then(
                if (style == ToggleStyle.Ios) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (checked) Color.Transparent else colors.borderStrong,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .padding(start = offset)
                .size(thumb)
                .clip(CircleShape)
                .background(if (enabled || !checked) Color.White else Color(0xFF9E9E9C)),
        )
    }
}

@Composable
fun WhitePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val themeId = "button:" + text.trim().lowercase()
    val style = LocalCustomTheme.current.elements[themeId]
    val selecting = LocalThemeEditState.current.selectionMode
    val action = if (selecting) {
        { ThemeController.selectElement(themeId, text) }
    } else {
        onClick
    }
    val customBg = style?.background?.let(::Color)
    val customContent = style?.content?.let(::Color)
    val iconPath = LocalCustomTheme.current.icons[themeId]
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    if (material3) {

        if (fullImage) {
            Box(
                modifier = modifier
                    .height(40.dp)
                    .clip(CircleShape),
            ) {
                ThemeAssetImage(
                    path = iconPath.orEmpty(),
                    modifier = Modifier.matchParentSize(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                if (customBg != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(customBg.copy(alpha = 0.22f)),
                    )
                }
                Button(
                    onClick = action,
                    modifier = Modifier.height(40.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = customContent ?: colors.onClay,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(text = text, style = MaterialTheme.typography.labelLarge)
                }
            }
            return
        }
        Button(
            onClick = action,
            modifier = modifier.height(40.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = customBg ?: colors.clay,
                contentColor = customContent ?: colors.onClay,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (!fullImage && iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        return
    }
    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = customBg ?: colors.textPrimary,
        shape = LocalClaudenShapes.current.button,
        modifier = modifier
            .height(height)
            .clickable(onClick = action),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!fullImage && iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = customContent ?: colors.bg,
            )
        }
    }
}

@Composable
fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val themeId = "button:" + text.trim().lowercase()
    val style = LocalCustomTheme.current.elements[themeId]
    val selecting = LocalThemeEditState.current.selectionMode
    val action = if (selecting) {
        { ThemeController.selectElement(themeId, text) }
    } else {
        onClick
    }
    val customBg = style?.background?.let(::Color)
    val customContent = style?.content?.let(::Color)
    val iconPath = LocalCustomTheme.current.icons[themeId]
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    if (LocalThemeStyle.current == ThemeStyle.Glass &&
        customBg == null && customContent == null && iconPath == null
    ) {
        LiquidGlassButton(
            onClick = action,
            enabled = enabled || selecting,
            shape = if (LocalButtonStyle.current == ButtonStyle.Square) {
                RoundedCornerShape(8.dp)
            } else {
                CircleShape
            },
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
        }
        return
    }
    if (material3) {
        if (fullImage) {
            ThemeElementFrame(
                style = style,
                iconPath = iconPath,
                fallback = customBg ?: colors.clay,
                shape = CircleShape,
                modifier = modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Button(
                    onClick = action,
                    enabled = enabled || selecting,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = customContent ?: colors.onClay,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = colors.buttonDisabledText,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(text = text, style = MaterialTheme.typography.labelLarge)
                }
            }
            return
        }
        Button(
            onClick = action,
            enabled = enabled || selecting,
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = customBg ?: colors.clay,
                contentColor = customContent ?: colors.onClay,
                disabledContainerColor = colors.buttonDisabled,
                disabledContentColor = colors.buttonDisabledText,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = when {
            !enabled && !selecting -> colors.buttonDisabled
            else -> customBg ?: colors.textPrimary
        },
        shape = LocalClaudenShapes.current.button,
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clickable(enabled = enabled || selecting, onClick = action),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!fullImage && iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = when {
                    !enabled && !selecting -> colors.buttonDisabledText
                    else -> customContent ?: colors.bg
                },
            )
        }
    }
}

@Composable
fun PlanBadge(text: String) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Surface(
        color = if (material3) colors.control else colors.textPrimary,
        contentColor = if (material3) colors.textPrimary else colors.bg,
        shape = CircleShape,
        modifier = Modifier.height(25.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Text(
        text = text,
        style = if (material3) {
            MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        } else {
            MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
        },
        color = if (material3) colors.clay else colors.textTertiary,
        modifier = Modifier.padding(start = 11.dp, top = 10.dp, bottom = 0.dp),
    )
}

@Composable
fun UpgradeCard(
    onUpgrade: () -> Unit,
    titleSize: Int = 17,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = LocalClaudenShapes.current.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Хотите больше возможностей Clauden?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = titleSize.sp,
                    lineHeight = (titleSize + 6).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Перейдите на новый план, чтобы получить более высокие лимиты " +
                    "использования и больше возможностей.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(22.dp))
            WhitePillButton(text = "Перейти на новый план", onClick = onUpgrade)
        }
    }
}

@Composable
fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    height: Dp = 57.dp,
    singleLine: Boolean = true,
    transformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textSize: Int = 18,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    if (material3) {

        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = textSize.sp,
                lineHeight = (textSize + 6).sp,
                color = colors.textPrimary,
            ),
            visualTransformation = transformation,
            keyboardOptions = keyboardOptions,
            placeholder = if (placeholder.isNotEmpty()) {
                {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = textSize.sp),
                        color = colors.textTertiary,
                    )
                }
            } else {
                null
            },
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.field,
                unfocusedContainerColor = colors.field,
                disabledContainerColor = colors.field,
                focusedIndicatorColor = colors.clay,
                unfocusedIndicatorColor = colors.borderStrong,
                disabledIndicatorColor = colors.border,
                cursorColor = colors.clay,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = height),
        )
        return
    }
    Surface(
        color = colors.bg300,
        shape = LocalClaudenShapes.current.field,
        border = BorderStroke(1.dp, colors.fieldBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = if (height > 80.dp) Alignment.TopStart else Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = textSize.sp,
                        lineHeight = (textSize + 6).sp,
                    ),
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = textSize.sp,
                    lineHeight = (textSize + 6).sp,
                    color = colors.textPrimary,
                ),
                cursorBrush = SolidColor(colors.clay),
                visualTransformation = transformation,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
