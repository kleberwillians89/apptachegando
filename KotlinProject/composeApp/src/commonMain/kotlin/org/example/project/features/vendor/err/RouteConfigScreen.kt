package org.example.project.features.vendor.err

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.example.project.core.data.RouteRepository
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.supabase
import org.example.project.core.domain.models.RouteWeekday
import org.example.project.core.ui.maps.NativeMap
import org.example.project.core.utils.Location
import org.example.project.core.utils.rememberLocationTracker
import kotlin.math.roundToInt

@Composable
fun RouteConfigScreen(
    onBack: () -> Unit,
    onRouteStarted: (String) -> Unit,
    templateRouteId: String? = null
) {
    val scope = rememberCoroutineScope()
    val locationTracker = rememberLocationTracker()
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(templateRouteId != null) }
    var isStartingGps by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var routeConfig by remember { mutableStateOf(RouteConfigState()) }
    var routeDraft by remember { mutableStateOf(RouteDraftState()) }
    var routeSchedule by remember { mutableStateOf(RouteScheduleState()) }
    var mapSheetState by remember { mutableStateOf(RouteMapSheetState()) }
    var expandedSheetDragOffset by remember { mutableStateOf(0f) }
    var collapsedSheetDragOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(templateRouteId) {
        if (templateRouteId == null) return@LaunchedEffect
        try {
            val route = RouteRepository().getRoute(templateRouteId)
                ?: error("Rota não encontrada.")
            routeDraft = RouteDraftState(route.waypoints, RouteCreationMode.Manual)
            routeSchedule = RouteScheduleState(route.name, false, route.activeDays.toSet())
            routeConfig = RouteConfigState(route.deviationToleranceMeters)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorMessage = "Não foi possível recuperar o percurso. Volte e tente novamente."
        } finally {
            isLoading = false
        }
    }

    fun stopRecording() {
        locationTracker.stopTracking()
        routeDraft = routeDraft.finishRecording()
    }

    fun startGpsRecording() {
        if (isStartingGps || isLoading || routeDraft.isRecording) return
        isStartingGps = true
        scope.launch {
            errorMessage = null
            try {
                if (!locationTracker.requestLocationPermission()) {
                    errorMessage = "Permita a localização para gravar sua rota."
                    return@launch
                }
                // Start from timestamped, accuracy-bearing fixes, never from a cached coordinate seed.
                routeDraft = routeDraft.startRecording(null)
                currentLocation = null
                errorMessage = "Aguardando GPS recente e preciso para gravar."
                locationTracker.startTrackingFix { fix ->
                    routeDraft = routeDraft.recordFix(fix, Clock.System.now().toEpochMilliseconds())
                    errorMessage = routeDraft.gpsWarning
                    if (routeDraft.gpsWarning == null) currentLocation = fix.location
                    if (!routeDraft.isRecording) locationTracker.stopTracking()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorMessage = "Não foi possível iniciar o GPS. Verifique a localização e tente novamente."
            } finally {
                isStartingGps = false
            }
        }
    }

    fun saveRoute() {
        if (isLoading || isStartingGps) return
        if (!routeDraft.canStartRoute) {
            errorMessage = if (routeDraft.creationMode == RouteCreationMode.Recording) {
                "Grave pelo menos dois pontos antes de salvar."
            } else {
                "Marque pelo menos dois pontos antes de salvar."
            }
            return
        }
        if (!routeSchedule.canStartRoute) {
            errorMessage = "Escolha pelo menos um dia para esta rota."
            return
        }
        stopRecording()
        val pointsToSave = routeDraft.waypoints
        val scheduleToSave = routeSchedule
        val configToSave = routeConfig
        isLoading = true
        scope.launch {
            errorMessage = null
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Usuario nao autenticado.")
                val vendorId = VendorRepository.resolveRouteVendorId(userId)
                val engine = ERREngine(RouteRepository(), configToSave.toERRConfig())
                val route = engine.initRoute(
                    vendorId = vendorId,
                    waypoints = pointsToSave,
                    routeName = scheduleToSave.routeNameForSave,
                    activeDays = scheduleToSave.activeDays
                )
                onRouteStarted(route.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorMessage = "Não foi possível salvar. Confira a conexão e se já existe uma rota ativa antes de tentar novamente."
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { locationTracker.stopTracking() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NativeMap(
            modifier = Modifier.fillMaxSize(),
            userLocation = currentLocation,
            routePoints = routeDraft.routePoints,
            onMapClick = { location ->
                if (!isLoading && !isStartingGps && routeDraft.creationMode == RouteCreationMode.Manual) {
                    routeDraft = routeDraft.addPoint(location)
                    errorMessage = null
                }
            }
        )

        if (mapSheetState.isVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = onBack, enabled = !isLoading && !isStartingGps) { Text("Voltar") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Minha rota",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                if (routeDraft.creationMode == RouteCreationMode.Recording) {
                                    "Grave sua rota enquanto trabalha. O app guarda o caminho pelo GPS."
                                } else {
                                    "Modo avancado: toque no mapa para marcar inicio e caminho."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { mapSheetState = mapSheetState.hide() }) {
                            Text("Ocultar")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = routeDraft.creationMode == RouteCreationMode.Recording,
                            enabled = !isLoading && !isStartingGps,
                            onClick = {
                                locationTracker.stopTracking()
                                routeDraft = routeDraft.switchToRecording()
                                errorMessage = null
                            },
                            label = { Text("Gravar rota") }
                        )
                        FilterChip(
                            selected = routeDraft.creationMode == RouteCreationMode.Manual,
                            enabled = !isLoading && !isStartingGps,
                            onClick = {
                                locationTracker.stopTracking()
                                routeDraft = routeDraft.switchToManual()
                                errorMessage = null
                            },
                            label = { Text("Marcar no mapa") }
                        )
                    }

                    if (routeDraft.creationMode == RouteCreationMode.Manual || routeDraft.waypoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        RouteSaveDetails(
                            routeSchedule = routeSchedule,
                            onScheduleChange = {
                                routeSchedule = it
                                errorMessage = null
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .offset { IntOffset(0, expandedSheetDragOffset.roundToInt()) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragCancel = { expandedSheetDragOffset = 0f },
                                    onDragEnd = {
                                        mapSheetState = mapSheetState.settleExpandedDrag(expandedSheetDragOffset)
                                        expandedSheetDragOffset = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        expandedSheetDragOffset = (expandedSheetDragOffset + dragAmount)
                                            .coerceIn(0f, 260f)
                                    }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RouteSheetDragHandle()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Controles da rota",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { mapSheetState = mapSheetState.hide() }) {
                                Text("Ocultar")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Avisar clientes ate qual distancia da rota?",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RouteDistancePreset.entries.toList()) { preset ->
                            FilterChip(
                                selected = routeConfig.deviationToleranceMeters == preset.meters,
                                onClick = { routeConfig = routeConfig.selectDistancePreset(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Text(
                        "${routeConfig.distanceLabel}: ${routeConfig.toleranceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        routeDraft.guidanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (routeDraft.creationMode == RouteCreationMode.Recording) {
                        RecordingControls(
                            routeDraft = routeDraft,
                            isLoading = isLoading || isStartingGps,
                            onStartRecording = { startGpsRecording() },
                            onFinishRecording = { stopRecording() },
                            onRecordAgain = {
                                locationTracker.stopTracking()
                                routeDraft = routeDraft.clear()
                                errorMessage = null
                            },
                            onSaveRoute = { saveRoute() }
                        )
                    } else {
                        ManualControls(
                            routeDraft = routeDraft,
                            isLoading = isLoading || isStartingGps,
                            routeSchedule = routeSchedule,
                            onCloseLoop = { routeDraft = routeDraft.closeLoop() },
                            onUndo = { routeDraft = routeDraft.removeLastPoint() },
                            onClear = { routeDraft = routeDraft.clear() },
                            onSaveRoute = { saveRoute() }
                        )
                    }

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            Card(
                onClick = { mapSheetState = mapSheetState.show() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
                    .offset { IntOffset(0, collapsedSheetDragOffset.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragCancel = { collapsedSheetDragOffset = 0f },
                            onDragEnd = {
                                mapSheetState = mapSheetState.settleCollapsedDrag(collapsedSheetDragOffset)
                                collapsedSheetDragOffset = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                collapsedSheetDragOffset = (collapsedSheetDragOffset + dragAmount)
                                    .coerceIn(-180f, 0f)
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RouteSheetDragHandle()
                    Text(
                        "Mostrar menus da rota",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        routeDraft.guidanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSheetDragHandle() {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun RouteSaveDetails(
    routeSchedule: RouteScheduleState,
    onScheduleChange: (RouteScheduleState) -> Unit
) {
    OutlinedTextField(
        value = routeSchedule.routeName,
        onValueChange = { onScheduleChange(routeSchedule.renameRoute(it)) },
        label = { Text("Nome da rota") },
        placeholder = { Text("Centro") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "Dias desta rota",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        routeSchedule.scheduleSummary,
        style = MaterialTheme.typography.bodySmall,
        color = if (routeSchedule.canStartRoute) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        }
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = routeSchedule.usesEveryDay,
            onClick = { onScheduleChange(routeSchedule.selectEveryDay()) },
            label = { Text("Todos os dias") }
        )
        FilterChip(
            selected = !routeSchedule.usesEveryDay,
            onClick = { onScheduleChange(routeSchedule.selectSpecificDays()) },
            label = { Text("Escolher dias") }
        )
    }
    if (!routeSchedule.usesEveryDay) {
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RouteWeekday.everyDay()) { day ->
                FilterChip(
                    selected = routeSchedule.isSelected(day),
                    onClick = { onScheduleChange(routeSchedule.toggleDay(day)) },
                    label = { Text(day.shortLabel) }
                )
            }
        }
    }
}

@Composable
private fun RecordingControls(
    routeDraft: RouteDraftState,
    isLoading: Boolean,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onRecordAgain: () -> Unit,
    onSaveRoute: () -> Unit
) {
    when {
        routeDraft.isRecording -> {
            Button(
                onClick = onFinishRecording,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Finalizar e revisar", style = MaterialTheme.typography.titleMedium)
            }
        }
        routeDraft.waypoints.isEmpty() -> {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Gravar minha rota", style = MaterialTheme.typography.titleMedium)
            }
        }
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRecordAgain,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Gravar de novo")
                }
                Button(
                    onClick = onSaveRoute,
                    enabled = !isLoading && routeDraft.canStartRoute,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Salvar rota")
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualControls(
    routeDraft: RouteDraftState,
    isLoading: Boolean,
    routeSchedule: RouteScheduleState,
    onCloseLoop: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSaveRoute: () -> Unit
) {
    OutlinedButton(
        onClick = onCloseLoop,
        enabled = routeDraft.canCloseRoute,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Fechar volta")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onUndo,
            enabled = routeDraft.waypoints.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Text("Apagar ultimo")
        }
        OutlinedButton(
            onClick = onClear,
            enabled = routeDraft.waypoints.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Text("Limpar rota")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSaveRoute,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = !isLoading && routeDraft.canStartRoute && routeSchedule.canStartRoute
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("Salvar rota", style = MaterialTheme.typography.titleMedium)
        }
    }
}
