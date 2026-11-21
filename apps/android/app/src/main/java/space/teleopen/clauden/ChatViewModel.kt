package space.teleopen.clauden

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import space.teleopen.clauden.alarm.FocusScheduler
import space.teleopen.clauden.alarm.ScheduledScheduler
import space.teleopen.clauden.alarm.ClaudenReplyNotifications
import space.teleopen.clauden.alarm.ClaudenReplyService
import space.teleopen.clauden.data.AnthropicCatalog
import space.teleopen.clauden.data.ApiConfig
import space.teleopen.clauden.data.AppUpdateApi
import space.teleopen.clauden.data.AppUpdater
import space.teleopen.clauden.data.Attachment
import space.teleopen.clauden.data.AppSettings
import space.teleopen.clauden.data.AuthApi
import space.teleopen.clauden.data.ApiKeyInfo
import space.teleopen.clauden.data.AuthHttpException
import space.teleopen.clauden.data.AuthExpiredException
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.SshConnectionHolder
import space.teleopen.clauden.data.AuthSession
import space.teleopen.clauden.data.BanRisk
import space.teleopen.clauden.data.BillingPlan
import space.teleopen.clauden.data.ChatEngine
import space.teleopen.clauden.data.ChatEngineHandle
import space.teleopen.clauden.data.ClaudeApi
import space.teleopen.clauden.data.ClaudeApiException
import space.teleopen.clauden.data.ClaudeAnswer
import space.teleopen.clauden.data.ClaudeDiagnostics
import space.teleopen.clauden.data.ClaudeImportService
import space.teleopen.clauden.data.ClaudeImportProgress
import space.teleopen.clauden.data.ClaudeImportState
import space.teleopen.clauden.data.ClaudeCatalog
import space.teleopen.clauden.data.ClaudeEngine
import space.teleopen.clauden.data.ClaudeErrorKind
import space.teleopen.clauden.data.ClaudeExportImporter
import space.teleopen.clauden.data.ClaudeSession
import space.teleopen.clauden.data.ClaudeSessionStore
import space.teleopen.clauden.data.ClaudenEngine
import space.teleopen.clauden.data.EngineCallbacks
import space.teleopen.clauden.data.EngineRequest
import space.teleopen.clauden.data.ChatMessage
import space.teleopen.clauden.data.ChatAction
import space.teleopen.clauden.data.ChatBackup
import space.teleopen.clauden.data.CheckoutOrder
import space.teleopen.clauden.data.ChatFile
import space.teleopen.clauden.data.ChatStep
import space.teleopen.clauden.data.ChatSource
import space.teleopen.clauden.data.RemoteActiveTurn
import space.teleopen.clauden.data.ChatStore
import space.teleopen.clauden.data.ClaudenCatalog
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.CustomApi
import space.teleopen.clauden.data.CustomApiStore
import space.teleopen.clauden.data.CustomizeData
import space.teleopen.clauden.data.CustomizeImport
import space.teleopen.clauden.data.CustomizeSnapshot
import space.teleopen.clauden.data.CustomizeStore
import space.teleopen.clauden.data.DeviceActions
import space.teleopen.clauden.data.DeviceSession
import space.teleopen.clauden.data.Effort
import space.teleopen.clauden.data.GatewayApi
import space.teleopen.clauden.data.Haptics
import space.teleopen.clauden.data.ConnectorCatalogApi
import space.teleopen.clauden.data.ConnectorCatalogItem
import space.teleopen.clauden.data.ConnectorCatalogState
import space.teleopen.clauden.data.MarketApi
import space.teleopen.clauden.data.MarketItem
import space.teleopen.clauden.data.LocationProvider
import space.teleopen.clauden.data.LocationResult
import space.teleopen.clauden.data.MarketState
import space.teleopen.clauden.data.MemoryItem
import space.teleopen.clauden.data.MemoryStore
import space.teleopen.clauden.data.Prefs
import space.teleopen.clauden.data.Project
import space.teleopen.clauden.data.ProjectFile
import space.teleopen.clauden.data.ProjectFiles
import space.teleopen.clauden.data.ProjectStore
import space.teleopen.clauden.data.ProjectTextApi
import space.teleopen.clauden.data.Provider
import space.teleopen.clauden.data.RemoteModel
import space.teleopen.clauden.data.RemoteTurn
import space.teleopen.clauden.data.ScheduledStore
import space.teleopen.clauden.data.ScheduledTask
import space.teleopen.clauden.data.SettingsStore
import space.teleopen.clauden.data.Skill
import space.teleopen.clauden.data.Plugin
import space.teleopen.clauden.data.Connector
import space.teleopen.clauden.data.Marketplace
import space.teleopen.clauden.data.TaskItem
import space.teleopen.clauden.data.TaskStore
import space.teleopen.clauden.data.UpdateState
import space.teleopen.clauden.data.UsageSnapshot
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class Screen {
    Chat, Chats, Projects, Project, ProjectContext, Artifacts, Code, Customize,
    Settings,
    General, Account, Profile, ClaudeMode, Billing, Usage, ApiKeys, Capabilities, Memory, Reflect, TimeFocus,
    Sessions, Tasks, Scheduled, Connection, CustomApi,
    Permissions, Notifications, Privacy, Sharing, ThemeStudio;

    val isSettingsSection: Boolean
        get() = this == Settings || ordinal >= General.ordinal
}

private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60_000L

private const val FREE_PLAN_CHAT_BLOCKED =
    "На тарифе Free запросы через Clauden недоступны. Войдите через Claude или оформите подписку."

data class PendingLocation(
    val actionId: String,
    val chatId: String,
    val accuracy: String = "approximate",
    val reason: String = "",
    val needsPermission: Boolean = true,
    val resolving: Boolean = false,
    val denied: Boolean = false,
)

data class AuthState(
    val token: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val birthday: String = "",
    val provider: String = "",
    val organizationId: String = "",
    val onboarded: Boolean = false,
    val authMode: String = "",
    val claudeEmail: String = "",
    val claudePlan: String = "",

    val autoRenew: Boolean = false,
    val renewAt: Long = 0L,
    val renewProvider: String = "",
    val renewStatus: String = "",

    val codeAccess: Boolean = false,
    val hasPassword: Boolean = false,

    val canIssueKeys: Boolean = false,
) {
    val signedIn: Boolean get() = token.isNotBlank()
}

data class ClaudeProblem(
    val kind: ClaudeErrorKind,
    val message: String,
    val retryAt: Long = 0L,
)

data class ClaudeFileImportState(
    val busy: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val title: String = "",
    val chats: Int = 0,
    val projects: Int = 0,
    val memories: Int = 0,
    val artifacts: Int = 0,
    val files: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    val finished: Boolean = false,
    val warnings: List<String> = emptyList(),
)

