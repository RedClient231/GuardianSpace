package com.tools.vspace

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tools.vspace.blackbox.BlackBoxEngine
import com.tools.vspace.engine.BlackBoxCore
import com.tools.vspace.engine.VEnvironment
import com.tools.vspace.engine.patch.GG_Patcher
import com.tools.vspace.engine.patch.RootEmu
import java.io.File

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
     * Install an APK into the virtual space
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

            val virtualApp = VirtualApp(
                packageName = packageInfo.packageName,
                appName = appName,
                apkPath = apkPath,
                icon = icon,
                isGameGuardian = isGG
            )

            installedApps.add(virtualApp)

            // Create virtual process
            val uid = 10000 + installedApps.size
            blackBoxEngine.createVirtualProcess(packageInfo.packageName, apkPath, uid)

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
     * Launch an app in the virtual space
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

            // Create virtual process
            val pid = blackBoxEngine.createVirtualProcess(
                packageName, app.apkPath, 10000 + installedApps.indexOf(app)
            )

            if (pid > 0) {
                Log.i(TAG, "Launched virtual app: ${app.appName} (pid=$pid)")
                true
            } else {
                Log.e(TAG, "Failed to create virtual process for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            false
        }
    }

    /**
     * Get list of installed virtual apps
     */
    fun getInstalledApps(): List<VirtualApp> = installedApps.toList()

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
