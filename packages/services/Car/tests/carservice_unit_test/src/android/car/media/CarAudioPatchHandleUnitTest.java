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

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;

import org.junit.Test;

public final class CarAudioPatchHandleUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int TEST_HANDLE_ID = 1;
    private static final String TEST_SOURCE_ADDRESS = "media_bus_device";
    private static final String TEST_SINK_ADDRESS = "FM Tuner";
    private static final CarAudioPatchHandle TEST_AUDIO_PATCH_HANDLE = new CarAudioPatchHandle(
            TEST_HANDLE_ID, TEST_SOURCE_ADDRESS, TEST_SINK_ADDRESS);

    @Test
    public void constructor_withNullSourceAddress() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarAudioPatchHandle(TEST_HANDLE_ID, /* sourceAddress= */ null,
                        TEST_SINK_ADDRESS));

        expectWithMessage("Exception for constructing car audio patch handle with null "
                + "source address").that(thrown).hasMessageThat().contains(
                        "Source's Address device can not be null");
    }

    @Test
    public void constructor_withNullSinkAddress() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarAudioPatchHandle(TEST_HANDLE_ID, TEST_SOURCE_ADDRESS,
                        /* sinkAddress= */ null));

        expectWithMessage("Exception for constructing car audio patch handle with null "
                + "sink address").that(thrown).hasMessageThat().contains(
                "Sink's Address device can not be null");
    }

    @Test
    public void toString_containsIdAndAddresses() {
        String audioPatchHandleString = TEST_AUDIO_PATCH_HANDLE.toString();

        expectWithMessage("Audio patch string for id %s", TEST_HANDLE_ID).that(
                audioPatchHandleString).contains(Integer.toString(TEST_HANDLE_ID));
        expectWithMessage("Audio patch string for source address %s", TEST_SOURCE_ADDRESS).that(
                audioPatchHandleString).contains(TEST_SOURCE_ADDRESS);
        expectWithMessage("Audio patch string for sink address %s", TEST_SINK_ADDRESS).that(
                audioPatchHandleString).contains(TEST_SINK_ADDRESS);
    }

    @Test
    public void writeToParcel_createFromParcel() {
        Parcel parcel = Parcel.obtain();

        TEST_AUDIO_PATCH_HANDLE.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        CarAudioPatchHandle handleFromParcel = CarAudioPatchHandle.CREATOR.createFromParcel(parcel);

        expectWithMessage("Handle id in car audio handle created from parcel")
                .that(handleFromParcel.getHandleId()).isEqualTo(TEST_HANDLE_ID);
        expectWithMessage("Source address in car audio handle created from parcel")
                .that(handleFromParcel.getSourceAddress()).isEqualTo(TEST_SOURCE_ADDRESS);
        expectWithMessage("Sink address in car audio handle created from parcel")
                .that(handleFromParcel.getSinkAddress()).isEqualTo(TEST_SINK_ADDRESS);
    }

    @Test
    public void newArray() {
        CarAudioPatchHandle[] handles = CarAudioPatchHandle.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Car audio patch handles size").that(handles).hasLength(3);
    }

    @Test
    public void getSourceAddress() {
        expectWithMessage("Source address").that(TEST_AUDIO_PATCH_HANDLE.getSourceAddress())
                .isEqualTo(TEST_SOURCE_ADDRESS);
    }

    @Test
    public void getSinkAddress() {
        expectWithMessage("Sink address").that(TEST_AUDIO_PATCH_HANDLE.getSinkAddress())
                .isEqualTo(TEST_SINK_ADDRESS);
    }

    @Test
    public void getHandleId() {
        expectWithMessage("Handle id").that(TEST_AUDIO_PATCH_HANDLE.getHandleId())
                .isEqualTo(TEST_HANDLE_ID);
    }
}
