/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.ethernet;

import static com.android.net.module.util.DeviceConfigUtils.TETHERING_MODULE_NAME;

import android.content.ApexEnvironment;
import android.net.IpConfiguration;
import android.os.Environment;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.net.IpConfigStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This class provides an API to store and manage Ethernet network configuration.
 */
public class EthernetConfigStore {
    private static final String TAG = EthernetConfigStore.class.getSimpleName();
    private static final String CONFIG_FILE = "ipconfig.txt";
    private static final String FILE_PATH = "/misc/ethernet/";
    private static final String LEGACY_IP_CONFIG_FILE_PATH = Environment.getDataDirectory()
            + FILE_PATH;
    private static final String APEX_IP_CONFIG_FILE_PATH = ApexEnvironment.getApexEnvironment(
            TETHERING_MODULE_NAME).getDeviceProtectedDataDir() + FILE_PATH;

    private IpConfigStore mStore = new IpConfigStore();
    private final ArrayMap<String, IpConfiguration> mIpConfigurations;
    private final Object mSync = new Object();

    public EthernetConfigStore() {
        mIpConfigurations = new ArrayMap<>(0);
    }

    private static boolean doesConfigFileExist(final String filepath) {
        return new File(filepath).exists();
    }

    private void writeLegacyIpConfigToApexPath(final String newFilePath, final String oldFilePath,
            final String filename) {
        final File directory = new File(newFilePath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Write the legacy IP config to the apex file path.
        FileOutputStream fos = null;
        final AtomicFile dst = new AtomicFile(new File(newFilePath + filename));
        final AtomicFile src = new AtomicFile(new File(oldFilePath + filename));
        try {
            final byte[] raw = src.readFully();
            if (raw.length > 0) {
                fos = dst.startWrite();
                fos.write(raw);
                fos.flush();
                dst.finishWrite(fos);
            }
        } catch (IOException e) {
            Log.e(TAG, "Fail to sync the legacy IP config to the apex file path.");
            dst.failWrite(fos);
        }
    }

    public void read() {
        read(APEX_IP_CONFIG_FILE_PATH, LEGACY_IP_CONFIG_FILE_PATH, CONFIG_FILE);
    }

    @VisibleForTesting
    void read(final String newFilePath, final String oldFilePath, final String filename) {
        synchronized (mSync) {
            // Attempt to read the IP configuration from apex file path first.
            if (doesConfigFileExist(newFilePath + filename)) {
                loadConfigFileLocked(newFilePath + filename);
                return;
            }

            // If the config file doesn't exist in the apex file path, attempt to read it from
            // the legacy file path, if config file exists, write the legacy IP configuration to
            // apex config file path, this should just happen on the first boot. New or updated
            // config entries are only written to the apex config file later.
            if (!doesConfigFileExist(oldFilePath + filename)) return;
            loadConfigFileLocked(oldFilePath + filename);
            writeLegacyIpConfigToApexPath(newFilePath, oldFilePath, filename);
        }
    }

    private void loadConfigFileLocked(final String filepath) {
        // readIpConfigurations can return null when the version is invalid.
        final ArrayMap<String, IpConfiguration> configs =
                IpConfigStore.readIpConfigurations(filepath);
        if (configs == null) {
            Log.e(TAG, "IpConfigStore#readIpConfigurations() returned null");
            return;
        }
        mIpConfigurations.putAll(configs);
    }

    public void write(String iface, IpConfiguration config) {
        final File directory = new File(APEX_IP_CONFIG_FILE_PATH);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        write(iface, config, APEX_IP_CONFIG_FILE_PATH + CONFIG_FILE);
    }

    @VisibleForTesting
    void write(String iface, IpConfiguration config, String filepath) {
        boolean modified;

        synchronized (mSync) {
            if (config == null) {
                modified = mIpConfigurations.remove(iface) != null;
            } else {
                IpConfiguration oldConfig = mIpConfigurations.put(iface, config);
                modified = !config.equals(oldConfig);
            }

            if (modified) {
                mStore.writeIpConfigurations(filepath, mIpConfigurations);
            }
        }
    }

    public ArrayMap<String, IpConfiguration> getIpConfigurations() {
        synchronized (mSync) {
            return new ArrayMap<>(mIpConfigurations);
        }
    }
}
