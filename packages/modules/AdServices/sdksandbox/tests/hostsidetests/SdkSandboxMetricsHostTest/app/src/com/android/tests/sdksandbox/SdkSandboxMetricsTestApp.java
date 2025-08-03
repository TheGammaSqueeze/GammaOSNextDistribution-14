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

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.Process;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.crashtest.ICrashTestSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SdkSandboxMetricsTestApp {

    private SdkSandboxManager mSdkSandboxManager;
    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.crashtest";
    private static final String TAG_SYSTEM_APP_CRASH = "system_app_crash";
    private static final int N_DROPBOX_HEADER_BYTES = 1024;

    // Values declared in SdkSandboxMetricsTestAppManifest.xml
    private static final String PACKAGE_NAME = "com.android.tests.sdksandbox";
    private static final String VERSION_NAME = "1.0";
    private static final int VERSION_CODE = 1;

    // Values declared in CrashTestSdkProviderManifest.xml
    private static final String SDK_PACKAGE_NAME = "com.android.tests.sdkprovider.crashtest";
    private static final int SDK_VERSION_CODE = 1;

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    private DropBoxManager mDropboxManager;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final SdkLifecycleHelper mSdkLifecycleHelper = new SdkLifecycleHelper(mContext);
    private PackageManager mPackageManager;
    private String mCrashEntryText;

    @Before
    public void setup() {
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        mDropboxManager = mContext.getSystemService(DropBoxManager.class);
        mPackageManager = mContext.getPackageManager();
        assertThat(mSdkSandboxManager).isNotNull();

        // Unload the SDK before running tests to ensure that the SDK is not loaded before running a
        // test
        mSdkLifecycleHelper.unloadSdk(SDK_PACKAGE);
    }

    @After
    public void tearDown() {
        if (mSdkSandboxManager != null) {
            mSdkLifecycleHelper.unloadSdk(SDK_PACKAGE);
        }
    }

    @Test
    public void testSdkCanAccessSdkSandboxExitReasons() throws Exception {
        mRule.getScenario();
        final long timeStampBeforeCrash = System.currentTimeMillis();
        generateSdkSandboxCrash();

        // Restart the SDK sandbox
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final ICrashTestSdkApi sdk =
                ICrashTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
        final List<ApplicationExitInfo> exitInfos = sdk.getSdkSandboxExitReasons();
        ApplicationExitInfo mostRecentExitInfo = exitInfos.get(0);
        assertThat(mostRecentExitInfo).isNotNull();
        assertThat(mostRecentExitInfo.getRealUid())
                .isEqualTo(Process.toSdkSandboxUid(Process.myUid()));
        assertThat(mostRecentExitInfo.getReason()).isEqualTo(ApplicationExitInfo.REASON_CRASH);
        assertThat(mostRecentExitInfo.getTimestamp()).isGreaterThan(timeStampBeforeCrash);
    }

    @Test
    public void testAppWithDumpPermissionCanAccessSdkSandboxExitReasons() throws Exception {
        mRule.getScenario();
        final long timeStampBeforeCrash = System.currentTimeMillis();
        generateSdkSandboxCrash();

        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        assertThat(am).isNotNull();

        final ApplicationExitInfo info =
                am.getHistoricalProcessExitReasons(
                                mContext.getPackageManager().getSdkSandboxPackageName(), 0, -1)
                        .get(0);
        assertThat(info).isNotNull();
        assertThat(info.getRealUid()).isEqualTo(Process.toSdkSandboxUid(Process.myUid()));
        assertThat(info.getReason()).isEqualTo(ApplicationExitInfo.REASON_CRASH);
        assertThat(info.getTimestamp()).isGreaterThan(timeStampBeforeCrash);
    }

    // Should only be tested after another app has started and crashed their own sandbox.
    @Test
    public void testSdkCannotAccessExitReasonsFromOtherSdkSandboxUids() throws Exception {
        mRule.getScenario();

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final ICrashTestSdkApi sdk =
                ICrashTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
        final List<ApplicationExitInfo> exitInfos = sdk.getSdkSandboxExitReasons();

        // Test that all exit infos (if any) are only for its own process
        for (int i = 0; i < exitInfos.size(); i++) {
            final ApplicationExitInfo exitInfo = exitInfos.get(i);
            assertThat(exitInfo.getRealUid()).isEqualTo(Process.toSdkSandboxUid(Process.myUid()));
        }
    }

    @Test
    public void testCrashSandboxGeneratesDropboxReport() throws Exception {
        mRule.getScenario();
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!intent.getStringExtra(DropBoxManager.EXTRA_TAG)
                                .equals(TAG_SYSTEM_APP_CRASH)) {
                            return;
                        }
                        final long timeBeforeEntry =
                                intent.getLongExtra(DropBoxManager.EXTRA_TIME, 0) - 1;
                        DropBoxManager.Entry entry =
                                mDropboxManager.getNextEntry(TAG_SYSTEM_APP_CRASH, timeBeforeEntry);
                        if (entry == null) {
                            return;
                        }
                        String entryText = entry.getText(N_DROPBOX_HEADER_BYTES);
                        if (entryText == null) {
                            entry.close();
                            return;
                        }
                        // Check if SDK sandbox crash
                        if (entryText.contains(
                                String.format(
                                        "Package: %s",
                                        mPackageManager.getSdkSandboxPackageName()))) {
                            mCrashEntryText = entryText;
                            latch.countDown();
                        }
                        entry.close();
                    }
                };

        mContext.registerReceiver(
                receiver, new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED));

        generateSdkSandboxCrash();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(mCrashEntryText)
                .contains(
                        String.format(
                                "SdkSandbox-Client-Package: %s v%d (%s)",
                                PACKAGE_NAME, VERSION_CODE, VERSION_NAME));
        assertThat(mCrashEntryText)
                .contains(
                        String.format(
                                "SdkSandbox-Library: %s v%d", SDK_PACKAGE_NAME, SDK_VERSION_CODE));
    }

    private void generateSdkSandboxCrash() throws Exception {
        final CountDownLatch deathLatch = new CountDownLatch(1);
        // Avoid being killed when sandbox crashes
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, deathLatch::countDown);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        // Start and crash the SDK sandbox
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final ICrashTestSdkApi sdk =
                ICrashTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
        sdk.triggerCrash();
        assertThat(deathLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
