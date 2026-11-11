package org.example.project.features.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.core.ui.maps.NativeMap

/** Explicitly synthetic playback through the original RouteDraftState and RouteRepository. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoRouteScreen(session: DemoRouteSession, onBack: () -> Unit) {
    val draft by session.draft.collectAsState()
    val routes by session.routes.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draft.isRecording) {
        if (draft.isRecording) repeat(session.syntheticPoints.size) { delay(1_000); session.sampleNext() }
    }
    DisposableEffect(session) { onDispose { session.finishRecording() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Rota habitual · simulação") }, navigationIcon = { TextButton(onClick = onBack) { Text("Home") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Percurso fictício em São Paulo. Sem GPS do aparelho ou publicação no Supabase. Salvo apenas nesta sessão.")
            NativeMap(Modifier.fillMaxWidth().height(250.dp), userLocation = session.syntheticPoints.first(), routePoints = draft.routePoints)
            Text("${draft.waypoints.size} pontos sintéticos", style = MaterialTheme.typography.titleMedium)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (draft.isRecording) Button(onClick = { session.finishRecording() }, modifier = Modifier.fillMaxWidth()) { Text("Encerrar percurso simulado") }
            else Button(onClick = { message = null; session.startRecording() }, modifier = Modifier.fillMaxWidth()) { Text("Gravar percurso simulado") }
            OutlinedButton(enabled = draft.canStartRoute && !draft.isRecording, onClick = { scope.launch {
                session.save(); message = "Rota salva na sessão; nenhuma rota real publicada."
            } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar rota demo") }
            routes.forEach { route ->
                OutlinedButton(onClick = { scope.launch {
                    session.reuse(route.id); message = "Percurso recuperado da sessão. Reuso entre dias no servidor ainda não homologado."
                } }, modifier = Modifier.fillMaxWidth()) { Text("Reutilizar ${route.name}") }
            }
            Text("Gravação real por GPS já existe no app. Persistência remota, reuso em outros dias e comportamento em segundo plano exigem homologação separada.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
