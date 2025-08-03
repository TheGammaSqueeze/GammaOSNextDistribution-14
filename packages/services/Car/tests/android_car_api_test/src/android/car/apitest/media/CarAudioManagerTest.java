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

package android.car.apitest.media;

import static android.car.Car.AUDIO_SERVICE;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING;
import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.car.apitest.CarApiTestBase;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioDeviceInfo;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class CarAudioManagerTest extends CarApiTestBase {

    private static final String TAG  = CarAudioManagerTest.class.getSimpleName();

    private static final int TEST_FLAGS = 0;

    private CarAudioManager mCarAudioManager;

    @Before
    public void setUp() throws Exception {
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(AUDIO_SERVICE);
        assertThat(mCarAudioManager).isNotNull();
    }

    @Test
    public void getVolumeGroupInfo() {
        assumeDynamicRoutingIsEnabled();

        int groupCount = mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE);

        for (int index = 0; index < groupCount; index++) {
            CarVolumeGroupInfo info =
                    mCarAudioManager.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, index);
            expectWithMessage("Car volume group info id for group %s", index)
                    .that(info.getId()).isEqualTo(index);
            expectWithMessage("Car volume group info zone for group %s", index)
                    .that(info.getZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
        }
    }

    @Test
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
            expectWithMessage("Car volume group infos for primary zone and group %s", index)
                    .that(infos).contains(info);
        }
    }

    @Test
    public void test_getAudioZoneIds() throws Exception {
        assumeDynamicRoutingIsEnabled();

        List<Integer> zoneIds = mCarAudioManager.getAudioZoneIds();
        assertThat(zoneIds).isNotEmpty();
        assertThat(zoneIds).contains(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void test_isAudioFeatureEnabled() throws Exception {
        // nothing to assert. Just call the API.
        mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING);
        mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING);
    }

    @Test
    public void test_getVolumeGroupCount() throws Exception {
        int primaryZoneCount = mCarAudioManager.getVolumeGroupCount();
        assertThat(
                mCarAudioManager.getVolumeGroupCount(PRIMARY_AUDIO_ZONE)).isEqualTo(
                primaryZoneCount);
    }

    @Test
    public void test_getGroupVolume() throws Exception {
        int groudId = 0;
        int volume = mCarAudioManager.getGroupVolume(groudId);
        assertThat(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groudId))
                .isEqualTo(volume);
        int maxVolume = mCarAudioManager.getGroupMaxVolume(groudId);
        assertThat(mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, groudId))
                .isEqualTo(maxVolume);
        int minVolume = mCarAudioManager.getGroupMinVolume(groudId);
        assertThat(mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, groudId))
                .isEqualTo(minVolume);
        assertThat(volume).isAtLeast(minVolume);
        assertThat(volume).isAtMost(maxVolume);
    }

    @Test
    public void test_setGroupVolume() throws Exception {
        int groudId = 0;
        int volume = mCarAudioManager.getGroupVolume(groudId);
        mCarAudioManager.setGroupVolume(groudId, volume, TEST_FLAGS);
        mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groudId, volume, TEST_FLAGS);
        assertThat(mCarAudioManager.getGroupVolume(groudId)).isEqualTo(volume);
    }

    @Test
    public void test_getInputDevicesForZoneId() throws Exception {
        assumeDynamicRoutingIsEnabled();

        List<AudioDeviceInfo> info = mCarAudioManager.getInputDevicesForZoneId(PRIMARY_AUDIO_ZONE);
        assertThat(info).isNotEmpty();
    }

    @Test
    public void test_getOutputDeviceForUsage() throws Exception {
        assumeDynamicRoutingIsEnabled();

        AudioDeviceInfo device = mCarAudioManager.getOutputDeviceForUsage(
                PRIMARY_AUDIO_ZONE, USAGE_MEDIA);
        assertThat(device).isNotNull();
    }

    @Test
    public void test_getVolumeGroupIdForUsage() throws Exception {
        int groupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);
        assertThat(mCarAudioManager.getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, USAGE_MEDIA))
                .isEqualTo(groupId);
        int primaryZoneCount = mCarAudioManager.getVolumeGroupCount();
        assertThat(groupId).isLessThan(primaryZoneCount);
    }

    @Test
    public void test_getZoneIdForUid() throws Exception {
        assumeDynamicRoutingIsEnabled();

        assertThat(mCarAudioManager.getZoneIdForUid(Process.myUid())).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void setVolumeGroupMute_toMute_mutesVolumeGroup() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = 0;
        boolean  muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);

        try {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, true, TEST_FLAGS);
            assertThat(mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId))
                    .isEqualTo(true);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
        }
    }

    @Test
    public void setVolumeGroupMute_toUnMute_unMutesVolumeGroup() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = 0;
        boolean  muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);

        try {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, false, TEST_FLAGS);
            assertThat(mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId))
                    .isEqualTo(false);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
        }
    }

    @Test
    public void setGroupVolume_whileMuted_unMutesVolumeGroup() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);
        int minVolume = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, groupId);
        assumeTrue("Mutable group shall have zero min index", minVolume == 0);
        boolean muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);
        int volume = mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groupId);

        try {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, /* mute= */ true,
                    TEST_FLAGS);
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, minVolume, TEST_FLAGS);

            assertWithMessage("Un-muted volume group for group %s in zone %s", groupId,
                    PRIMARY_AUDIO_ZONE).that(mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE,
                    groupId)).isEqualTo(false);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, volume, TEST_FLAGS);
        }
    }

    @Test
    public void setGroupVolumeToZero_doesNotMuteVolumeGroup() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = mCarAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);
        int minVolume = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, groupId);
        assumeTrue("Mutable group shall have zero min index", minVolume == 0);
        boolean muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);
        int volume = mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groupId);

        try {
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, minVolume, TEST_FLAGS);

            assertThat(mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId))
                    .isEqualTo(false);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, volume, TEST_FLAGS);
        }
    }

    @Test
    public void getGroupVolume_whileMuted_returnsMinVolume() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = 0;
        boolean  muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);
        int minVolume = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, groupId);

        try {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, true, TEST_FLAGS);

            assertThat(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groupId))
                    .isEqualTo(minVolume);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
        }
    }

    @Test
    public void getGroupVolume_whileUnMuted_returnLastSetValue() throws Exception {
        assumeVolumeGroupMutingIsEnabled();
        int groupId = 0;
        boolean  muteState = mCarAudioManager.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, groupId);
        int volume = mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groupId);
        int minVolume = mCarAudioManager.getGroupMinVolume(PRIMARY_AUDIO_ZONE, groupId);
        int maxVolume = mCarAudioManager.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, groupId);
        int testVolume = (minVolume + maxVolume) / 2;

        try {
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, testVolume, TEST_FLAGS);

            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, true, TEST_FLAGS);

            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, false, TEST_FLAGS);

            assertThat(mCarAudioManager.getGroupVolume(PRIMARY_AUDIO_ZONE, groupId))
                    .isEqualTo(testVolume);
        } finally {
            mCarAudioManager.setVolumeGroupMute(PRIMARY_AUDIO_ZONE, groupId, muteState, TEST_FLAGS);
            mCarAudioManager.setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, volume, TEST_FLAGS);
        }
    }

    @Test
    public void test_isPlaybackOnVolumeGroupActive() throws Exception {
        assumeDynamicRoutingIsEnabled();

        // TODO(b/191660867): Better to change this to play something and asert true.
        assertThat(
                mCarAudioManager.isPlaybackOnVolumeGroupActive(PRIMARY_AUDIO_ZONE,
                        0)).isFalse();
    }

    private void assumeDynamicRoutingIsEnabled() {
        assumeTrue(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));
    }
    private void assumeVolumeGroupMutingIsEnabled() {
        assumeTrue(mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_VOLUME_GROUP_MUTING));
    }
}
