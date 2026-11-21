package space.teleopen.clauden.ui

import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.data.AuthMode
import java.util.Locale

fun claudePlanName(plan: String): String = when (plan.lowercase(Locale.US)) {
    "pro" -> "Pro"
    "max" -> "Max"
    "team" -> "Team"
    else -> "Free"
}

fun claudePlanTitle(plan: String): String = "Claude " + claudePlanName(plan)

fun activePlanLabel(state: ChatUiState): String =
    if (state.authMode == AuthMode.Claude && state.claudeLinked) {
        claudePlanTitle(state.claudePlan)
    } else {
        "Clauden " + state.settings.plan.ifBlank { "Free" }
    }
