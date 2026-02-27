package net.harimurti.tv

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
                UPDATE_PLAYLIST -> {
                    Log.d("MainActivity", "Broadcast: UPDATE_PLAYLIST")
                    updatePlaylist(false)
                }
                INSERT_FAVORITE -> {
                    Log.d("MainActivity", "Broadcast: INSERT_FAVORITE")
                    if (::categoryAdapter.isInitialized) {
                        categoryAdapter.insertOrUpdateFavorite()
                    }
                }
                REMOVE_FAVORITE -> {
                    Log.d("MainActivity", "Broadcast: REMOVE_FAVORITE")
                    if (::categoryAdapter.isInitialized) {
                        categoryAdapter.removeFavorite()
                    }
                }
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
        Log.d("MainActivity", "onCreate started")
        
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        setupAdapter()
        restoreSavedState(savedInstanceState)
        loadInitialData()
        
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))
    }

    private fun setupClickListeners() {
        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { 
            Log.d("MainActivity", "Refresh button clicked")
            showLoading()
            updatePlaylist(false) 
        }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }
    }

    private fun restoreSavedState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            currentCategoryPosition = savedInstanceState.getInt("current_position", 0)
            Log.d("MainActivity", "Restored position: $currentCategoryPosition")
        }
    }

    private fun loadInitialData() {
        if (!Playlist.cached.isCategoriesEmpty()) {
            Log.d("MainActivity", "Using cached playlist with ${Playlist.cached.categories?.size} categories")
            hideLoadingAndShowContent()
            setPlaylistToAdapter(Playlist.cached)
        } else {
            Log.d("MainActivity", "No cache, loading playlist from URL: ${preferences.sources}")
            showLoading()
            updatePlaylist(true)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume - isDataLoaded: $isDataLoaded")
        
        if (!isDataLoaded) {
            if (Playlist.cached.categories.isNullOrEmpty()) {
                Log.d("MainActivity", "onResume: no data, loading...")
                showLoading()
                updatePlaylist(true)
            } else {
                Log.d("MainActivity", "onResume: using cache with ${Playlist.cached.categories?.size} categories")
                hideLoadingAndShowContent()
                setPlaylistToAdapter(Playlist.cached)
            }
        }
    }

    private fun setupAdapter() {
        Log.d("MainActivity", "setupAdapter called")
        
        categoryAdapter = CategoryAdapter(arrayListOf())
        categoryAdapter.setOnCategoryClickListener { position ->
            Log.d("MainActivity", "Category clicked: $position")
            onCategoryClicked(position)
        }
        
        binding.rvCategory.adapter = categoryAdapter
        binding.setCatAdapter(categoryAdapter)
        
        // Set layout manager for grid channels
        val spanCount = if (isTelevision) 8 else 6
        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)
        
        Log.d("MainActivity", "Adapter setup complete")
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
        Log.d("MainActivity", "onCategoryClicked: $position")
        val category = Playlist.cached.categories?.getOrNull(position)
        if (category != null && currentCategoryPosition != position) {
            currentCategoryPosition = position
            currentCategory = category
            displayChannels(category, position)
        }
    }

    private fun displayChannels(category: Category?, position: Int) {
        Log.d("MainActivity", "displayChannels for position $position, category: ${category?.name}")
        
        currentCategoryPosition = position
        currentCategory = category
        
        val channels = category?.channels
        val spanCount = if (isTelevision) 8 else 6
        (binding.rvChannels.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        
        val channelAdapter = ChannelAdapter(channels, position, false)
        binding.rvChannels.adapter = channelAdapter
        
        Log.d("MainActivity", "Channels displayed: ${channels?.size} channels with spanCount=$spanCount")
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        Log.d("MainActivity", "setPlaylistToAdapter started")
        
        // Process favorites
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) {
            playlistSet.insertFavorite(fav.channels)
            Log.d("MainActivity", "Favorites added: ${fav.channels?.size} channels")
        }
        
        val categories = playlistSet.categories ?: arrayListOf()
        Log.d("MainActivity", "Categories from playlist: ${categories.size}")
        
        // Log all category names
        categories.forEachIndexed { index, category ->
            Log.d("MainActivity", "  Category[$index]: '${category.name}' with ${category.channels?.size} channels")
        }
        
        if (::categoryAdapter.isInitialized) {
            Log.d("MainActivity", "Updating existing adapter")
            categoryAdapter.updateData(categories)
            
            if (categories.isNotEmpty()) {
                // Adjust position if needed
                if (currentCategoryPosition >= categories.size) {
                    currentCategoryPosition = 0
                    Log.d("MainActivity", "Reset position to 0")
                }
                
                categoryAdapter.setSelectedPosition(currentCategoryPosition)
                
                val targetPosition = if (currentCategoryPosition < categories.size) 
                    currentCategoryPosition else 0
                currentCategory = categories[targetPosition]
                displayChannels(currentCategory, targetPosition)
                
                Log.d("MainActivity", "Selected category: '${currentCategory?.name}' at position $targetPosition")
            } else {
                binding.rvChannels.adapter = null
                Log.w("MainActivity", "No categories to display")
                Toast.makeText(this, "Tidak ada kategori dari playlist", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("MainActivity", "Adapter not initialized!")
            setupAdapter()
            setPlaylistToAdapter(playlistSet)
            return
        }
        
        Playlist.cached = playlistSet
        isDataLoaded = true
        hideLoadingAndShowContent()
        
        Log.d("MainActivity", "setPlaylistToAdapter completed")
    }

    private fun updatePlaylist(useCache: Boolean) {
        Log.d("MainActivity", "updatePlaylist called with useCache=$useCache, URL: ${preferences.sources}")
        showLoading()
        
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                Log.e("MainActivity", "Error loading playlist: $error from source $source")
                runOnUiThread {
                    if (!Playlist.cached.categories.isNullOrEmpty()) {
                        Log.d("MainActivity", "Using cache after error")
                        setPlaylistToAdapter(Playlist.cached)
                    } else {
                        hideLoadingAndShowContent()
                        Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            override fun onResponse(playlist: Playlist?) { 
                playlist?.let { 
                    playlistSet.mergeWith(it)
                    Log.d("MainActivity", "onResponse: merged ${it.categories?.size} categories")
                }
            }
            
            override fun onFinish() { 
                Log.d("MainActivity", "onFinish: total categories ${playlistSet.categories?.size}")
                runOnUiThread {
                    if (playlistSet.categories.isNullOrEmpty()) {
                        if (!Playlist.cached.categories.isNullOrEmpty()) {
                            Log.d("MainActivity", "No new data, using cache")
                            setPlaylistToAdapter(Playlist.cached)
                        } else {
                            hideLoadingAndShowContent()
                            Toast.makeText(this@MainActivity, "Playlist kosong atau format tidak sesuai", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.d("MainActivity", "Setting new playlist to adapter")
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