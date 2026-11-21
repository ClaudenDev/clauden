package space.teleopen.claudencode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.claudencode.data.CodeSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF0B0908),
                        0.55f to Pal.Bg,
                        1f to Pal.BgDeep,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1C6B321A), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 1.08f),
                        radius = size.width * 0.95f,
                    ),
                )
            },
    ) {
        content()
    }
}

@Composable
fun HomeHeader(
    onNew: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaudeMark(size = 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Claude Code", color = Pal.White, style = type(21.sp, FontWeight.Bold, 1.2f))
            Text(
                "Agent mode · Android",
                color = Pal.Gray,
                style = type(12.5.sp, null, 1.3f),
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .clip(CircleShape)
                .termClickable(onNew)
                .padding(8.dp),
        ) {
            IconPlus(size = 23.dp)
        }
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .termClickable(onMenu)
                .padding(8.dp),
        ) {
            IconDots(size = 22.dp)
        }
    }
}

@Composable
fun SegmentedMode(
    mode: String,
    onMode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Pal.Panel)
            .border(1.dp, Pal.Border, RoundedCornerShape(26.dp))
            .padding(3.dp),
    ) {
        Segment(
            label = "Телефон",
            selected = mode == "phone",
            modifier = Modifier.weight(1f),
            onClick = { onMode("phone") },
        ) {
            IconPhone(
                size = 21.dp,
                tint = if (mode == "phone") Pal.Accent else Pal.Gray,
            )
        }
        Segment(
            label = "SSH",
            selected = mode == "ssh",
            modifier = Modifier.weight(1f),
            onClick = { onMode("ssh") },
        ) {
            IconMonitor(
                size = 22.dp,
                tint = if (mode == "ssh") Pal.Accent else Pal.Gray,
            )
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(Color(0xFF24110A), Color(0xFF180C07)))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .then(
                if (selected) Modifier.border(1.2.dp, Pal.Accent, shape) else Modifier,
            )
            .termClickable(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (selected) Pal.Accent else Pal.Gray,
            style = type(15.sp, FontWeight.Medium, 1.2f),
        )
    }
}

data class ProjectUi(
    val title: String,
    val label: String = "Текущий проект",
    val branch: String = "",
    val state: String = "Активен",
    val configured: Boolean = true,
)

@Composable
fun ProjectCard(
    project: ProjectUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(Pal.Panel)
            .border(1.dp, Color(0xFF5C2C1C), shape)
            .termClickable(onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconFolder(
                size = 23.dp,
                tint = if (project.configured) Pal.Gray else Pal.Dim,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.label,
                    color = Pal.Dim,
                    style = type(11.5.sp, null, 1.25f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.title,
                        color = if (project.configured) Pal.White else Pal.Gray,
                        style = type(16.5.sp, FontWeight.Bold, 1.25f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (project.branch.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF191817))
                                .border(1.dp, Pal.BorderSoft, RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBranch(size = 14.dp, tint = Pal.Dim)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                project.branch,
                                color = Pal.Gray,
                                style = type(11.sp, null, 1.2f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (project.configured) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Pal.Green),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        project.state,
                        color = Pal.Green,
                        style = type(11.5.sp, FontWeight.Medium, 1.2f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            IconChevron(size = 17.dp, tint = Pal.Dim)
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFFE0754D),
                        0.5f to Pal.Accent,
                        1f to Color(0xFF7E341D),
                    ),
                ),
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    subtitle: String,
    working: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF170C07), Color(0xFF0A0705)),
                ),
            )
            .border(1.dp, Color(0xFFB04C2D), shape)
            .termClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaudeMark(size = 44.dp, hole = Color(0xFF160C08))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.White, style = type(17.sp, FontWeight.Bold, 1.25f))
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Pal.Gray,
                style = type(12.sp, null, 1.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Waveform(working = working)
    }
}

