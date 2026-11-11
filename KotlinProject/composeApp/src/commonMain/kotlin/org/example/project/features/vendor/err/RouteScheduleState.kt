package org.example.project.features.vendor.err

import org.example.project.core.domain.models.RouteWeekday

data class RouteScheduleState(
    val routeName: String = "",
    val usesEveryDay: Boolean = true,
    val selectedDays: Set<RouteWeekday> = RouteWeekday.everyDay().toSet()
) {
    val routeNameForSave: String
        get() = routeName.trim().ifBlank { "Rota sem nome" }

    val activeDays: List<RouteWeekday>
        get() = if (usesEveryDay) {
            RouteWeekday.everyDay()
        } else {
            selectedDays.sortedBy { day -> day.ordinal }
        }

    val canStartRoute: Boolean
        get() = usesEveryDay || selectedDays.isNotEmpty()

    val scheduleSummary: String
        get() = when {
            usesEveryDay -> "Todos os dias"
            selectedDays.isEmpty() -> "Escolha pelo menos um dia"
            else -> activeDays.joinToString(", ") { day -> day.shortLabel }
        }

    fun renameRoute(value: String): RouteScheduleState {
        return copy(routeName = value)
    }

    fun selectEveryDay(): RouteScheduleState {
        return copy(
            usesEveryDay = true,
            selectedDays = RouteWeekday.everyDay().toSet()
        )
    }

    fun selectSpecificDays(): RouteScheduleState {
        return if (usesEveryDay) {
            copy(usesEveryDay = false, selectedDays = emptySet())
        } else {
            this
        }
    }

    fun toggleDay(day: RouteWeekday): RouteScheduleState {
        val nextDays = if (selectedDays.contains(day)) {
            selectedDays - day
        } else {
            selectedDays + day
        }
        return copy(usesEveryDay = false, selectedDays = nextDays)
    }

    fun isSelected(day: RouteWeekday): Boolean {
        return usesEveryDay || selectedDays.contains(day)
    }
}
