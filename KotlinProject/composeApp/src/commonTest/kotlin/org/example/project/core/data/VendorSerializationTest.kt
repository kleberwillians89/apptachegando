package org.example.project.core.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VendorSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLiveVendorProductsWithoutProductIds() {
        val vendors = json.decodeFromString<List<Vendor>>(
            """
            [
              {
                "id": "1",
                "name": "Tapioca da Tia",
                "description": "Feita na hora",
                "category": "Comida",
                "distance": "0.2 km",
                "rating": "4.8",
                "review_count": 120,
                "is_online": true,
                "products": [
                  {"name": "Tapioca de Carne Seca", "price": "12.00"},
                  {"name": "Tapioca Doce", "price": "10.00"}
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals("Tapioca de Carne Seca", vendors.single().products.first().name)
    }

    @Test
    fun decodesLiveVendorProductsWithUnitPriceText() {
        val vendors = json.decodeFromString<List<Vendor>>(
            """
            [
              {
                "id": "4",
                "name": "Doces da Dona Lucia",
                "description": "Doces caseiros",
                "category": "Doces",
                "distance": "0.4 km",
                "rating": "4.9",
                "review_count": 90,
                "is_online": true,
                "products": [
                  {"name": "Bolo de Cenoura", "price": "8.00/fatia"}
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals("Bolo de Cenoura", vendors.single().products.single().name)
    }
}
