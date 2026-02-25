package net.harimurti.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Playlist
import net.harimurti.tv.extra.Preferences

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    lateinit var context: Context

    // callback untuk kirim kategori ke MainActivity
    private var listener: ((Category) -> Unit)? = null

    fun setOnCategorySelected(listener: (Category) -> Unit) {
        this.listener = listener
    }

    class ViewHolder(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(obj: Any?) {
            binding.setVariable(BR.catModel, obj)
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.item_category,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories?.get(position)

        holder.bind(category)

        // klik kategori → kirim ke MainActivity
        holder.itemView.setOnClickListener {
            category?.let { listener?.invoke(it) }
        }
    }

    override fun getItemCount(): Int {
        return categories?.size ?: 0
    }

    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (Preferences().sortFavorite) fav.sort()

        if (categories?.get(0)?.isFavorite() == false) {
            val lastCount = itemCount
            categories.addFavorite(fav.channels)
            notifyItemInserted(0)
            notifyItemRangeChanged(1, lastCount)
        } else {
            categories?.get(0)?.channels = fav.channels
            notifyItemChanged(0)
        }
    }

    fun removeFavorite() {
        if (categories?.get(0)?.isFavorite() == true) {
            categories.removeAt(0)
            notifyItemRemoved(0)
            notifyItemRangeChanged(0, itemCount)
        }
    }
    }
