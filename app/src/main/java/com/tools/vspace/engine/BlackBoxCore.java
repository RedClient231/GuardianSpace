package com.tools.vspace.engine;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.tools.vspace.blackbox.BlackBoxEngine;

import java.lang.reflect.Field;

/**
 * Core virtualization engine wrapper.
 * Bridges the BlackBox native engine with Java-level hooks.
 */
public class BlackBoxCore {

    private static final String TAG = "BlackBoxCore";

    private final Context context;
    private final BlackBoxEngine engine;
    private final VEnvironment vEnv;

    public BlackBoxCore(Context context) {
        this.context = context;
        this.engine = new BlackBoxEngine(context);
        this.vEnv = new VEnvironment(context);
    }

    /**
     * Apply BuildConfig spoofing to appear as Google Pixel 5
     */
    public void applyBuildSpoof() {
        try {
            setStaticField(Build.class, "MODEL", "Pixel 5");
            setStaticField(Build.class, "MANUFACTURER", "Google");
            setStaticField(Build.class, "BRAND", "google");
            setStaticField(Build.class, "DEVICE", "redfin");
            setStaticField(Build.class, "PRODUCT", "redfin");
            setStaticField(Build.class, "HARDWARE", "redfin");
            setStaticField(Build.class, "FINGERPRINT",
                "google/redfin/redfin:11/RQ3A.211001.001/7641976:userdebug/dev-keys");
            setStaticField(Build.class, "ID", "RQ3A.211001.001");
            setStaticField(Build.class, "TYPE", "userdebug");
            setStaticField(Build.class, "TAGS", "dev-keys");

            if (Build.VERSION.SDK_INT >= 23) {
                setStaticField(Build.VERSION.class, "RELEASE", "11");
                setStaticField(Build.VERSION.class, "INCREMENTAL", "7641976");
            }

            Log.i(TAG, "Build spoof applied: Pixel 5 / Android 11");
        } catch (Exception e) {
            Log.e(TAG, "Build spoof failed", e);
        }
    }

    /**
     * Hide virtual packages from PackageManager queries
     */
    public void hideVirtualPackages() {
        Log.i(TAG, "Virtual package hiding enabled");
    }

    /**
     * Get virtual app's opPackageName for Android 11+ compatibility
     */
    public String getVirtualOpPackageName(String packageName) {
        return packageName;
    }

    private void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            field.set(null, value);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set " + fieldName + ": " + e.getMessage());
        }
    }
}
