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

package android.car.cts;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_AUDIO_MIRRORING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_EVENTS;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.AUDIO_MIRROR_CAN_ENABLE;
import static android.car.media.CarAudioManager.INVALID_AUDIO_ZONE;
import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.car.test.mocks.JavaMockitoHelper.await;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.PlatformVersion;
import android.car.cts.utils.ProtoDumpUtils;
import android.car.feature.Flags;
import android.car.media.AudioZonesMirrorStatusCallback;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupInfo;
import android.car.media.MediaAudioRequestStatusCallback;
import android.car.media.PrimaryZoneMediaAudioRequestCallback;
import android.car.media.SwitchAudioZoneConfigCallback;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.test.util.CarAudioManagerTestUtils;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.audio.CarAudioDumpProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneConfigProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneProto;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupProto;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarAudioManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarAudioManagerTest.class.getSimpleName();

    private static final long WAIT_TIMEOUT_MS = 5_000;

    private static final Pattern ZONE_PATTERN = Pattern.compile(
            "CarAudioZone\\(.*:(\\d?)\\) isPrimary\\? (.*?)\n.*Current Config Id: (\\d?)");
    private static final Pattern VOLUME_GROUP_PATTERN = Pattern.compile(
            "CarVolumeGroup\\((\\d?)\\)\n.*Name\\((.*?)\\)\n.*Zone Id\\((\\d?)\\)\n"
                    + ".*Configuration Id\\((\\d?)\\)");
    private static final Pattern ZONE_CONFIG_PATTERN = Pattern.compile(
            "CarAudioZoneConfig\\((.*?):(\\d?)\\) of zone (\\d?) isDefault\\? (.*?)");
    private static final Pattern PRIMARY_ZONE_MEDIA_REQUEST_APPROVERS_PATTERN =
            Pattern.compile("Media request callbacks\\[(\\d+)\\]:");

    private static final int USAGE_INVALID = -1;
    private static final int VOLUME_FLAGS = 0;
    private static final int INVALID_VOLUME_GROUP_ID = -1;
    private static final int LEGACY_ZONE_ID = 0;
    private static final int LEGACY_VOLUME_GROUP_ID = 0;
    private static final int LEGACY_GROUP_VOLUME_COUNT = 3;

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarAudioManager mCarAudioManager;
    private CarAudioManagerTestUtils.SyncCarVolumeCallback mCallback;
    private int mZoneId = LEGACY_ZONE_ID;
    private int mConfigId = -1;
    private int mVolumeGroupId = -1;
    private int mVolumeGroupCount = LEGACY_GROUP_VOLUME_COUNT;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private TestPrimaryZoneMediaAudioRequestStatusCallback mRequestCallback;
    private long mMediaRequestId = INVALID_REQUEST_ID;
    private String mCarAudioServiceDump;
    private CarAudioDumpProto mCarAudioServiceProtoDump;
    private TestAudioZonesMirrorStatusCallback mAudioZonesMirrorCallback;
    private long mMirrorRequestId = INVALID_REQUEST_ID;
    private CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback mEventCallback;

    @Before
    public void setUp() throws Exception {
        mCarAudioManager = getCar().getCarManager(CarAudioManager.class);
        mCarOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
        if (Flags.carDumpToProto()) {
            mCarAudioServiceProtoDump = CarAudioDumpProto.parseFrom(
                    ProtoDumpUtils.executeProtoDumpShellCommand("CarAudioService"));
        } else {
            mCarAudioServiceDump = ShellUtils.runShellCommand(
                    "dumpsys car_service --services CarAudioService");
        }
    }

    @After
    public void cleanUp() {
        if (mCallback != null) {
            // Unregistering the last callback requires PERMISSION_CAR_CONTROL_AUDIO_VOLUME
            runWithCarControlAudioVolumePermission(
                    () -> mCarAudioManager.unregisterCarVolumeCallback(mCallback));
        }

        if (mMediaRequestId != INVALID_REQUEST_ID) {
            Log.w(TAG, "Cancelling media request " +  mMediaRequestId);
            mCarAudioManager.cancelMediaAudioOnPrimaryZone(mMediaRequestId);
        }

        if (mRequestCallback != null) {
            Log.w(TAG, "Releasing media request callback");
            mCarAudioManager.clearPrimaryZoneMediaAudioRequestCallback();
        }

        if (mAudioZonesMirrorCallback != null) {
            Log.i(TAG, "Releasing audio mirror request callback");
            mCarAudioManager.clearAudioZonesMirrorStatusCallback();
        }

        if (mMirrorRequestId != INVALID_REQUEST_ID) {
            Log.i(TAG, "Disabling audio mirror for request: " + mMirrorRequestId);
            mCarAudioManager.disableAudioMirror(mMirrorRequestId);
        }

        if (mEventCallback != null && Car.getPlatformVersion().isAtLeast(
                PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0)) {
            runWithCarControlAudioVolumePermission(
                    () -> mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback));
        }
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#isAudioFeatureEnabled"})
    public void isAudioFeatureEnabled_withVolumeGroupMuteFeature_succeeds() {
        boolean volumeGroupMutingEnabled = mCarAudioManager.isAudioFeatureEnabled(
                        AUDIO_FEATURE_VOLUME_GROUP_MUTING);

        assertThat(volumeGroupMutingEnabled).isAnyOf(true, false);
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#isAudioFeatureEnabled"})
    public void isAudioFeatureEnabled_withDynamicRoutingFeature_succeeds() {
        boolean dynamicRoutingEnabled = mCarAudioManager.isAudioFeatureEnabled(
                        AUDIO_FEATURE_DYNAMIC_ROUTING);

        assertThat(dynamicRoutingEnabled).isAnyOf(true, false);
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#isAudioFeatureEnabled"})
    public void isAudioFeatureEnabled_withVolumeGroupEventsFeature_succeeds() {
        boolean volumeGroupEventsEnabled = mCarAudioManager.isAudioFeatureEnabled(
                AUDIO_FEATURE_VOLUME_GROUP_EVENTS);

        assertWithMessage("Car volume group events feature").that(volumeGroupEventsEnabled)
                .isAnyOf(true, false);
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#isAudioFeatureEnabled"})
    public void isAudioFeatureEnabled_withNonAudioFeature_fails() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.isAudioFeatureEnabled(-1));

        assertThat(exception).hasMessageThat().contains("Unknown Audio Feature");
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager#isAudioFeatureEnabled(int)",
            "android.car.media.CarAudioManager#AUDIO_FEATURE_AUDIO_MIRRORING"})
    public void isAudioFeatureEnabled_withAudioMirrorFeature_succeeds() {
        boolean audioMirroringEnabled = mCarAudioManager.isAudioFeatureEnabled(
                AUDIO_FEATURE_AUDIO_MIRRORING);

        assertThat(audioMirroringEnabled).isAnyOf(true, false);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#canEnableAudioMirror"})
    public void canEnableAudioMirror() {
        assumeAudioMirrorEnabled();

        int status = mCarAudioManager.canEnableAudioMirror();

        assertWithMessage("Can enable audio mirror status with feature supported")
                .that(status).isEqualTo(AUDIO_MIRROR_CAN_ENABLE);
    }

    @Test
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#registerCarVolumeCallback"})
    public void registerCarVolumeCallback_nullCallback_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> mCarAudioManager.registerCarVolumeCallback(null));
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#registerCarVolumeCallback"})
    public void registerCarVolumeCallback_onGroupVolumeChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();

        mCarAudioManager.registerCarVolumeCallback(mCallback);

        injectVolumeDownKeyEvent();
        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should be called")
                .that(mCallback.receivedGroupVolumeChanged())
                .isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#registerCarVolumeCallback"})
    public void registerCarVolumeCallback_onMasterMuteChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupMutingIsDisabled();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();

        mCarAudioManager.registerCarVolumeCallback(mCallback);

        injectVolumeMuteKeyEvent();
        try {
            assertWithMessage("CarVolumeCallback#onMasterMuteChanged should be called")
                .that(mCallback.receivedMasterMuteChanged())
                .isTrue();
        } finally {
            injectVolumeMuteKeyEvent();
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#registerCarVolumeCallback"})
    public void registerCarVolumeCallback_onGroupMuteChanged() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupMutingIsEnabled();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();

        readFirstZoneAndVolumeGroup();
        mCarAudioManager.registerCarVolumeCallback(mCallback);
        setVolumeGroupMute(mZoneId, mVolumeGroupId, /* mute= */ true);
        setVolumeGroupMute(mZoneId, mVolumeGroupId, /* mute= */ false);

        assertWithMessage("CarVolumeCallback#onGroupMuteChanged should be called")
            .that(mCallback.receivedGroupMuteChanged()).isTrue();
        assertWithMessage("CarVolumeCallback#onGroupMuteChanged wrong zoneId")
            .that(mCallback.zoneId).isEqualTo(mZoneId);
        assertWithMessage("CarVolumeCallback#onGroupMuteChanged wrong groupId")
            .that(mCallback.groupId).isEqualTo(mVolumeGroupId);
    }

    @Test
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#unregisterCarVolumeCallback"})
    public void unregisterCarVolumeCallback_nullCallback_throws() {
        assertThrows(NullPointerException.class,
                () -> mCarAudioManager.unregisterCarVolumeCallback(null));
    }

    @Test
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#unregisterCarVolumeCallback"})
    public void unregisterCarVolumeCallback_unregisteredCallback_doesNotReceiveCallback()
            throws Exception {
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();

        mCarAudioManager.unregisterCarVolumeCallback(mCallback);

        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should not be called")
                .that(mCallback.receivedGroupVolumeChanged())
                .isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#unregisterCarVolumeCallback"})
    public void unregisterCarVolumeCallback_noLongerReceivesCallback() throws Exception {
        assumeDynamicRoutingIsEnabled();
        CarAudioManagerTestUtils.SyncCarVolumeCallback callback =
                new CarAudioManagerTestUtils.SyncCarVolumeCallback();
        mCarAudioManager.registerCarVolumeCallback(callback);
        mCarAudioManager.unregisterCarVolumeCallback(callback);

        injectVolumeDownKeyEvent();

        assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should not be called")
                .that(callback.receivedGroupVolumeChanged())
                .isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfo"})
    @RequiresFlagsDisabled(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
    public void getVolumeGroupInfo_withMinMaxActivationVolumeFlagDisabled() {
        assumeDynamicRoutingIsEnabled();
        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            int minIndex = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, index);
            int maxIndex = mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, index);
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group id for info %s and group %s", info, index)
                    .that(info.getId()).isEqualTo(index);
            expectWithMessage("Car volume group info zone for info %s and group %s",
                    info, index).that(info.getZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
            expectWithMessage("Car volume group info max index for info %s and group %s",
                    info, index).that(info.getMaxVolumeGainIndex()).isEqualTo(maxIndex);
            expectWithMessage("Car volume group info min index for info %s and group %s",
                    info, index).that(info.getMinVolumeGainIndex()).isEqualTo(minIndex);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfo",
            "android.car.media.CarVolumeGroupInfo#getMaxActivationVolumeGainIndex",
            "android.car.media.CarVolumeGroupInfo#getMinActivationVolumeGainIndex"})
    @RequiresFlagsEnabled(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME)
    public void getVolumeGroupInfo_withMinMaxActivationVolumeFlagEnabled() {
        assumeDynamicRoutingIsEnabled();
        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            int minIndex = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, index);
            int maxIndex = mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, index);
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            int minActivationIndex = info.getMinActivationVolumeGainIndex();
            int maxActivationIndex = info.getMaxActivationVolumeGainIndex();

            expectWithMessage("Car volume group id for info %s and group %s", info, index)
                    .that(info.getId()).isEqualTo(index);
            expectWithMessage("Car volume group info zone for info %s and group %s",
                    info, index).that(info.getZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
            expectWithMessage("Car volume group info max index for info %s and group %s",
                    info, index).that(info.getMaxVolumeGainIndex()).isEqualTo(maxIndex);
            expectWithMessage("Car volume group info min index for info %s and group %s",
                    info, index).that(info.getMinVolumeGainIndex()).isEqualTo(minIndex);
            expectWithMessage("Car volume group info min activation volume for info %s and"
                    + " group %s", info, index).that(minActivationIndex).isAtLeast(minIndex);
            expectWithMessage("Car volume group info max activation volume for info %s and"
                    + " group %s", info, index).that(maxActivationIndex).isAtMost(maxIndex);
            expectWithMessage("Max activation volume and min activation volume for info %s"
                    + " and group %s", info, index).that(maxActivationIndex)
                    .isGreaterThan(minActivationIndex);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfosForZone"})
    public void getVolumeGroupInfosForZone() {
        assumeDynamicRoutingIsEnabled();
        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        List<CarVolumeGroupInfo> infos =
                mCarAudioManager.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        expectWithMessage("Car volume group infos for primary zone")
                .that(infos).hasSize(groupCount);
        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group infos for info %s and group %s", info, index)
                    .that(infos).contains(info);
        }
    }

    @Test
    @EnsureHasPermission({Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME,
            Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS})
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupInfosForZone",
            "android.car.media.CarVolumeGroupInfo#getAudioDeviceAttributes",
            "android.car.media.CarVolumeGroupInfo#isDefault"})
    @RequiresFlagsEnabled(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public void getVolumeGroupInfosForZone_forPrimaryZone_withAudioDeviceAttributes()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        setUpDefaultCarAudioConfigurationForZone(PRIMARY_AUDIO_ZONE);

        List<CarVolumeGroupInfo> infos =
                mCarAudioManager.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE);

        expectWithMessage("Primary zone's car volume groups").that(infos).isNotEmpty();
        for (int count = 0; count < infos.size(); count++) {
            CarVolumeGroupInfo info = infos.get(count);
            List<Integer> types = info.getAudioDeviceAttributes().stream()
                    .map(AudioDeviceAttributes::getType).collect(Collectors.toList());
            expectWithMessage("Primary zone's car volume group %s types", info.getId())
                    .that(types).isNotEmpty();
            for (int index = 0; index < types.size(); index++)  {
                expectWithMessage("Primary zone's car volume group device type "
                    + " for group %s device index %s", info.getId(), index)
                        .that(types.get(index)).isEqualTo(AudioDeviceInfo.TYPE_BUS);
            }
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#getAudioAttributesForVolumeGroup"})
    public void getAudioAttributesForVolumeGroup() {
        assumeDynamicRoutingIsEnabled();
        CarVolumeGroupInfo info =
                mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, /* groupId= */ 0);

        expectWithMessage("Car volume audio attributes")
                .that(mCarAudioManager.getAudioAttributesForVolumeGroup(info))
                .isNotEmpty();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getGroupMaxVolume",
            "android.car.media.CarAudioManager#getGroupMinVolume",
            "android.car.media.CarAudioManager#getGroupVolume"})
    public void getGroupVolume() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            assumePrimaryZone();
        }
        int maxIndex = mCarAudioManager.getGroupMaxVolume(mVolumeGroupId);
        int minIndex = mCarAudioManager.getGroupMinVolume(mVolumeGroupId);

        int currentIndex = mCarAudioManager.getGroupVolume(mVolumeGroupId);

        assertWithMessage("Current maximum volume for primary zone")
                .that(currentIndex).isAtMost(maxIndex);
        assertWithMessage("Current minimum volume for primary zone")
                .that(currentIndex).isAtLeast(minIndex);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getGroupMaxVolume",
            "android.car.media.CarAudioManager#getGroupMinVolume",
            "android.car.media.CarAudioManager#getGroupVolume"})
    public void getGroupVolume_withZoneId() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            readFirstZoneAndVolumeGroup();
        }
        int maxIndex = mCarAudioManager.getGroupMaxVolume(mZoneId, mVolumeGroupId);
        int minIndex = mCarAudioManager.getGroupMinVolume(mZoneId, mVolumeGroupId);

        int currentIndex = mCarAudioManager.getGroupVolume(mZoneId, mVolumeGroupId);

        assertWithMessage("Current maximum volume for zone %s volume group %s", mZoneId,
                mVolumeGroupId).that(currentIndex).isAtMost(maxIndex);
        assertWithMessage("Current minimum volume for zone %s volume group %s", mZoneId,
                mVolumeGroupId).that(currentIndex).isAtLeast(minIndex);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_toMax_succeeds() {
        assumeDynamicRoutingIsEnabled();
        assumePrimaryZone();
        int prevIndex = mCarAudioManager.getGroupVolume(mZoneId, mVolumeGroupId);
        int maxIndex = mCarAudioManager.getGroupMaxVolume(mVolumeGroupId);

        mCarAudioManager.setGroupVolume(mVolumeGroupId, maxIndex, VOLUME_FLAGS);

        try {
            assertWithMessage("Current volume after setting to max for primary zone volume group"
                    + " %s", mVolumeGroupId).that(mCarAudioManager.getGroupVolume(mVolumeGroupId))
                    .isEqualTo(maxIndex);
        } finally {
            mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, prevIndex, VOLUME_FLAGS);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_toMin_succeeds() {
        assumeDynamicRoutingIsEnabled();
        assumePrimaryZone();
        int prevIndex = mCarAudioManager.getGroupVolume(mZoneId, mVolumeGroupId);
        int minIndex = mCarAudioManager.getGroupMinVolume(mVolumeGroupId);

        mCarAudioManager.setGroupVolume(mVolumeGroupId, minIndex, VOLUME_FLAGS);

        try {
            assertWithMessage("Current volume after setting to min for primary zone volume group"
                    + " %s", mVolumeGroupId).that(mCarAudioManager.getGroupVolume(mVolumeGroupId))
                    .isEqualTo(minIndex);
        } finally {
            mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, prevIndex, VOLUME_FLAGS);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_withZone_toMax_succeeds() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        int prevIndex = mCarAudioManager.getGroupVolume(mZoneId, mVolumeGroupId);
        int maxIndex = mCarAudioManager.getGroupMaxVolume(mZoneId, mVolumeGroupId);

        mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, maxIndex, VOLUME_FLAGS);

        try {
            assertWithMessage("Current volume after setting to max for zone %s volume group %s",
                    mZoneId, mVolumeGroupId).that(mCarAudioManager.getGroupVolume(mZoneId,
                    mVolumeGroupId)).isEqualTo(maxIndex);
        } finally {
            mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, prevIndex, VOLUME_FLAGS);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_withZone_toMin_succeeds() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        int prevIndex = mCarAudioManager.getGroupVolume(mZoneId, mVolumeGroupId);
        int minIndex = mCarAudioManager.getGroupMinVolume(mZoneId, mVolumeGroupId);

        mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, minIndex, VOLUME_FLAGS);

        try {
            assertWithMessage("Current volume after setting to min for zone %s volume group %s",
                    mZoneId, mVolumeGroupId).that(mCarAudioManager.getGroupVolume(mZoneId,
                    mVolumeGroupId)).isEqualTo(minIndex);
        } finally {
            mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, prevIndex, VOLUME_FLAGS);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_aboveMax_throwsException() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        int maxIndex = mCarAudioManager.getGroupMaxVolume(mZoneId, mVolumeGroupId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, maxIndex + 1,
                        VOLUME_FLAGS));

        assertWithMessage("Exception for setting volume above max")
                .that(exception).hasMessageThat().contains("Gain out of range");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setGroupVolume"})
    public void setGroupVolume_belowMin_throwsException() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        int minIndex = mCarAudioManager.getGroupMinVolume(mZoneId, mVolumeGroupId);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.setGroupVolume(mZoneId, mVolumeGroupId, minIndex - 1,
                        VOLUME_FLAGS));

        assertWithMessage("Exception for setting volume below min")
                .that(exception).hasMessageThat().contains("Gain out of range");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupCount"})
    public void getVolumeGroupCount() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            assumePrimaryZone();
        }

        assertWithMessage("Primary zone volume group count")
                .that(mCarAudioManager.getVolumeGroupCount()).isEqualTo(mVolumeGroupCount);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupCount"})
    public void getVolumeGroupCount_withZoneIdAndDynamicRouting() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            readFirstZoneAndVolumeGroup();
        }

        assertWithMessage("Zone %s volume group count", mZoneId)
                .that(mCarAudioManager.getVolumeGroupCount(mZoneId)).isEqualTo(mVolumeGroupCount);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupCount"})
    public void getVolumeGroupCount_withInvalidZoneId_throwsException() {
        assumeDynamicRoutingIsEnabled();
        assumePrimaryZone();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getVolumeGroupCount(INVALID_AUDIO_ZONE));

        assertWithMessage("Invalid zone volume group count exception").that(exception)
                .hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupIdForUsage"})
    public void getVolumeGroupIdForUsage_forInvalidUsage_returnsInvalidId() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            assumePrimaryZone();
        }

        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_INVALID);

        assertWithMessage("Volume group id for invalid usage").that(volumeGroupId)
                .isEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupIdForUsage"})
    public void getVolumeGroupIdForUsage_withInvalidZoneId_throwsException() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getVolumeGroupIdForUsage(INVALID_AUDIO_ZONE, USAGE_MEDIA));

        assertWithMessage("Invalid zone volume group for media usage exception").that(exception)
                .hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupIdForUsage"})
    public void getVolumeGroupIdForUsage_returnsValidId() {
        if (mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING)) {
            assumePrimaryZone();
        }

        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);

        assertWithMessage("Valid volume group id in primary zone")
                .that(volumeGroupId).isNotEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getVolumeGroupIdForUsage"})
    public void getVolumeGroupIdForUsage_withZoneId_returnsValidId() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();

        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(mZoneId, USAGE_MEDIA);

        assertWithMessage("Valid volume group id for media usage in zone %s", mZoneId)
                .that(volumeGroupId).isNotEqualTo(INVALID_VOLUME_GROUP_ID);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getUsagesForVolumeGroupId"})
    public void getUsagesForVolumeGroupId_returnsAtLeastOneUsage() {
        assumeDynamicRoutingIsEnabled();
        assumePrimaryZone();

        int[] usages = mCarAudioManager.getUsagesForVolumeGroupId(mVolumeGroupId);

        assertVolumeGroupIdForUsages(PRIMARY_AUDIO_ZONE, mVolumeGroupId, usages,
                "with dynamic routing");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getUsagesForVolumeGroupId"})
    public void getUsagesForVolumeGroupId_withoutDynamicRouting_returnsAtLeastOneUsage() {
        assumeDynamicRoutingIsDisabled();

        int[] usages = mCarAudioManager.getUsagesForVolumeGroupId(LEGACY_VOLUME_GROUP_ID);

        assertVolumeGroupIdForUsages(PRIMARY_AUDIO_ZONE, LEGACY_VOLUME_GROUP_ID,
                usages, "with dynamic routing disabled");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getUsagesForVolumeGroupId"})
    public void getUsagesForVolumeGroupId_withZoneId_returnsAtLeastOneUsage() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();

        int[] usages = mCarAudioManager.getUsagesForVolumeGroupId(mVolumeGroupId);

        assertVolumeGroupIdForUsages(mZoneId, mVolumeGroupId, usages,
                "with id and dynamic routing");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getUsagesForVolumeGroupId"})
    public void getUsagesForVolumeGroupId_withZoneIdWithoutDynamicRouting_returnsAtLeastOneUsage() {
        assumeDynamicRoutingIsDisabled();

        int[] usages = mCarAudioManager.getUsagesForVolumeGroupId(LEGACY_VOLUME_GROUP_ID);

        assertVolumeGroupIdForUsages(LEGACY_ZONE_ID, LEGACY_VOLUME_GROUP_ID, usages,
                "with id and with dynamic routing disabled");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getUsagesForVolumeGroupId"})
    public void getUsagesForVolumeGroupId_withInvalidZone_throwsException() {
        assumeDynamicRoutingIsEnabled();
        assumePrimaryZone();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getUsagesForVolumeGroupId(INVALID_AUDIO_ZONE,
                        mVolumeGroupId));

        assertWithMessage("Exception for getting usage for volume group in invalid zone")
                .that(exception).hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getOutputDeviceForUsage"})
    public void getOutputDeviceForUsage_withoutDynamicRouting_throws() {
        assumeDynamicRoutingIsDisabled();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> mCarAudioManager.getOutputDeviceForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA));

        assertWithMessage("Exception for getting media output device with dynamic routing disabled")
                .that(exception).hasMessageThat().contains("Non legacy routing");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getOutputDeviceForUsage"})
    public void getOutputDeviceForUsage_withInvalidZoneId_throws() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getOutputDeviceForUsage(INVALID_AUDIO_ZONE, USAGE_MEDIA));

        assertWithMessage("Exception for getting invalid zone media output device")
                .that(exception).hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getOutputDeviceForUsage"})
    public void getOutputDeviceForUsage_withInvalidUsage_throws() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getOutputDeviceForUsage(PRIMARY_AUDIO_ZONE, USAGE_INVALID));

        assertWithMessage("Invalid usage output device exception")
                .that(exception).hasMessageThat().contains("Invalid audio attribute");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getOutputDeviceForUsage"})
    public void getOutputDeviceForUsage_succeeds() {
        assumeDynamicRoutingIsEnabled();

        AudioDeviceInfo deviceInfo = mCarAudioManager.getOutputDeviceForUsage(PRIMARY_AUDIO_ZONE,
                USAGE_MEDIA);

        assertWithMessage("Device for media in primary zone")
                .that(deviceInfo).isNotNull();
        assertWithMessage("Sink device for media in primary zone")
                .that(deviceInfo.isSink()).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getInputDevicesForZoneId"})
    public void getInputDevicesForZoneId_withoutDynamicRouting_throws() {
        assumeDynamicRoutingIsDisabled();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> mCarAudioManager.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE));

        assertWithMessage("Exception for getting input devices with dynamic routing disabled")
                .that(exception).hasMessageThat().contains("Non legacy routing");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getInputDevicesForZoneId"})
    public void getInputDevicesForZoneId_withInvalidZoneId_throws() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getInputDevicesForZoneId(INVALID_AUDIO_ZONE));

        assertWithMessage("Exception for getting input devices with invalid volume group id")
                .that(exception).hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getInputDevicesForZoneId"})
    public void getInputDevicesForZoneId_succeeds() {
        assumeDynamicRoutingIsEnabled();

        List<AudioDeviceInfo> audioDeviceInfos = mCarAudioManager.getInputDevicesForZoneId(
                PRIMARY_AUDIO_ZONE);

        assertWithMessage("Input devices for primary zone")
                .that(audioDeviceInfos).isNotNull();
        for (int i = 0; i < audioDeviceInfos.size(); i++) {
            assertWithMessage("Source device %s in primary zone", audioDeviceInfos.get(i)
                    .getAddress()).that(audioDeviceInfos.get(i).isSource()).isTrue();
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone"})
    public void isMediaAudioAllowedInPrimaryZone_byDefault() {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        OccupantZoneInfo info =
                        mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);

        assertWithMessage("Default media allowed status")
                .that(mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info)).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone"})
    public void isMediaAudioAllowedInPrimaryZone_afterAllowed() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ true);

        boolean approved = mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info);

        assertWithMessage("Approved media allowed status")
                .that(approved).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#isMediaAudioAllowedInPrimaryZone"})
    public void isMediaAudioAllowedInPrimaryZone_afterRejected() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ false);

        boolean approved = mCarAudioManager.isMediaAudioAllowedInPrimaryZone(info);

        assertWithMessage("Unapproved media allowed status")
                .that(approved).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#setPrimaryZoneMediaAudioRequestCallback"})
    public void setPrimaryZoneMediaAudioRequestCallback() {
        assumePassengerWithValidAudioZone();
        Executor executor = Executors.newFixedThreadPool(1);
        TestPrimaryZoneMediaAudioRequestStatusCallback
                requestCallback = new TestPrimaryZoneMediaAudioRequestStatusCallback();

        boolean registered =
                mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(executor, requestCallback);

        assertWithMessage("Set status of media request callback")
                .that(registered).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#requestMediaAudioOnPrimaryZone"})
    public void requestMediaAudioOnPrimaryZone() throws Exception {
        assumeDynamicRoutingIsEnabled();
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();

        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);

        mRequestCallback.receivedMediaRequest();
        assertWithMessage("Received request id").that(mRequestCallback.mRequestId)
                .isEqualTo(mMediaRequestId);
        assertWithMessage("Received occupant info").that(mRequestCallback.mOccupantZoneInfo)
                .isEqualTo(info);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#requestMediaAudioOnPrimaryZone"})
    public void requestMediaAudioOnPrimaryZone_withoutApprover() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeNoPrimaryZoneAudioMediaApprovers();
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();

        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);

        callback.receivedApproval();
        assertWithMessage("Request id for rejected request")
                .that(callback.mRequestId).isEqualTo(mMediaRequestId);
        assertWithMessage("Rejected request status").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone"})
    public void allowMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback =
                requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        boolean succeeded = mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId,
                /* allow= */ true);

        callback.receivedApproval();
        assertWithMessage("Approved results for request in primary zone")
                .that(succeeded).isTrue();
        assertWithMessage("Approved request id in primary zone")
                .that(callback.mRequestId).isEqualTo(mMediaRequestId);
        assertWithMessage("Approved request occupant in primary zone")
                .that(callback.mOccupantZoneInfo).isEqualTo(info);
        assertWithMessage("Audio status in primary zone").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone"})
    public void allowMediaAudioOnPrimaryZone_withReject() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        boolean succeeded = mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId,
                /* allow= */ false);

        callback.receivedApproval();
        long tempRequestId = mMediaRequestId;
        mMediaRequestId = INVALID_REQUEST_ID;
        assertWithMessage("Unapproved results for request in primary zone")
                .that(succeeded).isTrue();
        assertWithMessage("Unapproved request id in primary zone")
                .that(callback.mRequestId).isEqualTo(tempRequestId);
        assertWithMessage("Unapproved audio status in primary zone").that(callback.mStatus)
                .isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#allowMediaAudioOnPrimaryZone"})
    public void allowMediaAudioOnPrimaryZone_withInvalidRequestId() throws Exception {
        assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();

        boolean succeeded = mCarAudioManager
                        .allowMediaAudioOnPrimaryZone(INVALID_REQUEST_ID, /* allow= */ true);

        assertWithMessage("Invalid request id allowed results")
                .that(succeeded).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#cancelMediaAudioOnPrimaryZone"})
    public void cancelMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();

        mCarAudioManager.cancelMediaAudioOnPrimaryZone(mMediaRequestId);

        mMediaRequestId = INVALID_REQUEST_ID;
        callback.receivedApproval();
        assertWithMessage("Cancelled audio status in primary zone")
                .that(callback.mStatus).isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#resetMediaAudioOnPrimaryZone"})
    public void resetMediaAudioOnPrimaryZone() throws Exception {
        int passengerAudioZoneId = assumePassengerWithValidAudioZone();
        setupMediaAudioRequestCallback();
        OccupantZoneInfo info =
                mCarOccupantZoneManager.getOccupantForAudioZoneId(passengerAudioZoneId);
        TestMediaAudioRequestStatusCallback callback = requestToPlayMediaInPrimaryZone(info);
        mRequestCallback.receivedMediaRequest();
        mCarAudioManager.allowMediaAudioOnPrimaryZone(mMediaRequestId, /* allow= */ true);
        callback.receivedApproval();
        callback.reset();

        mCarAudioManager.resetMediaAudioOnPrimaryZone(info);

        long tempRequestId = mMediaRequestId;
        mMediaRequestId = INVALID_REQUEST_ID;
        callback.receivedApproval();
        assertWithMessage("Reset request id in primary zone")
                .that(callback.mRequestId).isEqualTo(tempRequestId);
        assertWithMessage("Reset audio status in primary zone")
                .that(callback.mStatus).isEqualTo(CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getCurrentAudioZoneConfigInfo"})
    public void getCurrentAudioZoneConfigInfo() {
        assumeDynamicRoutingIsEnabled();
        List<TestZoneConfigIdInfo> zoneConfigs = assumeSecondaryZoneConfigs();

        CarAudioZoneConfigInfo currentZoneConfigInfo =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(mZoneId);

        assertWithMessage("Current zone config info")
                .that(TestZoneConfigIdInfo.getZoneConfigFromInfo(currentZoneConfigInfo))
                .isIn(zoneConfigs);
    }

    @Test
    @EnsureHasPermission({Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS,
            Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME})
    @ApiTest(apis = {"android.car.media.CarAudioManager#getCurrentAudioZoneConfigInfo",
            "android.car.media.CarAudioZoneConfigInfo#isActive",
            "android.car.media.CarAudioZoneConfigInfo#isSelected",
            "android.car.media.CarAudioZoneConfigInfo#isDefault",
            "android.car.media.CarAudioZoneConfigInfo#getConfigVolumeGroups"})
    @RequiresFlagsEnabled(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public void getCurrentAudioZoneConfigInfo_withDynamicDevicesEnabled() throws Exception {
        assumeDynamicRoutingIsEnabled();
        List<TestZoneConfigIdInfo> zoneConfigs = assumeSecondaryZoneConfigs();
        setUpDefaultCarAudioConfigurationForZone(mZoneId);

        CarAudioZoneConfigInfo currentZoneConfigInfo =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(mZoneId);

        assertWithMessage("Current zone config info, with dynamic device enabled")
                .that(TestZoneConfigIdInfo.getZoneConfigFromInfo(currentZoneConfigInfo))
                .isIn(zoneConfigs);
        assertWithMessage("Current zone config info active status")
                .that(currentZoneConfigInfo.isActive()).isTrue();
        assertWithMessage("Current zone config info selected status")
                .that(currentZoneConfigInfo.isSelected()).isTrue();
        assertWithMessage("Current zone config info default indicator")
                .that(currentZoneConfigInfo.isDefault()).isFalse();
        List<CarVolumeGroupInfo> currentInfos =
                mCarAudioManager.getVolumeGroupInfosForZone(mZoneId);
        for (CarVolumeGroupInfo info : currentInfos) {
            assertWithMessage("Current zone config info volume group %s", info.getId())
                    .that(currentZoneConfigInfo.getConfigVolumeGroups()).contains(info);
        }
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getCurrentAudioZoneConfigInfo"})
    public void getCurrentAudioZoneConfigInfo_withInvalidZoneId_fails() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getCurrentAudioZoneConfigInfo(INVALID_AUDIO_ZONE));

        assertThat(exception).hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getAudioZoneConfigInfos"})
    public void getAudioZoneConfigInfos_forPrimaryZone() {
        assumeDynamicRoutingIsEnabled();
        List<TestZoneConfigIdInfo> primaryZoneConfigsFromDump = parsePrimaryZoneConfigs();

        List<CarAudioZoneConfigInfo> zoneConfigInfos =
                mCarAudioManager.getAudioZoneConfigInfos(PRIMARY_AUDIO_ZONE);

        assertWithMessage("Primary audio zone config")
                .that(TestZoneConfigIdInfo.getZoneConfigListFromInfoList(zoneConfigInfos))
                .containsExactlyElementsIn(primaryZoneConfigsFromDump);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getAudioZoneConfigInfos"})
    public void getAudioZoneConfigInfos_forSecondaryZone() {
        assumeDynamicRoutingIsEnabled();
        List<TestZoneConfigIdInfo> zoneConfigs = assumeSecondaryZoneConfigs();

        List<CarAudioZoneConfigInfo> zoneConfigInfosFromDump =
                mCarAudioManager.getAudioZoneConfigInfos(mZoneId);

        assertWithMessage("All zone config infos")
                .that(TestZoneConfigIdInfo.getZoneConfigListFromInfoList(zoneConfigInfosFromDump))
                .containsExactlyElementsIn(zoneConfigs);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getAudioZoneConfigInfos"})
    public void getAudioZoneConfigInfos_withInvalidZoneId_fails() {
        assumeDynamicRoutingIsEnabled();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.getAudioZoneConfigInfos(INVALID_AUDIO_ZONE));

        assertThat(exception).hasMessageThat().contains("Invalid audio zone Id");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#switchAudioZoneToConfig"})
    @RequiresFlagsDisabled(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public void switchAudioZoneToConfig() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeSecondaryZoneConfigs();
        CarAudioZoneConfigInfo zoneConfigInfoSaved =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(mZoneId);
        CarAudioZoneConfigInfo zoneConfigInfoSwitchedTo = getNonCurrentZoneConfig(mZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestSwitchAudioZoneConfigCallback callback = new TestSwitchAudioZoneConfigCallback();

        mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSwitchedTo, callbackExecutor,
                callback);

        callback.receivedApproval();
        assertWithMessage("Zone configuration switching status")
                .that(callback.mIsSuccessful).isTrue();
        assertWithMessage("Updated zone configuration")
                .that(callback.mZoneConfigInfo).isEqualTo(zoneConfigInfoSwitchedTo);
        callback.reset();
        mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSaved, callbackExecutor, callback);
        callback.receivedApproval();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#switchAudioZoneToConfig",
            "android.car.media.CarAudioZoneConfigInfo#isActive",
            "android.car.media.CarAudioZoneConfigInfo#isSelected",
            "android.car.media.CarAudioZoneConfigInfo#getConfigVolumeGroups"})
    @RequiresFlagsEnabled(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES)
    public void switchAudioZoneToConfig_withDynamicDevicesEnabled() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeSecondaryZoneConfigs();
        CarAudioZoneConfigInfo zoneConfigInfoSaved =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(mZoneId);
        CarAudioZoneConfigInfo zoneConfigInfoSwitchedTo = getNonCurrentZoneConfig(mZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestSwitchAudioZoneConfigCallback callback = new TestSwitchAudioZoneConfigCallback();

        mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSwitchedTo, callbackExecutor,
                callback);

        callback.receivedApproval();
        assertWithMessage("Zone configuration switching status with dynamic devices enabled")
                .that(callback.mIsSuccessful).isTrue();
        assertWithMessage("Updated zone configuration with dynamic devices enabled")
                .that(callback.mZoneConfigInfo.hasSameConfigInfo(zoneConfigInfoSwitchedTo))
                .isTrue();
        assertWithMessage("Switched configuration active status with dynamic devices enable")
                .that(callback.mZoneConfigInfo.isActive()).isTrue();
        assertWithMessage("Switched configuration selected status with dynamic devices enable")
                .that(callback.mZoneConfigInfo.isSelected()).isTrue();
        CarAudioZoneConfigInfo updatedPreviousConfig =
                mCarAudioManager.getAudioZoneConfigInfos(mZoneId).stream()
                        .filter(info -> info.getConfigId() == zoneConfigInfoSaved.getConfigId())
                        .findFirst().orElseThrow();
        assertWithMessage("Previous configuration selected status with dynamic devices enable")
                .that(updatedPreviousConfig.isSelected()).isFalse();
        callback.reset();
        mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSaved, callbackExecutor, callback);
        callback.receivedApproval();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#switchAudioZoneToConfig"})
    public void switchAudioZoneToConfig_withNullConfig_fails() throws Exception {
        assumeDynamicRoutingIsEnabled();
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestSwitchAudioZoneConfigCallback callback = new TestSwitchAudioZoneConfigCallback();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.switchAudioZoneToConfig(/* zoneConfig= */ null,
                        callbackExecutor, callback));

        assertThat(exception).hasMessageThat().contains("Audio zone configuration can not be null");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#switchAudioZoneToConfig"})
    public void switchAudioZoneToConfig_withNullExecutor_fails() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeSecondaryZoneConfigs();
        CarAudioZoneConfigInfo zoneConfigInfoSwitchedTo = getNonCurrentZoneConfig(mZoneId);
        TestSwitchAudioZoneConfigCallback callback = new TestSwitchAudioZoneConfigCallback();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSwitchedTo,
                        /* executor= */ null, callback));

        assertThat(exception).hasMessageThat().contains("Executor can not be null");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#switchAudioZoneToConfig"})
    public void switchAudioZoneToConfig_withNullCallback_fails() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeSecondaryZoneConfigs();
        CarAudioZoneConfigInfo zoneConfigInfoSwitchedTo = getNonCurrentZoneConfig(mZoneId);
        Executor callbackExecutor = Executors.newFixedThreadPool(1);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.switchAudioZoneToConfig(zoneConfigInfoSwitchedTo,
                        callbackExecutor, /* callback= */ null));

        assertThat(exception).hasMessageThat().contains("callback can not be null");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#setAudioZoneMirrorStatusCallback"})
    public void setAudioZoneMirrorStatusCallback() {
        assumeAudioMirrorEnabled();
        assumePassengersForAudioMirror();
        Executor executor = Executors.newFixedThreadPool(1);
        TestAudioZonesMirrorStatusCallback callback;
        boolean registered = false;

        try {
            callback = new TestAudioZonesMirrorStatusCallback();
            registered = mCarAudioManager.setAudioZoneMirrorStatusCallback(executor, callback);
        } finally {
            if (registered) {
                mCarAudioManager.clearAudioZonesMirrorStatusCallback();
            }
        }

        assertWithMessage("Audio zone mirror status callback registered status")
                .that(registered).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#enableMirrorForAudioZones"})
    public void enableMirrorForAudioZones() throws Exception {
        assumeAudioMirrorEnabled();
        List<Integer> audioZones = assumePassengersForAudioMirror();
        setupAudioMirrorStatusCallback();

        mMirrorRequestId = mCarAudioManager.enableMirrorForAudioZones(audioZones);

        mAudioZonesMirrorCallback.waitForCallback();
        assertWithMessage("Enabled mirror for audio zone status")
                .that(mAudioZonesMirrorCallback.mStatus).isEqualTo(
                        CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED);
        assertWithMessage("Enabled mirror audio zones")
                .that(mAudioZonesMirrorCallback.mAudioZones).containsExactlyElementsIn(audioZones);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#enableMirrorForAudioZones"})
    public void enableMirrorForAudioZones_withNullList() throws Exception {
        assumeAudioMirrorEnabled();
        setupAudioMirrorStatusCallback();

        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                mMirrorRequestId = mCarAudioManager
                        .enableMirrorForAudioZones(/* audioZonesToMirror= */ null));

        assertWithMessage("Null enable mirror audio zones exception")
                .that(thrown).hasMessageThat().contains("Audio zones to mirror");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#disableAudioMirrorForZone"})
    public void disableAudioMirrorForZone() throws Exception {
        assumeAudioMirrorEnabled();
        List<Integer> audioZones = assumePassengersForAudioMirror();
        int zoneToDisable = audioZones.get(0);
        setupAudioMirrorStatusCallback();
        mMirrorRequestId = mCarAudioManager.enableMirrorForAudioZones(audioZones);
        mAudioZonesMirrorCallback.waitForCallback();
        mAudioZonesMirrorCallback.reset();

        mCarAudioManager.disableAudioMirrorForZone(zoneToDisable);

        mMirrorRequestId = INVALID_REQUEST_ID;
        mAudioZonesMirrorCallback.waitForCallback();
        assertWithMessage("Disable mirror status for audio zone %s", zoneToDisable)
                .that(mAudioZonesMirrorCallback.mStatus).isEqualTo(
                        CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
        assertWithMessage("Disable mirror zones for audio zone %s", zoneToDisable)
                .that(mAudioZonesMirrorCallback.mAudioZones).containsExactlyElementsIn(audioZones);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#disableAudioMirror"})
    public void disableAudioMirror() throws Exception {
        assumeAudioMirrorEnabled();
        List<Integer> audioZones = assumePassengersForAudioMirror();
        setupAudioMirrorStatusCallback();
        mMirrorRequestId = mCarAudioManager.enableMirrorForAudioZones(audioZones);
        mAudioZonesMirrorCallback.waitForCallback();
        mAudioZonesMirrorCallback.reset();

        mCarAudioManager.disableAudioMirror(mMirrorRequestId);

        mMirrorRequestId = INVALID_REQUEST_ID;
        mAudioZonesMirrorCallback.waitForCallback();
        assertWithMessage("Disable mirror status for audio zones")
                .that(mAudioZonesMirrorCallback.mStatus).isEqualTo(
                        CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
        assertWithMessage("Disable mirror zones for audio zones")
                .that(mAudioZonesMirrorCallback.mAudioZones).containsExactlyElementsIn(audioZones);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getMirrorAudioZonesForAudioZone"})
    public void getMirrorAudioZonesForAudioZone() throws Exception {
        assumeAudioMirrorEnabled();
        List<Integer> audioZones = assumePassengersForAudioMirror();
        int zoneToQuery = audioZones.get(0);
        setupAudioMirrorStatusCallback();
        mMirrorRequestId = mCarAudioManager.enableMirrorForAudioZones(audioZones);
        mAudioZonesMirrorCallback.waitForCallback();

        List<Integer> queriedZones = mCarAudioManager.getMirrorAudioZonesForAudioZone(zoneToQuery);

        assertWithMessage("Queried audio zones").that(queriedZones)
                .containsExactlyElementsIn(audioZones);
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    @ApiTest(apis = {"android.car.media.CarAudioManager#getMirrorAudioZonesForMirrorRequest"})
    public void getMirrorAudioZonesForMirrorRequest() throws Exception {
        assumeAudioMirrorEnabled();
        List<Integer> audioZones = assumePassengersForAudioMirror();
        setupAudioMirrorStatusCallback();
        mMirrorRequestId = mCarAudioManager.enableMirrorForAudioZones(audioZones);
        mAudioZonesMirrorCallback.waitForCallback();

        List<Integer> queriedZones = mCarAudioManager.getMirrorAudioZonesForMirrorRequest(
                mMirrorRequestId);

        assertWithMessage("Queried audio zones for request %s", mMirrorRequestId).that(queriedZones)
                .containsExactlyElementsIn(audioZones);
    }

    private TestMediaAudioRequestStatusCallback requestToPlayMediaInPrimaryZone(
            OccupantZoneInfo info) {
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestMediaAudioRequestStatusCallback callback = new TestMediaAudioRequestStatusCallback();
        mMediaRequestId =
                mCarAudioManager.requestMediaAudioOnPrimaryZone(info, callbackExecutor, callback);
        return callback;
    }

    private void setupMediaAudioRequestCallback() {
        Executor requestExecutor = Executors.newFixedThreadPool(1);
        mRequestCallback = new TestPrimaryZoneMediaAudioRequestStatusCallback();
        mCarAudioManager.setPrimaryZoneMediaAudioRequestCallback(requestExecutor, mRequestCallback);
    }

    private void setupAudioMirrorStatusCallback() {
        Executor executor = Executors.newFixedThreadPool(1);
        mAudioZonesMirrorCallback = new TestAudioZonesMirrorStatusCallback();
        mCarAudioManager.setAudioZoneMirrorStatusCallback(executor, mAudioZonesMirrorCallback);
    }

    private int assumePassengerWithValidAudioZone() {
        List<Integer> audioZonesWithPassengers = assumePassengersWithValidAudioZones(
                /* count= */ 1, "Audio share to primary zone");

        return audioZonesWithPassengers.get(0);
    }

    private List<Integer> assumePassengersWithValidAudioZones(int minimumCount, String message) {
        List<Integer> audioZonesWithPassengers = getAvailablePassengerAudioZone();
        assumeTrue(message + ": Need at least " + minimumCount
                        + " passenger(s) with valid audio zone id",
                audioZonesWithPassengers.size() >= minimumCount);

        return audioZonesWithPassengers;
    }

    private List<Integer> assumePassengersForAudioMirror() {
        List<Integer> audioZonesWithPassengers = assumePassengersWithValidAudioZones(/* count= */ 2,
                "Passenger audio mirror");

        return audioZonesWithPassengers.subList(/* fromIndex= */ 0, /* toIndex= */ 2);
    }

    private List<Integer> getAvailablePassengerAudioZone() {
        return mCarOccupantZoneManager.getAllOccupantZones().stream()
                .filter(occupant -> mCarOccupantZoneManager.getUserForOccupant(occupant)
                        != CarOccupantZoneManager.INVALID_USER_ID)
                .map(occupant -> mCarOccupantZoneManager.getAudioZoneIdForOccupant(occupant))
                .filter(audioZoneId -> audioZoneId != INVALID_AUDIO_ZONE
                        && audioZoneId != PRIMARY_AUDIO_ZONE)
                .collect(Collectors.toList());
    }

    private List<TestZoneConfigIdInfo> assumeSecondaryZoneConfigs() {
        List<Integer> audioZonesWithPassengers = getAvailablePassengerAudioZone();
        assumeFalse("Requires a zone with a passenger/user", audioZonesWithPassengers.isEmpty());
        SparseArray<List<TestZoneConfigIdInfo>> zoneConfigs = parseAudioZoneConfigs();
        List<TestZoneConfigIdInfo> secondaryZoneConfigs = null;
        for (int index = 0; index < audioZonesWithPassengers.size(); index++) {
            int zoneId = audioZonesWithPassengers.get(index);
            if (zoneConfigs.contains(zoneId) && zoneConfigs.get(zoneId).size() > 1) {
                mZoneId = zoneId;
                secondaryZoneConfigs = zoneConfigs.get(zoneId);
                break;
            }
        }
        assumeTrue("Secondary zones requires multiple zone configurations",
                secondaryZoneConfigs != null);
        return secondaryZoneConfigs;
    }

    private List<TestZoneConfigIdInfo> parsePrimaryZoneConfigs() {
        SparseArray<List<TestZoneConfigIdInfo>> zoneConfigs = parseAudioZoneConfigs();
        List<TestZoneConfigIdInfo> primaryZoneConfigs = zoneConfigs.get(PRIMARY_AUDIO_ZONE);
        assertWithMessage("Dumped primary audio zone configuration")
                .that(primaryZoneConfigs).isNotNull();
        return primaryZoneConfigs;
    }

    private SparseArray<List<TestZoneConfigIdInfo>> parseAudioZoneConfigs() {
        SparseArray<List<TestZoneConfigIdInfo>> zoneConfigs = new SparseArray<>();
        if (Flags.carDumpToProto()) {
            List<CarAudioZoneProto> zoneProtoList = mCarAudioServiceProtoDump
                    .getCarAudioZonesList();
            for (int zoneIndex = 0; zoneIndex < zoneProtoList.size(); zoneIndex++) {
                int zoneId = zoneProtoList.get(zoneIndex).getId();
                List<CarAudioZoneConfigProto> zoneConfigProtoList = zoneProtoList.get(zoneIndex)
                        .getZoneConfigsList();
                for (int configIndex = 0; configIndex < zoneConfigProtoList.size(); configIndex++) {
                    if (!zoneConfigs.contains(zoneId)) {
                        zoneConfigs.put(zoneId, new ArrayList<>());
                    }
                    int zoneConfigId = zoneConfigProtoList.get(configIndex).getId();
                    String configName = zoneConfigProtoList.get(configIndex).getName();
                    zoneConfigs.get(zoneId).add(new TestZoneConfigIdInfo(zoneId, zoneConfigId,
                            configName));
                }
            }
        } else {
            Matcher zoneConfigMatcher = ZONE_CONFIG_PATTERN.matcher(mCarAudioServiceDump);
            while (zoneConfigMatcher.find()) {
                int zoneId = Integer.parseInt(zoneConfigMatcher.group(3));
                int zoneConfigId = Integer.parseInt(zoneConfigMatcher.group(2));
                String configName = zoneConfigMatcher.group(1);
                if (!zoneConfigs.contains(zoneId)) {
                    zoneConfigs.put(zoneId, new ArrayList<>());
                }
                zoneConfigs.get(zoneId).add(new TestZoneConfigIdInfo(zoneId, zoneConfigId,
                        configName));
            }
        }
        return zoneConfigs;
    }

    private CarAudioZoneConfigInfo getNonCurrentZoneConfig(int zoneId) {
        List<CarAudioZoneConfigInfo> zoneConfigInfos =
                mCarAudioManager.getAudioZoneConfigInfos(zoneId);
        CarAudioZoneConfigInfo currentZoneConfigInfo =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(zoneId);

        CarAudioZoneConfigInfo differentZoneConfig = null;
        for (int index = 0; index < zoneConfigInfos.size(); index++) {
            if (!currentZoneConfigInfo.equals(zoneConfigInfos.get(index))) {
                differentZoneConfig = zoneConfigInfos.get(index);
                break;
            }
        }

        return differentZoneConfig;
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback"})
    public void registerCarVolumeGroupEventCallback_nullCallback_throwsNPE() {
        Executor executor = Executors.newFixedThreadPool(1);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.registerCarVolumeGroupEventCallback(executor,
                        /* callback= */ null));

        assertWithMessage("Register car volume group event with null callback exception")
                .that(exception).hasMessageThat()
                .contains("Car volume event callback can not be null");
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback"})
    public void registerCarVolumeGroupEventCallback_nullExecutor_throwsNPE() {
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.registerCarVolumeGroupEventCallback(/* executor= */ null,
                        mEventCallback));

        mEventCallback = null;
        assertWithMessage("Register car volume group event with null executor exception")
                .that(exception).hasMessageThat().contains("Executor can not be null");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback"})
    public void registerCarVolumeGroupEventCallback_volumeGroupEventsDisabled() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsDisabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();

        Exception exception = assertThrows(IllegalStateException.class,
                () -> mCarAudioManager.registerCarVolumeGroupEventCallback(executor,
                        mEventCallback));

        mEventCallback = null;
        assertWithMessage("Register car volume group event with feature disabled")
                .that(exception).hasMessageThat().contains("Car Volume Group Event is required");
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {
            "android.car.media.CarAudioManager#registerCarVolumeGroupEventCallback"})
    public void registerCarVolumeGroupEventCallback_onVolumeGroupEvent() throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();

        boolean status = mCarAudioManager.registerCarVolumeGroupEventCallback(executor,
                mEventCallback);

        injectVolumeUpKeyEvent();
        assertWithMessage("Car volume group event callback")
                .that(mEventCallback.receivedVolumeGroupEvents()).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback"})
    public void registerCarVolumeGroupEventCallback_registerCarVolumeCallback_onVolumeGroupEvent()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();
        mCarAudioManager.registerCarVolumeGroupEventCallback(executor, mEventCallback);

        mCarAudioManager.registerCarVolumeCallback(mCallback);

        injectVolumeDownKeyEvent();
        assertWithMessage("Car volume group event for registered callback")
            .that(mEventCallback.receivedVolumeGroupEvents()).isTrue();
        assertWithMessage("Car group volume changed for deprioritized callback")
            .that(mCallback.receivedGroupVolumeChanged()).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback",
            "android.car.media.CarAudioManager"
                    + "#unregisterCarVolumeGroupEventCallback"})
    public void unregisterCarVolumeGroupEventCallback_onGroupVolumeChanged()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();
        mCarAudioManager.registerCarVolumeGroupEventCallback(executor, mEventCallback);
        mCarAudioManager.registerCarVolumeCallback(mCallback);

        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback);

        injectVolumeUpKeyEvent();
        assertWithMessage("Car volume group event for unregistered callback")
            .that(mEventCallback.receivedVolumeGroupEvents()).isFalse();
        assertWithMessage("Car group volume changed for reprioritized callback")
            .that(mCallback.receivedGroupVolumeChanged()).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback",
            "android.car.media.CarAudioManager"
                    + "#unregisterCarVolumeGroupEventCallback"})
    public void reRegisterCarVolumeGroupEventCallback_eventCallbackReprioritized()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        mCallback = new CarAudioManagerTestUtils.SyncCarVolumeCallback();
        mCarAudioManager.registerCarVolumeGroupEventCallback(executor, mEventCallback);
        mCarAudioManager.registerCarVolumeCallback(mCallback);
        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback);

        mCarAudioManager.registerCarVolumeGroupEventCallback(executor, mEventCallback);

        injectVolumeDownKeyEvent();
        assertWithMessage("Car volume group event for re-registered callback")
            .that(mEventCallback.receivedVolumeGroupEvents()).isTrue();
        assertWithMessage("Car group volume changed for deprioritized callback")
            .that(mCallback.receivedGroupVolumeChanged()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#unregisterCarVolumeGroupEventCallback"})
    public void unregisterCarVolumeGroupEventCallback_nullCallback_throwsNPE() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> mCarAudioManager.unregisterCarVolumeGroupEventCallback(/* callback= */ null));

        assertWithMessage("Unregister car volume group event with null callback exception")
                .that(exception).hasMessageThat()
                .contains("Car volume event callback can not be null");
    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#unregisterCarVolumeGroupEventCallback"})
    public void unregisterCarVolumeGroupEventCallback_withoutPermission_throws()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        runWithCarControlAudioVolumePermission(
                () -> mCarAudioManager.registerCarVolumeGroupEventCallback(executor,
                        mEventCallback));

        Exception exception = assertThrows(SecurityException.class,
                () -> mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback));

        assertWithMessage("Unregister car volume group event callback without permission exception")
                .that(exception).hasMessageThat().contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);

    }

    @Test
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#unregisterCarVolumeGroupEventCallback"})
    public void unregisterCarVolumeGroupEventCallback_withoutPermission_receivesCallback()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        runWithCarControlAudioVolumePermission(
                () -> mCarAudioManager.registerCarVolumeGroupEventCallback(executor,
                        mEventCallback));

        Exception exception = assertThrows(SecurityException.class,
                () -> mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback));

        injectVolumeDownKeyEvent();
        assertWithMessage("Car volume group event after unregister security exception")
                .that(mEventCallback.receivedVolumeGroupEvents()).isTrue();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager"
            + "#registerCarVolumeGroupEventCallback",
            "android.car.media.CarAudioManager"
                    + "#unregisterCarVolumeGroupEventCallback"})
    public void unregisterCarVolumeGroupEventCallback_noLongerReceivesEventCallback()
            throws Exception {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupEventsIsEnabled();
        Executor executor = Executors.newFixedThreadPool(1);
        mEventCallback = new CarAudioManagerTestUtils.TestCarVolumeGroupEventCallback();
        mCarAudioManager.registerCarVolumeGroupEventCallback(executor, mEventCallback);

        mCarAudioManager.unregisterCarVolumeGroupEventCallback(mEventCallback);

        injectVolumeUpKeyEvent();
        assertWithMessage("Car volume group event for unregistered callback")
            .that(mEventCallback.receivedVolumeGroupEvents()).isFalse();
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#isVolumeGroupMuted",
            "android.car.media.CarVolumeGroupInfo#isMuted"})
    public void isVolumeGroupMuted() {
        assumeDynamicRoutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        CarVolumeGroupInfo volumeGroupInfo = mCarAudioManager.getVolumeGroupInfo(mZoneId,
                mVolumeGroupId);

        boolean isMuted = mCarAudioManager.isVolumeGroupMuted(mZoneId, mVolumeGroupId);

        assertWithMessage("Mute state for zone %s group %s", mZoneId, mVolumeGroupId)
                .that(isMuted).isEqualTo(volumeGroupInfo.isMuted());
    }

    @Test
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    @ApiTest(apis = {"android.car.media.CarAudioManager#setVolumeGroupMute",
            "android.car.media.CarAudioManager#isVolumeGroupMuted",
            "android.car.media.CarVolumeGroupInfo#isMutedBySystem"})
    @RequiresFlagsEnabled(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY)
    public void setVolumeGroupMute_withMuteAmbiguityFlagEnabled() {
        assumeDynamicRoutingIsEnabled();
        assumeVolumeGroupMutingIsEnabled();
        readFirstZoneAndVolumeGroup();
        boolean isMuted = mCarAudioManager.isVolumeGroupMuted(mZoneId, mVolumeGroupId);
        boolean isMutedBySystem = mCarAudioManager.getVolumeGroupInfo(mZoneId,
                mVolumeGroupId).isMutedBySystem();
        String muteOperationStr = isMuted ? "unmuting" : "muting";

        try {
            mCarAudioManager.setVolumeGroupMute(mZoneId, mVolumeGroupId, !isMuted, /* flags= */ 0);

            assertWithMessage("Mute state for zone %s group %s after %s", mZoneId, mVolumeGroupId,
                    muteOperationStr)
                    .that(mCarAudioManager.isVolumeGroupMuted(mZoneId, mVolumeGroupId))
                    .isEqualTo(mCarAudioManager.getVolumeGroupInfo(mZoneId, mVolumeGroupId)
                            .isMuted());
            assertWithMessage("Mute by system state for zone %s group %s after %s", mZoneId,
                    mVolumeGroupId, muteOperationStr)
                    .that(mCarAudioManager.getVolumeGroupInfo(mZoneId, mVolumeGroupId)
                            .isMutedBySystem()).isEqualTo(isMutedBySystem);
        } finally {
            mCarAudioManager.setVolumeGroupMute(mZoneId, mVolumeGroupId, isMuted, /* flags= */ 0);
        }
    }

    private int getNumberOfPrimaryZoneAudioMediaCallbacks() {
        if (Flags.carDumpToProto()) {
            return mCarAudioServiceProtoDump.getMediaRequestHandler().hasMediaRequestCallbackCount()
                    ? mCarAudioServiceProtoDump.getMediaRequestHandler()
                    .getMediaRequestCallbackCount() : 0;
        }
        Matcher matchCount = PRIMARY_ZONE_MEDIA_REQUEST_APPROVERS_PATTERN
                .matcher(mCarAudioServiceDump);
        assertWithMessage("No Car Audio Media in dump").that(matchCount.find()).isTrue();
        return Integer.parseInt(matchCount.group(1));
    }

    private void assumeNoPrimaryZoneAudioMediaApprovers() {
        assumeTrue("Primary zone audio media approvers must be empty",
                getNumberOfPrimaryZoneAudioMediaCallbacks() == 0);
    }

    private void setUpDefaultCarAudioConfigurationForZone(int zoneId) throws Exception {
        CarAudioZoneConfigInfo current =
                mCarAudioManager.getCurrentAudioZoneConfigInfo(zoneId);
        if (current != null && current.isDefault()) {
            return;
        }

        CarAudioZoneConfigInfo defaultConfig =
                mCarAudioManager.getAudioZoneConfigInfos(zoneId).stream()
                        .filter(CarAudioZoneConfigInfo::isDefault).findFirst().orElseThrow();
        Executor callbackExecutor = Executors.newFixedThreadPool(1);
        TestSwitchAudioZoneConfigCallback callback = new TestSwitchAudioZoneConfigCallback();
        mCarAudioManager.switchAudioZoneToConfig(defaultConfig, callbackExecutor, callback);
        callback.receivedApproval();
    }

    private void assumeDynamicRoutingIsEnabled() {
        assumeTrue("Requires dynamic audio routing",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));
    }

    private void assumeDynamicRoutingIsDisabled() {
        assumeFalse("Requires dynamic audio routing",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));
    }

    private void assumeVolumeGroupMutingIsEnabled() {
        assumeTrue("Requires volume group muting",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING));
    }

    private void assumeVolumeGroupMutingIsDisabled() {
        assumeFalse("Requires volume group muting disabled",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING));
    }

    private void assumeAudioMirrorEnabled() {
        assumeTrue("Requires audio mirroring",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_AUDIO_MIRRORING));
    }

    private void assumeVolumeGroupEventsIsEnabled() {
        assumeTrue(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_EVENTS));
    }

    private void assumeVolumeGroupEventsIsDisabled() {
        assumeFalse(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_EVENTS));
    }

    private void runWithCarControlAudioVolumePermission(Runnable runnable) {
        UI_AUTOMATION.adoptShellPermissionIdentity(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
        try {
            runnable.run();
        } finally {
            UI_AUTOMATION.dropShellPermissionIdentity();
        }
    }

    private void injectKeyEvent(int keyCode) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent volumeDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
            keyCode, 0);
        UI_AUTOMATION.injectInputEvent(volumeDown, true);
    }

    private void injectVolumeDownKeyEvent() {
        injectKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN);
    }

    private void injectVolumeUpKeyEvent() {
        injectKeyEvent(KeyEvent.KEYCODE_VOLUME_UP);
    }

    private void injectVolumeMuteKeyEvent() {
        injectKeyEvent(KeyEvent.KEYCODE_VOLUME_MUTE);
    }

    private void readFirstZoneAndVolumeGroup() {
        if (Flags.carDumpToProto()) {
            List<CarAudioZoneProto> zoneProtoList = mCarAudioServiceProtoDump
                    .getCarAudioZonesList();
            assertWithMessage("No CarAudioZone in proto dump").that(zoneProtoList)
                    .isNotEmpty();
            mZoneId = zoneProtoList.get(0).getId();
            readFirstVolumeGroupAndVolumeGroupCountFromProto(zoneProtoList.get(0));
            return;
        }
        Matcher matchZone = ZONE_PATTERN.matcher(mCarAudioServiceDump);
        assertWithMessage("No CarAudioZone in dump").that(matchZone.find()).isTrue();
        mZoneId = Integer.parseInt(matchZone.group(1));
        mConfigId = Integer.parseInt(matchZone.group(3));
        readFirstVolumeGroupAndVolumeGroupCount(mZoneId, mConfigId);
    }

    private void assumePrimaryZone() {
        if (Flags.carDumpToProto()) {
            List<CarAudioZoneProto> zoneProtoList = mCarAudioServiceProtoDump
                    .getCarAudioZonesList();
            for (int zoneIndex = 0; zoneIndex < zoneProtoList.size(); zoneIndex++) {
                int zoneId = zoneProtoList.get(zoneIndex).getId();
                if (zoneId == PRIMARY_AUDIO_ZONE) {
                    mZoneId = zoneId;
                    readFirstVolumeGroupAndVolumeGroupCountFromProto(zoneProtoList.get(zoneIndex));
                    break;
                }
            }
        } else {
            Matcher matchZone = ZONE_PATTERN.matcher(mCarAudioServiceDump);
            while (matchZone.find()) {
                if (Integer.parseInt(matchZone.group(1)) == PRIMARY_AUDIO_ZONE) {
                    mZoneId = PRIMARY_AUDIO_ZONE;
                    mConfigId = Integer.parseInt(matchZone.group(3));
                    readFirstVolumeGroupAndVolumeGroupCount(mZoneId, mConfigId);
                    break;
                }
            }
        }
        assumeTrue("Primary zone exists", mZoneId == PRIMARY_AUDIO_ZONE);
    }

    private void readFirstVolumeGroupAndVolumeGroupCount(int zoneId, int currentConfigId) {
        Matcher matchGroup = VOLUME_GROUP_PATTERN.matcher(mCarAudioServiceDump);
        boolean findVolumeGroup = false;
        mVolumeGroupCount = 0;
        while (matchGroup.find()) {
            if (Integer.parseInt(matchGroup.group(3)) == zoneId
                    && Integer.parseInt(matchGroup.group(4)) == currentConfigId) {
                if (!findVolumeGroup) {
                    mVolumeGroupId = Integer.parseInt(matchGroup.group(1));
                    findVolumeGroup = true;
                }
                mVolumeGroupCount++;
            }
        }
        assertWithMessage("No CarVolumeGroup in dump").that(findVolumeGroup).isTrue();
    }

    private void readFirstVolumeGroupAndVolumeGroupCountFromProto(CarAudioZoneProto zoneProto) {
        mConfigId = zoneProto.getCurrentZoneConfigId();
        List<CarAudioZoneConfigProto> zoneConfigProtoList = zoneProto.getZoneConfigsList();
        for (int configIndex = 0; configIndex < zoneConfigProtoList.size(); configIndex++) {
            if (zoneConfigProtoList.get(configIndex).getId() != mConfigId) {
                continue;
            }
            List<CarVolumeGroupProto> volumeGroupProtoList = zoneConfigProtoList
                    .get(configIndex).getVolumeGroupsList();
            assertWithMessage("No CarVolumeGroup in dump").that(volumeGroupProtoList)
                    .isNotEmpty();
            mVolumeGroupCount = volumeGroupProtoList.size();
            mVolumeGroupId = volumeGroupProtoList.get(0).getId();
        }
    }

    private void setVolumeGroupMute(int zoneId, int groupId, boolean mute) {
        ShellUtils.runShellCommand("cmd car_service set-mute-car-volume-group %d %d %s",
                zoneId, groupId, mute ? "mute" : "unmute");
    }

    private void assertVolumeGroupIdForUsages(int zoneId, int volumeGroupId, int[] usages,
            String message) {
        assertWithMessage("Usage for volume group %s in zone %s %s", volumeGroupId, zoneId,
                message).that(usages).isNotEmpty();
        for (int i = 0; i < usages.length; i++) {
            assertWithMessage("Usage %s in volume group %s in zone %s %s", usages[i], volumeGroupId,
                    zoneId, message).that(mCarAudioManager.getVolumeGroupIdForUsage(zoneId,
                    usages[i])).isEqualTo(volumeGroupId);
        }
    }

    private static final class TestZoneConfigIdInfo {
        private final int mZoneId;
        private final int mConfigId;
        private final String mConfigName;

        TestZoneConfigIdInfo(int zoneId, int configId, String configName) {
            mZoneId = zoneId;
            mConfigId = configId;
            mConfigName = configName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof TestZoneConfigIdInfo)) {
                return false;
            }

            TestZoneConfigIdInfo that = (TestZoneConfigIdInfo) o;

            return mZoneId == that.mZoneId && mConfigId == that.mConfigId
                    && mConfigName.equals(that.mConfigName);
        }

        static TestZoneConfigIdInfo getZoneConfigFromInfo(CarAudioZoneConfigInfo configInfo) {
            return new TestZoneConfigIdInfo(configInfo.getZoneId(), configInfo.getConfigId(),
                    configInfo.getName());
        }

        static List<TestZoneConfigIdInfo> getZoneConfigListFromInfoList(
                List<CarAudioZoneConfigInfo> configInfo) {
            List<TestZoneConfigIdInfo> zoneConfigs = new ArrayList<>(configInfo.size());
            Log.e(TAG, "getZoneConfigListFromInfoList " + configInfo.size());
            for (int index = 0; index < configInfo.size(); index++) {
                zoneConfigs.add(getZoneConfigFromInfo(configInfo.get(index)));
            }
            return zoneConfigs;
        }
    }

    private static final class TestPrimaryZoneMediaAudioRequestStatusCallback implements
            PrimaryZoneMediaAudioRequestCallback {

        private final CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        private long mRequestId;
        private OccupantZoneInfo mOccupantZoneInfo;

        @Override
        public void onRequestMediaOnPrimaryZone(OccupantZoneInfo info, long requestId) {
            mOccupantZoneInfo = info;
            mRequestId = requestId;
            Log.i(TAG, "onRequestMediaOnPrimaryZone info " + info + " request id " + requestId);
            mRequestAudioLatch.countDown();
        }

        @Override
        public void onMediaAudioRequestStatusChanged(OccupantZoneInfo info,
                long requestId, int status) {
        }

        void receivedMediaRequest() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }
    }

    private static final class TestMediaAudioRequestStatusCallback implements
            MediaAudioRequestStatusCallback {

        private CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        private long mRequestId;
        private OccupantZoneInfo mOccupantZoneInfo;
        private int mStatus;

        void receivedApproval() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }

        void reset() {
            mRequestAudioLatch = new CountDownLatch(1);
            mRequestId = INVALID_REQUEST_ID;
            mOccupantZoneInfo = null;
            mStatus = 0;
        }

        @Override
        public void onMediaAudioRequestStatusChanged(OccupantZoneInfo info,
                long requestId, int status) {
            mOccupantZoneInfo = info;
            mRequestId = requestId;
            mStatus = status;
            Log.i(TAG, "onMediaAudioRequestStatusChanged info " + info + " request id "
                    + requestId + " status " + status);
            mRequestAudioLatch.countDown();
        }
    }

    private static final class TestSwitchAudioZoneConfigCallback implements
            SwitchAudioZoneConfigCallback {

        private CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        private CarAudioZoneConfigInfo mZoneConfigInfo;
        private boolean mIsSuccessful;

        void receivedApproval() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }

        void reset() {
            mRequestAudioLatch = new CountDownLatch(1);
            mZoneConfigInfo = null;
            mIsSuccessful = false;
        }

        @Override
        public void onAudioZoneConfigSwitched(CarAudioZoneConfigInfo zoneConfigInfo,
                boolean isSuccessful) {
            mZoneConfigInfo = zoneConfigInfo;
            mIsSuccessful = isSuccessful;
            Log.i(TAG, "onAudioZoneConfigSwitched zoneConfig " + zoneConfigInfo + " is successful? "
                    + isSuccessful);
            mRequestAudioLatch.countDown();
        }
    }

    private static final class TestAudioZonesMirrorStatusCallback implements
            AudioZonesMirrorStatusCallback {

        private CountDownLatch mRequestAudioLatch = new CountDownLatch(1);
        public List<Integer> mAudioZones;
        public int mStatus;

        @Override
        public void onAudioZonesMirrorStatusChanged(List<Integer> mirroredAudioZones, int status) {
            mAudioZones = mirroredAudioZones;
            mStatus = status;
            Log.i(TAG, "onAudioZonesMirrorStatusChanged: audio zones " + mirroredAudioZones
                    + " status " + status);
            mRequestAudioLatch.countDown();
        }

        private void waitForCallback() throws InterruptedException {
            await(mRequestAudioLatch, WAIT_TIMEOUT_MS);
        }

        public void reset() {
            mRequestAudioLatch = new CountDownLatch(1);
        }
    }

}
