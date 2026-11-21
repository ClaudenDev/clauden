package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Connector
import space.teleopen.clauden.data.ConnectorCatalogItem
import space.teleopen.clauden.data.ConnectorCatalogState
import space.teleopen.clauden.data.CustomizeCatalog
import space.teleopen.clauden.data.MarketInstallResponse
import space.teleopen.clauden.data.MarketItem
import space.teleopen.clauden.data.Marketplace
import space.teleopen.clauden.data.Plugin
import space.teleopen.clauden.data.Skill
import java.util.UUID
import kotlinx.coroutines.delay

private enum class CustomizeSort(val label: String) {
    Popular("По популярности"),
    New("Сначала новые"),
    Alpha("По алфавиту"),
}

private fun sorted(list: List<Skill>, sort: CustomizeSort): List<Skill> = when (sort) {
    CustomizeSort.Popular -> list.sortedByDescending { it.installs }
    CustomizeSort.New -> list.sortedByDescending { it.createdAt }
    CustomizeSort.Alpha -> list.sortedBy { it.title.lowercase() }
}

private fun sortedPlugins(list: List<Plugin>, sort: CustomizeSort): List<Plugin> = when (sort) {
    CustomizeSort.Popular -> list.sortedByDescending { it.installs }
    CustomizeSort.New -> list.sortedByDescending { it.createdAt }
    CustomizeSort.Alpha -> list.sortedBy { it.title.lowercase() }
}

private fun formatInstalls(value: Int): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f).replace(".0M", "M")
    value >= 1_000 -> String.format("%.0fK", value / 1_000f)
    value > 0 -> value.toString()
    else -> ""
}

