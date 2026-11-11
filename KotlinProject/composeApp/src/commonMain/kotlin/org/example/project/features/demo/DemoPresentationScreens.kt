package org.example.project.features.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.core.data.Product
import org.example.project.core.data.Vendor
import org.jetbrains.compose.resources.painterResource
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.logo

@Composable
fun DemoWelcome(onRegister: () -> Unit, onExplore: () -> Unit, onAutomaticRoute: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(Res.drawable.logo), "TáChegando", Modifier.size(120.dp))
        Text("Seu bairro, mais perto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Conecte-se a vendedores ambulantes próximos. Conheça suas categorias e consulte disponibilidade, em um cenário fictício de São Paulo.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        listOf("1. Descubra profissionais no mapa", "2. Conheça as categorias habituais", "3. Manifeste interesse, sem pedido ou pagamento").forEach { step ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(step, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Cadastrar na demonstração") }
        OutlinedButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Explorar demonstração") }
        TextButton(onClick = onAutomaticRoute, modifier = Modifier.fillMaxWidth()) { Text("Atendimento automático por rota") }
        Text("Cadastros e interesses ficam na sessão. Rotas fictícias do atendimento automático podem ser salvas no Android. O mapa Google usa internet.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoRegistrationScreen(onBack: () -> Unit, onComplete: (String, DemoRole, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var vendorRole by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Pães") }
    Scaffold(topBar = { TopAppBar(title = { Text("Cadastro demo") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Como você quer conhecer o TáChegando?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !vendorRole, onClick = { vendorRole = false }, label = { Text("Consumidor") })
                FilterChip(selected = vendorRole, onClick = { vendorRole = true }, label = { Text("Vendedor") })
            }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Nome fictício") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = attempted && name.isBlank(), supportingText = { Text("Use um apelido de teste. Não pedimos email ou senha.") })
            if (vendorRole) DemoCategoryPicker(category, onSelect = { category = it })
            Spacer(Modifier.height(24.dp))
            Button(onClick = { attempted = true; if (name.isNotBlank()) onComplete(name, if (vendorRole) DemoRole.Vendor else DemoRole.Consumer, category) },
                modifier = Modifier.fillMaxWidth()) { Text("Concluir cadastro demo") }
            Text("Este formulário não cria uma conta real e não envia informações ao Supabase.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DemoRegistrationComplete(registration: DemoRegistration, onExplore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Cadastro de demonstração concluído", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Olá, ${registration.name}.", style = MaterialTheme.typography.titleLarge)
        Text("Perfil simulado: ${if (registration.role == DemoRole.Vendor) "vendedor" else "consumidor"}.")
        registration.category?.let { Text("Categoria demo: $it") }
        Spacer(Modifier.height(16.dp))
        Text(if (registration.role == DemoRole.Vendor) "Nenhuma conta real foi criada. Continue no seu painel local para explorar categorias, disponibilidade e rotas simuladas." else "Nenhuma conta real foi criada. Agora explore a jornada de encontrar vendedores e consultar as categorias habituais.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text(if (registration.role == DemoRole.Vendor) "Ir para meu painel" else "Explorar vendedores") }
    }
}

/** Compact selector, shared by registration and the seller's local catalogue. */
@Composable
fun DemoCategoryPicker(category: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Categoria: $category") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            demoCategories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoProductScreen(vendor: Vendor, product: Product, onBack: () -> Unit, onConfirm: () -> Unit) {
    var submitted by remember(product.id) { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Conheça a categoria") }, navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DemoPhoto(product.id, product.name, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)))
            Text(product.name, style = MaterialTheme.typography.headlineMedium)
            Text(vendor.name, style = MaterialTheme.typography.titleMedium)
            Text("Categoria que este ambulante costuma oferecer. Imagem ilustrativa; os itens disponíveis variam ao longo do percurso.")
            Button(enabled = !submitted, onClick = { if (!submitted) { submitted = true; onConfirm() } }, modifier = Modifier.fillMaxWidth()) { Text("Consultar disponibilidade") }
            Text("Simulação local. Na experiência real, confirme a disponibilidade diretamente com o vendedor. Esta ação não reserva produtos nem gera pedido ou pagamento.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoInterestReceipt(interest: DemoInterest, onHistory: () -> Unit, onHome: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Consulta simulada") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Interesse registrado nesta sessão", style = MaterialTheme.typography.headlineMedium)
            DemoPhoto(interest.productId, interest.productName, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)))
            Text(interest.productName, style = MaterialTheme.typography.titleLarge)
            Text(interest.vendorName)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Seu protocolo local", style = MaterialTheme.typography.labelLarge)
                    Text(interest.protocol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Disponibilidade a confirmar diretamente com o vendedor. Nenhuma mensagem real foi enviada; não há pedido, pagamento ou reserva.")
            Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Consultar protocolo") }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Voltar à home") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoInterestHistory(interests: List<DemoInterest>, onOpen: (String) -> Unit, onHome: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Protocolos locais") }, navigationIcon = { TextButton(onClick = onHome) { Text("Home") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Somente nesta demonstração", style = MaterialTheme.typography.titleLarge)
            if (interests.isEmpty()) Text("Você ainda não consultou uma categoria nesta sessão.")
            interests.forEach { interest ->
                ElevatedCard(onClick = { onOpen(interest.protocol) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DemoPhoto(interest.productId, interest.productName, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(interest.protocol, style = MaterialTheme.typography.titleMedium)
                            Text(interest.productName)
                            Text("Consulta simulada · disponibilidade não confirmada")
                        }
                    }
                }
            }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Voltar à home") }
            Text("Os protocolos são apagados ao sair da demonstração.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
