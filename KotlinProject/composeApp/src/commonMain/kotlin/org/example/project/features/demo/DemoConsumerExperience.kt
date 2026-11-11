package org.example.project.features.demo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.example.project.core.data.ConsumerExperience
import org.example.project.core.data.Product
import org.example.project.core.data.PurchaseIntentReceipt
import org.example.project.core.data.Vendor
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

/** Ephemeral, explicitly fictional catalogue. No Auth, Supabase, HTTP, disk or device GPS access. */
class DemoConsumerExperience : ConsumerExperience {
    override val isDemonstration = true
    override val referenceLocation = Location(-23.55052, -46.633308)
    val ownedVendorId: String get() = when (registration?.category) {
        "Frutas" -> "demo-vendor-frutas"
        "Salgados fritos", "Salgados assados" -> "demo-vendor-salgados"
        "Lanches naturais" -> "demo-vendor-lanches"
        "Sorvetes", "Picolés" -> "demo-vendor-sorvetes"
        else -> "demo-vendor-paes"
    }
    val routes = DemoRouteSession()
    private val changed = MutableStateFlow(0)
    val revision = changed.asStateFlow()
    private val intentStatuses = mutableMapOf<String, DemoIntentStatus>()
    private val productArtwork = mutableMapOf<String, String>()
    private var nextProduct = 1
    private fun notifyChanged() { changed.value++ }
    private fun requireSeller() { check(registration?.role == DemoRole.Vendor) { "Cadastre o perfil vendedor nesta demonstração." } }
    fun ownVendor(): Vendor { requireSeller(); return catalogue.single { it.id == ownedVendorId } }
    fun artworkId(id: String): String = productArtwork[id] ?: id
    fun setDemoOnline(online: Boolean) {
        requireSeller()
        catalogueState.value = catalogue.map { if (it.id == ownedVendorId) it.copy(isOnline = online,
            latitude = if (online) it.latitude else null, longitude = if (online) it.longitude else null) else it }
        notifyChanged()
    }
    fun publishDemoLocation() {
        check(ownVendor().isOnline) { "Fique online antes de publicar a posição simulada." }
        val position = initialPositions.getValue(ownedVendorId)
        catalogueState.value = catalogue.map { if (it.id == ownedVendorId) it.copy(latitude = position.latitude, longitude = position.longitude) else it }
        notifyChanged()
    }
    /** The same local catalogue reflects this seller's simulated operational journey. */
    fun applyJourneySnapshot(snapshot: org.example.project.core.domain.route.AttendanceSnapshot) {
        requireSeller()
        val p = snapshot.location
        val visible = snapshot.phase == org.example.project.core.domain.route.JourneyPhase.Working &&
            snapshot.connected && snapshot.gpsIssue == null && p != null &&
            org.example.project.core.domain.route.RouteProgress.valid(p)
        val next = catalogue.map { if (it.id != ownedVendorId) it else it.copy(isOnline = visible,
            latitude = if (visible) p?.latitude else null, longitude = if (visible) p?.longitude else null) }
        if (next != catalogue) { catalogueState.value = next; notifyChanged() }
    }
    fun canUseAutomaticJourney(vendorId: String, snapshot: org.example.project.core.domain.route.AttendanceSnapshot?): Boolean =
        registration?.role == DemoRole.Vendor && vendorId == ownedVendorId &&
            snapshot?.phase in listOf(org.example.project.core.domain.route.JourneyPhase.Working, org.example.project.core.domain.route.JourneyPhase.Paused)

    fun saveDemoCategory(id: String?, category: String) {
        val owner = ownVendor()
        require(category in demoCategories) { "Escolha uma categoria de produtos prontos." }
        if (id != null) require(owner.products.any { it.id == id }) { "Categoria não pertence a este vendedor demo." }
        else require(owner.products.size < 10) { "Limite de categorias nesta demonstração." }
        require(owner.products.none { it.name == category && it.id != id }) { "Categoria já cadastrada." }
        val productId = id ?: "demo-product-extra-${nextProduct++}"
        productArtwork[productId] = when (category) {
            "Pães" -> "demo-product-paes"
            "Doces de padaria" -> "demo-product-doces"
            "Temperos" -> "demo-product-temperos"
            "Frutas" -> "demo-product-frutas"
            "Salgados fritos" -> "demo-product-fritos"
            "Salgados assados" -> "demo-product-assados"
            "Lanches naturais" -> "demo-product-naturais"
            "Sorvetes" -> "demo-product-sorvete"
            "Picolés" -> "demo-product-picole"
            else -> "demo-product-generico"
        }
        val product = Product(productId, category, "")
        catalogueState.value = catalogue.map { vendor -> if (vendor.id != ownedVendorId) vendor else vendor.copy(
            products = if (id == null) vendor.products + product else vendor.products.map { if (it.id == id) product else it }) }
        notifyChanged()
    }
    fun incomingIntents(): List<DemoIncomingIntent> {
        requireSeller()
        return interestHistory().filter { it.vendorId == ownedVendorId }.map { DemoIncomingIntent(it, intentStatuses[it.protocol] ?: DemoIntentStatus.Pending) }
    }
    fun simulateCustomer(): DemoInterest {
        val owner = ownVendor();check(owner.isOnline && owner.latitude != null) { "Ative a disponibilidade e a posição simulada antes de simular um cliente." }
        return consultAvailability(owner.id, owner.products.first().id)
    }
    fun advanceDemoIntent(protocol: String) {
        requireSeller()
        val intent = incomingIntents().singleOrNull { it.interest.protocol == protocol } ?: error("Intenção não pertence ao vendedor demo.")
        intentStatuses[protocol] = when (intent.status) {
            DemoIntentStatus.Pending -> DemoIntentStatus.Accepted
            DemoIntentStatus.Accepted -> DemoIntentStatus.Completed
            DemoIntentStatus.Completed -> error("Intenção já concluída.")
        }
        notifyChanged()
    }
    private val receipts = mutableMapOf<String, PurchaseIntentReceipt>()
    private val interests = linkedMapOf<String, DemoInterest>()
    var registration: DemoRegistration? = null
        private set

