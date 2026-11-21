package space.teleopen.clauden.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.AuthState
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.ThemeStyle
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

private enum class AuthStage { Login, Terms, Birthday, Name }

private enum class EntryScreen { Welcome, Email, Claude }

private const val POLICY_SEEN_KEY = "welcome_policy_seen"
private const val POLICY_WAIT_SECONDS = 10

@Composable
fun AuthFlow(state: ChatUiState, vm: ChatViewModel) {
    val auth = state.auth
    val context = LocalContext.current
    val policyPrefs = remember { context.getSharedPreferences("clauden", Context.MODE_PRIVATE) }
    var policySeen by rememberSaveable {
        mutableStateOf(policyPrefs.getBoolean(POLICY_SEEN_KEY, false))
    }
    if (!auth.signedIn && !policySeen) {
        WelcomePolicyScreen(
            onAccept = {
                policyPrefs.edit().putBoolean(POLICY_SEEN_KEY, true).apply()
                policySeen = true
            },
        )
        return
    }

    if (!auth.signedIn) {
        var screen by rememberSaveable { mutableStateOf(EntryScreen.Welcome) }
        var register by rememberSaveable { mutableStateOf(false) }
        var termsOpen by rememberSaveable { mutableStateOf(false) }

        when (screen) {
            EntryScreen.Welcome -> AuthEntryScreen(
                onClaudeLogin = {
                    register = false
                    vm.dismissAuthError()
                    screen = EntryScreen.Claude
                },
                onClaudeRegister = {
                    register = true
                    vm.dismissAuthError()
                    screen = EntryScreen.Claude
                },
                onClaudenLogin = {
                    register = false
                    vm.dismissAuthError()
                    screen = EntryScreen.Email
                },
                onClaudenRegister = {
                    register = true
                    vm.dismissAuthError()
                    screen = EntryScreen.Email
                },
                onTerms = { termsOpen = true },
            )

            EntryScreen.Email -> LoginScreen(
                state = state,
                vm = vm,
                initialRegister = register,
                onBack = {
                    vm.clearAuthCodeState()
                    screen = EntryScreen.Welcome
                },
            )

            EntryScreen.Claude -> ClaudeLoginScreen(
                busy = state.authBusy,
                error = state.authError,
                onCaptured = vm::signInClaude,
                onBack = {
                    vm.dismissAuthError()
                    screen = EntryScreen.Welcome
                },
                onClauden = {
                    register = false
                    vm.dismissAuthError()
                    screen = EntryScreen.Email
                },
                linkPasswordFor = if (state.claudeLinkPassword) state.claudeLinkEmail else "",
                linkPasswordError = state.claudeLinkError,
                onLinkPassword = vm::linkClaudeWithPassword,
                onCancelLinkPassword = vm::cancelClaudeLink,
            )
        }
        if (termsOpen) {
            WelcomeTermsDialog(onClose = { termsOpen = false })
        }
        return
    }

    var stage by rememberSaveable(auth.userId) { mutableStateOf(AuthStage.Terms) }
    var accepted by rememberSaveable(auth.userId) { mutableStateOf(false) }
    var birthday by rememberSaveable(auth.userId) { mutableStateOf(auth.birthday) }
    var name by rememberSaveable(auth.userId) { mutableStateOf(auth.name) }

    BackHandler(enabled = stage != AuthStage.Terms) {
        stage = when (stage) {
            AuthStage.Name -> AuthStage.Birthday
            AuthStage.Birthday -> AuthStage.Terms
            else -> AuthStage.Terms
        }
    }

    when (stage) {
        AuthStage.Terms -> TermsScreen(
            busy = false,
            error = state.authError,
            enabled = accepted,
            onToggle = { accepted = !accepted },
            onDismissError = vm::dismissAuthError,
            onContinue = { stage = AuthStage.Birthday },
        )

        AuthStage.Birthday -> BirthdayScreen(
            birthday = birthday,
            busy = false,
            error = state.authError,
            onPick = { birthday = it },
            onDismissError = vm::dismissAuthError,
            onContinue = { stage = AuthStage.Name },
        )

        AuthStage.Name -> NameScreen(
            name = name,
            busy = state.authBusy,
            error = state.authError,
            onChange = { name = it },
            onDismissError = vm::dismissAuthError,
            onContinue = { vm.completeOnboarding(birthday, name) },
        )

        AuthStage.Login -> Unit
    }
}

