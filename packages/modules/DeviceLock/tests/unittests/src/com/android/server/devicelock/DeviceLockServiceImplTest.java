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

package com.android.server.devicelock;

import static android.app.AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;
import static android.devicelock.IDeviceLockService.KEY_REMOTE_CALLBACK_RESULT;

import static com.android.server.devicelock.DeviceLockControllerPackageUtils.SERVICE_ACTION;
import static com.android.server.devicelock.DeviceLockServiceImpl.MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER;
import static com.android.server.devicelock.DeviceLockServiceImpl.OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION;
import static com.android.server.devicelock.DeviceLockServiceImpl.OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS;
import static com.android.server.devicelock.DeviceLockServiceImpl.OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS;
import static com.android.server.devicelock.TestUtils.eventually;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.AppOpsManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.devicelock.IGetDeviceIdCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerExemptionManager;
import android.os.Process;
import android.os.RemoteCallback;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.IDeviceLockControllerService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAppOpsManager;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowTelephonyManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link com.android.server.devicelock.DeviceLockServiceImpl}.
 */
@RunWith(RobolectricTestRunner.class)
public final class DeviceLockServiceImplTest {
    private static final String DLC_PACKAGE_NAME = "test.package";

    private static final String DLC_SERVICE_NAME = "test.service";

    private static final long ONE_SEC_MILLIS = 1000;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private Context mContext;
    private ShadowTelephonyManager mShadowTelephonyManager;
    private ShadowAppOpsManager mShadowAppOpsManager;

    @Mock
    private IDeviceLockControllerService mDeviceLockControllerService;
    @Mock
    private PowerExemptionManager mPowerExemptionManager;

    private ShadowApplication mShadowApplication;

    private DeviceLockServiceImpl mService;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mShadowApplication = shadowOf((Application) mContext);
        mShadowApplication.grantPermissions(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER);
        mShadowApplication.setSystemService(
                mContext.getSystemServiceName(PowerExemptionManager.class),
                mPowerExemptionManager);

        PackageManager packageManager = mContext.getPackageManager();
        ShadowPackageManager shadowPackageManager = shadowOf(packageManager);
        shadowPackageManager.setPackagesForUid(Process.myUid(),
                new String[]{mContext.getPackageName()});

        PackageInfo dlcPackageInfo = new PackageInfo();
        dlcPackageInfo.packageName = DLC_PACKAGE_NAME;
        shadowPackageManager.installPackage(dlcPackageInfo);

