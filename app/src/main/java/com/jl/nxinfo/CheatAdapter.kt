package com.jl.nxinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CheatAdapter(
    private val cheats: List<Cheat>,
    private val onCheatClick: (Cheat) -> Unit
) : RecyclerView.Adapter<CheatAdapter.CheatViewHolder>() {

    class CheatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cheatName: TextView = itemView.findViewById(R.id.textview_cheat_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cheat, parent, false)
        return CheatViewHolder(view)
    }

    override fun onBindViewHolder(holder: CheatViewHolder, position: Int) {
        val cheat = cheats[position]
        holder.cheatName.text = cheat.name

        holder.itemView.setOnClickListener {
            onCheatClick(cheat)
        }
    }

    override fun getItemCount(): Int = cheats.size
}