@Composable
fun CustomizeScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onMenu: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val data = state.customize
    var mode by rememberSaveable { mutableStateOf("discover") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(CustomizeSort.Popular) }
    var sortOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var connectorCategory by rememberSaveable { mutableStateOf("") }
    var selectedSkill by remember { mutableStateOf<Skill?>(null) }
    var selectedPlugin by remember { mutableStateOf<Plugin?>(null) }
    var selectedConnector by remember { mutableStateOf<Connector?>(null) }
    var createSkill by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var createPlugin by remember { mutableStateOf(false) }
    var createConnector by remember { mutableStateOf(false) }
    var marketplaceOpen by remember { mutableStateOf(false) }
    var marketplaceUrl by remember { mutableStateOf("") }

    BackHandler {
        when {
            selectedSkill != null -> selectedSkill = null
            selectedPlugin != null -> selectedPlugin = null
            selectedConnector != null -> selectedConnector = null
            createSkill -> { createSkill = false; editingSkill = null }
            createPlugin -> createPlugin = false
            createConnector -> createConnector = false
            marketplaceOpen -> marketplaceOpen = false
            else -> vm.closeCustomize()
        }
    }

    fun openTab(tab: String) {
        vm.setCustomizeTab(tab)
        category = null
        connectorCategory = ""
        searchVisible = false
        query = ""
    }

    LaunchedEffect(mode, state.customizeTab, query, connectorCategory) {
        if (mode == "discover" && state.customizeTab == "connectors") {
            delay(250)
            vm.loadConnectorCatalog(query = query, category = connectorCategory, reset = true)
        }
    }

    val skillPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull() ?: "skill.md"
            vm.importSkillText(text, name)
        }
    }
    val pluginPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull() ?: "plugin.json"
            vm.importPluginText(text, name)
        }
    }

    Box(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CustomizeHeader(
                onBack = vm::closeCustomize,
                onMenu = onMenu,
            )

            CustomizeTabs(selected = state.customizeTab, onSelect = ::openTab)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentPill("Ваши", mode == "yours") { mode = "yours" }
                Spacer(modifier = Modifier.width(8.dp))
                SegmentPill("Обзор", mode == "discover") { mode = "discover" }
                Spacer(modifier = Modifier.width(8.dp))
                SegmentPill("Маркетплейс", mode == "market") {
                    mode = "market"
                    addOpen = false
                    sortOpen = false
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { searchVisible = !searchVisible }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (mode != "market") {
                    Box {
                        IconButton(onClick = { sortOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                ClaudenIcons.updown,
                                contentDescription = "Сортировка",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortOpen,
                            onDismissRequest = { sortOpen = false },
                            containerColor = colors.bg300,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            CustomizeSort.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    trailingIcon = {
                                        if (sort == item) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        sort = item
                                        sortOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        AddPill(onClick = { addOpen = true })
                        AddMenu(
                            expanded = addOpen,
                            tab = state.customizeTab,
                            onDismiss = { addOpen = false },
                            onCreateSkill = { addOpen = false; createSkill = true },
                            onUploadSkill = { addOpen = false; skillPicker.launch(arrayOf("*/*")) },
                            onCreatePlugin = { addOpen = false; createPlugin = true },
                            onUploadPlugin = { addOpen = false; pluginPicker.launch(arrayOf("*/*")) },
                            onMarketplace = { addOpen = false; marketplaceOpen = true },
                            onCreateConnector = { addOpen = false; createConnector = true },
                            onCreateWithClaude = {
                                addOpen = false
                                val prompt = if (state.customizeTab == "plugins") {
                                    "Помоги мне создать плагин для Clauden. Задай три вопроса о задаче и инструментах, " +
                                        "затем верни готовый JSON-плагин с полями title, description, instructions, skills."
                                } else {
                                    "Помоги мне создать навык для Clauden. Задай три вопроса о задаче, " +
                                        "затем верни готовый JSON-навык с полями title, description, instructions, category."
                                }
                                vm.startCustomizeChat(prompt)
                            },
                        )
                    }
                }
            }

            if (searchVisible) {
                CustomizeSearch(query = query, onValueChange = { query = it })
            }

            if (category != null && state.customizeTab == "skills" && mode != "market") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Категория: $category",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Сбросить",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.blueLink,
                        modifier = Modifier.clickable { category = null },
                    )
                }
            }

            if (mode == "discover") {
                MarketplaceBanner { mode = "market" }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 36.dp),
            ) {
                if (mode == "market") {
                    MarketplaceTab(
                        state = state,
                        vm = vm,
                        kind = state.customizeTab,
                        query = query,
                    )
                } else when (state.customizeTab) {
                    "connectors" -> ConnectorsTab(
                        state = state,
                        vm = vm,
                        mode = mode,
                        query = query,
                        catalog = state.connectorCatalog,
                        onCategory = { connectorCategory = it },
                        onConnect = { vm.connectCatalogItem(it) },
                        onMore = {
                            vm.loadConnectorCatalog(
                                query = query,
                                category = connectorCategory,
                                reset = false,
                            )
                        },
                        onClick = { selectedConnector = it },
                    )
                    "plugins" -> PluginsTab(
                        state = state,
                        vm = vm,
                        mode = mode,
                        query = query,
                        sort = sort,
                        category = category,
                        onCategory = { category = it },
                        onOpen = { selectedPlugin = it },
                        onCreate = { createPlugin = true },
                    )
                    else -> SkillsTab(
                        state = state,
                        vm = vm,
                        mode = mode,
                        query = query,
                        sort = sort,
                        category = category,
                        onCategory = { category = it },
                        onOpen = { selectedSkill = it },
                        onCreate = { editingSkill = null; createSkill = true },
                    )
                }
            }
        }

        selectedSkill?.let { skill ->
            SkillDetail(
                skill = skill,
                installed = skill.id in data.installedSkills,
                enabled = skill.id in data.enabledSkills,
                custom = data.customSkills.any { it.id == skill.id },
                onDismiss = { selectedSkill = null },
                onInstall = { vm.installSkill(skill.id); selectedSkill = null },
                onUninstall = { vm.uninstallSkill(skill.id); selectedSkill = null },
                onToggle = { vm.toggleSkill(skill.id) },
                onEdit = {
                    editingSkill = skill
                    selectedSkill = null
                    createSkill = true
                },
                onDelete = {
                    vm.deleteCustomSkill(skill.id)
                    selectedSkill = null
                },
            )
        }

        selectedPlugin?.let { plugin ->
            PluginDetail(
                plugin = plugin,
                skills = data.allSkills(),
                installed = plugin.id in data.installedPlugins,
                enabled = plugin.id in data.enabledPlugins,
                custom = data.customPlugins.any { it.id == plugin.id },
                onDismiss = { selectedPlugin = null },
                onInstall = { vm.installPlugin(plugin.id); selectedPlugin = null },
                onUninstall = { vm.uninstallPlugin(plugin.id); selectedPlugin = null },
                onToggle = { vm.togglePlugin(plugin.id) },
                onDelete = {
                    vm.deleteCustomPlugin(plugin.id)
                    selectedPlugin = null
                },
            )
        }

        selectedConnector?.let { connector ->
            ConnectorDetail(
                connector = connector,
                installed = connector.id in data.installedConnectors,
                checkBusy = state.connectorCheckBusy && state.connectorCheckId == connector.id,
                checkStatus = if (state.connectorCheckId == connector.id) state.connectorCheckStatus else "",
                checkError = if (state.connectorCheckId == connector.id) state.connectorCheckError else "",
                oauthBusy = state.connectorOAuth.busy && state.connectorOAuth.id == connector.id,
                oauthError = if (state.connectorOAuth.id == connector.id) state.connectorOAuth.error else "",
                onCheck = { vm.checkConnector(connector.id) },
                onOAuth = { vm.startConnectorOAuth(connector.id) },
                onDismiss = { selectedConnector = null },
                onInstall = { vm.installConnector(connector.id); selectedConnector = null },
                onUninstall = { vm.uninstallConnector(connector.id); selectedConnector = null },
            )
        }

        if (state.connectorOAuth.authUrl.isNotBlank() ||
            state.connectorOAuth.busy ||
            state.connectorOAuth.error.isNotBlank()
        ) {
            ConnectorOAuthScreen(
                title = state.customize.connector(state.connectorOAuth.id)?.title ?: "Вход в сервис",
                authUrl = state.connectorOAuth.authUrl,
                redirectUri = state.connectorOAuth.redirectUri,
                busy = state.connectorOAuth.busy,
                error = state.connectorOAuth.error,
                onCallback = vm::finishConnectorOAuth,
                onClose = vm::cancelConnectorOAuth,
            )
        }

        if (createSkill) {
            SkillEditorDialog(
                initial = editingSkill,
                onDismiss = {
                    createSkill = false
                    editingSkill = null
                },
                onSave = { skill ->
                    vm.saveCustomSkill(skill)
                    createSkill = false
                    editingSkill = null
                },
            )
        }

        if (createPlugin) {
            PluginEditorDialog(
                skills = data.allSkills(),
                onDismiss = { createPlugin = false },
                onSave = { plugin ->
                    vm.saveCustomPlugin(plugin)
                    createPlugin = false
                },
            )
        }

        if (createConnector) {
            ConnectorEditorDialog(
                onDismiss = { createConnector = false },
                onSave = { connector ->
                    vm.saveCustomConnector(connector)
                    createConnector = false
                    selectedConnector = connector
                    vm.checkConnector(connector.id)
                },
            )
        }

        if (marketplaceOpen) {
            InputDialog(
                title = "Добавить маркетплейс",
                hint = "https://example.com/marketplace.json",
                value = marketplaceUrl,
                onValueChange = { marketplaceUrl = it },
                confirm = "Добавить",
                onDismiss = { marketplaceOpen = false },
                onConfirm = {
                    vm.addMarketplace(marketplaceUrl)
                    marketplaceOpen = false
                },
            )
        }
    }
}

@Composable
private fun CustomizeHeader(onBack: () -> Unit, onMenu: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(
                ClaudenIcons.arrowBack,
                contentDescription = "Назад",
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "Customize",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMenu, modifier = Modifier.size(42.dp)) {
            Icon(
                ClaudenIcons.menu,
                contentDescription = "Меню",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CustomizeTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("skills" to "Навыки", "connectors" to "Коннекторы", "plugins" to "Плагины")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { (id, label) ->
            SegmentPill(label, selected == id) { onSelect(id) }
        }
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.control else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .height(34.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = if (selected) colors.textPrimary else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun AddPill(onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.textPrimary,
        contentColor = colors.bg,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .height(34.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Добавить", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
        }
    }
}

@Composable
private fun AddMenu(
    expanded: Boolean,
    tab: String,
    onDismiss: () -> Unit,
    onCreateSkill: () -> Unit,
    onUploadSkill: () -> Unit,
    onCreatePlugin: () -> Unit,
    onUploadPlugin: () -> Unit,
    onMarketplace: () -> Unit,
    onCreateConnector: () -> Unit,
    onCreateWithClaude: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.popupBg,
        shape = RoundedCornerShape(18.dp),
        offset = DpOffset(0.dp, 8.dp),
    ) {
        if (tab == "connectors") {
            DropdownItem("Добавить MCP-сервер", ClaudenIcons.connectors, onCreateConnector)
        } else {
            if (tab == "plugins") {
                DropdownItem("Создать плагин", ClaudenIcons.grid, onCreatePlugin)
                DropdownItem("Загрузить плагин", ClaudenIcons.doc, onUploadPlugin)
            } else {
                DropdownItem("Создать навык", ClaudenIcons.spark, onCreateSkill)
                DropdownItem("Загрузить навык", ClaudenIcons.doc, onUploadSkill)
            }
            DropdownItem("Добавить маркетплейс", ClaudenIcons.globe, onMarketplace)
            DropdownItem("Создать с Clauden", ClaudenIcons.chatBubble, onCreateWithClaude)
        }
    }
}

@Composable
private fun DropdownItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(19.dp))
        },
        onClick = onClick,
    )
}

