package net.harimurti.tv

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import net.harimurti.tv.adapter.CategoryAdapter
import net.harimurti.tv.adapter.ChannelAdapter
import net.harimurti.tv.databinding.ActivityMainBinding
import net.harimurti.tv.extra.PlaylistLoader
import net.harimurti.tv.model.*

open class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // kategori horizontal
        binding.rvCategory.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // grid channel
        binding.rvChannels.layoutManager =
            GridLayoutManager(this, 4)

        loadPlaylist()
    }

    private fun loadPlaylist() {
        PlaylistLoader(this).load(false) { playlist ->
            showPlaylist(playlist)
        }
    }

    private fun showPlaylist(playlist: Playlist) {

        adapter = CategoryAdapter(playlist.categories)

        // ⭐ penting
        binding.rvCategory.adapter = adapter
        binding.catAdapter = adapter

        // klik kategori → tampilkan channel
        adapter.setOnCategorySelected {
            binding.rvChannels.adapter =
                ChannelAdapter(it.channels, 0, false)
        }

        // tampilkan kategori pertama
        if (playlist.categories.isNotEmpty()) {
            binding.rvChannels.adapter =
                ChannelAdapter(playlist.categories[0].channels, 0, false)
        }
    }
}