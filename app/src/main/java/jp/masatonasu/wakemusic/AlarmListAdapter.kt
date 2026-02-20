package jp.masatonasu.wakemusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlarmListAdapter(
    private var items: List<AlarmItem>,
    private val onTimeClick: (AlarmItem) -> Unit,
    private val onToggleEnabled: (AlarmItem, Boolean) -> Unit,
    private val onToggleWeekdaysOnly: (AlarmItem, Boolean) -> Unit,
    private val onDelete: (AlarmItem) -> Unit,
) : RecyclerView.Adapter<AlarmListAdapter.VH>() {

    fun submitList(newItems: List<AlarmItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTime.text = item.formatTime()

        holder.swEnabled.setOnCheckedChangeListener(null)
        holder.swEnabled.isChecked = item.enabled
        holder.swEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggleEnabled(item, isChecked)
        }

        holder.cbWeekdaysOnly.setOnCheckedChangeListener(null)
        holder.cbWeekdaysOnly.isChecked = item.weekdaysOnly
        holder.cbWeekdaysOnly.setOnCheckedChangeListener { _, isChecked ->
            onToggleWeekdaysOnly(item, isChecked)
        }

        holder.tvTime.setOnClickListener { onTimeClick(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val cbWeekdaysOnly: CheckBox = v.findViewById(R.id.cbWeekdaysOnly)
        val swEnabled: Switch = v.findViewById(R.id.swEnabled)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }
}
