package org.example.project.features.demo

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.investor_paes
import kotlinproject.composeapp.generated.resources.investor_salgados
import kotlinproject.composeapp.generated.resources.investor_naturais
import kotlinproject.composeapp.generated.resources.investor_doces
import kotlinproject.composeapp.generated.resources.investor_temperos
import kotlinproject.composeapp.generated.resources.presentation_vendor_frutas
import kotlinproject.composeapp.generated.resources.presentation_vendor_sorvetes
import kotlinproject.composeapp.generated.resources.presentation_product_maca
import kotlinproject.composeapp.generated.resources.presentation_product_picole
import kotlinproject.composeapp.generated.resources.presentation_product_sorvete
import kotlinproject.composeapp.generated.resources.logo

/** Bundled AI illustrative photographs. No real merchant identity or runtime image URLs. */
fun demoPhotoResource(id: String): DrawableResource? = when (id) {
    "demo-vendor-paes", "demo-product-paes" -> Res.drawable.investor_paes
    "demo-vendor-frutas" -> Res.drawable.presentation_vendor_frutas
    "demo-vendor-sorvetes" -> Res.drawable.presentation_vendor_sorvetes
    "demo-vendor-salgados", "demo-product-fritos", "demo-product-assados" -> Res.drawable.investor_salgados
    "demo-vendor-lanches", "demo-product-naturais" -> Res.drawable.investor_naturais
    "demo-product-doces", "demo-product-doces-lanches" -> Res.drawable.investor_doces
    "demo-product-frutas" -> Res.drawable.presentation_product_maca
    "demo-product-temperos" -> Res.drawable.investor_temperos
    "demo-product-picole" -> Res.drawable.presentation_product_picole
    "demo-product-sorvete" -> Res.drawable.presentation_product_sorvete
    "demo-product-generico" -> Res.drawable.logo // Brand placeholder, never a misleading food image.
    else -> null
}

@Composable
fun DemoPhoto(id: String, description: String, modifier: Modifier = Modifier) {
    val source = LocalConsumerExperience.current
    val artworkId = if (source is DemoConsumerExperience) source.artworkId(id) else id
    demoPhotoResource(artworkId)?.let { resource ->
        Image(painterResource(resource), contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
    }
}
