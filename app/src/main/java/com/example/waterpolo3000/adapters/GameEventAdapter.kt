package com.example.waterpolo3000.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.waterpolo3000.GameFragment
import com.example.waterpolo3000.data.GameEventView
import com.example.waterpolo3000.databinding.ListItemGameEventBinding
import com.example.waterpolo3000.viewmodels.GameViewModel

/**
 * Adapter for the [RecyclerView] in [GameFragment].
 */
class GameEventAdapter : ListAdapter<GameEventView, RecyclerView.ViewHolder>(GameEventDiffCallback()) {
    lateinit var viewModelOut: GameViewModel

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return GameEventViewHolder(
            viewModelOut,
            ListItemGameEventBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val gameEvent = getItem(position)
        (holder as GameEventViewHolder).bind(gameEvent)
    }


    class GameEventViewHolder(
        private val viewModelIn: GameViewModel,
        private val binding: ListItemGameEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.setClickListener1 {
                binding.edit = !binding.edit
            }

            binding.setClickListener2 {
                binding.edit = true
                binding.gameEvent?.let { gameEvent ->
                    viewModelIn.deleteGameEvent(gameEvent)
                }
            }

            binding.setClickListener3 {
                binding.edit = true

                binding.gameEvent?.let { gameEvent ->
                    viewModelIn.requestGameEventEdit(gameEvent)
                }
            }

            binding.edit = true

//                    navigateToGameEvent(gameEvent, it)
        }

//        private fun navigateToGameEvent(gameEvent: GameEvent,view: View) {
//            val direction =
//                HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment( // replace
//                    gameEvent.participant
//                )
//            view.findNavController().navigate(direction)
//        }

        fun bind(item: GameEventView) {
            binding.apply {
                gameEvent = item
                executePendingBindings()
            }
        }
    }
}

private class GameEventDiffCallback : DiffUtil.ItemCallback<GameEventView>() {

    override fun areItemsTheSame(oldItem: GameEventView, newItem: GameEventView): Boolean {
        return oldItem.guid == newItem.guid
    }

    override fun areContentsTheSame(oldItem: GameEventView, newItem: GameEventView): Boolean {
        return oldItem == newItem
    }
}