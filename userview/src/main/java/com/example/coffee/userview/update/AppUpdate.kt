package com.example.coffee.userview.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** One GitHub release of an app: [version] is the tag without its prefix ("userapp-v1.1" -> "1.1"). */
data class AppRelease(val version: String, val url: String)

/** A release as the GitHub API lists it, only the fields needed to pick the latest one. */
data class ReleaseInfo(val tag: String, val draft: Boolean, val prerelease: Boolean, val url: String)

object AppVersions {
    /** "1.10.2" -> [1, 10, 2]; null if it is not a dotted number. */
    fun parse(version: String): List<Int>? =
        version.trim().split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

    /** true if [candidate] is a higher version than [installed]; "1.1" == "1.1.0". */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The highest published release whose tag starts with [prefix] (e.g. "userapp-v"). */
    fun latest(releases: List<ReleaseInfo>, prefix: String): AppRelease? =
        releases.asSequence()
            .filter { !it.draft && !it.prerelease && it.tag.startsWith(prefix) }
            .map { AppRelease(it.tag.removePrefix(prefix), it.url) }
            .filter { parse(it.version) != null }
            .fold(null as AppRelease?) { best, next -> if (best == null || isNewer(next.version, best.version)) next else best }
}

/**
 * Looks for a newer release of this app on GitHub (the APKs are installed by hand from there).
 * The repository is public, so no token is needed. GitHub is asked at most once a day; in between,
 * and when the phone is offline, the last answer is used.
 */
class AppUpdateChecker(
    context: Context,
    /** tag prefix of this app's releases: "userapp-v" or "adminapp-v" */
    private val tagPrefix: String,
    private val repository: String = "vlszabolcs/CoffeeAdminApp"
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_update_$tagPrefix", Context.MODE_PRIVATE)

    val installedVersion: String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: ""

    /** The newer release, or null if this is the latest (or nothing is known yet). */
    suspend fun check(): AppRelease? {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_CHECKED, 0L) >= CHECK_INTERVAL_MS) {
            runCatching { withContext(Dispatchers.IO) { fetchLatest() } }.onSuccess { latest ->
                prefs.edit()
                    .putLong(KEY_CHECKED, now)
                    .putString(KEY_VERSION, latest?.version)
                    .putString(KEY_URL, latest?.url)
                    .apply()
            }
        }
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        return AppRelease(version, url).takeIf { AppVersions.isNewer(version, installedVersion) }
    }

    private fun fetchLatest(): AppRelease? {
        val connection = URL("https://api.github.com/repos/$repository/releases?per_page=50").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return AppVersions.latest(parseReleases(body), tagPrefix)
        } finally {
            connection.disconnect()
        }
    }

    // the APK asset of the release if there is one, otherwise the release page
    private fun parseReleases(json: String): List<ReleaseInfo> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val release = array.getJSONObject(i)
            val assets = release.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it).optString("browser_download_url") }
                .firstOrNull { it.endsWith(".apk") }
            ReleaseInfo(
                tag = release.optString("tag_name"),
                draft = release.optBoolean("draft"),
                prerelease = release.optBoolean("prerelease"),
                url = apk ?: release.optString("html_url")
            )
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val TIMEOUT_MS = 10_000
        const val KEY_CHECKED = "checked"
        const val KEY_VERSION = "version"
        const val KEY_URL = "url"
    }
}
