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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.claudencode.data.CodeSession
import space.teleopen.claudencode.data.CodeUsage

@Composable
fun HomeScreen(
    mode: String,
    onMode: (String) -> Unit,
    project: ProjectUi,
    statusTitle: String,
    statusSubtitle: String,
    working: Boolean,
    sessions: List<CodeSession>,
    usage: CodeUsage?,
    modelLabel: (CodeSession) -> String,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    commands: List<PromptCommand>,
    note: String?,
    onNoteClose: () -> Unit,
    onNew: () -> Unit,
    onMenu: () -> Unit,
    onProject: () -> Unit,
    onStatus: () -> Unit,
    onAllSessions: () -> Unit,
    onOpenSession: (String) -> Unit,
    onHelp: () -> Unit,
    onGithub: () -> Unit,
    onSessions: () -> Unit,
) {
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            HomeHeader(onNew = onNew, onMenu = onMenu)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                SegmentedMode(mode = mode, onMode = onMode)
                Spacer(Modifier.height(9.dp))
                ProjectCard(project = project, onClick = onProject)
                Spacer(Modifier.height(10.dp))
                StatusCard(
                    title = statusTitle,
                    subtitle = statusSubtitle,
                    working = working,
                    onClick = onStatus,
                )
                Spacer(Modifier.height(16.dp))
                SectionHeader(title = "Последние сессии", onAll = onAllSessions)
                Spacer(Modifier.height(6.dp))
                if (sessions.isEmpty()) {
                    EmptySessions(mode = mode)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sessions.take(3).forEach { entry ->
                            HomeSessionCard(
                                session = entry,
                                modelLabel = modelLabel(entry),
                                onClick = { onOpenSession(entry.id) },
                            )
                        }
                    }
                }
                usage?.let { used ->
                    val tokens = used.tokens
                    val total = (tokens?.input ?: 0) + (tokens?.output ?: 0) +
                        (tokens?.reasoning ?: 0)
                    if (total > 0) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "В этой сессии ${formatTokens(total)} токенов · " +
                                "\$${"%.2f".format(used.cost)}",
                            color = Pal.Dim,
                            style = type(11.sp, null, 1.3f),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Bg)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
            ) {
                note?.let { text ->
                    NoteBanner(
                        text = text,
                        onClose = onNoteClose,
                        modifier = Modifier.termClickable(onNoteClose),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                ComposerBar(
                    draft = draft,
                    onDraft = onDraft,
                    onSend = onSend,
                    commands = commands,
                    placeholder = "Введите команду или вопрос…",
                    accent = false,
                )
                Spacer(Modifier.height(12.dp))
                QuickChips(
                    pill = false,
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

@Composable
private fun EmptySessions(mode: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clipCard()
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            "Пока нет сессий",
            color = Pal.PeriSoft,
            style = type(14.sp, FontWeight.Medium, 1.3f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (mode == "ssh") {
                "Подключите сервер по SSH и отправьте первую команду."
            } else {
                "Отправьте первую команду — сессия появится здесь."
            },
            color = Pal.Dim,
            style = type(11.5.sp, null, 1.4f),
        )
    }
}

private fun Modifier.clipCard(): Modifier = this
    .background(Pal.Panel, RoundedCornerShape(14.dp))
    .border(1.dp, Pal.BorderSoft, RoundedCornerShape(14.dp))

internal fun planLabel(user: space.teleopen.claudencode.data.AuthUser): String =
    when (user.plan.lowercase()) {
        "", "free" -> if (user.codeAccess) "Clauden Pro" else "Clauden Free"
        "pro" -> "Clauden Pro"
        "max" -> "Clauden Max"
        "ultra" -> "Clauden Ultra"
        "owner" -> "Clauden Owner"
        else -> "Clauden ${user.plan}"
    }

internal fun displayName(user: space.teleopen.claudencode.data.AuthUser): String {
    val fromName = user.name.substringBefore(' ').trim()
    if (fromName.isNotBlank()) return fromName
    val nick = user.email.substringBefore('@')
    return nick.replaceFirstChar { it.uppercase() }.take(22)
}
