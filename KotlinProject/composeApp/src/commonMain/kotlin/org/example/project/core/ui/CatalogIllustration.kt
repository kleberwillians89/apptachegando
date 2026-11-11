package org.example.project.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import kotlinproject.composeapp.generated.resources.*

/** Bundled illustrative artwork only; never a photo of stock or a real merchant. */
@Composable
fun CatalogIllustration(name: String, modifier: Modifier = Modifier) {
    val value = name.lowercase()
    val resource = when {
        "pães" in value || "pão" in value -> Res.drawable.investor_paes
        "fruta" in value -> Res.drawable.presentation_vendor_frutas
        "salgado" in value -> Res.drawable.investor_salgados
        "doce" in value -> Res.drawable.investor_doces
        "natural" in value -> Res.drawable.investor_naturais
        "sorvete" in value || "picolé" in value -> Res.drawable.presentation_product_sorvete
        "tempero" in value -> Res.drawable.investor_temperos
        else -> null
    }
    if (resource == null) Box(modifier) { Text("Sem foto") }
    else Image(painterResource(resource), contentDescription = "Ilustração da categoria $name", modifier = modifier, contentScale = ContentScale.Crop)
}
