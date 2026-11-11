package org.example.project.core.data

import kotlinx.coroutines.flow.Flow
import org.example.project.core.utils.Location
import org.example.project.core.utils.UiState

/** A screen receives one source. Demo data never falls back to a remote repository. */
interface ConsumerExperience {
    val isDemonstration: Boolean
    val referenceLocation: Location? get() = null
    fun vendors(page: Int = 0, vendorId: String? = null, query: String = ""): Flow<UiState<List<Vendor>>>
    fun discover(location: Location, page: Int = 0, query: String = ""): Flow<UiState<List<Vendor>>> = vendors(page = page, query = query)
    suspend fun pendingIntent(vendorId: String): UiState<PurchaseIntentReceipt?>
    suspend fun createIntent(vendorId: String, location: Location): UiState<PurchaseIntentReceipt>
    suspend fun cancelIntent(id: String): UiState<Unit> = UiState.Error("Cancelamento não disponível nesta experiência.")
}

object RemoteConsumerExperience : ConsumerExperience {
    override val isDemonstration = false
    private fun requireRemoteIdentity(vendorId: String?) {
        require(vendorId?.startsWith("demo-") != true) { "Dados de demonstração não podem acessar o Supabase." }
    }
    override fun vendors(page: Int, vendorId: String?, query: String): Flow<UiState<List<Vendor>>> {
        requireRemoteIdentity(vendorId)
        return VendorRepository.listenToVendorStates(page, vendorId, query)
    }
    override fun discover(location: Location, page: Int, query: String): Flow<UiState<List<Vendor>>> =
        VendorRepository.listenToVendorStates(page = page, query = query, location = location)
    override suspend fun pendingIntent(vendorId: String): UiState<PurchaseIntentReceipt?> {
        requireRemoteIdentity(vendorId)
        return PurchaseIntentRepository.pendingForVendor(vendorId)
    }
    override suspend fun cancelIntent(id: String): UiState<Unit> {
        requireRemoteIdentity(id)
        return PurchaseIntentRepository.cancel(id)
    }
    override suspend fun createIntent(vendorId: String, location: Location): UiState<PurchaseIntentReceipt> {
        requireRemoteIdentity(vendorId)
        return PurchaseIntentRepository.createArrivalAlert(vendorId, location)
    }
}
