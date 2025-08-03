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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

/**
 * Class containing encoder input resources.
 */
public class EncoderInput {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    // files are in WorkDir.getMediaDirString();
    private static final RawResource INPUT_VIDEO_FILE =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "bbb_cif_yuv420p_30fps.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(1)
                    .setColorFormat(ImageFormat.YUV_420_888)
                    .build();
    private static final RawResource INPUT_VIDEO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "cosmat_cif_24fps_yuv420p16le.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(2)
                    .setColorFormat(ImageFormat.YCBCR_P010)
                    .build();

    /* Note: The mSampleRate and mChannelCount fields of RawResource are not used by the tests
    the way mWidth and mHeight are used. mWidth and mHeight is used by fillImage() to select a
    portion of the frame or duplicate the frame as tiles depending on testWidth and testHeight.
    Ideally mSampleRate and mChannelCount information should be used to resample and perform
    channel-conversion basing on testSampleRate and testChannelCount. Instead the test considers
    the resource file to be of testSampleRate and testChannelCount. */
    private static final RawResource INPUT_AUDIO_FILE =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "bbb_2ch_44kHz_s16le.raw", true)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .setBytesPerSample(2)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    private static final RawResource INPUT_AUDIO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "audio/sd_2ch_48kHz_f32le.raw", true)
                    .setSampleRate(48000)
                    .setChannelCount(2)
                    .setBytesPerSample(4)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build();

    public static RawResource getRawResource(EncoderConfigParams cfg) {
        if (cfg.mIsAudio) {
            if (cfg.mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                return INPUT_AUDIO_FILE_HBD;
            } else if (cfg.mPcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                return INPUT_AUDIO_FILE;
            }
        } else {
            if (cfg.mColorFormat == COLOR_FormatYUV420Flexible) {
                return INPUT_VIDEO_FILE;
            } else if (cfg.mColorFormat == COLOR_FormatYUVP010) {
                return INPUT_VIDEO_FILE_HBD;
            }
        }
        return null;
    }
}
