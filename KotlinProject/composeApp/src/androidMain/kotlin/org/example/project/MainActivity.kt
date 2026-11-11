package org.example.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.example.project.core.domain.notifications.InterestNotice
import org.example.project.core.utils.AndroidInterestNotices
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import org.example.project.core.data.AuthLinks

class MainActivity : ComponentActivity() {
    private var interestNotice by mutableStateOf<InterestNotice?>(null)
    private var authEntryRequest by mutableIntStateOf(0)

    private fun handlePublicLink(intent: Intent?) {
        interestNotice = AndroidInterestNotices.readTarget(this, intent)
        if (intent?.action == Intent.ACTION_VIEW && AuthLinks.isAppEntry(intent.dataString)) {
            authEntryRequest++
        }
        // Never pass this URI to handleDeeplinks/importSession: this is a navigation link only.
        // Do not log incoming URIs, which may contain attacker-supplied credentials.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePublicLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInterestNotices.initialize(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) handlePublicLink(intent)
        setContent {
            App(authEntryRequest, interestNotice)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
