package com.tools.vspace

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tools.vspace.ui.AppListFragment
import com.tools.vspace.ui.HomeFragment

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_APK = 1001
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize VirtualCore
        VirtualCore.get(this).initialize()

        setupViews()
        setupNavigation()
    }

    private fun setupViews() {
        viewPager = findViewById(R.id.view_pager)
        bottomNav = findViewById(R.id.bottom_navigation)
        fab = findViewById(R.id.fab_add_app)

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.isUserInputEnabled = false

        fab.setOnClickListener {
            openFilePicker()
        }
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewPager.currentItem = 0
                    true
                }
                R.id.nav_apps -> {
                    viewPager.currentItem = 1
                    true
                }
                else -> false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = when (position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_apps
                    else -> R.id.nav_home
                }
            }
        })
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/vnd.android.package-archive"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select APK"),
                REQUEST_PICK_APK
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No file manager found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_APK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                handleApkUri(uri)
            }
        }
    }

    private fun handleApkUri(uri: Uri) {
        try {
            // Copy APK to internal storage
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val apkFile = java.io.File(filesDir, "apk_${System.currentTimeMillis()}.apk")

            java.io.FileOutputStream(apkFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val vc = VirtualCore.get(this)
            if (vc.installApk(apkFile.absolutePath)) {
                Toast.makeText(this, "App installed in virtual space", Toast.LENGTH_SHORT).show()
                // Refresh the current fragment
                refreshCurrentFragment()
            } else {
                Toast.makeText(this, "Installation failed", Toast.LENGTH_SHORT).show()
                apkFile.delete()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshCurrentFragment() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is HomeFragment) {
                fragment.refreshApps()
            } else if (fragment is AppListFragment) {
                fragment.refreshApps()
            }
        }
    }

    private inner class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> AppListFragment()
                else -> HomeFragment()
            }
        }
    }
}
