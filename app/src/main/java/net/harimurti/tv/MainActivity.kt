package net.harimurti.tv

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.harimurti.tv.adapter.CategoryAdapter
import net.harimurti.tv.adapter.ChannelAdapter
import net.harimurti.tv.databinding.ActivityMainBinding
import net.harimurti.tv.dialog.SearchDialog
import net.harimurti.tv.dialog.SettingDialog
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.*
import net.harimurti.tv.model.*

open class MainActivity : AppCompatActivity() {
    private var doubleBackToExitPressedOnce = false
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> adapter.insertOrUpdateFavorite()
                REMOVE_FAVORITE -> adapter.removeFavorite()
            }
        }
    }

    companion object {
        const val MAIN_CALLBACK = "MAIN_CALLBACK"
        const val UPDATE_PLAYLIST = "UPDATE_PLAYLIST"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCategory.layoutManager = LinearLayoutManager(this)
        binding.buttonSearch.setOnClickListener{ openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener{ openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else showAlertPlaylistError(getString(R.string.null_playlist))
    }

    fun updateChannelGrid(channels: ArrayList<Channel>?, catId: Int) {
        val isFav = (catId == 0 && channels?.get(0)?.isFavorite() == true)
        val chAdapter = ChannelAdapter(channels, catId, isFav)
        binding.rvChannels.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 5) // 5 Kolom Grid
            adapter = chAdapter
        }
    }

    private fun setLoadingPlaylist(show: Boolean) {
        if (show) { binding.loading.startShimmer(); binding.loading.visibility = View.VISIBLE }
        else { binding.loading.stopShimmer(); binding.loading.visibility = View.GONE }
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        if(preferences.sortCategory) playlistSet.sortCategories()
        if(preferences.sortChannel) playlistSet.sortChannels()
        playlistSet.trimChannelWithEmptyStreamUrl()

        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (preferences.sortFavorite) fav.sort()
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)
        else playlistSet.removeFavorite()

        adapter = CategoryAdapter(playlistSet.categories)
        binding.rvCategory.adapter = adapter

        if (!playlistSet.categories.isNullOrEmpty()) {
            updateChannelGrid(playlistSet.categories[0].channels, 0)
        }

        Playlist.cached = playlistSet
        helper.writeCache(playlistSet)
        setLoadingPlaylist(false)
    }

    private fun updatePlaylist(useCache: Boolean) {
        setLoadingPlaylist(true)
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                val snackbar = Snackbar.make(binding.root, "[${error.uppercase()}] $source", Snackbar.LENGTH_INDEFINITE)
                snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
                snackbar.show()
            }
            override fun onResponse(playlist: Playlist?) { if (playlist != null) playlistSet.mergeWith(playlist) }
            override fun onFinish() { if (!playlistSet.isCategoriesEmpty()) setPlaylistToAdapter(playlistSet) }
        }).process(useCache)
    }

    private fun showAlertPlaylistError(message: String?) {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.alert_title_playlist_error)
            setMessage(message)
            setCancelable(false)
            setNeutralButton(R.string.settings) { _,_ -> openSettings() }
            setPositiveButton(R.string.dialog_retry) { _,_ -> updatePlaylist(true) }
        }.create().show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.setFullScreenFlags()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { openSettings(); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (isTelevision || doubleBackToExitPressedOnce) { super.onBackPressed(); finish(); return }
        doubleBackToExitPressedOnce = true
        Toast.makeText(this, getString(R.string.press_back_twice_exit_app), Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private fun openSettings() { SettingDialog().show(supportFragmentManager.beginTransaction(),null) }
    private fun openSearch() { SearchDialog().show(supportFragmentManager.beginTransaction(),null) }
}
