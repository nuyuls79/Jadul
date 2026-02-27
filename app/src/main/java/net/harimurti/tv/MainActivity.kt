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

class MainActivity : AppCompatActivity() {
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private var currentCategoryPosition = 0
    private var currentCategory: Category? = null
    private var isDataLoaded = false  // Flag untuk menandai data sudah dimuat

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
        
        // PERBAIKAN: Jangan setup adapter kosong, langsung loading
        binding.loading.visibility = View.VISIBLE
        binding.rvCategory.visibility = View.GONE  // Sembunyikan RecyclerView dulu
        
        // Cek apakah sudah ada cache
        if (!Playlist.cached.isCategoriesEmpty()) {
            // Jika sudah ada cache, langsung tampilkan
            binding.loading.visibility = View.GONE
            binding.rvCategory.visibility = View.VISIBLE
            setPlaylistToAdapter(Playlist.cached)
        } else {
            // Update playlist
            updatePlaylist(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // PERBAIKAN: Jika data belum dimuat dan cache kosong, muat ulang
        if (!isDataLoaded && Playlist.cached.categories.isNullOrEmpty()) {
            binding.loading.visibility = View.VISIBLE
            binding.rvCategory.visibility = View.GONE
            updatePlaylist(true)
        } else if (!isDataLoaded && !Playlist.cached.categories.isNullOrEmpty()) {
            // Jika cache ada tapi data belum dimuat
            binding.loading.visibility = View.GONE
            binding.rvCategory.visibility = View.VISIBLE
            setPlaylistToAdapter(Playlist.cached)
        }
    }

    fun onCategoryClicked(position: Int) {
        val category = Playlist.cached.categories?.getOrNull(position)
        if (category != null && currentCategoryPosition != position) {
            currentCategoryPosition = position
            currentCategory = category
            displayChannels(category, position)
        }
    }

    private fun displayChannels(category: Category?, position: Int) {
        currentCategoryPosition = position
        currentCategory = category
        
        val channels = category?.channels
        val spanCount = if (isTelevision) 6 else 4
        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)
        
        val channelAdapter = ChannelAdapter(channels, position, false)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)
        
        // PERBAIKAN: Pastikan categories tidak null
        val categories = playlistSet.categories ?: arrayListOf()
        
        // Buat adapter baru dengan data
        categoryAdapter = CategoryAdapter(categories)
        categoryAdapter.setOnCategoryClickListener { position ->
            onCategoryClicked(position)
        }
        
        binding.rvCategory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategory.adapter = categoryAdapter
        binding.rvCategory.visibility = View.VISIBLE  // Tampilkan RecyclerView
        
        // Set selected position jika ada
        if (categories.isNotEmpty()) {
            if (currentCategoryPosition >= categories.size) {
                currentCategoryPosition = 0
            }
            categoryAdapter.setSelectedPosition(currentCategoryPosition)
            
            // Tampilkan channel untuk kategori pertama
            val targetPosition = if (currentCategoryPosition < categories.size) 
                currentCategoryPosition else 0
            currentCategory = categories[targetPosition]
            displayChannels(currentCategory, targetPosition)
        } else {
            // Jika tidak ada kategori, sembunyikan loading dan tampilkan pesan
            binding.rvChannels.adapter = null
            Toast.makeText(this, "Tidak ada kategori", Toast.LENGTH_SHORT).show()
        }
        
        Playlist.cached = playlistSet
        isDataLoaded = true  // Tandai data sudah dimuat
        binding.loading.visibility = View.GONE
    }

    private fun updatePlaylist(useCache: Boolean) {
        binding.loading.visibility = View.VISIBLE
        binding.rvCategory.visibility = View.GONE  // Sembunyikan RecyclerView
        
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    binding.rvCategory.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Error loading playlist: $error", Toast.LENGTH_SHORT).show()
                    
                    // Jika error, coba tampilkan cache jika ada
                    if (!Playlist.cached.categories.isNullOrEmpty()) {
                        setPlaylistToAdapter(Playlist.cached)
                    }
                }
            }
            
            override fun onResponse(playlist: Playlist?) { 
                playlist?.let { playlistSet.mergeWith(it) } 
            }
            
            override fun onFinish() { 
                runOnUiThread {
                    if (playlistSet.categories.isNullOrEmpty()) {
                        // Jika tidak ada data dari network, coba cache
                        if (!Playlist.cached.categories.isNullOrEmpty()) {
                            setPlaylistToAdapter(Playlist.cached)
                        } else {
                            binding.loading.visibility = View.GONE
                            binding.rvCategory.visibility = View.VISIBLE
                            Toast.makeText(this@MainActivity, "Playlist kosong", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        setPlaylistToAdapter(playlistSet)
                    }
                }
            }
        }).process(useCache)
    }

    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    private fun openSearch() = SearchDialog().show(supportFragmentManager.beginTransaction(), null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_position", currentCategoryPosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }
}