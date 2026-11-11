package org.example.project.core.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // ── Cores da Marca (extraídas do logotipo) ──────────────────────────
    val BrandOrange = Color(0xFFFF6B00) // Laranja do pin e da palavra "chegando"
    val BrandBlue = Color(0xFF1B2A4A)   // Azul escuro da palavra "Tá"

    // ── Primary (Laranja) ───────────────────────────────────────────────
    val Primary = BrandOrange
    val PrimaryDark = Color(0xFFE55E00)
    val PrimaryLight = Color(0xFFFF8A33)

    // ── Secondary (Azul Escuro) ─────────────────────────────────────────
    val Secondary = BrandBlue
    val SecondaryDark = Color(0xFF0F1D35)
    val SecondaryLight = Color(0xFF3D506E)

    // ── Accent / Destaque ───────────────────────────────────────────────
    val Accent = Color(0xFFFFD166)
    val AccentDark = Color(0xFFE6B85C)
    val AccentLight = Color(0xFFFFDCA3)

    // ── Semânticas ──────────────────────────────────────────────────────
    val Success = Color(0xFF06D6A0)
    val Error = Color(0xFFEF476F)

    // ── Superfícies (Tema Claro) ────────────────────────────────────────
    val Background = Color(0xFFFAFAFA)
    val Surface = Color.White
    val SurfaceVariant = Color(0xFFF5F5F5)
    val OnBackground = Color(0xFF1E1E1E)
    val OnSurface = Color(0xFF1E1E1E)
    val OnSurfaceVariant = Color(0xFF757575)
    val OnPrimary = Color.White
    val OnSecondary = Color.White
    val Outline = Color(0xFFE0E0E0)

    // ── Categorias ──────────────────────────────────────────────────────
    val CategoryOrange = Color(0xFFFFF3E0)
    val CategoryYellow = Color(0xFFFFFDE7)
    val CategoryPurple = Color(0xFFF3E5F5)
    val CategoryGreen = Color(0xFFE8F5E9)
    val CategoryBlue = Color(0xFFE3F2FD)
    val CategoryPink = Color(0xFFFCE4EC)
}
