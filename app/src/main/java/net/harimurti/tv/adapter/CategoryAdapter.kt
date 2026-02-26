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

class CategoryAdapter(private val listKategori: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var ctx: Context
    private var selectedPos = 0

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        ctx = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(ctx), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dataKategori = listKategori?.get(position)
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            // Akses field category dari model Category, bukan dari Char
            text = dataKategori?.category ?: "" 
            setTextColor(if (selectedPos == position) Color.WHITE else Color.GRAY)
            setBackgroundColor(if (selectedPos == position) Color.parseColor("#E91E63") else Color.TRANSPARENT)
            setPadding(24, 12, 24, 12)
        }

        holder.itemView.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            if (ctx is MainActivity) {
                (ctx as MainActivity).displayChannels(dataKategori?.channels)
            }
        }
    }

    override fun getItemCount(): Int = listKategori?.size ?: 0
    fun insertOrUpdateFavorite() { notifyDataSetChanged() }
    fun removeFavorite() { notifyDataSetChanged() }
}