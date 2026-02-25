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
import net.harimurti.tv.adapter.CategoryAdapter
import net.harimurti.tv.adapter.ChannelAdapter
import net.harimurti.tv.databinding.ActivityMainBinding
import net.harimurti.tv.extra.*
import net.harimurti.tv.model.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter
    private val preferences = Preferences()
    private val helper = PlaylistHelper()

    companion object {
        const val MAIN_CALLBACK = "MAIN_CALLBACK"
        const val INSERT_FAVORITE = "REFRESH_FAVORITE"
        const val REMOVE_FAVORITE = "REMOVE_FAVORITE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCategory.layoutManager = LinearLayoutManager(this)
        
        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
    }

    fun updateChannelGrid(channels: ArrayList<Channel>?, catId: Int) {
        val chAdapter = ChannelAdapter(channels, catId, catId == 0)
        binding.rvChannels.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 5)
            adapter = chAdapter
        }
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        adapter = CategoryAdapter(playlistSet.categories)
        binding.rvCategory.adapter = adapter
        if (!playlistSet.categories.isNullOrEmpty()) {
            updateChannelGrid(playlistSet.categories[0].channels, 0)
        }
    }
}