@Composable
private fun WelcomePolicyScreen(onAccept: () -> Unit) {
    val colors = LocalClaudenColors.current
    var secondsLeft by rememberSaveable { mutableStateOf(POLICY_WAIT_SECONDS) }
    val ready = secondsLeft <= 0
    val buttonBg by animateColorAsState(
        targetValue = if (ready) colors.clay else colors.control,
        animationSpec = tween(220),
        label = "policy-button-bg",
    )
    val buttonFg by animateColorAsState(
        targetValue = if (ready) Color.White else colors.textTertiary,
        animationSpec = tween(220),
        label = "policy-button-fg",
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (secondsLeft > 0) {
                delay(1_000)

                if (windowInfo.isWindowFocused) secondsLeft -= 1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudenLogo(
                    modifier = Modifier.size(26.dp),
                    tint = colors.clay,
                    variant = ClaudenLogoVariant.Idle,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clauden",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                color = colors.popupBg,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                ) {
                    Text(
                        text = "Быстрая политика и условия",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 27.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.3).sp,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Вход через ")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                ),
                            ) { append("Clauden") }
                            append(
                                " не даёт вам бесплатно пользоваться ИИ-моделями, " +
                                    "даже моделями Sonnet 5.5 и Haiku 5.5. Мы по финансовым " +
                                    "причинам не можем поддерживать такое количество людей " +
                                    "с бесплатными лимитами, поэтому советуем вам ",
                            )
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.clay,
                                ),
                            ) { append("«Войти через Claude»") }
                            append(
                                ", если вы не собираетесь ничего оплачивать. Все функции " +
                                    "и интерфейс будут сохранены, но запросы будут " +
                                    "перенаправляться в официальный клиент Claude.",
                            )
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        ),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        color = colors.clay.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Если вы пришли сюда, чтобы купить аккаунт и не " +
                                "беспокоиться о том, что его забанят, тогда смело можете " +
                                "входить через Clauden.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.textPrimary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = buttonBg,
                contentColor = buttonFg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(enabled = ready, onClick = onAccept),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Хорошо",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (!ready) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$secondsLeft с",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AuthEntryScreen(
    onClaudeLogin: () -> Unit,
    onClaudeRegister: () -> Unit,
    onClaudenLogin: () -> Unit,
    onClaudenRegister: () -> Unit,
    onTerms: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudenLogo(
                    modifier = Modifier.size(28.dp),
                    tint = colors.clay,
                    variant = ClaudenLogoVariant.Idle,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clauden",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(22.dp))
                AuthIllustration(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Как вы хотите войти?",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 29.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Через Claude — бесплатно, своим аккаунтом. Через Clauden — подписка в рублях.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))

                AuthPrimaryButton(
                    text = "Продолжить с Claude",
                    busy = false,
                    enabled = true,
                    onClick = onClaudeLogin,
                )
                Spacer(modifier = Modifier.height(10.dp))
                AuthSecondaryButton(
                    text = "Войти в Clauden",
                    onClick = onClaudenLogin,
                )

                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.border),
                    )
                    Text(
                        text = "ИЛИ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.border),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                AuthSecondaryButton(text = "Создать аккаунт с Claude", onClick = onClaudeRegister)
                Spacer(modifier = Modifier.height(10.dp))
                AuthSecondaryButton(text = "Создать аккаунт Clauden", onClick = onClaudenRegister)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Вход через Clauden не даёт доступа к бесплатному Claude. " +
                        "Он позволяет купить подписку на Claude в рублях и на 100% защитить " +
                        "свой аккаунт от бана.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                    ),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Продолжая, вы принимаете ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = colors.textTertiary,
                    )
                    Text(
                        text = "условия",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = colors.clay,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onTerms)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                    Text(
                        text = " использования.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AuthSecondaryButton(text: String, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun WelcomeTermsDialog(onClose: () -> Unit) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.popupBg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .blockClicks(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Text(
                    text = "Условия использования",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Clauden — интерфейс к моделям Claude. Пользуясь приложением, вы " +
                        "принимаете Потребительские условия и Политику использования Clauden. " +
                        "В режиме Claude запросы идут с вашего устройства напрямую в claude.ai " +
                        "под вашим аккаунтом; Clauden не хранит ваш пароль и cookie Claude. " +
                        "В режиме Clauden ответы готовит наш шлюз в рамках оплаченного тарифа. " +
                        "Не передавайте доступ к аккаунту третьим лицам и соблюдайте правила " +
                        "Anthropic.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    ),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                AuthPrimaryButton(
                    text = "Понятно",
                    busy = false,
                    enabled = true,
                    onClick = onClose,
                )
            }
        }
    }
}

