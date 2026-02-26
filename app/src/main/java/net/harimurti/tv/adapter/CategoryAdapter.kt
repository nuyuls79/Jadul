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

class CategoryAdapter(private val listCat: ArrayList<Category>?) :
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
        val catData = listCat?.get(position)
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            // Menggunakan catData?.category agar tidak bentrok dengan Char.category
            text = catData?.category ?: "" 
            setTextColor(if (selectedPos == position) Color.WHITE else Color.GRAY)
            // Menggunakan warna HEX langsung agar tidak butuh file XML tambahan (menghindari Unresolved reference)
            setBackgroundColor(if (selectedPos == position) Color.parseColor("#E91E63") else Color.TRANSPARENT)
            setPadding(24, 12, 24, 12)
        }

        holder.itemView.setOnClickListener {
            val oldPos = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPos)
            
            if (context is MainActivity) {
                (context as MainActivity).displayChannels(catData?.channels)
            }
        }
    }

    override fun getItemCount(): Int = listCat?.size ?: 0
    fun insertOrUpdateFavorite() { notifyDataSetChanged() }
    fun removeFavorite() { notifyDataSetChanged() }
}