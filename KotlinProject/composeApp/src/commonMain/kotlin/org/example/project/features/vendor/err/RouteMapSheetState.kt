package org.example.project.features.vendor.err

data class RouteMapSheetState(
    val isVisible: Boolean = true
) {
    fun hide(): RouteMapSheetState = copy(isVisible = false)

    fun show(): RouteMapSheetState = copy(isVisible = true)

    fun settleExpandedDrag(offset: Float): RouteMapSheetState {
        return if (offset >= DRAG_DISTANCE_TO_SWITCH) hide() else this
    }

    fun settleCollapsedDrag(offset: Float): RouteMapSheetState {
        return if (offset <= -DRAG_DISTANCE_TO_SWITCH) show() else this
    }

    companion object {
        const val DRAG_DISTANCE_TO_SWITCH = 90f
    }
}
