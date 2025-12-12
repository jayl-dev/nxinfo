package com.jl.nxinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.button.MaterialButton

class GameSearchAdapter(
    private val fragment: Fragment,
    private val onExpandedPositionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<GameSearchAdapter.GameViewHolder>() {

    private var games: List<GameInfo> = emptyList()
    private var expandedPosition: Int = -1

    fun updateGames(newGames: List<GameInfo>) {
        val previousExpandedPosition = expandedPosition
        expandedPosition = -1 // Reset expansion when updating games
        games = newGames

        if (previousExpandedPosition != -1) {
            notifyItemChanged(previousExpandedPosition)
        }
        notifyDataSetChanged()
        onExpandedPositionChanged(expandedPosition)
    }

    fun setExpandedPosition(position: Int) {
        if (expandedPosition != position) {
            val previousExpandedPosition = expandedPosition
            expandedPosition = position

            if (previousExpandedPosition != -1) {
                notifyItemChanged(previousExpandedPosition)
            }
            if (position != -1 && position < games.size) {
                notifyItemChanged(position)
            }
        }
    }

    fun getExpandedPosition(): Int = expandedPosition

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_search, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position], position)
    }

    override fun getItemCount(): Int = games.size

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val containerMain: LinearLayout = itemView.findViewById(R.id.container_main)
        private val containerExpanded: LinearLayout = itemView.findViewById(R.id.container_expanded)
        private val gameIcon: ImageView = itemView.findViewById(R.id.image_game_icon)
        private val gameNameEn: TextView = itemView.findViewById(R.id.text_game_name_en)
        private val gameNameZh: TextView = itemView.findViewById(R.id.text_game_name_zh)
        private val titleId: TextView = itemView.findViewById(R.id.text_title_id)
        private val buttonFindCheatslips: MaterialButton = itemView.findViewById(R.id.button_find_cheatslips)
        private val buttonFindCheatsDb: MaterialButton = itemView.findViewById(R.id.button_find_cheats_db)

        fun bind(game: GameInfo, position: Int) {
            gameNameEn.text = game.nameEn

            if (game.nameZh.isNotBlank()) {
                gameNameZh.text = game.nameZh
                gameNameZh.visibility = View.VISIBLE
            } else {
                gameNameZh.visibility = View.GONE
            }

            titleId.text = game.titleId

            // Load icon
            if (game.iconUrl.isNotBlank()) {
                Glide.with(fragment.requireContext())
                    .load(game.iconUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(gameIcon)
            } else {
                gameIcon.setImageResource(R.drawable.ic_launcher_background)
            }

            // Handle expansion
            val isExpanded = position == expandedPosition
            containerExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                gameNameEn.maxLines = Integer.MAX_VALUE
                gameNameZh.maxLines = Integer.MAX_VALUE
            } else {
                gameNameEn.maxLines = 2
                gameNameZh.maxLines = 1
            }

            // Click to expand/collapse
            containerMain.setOnClickListener {
                val previousExpandedPosition = expandedPosition

                if (isExpanded) {
                    // Collapse
                    expandedPosition = -1
                    notifyItemChanged(position)
                } else {
                    // Expand this, collapse previous
                    expandedPosition = position
                    notifyItemChanged(position)

                    if (previousExpandedPosition != -1 && previousExpandedPosition != position) {
                        notifyItemChanged(previousExpandedPosition)
                    }
                }

                onExpandedPositionChanged(expandedPosition)
            }

            // Cheat search buttons
            buttonFindCheatslips.setOnClickListener {
                CheatSearchHelper.searchOnCheatSlips(fragment.requireContext(), game.nameEn)
            }

            buttonFindCheatsDb.setOnClickListener {
                CheatSearchHelper.findInCheatDatabase(fragment, game.titleId, game.nameEn)
            }
        }
    }
}
