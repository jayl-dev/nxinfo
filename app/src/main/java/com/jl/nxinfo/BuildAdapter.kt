package com.jl.nxinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BuildAdapter(
    private val builds: List<BuildCheats>,
    private val onCheatClick: (Cheat) -> Unit,
    private val onExportClick: (BuildCheats) -> Unit,
    private val onInfoClick: () -> Unit
) : RecyclerView.Adapter<BuildAdapter.BuildViewHolder>() {

    private val viewPool = RecyclerView.RecycledViewPool()

    class BuildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val buildId: TextView = itemView.findViewById(R.id.textview_build_id)
        val exportButton: Button = itemView.findViewById(R.id.button_export)
        val infoButton: View = itemView.findViewById(R.id.button_info)
        val cheatsRecyclerView: RecyclerView = itemView.findViewById(R.id.recyclerview_cheats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_build, parent, false)
        return BuildViewHolder(view)
    }

    override fun onBindViewHolder(holder: BuildViewHolder, position: Int) {
        val build = builds[position]
        holder.buildId.text = build.buildId

        holder.exportButton.setOnClickListener {
            onExportClick(build)
        }

        holder.infoButton.setOnClickListener {
            onInfoClick()
        }

        val cheatAdapter = CheatAdapter(build.cheats, onCheatClick)
        holder.cheatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(holder.itemView.context)
            adapter = cheatAdapter
            setRecycledViewPool(viewPool)
        }
    }

    override fun getItemCount(): Int = builds.size
}