data class ConnectorOAuthState(
    val id: String = "",
    val busy: Boolean = false,
    val authUrl: String = "",
    val attemptId: String = "",
    val redirectUri: String = "",
    val error: String = "",
)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val projects: List<Project> = emptyList(),

    val activeProjectId: String = "",

    val draftProjectId: String = "",

    val projectBusy: Boolean = false,
    val activeId: String? = null,
    val streaming: Boolean = false,
    val provider: Provider = Provider.Clauden,
    val model: String = "",
    val models: List<RemoteModel> = ClaudenCatalog.fallbackModels,
    val loadingModels: Boolean = false,
    val baseUrl: String = ClaudenCatalog.baseUrl,

    val claudenBaseUrl: String = ClaudenCatalog.baseUrl,
    val customBaseUrl: String = "",
    val apiKey: String = "",

    val customApis: List<CustomApi> = emptyList(),
    val customApiId: String = "",

    val customApiBusy: Boolean = false,
    val customApiModels: List<RemoteModel> = emptyList(),
    val customApiError: String? = null,
    val effort: Effort = Effort.Medium,
    val screen: Screen = Screen.Chat,
    val error: String? = null,
    val notice: String? = null,

    val recoveringId: String? = null,
    val settings: AppSettings = AppSettings(),
    val auth: AuthState = AuthState(),
    val authBusy: Boolean = false,
    val authError: String? = null,

    val apiKeys: List<ApiKeyInfo> = emptyList(),
    val apiKeysCanIssue: Boolean = false,
    val apiKeysBusy: Boolean = false,
    val apiKeysError: String? = null,
    val apiKeyLimit: Int = 0,
    val apiKeyFresh: String = "",

    val authMode: AuthMode = AuthMode.Clauden,

    val claudeLinked: Boolean = false,
    val claudeEmail: String = "",
    val claudePlan: String = "",

    val claudeSwitched: Boolean = false,

    val claudeProblem: ClaudeProblem? = null,

    val banRiskBusy: Boolean = false,
    val banRisk: BanRisk? = null,
    val banRiskError: String? = null,

    val claudeLoginOpen: Boolean = false,

    val claudeLinkPassword: Boolean = false,
    val claudeLinkEmail: String = "",
    val claudeLinkError: String? = null,

    val claudeImportBusy: Boolean = false,
    val claudeImportDone: Int = 0,
    val claudeImportTotal: Int = 0,
    val claudeImportTitle: String = "",
    val claudeImportFiles: Int = 0,
    val claudeImportError: String? = null,
    val claudeImportFinished: Boolean = false,

    val claudeFileImport: ClaudeFileImportState = ClaudeFileImportState(),

    val authCodeBusy: Boolean = false,

    val authCodeSentTo: String = "",

    val billingPlans: List<BillingPlan> = ClaudenCatalog.fallbackBillingPlans,
    val checkoutBusy: Boolean = false,
    val billingCancelBusy: Boolean = false,
    val checkoutOrder: CheckoutOrder? = null,
    val checkoutError: String? = null,

    val checkoutNeedsPassword: Boolean = false,

    val usage: UsageSnapshot? = null,
    val usageBusy: Boolean = false,
    val usageError: String? = null,

    val planThanks: String? = null,

    val voiceMode: Boolean = false,

    val customize: CustomizeData = CustomizeData(),
    val customizeTab: String = "skills",

    val connectorCheckId: String = "",
    val connectorCheckBusy: Boolean = false,
    val connectorCheckStatus: String = "",
    val connectorCheckError: String = "",

    val connectorOAuth: ConnectorOAuthState = ConnectorOAuthState(),

    val memory: List<MemoryItem> = emptyList(),
    val memoryBusy: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val scheduled: List<ScheduledTask> = emptyList(),

    val sessions: List<DeviceSession> = emptyList(),
    val sessionsBusy: Boolean = false,

    val searchOpen: Boolean = false,

    val market: MarketState = MarketState(),

    val connectorCatalog: ConnectorCatalogState = ConnectorCatalogState(),

    val pendingLocation: PendingLocation? = null,

    val openChatId: String = "",

    val remoteToken: String = "",

    val update: UpdateState = UpdateState(),
) {
    val organizationId: String
        get() = auth.organizationId.ifBlank {
            if (auth.userId.isBlank()) "" else
                UUID.nameUUIDFromBytes(("clauden-org:" + auth.userId).toByteArray()).toString()
        }

    val activeSkills: List<Skill> get() = customize.enabledSkillList
    val activePlugins: List<Plugin> get() = customize.enabledPluginList
    val openTasks: Int get() = tasks.count { !it.done }
    val active: Conversation? get() = conversations.firstOrNull { it.id == activeId }
    val activeProject: Project? get() = projects.firstOrNull { it.id == activeProjectId }
    val messages: List<ChatMessage> get() = active?.messages.orEmpty().filterNot { it.hidden }
    val canRetry: Boolean
        get() = !streaming &&
            (recoveringId == null || recoveringId != activeId) &&
            messages.any { it.role == "user" }
    val pinned: List<Conversation> get() = conversations.filter { it.pinned }
    val recent: List<Conversation> get() = conversations.filterNot { it.pinned }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val settingsStore = SettingsStore(app)
    private val authApi = AuthApi()
    private val claudeDiagnostics = ClaudeDiagnostics(app)
    private val claudeApi = ClaudeApi(claudeDiagnostics)
    private val claudeStore = ClaudeSessionStore(app)
    private val customApiStore = CustomApiStore(app)
    private val initialCustomApis = customApiStore.list()
    private var claudeSession: ClaudeSession? = claudeStore.load()

    private var pendingClaudeSession: ClaudeSession? = null
    private var accountKey: String = prefs.authUserId
    private var store = ChatStore(app, accountKey)
    private var projectStore = ProjectStore(app, accountKey)
    private var customizeStore = CustomizeStore(app, accountKey)
    private var memoryStore = MemoryStore(app, accountKey)
    private var memoryAutoAttempted = false

    private var appForeground = true
    private var taskStore = TaskStore(app, accountKey)
    private var scheduledStore = ScheduledStore(app, accountKey)
    private val api = GatewayApi()
    private val connectorCatalogApi = ConnectorCatalogApi()
    private val claudenEngine: ChatEngine = ClaudenEngine(api)
    private val claudeEngine: ChatEngine = ClaudeEngine(claudeApi)
    private val marketApi = MarketApi()
    private val updateApi = AppUpdateApi()
    private val updater = AppUpdater(app)
    private val projectTextApi = ProjectTextApi()
    private var marketRequest = 0
    private var catalogRequest = 0
    private var chatUploadJob: Job? = null
    private var projectUploadJob: Job? = null
    private var customizeUploadJob: Job? = null
    private val uploadedChats = mutableMapOf<String, Long>()
    private val uploadedProjects = mutableMapOf<String, Long>()
    private var customizeUploadedAt = 0L
    private var syncingAccount = false
    private var pendingCheckoutPlan = ""
    private var pendingCheckoutMethod = ""
    private var pendingCheckoutAutoRenew = true

    private val initialAuth = AuthState(
        token = prefs.authToken,
        userId = prefs.authUserId,
        email = prefs.authEmail,
        name = prefs.authName,
        birthday = prefs.authBirthday,
        provider = prefs.authProvider,
        onboarded = prefs.authOnboarded,
        codeAccess = prefs.authCodeAccess,
        canIssueKeys = prefs.authCanIssueKeys,
    )

    private val initialPlan = settingsStore.load().plan
    private val initialMode: AuthMode = when {
        prefs.authMode == AuthMode.Claude -> AuthMode.Claude
        (prefs.authClaudeLinked || prefs.authProvider.equals("claude", true)) &&
            isFreePlan(initialPlan) -> AuthMode.Claude
        else -> AuthMode.Clauden
    }

    private val initialCustom = initialCustomApis.firstOrNull { it.id == prefs.customApiId }
        ?: initialCustomApis.firstOrNull().takeIf { prefs.provider == Provider.Custom }
    private val initialProvider = if (initialAuth.signedIn) {

        if (initialCustom != null) Provider.Custom else Provider.Clauden
    } else {
        prefs.provider.let { stored ->

            if (stored == Provider.Anthropic && prefs.apiKey.isBlank() &&
                ClaudenCatalog.apiKey.isNotBlank()
            ) {
                prefs.provider = Provider.Clauden
                Provider.Clauden
            } else {
                stored
            }
        }
    }
    private val initialModels = when {
        initialMode == AuthMode.Claude -> ClaudeCatalog.fallbackModels
        initialProvider == Provider.Anthropic -> AnthropicCatalog.fallbackModels
        initialProvider == Provider.Clauden -> ClaudenCatalog.fallbackModels
        initialProvider == Provider.Custom -> initialCustom?.models.orEmpty()
        else -> emptyList()
    }
    private val storedModel = prefs.model
    private val initialModel: String = when {
        initialMode == AuthMode.Claude ->
            storedModel.takeIf { it.startsWith("claude-") }
                ?: ClaudeCatalog.preferredModel(initialModels)

        initialProvider == Provider.Custom ->
            storedModel.takeIf { id -> initialModels.any { it.id == id } }
                ?: initialModels.firstOrNull()?.id.orEmpty()

        storedModel.isBlank() -> AnthropicCatalog.preferredModel(initialModels)
        initialProvider == Provider.Anthropic && !storedModel.contains("claude", ignoreCase = true) ->
            AnthropicCatalog.preferredModel(initialModels)
        initialProvider == Provider.Clauden && !storedModel.startsWith("claude") -> "claude-opus-5-5"
        initialProvider != Provider.Custom -> AnthropicCatalog.upgradeModel(storedModel)
        else -> storedModel
    }

    init {
        if (initialModel != storedModel && initialModel.isNotBlank()) prefs.model = initialModel
    }

    private val _state = MutableStateFlow(
        ChatUiState(
            provider = initialProvider,
            baseUrl = if (initialProvider == Provider.Custom) {
                initialCustom?.baseUrl ?: prefs.baseUrl
            } else {
                prefs.effectiveBaseUrl(initialProvider)
            },
            claudenBaseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
            customBaseUrl = prefs.baseUrl,
            apiKey = when {
                initialProvider == Provider.Custom -> initialCustom?.apiKey ?: prefs.apiKey
                initialAuth.signedIn -> initialAuth.token
                else -> prefs.effectiveKey(initialProvider)
            },
            model = initialModel,
            models = initialModels,
            customApis = initialCustomApis,
            customApiId = initialCustom?.id.orEmpty(),
            effort = prefs.effort,
            settings = settingsStore.load(),
            auth = initialAuth,
            authMode = initialMode,
            claudeLinked = claudeSession?.isUsable == true,
            claudeEmail = claudeSession?.email.orEmpty(),
            claudePlan = claudeSession?.plan.orEmpty(),
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var engineCall: ChatEngineHandle? = null
    private var settingsReturnScreen: Screen = Screen.Chat
    private var customizeReturnScreen: Screen = Screen.Chat
    private var lastCalendarContext: String = ""
    private var celebratedPlan: String? = null

    private class StreamRun(val chatId: String) {
        val target = StringBuilder()
        var shown = 0
        var done = false
        var cancelled = false
        var onFinished: (() -> Unit)? = null
    }

    private var activeRun: StreamRun? = null
    private var revealJob: Job? = null
    private var recoveryJob: Job? = null
    private val persistMutex = Mutex()

    init {
        ClaudeImportState.load(app)
        viewModelScope.launch {
            var wasBusy = false
            ClaudeImportState.progress.collect { progress ->
                val finished = wasBusy && !progress.busy
                wasBusy = progress.busy
                _state.update {
                    it.copy(
                        claudeImportBusy = progress.busy,
                        claudeImportDone = progress.done,
                        claudeImportTotal = progress.total,
                        claudeImportTitle = progress.title,
                        claudeImportFiles = progress.files,
                        claudeImportError = progress.error,
                        claudeImportFinished = progress.finished,
                    )
                }
                if (finished) {

                    if (_state.value.auth.signedIn) loadAccount()
                }
            }
        }
        if (ClaudeImportState.progress.value.busy) {
            runCatching { ClaudeImportService.start(app) }
        }
    }

    init {
        viewModelScope.launch {
            if (!_state.value.auth.signedIn) return@launch

            if (claudeSession?.isUsable == true) refreshClaudeSession()
            if (_state.value.authMode == AuthMode.Claude) {
                refreshModels(silent = true)
            }
            loadAccount()
            validateSession()
            refreshSessions()
        }
    }

    init {
        checkForUpdate(force = true)
    }

    fun checkForUpdate(force: Boolean = false, userInitiated: Boolean = false) {
        val snapshot = _state.value.update
        if (snapshot.checking || snapshot.downloading) return
        val now = System.currentTimeMillis()
        if (!force && now - snapshot.lastCheckedAt < UPDATE_CHECK_INTERVAL_MS) return
        _state.update {
            it.copy(update = it.update.copy(checking = true, lastCheckedAt = now, error = null))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { updateApi.check(BuildConfig.VERSION_CODE) }
            }
            result.onSuccess { release ->
                _state.update { st ->
                    val previous = st.update
                    val keepFile = previous.latest?.versionCode == release?.versionCode
                    st.copy(
                        update = previous.copy(
                            checking = false,
                            latest = release,
                            visible = release != null &&
                                (userInitiated || release.versionCode != previous.dismissedCode),
                            downloadedPath = if (keepFile) previous.downloadedPath else "",
                            progress = if (keepFile) previous.progress else 0,
                            error = null,
                        ),
                    )
                }
                if (release == null && userInitiated) {
                    _state.update { it.copy(notice = "У вас последняя версия Clauden") }
                }
            }.onFailure {
                _state.update { it.copy(update = it.update.copy(checking = false)) }
                if (userInitiated) {
                    _state.update { it.copy(notice = "Не удалось проверить обновления") }
                }
            }
        }
    }

    fun dismissUpdate() {
        if (_state.value.update.downloading) return
        _state.update {
            it.copy(
                update = it.update.copy(
                    visible = false,
                    dismissedCode = it.update.latest?.versionCode ?: it.update.dismissedCode,
                ),
            )
        }
    }

    fun refreshDownloadPermission() {
        val update = _state.value.update
        if (update.error == null) return
        if (updater.canInstall()) {
            _state.update { it.copy(update = it.update.copy(error = null)) }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.update.latest ?: return
        if (_state.value.update.downloading) return
        _state.update {
            it.copy(
                update = it.update.copy(
                    downloading = true,
                    progress = 0,
                    downloadedPath = "",
                    error = null,
                ),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updater.download(release) { percent ->
                        _state.update { st ->
                            st.copy(update = st.update.copy(progress = percent))
                        }
                    }
                }
            }
            result.onSuccess { file ->
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            downloading = false,
                            progress = 100,
                            downloadedPath = file.absolutePath,
                            error = null,
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        update = it.update.copy(
                            downloading = false,
                            progress = 0,
                            error = error.message ?: "Не удалось скачать обновление",
                        ),
                    )
                }
            }
        }
    }

    fun startUpdate() {
        val update = _state.value.update
        if (update.downloading) return
        if (!update.downloaded) {
            downloadUpdate()
            return
        }
        val file = File(update.downloadedPath)
        if (!file.exists()) {
            downloadUpdate()
            return
        }
        if (!updater.canInstall()) {
            val opened = updater.openInstallSettings()
            _state.update {
                it.copy(
                    update = it.update.copy(
                        error = if (opened) {
                            "Разрешите установку из Clauden и вернитесь — нажмите «Установить» ещё раз"
                        } else {
                            "Разрешите установку приложений из неизвестных источников"
                        },
                    ),
                )
            }
            return
        }
        if (!updater.install(file)) {
            _state.update {
                it.copy(update = it.update.copy(error = "Не удалось открыть установщик"))
            }
        }
    }

    private var billingPlansLoadedAt = 0L

    private var sessionValidatedAt = 0L

    private suspend fun loadBillingPlans(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && billingPlansLoadedAt > 0 && now - billingPlansLoadedAt < 60_000L) return
        val plans = withContext(Dispatchers.IO) { runCatching { authApi.plans() }.getOrNull() }
        if (!plans.isNullOrEmpty()) {
            billingPlansLoadedAt = now
            _state.update { it.copy(billingPlans = plans.sortedBy { plan -> plan.order }) }
        }
    }

    fun openApiKeys() {
        _state.update { it.copy(screen = Screen.ApiKeys, apiKeysError = null) }
        loadApiKeys(force = true)
    }

    fun loadApiKeys(force: Boolean = false) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        if (_state.value.apiKeysBusy) return
        if (!force && _state.value.apiKeys.isNotEmpty()) return
        _state.update { it.copy(apiKeysBusy = true, apiKeysError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { authApi.listApiKeys(auth.token) } }
            result.onSuccess { payload ->
                _state.update {
                    it.copy(
                        apiKeysBusy = false,
                        apiKeys = payload.data,
                        apiKeysCanIssue = payload.canIssue,
                        apiKeyLimit = payload.limit,
                    )
                }
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        apiKeysBusy = false,
                        apiKeysError = error.message ?: "Не удалось загрузить API-ключи",
                    )
                }
            }
        }
    }

    fun createApiKey() {
        if (_state.value.apiKeysBusy) return
        val auth = _state.value.auth
        if (!auth.signedIn) return
        _state.update { it.copy(apiKeysBusy = true, apiKeysError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { authApi.createApiKey(auth.token) } }
            result.onSuccess { created ->
                _state.update { it.copy(apiKeysBusy = false, apiKeyFresh = created.key) }
                loadApiKeys(force = true)
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        apiKeysBusy = false,
                        apiKeysError = error.message ?: "Не удалось выпустить ключ",
                    )
                }
            }
        }
    }

    fun resetApiKey() {
        if (_state.value.apiKeysBusy) return
        val auth = _state.value.auth
        if (!auth.signedIn) return
        _state.update { it.copy(apiKeysBusy = true, apiKeysError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { authApi.resetApiKey(auth.token) } }
            result.onSuccess { created ->
                _state.update { it.copy(apiKeysBusy = false, apiKeyFresh = created.key) }
                loadApiKeys(force = true)
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        apiKeysBusy = false,
                        apiKeysError = error.message ?: "Не удалось сбросить ключ",
                    )
                }
            }
        }
    }

    fun revokeApiKey(id: String) {
        val auth = _state.value.auth
        if (!auth.signedIn || id.isBlank()) return
        _state.update { it.copy(apiKeysBusy = true, apiKeysError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { authApi.revokeApiKey(auth.token, id) } }
            result.onSuccess {
                _state.update { it.copy(apiKeysBusy = false) }
                loadApiKeys(force = true)
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        apiKeysBusy = false,
                        apiKeysError = error.message ?: "Не удалось отозвать ключ",
                    )
                }
            }
        }
    }

    fun clearFreshApiKey() {
        _state.update { it.copy(apiKeyFresh = "") }
    }

    fun loadUsage(force: Boolean = false) {
        val auth = _state.value.auth
        if (!auth.signedIn) return

        if (_state.value.authMode == AuthMode.Claude) {
            _state.update { it.copy(usage = null, usageBusy = false, usageError = null) }
            return
        }
        if (_state.value.usageBusy) return
        if (!force && _state.value.usage != null) return
        _state.update { it.copy(usageBusy = true, usageError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.usage(auth.token) }
            }
            result.onSuccess { usage ->
                _state.update { it.copy(usageBusy = false, usage = usage) }
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        usageBusy = false,
                        usageError = error.message ?: "Не удалось загрузить лимиты",
                    )
                }
            }
        }
    }

    private suspend fun validateSession() {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        val result = withContext(Dispatchers.IO) { runCatching { authApi.me(auth.token) } }
        result.onSuccess { user ->
            sessionValidatedAt = SystemClock.elapsedRealtime()
            val previousPlan = _state.value.settings.plan
            val newPlan = user.plan.ifBlank { "Free" }
            val planChanged = newPlan != previousPlan
            if (user.onboarded != auth.onboarded || user.name != auth.name ||
                user.birthday != auth.birthday || user.email != auth.email ||
                user.nickname != _state.value.settings.nickname ||
                user.work != _state.value.settings.work ||
                user.instructions != _state.value.settings.preferenceNote ||
                planChanged ||
                user.autoRenew != auth.autoRenew ||
                user.codeAccess != auth.codeAccess ||
                user.canIssueKeys != auth.canIssueKeys ||
                user.planUpdatedAt > prefs.lastCelebratedPlanAt
            ) {
                prefs.authOnboarded = user.onboarded
                prefs.authName = user.name
                prefs.authBirthday = user.birthday
                prefs.authEmail = user.email
                val settings = _state.value.settings.copy(
                    email = user.email,
                    fullName = user.name.ifBlank { _state.value.settings.fullName },
                    nickname = user.nickname.ifBlank {
                        user.name.ifBlank { _state.value.settings.nickname }
                    },
                    work = user.work.ifBlank { _state.value.settings.work },
                    preferenceNote = user.instructions.ifBlank {
                        _state.value.settings.preferenceNote
                    },
                    plan = newPlan,
                )
                settingsStore.save(settings)
                prefs.authCodeAccess = user.codeAccess
                prefs.authCanIssueKeys = user.canIssueKeys
                _state.update {
                    it.copy(
                        auth = it.auth.copy(
                            email = user.email,
                            name = user.name,
                            birthday = user.birthday,
                            organizationId = user.organizationId,
                            onboarded = user.onboarded,
                            authMode = user.authMode,
                            claudeEmail = user.claudeEmail,
                            claudePlan = user.claudePlan,
                            autoRenew = user.autoRenew,
                            renewAt = user.renewAt,
                            renewProvider = user.renewProvider,
                            renewStatus = user.renewStatus,
                            codeAccess = user.codeAccess,
                            hasPassword = user.hasPassword,
                            canIssueKeys = user.canIssueKeys,
                        ),
                        settings = settings,
                        usage = if (planChanged) null else it.usage,
                        usageError = if (planChanged) null else it.usageError,
                    )
                }
                val paid = !newPlan.isBlank() && !newPlan.equals("Free", true)
                if (planChanged && paid && _state.value.authMode == AuthMode.Claude) {

                    switchEngine(AuthMode.Clauden, auto = true)
                } else {
                    if (planChanged) {
                        celebratePlan(user.plan, user.planUpdatedAt)
                    } else {
                        maybeCelebrateRecentPlan(user.plan, user.planUpdatedAt)
                    }
                }
            }

            val freePlan = newPlan.isBlank() || newPlan.equals("Free", true)
            prefs.authClaudeLinked = user.authMode == "claude" || user.claudeEmail.isNotBlank()
            if (freePlan && user.authMode == "claude" &&
                _state.value.authMode != AuthMode.Claude
            ) {
                switchEngine(AuthMode.Claude, auto = false)
                _state.update {
                    it.copy(notice = "Аккаунт Claude привязан — включён режим Claude")
                }
            }
        }.onFailure { error ->
            if (error is AuthExpiredException) logout()
        }
    }

    private suspend fun loadAccount() {
        memoryAutoAttempted = false
        val deletedIds = prefs.deletedChatIds
        val storedChats = store.load()
        val loaded = storedChats
            .filterNot { it.id in deletedIds }
            .sortedByDescending { it.updatedAt }
        if (loaded.size != storedChats.size) {
            withContext(Dispatchers.IO) { store.save(loaded) }
        }
        val projects = projectStore.load().sortedByDescending { it.updatedAt }
        val customize = withContext(Dispatchers.IO) { customizeStore.load() }
        val memory = withContext(Dispatchers.IO) { memoryStore.load() }
        val tasks = withContext(Dispatchers.IO) { taskStore.load() }
        val scheduled = withContext(Dispatchers.IO) { scheduledStore.load() }
        _state.update {
            it.copy(
                conversations = loaded,
                projects = projects,
                customize = customize,
                memory = memory,
                tasks = tasks,
                scheduled = scheduled,
                activeId = null,
                activeProjectId = "",
                draftProjectId = "",
                streaming = false,
                recoveringId = null,
            )
        }
        maybeAutoGenerateMemory()
        val pending = _state.value.openChatId
        if (pending.isNotBlank() && loaded.any { it.id == pending }) {
            _state.update { it.copy(openChatId = "") }
            openChat(pending)
            if (_state.value.apiKey.isNotBlank()) refreshModels(silent = true)
            return
        }
        val last = prefs.lastConversationId?.takeIf { id -> loaded.any { it.id == id } }
        if (last != null) {
            _state.update { it.copy(activeId = last) }
            recoverTurn(last)
        } else if (loaded.isNotEmpty()) {
            _state.update { it.copy(activeId = loaded.first().id) }
            recoverTurn(loaded.first().id)
        } else {
            newChat()
        }
        if (_state.value.apiKey.isNotBlank()) refreshModels(silent = true)
        scheduled.filter { it.runAt > System.currentTimeMillis() }
            .forEach { ScheduledScheduler.schedule(getApplication(), it) }
        syncAccountFromServer()
    }

    fun syncAccountFromServer(notify: Boolean = false) {
        val auth = _state.value.auth
        if (!auth.signedIn || syncingAccount || _state.value.streaming) return
        syncingAccount = true
        viewModelScope.launch {
            try {
                val remoteChats = withContext(Dispatchers.IO) {
                    runCatching { authApi.fetchChats(auth.token) }.getOrNull()
                }
                val remoteProjects = withContext(Dispatchers.IO) {
                    runCatching { authApi.fetchProjects(auth.token) }.getOrNull()
                }
                val remoteCustomize = withContext(Dispatchers.IO) {
                    runCatching { authApi.fetchCustomize(auth.token) }.getOrNull()
                }
                remoteChats?.let { retryDeletedChats(auth.token, it) }
                val added = remoteChats?.let { mergeRemoteChats(it) } ?: 0
                remoteProjects?.let { mergeRemoteProjects(it) }
                remoteCustomize?.let { mergeRemoteCustomize(it) }
                if (notify) {
                    _state.update {
                        it.copy(
                            notice = if (added > 0) {
                                "Загружено чатов: $added"
                            } else {
                                "История уже синхронизирована"
                            },
                        )
                    }
                }
                scheduleChatUpload(_state.value.conversations)
                scheduleProjectUpload(_state.value.projects)
                scheduleCustomizeUpload(_state.value.customize)
            } finally {
                syncingAccount = false
            }
        }
    }

    private suspend fun mergeRemoteCustomize(remote: CustomizeSnapshot) {
        val remoteData = remote.data
        if (remoteData == null) {

            val local = withContext(Dispatchers.IO) { customizeStore.load() }
            val value = local.copy(
                updatedAt = if (local.updatedAt > 0L) local.updatedAt else System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) { customizeStore.save(value) }
            _state.update { it.copy(customize = value) }
            scheduleCustomizeUpload(value)
            return
        }
        val local = withContext(Dispatchers.IO) { customizeStore.load() }
        val remoteAt = maxOf(remote.updatedAt, remoteData.updatedAt)
        val localAt = local.updatedAt
        val merged = when {
            remoteAt == 0L && localAt == 0L -> unionCustomize(local, remoteData)
            remoteAt > localAt -> remoteData
            localAt > remoteAt -> local
            else -> remoteData
        }
        if (merged == local) {
            scheduleCustomizeUpload(local)
            return
        }
        val value = if (remoteAt == 0L && localAt == 0L) {

            merged.copy(updatedAt = System.currentTimeMillis())
        } else {
            merged
        }
        withContext(Dispatchers.IO) { customizeStore.save(value) }
        customizeUploadedAt = maxOf(customizeUploadedAt, remoteAt)
        _state.update { it.copy(customize = value) }
        if (localAt >= remoteAt) scheduleCustomizeUpload(value)
    }

    private fun unionCustomize(local: CustomizeData, remote: CustomizeData): CustomizeData {
        fun <T> mergeById(a: List<T>, b: List<T>, id: (T) -> String): List<T> {
            val map = LinkedHashMap<String, T>()
            a.forEach { map[id(it)] = it }
            b.forEach { item -> map[id(item)] = item }
            return map.values.toList()
        }
        return local.copy(
            installedSkills = (local.installedSkills + remote.installedSkills).distinct(),
            enabledSkills = (local.enabledSkills + remote.enabledSkills).distinct(),
            customSkills = mergeById(local.customSkills, remote.customSkills) { it.id },
            installedPlugins = (local.installedPlugins + remote.installedPlugins).distinct(),
            enabledPlugins = (local.enabledPlugins + remote.enabledPlugins).distinct(),
            customPlugins = mergeById(local.customPlugins, remote.customPlugins) { it.id },
            installedConnectors = (local.installedConnectors + remote.installedConnectors).distinct(),
            customConnectors = mergeById(local.customConnectors, remote.customConnectors) { it.id },
            marketplaces = mergeById(local.marketplaces, remote.marketplaces) { it.id },
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
        )
    }

    private suspend fun retryDeletedChats(token: String, remote: ChatBackup) {
        val deleted = prefs.deletedChatIds
        val ids = remote.data
            .asSequence()
            .map { it.id }
            .filter { it in deleted }
            .take(50)
            .toList()
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            ids.forEach { id -> runCatching { authApi.deleteChat(token, id) } }
        }
    }

    private suspend fun mergeRemoteChats(remote: ChatBackup): Int {

        val deletedIds = prefs.deletedChatIds.toMutableSet()
        for (item in remote.deleted) {
            if (item.id.isNotBlank()) deletedIds.add(item.id)
        }
        if (deletedIds != prefs.deletedChatIds) prefs.deletedChatIds = deletedIds

        val allLocal = withContext(Dispatchers.IO) { store.load() }
        val local = allLocal
            .filterNot { it.id in deletedIds }
            .associateBy { it.id }
            .toMutableMap()
        var added = 0
        var changed = local.size != allLocal.size
        for (chat in remote.data) {
            if (chat.id in deletedIds) continue
            val existing = local[chat.id]
            if (existing == null) added += 1
            if (existing == null || chat.updatedAt > existing.updatedAt) {
                local[chat.id] = chat
                uploadedChats[chat.id] = chat.updatedAt
                changed = true
            }
        }
        if (!changed) return 0
        val merged = local.values.sortedByDescending { it.updatedAt }
        withContext(Dispatchers.IO) { store.save(merged) }
        if (_state.value.streaming) return added
        _state.update { state ->
            val current = state.activeId
            val currentChat = merged.firstOrNull { it.id == current }
            val currentEmpty = currentChat == null || currentChat.messages.isEmpty()
            val nextActive = if (currentEmpty) {
                merged.firstOrNull { it.messages.isNotEmpty() }?.id ?: current
            } else {
                current
            }
            state.copy(conversations = merged, activeId = nextActive)
        }
        return added
    }

    private suspend fun mergeRemoteProjects(remote: List<Project>) {
        if (remote.isEmpty()) return
        val local = withContext(Dispatchers.IO) { projectStore.load() }
            .associateBy { it.id }
            .toMutableMap()
        var changed = false
        for (project in remote) {
            val existing = local[project.id]
            if (existing == null || project.updatedAt > existing.updatedAt) {
                local[project.id] = project
                uploadedProjects[project.id] = project.updatedAt
                changed = true
            }
        }
        if (!changed) return
        val merged = local.values.sortedByDescending { it.updatedAt }
        withContext(Dispatchers.IO) { projectStore.save(merged) }
        _state.update { it.copy(projects = merged) }
    }

    private fun scheduleChatUpload(conversations: List<Conversation>) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        val deletedIds = prefs.deletedChatIds
        val pending = conversations
            .filter { it.id !in deletedIds }
            .filter { it.messages.isNotEmpty() && uploadedChats[it.id] != it.updatedAt }
            .sortedByDescending { it.updatedAt }
            .take(200)
        if (pending.isEmpty()) return
        val batches = mutableListOf<MutableList<Conversation>>()
        var batch = mutableListOf<Conversation>()
        var batchBytes = 0L
        for (chat in pending) {
            val bytes = estimateChatBytes(chat)

            if (bytes > 4_000_000L) continue
            if (batch.isNotEmpty() && (batch.size >= 10 || batchBytes + bytes > 4_000_000L)) {
                batches += batch
                batch = mutableListOf()
                batchBytes = 0L
            }
            batch += chat
            batchBytes += bytes
        }
        if (batch.isNotEmpty()) batches += batch
        if (batches.isEmpty()) return
        chatUploadJob?.cancel()
        chatUploadJob = viewModelScope.launch {
            delay(2500)
            for (part in batches) {
                val snapshot = part.map { it.forBackup() }
                val ok = withContext(Dispatchers.IO) {
                    runCatching { authApi.backupChats(auth.token, snapshot) }.isSuccess
                }
                if (ok) part.forEach { uploadedChats[it.id] = it.updatedAt }
            }
        }
    }

    private fun estimateChatBytes(chat: Conversation): Long {
        var size = (chat.title.length + 64).toLong()
        for (message in chat.messages) {
            size += (message.content.length + message.thinking.length + 128).toLong()
        }
        return size * 2
    }

    private fun scheduleProjectUpload(projects: List<Project>) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        val pending = projects
            .filter { uploadedProjects[it.id] != it.updatedAt }
            .sortedByDescending { it.updatedAt }
            .take(100)
        if (pending.isEmpty()) return
        val batches = pending.chunked(10)
        projectUploadJob?.cancel()
        projectUploadJob = viewModelScope.launch {
            delay(2500)
            for (part in batches) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { authApi.backupProjects(auth.token, part) }.isSuccess
                }
                if (ok) part.forEach { uploadedProjects[it.id] = it.updatedAt }
            }
        }
    }

    private fun scheduleCustomizeUpload(data: CustomizeData) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        if (data.updatedAt <= customizeUploadedAt) return
        customizeUploadJob?.cancel()
        customizeUploadJob = viewModelScope.launch {
            delay(1800)
            val snapshot = _state.value.customize
            if (snapshot.updatedAt <= customizeUploadedAt) return@launch
            val saved = withContext(Dispatchers.IO) {
                runCatching { authApi.backupCustomize(auth.token, snapshot) }.getOrNull()
            }
            if (saved != null) {
                customizeUploadedAt = maxOf(customizeUploadedAt, saved.updatedAt, snapshot.updatedAt)
            }
        }
    }

    private fun Conversation.forBackup(): Conversation = copy(
        messages = messages.map { message ->
            if (message.attachments.isEmpty() && message.files.none { it.localPath.isNotBlank() }) {
                message
            } else {
                message.copy(
                    attachments = message.attachments.map { it.copy(base64 = "") },
                    files = message.files.map { it.copy(localPath = "", text = "") },
                )
            }
        },
    )

    fun openSettings() {
        val current = _state.value.screen
        if (!current.isSettingsSection) settingsReturnScreen = current
        _state.update { it.copy(screen = Screen.Settings) }
    }
    fun closeSettings() = _state.update { it.copy(screen = settingsReturnScreen) }

    fun openSettingsSection(screen: Screen) {
        if (!screen.isSettingsSection) return
        _state.update { it.copy(screen = screen) }
        if (screen == Screen.Billing) {
            viewModelScope.launch {
                loadBillingPlans(force = true)
                validateSession()
            }
        }
        if (screen == Screen.Usage) {
            loadUsage(force = true)
        }
    }

    fun backToSettings() = _state.update { it.copy(screen = Screen.Settings) }

    fun showNotice(text: String) {
        _state.update { it.copy(notice = text) }
    }

    fun startCheckout(planId: String, method: String, autoRenew: Boolean = true) {
        val auth = _state.value.auth
        if (!auth.signedIn) {
            showNotice("Войдите в аккаунт")
            return
        }

        if (_state.value.authMode == AuthMode.Claude && !auth.hasPassword) {
            pendingCheckoutPlan = planId
            pendingCheckoutMethod = method
            pendingCheckoutAutoRenew = autoRenew
            _state.update {
                it.copy(checkoutNeedsPassword = true, checkoutError = null)
            }
            return
        }
        if (_state.value.checkoutBusy) return
        _state.update {
            it.copy(checkoutBusy = true, checkoutError = null, checkoutOrder = null)
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.checkout(auth.token, planId, method, autoRenew) }
            }
            result.onSuccess { order ->
                _state.update { it.copy(checkoutBusy = false, checkoutOrder = order) }
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        checkoutBusy = false,
                        checkoutError = error.message ?: "Не удалось создать счёт",
                    )
                }
            }
        }
    }

    fun refreshCheckout() {
        val order = _state.value.checkoutOrder ?: return
        val auth = _state.value.auth
        if (!auth.signedIn) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.checkoutOrder(auth.token, order.id) }
            }
            result.onSuccess { fresh ->
                _state.update { it.copy(checkoutOrder = fresh) }
                if (fresh.status == "paid") {
                    validateSession()
                    if (_state.value.auth.signedIn && !_state.value.claudeSwitched) {
                        celebratePlan(fresh.plan)
                    }
                    _state.update {
                        it.copy(checkoutOrder = null, checkoutError = null)
                    }
                }
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(checkoutError = error.message ?: "Не удалось проверить оплату")
                }
            }
        }
    }

    fun clearCheckout() {
        _state.update {
            it.copy(checkoutBusy = false, checkoutOrder = null, checkoutError = null)
        }
    }

    fun cancelRenewal() {
        val auth = _state.value.auth
        if (!auth.signedIn || !auth.autoRenew) return
        viewModelScope.launch {
            _state.update { it.copy(billingCancelBusy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.cancelRenewal(auth.token) }
            }
            result.onSuccess { user ->
                _state.update {
                    it.copy(
                        billingCancelBusy = false,
                        auth = it.auth.copy(
                            autoRenew = user.autoRenew,
                            renewAt = user.renewAt,
                            renewProvider = user.renewProvider,
                            renewStatus = user.renewStatus,
                        ),
                        notice = "Автопродление отменено. Тариф работает до конца оплаченного срока.",
                    )
                }
            }.onFailure { error ->
                if (error is AuthExpiredException) logout()
                _state.update {
                    it.copy(
                        billingCancelBusy = false,
                        notice = error.message ?: "Не удалось отменить автопродление",
                    )
                }
            }
        }
    }

    fun cancelCheckoutPassword() {
        pendingCheckoutPlan = ""
        pendingCheckoutMethod = ""
        pendingCheckoutAutoRenew = true
        _state.update { it.copy(checkoutNeedsPassword = false, authError = null) }
    }

    private fun celebratePlan(plan: String, planUpdatedAt: Long = 0L) {
        val clean = plan.trim()
        if (clean.isBlank() || clean.equals("Free", true)) return
        if (celebratedPlan.equals(clean, ignoreCase = true)) return
        if (planUpdatedAt > 0 && planUpdatedAt <= prefs.lastCelebratedPlanAt) return
        if (planUpdatedAt <= 0 && prefs.lastCelebratedPlan.equals(clean, ignoreCase = true)) return
        celebratedPlan = clean
        prefs.lastCelebratedPlan = clean
        if (planUpdatedAt > 0) prefs.lastCelebratedPlanAt = planUpdatedAt
        _state.update { it.copy(planThanks = clean, notice = null) }
    }

    private fun maybeCelebrateRecentPlan(plan: String, planUpdatedAt: Long) {
        if (planUpdatedAt <= 0 || planUpdatedAt <= prefs.lastCelebratedPlanAt) return
        celebratePlan(plan, planUpdatedAt)
    }

    fun dismissPlanThanks() {
        _state.update { it.copy(planThanks = null) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val previous = _state.value.settings
        val next = transform(previous)
        settingsStore.save(next)
        _state.update { it.copy(settings = next) }
        if (previous != next) {
            Haptics.tap(getApplication(), next.haptics)
        }
        if (previous.breakReminders != next.breakReminders ||
            previous.breakInterval != next.breakInterval
        ) {
            FocusScheduler.apply(getApplication(), next)
        }
        if (!previous.memory && next.memory && _state.value.memory.isEmpty()) {

            memoryAutoAttempted = true
            generateMemory(quiet = true)
        }
    }

    fun saveProfile(
        fullName: String,
        nickname: String,
        preferenceNote: String,
        work: String = _state.value.settings.work,
    ) {
        updateSettings {
            it.copy(
                fullName = fullName.trim().ifBlank { it.fullName },
                nickname = nickname.trim().ifBlank { it.fullName },
                work = work.trim(),
                preferenceNote = preferenceNote,
            )
        }
        val auth = _state.value.auth
        if (auth.signedIn) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        authApi.updateProfile(
                            token = auth.token,
                            name = nickname.trim().ifBlank { fullName.trim() },
                            birthday = auth.birthday,
                            nickname = nickname.trim(),
                            work = work.trim(),
                            instructions = preferenceNote,
                        )
                    }
                }
            }
        }
        _state.update { it.copy(notice = "Профиль обновлён") }
    }

    fun saveAvatar(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val target = File(
                        getApplication<Application>().filesDir,
                        "avatar-${accountKey.ifBlank { "guest" }}.img",
                    )
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            if (path.isNullOrBlank()) {
                _state.update { it.copy(notice = "Не удалось прочитать фото") }
            } else {
                updateSettings { it.copy(avatarPath = path) }
                _state.update { it.copy(notice = "Фото профиля обновлено") }
            }
        }
    }

    fun openCustomize(tab: String = "skills", fromSettings: Boolean = false) {
        val current = _state.value.screen
        customizeReturnScreen = if (fromSettings || current.isSettingsSection) {
            Screen.Settings
        } else {
            current
        }
        _state.update { it.copy(screen = Screen.Customize, customizeTab = tab, searchOpen = false) }
    }

    fun closeCustomize() = _state.update { it.copy(screen = customizeReturnScreen) }

    fun setCustomizeTab(tab: String) = _state.update { it.copy(customizeTab = tab) }

    private fun persistCustomize(data: CustomizeData) {
        val value = data.copy(updatedAt = System.currentTimeMillis())
        customizeStore.save(value)
        _state.update { it.copy(customize = value) }
        scheduleCustomizeUpload(value)
    }

    fun installSkill(id: String) {
        val data = _state.value.customize
        if (id in data.installedSkills) return
        persistCustomize(
            data.copy(
                installedSkills = data.installedSkills + id,
                enabledSkills = data.enabledSkills + id,
            )
        )
        _state.update { it.copy(notice = "Навык добавлен и включён") }
    }

    fun uninstallSkill(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                installedSkills = data.installedSkills - id,
                enabledSkills = data.enabledSkills - id,
            )
        )
    }

    fun toggleSkill(id: String) {
        val data = _state.value.customize
        val enabled = if (id in data.enabledSkills) data.enabledSkills - id else data.enabledSkills + id
        persistCustomize(data.copy(enabledSkills = enabled))
    }

    fun useSkill(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                installedSkills = (data.installedSkills + id).distinct(),
                enabledSkills = (data.enabledSkills + id).distinct(),
            )
        )
    }

    fun saveCustomSkill(skill: Skill) {
        val data = _state.value.customize
        val custom = data.customSkills.filterNot { it.id == skill.id } + skill
        persistCustomize(
            data.copy(
                customSkills = custom,
                installedSkills = (data.installedSkills + skill.id).distinct(),
                enabledSkills = (data.enabledSkills + skill.id).distinct(),
            )
        )
        _state.update { it.copy(notice = "Навык «${skill.title}» добавлен и включён") }
    }

    fun deleteCustomSkill(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                customSkills = data.customSkills.filterNot { it.id == id },
                installedSkills = data.installedSkills - id,
                enabledSkills = data.enabledSkills - id,
            )
        )
    }

    fun importSkills(skills: List<Skill>, marketplace: Marketplace? = null) {
        if (skills.isEmpty()) {
            _state.update { it.copy(notice = "В файле не нашлось навыков") }
            return
        }
        val data = _state.value.customize
        val existing = data.customSkills.associateBy { it.id }
        val merged = existing + skills.associateBy { it.id }
        persistCustomize(
            data.copy(
                customSkills = merged.values.toList(),
                installedSkills = (data.installedSkills + skills.map { it.id }).distinct(),
                marketplaces = if (marketplace != null) {
                    (data.marketplaces.filterNot { it.id == marketplace.id } + marketplace)
                } else {
                    data.marketplaces
                },
            )
        )
        _state.update { it.copy(notice = "Добавлено навыков: ${skills.size}") }
    }

    fun installPlugin(id: String) {
        val data = _state.value.customize
        if (id in data.installedPlugins) return
        persistCustomize(
            data.copy(
                installedPlugins = data.installedPlugins + id,
                enabledPlugins = data.enabledPlugins + id,
            )
        )
        _state.update { it.copy(notice = "Плагин добавлен и включён") }
    }

    fun uninstallPlugin(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                installedPlugins = data.installedPlugins - id,
                enabledPlugins = data.enabledPlugins - id,
            )
        )
    }

    fun togglePlugin(id: String) {
        val data = _state.value.customize
        val enabled = if (id in data.enabledPlugins) data.enabledPlugins - id else data.enabledPlugins + id
        persistCustomize(data.copy(enabledPlugins = enabled))
    }

    fun usePlugin(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                installedPlugins = (data.installedPlugins + id).distinct(),
                enabledPlugins = (data.enabledPlugins + id).distinct(),
            )
        )
    }

    fun saveCustomPlugin(plugin: Plugin) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                customPlugins = data.customPlugins.filterNot { it.id == plugin.id } + plugin,
                installedPlugins = (data.installedPlugins + plugin.id).distinct(),
                enabledPlugins = (data.enabledPlugins + plugin.id).distinct(),
            )
        )
        _state.update { it.copy(notice = "Плагин сохранён") }
    }

    fun deleteCustomPlugin(id: String) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                customPlugins = data.customPlugins.filterNot { it.id == id },
                installedPlugins = data.installedPlugins - id,
                enabledPlugins = data.enabledPlugins - id,
            )
        )
    }

    fun installConnector(id: String) {
        val data = _state.value.customize
        if (id in data.installedConnectors) return
        val connector = data.connector(id)
        if (connector != null && !connector.hasLiveTools) {
            _state.update {
                it.copy(notice = "У этого коннектора нет MCP-адреса. Добавьте свой MCP-сервер.")
            }
            return
        }
        persistCustomize(data.copy(installedConnectors = data.installedConnectors + id))
        _state.update {
            it.copy(
                notice = if (connector?.native == "calendar" || id == "gcal") {
                    "Календарь подключён"
                } else {
                    "Коннектор добавлен — инструменты появятся в чате"
                },
            )
        }
    }

    fun uninstallConnector(id: String) {
        val data = _state.value.customize
        persistCustomize(data.copy(installedConnectors = data.installedConnectors - id))
        if (_state.value.connectorCheckId == id) {
            _state.update {
                it.copy(
                    connectorCheckId = "",
                    connectorCheckBusy = false,
                    connectorCheckStatus = "",
                    connectorCheckError = "",
                )
            }
        }
    }

    fun checkConnector(id: String, autoOAuth: Boolean = false) {
        val snapshot = _state.value
        val connector = snapshot.customize.connector(id) ?: return
        val url = connector.mcpUrl
        if (url.isBlank()) {
            _state.update {
                it.copy(notice = "Сначала укажите URL MCP-сервера")
            }
            return
        }
        if (snapshot.connectorCheckBusy && snapshot.connectorCheckId == id) return
        val auth = snapshot.auth
        if (!auth.signedIn) {
            _state.update { it.copy(notice = "Войдите в аккаунт Clauden, чтобы проверить коннектор") }
            return
        }
        Haptics.tap(getApplication(), snapshot.settings.haptics)
        _state.update {
            it.copy(
                connectorCheckId = id,
                connectorCheckBusy = true,
                connectorCheckStatus = "",
                connectorCheckError = "",
            )
        }
        viewModelScope.launch {
            val state = _state.value
            val config = ApiConfig(
                Provider.Clauden,
                prefs.effectiveBaseUrl(Provider.Clauden),
                state.auth.token,
            )
            val result = withContext(Dispatchers.IO) {
                runCatching { api.checkConnector(config, url, connector.headers) }
            }
            result.onSuccess { check ->
                _state.update {
                    it.copy(
                        connectorCheckBusy = false,
                        connectorCheckStatus = check.status,
                        connectorCheckError = check.error,
                        notice = when (check.status) {
                            "connected" -> "Коннектор подключён"
                            "needs_auth" -> "Сервис требует авторизацию"
                            else -> "Не удалось подключить коннектор"
                        },
                    )
                }
                if (autoOAuth && check.status == "needs_auth") {
                    startConnectorOAuth(id)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        connectorCheckBusy = false,
                        connectorCheckStatus = "failed",
                        connectorCheckError = friendlyError(error),
                        notice = "Не удалось проверить коннектор",
                    )
                }
            }
        }
    }

    fun startConnectorOAuth(id: String) {
        val snapshot = _state.value
        val connector = snapshot.customize.connector(id) ?: return
        val url = connector.mcpUrl
        if (url.isBlank()) {
            _state.update { it.copy(notice = "Сначала укажите URL MCP-сервера") }
            return
        }
        if (snapshot.connectorOAuth.busy) return
        val auth = snapshot.auth
        if (!auth.signedIn) {
            _state.update { it.copy(notice = "Войдите в аккаунт Clauden, чтобы подключить сервис") }
            return
        }
        Haptics.tap(getApplication(), snapshot.settings.haptics)
        _state.update {
            it.copy(
                connectorOAuth = ConnectorOAuthState(id = id, busy = true),
            )
        }
        viewModelScope.launch {
            val state = _state.value
            val config = ApiConfig(
                Provider.Clauden,
                prefs.effectiveBaseUrl(Provider.Clauden),
                state.auth.token,
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    api.startConnectorOAuth(config, url, connector.headers, connector.id, connector.title)
                }
            }
            result.onSuccess { start ->
                if (_state.value.connectorOAuth.id != id) return@onSuccess
                when (start.status) {
                    "connected" -> {
                        _state.update {
                            it.copy(
                                connectorOAuth = ConnectorOAuthState(),
                                notice = "Сервис уже подключён",
                            )
                        }
                        checkConnector(id)
                    }
                    "auth_required" -> {
                        if (start.authUrl.isBlank()) {
                            _state.update {
                                it.copy(
                                    connectorOAuth = ConnectorOAuthState(
                                        id = id,
                                        error = start.error.ifBlank { "Сервис не выдал ссылку для входа" },
                                    ),
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    connectorOAuth = ConnectorOAuthState(
                                        id = id,
                                        authUrl = start.authUrl,
                                        attemptId = start.attemptId,
                                        redirectUri = start.redirectUri,
                                    ),
                                )
                            }
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                connectorOAuth = ConnectorOAuthState(
                                    id = id,
                                    error = start.error.ifBlank { "Не удалось начать вход" },
                                ),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                if (_state.value.connectorOAuth.id != id) return@onFailure
                _state.update {
                    it.copy(
                        connectorOAuth = ConnectorOAuthState(
                            id = id,
                            error = friendlyError(error),
                        ),
                        notice = "Не удалось начать вход",
                    )
                }
            }
        }
    }

    fun finishConnectorOAuth(callbackUrl: String) {
        val oauth = _state.value.connectorOAuth
        if (oauth.attemptId.isBlank() || oauth.busy) return
        val auth = _state.value.auth
        if (!auth.signedIn) return
        _state.update { it.copy(connectorOAuth = oauth.copy(busy = true, error = "")) }
        viewModelScope.launch {
            val config = ApiConfig(
                Provider.Clauden,
                prefs.effectiveBaseUrl(Provider.Clauden),
                auth.token,
            )
            val result = withContext(Dispatchers.IO) {
                runCatching { api.finishConnectorOAuth(config, oauth.attemptId, callbackUrl) }
            }
            result.onSuccess { finish ->
                if (_state.value.connectorOAuth.attemptId != oauth.attemptId) return@onSuccess
                when (finish.status) {
                    "connected", "complete" -> {
                        _state.update {
                            it.copy(
                                connectorOAuth = ConnectorOAuthState(),
                                notice = "Вход выполнен — проверяю коннектор",
                            )
                        }
                        checkConnector(oauth.id)
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                connectorOAuth = oauth.copy(
                                    busy = false,
                                    error = finish.error.ifBlank { "Не удалось завершить вход" },
                                ),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                if (_state.value.connectorOAuth.attemptId != oauth.attemptId) return@onFailure
                _state.update {
                    it.copy(
                        connectorOAuth = oauth.copy(
                            busy = false,
                            error = friendlyError(error),
                        ),
                    )
                }
            }
        }
    }

    fun cancelConnectorOAuth() {
        _state.update { it.copy(connectorOAuth = ConnectorOAuthState()) }
    }

    fun saveCustomConnector(connector: Connector) {
        val data = _state.value.customize
        persistCustomize(
            data.copy(
                customConnectors = data.customConnectors.filterNot { it.id == connector.id } + connector,
                installedConnectors = (data.installedConnectors + connector.id).distinct(),
            )
        )
    }

    fun importPlugin(plugin: Plugin) {
        saveCustomPlugin(plugin)
    }

    fun loadConnectorCatalog(query: String, category: String, reset: Boolean) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        val snapshot = _state.value.connectorCatalog
        if (snapshot.loading && !reset) return
        val offset = if (reset) 0 else snapshot.items.size
        val request = ++catalogRequest
        _state.update {
            it.copy(
                connectorCatalog = it.connectorCatalog.copy(
                    items = if (reset) emptyList() else it.connectorCatalog.items,
                    total = if (reset) 0 else it.connectorCatalog.total,
                    loading = true,
                    error = "",
                    query = query,
                    category = category,
                )
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    connectorCatalogApi.list(
                        token = auth.token,
                        query = query,
                        category = category,
                        connectableOnly = true,
                        limit = 24,
                        offset = offset,
                    )
                }
            }
            if (request != catalogRequest) return@launch
            result.onSuccess { page ->
                _state.update { state ->
                    state.copy(
                        connectorCatalog = state.connectorCatalog.copy(
                            items = if (reset) page.results else state.connectorCatalog.items + page.results,
                            total = page.total,
                            facets = page.facets,
                            loading = false,
                            error = "",
                        )
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        connectorCatalog = it.connectorCatalog.copy(
                            loading = false,
                            error = error.message?.take(200) ?: "Каталог не ответил",
                        )
                    )
                }
            }
        }
    }

    fun connectCatalogItem(item: ConnectorCatalogItem) {
        if (!item.connectable || item.url.isBlank()) {
            _state.update { it.copy(notice = "У этого сервера нет публичного MCP-адреса") }
            return
        }
        if (_state.value.connectorCheckBusy && _state.value.connectorCheckId == item.id) return
        val data = _state.value.customize
        val connector = item.toConnector()
        persistCustomize(
            data.copy(
                customConnectors = data.customConnectors.filterNot { it.id == connector.id } + connector,
                installedConnectors = (data.installedConnectors + connector.id).distinct(),
            )
        )
        checkConnector(connector.id, autoOAuth = true)
    }

    fun rememberCatalogConnector(item: ConnectorCatalogItem) {
        if (!item.connectable || item.url.isBlank()) return
        val data = _state.value.customize
        if (data.customConnectors.any { it.id == item.id }) return
        persistCustomize(
            data.copy(
                customConnectors = data.customConnectors.filterNot { it.id == item.id } +
                    item.toConnector(),
            )
        )
    }

    fun loadMarket(kind: String, source: String, query: String, reset: Boolean) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        val snapshot = _state.value.market
        if (snapshot.loading && !reset) return
        val offset = if (reset) 0 else snapshot.items.size
        val request = ++marketRequest
        _state.update {
            it.copy(
                market = it.market.copy(
                    items = if (reset) emptyList() else it.market.items,
                    total = if (reset) 0 else it.market.total,
                    loading = true,
                    error = "",
                    warning = "",
                    kind = kind,
                    source = source,
                    query = query,
                )
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    marketApi.list(
                        token = auth.token,
                        kind = kind,
                        source = source,
                        query = query,
                        limit = 20,
                        offset = offset,
                    )
                }
            }
            if (request != marketRequest) return@launch
            result.onSuccess { page ->
                _state.update { state ->
                    state.copy(
                        market = state.market.copy(
                            items = if (reset) page.results else state.market.items + page.results,
                            total = page.total,
                            loading = false,
                            error = "",
                            warning = page.warning,
                        )
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        market = it.market.copy(
                            loading = false,
                            error = error.message?.take(200) ?: "Маркетплейс не ответил",
                            warning = "",
                        )
                    )
                }
            }
        }
    }

    fun installMarket(item: MarketItem) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        if (_state.value.market.installingId.isNotBlank()) return
        val kind = _state.value.market.kind
        _state.update { it.copy(market = it.market.copy(installingId = item.id)) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { marketApi.install(auth.token, kind, item.id) }
            }
            result.onSuccess { response ->
                when (kind) {
                    "plugins" -> {
                        val plugin = response.plugin
                        if (plugin == null) {
                            _state.update { it.copy(notice = "Маркетплейс не вернул плагин") }
                        } else {
                            saveMarketPluginBundle(item, plugin, response.skills)
                        }
                    }
                    "connectors" -> {
                        val connector = response.connector
                        if (connector == null) {
                            _state.update { it.copy(notice = "Маркетплейс не вернул коннектор") }
                        } else {
                            saveCustomConnector(
                                connector.copy(origin = item.id, source = "marketplace")
                            )
                            _state.update {
                                it.copy(notice = "Коннектор «${connector.title}» добавлен")
                            }
                        }
                    }
                    else -> {
                        val skill = response.skill
                        if (skill == null) {
                            _state.update { it.copy(notice = "Маркетплейс не вернул навык") }
                        } else {
                            saveCustomSkill(
                                skill.copy(origin = item.id, source = "marketplace")
                            )
                        }
                    }
                }
                _state.update { it.copy(market = it.market.copy(installingId = "")) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        market = it.market.copy(installingId = ""),
                        notice = error.message?.take(220) ?: "Не удалось установить",
                    )
                }
            }
        }
    }

    fun previewMarket(item: MarketItem) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        if (_state.value.market.previewLoading && _state.value.market.previewId == item.id) return
        val kind = _state.value.market.kind
        _state.update {
            it.copy(
                market = it.market.copy(
                    previewId = item.id,
                    previewLoading = true,
                    preview = null,
                    previewError = "",
                )
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { marketApi.preview(auth.token, kind, item.id) }
            }
            if (_state.value.market.previewId != item.id) return@launch
            result.onSuccess { preview ->
                _state.update {
                    it.copy(
                        market = it.market.copy(
                            preview = preview,
                            previewLoading = false,
                            previewError = "",
                        )
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        market = it.market.copy(
                            previewLoading = false,
                            previewError = error.message?.take(220) ?: "Не удалось загрузить содержимое",
                        )
                    )
                }
            }
        }
    }

    fun clearMarketPreview() {
        _state.update {
            it.copy(
                market = it.market.copy(
                    preview = null,
                    previewId = "",
                    previewLoading = false,
                    previewError = "",
                )
            )
        }
    }

    private fun saveMarketPluginBundle(item: MarketItem, plugin: Plugin, skills: List<Skill>) {
        val data = _state.value.customize
        val names = skills.map { it.id }.toSet()
        val imported = skills.map {
            it.copy(origin = "${item.id}#${it.id}", source = "marketplace")
        }
        val customSkills = data.customSkills.filterNot { it.id in names } + imported
        val customPlugins = data.customPlugins.filterNot { it.id == plugin.id } +
            plugin.copy(origin = item.id, source = "marketplace")
        val next = data.copy(
            customSkills = customSkills,
            installedSkills = (data.installedSkills + names).distinct(),
            customPlugins = customPlugins,
            installedPlugins = (data.installedPlugins + plugin.id).distinct(),
            enabledPlugins = (data.enabledPlugins + plugin.id).distinct(),
        )
        persistCustomize(next)
        _state.update {
            it.copy(notice = "Плагин «${plugin.title}» установлен: навыков ${skills.size}")
        }
    }

    fun importSkillText(text: String, fileName: String) {
        val skill = CustomizeImport.parseSkillDocument(text, fileName.substringBeforeLast('.'))
        if (skill == null) {
            _state.update { it.copy(notice = "Не удалось прочитать навык") }
        } else {
            saveCustomSkill(skill)
        }
    }

    fun importPluginText(text: String, fileName: String) {
        val plugin = CustomizeImport.parsePluginDocument(text, fileName.substringBeforeLast('.'))
        if (plugin == null) {
            _state.update { it.copy(notice = "Не удалось прочитать плагин") }
        } else {
            saveCustomPlugin(plugin)
        }
    }

    fun addMarketplace(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(notice = "Загружаю маркетплейс…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CustomizeImport.loadMarketplace(url) }
            }
            result.onSuccess { bundle ->
                importSkills(bundle.skills, bundle.marketplace)
            }.onFailure { error ->
                _state.update {
                    it.copy(notice = "Маркетплейс не загрузился: ${error.message?.take(120)}")
                }
            }
        }
    }

    fun startCustomizeChat(prompt: String) {
        newChat()
        _state.update { it.copy(screen = Screen.Chat, voiceMode = false) }
        send(prompt)
    }

    fun openSearch() = _state.update { it.copy(searchOpen = true) }
    fun closeSearch() = _state.update { it.copy(searchOpen = false) }

    fun addTask(title: String, dueAt: Long = 0L) {
        val text = title.trim()
        if (text.isBlank()) return
        val item = TaskItem(
            id = UUID.randomUUID().toString(),
            title = text,
            createdAt = System.currentTimeMillis(),
            dueAt = dueAt,
        )
        val next = listOf(item) + _state.value.tasks
        taskStore.save(next)
        _state.update { it.copy(tasks = next, notice = "Задача добавлена") }
    }

    fun toggleTask(id: String) {
        val next = _state.value.tasks.map { if (it.id == id) it.copy(done = !it.done) else it }
        taskStore.save(next)
        _state.update { it.copy(tasks = next) }
    }

    fun deleteTask(id: String) {
        val next = _state.value.tasks.filterNot { it.id == id }
        taskStore.save(next)
        _state.update { it.copy(tasks = next) }
    }

    fun addScheduled(title: String, prompt: String, runAt: Long, repeat: String) {
        val cleanTitle = title.trim().ifBlank { prompt.trim().take(40) }
        if (prompt.isBlank() || runAt <= System.currentTimeMillis()) {
            _state.update { it.copy(notice = "Укажите текст и время в будущем") }
            return
        }
        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            prompt = prompt.trim(),
            runAt = runAt,
            repeat = repeat,
            createdAt = System.currentTimeMillis(),
        )
        val next = _state.value.scheduled + task
        scheduledStore.save(next)
        ScheduledScheduler.schedule(getApplication(), task)
        _state.update { it.copy(scheduled = next, notice = "Задача запланирована") }
    }

    fun refreshScheduled() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { scheduledStore.load() }
            _state.update { it.copy(scheduled = items) }
        }
    }

    fun deleteScheduled(id: String) {
        ScheduledScheduler.cancel(getApplication(), id)
        val next = _state.value.scheduled.filterNot { it.id == id }
        scheduledStore.save(next)
        _state.update { it.copy(scheduled = next) }
    }

    fun openTaskChat(chatId: String) {
        if (chatId.isBlank()) return
        if (_state.value.conversations.any { it.id == chatId }) {
            openChat(chatId)
        } else {
            viewModelScope.launch {
                loadAccount()
                if (_state.value.conversations.any { it.id == chatId }) {
                    openChat(chatId)
                } else {
                    _state.update { it.copy(notice = "Этот ответ ещё не загружен") }
                }
            }
        }
    }

    fun refreshSessions() {
        val auth = _state.value.auth
        if (!auth.signedIn || _state.value.sessionsBusy) return
        _state.update { it.copy(sessionsBusy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { authApi.sessions(auth.token) } }
            result.onSuccess { list ->
                _state.update { it.copy(sessions = list, sessionsBusy = false) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        sessionsBusy = false,
                        notice = if (error is AuthExpiredException) null
                        else "Не удалось загрузить сеансы",
                    )
                }
                if (error is AuthExpiredException) logout()
            }
        }
    }

    fun revokeSession(id: String) {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.revokeSession(auth.token, id) }
            }
            result.onSuccess { current ->
                if (current) {
                    logout()
                } else {
                    _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.id == id }) }
                    _state.update { it.copy(notice = "Сеанс завершён") }
                }
            }.onFailure { error ->
                _state.update { it.copy(notice = error.message ?: "Не удалось завершить сеанс") }
            }
        }
    }

    fun logoutAllDevices() {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { authApi.logoutAll(auth.token) } }
            logout()
        }
    }

    fun deleteAccount() {
        val auth = _state.value.auth
        if (!auth.signedIn) return
        _state.value.scheduled.forEach { ScheduledScheduler.cancel(getApplication(), it.id) }
        val removedAccount = accountKey
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.deleteAccount(auth.token) }
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(notice = result.exceptionOrNull()?.message ?: "Не удалось удалить аккаунт")
                }
                return@launch
            }
            withContext(Dispatchers.IO) {
                listOf(
                    "chats-$removedAccount.json",
                    "projects-$removedAccount.json",
                    "customize-$removedAccount.json",
                    "memory-$removedAccount.json",
                    "tasks-$removedAccount.json",
                    "scheduled-$removedAccount.json",
                    "avatar-$removedAccount.img",
                ).forEach { name ->
                    runCatching { File(getApplication<Application>().filesDir, name).delete() }
                }
            }
            prefs.clearAuth()
            prefs.apiKey = ""
            accountKey = ""
            store = ChatStore(getApplication(), "")
            projectStore = ProjectStore(getApplication(), "")
            customizeStore = CustomizeStore(getApplication(), "")
            memoryStore = MemoryStore(getApplication(), "")
            taskStore = TaskStore(getApplication(), "")
            scheduledStore = ScheduledStore(getApplication(), "")
            val cleared = _state.value.settings.copy(
                email = "",
                fullName = "",
                nickname = "",
                work = "",
                preferenceNote = "",
                avatarPath = "",
                plan = "Free",
            )
            settingsStore.save(cleared)
            _state.update {
                it.copy(
                    auth = AuthState(),
                    conversations = emptyList(),
                    projects = emptyList(),
                    activeProjectId = "",
                    draftProjectId = "",
                    projectBusy = false,
                    customize = CustomizeData(),
                    memory = emptyList(),
                    tasks = emptyList(),
                    scheduled = emptyList(),
                    sessions = emptyList(),
                    market = MarketState(),
                    activeId = null,
                    pendingLocation = null,
                    screen = Screen.Chat,
                    settings = cleared,
                    notice = "Аккаунт удалён",
                )
            }
        }
    }

    fun signInGoogle(idToken: String) {
        if (idToken.isBlank()) return
        authCall("Google не подтвердил вход") { authApi.loginGoogle(idToken) }
    }

    fun signInGoogleBridge(payload: String, signature: String) {
        if (payload.isBlank() || signature.isBlank()) return
        authCall("Google не подтвердил вход") {
            authApi.bridge(payload, signature)
        }
    }

    fun signInEmail(email: String, password: String) {
        val normalized = email.trim()
        if (!isEmailValid(normalized)) {
            _state.update { it.copy(authError = "Введите корректный email") }
            return
        }
        if (password.isBlank()) {
            _state.update { it.copy(authError = "Введите пароль") }
            return
        }
        authCall("Не удалось войти по email") { authApi.loginEmail(normalized, password) }
    }

    fun requestEmailCode(email: String) {
        val normalized = email.trim()
        if (!isEmailValid(normalized)) {
            _state.update { it.copy(authError = "Введите корректный email") }
            return
        }
        if (_state.value.authCodeBusy) return
        _state.update { it.copy(authCodeBusy = true, authError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.requestEmailCode(normalized) }
            }
            result.onSuccess {
                _state.update {
                    it.copy(
                        authCodeBusy = false,
                        authCodeSentTo = normalized,
                        notice = "Код отправлен на $normalized",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        authCodeBusy = false,
                        authError = friendlyAuthError(error, "Не удалось отправить код"),
                    )
                }
            }
        }
    }

    fun registerEmail(email: String, code: String, password: String) {
        val normalized = email.trim()
        if (!isEmailValid(normalized)) {
            _state.update { it.copy(authError = "Введите корректный email") }
            return
        }
        val cleanCode = code.trim()
        if (!cleanCode.matches(Regex("^\\d{6}$"))) {
            _state.update { it.copy(authError = "Введите 6-значный код из письма") }
            return
        }
        val problem = passwordProblem(password)
        if (problem != null) {
            _state.update { it.copy(authError = problem) }
            return
        }
        authCall("Не удалось создать аккаунт") {
            authApi.registerEmail(normalized, cleanCode, password)
        }
    }

    fun clearAuthCodeState() = _state.update {
        it.copy(authCodeSentTo = "", authCodeBusy = false, authError = null)
    }

    fun dismissAuthError() = _state.update { it.copy(authError = null) }

    private fun authCall(fallbackMessage: String, block: suspend () -> AuthSession) {
        if (_state.value.authBusy) return
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { block() } }
            result.onSuccess { session ->
                applySession(session)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        authBusy = false,
                        authError = friendlyAuthError(error, fallbackMessage),
                    )
                }
            }
        }
    }

    private suspend fun applySession(session: AuthSession, mode: AuthMode? = null) {
        val user = session.user
        if (session.token.isBlank() || user.id.isBlank()) {
            _state.update { it.copy(authBusy = false, authError = "Сервер не вернул аккаунт") }
            return
        }

        val effectiveMode = mode ?: if (
            isFreePlan(user.plan) &&
            (user.authMode == "claude" || user.claudeEmail.isNotBlank())
        ) {
            AuthMode.Claude
        } else {
            AuthMode.Clauden
        }
        val previousAccount = accountKey
        val previousMode = _state.value.authMode
        val accountChanged = previousAccount.isNotBlank() && previousAccount != user.id
        if (accountChanged) {
            prefs.lastConversationId = null
            _state.value.scheduled.forEach { ScheduledScheduler.cancel(getApplication(), it.id) }
        }
        prefs.authToken = session.token
        prefs.authUserId = user.id
        prefs.authEmail = user.email
        prefs.authName = user.name
        prefs.authBirthday = user.birthday
        prefs.authProvider = user.provider
        prefs.authOnboarded = user.onboarded
        prefs.authMode = effectiveMode

        accountKey = user.id
        store = ChatStore(getApplication(), accountKey)
        projectStore = ProjectStore(getApplication(), accountKey)
        customizeStore = CustomizeStore(getApplication(), accountKey)
        memoryStore = MemoryStore(getApplication(), accountKey)
        taskStore = TaskStore(getApplication(), accountKey)
        scheduledStore = ScheduledStore(getApplication(), accountKey)
        uploadedChats.clear()
        uploadedProjects.clear()
        customizeUploadJob?.cancel()
        customizeUploadedAt = 0L

        val current = _state.value.settings
        val settings = current.copy(
            email = user.email,
            fullName = user.name.ifBlank { if (accountChanged) "" else current.fullName },
            nickname = user.nickname.ifBlank {
                user.name.ifBlank { if (accountChanged) "" else current.nickname }
            },
            work = user.work.ifBlank { if (accountChanged) "" else current.work },
            preferenceNote = user.instructions.ifBlank {
                if (accountChanged) "" else current.preferenceNote
            },
            avatarPath = if (accountChanged) "" else current.avatarPath,
            plan = user.plan.ifBlank { "Free" },
        )
        settingsStore.save(settings)
        prefs.authCodeAccess = user.codeAccess
        prefs.authCanIssueKeys = user.canIssueKeys

        prefs.provider = Provider.Clauden
        prefs.customApiId = ""

        _state.update {
            it.copy(
                auth = AuthState(
                    token = session.token,
                    userId = user.id,
                    email = user.email,
                    name = user.name,
                    birthday = user.birthday,
                    provider = user.provider,
                    organizationId = user.organizationId,
                    onboarded = user.onboarded,
                    authMode = user.authMode,
                    claudeEmail = user.claudeEmail,
                    claudePlan = user.claudePlan,
                    autoRenew = user.autoRenew,
                    renewAt = user.renewAt,
                    renewProvider = user.renewProvider,
                    renewStatus = user.renewStatus,
                    codeAccess = user.codeAccess,
                    hasPassword = user.hasPassword,
                    canIssueKeys = user.canIssueKeys,
                ),
                authMode = effectiveMode,
                claudeLinked = claudeSession?.isUsable == true,
                claudeEmail = claudeSession?.email.orEmpty(),
                claudePlan = claudeSession?.plan.orEmpty(),
                claudeProblem = null,
                claudeLoginOpen = false,
                apiKey = session.token,
                provider = Provider.Clauden,
                baseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                claudenBaseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                customApiId = "",
                authBusy = false,
                authError = null,
                authCodeBusy = false,
                authCodeSentTo = "",
                settings = settings,
                conversations = emptyList(),
                projects = emptyList(),
                activeProjectId = "",
                draftProjectId = "",
                projectBusy = false,
                customize = CustomizeData(),
                memory = emptyList(),
                tasks = emptyList(),
                scheduled = emptyList(),
                sessions = emptyList(),
                sessionsBusy = false,
                market = MarketState(),
                activeId = null,
                streaming = false,
                recoveringId = null,
                pendingLocation = null,
                voiceMode = false,
                searchOpen = false,
                openChatId = "",
                usage = null,
                usageBusy = false,
                usageError = null,
                planThanks = null,
                checkoutBusy = false,
                billingCancelBusy = false,
                checkoutOrder = null,
                checkoutError = null,
                screen = Screen.Chat,
            )
        }
        if (previousMode != effectiveMode) {
            switchEngine(effectiveMode, persist = false)
        } else {
            if (effectiveMode == AuthMode.Claude) refreshModels(silent = true)
        }
        if (user.onboarded) {
            loadAccount()
            refreshSessions()
        } else {
            prefs.lastConversationId = null
        }
    }

    fun completeOnboarding(birthday: String, name: String) {
        val auth = _state.value.auth
        val cleanName = name.trim()
        if (!auth.signedIn || _state.value.authBusy) return
        if (birthday.isBlank()) {
            _state.update { it.copy(authError = "Выберите дату рождения") }
            return
        }
        if (cleanName.isBlank()) {
            _state.update { it.copy(authError = "Введите имя") }
            return
        }
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    authApi.updateProfile(
                        token = auth.token,
                        name = cleanName,
                        birthday = birthday,
                        terms = true,
                    )
                }
            }
            result.onSuccess { user ->
                prefs.authBirthday = user.birthday
                prefs.authName = user.name
                prefs.authOnboarded = user.onboarded
                val settings = _state.value.settings.copy(
                    email = user.email,
                    fullName = user.name,
                    nickname = user.name,
                )
                settingsStore.save(settings)
                _state.update {
                    it.copy(
                        auth = it.auth.copy(
                            name = user.name,
                            birthday = user.birthday,
                            onboarded = user.onboarded,
                        ),
                        settings = settings,
                        authBusy = false,
                        authError = null,
                    )
                }
                loadAccount()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        authBusy = false,
                        authError = friendlyAuthError(error, "Не удалось сохранить профиль"),
                    )
                }
            }
        }
    }

    fun logout() {
        val token = _state.value.auth.token
        runCatching { ClaudeImportService.cancel(getApplication()) }
        ClaudeImportState.clear(getApplication())
        _state.value.scheduled.forEach { ScheduledScheduler.cancel(getApplication(), it.id) }
        cancelActiveSend(markComplete = false)
        SshConnectionHolder.closeAll()
        if (token.isNotBlank()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { authApi.logout(token) } }
            }
        }
        prefs.clearAuth()
        prefs.authCodeAccess = false
        prefs.provider = Provider.Clauden
        prefs.customApiId = ""
        prefs.apiKey = ""
        accountKey = ""
        store = ChatStore(getApplication(), "")
        projectStore = ProjectStore(getApplication(), "")
        customizeStore = CustomizeStore(getApplication(), "")
        memoryStore = MemoryStore(getApplication(), "")
        taskStore = TaskStore(getApplication(), "")
        scheduledStore = ScheduledStore(getApplication(), "")
        uploadedChats.clear()
        uploadedProjects.clear()
        customizeUploadJob?.cancel()
        customizeUploadedAt = 0L
        settingsStore.save(
            _state.value.settings.copy(
                email = "",
                fullName = "",
                nickname = "",
                work = "",
                preferenceNote = "",
                avatarPath = "",
                plan = "Free",
            ),
        )
        _state.update {
            it.copy(
                auth = AuthState(),
                apiKey = "",
                provider = Provider.Clauden,
                baseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                claudenBaseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                customApiId = "",
                authMode = AuthMode.Clauden,
                claudeLinked = claudeSession?.isUsable == true,
                claudeEmail = claudeSession?.email.orEmpty(),
                claudePlan = claudeSession?.plan.orEmpty(),
                claudeProblem = null,
                claudeLoginOpen = false,
                claudeSwitched = false,
                models = ClaudenCatalog.fallbackModels,
                model = "claude-opus-5-5",
                authBusy = false,
                authError = null,
                authCodeBusy = false,
                authCodeSentTo = "",
                conversations = emptyList(),
                projects = emptyList(),
                activeProjectId = "",
                draftProjectId = "",
                projectBusy = false,
                customize = CustomizeData(),
                memory = emptyList(),
                tasks = emptyList(),
                scheduled = emptyList(),
                sessions = emptyList(),
                market = MarketState(),
                activeId = null,
                streaming = false,
                recoveringId = null,
                pendingLocation = null,
                screen = Screen.Chat,
                voiceMode = false,
                settings = it.settings.copy(
                    email = "",
                    fullName = "",
                    nickname = "",
                    work = "",
                    preferenceNote = "",
                    avatarPath = "",
                    plan = "Free",
                ),
                notice = null,
                error = null,
            )
        }
    }

    fun openVoiceMode() = _state.update {
        if (it.auth.signedIn && it.auth.onboarded) it.copy(voiceMode = true) else it
    }

    fun closeVoiceMode() = _state.update { it.copy(voiceMode = false) }

    fun setChatProject(chatId: String, projectId: String) {
        mutateConversation(chatId) { it.copy(projectId = projectId) }
        persist()
    }

    private fun isEmailValid(value: String): Boolean =
        value.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$"))

    private fun passwordProblem(value: String): String? = when {
        value.length < 8 -> "Пароль должен быть не короче 8 символов"
        value.length > 128 -> "Пароль слишком длинный"
        else -> null
    }

    private fun friendlyAuthError(error: Throwable, fallback: String): String = when (error) {
        is UnknownHostException, is ConnectException ->
            "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз."
        is SocketTimeoutException -> "Сервер не ответил. Попробуйте ещё раз."
        else -> error.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun signInClaude(sessionKey: String, cfClearance: String, userAgent: String) {
        val key = sessionKey.trim()
        if (key.isBlank()) {
            _state.update {
                it.copy(
                    authBusy = false,
                    authError = "Не удалось получить доступ. Попробуйте ещё раз.",
                )
            }
            return
        }
        if (_state.value.authBusy) return
        _state.update {
            it.copy(
                authBusy = true,
                authError = null,
                claudeLinkPassword = false,
                claudeLinkEmail = "",
                claudeLinkError = null,
            )
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val base = ClaudeSession(
                        sessionKey = key,
                        cfClearance = cfClearance.trim(),
                        userAgent = userAgent.ifBlank { ClaudeApi.DEFAULT_UA },
                        linkedAt = now,
                    )
                    val discovered = claudeApi.discover(base)
                    val pending = discovered.copy(linkedAt = now, lastOkAt = now)
                    pendingClaudeSession = pending
                    pending to authApi.linkClaude(
                        email = pending.email,
                        name = pending.name,
                        claudePlan = pending.plan,
                        organizationId = pending.orgId,

                        token = _state.value.auth.token,
                    )
                }
            }
            result.onSuccess { (session, auth) -> finishClaudeLink(session, auth) }
                .onFailure { error -> handleClaudeLinkError(error) }
        }
    }

    private suspend fun finishClaudeLink(session: ClaudeSession, auth: AuthSession) {
        pendingClaudeSession = null
        claudeSession = session
        claudeStore.save(session)
        prefs.authClaudeLinked = true
        val paid = !auth.user.plan.isBlank() && !auth.user.plan.equals("Free", true)
        prefs.authMode = if (paid) AuthMode.Clauden else AuthMode.Claude
        _state.update {
            it.copy(claudeLinkPassword = false, claudeLinkEmail = "", claudeLinkError = null)
        }
        applySession(auth, mode = if (paid) AuthMode.Clauden else AuthMode.Claude)
        if (paid) {
            _state.update {
                it.copy(notice = "Тариф уже активен — работаете через Clauden")
            }
        } else {
            switchEngine(AuthMode.Claude, persist = true)
            _state.update {
                it.copy(notice = "Аккаунт Claude подключён — работаете через Claude")
            }
        }
    }

    private fun handleClaudeLinkError(error: Throwable) {
        val link = error as? AuthHttpException
        if (link?.code == "link_password") {

            _state.update {
                it.copy(
                    authBusy = false,
                    authError = null,
                    claudeLinkPassword = true,
                    claudeLinkEmail = pendingClaudeSession?.email.orEmpty(),
                    claudeLinkError = null,
                )
            }
            return
        }
        if (link?.code == "link_bad_password") {
            _state.update {
                it.copy(
                    authBusy = false,
                    claudeLinkError = link.message?.takeIf { it.isNotBlank() }
                        ?: "Неверный пароль Clauden",
                )
            }
            return
        }
        _state.update {
            it.copy(authBusy = false, authError = claudeAuthMessage(error))
        }
    }

    fun linkClaudeWithPassword(password: String) {
        val pending = pendingClaudeSession ?: return
        if (password.isBlank() || _state.value.authBusy) return
        _state.update { it.copy(authBusy = true, claudeLinkError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    authApi.linkClaude(
                        email = pending.email,
                        name = pending.name,
                        claudePlan = pending.plan,
                        organizationId = pending.orgId,
                        token = _state.value.auth.token,
                        password = password,
                    )
                }
            }
            result.onSuccess { auth -> finishClaudeLink(pending, auth) }
                .onFailure { error -> handleClaudeLinkError(error) }
        }
    }

    fun cancelClaudeLink() {
        pendingClaudeSession = null
        _state.update {
            it.copy(claudeLinkPassword = false, claudeLinkEmail = "", claudeLinkError = null)
        }
    }

    private fun claudeAuthMessage(error: Throwable): String {
        if (error is ClaudeApiException) {
            return when (error.kind) {
                ClaudeErrorKind.Unauthorized ->
                    "Не удалось получить доступ. Попробуйте войти ещё раз."
                ClaudeErrorKind.Blocked ->
                    "Claude недоступен из вашей сети. Включите прокси Clauden или смените VPN."
                ClaudeErrorKind.RateLimit ->
                    "Claude временно ограничил вход. Попробуйте позже."
                else -> error.message ?: "Не удалось войти через Claude"
            }
        }
        return friendlyAuthError(error, "Не удалось войти через Claude")
    }

    fun openClaudeLogin() = _state.update {
        it.copy(
            claudeLoginOpen = true,
            authError = null,
            claudeLinkPassword = false,
            claudeLinkError = null,
        )
    }

    fun closeClaudeLogin() {
        pendingClaudeSession = null
        _state.update {
            it.copy(
                claudeLoginOpen = false,
                claudeLinkPassword = false,
                claudeLinkEmail = "",
                claudeLinkError = null,
            )
        }
    }

    fun dismissClaudeProblem() = _state.update { it.copy(claudeProblem = null) }

    fun dismissClaudeSwitched() = _state.update { it.copy(claudeSwitched = false) }

    fun switchEngine(mode: AuthMode, auto: Boolean = false, persist: Boolean = true) {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        if (mode == AuthMode.Claude) {

            space.teleopen.clauden.proxy.ProxyController.attach(getApplication())
            space.teleopen.clauden.proxy.ProxyController.probeClaude()
        }
        if (mode == AuthMode.Clauden && isFreePlan()) {
            _state.update {
                it.copy(
                    notice = "Clauden доступен на Pro, Max и Ultra. На Free работает Claude.",
                )
            }
            return
        }
        if (mode == AuthMode.Claude && claudeSession?.isUsable != true) {

            prefs.authMode = AuthMode.Claude
            _state.update {
                it.copy(
                    authMode = AuthMode.Claude,
                    claudeProblem = ClaudeProblem(
                        kind = ClaudeErrorKind.Unauthorized,
                        message = "Сессия Claude истекла. Войдите в Claude заново.",
                    ),
                )
            }
            return
        }
        cancelActiveSend(markComplete = false)
        if (persist) prefs.authMode = mode
        if (mode == AuthMode.Clauden) {

            prefs.provider = Provider.Clauden
            prefs.customApiId = ""
        }
        val models = if (mode == AuthMode.Claude) {
            ClaudeCatalog.modelsForPlan(_state.value.claudePlan, emptyList())
        } else {
            ClaudenCatalog.fallbackModels
        }
        val model = if (mode == AuthMode.Claude) {
            ClaudeCatalog.preferredModel(models)
        } else {
            "claude-opus-5-5"
        }
        prefs.model = model
        _state.update {
            it.copy(
                authMode = mode,
                provider = if (mode == AuthMode.Clauden) Provider.Clauden else it.provider,
                baseUrl = if (mode == AuthMode.Clauden) {
                    prefs.effectiveBaseUrl(Provider.Clauden)
                } else {
                    it.baseUrl
                },
                apiKey = if (mode == AuthMode.Clauden) {
                    it.auth.token.ifBlank { prefs.effectiveKey(Provider.Clauden) }
                } else {
                    it.apiKey
                },
                customApiId = if (mode == AuthMode.Clauden) "" else it.customApiId,
                models = models,
                model = model,
                claudeProblem = null,
                claudeSwitched = it.claudeSwitched || auto,
            )
        }
        if (_state.value.auth.signedIn) refreshModels(silent = true)
    }

    fun unlinkClaude() {
        claudeStore.clear()
        claudeSession = null
        prefs.authClaudeLinked = false

        runCatching {
            val manager = android.webkit.CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
        claudeApi.clearConversations()
        cancelActiveSend(markComplete = false)
        val paid = !_state.value.settings.plan.isBlank() &&
            !_state.value.settings.plan.equals("Free", true)
        val mode = AuthMode.Clauden
        val model = if (paid) {
            "claude-opus-5-5"
        } else {
            ClaudeCatalog.preferredModel(ClaudeCatalog.fallbackModels)
        }
        prefs.authMode = mode
        prefs.model = model
        _state.update {
            it.copy(
                authMode = mode,
                claudeLinked = false,
                claudeEmail = "",
                claudePlan = "",
                claudeProblem = null,

                claudeLoginOpen = false,
                models = if (paid) ClaudenCatalog.fallbackModels else ClaudeCatalog.fallbackModels,
                model = model,
                notice = if (paid) {
                    "Аккаунт Claude отвязан"
                } else {
                    "Аккаунт Claude отвязан. Войти заново можно в «Режим Claude»"
                },
            )
        }
        if (paid && _state.value.auth.signedIn) refreshModels(silent = true)
    }

    fun importClaudeChats() {
        val session = claudeSession
        if (session?.isUsable != true) {
            _state.update {
                it.copy(claudeImportError = "Сначала войдите в аккаунт Claude")
            }
            return
        }
        if (_state.value.auth.token.isBlank()) {
            _state.update { it.copy(claudeImportError = "Войдите в Clauden") }
            return
        }
        if (ClaudeImportState.progress.value.busy) return
        ClaudeImportState.update(
            getApplication(),
            ClaudeImportProgress(busy = true, finished = false),
        )
        runCatching { ClaudeImportService.start(getApplication()) }
            .onFailure {
                ClaudeImportState.update(
                    getApplication(),
                    ClaudeImportProgress(busy = false, finished = true, error = "Не удалось запустить импорт"),
                )
            }
    }

    fun cancelClaudeImport() {
        runCatching { ClaudeImportService.cancel(getApplication()) }
        _state.update { it.copy(notice = "Импорт остановлен") }
    }

    fun dismissClaudeImport() {
        ClaudeImportState.clear(getApplication())
    }

    fun importClaudeFromFiles(uris: List<Uri>) {
        val unique = uris.distinctBy { it.toString() }
        if (unique.isEmpty() || _state.value.claudeFileImport.busy) return
        val account = accountKey.ifBlank { prefs.authUserId }
        if (account.isBlank()) {
            _state.update {
                it.copy(
                    claudeFileImport = ClaudeFileImportState(
                        finished = true,
                        error = "Войдите в Clauden",
                    ),
                )
            }
            return
        }
        _state.update {
            it.copy(
                claudeFileImport = ClaudeFileImportState(
                    busy = true,
                    total = unique.size,
                ),
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ClaudeExportImporter(getApplication(), account).import(unique) { done, total, title ->
                        _state.update { current ->
                            current.copy(
                                claudeFileImport = current.claudeFileImport.copy(
                                    done = done,
                                    total = total,
                                    title = title,
                                ),
                            )
                        }
                    }
                }
            }
            result.onSuccess { summary ->
                loadAccount()
                _state.update {
                    it.copy(
                        claudeFileImport = ClaudeFileImportState(
                            finished = true,
                            done = unique.size,
                            total = unique.size,
                            chats = summary.chats,
                            projects = summary.projects,
                            memories = summary.memories,
                            artifacts = summary.artifacts,
                            files = summary.files,
                            skipped = summary.skipped,
                            warnings = summary.warnings,
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        claudeFileImport = it.claudeFileImport.copy(
                            busy = false,
                            finished = true,
                            error = error.message ?: "Не удалось перенести файлы",
                        ),
                    )
                }
            }
        }
    }

    fun dismissClaudeFileImport() {
        _state.update { it.copy(claudeFileImport = ClaudeFileImportState()) }
    }

    fun restoreClaudeChats() {
        val auth = _state.value.auth
        if (!auth.signedIn) {
            _state.update { it.copy(claudeImportError = "Войдите в Clauden") }
            return
        }
        if (_state.value.claudeImportBusy) return
        _state.update {
            it.copy(
                claudeImportBusy = true,
                claudeImportDone = 0,
                claudeImportTotal = 0,
                claudeImportTitle = "Загрузка бэкапа",
                claudeImportFiles = 0,
                claudeImportError = null,
                claudeImportFinished = false,
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.fetchChats(auth.token) }
            }
            result.onSuccess { remote ->
                retryDeletedChats(auth.token, remote)
                val added = mergeRemoteChats(remote)
                val projects = withContext(Dispatchers.IO) {
                    runCatching { authApi.fetchProjects(auth.token) }.getOrNull()
                }
                projects?.let { mergeRemoteProjects(it) }
                scheduleChatUpload(_state.value.conversations)
                scheduleProjectUpload(_state.value.projects)
                _state.update {
                    it.copy(
                        claudeImportBusy = false,
                        claudeImportFinished = true,
                        claudeImportTitle = "",
                        notice = "Загружено из бэкапа: $added",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        claudeImportBusy = false,
                        claudeImportFinished = true,
                        claudeImportTitle = "",
                        claudeImportError = friendlyAuthError(error, "Не удалось загрузить бэкап"),
                    )
                }
            }
        }
    }

    fun checkBanRisk() {
        val snapshot = _state.value
        if (snapshot.banRiskBusy) return
        if (claudeSession?.isUsable != true) {
            _state.update { it.copy(banRiskError = "Сначала войдите в аккаунт Claude") }
            return
        }
        if (snapshot.auth.token.isBlank()) {
            _state.update {
                it.copy(banRiskError = "Войдите в аккаунт Clauden, чтобы проверить риск")
            }
            return
        }
        Haptics.tap(getApplication(), snapshot.settings.haptics)
        _state.update { it.copy(banRiskBusy = true, banRiskError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val ip = claudeApi.exitIp()
                    authApi.checkBanRisk(snapshot.auth.token, ip)
                }
            }
            result.onSuccess { risk ->
                _state.update {
                    it.copy(banRiskBusy = false, banRisk = risk, banRiskError = null)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        banRiskBusy = false,
                        banRiskError = friendlyAuthError(error, "Не удалось проверить риск"),
                    )
                }
            }
        }
    }

    fun clearBanRisk() {
        _state.update { it.copy(banRisk = null, banRiskError = null) }
    }

    fun copyClaudeDiagnostics() {        val text = claudeDiagnostics.read().trim()
        if (text.isBlank()) {
            _state.update {
                it.copy(notice = "Диагностика пуста: сначала отправьте сообщение в Claude")
            }
            return
        }
        val clipboard = getApplication<Application>()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Claude SSE", text))
        _state.update { it.copy(notice = "Диагностика Claude скопирована") }
    }

    fun setClaudePassword(password: String) {
        val auth = _state.value.auth
        if (!auth.signedIn || _state.value.authBusy) return
        val problem = passwordProblem(password)
        if (problem != null) {
            _state.update { it.copy(authError = problem) }
            return
        }
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authApi.setPassword(auth.token, password) }
            }
            result.onSuccess { user ->
                _state.update {
                    it.copy(
                        authBusy = false,
                        authError = null,
                        auth = it.auth.copy(hasPassword = user.hasPassword),
                        checkoutNeedsPassword = false,
                        notice = "Пароль установлен",
                    )
                }
                val plan = pendingCheckoutPlan
                val method = pendingCheckoutMethod
                val autoRenew = pendingCheckoutAutoRenew
                if (plan.isNotBlank()) {
                    pendingCheckoutPlan = ""
                    pendingCheckoutMethod = ""
                    pendingCheckoutAutoRenew = true
                    startCheckout(plan, method, autoRenew)
                }
            }.onFailure { error ->
                if (error is AuthExpiredException) {
                    logout()
                } else {
                    _state.update {
                        it.copy(
                            authBusy = false,
                            authError = friendlyAuthError(error, "Не удалось установить пароль"),
                        )
                    }
                }
            }
        }
    }
    fun openChats() = _state.update { it.copy(screen = Screen.Chats) }
    fun openProjects() = _state.update {
        it.copy(screen = Screen.Projects, activeProjectId = "", searchOpen = false)
    }
    fun openProject(id: String) {
        if (_state.value.projects.none { it.id == id }) return
        _state.update {
            it.copy(screen = Screen.Project, activeProjectId = id, searchOpen = false)
        }
    }
    fun closeProject() = _state.update {
        it.copy(screen = Screen.Projects, activeProjectId = "")
    }
    fun openProjectContext() {
        if (_state.value.activeProjectId.isBlank()) return
        _state.update { it.copy(screen = Screen.ProjectContext) }
    }
    fun closeProjectContext() = _state.update { it.copy(screen = Screen.Project) }
    fun openArtifacts() = _state.update { it.copy(screen = Screen.Artifacts) }
    fun openCode() = _state.update { it.copy(screen = Screen.Code) }

    fun openRemote(token: String) {
        if (token.isBlank()) return
        _state.update {
            it.copy(screen = Screen.Code, remoteToken = token, searchOpen = false)
        }
    }

    fun consumeRemoteToken() = _state.update { it.copy(remoteToken = "") }

    fun openChat(id: String) {
        Haptics.tap(getApplication(), _state.value.settings.haptics)

        prefs.lastConversationId = id
        _state.update {
            it.copy(
                activeId = id,
                draftProjectId = "",
                screen = Screen.Chat,
                error = null,
                searchOpen = false,
                openChatId = "",
            )
        }
        recoverTurn(id)
    }

    fun openChatFromNotification(id: String) {
        if (id.isBlank()) return
        val loaded = _state.value.conversations.any { it.id == id }
        if (loaded) {
            openChat(id)
        } else {
            _state.update { it.copy(openChatId = id) }
            if (_state.value.auth.signedIn) {
                viewModelScope.launch { loadAccount() }
            }
        }
    }

    fun onAppResumed() {
        appForeground = true

        checkForUpdate()
        refreshDownloadPermission()
        val snapshot = _state.value
        if (!snapshot.auth.signedIn || !snapshot.auth.onboarded) return

        if (SystemClock.elapsedRealtime() - sessionValidatedAt > 60_000L) {
            viewModelScope.launch { validateSession() }
        }
        if (snapshot.error != null && isNetworkMessage(snapshot.error)) {
            _state.update { it.copy(error = null) }
        }

        val problem = snapshot.claudeProblem
        if (problem != null && problem.retryAt > 0L &&
            problem.retryAt <= System.currentTimeMillis()
        ) {
            _state.update { it.copy(claudeProblem = null) }
        }

        if (!snapshot.streaming) syncAccountFromServer()
        val chatId = snapshot.activeId ?: return
        if (snapshot.streaming) return
        recoverTurn(chatId)
        if (snapshot.apiKey.isNotBlank() && snapshot.models.isEmpty()) refreshModels(silent = true)
    }

    fun onAppBackgrounded() {
        appForeground = false
        val snapshot = _state.value
        if (snapshot.authMode != AuthMode.Claude) return
        if (!snapshot.streaming) return
        cancelActiveSend(markComplete = false)
        persist()
    }

    private fun recoverTurn(chatId: String) {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val conversation = snapshot.conversations.firstOrNull { it.id == chatId } ?: return
        val lastAssistant = conversation.messages.lastOrNull() ?: return
        if (lastAssistant.role != "assistant") return
        if (lastAssistant.interrupted) return
        if (snapshot.authMode == AuthMode.Claude) {
            if (!lastAssistant.complete) recoverClaudeTurn(chatId)
            return
        }
        if (snapshot.provider == Provider.Custom) {

            if (!lastAssistant.complete) markAssistantComplete(chatId)
            return
        }
        if (snapshot.apiKey.isBlank()) return
        if (!lastAssistant.complete) {
            scheduleRecovery(chatId)
            return
        }

        if (lastAssistant.ts > 0 &&
            System.currentTimeMillis() - lastAssistant.ts < 60 * 60_000L
        ) {
            refreshTurn(chatId)
        }
    }

    private fun recoverClaudeTurn(chatId: String) {
        val snapshot = _state.value
        val conversation = snapshot.conversations.firstOrNull { it.id == chatId } ?: return
        val lastAssistant = conversation.messages.lastOrNull { it.role == "assistant" } ?: return
        if (lastAssistant.complete || lastAssistant.interrupted) return
        val session = claudeSession
        if (session == null || !session.isUsable) {
            val message = "Сессия Claude истекла. Войдите заново."
            markAssistantFailed(chatId, message)
            _state.update {
                it.copy(
                    recoveringId = null,
                    error = message,
                    claudeProblem = ClaudeProblem(ClaudeErrorKind.Unauthorized, message),
                )
            }
            persist()
            return
        }
        val remoteId = conversation.claudeConversationId
        if (remoteId.isBlank()) {
            val message = "Ответ остался в Claude: диалог не сохранился на устройстве"
            markAssistantFailed(chatId, message)
            _state.update { it.copy(error = message) }
            persist()
            return
        }
        if (_state.value.recoveringId == chatId) return
        recoveryJob?.cancel()
        _state.update { it.copy(recoveringId = chatId, error = null) }
        recoveryJob = viewModelScope.launch {
            var ready: ClaudeAnswer? = null
            var failure: ClaudeApiException? = null
            var lastSeen = ""
            var stable = 0
            val deadline = System.currentTimeMillis() + 5 * 60_000L
            while (System.currentTimeMillis() < deadline) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { claudeApi.latestAssistantAnswer(session, remoteId) }
                }
                val candidate = result.getOrNull()
                if (candidate != null) {

                    if (candidate.text == lastSeen) {
                        stable += 1
                        if (stable >= 2) {
                            ready = candidate
                            break
                        }
                    } else {
                        lastSeen = candidate.text
                        stable = 0
                    }
                } else {
                    stable = 0
                }
                val error = result.exceptionOrNull()
                if (error is ClaudeApiException) {
                    failure = error
                    if (error.kind == ClaudeErrorKind.Unauthorized ||
                        error.kind == ClaudeErrorKind.Blocked
                    ) {
                        break
                    }
                }
                delay(2_500L)
            }
            val current = _state.value.conversations.firstOrNull { it.id == chatId }
            val last = current?.messages?.lastOrNull { it.role == "assistant" }
            val answer = ready
            if (answer != null && last != null && !last.interrupted) {
                mutateConversation(chatId) { conv ->
                    val messages = conv.messages.toMutableList()
                    val index = messages.indexOfLast { it.role == "assistant" }
                    if (index >= 0 && !messages[index].interrupted) {
                        messages[index] = messages[index].copy(
                            content = answer.text,
                            steps = answer.steps.ifEmpty { messages[index].steps },
                            complete = true,
                            syncedTs = System.currentTimeMillis(),
                            liveElapsedMs = 0L,
                        )
                    }
                    conv.copy(messages = messages, updatedAt = System.currentTimeMillis())
                }
                _state.update { it.copy(recoveringId = null, notice = "Ответ забран из Claude") }
                persist()
            } else {
                val fail = failure
                val message = fail?.message
                    ?: "Ответ ещё готовится в Claude. Откройте чат позже."

                markAssistantFailed(chatId, message)
                _state.update {
                    it.copy(
                        recoveringId = null,
                        error = message,
                        claudeProblem = if (fail != null && (
                                fail.kind == ClaudeErrorKind.Unauthorized ||
                                    fail.kind == ClaudeErrorKind.Blocked
                                )
                        ) {
                            ClaudeProblem(fail.kind, fail.message ?: "Claude недоступен")
                        } else {
                            it.claudeProblem
                        },
                    )
                }
                persist()
            }
        }
    }

    private fun refreshTurn(chatId: String) {
        viewModelScope.launch {
            val snapshot = _state.value
            val conversation = snapshot.conversations.firstOrNull { it.id == chatId }
                ?: return@launch
            val lastAssistant = conversation.messages.lastOrNull() ?: return@launch
            if (lastAssistant.role != "assistant" || lastAssistant.turnId.isBlank()) return@launch
            if (lastAssistant.interrupted) return@launch
            if (snapshot.provider == Provider.Custom) return@launch
            val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)
            val turns = withContext(Dispatchers.IO) {
                runCatching { api.fetchTurns(config, chatId) }.getOrDefault(emptyList())
            }
            val turn = turns.lastOrNull { it.turnId == lastAssistant.turnId } ?: return@launch
            if (turn.ts <= lastAssistant.syncedTs) return@launch
            applyRemoteTurn(chatId, turn)
        }
    }

    private fun scheduleRecovery(chatId: String) {

        if (_state.value.authMode == AuthMode.Claude) {
            recoverClaudeTurn(chatId)
            return
        }
        if (recoveryJob?.isActive == true && _state.value.recoveringId == chatId) return
        recoveryJob?.cancel()
        _state.update { it.copy(recoveringId = chatId, error = null) }
        recoveryJob = viewModelScope.launch {
            var applied = false
            var misses = 0
            var networkFailures = 0
            try {

                val deadline = System.currentTimeMillis() + 15 * 60_000L
                while (System.currentTimeMillis() < deadline) {
                    val snapshot = _state.value
                    val conversation = snapshot.conversations.firstOrNull { it.id == chatId }
                        ?: return@launch
                    val lastUser = conversation.messages.lastOrNull { it.role == "user" }
                        ?: return@launch
                    val lastAssistant = conversation.messages.lastOrNull() ?: return@launch
                    if (lastAssistant.role != "assistant") return@launch
                    if (lastAssistant.interrupted) return@launch
                    val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)

                    if (lastAssistant.turnId.isBlank() || !lastAssistant.complete) {
                        val activeResult = withContext(Dispatchers.IO) {
                            runCatching { api.fetchActiveTurn(config, chatId) }
                        }
                        val failure = activeResult.exceptionOrNull()
                        if (failure != null) {

                            if (isTransient(failure)) {
                                networkFailures += 1
                                if (networkFailures == 1) {
                                    _state.update {
                                        it.copy(notice = "Связь прервалась — жду восстановления, ответ не потеряется")
                                    }
                                }
                                delay(3000L)
                                continue
                            }
                            _state.update {
                                it.copy(recoveringId = null, error = friendlyError(failure))
                            }
                            return@launch
                        }
                        if (networkFailures > 0) {
                            networkFailures = 0
                            _state.update { it.copy(notice = null) }
                        }
                        val active = activeResult.getOrNull()
                        if (active != null) {
                            misses = 0
                            if (active.status == "error") {
                                val raw = active.error.ifBlank {
                                    "Не удалось получить ответ. Попробуйте ещё раз."
                                }
                                val message = friendlyError(IllegalStateException(raw))
                                markAssistantFailed(chatId, message)
                                _state.update {
                                    it.copy(
                                        recoveringId = null,
                                        error = message,
                                    )
                                }
                                return@launch
                            }
                            applyActiveTurn(chatId, active)
                            delay(2500L)
                            continue
                        }
                    }

                    val turnsResult = withContext(Dispatchers.IO) {
                        runCatching { api.fetchTurns(config, chatId) }
                    }
                    val turnsFailure = turnsResult.exceptionOrNull()
                    if (turnsFailure != null) {
                        if (isTransient(turnsFailure)) {
                            networkFailures += 1
                            if (networkFailures == 1) {
                                _state.update {
                                    it.copy(notice = "Связь прервалась — жду восстановления, ответ не потеряется")
                                }
                            }
                            delay(3000L)
                            continue
                        }
                        _state.update {
                            it.copy(recoveringId = null, error = friendlyError(turnsFailure))
                        }
                        return@launch
                    }
                    if (networkFailures > 0) {
                        networkFailures = 0
                        _state.update { it.copy(notice = null) }
                    }
                    val turns = turnsResult.getOrDefault(emptyList())
                    val turn = turns.lastOrNull { candidate ->
                        candidate.text.isNotBlank() &&
                            lastAssistant.turnId.isNotBlank() &&
                            candidate.turnId == lastAssistant.turnId
                    } ?: turns.lastOrNull { candidate ->

                        candidate.turnId.isBlank() &&
                            candidate.text.isNotBlank() &&
                            candidate.userText.isNotBlank() &&
                            lastUser.content.trimStart()
                                .startsWith(candidate.userText.trimStart().take(50))
                    }
                    if (turn != null) {
                        applyRemoteTurn(chatId, turn)
                        applied = true
                        return@launch
                    }

                    misses += 1
                    if (misses > 24) break
                    delay(3000L)
                }
            } finally {
                if (_state.value.recoveringId == chatId) {
                    _state.update {
                        it.copy(
                            recoveringId = null,
                            error = if (applied) {
                                null
                            } else if (networkFailures > 0) {
                                "Нет связи с сервером. Ответ сохранён на сервере — откройте чат, когда появится интернет."
                            } else {
                                "Не удалось получить ответ. Проверьте соединение."
                            },
                            notice = if (applied) null else it.notice,
                        )
                    }
                }
                ClaudenReplyService.stop(getApplication())
            }
        }
    }

    private fun applyActiveTurn(chatId: String, active: RemoteActiveTurn) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                val current = messages[index]
                if (current.interrupted) return@mutateConversation conversation
                messages[index] = current.copy(
                    content = active.text.ifBlank { current.content },
                    thinking = active.thinking.ifBlank { current.thinking },
                    steps = if (active.steps.isNotEmpty()) active.steps else current.steps,
                    files = if (active.files.isNotEmpty()) active.files else current.files,
                    actions = if (active.actions.isNotEmpty()) active.actions else current.actions,
                    sources = if (active.sources.isNotEmpty()) active.sources else current.sources,
                    turnId = active.turnId.ifBlank { current.turnId },
                    liveElapsedMs = active.elapsedMs,
                    complete = false,
                )
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun applyRemoteTurn(chatId: String, turn: RemoteTurn) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                val current = messages[index]
                if (current.interrupted) return@mutateConversation conversation
                val text = turn.text.ifBlank {
                    turn.files.firstOrNull()?.let { "Готово — файл «${it.name}»." }
                        ?: current.content
                }
                messages[index] = current.copy(
                    content = text,
                    turnId = turn.turnId.ifBlank { current.turnId },
                    thinking = turn.thinking.ifBlank { current.thinking },
                    thinkingMs = if (turn.thinkingMs > 0) turn.thinkingMs else current.thinkingMs,
                    steps = if (turn.steps.isNotEmpty()) turn.steps else current.steps,
                    files = if (turn.files.isNotEmpty()) turn.files else current.files,
                    actions = turn.actions.map {
                        if (it.status == "pending") {
                            if (it.type in setOf("alarm", "timer", "calendar")) {
                                it.copy(status = "ready", detail = "Нажмите, чтобы открыть")
                            } else {
                                it.copy(status = "sent", detail = "Действие отправлено на телефон")
                            }
                        } else {
                            it
                        }
                    }.ifEmpty { current.actions },
                    sources = if (turn.sources.isNotEmpty()) turn.sources else current.sources,
                    complete = true,

                    interrupted = turn.incomplete || current.interrupted,
                    syncedTs = turn.ts,
                    liveElapsedMs = 0L,
                )
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
        _state.update { it.copy(recoveringId = null, error = null) }
        persist()
        ClaudenReplyService.stop(getApplication())
        if (!appForeground && _state.value.settings.notifyReplies && turn.text.isNotBlank()) {
            val chatTitle = _state.value.conversations.firstOrNull { it.id == chatId }?.title.orEmpty()
            val heading = if (chatTitle.isBlank()) {
                "Clauden ответил"
            } else {
                "Clauden ответил · $chatTitle"
            }
            ClaudenReplyNotifications.reply(getApplication(), chatId, heading, turn.text)
        }
    }

    fun newChat(projectId: String = "") {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        cancelActiveSend(markComplete = false)
        prefs.lastConversationId = null
        _state.update {
            it.copy(
                activeId = null,
                draftProjectId = projectId,
                streaming = false,
                screen = Screen.Chat,
                error = null,
                recoveringId = null,
                pendingLocation = null,
            )
        }
    }

    fun selectChat(id: String) {
        openChat(id)
    }

    fun renameChat(id: String, title: String) {
        val clean = title.trim().ifBlank { "Новый чат" }
        mutateConversation(id) { it.copy(title = clean) }
        persist()
    }

    fun togglePin(id: String) {
        mutateConversation(id) { it.copy(pinned = !it.pinned) }
        persist()
    }

    fun deleteChat(id: String) {

        prefs.markChatDeleted(id)
        if (_state.value.activeId == id) {
            cancelActiveSend(markComplete = false)
            if (_state.value.recoveringId == id) {
                _state.update { it.copy(recoveringId = null) }
            }
        }
        _state.update { s ->
            val left = s.conversations.filterNot { it.id == id }
            val nextActive = when {
                s.activeId != id -> s.activeId
                else -> left.firstOrNull()?.id
            }
            s.copy(conversations = left, activeId = nextActive)
        }
        if (_state.value.conversations.isEmpty()) newChat()
        prefs.lastConversationId = _state.value.activeId
        uploadedChats.remove(id)
        val token = _state.value.auth.token
        if (token.isNotBlank()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { authApi.deleteChat(token, id) } }
            }
        }
        persist()
    }

    fun clearHistory() {
        cancelActiveSend(markComplete = false)
        val token = _state.value.auth.token
        prefs.markChatsDeleted(_state.value.conversations.map { it.id })
        _state.update { it.copy(conversations = emptyList(), activeId = null, recoveringId = null) }
        uploadedChats.clear()
        newChat()
        persist()
        if (token.isNotBlank()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { authApi.clearChats(token) } }
            }
        }
    }

    fun createProject(title: String, description: String = "") {
        val project = Project(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Новый проект" },
            description = description.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        _state.update {
            it.copy(
                projects = listOf(project) + it.projects,
                screen = Screen.Project,
                activeProjectId = project.id,
            )
        }
        persistProjects()
    }

    fun renameProject(id: String, title: String) {
        updateProject(id) { it.copy(title = title.trim().ifBlank { it.title }) }
    }

    fun updateProjectDetails(id: String, title: String, description: String) {
        updateProject(id) {
            it.copy(
                title = title.trim().ifBlank { it.title },
                description = description.trim(),
            )
        }
    }

    fun toggleProjectPin(id: String) {
        updateProject(id) { it.copy(pinned = !it.pinned) }
    }

    fun archiveProject(id: String, archived: Boolean = true) {
        updateProject(id) { it.copy(archived = archived) }
        if (!archived) return
        _state.update { s ->
            if (s.activeProjectId == id) {
                s.copy(screen = Screen.Projects, activeProjectId = "")
            } else {
                s
            }
        }
    }

    fun updateProjectInstructions(id: String, text: String) {
        updateProject(id) { it.copy(instructions = text) }
    }

    fun deleteProject(id: String) {
        _state.update { s ->
            s.copy(
                projects = s.projects.filterNot { it.id == id },
                conversations = s.conversations.map { chat ->
                    if (chat.projectId == id) chat.copy(projectId = "") else chat
                },
                activeProjectId = if (s.activeProjectId == id) "" else s.activeProjectId,
                draftProjectId = if (s.draftProjectId == id) "" else s.draftProjectId,
                screen = if (s.activeProjectId == id) Screen.Projects else s.screen,
            )
        }
        persistProjects()
        persist()
    }

    private fun updateProject(id: String, transform: (Project) -> Project) {
        _state.update { s ->
            s.copy(
                projects = s.projects.map { project ->
                    if (project.id != id) project else
                        transform(project).copy(updatedAt = System.currentTimeMillis())
                },
            )
        }
        persistProjects()
    }

    fun addProjectFiles(projectId: String, uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.projectBusy) return
        val gateway = _state.value
        val extractBase = if (gateway.provider == Provider.Clauden) {
            gateway.baseUrl.ifBlank { ClaudenCatalog.baseUrl }
        } else {
            ClaudenCatalog.baseUrl
        }
        val extractKey = if (gateway.provider == Provider.Clauden) {
            gateway.apiKey.ifBlank { ClaudenCatalog.apiKey }
        } else {
            ClaudenCatalog.apiKey
        }
        viewModelScope.launch {
            _state.update { it.copy(projectBusy = true) }
            val added = mutableListOf<ProjectFile>()
            var failed = 0
            for (uri in uris) {
                val picked = withContext(Dispatchers.IO) {
                    ProjectFiles.read(getApplication(), uri)
                }
                if (picked == null) {
                    failed += 1
                    continue
                }
                val text = if (ProjectFiles.isTextLike(picked.name, picked.mime)) {
                    withContext(Dispatchers.Default) { ProjectFiles.decode(picked.bytes) }
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            projectTextApi.extract(
                                baseUrl = extractBase,
                                apiKey = extractKey,
                                name = picked.name,
                                mime = picked.mime,
                                bytes = picked.bytes,
                            )
                        }.getOrNull().orEmpty()
                    }
                }
                if (text.isBlank()) {
                    failed += 1
                    continue
                }
                added += ProjectFile(
                    id = UUID.randomUUID().toString(),
                    name = picked.name,
                    mime = picked.mime,
                    text = text.take(ProjectFiles.MAX_CHARS),
                    sizeBytes = picked.sizeBytes,
                    addedAt = System.currentTimeMillis(),
                )
            }
            if (added.isNotEmpty()) {
                _state.update { s ->
                    s.copy(
                        projects = s.projects.map { project ->
                            if (project.id != projectId) project else project.copy(
                                files = project.files + added,
                                updatedAt = System.currentTimeMillis(),
                            )
                        },
                    )
                }
                persistProjects()
            }
            _state.update {
                it.copy(
                    projectBusy = false,
                    notice = when {
                        added.isEmpty() -> "Не удалось прочитать файлы"
                        failed > 0 -> "Добавлено файлов: ${added.size}, не прочитано: $failed"
                        else -> "Файлов добавлено: ${added.size}"
                    },
                )
            }
        }
    }

    fun removeProjectFile(projectId: String, fileId: String) {
        updateProject(projectId) { project ->
            project.copy(files = project.files.filterNot { it.id == fileId })
        }
    }

    private fun buildProjectContext(): String {
        val snapshot = _state.value
        val projectId = snapshot.active?.projectId.orEmpty().ifBlank { snapshot.draftProjectId }
        if (projectId.isBlank()) return ""
        val project = snapshot.projects.firstOrNull { it.id == projectId } ?: return ""
        if (project.instructions.isBlank() && project.files.isEmpty()) return ""
        val safeTitle = project.title.replace("\"", "").take(120)
        return buildString {
            appendLine("<project title=\"$safeTitle\">")
            appendLine(
                "Ты работаешь в проекте «${project.title}». Опирайся на его инструкции " +
                    "и знания в каждом ответе."
            )
            if (project.description.isNotBlank()) {
                appendLine("Описание проекта: ${project.description.take(2000)}")
            }
            if (project.instructions.isNotBlank()) {
                appendLine()
                appendLine("Инструкции проекта (обязательны к выполнению):")
                appendLine(project.instructions.take(6000))
            }
            val files = project.files.filter { it.text.isNotBlank() }
            if (files.isNotEmpty()) {
                appendLine()
                appendLine("Знания проекта (файлы; ссылайся на них по имени):")
                var budget = 48_000
                for (file in files) {
                    if (budget <= 0) break
                    val chunk = file.text.take(minOf(budget, 12_000))
                    appendLine("=== ${file.name} ===")
                    appendLine(chunk)
                    budget -= chunk.length
                }
                if (budget <= 0) appendLine("(часть файлов не поместилась в контекст)")
            }
            append("</project>")
        }
    }

    fun setEffort(effort: Effort) {
        prefs.effort = effort
        _state.update { it.copy(effort = effort) }
    }

    fun saveSettings(provider: Provider, baseUrl: String, apiKey: String, model: String) {
        prefs.provider = provider
        when (provider) {
            Provider.Clauden -> prefs.claudenBaseUrl = baseUrl
            Provider.Custom -> prefs.baseUrl = baseUrl
            Provider.Anthropic -> Unit
        }
        prefs.apiKey = apiKey
        if (model.isNotBlank()) prefs.model = model.trim()
        _state.update {
            it.copy(
                provider = provider,
                baseUrl = prefs.effectiveBaseUrl(provider),
                claudenBaseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                customBaseUrl = prefs.baseUrl,
                apiKey = prefs.effectiveKey(provider),
                model = if (model.isNotBlank()) model.trim() else it.model,
                notice = "Настройки сохранены",
            )
        }
    }

    fun selectModel(id: String) {
        val snapshot = _state.value
        val claudenModel = snapshot.provider == Provider.Clauden ||
            (snapshot.provider == Provider.Custom && snapshot.auth.token.isNotBlank())
        if (snapshot.authMode == AuthMode.Clauden && claudenModel &&
            ClaudenCatalog.isLockedForPlan(snapshot.settings.plan, id)
        ) {
            _state.update { it.copy(notice = "Эта модель доступна на платных тарифах") }
            return
        }

        if (snapshot.provider == Provider.Custom) {
            prefs.provider = Provider.Clauden
            prefs.customApiId = ""
            val token = snapshot.auth.token
            _state.update {
                it.copy(
                    provider = Provider.Clauden,
                    baseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                    apiKey = if (token.isNotBlank()) token else prefs.effectiveKey(Provider.Clauden),
                    customApiId = "",
                    models = ClaudenCatalog.fallbackModels,
                )
            }
        }
        prefs.model = id
        _state.update { it.copy(model = id) }
    }

    fun refreshCustomApis() {
        _state.update { it.copy(customApis = customApiStore.list()) }
    }

    fun testCustomApi(rawBaseUrl: String, rawKey: String) {
        val baseUrl = prefs.normalizeBase(rawBaseUrl)
        if (baseUrl.isBlank()) {
            _state.update { it.copy(customApiError = "Укажите Base URL") }
            return
        }
        _state.update {
            it.copy(customApiBusy = true, customApiError = null, customApiModels = emptyList())
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    api.listModels(ApiConfig(Provider.Custom, baseUrl, rawKey.trim()))
                }
            }
            result.onSuccess { list ->
                _state.update {
                    it.copy(
                        customApiBusy = false,
                        customApiModels = list,
                        customApiError = null,
                        notice = "Подключение OK. Моделей: ${list.size}",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        customApiBusy = false,
                        customApiModels = emptyList(),
                        customApiError = error.message ?: "Не удалось получить модели",
                    )
                }
            }
        }
    }

    fun clearCustomApiTest() {
        _state.update { it.copy(customApiModels = emptyList(), customApiError = null) }
    }

    fun saveCustomApi(api: CustomApi) {
        val baseUrl = prefs.normalizeBase(api.baseUrl)
        if (baseUrl.isBlank()) {
            _state.update { it.copy(customApiError = "Укажите Base URL") }
            return
        }
        val models = if (api.models.isNotEmpty()) api.models else _state.value.customApiModels
        val item = api.copy(
            id = api.id.ifBlank { java.util.UUID.randomUUID().toString() },
            name = api.name.trim(),
            baseUrl = baseUrl,
            apiKey = api.apiKey.trim(),
            models = models,
        )
        val list = customApiStore.upsert(item)
        _state.update {
            it.copy(
                customApis = list,
                customApiBusy = false,
                customApiError = null,
                customApiModels = emptyList(),
                notice = "Свой API сохранён",
            )
        }
    }

    fun deleteCustomApi(id: String) {
        val list = customApiStore.remove(id)
        val wasActive = _state.value.customApiId == id
        if (wasActive) {
            prefs.provider = Provider.Clauden
            prefs.customApiId = ""
            prefs.model = "claude-opus-5-5"
            val token = _state.value.auth.token
            _state.update {
                it.copy(
                    provider = Provider.Clauden,
                    baseUrl = prefs.effectiveBaseUrl(Provider.Clauden),
                    apiKey = if (token.isNotBlank()) token else prefs.effectiveKey(Provider.Clauden),
                    customApiId = "",
                    model = "claude-opus-5-5",
                    models = ClaudenCatalog.fallbackModels,
                    notice = "Свой API удалён",
                )
            }
        }
        _state.update { it.copy(customApis = list) }
    }

    fun selectCustomModel(apiId: String, model: RemoteModel) {
        if (_state.value.streaming) {
            _state.update { it.copy(notice = "Дождитесь ответа или остановите его") }
            return
        }
        val item = _state.value.customApis.firstOrNull { it.id == apiId } ?: return
        prefs.provider = Provider.Custom
        prefs.baseUrl = item.baseUrl
        prefs.model = model.id

        prefs.customApiId = item.id
        prefs.authMode = AuthMode.Clauden
        _state.update {
            it.copy(
                provider = Provider.Custom,
                baseUrl = item.baseUrl,
                apiKey = item.apiKey,
                model = model.id,
                models = item.models,
                customApiId = item.id,
                authMode = AuthMode.Clauden,
                notice = "Свой API: ${item.label}",
            )
        }
    }

    private suspend fun refreshClaudeSession() {
        val current = claudeSession ?: return
        val result = withContext(Dispatchers.IO) {
            runCatching { claudeApi.discover(current) }
        }
        result.onSuccess { fresh ->
            val updated = fresh.copy(lastOkAt = System.currentTimeMillis())
            val changed = updated.plan != current.plan ||
                updated.email != current.email ||
                updated.orgId != current.orgId
            claudeSession = updated
            claudeStore.save(updated)
            _state.update {
                it.copy(
                    claudeEmail = updated.email,
                    claudePlan = updated.plan,
                    claudeLinked = true,
                )
            }

            val token = _state.value.auth.token
            if (changed && token.isNotBlank()) {
                val synced = withContext(Dispatchers.IO) {
                    runCatching {
                        authApi.updateClaudeProfile(
                            token = token,
                            email = updated.email,
                            claudePlan = updated.plan,
                            organizationId = updated.orgId,
                        )
                    }.getOrNull()
                }
                if (synced != null) {
                    _state.update {
                        it.copy(
                            auth = it.auth.copy(
                                claudeEmail = synced.claudeEmail,
                                claudePlan = synced.claudePlan,
                            ),
                        )
                    }
                }
            }

            val wasPaid = current.plan.equals("pro", true) || current.plan.equals("max", true)
            val isPaid = updated.plan.equals("pro", true) || updated.plan.equals("max", true)
            if (wasPaid != isPaid && _state.value.authMode == AuthMode.Claude) {
                refreshModels(silent = true)
            }
        }
    }

    fun refreshModels(silent: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.loadingModels) return
        val claude = snapshot.authMode == AuthMode.Claude
        _state.update { it.copy(loadingModels = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (claude) {
                    val session = claudeSession
                    if (session == null) {
                        Result.failure(
                            ClaudeApiException(ClaudeErrorKind.Unauthorized, "Нет сессии Claude")
                        )
                    } else {
                        runCatching {
                            claudeEngine.listModels(
                                EngineRequest(
                                    config = ApiConfig(Provider.Clauden, "", ""),
                                    claudeSession = session,
                                    model = "",
                                    history = emptyList(),
                                    maxTokens = 0,
                                )
                            )
                        }
                    }
                } else {
                    val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)
                    runCatching { api.listModels(config) }
                }
            }
            result.onSuccess { rawModels ->
                val models = when {
                    claude -> rawModels.ifEmpty { ClaudeCatalog.fallbackModels }
                    snapshot.provider == Provider.Custom -> rawModels
                    else -> ClaudenCatalog.sortModels(
                        rawModels.filter { it.id.startsWith("claude", ignoreCase = true) }
                    )
                }
                val oldClaudeDefaults = setOf(
                    "claude-sonnet-4-6",
                    "claude-sonnet-4-5",
                    "claude-haiku-4-5",
                    "claude-sonnet-4-5-20250929",
                    "claude-haiku-4-5-20251001",
                )
                val migrateDefault = claude &&
                    snapshot.model in oldClaudeDefaults &&
                    models.firstOrNull()?.id != snapshot.model
                val chosen = if (migrateDefault) {
                    ClaudeCatalog.preferredModel(models)
                } else {
                    snapshot.model.takeIf { m -> models.any { it.id == m } }
                        ?: if (claude) {
                            ClaudeCatalog.preferredModel(models)
                        } else {
                            AnthropicCatalog.preferredModel(models)
                        }
                }
                if (chosen.isNotBlank()) prefs.model = chosen
                if (snapshot.provider == Provider.Custom &&
                    snapshot.customApiId.isNotBlank() &&
                    models.isNotEmpty()
                ) {
                    snapshot.customApis
                        .firstOrNull { it.id == snapshot.customApiId }
                        ?.let { item ->
                            val list = customApiStore.upsert(item.copy(models = models))
                            _state.update { it.copy(customApis = list) }
                        }
                }
                _state.update {
                    it.copy(
                        models = models,
                        model = chosen.ifBlank { snapshot.model },
                        loadingModels = false,
                        notice = if (silent) it.notice else "Моделей: ${models.size}",
                    )
                }
            }.onFailure { t ->
                val fallback = when {
                    claude -> ClaudeCatalog.fallbackModels
                    snapshot.provider == Provider.Anthropic -> AnthropicCatalog.fallbackModels
                    snapshot.provider == Provider.Clauden -> ClaudenCatalog.fallbackModels
                    else -> emptyList()
                }
                _state.update {
                    val models = if (it.models.isEmpty()) fallback else it.models
                    val problem = if (claude && t is ClaudeApiException &&
                        (t.kind == ClaudeErrorKind.Unauthorized ||
                            t.kind == ClaudeErrorKind.Blocked ||
                            t.kind == ClaudeErrorKind.RateLimit)
                    ) {
                        ClaudeProblem(t.kind, t.message ?: "Claude недоступен", t.retryAt)
                    } else {
                        it.claudeProblem
                    }
                    it.copy(
                        loadingModels = false,
                        models = models,
                        model = it.model.ifBlank {
                            if (claude) {
                                ClaudeCatalog.preferredModel(models)
                            } else {
                                AnthropicCatalog.preferredModel(models)
                            }
                        },
                        claudeProblem = problem,

                        error = if (silent || claude) it.error else friendlyError(t),
                    )
                }
            }
        }
    }

    fun testConnection() {
        val snapshot = _state.value
        if (snapshot.provider == Provider.Custom && snapshot.baseUrl.isBlank()) {
            _state.update { it.copy(error = "Укажите Base URL для Custom-провайдера") }
            return
        }
        _state.update { it.copy(loadingModels = true, error = null, notice = null) }
        viewModelScope.launch {
            val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)
            val result = withContext(Dispatchers.IO) { runCatching { api.listModels(config) } }
            result.onSuccess { rawModels ->
                val models = if (snapshot.provider == Provider.Custom) {
                    rawModels
                } else {
                    ClaudenCatalog.sortModels(
                        rawModels.filter { it.id.startsWith("claude", ignoreCase = true) }
                    )
                }
                val chosen = snapshot.model.takeIf { m -> models.any { it.id == m } }
                    ?: AnthropicCatalog.preferredModel(models)
                if (chosen.isNotBlank()) prefs.model = chosen
                _state.update {
                    it.copy(
                        loadingModels = false,
                        models = models,
                        model = chosen.ifBlank { snapshot.model },
                        notice = "Подключение OK. Моделей: ${models.size}",
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loadingModels = false,
                        error = "Не удалось подключиться: ${friendlyError(t)}",
                    )
                }
            }
        }
    }

    fun sendArtifactEdit(
        chatId: String,
        title: String,
        instruction: String,
        identifier: String = "",
    ) {
        val clean = instruction.trim()
        if (clean.isEmpty()) return
        if (_state.value.streaming) {
            _state.update { it.copy(notice = "Дождитесь ответа на предыдущее сообщение") }
            return
        }
        if (chatId.isNotBlank() && _state.value.activeId != chatId) openChat(chatId)
        val sameId = identifier.trim().takeIf {
            it.isNotBlank() &&
                !it.startsWith("artifact-", ignoreCase = true) &&
                !it.startsWith("code-", ignoreCase = true)
        }
        val safeTitle = title.replace("\"", "'")
        send(
            "Обнови артефакт «$safeTitle»: $clean\n" +
                "Не создавай новый артефакт и не меняй его название. Верни обновлённый " +
                "артефакт целиком в теге <artifact" +
                (sameId?.let { " identifier=\"$it\"" } ?: "") +
                " title=\"$safeTitle\">…</artifact> с тем же идентификатором, названием и типом."
        )
    }

    fun send(
        rawText: String,
        attachments: List<Attachment> = emptyList(),
        calendarContext: String = "",
    ) {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        sendInternal(
            rawText,
            appendUser = true,
            attachments = attachments,
            calendarContext = calendarContext,
        )
    }

    fun retryLast() {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        val active = _state.value.active ?: return
        val lastUserIndex = active.messages.indexOfLast { it.role == "user" }
        if (lastUserIndex < 0) return
        val text = active.messages[lastUserIndex].content
        val attachments = active.messages[lastUserIndex].attachments
        val trimmed = active.messages.take(lastUserIndex + 1)
        mutateConversation(active.id) { it.copy(messages = trimmed) }
        sendInternal(
            text,
            appendUser = false,
            attachments = attachments,
            calendarContext = lastCalendarContext,
        )
    }

    private fun sendInternal(
        rawText: String,
        appendUser: Boolean,
        attachments: List<Attachment> = emptyList(),
        calendarContext: String = "",
        toolResult: String = "",
        targetChatId: String? = null,
    ) {
        val text = rawText.trim()
        lastCalendarContext = calendarContext
        if (appendUser) _state.update { it.copy(pendingLocation = null) }
        val snapshot = _state.value
        if (text.isEmpty() && attachments.isEmpty() && toolResult.isBlank()) return
        if (snapshot.streaming) return
        if (snapshot.recoveringId == snapshot.activeId && snapshot.activeId != null) return

        val claudeMode = snapshot.authMode == AuthMode.Claude
        if (claudeMode) {

            if (snapshot.claudeProblem != null) {
                _state.update { it.copy(claudeProblem = null) }
            }
            if (claudeSession?.isUsable != true) {
                _state.update {
                    it.copy(
                        error = null,
                        claudeProblem = ClaudeProblem(
                            ClaudeErrorKind.Unauthorized,
                            "Сессия Claude истекла. Войдите заново.",
                        ),
                    )
                }
                return
            }
        } else if (snapshot.provider == Provider.Anthropic && snapshot.apiKey.isBlank()) {
            _state.update { it.copy(error = "Добавьте Anthropic API key в настройках") }
            return
        } else if (snapshot.provider == Provider.Clauden && isFreePlan(snapshot.settings.plan)) {
            val linked = snapshot.claudeLinked ||
                snapshot.auth.authMode == "claude" ||
                snapshot.auth.claudeEmail.isNotBlank()
            _state.update {
                it.copy(
                    error = if (linked) {
                        "Аккаунт Claude привязан, но сессия на этом устройстве истекла. " +
                            "Войдите в Claude заново."
                    } else {
                        FREE_PLAN_CHAT_BLOCKED
                    },
                    claudeProblem = if (linked) {
                        ClaudeProblem(
                            ClaudeErrorKind.Unauthorized,
                            "Сессия Claude истекла. Войдите заново.",
                        )
                    } else {
                        it.claudeProblem
                    },
                )
            }
            return
        }

        val model = if (claudeMode) {
            val models = snapshot.models.ifEmpty { ClaudeCatalog.fallbackModels }
            snapshot.model.takeIf { id -> models.any { it.id == id } }
                ?: ClaudeCatalog.preferredModel(models)
        } else {
            snapshot.model.ifBlank { AnthropicCatalog.preferredModel(snapshot.models) }
        }
        if (model.isBlank()) {
            _state.update { it.copy(error = "Не выбрана модель: откройте настройки") }
            return
        }

        val now = System.currentTimeMillis()
        val turnId = UUID.randomUUID().toString()
        if (targetChatId != null &&
            snapshot.conversations.none { it.id == targetChatId }
        ) {
            return
        }
        val chatId = targetChatId ?: snapshot.activeId ?: run {

            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                title = if (appendUser) {
                    makeTitle(text.ifBlank { attachments.firstOrNull()?.name ?: "Вложение" })
                } else {
                    "Новый чат"
                },
                updatedAt = now,
                projectId = snapshot.draftProjectId,
            )
            _state.update {
                it.copy(
                    conversations = listOf(conversation) + it.conversations,
                    activeId = conversation.id,
                )
            }
            prefs.lastConversationId = conversation.id
            conversation.id
        }
        if (!claudeMode && snapshot.provider != Provider.Custom &&
            snapshot.settings.notifyReplies
        ) {
            ClaudenReplyService.start(getApplication())
        }

        val steps = buildList {
            if (appendUser) {
                add(
                    ChatMessage(
                        role = "user",
                        content = text,
                        ts = now,
                        attachments = attachments,
                    )
                )
            }
            if (toolResult.isNotBlank()) {
                add(
                    ChatMessage(
                        role = "user",
                        content = toolResult,
                        ts = now,
                        hidden = true,
                    )
                )
            }

            add(
                ChatMessage(
                    role = "assistant",
                    content = "",
                    ts = now + 1,
                    complete = false,
                    turnId = turnId,
                )
            )
        }
        mutateConversation(chatId) { conversation ->
            conversation.copy(
                messages = conversation.messages + steps,
                title = if (appendUser && conversation.messages.isEmpty()) {
                    makeTitle(text.ifBlank { attachments.firstOrNull()?.name ?: "Вложение" })
                } else {
                    conversation.title
                },
                updatedAt = now,
            )
        }
        _state.update { it.copy(streaming = true, error = null, recoveringId = null) }
        persist()

        val history = _state.value.conversations
            .firstOrNull { it.id == chatId }
            ?.messages
            ?.filter { it.role == "user" || it.content.isNotBlank() }
            .orEmpty()

        val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)
        val run = StreamRun(chatId)
        activeRun = run
        val stepMap = LinkedHashMap<String, ChatStep>()
        val fileMap = LinkedHashMap<String, ChatFile>()
        val actionMap = LinkedHashMap<String, ChatAction>()
        var thinkingMs = 0L

        fun finishRun() {
            setAssistantMeta(chatId, thinkingMs)
            markAssistantComplete(chatId)
            _state.update { it.copy(streaming = false, claudeProblem = null) }
            activeRun = null
            engineCall = null
            persist()
            maybeResolvePendingLocation(chatId)
            maybeAutoGenerateMemory()
            ClaudenReplyService.stop(getApplication())
        }

        val memoryText = if (claudeMode) "" else buildString {
            append(buildMemory(chatId))
            if (calendarContext.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(calendarContext)
            }
        }
        val systemText = when {
            claudeMode -> ""
            snapshot.provider == Provider.Custom -> buildClaudeSystemPrompt()
            else -> buildSystemPrompt()
        }
        val projectText = if (claudeMode) "" else buildProjectContext()
        val claudeConversationId = if (claudeMode) {
            snapshot.conversations.firstOrNull { it.id == chatId }
                ?.claudeConversationId
                .orEmpty()
        } else {
            ""
        }

        val callbacks = EngineCallbacks(

            onDelta = { piece ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch
                    run.target.append(piece)
                    kickReveal(run)
                }
            },
            onThinkingMs = { value ->
                viewModelScope.launch { if (activeRun === run) thinkingMs = value }
            },
            onThinking = { piece ->
                viewModelScope.launch {
                    if (activeRun !== run || piece.isBlank()) return@launch
                    appendAssistantThinking(chatId, piece)
                }
            },
            onStep = { step ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch
                    stepMap[step.id] = step
                    setAssistantSteps(chatId, stepMap.values.toList())
                }
            },
            onFile = { file ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch
                    fileMap["${file.id}/${file.name}"] = file
                    setAssistantFiles(chatId, fileMap.values.toList())
                }
            },
            onSources = { sources ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch
                    setAssistantSources(chatId, sources)
                }
            },
            onAction = { action ->
                viewModelScope.launch {
                    if (activeRun !== run && action.type != "location") return@launch
                    if (action.type == "location") {
                        val prepared = action.copy(
                            id = action.id.ifBlank { UUID.randomUUID().toString() },
                        )
                        val needsPermission = !LocationProvider.hasPermission(getApplication())
                        _state.update {
                            it.copy(
                                pendingLocation = PendingLocation(
                                    actionId = prepared.id,
                                    chatId = chatId,
                                    accuracy = prepared.accuracy.ifBlank { "approximate" },
                                    reason = prepared.reason,
                                    needsPermission = needsPermission,
                                )
                            )
                        }
                        val waiting = prepared.copy(
                            status = if (needsPermission) "waiting" else "running",
                            detail = if (needsPermission) {
                                "Нужно разрешение на геопозицию"
                            } else {
                                "Определяю местоположение…"
                            },
                        )
                        actionMap[waiting.id] = waiting
                        setAssistantActions(chatId, actionMap.values.toList())
                        if (!needsPermission && !_state.value.streaming) {
                            maybeResolvePendingLocation(chatId)
                        }
                    } else {
                        val result = DeviceActions.execute(getApplication(), action)
                        if (result.type == "task" && result.status == "done") {
                            addTask(result.title.ifBlank { result.text })
                        }
                        actionMap[result.id.ifBlank { result.type }] = result
                        setAssistantActions(chatId, actionMap.values.toList())
                    }
                }
            },
            onDone = { full ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch

                    if (full.isBlank()) {
                        val hasSteps = stepMap.isNotEmpty() ||
                            fileMap.isNotEmpty() ||
                            actionMap.isNotEmpty()
                        if (!hasSteps) {
                            run.done = true
                            val message = if (claudeMode) {
                                "Claude вернул пустой ответ. Попробуйте ещё раз."
                            } else {
                                "Модель вернула пустой ответ. Попробуйте ещё раз."
                            }
                            run.onFinished = {
                                setAssistantMeta(chatId, thinkingMs)
                                markAssistantFailed(chatId, message)
                                _state.update {
                                    it.copy(
                                        streaming = false,
                                        error = if (claudeMode) null else message,
                                        claudeProblem = if (claudeMode) {
                                            ClaudeProblem(ClaudeErrorKind.Other, message)
                                        } else {
                                            it.claudeProblem
                                        },
                                    )
                                }
                                activeRun = null
                                engineCall = null
                                ClaudenReplyService.stop(getApplication())
                                persist()
                            }
                            kickReveal(run)
                            return@launch
                        }

                        run.target.clear()
                        run.target.append("Готово.")
                        if (run.shown > run.target.length) run.shown = run.target.length
                        run.done = true
                        run.onFinished = { finishRun() }
                        kickReveal(run)
                        return@launch
                    }
                    run.target.clear()
                    run.target.append(full)
                    if (run.shown > run.target.length) run.shown = run.target.length
                    run.done = true
                    run.onFinished = { finishRun() }
                    if (!appForeground && !claudeMode &&
                        _state.value.settings.notifyReplies && full.isNotBlank()
                    ) {
                        val chatTitle = _state.value.conversations
                            .firstOrNull { it.id == chatId }
                            ?.title
                            .orEmpty()
                        val heading = if (chatTitle.isBlank()) {
                            "Clauden ответил"
                        } else {
                            "Clauden ответил · $chatTitle"
                        }
                        ClaudenReplyNotifications.reply(getApplication(), chatId, heading, full)
                    }
                    kickReveal(run)
                }
            },
            onError = { t ->
                viewModelScope.launch {
                    if (activeRun !== run) return@launch
                    run.done = true
                    val claudeError = t as? ClaudeApiException
                    if (claudeMode) {

                        val friendly = claudeAuthMessage(t)
                        run.onFinished = {
                            setAssistantMeta(chatId, thinkingMs)
                            markAssistantFailed(chatId, friendly)
                            _state.update {
                                it.copy(
                                    streaming = false,
                                    error = null,
                                    claudeProblem = ClaudeProblem(
                                        kind = claudeError?.kind ?: ClaudeErrorKind.Network,
                                        message = claudeError?.message ?: friendly,
                                        retryAt = claudeError?.retryAt ?: 0L,
                                    ),
                                )
                            }
                            activeRun = null
                            engineCall = null
                            ClaudenReplyService.stop(getApplication())
                            persist()
                        }
                    } else if (isTransient(t)) {

                        run.onFinished = {
                            setAssistantMeta(chatId, thinkingMs)
                            _state.update {
                                it.copy(
                                    streaming = false,
                                    notice = "Связь прервалась — забираю ответ с сервера",
                                )
                            }
                            activeRun = null
                            engineCall = null
                            scheduleRecovery(chatId)
                        }
                    } else {
                        val friendly = friendlyError(
                            t,
                            freeSupport = snapshot.provider != Provider.Custom,
                        )
                        run.onFinished = {
                            setAssistantMeta(chatId, thinkingMs)

                            markAssistantFailed(chatId, friendly)
                            _state.update {
                                it.copy(
                                    streaming = false,
                                    error = friendly,
                                )
                            }
                            activeRun = null
                            engineCall = null
                            persist()
                        }
                    }
                    kickReveal(run)
                }
            },
            onConversation = { remoteId ->
                viewModelScope.launch {
                    mutateConversation(chatId) { it.copy(claudeConversationId = remoteId) }
                    persist()
                }
            },
        )

        val request = EngineRequest(
            config = config,
            claudeSession = if (claudeMode) claudeSession else null,
            model = model,
            history = history,
            maxTokens = snapshot.effort.maxTokens,
            memory = memoryText,
            system = systemText,
            project = projectText,
            conversationId = chatId,
            turnId = if (claudeMode) "" else turnId,
            claudeConversationId = claudeConversationId,
            webSearch = snapshot.settings.webSearch,
            thinking = snapshot.settings.claudeThinking,
            attachments = attachments,
            connectors = snapshot.customize.installedConnectorList.filter { it.mcpUrl.isNotBlank() },
        )
        val engine = if (claudeMode) claudeEngine else claudenEngine
        if (claudeMode) claudeDiagnostics.reset()
        engineCall = engine.start(request, callbacks)
    }

    private fun kickReveal(run: StreamRun) {
        if (revealJob?.isActive == true) return
        revealJob = viewModelScope.launch {
            var lastAt = SystemClock.elapsedRealtime()
            var carry = 0f
            while (activeRun === run) {
                val backlog = run.target.length - run.shown
                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastAt).coerceIn(0L, 120L).toFloat()
                lastAt = now
                if (backlog > 0) {

                    val speed = (58f + backlog * 4f).coerceAtMost(1200f)
                    carry += speed * dt / 1000f
                    val whole = carry.toInt()
                    if (whole > 0) {
                        carry -= whole
                        run.shown = minOf(run.target.length, run.shown + whole)
                        setAssistantText(run.chatId, run.target.substring(0, run.shown))
                    }

                    delay(16)
                    continue
                }
                carry = 0f
                if (!run.done) {
                    delay(24)
                    continue
                }
                val finish = run.onFinished
                run.onFinished = null
                finish?.invoke()
                break
            }
            revealJob = null
        }
    }

    fun stop() {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        val snapshot = _state.value
        val chatId = snapshot.activeId
        val conversation = snapshot.conversations.firstOrNull { it.id == chatId }
        val turnId = conversation?.messages
            ?.lastOrNull { it.role == "assistant" }
            ?.turnId
            .orEmpty()

        cancelActiveSend(markComplete = false)
        if (chatId != null) {
            markAssistantInterrupted(chatId)
            persist()
        }
        if (chatId != null && turnId.isNotBlank() &&
            snapshot.authMode != AuthMode.Claude &&
            snapshot.provider != Provider.Custom
        ) {
            val config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { api.stopTurn(config, chatId, turnId) }
            }
        }
    }

    private fun cancelActiveSend(markComplete: Boolean) {
        val run = activeRun
        activeRun = null
        run?.cancelled = true
        _state.update { it.copy(streaming = false, recoveringId = null) }
        revealJob?.cancel()
        revealJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        engineCall?.cancel()
        engineCall = null
        if (run != null) {
            if (markComplete) markAssistantComplete(run.chatId)
            persist()
        }
        ClaudenReplyService.stop(getApplication())
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun showFreePlanBlocked() = _state.update { it.copy(error = FREE_PLAN_CHAT_BLOCKED) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun notify(text: String) = _state.update { it.copy(notice = text) }

    private fun markAssistantInterrupted(chatId: String) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(
                    complete = true,
                    interrupted = true,
                    liveElapsedMs = 0L,
                )
            }
            conversation.copy(messages = messages)
        }
    }

    private fun markAssistantComplete(chatId: String) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) messages[index] = messages[index].copy(complete = true)
            conversation.copy(messages = messages)
        }
    }

    private fun markAssistantFailed(chatId: String, message: String) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                val current = messages[index]
                messages[index] = current.copy(
                    content = current.content.ifBlank { message },
                    complete = true,
                )
            }
            conversation.copy(messages = messages)
        }
    }

    private fun isTransient(t: Throwable): Boolean {

        val rawMessage = t.message.orEmpty().lowercase()
        if (
            rawMessage.contains("turn timed out") ||
            rawMessage.contains("turn stalled") ||
            rawMessage.contains("ответ готовился слишком долго")
        ) {
            return false
        }

        if (t.message.orEmpty().trimStart().startsWith("HTTP ")) {
            val code = t.message.orEmpty()
                .trimStart()
                .removePrefix("HTTP ")
                .takeWhile { it.isDigit() }
                .toIntOrNull()
            return code != null && code >= 500
        }
        if (t is UnknownHostException || t is ConnectException || t is SocketTimeoutException ||
            t is java.io.EOFException
        ) {
            return true
        }
        val message = t.message.orEmpty().lowercase()
        return listOf(
            "unable to resolve host",
            "no address associated",
            "failed to connect",
            "connection refused",
            "connection reset",
            "connection abort",
            "software caused connection",
            "stream was reset",
            "unexpected end",
            "timed out",
            "timeout",
            "econnreset",
            "network is unreachable",
            "network error",
            "networkerror",
            "failed to fetch",
            "fetch failed",
            "load failed",
            "socket hang up",
            "other side closed",
            "enotfound",
            "eai_again",
            "etimedout",
        ).any { message.contains(it) }
    }

    private fun isNetworkMessage(text: String): Boolean {
        val lower = text.lowercase()
        return isTransient(IllegalStateException(text)) ||
            lower.contains("нет соединения") ||
            lower.contains("соединение прервалось") ||
            lower.contains("сервер недоступен") ||
            lower.contains("сервер не ответил")
    }

    private fun isFreePlan(plan: String): Boolean =
        plan.isBlank() || plan.equals("Free", ignoreCase = true)

    private fun isFreePlan(): Boolean = isFreePlan(_state.value.settings.plan)

    private fun friendlyError(
        t: Throwable,
        freeSupport: Boolean = false,
        claudeMode: Boolean = false,
    ): String {
        if (freeSupport && !claudeMode && isFreePlan()) {
            return FREE_PLAN_CHAT_BLOCKED
        }
        val message = t.message.orEmpty().trim()
        val lower = message.lowercase()
        val publicMessage = message
        return when {
            t is UnknownHostException || lower.contains("unable to resolve host") ||
                lower.contains("no address associated") ->
                "Нет соединения с интернетом"

            lower.contains("stream ended without finish_reason") ||
                lower.contains("incomplete-stream") ||
                lower.contains("invalid stream event") ||
                lower.contains("streaming response failed") ->
                "Сервер не успел закончить ответ. Попробуйте ещё раз — работа сохранится."

            lower.contains("tool call stream state") ||
                lower.contains("tool output parsing failed") ->
                "Модель не смогла завершить ответ. Попробуйте ещё раз."

            lower.contains("turn timed out") || lower.contains("turn stalled") ->
                "Ответ готовился слишком долго"

            lower.contains("network error") || lower.contains("networkerror") ||
                lower.contains("failed to fetch") || lower.contains("fetch failed") ||
                lower.contains("load failed") || lower.contains("socket hang up") ||
                lower.contains("other side closed") || lower.contains("enotfound") ||
                lower.contains("eai_again") ->
                "Соединение прервалось"

            t is SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout") ->
                "Сервер не ответил вовремя"

            t is ConnectException || lower.contains("failed to connect") ||
                lower.contains("connection refused") || lower.contains("network is unreachable") ->
                "Сервер недоступен — проверьте соединение"

            lower.contains("connection abort") || lower.contains("connection reset") ||
                lower.contains("stream was reset") || lower.contains("unexpected end") ->
                "Соединение прервалось"

            lower.contains("http 401") || lower.contains("unauthorized") ->
                "Неверный ключ доступа"

            lower.contains("subscription_required") ||
                lower.contains("на тарифе free") ->
                FREE_PLAN_CHAT_BLOCKED

            lower.contains("модель доступна на платных") ||
                lower.contains("model_locked") ->
                "Эта модель доступна на платных тарифах"

            lower.contains("http 403") || lower.contains("forbidden") ->
                "Доступ запрещён"

            lower.contains("http 429") || lower.contains("too many") ->
                "Слишком много запросов — попробуйте позже"

            lower.contains("empty answer") ->
                "Модель вернула пустой ответ. Попробуйте ещё раз"

            lower.contains("tool call instead of text") ->
                "Модель не смогла дать ответ. Попробуйте ещё раз"

            lower.contains("upstream") || lower.contains("provider") ->
                "Сервер временно недоступен"

            lower.startsWith("http 5") || lower.contains("http 502") ||
                lower.contains("http 503") || lower.contains("http 504") ->
                "Сервер временно недоступен"

            publicMessage.isBlank() -> "Ошибка запроса"
            else -> publicMessage
        }
    }

    private fun setAssistantActions(chatId: String, actions: List<ChatAction>) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(actions = actions)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun setAssistantSources(chatId: String, sources: List<ChatSource>) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(sources = sources)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun setAssistantFiles(chatId: String, files: List<ChatFile>) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(files = files)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun setAssistantSteps(chatId: String, steps: List<ChatStep>) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(steps = steps)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun setAssistantMeta(chatId: String, ms: Long) {
        if (ms == 0L) return
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(thinkingMs = ms)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun appendAssistantThinking(chatId: String, piece: String) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                val current = messages[index].thinking
                messages[index] = messages[index].copy(
                    thinking = (current + piece).takeLast(60_000),
                )
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun setAssistantText(chatId: String, text: String) {
        mutateConversation(chatId) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(content = text)
            }
            conversation.copy(messages = messages, updatedAt = System.currentTimeMillis())
        }
    }

    private fun mutateConversation(id: String, block: (Conversation) -> Conversation) {
        _state.update { s ->
            s.copy(
                conversations = s.conversations.map { if (it.id == id) block(it) else it }
            )
        }
    }

    private fun maybeAutoGenerateMemory() {
        val snapshot = _state.value
        if (memoryAutoAttempted) return
        if (!snapshot.settings.memory || snapshot.memoryBusy) return
        val latest = snapshot.memory.maxOfOrNull { it.createdAt } ?: 0L
        val day = 24 * 60 * 60 * 1000L
        val hasNewChats = snapshot.conversations.any { chat ->
            chat.updatedAt > latest && chat.messages.any { it.role == "user" && it.content.isNotBlank() }
        }
        val stale = snapshot.memory.isEmpty() ||
            (hasNewChats && System.currentTimeMillis() - latest > day)
        if (!stale) return
        if (snapshot.conversations.count { chat ->
                chat.messages.any { it.role == "user" && it.content.isNotBlank() }
            } < 2
        ) {
            return
        }
        memoryAutoAttempted = true
        generateMemory(quiet = true)
    }

    fun generateMemory(quiet: Boolean = false) {
        if (!quiet) Haptics.tap(getApplication(), _state.value.settings.haptics)
        val snapshot = _state.value
        if (snapshot.memoryBusy) return
        if (!snapshot.settings.memory) {
            _state.update { it.copy(notice = "Сначала включите память в настройках") }
            return
        }
        val digest = snapshot.conversations
            .sortedByDescending { it.updatedAt }
            .take(10)
            .joinToString("\n\n") { chat ->
                val textMessages = chat.messages
                    .filter { it.role == "user" || it.content.isNotBlank() }
                val picked = (textMessages.take(2) + textMessages.takeLast(6))
                    .distinctBy { it.content.take(80) }
                picked
                    .joinToString("\n") { message ->
                        (if (message.role == "user") "Вы: " else "Clauden: ") +
                            message.content.replace('\n', ' ').take(320)
                    }
                    .let { "Чат «${chat.title}»:\n$it" }
            }
            .take(9000)
        if (digest.isBlank()) {
            _state.update { it.copy(notice = "Пока нет чатов, из которых можно собрать память") }
            return
        }
        val existing = snapshot.memory.joinToString("\n") { "- ${it.text}" }.take(4000)
        _state.update { it.copy(memoryBusy = true) }
        viewModelScope.launch {
            val answer = withContext(Dispatchers.IO) {
                runCatching {
                    api.complete(
                        config = ApiConfig(snapshot.provider, snapshot.baseUrl, snapshot.apiKey),
                        model = snapshot.model.ifBlank { "claude-sonnet-5-5" },
                        prompt = buildString {
                            appendLine("Ниже — фрагменты чатов пользователя. Выдели устойчивые факты о нём,")
                            appendLine("его работе, инструментах, проектах и предпочтениях. Не включай разовые просьбы,")
                            appendLine("пароли, адреса, номера документов и всё, что похоже на секрет.")
                            appendLine("Не записывай временные состояния: пустая рабочая папка, ошибки")
                            appendLine("загрузки или чтения вложений, «файл не дошёл» — это не факт о человеке.")
                            appendLine("Верни только список строк, каждая начинается с «- », не больше 10 пунктов.")
                            if (existing.isNotBlank()) {
                                appendLine("Уже сохранено (не повторяй):")
                                appendLine(existing)
                            }
                            appendLine("Фрагменты:")
                            append(digest)
                        },
                        system = "Ты ведёшь долговременную память ассистента. " +
                            "Пиши короткие факты от третьего лица, без оценок и домыслов.",
                        maxTokens = 1200,
                    )
                }.getOrNull()
            }
            val parsed = parseMemoryLines(answer.orEmpty())
            val fallback = if (parsed.isEmpty()) localMemoryCandidates(snapshot) else emptyList()
            val fresh = (parsed + fallback)
                .map { it.trim() }
                .filter { it.length in 8..300 }
                .distinctBy { it.lowercase() }
                .filter { candidate ->
                    snapshot.memory.none { it.text.equals(candidate, ignoreCase = true) }
                }
                .take(10)
            if (fresh.isEmpty()) {
                _state.update {
                    it.copy(
                        memoryBusy = false,
                        notice = "Новых фактов не нашлось — память уже актуальна",
                    )
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val items = fresh.mapIndexed { index, text ->
                MemoryItem(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    sourceTitle = "Из чатов",
                    createdAt = now + index,
                )
            }
            val next = items + _state.value.memory
            memoryStore.save(next)
            _state.update {
                it.copy(memory = next, memoryBusy = false, notice = "Запомнено фактов: ${items.size}")
            }
        }
    }

    fun deleteMemory(id: String) {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        val next = _state.value.memory.filterNot { it.id == id }
        memoryStore.save(next)
        _state.update { it.copy(memory = next) }
    }

    fun clearMemory() {
        Haptics.tap(getApplication(), _state.value.settings.haptics)
        memoryStore.save(emptyList())
        _state.update { it.copy(memory = emptyList(), notice = "Память очищена") }
    }

    private fun parseMemoryLines(text: String): List<String> =
        text.lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .removePrefix("•")
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() && !it.startsWith("```") && !it.endsWith(":") }
            .filter { it.length in 8..300 }
            .toList()

    private fun localMemoryCandidates(snapshot: ChatUiState): List<String> {
        val markers = listOf(
            "я ", "мне ", "меня ", "мой ", "моя ", "мои ", "работаю", "живу",
            "люблю", "не люблю", "предпочитаю", "зовут", "учусь", "проект",
        )
        return snapshot.conversations
            .flatMap { it.messages.filter { message -> message.role == "user" } }
            .flatMap { message -> message.content.split('.', '!', '?', '\n') }
            .map { it.trim() }
            .filter { sentence ->
                sentence.length in 12..220 && markers.any { sentence.lowercase().contains(it) }
            }
            .distinctBy { it.lowercase() }
            .take(8)
    }

    fun testLocation() {
        if (!LocationProvider.hasPermission(getApplication())) {
            _state.update { it.copy(notice = "Сначала разрешите доступ к геопозиции") }
            return
        }
        _state.update { it.copy(notice = "Определяю местоположение…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    LocationProvider.get(getApplication(), precise = true)
                }.getOrElse {
                    LocationResult(ok = false, error = "location_unavailable")
                }
            }
            _state.update {
                it.copy(
                    notice = if (result.ok) {
                        "Геопозиция: ${result.label} (${result.accuracy})"
                    } else {
                        result.label
                    }
                )
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val pending = _state.value.pendingLocation ?: return
        if (!granted) {
            finishLocationWithError(pending, "permission_denied")
            return
        }
        val next = pending.copy(needsPermission = false)
        _state.update { it.copy(pendingLocation = next) }
        resolveLocation(next)
    }

    fun denyLocation() {
        val pending = _state.value.pendingLocation ?: return
        finishLocationWithError(pending, "permission_denied")
    }

    fun dismissPendingLocation() = _state.update { it.copy(pendingLocation = null) }

    private fun maybeResolvePendingLocation(chatId: String) {
        val pending = _state.value.pendingLocation ?: return
        if (pending.chatId != chatId) return
        if (pending.resolving) return
        if (pending.denied) {
            finishLocationWithError(pending, "permission_denied")
            return
        }
        if (pending.needsPermission) return
        resolveLocation(pending)
    }

    private fun resolveLocation(pending: PendingLocation) {
        if (pending.resolving) return
        if (_state.value.streaming) return
        _state.update {
            it.copy(pendingLocation = it.pendingLocation?.copy(resolving = true))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    LocationProvider.get(getApplication(), pending.accuracy == "precise")
                }.getOrElse {
                    LocationResult(ok = false, error = "location_unavailable")
                }
            }
            if (_state.value.pendingLocation?.actionId != pending.actionId) return@launch
            updateLocationAction(pending, result)
            _state.update { it.copy(pendingLocation = null) }
            continueWithLocation(pending, result)
        }
    }

    private fun finishLocationWithError(pending: PendingLocation, error: String) {
        val next = pending.copy(needsPermission = false, denied = true, resolving = false)
        _state.update { it.copy(pendingLocation = next) }
        if (_state.value.streaming) return
        val result = LocationResult(ok = false, error = error)
        updateLocationAction(next, result)
        _state.update { it.copy(pendingLocation = null) }
        continueWithLocation(next, result)
    }

    private fun updateLocationAction(pending: PendingLocation, result: LocationResult) {
        mutateConversation(pending.chatId) { conversation ->
            conversation.copy(
                messages = conversation.messages.map { message ->
                    message.copy(
                        actions = message.actions.map { action ->
                            if (action.id != pending.actionId) {
                                action
                            } else {
                                action.copy(
                                    status = if (result.ok) "done" else "failed",
                                    detail = result.label,
                                    latitude = result.latitude.takeIf { result.ok },
                                    longitude = result.longitude.takeIf { result.ok },
                                )
                            }
                        }
                    )
                }
            )
        }
        persist()
    }

    private fun continueWithLocation(pending: PendingLocation, result: LocationResult) {
        if (_state.value.conversations.none { it.id == pending.chatId }) return
        val payload = org.json.JSONObject().apply {
            put("ok", result.ok)
            put("requested_accuracy", pending.accuracy)
            if (result.ok) {
                put("latitude", result.latitude)
                put("longitude", result.longitude)
                put("accuracy", result.accuracy)
                put("accuracy_m", result.accuracyMeters.toDouble())
                put("provider", result.provider)
                if (result.city.isNotBlank()) put("city", result.city)
                if (result.district.isNotBlank()) put("district", result.district)
                if (result.country.isNotBlank()) put("country", result.country)
            } else {
                put("error", result.error)
            }
        }
        val toolResult =
            "<tool_result name=\"get_user_location\">\n$payload\n</tool_result>"
        sendInternal(
            rawText = "",
            appendUser = false,
            toolResult = toolResult,
            targetChatId = pending.chatId,
        )
    }

    private fun buildClaudeSystemPrompt(): String {
        val settings = _state.value.settings
        val now = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale("ru"))
            .format(java.util.Date())
        return buildString {
            appendLine("Ты — Clauden, полезный ИИ-ассистент. Отвечай на языке пользователя.")
            appendLine("Сейчас: $now.")
            if (settings.nickname.isNotBlank()) {
                appendLine("Обращайся к пользователю: ${settings.nickname}.")
            }
            if (settings.fullName.isNotBlank() && settings.fullName != settings.nickname) {
                appendLine("Полное имя: ${settings.fullName}.")
            }
            if (settings.work.isNotBlank()) {
                appendLine("Чем занимается: ${settings.work}.")
            }
            if (settings.preferenceNote.isNotBlank()) {
                appendLine("Пожелания пользователя (учитывай в первую очередь):")
                appendLine(settings.preferenceNote.take(3000))
            }
        }.trim()
    }

    private fun buildSystemPrompt(): String {
        val snapshot = _state.value
        val settings = snapshot.settings
        val data = snapshot.customize
        val now = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale("ru"))
            .format(java.util.Date())
        return buildString {
            appendLine("Ты Clauden — полезный ИИ-ассистент. Отвечай на языке пользователя, кратко и по делу.")
            appendLine("Сейчас: $now.")
            if (settings.nickname.isNotBlank()) {
                appendLine("Обращайся к пользователю: ${settings.nickname}.")
            }
            if (settings.fullName.isNotBlank() && settings.fullName != settings.nickname) {
                appendLine("Полное имя: ${settings.fullName}.")
            }
            if (settings.work.isNotBlank()) {
                appendLine("Чем занимается: ${settings.work}.")
            }
            if (settings.preferenceNote.isNotBlank()) {
                appendLine("Инструкции пользователя (выполняй в первую очередь):")
                appendLine(settings.preferenceNote.take(3000))
            }
            appendLine(
                if (settings.artifacts) {
                    "Артефакты включены: код и документы оформляй отдельными блоками."
                } else {
                    "Артефакты выключены: не создавай отдельные окна артефактов."
                }
            )
            appendLine(
                if (settings.codeExecution) {
                    "Выполнение кода включено: можешь писать и запускать код, создавать файлы."
                } else {
                    "Выполнение кода выключено: только объясняй код, не запускай его."
                }
            )
            appendLine(
                "Долгие задачи (рендер видео, сборка больших файлов, архивы) выполняй " +
                    "прямо в этом ходе и дождись завершения. Не отвечай «рендерю в фоне»: " +
                    "пользователь должен получить готовый файл в ответе."
            )
            if (!settings.visualizations) {
                appendLine("Не создавай интерактивные визуализации и графики в чате.")
            }
            if (!settings.aiArtifacts) {
                appendLine("Не встраивай Clauden внутрь артефактов.")
            }
            if (!settings.networkEgress) {
                appendLine("Не устанавливай пакеты и не ходи в сеть при выполнении кода.")
            }
            appendLine(
                if (settings.webSearch) {
                    "Веб-поиск доступен: используй его, когда нужны свежие факты, и указывай источники."
                } else {
                    "Веб-поиск выключен: отвечай по знаниям и явно предупреждай, если данные могли устареть."
                }
            )
            if (settings.memory && snapshot.memory.isNotEmpty()) {
                appendLine("Долговременная память о пользователе:")
                snapshot.memory.take(40).forEach { appendLine("- ${it.text}") }
            }
            val usedSkillIds = LinkedHashSet<String>()
            data.enabledSkillList.forEach { skill ->
                usedSkillIds += skill.id
                appendLine("Активный навык «${skill.title}»:")
                appendLine(skill.instructions.take(2400))
            }
            data.enabledPluginList.forEach { plugin ->
                appendLine("Активный плагин «${plugin.title}»:")
                if (plugin.instructions.isNotBlank()) appendLine(plugin.instructions.take(2000))
                plugin.skillIds.forEach { id ->
                    if (usedSkillIds.add(id)) {
                        data.skill(id)?.let { linked ->
                            appendLine("Вложенный навык «${linked.title}»:")
                            appendLine(linked.instructions.take(1600))
                        }
                    }
                }
            }
            val installedConnectors = data.installedConnectorList
            val liveConnectors = installedConnectors.filter { it.mcpUrl.isNotBlank() }
            if (liveConnectors.isNotEmpty()) {
                appendLine(
                    "Подключённые MCP-коннекторы: " +
                        liveConnectors.joinToString { it.title } +
                        ". Их инструменты вызывай через execute: " +
                        "const r = await tools.<namespace>.<tool>({...}); return r.result. " +
                        "Не выдумывай результат и не утверждай, что уже посмотрел данные, " +
                        "пока инструмент не вернул ответ.",
                )
            }
            val nativeCalendar = installedConnectors.any { it.native == "calendar" || it.id == "gcal" }
            if (nativeCalendar) {
                appendLine(
                    "Google Calendar подключён через системный календарь телефона: " +
                        "события и действия calendar доступны.",
                )
            }
            val missingConnectors = installedConnectors.filter { !it.hasLiveTools }
            if (missingConnectors.isNotEmpty()) {
                appendLine(
                    "Без MCP-адреса: " +
                        missingConnectors.joinToString { it.title } +
                        ". Не утверждай, что их инструменты доступны.",
                )
            }
            appendLine(
                when (settings.toolAccess) {
                    space.teleopen.clauden.data.ToolAccess.Auto ->
                        "Инструменты: выбирай сам, когда они действительно нужны."
                    space.teleopen.clauden.data.ToolAccess.OnDemand ->
                        "Инструменты: используй только по прямой необходимости, не загружай всё сразу."
                    space.teleopen.clauden.data.ToolAccess.Always ->
                        "Инструменты: считай, что все доступные инструменты уже загружены."
                }
            )
            appendLine("Инструменты устройства доступны через блок <clauden_action>...</clauden_action>.")
            appendLine(
                "Если для ответа нужна геопозиция (места рядом, погода, маршруты, «где я», район), " +
                    "верни ровно один блок: <clauden_action>{\"type\":\"location\",\"accuracy\":\"precise|approximate\",\"reason\":\"зачем\"}</clauden_action> " +
                    "и остановись. Приложение запросит разрешение у пользователя и пришлёт следующим сообщением <tool_result name=\"get_user_location\"> с координатами или ошибкой. " +
                    "После tool_result продолжи ответ. precise — только для «рядом со мной», парковок и навигации; approximate — для погоды и общего города. " +
                    "Не запрашивай геопозицию без необходимости и не упоминай её без повода. " +
                    "Не утверждай, что уже определил местоположение, пока не получил tool_result. " +
                    "Если в tool_result permission_denied — объясни, как включить доступ, и не повторяй вызов. " +
                    "После tool_result, если просят места рядом, маршрут или карту, можешь добавить " +
                    "<clauden_action>{\"type\":\"map\",\"query\":\"кафе\",\"latitude\":55.7,\"longitude\":37.6}</clauden_action> " +
                    "с координатами из tool_result; не рисуй карту текстом."
            )
            if (settings.connectorDiscovery) {
                appendLine("Можешь предлагать подходящие коннекторы из каталога, но не навязывай их.")
            } else {
                appendLine("Не предлагай коннекторы без прямой просьбы пользователя.")
            }
            if (settings.modelFallback) {
                appendLine("Если сообщение помечено системой безопасности, продолжай работу на другой модели.")
            }
        }.take(12000)
    }

    private fun buildMemory(activeId: String): String {
        val others = _state.value.conversations
            .filter { it.id != activeId && it.messages.isNotEmpty() }
            .sortedByDescending { it.updatedAt }
            .take(5)
        if (others.isEmpty()) return ""
        return buildString {
            appendLine("Ниже — выдержки из прошлых чатов пользователя.")
            appendLine("Если он спрашивает, что обсуждали раньше, опирайся на них.")
            others.forEach { conversation ->
                appendLine("— ${conversation.title}")
                val textMessages = conversation.messages
                    .filter { !it.hidden && (it.role == "user" || it.content.isNotBlank()) }
                (textMessages.take(2) + textMessages.takeLast(6))
                    .distinctBy { it.content.take(80) }
                    .forEach { message ->
                        val text = message.content
                            .replace('\n', ' ')
                            .trim()
                            .take(320)
                        if (text.isNotBlank()) {
                            appendLine((if (message.role == "user") "  Вы: " else "  Clauden: ") + text)
                        }
                    }
            }
        }.take(6000)
    }

    private fun makeTitle(text: String): String {
        val oneLine = text.replace('\n', ' ').trim()
        return if (oneLine.length <= 48) oneLine else oneLine.take(47).trimEnd() + "…"
    }

    private fun persist() {
        val conversations = _state.value.conversations
        viewModelScope.launch {

            persistMutex.withLock { store.save(conversations) }
            scheduleChatUpload(conversations)
        }
    }

    private fun persistProjects() {
        val projects = _state.value.projects
        viewModelScope.launch {
            projectStore.save(projects)
            scheduleProjectUpload(projects)
        }
    }
}
