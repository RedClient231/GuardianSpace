package com.tools.vspace.blackbox

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Virtual Environment configuration and management
 * Handles virtual filesystem paths, device spoofing, and environment variables
 */
class VEnvironment(private val context: Context) {

    companion object {
        private const val TAG = "VEnvironment"
        private const val PREFS_NAME = "venvironment_config"

        // Default spoofed device: Google Pixel 5
        const val DEFAULT_MODEL = "Pixel 5"
        const val DEFAULT_MANUFACTURER = "Google"
        const val DEFAULT_BRAND = "google"
        const val DEFAULT_DEVICE = "redfin"
        const val DEFAULT_PRODUCT = "redfin"
        const val DEFAULT_HARDWARE = "redfin"
        const val DEFAULT_FINGERPRINT = "google/redfin/redfin:11/RQ3A.211001.001/7641976:userdebug/dev-keys"
        const val DEFAULT_BUILD_ID = "RQ3A.211001.001"
    }

    data class DeviceSpoof(
        val model: String = DEFAULT_MODEL,
        val manufacturer: String = DEFAULT_MANUFACTURER,
        val brand: String = DEFAULT_BRAND,
        val device: String = DEFAULT_DEVICE,
        val product: String = DEFAULT_PRODUCT,
        val hardware: String = DEFAULT_HARDWARE,
        val fingerprint: String = DEFAULT_FINGERPRINT,
        val buildId: String = DEFAULT_BUILD_ID
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val virtualDataDir: File
        get() = File(context.filesDir, "virtual_data")

    val virtualSystemDir: File
        get() = File(virtualDataDir, "system")

    val virtualBinDir: File
        get() = File(virtualSystemDir, "bin")

    /**
     * Initialize virtual environment directories
     */
    fun initialize() {
        virtualDataDir.mkdirs()
        virtualSystemDir.mkdirs()
        virtualBinDir.mkdirs()

        // Create fake su binary path
        val suFile = File(virtualBinDir, "su")
        if (!suFile.exists()) {
            suFile.createNewFile()
            suFile.setExecutable(true)
        }

        Log.i(TAG, "Virtual environment initialized at: ${virtualDataDir.absolutePath}")
    }

    /**
     * Get current device spoof configuration
     */
    fun getDeviceSpoof(): DeviceSpoof {
        return DeviceSpoof(
            model = prefs.getString("spoof_model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
            manufacturer = prefs.getString("spoof_manufacturer", DEFAULT_MANUFACTURER) ?: DEFAULT_MANUFACTURER,
            brand = prefs.getString("spoof_brand", DEFAULT_BRAND) ?: DEFAULT_BRAND,
            device = prefs.getString("spoof_device", DEFAULT_DEVICE) ?: DEFAULT_DEVICE,
            product = prefs.getString("spoof_product", DEFAULT_PRODUCT) ?: DEFAULT_PRODUCT,
            hardware = prefs.getString("spoof_hardware", DEFAULT_HARDWARE) ?: DEFAULT_HARDWARE,
            fingerprint = prefs.getString("spoof_fingerprint", DEFAULT_FINGERPRINT) ?: DEFAULT_FINGERPRINT,
            buildId = prefs.getString("spoof_build_id", DEFAULT_BUILD_ID) ?: DEFAULT_BUILD_ID
        )
    }

    /**
     * Save device spoof configuration
     */
    fun saveDeviceSpoof(spoof: DeviceSpoof) {
        prefs.edit().apply {
            putString("spoof_model", spoof.model)
            putString("spoof_manufacturer", spoof.manufacturer)
            putString("spoof_brand", spoof.brand)
            putString("spoof_device", spoof.device)
            putString("spoof_product", spoof.product)
            putString("spoof_hardware", spoof.hardware)
            putString("spoof_fingerprint", spoof.fingerprint)
            putString("spoof_build_id", spoof.buildId)
            apply()
        }
    }

    /**
     * Check if virtual environment is properly set up
     */
    fun isReady(): Boolean {
        return virtualDataDir.exists() && virtualBinDir.exists()
    }
}
