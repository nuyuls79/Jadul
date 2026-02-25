package net.harimurti.tv.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.MainActivity
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.Preferences
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Playlist

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var selectedPosition = 0

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
        viewHolder.itemCatBinding.rvChannels.visibility = android.view.View.GONE
        viewHolder.itemCatBinding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            text = category?.category
            setTextColor(if (selectedPosition == position) Color.WHITE else Color.LTGRAY)
            val params = LinearLayout.LayoutParams(-2, -2).apply { setMargins(10, 5, 10, 5) }
            layoutParams = params
            setPadding(30, 15, 30, 15)
            if (selectedPosition == position) {
                try { setBackgroundResource(R.drawable.bg_button_selected) }
                catch (e: Exception) { setBackgroundColor(Color.parseColor("#E91E63")) }
            } else { setBackgroundColor(Color.TRANSPARENT) }
        }
        viewHolder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = viewHolder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            if (context is MainActivity) { (context as MainActivity).displayChannels(category?.channels) }
        }
        viewHolder.bind(category)
    }

    override fun getItemCount(): Int = categories?.size ?: 0
    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }
    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (categories?.get(0)?.isFavorite() == false) {
            categories.addFavorite(fav.channels)
            notifyItemInserted(0)
        } else {
            categories?.get(0)?.channels = fav.channels
            notifyItemChanged(0)
        }
    }
    fun removeFavorite() {
        if (categories?.get(0)?.isFavorite() == true) {
            categories.removeAt(0)
            notifyItemRemoved(0)
        }
    }
}