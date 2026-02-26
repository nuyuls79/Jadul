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
        // Ambil data tanpa menggunakan nama variabel yang berisiko bentrok
        val item = listKategori?.get(position)
        
        holder.binding.rvChannels.visibility = android.view.View.GONE
        holder.binding.textCategory.apply {
            visibility = android.view.View.VISIBLE
            
            // Akses property .category dari model secara paksa/eksplisit
            text = item?.category ?: "" 
            
            // Menggunakan warna HEX langsung, tidak memanggil XML bg_button_selected
            // Ini untuk memastikan tidak ada error 'Unresolved reference' pada resource
            if (selectedPos == position) {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#E91E63"))
            } else {
                setTextColor(Color.GRAY)
                setBackgroundColor(Color.TRANSPARENT)
            }
            setPadding(24, 12, 24, 12)
        }

        holder.itemView.setOnClickListener {
            val old = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
            
            if (context is MainActivity) {
                // Langsung akses field channels dari model item
                (context as MainActivity).displayChannels(item?.channels)
            }
        }
    }

    override fun getItemCount(): Int = listKategori?.size ?: 0
    fun insertOrUpdateFavorite() { notifyDataSetChanged() }
    fun removeFavorite() { notifyDataSetChanged() }
}