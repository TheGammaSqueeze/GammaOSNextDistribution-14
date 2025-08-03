/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FilesAppPerfTest {
    private static final String TAG = "FilesAppPerfTest";

    // Keys used to report metrics to APCT.
    private static final String KEY_FILES_COLD_START_PERFORMANCE_MEDIAN =
            "files-cold-start-performance-median";
    private static final String KEY_FILES_WARM_START_PERFORMANCE_MEDIAN =
            "files-warm-start-performance-median";

    private static final int NUM_MEASUREMENTS = 10;
    private static final long REMOVAL_TIMEOUT_MS = 3000;
    private static final long TIMEOUT_INTERVAL_MS = 200;

    private Instrumentation mInstrumentation;
    private Context mContext;
    private LauncherActivity mLauncherActivity;
    private ActivityInfo mDocumentsUiActivityInfo;

    @Before
    public void setUp() {
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        final ResolveInfo info = mContext.getPackageManager().resolveActivity(
                LauncherActivity.OPEN_DOCUMENT_INTENT, PackageManager.ResolveInfoFlags.of(0));
        assumeNotNull(info);
        mDocumentsUiActivityInfo = info.activityInfo;
        mLauncherActivity = (LauncherActivity) mInstrumentation.startActivitySync(
                new Intent(mContext, LauncherActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @After
    public void tearDown() {
        mLauncherActivity.finishAndRemoveTask();
    }

    @Test
    public void testFilesColdStartPerformance() throws Exception {
        runFilesStartPerformanceTest(true);
    }

    @Test
    public void testFilesWarmStartPerformance() throws Exception {
        runFilesStartPerformanceTest(false);
    }

    public void runFilesStartPerformanceTest(boolean cold) throws Exception {
        final String documentsUiPackageName = mDocumentsUiActivityInfo.packageName;
        String[] providerPackageNames = null;
        if (cold) {
            providerPackageNames = getDocumentsProviderPackageNames();
        }
        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        long[] measurements = new long[NUM_MEASUREMENTS];
        for (int i = 0; i < NUM_MEASUREMENTS; i++) {
            if (cold) {
                // Kill all providers, as well as DocumentsUI to measure a cold start.
                for (String pkgName : providerPackageNames) {
                    // Use kill-bg to avoid affecting other important services.
                    Log.i(TAG, "killBackgroundProcesses " + pkgName);
                    am.killBackgroundProcesses(pkgName);
                }
                Log.i(TAG, "forceStopPackage " + documentsUiPackageName);
                am.forceStopPackage(documentsUiPackageName);
                // Wait for any closing animations to finish.
                mInstrumentation.getUiAutomation().syncInputTransactions();
            }

            measurements[i] = mLauncherActivity.startAndWaitDocumentsUi();
            // The DocumentUi will finish automatically according to the request code for testing,
            // so wait until it is completely removed to avoid affecting next iteration.
            waitUntilDocumentsUiActivityRemoved();
        }

        reportMetrics(cold ? KEY_FILES_COLD_START_PERFORMANCE_MEDIAN
                : KEY_FILES_WARM_START_PERFORMANCE_MEDIAN, measurements);
    }

    private void reportMetrics(String key, long[] measurements) {
        final Bundle status = new Bundle();
        Arrays.sort(measurements);
        final long median = measurements[NUM_MEASUREMENTS / 2 - 1];
        status.putDouble(key + "(ms)", median);

        mInstrumentation.sendStatus(Activity.RESULT_OK, status);
    }

    private String[] getDocumentsProviderPackageNames() {
        final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
        final List<ResolveInfo> providers = mContext.getPackageManager()
                .queryIntentContentProviders(intent, PackageManager.ResolveInfoFlags.of(0));
        final String[] pkgNames = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            pkgNames[i] = providers.get(i).providerInfo.packageName;
        }
        return pkgNames;
    }

    private void waitUntilDocumentsUiActivityRemoved() {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        final String classPattern = new ComponentName(mDocumentsUiActivityInfo.packageName,
                mDocumentsUiActivityInfo.name).flattenToShortString();
        final long startTime = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - startTime <= REMOVAL_TIMEOUT_MS) {
            SystemClock.sleep(TIMEOUT_INTERVAL_MS);
            final String windowTokenDump;
            try {
                windowTokenDump = uiDevice.executeShellCommand("dumpsys window tokens");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!windowTokenDump.contains(classPattern)) {
                return;
            }
        }
        Log.i(TAG, "Removal timeout of " + classPattern);
    }
}
