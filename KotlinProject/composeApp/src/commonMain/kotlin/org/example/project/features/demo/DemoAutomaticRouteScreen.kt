package org.example.project.features.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.core.domain.route.*
import org.example.project.features.vendor.err.RouteAttendancePanel
import org.example.project.features.vendor.err.interestDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoAutomaticRouteScreen(demo: DemoAutomaticAttendance, persistent: Boolean,
    openConsumerOnEnter: Boolean = false, onConsumerOpened: () -> Unit = {},
    onExploreCatalog: () -> Unit = {}, onBack: () -> Unit) {
    val routes = demo.routes
    val attendance by demo.attendance.collectAsState()
    val saved by routes.routes.collectAsState()
    val previousJourneys by demo.previousJourneys.collectAsState()
    var showPrevious by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var consumer by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var playback by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(10) }
    LaunchedEffect(openConsumerOnEnter) {
        if (openConsumerOnEnter) { consumer = true; onConsumerOpened() }
    }
    fun act(block: suspend () -> Unit) { scope.launch {
        try { error = null; block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Não foi possível concluir a ação local. A rota salva foi preservada." }
    } }
    LaunchedEffect(routes) {
        try { routes.restoreSavedRoutes() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { error = "Arquivo demo inválido ou indisponível. Dados preservados; não foram apagados." }
        loading = false
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle) {
        if (!lifecycle.isAtLeast(Lifecycle.State.STARTED)) { playback = false; attendance?.pause() }
    }
    LaunchedEffect(playback, attendance, lifecycle) {
        if (!playback || !lifecycle.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        repeat(3) { delay(2500); if (attendance?.state?.value?.phase == JourneyPhase.Working) demo.advance() }
        playback = false
    }
    if (showPrevious) AlertDialog(onDismissRequest = { showPrevious = false }, title = { Text("Histórico local da sessão") }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            previousJourneys.forEachIndexed { index, snapshot ->
                item { Text("Jornada ${index + 1} · ${snapshot.entries.size} registros preservados") }
                items(snapshot.entries, key = { "${index}-${it.intent.id}" }) { Text("${interestDisplayName(it.intent.consumerId)} · ${it.status.label}") }
            }
        }
    }, confirmButton = { TextButton(onClick = { showPrevious = false }) { Text("Fechar") } })
    Scaffold(topBar = { TopAppBar(title = { Text("Atendimento por rota · demo") },
        navigationIcon = { TextButton(onClick = { playback = false; onBack() }) { Text("Voltar") } },
        actions = { if (attendance != null) TextButton(onClick = { controls = true }) { Text("Simulação") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            val selected = attendance
            if (selected == null) {
                LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Sua rota, menos toques", style = MaterialTheme.typography.headlineMedium)
                        Text("Selecione uma rota fictícia salva neste Android. Interesses entrarão na lista sem aceite individual. Sem GPS real ou Supabase.")
                        if (!persistent) Text("Persistência desta demonstração disponível somente no Android.")
                        if (loading) CircularProgressIndicator()
                    }
                    items(saved, key = { it.id }) { route ->
                        ElevatedCard(onClick = { act { demo.select(route.id) } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Selecionar rota salva", style = MaterialTheme.typography.titleMedium)
                                Text(route.name)
                                Text("${route.waypoints.size} pontos fictícios · armazenamento privado local", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (previousJourneys.isNotEmpty()) item {
                        TextButton(onClick = { showPrevious = true }) { Text("Histórico de jornadas da sessão (${previousJourneys.size})") }
                    }
                    if (saved.isEmpty()) item {
                        Button(enabled = persistent && !loading && error == null, onClick = { act { demo.prepareRoute() } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar rota de exemplo") }
                    }
                    item { Text("Rotas e contas reais não são modificadas. Clientes simulados ficam apenas nesta sessão.", style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                val state by selected.state.collectAsState()
                RouteAttendancePanel(selected, state,
                    onStart = { act { demo.start() } },
                    onPauseResume = { playback = false; act { if (state.phase == JourneyPhase.Working) demo.pause() else demo.resume() } },
                    onEnd = { ending = true; playback = false }, onHistory = { history = true }, onConsumer = { consumer = true },
                    onChooseRoute = { act { demo.chooseRouteAgain() } })
                if (controls) AlertDialog(onDismissRequest = { controls = false }, title = { Text("Controles de simulação") }, text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { controls = false; onExploreCatalog() }) { Text("Explorar como cliente") }
                        Text("Tempo, clientes, conexão e GPS abaixo são sintéticos. Sem notificações ou mensagens reais.")
                        Row { listOf(10, 90, 450).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) } }
                        Button(enabled = state.phase == JourneyPhase.Working, onClick = { controls = false; act { demo.simulateClients(count) } }) { Text("Simular interesses") }
                        OutlinedButton(enabled = state.phase == JourneyPhase.Working, onClick = { controls = false; act { demo.advance() } }) { Text("Avançar posição simulada") }
                        TextButton(enabled = state.phase == JourneyPhase.Working, onClick = { controls = false; playback = true }) { Text("Percorrer automaticamente (demo)") }
                        TextButton(onClick = { controls = false; act { demo.replayEvents() } }) { Text("Reenviar mesmos eventos") }
                        TextButton(onClick = { controls = false; act { demo.toggleConnection() } }) { Text(if (state.connected) "Desconectar simulação" else "Reconectar simulação") }
                        TextButton(onClick = { controls = false; act { if (state.gpsIssue == null) demo.loseGps() else demo.restoreGps() } }) { Text(if (state.gpsIssue == null) "Perder sinal simulado" else "Recuperar sinal simulado") }
                    }
                }, confirmButton = { TextButton(onClick = { controls = false }) { Text("Fechar") } })
                if (history) AlertDialog(onDismissRequest = { history = false }, title = { Text("Situações dos interesses") }, text = {
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.entries, key = { it.intent.id }) { entry ->
                            Column { Text(interestDisplayName(entry.intent.consumerId), style = MaterialTheme.typography.titleSmall); Text(entry.status.label) }
                        }
                        if (state.entries.isEmpty()) item { Text("Nenhum interesse nesta jornada.") }
                    }
                }, confirmButton = { TextButton(onClick = { history = false }) { Text("Fechar") } })
                if (consumer) DemoRouteConsumerDialog(demo, state, onClose = { consumer = false })
                if (ending) AlertDialog(onDismissRequest = { ending = false }, title = { Text("Encerrar jornada simulada?") }, text = { Text("Os interessados verão Jornada encerrada. A rota fictícia salva permanece no aparelho.") },
                    confirmButton = { TextButton(onClick = { ending = false; act { demo.end() } }) { Text("Confirmar encerramento") } },
                    dismissButton = { TextButton(onClick = { ending = false }) { Text("Continuar jornada") } })
            }
        }
    }
}

@Composable
private fun DemoRouteConsumerDialog(demo: DemoAutomaticAttendance, state: AttendanceSnapshot, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val protocol by demo.consumerProtocol.collectAsState()
    val cancelPending by demo.consumerCancellationPending.collectAsState()
    val context by demo.consumerContext.collectAsState()
    val entry = state.entries.firstOrNull { it.intent.id == protocol }
    var option by remember { mutableIntStateOf(0) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    var confirmed by remember { mutableStateOf(false) }
    val point = remember(option, selectionRevision, protocol) {
        if (selectionRevision == 0) entry?.intent?.location ?: demo.meetingPoint(option) else demo.meetingPoint(option)
    }
    var error by remember { mutableStateOf<String?>(null) }
    fun act(block: suspend () -> Unit) { scope.launch {
        try { error = null; block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Não foi possível atualizar a consulta local." }
    } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Consumidor · visão simulada") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${context.vendorName} · categoria ${context.category}. Ele segue a rota habitual; não está vindo especificamente até você.")
            listOf("Esquina à frente", "Ponto atrás", "Fora do percurso").forEachIndexed { index, label ->
                FilterChip(selected = option == index, onClick = { option = index; selectionRevision++; confirmed = false }, label = { Text("$label (simulado)") })
            }
            Text("Ponto fictício: ${point.latitude.toString().take(9)}, ${point.longitude.toString().take(9)}. Não usa GPS do aparelho.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmed = true }) { Text(if (confirmed) "Ponto simulado confirmado" else "Confirmar ponto simulado") }
            Button(enabled = confirmed && state.phase in listOf(JourneyPhase.Working, JourneyPhase.Paused), onClick = { act { demo.consumerInterest(point) } }) { Text("Demonstrar interesse local") }
            protocol?.let { Text("Protocolo local: $it", style = MaterialTheme.typography.labelMedium) }
            Text(if (cancelPending && state.phase != JourneyPhase.Ended) "Cancelamento aguardando reconexão simulada" else entry?.status?.label ?: if (protocol != null) "Interesse enviado; aguardando sincronização simulada" else "Confirme o ponto antes de enviar.", style = MaterialTheme.typography.titleMedium)
            if (protocol != null && entry?.status != AttendanceStatus.Cancelled && !cancelPending) TextButton(onClick = { act { demo.cancelConsumerInterest() } }) { Text("Cancelar interesse local") }
            Text("Sem pedido, pagamento, reserva ou confirmação de atendimento.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Voltar à jornada") } })
}
