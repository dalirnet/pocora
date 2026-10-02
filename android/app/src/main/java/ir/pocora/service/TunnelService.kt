package ir.pocora.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import ir.pocora.PocoraApp
import ir.pocora.debug.FileLogger
import java.io.FileInputStream
import java.io.IOException
import kotlin.concurrent.thread

// The VPN, with no core. It takes in only the apps that must not have internet now, and drops their packets.
// Every other app keeps its normal connection, so Pocora never sees or slows their traffic.
class TunnelService : VpnService() {
    companion object {
        private const val TAG = "TunnelService"
        private const val ACTION_APPLY = "ir.pocora.tunnel.APPLY"
        private const val ACTION_STOP = "ir.pocora.tunnel.STOP"
        private const val ADDRESS = "10.111.0.1"
        private const val DNS = "10.111.0.2"
        private const val ADDRESS_V6 = "fd00:111::1"
        private const val PACKET_SIZE_BYTES = 32_767

        @Volatile
        var running = false
            private set

        // The apps blocked by the last call, kept for when Android starts the VPN itself, as an Always-on VPN.
        @Volatile
        private var blocked: List<String> = emptyList()

        @Volatile
        private var wanted: List<String>? = null

        // Null turns the VPN off. A list rebuilds it, if it differs from the one in place.
        fun apply(
            context: Context,
            apps: List<String>?,
        ) {
            if (apps == wanted && (apps == null) != running) return
            wanted = apps
            if (apps == null) {
                if (running) start(context, Intent(context, TunnelService::class.java).setAction(ACTION_STOP))
                return
            }
            // Without the child's consent, given once during setup, Android will not let the VPN start.
            if (prepare(context) != null) return
            blocked = apps
            start(context, Intent(context, TunnelService::class.java).setAction(ACTION_APPLY))
        }

        private fun start(
            context: Context,
            intent: Intent,
        ) {
            try {
                context.startService(intent)
            } catch (error: IllegalStateException) {
                FileLogger.w(TAG, "Could not reach the VPN", error)
            }
        }
    }

    private var tunnel: ParcelFileDescriptor? = null
    private var drain: Thread? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            close()
            stopSelf()
            return START_NOT_STICKY
        }
        // Started by Android as an Always-on VPN, or by the agent. Either way, a paired phone's agent should be running.
        if (intent?.action != ACTION_APPLY) (application as PocoraApp).startServiceIfPaired()
        establish(blocked)
        return START_STICKY
    }

    private fun establish(apps: List<String>) {
        val builder =
            Builder()
                .setSession("Pocora")
                .addAddress(ADDRESS, 32)
                .addAddress(ADDRESS_V6, 128)
                .addDnsServer(DNS)
                .setBlocking(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        var added = 0
        for (app in apps) {
            try {
                builder.addAllowedApplication(app)
                added++
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        if (added > 0) {
            builder.addRoute("0.0.0.0", 0).addRoute("::", 0)
        } else {
            // Nothing to block. The VPN stays up, so Android still counts it as on, but carries nothing.
            builder.addDisallowedApplication(packageName).addRoute(DNS, 32)
        }
        val next =
            try {
                builder.establish()
            } catch (error: RuntimeException) {
                FileLogger.e(TAG, "Could not start the VPN", error)
                null
            }
        if (next == null) {
            close()
            return
        }
        val previous = tunnel
        tunnel = next
        running = true
        startDrain(next)
        try {
            previous?.close()
        } catch (_: IOException) {
        }
        FileLogger.i(TAG, "VPN on, ${apps.size} apps without internet")
    }

    // Reads and throws away every packet, so the blocked apps' connections fail fast instead of filling a queue.
    private fun startDrain(descriptor: ParcelFileDescriptor) {
        drain =
            thread(name = "pocora-tunnel") {
                val input = FileInputStream(descriptor.fileDescriptor)
                val packet = ByteArray(PACKET_SIZE_BYTES)
                try {
                    while (input.read(packet) >= 0) Unit
                } catch (_: IOException) {
                }
            }
    }

    private fun close() {
        try {
            tunnel?.close()
        } catch (_: IOException) {
        }
        tunnel = null
        running = false
    }

    // The child turned the VPN off in Android's settings, or another VPN took its place.
    override fun onRevoke() {
        close()
        wanted = null
        stopSelf()
        (application as PocoraApp).agent.refresh()
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }
}
