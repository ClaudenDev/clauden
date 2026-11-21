package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.R
import space.teleopen.clauden.data.BillingPlan
import space.teleopen.clauden.data.CheckoutOrder
import space.teleopen.clauden.data.ClaudenCatalog

private enum class BillingStep { Plans, Configure, Payment }

private data class PayMethod(
    val id: String,
    val title: String,
    val subtitle: String,
    val logo: Int,
)

private val payMethods = listOf(
    PayMethod("xrocket", "Криптовалюта", "USDT", R.drawable.pay_xrocket),
    PayMethod("platega", "СБП", "QR-код или ссылка из банка", R.drawable.pay_sbp),
    PayMethod("lava", "Карта / PayPal", "Lava.top", R.drawable.pay_lava),
)

@Composable
fun BillingScreen(state: ChatUiState, vm: ChatViewModel) {
    val plans = remember(state.billingPlans) {
        state.billingPlans
            .ifEmpty { ClaudenCatalog.fallbackBillingPlans }
            .sortedBy { it.order }
    }
    val current = state.settings.plan.ifBlank { "Free" }
    val currentPaid = !current.isBlank() && !current.equals("Free", ignoreCase = true)
    var step by remember { mutableStateOf(BillingStep.Plans) }
    var selectedId by remember { mutableStateOf("") }
    var methodId by remember { mutableStateOf(payMethods.first().id) }
    var autoRenew by remember { mutableStateOf(true) }
    var confirmCancelRenewal by remember { mutableStateOf(false) }
    val selected = plans.firstOrNull { it.id == selectedId }
    val uriHandler = LocalUriHandler.current

    if (state.checkoutNeedsPassword) {
        PasswordSetupDialog(
            hasPassword = false,
            busy = state.authBusy,
            error = state.authError,
            onDismiss = vm::cancelCheckoutPassword,
            onSave = vm::setClaudePassword,
        )
    }

    LaunchedEffect(currentPaid) {
        if (currentPaid) step = BillingStep.Plans
    }

    LaunchedEffect(state.checkoutOrder?.id) {
        val url = state.checkoutOrder?.url.orEmpty()
        if (url.isNotBlank()) uriHandler.openUri(url)
    }

    LaunchedEffect(state.checkoutOrder?.id, state.checkoutOrder?.status) {
        val order = state.checkoutOrder ?: return@LaunchedEffect
        if (order.status != "pending") return@LaunchedEffect
        while (true) {
            delay(6_000)
            vm.refreshCheckout()
        }
    }

    BackHandler(enabled = step != BillingStep.Plans) {
        step = when (step) {
            BillingStep.Payment -> if (selected?.group == "max_ultra") {
                BillingStep.Configure
            } else {
                BillingStep.Plans
            }

            BillingStep.Configure -> BillingStep.Plans
            else -> BillingStep.Plans
        }
    }

    val goBack: () -> Unit = {
        when (step) {
            BillingStep.Plans -> vm.backToSettings()
            BillingStep.Configure -> step = BillingStep.Plans
            BillingStep.Payment -> step = if (selected?.group == "max_ultra") {
                BillingStep.Configure
            } else {
                BillingStep.Plans
            }
        }
    }

    when (step) {
        BillingStep.Plans -> PlansPage(
            plans = plans,
            current = current,
            claudeLinked = state.claudeLinked,
            claudePlan = state.claudePlan,
            autoRenew = state.auth.autoRenew,
            cancelBusy = state.billingCancelBusy,
            onBack = goBack,
            onTeam = { vm.showNotice("Тарифы для команд скоро появятся") },
            onFree = { vm.showNotice("Переход на Free — через поддержку") },
            onPro = {
                selectedId = "Pro"
                methodId = payMethods.first().id
                autoRenew = true
                step = BillingStep.Payment
            },
            onMaxUltra = {
                selectedId = if (current.equals("Max", true) || current.equals("Ultra", true)) {
                    current
                } else {
                    "Max"
                }
                methodId = payMethods.first().id
                autoRenew = true
                step = BillingStep.Configure
            },
            onCancelRenewal = { confirmCancelRenewal = true },
        )

        BillingStep.Configure -> ConfigurePage(
            plans = plans,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            onBack = goBack,
            onContinue = {
                if (selectedId.isNotBlank()) {
                    methodId = payMethods.first().id
                    autoRenew = true
                    step = BillingStep.Payment
                }
            },
        )

        BillingStep.Payment -> {
            val plan = selected
            if (plan == null) {
                step = BillingStep.Plans
            } else {
                PaymentPage(
                    plan = plan,
                    methodId = methodId,
                    autoRenew = autoRenew,
                    busy = state.checkoutBusy,
                    onSelectMethod = { methodId = it },
                    onAutoRenewChange = { autoRenew = it },
                    onBack = goBack,
                    onContinue = { vm.startCheckout(plan.id, methodId, autoRenew) },
                )
            }
        }
    }

    if (state.checkoutOrder != null || state.checkoutError != null) {
        CheckoutSheet(
            order = state.checkoutOrder,
            error = state.checkoutError,
            onOpen = { url -> uriHandler.openUri(url) },
            onCheck = vm::refreshCheckout,
            onDismiss = vm::clearCheckout,
        )
    }

    if (confirmCancelRenewal) {
        SettingsConfirmDialog(
            title = "Отменить продление?",
            text = "Тариф $current останется до конца оплаченного срока. " +
                "Следующее автосписание не произойдёт, подписку можно оформить снова.",
            confirmText = "Отменить продление",
            onConfirm = {
                confirmCancelRenewal = false
                vm.cancelRenewal()
            },
            onDismiss = { confirmCancelRenewal = false },
        )
    }
}

