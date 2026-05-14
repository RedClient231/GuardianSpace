package com.tools.vspace.engine;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Virtual Environment manager for the app-level engine.
 * Wraps the blackbox-core VEnvironment and provides
 * additional virtual filesystem and property management.
 */
public class VEnvironment {

    private static final String TAG = "VEnvWrapper";

    private final Context context;
    private final Map<String, String> virtualProps = new HashMap<>();

    /**
     * Device spoof configuration data class
     */
    public static class DeviceSpoof {
        public final String model;
        public final String manufacturer;
        public final String brand;

        public DeviceSpoof(String model, String manufacturer, String brand) {
            this.model = model;
            this.manufacturer = manufacturer;
            this.brand = brand;
        }
    }

    public VEnvironment(Context context) {
        this.context = context;
        initVirtualProps();
    }

    /**
     * Initialize the virtual environment
     */
    public void initialize() {
        // Ensure virtual data directories exist
        getVirtualDataDir("system");
        getVirtualBinDir();
        Log.i(TAG, "VEnvironment initialized");
    }

    /**
     * Get the current device spoof configuration
     */
    public DeviceSpoof getDeviceSpoof() {
        return new DeviceSpoof(
            virtualProps.getOrDefault("ro.product.model", "Pixel 5"),
            virtualProps.getOrDefault("ro.product.manufacturer", "Google"),
            virtualProps.getOrDefault("ro.product.brand", "google")
        );
    }

    private void initVirtualProps() {
        virtualProps.put("ro.product.model", "Pixel 5");
        virtualProps.put("ro.product.manufacturer", "Google");
        virtualProps.put("ro.product.brand", "google");
        virtualProps.put("ro.product.device", "redfin");
        virtualProps.put("ro.product.name", "redfin");
        virtualProps.put("ro.build.display.id", "RQ3A.211001.001");
        virtualProps.put("ro.build.version.release", "11");
        virtualProps.put("ro.build.version.sdk", "30");
        virtualProps.put("ro.build.fingerprint",
                "google/redfin/redfin:11/RQ3A.211001.001/7641976:userdebug/dev-keys");
        virtualProps.put("ro.hardware", "redfin");
        virtualProps.put("ro.build.type", "userdebug");
        virtualProps.put("ro.build.tags", "dev-keys");
        virtualProps.put("ro.debuggable", "0");
        virtualProps.put("ro.secure", "1");
        Log.i(TAG, "Virtual properties initialized for Pixel 5");
    }

    public String getProperty(String key, String defaultValue) {
        String value = virtualProps.get(key);
        return value != null ? value : defaultValue;
    }

    public void setProperty(String key, String value) {
        virtualProps.put(key, value);
    }

    public File getVirtualDataDir(String packageName) {
        File dataDir = new File(context.getFilesDir(), "virtual_data/" + packageName);
        dataDir.mkdirs();
        return dataDir;
    }

    public File getVirtualBinDir() {
        File binDir = new File(context.getFilesDir(), "virtual_data/system/bin");
        binDir.mkdirs();
        return binDir;
    }

    public boolean shouldRedirect(String path) {
        if (path == null) return false;
        return path.startsWith("/system/bin/su") ||
               path.startsWith("/system/xbin/su") ||
               path.startsWith("/sbin/su");
    }

    public String getRedirectedPath(String originalPath) {
        if (originalPath == null) return null;
        if (originalPath.contains("/su")) {
            return new File(getVirtualBinDir(), "su").getAbsolutePath();
        }
        return originalPath;
    }
}
