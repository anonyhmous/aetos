package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.databinding.ItemUiMutationBinding
import com.example.model.MutationSeverity
import com.example.model.UiMutationAlert

class UiMutationAdapter : ListAdapter<UiMutationAlert, UiMutationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUiMutationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemUiMutationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UiMutationAlert) {
            val context = binding.root.context
            binding.tvMutationAppName.text = item.appName
            binding.tvMutationTypeBadge.text = item.mutationType.name

            when (item.severity) {
                MutationSeverity.CRITICAL_REDESIGN -> {
                    binding.tvMutationTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.alert_red))
                }
                MutationSeverity.WARNING_SHIFT -> {
                    binding.tvMutationTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                }
                MutationSeverity.INFO_PATCH -> {
                    binding.tvMutationTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_cyan_info))
                }
            }

            binding.tvMutationDesc.text = item.description
            binding.tvMutationPatch.text = "⚡ Auto-Patch (${(item.confidenceScore * 100).toInt()}% Conf): ${item.patchActionMessage}"
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<UiMutationAlert>() {
            override fun areItemsTheSame(oldItem: UiMutationAlert, newItem: UiMutationAlert): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: UiMutationAlert, newItem: UiMutationAlert): Boolean {
                return oldItem == newItem
            }
        }
    }
}
