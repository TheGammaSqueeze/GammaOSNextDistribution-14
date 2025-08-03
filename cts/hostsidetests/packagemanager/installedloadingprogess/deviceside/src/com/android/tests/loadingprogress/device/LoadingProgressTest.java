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

package com.android.tests.loadingprogress.device;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

/**
 * Device-side test, launched by the host-side test only and should not be called directly.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LoadingProgressTest {
    private static final String TEST_PACKAGE_NAME = "com.android.tests.loadingprogress.app";
    private static final String REGISTER_APP_NAME = "com.android.tests.loadingprogress.registerapp";

    protected Context mContext;
    private PackageManager mPackageManager;
    private UserHandle mUser;
    private LauncherApps mLauncherApps;
    private static final int WAIT_TIMEOUT_MILLIS = 2000; /* 2 second */
    private ConditionVariable mCalled  = new ConditionVariable();
    private final HandlerThread mCallbackThread = new HandlerThread("callback");
    private LauncherAppsCallback mCallback;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        assertNotNull(mPackageManager);
        mUser = Process.myUserHandle();
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mCallbackThread.start();
    }

    @After
    public void tearDown() {
        mCallbackThread.quit();
        if (mCallback != null) {
            mLauncherApps.unregisterCallback(mCallback);
        }
    }

    @Test
    public void registerFirstLauncherAppsCallback() {
        final Intent intent = mPackageManager.getLaunchIntentForPackage(REGISTER_APP_NAME);
        assertNotNull(intent);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivity(intent);
    }

    @Test
    public void testGetPartialLoadingProgress() throws Exception {
        // Package is installed but only partially streamed
        checkLoadingProgress(loadingProgress -> loadingProgress < 1.0f && loadingProgress > 0);
    }

    @Test
    public void testReadAllBytes() throws Exception {
        ApplicationInfo appInfo = mLauncherApps.getApplicationInfo(
                TEST_PACKAGE_NAME, /* flags= */ 0, mUser);
        final String codePath = appInfo.sourceDir;
        final String apkDir = codePath.substring(0, codePath.lastIndexOf('/'));
        for (String apkName : new File(apkDir).list()) {
            final String apkPath = apkDir + "/" + apkName;
            assertTrue(new File(apkPath).exists());
            byte[] apkContentBytes = Files.readAllBytes(Paths.get(apkPath));
            assertNotNull(apkContentBytes);
            assertTrue(apkContentBytes.length > 0);
        }
    }

    @Test
    public void testGetFullLoadingProgress() throws Exception {
        // Package should be fully streamed now
        checkLoadingProgress(loadingProgress -> (1 - loadingProgress) < 0.001f);
    }

    private void checkLoadingProgress(Predicate<Float> progressCondition) {
        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(TEST_PACKAGE_NAME, mUser);
        boolean foundTestApp = false;
        for (LauncherActivityInfo activity : activities) {
            if (activity.getComponentName().getPackageName().equals(
                    TEST_PACKAGE_NAME)) {
                foundTestApp = true;
                final float progress = activity.getLoadingProgress();
                assertTrue("progress <" + progress + "> does not meet requirement",
                        progressCondition.test(progress));
            }
            assertTrue(activity.getUser().equals(mUser));
        }
        assertTrue(foundTestApp);
    }

    @Test
    public void testOnPackageLoadingProgressChangedCalledWithPartialLoaded() throws Exception {
        mCalled.close();
        mCallback = new LauncherAppsCallback(
                loadingProgress -> loadingProgress < 1.0f && loadingProgress > 0);
        mLauncherApps.registerCallback(mCallback, new Handler(mCallbackThread.getLooper()));
        assertTrue(mCalled.block(WAIT_TIMEOUT_MILLIS));
    }

    @Test
    public void testOnPackageLoadingProgressChangedCalledWithFullyLoaded() throws Exception {
        mCalled.close();
        mCallback = new LauncherAppsCallback(loadingProgress -> 1 - loadingProgress < 0.001);
        mLauncherApps.registerCallback(mCallback, new Handler(mCallbackThread.getLooper()));
        testReadAllBytes();
        assertTrue(mCalled.block(WAIT_TIMEOUT_MILLIS));
    }

    class LauncherAppsCallback extends LauncherApps.Callback {
        private final Predicate<Float> mCondition;
        LauncherAppsCallback(Predicate<Float> progressCondition) {
            mCondition = progressCondition;
        }
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
        }

        @Override
        public void onPackageLoadingProgressChanged(@NonNull String packageName,
                @NonNull UserHandle user, float progress) {
            if (mCondition.test(progress)) {
                // Only release when progress meets the expected condition
                mCalled.open();
            }
        }
    }
}
