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
    private var selectedPosition = 0

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = categories?.get(position)
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            // PERBAIKAN: Gunakan akses yang aman
            text = item?.category ?: "" 
            setTextColor(if (selectedPosition == position) Color.WHITE else Color.GRAY)
            // PERBAIKAN: Gunakan warna hex langsung agar tidak butuh file XML tambahan
            setBackgroundColor(if (selectedPosition == position) Color.parseColor("#E91E63") else Color.TRANSPARENT)
            setPadding(24, 12, 24, 12)
        }

        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            if (context is MainActivity) {
                (context as MainActivity).displayChannels(item?.channels)
            }
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0
    fun insertOrUpdateFavorite() { notifyDataSetChanged() }
    fun removeFavorite() { notifyDataSetChanged() }
}