package com.worldtv.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidNetworkStatus(context: Context) : () -> Boolean {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun invoke(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
