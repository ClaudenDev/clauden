package space.teleopen.claudencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.claudencode.data.CodeDiff
import space.teleopen.claudencode.data.CodeTodo

data class SelectOption(
    val title: String,
    val subtitle: String = "",
    val onClick: () -> Unit,
)

@Composable
fun Scrim(
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xC8000000)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .termClickable { onDismiss?.invoke() },
        )
        Box(Modifier.align(Alignment.Center), content = content)
    }
}

@Composable
private fun Modifier.selectBox(maxWidth: Dp = 430.dp): Modifier = this
    .padding(horizontal = 16.dp)
    .widthIn(max = maxWidth)
    .clip(RoundedCornerShape(20.dp))
    .background(Pal.Panel)
    .border(1.dp, Pal.Border, RoundedCornerShape(20.dp))

@Composable
fun SelectOverlay(
    header: String,
    options: List<SelectOption>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Int = -1,
) {
    Scrim(modifier = modifier, onDismiss = onCancel) {
        Column(
            modifier = Modifier
                .selectBox()
                .heightIn(max = 480.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    header,
                    color = Pal.White,
                    style = type(16.sp, FontWeight.Bold, 1.25f),
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                )
                options.forEachIndexed { index, option ->
                    val isSel = index == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSel) Pal.PanelHi else Color.Transparent)
                            .termClickable(option.onClick)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSel) {
                            Text(
                                "❯",
                                color = Pal.Accent,
                                style = mono(15.sp, FontWeight.Bold),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            "${minOf(index + 1, 9)}. ",
                            color = Pal.Dim,
                            style = mono(13.sp, FontWeight.Bold),
                        )
                        Text(
                            option.title,
                            color = Pal.White,
                            style = mono(13.5.sp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.subtitle.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                option.subtitle,
                                color = Pal.Dim,
                                style = mono(13.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Pal.Border)
            Text(
                "Закрыть",
                color = Pal.Accent,
                style = type(12.sp, FontWeight.Medium, 1.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .termClickable(onCancel)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun HelpOverlay(
    commands: List<Pair<String, String>>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .selectBox()
                .heightIn(max = 520.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "  Команды",
                    color = Pal.White,
                    style = mono(14.5.sp, FontWeight.Bold),
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                commands.forEach { (name, description) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            name,
                            color = Pal.Peri,
                            style = mono(15.sp),
                            modifier = Modifier
                                .width(120.dp),
                            maxLines = 2,
                        )
                        Text(
                            description,
                            color = Pal.Gray,
                            style = mono(14.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(color = Pal.Border)
            Text(
                "Закрыть",
                color = Pal.Accent,
                style = type(12.sp, FontWeight.Medium, 1.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .termClickable(onClose)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun DiffOverlay(
    diff: CodeDiff,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screen = LocalConfiguration.current.screenHeightDp.dp
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .heightIn(max = screen * 0.84f)
                .clip(RoundedCornerShape(20.dp))
                .background(Pal.Panel)
                .border(1.dp, Pal.Border, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Changes",
                    color = Pal.White,
                    style = mono(15.sp, FontWeight.Bold),
                )
                Spacer(Modifier.width(10.dp))
                val adds = diff.files.sumOf { it.additions }
                val dels = diff.files.sumOf { it.deletions }
                if (adds > 0) {
                    Text("+$adds", color = Pal.Green, style = mono(14.sp, FontWeight.Bold))
                    Spacer(Modifier.width(8.dp))
                }
                if (dels > 0) {
                    Text("−$dels", color = Pal.Red, style = mono(14.sp, FontWeight.Bold))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "close",
                    color = Pal.Gray,
                    style = mono(14.sp),
                    modifier = Modifier.termClickable(onClose),
                )
            }
            HorizontalDivider(color = Pal.Border)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                if (diff.empty) {
                    Text(
                        "  No changes yet",
                        color = Pal.Dim,
                        style = mono(15.sp),
                        modifier = Modifier.padding(12.dp),
                    )
                }
                val body = diff.text.ifBlank {
                    diff.files.joinToString("\n") { file ->
                        "--- ${file.path} (+${file.additions} −${file.deletions})\n${file.diff}"
                    }
                }
                body.lines().take(4000).forEach { row ->
                    val (bg, fg) = when {
                        row.startsWith("+++") || row.startsWith("---") ->
                            null to Pal.Dim
                        row.startsWith("@@") -> null to Pal.Cyan
                        row.startsWith("+") -> Color(0x14104624) to Pal.Green
                        row.startsWith("-") -> Color(0x144A1C1C) to Pal.Red
                        else -> null to Pal.Gray
                    }
                    Text(
                        row,
                        style = mono(13.sp),
                        color = fg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg ?: Color.Transparent),
                        maxLines = 6,
                    )
                }
            }
        }
    }
}

@Composable
fun GithubTokenSheet(
    busy: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var token by remember { mutableStateOf("") }
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            Modifier
                .selectBox()
                .padding(vertical = 12.dp),
        ) {
            Text(
                "Connect GitHub",
                color = Pal.White,
                style = mono(14.sp, FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Paste a personal access token (repo scope).",
                color = Pal.Gray,
                style = mono(11.sp),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("❯", color = Pal.Accent, style = mono(13.sp, FontWeight.Bold))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    textStyle = mono(12.5.sp).copy(color = Pal.White),
                    cursorBrush = SolidColor(Pal.Accent),
                    decorationBox = { inner ->
                        Box {
                            if (token.isEmpty()) {
                                Text("ghp_… / github_pat_…", color = Pal.Faint, style = mono(12.sp))
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            error?.let {
                Text(
                    "✕ $it",
                    color = Pal.Red,
                    style = mono(11.sp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (busy) "Connecting…" else "Connect",
                    color = if (busy) Pal.Dim else Pal.White,
                    style = mono(13.sp, FontWeight.Bold),
                    modifier = Modifier
                        .border(1.dp, if (busy) Pal.Border else Pal.Accent)
                        .background(Pal.PanelHi)
                        .termClickable { if (!busy && token.isNotBlank()) onConnect(token.trim()) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Закрыть",
                    color = Pal.Dim,
                    style = mono(11.sp),
                    modifier = Modifier.termClickable(onClose),
                )
            }
        }
    }
}

@Composable
fun FileOverlay(
    path: String,
    content: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screen = LocalConfiguration.current.screenHeightDp.dp
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .heightIn(max = screen * 0.86f)
                .clip(RoundedCornerShape(20.dp))
                .background(Pal.Panel)
                .border(1.dp, Pal.Border, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    path,
                    color = Pal.Peri,
                    style = mono(14.sp),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "close",
                    color = Pal.Gray,
                    style = mono(14.sp),
                    modifier = Modifier.termClickable(onClose),
                )
            }
            HorizontalDivider(color = Pal.Border)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                if (content.isBlank()) {
                    Text(
                        "  (empty or binary)",
                        color = Pal.Dim,
                        style = mono(14.sp),
                        modifier = Modifier.padding(10.dp),
                    )
                }
                content.lines().take(3000).forEach { row ->
                    Text(
                        row,
                        style = mono(13.sp),
                        color = Pal.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TodosPanel(
    todos: List<CodeTodo>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .selectBox()
                .heightIn(max = 460.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                val done = todos.count { it.status == "completed" }
                Text(
                    "  Todolist   $done/${todos.size} completed",
                    color = Pal.White,
                    style = mono(15.sp, FontWeight.Bold),
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
                todos.forEach { todo ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val mark = when (todo.status) {
                            "completed" -> "✓"
                            "in_progress" -> "❯"
                            "cancelled" -> "×"
                            else -> " "
                        }
                        Text(
                            mark,
                            color = when (todo.status) {
                                "completed" -> Pal.Green
                                "in_progress" -> Pal.Amber
                                "cancelled" -> Pal.Faint
                                else -> Pal.Faint
                            },
                            style = mono(15.sp, FontWeight.Bold),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            todo.content,
                            color = when (todo.status) {
                                "completed" -> Pal.Dim
                                "in_progress" -> Pal.White
                                else -> Pal.Gray
                            },
                            style = mono(14.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(color = Pal.Border)
            Text(
                "Закрыть",
                color = Pal.Accent,
                style = type(12.sp, FontWeight.Medium, 1.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .termClickable(onClose)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun FilesOverlay(
    listing: space.teleopen.claudencode.data.CodeFilesListing,
    onTapFile: (path: String) -> Unit,
    onTapDir: (path: String) -> Unit,
    onParent: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .selectBox()
                .heightIn(max = 520.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "  ${listing.path}",
                    color = Pal.Peri,
                    style = mono(14.sp),
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
                onParent?.let { parent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .termClickable(parent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("↰ ..", color = Pal.Gray, style = mono(15.sp))
                    }
                }
                listing.entries.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .termClickable {
                                if (entry.type == "dir") {
                                    onTapDir(entry.path)
                                } else {
                                    onTapFile(entry.path)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (entry.type == "dir") "▸" else " ",
                            color = Pal.Dim,
                            style = mono(15.sp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.name,
                            color = if (entry.type == "dir") Pal.White else Pal.PeriSoft,
                            style = mono(15.sp),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.type == "file") {
                            Text(
                                if (entry.size > 1024) "${entry.size / 1024}k" else "${entry.size}",
                                color = Pal.Faint,
                                style = mono(13.sp),
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(color = Pal.Border)
            Text(
                "Закрыть",
                color = Pal.Accent,
                style = type(12.sp, FontWeight.Medium, 1.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .termClickable(onClose)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun ShortcutsOverlay(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        "esc" to "interrupt the agent / close dialogs",
        "/ + tab" to "open the commands menu",
        "tap tool line" to "expand output (like ctrl+o)",
        "tap prompt ❯" to "focus the input",
        "/resume" to "pick a past session",
        "/model · /effort" to "switch model or effort plan",
        "/diff · /commit · /push" to "git roundtrip",
        "/compact" to "compress long context",
        "/clear" to "leave this session fresh",
        "/help" to "list every command",
        "Enter" to "send the prompt · run queued message",
    )
    Scrim(modifier = modifier, onDismiss = onClose) {
        Column(
            modifier = Modifier
                .selectBox()
                .heightIn(max = 500.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "  Shortcuts",
                    color = Pal.White,
                    style = mono(14.5.sp, FontWeight.Bold),
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
                rows.forEach { (key, description) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            key,
                            color = Pal.Peri,
                            style = mono(14.sp),
                            modifier = Modifier.width(140.dp),
                            maxLines = 1,
                        )
                        Text(
                            description,
                            color = Pal.Gray,
                            style = mono(14.sp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.padding(top = 4.dp))
            }
            HorizontalDivider(color = Pal.Border)
            Text(
                "Закрыть",
                color = Pal.Accent,
                style = type(12.sp, FontWeight.Medium, 1.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .termClickable(onClose)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
