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

import static android.media.FadeManagerConfiguration.FADE_STATE_DISABLED;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.media.FadeManagerConfiguration;
import android.os.Parcel;

import org.junit.Test;

public final class CarAudioFadeConfigurationUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PARCEL_FLAGS = 0;
    private static final String TEST_NAME_AGGRESSIVE_FADING = "aggressive fading";
    private static final String TEST_NAME_DISABLED_FADING = "disabled fading";
    private static final String TEST_NAME_RELAXED_FADING = "relaxed fading";
    private static final FadeManagerConfiguration TEST_FADE_MANAGER_CONFIG_DISABLED =
            new FadeManagerConfiguration.Builder().setFadeState(FADE_STATE_DISABLED).build();
    private static final FadeManagerConfiguration TEST_FADE_MANAGER_CONFIG_ENABLED =
            new FadeManagerConfiguration.Builder().build();

    @Test
    public void build() {
        CarAudioFadeConfiguration carAudioFadeConfiguration =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED)
                        .setName(TEST_NAME_AGGRESSIVE_FADING).build();

        expectWithMessage("Fade manager configuration")
                .that(carAudioFadeConfiguration.getFadeManagerConfiguration())
                .isEqualTo(TEST_FADE_MANAGER_CONFIG_ENABLED);
        expectWithMessage("Car Audio Fade configuration name")
                .that(carAudioFadeConfiguration.getName()).isEqualTo(TEST_NAME_AGGRESSIVE_FADING);
    }

    @Test
    public void builder_withNullFadeManagerConfiguration_fails() {

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioFadeConfiguration.Builder(/* fadeManagerConfiguration= */ null)
        );

        expectWithMessage("Null fade manager configuration exception")
                .that(thrown).hasMessageThat().contains("configuration can not be null");
    }

    @Test
    public void builder_withNullName_fails() {

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED)
                        .setName(/* name= */ null)
        );

        expectWithMessage("Null name exception")
                .that(thrown).hasMessageThat().contains("Name can not");
    }

    @Test
    public void builder_withReuse_fails() {
        CarAudioFadeConfiguration.Builder builder =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }


    @Test
    public void writeToParcel_thenCreateFromParcel() {
        Parcel parcel = Parcel.obtain();

        CarAudioFadeConfiguration carAudioFadeConfiguration = createAndWriteToParcel(parcel);

        expectWithMessage("Car audio fade configuration from parcel")
                .that(CarAudioFadeConfiguration.CREATOR.createFromParcel(parcel))
                .isEqualTo(carAudioFadeConfiguration);
    }

    @Test
    public void equals_withDuplicate() {
        CarAudioFadeConfiguration result =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        CarAudioFadeConfiguration duplResult =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();

        expectWithMessage("Duplicate car audio fade configuration").that(result)
                .isEqualTo(duplResult);
    }

    @Test
    public void equals_withDifferentFadeManagerConfiguration() {
        CarAudioFadeConfiguration result1 =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED)
                        .setName(TEST_NAME_RELAXED_FADING).build();
        CarAudioFadeConfiguration result2 =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_DISABLED)
                        .setName(TEST_NAME_DISABLED_FADING).build();

        expectWithMessage("Car audio fade configuration with different fade configs").that(result1)
                .isNotEqualTo(result2);
    }

    @Test
    public void hashCode_withDuplicate_equals() {
        CarAudioFadeConfiguration result =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        CarAudioFadeConfiguration duplResult =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();

        expectWithMessage("Duplicate hash code").that(result.hashCode())
                .isEqualTo(duplResult.hashCode());
    }


    private CarAudioFadeConfiguration createAndWriteToParcel(Parcel parcel) {
        CarAudioFadeConfiguration result =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED)
                        .setName(TEST_NAME_RELAXED_FADING).build();

        result.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        return result;
    }
}
