package net.harimurti.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.MainActivity
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.extra.Preferences
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Playlist

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    lateinit var context: Context

    class ViewHolder(var itemCatBinding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(itemCatBinding.root) {
        fun bind(obj: Any?) {
            itemCatBinding.setVariable(BR.catModel, obj)
            itemCatBinding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val category: Category? = categories?.get(position)
        viewHolder.bind(category)

        viewHolder.itemView.setOnClickListener {
            if (context is MainActivity) {
                (context as MainActivity).updateChannelGrid(category?.channels, position)
            }
        }

        viewHolder.itemView.setOnFocusChangeListener { v, hasFocus ->
            v.isSelected = hasFocus
            if (hasFocus && context is MainActivity) {
                (context as MainActivity).updateChannelGrid(category?.channels, position)
            }
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0

    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (Preferences().sortFavorite) fav.sort()
        if (categories?.get(0)?.isFavorite() == false) {
            categories?.add(0, Category().apply { name = context.getString(R.string.favorite); channels = fav.channels })
            notifyItemInserted(0)
        } else {
            categories?.get(0)?.channels = fav.channels
            notifyItemChanged(0)
        }
    }

    fun removeFavorite() {
        if (categories?.get(0)?.isFavorite() == true) {
            categories.removeAt(0); notifyItemRemoved(0)
        }
    }
}
