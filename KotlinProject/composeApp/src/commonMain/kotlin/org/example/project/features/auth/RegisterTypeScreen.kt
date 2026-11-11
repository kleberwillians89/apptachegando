package org.example.project.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.ArrowForwardIcon
import org.example.project.core.navigation.CheckIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.ShoppingBagIcon
import org.example.project.core.navigation.StarIcon
import org.example.project.core.navigation.StoreIcon
import org.example.project.core.theme.AppColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RegisterTypeScreen(
    onBack: () -> Unit,
    onConsumerSelected: () -> Unit,
    onVendorSelected: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = ArrowBackIcon,
                        contentDescription = "Voltar",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AppColors.Primary, AppColors.PrimaryDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LocationOnIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Como voc\u00ea quer usar o app?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Escolha o tipo de conta para come\u00e7ar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            SelectableTypeCard(
                title = "Cliente",
                subtitle = "Encontrar profissionais e comprar",
                icon = ShoppingBagIcon,
                features = listOf("Encontrar profissionais perto", "Acompanhar no mapa", "Salvar favoritos"),
                gradientColors = listOf(AppColors.Primary, AppColors.PrimaryDark),
                isSelected = selected == "consumer",
                onTap = { selected = "consumer" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SelectableTypeCard(
                title = "Profissional",
                subtitle = "Vender enquanto se desloca",
                icon = StoreIcon,
                features = listOf("Ver onde tem clientes", "Criar minhas rotas", "Avisar clientes"),
                gradientColors = listOf(AppColors.Secondary, AppColors.SecondaryDark),
                isSelected = selected == "vendor",
                onTap = { selected = "vendor" }
            )

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    if (selected == "consumer") onConsumerSelected()
                    else if (selected == "vendor") onVendorSelected()
                },
                enabled = selected != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Continuar",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = ArrowForwardIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SelectableTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    features: List<String>,
    gradientColors: List<Color>,
    isSelected: Boolean,
    onTap: () -> Unit
) {
    val borderColor = if (isSelected) gradientColors.first() else MaterialTheme.colorScheme.outline
    val borderWidth = if (isSelected) 2.dp else 1.dp

    OutlinedButton(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) gradientColors.first().copy(alpha = 0.05f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(colors = gradientColors)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = gradientColors.first()
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = CheckIcon,
                                contentDescription = "Selecionado",
                                tint = gradientColors.first(),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = StarIcon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = gradientColors.first().copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
