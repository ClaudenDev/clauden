package space.teleopen.clauden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.ChatStep

private fun stepTitle(step: ChatStep): String =
    step.title.ifBlank { step.name.replace('_', ' ') }.ifBlank { "Шаг" }

@Composable
fun ClaudeStepsLink(
    steps: List<ChatStep>,
    running: Boolean,
    onOpen: () -> Unit,
) {
    if (steps.isEmpty()) return
    val colors = LocalClaudenColors.current
    val custom = LocalCustomTheme.current
    val current = steps.lastOrNull { it.status == "running" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 5.dp),
    ) {
        if (running && current != null) {
            if (custom.enabled) {
                ThemeGenerationIndicator(
                    animation = custom.textAnimation,
                    color = colors.clay,
                    customPath = custom.stepsLoadingPath.ifBlank { custom.messageLoadingPath },
                )
            } else {
                Icon(
                    imageVector = ClaudenIcons.clock,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = current?.let { stepTitle(it) + "…" } ?: "Работаю…",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                text = "Шагов: ${steps.size}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = ClaudenIcons.chevronRight,
            contentDescription = "Открыть шаги",
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeStepsSheet(
    steps: List<ChatStep>,
    onOpenStep: (ChatStep) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bubble,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = colors.textSecondary,
                    )
                }
                Text(
                    text = "Сводка",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.height(10.dp))
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStep(step) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.control),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = stepIcon(step),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stepTitle(step),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
                        ),
                        color = if (step.status == "failed") colors.dangerSoft else colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = ClaudenIcons.chevronRight,
                        contentDescription = "Открыть шаг",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun stepIcon(step: ChatStep) = when (step.name.lowercase()) {
    "view", "read" -> ClaudenIcons.doc
    "bash", "shell", "repl" -> ClaudenIcons.terminal
    "web_search_v0", "web_search", "websearch", "webfetch" -> ClaudenIcons.globe
    "glob", "grep" -> ClaudenIcons.doc
    else -> ClaudenIcons.spark
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeStepSheet(
    step: ChatStep,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bubble,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = stepTitle(step),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                ),
                color = colors.textPrimary,
                modifier = Modifier.padding(end = 40.dp),
            )
            Spacer(Modifier.height(16.dp))
            StepSection(
                label = "Запрос",
                value = step.detail.ifBlank { "Запрос не сохранился" },
            )
            Spacer(Modifier.height(16.dp))
            StepSection(
                label = "Ответ",
                value = step.result.ifBlank {
                    if (step.status == "running") "Инструмент ещё выполняется"
                    else "Ответ не сохранился"
                },
            )
        }
    }
}

@Composable
private fun StepSection(label: String, value: String) {
    val colors = LocalClaudenColors.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = colors.field,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = value,
                style = ClaudenType.mono.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textPrimary,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
