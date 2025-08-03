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

package android.car.media;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.junit.Assert.assertThrows;

import android.car.feature.Flags;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.os.Parcel;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public final class CarAudioZoneConfigInfoUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int TEST_ZONE_ID = 1;
    private static final int TEST_CONFIG_ID = 0;
    private static final int TEST_CONFIG_ID_2 = 1;
    private static final String TEST_CONFIG_NAME = "zone 1 config 0";

    private static final int TEST_PRIMARY_GROUP_ID = 7;
    private static final String TEST_GROUP_NAME = "3";
    private static final int TEST_MAX_GAIN_INDEX = 9_005;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 8_005;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 1_000;

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
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX)
                    .setAudioAttributes(TEST_AUDIO_ATTRIBUTES).build();
    private static final boolean TEST_ACTIVE_STATUS = true;
    private static final boolean TEST_SELECTED_STATUS = false;
    private static final boolean TEST_DEFAULT_STATUS = true;
    private static final CarAudioZoneConfigInfo TEST_ZONE_CONFIG_INFO =
            new CarAudioZoneConfigInfo(TEST_CONFIG_NAME, List.of(TEST_VOLUME_INFO),
                    TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS, TEST_SELECTED_STATUS,
                    TEST_DEFAULT_STATUS);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void constructor_withNullName_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarAudioZoneConfigInfo(/* name= */ null, TEST_ZONE_ID, TEST_CONFIG_ID)
        );

        expectWithMessage("Null zone configuration info name exception")
                .that(thrown).hasMessageThat().contains("Zone configuration name");
    }

    @Test
    public void constructor_withNullVolumeGroups_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarAudioZoneConfigInfo(TEST_CONFIG_NAME, /* groups= */ null , TEST_ZONE_ID,
                        TEST_CONFIG_ID, /* isActive= */ true, /* isSelected= */ false,
                        /* isDefault= */ false)
        );

        expectWithMessage("Null zone configuration info volumes exception")
                .that(thrown).hasMessageThat().contains("Zone configuration volume groups");
    }

    @Test
    public void getZoneId() {
        expectWithMessage("Zone id of zone configuration info")
                .that(TEST_ZONE_CONFIG_INFO.getZoneId()).isEqualTo(TEST_ZONE_ID);
    }

    @Test
    public void getConfigId() {
        expectWithMessage("Config id of zone configuration info")
                .that(TEST_ZONE_CONFIG_INFO.getConfigId()).isEqualTo(TEST_CONFIG_ID);
    }

    @Test
    public void getName() {
        expectWithMessage("Config name of zone configuration info")
                .that(TEST_ZONE_CONFIG_INFO.getName()).isEqualTo(TEST_CONFIG_NAME);
    }

    @Test
    public void isActive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        expectWithMessage("Config active status")
                .that(TEST_ZONE_CONFIG_INFO.isActive()).isEqualTo(TEST_ACTIVE_STATUS);
    }

    @Test
    public void isSelected() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        expectWithMessage("Config selected status")
                .that(TEST_ZONE_CONFIG_INFO.isSelected()).isEqualTo(TEST_SELECTED_STATUS);
    }

    @Test
    public void isDefault() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        expectWithMessage("Config default indicator").that(TEST_ZONE_CONFIG_INFO.isDefault())
                .isEqualTo(TEST_DEFAULT_STATUS);
    }

    @Test
    public void getConfigVolumeGroups() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);

        expectWithMessage("Config volume groups").that(
                TEST_ZONE_CONFIG_INFO.getConfigVolumeGroups()).containsExactly(TEST_VOLUME_INFO);
    }

    @Test
    public void writeToParcel_createFromParcel() {
        Parcel parcel = Parcel.obtain();

        TEST_ZONE_CONFIG_INFO.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Zone configuration info created from parcel")
                .that(CarAudioZoneConfigInfo.CREATOR.createFromParcel(parcel))
                .isEqualTo(TEST_ZONE_CONFIG_INFO);
    }

    @Test
    public void newArray() {
        CarAudioZoneConfigInfo[] infos = CarAudioZoneConfigInfo.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Zone configuration infos size").that(infos).hasLength(3);
    }

    @Test
    public void equals_forSameContent() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                TEST_ZONE_ID, TEST_CONFIG_ID);

        expectWithMessage("Zone configuration info with same content")
                .that(infoWithSameContent).isEqualTo(TEST_ZONE_CONFIG_INFO);
    }

    @Test
    public void equals_forSameContent_withDynamicFlagEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                List.of(TEST_VOLUME_INFO), TEST_ZONE_ID, TEST_CONFIG_ID, TEST_ACTIVE_STATUS,
                TEST_SELECTED_STATUS, TEST_DEFAULT_STATUS);

        expectWithMessage("Zone config info with same content and dynamic flags enabled")
                .that(infoWithSameContent).isEqualTo(TEST_ZONE_CONFIG_INFO);
    }

    @Test
    public void equals_forDifferentContent_withDynamicFlagEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                List.of(TEST_VOLUME_INFO), TEST_ZONE_ID, TEST_CONFIG_ID_2, TEST_ACTIVE_STATUS,
                TEST_SELECTED_STATUS, TEST_DEFAULT_STATUS);

        expectWithMessage("Zone config info with same content and dynamic flags enabled")
                .that(infoWithSameContent).isNotEqualTo(TEST_ZONE_CONFIG_INFO);
    }

    @Test
    public void equals_forNull() {
        expectWithMessage("Zone configuration info null content")
                .that(TEST_ZONE_CONFIG_INFO.equals(null)).isFalse();
    }

    @Test
    public void hasSameConfigInfo_forSameContent() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                TEST_ZONE_ID, TEST_CONFIG_ID);

        expectWithMessage("Zone configuration with same info")
                .that(infoWithSameContent.hasSameConfigInfo(TEST_ZONE_CONFIG_INFO)).isTrue();
    }

    @Test
    public void hasSameConfigInfo_withNullInfo() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                TEST_ZONE_ID, TEST_CONFIG_ID);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> infoWithSameContent.hasSameConfigInfo(null));

        expectWithMessage("Zone configuration exception")
                .that(thrown).hasMessageThat().contains("Car audio zone info");
    }

    @Test
    public void hashCode_forSameContent() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo infoWithSameContent = new CarAudioZoneConfigInfo(TEST_CONFIG_NAME,
                TEST_ZONE_ID, TEST_CONFIG_ID);

        expectWithMessage("Zone Configuration info hash with same content")
                .that(infoWithSameContent.hashCode()).isEqualTo(TEST_ZONE_CONFIG_INFO.hashCode());
    }
}
