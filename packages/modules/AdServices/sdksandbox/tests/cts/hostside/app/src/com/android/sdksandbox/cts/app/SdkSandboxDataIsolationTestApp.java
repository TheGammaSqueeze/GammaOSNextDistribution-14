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

package com.android.sdksandbox.cts.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sdksandbox.cts.provider.dataisolationtest.IDataIsolationTestSdkApi;

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
public class SdkSandboxDataIsolationTestApp {

    private static final String APP_PKG = "com.android.sdksandbox.cts.app";
    private static final String APP_2_PKG = "com.android.sdksandbox.cts.app2";
    private static final String CURRENT_USER_ID =
            String.valueOf(Process.myUserHandle().getUserId(Process.myUid()));
    private static final String SDK_NAME = "com.android.sdksandbox.cts.provider.dataisolationtest";
    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";
    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final SdkLifecycleHelper mSdkLifecycleHelper = new SdkLifecycleHelper(mContext);

    private SdkSandboxManager mSdkSandboxManager;
    private IDataIsolationTestSdkApi mSdk;

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    @Before
    public void setup() {
        mRule.getScenario();
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);

        // unload SDK to fix flakiness
        mSdkLifecycleHelper.unloadSdk(SDK_NAME);
    }

    @After
    public void tearDown() {
        // unload SDK to fix flakiness
        mSdkLifecycleHelper.unloadSdk(SDK_NAME);
    }

    @Test
    public void testAppCannotAccessAnySandboxDirectories() throws Exception {
        assertFileAccessIsDenied("/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/" + APP_PKG);
        assertFileAccessIsDenied("/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG);
        assertFileAccessIsDenied("/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist");

        assertFileAccessIsDenied("/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/" + APP_PKG);
        assertFileAccessIsDenied("/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG);
        assertFileAccessIsDenied("/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist");
    }

    @Test
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() throws Exception {
        loadSdk();
        mSdk.testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory();
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyAppExistence() throws Exception {
        loadSdk();
        mSdk.testSdkSandboxDataIsolation_CannotVerifyAppExistence();
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence() throws Exception {
        loadSdk();
        final Bundle arguments = InstrumentationRegistry.getArguments();
        mSdk.testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence(arguments);
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes() throws Exception {
        loadSdk();
        final Bundle arguments = InstrumentationRegistry.getArguments();
        mSdk.testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes(arguments);
    }

    private void loadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        // Store the returned SDK interface so that we can interact with it later.
        mSdk = IDataIsolationTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    private static void assertFileAccessIsDenied(String path) {
        File file = new File(path);

        // Trying to access a file that does not exist in that directory, it should return
        // permission denied file not found.
        Exception exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> {
                            new FileInputStream(file);
                        });
        assertThat(exception.getMessage()).contains(JAVA_FILE_PERMISSION_DENIED_MSG);
        assertThat(exception.getMessage()).doesNotContain(JAVA_FILE_NOT_FOUND_MSG);

        assertThat(file.canExecute()).isFalse();
    }
}
