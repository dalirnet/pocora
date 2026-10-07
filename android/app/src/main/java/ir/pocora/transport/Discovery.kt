package ir.pocora.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import ir.pocora.debug.FileLogger
import ir.pocora.protocol.Protocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

// Announces this phone on the Wi-Fi and looks for the other one, by its random id.
class Discovery(
    context: Context,
) {
    companion object {
        private const val TAG = "Discovery"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    fun announce(
        id: String,
        port: Int,
    ) {
        stopAnnouncing()
        val service =
            NsdServiceInfo().apply {
                serviceName = id
                serviceType = Protocol.SERVICE_TYPE
                setPort(port)
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = FileLogger.i(TAG, "Announcing on port $port")

                override fun onRegistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = FileLogger.w(TAG, "Announcing failed: $errorCode")

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = Unit
            }
        registration = listener
        nsdManager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Synchronized
    fun stopAnnouncing() {
        val listener = registration ?: return
        registration = null
        try {
            nsdManager.unregisterService(listener)
        } catch (_: IllegalArgumentException) {
        }
    }

    // Blocks until the phone with this id is found, or the time is up.
    fun find(
        id: String,
        timeoutMilliseconds: Long,
    ): InetSocketAddress? {
        val found = ArrayBlockingQueue<InetSocketAddress>(1)
        val listener =
            object : NsdManager.DiscoveryListener {
                // A name that was already taken on the network comes back with a suffix.
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceName.startsWith(id)) resolve(info, found)
                }

                override fun onServiceLost(info: NsdServiceInfo) = Unit

                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = FileLogger.w(TAG, "Looking failed: $errorCode")

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }
        nsdManager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        val address =
            try {
                found.poll(timeoutMilliseconds, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                null
            }
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
        }
        return address
    }

    // The network's gateway. On a hotspot it is the phone that hosts it, where discovery is unreliable.
    // Read from the real network, never a VPN: Pocora's own VPN may take Pocora in with no gateway, and then the
    // active network has none.
    @Suppress("DEPRECATION")
    fun gateway(): InetAddress? {
        val active = connectivityManager.activeNetwork
        val networks = listOfNotNull(active) + connectivityManager.allNetworks.filter { it != active }
        return networks
            .filter {
                connectivityManager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ==
                    false
            }.firstNotNullOfOrNull { network ->
                connectivityManager
                    .getLinkProperties(network)
                    ?.routes
                    ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                    ?.gateway
            }
    }

    // Where a paired phone may be, tried in order: found by discovery, the hotspot's host at the gateway, and this
    // phone itself, for both apps on one phone. Lazy, so discovery waits only if it is reached.
    fun addresses(
        id: String,
        port: Int,
        timeoutMilliseconds: Long,
    ): Sequence<InetSocketAddress> =
        sequence {
            find(id, timeoutMilliseconds)?.let { yield(it) }
            gateway()?.let { yield(InetSocketAddress(it, port)) }
            yield(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
        }

    // The newer resolving call needs Android 14. This one works on every version the app supports.
    @Suppress("DEPRECATION")
    private fun resolve(
        info: NsdServiceInfo,
        found: ArrayBlockingQueue<InetSocketAddress>,
    ) {
        val listener =
            object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host ?: return
                    found.offer(InetSocketAddress(host, resolved.port))
                }

                override fun onResolveFailed(
                    info: NsdServiceInfo,
                    errorCode: Int,
                ) = FileLogger.w(TAG, "Resolving failed: $errorCode")
            }
        nsdManager.resolveService(info, listener)
    }
}
