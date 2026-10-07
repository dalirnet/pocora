package ir.pocora.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// What Android must allow for the Bluetooth wake-up, see Wake. Sending needs Bluetooth on and, from Android 12, the
// nearby devices permission. Hearing needs that permission too. Before Android 12, Android hands Bluetooth to an app in
// the background only with location access, all the time from Android 10, and with location switched on. Pocora never
// reads the location.
object WakeAccess {
    val needsLocation: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    // Android 12's nearby devices permission is one prompt for all three.
    private val permissions: List<String>
        get() =
            when {
                !needsLocation -> {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }

                else -> {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

    fun isAllowed(context: Context): Boolean = permissions.all { granted(context, it) }

    // The next prompt to show, empty once all is allowed. Android 11 asks for location all the time on a page of its
    // own, and only once location is allowed at all.
    fun nextPrompt(context: Context): Array<String> {
        val missing = permissions.filterNot { granted(context, it) }
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && fine in missing) return arrayOf(fine)
        return missing.toTypedArray()
    }

    fun isLocationReady(context: Context): Boolean = !needsLocation || Radios.isLocationOn(context)

    // What keeps this phone from hearing the other one. Each is an episode the parent is told about.
    fun bluetoothBlocked(context: Context): Boolean =
        !Radios.isBluetoothOn(context) || (!needsLocation && !isAllowed(context))

    fun locationBlocked(context: Context): Boolean =
        needsLocation && (!isAllowed(context) || !Radios.isLocationOn(context))

    fun canCall(context: Context): Boolean = Radios.isBluetoothOn(context) && (needsLocation || isAllowed(context))

    fun canHear(context: Context): Boolean = !bluetoothBlocked(context) && !locationBlocked(context)

    // Android's own "Turn on Bluetooth?" dialog, which from Android 12 needs the nearby devices permission.
    fun canAskForBluetooth(context: Context): Boolean =
        needsLocation || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    private fun granted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