@Composable
private fun CustomizeSearch(query: String, onValueChange: (String) -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Поиск навыков, плагинов и коннекторов",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.clay),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Очистить",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

@Composable
private fun SkillsTab(
    state: ChatUiState,
    vm: ChatViewModel,
    mode: String,
    query: String,
    sort: CustomizeSort,
    category: String?,
    onCategory: (String?) -> Unit,
    onOpen: (Skill) -> Unit,
    onCreate: () -> Unit,
) {
    val data = state.customize
    val installed = data.installedSkills.toSet()
    val enabled = data.enabledSkills.toSet()
    val all = data.allSkills()
    val filtered = all.filter { skill ->
        (category == null || skill.category == category) &&
            (query.isBlank() || skill.title.contains(query, ignoreCase = true) ||
                skill.description.contains(query, ignoreCase = true) ||
                skill.category.contains(query, ignoreCase = true))
    }

    if (mode == "yours") {
        val mine = filtered.filter { it.id in installed }.let { sorted(it, sort) }
        MineHeader(
            title = "Ваши навыки",
            subtitle = if (mine.isEmpty()) "Пока пусто" else "Включено в чатах: ${mine.count { it.id in enabled }}",
            action = "Создать",
        ) { onCreate() }
        if (mine.isEmpty()) {
            EmptyHint("Добавьте навык из обзора или создайте свой — он будет помогать модели в чатах.")
        }
        mine.forEach { skill ->
            SkillCard(
                skill = skill,
                installed = true,
                enabled = skill.id in enabled,
                onOpen = { onOpen(skill) },
                onTry = { vm.useSkill(skill.id); vm.startCustomizeChat(skillTryPrompt(skill)) },
                onInstall = {},
                onToggle = { vm.toggleSkill(skill.id) },
            )
        }
        if (data.marketplaces.isNotEmpty()) {
            SectionTitle("Маркетплейсы")
            data.marketplaces.forEach { marketplace ->
                MarketplaceRow(marketplace)
            }
        }
        return
    }

    if (category != null) {
        SectionTitle("$category · ${filtered.size}")
        sorted(filtered, sort).forEach { skill ->
            SkillCard(
                skill = skill,
                installed = skill.id in installed,
                enabled = skill.id in enabled,
                onOpen = { onOpen(skill) },
                onTry = {
                    vm.useSkill(skill.id)
                    vm.startCustomizeChat(skillTryPrompt(skill))
                },
                onInstall = { vm.installSkill(skill.id) },
                onToggle = { vm.toggleSkill(skill.id) },
            )
        }
        return
    }

    val hero = all.firstOrNull { it.id == "data" }
    if (hero != null && query.isBlank()) {
        HeroCard(
            kicker = "Куратор Clauden",
            title = hero.title,
            description = hero.description,
            color = Color(0xFF23452C),
            installed = hero.id in installed,
            onAdd = {
                if (hero.id in installed) onOpen(hero) else vm.installSkill(hero.id)
            },
        )
    }

    val forYou = sorted(filtered.filter { it.id !in installed }.take(8), sort)
    if (forYou.isNotEmpty()) {
        SectionTitle("Для вас")
        forYou.forEach { skill ->
            SkillCard(
                skill = skill,
                installed = false,
                enabled = false,
                onOpen = { onOpen(skill) },
                onTry = {
                    vm.useSkill(skill.id)
                    vm.startCustomizeChat(skillTryPrompt(skill))
                },
                onInstall = { vm.installSkill(skill.id) },
                onToggle = { vm.toggleSkill(skill.id) },
            )
        }
    }

    val newer = filtered.filter { it.id in data.customSkills.map { c -> c.id } }
        .ifEmpty { sorted(filtered, CustomizeSort.Alpha).takeLast(6) }
    if (newer.isNotEmpty() && query.isBlank()) {
        SectionTitle("Новые навыки")
        newer.take(6).forEach { skill ->
            SkillCard(
                skill = skill,
                installed = skill.id in installed,
                enabled = skill.id in enabled,
                onOpen = { onOpen(skill) },
                onTry = {
                    vm.useSkill(skill.id)
                    vm.startCustomizeChat(skillTryPrompt(skill))
                },
                onInstall = { vm.installSkill(skill.id) },
                onToggle = { vm.toggleSkill(skill.id) },
            )
        }
    }

    val popular = sorted(filtered, CustomizeSort.Popular).take(8)
    if (popular.isNotEmpty()) {
        SectionTitle("Популярные навыки")
        popular.forEach { skill ->
            SkillCard(
                skill = skill,
                installed = skill.id in installed,
                enabled = skill.id in enabled,
                onOpen = { onOpen(skill) },
                onTry = {
                    vm.useSkill(skill.id)
                    vm.startCustomizeChat(skillTryPrompt(skill))
                },
                onInstall = { vm.installSkill(skill.id) },
                onToggle = { vm.toggleSkill(skill.id) },
            )
        }
    }

    if (query.isBlank()) {
        SectionTitle("Категории")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomizeCatalog.categories
                .filter { name -> all.any { it.category == name } }
                .chunked(2)
                .forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { name ->
                            val count = all.count { it.category == name }
                            CategoryCard(name, count, Modifier.weight(1f)) { onCategory(name) }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
        }
    }
}

private fun skillTryPrompt(skill: Skill): String =
    "Попробуем навык «${skill.title}». Начни с одного короткого вопроса о задаче, " +
        "затем помоги мне применить этот навык на практике."

@Composable
private fun SkillCard(
    skill: Skill,
    installed: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTry: () -> Unit,
    onInstall: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg300)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SkillMark(skill.icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = buildString {
                    append(skill.author)
                    formatInstalls(skill.installs).takeIf { it.isNotBlank() }?.let {
                        append(" · $it установок")
                    }
                    if (installed && enabled) append(" · включён")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            TryPill(onClick = onTry)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            installed && enabled -> colors.blue.copy(alpha = 0.16f)
                            installed -> colors.control
                            else -> colors.control
                        }
                    )
                    .clickable { if (installed) onToggle() else onInstall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (installed && enabled) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (installed && enabled) "Выключить" else "Включить",
                    tint = if (installed && enabled) colors.blueLink else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun SkillMark(icon: String) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.control),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = skillIcon(icon),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(23.dp),
        )
    }
}