@Composable
private fun PlansPage(
    plans: List<BillingPlan>,
    current: String,
    claudeLinked: Boolean,
    claudePlan: String,
    autoRenew: Boolean,
    cancelBusy: Boolean,
    onBack: () -> Unit,
    onTeam: () -> Unit,
    onFree: () -> Unit,
    onPro: () -> Unit,
    onMaxUltra: () -> Unit,
    onCancelRenewal: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DetailScaffold(title = "Оплата", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Тарифы, которые растут вместе с вами",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp,
                ),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TeamToggle(onTeam)
        }

        if (claudeLinked) {
            Text(
                text = "Аккаунт Claude: ${claudePlanTitle(claudePlan)}. Это отдельная " +
                    "подписка — она работает в режиме Claude и не входит в тариф Clauden.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        val currentPaid = !current.isBlank() && !current.equals("Free", ignoreCase = true)
        plans.filter { it.group.isBlank() }.forEach { plan ->
            val isCurrent = plan.id.equals(current, ignoreCase = true)
            PlanCard(
                plan = plan,
                current = isCurrent,
                paid = currentPaid && isCurrent,
                locked = currentPaid && !isCurrent,
                autoRenew = autoRenew && isCurrent,
                cancelBusy = cancelBusy,
                highlighted = plan.id.equals("Pro", ignoreCase = true),
                showProNote = plan.id.equals("Pro", ignoreCase = true),
                onCancelRenewal = onCancelRenewal,
                onCta = {
                    when {
                        isCurrent -> Unit
                        plan.id.equals("Free", ignoreCase = true) -> onFree()
                        else -> onPro()
                    }
                },
            )
        }

        val max = plans.firstOrNull { it.id.equals("Max", ignoreCase = true) }
        val ultra = plans.firstOrNull { it.id.equals("Ultra", ignoreCase = true) }
        if (max != null && ultra != null) {
            val maxUltraCurrent =
                current.equals("Max", ignoreCase = true) ||
                    current.equals("Ultra", ignoreCase = true)
            MaxUltraCard(
                max = max,
                ultra = ultra,
                currentLabel = current,
                paid = currentPaid && maxUltraCurrent,
                locked = currentPaid && !maxUltraCurrent,
                autoRenew = autoRenew && maxUltraCurrent,
                cancelBusy = cancelBusy,
                onCancelRenewal = onCancelRenewal,
                onCta = onMaxUltra,
            )
        }

        Text(
            text = "Цены указаны в долларах США. Налоги могут применяться. " +
                "Подписку можно отменить в любой момент.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Text(
            text = "Ultra: $180 вместо $200 — скидка $20.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.clay,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TeamToggle(onTeam: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(11.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            Surface(
                color = colors.control,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Индивидуально",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
            Text(
                text = "Команда и бизнес",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = colors.textTertiary,
                modifier = Modifier
                    .alpha(0.7f)
                    .clickable(onClick = onTeam)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun PaidBadge() {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.clay.copy(alpha = 0.16f),
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.clay.copy(alpha = 0.45f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = ClaudenIcons.check,
                contentDescription = null,
                tint = colors.clay,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Оплачено",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.clay,
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: BillingPlan,
    current: Boolean,
    paid: Boolean,
    locked: Boolean,
    autoRenew: Boolean,
    cancelBusy: Boolean,
    highlighted: Boolean,
    showProNote: Boolean,
    onCancelRenewal: () -> Unit,
    onCta: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val border = when {
        paid -> colors.clay
        highlighted -> colors.clay.copy(alpha = 0.65f)
        else -> colors.border.copy(alpha = 0.55f)
    }
    Surface(
        color = if (paid) colors.card else colors.bg300,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(if (paid) 1.5.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.45f else 1f)
            .clickable(enabled = !paid && !locked && !current, onClick = onCta),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ClaudenIcons.spark,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (paid) PaidBadge()
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = plan.label,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = plan.tagline,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            PriceLine(price = plan.price)
            Spacer(modifier = Modifier.height(16.dp))
            BillingButton(
                text = when {
                    paid && autoRenew -> if (cancelBusy) "Отменяем…" else "Отменить продление"
                    paid -> "Оплачено"
                    locked -> "Недоступно"
                    current -> "Текущий тариф"
                    else -> plan.cta.ifBlank { "Выбрать ${plan.label}" }
                },
                enabled = (!paid && !locked && !current) || (paid && autoRenew && !cancelBusy),
                primary = (!paid && !locked && !current) || (paid && autoRenew),
                onClick = if (paid && autoRenew) onCancelRenewal else onCta,
            )
            if (showProNote) {
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = "Без обязательств · Отмена в любой момент",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (plan.features.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = if (plan.id.equals("Free", ignoreCase = true)) {
                        "Что входит:"
                    } else {
                        "Всё из Free и:"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                plan.features.forEach { feature ->
                    FeatureRow(feature)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MaxUltraCard(
    max: BillingPlan,
    ultra: BillingPlan,
    currentLabel: String,
    paid: Boolean,
    locked: Boolean,
    autoRenew: Boolean,
    cancelBusy: Boolean,
    onCancelRenewal: () -> Unit,
    onCta: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (paid) colors.card else colors.bg300,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            if (paid) 1.5.dp else 1.dp,
            if (paid) colors.clay else colors.border.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.45f else 1f)
            .clickable(enabled = !paid && !locked, onClick = onCta),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ClaudenIcons.spark,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (paid) {
                    PaidBadge()
                } else if (ultra.badge.isNotBlank()) {
                    Surface(
                        color = colors.clay.copy(alpha = 0.16f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = ultra.badge,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.clay,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Max и Ultra",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Повышенные лимиты и приоритетный доступ",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textSecondary,
            )
            if (paid) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Подключён ${currentLabel.ifBlank { "Max" }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.clay,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "От $${max.price}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "/ месяц",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = "Max $${max.price} · Ultra $${ultra.price}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            BillingButton(
                text = when {
                    paid && autoRenew -> if (cancelBusy) "Отменяем…" else "Отменить продление"
                    paid -> "Оплачено"
                    locked -> "Недоступно"
                    else -> "Выбрать Max или Ultra"
                },
                enabled = (!paid && !locked) || (paid && autoRenew && !cancelBusy),
                primary = (!paid && !locked) || (paid && autoRenew),
                onClick = if (paid && autoRenew) onCancelRenewal else onCta,
            )
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = "Без обязательств · Отмена в любой момент",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Всё из Pro, плюс:",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            listOf(
                "До 7,4× больше лимитов Pro",
                "Приоритетный доступ в часы пик",
                "Ранний доступ к новым возможностям",
                "Выделенная поддержка",
            ).forEach { feature ->
                FeatureRow(feature)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConfigurePage(
    plans: List<BillingPlan>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val options = plans.filter { it.group == "max_ultra" }.sortedBy { it.price }
    DetailScaffold(title = "Оплата", onBack = onBack) {
        Text(
            text = "Настройте тариф",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = LocalClaudenFonts.current.serif,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
            ),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Text(
            text = "Выберите, сколько лимитов вам нужно.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { plan ->
                ConfigureCard(
                    plan = plan,
                    selected = plan.id == selectedId,
                    onSelect = { onSelect(plan.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        BillingButton(
            text = "Продолжить",
            enabled = selectedId.isNotBlank(),
            onClick = onContinue,
        )
        Text(
            text = "Цены указаны в долларах США. Налоги могут применяться.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ConfigureCard(
    plan: BillingPlan,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.card else colors.bg300,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) colors.clay else colors.border,
        ),
        modifier = modifier.clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RadioDot(selected)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = plan.label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = plan.tagline,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$${plan.price}",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "USD",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            if (plan.compareAt > plan.price) {
                Text(
                    text = "$${plan.compareAt}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (plan.badge.isNotBlank()) {
                    Surface(
                        color = colors.clay.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = plan.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.clay,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentPage(
    plan: BillingPlan,
    methodId: String,
    autoRenew: Boolean,
    busy: Boolean,
    onSelectMethod: (String) -> Unit,
    onAutoRenewChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DetailScaffold(title = "Оплата", onBack = onBack) {
        Text(
            text = "Способ оплаты",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = LocalClaudenFonts.current.serif,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
            ),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Text(
            text = buildString {
                append("Тариф ${plan.label} · $${plan.price} в месяц")
                if (plan.compareAt > plan.price) append(" · вместо $${plan.compareAt}")
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
        )

        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, colors.border.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                payMethods.forEachIndexed { index, method ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(horizontal = 14.dp)
                                .background(colors.border.copy(alpha = 0.45f)),
                        )
                    }
                    MethodRow(
                        method = method,
                        selected = method.id == methodId,
                        onClick = { onSelectMethod(method.id) },
                    )
                }
            }
        }

        if (methodId == "platega") {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = colors.bg300,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Автопродление",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (autoRenew) {
                                "Тариф продлится автоматически через 30 дней. " +
                                    "Отключить можно в любой момент в тарифах."
                            } else {
                                "Оформим разовый платёж на 30 дней без автосписаний."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    SettingsToggle(
                        checked = autoRenew,
                        onCheckedChange = onAutoRenewChange,
                    )
                }
            }
        }

        BillingButton(
            text = when {
                busy -> "Создаём счёт…"
                methodId == "platega" -> "Оплатить через СБП"
                methodId == "lava" -> "Оплатить картой"
                else -> "Оплатить $${plan.price}"
            },
            enabled = !busy,
            onClick = onContinue,
        )
        if (methodId == "lava") {
            Text(
                text = "Итоговую сумму и валюту показывает Lava.top на своём экране оплаты.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (methodId == "platega") {
            Text(
                text = "СБП: откройте ссылку, выберите банк и подтвердите платёж. Для оплаты используется email аккаунта.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        Text(
            text = "Итоговая сумма и валюта — на странице оплаты. Тариф активируется после подтверждения платежа.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MethodRow(
    method: PayMethod,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(method.logo),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = method.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
            Text(
                text = method.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
        RadioDot(selected)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(
    order: CheckoutOrder?,
    error: String?,
    onOpen: (String) -> Unit,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val method = payMethods.firstOrNull { it.id == order?.method }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = if (order == null) "Не удалось создать счёт" else "Оплата тарифа ${order.plan}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (order == null) {
                Text(
                    text = error ?: "Платёжный сервис не ответил",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                BillingButton(text = "Закрыть", onClick = onDismiss)
                Spacer(modifier = Modifier.height(8.dp))
                BillingButton(
                    text = "Написать в поддержку",
                    primary = false,
                    onClick = {
                        onDismiss()
                        uriHandler.openUri("https://t.me/ClaudenApp")
                    },
                )
                return@Column
            }

            Text(
                text = if (order.currency == "RUB") {
                    "${order.chargeAmount.takeIf { it > 0 } ?: order.amount} ₽ · ${method?.title ?: order.method}"
                } else {
                    "$${order.chargeAmount.takeIf { it > 0 } ?: order.amount} · ${method?.title ?: order.method}"
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (order.status) {
                "paid" -> {
                    Text(
                        text = "Оплата подтверждена. Тариф ${order.plan} активирован.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    BillingButton(text = "Готово", onClick = onDismiss)
                }

                "failed", "expired" -> {
                    Text(
                        text = "Платёж не завершён. Можно попробовать ещё раз или выбрать другой способ.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    BillingButton(text = "Закрыть", onClick = onDismiss)
                }

                else -> {
                    Text(
                        text = "Счёт создан. Оплатите его в открывшемся окне — тариф " +
                            "включится автоматически после подтверждения.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = colors.textSecondary,
                    )
                    if (order.autoRenew) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Автопродление включено: списание раз в месяц, " +
                                "отменить можно в тарифах.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            color = colors.textTertiary,
                        )
                    }
                    if (order.renewalNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = order.renewalNote,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    if (order.url.isNotBlank()) {
                        BillingButton(
                            text = "Открыть оплату",
                            onClick = { onOpen(order.url) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    BillingButton(
                        text = "Проверить оплату",
                        primary = false,
                        onClick = onCheck,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BillingButton(
                        text = "Закрыть",
                        primary = false,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceLine(price: Int) {
    val colors = LocalClaudenColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$$price",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = if (price == 0) "навсегда" else "/ месяц",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(bottom = 5.dp),
        )
    }
    if (price > 0) {
        Text(
            text = "Оплата ежемесячно + налог",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun FeatureRow(text: String) {
    val colors = LocalClaudenColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = ClaudenIcons.check,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.clay.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.clay else colors.textTertiary.copy(alpha = 0.55f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.clay),
            )
        }
    }
}

@Composable
private fun BillingButton(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (primary) colors.textPrimary else colors.control,
        contentColor = if (primary) colors.bg else colors.textPrimary,
        shape = RoundedCornerShape(10.dp),
        border = if (primary) null else BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
