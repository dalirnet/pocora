package ir.pocora.model

import ir.pocora.preset.AppGroup
import ir.pocora.preset.Presets

// Whether an app has internet at this moment. An app fixed by hand, or always by the presets, is in or out at every
// moment. The rest follow the apps list in an Allowed mark, and have none in a Limited one or once the data ran out.
object AppAccess {
    // The parent's own choice for an app, or the presets' when the parent made none.
    fun choiceOf(
        presets: Presets,
        rules: Rules,
        packageName: String,
    ): AppChoice? = rules.apps[packageName] ?: AppChoice.IN.takeIf { presets.isAlways(packageName) }

    fun hasInternet(
        presets: Presets,
        rules: Rules,
        app: InstalledApp,
        appsListId: String,
        allowed: Boolean,
    ): Boolean {
        // VPN apps never have internet, whatever was chosen: one would carry every other app around Pocora.
        if (app.vpn) return false
        // What the list says, in an Allowed mark. System apps are in every list.
        val listed = app.system || app.group == AppGroup.SYSTEM || app.group in presets.appsList(appsListId).groups
        return when (choiceOf(presets, rules, app.`package`)) {
            AppChoice.IN -> true
            AppChoice.OUT -> false
            null -> allowed && listed
        }
    }

    // Whether a whole group has internet with a list, for the Apps tab.
    fun groupHasInternet(
        presets: Presets,
        appsListId: String,
        groupId: String,
    ): Boolean = groupId == AppGroup.SYSTEM || groupId in presets.appsList(appsListId).groups
}
