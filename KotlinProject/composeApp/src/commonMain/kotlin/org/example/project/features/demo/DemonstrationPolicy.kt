package org.example.project.features.demo

/** Reuse the existing local demonstration; never make it available in production. */
internal fun demonstrationAvailable(environment: String): Boolean = environment in setOf("development", "homologation")
