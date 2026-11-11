package org.example.project.features.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.project.core.data.conditionMessage
import org.example.project.core.data.PurchaseIntentRepository
import org.example.project.core.data.productDisplayPrice
import org.example.project.core.data.backendResult
import androidx.compose.foundation.clickable
import org.example.project.features.demo.DemoPhoto
import org.example.project.features.demo.LocalConsumerExperience
import org.example.project.core.data.VendorRepository
import org.example.project.core.data.Vendor
import org.example.project.core.utils.UiState
import org.example.project.core.utils.rememberLocationTracker
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.ArrowForwardIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.PersonIcon
import org.example.project.core.navigation.StarIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProfileScreen(
    vendorId: String,
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    onProductClick: ((String) -> Unit)? = null
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val experience = LocalConsumerExperience.current
    val vendorFlow = remember(vendorId, experience) { experience.vendors(vendorId = vendorId) }
    val vendorState by vendorFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val uiState: UiState<Vendor?> = when (val state = vendorState) {
        is UiState.Success -> UiState.Success(state.data.singleOrNull())
        is UiState.Error -> state
        UiState.Loading -> UiState.Loading
    }
    var receiptId by remember(vendorId, experience) { mutableStateOf<String?>(null) }
    var isAlertEnabled by remember(vendorId) { mutableStateOf(false) }
    var isAlertLoading by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val locationTracker = if (experience.isDemonstration) null else rememberLocationTracker()
    val scope = rememberCoroutineScope()
    var intentRefresh by remember(vendorId) { mutableStateOf(0) }
    LaunchedEffect(vendorId, intentRefresh) {
        isAlertLoading = true
        when (val result = experience.pendingIntent(vendorId)) {
            is UiState.Success -> {
                receiptId = result.data?.id
                isAlertEnabled = result.data != null
                alertMessage = result.data?.let { if (experience.isDemonstration) "Simulação local encontrada. Protocolo: ${it.id}. Nenhuma compra foi enviada." else "${it.conditionMessage()} Protocolo: ${it.id}" }
            }
            is UiState.Error -> alertMessage = result.message
            UiState.Loading -> Unit
        }
        isAlertLoading = false
    }

    LaunchedEffect(receiptId, experience, lifecycle) {
        val id = receiptId ?: return@LaunchedEffect
        if (experience.isDemonstration || !lifecycle.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000)
            when (val result = PurchaseIntentRepository.refresh(id)) {
                is UiState.Success -> {
                    alertMessage = "${result.data.conditionMessage()} Protocolo: ${result.data.id}"
                    isAlertEnabled = result.data.status == "pending"
                    if (!isAlertEnabled) receiptId = null
                }
                is UiState.Error -> alertMessage = result.message
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (uiState is UiState.Success) {
                        (uiState as UiState.Success<Vendor?>).data?.name ?: "Profissional"
                    } else "Carregando..."
                    Text(titleText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = ArrowBackIcon, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Não deu para carregar os dados.")
                }
            }
            is UiState.Success -> {
                val vendor = (uiState as UiState.Success<Vendor?>).data
                if (vendor == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Profissional não encontrado", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onBack) { Text("Voltar") }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (experience.isDemonstration) DemoPhoto(vendor.id, vendor.name, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)))
                        else Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = PersonIcon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(vendor.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(vendor.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (vendor.reviewCount > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = StarIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(vendor.rating, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("(${vendor.reviewCount} avalia\u00e7\u00f5es)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (vendor.isOnline) "Online" else "Offline", color = MaterialTheme.colorScheme.primary)
                        if (vendor.distance.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = LocationOnIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${vendor.distance} de dist\u00e2ncia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        if (!experience.isDemonstration) Button(
                            onClick = { onTrackClick(vendorId) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Acompanhar", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = ArrowForwardIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!experience.isDemonstration) OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isAlertLoading = true
                                    alertMessage = null
                                    val currentLocation = if (experience.isDemonstration) experience.referenceLocation
                                        else if (locationTracker != null && locationTracker.requestLocationPermission()) locationTracker.getCurrentLocation() else null
                                    if (currentLocation == null) {
                                        alertMessage = "Ative sua localização para registrar a intenção."
                                        isAlertLoading = false
                                        return@launch
                                    }

                                    when (val result = experience.createIntent(vendor.id, currentLocation)) {
                                        is UiState.Success -> {
                                            receiptId = result.data.id
                                            isAlertEnabled = true
                                            alertMessage = if (experience.isDemonstration) "Simulação local registrada. Protocolo: ${result.data.id}. Nenhuma compra ou notificação foi enviada." else "${result.data.conditionMessage()} Protocolo: ${result.data.id}"
                                        }
                                        is UiState.Error -> alertMessage = result.message
                                        UiState.Loading -> {}
                                    }
                                    isAlertLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = trackingLocation(UiState.Success(listOf(vendor))) != null && !isAlertLoading && !isAlertEnabled
                        ) {
                            if (isAlertLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(if (experience.isDemonstration) { if (isAlertEnabled) "Simulação registrada" else "Simular intenção" } else { if (isAlertEnabled) "Intenção registrada" else "Registrar intenção" })
                            }
                        }
                        if (!experience.isDemonstration && vendor.isOnline && trackingLocation(UiState.Success(listOf(vendor))) == null) {
                            Text("Localização do profissional indisponível ou desatualizada. Aguarde uma nova posição para demonstrar interesse.", Modifier.padding(vertical = 8.dp))
                        }
                        if (!experience.isDemonstration) alertMessage?.let { message ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAlertEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!experience.isDemonstration && receiptId != null) OutlinedButton(
                            enabled = !isAlertLoading,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val id = receiptId ?: return@OutlinedButton
                                isAlertLoading = true
                                scope.launch {
                                    try {
                                        when (val result = experience.cancelIntent(id)) {
                                            is UiState.Success -> { receiptId = null; isAlertEnabled = false; alertMessage = "Interesse cancelado no servidor." }
                                            is UiState.Error -> alertMessage = result.message
                                            else -> Unit
                                        }
                                    } finally { isAlertLoading = false }
                                }
                            }
                        ) { Text("Cancelar interesse") }
                        if (!experience.isDemonstration) OutlinedButton(
                            onClick = { intentRefresh++ },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAlertLoading
                        ) {
                            Text("Consultar intenção")
                        }
                    }
                }
                item {
                    Text("Conheça as categorias habituais. Imagens ilustrativas; disponibilidade a confirmar com o vendedor. Sem reserva ou compra por esta tela.", modifier = Modifier.padding(horizontal = 16.dp))
                    Text(
                        if (experience.isDemonstration) "Categorias que costuma oferecer" else "Categorias e produtos habituais",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (vendor.products.isEmpty()) item {
                    Text("Ainda não há categorias ou produtos cadastrados.", modifier = Modifier.padding(16.dp))
                }
                items(vendor.products.size) { index ->
                    val product = vendor.products[index]
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = experience.isDemonstration && onProductClick != null) { onProductClick?.invoke(product.id) }.padding(horizontal = 16.dp, vertical = if (experience.isDemonstration) 10.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (experience.isDemonstration) DemoPhoto(product.id, product.name, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                        else org.example.project.core.ui.CatalogIllustration(product.name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
        }
        }
    }
}
