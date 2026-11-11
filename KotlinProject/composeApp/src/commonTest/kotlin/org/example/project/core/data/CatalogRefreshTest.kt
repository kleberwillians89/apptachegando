package org.example.project.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogRefreshTest {
    @Test fun deniedRealtimeDoesNotBlockFirstReadOrPolling() = runTest {
        var reads = 0
        val values = mutableListOf<Int>()
        val job = backgroundScope.launch {
            refreshingCatalog(load = { ++reads }, changes = flow { error("Realtime denied") }, refreshMillis = 1_000).collect { values += it }
        }
        runCurrent()
        assertEquals(listOf(1), values)
        advanceTimeBy(2_100); runCurrent()
        assertTrue(reads >= 3)
        job.cancel(); val stopped = reads
        advanceTimeBy(5_000); runCurrent()
        assertEquals(stopped, reads)
    }
    @Test fun failedReadNeverBecomesSuccessfulEmptyCatalog() = runTest {
        assertFailsWith<IllegalStateException> {
            refreshingCatalog<Int>(load = { error("read denied") }, changes = flow {}).collect { fail("false success") }
        }
    }
    @Test fun cancellingCollectorStopsRealtimeAndPolling() = runTest {
        var reads = 0
        val closed = CompletableDeferred<Unit>()
        val job = backgroundScope.launch {
            refreshingCatalog(load = { ++reads }, changes = flow {
                try { awaitCancellation() } finally { closed.complete(Unit) }
            }, refreshMillis = 1_000).collect { }
        }
        runCurrent()
        job.cancel(); runCurrent()
        assertTrue(job.isCancelled)
        assertTrue(closed.isCompleted)
        val stopped = reads
        advanceTimeBy(5_000); runCurrent()
        assertEquals(stopped, reads)
    }
}
