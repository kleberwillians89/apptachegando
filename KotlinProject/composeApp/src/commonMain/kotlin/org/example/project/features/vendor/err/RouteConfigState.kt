package org.example.project.features.vendor.err

import org.example.project.core.domain.models.ERRConfig

data class RouteConfigState(
    val deviationToleranceMeters: Int = 150
) {
    val toleranceLabel: String
        get() = "$deviationToleranceMeters metros"

    val distanceLabel: String
        get() = RouteDistancePreset.entries
            .firstOrNull { preset -> preset.meters == deviationToleranceMeters }
            ?.label
            ?: toleranceLabel

    fun adjustTolerance(value: Int): RouteConfigState {
        return copy(deviationToleranceMeters = value.coerceIn(MIN_TOLERANCE_METERS, MAX_TOLERANCE_METERS))
    }

    fun selectDistancePreset(preset: RouteDistancePreset): RouteConfigState {
        return copy(deviationToleranceMeters = preset.meters)
    }

    fun toERRConfig(): ERRConfig {
        return ERRConfig(deviationToleranceMeters = deviationToleranceMeters)
    }

    companion object {
        const val MIN_TOLERANCE_METERS = 50
        const val MAX_TOLERANCE_METERS = 300
    }
}

enum class RouteDistancePreset(
    val label: String,
    val helper: String,
    val meters: Int
) {
    Close("Perto", "Avisa clientes bem proximos", 100),
    Normal("Normal", "Bom para a maioria das rotas", 150),
    Far("Mais longe", "Avisa clientes um pouco mais distantes", 300)
}
