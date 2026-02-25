package net.harimurti.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.harimurti.tv.BR
import net.harimurti.tv.R
import net.harimurti.tv.databinding.ItemCategoryBinding
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.Preferences
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Playlist
import kotlin.math.max
import kotlin.math.round

class CategoryAdapter(private val categories: ArrayList<Category>?) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    lateinit var context: Context

    class ViewHolder(var itemCatBinding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(itemCatBinding.root) {

        fun bind(obj: Any?) {
            itemCatBinding.setVariable(BR.catModel, obj)
            itemCatBinding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding: ItemCategoryBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.item_category, parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val category: Category? = categories?.get(position)
        val chCount = category?.channels?.size ?: 0
        val isFav = category.isFavorite() && position == 0

        // 设置频道 Adapter（保持不变）
        viewHolder.itemCatBinding.chAdapter = ChannelAdapter(category?.channels, position, isFav)

        // ---------------------- 关键修改开始 ----------------------
        // 自动计算列数：每个频道项大约 150dp 宽（可根据实际 item_channel.xml 调整）
        val itemWidthDp = 100f  // 如果频道名称较长可改为 170~200；想更密集可改为 130
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        val screenWidthDp = screenWidthPx / density

        // 计算能放多少列，至少 2 列（竖屏手机通常 3~4 列，电视/横屏会更多）
        val spanCount = max(2, (screenWidthDp / itemWidthDp).toInt())

        // 使用垂直方向的普通 GridLayoutManager（规整、美观、无横向滑动）
        viewHolder.itemCatBinding.rvChannels.layoutManager =
            GridLayoutManager(context, spanCount, GridLayoutManager.VERTICAL, false)
        // ---------------------- 关键修改结束 ----------------------

        // 原有标题 margin 处理（收藏夹右边留空，便于区分）
        val marginEnd = (200 * context.resources.displayMetrics.density).toInt()
        val wrapContent = LinearLayout.LayoutParams.WRAP_CONTENT
        if (position == 0) {
            viewHolder.itemCatBinding.textCategory.layoutParams =
                LinearLayout.LayoutParams(wrapContent, wrapContent).apply {
                    setMargins(0, 0, marginEnd, 0)
                }
        }

        viewHolder.bind(category)
    }

    override fun getItemCount(): Int {
        return categories?.size ?: 0
    }

    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun insertOrUpdateFavorite() {
        val fav = Playlist.favorites
        if (Preferences().sortFavorite) fav.sort()
        if (categories?.get(0)?.isFavorite() == false) {
            val lastCount = itemCount
            categories.addFavorite(fav.channels)
            notifyItemInserted(0)
            notifyItemRangeChanged(1, lastCount)
        } else {
            categories?.get(0)?.channels = fav.channels
            notifyItemChanged(0)
        }
    }

    fun removeFavorite() {
        if (categories?.get(0)?.isFavorite() == true) {
            categories.removeAt(0)
            notifyItemRemoved(0)
            notifyItemRangeChanged(0, itemCount)
        }
    }
}
