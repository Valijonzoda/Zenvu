package com.worldtv.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.worldtv.app.data.LocaleApplier
import com.worldtv.app.ui.WorldTvApp
import com.worldtv.app.ui.isTelevision

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleApplier.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorldTvApplication).container
        setContent {
            WorldTvApp(container = container, television = isTelevision(this))
        }
    }
}
