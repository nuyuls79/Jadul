package net.harimurti.tv

import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.View
import android.widget.PopupMenu
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var isTelevision = UiMode().isTelevision()
    private val preferences = Preferences()
    private val helper = PlaylistHelper()
    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private var currentCategoryPosition = 0
    private var currentCategory: Category? = null
    private var isDataLoaded = false

    // Untuk jam real-time
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var handler: Handler? = null
    private var runnable: Runnable? = null

    // Untuk pemilihan sumber playlist
    private var currentSourceIndex = 0
    private var sourceList: ArrayList<Source> = arrayListOf()

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

        // Inisialisasi daftar sumber dari preferences
        sourceList = preferences.sources ?: arrayListOf()
        if (sourceList.isEmpty()) {
            Toast.makeText(this, "Tidak ada sumber playlist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Tentukan indeks sumber aktif
        currentSourceIndex = sourceList.indexOfFirst { it.active }
        if (currentSourceIndex < 0) {
            currentSourceIndex = 0
            sourceList[currentSourceIndex].active = true
            preferences.sources = sourceList
        }

        // Setup tombol pemilih sumber
        setupSourceSelector()

        // Tombol-tombol lainnya
        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (savedInstanceState != null) {
            currentCategoryPosition = savedInstanceState.getInt("current_position", 0)
            currentSourceIndex = savedInstanceState.getInt("current_source_index", 0)
        }

        setupAdapter()
        startClock()

        // Muat playlist dari sumber yang aktif
        loadPlaylistFromSource(sourceList[currentSourceIndex])
    }

    override fun onResume() {
        super.onResume()
        startClock()
        // Mungkin ada perubahan sumber dari dialog? Reload sourceList
        val newList = preferences.sources ?: arrayListOf()
        if (newList != sourceList) {
            sourceList = newList
            currentSourceIndex = sourceList.indexOfFirst { it.active }
            if (currentSourceIndex < 0) currentSourceIndex = 0
            updateSourceDisplay()
            // Jangan reload otomatis, biarkan manual
        }
    }

    override fun onPause() {
        super.onPause()
        stopClock()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClock()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_position", currentCategoryPosition)
        outState.putInt("current_source_index", currentSourceIndex)
    }

    private fun startClock() {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                updateTime()
                handler?.postDelayed(this, 1000)
            }
        }
        handler?.post(runnable!!)
    }

    private fun stopClock() {
        handler?.removeCallbacks(runnable!!)
        handler = null
        runnable = null
    }

    private fun updateTime() {
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        binding.tvHeaderTime.text = "$timeStr WIB $dateStr"
    }

    private fun setupAdapter() {
        categoryAdapter = CategoryAdapter(arrayListOf())
        categoryAdapter.setOnCategoryClickListener { position ->
            onCategoryClicked(position)
        }
        binding.rvCategory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvCategory.adapter = categoryAdapter
        binding.setCatAdapter(categoryAdapter)
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
        val spanCount = if (isTelevision) 8 else 4
        binding.rvChannels.layoutManager = GridLayoutManager(this, spanCount)

        val channelAdapter = ChannelAdapter(channels, position, false)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun setPlaylistToAdapter(playlistSet: Playlist) {
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)

        val categories = playlistSet.categories ?: arrayListOf()

        categoryAdapter.updateData(categories)

        if (categories.isNotEmpty()) {
            if (currentCategoryPosition >= categories.size) {
                currentCategoryPosition = 0
            }
            categoryAdapter.setSelectedPosition(currentCategoryPosition)

            val targetPosition = if (currentCategoryPosition < categories.size)
                currentCategoryPosition else 0
            currentCategory = categories[targetPosition]
            displayChannels(currentCategory, targetPosition)
        } else {
            binding.rvChannels.adapter = null
            Toast.makeText(this, "Tidak ada kategori", Toast.LENGTH_SHORT).show()
        }

        Playlist.cached = playlistSet
        isDataLoaded = true

        binding.loading.visibility = View.GONE
        binding.rvCategory.visibility = View.VISIBLE
        binding.rvChannels.visibility = View.VISIBLE

        binding.rvCategory.post {
            categoryAdapter.notifyDataSetChanged()
            binding.rvCategory.scrollToPosition(currentCategoryPosition)
            binding.rvCategory.requestLayout()
            binding.rvCategory.visibility = View.INVISIBLE
            binding.rvCategory.visibility = View.VISIBLE
        }
    }

    // ================== Bagian Pemilih Sumber ==================

    private fun setupSourceSelector() {
        updateSourceDisplay()
        binding.tvSourceSelector.setOnClickListener {
            showSourceMenu()
        }
    }

    private fun updateSourceDisplay() {
        val source = sourceList.getOrNull(currentSourceIndex)
        binding.tvSourceSelector.text = getSourceDisplayName(source?.path ?: "")
    }

    private fun getSourceDisplayName(path: String): String {
        return when (path) {
            Preferences.DEFAULT_PLAYLIST_URL_1 -> "Live TV 1"
            Preferences.DEFAULT_PLAYLIST_URL_2 -> "Live TV 2"
            else -> "Sumber ${path.take(15)}..."
        }
    }

    private fun showSourceMenu() {
        val popup = PopupMenu(this, binding.tvSourceSelector)
        sourceList.forEachIndexed { index, source ->
            val title = getSourceDisplayName(source.path ?: "")
            popup.menu.add(0, index, index, title)
        }
        popup.setOnMenuItemClickListener { item ->
            val index = item.itemId
            if (index != currentSourceIndex) {
                // Update status aktif di sourceList
                sourceList.forEachIndexed { i, src ->
                    src.active = (i == index)
                }
                // Simpan perubahan ke preferences
                preferences.sources = sourceList
                // Update indeks dan tampilan
                currentSourceIndex = index
                updateSourceDisplay()
                // Muat playlist dari sumber baru
                loadPlaylistFromSource(sourceList[index])
            }
            true
        }
        popup.show()
    }

    private fun loadPlaylistFromSource(source: Source) {
        // Tampilkan loading
        binding.loading.visibility = View.VISIBLE
        binding.rvCategory.visibility = View.GONE
        binding.rvChannels.visibility = View.GONE

        val playlistSet = Playlist()
        // Buat daftar berisi hanya satu sumber yang dipilih
        val singleSourceList = arrayListOf(source)

        SourcesReader().set(singleSourceList, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                    // Jika gagal, mungkin kembali ke cache atau sumber sebelumnya
                    if (!Playlist.cached.isCategoriesEmpty()) {
                        setPlaylistToAdapter(Playlist.cached)
                    }
                }
            }
            override fun onResponse(playlist: Playlist?) {
                playlist?.let { playlistSet.mergeWith(it) }
            }
            override fun onFinish() {
                runOnUiThread {
                    setPlaylistToAdapter(playlistSet)
                }
            }
        }).process(true) // gunakan cache jika ada?
    }

    // ================== Fungsi yang sudah ada ==================

    private fun updatePlaylist(useCache: Boolean) {
        // Panggil load dengan sumber saat ini
        loadPlaylistFromSource(sourceList[currentSourceIndex])
    }

    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    private fun openSearch() = SearchDialog().show(supportFragmentManager.beginTransaction(), null)
}