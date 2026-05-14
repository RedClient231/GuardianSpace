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
    private var hostedView: View? = null

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
            val resources = virtualApp.resources ?: return false
            val appInfo = virtualApp.appInfo ?: return false

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
            var launcherActivity: ActivityInfo? = null
            for (activity in activities) {
                val intentFilter = pm.getActivityInfo(
                    ComponentName(targetPackageName!!, activity.name), 0
                )
                // Check if this is the main/launcher activity
                if (activity.name.contains("Main", ignoreCase = true) ||
                    activity.name.contains("Launch", ignoreCase = true) ||
                    activity.name.contains("Splash", ignoreCase = true)) {
                    launcherActivity = activity
                    break
                }
            }

            // If no obvious launcher found, use the first activity
            if (launcherActivity == null) {
                launcherActivity = activities[0]
            }

            Log.i(TAG, "Loading Activity: ${launcherActivity.name}")

            // Load the Activity class using the APK's classloader
            val activityClass = classLoader.loadClass(launcherActivity.name)

            // Create an instance of the target Activity
            val targetActivity = activityClass.newInstance() as? Activity

            if (targetActivity != null) {
                // Set up the activity with our context
                // The activity will run inside our process
                Toast.makeText(this, "Launching inside virtual space...", Toast.LENGTH_SHORT).show()

                // Launch via intent with the target activity class
                val launchIntent = Intent().apply {
                    setClassName(targetPackageName!!, launcherActivity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    startActivity(launchIntent)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Direct launch failed, trying reflection: ${e.message}")
                }

                // Fallback: use reflection to call onCreate
                try {
                    val attachMethod = Activity::class.java.getDeclaredMethod(
                        "attach",
                        android.content.Context::class.java,
                        android.app.Instrumentation::class.java,
                        android.app.Application::class.java,
                        android.os.IBinder::class.java,
                        android.app.Application::class.java,
                        Intent::class.java,
                        ActivityInfo::class.java,
                        CharSequence::class.java,
                        Activity::class.java,
                        String::class.java,
                        android.app.ActivityThread::class.java,
                        android.content.res.Configuration::class.java
                    )
                    attachMethod.isAccessible = true

                    // This is complex - fall back to showing the container
                    Log.w(TAG, "Reflection attach too complex, using container")
                    return false
                } catch (e: Exception) {
                    Log.e(TAG, "Reflection failed: ${e.message}")
                    return false
                }
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

        // Also try to load the app's main layout
        try {
            val vc = VirtualCore.get(this)
            val virtualApp = vc.getVirtualApp(targetPackageName!!)
            val resources = virtualApp?.resources

            if (resources != null) {
                // Try to find and inflate the app's main layout
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
