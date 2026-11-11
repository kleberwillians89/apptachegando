package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import org.example.project.core.utils.UiState

suspend fun <T> backendResult(message: String, block: suspend () -> T): UiState<T> = try {
    UiState.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    UiState.Error(message)
}
