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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.teleopen.claudencode.data.AuthApi
import space.teleopen.claudencode.data.AuthHttpException

@Composable
fun LoginScreen(
    api: AuthApi,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                api.loginEmail(email.trim(), password)
                onDone()
            } catch (err: AuthHttpException) {
                error = when (err.status) {
                    401 -> "Wrong email or password"
                    429 -> "Too many attempts, try again shortly"
                    else -> err.message ?: "Sign-in failed"
                }
            } catch (err: Throwable) {
                error = "Network error · ${err.javaClass.simpleName}: ${err.message ?: "no message"}"
            } finally {
                busy = false
            }
        }
    }

    fun requestCode() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                api.requestEmailCode(email.trim())
                codeSent = true
            } catch (err: Throwable) {
                error = "Network error · ${err.javaClass.simpleName}: ${err.message ?: "no message"}"
            } finally {
                busy = false
            }
        }
    }

    fun register() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                api.registerEmail(email.trim(), codeText.trim(), password)
                onDone()
            } catch (err: AuthHttpException) {
                error = if (err.status == 400) "Wrong code or password" else err.message
            } catch (err: Throwable) {
                error = "Network error · ${err.javaClass.simpleName}: ${err.message ?: "no message"}"
            } finally {
                busy = false
            }
        }
    }

    val focus = LocalFocusManager.current
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            ClaudeMark(size = 86.dp)
            Spacer(Modifier.height(22.dp))
            Text("Claude Code", color = Pal.White, style = type(24.sp, FontWeight.Bold, 1.2f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Вход в аккаунт Clauden",
                color = Pal.Gray,
                style = type(13.sp, null, 1.3f),
            )
            Spacer(Modifier.height(30.dp))

            Field(
                value = email,
                onValueChange = { email = it },
                hint = "email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onDone = { focus.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(10.dp))
            if (mode == "login") {
                Field(
                    value = password,
                    onValueChange = { password = it },
                    hint = "пароль",
                    keyboardType = KeyboardType.Password,
                    mask = true,
                    imeAction = ImeAction.Done,
                    submit = ::signIn,
                )
            } else {
                if (codeSent) {
                    Field(
                        value = codeText,
                        onValueChange = { codeText = it },
                        hint = "код из письма",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        onDone = { focus.moveFocus(FocusDirection.Down) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Field(
                    value = password,
                    onValueChange = { password = it },
                    hint = if (codeSent) "новый пароль" else "пароль (задаётся после кода)",
                    keyboardType = KeyboardType.Password,
                    mask = true,
                    imeAction = if (codeSent) ImeAction.Done else ImeAction.Send,
                    submit = if (codeSent) ::register else ::requestCode,
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                label = when {
                    mode == "login" -> if (busy) "Входим…" else "Войти"
                    codeSent && busy -> "Создаём аккаунт…"
                    codeSent -> "Создать аккаунт"
                    busy -> "Отправляем код…"
                    else -> "Отправить код на email"
                },
                enabled = !busy && email.isNotBlank() &&
                    (if (mode == "login") password.isNotBlank() else true),
                onClick = {
                    when {
                        mode == "login" -> signIn()
                        codeSent -> register()
                        else -> requestCode()
                    }
                },
            )
            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                NoteBanner(text = message, onClose = { error = null })
            }
            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Rule(Pal.Border, Modifier.width(52.dp))
                Spacer(Modifier.width(12.dp))
                Text("или", color = Pal.Dim, style = type(11.5.sp, null, 1.2f))
                Spacer(Modifier.width(12.dp))
                Rule(Pal.Border, Modifier.width(52.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (mode == "login") {
                    "Нет пароля? Отправить код на email"
                } else {
                    "Уже есть аккаунт? Войти"
                },
                color = Pal.Accent,
                style = type(12.5.sp, null, 1.3f),
                modifier = Modifier.termClickable {
                    mode = if (mode == "login") "code" else "login"
                    codeSent = false
                    codeText = ""
                    error = null
                },
            )
            Spacer(Modifier.height(46.dp))
            Text(
                "Clauden Code · clauden.best",
                color = Pal.Faint,
                style = type(11.sp, null, 1.2f),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    mask: Boolean = false,
    onDone: (() -> Unit)? = null,
    submit: (() -> Unit)? = null,
) {
    val actions = remember(onDone, submit) {
        KeyboardActions(
            onNext = { onDone?.invoke() },
            onDone = { submit?.invoke() ?: onDone?.invoke() },
            onGo = { submit?.invoke() ?: onDone?.invoke() },
            onSearch = { submit?.invoke() ?: onDone?.invoke() },
            onSend = { submit?.invoke() ?: onDone?.invoke() },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Panel)
            .border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = type(14.sp, null, 1.3f).copy(color = Pal.White),
            cursorBrush = SolidColor(Pal.Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = actions,
            visualTransformation =
                if (mask) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 15.dp)) {
                    if (value.isEmpty()) {
                        Text(hint, color = Pal.Dim, style = type(14.sp, null, 1.3f))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun TermButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) Pal.Accent else Pal.PanelHi)
            .termClickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color(0xFF1A0B06) else Pal.Dim,
            style = type(13.sp, FontWeight.Bold, 1.2f),
        )
    }
}
