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

package android.virtualdevice.cts.core;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.companion.virtual.sensor.VirtualSensorDirectChannelCallback;
import android.content.ComponentName;
import android.hardware.SensorDirectChannel;
import android.os.Parcel;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceParamsTest {

    private static final String VIRTUAL_DEVICE_NAME = "VirtualDeviceName";
    private static final String SENSOR_NAME = "VirtualSensorName";
    private static final String SENSOR_VENDOR = "VirtualSensorVendor";
    private static final int PLAYBACK_SESSION_ID = 42;
    private static final int RECORDING_SESSION_ID = 77;
    private static final ComponentName COMPONENT_NAME = new ComponentName("test", ".Activity1");
    private static final ComponentName COMPONENT_NAME_2 = new ComponentName("test", ".Activity2");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock
    private VirtualSensorCallback mVirtualSensorCallback;
    @Mock
    private VirtualSensorDirectChannelCallback mVirtualSensorDirectChannelCallback;

    private Executor mExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mExecutor = InstrumentationRegistry.getInstrumentation().getContext().getMainExecutor();
    }

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        VirtualDeviceParams originalParams = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .setUsersWithMatchingAccounts(Set.of(UserHandle.of(123), UserHandle.of(456)))
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(PLAYBACK_SESSION_ID)
                .setAudioRecordingSessionId(RECORDING_SESSION_ID)
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                .setVendor(SENSOR_VENDOR)
                                .setDirectChannelTypesSupported(
                                        SensorDirectChannel.TYPE_MEMORY_FILE)
                                .setHighestDirectReportRateLevel(SensorDirectChannel.RATE_NORMAL)
                                .build())
                .setVirtualSensorCallback(mExecutor, mVirtualSensorCallback)
                .setVirtualSensorDirectChannelCallback(
                        mExecutor, mVirtualSensorDirectChannelCallback)
                .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualDeviceParams params = VirtualDeviceParams.CREATOR.createFromParcel(parcel);
        assertThat(params).isEqualTo(originalParams);
        assertThat(params.getLockState()).isEqualTo(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED);
        assertThat(params.getUsersWithMatchingAccounts())
                .containsExactly(UserHandle.of(123), UserHandle.of(456));
        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_RECENTS)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CLIPBOARD)).isEqualTo(
                Flags.crossDeviceClipboard() ? DEVICE_POLICY_CUSTOM : DEVICE_POLICY_DEFAULT);
        assertThat(params.getAudioPlaybackSessionId()).isEqualTo(PLAYBACK_SESSION_ID);
        assertThat(params.getAudioRecordingSessionId()).isEqualTo(RECORDING_SESSION_ID);

        List<VirtualSensorConfig> sensorConfigs = params.getVirtualSensorConfigs();
        assertThat(sensorConfigs).hasSize(1);
        VirtualSensorConfig sensorConfig = sensorConfigs.get(0);
        assertThat(sensorConfig.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(sensorConfig.getName()).isEqualTo(SENSOR_NAME);
        assertThat(sensorConfig.getVendor()).isEqualTo(SENSOR_VENDOR);
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_HOME)
    @Test
    public void customHome_parcelable_shouldRecreateSuccessfully() {
        VirtualDeviceParams originalParams = new VirtualDeviceParams.Builder()
                .setHomeComponent(COMPONENT_NAME)
                .build();

        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualDeviceParams params = VirtualDeviceParams.CREATOR.createFromParcel(parcel);
        assertThat(params).isEqualTo(originalParams);
        assertThat(params.getHomeComponent()).isEqualTo(COMPONENT_NAME);
    }

    @RequiresFlagsEnabled(Flags.FLAG_VDM_CUSTOM_IME)
    @Test
    public void customIme_parcelable_shouldRecreateSuccessfully() {
        VirtualDeviceParams originalParams = new VirtualDeviceParams.Builder()
                .setInputMethodComponent(COMPONENT_NAME)
                .build();

        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        VirtualDeviceParams params = VirtualDeviceParams.CREATOR.createFromParcel(parcel);
        assertThat(params).isEqualTo(originalParams);
        assertThat(params.getInputMethodComponent()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void setAllowedAndBlockedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of(COMPONENT_NAME));
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of());
        });
    }

    @Test
    public void setBlockedAndAllowedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of(COMPONENT_NAME));
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of());
        });
    }

    @Test
    public void getAllowedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(COMPONENT_NAME, COMPONENT_NAME_2))
                .build();

        assertThat(params.getAllowedCrossTaskNavigations())
                .containsExactly(COMPONENT_NAME, COMPONENT_NAME_2);
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(COMPONENT_NAME, COMPONENT_NAME_2))
                .build();

        assertThat(params.getBlockedCrossTaskNavigations())
                .containsExactly(COMPONENT_NAME, COMPONENT_NAME_2);
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_ALLOWED);
    }

    @Test
    public void setAllowedAndBlockedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedActivities(Set.of(COMPONENT_NAME));
            paramsBuilder.setBlockedActivities(Set.of());
        });
    }

    @Test
    public void setBlockedAndAllowedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedActivities(Set.of(COMPONENT_NAME));
            paramsBuilder.setAllowedActivities(Set.of());
        });
    }

    @Test
    public void getAllowedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(COMPONENT_NAME, COMPONENT_NAME_2))
                .build();

        assertThat(params.getAllowedActivities())
                .containsExactly(COMPONENT_NAME, COMPONENT_NAME_2);
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(COMPONENT_NAME, COMPONENT_NAME_2))
                .build();

        assertThat(params.getBlockedActivities())
                .containsExactly(COMPONENT_NAME, COMPONENT_NAME_2);
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_ALLOWED);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setBlockedActivities_activityPolicyCustom_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualDeviceParams.Builder()
                        .setBlockedActivities(Set.of(COMPONENT_NAME))
                        .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                        .build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setBlockedActivities_activityPolicyDefault_isOK() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(COMPONENT_NAME))
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build();

        assertThat(params.getBlockedActivities()).containsExactly(COMPONENT_NAME);
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_ALLOWED);
        assertThat(params.getDevicePolicy(POLICY_TYPE_ACTIVITY)).isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setAllowedActivities_activityPolicyDefault_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualDeviceParams.Builder()
                        .setAllowedActivities(Set.of(COMPONENT_NAME))
                        .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                        .build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setAllowedActivities_activityPolicyCustom_isOK() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(COMPONENT_NAME))
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build();

        assertThat(params.getAllowedActivities()).containsExactly(COMPONENT_NAME);
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_BLOCKED);
        assertThat(params.getDevicePolicy(POLICY_TYPE_ACTIVITY)).isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setAllowedActivities_activityPolicyUndefined_setsActivityPolicyCustom() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(COMPONENT_NAME))
                .build();
        assertThat(params.getDevicePolicy(POLICY_TYPE_ACTIVITY)).isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setBlockedActivities_activityPolicyUndefined_setsActivityPolicyDefault() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(COMPONENT_NAME))
                .build();
        assertThat(params.getDevicePolicy(POLICY_TYPE_ACTIVITY)).isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getLockState_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build();

        assertThat(params.getLockState()).isEqualTo(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED);
    }

    @Test
    public void getUsersWithMatchingAccounts_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setUsersWithMatchingAccounts(Set.of(UserHandle.SYSTEM))
                .build();

        assertThat(params.getUsersWithMatchingAccounts()).containsExactly(UserHandle.SYSTEM);
    }

    @Test
    public void getName_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .build();

        assertThat(params.getName()).isEqualTo(VIRTUAL_DEVICE_NAME);
    }

    @Test
    public void getDevicePolicy_noPolicySpecified_shouldReturnDefault() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .build();

        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_RECENTS)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CLIPBOARD)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CAMERA)).isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                .build();

        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_RECENTS)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CLIPBOARD)).isEqualTo(
                Flags.crossDeviceClipboard() ? DEVICE_POLICY_CUSTOM : DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CAMERA)).isEqualTo(
                Flags.virtualCamera() ? DEVICE_POLICY_CUSTOM : DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void setDevicePolicy_overwritePreviousValue_shouldReturnValueFromLastCall() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)

                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_DEFAULT)
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_DEFAULT)
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT)

                .build();

        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_RECENTS)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_CLIPBOARD)).isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void virtualSensorConfigs_withoutCustomPolicy_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .addVirtualSensorConfig(new VirtualSensorConfig.Builder(
                                TYPE_ACCELEROMETER, SENSOR_NAME)
                                .build())
                        .setVirtualSensorCallback(mExecutor, mVirtualSensorCallback)
                        .build());
    }

    @Test
    public void virtualSensorConfigs_withoutVirtualSensorCallback_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .addVirtualSensorConfig(new VirtualSensorConfig.Builder(
                                TYPE_ACCELEROMETER, SENSOR_NAME)
                                .build())
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .build());
    }

    @Test
    public void virtualSensorConfigs_withoutVirtualSensorDirectChannelCallback_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .addVirtualSensorConfig(new VirtualSensorConfig.Builder(
                                TYPE_ACCELEROMETER, SENSOR_NAME)
                                .setDirectChannelTypesSupported(
                                        SensorDirectChannel.TYPE_MEMORY_FILE)
                                .setHighestDirectReportRateLevel(SensorDirectChannel.RATE_NORMAL)
                                .build())
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .setVirtualSensorCallback(mExecutor, mVirtualSensorCallback)
                        .build());
    }

    @Test
    public void virtualSensorConfigs_duplicateNamePerType_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                        .build())
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                        .build())
                        .build());
    }

    @Test
    public void virtualSensorConfigs_multipleSensorsPerType_succeeds() {
        final String secondSensorName = SENSOR_NAME + "2";
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME).build())
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, secondSensorName)
                                .build())
                .setVirtualSensorCallback(mExecutor, mVirtualSensorCallback)
                .build();

        List<VirtualSensorConfig> virtualSensorConfigs = params.getVirtualSensorConfigs();
        assertThat(virtualSensorConfigs).hasSize(2);
        for (int i = 0; i < virtualSensorConfigs.size(); ++i) {
            assertThat(virtualSensorConfigs.get(i).getType()).isEqualTo(TYPE_ACCELEROMETER);
        }
    }

    @Test
    public void audioSessionIds_placeholder_succeeds() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAudioPlaybackSessionId(AUDIO_SESSION_ID_GENERATE)
                .setAudioRecordingSessionId(AUDIO_SESSION_ID_GENERATE).build();

        assertThat(params.getAudioPlaybackSessionId()).isEqualTo(AUDIO_SESSION_ID_GENERATE);
        assertThat(params.getAudioRecordingSessionId()).isEqualTo(AUDIO_SESSION_ID_GENERATE);
    }

    @Test
    public void audioSessionIds_validIds_succeeds() {
        int playbackSessionId = 42;
        int recordingSessionId = 77;
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build();

        assertThat(params.getAudioPlaybackSessionId()).isEqualTo(playbackSessionId);
        assertThat(params.getAudioRecordingSessionId()).isEqualTo(recordingSessionId);
    }

    @Test
    public void audioPlaybackSessionId_invalidId_throwsException() {
        int playbackSessionId = -42;
        int recordingSessionId = 77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build());
    }

    @Test
    public void audioRecordingSessionId_invalidId_throwsException() {
        int playbackSessionId = 42;
        int recordingSessionId = -77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build());
    }

    @Test
    public void audioPlaybackSessionId_withoutCustomPolicy_throwsException() {
        int playbackSessionId = 42;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setAudioPlaybackSessionId(playbackSessionId).build());
    }

    @Test
    public void audioRecordingSessionId_withoutCustomPolicy_throwsException() {
        int recordingSessionId = 77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setAudioRecordingSessionId(recordingSessionId).build());
    }
}

