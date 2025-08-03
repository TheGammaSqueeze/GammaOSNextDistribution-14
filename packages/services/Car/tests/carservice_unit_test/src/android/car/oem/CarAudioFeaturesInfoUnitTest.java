/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.oem;

import static android.car.feature.Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES;
import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS;
import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_NO_FEATURE;
import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_FADE_MANAGER_CONFIGS;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

public final class CarAudioFeaturesInfoUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PARCEL_FLAGS = 0;
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void build_withDynamicDeviceFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();

        expectWithMessage("Car audio dynamic devices feature with dynamic device only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isTrue();
        expectWithMessage("Car fade manager feature with dynamic device only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isFalse();
    }

    @Test
    public void build_withFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Car audio dynamic devices feature with fade manager only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isFalse();
        expectWithMessage("Car fade manager feature with fade manager device only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isTrue();
    }

    @Test
    public void build_withNoFeatures() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_NO_FEATURE).build();

        expectWithMessage("Car audio dynamic devices feature with no features")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isFalse();
        expectWithMessage("Car fade manager feature with no features")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isFalse();
    }

    @Test
    public void build_afterReUse_fails() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo.Builder builder = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_NO_FEATURE);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, builder::build);

        expectWithMessage("Re-using build in builder exception").that(thrown).hasMessageThat()
                .contains("should not be reused");
    }

    @Test
    public void build_withInvalidFeature_fails() {
        int invalidAudioFeature = -1;
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioFeaturesInfo.Builder(invalidAudioFeature));

        expectWithMessage("Invalid audio feature exception")
                .that(thrown).hasMessageThat().contains("invalid");
    }

    @Test
    public void addAudioFeatures_withDynamicDevicesFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(AUDIO_FEATURE_NO_FEATURE)
                .addAudioFeature(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();

        expectWithMessage("Car audio focus feature with added focus feature only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isTrue();
        expectWithMessage("Car audio fade manager feature with added focus feature only")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isFalse();
    }

    @Test
    public void addAudioFeatures_withFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(AUDIO_FEATURE_NO_FEATURE)
                .addAudioFeature(AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Car audio focus feature with added fade manager feature")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isFalse();
        expectWithMessage("Car audio fade manager feature with added fade manager feature")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isTrue();
    }

    @Test
    public void addAudioFeatures_afterReUse_fails() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo.Builder builder = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_NO_FEATURE);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> builder.addAudioFeature(AUDIO_FEATURE_FADE_MANAGER_CONFIGS));

        expectWithMessage("Re-using add audio feature in builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void writeToParcel_readFromParcel_andFocusFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        Parcel parcel = Parcel.obtain();

        info.writeToParcel(parcel, TEST_PARCEL_FLAGS);

        parcel.setDataPosition(/* pos= */ 0);
        CarAudioFeaturesInfo createdInfo = CarAudioFeaturesInfo.CREATOR.createFromParcel(parcel);
        expectWithMessage("Car audio device feature with focus feature and crated from parcel")
                .that(createdInfo).isEqualTo(info);
        expectWithMessage("Car focus feature with focus feature and created from parcel")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isTrue();
        expectWithMessage("Car fade manager feature with focus feature and created from parcel")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isFalse();
    }

    @Test
    public void writeToParcel_readFromParcel_andFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();
        Parcel parcel = Parcel.obtain();

        info.writeToParcel(parcel, TEST_PARCEL_FLAGS);

        parcel.setDataPosition(/* pos= */ 0);
        CarAudioFeaturesInfo createdInfo = CarAudioFeaturesInfo.CREATOR.createFromParcel(parcel);
        expectWithMessage("Car audio device feature with fade manager feature created from parcel")
                .that(createdInfo).isEqualTo(info);
        expectWithMessage("Car focus feature with fade manager feature and created from parcel")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isFalse();
        expectWithMessage("Car fade manager feature with fade manager feature"
                + "and created from parcel")
                .that(info.isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isTrue();
    }

    @Test
    public void equals_withFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info1 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();
        CarAudioFeaturesInfo info2 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Fade manager configuration features").that(info1).isEqualTo(info2);
    }

    @Test
    public void equals_withFocusFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info1 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        CarAudioFeaturesInfo info2 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();

        expectWithMessage("Isolated focus features").that(info1).isEqualTo(info2);
    }

    @Test
    public void equals_withDifferentFeatures() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info1 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        CarAudioFeaturesInfo info2 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Car audio features").that(info1).isNotEqualTo(info2);
    }

    @Test
    public void hashCode_withFocusFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info1 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        CarAudioFeaturesInfo info2 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();

        expectWithMessage("Isolated focus features hash").that(info1.hashCode())
                .isEqualTo(info2.hashCode());
    }

    @Test
    public void hashCode_withFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info1 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();
        CarAudioFeaturesInfo info2 = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Fade manager features hash").that(info1.hashCode())
                .isEqualTo(info2.hashCode());
    }

    @Test
    public void toString_withFocusFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();

        expectWithMessage("Isolated focus features string").that(info.toString())
                .contains("ISOLATED_DEVICE_FOCUS");
    }

    @Test
    public void toString_withFadeManagerFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();

        expectWithMessage("Fade manager feature string").that(info.toString())
                .contains("FADE_MANAGER_CONFIGS");
    }

    @Test
    public void toString_withNoFeature() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo info = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_NO_FEATURE).build();

        expectWithMessage("Empty audio feature").that(info.toString())
                .contains("NONE");
    }
}
