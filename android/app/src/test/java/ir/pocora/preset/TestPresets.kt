package ir.pocora.preset

import java.io.File

// The real presets.json, read from the source tree. Unit tests run with the app module as their folder.
object TestPresets {
    val presets: Presets by lazy { Presets.parse(File("src/main/assets/presets.json").readText()) }
}
