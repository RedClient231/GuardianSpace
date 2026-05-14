package com.tools.vspace

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import dalvik.system.DexClassLoader
import com.tools.vspace.blackbox.BlackBoxEngine
import com.tools.vspace.engine.BlackBoxCore
import com.tools.vspace.engine.VEnvironment
import com.tools.vspace.engine.patch.GG_Patcher
import com.tools.vspace.engine.patch.RootEmu
import java.io.File
import java.io.FileOutputStream

/**
 * Central controller for the Virtual Space.
 * Manages the BlackBox engine, virtual environment, and app lifecycle.
 *
 * Apps are loaded and run INSIDE the virtual space using DexClassLoader,
 * not installed on the real device.
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
        val icon: Drawable?,
        val isGameGuardian: Boolean = false,
        // Virtual runtime state
        var classLoader: DexClassLoader? = null,
        var resources: Resources? = null,
        var appInfo: android.content.pm.ApplicationInfo? = null
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
     * Parses the APK, creates a DexClassLoader, and loads resources.
     */
    fun installApk(apkPath: String): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "VirtualCore not initialized")
            return false
        }

        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)

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

            // Create DexClassLoader for this APK (loads classes inside our process)
            val optimizedDir = File(context.filesDir, "virtual_dex/${packageInfo.packageName}")
            optimizedDir.mkdirs()

            val classLoader = DexClassLoader(
                apkPath,
                optimizedDir.absolutePath,
                null, // library search path
                context.classLoader // parent - use app's classloader
            )

            // Create Resources for this APK
            val resources = createApkResources(apkPath, appInfo)

            val virtualApp = VirtualApp(
                packageName = packageInfo.packageName,
                appName = appName,
                apkPath = apkPath,
                icon = icon,
                isGameGuardian = isGG,
                classLoader = classLoader,
                resources = resources,
                appInfo = appInfo
            )

            installedApps.add(virtualApp)

            // Register with BlackBox engine
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
     * Create Resources object for an APK so we can load its layouts/drawables
     */
    private fun createApkResources(apkPath: String, appInfo: android.content.pm.ApplicationInfo): Resources {
        val assetManager = AssetManager::class.java.newInstance()
        val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.isAccessible = true
        addAssetPath.invoke(assetManager, apkPath)

        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        return Resources(assetManager, displayMetrics, configuration)
    }

    /**
     * Launch an app INSIDE the virtual space.
     * Opens a hosting Activity that runs the target app's code.
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

            // Launch the virtual app inside our container
            val intent = Intent(context, VirtualHostActivity::class.java).apply {
                putExtra("package_name", packageName)
                putExtra("apk_path", app.apkPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            Log.i(TAG, "Launched virtual app: ${app.appName} inside virtual space")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed", e)
            false
        }
    }

    /**
     * Get the VirtualApp entry for a package
     */
    fun getVirtualApp(packageName: String): VirtualApp? {
        return installedApps.find { it.packageName == packageName }
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

        installedApps.remove(app)

        // Clean up dex cache
        try {
            val dexDir = File(context.filesDir, "virtual_dex/$packageName")
            dexDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean dex cache: ${e.message}")
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
