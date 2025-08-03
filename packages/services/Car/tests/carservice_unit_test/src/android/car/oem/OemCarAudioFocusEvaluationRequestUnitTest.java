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

import static android.car.feature.Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES;
import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS;
import static android.car.oem.CarAudioFeaturesInfo.AUDIO_FEATURE_FADE_MANAGER_CONFIGS;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;

import static org.junit.Assert.assertThrows;

import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;
import android.platform.test.flag.junit.SetFlagsRule;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class OemCarAudioFocusEvaluationRequestUnitTest extends AbstractExpectableTestCase {

    private static final AudioFocusEntry TEST_MEDIA_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_MEDIA);
    private static final AudioFocusEntry TEST_CALL_FOCUS_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_VOICE_COMMUNICATION);
    private static final AudioFocusEntry TEST_NAV_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private static final AudioFocusEntry TEST_ASSISTANT_AUDIO_FOCUS_ENTRY =
            OemFocusUtils.getAudioFocusEntry(USAGE_ASSISTANT);
    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int TEST_VOLUME_GROUP_ID = 2;
    private static final int TEST_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE + 1;
    private static final String TEST_GROUP_NAME = "media";
    private static final int TEST_MAX_GAIN_INDEX = 9_000;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 8_000;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 1_000;
    private static final CarVolumeGroupInfo TEST_MUTED_VOLUME_GROUP =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, CarAudioManager.PRIMARY_AUDIO_ZONE,
                    TEST_VOLUME_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();
    private static final CarVolumeGroupInfo TEST_MUTED_VOLUME_GROUP_2 =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                    TEST_VOLUME_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void build() {
        OemCarAudioFocusEvaluationRequest request = new OemCarAudioFocusEvaluationRequest.Builder(
                List.of(TEST_MUTED_VOLUME_GROUP), List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                List.of(TEST_NAV_AUDIO_FOCUS_ENTRY), CarAudioManager.PRIMARY_AUDIO_ZONE)
                .setAudioFocusRequest(TEST_CALL_FOCUS_FOCUS_ENTRY).build();

        expectWithMessage("Audio focus request").that(request.getAudioFocusRequest())
                .isEqualTo(TEST_CALL_FOCUS_FOCUS_ENTRY);
        expectWithMessage("Audio focus losers").that(request.getFocusLosers())
                .containsExactly(TEST_NAV_AUDIO_FOCUS_ENTRY);
        expectWithMessage("Audio focus holders").that(request.getFocusHolders())
                .containsExactly(TEST_MEDIA_AUDIO_FOCUS_ENTRY);
        expectWithMessage("Muted audio volume groups").that(request.getMutedVolumeGroups())
                .containsExactly(TEST_MUTED_VOLUME_GROUP);
        expectWithMessage("Request audio zone").that(request.getAudioZoneId())
                .isEqualTo(CarAudioManager.PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void writeToParcel() {
        Parcel parcel = Parcel.obtain();

        OemCarAudioFocusEvaluationRequest request = createRequestAndWriteToParcel(parcel);

        expectWithMessage("Car audio focus evaluation request from parcel")
                .that(new OemCarAudioFocusEvaluationRequest(parcel)).isEqualTo(request);
    }

    @Test
    public void createFromParcel() {
        Parcel parcel = Parcel.obtain();
        OemCarAudioFocusEvaluationRequest request = createRequestAndWriteToParcel(parcel);

        expectWithMessage("Car audio focus evaluation request created from parcel")
                .that(OemCarAudioFocusEvaluationRequest.CREATOR.createFromParcel(parcel))
                .isEqualTo(request);
    }

    @NotNull
    private OemCarAudioFocusEvaluationRequest createRequestAndWriteToParcel(Parcel parcel) {
        OemCarAudioFocusEvaluationRequest request = new OemCarAudioFocusEvaluationRequest.Builder(
                List.of(TEST_MUTED_VOLUME_GROUP), List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                List.of(TEST_NAV_AUDIO_FOCUS_ENTRY), CarAudioManager.PRIMARY_AUDIO_ZONE)
                .setAudioFocusRequest(TEST_CALL_FOCUS_FOCUS_ENTRY).build();
        request.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        return request;
    }

    @Test
    public void builder_withReuse_fails() {
        OemCarAudioFocusEvaluationRequest.Builder builder =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY), List.of(TEST_NAV_AUDIO_FOCUS_ENTRY),
                        CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setAudioFocusRequest(TEST_CALL_FOCUS_FOCUS_ENTRY);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void builder_withNullFocusRequest_succeeds() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY),
                        List.of(TEST_NAV_AUDIO_FOCUS_ENTRY), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .build();

        OemCarAudioFocusEvaluationRequest nullFocusRequest =
                new OemCarAudioFocusEvaluationRequest(null, List.of(TEST_MUTED_VOLUME_GROUP),
                        List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY), List.of(TEST_NAV_AUDIO_FOCUS_ENTRY),
                        /* carAudioFeaturesInfo= */ null, CarAudioManager.PRIMARY_AUDIO_ZONE);

        expectWithMessage("Request with null audio focus request")
                .that(nullFocusRequest).isEqualTo(request);
        expectWithMessage("Audio focus entry request")
                .that(request.getAudioFocusRequest()).isNull();
    }

    @Test
    public void setAudioZoneId_succeeds() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        List.of(TEST_MEDIA_AUDIO_FOCUS_ENTRY), List.of(TEST_NAV_AUDIO_FOCUS_ENTRY),
                        CarAudioManager.PRIMARY_AUDIO_ZONE).setAudioZoneId(TEST_ZONE_ID).build();

        expectWithMessage("Request zone id").that(request.getAudioZoneId())
                .isEqualTo(TEST_ZONE_ID);
    }

    @Test
    public void setFocusHolders() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setFocusHolders(List.of(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY)).build();

        expectWithMessage("Focus holders").that(request.getFocusHolders())
                .contains(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void setFocusHolders_withNullHolders_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setFocusHolders(null)
        );

        expectWithMessage("Null holders exception")
                .that(thrown).hasMessageThat().contains("Focus holders");
    }

    @Test
    public void addFocusHolders() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addFocusHolders(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Focus holder").that(request.getFocusHolders())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void addFocusHolders_withNullHolder_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addFocusHolders(null)
        );

        expectWithMessage("Null holder exception")
                .that(thrown).hasMessageThat().contains("Focus holder");
    }

    @Test
    public void setFocusLosers() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setFocusLosers(List.of(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY)).build();

        expectWithMessage("Focus losers").that(request.getFocusLosers())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void setFocusLosers_withNullLosers_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setFocusLosers(null)
        );

        expectWithMessage("Null losers exception")
                .that(thrown).hasMessageThat().contains("Focus loser");
    }

    @Test
    public void addFocusLosers() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addFocusLosers(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY).build();

        expectWithMessage("Focus loser").that(request.getFocusLosers())
                .containsExactly(TEST_ASSISTANT_AUDIO_FOCUS_ENTRY);
    }

    @Test
    public void addFocusLosers_withNullLosers_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addFocusLosers(null)
        );

        expectWithMessage("Null loser exception")
                .that(thrown).hasMessageThat().contains("Focus loser");
    }

    @Test
    public void setMutedVolumeGroups() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setMutedVolumeGroups(List.of(TEST_MUTED_VOLUME_GROUP_2)).build();

        expectWithMessage("Muted volume groups").that(request.getMutedVolumeGroups())
                .containsExactly(TEST_MUTED_VOLUME_GROUP_2);
    }

    @Test
    public void setMutedVolumeGroups_withNullGroups_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setMutedVolumeGroups(null)
        );

        expectWithMessage("Null muted volume groups exception")
                .that(thrown).hasMessageThat().contains("Muted volume groups");
    }

    @Test
    public void addMutedVolumeGroups() {
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2).build();

        expectWithMessage("Muted volume group").that(request.getMutedVolumeGroups())
                .containsExactly(TEST_MUTED_VOLUME_GROUP_2);
    }

    @Test
    public void addMutedVolumeGroups_withNullGroups_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(null)
        );

        expectWithMessage("Null muted volume group exception")
                .that(thrown).hasMessageThat().contains("Muted volume group");
    }


    @Test
    public void setAudioFocusRequest_withNullGroups_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new OemCarAudioFocusEvaluationRequest.Builder(List.of(TEST_MUTED_VOLUME_GROUP),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .setAudioFocusRequest(null)
        );

        expectWithMessage("Null audio focus request exception")
                .that(thrown).hasMessageThat().contains("Audio focus request");
    }

    @Test
    public void getAudioFeaturesInfo() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo featureInfo = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).addAudioFeature(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();
        OemCarAudioFocusEvaluationRequest request =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2)
                        .setAudioFeaturesInfo(featureInfo).build();

        CarAudioFeaturesInfo requestFeatureInfo = request.getAudioFeaturesInfo();

        expectWithMessage("Existing audio feature info").that(requestFeatureInfo).isNotNull();
        expectWithMessage("Audio feature info").that(requestFeatureInfo).isEqualTo(featureInfo);
        expectWithMessage("Isolated audio focus feature").that(requestFeatureInfo
                        .isAudioFeatureEnabled(AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS)).isTrue();
        expectWithMessage("Fade manager configuration audio feature").that(requestFeatureInfo
                        .isAudioFeatureEnabled(AUDIO_FEATURE_FADE_MANAGER_CONFIGS))
                .isTrue();
    }

    @Test
    public void equals_withSameFeatureInfo() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo featureInfo = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        OemCarAudioFocusEvaluationRequest requestOne =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2)
                        .setAudioFeaturesInfo(featureInfo).build();
        OemCarAudioFocusEvaluationRequest requestTwo =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2)
                        .setAudioFeaturesInfo(featureInfo).build();

        expectWithMessage("Car audio focus eval requests").that(requestOne).isEqualTo(requestTwo);
    }

    @Test
    public void equals_withDifferentFeatureInfo() {
        mSetFlagsRule.enableFlags(FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioFeaturesInfo featureInfoOne = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_ISOLATED_DEVICE_FOCUS).build();
        CarAudioFeaturesInfo featureInfoTwo = new CarAudioFeaturesInfo.Builder(
                AUDIO_FEATURE_FADE_MANAGER_CONFIGS).build();
        OemCarAudioFocusEvaluationRequest requestOne =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2)
                        .setAudioFeaturesInfo(featureInfoOne).build();
        OemCarAudioFocusEvaluationRequest requestTwo =
                new OemCarAudioFocusEvaluationRequest.Builder(new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>(), CarAudioManager.PRIMARY_AUDIO_ZONE)
                        .addMutedVolumeGroups(TEST_MUTED_VOLUME_GROUP_2)
                        .setAudioFeaturesInfo(featureInfoTwo).build();

        expectWithMessage("Car audio focus eval requests with different feature info")
                .that(requestOne).isNotEqualTo(requestTwo);
    }
}
