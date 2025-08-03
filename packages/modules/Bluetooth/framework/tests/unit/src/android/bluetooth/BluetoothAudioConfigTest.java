/*
 * Copyright 2022 The Android Open Source Project
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

package android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.media.AudioFormat;
import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Test cases for {@link BluetoothAudioConfig}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothAudioConfigTest {

    private static final int TEST_SAMPLE_RATE = 44;
    private static final int TEST_CHANNEL_COUNT = 1;

    @Test
    public void createBluetoothAudioConfig() {
        BluetoothAudioConfig audioConfig =
                new BluetoothAudioConfig(
                        TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT, AudioFormat.ENCODING_PCM_16BIT);

        assertThat(audioConfig.getSampleRate()).isEqualTo(TEST_SAMPLE_RATE);
        assertThat(audioConfig.getChannelConfig()).isEqualTo(TEST_CHANNEL_COUNT);
        assertThat(audioConfig.getAudioFormat()).isEqualTo(AudioFormat.ENCODING_PCM_16BIT);
    }

    @Test
    public void writeToParcel() {
        BluetoothAudioConfig originalConfig =
                new BluetoothAudioConfig(
                        TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT, AudioFormat.ENCODING_PCM_16BIT);

        Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BluetoothAudioConfig configOut = BluetoothAudioConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(configOut.getSampleRate()).isEqualTo(originalConfig.getSampleRate());
        assertThat(configOut.getChannelConfig()).isEqualTo(originalConfig.getChannelConfig());
        assertThat(configOut.getAudioFormat()).isEqualTo(originalConfig.getAudioFormat());
    }

    @Test
    public void bluetoothAudioConfigHashCode() {
        BluetoothAudioConfig audioConfig =
                new BluetoothAudioConfig(
                        TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT, AudioFormat.ENCODING_PCM_16BIT);

        int hashCode =
                audioConfig.getSampleRate()
                        | (audioConfig.getChannelConfig() << 24)
                        | (audioConfig.getAudioFormat() << 28);
        int describeContents = 0;

        assertThat(audioConfig.hashCode()).isEqualTo(hashCode);
        assertThat(audioConfig.describeContents()).isEqualTo(describeContents);
    }

    @Test
    public void bluetoothAudioConfigToString() {
        BluetoothAudioConfig audioConfig =
                new BluetoothAudioConfig(
                        TEST_SAMPLE_RATE, TEST_CHANNEL_COUNT, AudioFormat.ENCODING_PCM_16BIT);

        String audioConfigString = audioConfig.toString();
        String expectedToString =
                "{mSampleRate:"
                        + audioConfig.getSampleRate()
                        + ",mChannelConfig:"
                        + audioConfig.getChannelConfig()
                        + ",mAudioFormat:"
                        + audioConfig.getAudioFormat()
                        + "}";

        assertThat(audioConfigString).isEqualTo(expectedToString);
    }
}
