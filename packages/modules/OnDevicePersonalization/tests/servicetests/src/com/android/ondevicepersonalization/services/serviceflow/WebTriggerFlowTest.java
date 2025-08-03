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

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions.NOTIFY_MEASUREMENT_EVENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParamsParcel;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.TestableDeviceConfig;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@RunWith(JUnit4.class)
public class WebTriggerFlowTest {

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);

    private boolean mCallbackSuccess;
    private boolean mCallbackError;
    private int mCallbackErrorCode;
    private ServiceFlowOrchestrator mSfo;

    @Mock UserPrivacyStatus mUserPrivacyStatus;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .addStaticMockFixtures(TestableDeviceConfig::new)
            .spyStatic(UserPrivacyStatus.class)
            .setStrictness(Strictness.LENIENT)
            .build();

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        PhFlagsTestUtil.disableGlobalKillSwitch();

        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);

        setUpTestData();

        mSfo = new ServiceFlowOrchestrator();
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
    }

    @Test
    public void testWebTriggerFlow_GlobalKillswitchOn() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, getWebTriggerParams(), mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_EnforceCallerPermission() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, getWebTriggerParams(), mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_EmptyWebTriggerParams() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        Bundle emptyWebTriggerParams = new Bundle();
        emptyWebTriggerParams.putParcelable(
                Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                null);

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, emptyWebTriggerParams, mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_EmptyDestionalUrl() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        Bundle emptyClassParams = new Bundle();
        emptyClassParams.putParcelable(
                Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                new MeasurementWebTriggerEventParamsParcel(
                        Uri.parse(""),
                        "com.example.browser",
                        ComponentName.createRelative(mContext.getPackageName(),
                                "com.test.TestPersonalizationService"),
                        null, new byte[] {1, 2, 3}));

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, emptyClassParams, mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_InvalidCertDigest() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        Bundle invalidCertDigestParams = new Bundle();
        invalidCertDigestParams.putParcelable(
                Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                new MeasurementWebTriggerEventParamsParcel(
                        Uri.parse("http://landingpage"),
                        "com.example.browser",
                        ComponentName.createRelative(mContext.getPackageName(),
                                "com.test.TestPersonalizationService"),
                        "randomTestCertDigest", new byte[] {1, 2, 3}));

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, invalidCertDigestParams, mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_InvalidClassName() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        Bundle invalidPackageNameParams = new Bundle();
        invalidPackageNameParams.putParcelable(
                Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                new MeasurementWebTriggerEventParamsParcel(
                        Uri.parse("http://landingpage"),
                        "com.example.browser",
                        ComponentName.createRelative(mContext.getPackageName(),
                                "not.com.test.TestPersonalizationService"),
                        null, new byte[] {1, 2, 3}));

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, invalidPackageNameParams, mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_CLASS_NOT_FOUND, mCallbackErrorCode);
    }

    @Test
    public void testWebTriggerFlow_Success() throws Exception {
        when(mContext.checkCallingOrSelfPermission(NOTIFY_MEASUREMENT_EVENT))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        mSfo.schedule(ServiceFlowType.WEB_TRIGGER_FLOW, getWebTriggerParams(), mContext,
                new TestWebCallback(), 100L);
        mLatch.await();

        assertTrue(mCallbackSuccess);
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
                null, new byte[] {1, 2, 3}));
        return params;
    }

    private void setUpTestData() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        ComponentName service = new ComponentName("com.example.test", "cls");
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                DbUtils.toTableValue(service),
                "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder().setService(service).setQueryData(
                        queryDataBytes).build());
        EventsDao.getInstanceForTest(mContext);
    }

    class TestWebCallback extends IRegisterMeasurementEventCallback.Stub {
        @Override
        public void onSuccess() {
            mCallbackSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mLatch.countDown();
        }
    }
}
