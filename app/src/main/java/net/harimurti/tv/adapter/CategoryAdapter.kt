package net.harimurti.tv.adapter

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.model.Category

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var selectedPos = 0
    private var onCategoryClickListener: ((Int) -> Unit)? = null

    init {
        Log.d("CategoryAdapter", "Adapter created with ${categories?.size} categories")
    }

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        Log.d("CategoryAdapter", "ViewHolder created")
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Log.d("CategoryAdapter", "onBindViewHolder position $position")
        
        val dataObj = categories?.get(position)
        
        if (dataObj == null) {
            Log.e("CategoryAdapter", "dataObj is null at position $position")
            holder.binding.textCategory.text = "Unknown"
            return
        }
        
        Log.d("CategoryAdapter", "Binding category: ${dataObj.name}")
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            text = dataObj.name ?: "No Name"
            
            if (selectedPos == position) {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#E91E63"))
            } else {
                setTextColor(Color.GRAY)
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        holder.itemView.setOnClickListener {
            Log.d("CategoryAdapter", "Item clicked at position $position")
            if (selectedPos != holder.adapterPosition) {
                val oldPos = selectedPos
                selectedPos = holder.adapterPosition
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPos)
                
                onCategoryClickListener?.invoke(selectedPos)
            }
        }
    }

    override fun getItemCount(): Int {
        val count = categories?.size ?: 0
        Log.d("CategoryAdapter", "getItemCount = $count")
        return count
    }
    
    fun setOnCategoryClickListener(listener: (Int) -> Unit) {
        this.onCategoryClickListener = listener
    }
    
    fun setSelectedPosition(position: Int) {
        Log.d("CategoryAdapter", "setSelectedPosition: $position")
        if (position != selectedPos && position < itemCount) {
            val oldPos = selectedPos
            selectedPos = position
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPos)
        }
    }
    
    fun updateData(newCategories: ArrayList<Category>?) {
        Log.d("CategoryAdapter", "updateData called with ${newCategories?.size} categories")
        
        categories?.clear()
        newCategories?.let { 
            categories?.addAll(it)
            Log.d("CategoryAdapter", "Added ${it.size} categories")
            
            // Log semua kategori
            it.forEachIndexed { index, category ->
                Log.d("CategoryAdapter", "  [$index] ${category.name}")
            }
        }
        
        notifyDataSetChanged()
        Log.d("CategoryAdapter", "notifyDataSetChanged called")
    }
    
    fun insertOrUpdateFavorite() { 
        Log.d("CategoryAdapter", "insertOrUpdateFavorite")
        notifyDataSetChanged() 
    }
    
    fun removeFavorite() { 
        Log.d("CategoryAdapter", "removeFavorite")
        notifyDataSetChanged() 
    }
}