package dev.smsgateway.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkState {
    /**
     * True when this app currently has a usable network. Returns false when
     * there is no network, and also when Android is blocking this app's
     * network (e.g. Doze without a battery exemption) — activeNetwork is null then.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
