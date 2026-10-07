package ir.pocora.service

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.Role
import ir.pocora.protocol.PairingCode
import ir.pocora.transport.Device
import ir.pocora.transport.PairingClient
import ir.pocora.transport.Wake
import ir.pocora.ui.SamePhone
import kotlin.concurrent.thread

// The broadcast receivers.

// Starts the agent after the phone restarts, or after Pocora is updated, once the phone is paired.
// In both apps, it also listens again for the other phone's Bluetooth signal, which a restart or an update ends.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as PocoraApp
        app.startServiceIfPaired()
        app.listenIfPaired()
    }
}

// The other phone's Bluetooth signal was heard, or it is time to listen again. See Wake.
// The child's agent is already running and takes it from here. The parent app may have been closed, so it reaches
// the child in the time Android gives a receiver.
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as PocoraApp
        when (intent.action) {
            Wake.ACTION_LISTEN -> {
                app.listenIfPaired()
            }

            Wake.ACTION_HEARD -> {
                if (Role.current == Role.CHILD) {
                    app.agent.onCalled(intent)
                    return
                }
                val pending = goAsync()
                thread(name = "pocora-wake") {
                    try {
                        app.parent.onCalled(intent)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

// The parent app on this same phone handed over its pairing code and a one-time token. The child app pairs
// here, out of sight, and the parent app accepts on its own: nobody needs to switch apps. See SamePhone.
class PairHereReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as PocoraApp
        val code = intent.getStringExtra(SamePhone.EXTRA_PAIRING_CODE)?.let(PairingCode::parse) ?: return
        val token = intent.getStringExtra(SamePhone.EXTRA_TOKEN) ?: return
        val pending = goAsync()
        PairingClient(
            identity = { app.identity },
            peerStore = app.peerStore,
            discovery = app.discovery,
            code = code,
            version = Device.appVersion(app),
            onAccepted = app.agent::paired,
            token = token,
        ) { pending.finish() }.start()
    }
}

// The parent app on this same phone, asking whether this phone is paired with it and paused, or pausing and resuming it.
// The answer carries this phone's id, so the parent app shows its switch only for the child really here. See SamePhone.
class PauseHereReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as PocoraApp
        if (app.peerStore.all().isEmpty()) return
        val pending = goAsync()
        thread(name = "pocora-pause-here") {
            if (intent.hasExtra(SamePhone.EXTRA_PAUSED)) {
                app.agent.setPaused(intent.getBooleanExtra(SamePhone.EXTRA_PAUSED, false))
            }
            pending.resultCode = Activity.RESULT_OK
            pending.setResultExtras(
                Bundle().apply {
                    putString(SamePhone.EXTRA_CHILD_ID, app.identity.id)
                    putBoolean(SamePhone.EXTRA_PAUSED, app.agent.paused)
                },
            )
            pending.finish()
        }
    }
}

// Device admin, asked for only so Pocora cannot be removed without first turning this off, which the parent sees.
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(
        context: Context,
        intent: Intent,
    ): CharSequence {
        val app = context.applicationContext as PocoraApp
        return app.localized.getString(R.string.parent_sees_admin_off)
    }

    override fun onDisabled(
        context: Context,
        intent: Intent,
    ) {
        (context.applicationContext as PocoraApp).agent.onDeviceAdminOff()
    }
}