@Composable
private fun Waveform(working: Boolean) {
    val transition = rememberInfiniteTransition("wave")
    val tops = listOf(7f, 10f, 4.5f, 9f, 6.5f)
    val bottoms = listOf(17f, 14f, 19.5f, 15f, 17.5f)
    Row(
        modifier = Modifier.height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until 5) {
            val pulse by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 120),
                ),
                label = "wave$index",
            )
            val goal = if (working) pulse else 0.45f
            val top = tops[index] + (bottoms[index] - tops[index]) * (1f - goal) * 0.34f
            val bottom = bottoms[index] - (bottoms[index] - tops[index]) * (1f - goal) * 0.34f
            Box(
                Modifier
                    .width(2.dp)
                    .height((bottom - top).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Pal.Accent),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconClock(size = 21.dp, tint = Pal.Gray)
        Spacer(Modifier.width(12.dp))
        Text(title, color = Pal.White, style = type(16.5.sp, FontWeight.Bold, 1.25f))
        Spacer(Modifier.weight(1f))
        if (onAll != null) {
            Row(
                Modifier.termClickable(onAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Все", color = Pal.Accent, style = type(14.sp, FontWeight.Medium, 1.2f))
                Spacer(Modifier.width(7.dp))
                IconArrowRight(size = 17.dp)
            }
        }
    }
}

@Composable
fun HomeSessionCard(
    session: CodeSession,
    modelLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Pal.Panel)
            .border(1.dp, Pal.BorderSoft, shape)
            .termClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SessionDot(session)
                Spacer(Modifier.width(12.dp))
                Text(
                    session.title.ifBlank { "Новая сессия" },
                    color = Pal.White,
                    style = type(15.sp, FontWeight.Bold, 1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                sessionMeta(session),
                color = Pal.Gray,
                style = type(12.sp, null, 1.25f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp),
            )
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionMetaCell(
                    text = sessionPath(session),
                    weight = 0.9f,
                ) { IconTerminal(size = 13.dp, tint = Pal.Dim) }
                MetaDivider()
                SessionMetaCell(
                    text = sessionBranch(session),
                    weight = 0.7f,
                ) { IconBranch(size = 12.dp, tint = Pal.Dim) }
                MetaDivider()
                SessionMetaCell(
                    text = modelLabel,
                    weight = 1.4f,
                ) { IconCube(size = 13.dp, tint = Pal.Dim) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(35.dp)
                .clip(CircleShape)
                .background(Pal.PanelHi)
                .border(1.dp, Pal.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconPlay(size = 14.dp, tint = Pal.White)
        }
    }
}

