/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.helpers;

import android.util.Log;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a collector helper that collects the dumpsys output for specified services and puts them
 * into files.
 */
public class DumpsysServiceHelper implements ICollectorHelper<String> {

    private static final String TAG = DumpsysServiceHelper.class.getSimpleName();

    private static final String DUMPSYS_CMD = "dumpsys %s";

    private static final String DUMPSYS_OUTPUT_DIRECTORY = "/sdcard/test_results";

    private static final String DUMPSYS_OUTPUT_FILENAME = "dumpsys_service_%s.txt";

    private static final String DUMPSYS_SERVICE_KEY = "dumpsys_service_%s";

    private String[] mServiceNames = {};
    private UiDevice mUiDevice;

    public void setUp(String... serviceNames) {
        if (serviceNames == null) {
            return;
        }
        mServiceNames = serviceNames;
        File outputDirectory = new File(DUMPSYS_OUTPUT_DIRECTORY);
        boolean success = outputDirectory.mkdirs();
        if (!success) {
            Log.w(TAG, "Failed to create output directory.");
        }
    }

    @Override
    public boolean startCollecting() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        return true;
    }

    @Override
    public Map<String, String> getMetrics() {
        Map<String, String> metrics = new HashMap<>();
        for (String serviceName : mServiceNames) {
            String outputPath = runDumpsysCmd(serviceName);
            metrics.put(String.format(DUMPSYS_SERVICE_KEY, serviceName), outputPath);
        }
        return metrics;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }

    private String runDumpsysCmd(String serviceName) {
        String outputPath =
                Paths.get(
                                DUMPSYS_OUTPUT_DIRECTORY,
                                String.format(DUMPSYS_OUTPUT_FILENAME, serviceName))
                        .toString();
        final String cmd = String.format(DUMPSYS_CMD, serviceName);
        try {
            String res = mUiDevice.executeShellCommand(cmd);
            FileOutputStream outputStream = new FileOutputStream(outputPath);
            outputStream.write(res.getBytes());
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to run dumpsys command.");
        }
        return outputPath;
    }
}
