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
 * limitations under the License
 */

package com.android.helpers;

import static com.android.helpers.MetricUtility.constructKey;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a collector helper to use PackageManager to get app package versions.
 */
public class AppVersionHelper implements ICollectorHelper<Long> {

    private static final String TAG = AppVersionHelper.class.getSimpleName();
    private static final String METRIC_PREFIX = "app-version-code-long";

    private Context context;
    private PackageManager packageManager;

    private String[] mPackageNames = {};

    public void setUp(String... packageNames) {
        context = ApplicationProvider.getApplicationContext().getApplicationContext();
        packageManager = context.getPackageManager();
        mPackageNames = packageNames;
    }

    @Override
    public boolean startCollecting() {
        return true;
    }

    @Override
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        if (mPackageNames == null || mPackageNames.length == 0 || mPackageNames[0].length() == 0) {
            // If no package specified, collects all.
            // Additional option flags is set to 0, which is simply default.
            List<PackageInfo> pkgInfos = packageManager.getInstalledPackages(0);
            for (PackageInfo pkgInfo : pkgInfos) {
                metrics.put(constructKey(METRIC_PREFIX, pkgInfo.packageName),
                        pkgInfo.getLongVersionCode());
            }
        } else {
            for (String pkg : mPackageNames) {
                try {
                    // Additional option flags is set to 0, which is simply default.
                    PackageInfo pkgInfo = packageManager.getPackageInfo(pkg, 0);
                    metrics.put(constructKey(METRIC_PREFIX, pkg),
                            pkgInfo.getLongVersionCode());
                    Log.d(TAG, "Found app version for package name " + pkg);
                } catch (PackageManager.NameNotFoundException exception) {
                    Log.e(TAG, "Can't find package name " + pkg);
                    continue;
                }
            }
        }
        return metrics;
    }

    @Override
    public boolean stopCollecting() {
        return true;
    }
}
