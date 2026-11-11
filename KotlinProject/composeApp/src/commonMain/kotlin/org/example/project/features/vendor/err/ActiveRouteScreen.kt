package org.example.project.features.vendor.err

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.example.project.core.data.*
import org.example.project.core.domain.route.*
import org.example.project.core.utils.*

@Composable
fun ActiveRouteScreen(routeId: String, onStopRoute: () -> Unit) {
    val owner = AuthRepository.currentUser()?.id
    val scope = rememberCoroutineScope()
    val detector = remember(routeId, owner) { org.example.project.core.domain.notifications.NewInterestDetector() }
    val notifier = rememberInterestNotifier()
    var pushStatus by remember(owner) { mutableStateOf("Push em segundo plano não configurado") }
    var highlighted by remember(routeId, owner) { mutableStateOf(emptySet<String>()) }
    val tracker = rememberLocationTracker()
    val repository = remember { RouteAttendanceRepository() }
    var session by remember(routeId, owner) { mutableStateOf<RemoteAttendanceSession?>(null) }
    var error by remember(routeId, owner) { mutableStateOf<String?>(null) }
    var publishError by remember(routeId, owner) { mutableStateOf<String?>(null) }
    var loading by remember(routeId, owner) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var showSituations by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val state = session?.attendance?.state?.collectAsStateWithLifecycle()?.value ?: AttendanceSnapshot()

    LaunchedEffect(routeId, owner, retry) {
        loading = true
        when (val result = backendResult("Não foi possível recuperar a rota. Confira sua conta e conexão.") {
            check(owner != null)
            val route = RouteRepository().getRoute(routeId) ?: error("Rota não encontrada.")
            check(route.vendorId == VendorRepository.resolveRouteVendorId(owner))
            RemoteAttendanceSession(RouteAttendance(route))
        }) {
            is UiState.Success -> { session = result.data; error = null }
            is UiState.Error -> error = result.message
            else -> Unit
        }
        loading = false
    }
    LaunchedEffect(session, lifecycle) {
        detector.suspendAlerts()
        val current = session ?: return@LaunchedEffect
        if (!lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
            detector.suspendAlerts()
            highlighted = emptySet()
            current.attendance.gpsUnavailable()
            current.failed()
            return@LaunchedEffect
        }
        repository.observe(routeId, current.attendance.route.vendorId).collect { result ->
            when (result) {
                is UiState.Success -> try {
                    current.apply(result.data)
                    val confirmed = current.attendance.state.value
                    val fresh = detector.observe(confirmed, lifecycle.isAtLeast(Lifecycle.State.RESUMED))
                    if (fresh.isNotEmpty() && AuthRepository.currentUser()?.id == owner) {
                        highlighted = fresh
                        fresh.forEach { id -> notifier.notify(org.example.project.core.domain.notifications.InterestNotice(owner ?: "", routeId, id)) }
                    }
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { detector.suspendAlerts(); current.failed(); error = "A fila recebida não corresponde à jornada. Volte e tente novamente." }
                is UiState.Error -> { detector.suspendAlerts(); highlighted = emptySet(); current.failed(); error = result.message }
                else -> Unit
            }
        }
    }
    LaunchedEffect(session, lifecycle, state.phase) {
        val current = session ?: return@LaunchedEffect
        if (!lifecycle.isAtLeast(Lifecycle.State.STARTED) || state.phase != JourneyPhase.Working) return@LaunchedEffect
        val publisher = LocationPublisher(this) { sample ->
            when (val result = backendResult("Não foi possível compartilhar a posição. Verifique sua conexão.") {
                check(AuthRepository.currentUser()?.id == owner)
                VendorRepository.publishRouteLocation(owner ?: error("Entre na sua conta."), routeId, sample.location, sample.recordedAtMillis, sample.accuracyMeters)
            }) {
                is UiState.Error -> { publishError = result.message; current.failed() }
                else -> publishError = null
            }
        }
        try {
            if (!tracker.requestLocationPermission()) {
                publishError = "Permita a localização para trabalhar na rota."
                current.attendance.gpsUnavailable()
                return@LaunchedEffect
            }
            publishError = null
            tracker.startTrackingFix { fix ->
                if (AuthRepository.currentUser()?.id == owner) {
                    launch { current.attendance.gps(fix, Clock.System.now().toEpochMilliseconds()) }
                    val now = Clock.System.now().toEpochMilliseconds()
                    if (RouteProgress.valid(fix.location) && fix.accuracyMeters.isFinite() &&
                        fix.accuracyMeters in 0.0..50.0 && fix.recordedAtMillis in (now - 30_000)..now) {
                        publisher.offer(fix.location, fix.recordedAtMillis, fix.accuracyMeters)
                    }
                }
            }
            while (true) {
                current.attendance.tick(Clock.System.now().toEpochMilliseconds())
                delay(5_000)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishError = "Não foi possível iniciar o GPS. Confira as permissões e tente novamente."; current.attendance.gpsUnavailable() }
        finally { tracker.stopTracking(); publisher.close() }
    }

    LaunchedEffect(session, notifier.enabled, lifecycle) {
        if (session != null && owner != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            pushStatus = notifier.preparePush(owner)
        }
    }
    LaunchedEffect(state.connected, state.phase, state.gpsIssue, lifecycle) {
        if (!state.connected || state.phase != JourneyPhase.Working || state.gpsIssue != null || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            detector.suspendAlerts()
            highlighted = emptySet()
        }
    }
    LaunchedEffect(highlighted) { if (highlighted.isNotEmpty()) { delay(8_000); highlighted = emptySet() } }

    fun availability(online: Boolean, end: Boolean = false) {
        if (busy) return
        val current = session ?: return
        detector.suspendAlerts()
        highlighted = emptySet()
        busy = true
        scope.launch {
            var confirmed = false
            try {
                check(AuthRepository.currentUser()?.id == owner)
                val acknowledgement = repository.setAvailability(routeId, online, end)
                check(AuthRepository.currentUser()?.id == owner)
                confirmed = true
                current.confirmAvailability(acknowledgement, online, end)
                if (end) onStopRoute()
                else current.apply(repository.refresh(routeId))
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                current.failed()
                error = if (confirmed) "A alteração da jornada foi confirmada. A fila aguarda uma nova atualização do servidor."
                    else "Não foi possível confirmar a alteração da jornada. Confira a conexão e consulte o estado antes de tentar novamente."
            }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onStopRoute, enabled = !busy) { Text("Voltar") }
            TextButton(onClick = { retry++ }, enabled = !busy && !loading) { Text("Atualizar") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (notifier.enabled) "Avisos durante a jornada ativados" else "Som e vibração desativados", Modifier.weight(1f).padding(8.dp))
            TextButton(onClick = notifier::requestPermission) { Text(if (notifier.enabled) "Configurar avisos" else "Ativar avisos") }
        }
        Text(pushStatus, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        if (highlighted.isNotEmpty()) Text("Novo interesse na sua rota · confira a fila", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary)
        publishError?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        session?.let { current ->
            RouteAttendancePanel(current.attendance, state,
                onStart = { availability(true) },
                onPauseResume = { availability(state.phase == JourneyPhase.Paused) },
                onEnd = { confirmEnd = true }, onHistory = { showSituations = true },
                onConsumer = {}, onChooseRoute = onStopRoute,
                isDemonstration = false, controlsEnabled = !busy && state.connected, highlightedInterestIds = highlighted)
        }
    }
    if (showSituations) AlertDialog(onDismissRequest = { showSituations = false },
        title = { Text("Situações dos interesses") },
        text = { androidx.compose.foundation.lazy.LazyColumn {
            items(state.entries.size) { index ->
                val entry = state.entries[index]
                Text("Protocolo ${entry.intent.id.take(8)} · ${entry.status.label}", Modifier.padding(vertical = 8.dp))
            }
        } }, confirmButton = { TextButton(onClick = { showSituations = false }) { Text("Fechar") } })
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text("Encerrar jornada?") }, text = { Text("O percurso será preservado e sua disponibilidade será desligada. Nenhuma venda será confirmada.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirmEnd = false; availability(false, true) }) { Text("Encerrar") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Continuar") } })
}
