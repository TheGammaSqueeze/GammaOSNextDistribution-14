/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.LOCAL_MAC_ADDRESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.IBinder;
import android.os.Process;
import android.os.UserManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.BooleanSupplier;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothServiceBinderTest {
    private static final String TAG = BluetoothServiceBinderTest.class.getSimpleName();
    private static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";
    private static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";

    @Rule public MockitoRule mockito = MockitoJUnit.rule().strictness(STRICT_STUBS);

    @Rule public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Mock private BluetoothManagerService mManagerService;
    @Mock private UserManager mUserManager;
    @Mock private AppOpsManager mAppOpsManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;

    private Context mContext =
            spy(
                    new ContextWrapper(
                            InstrumentationRegistry.getInstrumentation().getTargetContext()));

    private final AttributionSource mSource =
            spy(new AttributionSource.Builder(Process.myUid()).build());

    private BluetoothServiceBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        lenient().doReturn(TAG).when(mSource).getPackageName();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(LOG_COMPAT_CHANGE, READ_COMPAT_CHANGE_CONFIG);

        final String appops = mContext.getSystemServiceName(AppOpsManager.class);
        final String devicePolicy = mContext.getSystemServiceName(DevicePolicyManager.class);
        doReturn(mAppOpsManager).when(mContext).getSystemService(eq(appops));
        doReturn(mDevicePolicyManager).when(mContext).getSystemService(eq(devicePolicy));

        mBinder = new BluetoothServiceBinder(mManagerService, null, mContext, mUserManager);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
        // Do not call verifyMock here. If the test fails the initial error will be lost
    }

    @Test
    public void registerAdapter() {
        assertThrows(NullPointerException.class, () -> mBinder.registerAdapter(null));
        mBinder.registerAdapter(mock(IBluetoothManagerCallback.class));
        verify(mManagerService).registerAdapter(any());
        verifyMock();
    }

    @Test
    public void unregisterAdapter() {
        assertThrows(NullPointerException.class, () -> mBinder.unregisterAdapter(null));
        mBinder.unregisterAdapter(mock(IBluetoothManagerCallback.class));
        verify(mManagerService).unregisterAdapter(any());
        verifyMock();
    }

    @Test
    @DisableCompatChanges({ChangeIds.RESTRICT_ENABLE_DISABLE})
    public void enableNoRestrictEnable() {
        assertThrows(NullPointerException.class, () -> mBinder.enable(null));

        checkDisabled(() -> mBinder.enable(mSource));
        checkHardDenied(() -> mBinder.enable(mSource), true);
        doReturn(true).when(mManagerService).enable(any());
        checkGranted(() -> mBinder.enable(mSource), true);
        verify(mUserManager).getProfileParent(any());
        verify(mManagerService).enable(eq(TAG));
        verifyMock();
    }

    @Test
    @EnableCompatChanges({ChangeIds.RESTRICT_ENABLE_DISABLE})
    public void enableWithRestrictEnable() {
        assertThrows(NullPointerException.class, () -> mBinder.enable(null));

        checkDisabled(() -> mBinder.enable(mSource));
        checkHardDenied(() -> mBinder.enable(mSource), true);
        checkGranted(() -> mBinder.enable(mSource), false);
        verify(mUserManager).getProfileParent(any());
        verifyMock();

        // TODO(b/280518177): add more test around compatChange
    }

    @Test
    public void enableNoAutoConnect() {
        assertThrows(NullPointerException.class, () -> mBinder.enableNoAutoConnect(null));

        checkDisabled(() -> mBinder.enableNoAutoConnect(mSource));
        checkHardDenied(() -> mBinder.enableNoAutoConnect(mSource), false);

        // enableNoAutoConnect is only available for Nfc and will fail otherwise
        assertThrows(SecurityException.class, () -> mBinder.enableNoAutoConnect(mSource));

        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        verify(mAppOpsManager).checkPackage(anyInt(), eq(TAG));
        verifyMock();

        // TODO(b/280518177): add test that simulate NFC caller to have a successful case
    }

    @Test
    @DisableCompatChanges({ChangeIds.RESTRICT_ENABLE_DISABLE})
    public void disableNoRestrictEnable() {
        assertThrows(NullPointerException.class, () -> mBinder.disable(null, true));

        assertThrows(SecurityException.class, () -> mBinder.disable(mSource, false));

        checkDisabled(() -> mBinder.disable(mSource, true));
        checkHardDenied(() -> mBinder.disable(mSource, true), true);
        doReturn(true).when(mManagerService).disable(any(), anyBoolean());
        checkGranted(() -> mBinder.disable(mSource, true), true);
        verify(mUserManager).getProfileParent(any());
        verify(mManagerService).disable(eq(TAG), anyBoolean());
        verifyMock();
    }

    @Test
    @EnableCompatChanges({ChangeIds.RESTRICT_ENABLE_DISABLE})
    public void disableWithRestrictEnable() {
        assertThrows(NullPointerException.class, () -> mBinder.disable(null, true));

        assertThrows(SecurityException.class, () -> mBinder.disable(mSource, false));

        checkDisabled(() -> mBinder.disable(mSource, true));
        checkHardDenied(() -> mBinder.disable(mSource, true), true);
        checkGranted(() -> mBinder.disable(mSource, true), false);
        verify(mUserManager).getProfileParent(any());
        verifyMock();

        // TODO(b/280518177): add more test around compatChange
    }

    @Test
    public void getState() {
        // TODO(b/280518177): add more test from not System / ...
        // TODO(b/280518177): add more test when caller is not in foreground

        mBinder.getState();
        verify(mManagerService).getState();
        verify(mUserManager).getProfileParent(any());
        verifyMock();
    }

    @Test
    public void getAddress() {
        assertThrows(NullPointerException.class, () -> mBinder.getAddress(null));

        assertThrows(SecurityException.class, () -> mBinder.getAddress(mSource));
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        // TODO(b/280518177): Throws SecurityException and remove DEFAULT_MAC_ADDRESS
        // assertThrows(SecurityException.class, () -> mBinder.getAddress(mSource));
        assertThat(mBinder.getAddress(mSource)).isEqualTo("02:00:00:00:00:00");
        verifyMockForCheckIfCallerIsForegroundUser();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_CONNECT, LOCAL_MAC_ADDRESS);

        // TODO(b/280518177): add more test from not System / ...
        // TODO(b/280518177): add more test when caller is not in foreground

        doReturn("foo").when(mManagerService).getAddress(any());
        assertThat(mBinder.getAddress(mSource)).isEqualTo("foo");

        verify(mManagerService).getAddress(any());
        verifyMockForCheckIfCallerIsForegroundUser();
    }

    @Test
    public void getName() {
        assertThrows(NullPointerException.class, () -> mBinder.getName(null));

        assertThrows(SecurityException.class, () -> mBinder.getName(mSource));
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        // TODO(b/280518177): add more test from not System / ...
        // TODO(b/280518177): add more test when caller is not in foreground

        doReturn("foo").when(mManagerService).getName(any());
        assertThat(mBinder.getName(mSource)).isEqualTo("foo");
        verify(mManagerService).getName(any());
        verifyMockForCheckIfCallerIsForegroundUser();
    }

    @Test
    public void onFactoryReset() {
        assertThrows(NullPointerException.class, () -> mBinder.onFactoryReset(null));

        assertThrows(SecurityException.class, () -> mBinder.onFactoryReset(mSource));
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);

        assertThrows(SecurityException.class, () -> mBinder.onFactoryReset(mSource));
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT);

        assertThat(mBinder.onFactoryReset(mSource)).isFalse();
        verify(mManagerService).onFactoryReset(any());
        verifyMock();
    }

    @Test
    public void isBleScanAlwaysAvailable() {
        // No permission needed for this call
        mBinder.isBleScanAlwaysAvailable();
        verify(mManagerService).isBleScanAlwaysAvailable();
        verifyMock();
    }

    @Test
    public void enableBle() {
        IBinder token = mock(IBinder.class);
        assertThrows(NullPointerException.class, () -> mBinder.enableBle(null, token));
        assertThrows(NullPointerException.class, () -> mBinder.enableBle(mSource, null));

        checkDisabled(() -> mBinder.enableBle(mSource, token));
        checkHardDenied(() -> mBinder.enableBle(mSource, token), false);
        doReturn(true).when(mManagerService).enableBle(eq(TAG), eq(token));
        checkGranted(() -> mBinder.enableBle(mSource, token), true);
        verify(mManagerService).enableBle(eq(TAG), eq(token));
        verifyMock();
    }

    @Test
    public void disableBle() {
        IBinder token = mock(IBinder.class);
        assertThrows(NullPointerException.class, () -> mBinder.disableBle(null, token));
        assertThrows(NullPointerException.class, () -> mBinder.disableBle(mSource, null));

        checkDisabled(() -> mBinder.disableBle(mSource, token));
        checkHardDenied(() -> mBinder.disableBle(mSource, token), false);
        doReturn(true).when(mManagerService).disableBle(eq(mSource), eq(TAG), eq(token));
        checkGranted(() -> mBinder.disableBle(mSource, token), true);
        verify(mManagerService).disableBle(eq(mSource), eq(TAG), eq(token));
        verifyMock();
    }

    @Test
    public void isBleAppPresent() {
        // No permission needed for this call
        mBinder.isBleAppPresent();
        verify(mManagerService).isBleAppPresent();
        verifyMock();
    }

    @Test
    public void isHearingAidProfileSupported() {
        // No permission needed for this call
        mBinder.isHearingAidProfileSupported();
        verify(mManagerService).isHearingAidProfileSupported();
        verifyMock();
    }

    @Test
    public void setBtHciSnoopLogMode() {
        assertThrows(SecurityException.class, () -> mBinder.setBtHciSnoopLogMode(0));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThat(mBinder.setBtHciSnoopLogMode(0)).isEqualTo(0);
        verify(mManagerService).setBtHciSnoopLogMode(anyInt());
        verifyMock();
    }

    @Test
    public void getBtHciSnoopLogMode() {
        assertThrows(SecurityException.class, () -> mBinder.getBtHciSnoopLogMode());

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThat(mBinder.getBtHciSnoopLogMode()).isEqualTo(0);
        verify(mManagerService).getBtHciSnoopLogMode();
        verifyMock();
    }

    // TODO(b/280518177): Add test for `handleShellCommand` and `dump`

    // *********************************************************************************************
    // Utility method used in tests

    private void verifyAndClearMock(Object o) {
        assertThat(mockingDetails(o).isMock() || mockingDetails(o).isSpy()).isTrue();
        verifyNoMoreInteractions(o);
        clearInvocations(o);
    }

    private void verifyMock() {
        verifyAndClearMock(mManagerService);
        verifyAndClearMock(mUserManager);
        verifyAndClearMock(mAppOpsManager);
        verifyAndClearMock(mDevicePolicyManager);
    }

    private void verifyMockForCheckIfCallerIsForegroundUser() {
        verify(mUserManager).getProfileParent(any());
        verifyMock();
    }

    private void checkDisabled(BooleanSupplier binderCall) {
        doReturn(true)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());

        assertThat(binderCall.getAsBoolean()).isFalse();

        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        verifyMock();
    }

    private void checkHardDenied(ThrowingRunnable binderCall, boolean requireForeground) {
        doReturn(false)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());

        assertThrows(SecurityException.class, binderCall);

        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        if (requireForeground) {
            verify(mUserManager).getProfileParent(any());
        }
        verify(mAppOpsManager).checkPackage(anyInt(), eq(TAG));
        verifyMock();
    }

    private void checkGranted(BooleanSupplier binderCall, boolean expectedResult) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        LOG_COMPAT_CHANGE, READ_COMPAT_CHANGE_CONFIG, BLUETOOTH_CONNECT);

        assertThat(binderCall.getAsBoolean()).isEqualTo(expectedResult);

        verify(mUserManager).hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        verify(mAppOpsManager).checkPackage(anyInt(), eq(TAG));
        if (!expectedResult) {
            verify(mDevicePolicyManager).getDeviceOwnerUser();
            verify(mDevicePolicyManager).getDeviceOwnerComponentOnAnyUser();
        }
    }
}
