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

package com.android.server.uwb;

import static android.Manifest.permission.UWB_PRIVILEGED;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static com.android.server.uwb.UwbServiceImpl.SETTINGS_SATELLITE_MODE_ENABLED;
import static com.android.server.uwb.UwbServiceImpl.SETTINGS_SATELLITE_MODE_RADIOS;
import static com.android.server.uwb.UwbSettingsStore.SETTINGS_FIRST_TOGGLE_DONE;
import static com.android.server.uwb.UwbSettingsStore.SETTINGS_TOGGLE_STATE;
import static com.android.server.uwb.UwbTestUtils.MAX_DATA_SIZE;
import static com.android.server.uwb.UwbTestUtils.TEST_STATUS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.PACS_PROFILE_SERVICE_ID;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.uwb.IOnUwbActivityEnergyInfoListener;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.UwbServiceCore.InitializationFailureListener;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.server.uwb.pm.ProfileManager;
import com.android.uwb.flags.FeatureFlags;
import com.android.uwb.flags.Flags;

import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.multichip.ChipInfoParams;
import com.google.uwb.support.profile.ServiceProfile;
import com.google.uwb.support.profile.UuidBundleWrapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link UwbServiceImpl}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceImplTest {
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final String DEFAULT_CHIP_ID = "defaultChipId";
    private static final ChipInfoParams DEFAULT_CHIP_INFO_PARAMS =
            ChipInfoParams.createBuilder().setChipId(DEFAULT_CHIP_ID).build();
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();

    @Mock private UwbServiceCore mUwbServiceCore;
    @Mock private Context mContext;
    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSettingsStore mUwbSettingsStore;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMultichipData mUwbMultichipData;
    @Mock private ProfileManager mProfileManager;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private UserManager mUserManager;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private UwbCountryCode mUwbCountryCode;
    @Mock private UciLogModeStore mUciLogModeStore;
    @Captor private ArgumentCaptor<IUwbRangingCallbacks> mRangingCbCaptor;
    @Captor private ArgumentCaptor<BroadcastReceiver> mApmModeBroadcastReceiver;
    @Captor private ArgumentCaptor<ContentObserver> mSatelliteModeContentObserver;
    @Captor private ArgumentCaptor<BroadcastReceiver> mUserRestrictionReceiver;
    @Captor private ArgumentCaptor<InitializationFailureListener> mInitializationFailureListener;

    private UwbServiceImpl mUwbServiceImpl;
    private TestLooper mTestLooper;

    @Mock private FeatureFlags mFeatureFlags;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();


    private void createUwbServiceImpl() {
        mUwbServiceImpl = new UwbServiceImpl(mContext, mUwbInjector);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        when(mUwbInjector.getUwbSettingsStore()).thenReturn(mUwbSettingsStore);
        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(true);
        when(mUwbSettingsStore.get(SETTINGS_FIRST_TOGGLE_DONE)).thenReturn(false);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.isUwbDisabledUntilFirstToggle()).thenReturn(false);
        when(mUwbMultichipData.getChipInfos()).thenReturn(List.of(DEFAULT_CHIP_INFO_PARAMS));
        when(mUwbMultichipData.getDefaultChipId()).thenReturn(DEFAULT_CHIP_ID);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        when(mUwbInjector.getMultichipData()).thenReturn(mUwbMultichipData);
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        when(mUwbInjector.getGlobalSettingsString(Settings.Global.AIRPLANE_MODE_RADIOS))
                .thenReturn("cell,bluetooth,uwb,wifi,wimax");
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_SATELLITE_MODE_ENABLED, 0)).thenReturn(0);
        when(mUwbInjector.getGlobalSettingsString(SETTINGS_SATELLITE_MODE_RADIOS))
                .thenReturn("cell,bluetooth,nfc,uwb,wifi");
        when(mUwbInjector.getNativeUwbManager()).thenReturn(mNativeUwbManager);
        when(mUwbInjector.getUserManager()).thenReturn(mUserManager);
        when(mUwbInjector.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mUwbInjector.getUwbCountryCode()).thenReturn(mUwbCountryCode);
        when(mUwbInjector.getUciLogModeStore()).thenReturn(mUciLogModeStore);
        when(mUserManager.getUserRestrictions().getBoolean(anyString())).thenReturn(false);
        when(mUwbServiceCore.getHandler()).thenReturn(new Handler(mTestLooper.getLooper()));

        createUwbServiceImpl();
        verify(mUwbServiceCore).addInitializationFailureListener(
                mInitializationFailureListener.capture());
        verify(mContext).registerReceiver(
                mApmModeBroadcastReceiver.capture(),
                argThat(i -> i.getAction(0).equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)),
                any(), any());
        if (SdkLevel.isAtLeastU()) {
            verify(mUwbInjector).registerContentObserver(
                    eq(Settings.Global.getUriFor(SETTINGS_SATELLITE_MODE_ENABLED)), anyBoolean(),
                    mSatelliteModeContentObserver.capture());
        }
        verify(mContext).registerReceiver(
                mUserRestrictionReceiver.capture(),
                argThat(i -> i.getAction(0).equals(UserManager.ACTION_USER_RESTRICTIONS_CHANGED)),
                any(), any());
    }

    @Test
    public void testRegisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.registerAdapterStateCallbacks(cb);

        verify(mUwbServiceCore).registerAdapterStateCallbacks(cb);
    }

    @Test
    public void testUnregisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.unregisterAdapterStateCallbacks(cb);

        verify(mUwbServiceCore).unregisterAdapterStateCallbacks(cb);
    }

    @Test
    public void testGetTimestampResolutionNanos() throws Exception {
        final long timestamp = 34L;
        when(mUwbServiceCore.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(/* chipId= */ null))
                .isEqualTo(timestamp);

        verify(mUwbServiceCore).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_validChipId() throws Exception {
        final long timestamp = 34L;
        when(mUwbServiceCore.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(DEFAULT_CHIP_ID))
                .isEqualTo(timestamp);

        verify(mUwbServiceCore).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getTimestampResolutionNanos("invalidChipId"));
    }

    @Test
    public void testGetSpecificationInfo_nullChipId() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mUwbServiceCore.getSpecificationInfo(anyString())).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(/* chipId= */ null))
                .isEqualTo(specification);

        verify(mUwbServiceCore).getSpecificationInfo(DEFAULT_CHIP_ID);
    }

    @Test
    public void testGetSpecificationInfo_validChipId() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mUwbServiceCore.getSpecificationInfo(anyString())).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(DEFAULT_CHIP_ID))
                .isEqualTo(specification);

        verify(mUwbServiceCore).getSpecificationInfo(DEFAULT_CHIP_ID);
    }

    @Test
    public void testGetSpecificationInfo_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getSpecificationInfo("invalidChipId"));
    }

    @Test
    public void testOpenRanging_nullChipId() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mUwbServiceCore).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters), eq(DEFAULT_CHIP_ID));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();
    }

    @Test
    public void testStartRanging() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.startRanging(sessionHandle, parameters);

        verify(mUwbServiceCore).startRanging(sessionHandle, parameters);
    }

    @Test
    public void testReconfigureRanging() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final FiraRangingReconfigureParams parameters =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(6)
                        .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG)
                        .setRangeDataProximityFar(6)
                        .setRangeDataProximityNear(4)
                        .build();
        mUwbServiceImpl.reconfigureRanging(sessionHandle, parameters.toBundle());
        verify(mUwbServiceCore).reconfigureRanging(eq(sessionHandle),
                argThat((x) -> x.getInt("update_block_stride_length") == 6));
    }

    @Test
    public void testStopRanging() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);

        mUwbServiceImpl.stopRanging(sessionHandle);

        verify(mUwbServiceCore).stopRanging(sessionHandle);
    }

    @Test
    public void testCloseRanging() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);

        mUwbServiceImpl.closeRanging(sessionHandle);

        verify(mUwbServiceCore).closeRanging(sessionHandle);
    }

    @Test
    public void testThrowSecurityExceptionWhenCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        try {
            mUwbServiceImpl.registerAdapterStateCallbacks(cb);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenSetUwbEnabledCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.setEnabled(true);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenOpenRangingCalledWithoutUwbRangingPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mUwbInjector).enforceUwbRangingPermissionForPreflight(
                any());

        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);
        try {
            mUwbServiceImpl.openRanging(
                    ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testInitialize() throws Exception {
        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(true);
        mUwbServiceImpl.initialize();
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.initialize();
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testInitializeWithUwbDisabledUntilFirstToggleFlagOn() throws Exception {
        when(mDeviceConfigFacade.isUwbDisabledUntilFirstToggle()).thenReturn(true);
        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(true);

        when(mUwbSettingsStore.get(SETTINGS_FIRST_TOGGLE_DONE)).thenReturn(false);
        mUwbServiceImpl.initialize();
        verify(mUwbServiceCore).setEnabled(false);

        when(mUwbSettingsStore.get(SETTINGS_FIRST_TOGGLE_DONE)).thenReturn(true);
        mUwbServiceImpl.initialize();
        verify(mUwbServiceCore).setEnabled(true);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefs() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefsWithUwbDisabledUntilFirstToggleFlagOn()
            throws Exception {
        when(mDeviceConfigFacade.isUwbDisabledUntilFirstToggle()).thenReturn(true);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbSettingsStore).put(SETTINGS_FIRST_TOGGLE_DONE, true);
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbSettingsStore, times(2)).put(SETTINGS_FIRST_TOGGLE_DONE, true);
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefsWhenApmModeOn() throws Exception {
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(false);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore, times(2)).setEnabled(false);
    }

    @Test
    public void testToggleStateReadFromSharedPrefsOnInitialization() throws Exception {
        when(mUwbServiceCore.getAdapterState()).thenReturn(STATE_ENABLED_ACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);
        verify(mUwbServiceCore).getAdapterState();

        when(mUwbServiceCore.getAdapterState()).thenReturn(STATE_ENABLED_INACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
        verify(mUwbServiceCore, times(2)).getAdapterState();
    }

    @Test
    public void testApmModeToggle() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        // Toggle on
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mUwbServiceCore).setEnabled(false);

        // Toggle off
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mUwbServiceCore, times(2)).setEnabled(true);
    }

    @Test
    public void testApmModeSetEnabledWhenUwbRadioNotSetInAndroidUAndHigher() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        when(mUwbInjector.getGlobalSettingsString(Settings.Global.AIRPLANE_MODE_RADIOS))
                .thenReturn("cell,bluetooth,wifi,wimax");

        // Recreate UwbServiceImpl to ensure we don't register APM broadcast receiver.
        clearInvocations(mContext);
        createUwbServiceImpl();
        // apm radio setting should be honored on android U+ devices.

        // Verify that we did not re-register the APM broadcast listener.
        verify(mContext, never()).registerReceiver(
                any(), argThat(i -> i.getAction(0).equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)),
                any(), any());

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle APM on
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle APM off
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
    }

    @Test
    public void testApmModeSetEnabledWhenUwbRadioNotSetInAndroidT() throws Exception {
        // Test should only run on T devices.
        assumeTrue(SdkLevel.isAtLeastT() && !SdkLevel.isAtLeastU());
        when(mUwbInjector.getGlobalSettingsString(Settings.Global.AIRPLANE_MODE_RADIOS))
                .thenReturn("cell,bluetooth,wifi,wimax");

        // Recreate UwbServiceImpl to ensure we do register APM broadcast receiver.
        clearInvocations(mContext);
        createUwbServiceImpl();
        // apm radio setting should be ignored on android T devices.

        // Verify that we did re-register the APM broadcast listener.
        verify(mContext).registerReceiver(
                mApmModeBroadcastReceiver.capture(),
                argThat(i -> i.getAction(0).equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)),
                any(), any());
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle APM on (ignored by uwb stack)
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(false);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle APM off (ignored by uwb stack)
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
    }

    @Test
    public void testSatelliteModeToggle() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        // Toggle satellite on
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_SATELLITE_MODE_ENABLED, 0)).thenReturn(1);
        mSatelliteModeContentObserver.getValue().onChange(false);
        verify(mUwbServiceCore).setEnabled(false);

        // Toggle satellite off
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_SATELLITE_MODE_ENABLED, 0)).thenReturn(0);
        mSatelliteModeContentObserver.getValue().onChange(false);
        verify(mUwbServiceCore, times(2)).setEnabled(true);
    }

    @Test
    public void testSatelliteModeSetEnabledWhenUwbRadioNotSet() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        when(mUwbInjector.getGlobalSettingsString(SETTINGS_SATELLITE_MODE_RADIOS))
                .thenReturn("cell,bluetooth,nfc,wifi");

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle satellite on (ignored by uwb stack)
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_SATELLITE_MODE_ENABLED, 0)).thenReturn(1);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
        clearInvocations(mUwbServiceCore, mUwbSettingsStore);

        // Toggle satellite off (ignored by uwb stack)
        when(mUwbInjector.getGlobalSettingsInt(SETTINGS_SATELLITE_MODE_ENABLED, 0)).thenReturn(0);
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);
    }

    @Test
    public void testUserRestrictionChanged() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.

        mUwbServiceImpl.setEnabled(true);

        // User restriction changes to disallow UWB
        when(mUserManager.getUserRestrictions().getBoolean(anyString())).thenReturn(true);
        mUserRestrictionReceiver.getValue().onReceive(
                mContext, new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));
        verify(mUwbServiceCore).setEnabled(false);

        // User restriction changes to allow UWB
        when(mUserManager.getUserRestrictions().getBoolean(anyString())).thenReturn(true);
        mUserRestrictionReceiver.getValue().onReceive(
                mContext, new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));
        verify(mUwbServiceCore, times(1)).setEnabled(true);
    }

    @Test
    public void testToggleFromRootedShellWhenApmModeOn() throws Exception {
        BinderUtil.setUid(Process.ROOT_UID);
        when(mUwbInjector.getGlobalSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mUwbServiceCore).setEnabled(false);
    }

    @Test
    public void testHandleInitializationFailure() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mUwbServiceCore).setEnabled(true);

        // Trigger failure callback.
        mInitializationFailureListener.getValue().onFailure();
        // Move time forward.
        mTestLooper.moveTimeForward(UwbServiceImpl.INITIALIZATION_RETRY_TIMEOUT_MS);
        // Verify UWB is re-enabled.
        verify(mUwbServiceCore).setEnabled(true);
        verify(mUwbServiceCore).removeInitializationFailureListener(
                mInitializationFailureListener.getValue());
    }

    @Test
    public void testGetDefaultChipId() {
        assertEquals(DEFAULT_CHIP_ID, mUwbServiceImpl.getDefaultChipId());
    }

    @Test
    public void testThrowSecurityExceptionWhenGetDefaultChipIdWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getDefaultChipId();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipIds() {
        List<String> chipIds = mUwbServiceImpl.getChipIds();
        assertThat(chipIds).containsExactly(DEFAULT_CHIP_ID);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipIdsWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipIds();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipInfos() {
        List<PersistableBundle> chipInfos = mUwbServiceImpl.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfoParams = ChipInfoParams.fromBundle(chipInfos.get(0));
        assertThat(chipInfoParams.getChipId()).isEqualTo(DEFAULT_CHIP_ID);
        assertThat(chipInfoParams.getPositionX()).isEqualTo(0.);
        assertThat(chipInfoParams.getPositionY()).isEqualTo(0.);
        assertThat(chipInfoParams.getPositionZ()).isEqualTo(0.);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipInfosWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipInfos();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testAddControlee() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.addControlee(sessionHandle, parameters);
        verify(mUwbServiceCore).addControlee(sessionHandle, parameters);
    }

    @Test
    public void testRemoveControlee() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.removeControlee(sessionHandle, parameters);
        verify(mUwbServiceCore).removeControlee(sessionHandle, parameters);
    }

    @Test
    public void testAddServiceProfile() {
        UUID serviceInstanceID = new UUID(100, 50);

        when(mUwbInjector.getProfileManager()).thenReturn(mProfileManager);
        when(mProfileManager.addServiceProfile(anyInt()))
                .thenReturn(Optional.of(serviceInstanceID));

        PersistableBundle bundle = new ServiceProfile.Builder()
                .setServiceID(PACS_PROFILE_SERVICE_ID)
                .build()
                .toBundle();

        assertEquals(ServiceProfile.fromBundle(bundle).getServiceID(),
                PACS_PROFILE_SERVICE_ID);

        PersistableBundle statusBundle = mUwbServiceImpl.addServiceProfile(bundle);

        verify(mProfileManager).addServiceProfile(1);

        assertEquals(UuidBundleWrapper.fromBundle(statusBundle).getServiceInstanceID().get(),
                serviceInstanceID);
    }

    @Test
    public void testGetAdfCertificateAndInfo() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.getAdfCertificateAndInfo(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testGetAdfProvisioningAuthorities() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.getAdfProvisioningAuthorities(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testGetAllServiceProfiles() throws Exception {
        try {
            mUwbServiceImpl.getAllServiceProfiles();
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testProvisionProfileAdfByScript() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();
        final IUwbAdfProvisionStateCallbacks cb = mock(IUwbAdfProvisionStateCallbacks.class);

        try {
            mUwbServiceImpl.provisionProfileAdfByScript(parameters, cb);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testRegisterVendorExtensionCallback() throws Exception {
        final IUwbVendorUciCallback cb = mock(IUwbVendorUciCallback.class);
        mUwbServiceImpl.registerVendorExtensionCallback(cb);
        verify(mUwbServiceCore).registerVendorExtensionCallback(cb);
    }

    @Test
    public void testUnregisterVendorExtensionCallback() throws Exception {
        final IUwbVendorUciCallback cb = mock(IUwbVendorUciCallback.class);
        mUwbServiceImpl.unregisterVendorExtensionCallback(cb);
        verify(mUwbServiceCore).unregisterVendorExtensionCallback(cb);
    }

    @Test
    public void testRemoveProfileAdf() throws Exception {
        final PersistableBundle parameters = new PersistableBundle();

        try {
            mUwbServiceImpl.removeProfileAdf(parameters);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testRemoveServiceProfile() throws Exception {
        UUID serviceInstanceID = new UUID(100, 50);

        when(mUwbInjector.getProfileManager()).thenReturn(mProfileManager);
        when(mProfileManager.removeServiceProfile(any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        UuidBundleWrapper uuidBundleWrapper = new UuidBundleWrapper.Builder()
                .setServiceInstanceID(Optional.of(serviceInstanceID))
                .build();
        int status = mUwbServiceImpl.removeServiceProfile(uuidBundleWrapper.toBundle());

        verify(mProfileManager).removeServiceProfile(any());
        assertEquals(status, UwbUciConstants.STATUS_CODE_OK);
    }

    @Test
    public void testResume() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle bundle = new PersistableBundle();
        mUwbServiceImpl.resume(sessionHandle, bundle);
        verify(mUwbServiceCore).resume(sessionHandle, bundle);
    }

    @Test
    public void testPause() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle bundle = new PersistableBundle();
        mUwbServiceImpl.pause(sessionHandle, bundle);
        verify(mUwbServiceCore).pause(sessionHandle, bundle);
    }

    @Test
    public void testSendData() throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final UwbAddress mUwbAddress = mock(UwbAddress.class);
        final PersistableBundle parameters = new PersistableBundle();
        final byte[] data = new byte[] {1, 3, 5, 7, 11, 13};

        mUwbServiceImpl.sendData(sessionHandle, mUwbAddress, parameters, data);
        verify(mUwbServiceCore).sendData(sessionHandle, mUwbAddress, parameters, data);
    }

    @Test
    public void testThrowSecurityExceptionWhenSendDataWithoutUwbPrivilegedPermission()
            throws Exception {
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final UwbAddress mUwbAddress = mock(UwbAddress.class);
        final PersistableBundle parameters = new PersistableBundle();
        final byte[] data = new byte[] {1, 3, 5, 7, 11, 13};

        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.sendData(sessionHandle, mUwbAddress, parameters, data);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_TRANSFER_PHASE_CONFIG)
    public void testSetDataTransferPhaseConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV()); // Test should only run on V+ devices.
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle bundle = new PersistableBundle();
        mUwbServiceImpl.setDataTransferPhaseConfig(sessionHandle, bundle);
        verify(mUwbServiceCore).setDataTransferPhaseConfig(sessionHandle, bundle);
    }

    @Test
    public void testSendVendorUciMessage() throws Exception {
        final int mt = 1;
        final int gid = 0;
        final int oid = 0;
        mUwbServiceImpl.sendVendorUciMessage(mt, gid, oid, null);
        verify(mUwbServiceCore).sendVendorUciMessage(mt, gid, oid, null,
                mUwbInjector.getMultichipData().getDefaultChipId());
    }

    @Test
    public void testRangingRoundsUpdateDtTag() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();
        mUwbServiceImpl.updateRangingRoundsDtTag(sessionHandle, parameters);

        verify(mUwbServiceCore).rangingRoundsUpdateDtTag(sessionHandle, parameters);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_QUERY_TIMESTAMP_MICROS)
    public void testQueryDataSize() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV()); // Test should only run on V+ devices.
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();

        when(mFeatureFlags.queryTimestampMicros()).thenReturn(true);
        when(mUwbServiceCore.queryMaxDataSizeBytes(sessionHandle)).thenReturn(MAX_DATA_SIZE);
        assertThat(mUwbServiceImpl.queryMaxDataSizeBytes(sessionHandle)).isEqualTo(MAX_DATA_SIZE);

        verify(mUwbServiceCore).queryMaxDataSizeBytes(sessionHandle);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HYBRID_SESSION_SUPPORT)
    public void testSetHybridSessionConfiguration() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV()); // Test should only run on V+ devices.
        final SessionHandle sessionHandle = mock(SessionHandle.class);
        final PersistableBundle parameters = new PersistableBundle();

        when(mFeatureFlags.hybridSessionSupport()).thenReturn(true);
        when(mUwbServiceCore.setHybridSessionConfiguration(sessionHandle, parameters))
               .thenReturn(TEST_STATUS);
        assertThat(mUwbServiceImpl.setHybridSessionConfiguration(sessionHandle, parameters))
                .isEqualTo(TEST_STATUS);

        verify(mUwbServiceCore).setHybridSessionConfiguration(sessionHandle, parameters);
    }

    @Test
    public void testGetUwbActivityEnergyInfoAsync() throws Exception {
        final IOnUwbActivityEnergyInfoListener listener = mock(
                IOnUwbActivityEnergyInfoListener.class);
        mUwbServiceImpl.getUwbActivityEnergyInfoAsync(listener);
        verify(mUwbServiceCore).reportUwbActivityEnergyInfo(listener);
    }

    @Test
    public void testGetUwbActivityEnergyInfoAsyncSecurityException() throws Exception {
        final IOnUwbActivityEnergyInfoListener listener = mock(
                IOnUwbActivityEnergyInfoListener.class);
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getUwbActivityEnergyInfoAsync(listener);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }
}
