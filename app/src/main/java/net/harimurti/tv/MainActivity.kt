package net.harimurti.tv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import net.harimurti.tv.adapter.CategoryAdapter
import net.harimurti.tv.adapter.ChannelAdapter
import net.harimurti.tv.databinding.ActivityMainBinding
import net.harimurti.tv.model.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tampilkan data awal jika ada cache
        if (!Playlist.cached.isCategoriesEmpty()) {
            setupUI(Playlist.cached)
        }
    }

    private fun setupUI(playlist: Playlist) {
        val catAdapter = CategoryAdapter(playlist.categories)
        binding.rvCategory.adapter = catAdapter

        // Set default grid ke kategori pertama
        if (!playlist.categories.isNullOrEmpty()) {
            updateChannelGrid(playlist.categories[0].channels, 0)
        }
    }

    fun updateChannelGrid(channels: ArrayList<Channel>?, catId: Int) {
        val chAdapter = ChannelAdapter(channels, catId, catId == 0)
        binding.rvChannels.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 5) // 5 Kolom
            adapter = chAdapter
        }
    }
}