package com.worldtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.app.domain.UserSettings
import com.worldtv.app.ui.WorldTvApp
import com.worldtv.app.ui.isTelevision
import com.worldtv.app.ui.theme.WorldTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorldTvApplication).container
        setContent {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val settings by container.settings.settings.collectAsStateWithLifecycle(
                initialValue = UserSettings(),
                lifecycle = lifecycle,
            )
            val television = isTelevision(this)
            WorldTvTheme(choice = settings.theme, television = television) {
                WorldTvApp(container = container, television = television)
            }
        }
    }
}
