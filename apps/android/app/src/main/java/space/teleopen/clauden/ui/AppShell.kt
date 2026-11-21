package space.teleopen.clauden.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.ButtonStyle
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeIconMode
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.data.UiStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClaudenApp(state: ChatUiState, vm: ChatViewModel) {
    if (state.claudeLoginOpen) {
        ClaudeLoginScreen(
            busy = state.authBusy,
            error = state.authError,
            onCaptured = vm::signInClaude,
            onBack = vm::closeClaudeLogin,
            onClauden = {
                vm.closeClaudeLogin()
                vm.switchEngine(AuthMode.Clauden)
            },
            linkPasswordFor = if (state.claudeLinkPassword) state.claudeLinkEmail else "",
            linkPasswordError = state.claudeLinkError,
            onLinkPassword = vm::linkClaudeWithPassword,
            onCancelLinkPassword = vm::cancelClaudeLink,
        )
        return
    }
    if (state.voiceMode) {
        VoiceModeScreen(state = state, vm = vm, onClose = vm::closeVoiceMode)
        return
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun hideInput() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }

    val openDrawer: () -> Unit = {
        hideInput()
        scope.launch { drawerState.open() }
    }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) hideInput()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !state.searchOpen,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerShape = LocalClaudenShapes.current.drawer,
                    drawerContainerColor = LocalClaudenColors.current.drawerBg,
                    drawerContentColor = LocalClaudenColors.current.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight()
                        .glassPanel(
                            shape = LocalClaudenShapes.current.drawer,
                            tint = LocalClaudenColors.current.drawerBg.copy(alpha = 0.10f),
                        ),
                ) {
                    DrawerContent(
                        state = state,
                        vm = vm,
                        onChats = {
                            vm.openChats()
                            closeDrawer()
                        },
                        onSearch = {
                            vm.openSearch()
                            closeDrawer()
                        },
                        onProjects = {
                            vm.openProjects()
                            closeDrawer()
                        },
                        onCode = {
                            vm.openCode()
                            closeDrawer()
                        },
                        onArtifacts = {
                            vm.openArtifacts()
                            closeDrawer()
                        },
                        onCustomize = {
                            vm.openCustomize("skills")
                            closeDrawer()
                        },
                        onTasks = {
                            vm.openSettingsSection(Screen.Tasks)
                            closeDrawer()
                        },
                        onNewChat = {
                            vm.newChat()
                            closeDrawer()
                        },
                        onSelectChat = { id ->
                            vm.openChat(id)
                            closeDrawer()
                        },
                        onSettings = {
                            vm.openSettings()
                            closeDrawer()
                        },
                    )
                }
            },
        ) {
            val section = if (state.screen.isSettingsSection) Screen.Settings else state.screen
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = fadeIn(tween(180)) +
                        slideInHorizontally(tween(210)) { if (forward) it / 12 else -it / 12 }
                    val exit = fadeOut(tween(120)) +
                        slideOutHorizontally(tween(180)) { if (forward) -it / 12 else it / 12 }
                    enter togetherWith exit
                },
                label = "main-screen",
            ) { screen ->
                when (screen) {
                    Screen.Chat -> ChatScreen(state = state, vm = vm, onMenu = openDrawer)
                    Screen.Chats -> ChatsScreen(state = state, vm = vm, onMenu = openDrawer)
                    Screen.Projects -> ProjectsScreen(state = state, vm = vm, onMenu = openDrawer)
                    Screen.Project -> ProjectScreen(state = state, vm = vm)
                    Screen.ProjectContext -> ProjectContextScreen(state = state, vm = vm)
                    Screen.Artifacts -> ArtifactsScreen(state = state, vm = vm, onMenu = openDrawer, codeOnly = false)
                    Screen.Code -> CodeScreen(state = state, vm = vm, onMenu = openDrawer)
                    Screen.Customize -> CustomizeScreen(state = state, vm = vm, onMenu = openDrawer)
                    else -> SettingsHost(state = state, vm = vm, onMenu = openDrawer)
                }
            }
        }

        SearchOverlay(state = state, vm = vm)

        if (state.claudeSwitched) {
            ClaudeSwitchedDialog(onConfirm = vm::dismissClaudeSwitched)
        }

        state.claudeProblem?.let { problem ->
            ClaudeProblemDialog(
                problem = problem,
                onDismiss = vm::dismissClaudeProblem,
                onRelogin = {
                    vm.dismissClaudeProblem()
                    vm.openClaudeLogin()
                },
                onClauden = {
                    vm.dismissClaudeProblem()
                    vm.switchEngine(AuthMode.Clauden)
                },
                onRetry = {
                    vm.dismissClaudeProblem()
                    vm.retryLast()
                },
                onDiagnostics = vm::copyClaudeDiagnostics,
            )
        }

        state.planThanks?.let { plan ->
            PlanSuccessDialog(
                planId = plan,
                plans = state.billingPlans,
                onDismiss = vm::dismissPlanThanks,
                onOpenUsage = {
                    vm.dismissPlanThanks()
                    vm.openSettingsSection(Screen.Usage)
                },
            )
        }

        if (state.notice != null && state.screen != Screen.Chat) {
            GlobalNotice(
                text = state.notice.orEmpty(),
                onClose = vm::dismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 24.dp),
            )
        }
    }

    LaunchedEffect(state.notice, state.screen) {
        if (state.notice != null && state.screen != Screen.Chat) {
            delay(2600)
            vm.dismissNotice()
        }
    }
}

