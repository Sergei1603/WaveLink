package ru.wavelink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.wavelink.app.downloads.DownloadsRepository
import ru.wavelink.app.ui.WaveLinkAppRoot
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var downloads: DownloadsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Downloads are checked against what the cache actually holds once per process, and from
        // the foreground: repairing one starts a service, which a backgrounded app may not do.
        lifecycleScope.launch { downloads.reconcile() }
        setContent { WaveLinkAppRoot() }
    }
}
