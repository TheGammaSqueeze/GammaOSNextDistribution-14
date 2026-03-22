/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater.controller;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import org.lineageos.updater.misc.Constants;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GammaOS OTA installer — extracts the update package to /data/gammaos_ota/
 * and launches the gammaos-ota native binary to perform the in-place flash.
 */
public class GammaOtaInstaller {

    private static final String TAG = "GammaOtaInstaller";
    private static final String OTA_DIR = Constants.GAMMAOS_OTA_DIR;

    private final Context mContext;
    private final UpdaterController mUpdaterController;

    public GammaOtaInstaller(Context context, UpdaterController controller) {
        mContext = context.getApplicationContext();
        mUpdaterController = controller;
    }

    /**
     * Install an update by extracting the zip to /data/gammaos_ota/ and
     * launching the gammaos-ota service.
     */
    public void install(String downloadId) {
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            Log.e(TAG, "Update not found: " + downloadId);
            return;
        }

        File updateFile = update.getFile();
        if (updateFile == null || !updateFile.exists()) {
            Log.e(TAG, "Update file not found");
            setFailed(downloadId);
            return;
        }

        // Set status to installing
        mUpdaterController.getActualUpdate(downloadId)
                .setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(downloadId);

        // Extract and launch in a background thread
        new Thread(() -> {
            try {
                // Create OTA directory
                File otaDir = new File(OTA_DIR);
                if (!otaDir.exists()) {
                    otaDir.mkdirs();
                }

                // Clean previous OTA files
                cleanOtaDir(otaDir);

                // Extract the zip to OTA directory
                Log.i(TAG, "Extracting " + updateFile.getAbsolutePath() + " to " + OTA_DIR);
                extractZip(updateFile, otaDir);

                // Verify manifest exists
                File manifest = new File(otaDir, "manifest.json");
                if (!manifest.exists()) {
                    Log.e(TAG, "No manifest.json in update package");
                    setFailed(downloadId);
                    return;
                }

                Log.i(TAG, "Extraction complete, launching gammaos-ota");

                // Set properties and launch the OTA service
                SystemProperties.set(Constants.PROP_GAMMAOS_OTA_PACKAGE, OTA_DIR);
                SystemProperties.set(Constants.PROP_GAMMAOS_OTA_AUTOINSTALL, "1");
                SystemProperties.set("ctl.start", "gammaos-ota");

                // The gammaos-ota binary takes over from here — it will stop the
                // framework, flash partitions, verify, and reboot.
                // We won't get any more callbacks after this point.

            } catch (IOException e) {
                Log.e(TAG, "Failed to extract update", e);
                setFailed(downloadId);
            }
        }, "GammaOtaInstaller").start();
    }

    /**
     * Launch gammaos-ota in manual mode (file browser).
     */
    public static void launchManualMode() {
        SystemProperties.set(Constants.PROP_GAMMAOS_OTA_PACKAGE, "");
        SystemProperties.set(Constants.PROP_GAMMAOS_OTA_AUTOINSTALL, "0");
        SystemProperties.set("ctl.start", "gammaos-ota");
    }

    private void setFailed(String downloadId) {
        mUpdaterController.getActualUpdate(downloadId)
                .setStatus(UpdateStatus.INSTALLATION_FAILED);
        mUpdaterController.notifyUpdateChange(downloadId);
    }

    private void cleanOtaDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    cleanOtaDir(f);
                }
                f.delete();
            }
        }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Security: prevent path traversal
                if (name.contains("..")) {
                    Log.w(TAG, "Skipping suspicious zip entry: " + name);
                    continue;
                }

                File outFile = new File(destDir, name);
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                // Ensure parent directory exists
                outFile.getParentFile().mkdirs();

                Log.d(TAG, "Extracting: " + name);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
