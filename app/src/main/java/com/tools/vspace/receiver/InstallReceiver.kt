package com.tools.vspace.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.tools.vspace.VirtualCore
import java.io.File

/**
 * Broadcast receiver for installing APKs into the virtual space.
 * Can be triggered via:
 *   adb shell am broadcast -a com.tools.vspace.INSTALL_PACKAGE -d file:///path/to/app.apk
 */
class InstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Install broadcast received: ${intent.action}")

        when (intent.action) {
            "com.tools.vspace.INSTALL_PACKAGE" -> {
                val uri = intent.data
                if (uri != null) {
                    handleInstall(context, uri)
                } else {
                    Log.e(TAG, "No APK URI provided")
                    Toast.makeText(context, "No APK specified", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleInstall(context: Context, uri: Uri) {
        try {
            val path = uri.path
            if (path == null) {
                Log.e(TAG, "Invalid path")
                return
            }

            val apkFile = File(path)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: $path")
                Toast.makeText(context, "APK not found: $path", Toast.LENGTH_SHORT).show()
                return
            }

            val vc = VirtualCore.get(context)
            vc.initialize()

            if (vc.installApk(apkFile.absolutePath)) {
                Log.i(TAG, "APK installed successfully via broadcast")
                Toast.makeText(context, "Installed in virtual space", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "APK installation failed")
                Toast.makeText(context, "Installation failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install error", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
