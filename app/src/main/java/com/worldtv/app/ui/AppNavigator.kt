package com.worldtv.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

sealed interface AppScreen {
    data object Catalog : AppScreen
    data object Countries : AppScreen
    data object Favorites : AppScreen
    data object Settings : AppScreen
    data object Privacy : AppScreen
    data object Player : AppScreen
}

class AppNavigator : ViewModel() {
    var stack by mutableStateOf(listOf<AppScreen>(AppScreen.Catalog))
        private set

    val current: AppScreen get() = stack.last()

    fun openSection(screen: AppScreen) {
        stack = listOf(screen)
    }

    fun openCountries() {
        stack = listOf(AppScreen.Catalog, AppScreen.Countries)
    }

    fun openPrivacy() {
        stack = listOf(AppScreen.Settings, AppScreen.Privacy)
    }

    fun openPlayer() {
        if (current != AppScreen.Player) stack = stack + AppScreen.Player
    }

    fun back(): Boolean {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            return true
        }
        if (current != AppScreen.Catalog) {
            stack = listOf(AppScreen.Catalog)
            return true
        }
        return false
    }
}
