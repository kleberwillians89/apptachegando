package org.example.project.features.consumer

data class ConsumerMapSheetState(
    val isVisible: Boolean = true
) {
    fun hide(): ConsumerMapSheetState = copy(isVisible = false)

    fun show(): ConsumerMapSheetState = copy(isVisible = true)

    fun settleExpandedDrag(offset: Float): ConsumerMapSheetState {
        return if (offset >= DRAG_DISTANCE_TO_SWITCH) hide() else this
    }

    fun settleCollapsedDrag(offset: Float): ConsumerMapSheetState {
        return if (offset <= -DRAG_DISTANCE_TO_SWITCH) show() else this
    }

    companion object {
        const val DRAG_DISTANCE_TO_SWITCH = 90f
    }
}
