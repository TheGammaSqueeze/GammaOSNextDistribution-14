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

package com.android.server.sdksandbox;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.server.sdksandbox.verifier.SdkDexVerifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.io.File;

/** Unit tests for {@link SdkSandboxVerifierReceiver}. */
public class SdkSandboxVerifierReceiverUnitTest {

    private static final Intent VERIFY_INTENT =
            new Intent().setData(Uri.fromFile(new File("sdk.apk")));
    private static final PackageInfo FAKE_PACKAGE_INFO = new PackageInfo();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private MockitoSession mStaticMockSession;
    private SdkSandboxVerifierReceiver mVerifierReceiver;
    private Context mSpyContext;
    private PackageManager mSpyPm;
    private SdkDexVerifier mSpyDexVerifier;
    private Handler mSpyHandler;

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.PACKAGE_VERIFICATION_AGENT);

        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession().spyStatic(DeviceConfig.class).initMocks(this);
        mVerifierReceiver = new SdkSandboxVerifierReceiver();
        mSpyDexVerifier = Mockito.spy(SdkDexVerifier.getInstance());
        mVerifierReceiver.setSdkDexVerifier(mSpyDexVerifier);

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);
        PackageManager pm = mSpyContext.getPackageManager();
        mSpyPm = Mockito.spy(pm);
        mSpyHandler = Mockito.spy(HANDLER);

        Mockito.when(mSpyContext.getPackageManager()).thenReturn(mSpyPm);
        Mockito.when(mSpyPm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(FAKE_PACKAGE_INFO);
        mStaticMockSession = mockitoSessionBuilder.startMocking();
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void verifierBroadcastReceived_startsDexParsing() {
        ExtendedMockito.when(
                        DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                SdkSandboxManagerService.PROPERTY_ENFORCE_RESTRICTIONS,
                                SdkSandboxManagerService.DEFAULT_VALUE_ENFORCE_RESTRICTIONS))
                .thenReturn(true);

        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(1)).post(Mockito.any());
    }

    @Test
    public void verifierBroadcastReceived_doesNotStartDexParsing() {
        ExtendedMockito.when(
                        DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                SdkSandboxManagerService.PROPERTY_ENFORCE_RESTRICTIONS,
                                SdkSandboxManagerService.DEFAULT_VALUE_ENFORCE_RESTRICTIONS))
                .thenReturn(false);

        mVerifierReceiver.verifySdkHandler(mSpyContext, VERIFY_INTENT, mSpyHandler);

        Mockito.verify(mSpyHandler, Mockito.times(0)).post(Mockito.any());
    }
}
