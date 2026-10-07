package ir.pocora.ui.parent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.pocora.model.Peer
import ir.pocora.model.Rules
import ir.pocora.parent.Parent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// One child as the screens see it: the last snapshot, whether the phone is here, and the parent's actions.
// An action that gets no answer leaves a retry behind, which the not-reachable sheet offers.
class ChildModel(
    private val parent: Parent,
    val child: Peer,
    private val scope: CoroutineScope,
    // Told when a change was applied.
    private val onSaved: () -> Unit,
) {
    var snapshot by mutableStateOf(parent.snapshots.read(child.id))
        private set
    var online by mutableStateOf(parent.isOnline(child.id))
        private set
    var unseenAlerts by mutableStateOf(parent.unseenAlerts(child.id))
        private set
    var busy by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var retry by mutableStateOf<(() -> Unit)?>(null)
        private set

    val rules: Rules?
        get() = snapshot?.rules

    // Changing anything needs the child's phone, which holds the only real copy. Shown as away, it is tried all the
    // same: the change wakes it over Bluetooth, and a phone truly away leaves a retry.
    val canEdit: Boolean
        get() = snapshot != null && !busy

    // From the stores, after a sync arrived.
    fun reload() {
        snapshot = parent.snapshots.read(child.id)
        online = parent.isOnline(child.id)
        unseenAlerts = parent.unseenAlerts(child.id)
    }

    // A live read, when the child's page opens.
    fun refresh() {
        scope.launch { read() }
    }

    // The same, waited for: Home pulled down.
    suspend fun read() {
        loading = true
        val fresh = withContext(Dispatchers.IO) { parent.read(child) }
        loading = false
        if (fresh != null) snapshot = fresh
        online = fresh != null || parent.isOnline(child.id)
        unseenAlerts = parent.unseenAlerts(child.id)
    }

    fun apply(
        rules: Rules,
        onDone: () -> Unit = {},
    ): Unit = act({ parent.setRules(child, rules) }, onDone) { apply(rules, onDone) }

    fun dismissRetry() {
        retry = null
    }

    fun markAlertsSeen() {
        parent.markAlertsSeen(child.id)
        unseenAlerts = 0
    }

    private fun act(
        call: () -> Boolean,
        onDone: () -> Unit,
        again: () -> Unit,
    ) {
        if (busy) return
        busy = true
        retry = null
        scope.launch {
            val done = withContext(Dispatchers.IO) { call() }
            busy = false
            if (done) {
                reload()
                online = true
                onSaved()
                onDone()
            } else {
                online = parent.isOnline(child.id)
                retry = again
            }
        }
    }
}
