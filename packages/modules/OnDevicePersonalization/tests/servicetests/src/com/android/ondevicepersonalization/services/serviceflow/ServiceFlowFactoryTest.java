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

package com.android.ondevicepersonalization.services.serviceflow;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParamsParcel;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.view.SurfaceControlViewHost;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.request.AppRequestFlow;
import com.android.ondevicepersonalization.services.request.RenderFlow;
import com.android.ondevicepersonalization.services.webtrigger.WebTriggerFlow;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ServiceFlowFactoryTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
    }

    @Test
    public void testCreateAppRequestFlowInstance() throws Exception {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(
                ServiceFlowType.APP_REQUEST_FLOW, "testCallingPackage",
                new ComponentName("testPackage", "testClass"), new Bundle(),
                new TestExecuteCallback(), mContext, 0L);

        assertThat(serviceFlow).isNotNull();
        assertThat(serviceFlow).isInstanceOf(AppRequestFlow.class);
    }

    @Test
    public void testCreateRenderFlowInstance() throws Exception {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(
                ServiceFlowType.RENDER_FLOW, "testToken", new Binder(), 0,
                100, 50, new TestRenderFlowCallback(), mContext, 0L);

        assertThat(serviceFlow).isNotNull();
        assertThat(serviceFlow).isInstanceOf(RenderFlow.class);
    }

    @Test(expected = ClassCastException.class)
    public void testCreateAppRequestFlowInstance_IllegalInputClass() throws Exception {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(
                ServiceFlowType.APP_REQUEST_FLOW, "testToken", new Binder(), 0,
                100, 50, new TestRenderFlowCallback(), mContext, 0L);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testCreateAppRequestFlowInstance_WrongArgumentLength() throws Exception {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(
                ServiceFlowType.APP_REQUEST_FLOW);
    }

    @Test
    public void testCreateWebTriggerFlowInstance() throws Exception {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(
                ServiceFlowType.WEB_TRIGGER_FLOW, getWebTriggerParams(), mContext,
                new TestWebCallback(), 0L);

        assertThat(serviceFlow).isNotNull();
        assertThat(serviceFlow).isInstanceOf(WebTriggerFlow.class);
    }

    class TestExecuteCallback extends IExecuteCallback.Stub {
        @Override
        public void onSuccess(Bundle bundle) {}
        @Override
        public void onError(int errorCode, int isolatedServiceErrorCode) {}
    }

    class TestRenderFlowCallback extends IRequestSurfacePackageCallback.Stub {
        @Override public void onSuccess(SurfaceControlViewHost.SurfacePackage surfacePackage) {}
        @Override public void onError(int errorCode, int isolatedServiceErrorCode) {}
    }

    class TestWebCallback extends IRegisterMeasurementEventCallback.Stub {
        @Override
        public void onSuccess() {}

        @Override
        public void onError(int errorCode) {}
    }

    private Bundle getWebTriggerParams() {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                new MeasurementWebTriggerEventParamsParcel(
                        Uri.parse("http://landingpage"),
                        "com.example.browser",
                        ComponentName.createRelative(mContext.getPackageName(),
                                "com.test.TestPersonalizationService"),
                        null, new byte[]{1, 2, 3}));
        return params;
    }
}
