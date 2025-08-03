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

import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public final class CarVolumeGroupEventUnitTest extends AbstractExpectableTestCase {
    private static final String TEST_GROUP_NAME_MEDIA = "MEDIA";
    private static final int TEST_ZONE_ID = 1;
    private static final int TEST_ID = 1;
    private static final int TEST_MIN_GAIN_INDEX = 0;
    private static final int TEST_MAX_GAIN_INDEX = 9_005;
    private static final int TEST_MAX_ACTIVATION_GAIN_INDEX = 8_005;
    private static final int TEST_MIN_ACTIVATION_GAIN_INDEX = 1_000;
    private static final int TEST_CURRENT_GAIN_INDEX = 5_000;
    private static final boolean TEST_DEFAULT_BLOCKED_STATE = false;
    private static final boolean TEST_DEFAULT_ATTENUATED_STATE = false;
    private static final boolean TEST_DEFAULT_MUTE_STATE = false;
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final int TEST_PARCEL_FLAGS = 0;

    private static final CarVolumeGroupInfo TEST_CAR_VOLUME_GROUP_INFO =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME_MEDIA, TEST_ZONE_ID, TEST_ID)
                    .setAttenuated(TEST_DEFAULT_ATTENUATED_STATE)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setVolumeGainIndex(TEST_CURRENT_GAIN_INDEX)
                    .setBlocked(TEST_DEFAULT_BLOCKED_STATE)
                    .setMuted(TEST_DEFAULT_MUTE_STATE)
                    .setAudioAttributes(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE))
                    .setMaxActivationVolumeGainIndex(TEST_MAX_ACTIVATION_GAIN_INDEX)
                    .setMinActivationVolumeGainIndex(TEST_MIN_ACTIVATION_GAIN_INDEX).build();

    private static final CarVolumeGroupEvent TEST_CAR_VOLUME_GROUP_EVENT =
            new CarVolumeGroupEvent.Builder(List.of(TEST_CAR_VOLUME_GROUP_INFO),
                    CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                    List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

    @Test
    public void build() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Volume groups info").that(event.getCarVolumeGroupInfos())
                .containsExactly(TEST_CAR_VOLUME_GROUP_INFO);
        expectWithMessage("Event types").that(event.getEventTypes())
                .isEqualTo(CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Extra infos").that(event.getExtraInfos())
                .containsExactly(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI);
    }

    @Test
    public void build_withOut_extraInfo() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED).build();

        expectWithMessage("Volume groups info").that(event.getCarVolumeGroupInfos())
                .containsExactly(TEST_CAR_VOLUME_GROUP_INFO);
        expectWithMessage("Event types").that(event.getEventTypes())
                .isEqualTo(CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Extra infos").that(event.getExtraInfos())
                .containsExactly(CarVolumeGroupEvent.EXTRA_INFO_NONE);
    }

    @Test
    public void builder_withReuse_fails() {
        CarVolumeGroupEvent.Builder builder = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI));
        builder.build();

        IllegalStateException  thrown = assertThrows(IllegalStateException.class, () ->
                builder.build());

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void build_withNullVolumeGroupInfo_fails() {
        IllegalArgumentException  thrown = assertThrows(IllegalArgumentException.class, () ->
                new CarVolumeGroupEvent.Builder(/* CarVolumeGroupInfo */null,
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI))
        );

        expectWithMessage("Null volume group info exception")
                .that(thrown).hasMessageThat().contains("Volume group info");
    }

    @Test
    public void build_withNullExtraInfo_fails() {
        IllegalArgumentException  thrown = assertThrows(IllegalArgumentException.class, () ->
                new CarVolumeGroupEvent.Builder(List.of(TEST_CAR_VOLUME_GROUP_INFO),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        /*ExtraInfo*/ null)
        );

        expectWithMessage("Null extra info exception")
                .that(thrown).hasMessageThat().contains("Extra info");
    }

    @Test
    public void addCarVolumeGroupInfo_withNullEntry_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new CarVolumeGroupEvent.Builder(new ArrayList<>(),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI))
                        .addCarVolumeGroupInfo(/*CarVolumeGroupInfo*/null)
                        .build()
        );

        expectWithMessage("Null Car volume group info exception")
                .that(thrown).hasMessageThat().contains("Volume group info");
    }

    @Test
    public void addCarVolumeGroupInfo() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(new ArrayList<>(),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI))
                .addCarVolumeGroupInfo(TEST_CAR_VOLUME_GROUP_INFO)
                .build();

        expectWithMessage("Volume group info")
                .that(event.getCarVolumeGroupInfos())
                .containsExactly(TEST_CAR_VOLUME_GROUP_INFO);
    }

    @Test
    public void addEventType() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_ATTENUATION_ACTIVATION))
                .addEventType(CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED)
                .build();

        expectWithMessage("Event types").that(event.getEventTypes())
                .isEqualTo(CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED
                        | CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED);
    }

    @Test
    public void setExtraInfos_withNullEntry_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new CarVolumeGroupEvent.Builder(
                        List.of(TEST_CAR_VOLUME_GROUP_INFO),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI))
                        .setExtraInfos(/*ExtraInfo*/null)
                        .build()
        );

        expectWithMessage("Null Extra infos exception")
                .that(thrown).hasMessageThat().contains("Extra infos can not");
    }

    @Test
    public void setExtraInfos() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED,
                new ArrayList<>())
                .setExtraInfos(
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED))
                .build();

        expectWithMessage("Extra infos")
                .that(event.getExtraInfos())
                .containsExactly(CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_DUCKED);
    }

    @Test
    public void addExtraInfo() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED,
                new ArrayList<>())
                .addExtraInfo(CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL)
                .build();

        expectWithMessage("Add extra info")
                .that(event.getExtraInfos())
                .containsExactly(CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL);
    }

    @Test
    public void writeToParcel_andCreateFromParcel() {
        Parcel parcel = Parcel.obtain();

        TEST_CAR_VOLUME_GROUP_EVENT.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* position= */ 0);

        expectWithMessage("Car volume event write to and create from parcel")
                .that(TEST_CAR_VOLUME_GROUP_EVENT)
                .isEqualTo(CarVolumeGroupEvent.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void newArray() {
        CarVolumeGroupEvent[] events = CarVolumeGroupEvent.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Car volume group event size").that(events)
                .hasLength(3);
    }

    @Test
    public void equals_forSameContent() {
        CarVolumeGroupEvent eventWithSameContent =
                new CarVolumeGroupEvent.Builder(
                        List.of(TEST_CAR_VOLUME_GROUP_INFO),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Car volume group event with same content")
                .that(eventWithSameContent).isEqualTo(TEST_CAR_VOLUME_GROUP_EVENT);
    }

    @Test
    public void equals_forNull() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Car volume group event null content")
                .that(event.equals(null)).isFalse();
    }

    @Test
    public void describeContents() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Car volume group event contents")
                .that(event.describeContents()).isEqualTo(0);
    }

    @Test
    public void hashCode_forSameContent() {
        CarVolumeGroupEvent eventWithSameContent = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Car volume info hash with same content")
                .that(eventWithSameContent.hashCode())
                .isEqualTo(TEST_CAR_VOLUME_GROUP_EVENT.hashCode());
    }


    @Test
    public void toString_forContent() {
        CarVolumeGroupEvent event = new CarVolumeGroupEvent.Builder(
                List.of(TEST_CAR_VOLUME_GROUP_INFO),
                CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED,
                List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)).build();

        expectWithMessage("Car volume event - volume group info")
                .that(event.toString()).contains(TEST_CAR_VOLUME_GROUP_INFO.toString());
        expectWithMessage("Car volume event - event types")
                .that(event.toString()).contains(CarVolumeGroupEvent.eventTypeToString(
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED));
        expectWithMessage("Car volume event - extra info")
                .that(event.toString()).contains(CarVolumeGroupEvent.extraInfosToString(
                        List.of(CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_UI)));
    }

    @Test
    public void convertExtraInfoToFlags_thenBackToExtraInfo() {
        List<Integer> extraInfo = List.of(CarVolumeGroupEvent.EXTRA_INFO_SHOW_UI,
                CarVolumeGroupEvent.EXTRA_INFO_PLAY_SOUND,
                CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_KEYEVENT);

        expectWithMessage("Convert extra info to/from flags")
                .that(CarVolumeGroupEvent.convertFlagsToExtraInfo(
                        CarVolumeGroupEvent.convertExtraInfoToFlags(extraInfo),
                        CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED))
                .isEqualTo(extraInfo);
    }
}
