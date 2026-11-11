package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.example.project.core.theme.AppTheme
import org.example.project.core.navigation.AppNavigation

@Composable
@Preview
fun App(authEntryRequest: Int = 0, interestNotice: org.example.project.core.domain.notifications.InterestNotice? = null) {
    AppTheme {
        AppNavigation(authEntryRequest, interestNotice)
    }
}