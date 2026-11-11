package org.example.project.core.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

// Lazy initialization lets the UI open without credentials and returns a useful error on use.
val supabase by lazy {
    check(GeneratedAppConfiguration.supabaseKey.isNotBlank()) {
        "Configure o Supabase local antes de entrar ou cadastrar uma conta."
    }
    createSupabaseClient(
        supabaseUrl = GeneratedAppConfiguration.supabaseUrl,
        supabaseKey = GeneratedAppConfiguration.supabaseKey
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
}
