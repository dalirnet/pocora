package ir.pocora.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.pocora.debug.FileLogger

// Starting the agent service, and putting it in the foreground.
object Foreground {
    private const val TAG = "Foreground"

    // Android refuses a start from the background on some versions. The next app open or boot tries again.
    fun start(
        context: Context,
        service: Class<out Service>,
    ) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, service))
        } catch (error: IllegalStateException) {
            FileLogger.w(TAG, "Could not start ${service.simpleName}", error)
        }
    }

    // Android 14 asks for a foreground type. The agent fits no named one. Its type is declared in the child's
    // manifest only, since the parent app runs nothing in the background, so lint does not see it there.
    @SuppressLint("ForegroundServiceType")
    fun enter(
        service: Service,
        id: Int,
        notification: Notification,
    ) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(service, id, notification, type)
    }
}

// Tells the agent or the open parent app when the phone joins a network, so it can announce itself again at its new address.
class NetworkWatch(
    context: Context,
    private val onJoined: () -> Unit,
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onJoined()
        }

    fun start() = connectivityManager.registerDefaultNetworkCallback(callback)

    fun stop() = connectivityManager.unregisterNetworkCallback(callback)
}
