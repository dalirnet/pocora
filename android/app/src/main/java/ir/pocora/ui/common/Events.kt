package ir.pocora.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.R
import ir.pocora.model.Event
import ir.pocora.model.EventKind
import ir.pocora.model.localDateOf
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.Format
import ir.pocora.ui.Labels
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.SectionTitle
import ir.pocora.ui.rememberFormat

// What happened on a child's phone, newest first, grouped by day, each with the time it happened.
// The parent sees it on the Activity tab, and the child sees the very same list on its own. Alerts are in bold.
@Composable
fun EventList(events: List<Event>) {
    val format = rememberFormat()
    for ((day, ofDay) in events.groupBy { localDateOf(it.start) }) {
        SectionTitle(format.dayName(day))
        Card {
            for (event in ofDay) EventRow(format, event)
        }
    }
}

@Composable
private fun EventRow(
    format: Format,
    event: Event,
) {
    val palette = LocalPalette.current
    val (icon, color) = look(event.kind)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.row)) {
        IconTile(icon, color, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Labels.event(event.kind)),
                color = palette.text,
                fontSize = Dimens.body,
                fontWeight = if (event.kind.alert) FontWeight.Bold else FontWeight.Normal,
            )
            detail(format, event)?.let { Text(text = it, color = palette.muted, fontSize = 14.sp) }
        }
        Text(text = format.time(event.start), color = palette.muted, fontSize = Dimens.caption)
    }
}

private fun look(kind: EventKind): Pair<ImageVector, Color> =
    when (kind) {
        EventKind.POCORA_STOPPED -> AppIcons.WarningAmber to AppColors.orange
        EventKind.VPN_OFF -> AppIcons.PowerSettingsNew to AppColors.orange
        EventKind.OTHER_VPN, EventKind.VPN_APP_INSTALLED -> AppIcons.VpnKey to AppColors.red
        EventKind.DEVICE_ADMIN_OFF -> AppIcons.AdminPanelSettings to AppColors.red
        EventKind.WATCHED_APP -> AppIcons.Visibility to AppColors.violet
        EventKind.REBOOT -> AppIcons.RestartAlt to AppColors.slate
        EventKind.APP_INSTALLED -> AppIcons.Download to AppColors.green
        EventKind.APP_REMOVED -> AppIcons.Delete to AppColors.slate
        EventKind.REQUEST_IGNORED -> AppIcons.HighlightOff to AppColors.slate
        EventKind.PAIRED -> AppIcons.PhonelinkRing to AppColors.green
        EventKind.RULES_CHANGED -> AppIcons.Tune to AppColors.violet
        EventKind.NO_CONTACT -> AppIcons.WifiOff to AppColors.slate
        EventKind.QUOTA_USED -> AppIcons.DataUsage to AppColors.orange
    }

// The line under the title: the app, what changed, or when it ended.
@Composable
private fun detail(
    format: Format,
    event: Event,
): String? {
    val end = event.end
    return when {
        event.appName != null || event.app != null -> {
            event.appName ?: event.app
        }

        event.parts.isNotEmpty() -> {
            val separator = stringResource(R.string.list_separator)
            event.parts.map { stringResource(Labels.part(it)) }.joinToString(separator)
        }

        end == null -> {
            null
        }

        event.kind == EventKind.NO_CONTACT -> {
            stringResource(R.string.reconnected_at, format.time(end))
        }

        event.kind == EventKind.VPN_OFF -> {
            stringResource(R.string.back_on_at, format.time(end))
        }

        else -> {
            stringResource(R.string.until_capital, format.time(end))
        }
    }
}