private enum class EmailMode { Login, Register }

@Composable
private fun LoginScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    initialRegister: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    var mode by rememberSaveable(initialRegister) {
        mutableStateOf(if (initialRegister) EmailMode.Register else EmailMode.Login)
    }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val error = localError ?: state.authError
    val busy = state.authBusy || state.authCodeBusy
    val register = mode == EmailMode.Register
    val codeSentToCurrent = state.authCodeSentTo.equals(email.trim(), ignoreCase = true)

    fun clearErrors() {
        localError = null
        vm.dismissAuthError()
    }

    fun submit() {
        clearErrors()
        if (register) vm.registerEmail(email, code, password)
        else vm.signInEmail(email, password)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    ClaudenLogo(
                        modifier = Modifier.size(28.dp),
                        tint = colors.clay,
                        variant = ClaudenLogoVariant.Idle,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clauden",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                }
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp),
                    ) {
                        Icon(
                            ClaudenIcons.arrowBack,
                            contentDescription = "Назад",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                AuthIllustration(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "ИИ для тех,\nкто решает задачи",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 29.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))

                AuthModeSwitch(mode = mode, onSelect = { next ->
                    if (next == mode) return@AuthModeSwitch
                    mode = next
                    code = ""
                    clearErrors()
                    vm.clearAuthCodeState()
                })

                Spacer(modifier = Modifier.height(18.dp))
                AuthTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (!state.authCodeSentTo.equals(it.trim(), ignoreCase = true)) code = ""
                        clearErrors()
                    },
                    placeholder = "Email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )

                if (register) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AuthCodeButton(
                        busy = state.authCodeBusy,
                        sent = codeSentToCurrent,
                        enabled = email.isNotBlank() && !busy,
                        onClick = {
                            clearErrors()
                            vm.requestEmailCode(email)
                        },
                    )
                    if (codeSentToCurrent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Код отправлен на ${state.authCodeSentTo}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center,
                            ),
                            color = colors.clay,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    AuthTextField(
                        value = code,
                        onValueChange = {
                            code = it.filter(Char::isDigit).take(6)
                            clearErrors()
                        },
                        placeholder = "Код из письма",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                AuthTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        clearErrors()
                    },
                    placeholder = "Пароль",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    password = true,
                    onDone = { if (!busy) submit() },
                )

                if (register) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Код действует 10 минут. Если аккаунт уже есть, вход по коду установит новый пароль.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                        ),
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AuthErrorBanner(error, onDismiss = { clearErrors() })
                }

                Spacer(modifier = Modifier.height(18.dp))
                AuthPrimaryButton(
                    text = if (register) "Создать аккаунт" else "Войти",
                    busy = state.authBusy,
                    enabled = !busy &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        (!register || code.length == 6),
                    onClick = { submit() },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.clay,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy) {
                            mode = if (register) EmailMode.Login else EmailMode.Register
                            code = ""
                            clearErrors()
                            vm.clearAuthCodeState()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Продолжая, вы принимаете условия использования и политику конфиденциальности Clauden.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                    ),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AuthModeSwitch(mode: EmailMode, onSelect: (EmailMode) -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.control, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        listOf(EmailMode.Login to "Вход", EmailMode.Register to "Регистрация").forEach { (item, label) ->
            val selected = mode == item
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.bg else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textTertiary,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.borderStrong,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = colors.clay,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthCodeButton(
    busy: Boolean,
    sent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(enabled = enabled && !busy, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = colors.clay,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (sent) "Отправить код ещё раз" else "Получить код на почту",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    if (LocalThemeStyle.current == ThemeStyle.Glass) {
        LiquidGlassButton(
            onClick = onClick,
            enabled = enabled && !busy,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.textPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
            }
        }
        return
    }
    Surface(
        color = if (enabled || busy) colors.textPrimary else colors.control,
        contentColor = if (enabled || busy) colors.bg else colors.textTertiary,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled && !busy, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.bg,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
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
private fun AuthIllustration(modifier: Modifier = Modifier) {
    val colors = LocalClaudenColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val unit = minOf(w, h)

            drawCircle(
                color = colors.clay.copy(alpha = 0.06f),
                radius = unit * 0.46f,
                center = Offset(w * 0.5f, h * 0.52f),
            )
            drawCircle(
                color = colors.clay.copy(alpha = 0.05f),
                radius = unit * 0.34f,
                center = Offset(w * 0.5f, h * 0.52f),
            )

            listOf(
                Offset(w * 0.36f, h * 0.18f) to unit * 0.022f,
                Offset(w * 0.40f, h * 0.28f) to unit * 0.018f,
                Offset(w * 0.34f, h * 0.38f) to unit * 0.014f,
            ).forEach { (center, radius) ->
                drawCircle(color = colors.clay, radius = radius, center = center)
            }
        }
        ClaudenLogo(
            modifier = Modifier.size(112.dp),
            tint = colors.textPrimary,
            variant = ClaudenLogoVariant.Jelly,
        )
    }
}

@Composable
private fun GoogleButton(busy: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            LocalClaudenColors.current.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF1F1F1F),
                    strokeWidth = 2.dp,
                )
            } else {
                GoogleG(modifier = Modifier.size(21.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Продолжить через Google",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color(0xFF1F1F1F),
                )
            }
        }
    }
}

