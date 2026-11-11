package org.example.project.core.navigation

import kotlinx.serialization.Serializable

/**
 * Rotas de navegação separadas em objetos @Serializable para
 * compatibilidade total com wasmJs (type-safe navigation).
 *
 * Rotas sem argumento são data objects.
 * Rotas com argumento são data classes.
 */

// ── Common ──────────────────────────────────────────────────────────
@Serializable data object SplashRoute
@Serializable data object OnboardingRoute
@Serializable data object LoginRoute
@Serializable data object ForgotPasswordRoute
@Serializable data object RegisterTypeRoute
@Serializable data object ConsumerRegisterRoute
@Serializable data object VendorRegisterRoute
@Serializable data object NotificationsRoute

// ── Consumer ────────────────────────────────────────────────────────
@Serializable data object ConsumerHomeRoute
@Serializable data object ConsumerCategoriesRoute
@Serializable data class ConsumerVendorListRoute(val category: String)
@Serializable data class ConsumerVendorProfileRoute(val vendorId: String)
@Serializable data class ConsumerVendorTrackingRoute(val vendorId: String)
@Serializable data object ConsumerProfileRoute
@Serializable data object ConsumerFavoritesRoute

// ── Vendor ──────────────────────────────────────────────────────────
@Serializable data object VendorDashboardRoute
@Serializable data object VendorRouteRoute
@Serializable data object VendorHeatmapRoute
@Serializable data object VendorSettingsRoute
@Serializable data object VendorSubscriptionRoute
@Serializable data object VendorCheckoutRoute
@Serializable data object VendorCheckoutSuccessRoute
@Serializable data object VendorProductsRoute
