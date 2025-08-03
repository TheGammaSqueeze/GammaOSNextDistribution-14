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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.view.SurfaceControlViewHost.SurfacePackage;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.ondevicepersonalization.services.PhFlagsTestUtil;
import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.data.events.Query;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.request.SlotWrapper;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

@RunWith(Parameterized.class)
public class RenderFlowTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final OnDevicePersonalizationDbHelper mDbHelper =
            OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);


    private boolean mCallbackSuccess;
    private boolean mCallbackError;
    private int mCallbackErrorCode;
    private int mIsolatedServiceErrorCode;
    private Bundle mCallbackResult;
    private MockitoSession mSession;
    private ServiceFlowOrchestrator mSfo;

    @Mock UserPrivacyStatus mUserPrivacyStatus;
    @Mock CryptUtils mCryptUtils;

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setup() throws Exception {
        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        ShellUtils.runShellCommand("settings put global hidden_api_policy 1");
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);

        setUpTestDate();

        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .spyStatic(UserPrivacyStatus.class)
                .spyStatic(CryptUtils.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        ExtendedMockito.doReturn(mUserPrivacyStatus).when(UserPrivacyStatus::getInstance);

        mSfo = new ServiceFlowOrchestrator();
    }

    @After
    public void cleanup() {
        mDbHelper.getWritableDatabase().close();
        mDbHelper.getReadableDatabase().close();
        mDbHelper.close();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testRenderFlow_PersonalizationDisabled() throws Exception {
        doReturn(false).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();

        mSfo.schedule(ServiceFlowType.RENDER_FLOW, "token", new Binder(), 0,
                100, 50, new TestRenderFlowCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_PERSONALIZATION_DISABLED, mCallbackErrorCode);
    }

    @Test
    public void testRunRenderFlow_InvalidToken() throws Exception {
        doReturn(true).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();

        mSfo.schedule(ServiceFlowType.RENDER_FLOW, "token", new Binder(), 0,
                100, 50, new TestRenderFlowCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackError);
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testRunRenderFlow_Success() throws Exception {
        doReturn(true).when(mUserPrivacyStatus).isPersonalizationStatusEnabled();
        ExtendedMockito.doReturn(
                        new SlotWrapper(
                                new RequestLogRecord.Builder()
                                        .build(),
                                new RenderingConfig.Builder()
                                        .addKey("bid1")
                                        .addKey("bid2")
                                        .build(),
                                mContext.getPackageName(), 0))
                .when(() -> CryptUtils.decrypt("token"));

        mSfo.schedule(ServiceFlowType.RENDER_FLOW, "token", new Binder(), 0,
                100, 50, new TestRenderFlowCallback(), mContext, 100L);
        mLatch.await();

        assertTrue(mCallbackSuccess);
    }

    private void setUpTestDate() {
        ArrayList<ContentValues> rows = new ArrayList<>();
        ContentValues row1 = new ContentValues();
        row1.put("a", 1);
        rows.add(row1);
        ContentValues row2 = new ContentValues();
        row2.put("b", 2);
        rows.add(row2);
        ComponentName service = new ComponentName(
                mContext.getPackageName(), "com.test.TestPersonalizationService");
        byte[] queryDataBytes = OnDevicePersonalizationFlatbufferUtils.createQueryData(
                DbUtils.toTableValue(service), "AABBCCDD", rows);
        EventsDao.getInstanceForTest(mContext).insertQuery(
                new Query.Builder().setService(service).setQueryData(
                        queryDataBytes).build());
        EventsDao.getInstanceForTest(mContext);
    }

    class TestRenderFlowCallback extends IRequestSurfacePackageCallback.Stub {
        @Override public void onSuccess(SurfacePackage surfacePackage) {
            mCallbackSuccess = true;
            mLatch.countDown();
        }
        @Override public void onError(int errorCode, int isolatedServiceErrorCode) {
            mCallbackError = true;
            mCallbackErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mLatch.countDown();
        }
    }
}
