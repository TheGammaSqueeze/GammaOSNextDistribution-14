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

package android.car.media;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.junit.Assert.assertThrows;

import android.car.feature.Flags;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.os.Parcel;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public final class CarVolumeGroupInfoUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_ZONE_ID = 8;
    private static final int TEST_PRIMARY_GROUP_ID = 7;
    private static final String TEST_GROUP_NAME = "3";
    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int TEST_CURRENT_GAIN = 9_000;
    private static final boolean TEST_DEFAULT_MUTE_STATE = false;
    private static final boolean TEST_DEFAULT_BLOCKED_STATE = false;
    private static final boolean TEST_DEFAULT_ATTENUATED_STATE = false;
    private static final int TEST_MAX_GAIN_INDEX = 9_005;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 8_005;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 1_000;
    private static final boolean TEST_MUTE_BY_SYSTEM_STATE = true;
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAVIGATION_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();
    private static final AudioAttributes TEST_ASSISTANT_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANT).build();
    private static final List<AudioAttributes> TEST_AUDIO_ATTRIBUTES = List.of(
            TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NAVIGATION_AUDIO_ATTRIBUTE,
            TEST_ASSISTANT_AUDIO_ATTRIBUTE);

    private static final CarVolumeGroupInfo TEST_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();
    private static final AudioDeviceAttributes TEST_AUDIO_DEVICE_ATTRIBUTE =
            new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "a2dp");

    private final CarVolumeGroupInfo.Builder mTestGroupInfoBuilder =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME,
            TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID).setAttenuated(TEST_DEFAULT_ATTENUATED_STATE)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setVolumeGainIndex(TEST_CURRENT_GAIN).setBlocked(TEST_DEFAULT_BLOCKED_STATE)
                    .setMuted(TEST_DEFAULT_MUTE_STATE)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void build_buildsGroupInfo() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume info build info zone id")
                .that(info.getZoneId()).isEqualTo(TEST_ZONE_ID);
        expectWithMessage("Car volume info build info group id")
                .that(info.getId()).isEqualTo(TEST_PRIMARY_GROUP_ID);
        expectWithMessage("Car volume info build info group name")
                .that(info.getName()).isEqualTo(TEST_GROUP_NAME);
    }

    @Test
    public void build_buildsGroupInfo_withoutAudioDevices_succeeds() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume group id, for group built without audio devices")
                .that(info.getId()).isEqualTo(TEST_PRIMARY_GROUP_ID);
    }

    @Test
    public void build_buildsGroupInfo_withAudioDevices_succeeds() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setAudioDeviceAttributes(List.of(TEST_AUDIO_DEVICE_ATTRIBUTE))
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume group info devices")
                .that(info.getAudioDeviceAttributes()).containsExactly(TEST_AUDIO_DEVICE_ATTRIBUTE);
    }

    @Test
    public void build_buildsGroupInfo_withNullAudioDevices_fails() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                        TEST_PRIMARY_GROUP_ID).setAudioDeviceAttributes(null)
        );

        expectWithMessage("Null audio devices exception")
                .that(thrown).hasMessageThat().contains("Audio Device Attributes");
    }

    @Test
    public void build_buildsGroupInfo_withMinMaxActivationVolume_succeeds() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);

        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume group info max activation volume")
                .that(info.getMaxActivationVolumeGainIndex())
                .isEqualTo(TEST_MAX_ACTIVATION_GAIN_INDEX);
        expectWithMessage("Car volume group info min activation volume")
                .that(info.getMinActivationVolumeGainIndex())
                .isEqualTo(TEST_MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void build_buildsGroupInfo_withMinActivationVolumeOutOfMinMaxRange_fails() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroupInfo.Builder infoBuilder = mTestGroupInfoBuilder
                .setMinActivationVolumeGainIndex(TEST_MIN_GAIN_INDEX - 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                infoBuilder::build);

        expectWithMessage("Min activation volume out of range exception")
                .that(thrown).hasMessageThat().contains("Min activation volume gain index");
    }

    @Test
    public void build_buildsGroupInfo_withMaxActivationVolumeOutOfMinMaxRange_fails() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroupInfo.Builder infoBuilder = mTestGroupInfoBuilder
                .setMaxActivationVolumeGainIndex(TEST_MAX_GAIN_INDEX + 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                infoBuilder::build);

        expectWithMessage("Max activation volume out of range exception")
                .that(thrown).hasMessageThat().contains("Max activation volume gain index");
    }

    @Test
    public void build_buildsGroupInfo_withMinLargerThanMaxActivationVolume_fails() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroupInfo.Builder infoBuilder = mTestGroupInfoBuilder
                .setMinActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX - 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                infoBuilder::build);

        expectWithMessage("Min activation volume gain larger than max activation exception")
                .that(thrown).hasMessageThat().contains("must be smaller than max activation");
    }

    @Test
    public void build_buildsGroupInfo_withMuteBySystem_succeeds() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);

        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setAudioDeviceAttributes(List.of(TEST_AUDIO_DEVICE_ATTRIBUTE))
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                .setMutedBySystem(TEST_MUTE_BY_SYSTEM_STATE).build();

        expectWithMessage("Car volume group info system mute state")
                .that(info.isMutedBySystem()).isEqualTo(TEST_MUTE_BY_SYSTEM_STATE);
    }

    @Test
    public void build_withNullName_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarVolumeGroupInfo.Builder(/* name= */ null,
                        TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
        );

        expectWithMessage("Null volume info name exception")
                .that(thrown).hasMessageThat().contains("Volume info name");
    }

    @Test
    public void setVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setVolumeGainIndex(9_001).build();

        expectWithMessage("Car volume info gain")
                .that(info.getVolumeGainIndex()).isEqualTo(9_001);
    }

    @Test
    public void setMinVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMinVolumeGainIndex(10).build();

        expectWithMessage("Car volume info min gain")
                .that(info.getMinVolumeGainIndex()).isEqualTo(10);
    }

    @Test
    public void setMaxVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMaxVolumeGainIndex(9_002).build();

        expectWithMessage("Car volume info max gain")
                .that(info.getMaxVolumeGainIndex()).isEqualTo(9_002);
    }

    @Test
    public void setMaxVolumeGainIndex_withMinLargerThanMax_buildFails() {
        CarVolumeGroupInfo.Builder infoBuilder =
                mTestGroupInfoBuilder.setMinVolumeGainIndex(9003)
                        .setMaxVolumeGainIndex(9_002);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> infoBuilder.build());

        expectWithMessage("Max volume gain smaller than min gain exception")
                .that(thrown).hasMessageThat().contains("must be smaller than max");
    }

    @Test
    public void setVolumeGainIndex_withGainOutOfMinMaxRange_buildFails() {
        CarVolumeGroupInfo.Builder infoBuilder =
                mTestGroupInfoBuilder.setMinVolumeGainIndex(10)
                        .setMaxVolumeGainIndex(100).setVolumeGainIndex(0);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> infoBuilder.build());

        expectWithMessage("Volume gain index out of range exception")
                .that(thrown).hasMessageThat().contains("out of range");
    }

    @Test
    public void setMuted_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMuted(true).build();

        expectWithMessage("Car volume info mute state")
                .that(info.isMuted()).isTrue();
    }

    @Test
    public void setAttenuated_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setAttenuated(true).build();

        expectWithMessage("Car volume info attenuated state")
                .that(info.isAttenuated()).isTrue();
    }

    @Test
    public void setBlocked_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setBlocked(true).build();

        expectWithMessage("Car volume info blocked state")
                .that(info.isBlocked()).isTrue();
    }

    @Test
    public void setAudioAttribute_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .build();

        expectWithMessage("Audio attributes").that(info.getAudioAttributes())
                .containsExactlyElementsIn(TEST_AUDIO_ATTRIBUTES);
    }
    @Test
    public void setAudioAttribute_withNull_buildFails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mTestGroupInfoBuilder.setAudioAttributes(null));

        expectWithMessage("Null audio attributes exception")
                .that(thrown).hasMessageThat().contains("Audio Attributes");
    }

    @Test
    public void builder_withReuse_fails() {
        CarVolumeGroupInfo.Builder builder = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME,
                TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void writeToParcel_withAllFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        Parcel parcel = Parcel.obtain();

        TEST_VOLUME_INFO.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Car volume info from parcel with all flags disabled")
                .that(new CarVolumeGroupInfo(parcel)).isEqualTo(TEST_VOLUME_INFO);
    }

    @Test
    public void writeToParcel_withAllFlagsEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroupInfo volumeGroupInfo = new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setAudioDeviceAttributes(List.of(TEST_AUDIO_DEVICE_ATTRIBUTE))
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                .setMutedBySystem(TEST_MUTE_BY_SYSTEM_STATE).build();
        Parcel parcel = Parcel.obtain();

        volumeGroupInfo.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Car volume info from parcel with all flags enabled")
                .that(new CarVolumeGroupInfo(parcel)).isEqualTo(volumeGroupInfo);
    }

    @Test
    public void createFromParcel_withAllFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        Parcel parcel = Parcel.obtain();
        TEST_VOLUME_INFO.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Car volume info created from parcel with all flags disabled")
                .that(CarVolumeGroupInfo.CREATOR.createFromParcel(parcel))
                .isEqualTo(TEST_VOLUME_INFO);
    }

    @Test
    public void createFromParcel_withAllFlagsEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroupInfo volumeGroupInfo = new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setAudioDeviceAttributes(List.of(TEST_AUDIO_DEVICE_ATTRIBUTE))
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                .setMutedBySystem(TEST_MUTE_BY_SYSTEM_STATE).build();
        Parcel parcel = Parcel.obtain();
        volumeGroupInfo.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Car volume info created from parcel with all flags enabled")
                .that(CarVolumeGroupInfo.CREATOR.createFromParcel(parcel))
                .isEqualTo(volumeGroupInfo);
    }

    @Test
    public void newArray() {
        CarVolumeGroupInfo[] infos = CarVolumeGroupInfo.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Car volume infos size").that(infos)
                .hasLength(3);
    }

    @Test
    public void equals_forSameContent() {
        CarVolumeGroupInfo infoWithSameContent =
                new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                        .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                        .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                        .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                        .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                        .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume info with same content")
                .that(infoWithSameContent).isEqualTo(TEST_VOLUME_INFO);
    }

    @Test
    public void equals_forNull() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume info null content")
                .that(info.equals(null)).isFalse();
    }

    @Test
    public void describeContents() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume info contents")
                .that(info.describeContents()).isEqualTo(0);
    }

    @Test
    public void hashCode_forSameContent_forAllFlagsDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroupInfo infoWithSameContent = new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        expectWithMessage("Car volume info hash with same content")
                .that(infoWithSameContent.hashCode()).isEqualTo(TEST_VOLUME_INFO.hashCode());
    }

    @Test
    public void hashCode_forSameContent_forAllFlagsEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroupInfo originalInfo = new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setAudioDeviceAttributes(List.of(TEST_AUDIO_DEVICE_ATTRIBUTE))
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                .setMutedBySystem(TEST_MUTE_BY_SYSTEM_STATE).build();

        CarVolumeGroupInfo infoWithSameContent =
                new CarVolumeGroupInfo.Builder(originalInfo).build();

        expectWithMessage("Car volume info hash with same content, with all flags enabled")
                .that(infoWithSameContent.hashCode()).isEqualTo(originalInfo.hashCode());
    }

    @Test
    public void toString_forContent() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN)
                .setAudioAttributes(TEST_AUDIO_ATTRIBUTES)
                .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

        String carVolumeGroupInfoString = info.toString();

        expectWithMessage("Car volume info name")
                .that(carVolumeGroupInfoString).contains(TEST_GROUP_NAME);
        expectWithMessage("Car volume info group id")
                .that(carVolumeGroupInfoString).contains(Integer.toString(TEST_PRIMARY_GROUP_ID));
        expectWithMessage("Car volume info group zone")
                .that(carVolumeGroupInfoString).contains(Integer.toString(TEST_ZONE_ID));
        expectWithMessage("Car volume group audio attributes")
                .that(carVolumeGroupInfoString).contains(TEST_ASSISTANT_AUDIO_ATTRIBUTE.toString());
    }
}
