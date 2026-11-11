package org.example.project.features.vendor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.core.data.RouteRepository
import org.example.project.core.data.AuthRepository
import org.example.project.core.data.backendResult
import org.example.project.core.utils.UiState
import org.example.project.core.domain.models.VendorRoute
import org.example.project.core.data.VendorRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorRouteScreen(
    onStopRoute: () -> Unit,
    onStartRoute: () -> Unit = {},
    onResumeRoute: (String) -> Unit = {},
    onReuseRoute: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val repository = remember { RouteRepository() }
    var savedRoutes by remember { mutableStateOf<List<VendorRoute>>(emptyList()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var isStopping by remember { mutableStateOf(false) }
    var activeRouteId by remember { mutableStateOf<String?>(null) }
    var isLoadingRoute by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val userId = AuthRepository.currentUser()?.id
        if (userId == null) {
            errorMessage = "Entre na sua conta para recuperar as rotas."
            isLoadingRoute = false
            return@LaunchedEffect
        }
        when (val result = backendResult("Não foi possível recuperar sua rota.") {
            val vendorId = VendorRepository.resolveRouteVendorId(userId)
            val active = repository.getActiveRoute(vendorId)
            when (val history = backendResult("Não foi possível carregar os percursos salvos.") {
                repository.getSavedRoutes(vendorId)
            }) {
                is UiState.Success -> savedRoutes = history.data
                is UiState.Error -> historyError = history.message
                else -> Unit
            }
            active
        }) {
            is UiState.Success -> activeRouteId = result.data?.id
            is UiState.Error -> errorMessage = result.message
            else -> Unit
        }
        isLoadingRoute = false
        // Opening route management does not start a work journey or publish GPS.
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Minha rota") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            // Native Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                org.example.project.core.ui.maps.NativeMap(
                    modifier = Modifier.fillMaxSize(),
                    onMapClick = {}
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (savedRoutes.isNotEmpty() && activeRouteId == null) {
                    Text("Reutilizar percurso salvo (até 20)")
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                        items(savedRoutes, key = { it.id }) { route ->
                            TextButton(enabled = !isLoadingRoute && !isStopping, onClick = { onReuseRoute(route.id) }) {
                                Text(route.name)
                            }
                        }
                    }
                }
                Button(
                    enabled = !isLoadingRoute && !isStopping && errorMessage == null,
                    onClick = { activeRouteId?.let(onResumeRoute) ?: onStartRoute() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(if (activeRouteId == null) "Criar nova rota" else "Continuar rota")
                }

                Button(
                    enabled = !isLoadingRoute && !isStopping,
                    onClick = {
                        if (isStopping) return@Button
                        isStopping = true
                        scope.launch {
                            val id = activeRouteId
                            if (id == null) { isStopping = false; onStopRoute(); return@launch }
                            when (val result = backendResult("Não foi possível encerrar sua rota.") { repository.endRoute(id) }) {
                                is UiState.Success -> onStopRoute()
                                is UiState.Error -> errorMessage = result.message
                                else -> Unit
                            }
                            isStopping = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Parar rota")
                }
            }
        }
    }
}
