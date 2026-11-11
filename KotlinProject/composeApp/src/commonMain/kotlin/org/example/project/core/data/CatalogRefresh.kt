package org.example.project.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Realtime is an invalidation hint. Initial reads and polling never depend on subscribing. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
internal fun <T> refreshingCatalog(load: suspend () -> T, changes: Flow<Unit>, refreshMillis: Long = 30_000): Flow<T> = flow {
    emit(load())
    val polling = flow { while (true) { delay(refreshMillis); emit(Unit) } }
    val hints = changes.catch { if (it is CancellationException) throw it else emit(Unit) }
    merge(hints, polling).conflate().sample(500).collect { emit(load()) }
}
