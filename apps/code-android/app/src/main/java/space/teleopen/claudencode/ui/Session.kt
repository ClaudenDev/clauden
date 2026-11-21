package space.teleopen.claudencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.teleopen.claudencode.data.CodeForm
import space.teleopen.claudencode.data.CodeMessage
import space.teleopen.claudencode.data.CodePermission
import space.teleopen.claudencode.data.CodeSession

@Composable
internal fun SessionScreen(
    session: CodeSession,
    usage: space.teleopen.claudencode.data.CodeUsage?,
    note: String?,
    queued: List<String>,
    draft: String,
    commands: List<PromptCommand>,
    userName: String,
    favorite: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onTodos: () -> Unit,
    onProject: () -> Unit,
    onModel: () -> Unit,
    onSessions: () -> Unit,
    onNew: () -> Unit,
    onFavorite: () -> Unit,
    onMenu: () -> Unit,
    onHelp: () -> Unit,
    onGithub: () -> Unit,
    onNoteClose: () -> Unit,
    onAnswerPermission: (CodePermission, String) -> Unit,
    onAnswerForm: (CodeForm, Map<String, String>) -> Unit,
    attachments: List<space.teleopen.claudencode.data.CodeAttachment> = emptyList(),
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: ((space.teleopen.claudencode.data.CodeAttachment) -> Unit)? = null,
) {
    val blocks = remember(session) {
        buildBlocks(session).toMutableList().also { list ->
            if (session.working && list.lastOrNull() is Block.User) {
                list += Block.Assistant(
                    messages = listOf(CodeMessage(role = "assistant", status = "working")),
                    streaming = true,
                    fallbackTs = session.updatedAt,
                )
            }
        }
    }
    val listState = rememberLazyListState()
    var autoFollow by remember(session.id) { mutableStateOf(true) }
    val latestBlocks by rememberUpdatedState(blocks)

    LaunchedEffect(session.id) {
        if (blocks.isNotEmpty()) listState.scrollToItem(blocks.lastIndex)
    }
    val userMessages = session.messages.count { it.role == "user" }
    LaunchedEffect(userMessages) {
        if (userMessages > 0) autoFollow = true
    }

    LaunchedEffect(session.id) {
        while (true) {
            delay(260)
            val info = listState.layoutInfo
            val lastInfo = info.visibleItemsInfo.lastOrNull()
            val atBottom = info.totalItemsCount == 0 || (
                lastInfo != null &&
                    lastInfo.index >= info.totalItemsCount - 1 &&
                    lastInfo.offset + lastInfo.size <= info.viewportEndOffset + 2
                )
            if (atBottom) autoFollow = true
            if (autoFollow && !atBottom && latestBlocks.isNotEmpty()) {
                listState.scrollToItem(latestBlocks.lastIndex)
            }
        }
    }

    val dragConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) autoFollow = false
                return Offset.Zero
            }
        }
    }

    val permission = session.permissions.firstOrNull()
    val choice = choiceForm(session)
    val fallbackForm = session.forms.firstOrNull { it != choice }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            SessionTopBar(
                title = sessionTitle(session),
                subtitle = sessionSubtitle(session),
                favorite = favorite,
                onBack = onBack,
                onFavorite = onFavorite,
                onMenu = onMenu,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(dragConnection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 18.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(blocks) { index, block ->
                    BlockView(
                        block = block,
                        userName = userName,
                        onTodos = onTodos,
                        onAnswerForm = onAnswerForm,
                        onInteract = { autoFollow = false },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Bg)
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            ) {
                if (queued.isNotEmpty()) {
                    queued.take(2).forEach { text ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .background(Pal.Panel, RoundedCornerShape(10.dp))
                                .border(1.dp, Pal.BorderSoft, RoundedCornerShape(10.dp))
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "В очереди",
                                color = Pal.Accent,
                                style = type(10.sp, FontWeight.Medium, 1.2f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text,
                                color = Pal.Gray,
                                style = type(11.sp, null, 1.3f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                note?.let { text ->
                    NoteBanner(
                        text = text,
                        onClose = onNoteClose,
                        modifier = Modifier.termClickable(onNoteClose),
                    )
                    Spacer(Modifier.height(9.dp))
                }
                when {
                    permission != null -> PermissionPane(
                        permission = permission,
                        onAnswer = onAnswerPermission,
                    )
                    fallbackForm != null -> FormPane(
                        form = fallbackForm,
                        onAnswer = onAnswerForm,
                    )
                }
                ComposerBar(
                    draft = draft,
                    onDraft = onDraft,
                    onSend = onSend,
                    commands = commands,
                    placeholder = "Написать сообщение или команду…",
                    accent = true,
                    working = session.working,
                    attachments = attachments,
                    onAttach = onAttach,
                    onRemoveAttachment = onRemoveAttachment,
                )
                Spacer(Modifier.height(12.dp))
                QuickChips(
                    pill = true,
                    onHelp = onHelp,
                    onProject = onProject,
                    onSessions = onSessions,
                    onGithub = onGithub,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun sessionTitle(session: CodeSession): String {
    val remote = session.remote?.label
    if (!remote.isNullOrBlank()) return remote
    return session.repoName.ifBlank {
        session.title.ifBlank { "Claude Code" }.take(28)
    }.ifBlank { "Claude Code" }
}

private fun sessionSubtitle(session: CodeSession): String {
    val state = when {
        session.status == "working" -> "Онлайн"
        session.source == "ssh" -> "Офлайн"
        else -> "Готов"
    }
    return "Claude Code · $state"
}

@Composable
private fun PermissionPane(
    permission: CodePermission,
    onAnswer: (CodePermission, String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Color(0xFF140D0A), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF4B2416), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            permission.action.replaceFirstChar { it.uppercase() }.ifBlank { "Команда" },
            color = Pal.White,
            style = type(13.5.sp, FontWeight.Bold, 1.3f),
        )
        val command = permission.resources.joinToString(" ").trim()
        if (command.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                "\$ $command",
                color = Pal.Gray,
                style = type(11.5.sp, null, 1.4f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (permission.message.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                permission.message,
                color = Pal.Dim,
                style = type(11.sp, null, 1.4f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(9.dp))
        PermissionOption("1", "Разрешить", false) { onAnswer(permission, "once") }
        Spacer(Modifier.height(6.dp))
        PermissionOption("2", "Разрешить и больше не спрашивать", false) {
            onAnswer(permission, "always")
        }
        Spacer(Modifier.height(6.dp))
        PermissionOption("3", "Отклонить", true) { onAnswer(permission, "reject") }
    }
}

@Composable
private fun PermissionOption(
    number: String,
    label: String,
    danger: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Pal.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Pal.BorderSoft, RoundedCornerShape(10.dp))
            .termClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number,
            color = Pal.Dim,
            style = type(11.5.sp, null, 1.2f),
            modifier = Modifier.width(18.dp),
        )
        IconChevron(size = 14.dp, tint = Pal.Dim)
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            color = if (danger) Pal.Red else Pal.White,
            style = type(12.5.sp, FontWeight.Medium, 1.3f),
        )
    }
}

@Composable
private fun FormPane(
    form: CodeForm,
    onAnswer: (CodeForm, Map<String, String>) -> Unit,
) {
    var answers by remember(form.id) { mutableStateOf(mapOf<String, String>()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Pal.Panel, RoundedCornerShape(14.dp))
            .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            form.title.ifBlank { "Нужен ответ" },
            color = Pal.White,
            style = type(13.5.sp, FontWeight.Bold, 1.3f),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            form.fields.forEach { field ->
                Text(
                    field.title,
                    color = Pal.PeriSoft,
                    style = type(11.5.sp, FontWeight.Medium, 1.3f),
                )
                if (field.description.isNotBlank()) {
                    Text(
                        field.description,
                        color = Pal.Dim,
                        style = type(10.5.sp, null, 1.35f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (field.options.isNotEmpty()) {
                    field.options.forEach { option ->
                        val picked = answers[field.key] == option.value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                                .background(
                                    if (picked) Pal.PanelHi else Pal.CodeBg,
                                    RoundedCornerShape(9.dp),
                                )
                                .border(
                                    1.dp,
                                    if (picked) Pal.Accent else Pal.BorderSoft,
                                    RoundedCornerShape(9.dp),
                                )
                                .termClickable {
                                    answers = answers + (field.key to option.value)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (picked) "✓ " else "",
                                color = Pal.Accent,
                                style = type(11.5.sp, FontWeight.Bold, 1.3f),
                            )
                            Text(
                                option.label,
                                color = Pal.White,
                                style = type(11.5.sp, null, 1.3f),
                            )
                            if (option.description.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    option.description,
                                    color = Pal.Dim,
                                    style = type(10.sp, null, 1.3f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    FormTextField(
                        value = answers[field.key].orEmpty(),
                        hint = if (field.required) "Обязательный ответ…" else "Ответ…",
                        onValue = { answers = answers + (field.key to it) },
                    )
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Pal.Accent)
                    .termClickable {
                        val filled = form.fields.all { field ->
                            answers[field.key].orEmpty().isNotBlank() || !field.required
                        }
                        if (filled) {
                            onAnswer(form, answers.filterValues { it.isNotBlank() })
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "Отправить",
                    color = androidx.compose.ui.graphics.Color(0xFF1A0B06),
                    style = type(11.5.sp, FontWeight.Bold, 1.2f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Можно отменить позже",
                color = Pal.Dim,
                style = type(10.sp, null, 1.3f),
            )
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    hint: String,
    onValue: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Pal.CodeBg, RoundedCornerShape(9.dp))
            .border(1.dp, Pal.BorderSoft, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = type(12.sp, null, 1.3f).copy(color = Pal.White),
            cursorBrush = SolidColor(Pal.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(hint, color = Pal.Dim, style = type(11.5.sp, null, 1.3f))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
