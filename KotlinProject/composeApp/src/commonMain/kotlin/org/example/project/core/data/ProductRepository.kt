package org.example.project.core.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonPrimitive

/** Decimal text is validated as cents: no floating point rounding in prices. */
internal fun canonicalPrice(value: String): String {
    val normalized = value.trim().replace(',', '.')
    require(Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(normalized)) { "Informe um preço válido." }
    val parts = normalized.split('.')
    return parts[0].toLong().toString() + "." + parts.getOrElse(1) { "" }.padEnd(2, '0')
}

/** Keep decimal precision and never invent a price for missing/invalid backend data. */
internal fun productDisplayPrice(value: String): String = runCatching {
    "R$ " + canonicalPrice(value).replace('.', ',')
}.getOrDefault("Preço indisponível")

@Serializable
internal data class ProductRecord(val id: String, val name: String, val price: JsonPrimitive) {
    fun toDomain() = Product(id, name, canonicalPrice(price.content))
}

object ProductRepository {
    @Serializable private data class ProductWrite(
        @SerialName("vendor_id") val vendorId: String, val name: String, val price: String
    )
    private suspend fun currentVendorId(): String = VendorRepository.resolveRouteVendorId(
        AuthRepository.currentUser()?.id ?: error("Entre na sua conta.")
    )
    suspend fun listOwn(page: Int = 0): List<Product> {
        val bounds = pageBounds(page, VendorRepository.PAGE_SIZE)
        val vendorId = currentVendorId()
        return supabase.postgrest["products"].select(columns = Columns.list("id", "name", "price")) {
            filter { eq("vendor_id", vendorId) }
            order("id", Order.ASCENDING)
            range(bounds.first, bounds.last)
        }.decodeList<ProductRecord>().map { it.toDomain() }
    }

    suspend fun addCategory(name: String): Product {
        require(name.trim().length in 1..160)
        @Serializable data class CategoryWrite(@SerialName("vendor_id") val vendorId: String, val name: String)
        return supabase.postgrest["products"].insert(CategoryWrite(currentVendorId(), name.trim())) {
            select()
        }.decodeSingle<ProductRecord>().toDomain()
    }
    suspend fun rename(id: String, name: String): Product {
        require(name.trim().length in 1..160)
        val vendorId = currentVendorId()
        return supabase.postgrest["products"].update({ set("name", name.trim()) }) {
            filter { eq("id", id); eq("vendor_id", vendorId) }; select()
        }.decodeSingle<ProductRecord>().toDomain()
    }

    suspend fun add(name: String, price: String): Product {
        require(name.trim().isNotEmpty())
        return supabase.postgrest["products"].insert(ProductWrite(currentVendorId(), name.trim(), canonicalPrice(price))) {
            select()
        }.decodeSingle<ProductRecord>().toDomain()
    }
    suspend fun update(product: Product): Product = supabase.postgrest["products"].update({
        set("name", product.name.trim()); set("price", canonicalPrice(product.price))
    }) { filter { eq("id", product.id) }; select() }.decodeSingle<ProductRecord>().toDomain()

    suspend fun delete(id: String) {
        val vendorId = currentVendorId()
        val rows = supabase.postgrest["products"].delete { filter { eq("id", id); eq("vendor_id", vendorId) }; select() }.decodeList<VendorIdentity>()
        check(rows.singleOrNull()?.id == id) { "Não foi possível remover o produto." }
    }
}
