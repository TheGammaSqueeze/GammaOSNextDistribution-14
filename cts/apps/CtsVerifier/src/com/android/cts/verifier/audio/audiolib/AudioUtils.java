/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio.audiolib;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;

import java.util.HashMap;

// TODO - This functionality probably exists in the framework function. Remove this and
//    use that instead.
public class AudioUtils {
    @SuppressWarnings("unused")
    private static final String TAG = "AudioUtils";

    public static int countIndexChannels(int chanConfig) {
        return Integer.bitCount(chanConfig & ~0x80000000);
    }

    public static int countToIndexMask(int chanCount) {
        return (1 << chanCount) - 1;
    }

    public static int countToOutPositionMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;

            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;

            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;

            default:
                return AudioTrack.ERROR_BAD_VALUE;
        }
    }

    public static int countToInPositionMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;

            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;

            default:
                return AudioRecord.ERROR_BAD_VALUE;
        }
    }

    // Encodings
    public static int sampleSizeInBytes(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                return 2;

            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;

            default:
                return 0;
        }
    }

    public static int calcFrameSizeInBytes(int encoding, int numChannels) {
        return sampleSizeInBytes(encoding) * numChannels;
    }

    public static native boolean isMMapSupported();
    public static native boolean isMMapExclusiveSupported();

    /*
     * Channel Mask Utilities
     */
    private static final HashMap<Integer, String> sEncodingStrings =
            new HashMap<Integer, String>();
    /**
     * A table of strings corresponding to output channel position masks
     */
    private static final HashMap<Integer, String> sOutChanPosStrings =
            new HashMap<Integer, String>();

    /**
     * A table of strings corresponding to output channel position masks
     */
    private static final HashMap<Integer, String> sInChanPosStrings =
            new HashMap<Integer, String>();

    static void initOutChanPositionStrings() {
        sOutChanPosStrings.put(AudioFormat.CHANNEL_INVALID, "CHANNEL_INVALID");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_DEFAULT, "CHANNEL_OUT_DEFAULT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_FRONT_LEFT,
                "CHANNEL_OUT_MONO"/*"CHANNEL_OUT_FRONT_LEFT"*/);
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_FRONT_RIGHT, "CHANNEL_OUT_FRONT_RIGHT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_FRONT_CENTER, "CHANNEL_OUT_FRONT_CENTER");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_LOW_FREQUENCY, "CHANNEL_OUT_LOW_FREQUENCY");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_BACK_LEFT, "CHANNEL_OUT_BACK_LEFT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_BACK_RIGHT, "CHANNEL_OUT_BACK_RIGHT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER,
                "CHANNEL_OUT_FRONT_LEFT_OF_CENTER");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER,
                "CHANNEL_OUT_FRONT_RIGHT_OF_CENTER");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_BACK_CENTER, "CHANNEL_OUT_BACK_CENTER");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_SIDE_LEFT, "CHANNEL_OUT_SIDE_LEFT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_SIDE_RIGHT, "CHANNEL_OUT_SIDE_RIGHT");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_STEREO, "CHANNEL_OUT_STEREO");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_QUAD, "CHANNEL_OUT_QUAD");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_SURROUND, "CHANNEL_OUT_SURROUND");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_5POINT1, "CHANNEL_OUT_5POINT1");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_7POINT1, "CHANNEL_OUT_7POINT1");
        sOutChanPosStrings.put(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                "CHANNEL_OUT_7POINT1_SURROUND");
    }

    static void initInChanPositionStrings() {
        sInChanPosStrings.put(AudioFormat.CHANNEL_INVALID, "CHANNEL_INVALID");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_DEFAULT, "CHANNEL_IN_DEFAULT");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_LEFT, "CHANNEL_IN_LEFT");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_RIGHT, "CHANNEL_IN_RIGHT");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_MONO, "CHANNEL_IN_MONO"/*CHANNEL_IN_FRONT*/);
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_BACK, "CHANNEL_IN_BACK");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_LEFT_PROCESSED, "CHANNEL_IN_LEFT_PROCESSED");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_RIGHT_PROCESSED, "CHANNEL_IN_RIGHT_PROCESSED");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_FRONT_PROCESSED, "CHANNEL_IN_FRONT_PROCESSED");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_BACK_PROCESSED, "CHANNEL_IN_BACK_PROCESSED");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_PRESSURE, "CHANNEL_IN_PRESSURE");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_X_AXIS, "CHANNEL_IN_X_AXIS");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_Y_AXIS, "CHANNEL_IN_Y_AXIS");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_Z_AXIS, "CHANNEL_IN_Z_AXIS");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_VOICE_UPLINK, "CHANNEL_IN_VOICE_UPLINK");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_VOICE_DNLINK, "CHANNEL_IN_VOICE_DNLINK");
        sInChanPosStrings.put(AudioFormat.CHANNEL_IN_STEREO, "CHANNEL_IN_STEREO");
    }

    static void initEncodingStrings() {
        sEncodingStrings.put(AudioFormat.ENCODING_INVALID, "ENCODING_INVALID");
        sEncodingStrings.put(AudioFormat.ENCODING_DEFAULT, "ENCODING_DEFAULT");
        sEncodingStrings.put(AudioFormat.ENCODING_PCM_16BIT, "ENCODING_PCM_16BIT");
        sEncodingStrings.put(AudioFormat.ENCODING_PCM_8BIT, "ENCODING_PCM_8BIT");
        sEncodingStrings.put(AudioFormat.ENCODING_PCM_FLOAT, "ENCODING_PCM_FLOAT");
        sEncodingStrings.put(AudioFormat.ENCODING_AC3, "ENCODING_AC3");
        sEncodingStrings.put(AudioFormat.ENCODING_E_AC3, "ENCODING_E_AC3");
        sEncodingStrings.put(AudioFormat.ENCODING_DTS, "ENCODING_DTS");
        sEncodingStrings.put(AudioFormat.ENCODING_DTS_HD, "ENCODING_DTS_HD");
        sEncodingStrings.put(AudioFormat.ENCODING_MP3, "ENCODING_MP3");
        sEncodingStrings.put(AudioFormat.ENCODING_AAC_LC, "ENCODING_AAC_LC");
        sEncodingStrings.put(AudioFormat.ENCODING_AAC_HE_V1, "ENCODING_AAC_HE_V1");
        sEncodingStrings.put(AudioFormat.ENCODING_AAC_HE_V2, "ENCODING_AAC_HE_V2");
        sEncodingStrings.put(AudioFormat.ENCODING_IEC61937, "ENCODING_IEC61937");
        sEncodingStrings.put(AudioFormat.ENCODING_DOLBY_TRUEHD, "ENCODING_DOLBY_TRUEHD");
        sEncodingStrings.put(AudioFormat.ENCODING_AAC_ELD, "ENCODING_AAC_ELD");
        sEncodingStrings.put(AudioFormat.ENCODING_AAC_XHE, "ENCODING_AAC_XHE");
        sEncodingStrings.put(AudioFormat.ENCODING_AC4, "ENCODING_AC4");
        sEncodingStrings.put(AudioFormat.ENCODING_E_AC3_JOC, "ENCODING_E_AC3_JOC");
        sEncodingStrings.put(AudioFormat.ENCODING_DOLBY_MAT, "ENCODING_DOLBY_MAT");
        sEncodingStrings.put(AudioFormat.ENCODING_OPUS, "ENCODING_OPUS");
        sEncodingStrings.put(AudioFormat.ENCODING_PCM_24BIT_PACKED, "ENCODING_PCM_24BIT_PACKED");
        sEncodingStrings.put(AudioFormat.ENCODING_PCM_32BIT, "ENCODING_PCM_32BIT");
        sEncodingStrings.put(AudioFormat.ENCODING_MPEGH_BL_L3, "ENCODING_MPEGH_BL_L3");
        sEncodingStrings.put(AudioFormat.ENCODING_MPEGH_BL_L4, "ENCODING_MPEGH_BL_L4");
        sEncodingStrings.put(AudioFormat.ENCODING_MPEGH_LC_L3, "ENCODING_MPEGH_LC_L3");
        sEncodingStrings.put(AudioFormat.ENCODING_MPEGH_LC_L4, "ENCODING_MPEGH_LC_L4");
        sEncodingStrings.put(AudioFormat.ENCODING_DTS_UHD, "ENCODING_DTS_UHD");
        sEncodingStrings.put(AudioFormat.ENCODING_DRA, "ENCODING_DRA");
    }

    static {
        initOutChanPositionStrings();
        initInChanPositionStrings();
        initEncodingStrings();
    }

    /**
     * @param channelMask An OUTPUT Positional Channel Mask
     * @return A human-readable string corresponding to the specified channel mask
     */
    public static String channelOutPositionMaskToString(int channelMask) {
        String maskString = sOutChanPosStrings.get(channelMask);
        return maskString != null ? maskString : ("0x" + Integer.toHexString(channelMask));
    }

    /**
     * @param channelMask An INPUT Positional Channel Mask
     * @return A human-readable string corresponding to the specified channel mask
     */
    public static String channelInPositionMaskToString(int channelMask) {
        String maskString = sInChanPosStrings.get(channelMask);
        return maskString != null ? maskString : ("0x" + Integer.toHexString(channelMask));
    }

    /**
     * @param channelMask An INDEX Channel Mask
     * @return A human-readable string corresponding to the specified channel mask
     */
    public static String channelIndexMaskToString(int channelMask) {
        return "0x" + Integer.toHexString(channelMask);
    }

    /**
     * @param encoding An audio encoding constant
     * @return A human-readable string corresponding to the specified encoding value
     */
    public static String encodingToString(int encoding) {
        String encodingString = sEncodingStrings.get(encoding);
        return encodingString != null ? encodingString : ("0x" + Integer.toHexString(encoding));
    }
}