@Composable
private fun GlobalNotice(text: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = LocalClaudenShapes.current.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsHost(
    state: ChatUiState,
    vm: ChatViewModel,
    onMenu: () -> Unit,
) {
    val screen = state.screen
    BackHandler(enabled = screen != Screen.Settings) { vm.backToSettings() }
    BackHandler(enabled = screen == Screen.Settings) { vm.closeSettings() }

    val screenStates = rememberSaveableStateHolder()

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val enter = fadeIn(tween(170)) +
                slideInHorizontally(tween(200)) { if (forward) it / 10 else -it / 10 }
            val exit = fadeOut(tween(110)) +
                slideOutHorizontally(tween(170)) { if (forward) -it / 10 else it / 10 }
            enter togetherWith exit
        },
        label = "settings-screen",
    ) { current ->
        screenStates.SaveableStateProvider(current.name) {
            when (current) {
                Screen.Settings -> SettingsScreen(state = state, vm = vm, onMenu = onMenu)
                Screen.General -> GeneralScreen(state = state, vm = vm)
                Screen.Account -> NewAccountScreen(state = state, vm = vm)
                Screen.Profile -> NewAccountScreen(state = state, vm = vm)
                Screen.ClaudeMode -> ClaudeModeScreen(state = state, vm = vm)
                Screen.Billing -> BillingScreen(state = state, vm = vm)
                Screen.Usage -> UsageScreen(state = state, vm = vm)
                Screen.ApiKeys -> ApiKeysScreen(state = state, vm = vm)
                Screen.Capabilities -> CapabilitiesScreen(state = state, vm = vm)
                Screen.Memory -> MemoryScreen(state = state, vm = vm)
                Screen.Reflect -> ReflectScreen(state = state, vm = vm)
                Screen.TimeFocus -> TimeFocusScreen(state = state, vm = vm)
                Screen.Sessions -> SessionsScreen(state = state, vm = vm)
                Screen.Tasks -> TasksScreen(state = state, vm = vm)
                Screen.Scheduled -> ScheduledScreen(state = state, vm = vm)
                Screen.Connection -> AccountScreen(state = state, vm = vm)
                Screen.CustomApi -> CustomApiScreen(state = state, vm = vm)
                Screen.Permissions -> PermissionsScreen(state = state, vm = vm)
                Screen.Notifications -> NotificationsScreen(state = state, vm = vm)
                Screen.Privacy -> PrivacyScreen(state = state, vm = vm)
                Screen.Sharing -> SharingScreen(state = state, vm = vm)
                Screen.ThemeStudio -> ThemeStudioScreen(state = state, vm = vm)
                else -> Unit
            }
        }
    }
}

