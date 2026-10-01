package ir.pocora.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import ir.pocora.PocoraApp
import ir.pocora.debug.FileLogger

// Keeps the parent's phone listening for the children's syncs, and checks the calendar once an hour.
// If Android stops it, nothing is lost: the app reads a fresh snapshot the next time it is opened near the child.
class ListenerService : Service() {
    companion object {
        private const val TAG = "ListenerService"
        private const val CALENDAR_MILLISECONDS = 60 * 60_000L

        fun start(context: Context) = Foreground.start(context, ListenerService::class.java)
    }

    private val app: PocoraApp
        get() = application as PocoraApp

    private val thread = HandlerThread("pocora-listener")
    private lateinit var handler: Handler

    private val calendar =
        object : Runnable {
            override fun run() {
                try {
                    app.parent.checkCalendar()
                } catch (error: RuntimeException) {
                    FileLogger.e(TAG, "Calendar check failed", error)
                }
                handler.postDelayed(this, CALENDAR_MILLISECONDS)
            }
        }

    private val network by lazy { NetworkWatch(this) { app.endpoint.reannounce() } }

    override fun onCreate() {
        super.onCreate()
        Foreground.enter(this, ParentNotifications.LISTENER_ID, app.parent.notifications.listener())
        app.parent.start()
        thread.start()
        handler = Handler(thread.looper)
        handler.post(calendar)
        network.start()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onDestroy() {
        network.stop()
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
