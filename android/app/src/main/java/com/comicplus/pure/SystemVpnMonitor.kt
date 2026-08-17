package com.comicplus.pure

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal data class SystemRouteSnapshot(
    val networkHandle: Long?,
    val vpnActive: Boolean,
    val linkFingerprint: String?,
)

internal fun systemRouteChanged(
    previous: SystemRouteSnapshot?,
    current: SystemRouteSnapshot,
): Boolean = previous != null && previous != current

/**
 * Observes the default network assigned to this app. Android routes ordinary
 * sockets through an active VPN automatically; clients only need to discard
 * connections opened on the previous route when that default changes.
 */
internal object SystemVpnMonitor {
    private val started = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val snapshotLock = Any()
    private var snapshot: SystemRouteSnapshot? = null

    @Volatile
    var isVpnActive: Boolean = false
        private set

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            started.set(false)
            return
        }
        refresh(manager)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh(manager)

            override fun onLost(network: Network) = refresh(manager)

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                refresh(manager)

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                refresh(manager)
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { started.set(false) }
    }

    fun registerRouteChangeListener(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    private fun refresh(manager: ConnectivityManager) {
        val current = manager.routeSnapshot()
        val changed = synchronized(snapshotLock) {
            val routeChanged = systemRouteChanged(snapshot, current)
            snapshot = current
            isVpnActive = current.vpnActive
            routeChanged
        }
        if (changed) listeners.forEach { listener -> runCatching(listener) }
    }
}

private fun ConnectivityManager.routeSnapshot(): SystemRouteSnapshot {
    val network = activeNetwork
    val capabilities = network?.let(::getNetworkCapabilities)
    val links = network?.let(::getLinkProperties)
    return SystemRouteSnapshot(
        networkHandle = network?.networkHandle,
        vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        linkFingerprint = links?.routeFingerprint(),
    )
}

private fun LinkProperties.routeFingerprint(): String = buildString {
    append(interfaceName.orEmpty())
    append('|')
    append(dnsServers.map { it.hostAddress.orEmpty() }.sorted().joinToString(","))
    append('|')
    append(routes.map { route -> route.toString() }.sorted().joinToString(","))
    append('|')
    append(httpProxy?.toString().orEmpty())
}
