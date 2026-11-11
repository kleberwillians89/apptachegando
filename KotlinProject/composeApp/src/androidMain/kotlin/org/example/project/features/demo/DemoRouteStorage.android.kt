package org.example.project.features.demo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberDemoRouteStorage(): DemoRouteStorage? {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : DemoRouteStorage {
            private val preferences = context.getSharedPreferences("tachegando_synthetic_routes_v1", Context.MODE_PRIVATE)
            override suspend fun read(): String? = withContext(Dispatchers.IO) { preferences.getString("routes", null) }
            override suspend fun write(value: String) = withContext(Dispatchers.IO) {
                check(preferences.edit().putString("routes", value).commit()) { "Não foi possível salvar a rota demo no aparelho." }
            }
        }
    }
}
