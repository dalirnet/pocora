package ir.pocora.model

import ir.pocora.preset.AppGroup
import ir.pocora.preset.Presets

// Whether an app has internet in an Allowed mark. In a Limited mark no app has.
object AppAccess {
    fun hasInternet(
        presets: Presets,
        rules: Rules,
        app: InstalledApp,
        appsListId: String,
    ): Boolean =
        when {
            // VPN apps never have internet, whatever the parent chose: one would carry every other app around Pocora.
            app.vpn -> false

            rules.apps[app.`package`] == AppChoice.IN -> true

            rules.apps[app.`package`] == AppChoice.OUT -> false

            app.system || app.group == AppGroup.SYSTEM -> true

            else -> app.group in presets.appsList(appsListId).groups
        }

    // Whether a whole group has internet with a list, for the Apps tab.
    fun groupHasInternet(
        presets: Presets,
        appsListId: String,
        groupId: String,
    ): Boolean = groupId == AppGroup.SYSTEM || groupId in presets.appsList(appsListId).groups
}
