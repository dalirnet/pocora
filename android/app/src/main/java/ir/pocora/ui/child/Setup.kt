package ir.pocora.ui.child

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.debug.FileLogger
import ir.pocora.service.AdminReceiver
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.Permissions
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.PointRow
import ir.pocora.ui.component.Screen

// C3b. One step at a time, as the parent's first screen: the step and a plain reason, every step in a card,
// and the one button that grants it at the bottom. A step done in Android's screen moves on by itself.
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PocoraApp
    val palette = LocalPalette.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        app.agent.refresh()
        onPauseOrDispose { }
    }
    val done = remember(checks) { SetupStep.entries.associateWith { it.isDone(context) } }
    val open = SetupStep.entries.firstOrNull { done[it] != true }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checks++
            app.agent.refresh()
        }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }

    fun start(step: SetupStep) {
        if (step == SetupStep.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (step == SetupStep.ALWAYS_ON) app.configStore.alwaysOnOpened = true
        if (step == SetupStep.BATTERY) app.configStore.batteryOpened = true
        val intent = step.intent(context) ?: return
        try {
            launcher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            FileLogger.w("SetupScreen", "No screen for $step", error)
            checks++
        }
    }

    val finish = {
        app.configStore.setupDone = true
        onDone()
    }
    Screen(
        title = stringResource(R.string.setup_title),
        trailing = { Progress(done, open) },
        bottom = {
            BottomAction {
                if (open == null) {
                    MainButton(
                        text = stringResource(R.string.done),
                        onClick = finish,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MainButton(
                        text = stringResource(open.action),
                        onClick = { start(open) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The VPN is the one step Pocora cannot work without; the others can wait.
                    if (done[SetupStep.VPN] == true) {
                        MainButton(
                            text = stringResource(R.string.finish_later),
                            onClick = finish,
                            quiet = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) {
        if (open == null) {
            EmptyState(
                icon = AppIcons.CheckCircle,
                color = AppColors.green,
                title = stringResource(R.string.setup_all_done),
                text = stringResource(R.string.setup_all_done_text),
            )
        } else {
            EmptyState(
                icon = iconOf(open),
                color = palette.brand,
                title = stringResource(open.title),
                text = stringResource(open.why),
            )
        }
        // Every step at a glance: done in green with a tick, this one in the brand colour, the rest waiting in grey.
        Card {
            for (step in SetupStep.entries) {
                PointRow(
                    icon = if (done[step] == true) AppIcons.Check else iconOf(step),
                    color =
                        when {
                            done[step] == true -> AppColors.green
                            step == open -> palette.brand
                            else -> AppColors.grey
                        },
                    title = stringResource(step.title),
                )
            }
        }
    }
}

// How far along, in the top bar: a dot per step, green when done, the current one long and in the brand colour.
@Composable
private fun Progress(
    done: Map<SetupStep, Boolean>,
    open: SetupStep?,
) {
    val palette = LocalPalette.current
    // The top bar's edge is 12dp and the content's 20dp; the dots line up with the content.
    Row(
        modifier = Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in SetupStep.entries) {
            val current = step == open
            Box(
                modifier =
                    Modifier
                        .width(if (current) 20.dp else 8.dp)
                        .height(8.dp)
                        .background(
                            when {
                                current -> palette.brand
                                done[step] == true -> AppColors.green
                                else -> palette.limited
                            },
                            RoundedCornerShape(4.dp),
                        ),
            )
        }
    }
}

private fun iconOf(step: SetupStep): ImageVector =
    when (step) {
        SetupStep.VPN -> AppIcons.VpnKey
        SetupStep.ALWAYS_ON -> AppIcons.Autorenew
        SetupStep.USAGE -> AppIcons.BarChart
        SetupStep.NOTIFICATIONS -> AppIcons.Notifications
        SetupStep.ADMIN -> AppIcons.AdminPanelSettings
        SetupStep.BATTERY -> AppIcons.BatteryChargingFull
    }

// The six setup steps, in order. Each knows whether it is done and which Android screen grants it.
enum class SetupStep(
    val title: Int,
    val why: Int,
    val action: Int,
) {
    VPN(R.string.step_vpn, R.string.step_vpn_why, R.string.allow),
    ALWAYS_ON(R.string.step_always_on, R.string.step_always_on_why, R.string.step_always_on_action),
    USAGE(R.string.step_usage, R.string.step_usage_why, R.string.step_usage_action),
    NOTIFICATIONS(R.string.step_notifications, R.string.step_notifications_why, R.string.allow),
    ADMIN(R.string.step_admin, R.string.step_admin_why, R.string.step_admin_action),
    BATTERY(R.string.step_battery, R.string.step_battery_why, R.string.allow),
    ;

    fun isDone(context: Context): Boolean {
        val app = context.applicationContext as PocoraApp
        return when (this) {
            VPN -> {
                VpnService.prepare(context) == null
            }

            ALWAYS_ON -> {
                app.configStore.alwaysOnOpened
            }

            USAGE -> {
                app.agent.usage.hasAccess()
            }

            NOTIFICATIONS -> {
                Permissions.canNotify(context)
            }

            ADMIN -> {
                (
                    context.getSystemService(
                        Context.DEVICE_POLICY_SERVICE,
                    ) as DevicePolicyManager
                ).isAdminActive(admin(context))
            }

            BATTERY -> {
                Permissions.isBatteryExempt(context) ||
                    (Permissions.hasOwnBatterySettings() && app.configStore.batteryOpened)
            }
        }
    }

    // The screen that grants the step. Null for notifications, which use the permission prompt, and the VPN, which uses its own.
    fun intent(context: Context): Intent? =
        when (this) {
            VPN -> {
                VpnService.prepare(context)
            }

            ALWAYS_ON -> {
                Intent(Settings.ACTION_VPN_SETTINGS)
            }

            USAGE -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }

            NOTIFICATIONS -> {
                Permissions.notificationSettingsIntent(context)
            }

            ADMIN -> {
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.step_admin_why))
            }

            BATTERY -> {
                Permissions.batteryExemptionIntent(context)
            }
        }

    companion object {
        fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun allDone(context: Context): Boolean = entries.all { it.isDone(context) }
    }
}
