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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import ir.pocora.ui.Dimens
import ir.pocora.ui.LocalPalette
import ir.pocora.ui.component.Avatar
import ir.pocora.ui.component.BottomAction
import ir.pocora.ui.component.ButtonPair
import ir.pocora.ui.component.Card
import ir.pocora.ui.component.Chip
import ir.pocora.ui.component.Field
import ir.pocora.ui.component.Hero
import ir.pocora.ui.component.IconTile
import ir.pocora.ui.component.MainButton
import ir.pocora.ui.component.QrCode
import ir.pocora.ui.component.Screen
import ir.pocora.ui.component.Sheet
import ir.pocora.ui.component.WhiteIcon
import ir.pocora.ui.rememberFormat

// Adding a child: welcome, name and age, and the pairing code.

private val POINTS =
    listOf(
        R.string.welcome_point_times,
        R.string.welcome_point_apps,
        R.string.welcome_point_alerts,
        R.string.welcome_point_private,
    )

// First run only: what Pocora does, in four lines, and one way forward.
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    Hero(
        color = AppColors.violet,
        icon = Icons.Filled.FamilyRestroom,
        title = stringResource(R.string.app_name_parent),
        around = listOf(Icons.Filled.PhoneAndroid, Icons.Filled.AccessTime, Icons.Filled.Shield),
        trailing = { WhiteIcon(Icons.Filled.Settings, stringResource(R.string.settings), onSettings) },
    ) {
        Card {
            Text(
                text = stringResource(R.string.welcome_title),
                color = palette.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            for (point in POINTS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = palette.brand, modifier = Modifier.size(20.dp))
                    Text(text = stringResource(point), color = palette.text, fontSize = Dimens.body)
                }
            }
            MainButton(text = stringResource(R.string.start), onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Home, null, tint = palette.muted, modifier = Modifier.size(20.dp))
            Text(
                text = "  " + stringResource(R.string.welcome_need_wifi),
                color = palette.muted,
                fontSize = Dimens.caption,
            )
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
                    Icon(Icons.Filled.Person, null, tint = palette.brand, modifier = Modifier.size(48.dp))
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val app = LocalContext.current.applicationContext as PocoraApp
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

    Box(modifier = Modifier.fillMaxSize()) {
        Screen(
            title = stringResource(R.string.connect_childs_phone, childName),
            onBack = onBack,
            trailing = { StepDots(current = 1, count = 2) },
        ) {
            Card(horizontalAlignment = Alignment.CenterHorizontally) {
                val codeModifier =
                    Modifier.widthIn(max = 260.dp).fillMaxWidth().alpha(
                        if (request ==
                            null
                        ) {
                            1f
                        } else {
                            DIMMED_ALPHA
                        },
                    )
                code?.let { QrCode(it, codeModifier) } ?: Spacer(codeModifier.aspectRatio(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.row)) {
                Step(1, stringResource(R.string.pair_step_install, childName))
                Step(2, stringResource(R.string.pair_step_wifi))
                Step(3, stringResource(R.string.pair_step_scan, childName))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Chip(stringResource(R.string.waiting_for_childs_phone, childName))
            }
        }
        request?.let { asking ->
            val reject = {
                request = null
                host.reject()
            }
            // Closing the sheet any other way than Accept turns the phone away.
            Sheet(
                onDismiss = reject,
                title = stringResource(R.string.phone_wants_to_connect),
                icon = Icons.Filled.PhonelinkRing,
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
                    IconTile(Icons.Filled.PhoneAndroid, AppColors.slate, Dimens.rowIcon)
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

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.row), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(28.dp).background(palette.limited, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String.format(LocalConfiguration.current.locales[0], "%d", number),
                color = palette.text,
                fontSize = 14.sp,
            )
        }
        Text(text = text, color = palette.text, fontSize = Dimens.body, modifier = Modifier.weight(1f))
    }
}
