package space.teleopen.claudencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PromptCommand(
    val name: String,
    val description: String,
    val run: (arg: String) -> Unit,
)

@Composable
fun ComposerBar(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    commands: List<PromptCommand>,
    placeholder: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    focusRequester: FocusRequester? = null,
    attachments: List<space.teleopen.claudencode.data.CodeAttachment> = emptyList(),
    onAttach: (() -> Unit)? = null,
    onRemoveAttachment: ((space.teleopen.claudencode.data.CodeAttachment) -> Unit)? = null,
) {
    var field by remember(draft) {
        mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
    }
    val menuOpen = draft.startsWith("/") && !draft.contains(' ')
    val query = draft.substringAfter("/").lowercase()
    val matches = if (menuOpen) {
        commands.filter { it.name.lowercase().contains(query) }.take(5)
    } else {
        emptyList()
    }

    fun setDraft(next: String) {
        field = TextFieldValue(next, TextRange(next.length))
        onDraft(next)
    }

    fun send() {
        if (draft.isBlank() && attachments.isEmpty()) return
        field = TextFieldValue("", TextRange.Zero)
        onSend()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (matches.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
                    .padding(vertical = 5.dp, horizontal = 5.dp),
            ) {
                matches.forEach { command ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .termClickable { setDraft("${command.name} ") }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            command.name,
                            color = Pal.Accent,
                            style = type(13.sp, FontWeight.Medium, 1.2f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            command.description,
                            color = Pal.Dim,
                            style = type(11.5.sp, null, 1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.padding(top = 8.dp))
        }

        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                attachments.forEach { image ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Pal.Panel)
                            .border(1.dp, Pal.BorderSoft, RoundedCornerShape(9.dp))
                            .padding(start = 8.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconFile(size = 14.dp, tint = Pal.Gray)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            image.name,
                            color = Pal.Gray,
                            style = type(11.sp, null, 1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                        if (onRemoveAttachment != null) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .termClickable { onRemoveAttachment(image) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("×", color = Pal.Dim, style = type(13.sp, null, 1f))
                            }
                        }
                    }
                }
            }
        }

        val shape = RoundedCornerShape(28.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(shape)
                .background(if (accent) Color(0xFF0C0807) else Pal.Panel)
                .border(
                    if (accent) 1.2.dp else 1.dp,
                    if (accent) Pal.Accent else Pal.Border,
                    shape,
                )
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAttach != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .termClickable { onAttach() },
                    contentAlignment = Alignment.Center,
                ) {
                    IconFile(size = 19.dp, tint = Pal.Gray)
                }
                Spacer(Modifier.width(2.dp))
            }
            Text(
                ">",
                color = Pal.Accent,
                style = type(17.sp, FontWeight.Bold, 1.1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = field,
                    onValueChange = { value ->
                        field = value
                        onDraft(value.text)
                    },
                    textStyle = type(13.5.sp, null, 1.3f).copy(color = Pal.White),
                    cursorBrush = SolidColor(Pal.Accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { send() },
                        onDone = { send() },
                        onGo = { send() },
                        onSearch = { send() },
                        onNext = { if (matches.isNotEmpty()) setDraft("${matches.first().name} ") },
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = Pal.Dim,
                                    style = type(13.5.sp, null, 1.3f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (accent) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .termClickable { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    IconSend(size = 25.dp, tint = Pal.Accent)
                }
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Pal.PanelHi)
                        .termClickable { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    IconUp(size = 19.dp, tint = Pal.White)
                }
            }
        }
    }
}
