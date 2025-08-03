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

package android.virtualdevice.cts.core;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.content.Context.DEVICE_ID_INVALID;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;
import static android.media.AudioManager.FX_BACK;
import static android.media.AudioManager.FX_KEY_CLICK;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.virtual.VirtualDevice;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.os.Process;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.Condition;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceManagerBasicTest {

    private static final String VIRTUAL_DEVICE_NAME = "VirtualDeviceName";
    private static final String SENSOR_NAME = "VirtualSensorName";

    private static final VirtualDeviceParams NAMED_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder()
                    .setName(VIRTUAL_DEVICE_NAME)
                    .build();

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();
    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    @Mock
    private VirtualSensorCallback mVirtualSensorCallback;
    @Mock
    private VirtualDeviceManager.VirtualDeviceListener mVirtualDeviceListener;
    @Mock
    private VirtualDeviceManager.SoundEffectListener mSoundEffectListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mVirtualDeviceManager = mContext.getSystemService(VirtualDeviceManager.class);

        if (Flags.vdmPublicApis()) {
            mVirtualDeviceManager.registerVirtualDeviceListener(
                    Runnable::run, mVirtualDeviceListener);
        }

        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @After
    public void tearDown() {
        if (Flags.vdmPublicApis()) {
            mVirtualDeviceManager.unregisterVirtualDeviceListener(mVirtualDeviceListener);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_DEVICE_ID_API)
    @Test
    public void getDisplayNameForPersistentId_nullPersistentId_throws() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDeviceManager.getDisplayNameForPersistentDeviceId(null));
    }

    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_DEVICE_ID_API)
    @Test
    public void getDisplayNameForPersistentId_nonExistentPersistentId_returnsNull() {
        assertThat(mVirtualDeviceManager.getDisplayNameForPersistentDeviceId(
                "nonExistentPersistentId"))
                .isNull();
    }

    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_DEVICE_ID_API)
    @Test
    public void getDisplayNameForPersistentId_defaultDevicePersistentId_returnsNull() {
        assertThat(mVirtualDeviceManager.getDisplayNameForPersistentDeviceId(
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT))
                .isNull();
    }

    @RequiresFlagsEnabled({Flags.FLAG_PERSISTENT_DEVICE_ID_API, Flags.FLAG_VDM_PUBLIC_APIS})
    @Test
    public void getDisplayNameForPersistentId_validVirtualDevice_returnsCorrectId() {
        final CharSequence virtualDeviceDisplayName =
                mVirtualDeviceManager
                        .getVirtualDevice(mVirtualDevice.getDeviceId())
                        .getDisplayName();
        final CharSequence persistentDeviceDisplayName =
                mVirtualDeviceManager.getDisplayNameForPersistentDeviceId(
                        mVirtualDevice.getPersistentDeviceId());

        assertThat(virtualDeviceDisplayName.toString())
                .isEqualTo(persistentDeviceDisplayName.toString());
    }

    @RequiresFlagsEnabled({Flags.FLAG_PERSISTENT_DEVICE_ID_API, Flags.FLAG_VDM_PUBLIC_APIS})
    @Test
    public void getAllPersistentDeviceIds_empty() {
        mRule.dropCompanionDeviceAssociation();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
        assertThat(mVirtualDeviceManager.getAllPersistentDeviceIds()).isEmpty();
    }

    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_DEVICE_ID_API)
    @Test
    public void getAllPersistentDeviceIds_validVirtualDevice() {
        assertThat(mVirtualDeviceManager.getAllPersistentDeviceIds()).containsExactly(
                mVirtualDevice.getPersistentDeviceId());
    }

    @RequiresFlagsEnabled({Flags.FLAG_PERSISTENT_DEVICE_ID_API, Flags.FLAG_VDM_PUBLIC_APIS})
    @Test
    public void getAllPersistentDeviceIds_includesClosedVirtualDevice() {
        final AssociationInfo associationInfo = mRule.createManagedAssociation();
        VirtualDeviceManager.VirtualDevice secondVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        associationInfo.getId(), VirtualDeviceRule.DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDevice.close();
        assertDeviceClosed(mVirtualDevice.getDeviceId());

        assertThat(mVirtualDeviceManager.getAllPersistentDeviceIds()).containsExactly(
                mVirtualDevice.getPersistentDeviceId(),
                secondVirtualDevice.getPersistentDeviceId());
    }

    @Test
    public void createVirtualDevice_shouldNotThrowException() {
        assertThat(mVirtualDevice).isNotNull();
        assertThat(mVirtualDevice.getDeviceId()).isGreaterThan(DEVICE_ID_DEFAULT);
    }

    @Test
    public void createVirtualDevice_deviceIdIsUniqueAndIncremented() {
        VirtualDeviceManager.VirtualDevice secondVirtualDevice = mRule.createManagedVirtualDevice();
        assertThat(secondVirtualDevice).isNotNull();
        assertThat(mVirtualDevice.getDeviceId() + 1).isEqualTo(secondVirtualDevice.getDeviceId());
    }

    @Test
    public void createVirtualDevice_noPermission_shouldThrowSecurityException() {
        mRule.runWithTemporaryPermission(
                () -> assertThrows(SecurityException.class,
                        () -> mRule.createManagedVirtualDevice()));
    }

    @Test
    public void createVirtualDevice_invalidAssociationId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mVirtualDeviceManager.createVirtualDevice(
                        /* associationId= */ -1, mRule.DEFAULT_VIRTUAL_DEVICE_PARAMS));
    }

    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_DEVICE_ID_API)
    @Test
    public void createVirtualDevice_invalidDeviceProfile_shouldThrowIllegalArgumentException() {
        final String fakeAddress = "00:00:00:00:10:10";
        SystemUtil.runShellCommand(String.format("cmd companiondevice associate %d %s %s",
                Process.myUserHandle().getIdentifier(), mContext.getPackageName(), fakeAddress));
        CompanionDeviceManager cdm = mContext.getSystemService(CompanionDeviceManager.class);
        List<AssociationInfo> associations = cdm.getMyAssociations();
        final AssociationInfo associationInfo = associations.stream()
                .filter(a -> fakeAddress.equals(a.getDeviceMacAddress().toString()))
                .findAny().orElse(null);
        assertThat(associationInfo).isNotNull();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> mVirtualDeviceManager.createVirtualDevice(
                            associationInfo.getId(), mRule.DEFAULT_VIRTUAL_DEVICE_PARAMS));
        } finally {
            cdm.disassociate(associationInfo.getId());
        }
    }

    @Test
    public void createVirtualDevice_closeMultipleTimes_isSafe() {
        mVirtualDevice.close();
        mVirtualDevice.close();
        mVirtualDevice.close();

        if (Flags.vdmPublicApis()) {
            assertDeviceClosed(mVirtualDevice.getDeviceId());
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void createVirtualDevice_removeAssociation_shouldCloseVirtualDevice() {
        if (!Flags.persistentDeviceIdApi()) {
            assumeFalse(UserManager.isHeadlessSystemUserMode());
        }

        VirtualDisplay display = mRule.createManagedVirtualDisplay(mVirtualDevice);

        assertThat(display).isNotNull();
        assertThat(display.getDisplay().isValid()).isTrue();

        mRule.dropCompanionDeviceAssociation();
        mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        assertThat(display.getDisplay().isValid()).isFalse();

        // Ensure the virtual device can no longer setup new functionality
        assertThrows(SecurityException.class,
                () -> mRule.createManagedVirtualDisplay(mVirtualDevice));
    }

    @RequiresFlagsDisabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void createVirtualDevice_closeAndRemoveAssociation_isSafe() {
        VirtualDisplay display = mRule.createManagedVirtualDisplay(mVirtualDevice);

        mVirtualDevice.close();
        mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        mRule.dropCompanionDeviceAssociation();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void createVirtualDevice_closeAndRemoveAssociation_isSafe_withListener() {
        mVirtualDevice.close();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
        mRule.dropCompanionDeviceAssociation();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
    }

    @RequiresFlagsDisabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void createVirtualDevice_removeAssociationAndClose_isSafe() {
        if (!Flags.persistentDeviceIdApi()) {
            assumeFalse(UserManager.isHeadlessSystemUserMode());
        }

        VirtualDisplay display = mRule.createManagedVirtualDisplay(mVirtualDevice);

        mRule.dropCompanionDeviceAssociation();
        mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        mVirtualDevice.close();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void createVirtualDevice_removeAssociationAndClose_isSafe_withListener() {
        if (!Flags.persistentDeviceIdApi()) {
            assumeFalse(UserManager.isHeadlessSystemUserMode());
        }

        mRule.dropCompanionDeviceAssociation();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
        mVirtualDevice.close();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void getVirtualDevice_unknownDeviceId_returnsNull() {
        assertThat(mVirtualDeviceManager.getVirtualDevice(DEVICE_ID_INVALID)).isNull();
        assertThat(mVirtualDeviceManager.getVirtualDevice(DEVICE_ID_DEFAULT)).isNull();
        assertThat(mVirtualDeviceManager.getVirtualDevice(mVirtualDevice.getDeviceId() + 1))
                .isNull();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void virtualDeviceListener_calledOnDeviceEvents() {
        assertDeviceCreated(mVirtualDevice.getDeviceId());

        VirtualDeviceManager.VirtualDevice secondVirtualDevice = mRule.createManagedVirtualDevice();
        assertDeviceCreated(secondVirtualDevice.getDeviceId());

        secondVirtualDevice.close();
        assertDeviceClosed(secondVirtualDevice.getDeviceId());

        mVirtualDevice.close();
        assertDeviceClosed(mVirtualDevice.getDeviceId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void getVirtualDevice_getDisplayIds() {
        VirtualDisplay firstDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        final int firstDisplayId = firstDisplay.getDisplay().getDisplayId();

        VirtualDevice device = mVirtualDeviceManager.getVirtualDevice(mVirtualDevice.getDeviceId());
        assertThat(device).isNotNull();
        assertThat(device.getDisplayIds()).asList().containsExactly(firstDisplayId);

        VirtualDisplay secondDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        final int secondDisplayId = secondDisplay.getDisplay().getDisplayId();

        assertThat(device.getDisplayIds()).asList().containsExactly(
                firstDisplayId, secondDisplayId);
        assertThat(device.hasCustomSensorSupport()).isFalse();
        assertThat(device.hasCustomAudioInputSupport()).isFalse();
        assertThat(device.hasCustomCameraSupport()).isFalse();

        firstDisplay.release();
        mRule.assertDisplayDoesNotExist(firstDisplayId);
        // The onVirtualDisplayRemoved callback is invoked asynchronously, so we add polling to
        // remove the test flakiness.
        // TODO(b/317872777): Remove the polling once the callback is synchronous.
        waitForCondition("display removal", () -> device.getDisplayIds().length == 1);
        assertThat(device.getDisplayIds()).asList().containsExactly(secondDisplayId);

        secondDisplay.release();
        mRule.assertDisplayDoesNotExist(secondDisplayId);
        waitForCondition("display removal", () -> device.getDisplayIds().length == 0);
        assertThat(device.getDisplayIds()).isEmpty();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void getVirtualDevice_hasCustomSensorSupport() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .build());
        VirtualDevice device = mVirtualDeviceManager.getVirtualDevice(virtualDevice.getDeviceId());
        assertThat(device).isNotNull();
        assertThat(device.hasCustomSensorSupport()).isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void getVirtualDevice_hasCustomAudioInputSupport() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .build());
        VirtualDevice device = mVirtualDeviceManager.getVirtualDevice(virtualDevice.getDeviceId());
        assertThat(device).isNotNull();
        assertThat(device.hasCustomAudioInputSupport()).isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_PUBLIC_APIS)
    @Test
    public void getVirtualDevice_hasCustomCameraSupport() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        VirtualDevice device = mVirtualDeviceManager.getVirtualDevice(virtualDevice.getDeviceId());
        assertThat(device).isNotNull();
        assertThat(device.hasCustomCameraSupport()).isTrue();
    }

    /**
     * It is expected that there are zero VirtualDevices active on a new device. If this test fails
     * some application may have created a VirtualDevice before this test was run. Clear all
     * VirtualDevices (or disassociate the related CDM associations) before re-running this test.
     */
    @Test
    public void getVirtualDevices_noVirtualDevices_returnsEmptyList() {
        mVirtualDevice.close();
        assertWithMessage(
                "Expected no previous VirtualDevices. Please remove all VirtualDevices before "
                        + "running this test.").that(
                mVirtualDeviceManager.getVirtualDevices()).isEmpty();
    }

    @Test
    public void getVirtualDevices_returnsAllVirtualDevices() {
        assertThat(mVirtualDevice.getPersistentDeviceId()).isNotNull();
        VirtualDeviceManager.VirtualDevice namedVirtualDevice =
                mRule.createManagedVirtualDevice(NAMED_VIRTUAL_DEVICE_PARAMS);
        assertThat(namedVirtualDevice).isNotNull();
        assertThat(namedVirtualDevice.getPersistentDeviceId())
                .isEqualTo(mVirtualDevice.getPersistentDeviceId());

        List<VirtualDevice> virtualDevices = mVirtualDeviceManager.getVirtualDevices();
        assertThat(virtualDevices.size()).isEqualTo(2);

        VirtualDevice device = virtualDevices.get(0);
        assertThat(device.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        assertThat(device.getPersistentDeviceId())
                .isEqualTo(mVirtualDevice.getPersistentDeviceId());
        assertThat(device.getName()).isNull();

        VirtualDevice anotherDevice = virtualDevices.get(1);
        assertThat(anotherDevice.getDeviceId()).isEqualTo(namedVirtualDevice.getDeviceId());
        assertThat(anotherDevice.getPersistentDeviceId())
                .isEqualTo(namedVirtualDevice.getPersistentDeviceId());
        assertThat(anotherDevice.getName()).isEqualTo(VIRTUAL_DEVICE_NAME);

        // The persistent IDs must be the same as the underlying association is the same.
        assertThat(device.getPersistentDeviceId()).isEqualTo(anotherDevice.getPersistentDeviceId());
    }

    @Test
    public void createDeviceContext_invalidDeviceId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mContext.createDeviceContext(DEVICE_ID_INVALID));
    }

    @Test
    public void createDeviceContext_missingDeviceId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mContext.createDeviceContext(mVirtualDevice.getDeviceId() + 1));
    }

    @Test
    public void createDeviceContext_defaultDeviceId() {
        Context defaultDeviceContext = mContext.createDeviceContext(DEVICE_ID_DEFAULT);
        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
        assertThat(defaultDeviceContext.getAttributionSource().getDeviceId()).isEqualTo(
                DEVICE_ID_DEFAULT);
    }

    @Test
    public void createDeviceContext_validVirtualDeviceId() {
        Context virtualDeviceContext =
                mContext.createDeviceContext(mVirtualDevice.getDeviceId());
        assertThat(virtualDeviceContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        assertThat(virtualDeviceContext.getAttributionSource().getDeviceId()).isEqualTo(
                mVirtualDevice.getDeviceId());

        // The default device context should be available from the virtual device one.
        Context defaultDeviceContext = virtualDeviceContext.createDeviceContext(DEVICE_ID_DEFAULT);
        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void createContext_returnsCorrectContext() {
        Context deviceContext = mVirtualDevice.createContext();
        assertThat(deviceContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void createContext_multipleDevices_returnCorrectContexts() {
        VirtualDeviceManager.VirtualDevice secondVirtualDevice = mRule.createManagedVirtualDevice();

        assertThat(mVirtualDevice.createContext().getDeviceId())
                .isEqualTo(mVirtualDevice.getDeviceId());
        assertThat(secondVirtualDevice.createContext().getDeviceId())
                .isEqualTo(secondVirtualDevice.getDeviceId());
    }

    @Test
    public void getDevicePolicy_noPolicySpecified_shouldReturnDefault() {
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_shouldReturnConfiguredValue() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .build());

        assertThat(
                mVirtualDeviceManager.getDevicePolicy(virtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    public void policyTypeRecents_changeAtRuntime_shouldReturnConfiguredValue() {
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    public void policyTypeActivity_changeAtRuntime_shouldReturnConfiguredValue() {
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_DYNAMIC_POLICY, Flags.FLAG_CROSS_DEVICE_CLIPBOARD})
    public void policyTypeClipboard_changeAtRuntime_shouldReturnConfiguredValue() {
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_CLIPBOARD))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_DEFAULT);
        assertThat(
                mVirtualDeviceManager.getDevicePolicy(mVirtualDevice.getDeviceId(),
                        POLICY_TYPE_CLIPBOARD))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_virtualDeviceClosed_shouldReturnDefault() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .build());
        virtualDevice.close();

        assertThat(
                mVirtualDeviceManager.getDevicePolicy(virtualDevice.getDeviceId(),
                        POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    public void setDevicePolicy_notDynamicPolicy_shouldThrow() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> virtualDevice.setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> virtualDevice.setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM));
    }

    @Test
    public void getVirtualSensorList_noSensorsConfigured_isEmpty() {
        assertThat(mVirtualDevice.getVirtualSensorList()).isEmpty();
    }

    @Test
    public void getVirtualSensorList_withConfiguredSensor() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                        .build())
                        .setVirtualSensorCallback(Runnable::run, mVirtualSensorCallback).build());

        List<VirtualSensor> sensorList = virtualDevice.getVirtualSensorList();
        assertThat(sensorList).hasSize(1);
        VirtualSensor sensor = sensorList.get(0);
        assertThat(sensor.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(sensor.getName()).isEqualTo(SENSOR_NAME);
    }

    @Test
    public void getAudioSessionIds_noIdsSpecified_shouldReturnPlaceholderValue() {
        assertThat(mVirtualDeviceManager.getAudioPlaybackSessionId(mVirtualDevice.getDeviceId()))
                .isEqualTo(AUDIO_SESSION_ID_GENERATE);
        assertThat(mVirtualDeviceManager.getAudioRecordingSessionId(mVirtualDevice.getDeviceId()))
                .isEqualTo(AUDIO_SESSION_ID_GENERATE);
    }

    @Test
    public void getAudioSessionIds_withIdsSpecified_shouldReturnPlaceholderValue() {
        int playbackSessionId = 42;
        int recordingSessionId = 77;
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .setAudioPlaybackSessionId(playbackSessionId)
                        .setAudioRecordingSessionId(recordingSessionId)
                        .build());

        assertThat(mVirtualDeviceManager.getAudioPlaybackSessionId(virtualDevice.getDeviceId()))
                .isEqualTo(playbackSessionId);
        assertThat(mVirtualDeviceManager.getAudioRecordingSessionId(virtualDevice.getDeviceId()))
                .isEqualTo(recordingSessionId);
    }

    @Test
    public void playSoundEffect_callsListener() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .build());
        virtualDevice.addSoundEffectListener(Runnable::run, mSoundEffectListener);

        mVirtualDeviceManager.playSoundEffect(virtualDevice.getDeviceId(), FX_KEY_CLICK);
        mVirtualDeviceManager.playSoundEffect(virtualDevice.getDeviceId(), FX_BACK);

        verify(mSoundEffectListener, timeout(TIMEOUT_MILLIS)).onPlaySoundEffect(FX_KEY_CLICK);
        verify(mSoundEffectListener, timeout(TIMEOUT_MILLIS)).onPlaySoundEffect(FX_BACK);
    }

    @Test
    public void playSoundEffect_unregistersListener() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .build());
        virtualDevice.addSoundEffectListener(Runnable::run, mSoundEffectListener);

        mVirtualDeviceManager.playSoundEffect(virtualDevice.getDeviceId(), FX_KEY_CLICK);
        verify(mSoundEffectListener, timeout(TIMEOUT_MILLIS)).onPlaySoundEffect(FX_KEY_CLICK);

        virtualDevice.removeSoundEffectListener(mSoundEffectListener);
        mVirtualDeviceManager.playSoundEffect(virtualDevice.getDeviceId(), FX_BACK);

        verifyNoMoreInteractions(mSoundEffectListener);
    }

    @Test
    public void playSoundEffect_incorrectDeviceId_doesNothing() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .build());
        virtualDevice.addSoundEffectListener(Runnable::run, mSoundEffectListener);

        mVirtualDeviceManager.playSoundEffect(virtualDevice.getDeviceId() + 1, FX_KEY_CLICK);

        verifyZeroInteractions(mSoundEffectListener);
    }

    @Test
    public void createVirtualDevice_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class, () -> mRule.createManagedVirtualDevice(null));
    }

    @Test
    public void createVirtualDisplay_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDisplay(null, null, null));
    }

    @Test
    public void addSoundEffectListener_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.addSoundEffectListener(null, effectType -> {}));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.addSoundEffectListener(Runnable::run, null));
    }

    @Test
    public void removeSoundEffectListener_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.removeSoundEffectListener(null));
    }

    @Test
    public void addActivityListener_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.addActivityListener(
                        null, mock(VirtualDeviceManager.ActivityListener.class)));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.addActivityListener(Runnable::run, null));
    }

    @Test
    public void removeActivityListener_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.removeActivityListener(null));
    }

    private void assertDeviceCreated(int deviceId) {
        verify(mVirtualDeviceListener, timeout(TIMEOUT_MILLIS).times(1))
                .onVirtualDeviceCreated(deviceId);
    }

    private void assertDeviceClosed(int deviceId) {
        verify(mVirtualDeviceListener, timeout(TIMEOUT_MILLIS).times(1))
                .onVirtualDeviceClosed(deviceId);
    }

    private static void waitForCondition(String message, BooleanSupplier waitCondition) {
        assertThat(Condition.waitFor(message, waitCondition)).isTrue();
    }
}