        Intent intent = new Intent(SERVICE_ACTION);
        ResolveInfo resolveInfo = makeDlcResolveInfo();
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo);

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        mShadowTelephonyManager = shadowOf(telephonyManager);

        mShadowAppOpsManager = shadowOf(mContext.getSystemService(AppOpsManager.class));

        mService = new DeviceLockServiceImpl(mContext, telephonyManager);
    }

    @Test
    public void getDeviceId_withIMEIType_shouldReturnIMEI() throws Exception {
        // GIVEN an IMEI registered in telephony manager
        final String testImei = "983402979622353";
        mShadowTelephonyManager.setActiveModemCount(1);
        mShadowTelephonyManager.setImei(/* slotIndex= */ 0, testImei);

        // GIVEN a successful service call to DLC app
        doAnswer((Answer<Void>) invocation -> {
            RemoteCallback callback = invocation.getArgument(0);
            Bundle bundle = new Bundle();
            bundle.putString(IDeviceLockControllerService.KEY_RESULT, testImei);
            callback.sendResult(bundle);
            return null;
        }).when(mDeviceLockControllerService).getDeviceIdentifier(any(RemoteCallback.class));

        IGetDeviceIdCallback mockCallback = mock(IGetDeviceIdCallback.class);

        // WHEN the device id is requested with the IMEI device type
        mService.getDeviceId(mockCallback, 1 << DEVICE_ID_TYPE_IMEI);
        waitUntilConnected();

        // THEN the IMEI id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_IMEI), eq(testImei));
    }

    @Test
    public void getDeviceId_withMEIDType_shouldReturnMEID() throws Exception {
        // GIVEN an MEID registered in telephony manager
        final String testMeid = "354403064522046";
        mShadowTelephonyManager.setActiveModemCount(1);
        mShadowTelephonyManager.setMeid(/* slotIndex= */ 0, testMeid);

        // GIVEN a successful service call to DLC app
        doAnswer((Answer<Void>) invocation -> {
            RemoteCallback callback = invocation.getArgument(0);
            Bundle bundle = new Bundle();
            bundle.putString(IDeviceLockControllerService.KEY_RESULT, testMeid);
            callback.sendResult(bundle);
            return null;
        }).when(mDeviceLockControllerService).getDeviceIdentifier(any(RemoteCallback.class));

        IGetDeviceIdCallback mockCallback = mock(IGetDeviceIdCallback.class);

        // WHEN the device id is requested with the MEID device type
        mService.getDeviceId(mockCallback, 1 << DEVICE_ID_TYPE_MEID);
        waitUntilConnected();

        // THEN the MEID id is received
        verify(mockCallback, timeout(ONE_SEC_MILLIS)).onDeviceIdReceived(
                eq(DEVICE_ID_TYPE_MEID), eq(testMeid));
    }

    @Test
    public void setCallerAllowedToSendUndismissibleNotifications_trueAllowsAppOp() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setCallerAllowedToSendUndismissibleNotifications(true, callback);

        assertThat(succeeded.get()).isTrue();
        final int opMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS,
                Process.myUid(),
                DLC_PACKAGE_NAME);
        assertThat(opMode).isEqualTo(AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void setCallerAllowedToSendUndismissibleNotifications_falseDisallowsAppOp() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setCallerAllowedToSendUndismissibleNotifications(false, callback);

        assertThat(succeeded.get()).isTrue();
        final int opMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS,
                Process.myUid(),
                DLC_PACKAGE_NAME);
        assertThat(opMode).isEqualTo(AppOpsManager.MODE_DEFAULT);
    }

    @Test
    public void setCallerExemptFromActivityBgStartRestrictionState_trueAllowsAppOp() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setCallerExemptFromActivityBgStartRestrictionState(true, callback);

        assertThat(succeeded.get()).isTrue();
        final int opMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION,
                Process.myUid(),
                DLC_PACKAGE_NAME);
        assertThat(opMode).isEqualTo(AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void setCallerExemptFromActivityBgStartRestrictionState_falseDisallowsAppOp() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setCallerExemptFromActivityBgStartRestrictionState(false, callback);

        assertThat(succeeded.get()).isTrue();
        final int opMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION,
                Process.myUid(),
                DLC_PACKAGE_NAME);
        assertThat(opMode).isEqualTo(AppOpsManager.MODE_DEFAULT);
    }

    @Test
    public void setUidExemptFromRestrictionsState_trueAllowsAppOps() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setUidExemptFromRestrictionsState(Process.myUid(), true, callback);

        assertThat(succeeded.get()).isTrue();
        final int hibernationOpMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION,
                Process.myUid(),
                mContext.getPackageName());
        assertThat(hibernationOpMode).isEqualTo(AppOpsManager.MODE_ALLOWED);
        final int powerOpMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS,
                Process.myUid(),
                mContext.getPackageName());
        assertThat(powerOpMode).isEqualTo(AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void setUidExemptFromRestrictionsState_falseDisallowsAppOps() {
        final AtomicBoolean succeeded = new AtomicBoolean(false);
        RemoteCallback callback = new RemoteCallback(result -> {
            succeeded.set(result.getBoolean(KEY_REMOTE_CALLBACK_RESULT));
        });
        mService.setUidExemptFromRestrictionsState(Process.myUid(), false, callback);

        assertThat(succeeded.get()).isTrue();
        final int hibernationOpMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION,
                Process.myUid(),
                mContext.getPackageName());
        assertThat(hibernationOpMode).isEqualTo(AppOpsManager.MODE_DEFAULT);
        final int powerOpMode = mShadowAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS,
                Process.myUid(),
                mContext.getPackageName());
        assertThat(powerOpMode).isEqualTo(AppOpsManager.MODE_DEFAULT);
    }

    /**
     * Make the resolve info for the DLC package.
     */
    private ResolveInfo makeDlcResolveInfo() {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.privateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = DLC_SERVICE_NAME;
        serviceInfo.packageName = DLC_PACKAGE_NAME;
        serviceInfo.applicationInfo = appInfo;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;

        return resolveInfo;
    }

    /**
     * Set-up calls to mock the service being connected.
     */
    private void waitUntilConnected() {
        eventually(() -> {
            shadowOf(Looper.getMainLooper()).idle();
            ServiceConnection connection = mShadowApplication.getBoundServiceConnections().get(0);
            Binder binder = new Binder();
            binder.attachInterface(mDeviceLockControllerService,
                    IDeviceLockControllerService.class.getName());
            connection.onServiceConnected(new ComponentName(DLC_PACKAGE_NAME, DLC_SERVICE_NAME),
                    binder);
        }, ONE_SEC_MILLIS);
    }
}
