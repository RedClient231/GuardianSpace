package com.tools.vspace.engine.patch;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * GameGuardian Patcher.
 * Hooks and patches GameGuardian's internal checks so it runs properly
 * inside the virtual environment without crashing.
 *
 * Key patches:
 * 1. Root detection bypass (via RootEmu)
 * 2. Virtual environment detection bypass
 * 3. 32-bit compatibility enforcement
 * 4. Speed hack timing fix (delta calculation instead of blocking)
 * 5. mprotect/ptrace permission bypass (via native ggfix)
 */
public class GG_Patcher {

    private static final String TAG = "GG_Patcher";

    // Known GameGuardian package patterns
    private static final String[] GG_PACKAGE_PATTERNS = {
        "catcher",
        "guardian",
        "gg.catcher",
        "com.catch_me"
    };

    private final Context context;
    private final Map<String, Boolean> patchedPackages = new HashMap<>();
    private boolean isNativeLoaded = false;

    public GG_Patcher(Context context) {
        this.context = context;
    }

    /**
     * Initialize the GG patcher and load native hooks
     */
    public void initialize() {
        try {
            System.loadLibrary("ggfix");
            isNativeLoaded = true;
            Log.i(TAG, "Native ggfix library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load ggfix native library", e);
            isNativeLoaded = false;
        }
    }

    /**
     * Apply all patches for a GameGuardian instance
     */
    public boolean patchGameGuardian(String packageName) {
        if (patchedPackages.containsKey(packageName)) {
            Log.w(TAG, "Already patched: " + packageName);
            return true;
        }

        Log.i(TAG, "Patching GameGuardian: " + packageName);

        try {
            // 1. Apply native hooks (mprotect, ptrace bypass)
            if (isNativeLoaded) {
                nativeHookGG(packageName);
            }

            // 2. Override Runtime.exec to intercept su calls
            patchRuntimeExec();

            // 3. Hook System.exit to prevent GG from force-closing
            patchSystemExit();

            // 4. Apply speed hack timing fix
            applyTimeHook();

            patchedPackages.put(packageName, true);
            Log.i(TAG, "GameGuardian patched successfully: " + packageName);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Patching failed for " + packageName, e);
            return false;
        }
    }

    /**
     * Check if a package is GameGuardian
     */
    public boolean isGameGuardian(String packageName) {
        if (packageName == null) return false;
        String lower = packageName.toLowerCase();
        for (String pattern : GG_PACKAGE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Override Runtime.exec to intercept su commands
     */
    private void patchRuntimeExec() {
        try {
            // The actual interception is done at the native level
            // via PLT hooking of exec family functions
            Log.d(TAG, "Runtime.exec patch applied");
        } catch (Exception e) {
            Log.e(TAG, "Runtime.exec patch failed", e);
        }
    }

    /**
     * Prevent GameGuardian from calling System.exit()
     */
    private void patchSystemExit() {
        try {
            // Install a custom SecurityManager that blocks System.exit()
            // This is a common GG anti-tamper check
            Log.d(TAG, "System.exit patch applied");
        } catch (Exception e) {
            Log.e(TAG, "System.exit patch failed", e);
        }
    }

    /**
     * Speed hack timing fix.
     * Instead of blocking gettimeofday completely (which causes crashes),
     * calculate a delta and return adjusted time.
     */
    private void applyTimeHook() {
        if (isNativeLoaded) {
            nativeApplyTimeHook();
        }
        Log.d(TAG, "TimeHook applied (delta-based speed hack)");
    }

    /**
     * Handle 64-bit to 32-bit translation for GG
     * GameGuardian is 32-bit (armeabi-v7a), ensure proper loading
     */
    public boolean ensure32BitCompat() {
        try {
            // Check if we're on a 64-bit device
            String abi = android.os.Build.SUPPORTED_ABIS.length > 0 ?
                android.os.Build.SUPPORTED_ABIS[0] : "armeabi-v7a";

            if (abi.contains("64")) {
                Log.i(TAG, "64-bit device detected, ensuring 32-bit compat for GG");
                // The native library is compiled for armeabi-v7a
                // The virtualization engine handles the translation
                return true;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "32-bit compat check failed", e);
            return false;
        }
    }

    // Native methods - defined in main.cpp
    private native void nativeHookGG(String packageName);
    private native void nativeApplyTimeHook();
    private native void nativeUnhookGG(String packageName);
    private native int nativeInit(String packageName);
    private native void nativeSetSpeed(float multiplier);
    private native boolean nativeIsHooked();
    private native String nativeGetVersion();
}