@Composable
private fun DrawerContent(
    state: ChatUiState,
    vm: ChatViewModel,
    onChats: () -> Unit,
    onSearch: () -> Unit,
    onProjects: () -> Unit,
    onCode: () -> Unit,
    onArtifacts: () -> Unit,
    onCustomize: () -> Unit,
    onTasks: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalClaudenColors.current

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(top = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 10.dp, end = 24.dp),
        ) {
            ClaudenLogo(
                modifier = Modifier.size(26.dp),
                tint = colors.clay,
                variant = ClaudenLogoVariant.Idle,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "Clauden",
                style = if (LocalUiStyle.current == UiStyle.Material3) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    ClaudenType.serifTitle.copy(
                        fontFamily = LocalClaudenFonts.current.serif,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                color = colors.textPrimary,
            )
        }

        DrawerMenuRow(icon = ClaudenIcons.plus, label = "Новый чат", onClick = onNewChat)
        DrawerMenuRow(icon = Icons.Default.Search, label = "Поиск", onClick = onSearch)
        DrawerMenuRow(
            icon = ClaudenIcons.projects,
            label = "Проекты",
            onClick = onProjects,
            highlighted = state.screen == Screen.Projects || state.screen == Screen.Project,
        )
        DrawerMenuRow(
            icon = ClaudenIcons.artifacts,
            label = "Артефакты",
            onClick = onArtifacts,
            highlighted = state.screen == Screen.Artifacts,
        )
        DrawerMenuRow(
            icon = ClaudenIcons.code,
            label = "Code",
            onClick = onCode,
            highlighted = state.screen == Screen.Code,
        )
        DrawerMenuRow(
            icon = ClaudenIcons.spark,
            label = "Customize",
            onClick = onCustomize,
            highlighted = state.screen == Screen.Customize,
        )

        HorizontalDivider(
            color = colors.border,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.pinned.isNotEmpty()) {
                item {
                    DrawerSectionLabel("Закрепленные")
                }
                items(items = state.pinned, key = { "pin_" + it.id }) { chat ->
                    DrawerChatRow(
                        chat = chat,
                        projects = state.projects,
                        vm = vm,
                        onClick = { onSelectChat(chat.id) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Чаты и задачи",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.openTasks > 0) {
                        Text(
                            text = "${state.openTasks} задач",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.blueLink,
                            modifier = Modifier.clickable(onClick = onTasks),
                        )
                    }
                }
            }
            items(items = state.recent.take(8), key = { "recent_" + it.id }) { chat ->
                DrawerChatRow(
                    chat = chat,
                    projects = state.projects,
                    vm = vm,
                    onClick = { onSelectChat(chat.id) },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChats)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Все чаты",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                    )
                }
            }
        }

        HorizontalDivider(color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clickable(onClick = onSettings)) {
                ProfileAvatar(
                    path = state.settings.avatarPath,
                    letter = state.settings.avatarLetter,
                    size = 40,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.settings.fullName.ifBlank {
                        state.auth.name.ifBlank { "Аккаунт" }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activePlanLabel(state),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = ClaudenIcons.sliders,
                    contentDescription = "Настройки",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val themeId = "drawer:" + label.trim().lowercase()
    val style = LocalCustomTheme.current.elements[themeId]
    val iconPath = LocalCustomTheme.current.icons[themeId]
    val selecting = LocalThemeEditState.current.selectionMode
    val action = if (selecting) {
        { ThemeController.selectElement(themeId, label) }
    } else {
        onClick
    }
    val customBg = style?.background?.let(::Color)
    val customContent = style?.content?.let(::Color)
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    val inlineIcon = !fullImage && iconPath != null
    if (material3) {
        val item: @Composable () -> Unit = {
            NavigationDrawerItem(
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = customContent ?: if (highlighted) colors.textPrimary else colors.textSecondary,
                    )
                },
                icon = {
                    if (inlineIcon) {
                        ThemeAssetImage(
                            path = iconPath.orEmpty(),
                            modifier = Modifier.size(24.dp),
                            contentDescription = null,
                            contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                                ContentScale.Crop
                            } else {
                                ContentScale.Fit
                            },
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                selected = highlighted,
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp),
                shape = CircleShape,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = if (fullImage) Color.Transparent else customBg ?: colors.control,
                    unselectedContainerColor = if (fullImage) Color.Transparent else customBg ?: Color.Transparent,
                    selectedIconColor = customContent ?: colors.textPrimary,
                    unselectedIconColor = customContent ?: colors.textSecondary,
                    selectedTextColor = customContent ?: colors.textPrimary,
                    unselectedTextColor = customContent ?: colors.textSecondary,
                ),
            )
        }
        if (fullImage) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .clip(CircleShape),
            ) {
                ThemeAssetImage(
                    path = iconPath.orEmpty(),
                    modifier = Modifier.matchParentSize(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                if (customBg != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(customBg.copy(alpha = 0.22f)),
                    )
                }
                item()
            }
        } else {
            item()
        }
        return
    }
    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = customBg ?: if (highlighted) colors.control else Color.Transparent,
        shape = if (highlighted) RoundedCornerShape(10.dp) else RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.padding(horizontal = 10.dp)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = action),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (highlighted) 14.dp else 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inlineIcon) {
                ThemeAssetImage(
                    path = iconPath.orEmpty(),
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = customContent ?: colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = customContent ?: colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        color = colorsTertiary(),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerChatRow(
    chat: Conversation,
    projects: List<space.teleopen.clauden.data.Project>,
    vm: ChatViewModel,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var menu by remember(chat.id) { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 24.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ClaudenIcons.chatBubble,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChatContextMenu(
            chat = chat,
            projects = projects,
            expanded = menu,
            onDismiss = { menu = false },
            vm = vm,
        )
    }
}

@Composable
private fun colorsTertiary() = LocalClaudenColors.current.textTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(
    title: String,
    onMenu: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    if (LocalUiStyle.current == UiStyle.Material3) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                ThemeIconButton(
                    id = "header.menu",
                    icon = ClaudenIcons.menu,
                    contentDescription = "Меню",
                    onClick = onMenu,
                    size = 44.dp,
                    iconSize = 24.dp,
                )
            },
            actions = { trailing?.invoke() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = headerBackground(colors),
                titleContentColor = colors.textPrimary,
                navigationIconContentColor = colors.textPrimary,
                actionIconContentColor = colors.textPrimary,
            ),
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp),
    ) {
        ThemeIconButton(
            id = "header.menu",
            icon = ClaudenIcons.menu,
            contentDescription = "Меню",
            onClick = onMenu,
            modifier = Modifier.align(Alignment.CenterStart),
            size = 44.dp,
            iconSize = 20.dp,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            trailing?.invoke()
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    withPlus: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val themeId = "fab:" + text.trim().lowercase()
    val style = LocalCustomTheme.current.elements[themeId]
    val selecting = LocalThemeEditState.current.selectionMode
    val action = if (selecting) {
        { ThemeController.selectElement(themeId, text) }
    } else {
        onClick
    }
    val customBg = style?.background?.let(::Color)
    val customContent = style?.content?.let(::Color)
    val iconPath = LocalCustomTheme.current.icons[themeId]
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    if (LocalThemeStyle.current == ThemeStyle.Glass &&
        customBg == null && customContent == null && iconPath == null
    ) {
        LiquidGlassButton(
            onClick = action,
            shape = if (LocalButtonStyle.current == ButtonStyle.Square) {
                RoundedCornerShape(8.dp)
            } else {
                CircleShape
            },
            modifier = modifier.height(44.dp),
        ) {
            if (withPlus) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
        }
        return
    }
    if (material3) {
        if (fullImage) {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                ThemeAssetImage(
                    path = iconPath.orEmpty(),
                    modifier = Modifier.matchParentSize(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                if (customBg != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(customBg.copy(alpha = 0.22f)),
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = action,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.Transparent,
                    contentColor = customContent ?: MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                ) {
                    Text(text = text, style = MaterialTheme.typography.labelLarge)
                }
            }
            return
        }

        ExtendedFloatingActionButton(
            onClick = action,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            containerColor = customBg ?: MaterialTheme.colorScheme.primaryContainer,
            contentColor = customContent ?: MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp,
                focusedElevation = 6.dp,
                hoveredElevation = 8.dp,
            ),
        ) {
            if (iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (withPlus) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        return
    }
    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = customBg ?: if (colors.isDark) Color(0xFFF7F6F2) else Color(0xFF1F1E1C),
        shape = LocalClaudenShapes.current.button,
        modifier = modifier.clickable(onClick = action),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
        ) {
            if (!fullImage && iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (!fullImage && withPlus) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = customContent ?: if (colors.isDark) Color(0xFF141414) else Color(0xFFF9F9F7),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = customContent ?: if (colors.isDark) Color(0xFF141414) else Color(0xFFF9F9F7),
            )
        }
    }
}

fun filesCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "$count файлов"
        mod10 == 1 -> "$count файл"
        mod10 in 2..4 -> "$count файла"
        else -> "$count файлов"
    }
}

