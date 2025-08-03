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

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.junit.Assert.assertThrows;

import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.os.Parcel;
import android.telephony.TelephonyManager;

import com.android.car.audio.CarAudioContext;

import org.junit.Test;

import java.util.List;

public final class OemCarAudioVolumeRequestUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PRIMARY_GROUP_ID = 7;
    private static final int TEST_SECONDARY_GROUP_ID = 9;
    private static final String TEST_GROUP_NAME = "3";
    private static final int TEST_MAX_GAIN_INDEX = 9_000;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 8_000;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 1_000;
    private static final int TEST_PARCEL_FLAGS = 0;

    private static final CarVolumeGroupInfo TEST_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, PRIMARY_AUDIO_ZONE,
                    TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();
    private static final CarVolumeGroupInfo TEST_VOLUME_INFO_2 =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, PRIMARY_AUDIO_ZONE,
                    TEST_SECONDARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

    private static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT);

    private static final OemCarAudioVolumeRequest TEST_VOLUME_REQUEST =
            new OemCarAudioVolumeRequest.Builder(PRIMARY_AUDIO_ZONE)
                    .addActivePlaybackAttributes(TEST_MEDIA_ATTRIBUTE)
                    .addDuckedAudioAttributes(TEST_NAVIGATION_ATTRIBUTE)
                    .addCarVolumeGroupInfos(TEST_VOLUME_INFO).build();

    private final OemCarAudioVolumeRequest.Builder mTestVolumeRequestBuilder =
            new OemCarAudioVolumeRequest.Builder(PRIMARY_AUDIO_ZONE);

    @Test
    public void build() {
        OemCarAudioVolumeRequest request = new OemCarAudioVolumeRequest.Builder(
                PRIMARY_AUDIO_ZONE).build();

        expectWithMessage("Car volume request build info zone id")
                .that(request.getAudioZoneId()).isEqualTo(PRIMARY_AUDIO_ZONE);
    }

    @Test
    public void setActivePlaybackAttributes() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .setActivePlaybackAttributes(List.of(TEST_ASSISTANT_ATTRIBUTE)).build();

        expectWithMessage("Car volume request build active attributes")
                .that(request.getActivePlaybackAttributes())
                .containsExactly(TEST_ASSISTANT_ATTRIBUTE);
    }

    @Test
    public void addActivePlaybackAttributes() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .addActivePlaybackAttributes(TEST_MEDIA_ATTRIBUTE).build();

        expectWithMessage("Car volume request build active attribute")
                .that(request.getActivePlaybackAttributes()).containsExactly(TEST_MEDIA_ATTRIBUTE);
    }

    @Test
    public void setDuckedAudioAttributes() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .setDuckedAudioAttributes(List.of(TEST_NAVIGATION_ATTRIBUTE)).build();

        expectWithMessage("Car volume request build ducked attributes")
                .that(request.getDuckedAudioAttributes())
                .containsExactly(TEST_NAVIGATION_ATTRIBUTE);
    }

    @Test
    public void addDuckedAudioAttributes() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .addDuckedAudioAttributes(TEST_MEDIA_ATTRIBUTE).build();

        expectWithMessage("Car volume request build ducked attribute")
                .that(request.getDuckedAudioAttributes()).containsExactly(TEST_MEDIA_ATTRIBUTE);
    }

    @Test
    public void setCarVolumeGroupInfos() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .setCarVolumeGroupInfos(List.of(TEST_VOLUME_INFO)).build();

        expectWithMessage("Car volume request build volumes infos")
                .that(request.getCarVolumeGroupInfos()).containsExactly(TEST_VOLUME_INFO);
    }

    @Test
    public void addCarVolumeGroupInfos() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .addCarVolumeGroupInfos(TEST_VOLUME_INFO_2).build();

        expectWithMessage("Car volume request build volume info")
                .that(request.getCarVolumeGroupInfos()).containsExactly(TEST_VOLUME_INFO_2);
    }

    @Test
    public void setCallState() {
        OemCarAudioVolumeRequest request = mTestVolumeRequestBuilder
                .setCallState(TelephonyManager.CALL_STATE_OFFHOOK).build();

        expectWithMessage("Car volume request build call state")
                .that(request.getCallState()).isEqualTo(TelephonyManager.CALL_STATE_OFFHOOK);
    }

    @Test
    public void builder_withReuse_fails() {
        mTestVolumeRequestBuilder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                mTestVolumeRequestBuilder.build()
        );

        expectWithMessage("Reuse request builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void writeToParcel() {
        Parcel parcel = Parcel.obtain();

        TEST_VOLUME_REQUEST.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);

        expectWithMessage("Car volume request from parcel")
                .that(OemCarAudioVolumeRequest.CREATOR.createFromParcel(parcel))
                .isEqualTo(TEST_VOLUME_REQUEST);
    }

    @Test
    public void newArray() {
        OemCarAudioVolumeRequest[] requests =
                OemCarAudioVolumeRequest.CREATOR.newArray(/* size= */ 7);

        expectWithMessage("Car volume request size").that(requests).hasLength(7);
    }

    @Test
    public void equals_forSameContent() {
        OemCarAudioVolumeRequest request =
                new OemCarAudioVolumeRequest.Builder(TEST_VOLUME_REQUEST).build();

        expectWithMessage("Car volume request with same content")
                .that(request).isEqualTo(TEST_VOLUME_REQUEST);
    }

    @Test
    public void equals_forNull() {
        OemCarAudioVolumeRequest request =
                new OemCarAudioVolumeRequest.Builder(TEST_VOLUME_REQUEST).build();

        expectWithMessage("Car volume request null content")
                .that(request.equals(null)).isFalse();
    }

    @Test
    public void hashCode_forSameContent() {
        OemCarAudioVolumeRequest request =
                new OemCarAudioVolumeRequest.Builder(TEST_VOLUME_REQUEST).build();

        expectWithMessage("Car volume request hash with same content")
                .that(request.hashCode()).isEqualTo(TEST_VOLUME_REQUEST.hashCode());
    }

    @Test
    public void toString_forContent() {
        OemCarAudioVolumeRequest request =
                new OemCarAudioVolumeRequest.Builder(TEST_VOLUME_REQUEST).build();

        expectWithMessage("Car volume request zone id")
                .that(request.toString()).contains(Integer.toString(PRIMARY_AUDIO_ZONE));
    }

    @Test
    public void describeContents() {
        OemCarAudioVolumeRequest request =
                new OemCarAudioVolumeRequest.Builder(TEST_VOLUME_REQUEST).build();

        expectWithMessage("Car volume request contents")
                .that(request.describeContents()).isEqualTo(/* expected= */ 0);
    }
}
