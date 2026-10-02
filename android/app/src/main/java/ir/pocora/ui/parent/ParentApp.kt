package ir.pocora.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import ir.pocora.PocoraApp
import ir.pocora.R
import ir.pocora.config.Look
import ir.pocora.model.Suggested
import ir.pocora.service.ParentNotifications
import ir.pocora.ui.AppIcons
import ir.pocora.ui.Dimens
import ir.pocora.ui.component.BarItem
import ir.pocora.ui.component.BottomBar
import ir.pocora.ui.component.BusyPill
import ir.pocora.ui.component.Toasts
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.time.LocalDate

private const val STATUS_REFRESH_MILLISECONDS = 30_000L
private const val TAB_HOME = 0
private const val TAB_ACTIVITY = 1
private const val TAB_SETTINGS = 2

// The parent app's screens and the way between them. Welcome, name and age, and the code add a child. Then Home, Activity and Settings
// are the three places of the bottom bar, and every feature of a child is its own screen.
@Composable
fun ParentApp(
    opened: Pair<String, String?>?,
    onOpened: () -> Unit,
    onLanguage: (String) -> Unit,
    onLook: (Look) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocoraApp
    val parent = app.parent
    val scope = rememberCoroutineScope()
    val changes by parent.changes.collectAsState()
    var children by remember { mutableStateOf(parent.children()) }
    var tick by remember { mutableIntStateOf(0) }
    val navigator = rememberNavigator(if (children.isEmpty()) Route.Welcome else Route.Home)
    var draftText by rememberSaveable { mutableStateOf(Json.encodeToString(Draft.serializer(), Draft())) }
    val draft = Json.decodeFromString(Draft.serializer(), draftText)
    val setDraft: (Draft) -> Unit = { draftText = Json.encodeToString(Draft.serializer(), it) }
    var selectedId by rememberSaveable { mutableStateOf(children.firstOrNull()?.id) }
    val models = remember { mutableMapOf<String, ChildModel>() }
    val savedText = context.getString(R.string.saved)

    fun model(id: String?): ChildModel? =
        id?.let(parent::child)?.let { child ->
            models.getOrPut(child.id) {
                ChildModel(parent, child, scope) { request ->
                    Toasts.show(if (request) context.getString(R.string.sent_to, child.name) else savedText)
                }
            }
        }

    // Nothing runs in the background: the children's phones are heard while the app is on screen, and not after.
    LifecycleStartEffect(Unit) {
        parent.start()
        onStopOrDispose { parent.stop() }
    }
    LaunchedEffect(changes) {
        children = parent.children()
        if (children.none { it.id == selectedId }) selectedId = children.firstOrNull()?.id
        models.values.forEach { it.reload() }
    }
    // Connected and not connected change with time alone, so the screens look again now and then.
    LaunchedEffect(Unit) {
        while (true) {
            delay(STATUS_REFRESH_MILLISECONDS)
            models.values.forEach { it.reload() }
            tick++
        }
    }
    LaunchedEffect(opened) {
        val (id, target) = opened ?: return@LaunchedEffect
        if (parent.child(id) != null) {
            selectedId = id
            navigator.reset(Route.Home)
            when (target) {
                ParentNotifications.OPEN_ALERTS -> navigator.replace(Route.Activity)
                ParentNotifications.OPEN_SCHEDULE -> navigator.go(Route.ChooseSchedule(id))
                ParentNotifications.OPEN_COPY_FRIDAY -> navigator.go(Route.Times(id, copyFriday = true))
                ParentNotifications.OPEN_USAGE -> navigator.go(Route.Usage(id))
            }
        }
        onOpened()
    }

    val route = navigator.current
    val selected = model(selectedId)
    BackHandler(enabled = navigator.stack.size > 1) { navigator.back() }
    BackHandler(
        enabled =
            route == Route.Activity || (route == Route.Settings && navigator.stack.size == 1 && children.isNotEmpty()),
    ) {
        navigator.replace(Route.Home)
    }
    // A sheet closes before the screen under it.
    BackHandler(enabled = SheetState.open != null) { SheetState.open = null }
    LaunchedEffect(route) { SheetState.open = null }

    val bottom: @Composable () -> Unit = {
        BottomBar(
            items =
                listOf(
                    BarItem(AppIcons.Home, stringResource(R.string.nav_home)),
                    BarItem(
                        AppIcons.Notifications,
                        stringResource(R.string.nav_activity),
                        selected?.unseenAlerts ?: 0,
                    ),
                    BarItem(AppIcons.Settings, stringResource(R.string.settings)),
                ),
            selected =
                when (route) {
                    Route.Activity -> TAB_ACTIVITY
                    Route.Settings -> TAB_SETTINGS
                    else -> TAB_HOME
                },
            onSelect = {
                navigator.reset(
                    when (it) {
                        TAB_ACTIVITY -> Route.Activity
                        TAB_SETTINGS -> Route.Settings
                        else -> Route.Home
                    },
                )
            },
        )
    }

    val current: ChildModel? =
        when (route) {
            is Route.Usage -> model(route.childId)
            is Route.Times -> model(route.childId)
            is Route.ChooseSchedule -> route.childId?.let(::model)
            is Route.PreviewSchedule -> route.childId?.let(::model)
            is Route.EditDay -> model(route.childId)
            is Route.Apps -> model(route.childId)
            is Route.ChooseApps -> route.childId?.let(::model)
            is Route.Group -> model(route.childId)
            is Route.Data -> model(route.childId)
            Route.Home, Route.Activity -> selected
            else -> null
        }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            Route.Welcome -> {
                WelcomeScreen(
                    onStart = {
                        setDraft(Draft())
                        navigator.go(Route.AddChild)
                    },
                    onSettings = { navigator.go(Route.Settings) },
                )
            }

            Route.AddChild -> {
                AddChildScreen(
                    draft = draft,
                    onDraft = setDraft,
                    onBack = navigator::back,
                    onNext = { navigator.go(Route.Pairing) },
                )
            }

            Route.Pairing -> {
                val age = draft.age.toIntOrNull()
                PairingCodeScreen(
                    childName = draft.name.trim(),
                    childAge = age,
                    rules = draft.rules(Suggested.rules(parent.presets, age, LocalDate.now())),
                    onBack = navigator::back,
                    onPaired = {
                        children = parent.children()
                        selectedId = children.lastOrNull()?.id
                        setDraft(Draft())
                        navigator.reset(Route.Home)
                    },
                )
            }

            Route.Home -> {
                selected?.let {
                    HomeScreen(
                        model = it,
                        children = children,
                        online = { id -> model(id)?.online ?: false },
                        tick = tick + changes.toInt(),
                        onChooseChild = { selectedId = it },
                        onAddChild = {
                            setDraft(Draft())
                            navigator.go(Route.AddChild)
                        },
                        go = { next -> if (next == Route.Activity) navigator.replace(next) else navigator.go(next) },
                        bottom = bottom,
                    )
                } ?: LaunchedEffect(route) { navigator.reset(Route.Welcome) }
            }

            Route.Activity -> {
                selected?.let { ActivityScreen(model = it, bottom = bottom) }
            }

            Route.Settings -> {
                SettingsScreen(
                    children = children,
                    onForget = { child ->
                        scope.launchForget(parent, child) {
                            models.remove(child.id)
                            children = parent.children()
                            if (children.isEmpty()) navigator.reset(Route.Welcome)
                        }
                    },
                    onBack = if (children.isEmpty()) navigator::back else null,
                    onLanguage = onLanguage,
                    onLook = onLook,
                    bottom = if (children.isEmpty()) null else bottom,
                )
            }

            is Route.Usage -> {
                current?.let {
                    ChildPage(
                        it,
                        stringResource(R.string.tile_usage),
                        navigator::back,
                    ) { snapshot -> UsageTab(it, snapshot) }
                }
            }

            is Route.Times -> {
                current?.let {
                    ChildPage(it, stringResource(R.string.tile_times), navigator::back) { snapshot ->
                        ScheduleTab(it, snapshot.rules, route.copyFriday, navigator::go)
                    }
                }
            }

            is Route.Apps -> {
                current?.let {
                    ChildPage(
                        it,
                        stringResource(R.string.tab_apps),
                        navigator::back,
                    ) { snapshot -> AppsTab(it, snapshot, navigator::go) }
                }
            }

            is Route.Data -> {
                current?.let {
                    ChildPage(
                        it,
                        stringResource(R.string.card_data),
                        navigator::back,
                    ) { snapshot -> DataTab(it, snapshot) }
                }
            }

            is Route.ChooseSchedule -> {
                ChooseScheduleScreen(
                    model = current,
                    chosen = if (route.childId == null) draft.schedule else null,
                    onBack = navigator::back,
                    onPreset = { navigator.go(Route.PreviewSchedule(route.childId, it)) },
                )
            }

            is Route.PreviewSchedule -> {
                PreviewScheduleScreen(
                    model = current,
                    scheduleId = route.scheduleId,
                    onBack = navigator::back,
                    onUse = { rules ->
                        if (current == null) {
                            setDraft(draft.copy(schedule = route.scheduleId))
                            navigator.backTo { it == Route.AddChild }
                        } else {
                            current.apply(rules) { navigator.backTo { it is Route.Times || it == Route.Home } }
                        }
                    },
                )
            }

            is Route.EditDay -> {
                current?.let {
                    EditDayScreen(
                        model = it,
                        date = LocalDate.ofEpochDay(route.epochDay),
                        onBack = navigator::back,
                    )
                }
            }

            is Route.ChooseApps -> {
                ChooseAppsScreen(
                    model = current,
                    age = if (current == null) draft.age.toIntOrNull() else current.child.age,
                    chosen = if (current == null) draft.appsList else null,
                    onBack = navigator::back,
                    onSave = { listId ->
                        if (current == null) {
                            setDraft(draft.copy(appsList = listId))
                            navigator.back()
                        } else {
                            current.rules?.let { rules ->
                                current.apply(rules.copy(appsList = listId)) { navigator.back() }
                            }
                        }
                    },
                )
            }

            is Route.Group -> {
                current?.let { GroupScreen(model = it, groupId = route.groupId, onBack = navigator::back) }
            }
        }
        // While a change is on its way to the child's phone.
        if (current?.busy == true) {
            BusyPill(
                text = stringResource(R.string.sending),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Dimens.small),
            )
        }
        current?.retry?.let { retry ->
            NotReachableSheet(childName = current.child.name, onCancel = current::dismissRetry, onRetry = retry)
        }
    }
}