@Composable
fun GoogleG(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2f
        val rect = Rect(
            left = inset,
            top = inset,
            right = size.width - inset,
            bottom = size.height - inset,
        )
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)
        drawArc(Color(0xFF4285F4), 315f, 90f, false, rect.topLeft, rect.size, style = style)
        drawArc(Color(0xFFEA4335), 225f, 90f, false, rect.topLeft, rect.size, style = style)
        drawArc(Color(0xFFFBBC05), 135f, 90f, false, rect.topLeft, rect.size, style = style)
        drawArc(Color(0xFF34A853), 45f, 90f, false, rect.topLeft, rect.size, style = style)
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(size.width * 0.5f, size.height * 0.5f - stroke / 2f),
            size = Size(size.width * 0.5f - inset, stroke),
        )
    }
}

@Composable
private fun SecondaryGoogleButton(busy: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleG(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Продолжить через Google в браузере",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = colors.textPrimary,
            )
        }
    }
}

fun openGoogleBrowser(context: Context, onError: (String) -> Unit) {
    val url = "https://clauden.best/webapi/auth/google/start?app=clauden"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { onError("Не удалось открыть браузер") }
}

@Composable
private fun OrDivider() {
    val colors = LocalClaudenColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border),
        )
        Text(
            text = "ИЛИ",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border),
        )
    }
}

