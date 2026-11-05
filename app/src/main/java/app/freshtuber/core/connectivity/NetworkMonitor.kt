package app.freshtuber.core.connectivity

import android.content.Context
import android.net.*
import app.freshtuber.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private fun connected(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true
    private data class Connection(val online: Boolean, val wifi: Boolean)
    private fun connection() = Connection(connected(), manager.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true)
    private val connection: StateFlow<Connection> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(connection()) }
            override fun onLost(network: Network) { trySend(connection()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(connection()) }
        }
        trySend(connection())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, connection())
    val online = connection.map { it.online }.stateIn(scope, SharingStarted.Eagerly, connection.value.online)
    val wifi = connection.map { it.wifi }.stateIn(scope, SharingStarted.Eagerly, connection.value.wifi)
}
