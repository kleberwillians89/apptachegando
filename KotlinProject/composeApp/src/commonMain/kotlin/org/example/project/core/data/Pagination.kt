package org.example.project.core.data

/** PostgREST ranges are inclusive. Validate before any network request. */
internal fun pageBounds(page: Int, size: Int): LongRange {
    require(page >= 0 && size in 1..100)
    val start = page.toLong() * size
    return start..(start + size - 1)
}
