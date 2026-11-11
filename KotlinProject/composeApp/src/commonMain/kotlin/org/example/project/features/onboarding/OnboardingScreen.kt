package org.example.project.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onDemoClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "Logo Tá Chegando",
            modifier = Modifier.size(if (onDemoClick != null) 180.dp else 240.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bem-vindo ao Tá Chegando!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        ElevatedCard(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(if (onDemoClick != null) 80.dp else 100.dp)
        ) {
            BoxCenter {
                Text("Entrar", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth().height(if (onDemoClick != null) 80.dp else 100.dp)
        ) {
            BoxCenter {
                Text("Criar nova conta", style = MaterialTheme.typography.titleLarge)
            }
        }
        onDemoClick?.let { openDemo ->
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = openDemo, modifier = Modifier.fillMaxWidth()) { Text("Explorar demonstração") }
            Text("Ambulantes próximos, categorias e consultas locais em São Paulo.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
