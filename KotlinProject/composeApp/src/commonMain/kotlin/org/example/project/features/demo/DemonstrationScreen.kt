package org.example.project.features.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.features.consumer.ConsumerHomeScreen
import org.example.project.features.consumer.VendorProfileScreen

@Composable
fun DemonstrationScreen(onExit: () -> Unit) {
    val experience = remember { DemoConsumerExperience() }
    val storage = rememberDemoRouteStorage()
    val automaticRoutes = remember(storage) { DemoRouteSession(storage) }
    val automatic = remember(automaticRoutes) { DemoAutomaticAttendance(automaticRoutes) }
    var page by remember { mutableStateOf("welcome") }
    var vendorId by remember { mutableStateOf("") }
    var productId by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("") }
    var buyerPreview by remember { mutableStateOf(false) }
    var openRouteConsumer by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(automatic, experience) {
        automatic.attendance.collectLatest { journey ->
            if (journey != null) {
                if (experience.registration?.role == DemoRole.Vendor) {
                    val owner = experience.ownVendor()
                    owner.products.firstOrNull()?.let { automatic.describeConsumerContext(owner, it) }
                }
                journey.state.collect { snapshot ->
                    if (experience.registration?.role == DemoRole.Vendor) experience.applyJourneySnapshot(snapshot)
                }
            }
        }
    }
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        if (!lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) automatic.attendance.value?.pause()
    }
    androidx.compose.runtime.LaunchedEffect(page, buyerPreview) {
        if (page != "automatic" && !buyerPreview) automatic.attendance.value?.pause()
    }
    CompositionLocalProvider(LocalConsumerExperience provides experience) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("MODO DEMONSTRAÇÃO — dados fictícios", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    if (experience.registration?.role == DemoRole.Vendor && page != "seller") {
                        TextButton(onClick = { if (buyerPreview) { buyerPreview = false; page = "automatic" } else page = "seller" }) {
                            Text(if (buyerPreview) "Jornada do vendedor" else if (page == "automatic") "Administração" else "Meu painel")
                        }
                    }
                    if (!buyerPreview && (page == "home" || page == "seller")) TextButton(onClick = { page = if (page == "seller") "automatic" else "routes" }) { Text(if (page == "seller") "Jornada" else "Rotas demo") }
                    TextButton(onClick = onExit) { Text("Sair da demo") }
                }
            }
            Column(Modifier.weight(1f)) {
                when (page) {
                    "welcome" -> DemoWelcome(onRegister = { page = "register" }, onExplore = { page = "home" }, onAutomaticRoute = { page = "automatic" })
                    "register" -> DemoRegistrationScreen(onBack = { page = "welcome" }, onComplete = { name, role, category ->
                        experience.register(name, role, category); page = "registered"
                    })
                    "registered" -> experience.registration?.let { DemoRegistrationComplete(it, onExplore = { page = if (it.role == DemoRole.Vendor) "automatic" else "home" }) }
                    "seller" -> DemoVendorScreen(experience, onPreview = { page = "home" })
                    "automatic" -> DemoAutomaticRouteScreen(automatic, persistent = storage != null,
                        openConsumerOnEnter = openRouteConsumer, onConsumerOpened = { openRouteConsumer = false },
                        onExploreCatalog = { buyerPreview = true; page = "home" }, onBack = { page = if (experience.registration?.role == DemoRole.Vendor) "seller" else "welcome" })
                    "routes" -> DemoRouteScreen(experience.routes, onBack = { page = "home" })
                    "home" -> ConsumerHomeScreen(onVendorClick = { vendorId = it; page = "vendor" }, onDemoProtocols = { page = "history" })
                    "vendor" -> VendorProfileScreen(vendorId = vendorId, onBack = { page = "home" }, onTrackClick = { page = "home" },
                        onProductClick = { productId = it; page = "product" })
                    "product" -> {
                        val vendor = experience.catalogue.single { it.id == vendorId }
                        val product = vendor.products.single { it.id == productId }
                        DemoProductScreen(vendor, product, onBack = { page = "vendor" }, onConfirm = {
                            if (experience.canUseAutomaticJourney(vendorId, automatic.attendance.value?.state?.value)) {
                                automatic.describeConsumerContext(vendor, product)
                                openRouteConsumer = true; buyerPreview = false; page = "automatic"
                            } else {
                                protocol = experience.consultAvailability(vendorId, productId).protocol; page = "receipt"
                            }
                        })
                    }
                    "receipt" -> experience.interest(protocol)?.let { purchase ->
                        DemoInterestReceipt(purchase, onHistory = { page = "history" }, onHome = { page = "home" })
                    }
                    "history" -> DemoInterestHistory(experience.interestHistory(), onOpen = { protocol = it; page = "receipt" }, onHome = { page = "home" })
                }
            }
        }
    }
}
