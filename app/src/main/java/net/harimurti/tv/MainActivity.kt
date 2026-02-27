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
    private var isDataLoaded = false

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
        
        // PERBAIKAN: Setup adapter kosong tapi langsung tampilkan shimmer
        setupInitialView()
        
        // Cek apakah sudah ada cache
        if (!Playlist.cached.isCategoriesEmpty()) {
            // Jika sudah ada cache, langsung tampilkan
            hideLoadingAndShowContent()
            setPlaylistToAdapter(Playlist.cached)
        } else {
            // Tampilkan shimmer loading
            showLoading()
            updatePlaylist(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // PERBAIKAN: Jika data belum dimuat dan cache kosong, muat ulang
        if (!isDataLoaded) {
            if (Playlist.cached.categories.isNullOrEmpty()) {
                showLoading()
                updatePlaylist(true)
            } else {
                hideLoadingAndShowContent()
                setPlaylistToAdapter(Playlist.cached)
            }
        }
    }

    private fun setupInitialView() {
        // Setup adapter kosong
        categoryAdapter = CategoryAdapter(arrayListOf())
        categoryAdapter.setOnCategoryClickListener { position ->
            onCategoryClicked(position)
        }
        binding.rvCategory.adapter = categoryAdapter
    }

    private fun showLoading() {
        binding.loading.visibility = View.VISIBLE
        binding.loading.startShimmer()
        binding.rvCategory.visibility = View.GONE
        binding.rvChannels.visibility = View.GONE
    }

    private fun hideLoadingAndShowContent() {
        binding.loading.stopShimmer()
        binding.loading.visibility = View.GONE
        binding.rvCategory.visibility = View.VISIBLE
        binding.rvChannels.visibility = View.VISIBLE
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
        
        // Update adapter dengan data baru
        categoryAdapter.updateData(categories)
        
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
            // Jika tidak ada kategori
            binding.rvChannels.adapter = null
            Toast.makeText(this, "Tidak ada kategori", Toast.LENGTH_SHORT).show()
        }
        
        Playlist.cached = playlistSet
        isDataLoaded = true
        
        // Sembunyikan loading dan tampilkan konten
        hideLoadingAndShowContent()
    }

    private fun updatePlaylist(useCache: Boolean) {
        showLoading()
        
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                runOnUiThread {
                    // Jika error, coba tampilkan cache jika ada
                    if (!Playlist.cached.categories.isNullOrEmpty()) {
                        setPlaylistToAdapter(Playlist.cached)
                    } else {
                        hideLoadingAndShowContent()
                        Toast.makeText(this@MainActivity, "Error loading playlist: $error", Toast.LENGTH_SHORT).show()
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
                            hideLoadingAndShowContent()
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