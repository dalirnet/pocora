package ir.pocora.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.protocol.PairingCode
import ir.pocora.transport.PairingClient
import ir.pocora.ui.SamePhone

// The broadcast receivers.

// Starts the agent after the phone restarts, or after Pocora is updated, once the phone is paired.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        (context.applicationContext as PocoraApp).startServiceIfPaired()
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
            onAccepted = app.agent::paired,
            token = token,
        ) { pending.finish() }.start()
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
