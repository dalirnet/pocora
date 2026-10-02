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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.debug.FileLogger
import ir.pocora.service.AdminReceiver
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.Permissions
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Hero
import ir.pocora.ui.component.MainButton

// C3b. One step at a time: a large icon, the step, a plain reason, and the one button that grants it.
// A row of dots shows how far along the child is. A step done in Android's screen moves on by itself.
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

    val count = done.values.count { it }
    if (open == null) {
        Hero(
            color = AppColors.green,
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.setup_all_done),
        ) {
            Card {
                Text(text = stringResource(R.string.setup_all_done_text), color = palette.text, fontSize = Dimens.body)
                MainButton(text = stringResource(R.string.done), onClick = {
                    app.configStore.setupDone = true
                    onDone()
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }
    Hero(
        color = AppColors.violet,
        icon = iconOf(open),
        title = stringResource(open.title),
        subtitle = stringResource(R.string.step_of, count + 1, SetupStep.entries.size),
    ) {
        Card {
            Text(text = stringResource(open.why), color = palette.text, fontSize = Dimens.body, lineHeight = 24.sp)
            MainButton(
                text = stringResource(open.action),
                onClick = { start(open) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            for (step in SetupStep.entries) {
                val current = step == open
                Box(
                    modifier =
                        Modifier
                            .width(if (current) 22.dp else 8.dp)
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
        // The VPN is the one step Pocora cannot work without; the others can wait.
        if (done[SetupStep.VPN] == true) {
            MainButton(text = stringResource(R.string.finish_later), onClick = {
                app.configStore.setupDone = true
                onDone()
            }, quiet = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun iconOf(step: SetupStep): ImageVector =
    when (step) {
        SetupStep.VPN -> Icons.Filled.VpnKey
        SetupStep.ALWAYS_ON -> Icons.Filled.Autorenew
        SetupStep.USAGE -> Icons.Filled.BarChart
        SetupStep.NOTIFICATIONS -> Icons.Filled.Notifications
        SetupStep.ADMIN -> Icons.Filled.AdminPanelSettings
        SetupStep.BATTERY -> Icons.Filled.BatteryChargingFull
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
