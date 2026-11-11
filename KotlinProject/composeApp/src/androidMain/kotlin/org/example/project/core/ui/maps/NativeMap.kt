package org.example.project.core.ui.maps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import org.example.project.core.data.GeneratedAppConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.example.project.core.utils.Location

import org.example.project.core.ui.maps.HeatZoneLocation

@Composable
actual fun NativeMap(
    modifier: Modifier,
    userLocation: Location?,
    vendorLocations: List<Location>,
    routePoints: List<Location>,
    heatZones: List<HeatZoneLocation>,
    onMapClick: (Location) -> Unit,
    vendorMarkers: List<VendorMapMarker>,
    onVendorClick: (String) -> Unit
) {
    if (!GeneratedAppConfiguration.mapsConfigured) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Mapa indisponível neste ambiente.")
        }
        return
    }
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun hasInternet(): Boolean = runCatching {
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)
    var online by remember { mutableStateOf(hasInternet()) }
    var attempt by remember { mutableStateOf(0) }
    var timedOut by remember(attempt) { mutableStateOf(false) }
    LaunchedEffect(connectivity) {
        while (true) { online = hasInternet(); delay(2_000) }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation?.takeIf { it.isValidMapLocation() }?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(-23.550520, -46.633308), // SP default
            if (userLocation != null) 15f else 10f
        )
    }

    var mapLoaded by remember(attempt) { mutableStateOf(false) }
    var routeFramed by remember(attempt) { mutableStateOf(false) }
    var locationFramed by remember(attempt) { mutableStateOf(false) }
    val safeRoutePoints = remember(routePoints) { routePoints.filter { it.isValidMapLocation() } }
    androidx.compose.runtime.LaunchedEffect(mapLoaded, safeRoutePoints, userLocation) {
        if (!mapLoaded) return@LaunchedEffect
        if (safeRoutePoints.isEmpty()) routeFramed = false
        if (!routeFramed && safeRoutePoints.isNotEmpty()) {
            val points = safeRoutePoints.map { LatLng(it.latitude, it.longitude) }
            val update = if (points.distinct().size == 1) CameraUpdateFactory.newLatLngZoom(points.first(), 15f)
            else CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { points.forEach { include(it) } }.build(), 80)
            cameraPositionState.animate(update)
            routeFramed = true
        } else if (safeRoutePoints.isEmpty() && !locationFramed && userLocation?.isValidMapLocation() == true) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(userLocation.latitude, userLocation.longitude), 15f))
            locationFramed = true
        }
    }

    LaunchedEffect(attempt, mapLoaded, online) {
        timedOut = false
        if (!mapLoaded && online) { delay(20_000); timedOut = true }
    }
    val markerLocations = remember<List<Location>>(vendorMarkers, vendorLocations) {
        if (vendorMarkers.isEmpty()) vendorLocations else vendorMarkers.map { it.location }
    }
    val clusters = remember<List<VendorMapCluster>>(markerLocations, cameraPositionState.position.zoom.toInt()) {
        clusterVendorLocations(markerLocations, cameraPositionState.position.zoom)
    }
    val scope = rememberCoroutineScope()
    var selectedClusterIds by remember { mutableStateOf(emptyList<String>()) }
    val polyline = remember(safeRoutePoints) { safeRoutePoints.map { LatLng(it.latitude, it.longitude) } }
    val routeMarkers = remember<List<Int>>(safeRoutePoints.size) { routeMarkerIndices(safeRoutePoints.size) }
    val properties = remember { MapProperties(isMyLocationEnabled = false) }
    val settings = remember { MapUiSettings(zoomControlsEnabled = false) }
    Box(modifier = modifier) {
    key(attempt) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = settings,
        onMapLoaded = { mapLoaded = true },
        onMapClick = { latLng -> onMapClick(Location(latLng.latitude, latLng.longitude)) }
    ) {
        userLocation?.takeIf { it.isValidMapLocation() }?.let { location ->
            val state = remember { MarkerState() }
            SideEffect { state.position = LatLng(location.latitude, location.longitude) }
            Marker(state = state, title = "Você")
        }
        clusters.forEach { cluster ->
            key(cluster.key) {
                val state = remember { MarkerState() }
                SideEffect { state.position = LatLng(cluster.location.latitude, cluster.location.longitude) }
                val marker = cluster.sourceIndices.singleOrNull()?.let { vendorMarkers.getOrNull(it) }
                Marker(
                    state = state,
                    title = marker?.name ?: if (cluster.count == 1) "Profissional" else "${cluster.count} profissionais · amplie o mapa",
                    onClick = {
                        if (marker != null) onVendorClick(marker.vendorId)
                        else if (cluster.sourceIndices.mapNotNull { vendorMarkers.getOrNull(it) }.isNotEmpty()) {
                            selectedClusterIds = cluster.sourceIndices.mapNotNull { vendorMarkers.getOrNull(it)?.vendorId }
                        } else if (cluster.count > 1) scope.launch {
                            cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                LatLng(cluster.location.latitude, cluster.location.longitude),
                                (cameraPositionState.position.zoom + 2f).coerceAtMost(21f)
                            ))
                        }
                        marker != null || cluster.count > 1
                    }
                )
            }
        }
        if (polyline.size >= 2) {
            Polyline(points = polyline, color = androidx.compose.ui.graphics.Color(0xFFFF6B00), width = 8f)
        }
        routeMarkers.forEach { index ->
            key(index) {
                val state = remember { MarkerState() }
                val point = polyline[index]
                SideEffect { state.position = point }
                Marker(state = state, title = when (index) {
                    0 -> "Início (0)"
                    safeRoutePoints.lastIndex -> if (safeRoutePoints.first() == safeRoutePoints.last()) "Volta ao início" else "Ponto $index"
                    else -> "Ponto $index"
                })
            }
        }
        heatZones.forEach { zone ->
            com.google.maps.android.compose.Circle(
                center = LatLng(zone.location.latitude, zone.location.longitude),
                radius = zone.radius,
                fillColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = zone.intensity * 0.5f),
                strokeColor = androidx.compose.ui.graphics.Color.Transparent,
                strokeWidth = 0f
            )
        }
    }
    } // Map instance: retry recreates only this map, never the journey/session.
    val status = mapLoadStatus(true, online, mapLoaded, timedOut)
    if (status != MapLoadStatus.Ready) {
        Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), tonalElevation = 4.dp) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (status) {
                    MapLoadStatus.Loading -> "Carregando mapa..."
                    MapLoadStatus.Offline -> "Sem conexão para carregar o mapa."
                    else -> "Mapa indisponível. Não foi possível carregar os tiles."
                })
                if (status == MapLoadStatus.Loading) CircularProgressIndicator()
                else TextButton(onClick = { attempt++ }) { Text("Tentar carregar mapa novamente") }
            }
        }
    }
    } // Keep loading/error status inside the existing map area.
    val currentChoices = selectedClusterIds.mapNotNull { id -> vendorMarkers.find { it.vendorId == id } }
    if (currentChoices.isNotEmpty()) AlertDialog(
        onDismissRequest = { selectedClusterIds = emptyList() },
        title = { Text("Profissionais nesta área") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                items(currentChoices, key = { it.vendorId }) { marker ->
                    TextButton(onClick = { selectedClusterIds = emptyList(); onVendorClick(marker.vendorId) }) { Text(marker.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { selectedClusterIds = emptyList() }) { Text("Fechar") } }
    )

}

private fun Location.isValidMapLocation(): Boolean = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0
