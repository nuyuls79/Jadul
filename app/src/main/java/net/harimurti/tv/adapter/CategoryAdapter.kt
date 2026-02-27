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

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var selectedPos = 0

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
        
        // Sembunyikan RecyclerView dulu
        holder.binding.rvChannels.visibility = android.view.View.GONE
        
        // Set text category
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            text = dataObj.name ?: ""  // Gunakan name, bukan category
            
            // Styling
            setTextColor(if (selectedPos == position) Color.WHITE else Color.GRAY)
            setBackgroundColor(if (selectedPos == position) Color.parseColor("#E91E63") else Color.TRANSPARENT)
        }

        holder.itemView.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            if (context is MainActivity) {
                (context as MainActivity).displayChannels(dataObj.channels)
            }
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0
    
    fun insertOrUpdateFavorite() { 
        notifyDataSetChanged() 
    }
    
    fun removeFavorite() { 
        notifyDataSetChanged() 
    }
}