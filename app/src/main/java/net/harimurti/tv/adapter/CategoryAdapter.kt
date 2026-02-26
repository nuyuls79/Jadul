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

class CategoryAdapter(private val listUtama: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private lateinit var ctx: Context
    private var posDipilih = 0

    class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        ctx = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(ctx), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Menggunakan nama variabel 'dataSatu' agar compiler tidak bentrok dengan keyword 'category'
        val dataSatu = listUtama?.get(position)
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            
            // Tetap memanggil field .category dari model, tapi melalui pointer dataSatu
            text = dataSatu?.category ?: "" 
            
            setTextColor(if (posDipilih == position) Color.WHITE else Color.GRAY)
            setBackgroundColor(if (posDipilih == position) Color.parseColor("#E91E63") else Color.TRANSPARENT)
            setPadding(24, 12, 24, 12)
        }

        holder.itemView.setOnClickListener {
            val posLama = posDipilih
            posDipilih = holder.adapterPosition
            notifyItemChanged(posLama)
            notifyItemChanged(posDipilih)
            
            if (ctx is MainActivity) {
                (ctx as MainActivity).displayChannels(dataSatu?.channels)
            }
        }
    }

    override fun getItemCount(): Int = listUtama?.size ?: 0
    fun insertOrUpdateFavorite() { notifyDataSetChanged() }
    fun removeFavorite() { notifyDataSetChanged() }
}