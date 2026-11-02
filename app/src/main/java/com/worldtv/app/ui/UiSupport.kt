package com.worldtv.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun isTelevision(context: Context): Boolean {
    val uiMode = context.getSystemService(UiModeManager::class.java)
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

inline fun <reified T : ViewModel> viewModelFactory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
