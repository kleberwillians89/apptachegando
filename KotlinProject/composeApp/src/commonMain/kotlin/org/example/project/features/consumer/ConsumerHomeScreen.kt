package org.example.project.features.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.example.project.core.utils.rememberLocationTracker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import org.example.project.features.demo.DemoPhoto
import org.example.project.features.demo.demoSearch
import org.example.project.features.demo.demoNearby
import org.example.project.features.demo.LocalConsumerExperience
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.Vendor
import org.example.project.core.utils.UiState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import org.example.project.core.navigation.PersonIcon
import org.example.project.core.navigation.StarIcon
import org.example.project.core.navigation.LocationOnIcon
import androidx.compose.ui.text.font.FontWeight

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.project.core.utils.Location
import org.example.project.core.ui.maps.VendorMapMarker
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumerHomeScreen(
    onVendorClick: (String) -> Unit,
    onDemoProtocols: (() -> Unit)? = null
) {
    val experience = LocalConsumerExperience.current
    var searchQuery by remember { mutableStateOf("") }
    var sheetState by remember { mutableStateOf(ConsumerMapSheetState()) }
    var expandedSheetDragOffset by remember { mutableStateOf(0f) }
    var collapsedSheetDragOffset by remember { mutableStateOf(0f) }

    val locationTracker = if (experience.isDemonstration) null else rememberLocationTracker()
    val scope = rememberCoroutineScope()
    var userLocation by remember(experience) { mutableStateOf(experience.referenceLocation) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var nearbyOnly by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var serverQuery by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(searchQuery) {
        page = 0
        kotlinx.coroutines.delay(300)
        serverQuery = searchQuery.take(120)
    }
    val vendorsFlow = remember(page, experience, serverQuery, userLocation) {
        if (experience.isDemonstration) experience.vendors(page)
        else userLocation?.let { experience.discover(it, page, serverQuery) }
            ?: kotlinx.coroutines.flow.flowOf(UiState.Error("Use Minha localização para encontrar profissionais elegíveis próximos e no seu percurso."))
    }
    val uiState by androidx.compose.runtime.key(page, serverQuery, userLocation) { vendorsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading) }
    val vendors = (uiState as? UiState.Success)?.data.orEmpty()

    val demoVisible = remember(vendors, searchQuery, nearbyOnly) {
        if (!experience.isDemonstration) vendors else {
            val nearby = if (nearbyOnly && experience.referenceLocation != null) demoNearby(vendors, experience.referenceLocation!!) else vendors
            demoSearch(nearby, searchQuery)
        }
    }
    val vendorMarkers = remember(demoVisible) { demoVisible.mapNotNull {
        if (it.isOnline && it.latitude != null && it.longitude != null)
            VendorMapMarker(it.id, it.name, Location(it.latitude, it.longitude)) else null
    } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tá Chegando") },
                actions = {
                    if (experience.isDemonstration) {
                        TextButton(onClick = {
                            nearbyOnly = true
                            searchQuery = ""
                            locationMessage = "São Paulo · referência simulada. Vendedores em até 1 km."
                        }) { Text("Perto de mim") }
                        onDemoProtocols?.let { open -> TextButton(onClick = open) { Text("Protocolos") } }
                    }
                    else TextButton(enabled = !locating, onClick = {
                        scope.launch {
                            locating = true
                            locationMessage = null
                            try {
                                if (locationTracker == null || !locationTracker.requestLocationPermission()) {
                                    userLocation = null
                                    locationMessage = "Permissão de localização não concedida. O catálogo continua disponível."
                                } else {
                                    userLocation = locationTracker.getCurrentLocation()
                                    page = 0
                                    locationMessage = if (userLocation == null) "Não foi possível obter uma posição recente. Verifique o GPS e tente novamente."
                                        else "Busca por localização atualizada; sua posição não foi publicada."
                                }
                            } finally { locating = false }
                        }
                    }) { Text(if (locating) "Localizando..." else "Minha localização") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Native Map KMP Component
            org.example.project.core.ui.maps.NativeMap(
                modifier = Modifier.fillMaxSize(),
                userLocation = userLocation,
                vendorMarkers = vendorMarkers,
                onVendorClick = onVendorClick,
                onMapClick = { }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                if (sheetState.isVisible) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, expandedSheetDragOffset.roundToInt()) },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                                .padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragCancel = { expandedSheetDragOffset = 0f },
                                        onDragEnd = {
                                            sheetState = sheetState.settleExpandedDrag(expandedSheetDragOffset)
                                            expandedSheetDragOffset = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            expandedSheetDragOffset = (expandedSheetDragOffset + dragAmount)
                                                .coerceIn(0f, 220f)
                                        }
                                    )
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(44.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Profissionais perto",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = { sheetState = sheetState.hide() }) {
                                        Text("Esconder lista")
                                    }
                                }
                            }

                            when (val state = uiState) {
                                is UiState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(180.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is UiState.Error -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Não foi possível atualizar o catálogo. Verifique a conexão e se o contrato de busca está liberado no servidor.")
                                    }
                                }
                                is UiState.Success -> {
                                    val vendors = state.data
                                    val filtered = demoVisible
                                    Row {
                                        TextButton(enabled = page > 0, onClick = { page-- }) { Text("Anterior") }
                                        Text("Página ${page + 1} · ${if (experience.isDemonstration) "busca local da demo" else "catálogo pesquisado no servidor"}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                        TextButton(enabled = vendors.size == VendorRepository.PAGE_SIZE, onClick = { page++ }) { Text("Próxima") }
                                    }
                                    if (filtered.isEmpty()) Text(
                                        if (searchQuery.isBlank()) "Nenhum profissional online disponível nesta página. Tente novamente em instantes."
                                        else "Nenhum profissional encontrado para essa busca.",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().height(250.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 4.dp,
                                            bottom = 12.dp
                                        ),
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(filtered, key = { it.id }) { vendor ->
                                            Card(
                                                onClick = { onVendorClick(vendor.id) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (experience.isDemonstration) DemoPhoto(vendor.id, vendor.name, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                                    else Box(
                                                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(imageVector = PersonIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(vendor.name, fontWeight = FontWeight.Bold)
                                                        if (experience.isDemonstration) Text(vendor.category, style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        else Text(vendor.category, style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        onClick = { sheetState = sheetState.show() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .offset { IntOffset(0, collapsedSheetDragOffset.roundToInt()) }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragCancel = { collapsedSheetDragOffset = 0f },
                                    onDragEnd = {
                                        sheetState = sheetState.settleCollapsedDrag(collapsedSheetDragOffset)
                                        collapsedSheetDragOffset = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        collapsedSheetDragOffset = (collapsedSheetDragOffset + dragAmount)
                                            .coerceIn(-160f, 0f)
                                    }
                                )
                            },
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                            Text(
                                "Ver profissionais perto",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Anuncie aqui", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Search/location controls remain local until the user explicitly registers an intention.
            Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(120) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface),
                placeholder = { Text("Buscar profissional ou produto...") }
            )
            if (experience.isDemonstration) androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.surface),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("Pães", "Frutas", "Salgados fritos", "Lanches naturais", "Sorvetes")) { category ->
                    androidx.compose.material3.FilterChip(selected = searchQuery == category,
                        onClick = { searchQuery = category }, label = { Text(category) })
                }
            }
            locationMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surface), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
