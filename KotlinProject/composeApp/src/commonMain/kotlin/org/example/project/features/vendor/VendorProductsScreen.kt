package org.example.project.features.vendor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.core.data.*
import org.example.project.core.domain.models.discoveryCategories
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.utils.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorProductsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf(emptyList<OwnCatalogProduct>()) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<OwnCatalogProduct?>(null) }
    var removing by remember { mutableStateOf<Product?>(null) }
    var page by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, refresh) {
        busy = true; loaded = false; error = null
        when (val result = backendResult("Não foi possível carregar o catálogo. Verifique a conexão e tente novamente.") { ProductRepository.listOwnEntries(page) }) {
            is UiState.Success -> { products = result.data; loaded = true }
            is UiState.Error -> error = result.message
            else -> Unit
        }
        busy = false
    }
    fun persist(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                when (val result = backendResult("Não foi possível confirmar a alteração. Confira sua conexão e tente novamente.") { action() }) {
                    is UiState.Success -> { name = ""; editing = null; refresh++ }
                    is UiState.Error -> error = result.message
                    else -> Unit
                }
            } finally { busy = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Meu catálogo") }, navigationIcon = {
        IconButton(onClick = onBack, enabled = !busy) { Icon(ArrowBackIcon, "Voltar") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gerencie os produtos que você cadastrou no site ou no aplicativo. Você pode ocultá-los sem excluir. O catálogo não garante estoque e não realiza compras.")
            Text("Imagens ilustrativas por categoria. Envio de fotos próprias ainda indisponível.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy, onClick = { refresh++ }) { Text("Tentar novamente") }
            }
            Row {
                TextButton(enabled = !busy && page > 0, onClick = { page-- }) { Text("Anterior") }
                Text("Página ${page + 1}", Modifier.weight(1f))
                TextButton(enabled = !busy && products.size == VendorRepository.PAGE_SIZE, onClick = { page++ }) { Text("Próxima") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loaded && products.isEmpty()) item { Text("Nenhum item nesta página. Adicione uma categoria ou volte à página anterior.") }
                items(products, key = { it.id }) { product ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(product.name, Modifier.weight(1f))
                            Text(if (product.isActive) "Visível" else "Oculto",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = loaded && !busy, onClick = {
                                persist { ProductRepository.setActive(product.id, !product.isActive) }
                            }) { Text(if (product.isActive) "Ocultar" else "Mostrar") }
                            TextButton(enabled = loaded && !busy, onClick = {
                                editing = product; name = product.name
                            }) { Text("Editar") }
                            TextButton(enabled = loaded && !busy, onClick = { removing = product }) { Text("Remover") }
                        }
                    }
                }
            }
            Text(if (editing == null) "Adicionar categoria ou produto" else "Editar item")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveryCategories) { category ->
                    FilterChip(enabled = !busy, selected = name == category, onClick = { name = category }, label = { Text(category) })
                }
            }
            OutlinedTextField(name, { name = it.take(160) }, enabled = !busy, label = { Text("Nome da categoria ou produto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = loaded && !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                val value = name.trim(); val item = editing
                persist { if (item == null) ProductRepository.addCategory(value) else ProductRepository.rename(item.id, value) }
            }) { Text(if (editing == null) "Adicionar ao catálogo" else "Salvar alteração") }
            if (editing != null) TextButton(enabled = !busy, onClick = { editing = null; name = "" }) { Text("Cancelar edição") }
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        }
    }
    removing?.let { product ->
        AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("Remover do catálogo?") },
            text = { Text("Remover ${product.name}? Os demais itens serão preservados.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                removing = null
                persist { ProductRepository.delete(product.id) }
            }) { Text("Remover") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text("Manter") } })
    }
}
