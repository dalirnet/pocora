package ir.pocora.config

import android.content.Context
import ir.pocora.debug.FileLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// One value kept as one JSON file in the app's private folder. Writes go to a temporary file first,
// so a crash never leaves half a file.
class JsonFile<T>(
    context: Context,
    name: String,
    private val serializer: KSerializer<T>,
) {
    companion object {
        private const val TAG = "JsonFile"
        val json = Json { ignoreUnknownKeys = true }
    }

    private val file = File(context.filesDir, name)

    @Synchronized
    fun read(): T? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (error: IllegalArgumentException) {
            FileLogger.w(TAG, "Unreadable ${file.name}, starting again", error)
            null
        } catch (error: IOException) {
            FileLogger.w(TAG, "Unreadable ${file.name}", error)
            null
        }
    }

    @Synchronized
    fun write(value: T) {
        try {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(serializer, value))
            if (!temporary.renameTo(file)) throw IOException("rename failed")
        } catch (error: IOException) {
            FileLogger.e(TAG, "Could not write ${file.name}", error)
        }
    }

    @Synchronized
    fun update(
        empty: T,
        change: (T) -> T,
    ): T = change(read() ?: empty).also(::write)

    @Synchronized
    fun delete() {
        file.delete()
    }
}
