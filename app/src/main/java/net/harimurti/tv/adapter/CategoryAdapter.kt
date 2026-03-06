package net.harimurti.tv.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.R
import net.harimurti.tv.model.Category

class CategoryAdapter(
    private val categories: ArrayList<Category> = ArrayList()
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private var selectedPosition = 0
    private var listener: ((Int) -> Unit)? = null

    fun setOnCategoryClickListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun updateData(newCategories: List<Category>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return categories.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val category = categories[position]

        holder.txtCategory.text = category.name

        holder.itemView.isSelected = position == selectedPosition

        holder.itemView.setOnClickListener {

            val oldPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)

            listener?.invoke(selectedPosition)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val txtCategory: TextView = itemView.findViewById(R.id.txtCategory)

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }
    }
}