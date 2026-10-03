package ir.pocora.transport

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ir.pocora.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Pocora's releases on GitHub: the newest version, and this app's APK from it, behind the Update button in Settings.
// The new APK installs over this one and keeps everything: both are signed with Pocora's key.
object Releases {
    private const val REPOSITORY = "dalirnet/pocora"
    private const val LATEST = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val DOWNLOAD = "https://github.com/$REPOSITORY/releases/latest/download/pocora-%s.apk"
    private const val TIMEOUT_MILLISECONDS = 15_000
    private const val BUFFER_BYTES = 64 * 1024
    private const val FOLDER = "updates"
    private const val APK_TYPE = "application/vnd.android.package-archive"

    private val json = Json { ignoreUnknownKeys = true }

    // The newest released version, such as "1.2.0", or null when GitHub cannot be reached.
    fun latest(): String? =
        try {
            val connection = open(LATEST)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            json
                .parseToJsonElement(text)
                .jsonObject["tag_name"]
                ?.jsonPrimitive
                ?.content
                ?.removePrefix("v")
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    // Whether a release is newer than this app: by number, part by part, so 1.10.0 is newer than 1.9.0.
    fun isNewer(
        release: String,
        current: String,
    ): Boolean {
        val theirs = parts(release)
        val ours = parts(current)
        for (index in 0 until maxOf(theirs.size, ours.size)) {
            val a = theirs.getOrElse(index) { 0 }
            val b = ours.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    // Downloads this app's APK of the newest release into the cache, telling how far along it is as a share.
    // Null when the download did not finish; nothing half done is left behind.
    fun download(
        context: Context,
        onProgress: (Float) -> Unit,
    ): File? {
        val role = Role.current.name.lowercase()
        val file = File(File(context.cacheDir, FOLDER).apply { mkdirs() }, "pocora-$role.apk")
        return try {
            val connection = open(DOWNLOAD.format(role))
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var done = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            file
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    // Hands the file to Android's installer, which asks the user and then replaces the app in place.
    fun install(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLISECONDS
            readTimeout = TIMEOUT_MILLISECONDS
            instanceFollowRedirects = true
        }
}
