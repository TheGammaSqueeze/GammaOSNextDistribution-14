/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;

import org.hyphonate.megaaudio.common.StreamBase;

public abstract class Recorder extends StreamBase {
    private static final String TAG = Recorder.class.getSimpleName();

    protected AudioSinkProvider mSinkProvider;

    // Recording state
    /**
     * <code>true</code> if currently recording audio data
     */
    protected boolean mRecording = false;

    //
    // Recorder-specific attributes
    //
    // This value is to indicate that no explicit call to set an input preset in the builder
    // will be made.
    // Constants can be found here:
    // https://developer.android.com/reference/android/media/MediaRecorder.AudioSource
    // or preferentially in oboe/Definitions.h
    int mInputPreset = INPUT_PRESET_NONE;
    public static final int INPUT_PRESET_NONE = -1;
    public static final int INPUT_PRESET_DEFAULT = 0;
    public static final int INPUT_PRESET_GENERIC = 1;
    public static final int INPUT_PRESET_VOICE_UPLINK = 2;
    public static final int INPUT_PRESET_VOICE_DOWNLINK = 3;
    public static final int INPUT_PRESET_VOICE_CALL = 4;
    public static final int INPUT_PRESET_CAMCORDER = 5;
    public static final int INPUT_PRESET_VOICERECOGNITION = 6;
    public static final int INPUT_PRESET_VOICECOMMUNICATION = 7;
    public static final int INPUT_PRESET_REMOTE_SUBMIX = 8;
    public static final int INPUT_PRESET_UNPROCESSED = 9;
    public static final int INPUT_PRESET_VOICEPERFORMANCE = 10;

    public Recorder(AudioSinkProvider sinkProvider) {
        mSinkProvider = sinkProvider;
    }

    // It is not clear that this can be set post-create for Java
    // public abstract void setInputPreset(int preset);

    /*
     * State
     */
    public boolean isRecording() {
        return mRecording;
    }

    /*
     * Utilities
     */
    public static final int AUDIO_CHANNEL_COUNT_MAX = 30;

    public static final int AUDIO_CHANNEL_REPRESENTATION_POSITION   = 0x0;
    public static final int AUDIO_CHANNEL_REPRESENTATION_INDEX      = 0x2;

    //
    // Attributes
    //
    /**
     * Calculate the optimal buffer size for the specified channel count and sample rate
     * @param channelCount number of channels of audio data in record buffers
     * @param sampleRate sample rate of recorded data
     * @return The minimal buffer size to avoid overruns in the recording stream.
     */
    public static int calcMinBufferFramesStatic(int channelCount, int sampleRate) {
        int channelMask = Recorder.channelCountToChannelMask(channelCount);
        int bufferSizeInBytes =
                AudioRecord.getMinBufferSize (sampleRate,
                        channelMask,
                        AudioFormat.ENCODING_PCM_FLOAT);
        return bufferSizeInBytes / sampleSizeInBytes(AudioFormat.ENCODING_PCM_FLOAT);
    }

    /*
     * Channel Utils
     */
    // TODO - Consider moving these into a "Utilities" library
    /* Not part of public API */
    private static int audioChannelMaskFromRepresentationAndBits(int representation, int bits)
    {
        return ((representation << AUDIO_CHANNEL_COUNT_MAX) | bits);
    }

    /* Derive a channel mask for index assignment from a channel count.
     * Returns the matching channel mask,
     * or AUDIO_CHANNEL_NONE if the channel count is zero,
     * or AUDIO_CHANNEL_INVALID if the channel count exceeds AUDIO_CHANNEL_COUNT_MAX.
     */
    private static int audioChannelMaskForIndexAssignmentFromCount(int channel_count)
    {
        if (channel_count == 0) {
            return 0; // AUDIO_CHANNEL_NONE
        }
        if (channel_count > AUDIO_CHANNEL_COUNT_MAX) {
            return AudioFormat.CHANNEL_INVALID;
        }
        int bits = (1 << channel_count) - 1;
        return audioChannelMaskFromRepresentationAndBits(AUDIO_CHANNEL_REPRESENTATION_INDEX, bits);
    }

    /**
     * @param channelCount  The number of channels for which to generate an input position mask.
     * @return An input channel-position mask corresponding the supplied number of channels.
     */
    public static int channelCountToChannelMask(int channelCount) {
        int bits;
        switch (channelCount) {
            case 1:
                bits = AudioFormat.CHANNEL_IN_MONO;
                break;

            case 2:
                bits = AudioFormat.CHANNEL_IN_STEREO;
                break;

            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                // FIXME FCC_8
                return audioChannelMaskForIndexAssignmentFromCount(channelCount);

            default:
                return AudioFormat.CHANNEL_INVALID;
        }

        return audioChannelMaskFromRepresentationAndBits(
                AUDIO_CHANNEL_REPRESENTATION_POSITION, bits);
    }
}
