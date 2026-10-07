package ir.pocora.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

// Wi-Fi and Bluetooth as this phone has them now, and Android's own screens that turn them on.
// Android lets no ordinary app turn Wi-Fi on, and from Android 13 not Bluetooth either: the person taps once.
object Radios {
    // On Wi-Fi, or hosting a hotspot, which is how two phones meet where the router keeps devices apart.
    fun isWifiOn(context: Context): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifi.isWifiEnabled || isHotspotOn(wifi)
    }

    // Android has no public way to ask. Where the hidden one is gone, a hotspot reads as Wi-Fi off.
    private fun isHotspotOn(wifi: WifiManager): Boolean =
        try {
            WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) == true
        } catch (_: ReflectiveOperationException) {
            false
        }

    fun isAirplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    fun bluetooth(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothOn(context: Context): Boolean = bluetooth(context)?.isEnabled == true

    fun isLocationOn(context: Context): Boolean =
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled

    fun locationIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    // The small panel over the app where it can be, the settings page on Android 9. With airplane mode on, the panel
    // that has both switches.
    fun wifiIntent(context: Context): Intent =
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> Intent(Settings.ACTION_WIFI_SETTINGS)
            isAirplaneOn(context) -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            else -> Intent(Settings.Panel.ACTION_WIFI)
        }

    // Android's own "Turn on Bluetooth?" dialog, one tap, where it may be shown; otherwise the settings page.
    fun bluetoothIntent(context: Context): Intent =
        if (WakeAccess.canAskForBluetooth(context)) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
}
