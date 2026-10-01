package ir.pocora.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import ir.pocora.PocoraApp

// Keeps the agent running: a foreground service with the status card as its notification.
// It also hears apps being installed or removed, and the Wi-Fi changing.
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
        network.start()
        app.agent.start()
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
        network.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
