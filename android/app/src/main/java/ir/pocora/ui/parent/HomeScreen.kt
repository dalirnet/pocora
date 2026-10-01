package ir.pocora.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.EventKind
import ir.pocora.model.Peer
import ir.pocora.model.Schedule
import ir.pocora.model.Seasons
import ir.pocora.model.Snapshot
import ir.pocora.parent.Parent
import ir.pocora.ui.AppColors
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.common.LayeredHome
import ir.pocora.ui.common.LocalSheetSpace
import ir.pocora.ui.common.ScreenTimeSection
import ir.pocora.ui.common.StatusPanel
import ir.pocora.ui.common.Tile
import ir.pocora.ui.common.TileGrid
import ir.pocora.ui.common.internetSentence
import ir.pocora.ui.component.ActionButton
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.LoadingCards
import ir.pocora.ui.rememberFormat
import ir.pocora.ui.text
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// The child on top, how they are now, the features as tiles, a suggestion when one is due, and today's screen time.
@Composable
fun HomeScreen(
    model: ChildModel,
    children: List<Peer>,
    online: (String) -> Boolean,
    tick: Int,
    onChooseChild: (String) -> Unit,
    onAddChild: () -> Unit,
    go: (Route) -> Unit,
    bottom: @Composable () -> Unit,
) {
    val parent = (LocalContext.current.applicationContext as PocoraApp).parent
    val snapshot = model.snapshot
    val id = model.child.id
    LaunchedEffect(id) { model.refresh() }
    LayeredHome(
        top = {
            ChildRow(children, id, online, onChooseChild, onAddChild)
            Status(parent, model, snapshot, tick)
            TileGrid(
                listOf(
                    Tile(
                        Icons.Filled.AccessTime,
                        AppColors.violet,
                        stringResource(R.string.tile_times),
                        snapshot != null,
                    ) { go(Route.Times(id)) },
                    Tile(
                        Icons.Filled.Apps,
                        AppColors.blue,
                        stringResource(R.string.tab_apps),
                        snapshot != null,
                    ) { go(Route.Apps(id)) },
                    Tile(
                        Icons.Filled.DataUsage,
                        AppColors.green,
                        stringResource(R.string.card_data),
                        snapshot != null,
                    ) {
                        go(Route.Data(id))
                    },
                ),
            )
        },
        sheet = {
            when {
                snapshot != null -> {
                    Suggestion(parent, model, snapshot, go)
                    ScreenTimeSection(parent.presets, snapshot) { go(Route.Usage(id)) }
                }

                model.loading -> {
                    LoadingCards(1)
                }

                else -> {
                    // Centred in the whole sheet, which is empty but for it.
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = LocalSheetSpace.current),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Filled.CloudOff,
                            color = LocalPalette.current.brand,
                            title = stringResource(R.string.empty_nothing_title, model.child.name),
                            text = stringResource(R.string.empty_nothing_text),
                            action = stringResource(R.string.try_again),
                            onAction = model::refresh,
                        )
                    }
                }
            }
        },
        bottom = bottom,
        overlay = { ChildSheets(model) },
    )
}

// How the child is now: one sentence, the schedule's name, and today's internet times.
@Composable
private fun Status(
    parent: Parent,
    model: ChildModel,
    snapshot: Snapshot?,
    tick: Int,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val schedule = remember { Schedule(parent.presets) }
    if (snapshot == null) {
        StatusPanel(
            Icons.Filled.CloudOff,
            palette.muted,
            stringResource(R.string.not_connected_status),
            stringResource(R.string.nothing_yet),
            null,
        )
        return
    }
    val plan = schedule.day(snapshot.rules, LocalDate.now())
    val changes = schedule.differences(snapshot.rules, LocalDate.now())
    val scheduleName =
        parent.presets
            .schedule(snapshot.rules.schedule)
            .name
            .text()
    if (!model.online) {
        StatusPanel(
            Icons.Filled.CloudOff,
            palette.muted,
            stringResource(R.string.last_update, format.dayAndTime(snapshot.takenAt)),
            scheduleName,
            plan,
            changes,
        )
        return
    }
    val off = snapshot.events.any { it.kind == EventKind.VPN_OFF && it.end == null }
    val status = remember(tick, snapshot) { internetNow(schedule, snapshot) }
    val allowed = status.allowed
    // The two things a parent most often does, under the sentence they change.
    // The second one follows the state: stop the internet while there is some, allow it while there is none.
    val actions: @Composable ColumnScope.() -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
            ActionButton(Icons.Filled.MoreTime, AppColors.cyan, stringResource(R.string.thirty_more_minutes), {
                model.apply(schedule.extend(snapshot.rules, LocalDateTime.now(), 1))
            }, Modifier.weight(1f), model.canEdit)
            ActionButton(
                if (allowed) Icons.Filled.WifiOff else Icons.Filled.Wifi,
                if (allowed) AppColors.orange else AppColors.blue,
                stringResource(if (allowed) R.string.tile_stop else R.string.tile_allow),
                { SheetState.open = ChildSheet.Duration(allowed = !allowed) },
                Modifier.weight(1f),
                model.canEdit,
            )
        }
    }
    if (off) {
        StatusPanel(
            Icons.Filled.PowerSettingsNew,
            palette.alert,
            stringResource(R.string.pocora_off_on_phone),
            scheduleName,
            plan,
            changes,
        )
    } else {
        StatusPanel(
            if (allowed) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            if (allowed) palette.allowed else palette.muted,
            internetSentence(status.allowed, status.until, toChild = false),
            scheduleName,
            plan,
            changes,
            actions,
        )
    }
}

// What the schedule says now, worked out on this phone's clock.
private class InternetNow(
    val allowed: Boolean,
    val until: LocalDateTime?,
)

private fun internetNow(
    schedule: Schedule,
    snapshot: Snapshot,
): InternetNow {
    val state = schedule.state(snapshot.rules, LocalDateTime.now())
    return InternetNow(state.allowed && !snapshot.state.quotaReached, state.until)
}

// A season starting soon, or a holiday tomorrow, as one coloured card.
@Composable
private fun Suggestion(
    parent: Parent,
    model: ChildModel,
    snapshot: Snapshot,
    go: (Route) -> Unit,
) {
    val format = rememberFormat()
    val today = LocalDate.now()
    val seasons = remember { Seasons(parent.presets) }
    val rules = snapshot.rules
    val season = seasons.upcomingFor(rules, today)
    val holiday = seasons.holidayTomorrow(rules, today)
    val (title, action, route) =
        when {
            season != null -> {
                Triple(
                    stringResource(
                        R.string.starts_in_days,
                        parent.presets
                            .schedule(season.first.first())
                            .name
                            .text(),
                        format.number(ChronoUnit.DAYS.between(today, season.second)),
                    ),
                    stringResource(R.string.see),
                    Route.ChooseSchedule(model.child.id),
                )
            }

            holiday != null -> {
                Triple(
                    stringResource(R.string.tomorrow_is_holiday, holiday.second.name.text()),
                    stringResource(R.string.use_fridays_hours),
                    Route.Times(model.child.id, copyFriday = true),
                )
            }

            else -> {
                return
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(AppColors.violet, AppColors.pink)),
                    RoundedCornerShape(Dimens.cardCorner),
                ).padding(Dimens.edge),
        verticalArrangement = Arrangement.spacedBy(Dimens.row),
    ) {
        Text(text = title, color = AppColors.onColor, fontSize = Dimens.heading, fontWeight = FontWeight.Bold)
        Text(
            text = action,
            color = AppColors.violet,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .background(AppColors.onColor, RoundedCornerShape(50))
                    .clickable { go(route) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
