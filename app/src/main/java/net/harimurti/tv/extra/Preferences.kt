package net.harimurti.tv.extra

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import net.harimurti.tv.App
import net.harimurti.tv.R
import net.harimurti.tv.extension.isLinkUrl
import net.harimurti.tv.extension.isPathExist
import net.harimurti.tv.model.PlayData
import net.harimurti.tv.model.Source
import java.util.*
import kotlin.collections.ArrayList

class Preferences {
    private val context = App.context
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val editor = preferences.edit()

    companion object {
        private const val FIRST_TIME = "FIRST_TIME"
        private const val IGNORED_VERSION = "IGNORED_VERSION"
        private const val LAST_WATCHED = "LAST_WATCHED"
        private const val OPEN_LAST_WATCHED = "OPEN_LAST_WATCHED"
        private const val LAUNCH_AT_BOOT = "LAUNCH_AT_BOOT"
        private const val SORT_FAVORITE = "SORT_FAVORITE"
        private const val SORT_CATEGORY = "SORT_CATEGORY"
        private const val SORT_CHANNEL = "SORT_CHANNEL"
        private const val OPTIMIZE_PREBUFFER = "OPTIMIZE_PREBUFFER"
        private const val REVERSE_NAVIGATION = "REVERSE_NAVIGATION"
        private const val CONTRIBUTORS = "CONTRIBUTORS"
        private const val RESIZE_MODE = "RESIZE_MODE"
        private const val SPEED_MODE = "SPEED_MODE"
        private const val VOLUME_CONTROL = "VOLUME_CONTROL"
        private const val SOURCES_PLAYLIST = "SOURCES_PLAYLIST"
        private const val COUNTRY_ID = "COUNTRY_ID"
        private const val DECODER_MODE = "DECODER_MODE" // Tambahan untuk mode decoder

        // Dua URL playlist default (ganti sesuai kebutuhan)
        const val DEFAULT_PLAYLIST_URL_1 = "https://bit.ly/KPL203"
        const val DEFAULT_PLAYLIST_URL_2 = "https://waduk.diskon.cloud/utama/multiplay.php" // Ganti dengan URL kedua yang sebenarnya
    }

    var isFirstTime: Boolean
        get() = preferences.getBoolean(FIRST_TIME, true)
        set(value) = editor.putBoolean(FIRST_TIME, value).apply()

    var ignoredVersion: Int
        get() = preferences.getInt(IGNORED_VERSION, 0)
        set(value) = editor.putInt(IGNORED_VERSION, value).apply()

    var launchAtBoot: Boolean
        get() = preferences.getBoolean(LAUNCH_AT_BOOT, false)
        set(value) = editor.putBoolean(LAUNCH_AT_BOOT, value).apply()

    var playLastWatched: Boolean
        get() = preferences.getBoolean(OPEN_LAST_WATCHED, true)
        set(value) = editor.putBoolean(OPEN_LAST_WATCHED, value).apply()

    var sortFavorite: Boolean
        get() = preferences.getBoolean(SORT_FAVORITE, false)
        set(value) = editor.putBoolean(SORT_FAVORITE, value).apply()

    var sortCategory: Boolean
        get() = preferences.getBoolean(SORT_CATEGORY, false)
        set(value) = editor.putBoolean(SORT_CATEGORY, value).apply()

    var sortChannel: Boolean
        get() = preferences.getBoolean(SORT_CHANNEL, true)
        set(value) = editor.putBoolean(SORT_CHANNEL, value).apply()

    var watched: PlayData
        get() = Gson().fromJson(preferences.getString(LAST_WATCHED, "{}") ?: "{}", PlayData::class.java)
        set(value) {
            val json = Gson().toJson(value)
            editor.putString(LAST_WATCHED, json).apply()
        }

    var optimizePrebuffer: Boolean
        get() = preferences.getBoolean(OPTIMIZE_PREBUFFER, true)
        set(value) = editor.putBoolean(OPTIMIZE_PREBUFFER, value).apply()

    var reverseNavigation: Boolean
        get() = preferences.getBoolean(REVERSE_NAVIGATION, false)
        set(value) = editor.putBoolean(REVERSE_NAVIGATION, value).apply()

    var countryId: String
        get() = preferences.getString(COUNTRY_ID, "id") ?: "id"
        set(value) = editor.putString(COUNTRY_ID, value).apply()

    var sources: ArrayList<Source>?
        get() {
            val result = ArrayList<Source>()
            // Dua sumber default
            val default1 = Source().apply {
                path = DEFAULT_PLAYLIST_URL_1
                active = true
            }
            val default2 = Source().apply {
                path = DEFAULT_PLAYLIST_URL_2
                active = false  // default kedua tidak aktif, bisa diubah di pengaturan
            }
            try {
                val json = preferences.getString(SOURCES_PLAYLIST, null)
                if (!json.isNullOrBlank()) {
                    val list = Gson().fromJson(json, Array<Source>::class.java)
                    if (list != null && list.isNotEmpty()) {
                        list.forEach {
                            if (it.path.isLinkUrl() || it.path.isPathExist()) result.add(it)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (result.isEmpty()) {
                result.add(default1)
                result.add(default2)
            }
            // Pastikan setidaknya satu aktif
            val active = result.filter { it.active }
            if (active.isEmpty()) {
                result.first().active = true
            }
            return result
        }
        set(value) {
            val json = Gson().toJson(value)
            editor.putString(SOURCES_PLAYLIST, json).apply()
        }

    var contributors: String?
        get() = preferences.getString(CONTRIBUTORS, context.getString(R.string.main_contributors))
        set(value) = editor.putString(CONTRIBUTORS, value).apply()

    var resizeMode: Int
        get() = preferences.getInt(RESIZE_MODE, 3)
        set(value) = editor.putInt(RESIZE_MODE, value).apply()

    var speedMode: Float
        get() = preferences.getFloat(SPEED_MODE, 1F)
        set(value) = editor.putFloat(SPEED_MODE, value).apply()

    var volume: Float
        get() = preferences.getFloat(VOLUME_CONTROL, 1F)
        set(value) = editor.putFloat(VOLUME_CONTROL, value).apply()

    // Mode decoder: 0=HW, 1=SW, 2=HW+
    var decoderMode: Int
        get() = preferences.getInt(DECODER_MODE, 0)
        set(value) = editor.putInt(DECODER_MODE, value).apply()
}