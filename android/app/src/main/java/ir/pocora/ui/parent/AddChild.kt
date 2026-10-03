package ir.pocora.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.model.Rules
import ir.pocora.protocol.PairRequest
import ir.pocora.transport.PairingHost
import ir.pocora.ui.AppColors
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.SamePhone
import ir.pocora.ui.component.Avatar
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.EmptyState
import ir.pocora.ui.component.Field
import ir.pocora.ui.component.IconAction
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.LinkRow
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.PointRow
import ir.pocora.ui.component.QrCode
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.rememberFormat

// Adding a child: welcome, name and age, and the pairing code.

// What Pocora does, as on Home: each point with the colour and icon of its feature there.
private class Point(
    val icon: ImageVector,
    val color: Color,
    val title: Int,
)

private val POINTS =
    listOf(
        Point(AppIcons.AccessTime, AppColors.violet, R.string.welcome_point_times),
        Point(AppIcons.Apps, AppColors.blue, R.string.welcome_point_apps),
        Point(AppIcons.Warning, AppColors.orange, R.string.welcome_point_alerts),
        Point(AppIcons.Home, AppColors.green, R.string.welcome_point_private),
    )

// First run only: what Pocora does, in four points, and one way forward at the bottom.
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onSettings: () -> Unit,
) {
    Screen(
        title = stringResource(R.string.app_name_parent),
        trailing = { IconAction(AppIcons.Settings, stringResource(R.string.settings), onSettings) },
        bottom = {
            BottomAction {
                MainButton(text = stringResource(R.string.start), onClick = onStart, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        EmptyState(
            icon = AppIcons.FamilyRestroom,
            color = AppColors.violet,
            title = stringResource(R.string.welcome_title),
            text = stringResource(R.string.welcome_need_wifi),
        )
        Card {
            for (point in POINTS) PointRow(point.icon, point.color, stringResource(point.title))
        }
    }
}

private const val MAXIMUM_NAME_LENGTH = 30
private val AGES = 8..16

// The child's name and age, and nothing else. The avatar above follows the name as it is typed.
// Age is a row of round chips, so there is no number to type. Next stays at the bottom.
@Composable
fun AddChildScreen(
    draft: Draft,
    onDraft: (Draft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val palette = LocalPalette.current
    val format = rememberFormat()
    val age = draft.age.toIntOrNull()
    val name = draft.name.trim()
    val ages =
        rememberLazyListState(initialFirstVisibleItemIndex = ((age ?: DEFAULT_AGE) - AGES.first - 2).coerceAtLeast(0))
    Screen(
        title = stringResource(R.string.add_a_child),
        onBack = onBack,
        trailing = { StepDots(current = 0, count = 2) },
        bottom = {
            BottomAction {
                MainButton(
                    text = stringResource(R.string.next),
                    onClick = onNext,
                    enabled = name.isNotEmpty() && age in AGES,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (name.isEmpty()) {
                // No name yet: a quiet placeholder instead of a coloured letter.
                Box(
                    modifier = Modifier.size(96.dp).background(palette.brand.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Person, null, tint = palette.brand, modifier = Modifier.size(48.dp))
                }
            } else {
                Avatar(name, 96.dp)
            }
            Text(
                text = name.ifEmpty { stringResource(R.string.child_name) },
                color = if (name.isEmpty()) palette.muted else palette.text,
                fontSize = Dimens.title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.row),
            )
            if (age !=
                null
            ) {
                Text(
                    text = stringResource(R.string.years_old, format.number(age)),
                    color = palette.muted,
                    fontSize = Dimens.body,
                )
            }
        }
        Card {
            Field(
                label = stringResource(R.string.child_name),
                value = draft.name,
                onValueChange = { onDraft(draft.copy(name = it.take(MAXIMUM_NAME_LENGTH))) },
            )
        }
        Card {
            Text(
                text =
                    if (name.isEmpty()) {
                        stringResource(
                            R.string.how_old_plain,
                        )
                    } else {
                        stringResource(R.string.how_old, name)
                    },
                color = palette.text,
                fontSize = Dimens.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyRow(state = ages, horizontalArrangement = Arrangement.spacedBy(Dimens.small)) {
                items(AGES.toList()) { value ->
                    val chosen = value == age
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (chosen) palette.brand else palette.limited.copy(alpha = 0.6f))
                                .clickable { onDraft(draft.copy(age = value.toString())) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = format.number(value),
                            color = if (chosen) AppColors.onColor else palette.text,
                            fontSize = Dimens.heading,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.add_child_note),
            color = palette.muted,
            fontSize = Dimens.caption,
            lineHeight = 20.sp,
        )
    }
}

// How far along a short flow is: a dot per step, the current one long and violet.
@Composable
private fun StepDots(
    current: Int,
    count: Int,
) {
    val palette = LocalPalette.current
    // The top bar's edge is 12dp and the content's 20dp; the dots line up with the content.
    Row(
        modifier = Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier =
                    Modifier
                        .size(width = if (index == current) 20.dp else 8.dp, height = 8.dp)
                        .background(if (index <= current) palette.brand else palette.limited, RoundedCornerShape(4.dp)),
            )
        }
    }
}

private const val DEFAULT_AGE = 10

private const val DIMMED_ALPHA = 0.3f

// The pairing code, and the accept sheet over it. Unpaired phones can reach this one only while this screen is open.
@Composable
fun PairingCodeScreen(
    childName: String,
    childAge: Int?,
    rules: Rules,
    onBack: () -> Unit,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocoraApp
    val childAppHere = remember { SamePhone.hasChildApp(context) }
    // Handed to the child app on this phone with the code. The request that brings it back is accepted at once.
    val token = remember { SamePhone.newToken() }
    var connectingHere by remember { mutableStateOf(false) }
    val currentOnPaired by rememberUpdatedState(onPaired)
    var code by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<PairRequest?>(null) }
    val host =
        remember {
            PairingHost(
                identity = { app.identity },
                peerStore = app.peerStore,
                onRequest = { request = it },
                onPairing = app.parent::clearData,
                onPaired = { currentOnPaired() },
                onRequestGone = { request = null },
            )
        }
    DisposableEffect(host) {
        app.endpoint.openPairing(host) { code = it.encode() }
        onDispose { app.endpoint.closePairing(host) }
    }
    // The child app on this phone, back with the handed token: the parent already chose this, so no sheet.
    val handedBack = connectingHere && request?.token == token
    LaunchedEffect(handedBack) {
        if (handedBack) host.accept(childName, childAge, rules)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(
            title = stringResource(R.string.connect_childs_phone, childName),
            onBack = onBack,
            trailing = { StepDots(current = 1, count = 2) },
        ) {
            Card(horizontalAlignment = Alignment.CenterHorizontally) {
                val codeModifier =
                    Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .alpha(if (request == null && !connectingHere) 1f else DIMMED_ALPHA)
                code?.let { QrCode(it, codeModifier) } ?: Spacer(codeModifier.aspectRatio(1f))
                Chip(
                    if (connectingHere) {
                        stringResource(R.string.connecting_here)
                    } else {
                        stringResource(R.string.waiting_for_childs_phone, childName)
                    },
                )
            }
            Card {
                PointRow(
                    AppIcons.PhoneAndroid,
                    AppColors.violet,
                    stringResource(R.string.pair_step_install, childName),
                )
                PointRow(AppIcons.Wifi, AppColors.teal, stringResource(R.string.pair_step_wifi))
                PointRow(AppIcons.QrCodeScanner, AppColors.blue, stringResource(R.string.pair_step_scan, childName))
            }
            // No camera can scan its own screen, so with the child app on this phone the code is handed over.
            code?.takeIf { childAppHere }?.let { shown ->
                Card {
                    LinkRow(
                        title = stringResource(R.string.pair_on_this_phone),
                        subtitle =
                            stringResource(
                                if (connectingHere) R.string.connecting_here else R.string.pair_on_this_phone_text,
                            ),
                        icon = AppIcons.PhonelinkRing,
                        iconColor = AppColors.green,
                        onClick = {
                            if (!connectingHere) {
                                connectingHere = true
                                SamePhone.handCode(context, shown, token)
                            }
                        },
                    )
                }
            }
        }
        request?.takeUnless { handedBack }?.let { asking ->
            val reject = {
                request = null
                host.reject()
            }
            // Closing the sheet any other way than Accept turns the phone away.
            Sheet(
                onDismiss = reject,
                title = stringResource(R.string.phone_wants_to_connect),
                icon = AppIcons.PhonelinkRing,
                subtitle = stringResource(R.string.accept_only_if, childName),
            ) {
                val palette = LocalPalette.current
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                palette.limited.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp),
                            ).padding(Dimens.inside),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.row),
                ) {
                    IconTile(AppIcons.PhoneAndroid, AppColors.slate, Dimens.rowIcon)
                    Column {
                        // A phone's name is Latin; the Persian font would turn its digits Persian.
                        Text(
                            text = asking.deviceName,
                            color = palette.text,
                            fontSize = Dimens.body,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                        )
                        Text(
                            text = stringResource(R.string.android_version, asking.androidVersion),
                            color = palette.muted,
                            fontSize = Dimens.caption,
                        )
                    }
                }
                // Already paired with another parent: it keeps its name and rules, and this parent sees them.
                asking.childName?.let { name ->
                    PointRow(
                        AppIcons.FamilyRestroom,
                        AppColors.blue,
                        stringResource(R.string.has_another_parent, name),
                        stringResource(R.string.has_another_parent_text),
                    )
                }
                ButtonPair(
                    quiet = stringResource(R.string.reject),
                    onQuiet = reject,
                    main = stringResource(R.string.accept),
                    onMain = { host.accept(childName, childAge, rules) },
                )
            }
        }
    }
}