fun chatsCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "$count чатов"
        mod10 == 1 -> "$count чат"
        mod10 in 2..4 -> "$count чата"
        else -> "$count чатов"
    }
}

fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    if (minutes < 1) return "Только что"
    if (minutes < 60) return "$minutes мин назад"
    val hours = minutes / 60
    if (hours < 24) return "$hours ч назад"
    val days = hours / 24
    if (days == 1L) return "Вчера"
    if (days < 7) return "$days дн назад"
    return formatShortDate(timestamp)
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy 'г.'", Locale("ru")).format(Date(timestamp))

fun formatShortDate(timestamp: Long): String =
    SimpleDateFormat("d MMM", Locale("ru")).format(Date(timestamp))

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }
}

class TtsController(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    var speakingText by mutableStateOf<String?>(null)
        private set
    private var onDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        main.post {
                            speakingText = null
                            val done = onDoneCallback
                            onDoneCallback = null
                            done?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        main.post {
                            speakingText = null
                            val done = onDoneCallback
                            onDoneCallback = null
                            done?.invoke()
                        }
                    }
                })
            }
        }
    }

    fun toggle(text: String) {
        val engine = tts ?: return
        if (speakingText == text) {
            stop()
            return
        }
        speak(text)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null) {
            onDone?.invoke()
            return
        }
        engine.stop()
        onDoneCallback = onDone
        speakingText = text
        val cyrillic = text.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }
        runCatching {
            engine.language = if (cyrillic) Locale("ru", "RU") else Locale.getDefault()
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clauden-${text.hashCode()}")
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            speakingText = null
            onDoneCallback = null
            onDone?.invoke()
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        speakingText = null
        onDoneCallback = null
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}

@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}
