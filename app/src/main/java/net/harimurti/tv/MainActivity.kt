package net.harimurti.tv

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // kategori horizontal
        binding.rvCategory.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // grid channel
        binding.rvChannels.layoutManager = GridLayoutManager(this, 4)

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (!Playlist.cached.isCategoriesEmpty())
            setPlaylistToAdapter(Playlist.cached)
        else
            showAlertPlaylistError("Playlist kosong")
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {

        adapter = CategoryAdapter(playlistSet.categories)

        adapter.setOnCategorySelected { category ->
            showChannels(category.channels)
        }

        binding.catAdapter = adapter

        // tampilkan kategori pertama
        if (playlistSet.categories.isNotEmpty()) {
            showChannels(playlistSet.categories[0].channels)
        }
    }

    private fun showChannels(channels: ArrayList<Channel>?) {
        binding.rvChannels.adapter =
            ChannelAdapter(channels, 0, false)
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

    private fun openSettings(){
        SettingDialog().show(supportFragmentManager.beginTransaction(),null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(),null)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}
