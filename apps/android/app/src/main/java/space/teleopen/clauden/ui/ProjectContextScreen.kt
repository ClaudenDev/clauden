package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.ProjectFile
import kotlin.math.roundToInt

private const val PROJECT_CAPACITY_CHARS = 60_000

@Composable
fun ProjectContextScreen(state: ChatUiState, vm: ChatViewModel) {
    val project = state.activeProject
    if (project == null) {
        LaunchedEffect(Unit) { vm.closeProject() }
        return
    }

    val colors = LocalClaudenColors.current
    val usedChars = remember(project.files) { project.files.sumOf { it.text.length } }
    val fraction = (usedChars.toFloat() / PROJECT_CAPACITY_CHARS).coerceIn(0f, 1f)
    val percent = (fraction * 100).roundToInt()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.addProjectFiles(project.id, uris)
    }

    BackHandler { vm.closeProjectContext() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeProjectContext() }, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = ClaudenIcons.arrowBack,
                    contentDescription = "Назад",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "Контекст",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(42.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(colors.control),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(colors.clay),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Использовано $percent% емкости проекта",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (project.files.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.62f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = ClaudenIcons.documentPlus,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(96.dp),
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            text = "Добавьте сюда подходящие документы, текст, код или " +
                                "другие файлы — Clauden сможет использовать их как контекст " +
                                "для всего в «${project.title}».",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                lineHeight = 25.sp,
                            ),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(items = project.files, key = { it.id }) { file ->
                    ProjectContextFileRow(
                        file = file,
                        onDelete = { vm.removeProjectFile(project.id, file.id) },
                    )
                }
            }
        }

        if (state.projectBusy) {
            Text(
                text = "Читаю файлы…",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.clay,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Surface(
            color = colors.textPrimary,
            contentColor = colors.bg,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .height(56.dp)
                .clickable(enabled = !state.projectBusy) {
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "text/*",
                            "application/json",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ),
                    )
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = ClaudenIcons.documentPlus,
                    contentDescription = null,
                    tint = colors.bg,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Добавить контент",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProjectContextFileRow(file: ProjectFile, onDelete: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ClaudenIcons.doc,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatChars(file.chars)} · ${formatSize(file.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = ClaudenIcons.trash,
                    contentDescription = "Удалить файл",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

private fun formatChars(chars: Int): String {
    val text = if (chars >= 1000) "${chars / 1000} тыс." else chars.toString()
    return "$text символов"
}
