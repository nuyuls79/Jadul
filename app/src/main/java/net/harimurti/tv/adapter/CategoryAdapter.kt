package net.harimurti.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.Preferences
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Playlist
import kotlin.math.max

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    lateinit var context: Context

    // callback klik kategori
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
        val isFav = category?.isFavorite() == true && position == 0

        holder.binding.chAdapter =
            ChannelAdapter(category?.channels, position, isFav)

        holder.itemView.setOnClickListener {
            category?.let { listener?.invoke(it) }
        }

        val itemWidthDp = 150f
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        val screenWidthDp = screenWidthPx / density
        val spanCount = max(2, (screenWidthDp / itemWidthDp).toInt())

        holder.binding.rvChannels.layoutManager =
            GridLayoutManager(context, spanCount)

        val marginEnd = (200 * density).toInt()
        val wrapContent = LinearLayout.LayoutParams.WRAP_CONTENT
        if (position == 0) {
            holder.binding.textCategory.layoutParams =
                LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
                    setMargins(0, 0, marginEnd, 0)
                }
        }

        holder.bind(category)
    }

    override fun getItemCount(): Int = categories?.size ?: 0

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
