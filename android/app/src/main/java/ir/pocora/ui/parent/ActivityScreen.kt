package ir.pocora.ui.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.timeline
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.common.EventList
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.Screen
import ir.pocora.ui.rememberFormat

// The second tab: what happened on the child's phone, newest first, grouped by day. The badge counts new alerts.
@Composable
fun ActivityScreen(
    model: ChildModel,
    bottom: @Composable () -> Unit,
) {
    val format = rememberFormat()
    val events = model.snapshot?.events?.timeline() ?: emptyList()
    val unseen = remember(model.child.id) { model.unseenAlerts }
    LaunchedEffect(model.child.id) { model.markAlertsSeen() }

    Screen(
        title = stringResource(R.string.alerts_of, model.child.name),
        trailing = { if (unseen > 0) Chip(stringResource(R.string.new_alerts, format.number(unseen))) },
        bottom = bottom,
        centered = events.isEmpty(),
    ) {
        if (events.isEmpty()) {
            EmptyState(
                AppIcons.CheckCircle,
                AppColors.green,
                stringResource(R.string.all_quiet),
                stringResource(R.string.no_alerts),
            )
        }
        EventList(events)
    }
}
