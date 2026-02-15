package jp.masatonasu.wakemusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class AlarmListAdapter(
    private val onToggle: (AlarmItem, Boolean) -> Unit,
    private val onDelete: (AlarmItem) -> Unit,
) : RecyclerView.Adapter<AlarmListAdapter.VH>() {

    private val items: MutableList<AlarmItem> = mutableListOf()

    fun submitList(list: List<AlarmItem>) {
        items.clear()
        items.addAll(list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id })))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val sw: SwitchMaterial = itemView.findViewById(R.id.swEnabled)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: AlarmItem) {
            tvTime.text = item.formatTime()

            sw.setOnCheckedChangeListener(null)
            sw.isChecked = item.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                onToggle(item, checked)
            }

            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
