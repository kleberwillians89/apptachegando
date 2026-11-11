package org.example.project.core.navigation

sealed class NavDestination(val route: String, val title: String) {
    object Splash : NavDestination("splash", "Splash")
    object Onboarding : NavDestination("onboarding", "Inicio")
    object Login : NavDestination("login", "Login")
    object ForgotPassword : NavDestination("forgot_password", "Recuperar Senha")
    object RegisterType : NavDestination("register_type", "Tipo de Conta")
    object ConsumerRegister : NavDestination("consumer_register", "Cadastro Cliente")
    object VendorRegister : NavDestination("vendor_register", "Cadastro Profissional")
    object Notifications : NavDestination("notifications", "Avisos")

    object ConsumerHome : NavDestination("consumer_home", "Mapa")
    object ConsumerCategories : NavDestination("consumer_categories", "Buscar")
    object ConsumerVendorList : NavDestination("consumer_vendor_list/{category}", "Profissionais") {
        fun createRoute(category: String) = "consumer_vendor_list/$category"
    }
    object ConsumerVendorProfile : NavDestination("consumer_vendor_profile/{vendorId}", "Profissional") {
        fun createRoute(vendorId: String) = "consumer_vendor_profile/$vendorId"
    }
    object ConsumerVendorTracking : NavDestination("consumer_vendor_tracking/{vendorId}", "Acompanhar") {
        fun createRoute(vendorId: String) = "consumer_vendor_tracking/$vendorId"
    }
    object ConsumerProfile : NavDestination("consumer_profile", "Perfil")
    object ConsumerFavorites : NavDestination("consumer_favorites", "Favoritos")

    object VendorDashboard : NavDestination("vendor_dashboard", "Inicio")
    object VendorRoute : NavDestination("vendor_route", "Rota")
    object VendorHeatmap : NavDestination("vendor_heatmap", "Clientes")
    object VendorSettings : NavDestination("vendor_settings", "Conta")
    object VendorSubscription : NavDestination("vendor_subscription", "Meu Plano")
    object VendorProducts : NavDestination("vendor_products", "Meus Produtos")
}
