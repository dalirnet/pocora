package ir.pocora.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.R
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.Labels
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.localDateOf
import ir.pocora.ui.rememberFormat

// The alerts of a child, newest first, grouped by day, each with the time it happened.
// The parent sees them on the Activity tab, and the child sees the very same list on its own.
@Composable
fun AlertList(alerts: List<Event>) {
    val format = rememberFormat()
    for ((day, events) in alerts.groupBy { localDateOf(it.start) }) {
        SectionTitle(format.dayName(day))
        Card {
            for (event in events) AlertRow(format, event)
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
            EventKind.POCORA_STOPPED -> AppIcons.WarningAmber to AppColors.orange
            EventKind.VPN_OFF -> AppIcons.PowerSettingsNew to AppColors.orange
            EventKind.OTHER_VPN, EventKind.VPN_APP_INSTALLED -> AppIcons.VpnKey to AppColors.red
            EventKind.DEVICE_ADMIN_OFF -> AppIcons.AdminPanelSettings to AppColors.red
            EventKind.WATCHED_APP -> AppIcons.Visibility to AppColors.violet
            EventKind.REBOOT -> AppIcons.RestartAlt to AppColors.slate
            EventKind.APP_INSTALLED -> AppIcons.Download to AppColors.green
            EventKind.APP_REMOVED -> AppIcons.Delete to AppColors.slate
            EventKind.REQUEST_IGNORED -> AppIcons.HighlightOff to AppColors.slate
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
