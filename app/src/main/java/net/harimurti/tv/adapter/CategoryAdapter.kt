package net.harimurti.tv.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.MainActivity
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.model.Category

class CategoryAdapter(
    private val categories: ArrayList<Category>?,
    private val onCategorySelected: (Category, Int) -> Unit  // Callback untuk kategori dipilih
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var selectedPos = 0
    private var lastSelectedCategory: Category? = null

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dataObj = categories?.get(position) ?: return
        
        // Sembunyikan channel recyclerView default
        holder.binding.rvChannels.visibility = android.view.View.GONE
        
        // Set text category
        holder.binding.textCategory.apply {
            text = dataObj.name ?: ""
            
            // Styling berdasarkan selected position
            if (selectedPos == position) {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#E91E63"))
                lastSelectedCategory = dataObj
            } else {
                setTextColor(Color.GRAY)
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        holder.itemView.setOnClickListener {
            if (selectedPos != holder.adapterPosition) {
                // Update selected position
                val oldPos = selectedPos
                selectedPos = holder.adapterPosition
                
                // Notify item changed untuk update warna
                notifyItemChanged(oldPos)
                notifyItemChanged(selectedPos)
                
                // Panggil callback dengan kategori yang dipilih
                onCategorySelected(dataObj, selectedPos)
            }
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0
    
    fun getSelectedCategory(): Category? = lastSelectedCategory
    
    fun updateSelectedPosition(position: Int) {
        if (position != selectedPos && position < itemCount) {
            val oldPos = selectedPos
            selectedPos = position
            lastSelectedCategory = categories?.get(position)
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPos)
        }
    }
    
    fun insertOrUpdateFavorite() { 
        notifyDataSetChanged() 
    }
    
    fun removeFavorite() { 
        notifyDataSetChanged() 
    }
}