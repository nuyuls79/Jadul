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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // setup grid kanan
        binding.rvChannels.layoutManager = GridLayoutManager(this, 4)

        // divider sidebar
        binding.rvCategory.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        if (!Playlist.cached.isCategoriesEmpty())
            setPlaylistToAdapter(Playlist.cached)
        else
            showAlertPlaylistError(getString(R.string.null_playlist))
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {

        adapter = CategoryAdapter(playlistSet.categories)

        // saat kategori dipilih → tampilkan channel di kanan
        adapter.setOnCategorySelected { category ->
            showChannels(category.channels)
        }

        binding.catAdapter = adapter

        // tampilkan kategori pertama otomatis
        if (playlistSet.categories.isNotEmpty()) {
            showChannels(playlistSet.categories[0].channels)
        }
    }

    private fun showChannels(channels: List<Channel>) {
        channelAdapter = ChannelAdapter(channels)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun updatePlaylist(useCache: Boolean) {
        setPlaylistToAdapter(Playlist.cached)
    }

    private fun showAlertPlaylistError(message: String?) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> updatePlaylist(true) }
            .show()
    }

    private fun openSettings() {
        SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(), null)
    }
}
