package com.tools.vspace.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tools.vspace.R
import com.tools.vspace.VirtualCore

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var statsView: TextView
    private var adapter: HomeAppAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_home_apps)
        emptyView = view.findViewById(R.id.tv_empty_home)
        statsView = view.findViewById(R.id.tv_stats)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        loadApps()
    }

    fun refreshApps() {
        loadApps()
    }

    private fun loadApps() {
        val vc = VirtualCore.get(requireContext())
        val apps = vc.getInstalledApps()

        if (apps.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            statsView.text = "No apps installed"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            val ggCount = apps.count { it.isGameGuardian }
            statsView.text = "${apps.size} app(s) in virtual space" +
                    if (ggCount > 0) " • $ggCount GameGuardian instance(s)" else ""

            adapter = HomeAppAdapter(apps) { app ->
                vc.launchApp(app.packageName)
            }
            recyclerView.adapter = adapter
        }
    }

    // Simple inner adapter
    private class HomeAppAdapter(
        private val apps: List<VirtualCore.VirtualApp>,
        private val onClick: (VirtualCore.VirtualApp) -> Unit
    ) : RecyclerView.Adapter<HomeAppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: android.widget.ImageView = view.findViewById(R.id.iv_app_icon)
            val nameView: android.widget.TextView = view.findViewById(R.id.tv_app_name)
            val badge: android.widget.TextView = view.findViewById(R.id.tv_badge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.nameView.text = app.appName
            holder.iconView.setImageDrawable(app.icon)

            if (app.isGameGuardian) {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = "GG"
            } else {
                holder.badge.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount(): Int = apps.size
    }
}