@Composable
private fun RowScope.SessionMetaCell(
    text: String,
    weight: Float,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier.weight(weight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            color = Pal.Gray,
            style = type(10.5.sp, null, 1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetaDivider() {
    Box(
        Modifier
            .padding(horizontal = 9.dp)
            .width(1.dp)
            .height(13.dp)
            .background(Pal.Border),
    )
}

@Composable
fun SessionDot(session: CodeSession) {
    val age = System.currentTimeMillis() - session.updatedAt
    val color = when {
        session.error.isNotBlank() -> Pal.Red
        session.working -> Pal.Blue
        session.updatedAt > 0 && age < 6 * 60 * 60 * 1000L -> Pal.Green
        else -> Color(0xFF6B6865)
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

fun sessionPath(session: CodeSession): String {
    val remote = session.remote?.root
    if (!remote.isNullOrBlank()) return remote
    val name = session.repoName.ifBlank { session.repo }
    return "~/" + name.ifBlank { "project" }
}

fun sessionBranch(session: CodeSession): String =
    session.branch.ifBlank { "main" }

fun sessionMeta(session: CodeSession): String {
    val ago = agoRu(session.updatedAt)
    val count = session.messageCount
    if (count <= 0) return ago
    val plural = pluralRu(count, "сообщение", "сообщения", "сообщений")
    return if (ago.isBlank()) "$count $plural" else "$ago · $count $plural"
}

@Composable
fun QuickChip(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    pill: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(
                if (pill) {
                    Modifier
                        .height(33.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Pal.Panel)
                        .border(1.dp, Pal.Border, RoundedCornerShape(17.dp))
                        .padding(horizontal = 6.dp)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                },
            )
            .termClickable(onClick),
        horizontalArrangement = if (pill) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(if (pill) 5.dp else 5.dp))
        Text(
            label,
            color = Color(0xFFBBB7B2),
            style = type(if (pill) 11.5.sp else 10.sp, null, 1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (pill) Modifier else Modifier.weight(1f),
        )
    }
}

@Composable
fun QuickChips(
    pill: Boolean,
    onHelp: () -> Unit,
    onProject: () -> Unit,
    onSessions: () -> Unit,
    onGithub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pill) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickChip("/help", { IconQuestion(size = 15.dp, tint = Pal.Gray) }, onHelp, true)
            QuickChip("/project", { IconFolder(size = 15.dp, tint = Pal.Gray) }, onProject, true)
            QuickChip("/sessions", { IconClock(size = 15.dp, tint = Pal.Gray) }, onSessions, true)
            QuickChip("/github", { IconGitHub(size = 14.dp, tint = Pal.Gray) }, onGithub, true)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickChip(
                "/help",
                { IconQuestion(size = 16.dp, tint = Pal.Gray) },
                onHelp,
                false,
                Modifier.weight(1f),
            )
            ChipRule()
            QuickChip(
                "/project",
                { IconFolder(size = 16.dp, tint = Pal.Gray) },
                onProject,
                false,
                Modifier.weight(1f),
            )
            ChipRule()
            QuickChip(
                "/sessions",
                { IconClock(size = 16.dp, tint = Pal.Gray) },
                onSessions,
                false,
                Modifier.weight(1.3f),
            )
            ChipRule()
            QuickChip(
                "/github",
                { IconGitHub(size = 15.dp, tint = Pal.Gray) },
                onGithub,
                false,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChipRule() {
    Box(
        Modifier
            .width(1.dp)
            .height(17.dp)
            .background(Pal.Border),
    )
}

@Composable
fun NoteBanner(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16100D))
            .border(1.dp, Color(0xFF4A2416), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Pal.Accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = Pal.PeriSoft,
            style = type(12.5.sp, null, 1.35f),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconClose(size = 15.dp, tint = Pal.Dim)
    }
}

fun agoRu(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return ""
    val delta = ((now - ts) / 1000).coerceAtLeast(0)
    return when {
        delta < 60 -> "только что"
        delta < 3600 -> "${delta / 60} мин назад"
        delta < 86_400 -> "${delta / 3600} ч назад"
        delta < 604_800 -> "${delta / 86_400} дн назад"
        else -> {
            val date = Date(ts)
            SimpleDateFormat("d MMM", Locale("ru")).format(date)
        }
    }
}

fun pluralRu(value: Int, one: String, few: String, many: String): String {
    val mod100 = value % 100
    val mod10 = value % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

fun timeLabel(ts: Long): String {
    if (ts <= 0) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}

fun shortModelLabel(key: String): String {
    if (key.isBlank()) return "Claude Sonnet 5.5"
    val raw = key.substringAfterLast('/')
    val cleaned = raw
        .replace("-thinking", "")
        .replace("-latest", "")
        .replace("-pre", "")
    val words = cleaned.split('-', '_').filter { it.isNotBlank() }
    if (words.isEmpty()) return raw
    val named = words.joinToString(" ") { word ->
        if (word.length <= 2 && word.toIntOrNull() != null) {
            word
        } else {
            word.replaceFirstChar { it.uppercase() }
        }
    }
    val branded = if (named.startsWith("Claude") || named.startsWith("Gpt") ||
        named.startsWith("Gemini")
    ) {
        named
    } else {
        "Claude $named"
    }

    return branded.replace(Regex("(\\d) (\\d)$"), "$1.$2")
}

@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .termClickable(onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFF0B0A0A))
                .border(
                    1.dp,
                    Pal.Border,
                    RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                )
                .padding(top = 10.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF34312F)),
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

enum class SheetIcon {
    New, Sessions, Project, Github, Help, Model, Effort, Diff, Files, Tasks,
    Usage, Rename, Stop, Leave, Logout, Star, Server, Terminal,
}

data class SheetAction(
    val title: String,
    val subtitle: String = "",
    val icon: SheetIcon = SheetIcon.Project,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun SheetIconView(icon: SheetIcon, tint: Color) {
    when (icon) {
        SheetIcon.New -> IconPlus(size = 18.dp, tint = tint)
        SheetIcon.Sessions -> IconClock(size = 18.dp, tint = tint)
        SheetIcon.Project -> IconFolder(size = 18.dp, tint = tint)
        SheetIcon.Github -> IconGitHub(size = 17.dp, tint = tint)
        SheetIcon.Help -> IconQuestion(size = 18.dp, tint = tint)
        SheetIcon.Model -> IconCube(size = 18.dp, tint = tint)
        SheetIcon.Effort -> IconWaveform(size = 18.dp, tint = tint)
        SheetIcon.Diff -> IconFile(size = 18.dp, tint = tint)
        SheetIcon.Files -> IconFolderOpen(size = 18.dp, tint = tint)
        SheetIcon.Tasks -> IconCheck(size = 18.dp, tint = tint)
        SheetIcon.Usage -> IconClock(size = 18.dp, tint = tint)
        SheetIcon.Rename -> IconTerminal(size = 18.dp, tint = tint)
        SheetIcon.Stop -> IconStop(size = 17.dp, tint = tint)
        SheetIcon.Leave -> IconChevron(size = 18.dp, tint = tint, left = true)
        SheetIcon.Logout -> IconClose(size = 16.dp, tint = tint)
        SheetIcon.Star -> IconStar(size = 18.dp, tint = tint, filled = true)
        SheetIcon.Server -> IconMonitor(size = 18.dp, tint = tint)
        SheetIcon.Terminal -> IconTerminal(size = 18.dp, tint = tint)
    }
}

@Composable
fun ActionSheet(
    title: String,
    subtitle: String,
    actions: List<SheetAction>,
    onClose: () -> Unit,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BottomSheet(onDismiss = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(title, color = Pal.White, style = type(19.sp, FontWeight.Bold, 1.2f))
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = Pal.Dim,
                    style = type(12.5.sp, null, 1.35f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (header != null) {
                Spacer(Modifier.height(14.dp))
                header()
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.BorderSoft, RoundedCornerShape(16.dp)),
            ) {
                actions.forEachIndexed { index, action ->
                    SheetActionRow(action)
                    if (index != actions.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp)
                                .height(1.dp)
                                .background(Pal.BorderSoft),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
                    .termClickable(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Закрыть",
                    color = Pal.Gray,
                    style = type(13.5.sp, FontWeight.Medium, 1.2f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SheetActionRow(action: SheetAction) {
    val tint = if (action.danger) Pal.Red else Pal.Gray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .termClickable(action.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (action.danger) Color(0xFF24100F) else Pal.PanelHi),
            contentAlignment = Alignment.Center,
        ) {
            SheetIconView(action.icon, tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                action.title,
                color = if (action.danger) Pal.Red else Pal.White,
                style = type(14.sp, FontWeight.Medium, 1.25f),
                maxLines = 1,
            )
            if (action.subtitle.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    action.subtitle,
                    color = Pal.Dim,
                    style = type(11.5.sp, null, 1.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!action.danger) {
            IconChevron(size = 16.dp, tint = Pal.Faint)
        }
    }
}

@Composable
fun SessionsSheet(
    sessions: List<CodeSession>,
    activeId: String?,
    modelLabel: (CodeSession) -> String,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheet(onDismiss = onClose) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сессии", color = Pal.White, style = type(19.sp, FontWeight.Bold, 1.2f))
                Spacer(Modifier.width(8.dp))
                Text(
                    sessions.size.toString(),
                    color = Pal.Dim,
                    style = type(13.sp, null, 1.2f),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.termClickable(onNew),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconPlus(size = 16.dp, tint = Pal.Accent)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Новая",
                        color = Pal.Accent,
                        style = type(13.sp, FontWeight.Medium, 1.2f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (sessions.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconChevron(size = 28.dp, tint = Pal.Faint, up = true)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Пока нет сессий",
                        color = Pal.Gray,
                        style = type(14.sp, null, 1.3f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Начните с сообщения на главном экране",
                        color = Pal.Dim,
                        style = type(11.5.sp, null, 1.3f),
                    )
                }
            } else {
                Column(
                    Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    sessions.forEach { entry ->
                        val active = entry.id == activeId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Pal.PanelHi else Color.Transparent)
                                .termClickable { onOpen(entry.id) }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SessionDot(entry)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title.ifBlank { "Новая сессия" },
                                    color = if (active) Pal.White else Pal.PeriSoft,
                                    style = type(13.5.sp, FontWeight.Medium, 1.25f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${agoRu(entry.updatedAt)} · ${modelLabel(entry)}",
                                    color = Pal.Dim,
                                    style = type(11.sp, null, 1.25f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconChevron(size = 16.dp, tint = Pal.Faint)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun TypingDots(
    tint: Color = Pal.Accent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until 3) {
            val transition = rememberInfiniteTransition("dot$index")
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "dotAlpha$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
fun SessionTopBar(
    title: String,
    subtitle: String,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .width(82.dp)
                    .termClickable(onBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChevron(size = 17.dp, tint = Pal.Gray, left = true)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Назад",
                    color = Pal.PeriSoft,
                    style = type(14.sp, FontWeight.Medium, 1.2f),
                    maxLines = 1,
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Pal.Green),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        color = Pal.White,
                        style = type(15.sp, FontWeight.Bold, 1.2f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    color = Pal.Dim,
                    style = type(11.sp, null, 1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.width(82.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .termClickable(onFavorite)
                        .padding(6.dp),
                ) {
                    IconStar(
                        size = 22.dp,
                        tint = if (favorite) Pal.Accent else Pal.Gray,
                        filled = favorite,
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .termClickable(onMenu)
                        .padding(6.dp),
                ) {
                    IconDots(size = 21.dp)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Pal.BorderSoft),
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Pal.Accent else Pal.PanelHi)
            .termClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color(0xFF1A0B06) else Pal.Dim,
            style = type(14.5.sp, FontWeight.Bold, 1.2f),
        )
    }
}

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Panel)
            .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
            .termClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Pal.PeriSoft,
            style = type(13.5.sp, FontWeight.Medium, 1.2f),
        )
    }
}

fun Modifier.cardSurface(
    radius: Dp = 14.dp,
    color: Color = Pal.Panel,
    borderColor: Color = Pal.BorderSoft,
): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(color)
    .border(1.dp, borderColor, RoundedCornerShape(radius))

@Composable
fun ProjectInfoSheet(
    session: CodeSession,
    modelName: String,
    onFiles: () -> Unit,
    onDiff: () -> Unit,
    onTodos: () -> Unit,
    onModel: () -> Unit,
    onTerminal: (() -> Unit)?,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    BottomSheet(onDismiss = onClose) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Проект", color = Pal.White, style = type(19.sp, FontWeight.Bold, 1.2f))
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.BorderSoft, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pal.PanelHi),
                    contentAlignment = Alignment.Center,
                ) {
                    IconFolder(size = 21.dp, tint = Pal.Accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.repoName.ifBlank {
                            session.remote?.label ?: session.repo.ifBlank { "Проект" }
                        },
                        color = Pal.White,
                        style = type(15.sp, FontWeight.Bold, 1.25f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${sessionBranch(session)} · ${sessionPath(session)}",
                        color = Pal.Dim,
                        style = type(11.sp, null, 1.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SessionDot(session)
            }
            Spacer(Modifier.height(12.dp))
            ProjectInfoRow("Модель", modelName)
            ProjectInfoRow(
                "Источник",
                if (session.source == "ssh") {
                    "SSH · ${session.remote?.target.orEmpty()}"
                } else {
                    "Git · ${session.repo.ifBlank { "project" }}"
                },
            )
            ProjectInfoRow(
                "Статус",
                when (session.status) {
                    "working" -> "Работает"
                    "stopped", "interrupted" -> "Остановлена"
                    else -> "Готова"
                },
            )
            ProjectInfoRow("Сообщений", session.messageCount.toString())
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.BorderSoft, RoundedCornerShape(16.dp)),
            ) {
                val actions = buildList {
                    add(SheetAction("Файлы", "Рабочая папка", SheetIcon.Files, onClick = onFiles))
                    add(SheetAction("Изменения", "Git diff", SheetIcon.Diff, onClick = onDiff))
                    add(SheetAction("Задачи", "Список агента", SheetIcon.Tasks, onClick = onTodos))
                    add(SheetAction("Модель", modelName, SheetIcon.Model, onClick = onModel))
                    if (onTerminal != null) {
                        add(SheetAction("Терминал", "SSH-сессия", SheetIcon.Terminal, onClick = onTerminal))
                    }
                    add(SheetAction("Новая сессия", "На этом проекте", SheetIcon.New, onClick = onNew))
                }
                actions.forEachIndexed { index, action ->
                    SheetActionRow(action)
                    if (index != actions.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp)
                                .height(1.dp)
                                .background(Pal.BorderSoft),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
                    .termClickable(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("Закрыть", color = Pal.Gray, style = type(13.5.sp, FontWeight.Medium, 1.2f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProjectInfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pal.Dim, style = type(11.5.sp, null, 1.3f))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = Pal.PeriSoft,
            style = type(11.5.sp, null, 1.3f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
