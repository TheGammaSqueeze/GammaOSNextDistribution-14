/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car;

import static android.car.CarBugreportManager.CarBugreportManagerCallback.CAR_BUGREPORT_DUMPSTATE_FAILED;
import static android.car.CarBugreportManager.CarBugreportManagerCallback.CAR_BUGREPORT_IN_PROGRESS;

import static com.android.car.CarBugreportManagerService.BUGREPORTD_SERVICE;
import static com.android.car.CarBugreportManagerService.DUMPSTATEZ_SERVICE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.car.ICarBugreportCallback;
import android.car.builtin.os.SystemPropertiesHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link CarBugreportManagerService}.
 *
 * <p>Run {@code atest CarServiceUnitTest:CarBugreportManagerServiceTest}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class CarBugreportManagerServiceTest {
    private static final boolean DUMPSTATE_DRY_RUN = true;

    private CarBugreportManagerService mService;
    private MockitoSession mSession;

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private PackageManager mMockPackageManager;
    @Mock private ICarBugreportCallback mMockCallback;
    @Mock private ParcelFileDescriptor mMockOutput;
    @Mock private ParcelFileDescriptor mMockExtraOutput;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(SystemPropertiesHelper.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @After
    public void tearDown() {
        if (mService != null) {
            mService.release();
        }
        try {
            mSession.finishMocking();
        } finally {
            // When using inline mock maker, clean up inline mocks to prevent OutOfMemory errors.
            // See https://github.com/mockito/mockito/issues/1614 and b/259280359.
            Mockito.framework().clearInlineMocks();
        }
    }

    @Test
    public void test_requestBugreport_failsIfNotDesignatedAppOnUserBuild() {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        when(mMockPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_MATCH);
        when(mMockPackageManager.getNameForUid(anyInt())).thenReturn("current_app_name");
        when(mMockPackageManager.getPackagesForUid(anyInt())).thenReturn(
                new String[]{"random_app_name"});
        when(mMockContext.getString(
                R.string.config_car_bugreport_application)).thenReturn("current_app_name");

        SecurityException expected =
                assertThrows(SecurityException.class,
                        () -> mService.requestBugreport(mMockOutput, mMockExtraOutput,
                                mMockCallback, DUMPSTATE_DRY_RUN));

        assertThat(expected).hasMessageThat().contains(
                "Caller current_app_name is not a designated bugreport app");
    }

    @Test
    public void test_requestBugreport_failsWhenBugreportServiceCannotStart() throws Exception {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        doThrow(new RuntimeException()).when(
                () -> SystemPropertiesHelper.set(anyString(), anyString()));

        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        assertThat(mService.mIsServiceRunning.get()).isFalse();
        verify(mMockCallback).onError(eq(CAR_BUGREPORT_DUMPSTATE_FAILED));
    }

    // This test tests the case where the callback that handles the error (mMockCallback#onError()),
    // can itself throw a remote exception.
    @Test
    public void test_requestBugreport_bugreportServiceCannotStart_reportErrorFails()
            throws Exception {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        doThrow(new RuntimeException()).when(
                () -> SystemPropertiesHelper.set(anyString(), anyString()));
        doThrow(new RemoteException()).when(mMockCallback).onError(anyInt());

        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        assertThat(mService.mIsServiceRunning.get()).isFalse();
    }

    @Test
    public void test_requestBugreport_serviceAlreadyRunning() throws Exception {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        doNothing().when(() -> SystemPropertiesHelper.set(anyString(),
                anyString()));

        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);
        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        verify(mMockCallback).onError(eq(CAR_BUGREPORT_IN_PROGRESS));
    }

    @Test
    public void test_requestBugreport_nonUserBuild_success() throws Exception {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ false);
        mService.init();
        when(mMockPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_MATCH);
        doNothing().when(() -> SystemPropertiesHelper.set(anyString(),
                anyString()));

        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        assertThat(mService.mIsServiceRunning.get()).isTrue();
    }

    @Test
    public void test_requestBugreport_success() throws Exception {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        doNothing().when(() -> SystemPropertiesHelper.set(anyString(),
                anyString()));

        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        assertThat(mService.mIsServiceRunning.get()).isTrue();
    }

    @Test
    public void test_cancelBugreport_success() {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        mService.cancelBugreport();

        verify(() -> SystemPropertiesHelper.set("ctl.stop", BUGREPORTD_SERVICE));
        verify(() -> SystemPropertiesHelper.set("ctl.stop", DUMPSTATEZ_SERVICE));
    }

    @Test
    public void test_cancelBugreport_serviceNotRunning() {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();

        mService.cancelBugreport();

        verify(() -> SystemPropertiesHelper.set("ctl.stop", BUGREPORTD_SERVICE),
                times(0));
        verify(() -> SystemPropertiesHelper.set("ctl.stop", DUMPSTATEZ_SERVICE),
                times(0));
    }

    @Test
    public void test_cancelBugreport_serviceCannotBeStopped() {
        mService = new CarBugreportManagerService(mMockContext, /* isUserBuild= */ true);
        mService.init();
        mockDesignatedBugReportApp();
        mService.requestBugreport(mMockOutput, mMockExtraOutput, mMockCallback, DUMPSTATE_DRY_RUN);

        doThrow(new RuntimeException()).when(() -> SystemPropertiesHelper.set(anyString(),
                anyString()));
        mService.cancelBugreport();

        verify(() -> SystemPropertiesHelper.set("ctl.stop", BUGREPORTD_SERVICE));
        verify(() -> SystemPropertiesHelper.set("ctl.stop", DUMPSTATEZ_SERVICE));
    }

    @Test
    public void test_requestBugreport_failsIfNotSignedWithPlatformKeys() {
        mService = new CarBugreportManagerService(mMockContext);
        mService.init();
        when(mMockPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_NO_MATCH);
        when(mMockPackageManager.getNameForUid(anyInt())).thenReturn("current_app_name");

        SecurityException expected =
                assertThrows(SecurityException.class,
                        () -> mService.requestBugreport(mMockOutput, mMockExtraOutput,
                                mMockCallback, DUMPSTATE_DRY_RUN));

        assertThat(expected).hasMessageThat().contains(
                "Caller current_app_name does not have the right signature");
    }

    @Test
    public void testCancelBugreport_failsIfNotSignedWithPlatformKeys() {
        mService = new CarBugreportManagerService(mMockContext);
        mService.init();
        when(mMockPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_NO_MATCH);
        when(mMockPackageManager.getNameForUid(anyInt())).thenReturn("current_app_name");

        SecurityException expected =
                assertThrows(SecurityException.class, () -> mService.cancelBugreport());

        assertThat(expected).hasMessageThat().contains(
                "Caller current_app_name does not have the right signature");
    }

    private void mockDesignatedBugReportApp() {
        when(mMockPackageManager.checkSignatures(anyInt(), anyInt()))
                .thenReturn(PackageManager.SIGNATURE_MATCH);
        when(mMockPackageManager.getNameForUid(anyInt())).thenReturn("current_app_name");
        when(mMockPackageManager.getPackagesForUid(anyInt())).thenReturn(
                new String[]{"current_app_name"});
        when(mMockContext.getString(R.string.config_car_bugreport_application))
                .thenReturn("current_app_name");
    }
}
