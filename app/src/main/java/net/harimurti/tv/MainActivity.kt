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
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private var currentCategory: Category? = null
    private var currentSelectedPosition = 0
    private var channelAdapter: ChannelAdapter? = null

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.getStringExtra(MAIN_CALLBACK)) {
                UPDATE_PLAYLIST -> updatePlaylist(false)
                INSERT_FAVORITE -> categoryAdapter.insertOrUpdateFavorite()
                REMOVE_FAVORITE -> categoryAdapter.removeFavorite()
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
        
        binding.buttonSearch.setOnClickListener{ openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener{ openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))
        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else updatePlaylist(true)
    }

    // PERBAIKAN 1: Method displayChannels dengan parameter position
    fun displayChannels(channels: ArrayList<Channel>?, position: Int) {
        currentSelectedPosition = position
        currentCategory = Playlist.cached.categories?.getOrNull(position)
        
        val spanCount = if (isTelevision) 6 else 4
        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)
        
        // Buat ChannelAdapter baru dengan callback
        channelAdapter = ChannelAdapter(channels, 0, false).apply {
            setOnItemClickListener { channel ->
                playChannel(channel)
            }
        }
        binding.rvChannels.adapter = channelAdapter
    }

    // PERBAIKAN 2: Method untuk memutar channel
    private fun playChannel(channel: Channel) {
        // Validasi channel berasal dari kategori yang benar
        if (currentCategory != null) {
            val isValidChannel = currentCategory?.channels?.any { it.url == channel.url } ?: false
            
            if (isValidChannel) {
                // TODO: Implementasi pemutaran channel
                // Misalnya: buka PlayerActivity atau ExoPlayer
                Toast.makeText(this, "Memutar: ${channel.name}", Toast.LENGTH_SHORT).show()
                
                // Contoh jika ada PlayerActivity:
                // val intent = Intent(this, PlayerActivity::class.java)
                // intent.putExtra("channel", channel)
                // startActivity(intent)
            } else {
                Toast.makeText(this, "Channel tidak valid", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // PERBAIKAN 3: Update setPlaylistToAdapter
    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)
        
        // Buat CategoryAdapter dengan callback
        categoryAdapter = CategoryAdapter(
            categories = playlistSet.categories,
            onCategorySelected = { category, position ->
                // Ketika kategori dipilih, tampilkan channel-nya
                displayChannels(category.channels, position)
            }
        )
        
        binding.rvCategory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategory.adapter = categoryAdapter
        
        // Tampilkan kategori pertama jika ada
        if (playlistSet.categories.isNotEmpty()) {
            currentSelectedPosition = 0
            currentCategory = playlistSet.categories[0]
            displayChannels(playlistSet.categories[0].channels, 0)
        }
        
        Playlist.cached = playlistSet
        binding.loading.visibility = View.GONE
    }

    // PERBAIKAN 4: Update method updatePlaylist
    private fun updatePlaylist(useCache: Boolean) {
        binding.loading.visibility = View.VISIBLE
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {}
            override fun onResponse(playlist: Playlist?) { playlist?.let { playlistSet.mergeWith(it) } }
            override fun onFinish() { 
                runOnUiThread {
                    setPlaylistToAdapter(playlistSet)
                }
            }
        }).process(useCache)
    }

    // PERBAIKAN 5: Handle restore state
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_position", currentSelectedPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentSelectedPosition = savedInstanceState.getInt("selected_position", 0)
    }

    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(),null)
    private fun openSearch() = SearchDialog().show(supportFragmentManager.beginTransaction(),null)

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }
}