private fun skillIcon(icon: String): ImageVector = when (icon) {
    "data" -> ClaudenIcons.chart
    "code" -> ClaudenIcons.code
    "design" -> ClaudenIcons.artifacts
    "finance" -> ClaudenIcons.dollar
    "product" -> ClaudenIcons.grid
    "ops" -> ClaudenIcons.sliders
    "people" -> ClaudenIcons.person
    "api" -> ClaudenIcons.connectors
    "learn" -> ClaudenIcons.doc
    "docs" -> ClaudenIcons.doc
    "clock" -> ClaudenIcons.clock
    "search" -> ClaudenIcons.globe
    "megaphone" -> ClaudenIcons.megaphone
    "legal" -> ClaudenIcons.shield
    "sales" -> ClaudenIcons.personPlus
    "notes" -> ClaudenIcons.chatBubble
    "translate" -> ClaudenIcons.globe
    "globe" -> ClaudenIcons.globe
    "warning" -> ClaudenIcons.warning
    else -> ClaudenIcons.spark
}

@Composable
private fun TryPill(onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.control,
        shape = CircleShape,
        modifier = Modifier
            .height(28.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ClaudenIcons.chatBubble,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Try",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HeroCard(
    kicker: String,
    title: String,
    description: String,
    color: Color,
    installed: Boolean,
    onAdd: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kicker,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = LocalClaudenFonts.current.serif,
                        fontSize = 28.sp,
                    ),
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .clickable(onClick = onAdd),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (installed) "Открыть" else "Добавить",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = Color(0xFF1A1A1A),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            HeroArt()
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun HeroArt() {
    Canvas(modifier = Modifier.size(width = 96.dp, height = 74.dp)) {
        val stroke = Color.White.copy(alpha = 0.75f)
        val w = size.width / 3f
        val h = size.height / 3f
        for (row in 0..2) {
            for (col in 0..2) {
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(col * w + 2f, row * h + 2f),
                    size = androidx.compose.ui.geometry.Size(w - 6f, h - 6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = LocalClaudenColors.current.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryCard(name: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg300)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.control),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = skillIcon(categoryIcon(name)),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
    }
}

private fun categoryIcon(name: String): String = when (name) {
    "Инженерия" -> "code"
    "Код" -> "code"
    "Файлы и документы" -> "docs"
    "Дизайн и медиа" -> "design"
    "Задачи и тикеты" -> "product"
    "Пайплайны и задания" -> "ops"
    "API" -> "api"
    "Заметки и память" -> "notes"
    else -> "search"
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
        color = LocalClaudenColors.current.textTertiary,
        modifier = Modifier.padding(vertical = 14.dp),
    )
}

@Composable
private fun MineHeader(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
        if (action.isNotBlank()) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.blueLink,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun MarketplaceRow(marketplace: Marketplace) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg300)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ClaudenIcons.globe,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = marketplace.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = colors.textPrimary,
            )
            Text(
                text = marketplace.url,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PluginsTab(
    state: ChatUiState,
    vm: ChatViewModel,
    mode: String,
    query: String,
    sort: CustomizeSort,
    category: String?,
    onCategory: (String?) -> Unit,
    onOpen: (Plugin) -> Unit,
    onCreate: () -> Unit,
) {
    val data = state.customize
    val installed = data.installedPlugins.toSet()
    val enabled = data.enabledPlugins.toSet()
    val all = data.allPlugins().filter { plugin ->
        val categoryOk = category == null || plugin.skillIds.any { id ->
            data.skill(id)?.category == category
        }
        categoryOk && (
            query.isBlank() ||
                plugin.title.contains(query, ignoreCase = true) ||
                plugin.description.contains(query, ignoreCase = true)
            )
    }

    if (mode == "yours") {
        val mine = sortedPlugins(all.filter { it.id in installed }, sort)
        MineHeader(
            title = "Ваши плагины",
            subtitle = if (mine.isEmpty()) "Пока пусто" else "Включено: ${mine.count { it.id in enabled }}",
            action = "Создать",
        ) { onCreate() }
        if (mine.isEmpty()) EmptyHint("Плагин объединяет навыки и коннекторы под одну задачу.")
        mine.forEach { plugin ->
            PluginCard(
                plugin = plugin,
                installed = true,
                enabled = plugin.id in enabled,
                onOpen = { onOpen(plugin) },
                onTry = { vm.usePlugin(plugin.id); vm.startCustomizeChat(pluginTryPrompt(plugin)) },
                onInstall = {},
                onToggle = { vm.togglePlugin(plugin.id) },
            )
        }
        return
    }

    if (category != null) {
        SectionTitle("$category · ${all.size}")
        sortedPlugins(all, sort).forEach { plugin ->
            PluginCard(
                plugin = plugin,
                installed = plugin.id in installed,
                enabled = plugin.id in enabled,
                onOpen = { onOpen(plugin) },
                onTry = { vm.usePlugin(plugin.id); vm.startCustomizeChat(pluginTryPrompt(plugin)) },
                onInstall = { vm.installPlugin(plugin.id) },
                onToggle = { vm.togglePlugin(plugin.id) },
            )
        }
        return
    }

    val hero = all.firstOrNull { it.id == "data" }
    if (hero != null && query.isBlank()) {
        HeroCard(
            kicker = "Куратор Clauden",
            title = hero.title,
            description = hero.description,
            color = Color(0xFF1D3550),
            installed = hero.id in installed,
            onAdd = {
                if (hero.id in installed) onOpen(hero) else vm.installPlugin(hero.id)
            },
        )
    }

    SectionTitle("Для вас")
    sortedPlugins(all.filter { it.id !in installed }.take(8), sort).forEach { plugin ->
        PluginCard(
            plugin = plugin,
            installed = false,
            enabled = false,
            onOpen = { onOpen(plugin) },
            onTry = { vm.usePlugin(plugin.id); vm.startCustomizeChat(pluginTryPrompt(plugin)) },
            onInstall = { vm.installPlugin(plugin.id) },
            onToggle = { vm.togglePlugin(plugin.id) },
        )
    }

    val custom = all.filter { plugin -> data.customPlugins.any { it.id == plugin.id } }
    if (custom.isNotEmpty()) {
        SectionTitle("Новые плагины")
        custom.take(6).forEach { plugin ->
            PluginCard(
                plugin = plugin,
                installed = plugin.id in installed,
                enabled = plugin.id in enabled,
                onOpen = { onOpen(plugin) },
                onTry = { vm.usePlugin(plugin.id); vm.startCustomizeChat(pluginTryPrompt(plugin)) },
                onInstall = { vm.installPlugin(plugin.id) },
                onToggle = { vm.togglePlugin(plugin.id) },
            )
        }
    }

    val popular = sortedPlugins(all, sort).take(8)
    if (popular.isNotEmpty()) {
        SectionTitle("Популярные плагины")
        popular.forEach { plugin ->
            PluginCard(
                plugin = plugin,
                installed = plugin.id in installed,
                enabled = plugin.id in enabled,
                onOpen = { onOpen(plugin) },
                onTry = { vm.usePlugin(plugin.id); vm.startCustomizeChat(pluginTryPrompt(plugin)) },
                onInstall = { vm.installPlugin(plugin.id) },
                onToggle = { vm.togglePlugin(plugin.id) },
            )
        }
    }

    if (query.isBlank()) {
        SectionTitle("Категории")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val counts = CustomizeCatalog.categories.associateWith { name ->
                data.allPlugins().count { plugin ->
                    plugin.skillIds.any { id -> data.skill(id)?.category == name }
                }
            }
            counts.filterValues { it > 0 }.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (name, count) ->
                        CategoryCard(name, count, Modifier.weight(1f)) { onCategory(name) }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun pluginTryPrompt(plugin: Plugin): String =
    "Попробуем плагин «${plugin.title}». Спроси, какая задача у меня есть, " +
        "и предложи, как этот плагин её решит."

@Composable
private fun PluginCard(
    plugin: Plugin,
    installed: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTry: () -> Unit,
    onInstall: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg300)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.control),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ClaudenIcons.grid,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = buildString {
                    append(plugin.author)
                    formatInstalls(plugin.installs).takeIf { it.isNotBlank() }?.let { append(" · $it установок") }
                    if (installed && enabled) append(" · включён")
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Описание и состав",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.blueLink,
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            TryPill(onClick = onTry)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (installed && enabled) colors.blue.copy(alpha = 0.16f) else colors.control)
                    .clickable { if (installed) onToggle() else onInstall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (installed && enabled) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (installed && enabled) colors.blueLink else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectorsTab(
    state: ChatUiState,
    vm: ChatViewModel,
    mode: String,
    query: String,
    catalog: ConnectorCatalogState,
    onCategory: (String) -> Unit,
    onConnect: (ConnectorCatalogItem) -> Unit,
    onMore: () -> Unit,
    onClick: (Connector) -> Unit,
) {
    val data = state.customize
    val installed = data.installedConnectors.toSet()
    val all = data.allConnectors().filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.author.contains(query, ignoreCase = true)
    }

    if (mode == "yours") {
        MineHeader(
            title = "Ваши коннекторы",
            subtitle = "${installed.size} добавлено",
            action = "",
        ) {}
        all.filter { it.id in installed }.forEach { connector ->
            ConnectorCard(connector, true, onClick = { onClick(connector) })
        }
        if (all.none { it.id in installed }) {
            EmptyHint("Добавьте коннектор из каталога — подключение в один тап.")
        }
        return
    }

    val hasCatalog = catalog.items.isNotEmpty() || catalog.loading || catalog.error.isNotBlank()
    if (hasCatalog) {
        if (catalog.facets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentPill("Все", catalog.category.isBlank()) { onCategory("") }
                catalog.facets.take(10).forEach { facet ->
                    SegmentPill(
                        label = facet.label,
                        selected = catalog.category == facet.id,
                    ) { onCategory(if (catalog.category == facet.id) "" else facet.id) }
                }
            }
        }

        SectionTitle("Каталог MCP · ${catalog.total.coerceAtLeast(catalog.items.size)} серверов")
        Text(
            text = "В одном чате работают до 8 коннекторов: остальные остаются в списке «Ваши».",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = LocalClaudenColors.current.textTertiary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (catalog.error.isNotBlank()) {
            EmptyHint("Каталог не ответил: ${catalog.error}")
        }
        catalog.items.forEach { item ->
            val connector = item.toConnector()
            ConnectorCard(
                connector = connector,
                installed = item.id in installed,
                onClick = {
                    vm.rememberCatalogConnector(item)
                    onClick(connector)
                },
                actionLabel = when {
                    item.id in installed -> "Добавлено"
                    state.connectorCheckBusy && state.connectorCheckId == item.id -> "Подключаю…"
                    else -> "Подключить"
                },
                actionEnabled = item.id !in installed &&
                    !(state.connectorCheckBusy && state.connectorCheckId == item.id),
                onAction = { onConnect(item) },
            )
        }
        if (catalog.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Загружаю каталог…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = LocalClaudenColors.current.textTertiary,
                )
            }
        } else if (catalog.items.size < catalog.total) {
            Text(
                text = "Показать ещё",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = LocalClaudenColors.current.blueLink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMore)
                    .padding(vertical = 14.dp),
            )
        }
        return
    }

    if (query.isBlank()) {
        DesignHero(installed = installed, onClick = onClick)
    }

    SectionTitle("Топ коннекторов · ${all.count { it.section == "top" }}")
    all.filter { it.section == "top" }.forEach { connector ->
        ConnectorCard(connector, connector.id in installed, onClick = { onClick(connector) })
    }

    listOf(
        "Trending" to "trending",
        "Новые коннекторы" to "new",
        "Справочные материалы" to "reference",
        "Отдел: инженерия" to "engineering",
        "Отрасль: финансы" to "finance",
    ).forEach { (title, section) ->
        val items = all.filter { it.section == section }
        if (items.isNotEmpty()) {
            SectionTitle(title)
            items.forEach { connector ->
                ConnectorCard(connector, connector.id in installed, onClick = { onClick(connector) })
            }
        }
    }
}

