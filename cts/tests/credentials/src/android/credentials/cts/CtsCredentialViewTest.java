/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.credentials.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Build;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.os.BuildCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class CtsCredentialViewTest {
    private static final String LOG_TAG = "CtsCredentialViewTest";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";
    private final Context mContext = getInstrumentation().getContext();
    private ViewTestCtsActivity mActivity;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(
                    DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL,
                    mContext,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Log.i(LOG_TAG, "Setting up CtsCredentialViewTest");
        assumeTrue("VERSION.SDK_INT=" + Build.VERSION.SDK_INT, BuildCompat.isAtLeastV());
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
        mActivity = mActivityRule.getActivity();
    }
    @Test
    public void testClearCredentialManagerRequest() {
        View view = new View(mActivity);
        GetCredentialRequest request = new GetCredentialRequest.Builder(Bundle.EMPTY)
                .addCredentialOption(
                        new CredentialOption.Builder(
                                "TYPE_XYZ",
                                new Bundle(),
                                new Bundle())
                                .build()
                )
                .build();

        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        assertNull(view.getCredentialManagerRequest());
        assertNull(view.getCredentialManagerCallback());

        view.setCredentialManagerRequest(request, callback);

        assertEquals(view.getCredentialManagerRequest(), request);
        assertEquals(view.getCredentialManagerCallback(), callback);

        view.clearCredentialManagerRequest();

        assertNull(view.getCredentialManagerRequest());
        assertNull(view.getCredentialManagerCallback());
    }

    @Test
    public void testSetCredentialManagerRequest() {
        View view = new View(mActivity);
        GetCredentialRequest request = new GetCredentialRequest.Builder(Bundle.EMPTY)
                .addCredentialOption(
                        new CredentialOption.Builder(
                                "TYPE_XYZ",
                                new Bundle(),
                                new Bundle())
                                .build()
                )
                .build();

        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        assertNull(view.getCredentialManagerRequest());
        assertNull(view.getCredentialManagerCallback());

        view.setCredentialManagerRequest(request, callback);

        assertEquals(view.getCredentialManagerRequest(), request);
        assertEquals(view.getCredentialManagerCallback(), callback);
    }
}
