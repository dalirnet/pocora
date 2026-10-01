package ir.pocora.ui.child

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.protocol.PairingCode
import ir.pocora.transport.PairingClient
import ir.pocora.transport.PairingResult
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Hero
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.WhiteIcon

// Connecting to the parent's phone: welcome, scan, and waiting for the parent.

// The child's welcome. Also where the child lands when connecting did not work, with the reason in its own card.
@Composable
fun WelcomeScreen(
    failure: PairingResult?,
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    Hero(
        color = AppColors.violet,
        icon = Icons.Filled.QrCodeScanner,
        title = stringResource(R.string.connect_to_parent),
        around = listOf(Icons.Filled.PhoneAndroid, Icons.Filled.Wifi, Icons.Filled.Link),
        trailing = { WhiteIcon(Icons.Filled.Settings, stringResource(R.string.settings), onSettings) },
    ) {
        if (failure != null) {
            val reason =
                if (failure ==
                    PairingResult.REJECTED
                ) {
                    R.string.parent_did_not_accept
                } else {
                    R.string.could_not_reach_parent
                }
            Card { Text(text = stringResource(reason), color = palette.alert, fontSize = Dimens.body) }
        }
        Card {
            Text(
                text = stringResource(R.string.connect_to_parent_text),
                color = palette.text,
                fontSize = Dimens.body,
                lineHeight = 24.sp,
            )
            MainButton(
                text = stringResource(R.string.scan_the_code),
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// The camera, between the welcome and waiting. Anything that is not a Pocora pairing code is passed over.
@Composable
fun ScanScreen(
    onCode: (PairingCode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Screen(title = stringResource(R.string.scan_the_code), onBack = onBack) {
        if (granted) {
            var found by remember { mutableStateOf(false) }
            CameraPreview { text ->
                val code = PairingCode.parse(text)
                if (code != null && !found) {
                    found = true
                    onCode(code)
                }
            }
            Text(text = stringResource(R.string.connect_to_parent_text), color = palette.muted, fontSize = Dimens.body)
        } else {
            Card {
                Text(text = stringResource(R.string.camera_needed), color = palette.text, fontSize = Dimens.body)
                MainButton(
                    text = stringResource(R.string.allow_camera),
                    onClick = { permission.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

// A square window onto the camera. The frame is taller than the window, so it is centred and cropped, not squeezed.
@Composable
private fun CameraPreview(onText: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnText by rememberUpdatedState(onText)
    val scanner = remember { CodeScanner(context) { currentOnText(it) } }
    DisposableEffect(scanner) { onDispose { scanner.stop() } }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(
                    1f,
                ).clip(RoundedCornerShape(20.dp))
                .background(LocalPalette.current.limited),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    surfaceTextureListener =
                        object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) = scanner.start(texture)

                            override fun onSurfaceTextureSizeChanged(
                                texture: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                }
            },
            // Held upright the phone shows the sensor's frame turned, so its width and height swap.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .aspectRatio(scanner.frameSize.height.toFloat() / scanner.frameSize.width),
        )
    }
}

// Waiting: asks the parent's phone to pair for as long as this screen is open. Leaving it takes the request back.
@Composable
fun WaitingScreen(
    code: PairingCode?,
    onResult: (PairingResult) -> Unit,
    onCancel: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as PocoraApp
    val palette = LocalPalette.current
    val currentOnResult by rememberUpdatedState(onResult)
    DisposableEffect(code) {
        val client =
            code?.let {
                PairingClient({
                    app.identity
                }, app.peerStore, app.discovery, it, app.agent::paired) { result -> currentOnResult(result) }
            }
        if (client == null) currentOnResult(PairingResult.NOT_REACHABLE) else client.start()
        onDispose { client?.cancel() }
    }

    Hero(color = AppColors.violet, icon = Icons.Filled.HourglassTop, title = stringResource(R.string.code_scanned)) {
        Card {
            Text(
                text = stringResource(R.string.waiting_for_parent),
                color = palette.text,
                fontSize = Dimens.body,
                lineHeight = 24.sp,
            )
            MainButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