    fun register(name: String, role: DemoRole, category: String = "Pães"): DemoRegistration {
        require(role != DemoRole.Vendor || category in demoCategories)
        require(name.trim().isNotEmpty() && name.trim().length <= 40) { "Use um nome fictício de até 40 caracteres." }
        return DemoRegistration(name.trim(), role, category.takeIf { role == DemoRole.Vendor }).also {
            registration = it
            if (role == DemoRole.Vendor) {
                setDemoOnline(false)
                if (ownVendor().products.none { product -> product.name == category }) saveDemoCategory(null, category)
            }
            notifyChanged()
        }
    }

    fun consultAvailability(vendorId: String, productId: String): DemoInterest {
        val vendor = catalogue.single { it.id == vendorId && it.isOnline }
        val product = vendor.products.single { it.id == productId }
        // Repeated taps for this seller/category return the same local protocol.
        interests.values.firstOrNull { it.vendorId == vendorId && it.productId == productId }?.let { return it }
        val protocol = "DEMO-INTERESSE-${(interests.size + 1).toString().padStart(4, '0')}"
        return DemoInterest(protocol, vendor.id, product.id, vendor.name, product.name).also {
            interests[protocol] = it
            receipts[vendorId] = PurchaseIntentReceipt(protocol, vendorId, "simulated", "demo-session-only")
            notifyChanged()
        }
    }

    fun interest(protocol: String): DemoInterest? = interests[protocol]
    fun interestHistory(): List<DemoInterest> = interests.values.toList().asReversed()

    private val catalogueState = MutableStateFlow(listOf(
        vendor("paes", "Pão do José", "Pães · Doces de padaria", -23.5498, -46.6342,
            listOf(product("paes", "Pães"), product("doces", "Doces de padaria"))),
        vendor("frutas", "Frutas do Luiz", "Frutas", -23.5474, -46.6292,
            listOf(product("frutas", "Frutas"), product("temperos", "Temperos"))),
        vendor("salgados", "Salgados da Praça", "Salgados fritos · Salgados assados", -23.5540, -46.6295,
            listOf(product("fritos", "Salgados fritos"), product("assados", "Salgados assados"))),
        vendor("lanches", "Lanches Naturais", "Lanches naturais", -23.5550, -46.6370,
            listOf(product("naturais", "Lanches naturais"), product("doces-lanches", "Doces de padaria"))),
        vendor("sorvetes", "Sorvetes do Bairro", "Sorvetes · Picolés", -23.5460, -46.6380,
            listOf(product("sorvete", "Sorvetes"), product("picole", "Picolés")))
    ))
    val catalogue: List<Vendor> get() = catalogueState.value
    private val initialPositions = catalogue.associate { it.id to Location(it.latitude!!, it.longitude!!) }

    override fun vendors(page: Int, vendorId: String?, query: String): Flow<UiState<List<Vendor>>> {
        require(page >= 0)
        return catalogueState.map { all ->
            val visible = all.filter { it.isOnline }
            UiState.Success(if (vendorId != null) visible.filter { it.id == vendorId } else if (page == 0) visible else emptyList())
        }
    }

    override suspend fun pendingIntent(vendorId: String): UiState<PurchaseIntentReceipt?> =
        if (catalogue.none { it.id == vendorId }) UiState.Error("Profissional de demonstração não encontrado.")
        else UiState.Success(receipts[vendorId])

    override suspend fun createIntent(vendorId: String, location: Location): UiState<PurchaseIntentReceipt> {
        if (catalogue.none { it.id == vendorId && it.isOnline }) return UiState.Error("Profissional de demonstração indisponível.")
        // Local de-duplication, cleared by disposing this demo session; never a real order.
        return UiState.Success(receipts.getOrPut(vendorId) {
            PurchaseIntentReceipt("DEMO-${receipts.size + 1}", vendorId, "simulated", "demo-session-only")
        })
    }

    private fun product(id: String, name: String) = Product("demo-product-$id", name, "")
    private fun vendor(id: String, name: String, category: String, lat: Double, lon: Double, products: List<Product>) =
        Vendor("demo-vendor-$id", "[DEMO] $name", "Ambulante fictício. Categorias ilustrativas de produtos prontos; confirme a disponibilidade diretamente com o vendedor.",
            category, "", "", 0, true, lat, lon, products)
}
