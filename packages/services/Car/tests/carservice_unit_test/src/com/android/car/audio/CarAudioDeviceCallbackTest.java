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

package com.android.car.audio;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioDeviceInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioDeviceCallbackTest extends AbstractExpectableTestCase {

    @Mock
    CarAudioService mMockCarAudioService;
    @Mock
    AudioDeviceInfo mTestAudioDeviceInfo1;
    @Mock
    AudioDeviceInfo mTestAudioDeviceInfo2;

    @Test
    public void constructor_withNullService_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioDeviceCallback(/* carAudioService= */ null));

        expectWithMessage("Null car audio service exception").that(thrown)
                .hasMessageThat().contains("Car audio service can not");
    }

    @Test
    public void onAudioDevicesAdded() {
        CarAudioDeviceCallback callback = new CarAudioDeviceCallback(mMockCarAudioService);

        callback.onAudioDevicesAdded(getTestAudioDevices());

        expectWithMessage("Added audio devices")
                .that(captureAddedAudioDevices()).containsExactly(mTestAudioDeviceInfo1,
                        mTestAudioDeviceInfo2);
    }

    @Test
    public void onAudioDevicesRemoved() {
        CarAudioDeviceCallback callback = new CarAudioDeviceCallback(mMockCarAudioService);

        callback.onAudioDevicesRemoved(getTestAudioDevices());

        expectWithMessage("Removed audio devices")
                .that(captureRemovedAudioDevices()).containsExactly(mTestAudioDeviceInfo1,
                        mTestAudioDeviceInfo2);
    }

    private List<AudioDeviceInfo> captureAddedAudioDevices() {
        ArgumentCaptor<AudioDeviceInfo[]> audioDevicesCaptor =
                ArgumentCaptor.forClass(AudioDeviceInfo[].class);
        verify(mMockCarAudioService).audioDevicesAdded(audioDevicesCaptor.capture());
        return Arrays.asList(audioDevicesCaptor.getValue());
    }

    private List<AudioDeviceInfo> captureRemovedAudioDevices() {
        ArgumentCaptor<AudioDeviceInfo[]> audioDevicesCaptor =
                ArgumentCaptor.forClass(AudioDeviceInfo[].class);
        verify(mMockCarAudioService).audioDevicesRemoved(audioDevicesCaptor.capture());
        return Arrays.asList(audioDevicesCaptor.getValue());
    }

    private AudioDeviceInfo[] getTestAudioDevices() {
        return new AudioDeviceInfo[] {mTestAudioDeviceInfo1, mTestAudioDeviceInfo2};
    }
}
