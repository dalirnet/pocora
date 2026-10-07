package ir.pocora.transport

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.IntentCompat
import ir.pocora.Role
import ir.pocora.debug.FileLogger
import ir.pocora.model.Peer
import ir.pocora.protocol.WakeTag
import ir.pocora.service.WakeReceiver
import java.util.concurrent.ConcurrentHashMap

// Bluetooth carries one thing: a short signal that wakes the other phone, which then syncs over Wi-Fi as always.
// A locked phone's Wi-Fi drops the announcements discovery needs, and its timers sleep; its Bluetooth chip keeps
// listening for this one signal and wakes the app for it, at almost no cost to the battery.
// Each paired phone is called with its own tag, see WakeTag, and only a paired phone's tag is heard.
// What Android must allow for each is in WakeAccess.
class Wake(
    private val context: Context,
) {
    companion object {
        private const val TAG = "Wake"

        // Pocora's own, so the Bluetooth chip wakes the app for nothing else.
        private val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("6b1f2c4e-8d3a-4f5b-9e7c-2a1d0c3b4e5f")

        // How long a call is sent. The other phone hears it within a few seconds.
        private const val SIGNAL_MILLISECONDS = 60_000

        // How long the phone stays awake for one wake-up: long enough to find the other phone and sync.
        private const val AWAKE_MILLISECONDS = 30_000L

        // A call is handled at most this often: the chip reports a signal many times while it lasts. Counted from the
        // call handled, so a parent who keeps calling is heard again.
        private const val SAME_CALL_MILLISECONDS = 20_000L

        private const val WAIT_STEP_MILLISECONDS = 500L

        // Android may quietly lower a long scan, and drops it when Bluetooth goes off, so it is started again now and then.
        private const val LISTEN_AGAIN_MILLISECONDS = AlarmManager.INTERVAL_HALF_HOUR

        const val ACTION_HEARD = "ir.pocora.action.WAKE_HEARD"
        const val ACTION_LISTEN = "ir.pocora.action.WAKE_LISTEN"

        // Blocks until done says so, or the time is up, as for a woken phone to sync. True if it was done.
        fun waitFor(
            milliseconds: Long,
            done: () -> Boolean,
        ): Boolean {
            val end = SystemClock.elapsedRealtime() + milliseconds
            while (!done()) {
                if (SystemClock.elapsedRealtime() >= end) return false
                Thread.sleep(WAIT_STEP_MILLISECONDS)
            }
            return true
        }
    }

    // This app calls the other one's role, and hears only calls to its own.
    private val sent = if (Role.current == Role.CHILD) WakeTag.Direction.TO_PARENT else WakeTag.Direction.TO_CHILD
    private val heard = if (Role.current == Role.CHILD) WakeTag.Direction.TO_CHILD else WakeTag.Direction.TO_PARENT

    private val calls = ConcurrentHashMap<String, AdvertiseCallback>()
    private val lastHeard = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pocora:wake")
            .apply { setReferenceCounted(false) }

    // A locked phone's Wi-Fi drops the announcements discovery finds the other phone by; this keeps them coming.
    private val multicastLock =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("pocora:wake")
            .apply { setReferenceCounted(false) }

    private val releaseMulticast = Runnable { if (multicastLock.isHeld) multicastLock.release() }

    // --- Calling ---

    // Sends this pair's tag for a while. Calling the same phone again starts its call over. False when it cannot be
    // sent: Bluetooth off, or a phone that has not had its key yet.
    @SuppressLint("MissingPermission")
    fun call(
        peer: Peer,
        now: Long,
    ): Boolean {
        val key = peer.wakeKey ?: return false
        if (!WakeAccess.canCall(context)) return false
        val advertiser = Radios.bluetooth(context)?.bluetoothLeAdvertiser ?: return false
        val id = peer.id
        val tag = WakeTag.of(key, sent, now)
        stop(id)
        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(SIGNAL_MILLISECONDS)
                .build()
        // The tag alone, with no name, so the signal says nothing about the phone.
        val data =
            AdvertiseData
                .Builder()
                .addServiceData(SERVICE_UUID, tag)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()
        val callback =
            object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    calls.remove(id, this)
                    FileLogger.w(TAG, "Calling failed: $errorCode")
                }
            }
        return try {
            advertiser.startAdvertising(settings, data, callback)
            calls[id] = callback
            true
        } catch (error: SecurityException) {
            FileLogger.w(TAG, "Calling not allowed", error)
            false
        }
    }

    // The other phone answered: its call ends early.
    @SuppressLint("MissingPermission")
    fun stop(id: String) {
        val callback = calls.remove(id) ?: return
        try {
            Radios.bluetooth(context)?.bluetoothLeAdvertiser?.stopAdvertising(callback)
        } catch (error: SecurityException) {
            FileLogger.w(TAG, "Stopping a call not allowed", error)
        }
    }

    // --- Listening ---

    // Hands the listening to the Bluetooth chip, which wakes WakeReceiver when Pocora's signal is near, even with this
    // app's process gone. Started again by an alarm every half hour, so a scan Android dropped comes back.
    @SuppressLint("MissingPermission")
    fun listen(): Boolean {
        listenAgainLater()
        if (!WakeAccess.canHear(context)) return false
        val scanner = Radios.bluetooth(context)?.bluetoothLeScanner ?: return false
        val heard = heardIntent()
        // The tag changes every few minutes, so the chip matches Pocora's signal and the app checks the tag.
        val filter = ScanFilter.Builder().setServiceData(SERVICE_UUID, ByteArray(0)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        return try {
            scanner.stopScan(heard)
            val error = scanner.startScan(listOf(filter), settings, heard)
            if (error != 0) FileLogger.w(TAG, "Listening failed: $error")
            error == 0
        } catch (error: SecurityException) {
            FileLogger.w(TAG, "Listening not allowed", error)
            false
        }
    }

    private fun listenAgainLater() {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeReceiver::class.java).setAction(ACTION_LISTEN)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        alarms.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + LISTEN_AGAIN_MILLISECONDS,
            LISTEN_AGAIN_MILLISECONDS,
            pending,
        )
    }

    // Android fills in what it heard, so this one must stay mutable.
    private fun heardIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, WakeReceiver::class.java).setAction(ACTION_HEARD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    // The paired phones calling, in what the chip heard, each once per call. With any, this phone is kept awake for
    // them to be reached.
    fun callers(
        intent: Intent,
        peers: List<Peer>,
        now: Long,
    ): List<Peer> {
        val tags =
            IntentCompat
                .getParcelableArrayListExtra(intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                .orEmpty()
                .mapNotNull { it.scanRecord?.getServiceData(SERVICE_UUID) }
        val callers =
            peers.filter { peer ->
                val key = peer.wakeKey ?: return@filter false
                tags.any { WakeTag.matches(key, heard, it, now) } &&
                    now - (lastHeard[peer.id] ?: 0L) >= SAME_CALL_MILLISECONDS
            }
        callers.forEach { lastHeard[it.id] = now }
        if (callers.isNotEmpty()) keepAwake()
        return callers
    }

    // --- Awake ---

    // Keeps the phone awake, and its Wi-Fi hearing announcements, while it syncs. Both let go on their own.
    private fun keepAwake() {
        wakeLock.acquire(AWAKE_MILLISECONDS)
        if (!multicastLock.isHeld) multicastLock.acquire()
        mainHandler.removeCallbacks(releaseMulticast)
        mainHandler.postDelayed(releaseMulticast, AWAKE_MILLISECONDS)
    }
}
