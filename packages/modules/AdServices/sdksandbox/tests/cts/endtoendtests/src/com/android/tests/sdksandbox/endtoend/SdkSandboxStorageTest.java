/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.DeviceSupportUtils;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ctssdkprovider.ICtsSdkProviderApi;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class SdkSandboxStorageTest extends SandboxKillerBeforeTest {

    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";
    private static final String FD_VALUE = "file-descriptor-value";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(DeviceSupportUtils.isSdkSandboxSupported(context));
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mRule.getScenario();
    }

    @After
    public void teardown() {
        try {
            mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        } catch (Exception ignored) {
        }
    }

    // Verify that the SDK is able to use the Room library for storage.
    @Test
    public void testSdkSandboxRoomDatabaseAccess() throws Exception {
        loadSdk().checkRoomDatabaseAccess();
    }

    @Test
    public void testSdkSandboxCanUseSharedPreferences() throws Exception {
        loadSdk().checkCanUseSharedPreferences();
    }

    @Test
    public void testSdkCanReadAppFileDescriptor() {
        assumeTrue(SdkLevel.isAtLeastU());
        ICtsSdkProviderApi sdk = loadSdk();

        File file = null;
        try {
            file = File.createTempFile("temp-file", ".txt");
            FileOutputStream fout = new FileOutputStream(file);
            fout.write(FD_VALUE.getBytes(StandardCharsets.UTF_8));
            fout.close();
            ParcelFileDescriptor pFd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            sdk.checkReadFileDescriptor(pFd, FD_VALUE);
        } catch (Exception e) {
            fail("Exception while creating/checking FD: " + e);
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }

    @Test
    public void testAppCanReadSdkFileDescriptor() {
        assumeTrue(SdkLevel.isAtLeastU());
        ICtsSdkProviderApi sdk = loadSdk();

        String readValue = "";
        try {
            ParcelFileDescriptor pFd = sdk.createFileDescriptor(FD_VALUE);
            FileInputStream fis = new FileInputStream(pFd.getFileDescriptor());
            readValue = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            fis.close();
            pFd.close();
        } catch (Exception e) {
            fail("Exception while reading SDK FD: " + e);
        }
        assertThat(readValue).isEqualTo(FD_VALUE);
    }

    // Helper method to load SDK_NAME_1
    private ICtsSdkProviderApi loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();
        assertNotNull(sandboxedSdk);
        return ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
