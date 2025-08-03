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

package android.media.audio.cts;

import static com.google.common.truth.Truth.assertThat;

import android.media.AudioTimestamp;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The unit tests for {@link AudioTimestamp} APIs.
 */
@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class AudioTimestampTest {
    private static final long FRAME_POSITION = 1;
    private static final long NANO_TIME = 2;

    @Test
    public void testParcelableDescribeContents() throws Exception {
        AudioTimestamp audioTimestamp = new AudioTimestamp();
        audioTimestamp.framePosition = FRAME_POSITION;
        audioTimestamp.nanoTime = NANO_TIME;
        assertThat(audioTimestamp.describeContents()).isEqualTo(0);
    }

    @Test
    public void testToString() throws Exception {
        AudioTimestamp audioTimestamp = new AudioTimestamp();
        audioTimestamp.framePosition = FRAME_POSITION;
        audioTimestamp.nanoTime = NANO_TIME;

        String result = audioTimestamp.toString();
        assertThat(result).contains("AudioTimeStamp:");
        assertThat(result).contains("framePos=" + FRAME_POSITION);
        assertThat(result).contains("nanoTime=" + NANO_TIME);
    }

    @Test
    public void testParcelizeAndDeparcelize() throws Exception {
        AudioTimestamp audioTimestamp = new AudioTimestamp();
        audioTimestamp.framePosition = FRAME_POSITION;
        audioTimestamp.nanoTime = NANO_TIME;

        final Parcel parcel = Parcel.obtain();
        audioTimestamp.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);

        AudioTimestamp targetAudioTimestamp = AudioTimestamp.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(targetAudioTimestamp.framePosition).isEqualTo(FRAME_POSITION);
        assertThat(targetAudioTimestamp.nanoTime).isEqualTo(NANO_TIME);
    }
}
