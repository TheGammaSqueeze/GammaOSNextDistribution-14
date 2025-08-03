/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.sdksandbox;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.tests.codeprovider.storagetest_1.IStorageTestSdk1Api;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@RunWith(JUnit4.class)
public class SdkSandboxStorageTestApp {

    private static final String TAG = "SdkSandboxStorageTestApp";

    private static final String SDK_NAME = "com.android.tests.codeprovider.storagetest";
    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final SdkLifecycleHelper mSdkLifecycleHelper = new SdkLifecycleHelper(mContext);

    private SdkSandboxManager mSdkSandboxManager;
    private IStorageTestSdk1Api mSdk;
    private UiDevice mUiDevice;

    @Before
    public void setup() {
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        mRule.getScenario();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // unload SDK to fix flakiness
        mSdkLifecycleHelper.unloadSdk(SDK_NAME);
    }

    @After
    public void tearDown() {
        // unload SDK to fix flakiness
        mSdkLifecycleHelper.unloadSdk(SDK_NAME);
    }

    @Test
    public void loadSdk() throws Exception {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        // Store the returned SDK interface so that we can interact with it later.
        mSdk = IStorageTestSdk1Api.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    @Test
    public void unlockDevice() throws Exception {
        mUiDevice.wakeUp();
        mUiDevice.waitForIdle();
        mUiDevice.pressMenu();
        mUiDevice.waitForIdle();
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mUiDevice.waitForIdle();
        mUiDevice.pressEnter();
        mUiDevice.waitForIdle();
        mUiDevice.pressHome();
        mUiDevice.waitForIdle();
    }

    @Test
    public void testSdkSandboxDataRootDirectory_IsNotAccessibleByApps() throws Exception {
        assertDirIsNotAccessible("/data/misc_ce/0/sdksandbox");
        assertDirIsNotAccessible("/data/misc_de/0/sdksandbox");
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        loadSdk();

        mSdk.verifySharedStorageIsUsable();
    }

    @Test
    public void testSdkDataSubDirectory_PerSdkStorageIsUsable() throws Exception {
        loadSdk();

        mSdk.verifyPerSdkStorageIsUsable();
    }

    @Test
    public void testSdkDataIsAttributedToApp() throws Exception {
        loadSdk();

        final StorageStatsManager stats = InstrumentationRegistry.getInstrumentation().getContext()
                                                .getSystemService(StorageStatsManager.class);
        int uid = Process.myUid();
        UserHandle user = Process.myUserHandle();

        final StorageStats initialAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats initialUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        // Have the sdk use up space
        final int sizeInBytes = 10000000; // 10 MB
        mSdk.createFilesInStorage(sizeInBytes);

        final StorageStats finalAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats finalUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        long deltaAppSize = 4 * sizeInBytes;
        long deltaCacheSize = 2 * sizeInBytes;

        // Assert app size is same
        final long appSizeAppStats = finalAppStats.getDataBytes() - initialAppStats.getDataBytes();
        final long appSizeUserStats =
                finalUserStats.getDataBytes() - initialUserStats.getDataBytes();
        assertMostlyEquals(deltaAppSize, appSizeAppStats, 5);
        assertMostlyEquals(deltaAppSize, appSizeUserStats, 10);

        // Assert cache size is same
        final long cacheSizeAppStats =
                finalAppStats.getCacheBytes() - initialAppStats.getCacheBytes();
        final long cacheSizeUserStats =
                finalUserStats.getCacheBytes() - initialUserStats.getCacheBytes();
        assertMostlyEquals(deltaCacheSize, cacheSizeAppStats, 5);
        assertMostlyEquals(deltaCacheSize, cacheSizeUserStats, 10);
    }

    private static void assertDirIsNotAccessible(String path) {
        // Trying to access a file that does not exist in that directory, it should return
        // permission denied not file not found.
        Exception exception = assertThrows(FileNotFoundException.class, () -> {
            new FileInputStream(new File(path, "FILE_DOES_NOT_EXIST"));
        });
        assertThat(exception.getMessage()).contains(JAVA_FILE_PERMISSION_DENIED_MSG);
        assertThat(exception.getMessage()).doesNotContain(JAVA_FILE_NOT_FOUND_MSG);

        assertThat(new File(path).canExecute()).isFalse();
    }

    private static void assertMostlyEquals(
            long expected, long actual, long errorMarginInPercentage) {
        final double diffInSize = Math.abs(expected - actual);
        final double diffInPercentage = (diffInSize / expected) * 100;
        if (diffInPercentage > errorMarginInPercentage) {
            throw new AssertionFailedError(
                    "Expected roughly "
                            + expected
                            + " but was "
                            + actual
                            + ". Diff in percentage: "
                            + Math.round(diffInPercentage * 100) / 100.00);
        }
    }
}
