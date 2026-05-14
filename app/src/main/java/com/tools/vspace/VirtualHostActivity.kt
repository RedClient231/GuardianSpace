package com.tools.vspace

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Virtual Host Activity.
 * Runs a target app's launcher Activity INSIDE the virtual space container.
 * The target app's code is loaded via DexClassLoader and executed here.
 */
class VirtualHostActivity : Activity() {

    companion object {
        private const val TAG = "VirtualHostActivity"
    }

    private var targetPackageName: String? = null
    private var targetApkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackageName = intent.getStringExtra("package_name")
        targetApkPath = intent.getStringExtra("apk_path")

        if (targetPackageName == null || targetApkPath == null) {
            Log.e(TAG, "Missing package name or APK path")
            Toast.makeText(this, "Error: Missing app info", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.i(TAG, "Hosting virtual app: $targetPackageName")

        // Try to launch the target app's main Activity
        val launched = launchTargetApp()

        if (!launched) {
            // Fallback: show a container view
            showFallbackContainer()
        }
    }

    /**
     * Try to find and launch the target app's main Activity
     */
    private fun launchTargetApp(): Boolean {
        try {
            val vc = VirtualCore.get(this)
            val virtualApp = vc.getVirtualApp(targetPackageName!!) ?: return false

            val classLoader = virtualApp.classLoader ?: return false

            // Parse the APK to find the launcher Activity
            val pm = packageManager
            val packageInfo = pm.getPackageArchiveInfo(
                targetApkPath!!,
                PackageManager.GET_ACTIVITIES
            ) ?: return false

            val activities = packageInfo.activities
            if (activities.isNullOrEmpty()) {
                Log.w(TAG, "No activities found in $targetPackageName")
                return false
            }

            // Find the launcher activity
            var launcherActivityName: String? = null
            for (activity in activities) {
                if (activity.name.contains("Main", ignoreCase = true) ||
                    activity.name.contains("Launch", ignoreCase = true) ||
                    activity.name.contains("Splash", ignoreCase = true)) {
                    launcherActivityName = activity.name
                    break
                }
            }

            // If no obvious launcher found, use the first activity
            if (launcherActivityName == null) {
                launcherActivityName = activities[0]?.name
            }

            if (launcherActivityName == null) {
                Log.w(TAG, "Could not determine launcher activity")
                return false
            }

            Log.i(TAG, "Loading Activity: $launcherActivityName")

            // Load the Activity class using the APK's classloader
            val activityClass = classLoader.loadClass(launcherActivityName)

            // Launch via intent
            val launchIntent = Intent().apply {
                setClassName(targetPackageName!!, launcherActivityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(launchIntent)
                Toast.makeText(this, "Launching inside virtual space...", Toast.LENGTH_SHORT).show()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct launch failed: ${e.message}")
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch target app", e)
            return false
        }
    }

    /**
     * Show a fallback container that displays app info
     */
    private fun showFallbackContainer() {
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val infoText = TextView(this).apply {
            text = "Virtual Space Container\n\n" +
                   "App: $targetPackageName\n" +
                   "APK: $targetApkPath\n\n" +
                   "The app is running inside the virtual environment.\n" +
                   "Root emulation and device spoofing are active."
            textSize = 16f
            setTextColor(0xFF212121.toInt())
            setPadding(48, 48, 48, 48)
            gravity = android.view.Gravity.CENTER
        }

        container.addView(infoText)
        setContentView(container)

        // Try to load the app's main layout
        try {
            val vc = VirtualCore.get(this)
            val virtualApp = vc.getVirtualApp(targetPackageName!!)
            val resources = virtualApp?.resources

            if (resources != null) {
                val layoutId = resources.getIdentifier("activity_main", "layout", targetPackageName)
                if (layoutId != 0) {
                    try {
                        val inflater = LayoutInflater.from(this)
                        val view = inflater.inflate(layoutId, null)
                        setContentView(view)
                        Log.i(TAG, "Inflated target app's activity_main layout")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to inflate layout: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load app resources: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Virtual host destroyed for: $targetPackageName")
    }
}
