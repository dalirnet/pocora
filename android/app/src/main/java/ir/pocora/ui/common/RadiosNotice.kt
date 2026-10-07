package ir.pocora.ui.common

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.debug.FileLogger
import ir.pocora.transport.Radios
import ir.pocora.transport.WakeAccess
import ir.pocora.ui.AppIcons

// Wi-Fi and Bluetooth, which the two phones need to reach each other: one line on Home while either is off, with the
// one tap that turns it on, Wi-Fi first as it carries the data. Gone by itself once both are on.
// The parent app also asks here for nearby devices, or location before Android 12; the child app asks in its setup.
// With homeWifi, the parent's phone called and found this one on another network.
@Composable
fun RadiosNotice(
    askPermission: Boolean = false,
    homeWifi: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocoraApp
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    DisposableEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    checks++
                }
            }
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            },
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val wifiOff = remember(checks) { !Radios.isWifiOn(context) }
    // A phone with no Bluetooth at all is never asked for it.
    val bluetoothOff = remember(checks) { Radios.bluetooth(context) != null && !Radios.isBluetoothOn(context) }
    val nearbyAllowed = remember(checks) { WakeAccess.isAllowed(context) }
    // Before Android 12, hearing the other phone also needs location on.
    val locationOff = remember(checks) { nearbyAllowed && !WakeAccess.isLocationReady(context) }
    // Back from Android's screen or prompt: checked again, and listening starts if Bluetooth is now ready.
    val changed: (Any) -> Unit = {
        checks++
        app.listenIfPaired()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult(), changed)
    val askNearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), changed)

    fun open(intent: Intent) {
        try {
            launcher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            FileLogger.w("RadiosNotice", "No screen for ${intent.action}", error)
        }
    }

    val turnOn = stringResource(R.string.turn_on)
    when {
        wifiOff && bluetoothOff -> {
            HomeNotice(AppIcons.WifiOff, stringResource(R.string.radio_both_off), turnOn) {
                open(Radios.wifiIntent(context))
            }
        }

        wifiOff -> {
            HomeNotice(AppIcons.WifiOff, stringResource(R.string.radio_wifi_off), turnOn) {
                open(Radios.wifiIntent(context))
            }
        }

        bluetoothOff -> {
            HomeNotice(AppIcons.Bluetooth, stringResource(R.string.radio_bluetooth_off), turnOn) {
                open(Radios.bluetoothIntent(context))
            }
        }

        askPermission && !nearbyAllowed -> {
            HomeNotice(
                AppIcons.Bluetooth,
                stringResource(
                    if (WakeAccess.needsLocation) R.string.radio_location_needed else R.string.radio_nearby_needed,
                ),
                stringResource(R.string.allow),
            ) {
                askNearby.launch(WakeAccess.nextPrompt(context))
            }
        }

        locationOff -> {
            HomeNotice(AppIcons.Location, stringResource(R.string.radio_location_off), turnOn) {
                open(Radios.locationIntent())
            }
        }

        homeWifi -> {
            HomeNotice(AppIcons.Wifi, stringResource(R.string.radio_home_wifi), stringResource(R.string.change)) {
                open(Radios.wifiIntent(context))
            }
        }
    }
}
