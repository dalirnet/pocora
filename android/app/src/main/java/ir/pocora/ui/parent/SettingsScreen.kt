package ir.pocora.ui.parent

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Look
import ir.pocora.model.Peer
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.AppVersion
import ir.pocora.ui.common.LanguageAndLook
import ir.pocora.ui.common.Permissions
import ir.pocora.ui.component.Avatar
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.CardTitle
import ir.pocora.ui.component.IconAction
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.component.SmallButton
import ir.pocora.ui.component.SwitchRow

// Children, each with its code again and removing, notifications, language and theme, and the version at the foot.
@Composable
fun SettingsScreen(
    children: List<Peer>,
    onReconnect: (Peer) -> Unit,
    onForget: (Peer) -> Unit,
    onBack: (() -> Unit)?,
    onLanguage: (String) -> Unit,
    onLook: (Look) -> Unit,
    bottom: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val config = (context.applicationContext as PocoraApp).configStore
    var removing by remember { mutableStateOf<Peer?>(null) }
    var home by remember { mutableStateOf(config.notifyHome) }
    var alerts by remember { mutableStateOf(config.notifyAlerts) }
    var suggestions by remember { mutableStateOf(config.notifySuggestions) }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val canNotify = remember(checks) { Permissions.canNotify(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(title = stringResource(R.string.settings), onBack = onBack, bottom = bottom) {
            if (children.isNotEmpty()) {
                SectionTitle(stringResource(R.string.children))
                Card {
                    for (child in children) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.row),
                        ) {
                            Avatar(child.name, 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                CardTitle(child.name)
                                // A phone's name is Latin; the Persian font would turn its digits Persian.
                                Text(
                                    text = child.deviceName,
                                    color = palette.muted,
                                    fontSize = Dimens.label,
                                    fontFamily = FontFamily.Default,
                                )
                            }
                            IconAction(
                                AppIcons.QrCodeScanner,
                                stringResource(R.string.connect_again),
                            ) { onReconnect(child) }
                            SmallButton(text = stringResource(R.string.remove), onClick = { removing = child })
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.notifications))
            Card {
                // Android's permission as a switch like the rest. On asks for it; off, which only Android's own
                // settings can do, opens them.
                SwitchRow(stringResource(R.string.show_notifications), canNotify, { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Permissions.notificationSettingsIntent(context))
                    }
                }, icon = AppIcons.Notifications, iconColor = AppColors.pink)
                SwitchRow(stringResource(R.string.notify_when_connects), home, {
                    home = it
                    config.notifyHome = it
                }, enabled = canNotify, icon = AppIcons.Wifi, iconColor = AppColors.teal)
                SwitchRow(stringResource(R.string.alerts), alerts, {
                    alerts = it
                    config.notifyAlerts = it
                }, enabled = canNotify, icon = AppIcons.WarningAmber, iconColor = AppColors.orange)
                SwitchRow(stringResource(R.string.schedule_suggestions), suggestions, {
                    suggestions = it
                    config.notifySuggestions = it
                }, enabled = canNotify, icon = AppIcons.CalendarMonth, iconColor = AppColors.violet)
            }

            LanguageAndLook(onLanguage, onLook)
            AppVersion()
        }
        removing?.let { child ->
            Sheet(
                onDismiss = { removing = null },
                title = stringResource(R.string.remove_child, child.name),
                icon = AppIcons.PersonRemove,
                color = AppColors.orange,
                subtitle = stringResource(R.string.remove_child_text, child.name),
            ) {
                ButtonPair(stringResource(R.string.cancel), { removing = null }, stringResource(R.string.remove), {
                    removing = null
                    onForget(child)
                }, danger = true)
            }
        }
    }
}
