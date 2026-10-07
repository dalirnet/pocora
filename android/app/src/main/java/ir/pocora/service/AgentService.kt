package ir.pocora.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import ir.pocora.PocoraApp

// Keeps the agent running: a foreground service with the status card as its notification.
// It also hears apps being installed or removed, the Wi-Fi changing, and Bluetooth coming on, which the listening
// for the parent's wake-up signal needs again.
class AgentService : Service() {
    companion object {
        fun start(context: Context) = Foreground.start(context, AgentService::class.java)
    }

    private val app: PocoraApp
        get() = application as PocoraApp

    private val packages =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                // An update is a removal and an install of the same app. Neither is worth noting.
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                app.agent.onPackageChanged(packageName, intent.action == Intent.ACTION_PACKAGE_ADDED)
            }
        }

    private val bluetooth =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) app.listenIfPaired()
            }
        }

    private val network by lazy {
        NetworkWatch(this) {
            app.endpoint.reannounce()
            app.agent.syncSoon()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Foreground.enter(
            this,
            AgentNotifications.STATUS_ID,
            app.agent.notifications.statusNotification(app.agent.status),
        )
        registerReceiver(
            packages,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
        )
        registerReceiver(bluetooth, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        network.start()
        app.agent.start()
        app.listenIfPaired()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        app.agent.refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        unregisterReceiver(bluetooth)
        network.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
