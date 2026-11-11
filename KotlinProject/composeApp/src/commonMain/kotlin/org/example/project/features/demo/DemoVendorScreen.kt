package org.example.project.features.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.core.ui.maps.NativeMap
import org.example.project.core.ui.maps.VendorMapMarker
import org.example.project.core.utils.Location

/** Seller presentation only. All actions target the current in-memory demo instance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoVendorScreen(experience: DemoConsumerExperience, onPreview: () -> Unit) {
    val revision by experience.revision.collectAsState()
    val owner = remember(revision) { experience.ownVendor() }
    val intents = remember(revision) { experience.incomingIntents() }
    var section by remember { mutableStateOf("panel") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var productName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    fun act(block: () -> Unit) {
        try { block() } catch (e: IllegalArgumentException) { message = e.message } catch (e: IllegalStateException) { message = e.message }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(when (section) { "products" -> "Minhas categorias"; "edit" -> "Categoria demo"; "location" -> "Localização simulada"; "intents" -> "Intenções recebidas"; else -> "Meu painel" }) },
            navigationIcon = { if (section != "panel") TextButton(onClick = { section = "panel"; message = null }) { Text("Painel") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            when (section) {
                "panel" -> {
                    DemoPhoto(owner.id, owner.name, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp)))
                    Text("Olá, ${experience.registration?.name}", style = MaterialTheme.typography.headlineSmall)
                    Text(owner.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (owner.isOnline) "Online no catálogo local" else "Offline no catálogo local", color = MaterialTheme.colorScheme.primary)
                    Text(if (owner.latitude != null) "Posição simulada disponível" else "Posição simulada não publicada", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { experience.setDemoOnline(!owner.isOnline); message = if (owner.isOnline) "Disponibilidade encerrada; posição retirada do cenário local." else "Você ficou online apenas na demonstração." }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (owner.isOnline) "Encerrar disponibilidade" else "Ficar online")
                    }
                    OutlinedButton(onClick = { section = "products"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Minhas categorias") }
                    OutlinedButton(onClick = { section = "location"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Localização simulada") }
                    OutlinedButton(onClick = { section = "intents"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Intenções recebidas (${intents.size})") }
                    TextButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("Ver como consumidor") }
                    Text("Cadastro, produtos, posições e intenções deste painel são locais e fictícios. Nenhuma ação é enviada ao Supabase.", style = MaterialTheme.typography.bodySmall)
                }
                "products" -> {
                    Button(onClick = { editingId = null; productName = "Pães"; section = "edit"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar categoria") }
                    owner.products.forEach { product ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DemoPhoto(product.id, product.name, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { editingId = product.id; productName = product.name; section = "edit"; message = null }) { Text("Editar ${product.name}") }
                                }
                            }
                        }
                    }
                }
                "edit" -> {
                    Text("Tipos de produtos que costuma oferecer", style = MaterialTheme.typography.titleLarge)
                    Text("Sem garantia de estoque. O consumidor confirma a disponibilidade diretamente com você.")
                    DemoCategoryPicker(productName, onSelect = { productName = it })
                    Button(onClick = { act { experience.saveDemoCategory(editingId, productName); section = "products"; message = "Categoria salva somente nesta demonstração." } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar categoria demo") }
                }
                "location" -> {
                    Text("São Paulo · posição fictícia", style = MaterialTheme.typography.titleLarge)
                    Text("Não usa GPS do aparelho nem publica dados remotamente. Ao ficar offline, a posição sai do cenário local.")
                    Button(enabled = owner.isOnline, onClick = { act { experience.publishDemoLocation(); message = "Posição simulada publicada apenas no catálogo local." } }, modifier = Modifier.fillMaxWidth()) { Text("Publicar posição simulada") }
                    if (!owner.isOnline) Text("Fique online no painel para habilitar esta simulação.")
                    val point = if (owner.latitude != null && owner.longitude != null) Location(owner.latitude, owner.longitude) else null
                    NativeMap(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)),
                        userLocation = experience.referenceLocation,
                        vendorMarkers = point?.let { listOf(VendorMapMarker(owner.id, owner.name, it)) }.orEmpty())
                }
                "intents" -> {
                    Text("Clientes e solicitações de exemplo", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = owner.isOnline && owner.latitude != null && owner.products.isNotEmpty(), onClick = { act { experience.simulateCustomer(); message = "Um cliente fictício criou uma intenção local." } }, modifier = Modifier.fillMaxWidth()) { Text("Simular cliente") }
                    if (intents.isEmpty()) Text("Nenhuma intenção simulada ainda. Ative disponibilidade e posição para simular um cliente.")
                    intents.forEach { intent ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(intent.interest.protocol, style = MaterialTheme.typography.titleMedium)
                                Text("Cliente fictício · ${intent.interest.productName}")
                                Text("Consulta de categoria · disponibilidade a confirmar")
                                Text(when (intent.status) { DemoIntentStatus.Pending -> "Pendente (demo)"; DemoIntentStatus.Accepted -> "Contato em andamento (demo)"; DemoIntentStatus.Completed -> "Atendimento simulado concluído" })
                                if (intent.status != DemoIntentStatus.Completed) Button(onClick = { act { experience.advanceDemoIntent(intent.interest.protocol); message = null } }) {
                                    Text(if (intent.status == DemoIntentStatus.Pending) "Simular contato" else "Concluir atendimento")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
