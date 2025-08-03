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

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.os.Build.VERSION.SDK_INT;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.os.Parcel;

import org.junit.Test;

public final class AudioFocusEntryUnitTest extends AbstractExpectableTestCase {

    private static final AudioFocusInfo TEST_AUDIO_FOCUS_INFO = createAudioFocusInfoForMedia();
    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int MEDIA_EMPTY_FLAG = 0;
    private static final int TEST_AUDIO_CONTEXT = 1;
    private static final int TEST_VOLUME_GROUP_ID = 2;
    private static final int MEDIA_APP_UID = 100000;
    private static final String MEDIA_CLIENT_ID = "client-id";
    private static final String MEDIA_PACKAGE_NAME = "android.car.oem";

    @Test
    public void build() {
        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN).build();

        expectWithMessage("Audio focus info from builder").that(entry.getAudioFocusInfo())
                .isEqualTo(TEST_AUDIO_FOCUS_INFO);
        expectWithMessage("Audio context from builder").that(entry.getAudioContextId())
                .isEqualTo(TEST_AUDIO_CONTEXT);
        expectWithMessage("Volume group id from builder").that(entry.getAudioVolumeGroupId())
                .isEqualTo(TEST_VOLUME_GROUP_ID);
        expectWithMessage("Audio focus results from builder").that(entry.getAudioFocusResult())
                .isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
    }

    @Test
    public void writeToParcel() {
        Parcel parcel = Parcel.obtain();

        AudioFocusEntry entry = createAndWriteEntryToParcel(parcel);

        expectWithMessage("Car volume entry from parcel")
                .that(new AudioFocusEntry(parcel)).isEqualTo(entry);
    }

    @Test
    public void createFromParcel() {
        Parcel parcel = Parcel.obtain();

        AudioFocusEntry entry = createAndWriteEntryToParcel(parcel);

        expectWithMessage("Car volume entry created from parcel")
                .that(AudioFocusEntry.CREATOR.createFromParcel(parcel)).isEqualTo(entry);
    }

    @Test
    public void setAudioFocusEntry() {
        AudioFocusInfo testSecondInfo = createAudioFocusInfo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                .setAudioFocusInfo(testSecondInfo).build();

        expectWithMessage("Second audio focus entry from builder").that(entry.getAudioFocusInfo())
                .isEqualTo(testSecondInfo);
    }

    @Test
    public void setAudioFocusEntry_withNullInfo_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                        TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioFocusInfo(/* audioFocusInfo= */ null)
        );

        expectWithMessage("Null audio focus name exception")
                .that(thrown).hasMessageThat().contains("Audio focus info");
    }

    @Test
    public void setAudioContextId() {
        int testAudioContext = 10;

        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                .setAudioContextId(testAudioContext).build();

        expectWithMessage("Set audio context from builder").that(entry.getAudioContextId())
                .isEqualTo(testAudioContext);
    }

    @Test
    public void setAudioVolumeGroupId() {
        int testVolumeGroupId = 6;
        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                .setAudioVolumeGroupId(testVolumeGroupId).build();

        expectWithMessage("Set audio volume group from builder")
                .that(entry.getAudioVolumeGroupId()).isEqualTo(testVolumeGroupId);
    }

    @Test
    public void setAudioFocusResult() {
        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                .setAudioFocusResult(AudioManager.AUDIOFOCUS_LOSS).build();

        expectWithMessage("Set audio focus results from builder")
                .that(entry.getAudioFocusResult()).isEqualTo(AudioManager.AUDIOFOCUS_LOSS);
    }

    @Test
    public void builder_withReuse_fails() {
        AudioFocusEntry.Builder builder = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN)
                .setAudioFocusResult(AudioManager.AUDIOFOCUS_LOSS);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void builder_withAudioFocusEntry() {
        AudioFocusEntry testEntry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN).build();

        AudioFocusEntry entry = new AudioFocusEntry.Builder(testEntry).build();

        expectWithMessage("Audio focus info from copy builder")
                .that(entry.getAudioFocusInfo()).isEqualTo(TEST_AUDIO_FOCUS_INFO);
        expectWithMessage("Audio context from copy builder").that(entry.getAudioContextId())
                .isEqualTo(TEST_AUDIO_CONTEXT);
        expectWithMessage("Volume group id from copy builder")
                .that(entry.getAudioVolumeGroupId()).isEqualTo(TEST_VOLUME_GROUP_ID);
        expectWithMessage("Audio focus results from copy builder")
                .that(entry.getAudioFocusResult()).isEqualTo(AudioManager.AUDIOFOCUS_GAIN);
    }

    @Test
    public void builder_withNullEntry_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new AudioFocusEntry.Builder(/* entry= */ null)
        );

        expectWithMessage("Null audio focus name exception")
                .that(thrown).hasMessageThat().contains("Audio focus entry");
    }

    private static AudioFocusInfo createAudioFocusInfoForMedia() {
        return createAudioFocusInfo(USAGE_MEDIA);
    }

    private static AudioFocusInfo createAudioFocusInfo(int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        builder.setUsage(usage);

        return new AudioFocusInfo(builder.build(), MEDIA_APP_UID, MEDIA_CLIENT_ID,
                MEDIA_PACKAGE_NAME, AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_LOSS,
                MEDIA_EMPTY_FLAG, SDK_INT);
    }

    private AudioFocusEntry createAndWriteEntryToParcel(Parcel parcel) {
        AudioFocusEntry entry = new AudioFocusEntry.Builder(TEST_AUDIO_FOCUS_INFO,
                TEST_AUDIO_CONTEXT, TEST_VOLUME_GROUP_ID, AudioManager.AUDIOFOCUS_GAIN).build();
        entry.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* pos= */ 0);
        return entry;
    }
}
