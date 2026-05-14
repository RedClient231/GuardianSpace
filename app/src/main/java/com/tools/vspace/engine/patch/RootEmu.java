package com.tools.vspace.engine.patch;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Root Emulation layer.
 * Intercepts root-checking mechanisms used by apps like GameGuardian.
 * Makes apps believe the device is rooted by:
 * 1. Creating virtual su binaries
 * 2. Intercepting exec("su") calls
 * 3. Mocking root-check results
 */
public class RootEmu {

    private static final String TAG = "RootEmu";

    private final Context context;
    private boolean isEnabled = false;

    // Virtual paths that appear as root binaries
    private static final String[] SU_PATHS = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su"
    };

    public RootEmu(Context context) {
        this.context = context;
    }

    /**
     * Initialize root emulation
     */
    public void initialize() {
        createVirtualSu();
        isEnabled = true;
        Log.i(TAG, "Root emulation initialized");
    }

    /**
     * Enable root emulation for a specific package
     */
    public void enableForPackage(String packageName) {
        if (!isEnabled) {
            initialize();
        }
        Log.i(TAG, "Root emulation enabled for: " + packageName);
    }

    /**
     * Create virtual su binary in the virtual filesystem
     */
    private void createVirtualSu() {
        try {
            File virtualBinDir = new File(context.getFilesDir(), "virtual_data/system/bin");
            virtualBinDir.mkdirs();

            File suFile = new File(virtualBinDir, "su");
            if (!suFile.exists()) {
                // Write a minimal su script that appears to work
                FileOutputStream fos = new FileOutputStream(suFile);
                fos.write("#!/system/bin/sh\nwhoami\n".getBytes());
                fos.close();
                suFile.setExecutable(true);
                suFile.setReadable(true);
            }

            // Also create in xbin
            File xbinDir = new File(context.getFilesDir(), "virtual_data/system/xbin");
            xbinDir.mkdirs();
            File xbinSu = new File(xbinDir, "su");
            if (!xbinSu.exists()) {
                FileOutputStream fos = new FileOutputStream(xbinSu);
                fos.write("#!/system/bin/sh\nwhoami\n".getBytes());
                fos.close();
                xbinSu.setExecutable(true);
                xbinSu.setReadable(true);
            }

            Log.i(TAG, "Virtual su binaries created");
        } catch (IOException e) {
            Log.e(TAG, "Failed to create virtual su", e);
        }
    }

    /**
     * Intercept exec("su") calls by providing a fake response
     * Called from the native hook layer
     */
    public Process execSu(String[] cmd) throws IOException {
        Log.i(TAG, "Intercepted su command: " + String.join(" ", cmd));

        // Return a process that simulates root shell
        List<String> modifiedCmd = new ArrayList<>();
        modifiedCmd.add("/system/bin/sh");
        if (cmd.length > 1) {
            for (int i = 1; i < cmd.length; i++) {
                modifiedCmd.add(cmd[i]);
            }
        }

        ProcessBuilder pb = new ProcessBuilder(modifiedCmd);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Check if a file path exists in the virtual environment
     */
    public boolean virtualFileExists(String path) {
        for (String suPath : SU_PATHS) {
            if (path.equals(suPath)) {
                File virtualSu = new File(context.getFilesDir(),
                    "virtual_data/system" + path.replace("/system", ""));
                return virtualSu.exists();
            }
        }
        return false;
    }

    /**
     * Get all virtual su paths
     */
    public String[] getSuPaths() {
        return SU_PATHS;
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
