package net.harimurti.tv

import android.content.*
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import net.harimurti.tv.adapter.CategoryAdapter
import net.harimurti.tv.adapter.ChannelAdapter
import net.harimurti.tv.databinding.ActivityMainBinding
import net.harimurti.tv.dialog.SearchDialog
import net.harimurti.tv.dialog.SettingDialog
import net.harimurti.tv.extension.SourcesReader
import net.harimurti.tv.extra.Preferences
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.Playlist

open class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCategory.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.rvChannels.layoutManager = GridLayoutManager(this, 4)

        binding.buttonSearch.setOnClickListener { openSearch() }
        binding.buttonRefresh.setOnClickListener { loadPlaylist() }
        binding.buttonSettings.setOnClickListener { openSettings() }
        binding.buttonExit.setOnClickListener { finish() }

        loadPlaylist()
    }

    private fun loadPlaylist() {

        binding.loading.visibility = View.VISIBLE

        val playlistSet = Playlist()

        SourcesReader().set(Preferences().sources, object: SourcesReader.Result {

            override fun onError(source: String, error: String) {}

            override fun onResponse(playlist: Playlist?) {
                if (playlist != null) playlistSet.mergeWith(playlist)
            }

            override fun onFinish() {

                binding.loading.visibility = View.GONE

                if (playlistSet.isCategoriesEmpty()) return

                adapter = CategoryAdapter(playlistSet.categories)

                binding.rvCategory.adapter = adapter
                binding.catAdapter = adapter

                adapter.setOnCategorySelected { category ->
                    showChannels(category.channels)
                }

                // tampilkan kategori pertama
                showChannels(playlistSet.categories[0].channels)
            }
        }).process(false)
    }

    private fun showChannels(channels: ArrayList<Channel>?) {

        if (channels == null || channels.isEmpty()) return

        binding.rvChannels.post {
            binding.rvChannels.adapter =
                ChannelAdapter(channels, 0, false)
            binding.rvChannels.visibility = View.VISIBLE
        }
    }

    private fun openSettings() {
        SettingDialog().show(supportFragmentManager.beginTransaction(), null)
    }

    private fun openSearch() {
        SearchDialog().show(supportFragmentManager.beginTransaction(), null)
    }
}