package ir.pocora.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.Labels
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.localDateOf
import ir.pocora.ui.rememberFormat

// The second tab: the child's alerts, newest first, grouped by day. Each keeps the time it happened.
@Composable
fun ActivityScreen(
    model: ChildModel,
    bottom: @Composable () -> Unit,
) {
    val format = rememberFormat()
    val parent = (LocalContext.current.applicationContext as PocoraApp).parent
    val alerts = model.snapshot?.let { parent.alertsOf(it) } ?: emptyList()
    val unseen = remember(model.child.id) { model.unseenAlerts }
    LaunchedEffect(model.child.id) { model.markAlertsSeen() }

    Screen(
        title = stringResource(R.string.alerts_of, model.child.name),
        trailing = { if (unseen > 0) Chip(stringResource(R.string.new_alerts, format.number(unseen))) },
        bottom = bottom,
        centered = alerts.isEmpty(),
    ) {
        if (alerts.isEmpty()) {
            EmptyState(
                Icons.Filled.CheckCircle,
                AppColors.green,
                stringResource(R.string.all_quiet),
                stringResource(R.string.no_alerts),
            )
        }
        for ((day, events) in alerts.groupBy { localDateOf(it.start) }) {
            SectionTitle(format.dayName(day))
            Card {
                for (event in events) AlertRow(format, event)
            }
        }
    }
}

@Composable
private fun AlertRow(
    format: Format,
    event: Event,
) {
    val palette = LocalPalette.current
    val (icon, color) =
        when (event.kind) {
            EventKind.POCORA_STOPPED -> Icons.Filled.WarningAmber to AppColors.orange
            EventKind.VPN_OFF -> Icons.Filled.PowerSettingsNew to AppColors.orange
            EventKind.OTHER_VPN, EventKind.VPN_APP_INSTALLED -> Icons.Filled.VpnKey to AppColors.red
            EventKind.DEVICE_ADMIN_OFF -> Icons.Filled.AdminPanelSettings to AppColors.red
            EventKind.WATCHED_APP -> Icons.Filled.Visibility to AppColors.violet
            EventKind.REBOOT -> Icons.Filled.RestartAlt to AppColors.slate
            EventKind.APP_INSTALLED -> Icons.Filled.Download to AppColors.green
            EventKind.APP_REMOVED -> Icons.Filled.Delete to AppColors.slate
            EventKind.REQUEST_IGNORED -> Icons.Filled.HighlightOff to AppColors.slate
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
        IconTile(icon, color, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Labels.event(event.kind)),
                color = palette.text,
                fontSize = Dimens.body,
                fontWeight = FontWeight.Bold,
            )
            val detail =
                when {
                    event.appName != null || event.app != null -> {
                        event.appName ?: event.app
                    }

                    event.end != null && event.kind == EventKind.VPN_OFF -> {
                        stringResource(
                            R.string.back_on_at,
                            format.time(event.end),
                        )
                    }

                    event.end != null -> {
                        stringResource(R.string.until_capital, format.time(event.end))
                    }

                    else -> {
                        null
                    }
                }
            if (detail != null) Text(text = detail, color = palette.muted, fontSize = 14.sp)
        }
        Text(text = format.time(event.start), color = palette.muted, fontSize = Dimens.caption)
    }
}
