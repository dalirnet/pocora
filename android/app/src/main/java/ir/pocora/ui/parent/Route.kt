package ir.pocora.ui.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.parent.Parent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Every place in the parent app. A child id of null means the child being added.
@Serializable
sealed interface Route {
    @Serializable
    data object Welcome : Route

    @Serializable
    data object AddChild : Route

    @Serializable
    data object Pairing : Route

    // The three places of the bottom bar.
    @Serializable
    data object Home : Route

    @Serializable
    data object Events : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class Usage(
        val childId: String,
    ) : Route

    @Serializable
    data class Times(
        val childId: String,
        val copyFriday: Boolean = false,
    ) : Route

    @Serializable
    data class ChooseSchedule(
        val childId: String?,
    ) : Route

    @Serializable
    data class PreviewSchedule(
        val childId: String?,
        val scheduleId: String,
    ) : Route

    @Serializable
    data class EditDay(
        val childId: String,
        val epochDay: Long,
    ) : Route

    @Serializable
    data class Apps(
        val childId: String,
    ) : Route

    @Serializable
    data class ChooseApps(
        val childId: String?,
    ) : Route

    @Serializable
    data class Group(
        val childId: String,
        val groupId: String,
    ) : Route

    @Serializable
    data class Data(
        val childId: String,
    ) : Route
}

// The way back. Kept as text, so it survives the activity being rebuilt.
class Navigator(
    private val read: () -> String,
    private val write: (String) -> Unit,
) {
    companion object {
        private val serializer = ListSerializer(Route.serializer())
        private val json = Json

        fun encode(routes: List<Route>): String = json.encodeToString(serializer, routes)
    }

    val stack: List<Route>
        get() = json.decodeFromString(serializer, read())

    val current: Route
        get() = stack.last()

    fun go(route: Route) = write(encode(stack + route))

    // Replaces the top, as a tab change does.
    fun replace(route: Route) = write(encode(stack.dropLast(1) + route))

    fun back() {
        val routes = stack
        if (routes.size > 1) write(encode(routes.dropLast(1)))
    }

    fun reset(route: Route) = write(encode(listOf(route)))

    // Back to the nearest route of a kind, as after a schedule is chosen.
    fun backTo(predicate: (Route) -> Boolean) {
        val routes = stack
        val index = routes.indexOfLast(predicate)
        if (index >= 0) write(encode(routes.take(index + 1)))
    }
}

@Composable
fun rememberNavigator(start: Route): Navigator {
    var text by rememberSaveable { mutableStateOf(Navigator.encode(listOf(start))) }
    return Navigator({ text }, { text = it })
}

// The child being added: name, age and the three presets, until pairing sends them.
@Serializable
data class Draft(
    val name: String = "",
    val age: String = "",
    val schedule: String? = null,
    val appsList: String? = null,
    val quota: String? = null,
) {
    fun rules(suggested: Rules): Rules =
        suggested.copy(
            schedule = schedule ?: suggested.schedule,
            appsList = appsList ?: suggested.appsList,
            quota = quota ?: suggested.quota,
        )
}

// Forgetting tries to tell the child's phone first, which takes a moment, so it runs off the main thread.
fun CoroutineScope.launchForget(
    parent: Parent,
    child: Peer,
    onDone: () -> Unit,
) {
    launch {
        withContext(Dispatchers.IO) { parent.forget(child) }
        onDone()
    }
}
