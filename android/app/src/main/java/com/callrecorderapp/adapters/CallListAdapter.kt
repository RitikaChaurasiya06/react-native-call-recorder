package com.callrecorderapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callrecorderapp.R
import com.callrecorderapp.databinding.CallListItemBinding
import com.callrecorderapp.models.RecordedCall
import java.text.SimpleDateFormat
import java.util.*

class CallListAdapter(private val onClick: (RecordedCall) -> Unit) :
    ListAdapter<RecordedCall, CallListAdapter.CallViewHolder>(CallDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = CallListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        val call = getItem(position)
        holder.bind(call)
    }

    inner class CallViewHolder(private val binding: CallListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                onClick(getItem(adapterPosition))
            }
        }

        fun bind(call: RecordedCall) {
            binding.textViewPhoneNumber.text = call.phoneNumber
            binding.textViewDuration.text = "Duration: ${formatDuration(call.callDuration)}"
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            binding.textViewDate.text = "Date: ${sdf.format(call.startTime)}"

            val callTypeIcon = if (call.callType == "Incoming") {
                R.drawable.ic_call_incoming
            } else {
                R.drawable.ic_call_outgoing
            }
            binding.imageViewCallType.setImageResource(callTypeIcon)
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

class CallDiffCallback : DiffUtil.ItemCallback<RecordedCall>() {
    override fun areItemsTheSame(oldItem: RecordedCall, newItem: RecordedCall): Boolean {
        return oldItem.fileName == newItem.fileName
    }

    override fun areContentsTheSame(oldItem: RecordedCall, newItem: RecordedCall): Boolean {
        return oldItem == newItem
    }
}