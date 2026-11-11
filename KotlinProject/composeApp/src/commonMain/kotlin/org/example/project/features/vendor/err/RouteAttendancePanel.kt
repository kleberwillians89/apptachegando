package org.example.project.features.vendor.err

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.core.domain.route.*
import org.example.project.core.ui.maps.NativeMap
import org.example.project.core.utils.Location

/** Operational view over the original route model and the derived queue. No per-interest accept. */
@Composable
fun RouteAttendancePanel(
    attendance: RouteAttendance, state: AttendanceSnapshot,
    onStart: () -> Unit, onPauseResume: () -> Unit, onEnd: () -> Unit, onHistory: () -> Unit,
    onConsumer: () -> Unit, onChooseRoute: () -> Unit,
    isDemonstration: Boolean = true, controlsEnabled: Boolean = true,
    highlightedInterestIds: Set<String> = emptySet()
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(attendance.route.name, style = MaterialTheme.typography.titleMedium)
            Text(if (!isDemonstration) when (state.phase) {
                JourneyPhase.Ready -> "Carregando jornada"
                JourneyPhase.Working -> "Trabalhando na rota"
                JourneyPhase.Paused -> "Jornada pausada · vendedor offline"
                JourneyPhase.Ended -> "Jornada encerrada"
            } else when (state.phase) {
                JourneyPhase.Ready -> "Rota salva no aparelho · pronta para selecionar"
                JourneyPhase.Working -> "Trabalhando · jornada simulada"
                JourneyPhase.Paused -> "Pausado · jornada simulada"
                JourneyPhase.Ended -> "Jornada encerrada · simulação"
            }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(if (isDemonstration) "Coordenadas, clientes e eventos fictícios. Nenhum aceite individual, venda ou atendimento confirmado." else "Interesses organizados automaticamente. Não confirmam estoque, venda ou atendimento. GPS compartilhado somente nesta tela em primeiro plano.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            NativeMap(Modifier.fillMaxWidth().height(210.dp),
                userLocation = if (isDemonstration) state.location ?: attendance.geometry.locationAt(0.0) else state.location,
                routePoints = attendance.route.waypoints.sortedBy { it.orderIndex }.map { Location(it.latitude, it.longitude) })
        }
        item {
            Text("${state.queue.size} ${if (state.queue.size == 1) "interesse" else "interesses"} à frente", style = MaterialTheme.typography.titleLarge)
            val passed = state.entries.count { it.status == AttendanceStatus.Behind }
            Text("${state.entries.size} registros preservados · $passed pontos ultrapassados", style = MaterialTheme.typography.bodySmall)
            if (!state.connected) Text(if (isDemonstration) "Sem conexão simulada. A lista aguarda reconciliação; registros preservados." else "Sem atualização confirmada. A fila aguarda reconexão; registros preservados.")
            if (state.gpsIssue != null && state.phase == JourneyPhase.Working) Text(if (isDemonstration) "Localização simulada indisponível ou não confiável. Aguardando atualização." else when (state.gpsIssue) {
                GpsIssue.Missing, GpsIssue.Stale -> "GPS indisponível ou desatualizado. Aguardando posição recente."
                GpsIssue.LowAccuracy -> "GPS sem precisão suficiente. A fila aguarda uma posição confiável."
                GpsIssue.OffRoute -> "Você está fora do corredor da rota. A fila aguarda seu retorno ao percurso."
                GpsIssue.Ambiguous -> "Posição ambígua neste trecho da rota. Aguarde a próxima atualização."
                GpsIssue.ImplausibleJump -> "Deslocamento incompatível entre posições. Aguardando confirmação do GPS."
                GpsIssue.Backtracking -> "Seu deslocamento está no sentido contrário ao percurso gravado."
                null -> ""
            })
            if (isDemonstration && state.phase == JourneyPhase.Ready) Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Iniciar jornada simulada") }
            if (state.phase == JourneyPhase.Working || state.phase == JourneyPhase.Paused) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = controlsEnabled, onClick = onPauseResume, modifier = Modifier.weight(1f)) { Text(if (state.phase == JourneyPhase.Working) "Pausar jornada" else "Retomar jornada") }
                Button(enabled = controlsEnabled, onClick = onEnd, modifier = Modifier.weight(1f)) { Text("Encerrar jornada") }
            }
            if (state.phase == JourneyPhase.Ended) OutlinedButton(onClick = onChooseRoute, modifier = Modifier.fillMaxWidth()) { Text("Selecionar rota novamente") }
            Row {
                TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Situações (${state.entries.size})") }
                if (isDemonstration) TextButton(onClick = onConsumer, modifier = Modifier.weight(1f)) { Text("Visão consumidor") }
            }
        }
        if (state.queue.isEmpty()) item { Text("Nenhum interesse elegível agora. Consulte as situações; nada foi descartado silenciosamente.") }
        items(state.queue.take(10), key = { it.intent.id }) { entry ->
            val highlighted = entry.intent.id in highlightedInterestIds
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                containerColor = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
            )) {
                Column(Modifier.padding(12.dp)) {
                    if (highlighted) Text("Novo interesse", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(if (isDemonstration) interestDisplayName(entry.intent.consumerId) else "Protocolo ${entry.intent.id.take(8)}", style = MaterialTheme.typography.titleMedium)
                    Text("${((entry.alongMeters ?: 0.0) - state.progressMeters).coerceAtLeast(0.0).toInt()} m no percurso${if (isDemonstration) " simulado" else ""}")
                    Text("Recebido automaticamente · disponibilidade a confirmar", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.queue.size > 10) item { Text("Exibindo próximos 10 de ${state.queue.size}. Demais registros em Situações.") }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

fun interestDisplayName(consumerId: String): String = if (consumerId == "demo-consumer-view") "Seu interesse (consumidor demo)"
    else "Cliente fictício ${consumerId.removePrefix("demo-client-")}"
