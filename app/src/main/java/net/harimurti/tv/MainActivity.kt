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
    private var currentCategoryPosition = 0
    private var currentCategory: Category? = null

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
        
        if (savedInstanceState != null) {
            currentCategoryPosition = savedInstanceState.getInt("current_position", 0)
        }
        
        if (!Playlist.cached.isCategoriesEmpty()) setPlaylistToAdapter(Playlist.cached)
        else updatePlaylist(true)
    }

    // PERBAIKAN: Method displayChannels dengan parameter category dan position
    fun displayChannels(category: Category?, position: Int) {
        currentCategoryPosition = position
        currentCategory = category
        
        val channels = category?.channels
        val spanCount = if (isTelevision) 6 else 4
        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)
        
        // Buat ChannelAdapter dengan catId = position (atau ID kategori yang sesuai)
        // Parameter: channels, catId, isFav
        val channelAdapter = ChannelAdapter(channels, position, false)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)
        
        // Buat CategoryAdapter dengan callback
        categoryAdapter = object : CategoryAdapter(playlistSet.categories) {
            override fun onBindViewHolder(holder: CategoryAdapter.ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                
                // Set selected position berdasarkan currentCategoryPosition
                if (currentCategoryPosition == position) {
                    holder.binding.textCategory.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                    holder.binding.textCategory.setTextColor(resources.getColor(android.R.color.white))
                }
            }
        }
        
        binding.rvCategory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategory.adapter = categoryAdapter
        
        // Tampilkan kategori yang tersimpan atau pertama
        if (playlistSet.categories.isNotEmpty()) {
            val targetPosition = if (currentCategoryPosition < playlistSet.categories.size) 
                currentCategoryPosition else 0
            currentCategory = playlistSet.categories[targetPosition]
            displayChannels(currentCategory, targetPosition)
        }
        
        Playlist.cached = playlistSet
        binding.loading.visibility = View.GONE
    }

    // PERBAIKAN: Method untuk menangani klik kategori
    fun onCategoryClicked(position: Int) {
        val category = Playlist.cached.categories?.getOrNull(position)
        if (category != null && currentCategoryPosition != position) {
            currentCategoryPosition = position
            currentCategory = category
            displayChannels(category, position)
            
            // Update visual selected category
            categoryAdapter.notifyDataSetChanged()
        }
    }

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

    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(),null)
    private fun openSearch() = SearchDialog().show(supportFragmentManager.beginTransaction(),null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_position", currentCategoryPosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }
}