@Composable
private fun DesignHero(installed: Set<String>, onClick: (Connector) -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = Color(0xFF234B3A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "CURATED BY ANTHROPIC",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Claude for design",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 26.sp,
                ),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Набросайте, соберите схему и доработайте её в своих инструментах.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomizeCatalog.connectors.filter {
                    it.id in listOf("figma", "canva", "framer", "notion", "gdocs")
                }.forEach { connector ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable { onClick(connector) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = connector.mark.take(2),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(connector.color),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = Color(0xFF10251B),
                shape = CircleShape,
                modifier = Modifier
                    .height(32.dp)
                    .clickable {
                        CustomizeCatalog.connectors.firstOrNull { it.id == "figma" }?.let(onClick)
                    },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if ("figma" in installed) "Открыть" else "Изучить",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = Color.White,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun ConnectorCard(
    connector: Connector,
    installed: Boolean,
    onClick: () -> Unit,
    actionLabel: String = "",
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg300)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = connector.mark.take(2),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (connector.mark.length > 1) 11.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color(connector.color),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = connector.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    ClaudenIcons.external,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = connector.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = connector.author,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (onAction != null) {
            Surface(
                color = colors.control,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .height(32.dp)
                    .clickable(enabled = actionEnabled, onClick = onAction),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = if (actionEnabled) colors.textPrimary else colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (installed) colors.blue.copy(alpha = 0.16f) else colors.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (installed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (installed) colors.blueLink else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun MarketplaceBanner(onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ClaudenIcons.globe,
                contentDescription = null,
                tint = colors.blueLink,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Маркетплейс",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = "Remotion, Figma, Vercel, Anthropic, плагины и MCP-серверы",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
            Icon(
                ClaudenIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private data class MarketSourceOption(val id: String, val label: String)

private fun marketSources(kind: String): List<MarketSourceOption> = when (kind) {
    "plugins" -> listOf(MarketSourceOption("claudemarket", "Claude Market"))
    "connectors" -> listOf(
        MarketSourceOption("mcp", "MCP Registry"),
        MarketSourceOption("claudemarket-mcp", "Claude Market"),
    )
    else -> listOf(
        MarketSourceOption("standard", "Стандартные"),
        MarketSourceOption("claudskills", "Каталог"),
        MarketSourceOption("anthropic", "Anthropic"),
        MarketSourceOption("community", "Claude Market"),
    )
}

private fun defaultMarketSource(kind: String): String = when (kind) {
    "plugins" -> "claudemarket"
    "connectors" -> "mcp"
    else -> "standard"
}

private fun marketSourceLabel(source: String): String = when (source) {
    "standard" -> "Стандартные"
    "claudskills" -> "Каталог ClaudSkills"
    "anthropic" -> "Anthropic"
    "hermes" -> "Hermes"
    "community" -> "Claude Market"
    "claudemarket" -> "Claude Market"
    "mcp" -> "MCP Registry"
    "claudemarket-mcp" -> "Claude Market"
    else -> source
}

private fun marketInstalled(state: ChatUiState, kind: String, item: MarketItem): Boolean =
    when (kind) {
        "plugins" -> state.customize.customPlugins.any { it.origin == item.id }
        "connectors" -> state.customize.customConnectors.any { it.origin == item.id }
        else -> state.customize.customSkills.any { it.origin == item.id }
    }

@Composable
private fun MarketplaceTab(
    state: ChatUiState,
    vm: ChatViewModel,
    kind: String,
    query: String,
) {
    val colors = LocalClaudenColors.current
    var source by remember(kind) { mutableStateOf(defaultMarketSource(kind)) }
    var selected by remember(kind) { mutableStateOf<MarketItem?>(null) }
    val market = state.market
    val sources = marketSources(kind)

    LaunchedEffect(kind, source, query) {
        delay(280)
        vm.loadMarket(kind, source, query, reset = true)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sources.forEach { option ->
            MarketSourceChip(option.label, source == option.id) { source = option.id }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (kind) {
                "plugins" -> "Плагины: нажмите на карточку, чтобы прочитать описание и состав"
                "connectors" -> "MCP-серверы"
                else -> if (source == "claudskills") {
                    "Каталог ClaudSkills: 15 000+ навыков (CC BY 4.0)"
                } else {
                    "Стандартные навыки: Remotion, Figma, Vercel, shadcn/ui, Anthropic"
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        if (market.total > 0) {
            Text(
                text = "${market.items.size} из ${market.total}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
    }

    if (market.loading && market.items.isEmpty()) {
        Text(
            text = "Загружаю каталог…",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(vertical = 22.dp),
        )
        return
    }

    if (market.error.isNotBlank()) {
        Text(
            text = market.error,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.dangerSoft,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        SecondaryAction(
            text = "Повторить",
            onClick = { vm.loadMarket(kind, source, query, reset = true) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (market.warning.isNotBlank() && market.error.isBlank()) {
        Text(
            text = market.warning,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }

    if (!market.loading && market.error.isBlank() && market.items.isEmpty()) {
        EmptyHint(
            if (query.isNotBlank()) {
                "В этом источнике ничего не найдено. Попробуйте другой запрос или каталог."
            } else {
                "Каталог временно пуст. Смените источник или повторите позже."
            }
        )
    }

    market.items.forEach { item ->
        MarketItemCard(
            item = item,
            kind = kind,
            installed = marketInstalled(state, kind, item),
            installing = market.installingId == item.id,
            onOpen = {
                selected = item
                vm.previewMarket(item)
            },
            onInstall = { vm.installMarket(item) },
        )
    }

    if (market.items.isNotEmpty() && market.items.size < market.total) {
        Spacer(modifier = Modifier.height(8.dp))
        SettingsActionButton(
            text = if (market.loading) "Загружаю…" else "Показать ещё",
            enabled = !market.loading,
            onClick = { vm.loadMarket(kind, source, query, reset = false) },
        )
    }

    selected?.let { item ->
        MarketItemDetail(
            item = item,
            kind = kind,
            installed = marketInstalled(state, kind, item),
            installing = market.installingId == item.id,
            preview = state.market.preview.takeIf { state.market.previewId == item.id },
            previewLoading = state.market.previewLoading && state.market.previewId == item.id,
            previewError = state.market.previewError.takeIf { state.market.previewId == item.id }.orEmpty(),
            onRetryPreview = { vm.previewMarket(item) },
            onDismiss = {
                selected = null
                vm.clearMarketPreview()
            },
            onInstall = { vm.installMarket(item) },
        )
    }
}

@Composable
private fun MarketSourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.textPrimary else colors.control,
        shape = CircleShape,
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (selected) colors.bg else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MarketItemCard(
    item: MarketItem,
    kind: String,
    installed: Boolean,
    installing: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg300)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.control),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (kind) {
                    "plugins" -> ClaudenIcons.grid
                    "connectors" -> ClaudenIcons.connectors
                    else -> skillIcon(categoryIcon(item.category))
                },
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = listOfNotNull(
                    item.author.takeIf { it.isNotBlank() },
                    item.category.takeIf { it.isNotBlank() },
                    marketSourceLabel(item.source).takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (kind == "plugins") "Описание и состав" else "Описание",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.blueLink,
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = when {
                installed -> colors.blue.copy(alpha = 0.16f)
                item.installable -> colors.textPrimary
                else -> colors.control
            },
            shape = CircleShape,
            modifier = Modifier
                .height(30.dp)
                .clickable(enabled = item.installable && !installed && !installing, onClick = onInstall),
        ) {
            Box(modifier = Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        installing -> "Ставлю…"
                        installed -> "Готово"
                        !item.installable -> "Нет файла"
                        else -> "Установить"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = when {
                        installed -> colors.blueLink
                        item.installable -> colors.bg
                        else -> colors.textTertiary
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewText(text: String) {
    val colors = LocalClaudenColors.current
    Surface(color = colors.bg, shape = RoundedCornerShape(10.dp)) {
        Text(
            text = text.take(12000),
            style = ClaudenType.mono.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
    }
}

@Composable
private fun MarketItemDetail(
    item: MarketItem,
    kind: String,
    installed: Boolean,
    installing: Boolean,
    preview: MarketInstallResponse?,
    previewLoading: Boolean,
    previewError: String,
    onRetryPreview: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val uriHandler = LocalUriHandler.current
    var expandedSkill by remember { mutableStateOf("") }
    DetailOverlay(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (kind) {
                        "plugins" -> ClaudenIcons.grid
                        "connectors" -> ClaudenIcons.connectors
                        else -> skillIcon(categoryIcon(item.category))
                    },
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = listOfNotNull(
                        item.author,
                        item.category,
                        marketSourceLabel(item.source),
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
        )
        if (item.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.tags.joinToString(" · ") { "#$it" },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textTertiary,
            )
        }
        if (item.url.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Открыть исходник",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.blueLink,
                modifier = Modifier
                    .clickable { runCatching { uriHandler.openUri(item.url) } }
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Что внутри",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        when {
            previewLoading -> Text(
                text = "Загружаю содержимое…",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            previewError.isNotBlank() -> Column {
                Text(
                    text = previewError,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.dangerSoft,
                )
                Spacer(modifier = Modifier.height(6.dp))
                SecondaryAction(
                    text = "Повторить",
                    onClick = onRetryPreview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            preview?.skill != null -> PreviewText(preview.skill.instructions)

            preview?.plugin != null -> {
                val plugin = preview.plugin

                if (plugin.instructions.isNotBlank() && plugin.instructions.length > 80) {
                    PreviewText(plugin.instructions)
                }
                if (preview.skills.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Состав плагина (${preview.skills.size})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                    )
                    preview.skills.forEach { skill ->
                        val expanded = expandedSkill == skill.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .screenBackground(colors)
                                .clickable {
                                    expandedSkill = if (expanded) "" else skill.id
                                }
                                .padding(10.dp),
                        ) {
                            Text(
                                text = skill.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textPrimary,
                            )
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = colors.textTertiary,
                                maxLines = if (expanded) 20 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (expanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = skill.instructions.take(6000),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    ),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            preview?.connector != null -> Text(
                text = preview.connector.docs.ifBlank {
                    "Коннектор добавляется в каталог аккаунта; живые инструменты появятся после настройки MCP."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
            )

            preview != null -> Text(
                text = "Содержимое не распознано.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        SettingsActionButton(
            text = when {
                installing -> "Устанавливаю…"
                installed -> "Установлено"
                !item.installable -> "Нет исходников для установки"
                else -> "Установить"
            },
            enabled = item.installable && !installed && !installing,
            onClick = onInstall,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun DetailOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalClaudenColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = colors.bg,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .blockClicks(),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SkillDetail(
    skill: Skill,
    installed: Boolean,
    enabled: Boolean,
    custom: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DetailOverlay(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillMark(skill.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = "${skill.author} · ${skill.category}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = skill.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
        )
        if (installed) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Использовать в чатах",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SettingsToggle(checked = enabled, onCheckedChange = { onToggle() })
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Инструкции навыка",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(color = colors.bg300, shape = RoundedCornerShape(12.dp)) {
            Text(
                text = skill.instructions,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(14.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (!installed) {
            SettingsActionButton("Добавить и включить", onClick = onInstall)
        } else {
            SettingsActionButton("Убрать из моих навыков", onClick = onUninstall)
            if (custom) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction("Изменить", onEdit, Modifier.weight(1f))
                    SecondaryAction("Удалить", onDelete, Modifier.weight(1f), danger = true)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun PluginDetail(
    plugin: Plugin,
    skills: List<Skill>,
    installed: Boolean,
    enabled: Boolean,
    custom: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DetailOverlay(onDismiss) {
        Text(
            text = plugin.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = colors.textPrimary,
        )
        Text(
            text = "${plugin.author} · плагин",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = plugin.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
        )
        if (installed) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Использовать в чатах",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SettingsToggle(checked = enabled, onCheckedChange = { onToggle() })
            }
        }
        val linked = skills.filter { it.id in plugin.skillIds }
        if (linked.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Внутри плагина",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            linked.forEach { skill ->
                Text(
                    text = "· ${skill.title}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (plugin.instructions.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Инструкции",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(color = colors.bg300, shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = plugin.instructions,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (!installed) {
            SettingsActionButton("Добавить и включить", onClick = onInstall)
        } else {
            SettingsActionButton("Убрать плагин", onClick = onUninstall)
            if (custom) {
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryAction("Удалить", onDelete, Modifier.fillMaxWidth(), danger = true)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun ConnectorDetail(
    connector: Connector,
    installed: Boolean,
    checkBusy: Boolean,
    checkStatus: String,
    checkError: String,
    oauthBusy: Boolean,
    oauthError: String,
    onCheck: () -> Unit,
    onOAuth: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    LaunchedEffect(connector.id) {
        if (connector.mcpUrl.isNotBlank() && checkStatus.isBlank() && !checkBusy) {
            onCheck()
        }
    }
    DetailOverlay(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = connector.mark.take(2),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(connector.color),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connector.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = colors.textPrimary,
                )
                Text(
                    text = connector.author,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = connector.description,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when {
                connector.mcpUrl.isNotBlank() ->
                    "MCP-сервер: ${connector.mcpUrl.substringBefore("?")}\nИнструменты появятся в чате после добавления."
                connector.native == "calendar" || connector.id == "gcal" ->
                    "Работает через системный календарь телефона: Clauden видит события и создаёт новые."
                else ->
                    "У этого коннектора нет публичного MCP-адреса. Добавьте свой MCP-сервер, чтобы получить живые инструменты."
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textTertiary,
        )
        if (checkBusy || checkStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    checkBusy -> "Проверяю подключение…"
                    checkStatus == "connected" -> "Подключение работает: сервер ответил."
                    checkStatus == "needs_auth" ->
                        checkError.ifBlank { "Сервис требует вход через OAuth." }
                    else -> checkError.ifBlank { "Не удалось подключиться к MCP-серверу." }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = when (checkStatus) {
                    "connected" -> colors.blueLink
                    "failed", "needs_auth" -> colors.dangerSoft
                    else -> colors.textTertiary
                },
            )
        }
        if (oauthError.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = oauthError,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.dangerSoft,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (checkStatus == "needs_auth" || oauthBusy) {
            SettingsActionButton(
                text = if (oauthBusy) "Открываю вход…" else "Войти в сервис",
                onClick = onOAuth,
                enabled = !oauthBusy,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (connector.mcpUrl.isNotBlank()) {
            SettingsActionButton(
                text = if (checkBusy) "Проверяю…" else "Проверить подключение",
                onClick = onCheck,
                enabled = !checkBusy,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (installed) {
            SettingsActionButton("Убрать коннектор", onClick = onUninstall)
        } else {
            SettingsActionButton(
                text = if (connector.hasLiveTools) "Добавить коннектор" else "Нужен MCP-адрес",
                onClick = onInstall,
                enabled = connector.hasLiveTools,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = CircleShape,
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = if (danger) colors.dangerSoft else colors.textPrimary,
            )
        }
    }
}

@Composable
private fun SkillEditorDialog(
    initial: Skill? = null,
    onDismiss: () -> Unit,
    onSave: (Skill) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: "Продуктивность") }
    FormDialog(
        title = if (initial == null) "Новый навык" else "Изменить навык",
        confirm = "Сохранить",
        enabled = title.isNotBlank() && instructions.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            val now = System.currentTimeMillis()
            onSave(
                Skill(
                    id = initial?.id ?: "user-" + UUID.randomUUID().toString().take(8),
                    title = title.trim().take(80),
                    description = description.trim().ifBlank { "Мой навык" }.take(400),
                    instructions = instructions.trim().take(12000),
                    icon = initial?.icon ?: "spark",
                    category = category,
                    author = "Вы",
                    installs = 0,
                    source = "user",
                    createdAt = initial?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        },
    ) {
        FormLabel("Название")
        SettingsField(value = title, onValueChange = { title = it }, placeholder = "Например, Помощник по договорам")
        FormLabel("Короткое описание")
        SettingsField(value = description, onValueChange = { description = it }, placeholder = "Что делает навык")
        FormLabel("Категория")
        CategoryPicker(selected = category, onSelect = { category = it })
        FormLabel("Инструкции для модели")
        SettingsField(
            value = instructions,
            onValueChange = { instructions = it },
            placeholder = "Как действовать, что проверять, в каком формате отвечать",
            height = 160.dp,
            singleLine = false,
            textSize = 15,
        )
    }
}

@Composable
private fun PluginEditorDialog(
    skills: List<Skill>,
    onDismiss: () -> Unit,
    onSave: (Plugin) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    FormDialog(
        title = "Новый плагин",
        confirm = "Сохранить",
        enabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                Plugin(
                    id = "plugin-" + UUID.randomUUID().toString().take(8),
                    title = title.trim().take(80),
                    description = description.trim().ifBlank { "Мой плагин" }.take(400),
                    instructions = instructions.trim().take(12000),
                    skillIds = picked.toList(),
                    author = "Вы",
                    source = "user",
                    createdAt = System.currentTimeMillis(),
                )
            )
        },
    ) {
        FormLabel("Название")
        SettingsField(value = title, onValueChange = { title = it }, placeholder = "Например, Работа с клиентами")
        FormLabel("Описание")
        SettingsField(value = description, onValueChange = { description = it }, placeholder = "Для чего этот плагин")
        FormLabel("Инструкции")
        SettingsField(
            value = instructions,
            onValueChange = { instructions = it },
            placeholder = "Общие правила для всех навыков плагина",
            height = 120.dp,
            singleLine = false,
            textSize = 15,
        )
        FormLabel("Навыки внутри")
        skills.take(14).forEach { skill ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        picked = if (skill.id in picked) picked - skill.id else picked + skill.id
                    }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (skill.id in picked) Icons.Default.Check else ClaudenIcons.plus,
                    contentDescription = null,
                    tint = if (skill.id in picked) LocalClaudenColors.current.blueLink
                    else LocalClaudenColors.current.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = skill.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = LocalClaudenColors.current.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun ConnectorEditorDialog(
    onDismiss: () -> Unit,
    onSave: (Connector) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val validUrl = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    FormDialog(
        title = "MCP-сервер",
        confirm = "Добавить",
        enabled = title.isNotBlank() && validUrl,
        onDismiss = onDismiss,
        onConfirm = {
            val clean = title.trim()
            val endpoint = url.trim()
            onSave(
                Connector(
                    id = "mcp-" + UUID.randomUUID().toString().take(8),
                    title = clean.take(60),
                    description = description.trim().ifBlank { "Собственный MCP-сервер" }.take(300),
                    author = "Вы",
                    mark = clean.take(2).uppercase(),
                    color = 0xFF3A6B5A,
                    category = "Свой",
                    section = "custom",
                    native = "mcp",
                    docs = endpoint,
                    url = endpoint,
                    headers = headers.trim().take(2000),
                )
            )
        },
    ) {
        FormLabel("Название")
        SettingsField(value = title, onValueChange = { title = it }, placeholder = "Мой MCP-сервер")
        FormLabel("URL сервера")
        SettingsField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://mcp.example.com/mcp",
        )
        FormLabel("Заголовок авторизации (если нужен)")
        SettingsField(
            value = headers,
            onValueChange = { headers = it },
            placeholder = "Authorization: Bearer …",
            height = 84.dp,
            singleLine = false,
            textSize = 15,
        )
        FormLabel("Описание")
        SettingsField(value = description, onValueChange = { description = it }, placeholder = "Какие инструменты даёт")
    }
}

@Composable
private fun FormDialog(
    title: String,
    confirm: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = colors.textSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                content()
                Spacer(modifier = Modifier.height(18.dp))
                SettingsActionButton(text = confirm, enabled = enabled, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = LocalClaudenColors.current.textSecondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalClaudenColors.current
    Box {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(ClaudenIcons.chevronRight, contentDescription = null, tint = colors.textTertiary)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.bg300,
        ) {
            CustomizeCatalog.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onSelect(category)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    FormDialog(
        title = title,
        confirm = confirm,
        enabled = value.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    ) {
        SettingsField(value = value, onValueChange = onValueChange, placeholder = hint)
    }
}
