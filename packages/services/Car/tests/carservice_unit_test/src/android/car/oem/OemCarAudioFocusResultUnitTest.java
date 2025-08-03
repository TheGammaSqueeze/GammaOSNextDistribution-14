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

package android.car.oem;

import static android.car.feature.Flags.FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.FadeManagerConfiguration.FADE_STATE_DISABLED;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.FadeManagerConfiguration;
import android.os.Parcel;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OemCarAudioFocusResultUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PARCEL_FLAGS = 0;
    private static final String TEST_OEM_TAG = "oem_tag";
    private static final AudioFocusEntry TEST_MEDIA_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_MEDIA);
    private static final AudioFocusEntry TEST_CALL_FOCUS_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_VOICE_COMMUNICATION);
    private static final AudioFocusEntry TEST_NAV_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private static final AudioFocusEntry TEST_ASSISTANT_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_ASSISTANT);
    private static final AudioAttributes TEST_AUDIOATTRIBUTES_MEDIA =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_AUDIOATTRIBUTES_OEM_TAG =
            new AudioAttributes.Builder().addTag(TEST_OEM_TAG).build();
    private static final FadeManagerConfiguration TEST_FADE_MANAGER_CONFIG_DISABLED =
            new FadeManagerConfiguration.Builder().setFadeState(FADE_STATE_DISABLED).build();
    private static final FadeManagerConfiguration TEST_FADE_MANAGER_CONFIG_ENABLED =
            new FadeManagerConfiguration.Builder().build();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void build() {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Newly lost entries").that(result.getNewlyLostAudioFocusEntries())
                .containsExactly(TEST_MEDIA_AUDIO_FOCUS_ENTRY);
        expectWithMessage("Newly blocked entries")
                .that(result.getNewlyBlockedAudioFocusEntries())
                .containsExactly(TEST_CALL_FOCUS_FOCUS_ENTRY);
        expectWithMessage("Evaluated focus entry").that(result.getAudioFocusEntry())
                .isEqualTo(TEST_NAV_AUDIO_FOCUS_ENTRY);
        expectWithMessage("Evaluation results").that(result.getAudioFocusResult())
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
    }

    @Test
    public void build_withCarAudioFadeConfiguration() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfiguration afc_disabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_DISABLED).build();
        CarAudioFadeConfiguration afc_enabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        Map<AudioAttributes,
                CarAudioFadeConfiguration> attrsToCarAudioFadeConfiguration = new ArrayMap<>();
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_MEDIA, afc_enabled);
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_OEM_TAG, afc_disabled);
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration)
                        .build();

        expectWithMessage("Usage to car audio fade configuration")
                .that(result.getAudioAttributesToCarAudioFadeConfigurationMap())
                .isEqualTo(attrsToCarAudioFadeConfiguration);
        expectWithMessage("Car audio fade configuration for usage media")
                .that(result.getCarAudioFadeConfigurationForAudioAttributes(
                        TEST_AUDIOATTRIBUTES_MEDIA)).isEqualTo(afc_enabled);
        expectWithMessage("Car audio fade configuration for usage assistant")
                .that(result.getCarAudioFadeConfigurationForAudioAttributes(
                        TEST_AUDIOATTRIBUTES_OEM_TAG)).isEqualTo(afc_disabled);
    }


    @Test
    public void writeToParcel_thenCreateFromParcel() {
        mSetFlagsRule.disableFlags(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        Parcel parcel = Parcel.obtain();

        OemCarAudioFocusResult result = createAndWriteToParcel(parcel);

        expectWithMessage("Audio focus evaluation from parcel")
                .that(OemCarAudioFocusResult.CREATOR.createFromParcel(parcel)).isEqualTo(result);
    }

    @Test
    public void writeToParcel_thenCreateFromParcel_withFeatureCarAudioFadeEnabled() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        Parcel parcel = Parcel.obtain();

        OemCarAudioFocusResult result = createAndWriteToParcel_withCarAudioFadeConfig(parcel);

        expectWithMessage("Audio focus evaluation from parcel")
                .that(OemCarAudioFocusResult.CREATOR.createFromParcel(parcel)).isEqualTo(result);
    }

    @Test
    public void equals_withDuplicate() {
        OemCarAudioFocusResult result1 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();
        OemCarAudioFocusResult result2 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Duplicate audio focus result").that(result1).isEqualTo(result2);
    }

    @Test
    public void equals_duplicate_withCarAudioFadeConfigMap_whenFeatureEnabled() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfiguration afc_disabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_DISABLED).build();
        CarAudioFadeConfiguration afc_enabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        Map<AudioAttributes,
                CarAudioFadeConfiguration> attrsToCarAudioFadeConfiguration = new ArrayMap<>();
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_MEDIA, afc_enabled);
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_OEM_TAG, afc_disabled);
        OemCarAudioFocusResult result1 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration).build();
        OemCarAudioFocusResult result2 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration).build();

        expectWithMessage("Duplicate audio focus result with car audio fade configurations")
                .that(result1).isEqualTo(result2);
    }

    @Test
    public void equals_withDifferentFocusEntry() {
        OemCarAudioFocusResult result1 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();
        OemCarAudioFocusResult result2 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Non equal audio focus result")
                .that(result1).isNotEqualTo(result2);
    }

    @Test
    public void hashCode_withDuplicate() {
        OemCarAudioFocusResult result1 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();
        OemCarAudioFocusResult result2 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Duplicate hash code")
                .that(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    public void hashCode_duplicate_withCarAudioFadeConfigMap_whenFeatureEnabled_equals() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_FADE_MANAGER_CONFIGURATION);
        CarAudioFadeConfiguration afc_disabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_DISABLED).build();
        CarAudioFadeConfiguration afc_enabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        Map<AudioAttributes,
                CarAudioFadeConfiguration> attrsToCarAudioFadeConfiguration = new ArrayMap<>();
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_MEDIA, afc_enabled);
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_OEM_TAG, afc_disabled);
        OemCarAudioFocusResult result1 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration).build();
        OemCarAudioFocusResult result2 =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration).build();

        expectWithMessage("Duplicate hash code with car audio fade configuration")
                .that(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    public void builder_withReuse_fails() {
        OemCarAudioFocusResult.Builder builder =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void setAudioFocusEntry_withNullEntry_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(/* focusEntry= */ null)
        );

        expectWithMessage("Null audio focus entry exception")
                .that(thrown).hasMessageThat().contains("Focus entry");
    }

    @Test
    public void setNewlyLostAudioFocusEntries() {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setNewlyLostAudioFocusEntries(List.of(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY))
                        .build();

        expectWithMessage("Set newly lost entries")
                .that(result.getNewlyLostAudioFocusEntries())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void setNewlyLostAudioFocusEntries_withNullEntries_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setNewlyLostAudioFocusEntries(null)
        );

        expectWithMessage("Null lost entries exception")
                .that(thrown).hasMessageThat().contains("Newly lost focus");
    }

    @Test
    public void addNewlyLostAudioFocusEntry() {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(new ArrayList<>(),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .addNewlyLostAudioFocusEntry(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY)
                        .build();

        expectWithMessage("Added newly lost entry")
                .that(result.getNewlyLostAudioFocusEntries())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void addNewlyLostAudioFocusEntry_withNullEntry_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusResult.Builder(new ArrayList<>(),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .addNewlyLostAudioFocusEntry(null)
        );

        expectWithMessage("Null lost entry exception")
                .that(thrown).hasMessageThat().contains("Newly lost focus");
    }

    @Test
    public void setNewlyBlockedAudioFocusEntries() {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setNewlyBlockedAudioFocusEntries(List.of(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY))
                        .build();

        expectWithMessage("Set newly lost entries")
                .that(result.getNewlyBlockedAudioFocusEntries())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void setNewlyBlockedAudioFocusEntries_withNullEntries_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setNewlyBlockedAudioFocusEntries(null)
        );

        expectWithMessage("Null lost entries exception")
                .that(thrown).hasMessageThat().contains("Newly blocked focus");
    }

    @Test
    public void addNewlyBlockedAudioFocusEntry() {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(new ArrayList<>(), new ArrayList<>(),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .addNewlyBlockedAudioFocusEntry(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY)
                        .build();

        expectWithMessage("Added newly blocked entry")
                .that(result.getNewlyBlockedAudioFocusEntries())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void addNewlyBlockedAudioFocusEntry_withNullEntry_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusResult.Builder(new ArrayList<>(), new ArrayList<>(),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .addNewlyBlockedAudioFocusEntry(null)
        );

        expectWithMessage("Null blocked entry exception")
                .that(thrown).hasMessageThat().contains("Newly blocked focus");
    }

    private OemCarAudioFocusResult createAndWriteToParcel(Parcel parcel) {
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY).build();

        result.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        return result;
    }

    private OemCarAudioFocusResult createAndWriteToParcel_withCarAudioFadeConfig(Parcel parcel) {
        CarAudioFadeConfiguration afc_disabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_DISABLED).build();
        CarAudioFadeConfiguration afc_enabled =
                new CarAudioFadeConfiguration.Builder(TEST_FADE_MANAGER_CONFIG_ENABLED).build();
        Map<AudioAttributes,
                CarAudioFadeConfiguration> attrsToCarAudioFadeConfiguration = new ArrayMap<>();
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_MEDIA, afc_enabled);
        attrsToCarAudioFadeConfiguration.put(TEST_AUDIOATTRIBUTES_OEM_TAG, afc_disabled);
        OemCarAudioFocusResult result =
                new OemCarAudioFocusResult.Builder(List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_CALL_FOCUS_FOCUS_ENTRY),
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                        .setAudioFocusEntry(TEST_NAV_AUDIO_FOCUS_ENTRY)
                        .setAudioAttributesToCarAudioFadeConfigurationMap(
                                attrsToCarAudioFadeConfiguration).build();

        result.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        return result;
    }
}
