package ir.pocora.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.pocora.PocoraApp
import ir.pocora.R

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
