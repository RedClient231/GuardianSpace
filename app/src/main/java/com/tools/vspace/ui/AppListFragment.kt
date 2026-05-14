package com.tools.vspace.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tools.vspace.R
import com.tools.vspace.VirtualCore

class AppListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var adapter: AppListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_app_list)
        emptyView = view.findViewById(R.id.tv_empty_list)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        swipeRefresh.setOnRefreshListener {
            loadApps()
            swipeRefresh.isRefreshing = false
        }

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
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            adapter = AppListAdapter(apps,
                onLaunch = { app ->
                    val success = vc.launchApp(app.packageName)
                    if (success) {
                        Toast.makeText(requireContext(), "Launching ${app.appName}...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to launch ${app.appName}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { app ->
                    vc.removeApp(app.packageName)
                    Toast.makeText(requireContext(), "${app.appName} removed from virtual space", Toast.LENGTH_SHORT).show()
                    loadApps() // Refresh the list
                }
            )
            recyclerView.adapter = adapter
        }
    }

    private class AppListAdapter(
        private val apps: List<VirtualCore.VirtualApp>,
        private val onLaunch: (VirtualCore.VirtualApp) -> Unit,
        private val onDelete: (VirtualCore.VirtualApp) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: android.widget.ImageView = view.findViewById(R.id.iv_list_icon)
            val nameView: android.widget.TextView = view.findViewById(R.id.tv_list_name)
            val packageView: android.widget.TextView = view.findViewById(R.id.tv_list_package)
            val launchBtn: android.widget.Button = view.findViewById(R.id.btn_launch)
            val deleteBtn: android.widget.ImageButton = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.nameView.text = app.appName
            holder.packageView.text = app.packageName
            holder.iconView.setImageDrawable(app.icon)

            if (app.isGameGuardian) {
                holder.nameView.setTextColor(0xFFE53935.toInt())
            }

            holder.launchBtn.setOnClickListener { onLaunch(app) }
            holder.deleteBtn.setOnClickListener { onDelete(app) }
        }

        override fun getItemCount(): Int = apps.size
    }
}
