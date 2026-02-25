package net.harimurti.tv.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.MainActivity
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.model.Category

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val category = categories?.get(position)
        viewHolder.binding.setVariable(BR.catModel, category)

        viewHolder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                (viewHolder.itemView.context as? MainActivity)?.updateChannelGrid(category?.channels, position)
            }
        }
        
        viewHolder.itemView.setOnClickListener {
            (viewHolder.itemView.context as? MainActivity)?.updateChannelGrid(category?.channels, position)
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0
}