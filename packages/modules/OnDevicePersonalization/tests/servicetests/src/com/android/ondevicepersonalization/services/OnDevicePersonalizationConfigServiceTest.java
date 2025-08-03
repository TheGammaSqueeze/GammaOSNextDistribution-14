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

package com.android.ondevicepersonalization.services;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ON_DEVICE_PERSONALIZATION_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.ServiceTestRule;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.ondevicepersonalization.services.data.user.RawUserData;
import com.android.ondevicepersonalization.services.data.user.UserDataCollector;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.statsd.errorlogging.ClientErrorLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(JUnit4.class)
@MockStatic(ClientErrorLogger.class)
public class OnDevicePersonalizationConfigServiceTest {
    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private OnDevicePersonalizationConfigServiceDelegate mBinder;
    private UserPrivacyStatus mUserPrivacyStatus;
    private RawUserData mUserData;
    private UserDataCollector mUserDataCollector;
    @Mock
    private ClientErrorLogger mMockClientErrorLogger;

    @Before
    public void setup() throws Exception {

        PhFlagsTestUtil.setUpDeviceConfigPermissions();
        PhFlagsTestUtil.disableGlobalKillSwitch();
        PhFlagsTestUtil.disablePersonalizationStatusOverride();
        when(mContext.checkCallingOrSelfPermission(anyString()))
                        .thenReturn(PackageManager.PERMISSION_GRANTED);
        mBinder = new OnDevicePersonalizationConfigServiceDelegate(mContext);
        mUserPrivacyStatus = UserPrivacyStatus.getInstance();
        mUserPrivacyStatus.setPersonalizationStatusEnabled(false);
        mUserData = RawUserData.getInstance();
        TimeZone pstTime = TimeZone.getTimeZone("GMT-08:00");
        TimeZone.setDefault(pstTime);
        mUserDataCollector = UserDataCollector.getInstanceForTest(mContext);
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
    }

    @Test
    public void testThrowIfGlobalKillSwitchEnabled() throws Exception {
        PhFlagsTestUtil.enableGlobalKillSwitch();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            mBinder.setPersonalizationStatus(true, null)
            );
        } finally {
            PhFlagsTestUtil.disableGlobalKillSwitch();
        }
    }

    @Test
    public void testSetPersonalizationStatusNoCallingPermission() throws Exception {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                        .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThrows(SecurityException.class, () -> {
            mBinder.setPersonalizationStatus(true, null);
        });
    }

    @Test
    public void testSetPersonalizationStatusChanged() throws Exception {
        assertFalse(mUserPrivacyStatus.isPersonalizationStatusEnabled());
        populateUserData();
        assertNotEquals(0, mUserData.utcOffset);
        assertTrue(mUserDataCollector.isInitialized());

        CountDownLatch latch = new CountDownLatch(1);
        mBinder.setPersonalizationStatus(true,
                new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        latch.countDown();
                    }
                });

        latch.await();
        assertTrue(mUserPrivacyStatus.isPersonalizationStatusEnabled());

        assertEquals(0, mUserData.utcOffset);
        assertFalse(mUserDataCollector.isInitialized());
    }

    @Test
    public void testSetPersonalizationStatusIfCallbackMissing() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            mBinder.setPersonalizationStatus(true, null);
        });
    }

    @Test
    public void testSetPersonalizationStatusThrowsIllegalArgument() throws Exception {
        when(mContext.checkCallingOrSelfPermission(anyString()))
                .thenThrow(IllegalArgumentException.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mBinder.setPersonalizationStatus(true, null);
                });
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testSetPersonalizationStatusThrowsRuntimeException() throws Exception {
        when(mContext.getSystemService(any(Class.class))).thenThrow(RuntimeException.class);
        CountDownLatch latch = new CountDownLatch(1);
        TestCallback callback = new TestCallback(latch);

        mBinder.setPersonalizationStatus(true, callback);

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, callback.getErrCode());
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        isA(RuntimeException.class),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ON_DEVICE_PERSONALIZATION_ERROR),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__ODP));
    }

    @Test
    public void testSetPersonalizationStatusNoOps() throws Exception {
        mUserPrivacyStatus.setPersonalizationStatusEnabled(true);

        populateUserData();
        assertNotEquals(0, mUserData.utcOffset);
        int utcOffset = mUserData.utcOffset;
        assertTrue(mUserDataCollector.isInitialized());

        CountDownLatch latch = new CountDownLatch(1);
        mBinder.setPersonalizationStatus(true,
                new IOnDevicePersonalizationConfigServiceCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        latch.countDown();
                    }
                });

        latch.await();

        assertTrue(mUserPrivacyStatus.isPersonalizationStatusEnabled());
        // Adult data should not be roll-back'ed
        assertEquals(utcOffset, mUserData.utcOffset);
        assertTrue(mUserDataCollector.isInitialized());
    }

    @Test
    public void testWithBoundService() throws TimeoutException {
        Intent serviceIntent = new Intent(mContext,
                OnDevicePersonalizationConfigServiceImpl.class);
        IBinder binder = serviceRule.bindService(serviceIntent);
        assertTrue(binder instanceof OnDevicePersonalizationConfigServiceDelegate);
    }

    @After
    public void tearDown() throws Exception {
        mUserDataCollector.clearUserData(mUserData);
        mUserDataCollector.clearMetadata();
    }

    private void populateUserData() {
        mUserDataCollector.updateUserData(mUserData);
    }

    class TestCallback extends IOnDevicePersonalizationConfigServiceCallback.Stub {

        int mErrCode;
        CountDownLatch mLatch;

        TestCallback(CountDownLatch latch) {
            this.mLatch = latch;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int errorCode) {
            mErrCode = errorCode;
            mLatch.countDown();
        }

        public int getErrCode() {
            return mErrCode;
        }
    }
}
