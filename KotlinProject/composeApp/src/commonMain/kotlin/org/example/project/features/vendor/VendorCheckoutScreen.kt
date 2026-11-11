package org.example.project.features.vendor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.CheckIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorCheckoutScreen(
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            delay(1500) // Simula processamento de pagamento
            onConfirm()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pagamento") },
                navigationIcon = {
                    if (!isProcessing) {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = ArrowBackIcon, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumo do Pedido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Plano completo (mensal)")
                        Text("R$ 29,90", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxa de Adesão")
                        Text("Grátis", color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("R$ 29,90", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processando pagamento...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(
                    onClick = { isProcessing = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirmar pagamento")
                }
            }
        }
    }
}
