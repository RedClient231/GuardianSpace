package com.tools.vspace

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.tools.vspace.blackbox.BlackBoxEngine
import com.tools.vspace.engine.BlackBoxCore
import com.tools.vspace.engine.VEnvironment
import com.tools.vspace.engine.patch.GG_Patcher
import com.tools.vspace.engine.patch.RootEmu
import java.io.File
import java.io.FileInputStream

/**
 * Central controller for the Virtual Space.
 * Manages the BlackBox engine, virtual environment, and app lifecycle.
 */
class VirtualCore private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VirtualCore"

        @Volatile
        private var instance: VirtualCore? = null

        fun get(context: Context): VirtualCore {
            return instance ?: synchronized(this) {
                instance ?: VirtualCore(context.applicationContext).also { instance = it }
            }
        }
    }

    data class VirtualApp(
        val packageName: String,
        val appName: String,
        val apkPath: String,
        val icon: android.graphics.drawable.Drawable?,
        val isGameGuardian: Boolean = false
    )

    private val blackBoxEngine = BlackBoxEngine(context)
    private val blackBoxCore = BlackBoxCore(context)
    private val vEnvironment = VEnvironment(context)
    private val rootEmu = RootEmu(context)
    private val ggPatcher = GG_Patcher(context)

    private val installedApps = mutableListOf<VirtualApp>()
    private var isInitialized = false

    /**
     * Initialize the virtual core and all sub-systems
     */
    fun initialize(): Boolean {
        if (isInitialized) return true

        Log.i(TAG, "Initializing VirtualCore...")

        // 1. Initialize virtual environment
        vEnvironment.initialize()

        // 2. Initialize BlackBox engine
        if (!blackBoxEngine.initialize()) {
            Log.e(TAG, "BlackBox engine initialization failed")
            return false
        }

        // 3. Apply device spoof (Pixel 5)
        val spoof = vEnvironment.getDeviceSpoof()
        blackBoxEngine.spoofDevice(spoof.model, spoof.manufacturer, spoof.brand)

        // 4. Initialize root emulation
        rootEmu.initialize()

        // 5. Initialize GG patcher
        ggPatcher.initialize()

        isInitialized = true
        Log.i(TAG, "VirtualCore initialized successfully")
        return true
    }

    /**
     * Install an APK into the virtual space.
     * Copies the APK to internal storage and registers it for launching.
     */
    fun installApk(apkPath: String): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "VirtualCore not initialized")
            return false
        }

        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)

            if (packageInfo == null) {
                Log.e(TAG, "Failed to parse APK: $apkPath")
                return false
            }

            val appInfo = packageInfo.applicationInfo
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath

            val appName = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)

            val isGG = packageInfo.packageName.contains("catcher") ||
                       appName.contains("Guardian", ignoreCase = true)

            // Copy APK to our internal storage for reliable access
            val internalApk = copyApkToInternal(apkPath, packageInfo.packageName)

            val virtualApp = VirtualApp(
                packageName = packageInfo.packageName,
                appName = appName,
                apkPath = internalApk ?: apkPath,
                icon = icon,
                isGameGuardian = isGG
            )

            installedApps.add(virtualApp)

            // Register with BlackBox engine
            val uid = 10000 + installedApps.size
            blackBoxEngine.createVirtualProcess(packageInfo.packageName, virtualApp.apkPath, uid)

            // If this is GameGuardian, apply patches
            if (isGG) {
                ggPatcher.patchGameGuardian(packageInfo.packageName)
            }

            Log.i(TAG, "Installed virtual app: $appName (${packageInfo.packageName})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK installation failed", e)
            false
        }
    }

    /**
     * Copy APK to internal storage for reliable access
     */
    private fun copyApkToInternal(sourcePath: String, packageName: String): String? {
        return try {
            val destDir = File(context.filesDir, "virtual_apks")
            destDir.mkdirs()
            val destFile = File(destDir, "$packageName.apk")

            // Only copy if source is different from dest
            if (sourcePath != destFile.absolutePath) {
                val source = File(sourcePath)
                source.copyTo(destFile, overwrite = true)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy APK to internal storage", e)
            null
        }
    }

    /**
     * Launch an app in the virtual space.
     * Installs the APK on the device and launches it.
     */
    fun launchApp(packageName: String): Boolean {
        val app = installedApps.find { it.packageName == packageName }
        if (app == null) {
            Log.e(TAG, "App not found: $packageName")
            return false
        }

        return try {
            // Apply root emulation for this app
            rootEmu.enableForPackage(packageName)

            // Apply build spoofing
            blackBoxCore.applyBuildSpoof()

            // Register with BlackBox engine
            blackBoxEngine.createVirtualProcess(
                packageName, app.apkPath, 10000 + installedApps.indexOf(app)
            )

            // Check if app is already installed on device
            val isInstalled = isAppInstalled(packageName)

            if (isInstalled) {
                // App already installed, just launch it
                launchInstalledApp(packageName)
            } else {
                // Need to install the APK first, then launch
                installAndLaunchApk(app.apkPath, packageName)
            }

            Log.i(TAG, "Launched virtual app: ${app.appName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            false
        }
    }

    /**
     * Check if an app is installed on the device
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launch an already installed app
     */
    private fun launchInstalledApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Launched installed app: $packageName")
        } else {
            Log.e(TAG, "No launch intent for: $packageName")
        }
    }

    /**
     * Install APK and launch it.
     * Uses ACTION_VIEW to trigger the system package installer.
     */
    private fun installAndLaunchApk(apkPath: String, packageName: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: $apkPath")
            return
        }

        // Use the system installer
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // For Android 7.0+ use FileProvider
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "Started APK installation for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start APK installation", e)
            // Fallback: try with file:// URI
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.fromFile(apkFile),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback installation also failed", e2)
            }
        }
    }

    /**
     * Get list of installed virtual apps
     */
    fun getInstalledApps(): List<VirtualApp> = installedApps.toList()

    /**
     * Remove an app from the virtual space
     */
    fun removeApp(packageName: String): Boolean {
        val app = installedApps.find { it.packageName == packageName }
        if (app == null) return false

        // Remove from list
        installedApps.remove(app)

        // Try to delete the internal APK copy
        try {
            File(app.apkPath).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete APK: ${e.message}")
        }

        Log.i(TAG, "Removed virtual app: $packageName")
        return true
    }

    /**
     * Check if an APK is GameGuardian
     */
    fun isGameGuardian(apkPath: String): Boolean {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkPath, 0)
            info?.packageName?.contains("catcher") == true
        } catch (e: Exception) {
            false
        }
    }

    fun getRootEmu(): RootEmu = rootEmu
    fun getVEnvironment(): VEnvironment = vEnvironment
    fun getBlackBoxEngine(): BlackBoxEngine = blackBoxEngine
}