@Composable
private fun OnboardingScaffold(
    title: String,
    subtitle: String,
    error: String?,
    onDismissError: () -> Unit,
    primaryText: String = "Продолжить",
    primaryEnabled: Boolean = true,
    busy: Boolean = false,
    onPrimary: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
            Spacer(modifier = Modifier.height(86.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 31.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(30.dp))
            content()
            if (error != null) {
                Spacer(modifier = Modifier.height(14.dp))
                AuthErrorBanner(error, onDismiss = onDismissError)
            }
            Spacer(modifier = Modifier.height(30.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            PrimaryAuthButton(
                text = primaryText,
                enabled = primaryEnabled && !busy,
                busy = busy,
                onClick = onPrimary,
            )
            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

@Composable
private fun TermsScreen(
    busy: Boolean,
    error: String?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDismissError: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    OnboardingScaffold(
        title = "Давайте создадим аккаунт",
        subtitle = "Несколько моментов, с которыми нужно ознакомиться перед началом работы",
        error = error,
        onDismissError = onDismissError,
        primaryEnabled = enabled,
        busy = busy,
        onPrimary = onContinue,
    ) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (enabled) colors.clay else colors.borderStrong,
                            shape = RoundedCornerShape(5.dp),
                        )
                        .background(
                            if (enabled) colors.clay else Color.Transparent,
                            RoundedCornerShape(5.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = buildAnnotatedTerms(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    ),
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun buildAnnotatedTerms(): androidx.compose.ui.text.AnnotatedString {
    val underline = androidx.compose.ui.text.SpanStyle(
        textDecoration = TextDecoration.Underline,
    )
    val text = "Я принимаю Потребительские условия и Политику использования Clauden."
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        val first = text.indexOf("Потребительские условия")
        if (first >= 0) addStyle(underline, first, first + "Потребительские условия".length)
        val second = text.indexOf("Политику использования")
        if (second >= 0) addStyle(underline, second, second + "Политику использования".length)
    }
}

@Composable
private fun BirthdayScreen(
    birthday: String,
    busy: Boolean,
    error: String?,
    onPick: (String) -> Unit,
    onDismissError: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val display = remember(birthday) {
        if (birthday.isBlank()) "" else {
            val parts = birthday.split("-")
            if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else birthday
        }
    }

    fun openPicker() {
        val now = Calendar.getInstance()
        val start = runCatching {
            val parts = birthday.split("-").map { it.toInt() }
            if (parts.size == 3) Calendar.getInstance().apply {
                set(parts[0], parts[1] - 1, parts[2])
            } else null
        }.getOrNull() ?: Calendar.getInstance().apply {
            set(now.get(Calendar.YEAR) - 25, 0, 1)
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPick(
                    String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        year,
                        month + 1,
                        day,
                    ),
                )
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    OnboardingScaffold(
        title = "Когда у вас день рождения?",
        subtitle = "Это помогает нам подтвердить ваш аккаунт",
        error = error,
        onDismissError = onDismissError,
        primaryEnabled = display.isNotBlank(),
        busy = busy,
        onPrimary = onContinue,
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable(onClick = ::openPicker),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = display.ifBlank { "Введите дату рождения" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = if (display.isBlank()) colors.textPrimary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Выбрать дату",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.blueText,
                )
            }
        }
    }
}

@Composable
private fun NameScreen(
    name: String,
    busy: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    OnboardingScaffold(
        title = "Как вас зовут?",
        subtitle = "Claude будет так к вам обращаться",
        error = error,
        onDismissError = onDismissError,
        primaryEnabled = name.isNotBlank(),
        busy = busy,
        primaryText = if (busy) "Сохраняем…" else "Продолжить",
        onPrimary = onContinue,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            placeholder = {
                Text(
                    text = "Введите имя",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = colors.textTertiary,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (name.isNotBlank() && !busy) onContinue()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.clay,
                unfocusedBorderColor = colors.borderStrong,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = colors.clay,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
    }
}

@Composable
private fun PrimaryAuthButton(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (enabled) colors.textPrimary else colors.buttonDisabled,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (colors.isDark) Color.Black else Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (enabled) {
                        if (colors.isDark) Color.Black else Color.White
                    } else {
                        colors.buttonDisabledText
                    },
                )
            }
        }
    }
}

@Composable
private fun AuthErrorBanner(text: String, onDismiss: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.danger.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
            color = colors.dangerSoft,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}
