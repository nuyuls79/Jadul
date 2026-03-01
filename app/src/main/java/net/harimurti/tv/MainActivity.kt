package net.harimurti.tv

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
import net.himurti.tv.extension.*
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

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var handler: Handler? = null
    private var runnable: Runnable? = null

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

        binding.tvHeaderTitle.text = "LIVE TV 1 VIP"

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { updatePlaylist(false) }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, IntentFilter(MAIN_CALLBACK))

        if (savedInstanceState != null) {
            currentCategoryPosition = savedInstanceState.getInt("current_position", 0)
        }

        setupAdapter()
        startClock()

        // Cek apakah sudah ada data cache
        if (!Playlist.cached.isCategoriesEmpty()) {
            Log.d("MainActivity", "Data cache ditemukan, langsung tampilkan")
            setPlaylistToAdapter(Playlist.cached)
        } else {
            Log.d("MainActivity", "Tidak ada cache, memuat playlist...")
            updatePlaylist(true)
        }
    }

    override fun onResume() {
        super.onResume()
        startClock()
        // Jika data sudah dimuat tapi kategori belum muncul, paksa refresh
        if (isDataLoaded && categoryAdapter.itemCount == 0) {
            categoryAdapter.notifyDataSetChanged()
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
        Log.d("MainActivity", "setPlaylistToAdapter dipanggil")
        val fav = helper.readFavorites().trimNotExistFrom(playlistSet)
        if (fav?.channels?.isNotEmpty() == true) playlistSet.insertFavorite(fav.channels)

        val categories = playlistSet.categories ?: arrayListOf()
        Log.d("MainActivity", "Jumlah kategori: ${categories.size}")

        // Update adapter
        categoryAdapter.updateData(categories)

        if (categories.isNotEmpty()) {
            if (currentCategoryPosition >= categories.size) {
                currentCategoryPosition = 0
            }
            categoryAdapter.setSelectedPosition(currentCategoryPosition)

            val targetPosition = if (currentCategoryPosition < categories.size) currentCategoryPosition else 0
            currentCategory = categories[targetPosition]
            displayChannels(currentCategory, targetPosition)
        } else {
            binding.rvChannels.adapter = null
            Toast.makeText(this, "Tidak ada kategori", Toast.LENGTH_SHORT).show()
        }

        Playlist.cached = playlistSet
        isDataLoaded = true

        // Sembunyikan loading dan tampilkan konten
        binding.loading.visibility = View.GONE
        binding.rvCategory.visibility = View.VISIBLE
        binding.rvChannels.visibility = View.VISIBLE

        // Paksa RecyclerView untuk menggambar ulang
        binding.rvCategory.post {
            categoryAdapter.notifyDataSetChanged()
            // Scroll ke posisi yang dipilih (opsional)
            binding.rvCategory.scrollToPosition(currentCategoryPosition)
        }
    }

    private fun updatePlaylist(useCache: Boolean) {
        Log.d("MainActivity", "updatePlaylist dipanggil, useCache=$useCache")
        binding.loading.visibility = View.VISIBLE
        binding.rvCategory.visibility = View.GONE
        binding.rvChannels.visibility = View.GONE

        val startTime = System.currentTimeMillis()
        val playlistSet = Playlist()
        SourcesReader().set(preferences.sources, object: SourcesReader.Result {
            override fun onError(source: String, error: String) {
                Log.e("MainActivity", "Error: $error")
                runOnUiThread {
                    binding.loading.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Gagal memuat playlist", Toast.LENGTH_SHORT).show()
                    // Jika ada cache, tampilkan
                    if (!Playlist.cached.isCategoriesEmpty()) {
                        setPlaylistToAdapter(Playlist.cached)
                    }
                }
            }
            override fun onResponse(playlist: Playlist?) {
                playlist?.let { playlistSet.mergeWith(it) }
            }
            override fun onFinish() {
                val elapsed = System.currentTimeMillis() - startTime
                val delay = if (elapsed < 500) 500 - elapsed else 0
                Handler(Looper.getMainLooper()).postDelayed({
                    runOnUiThread {
                        setPlaylistToAdapter(playlistSet)
                    }
                }, delay)
            }
        }).process(useCache)
    }

    private fun openSettings() = SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    private fun openSearch() = SearchDialog().show(supportFragmentManager.beginTransaction(), null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_position", currentCategoryPosition)
    }
}