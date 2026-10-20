package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.local.RideLogEntity
import com.example.databinding.ItemRideLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RideLogAdapter : ListAdapter<RideLogEntity, RideLogAdapter.RideLogViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideLogViewHolder {
        val binding = ItemRideLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RideLogViewHolder(private val binding: ItemRideLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RideLogEntity) {
            val context = binding.root.context
            binding.tvLogAppName.text = item.appName
            binding.tvLogTime.text = timeFormat.format(Date(item.timestamp))

            // Engine detection badge
            when (item.engineSource) {
                "OCR_VISION" -> {
                    binding.tvLogEngine.text = "OCR VISION"
                    binding.tvLogEngine.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                }
                "SIMULATION" -> {
                    binding.tvLogEngine.text = "SIMULATED"
                    binding.tvLogEngine.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                else -> {
                    binding.tvLogEngine.text = "NODE TREE"
                    binding.tvLogEngine.setTextColor(ContextCompat.getColor(context, R.color.accent_cyan_info))
                }
            }

            // Isolated Pickup
            binding.tvLogPickup.text = String.format(
                Locale.US,
                "%.1f km (%dm)",
                item.pickupDistanceKm,
                item.pickupDurationMin
            )

            // Isolated Trip
            binding.tvLogTrip.text = String.format(
                Locale.US,
                "%.1f km (%dm)",
                item.tripDistanceKm,
                item.tripDurationMin
            )

            // Fares
            binding.tvLogOfferFare.text = String.format(
                Locale.US,
                "Offer: %s%.2f  |  True: %s%.2f",
                item.currency,
                item.appOfferFare,
                item.currency,
                item.calculatedFare
            )

            // Profit rating
            val diff = item.difference
            if (diff >= 0.0) {
                binding.tvLogRating.text = String.format(Locale.US, "+ %s%.2f PROFIT", item.currency, diff)
                binding.tvLogRating.setTextColor(ContextCompat.getColor(context, R.color.accent_driver_green))
            } else {
                binding.tvLogRating.text = String.format(Locale.US, "- %s%.2f LOSS", item.currency, abs(diff))
                binding.tvLogRating.setTextColor(ContextCompat.getColor(context, R.color.alert_red))
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RideLogEntity>() {
            override fun areItemsTheSame(oldItem: RideLogEntity, newItem: RideLogEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RideLogEntity, newItem: RideLogEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
