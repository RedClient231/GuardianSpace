package com.tools.vspace.blackbox

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * Virtual PackageManager that intercepts package queries
 * to hide virtual environment traces and support anti-detection.
 */
class VirtualPackageManager(private val context: Context) {

    companion object {
        private const val TAG = "VirtualPackageManager"

        // Packages that should be hidden from detection
        private val HIDDEN_PACKAGES = setOf(
            "com.tools.vspace",
            "com.lbe.parallel",
            "com.bly.dkplat",
            "io.virtualapp",
            "com.blackbox.core"
        )
    }

    private val virtualPackages = mutableMapOf<String, ApplicationInfo>()

    /**
     * Check if a package is a virtual space package that should be hidden
     */
    fun isVirtualPackage(packageName: String): Boolean {
        return HIDDEN_PACKAGES.any { packageName.contains(it) }
    }

    /**
     * Register a virtual package
     */
    fun registerVirtualPackage(packageName: String, info: ApplicationInfo) {
        virtualPackages[packageName] = info
        Log.d(TAG, "Registered virtual package: $packageName")
    }

    /**
     * Get filtered package list excluding virtual packages
     */
    fun getInstalledPackages(flags: Int): List<android.content.pm.PackageInfo> {
        val pm = context.packageManager
        val allPackages = pm.getInstalledPackages(flags)
        return allPackages.filter { !isVirtualPackage(it.packageName) }
    }

    /**
     * Get package info, returning virtual info if available
     */
    fun getPackageInfo(packageName: String, flags: Int): android.content.pm.PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
