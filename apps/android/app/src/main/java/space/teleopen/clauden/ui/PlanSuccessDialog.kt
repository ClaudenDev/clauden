package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.teleopen.clauden.data.BillingPlan

@Composable
fun PlanSuccessDialog(
    planId: String,
    plans: List<BillingPlan>,
    onDismiss: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val haptic = LocalHapticFeedback.current
    val plan = plans.firstOrNull { it.id.equals(planId, ignoreCase = true) }
    val label = plan?.label?.ifBlank { planId } ?: planId
    val features = plan?.features
        ?.filterNot { it.startsWith("Всё из", ignoreCase = true) }
        ?.take(4)
        ?.ifEmpty { null }
        ?: defaultFeatures(planId)

    var shown by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "plan-card-scale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(240),
        label = "plan-card-alpha",
    )
    val pulse by rememberInfiniteTransition(label = "plan-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.13f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plan-pulse-ring",
    )

    LaunchedEffect(Unit) {
        shown = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = 0.86f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.clay.copy(alpha = 0.28f),
                                colors.clay.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )

            Surface(
                color = colors.popupBg,
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, colors.clay.copy(alpha = 0.38f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .scale(cardScale)
                    .alpha(cardAlpha),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(124.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dots = listOf(
                                0.16f to 0.24f,
                                0.84f to 0.18f,
                                0.9f to 0.7f,
                                0.12f to 0.78f,
                            )
                            dots.forEachIndexed { index, (x, y) ->
                                drawCircle(
                                    color = colors.clay.copy(alpha = if (index % 2 == 0) 0.55f else 0.3f),
                                    radius = if (index % 2 == 0) 3.4.dp.toPx() else 2.4.dp.toPx(),
                                    center = Offset(size.width * x, size.height * y),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .background(colors.clay.copy(alpha = 0.12f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            colors.clay,
                                            colors.clay.copy(alpha = 0.72f),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.check,
                                contentDescription = null,
                                tint = colors.bg,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "ТАРИФ АКТИВИРОВАН",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                        ),
                        color = colors.clay,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Спасибо за оформление!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = LocalClaudenFonts.current.serif,
                            fontSize = 27.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Тариф $label теперь ваш",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        ),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (plan != null && plan.price > 0) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "$${plan.price} в месяц",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textTertiary,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Вам теперь доступны:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 9.dp),
                    )
                    features.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.check,
                                contentDescription = null,
                                tint = colors.clay,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = colors.textSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = colors.textPrimary,
                        contentColor = colors.bg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.textPrimary)
                            .clickableNoIndication(onDismiss),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Отлично",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Посмотреть лимиты и расход",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.clay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableNoIndication(onOpenUsage)
                            .padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun defaultFeatures(planId: String): List<String> {
    val id = planId.lowercase()
    return when {
        id.contains("ultra") -> listOf(
            "Максимальные лимиты и 5-часовые окна",
            "Приоритетный доступ к моделям",
            "Ранний доступ к новым возможностям",
            "Выделенная поддержка",
        )

        id.contains("max") -> listOf(
            "В 5 раз больше лимитов Pro",
            "Приоритетный доступ в часы пик",
            "Ранний доступ к новым возможностям",
        )

        else -> listOf(
            "Более высокие лимиты",
            "Проекты с инструкциями и файлами",
            "Память, задачи и навыки",
            "Приоритетная поддержка",
        )
    }
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        ),
    )
