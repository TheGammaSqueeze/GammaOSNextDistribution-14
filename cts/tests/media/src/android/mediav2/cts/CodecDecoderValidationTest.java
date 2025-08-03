/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ANY;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_DEFAULT;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_HW;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The tests accepts multiple test vectors packaged in different way. For instance, Avc elementary
 * stream can be packed in mp4, avi, mkv, ts, 3gp, webm and so on. These clips when decoded, are
 * expected to yield same output. Similarly for Vpx, av1, no-show frame can be packaged in to
 * separate access units or can be combined with a display frame in to one access unit. Both these
 * scenarios are expected to give same output.
 * <p>
 * The test extracts and decodes the stream from all of its listed containers and verifies that
 * they produce the same output. In short, the tests validate extractors, codecs together.
 * <p>
 * Additionally, as the test runs mediacodec in byte buffer mode,
 * <ul>
 *     <li>For normative video media types, the test expects the decoded output to be identical to
 *     reference decoded output. The reference decoded output is represented by its CRC32
 *     checksum and is sent to the test as a parameter along with the test clip.</li>
 *     <li>For non normative media types, the decoded output is checked for consistency.</li>
 *     <li>For lossless audio media types, the test verifies if the rms error between input and
 *     output is 0.</li>
 *     <li>For lossy audio media types, the test verifies if the rms error is within 5% of
 *     reference rms error. The reference value is computed using reference decoder and is sent
 *     to the test as a parameter along with the test clip.</li>
 *     <li>For all video components, the test expects the output timestamp list to be identical to
 *     input timestamp list.</li>
 *     <li>For all audio components, the test expects the output timestamps to be strictly
 *     increasing.</li>
 *     <li>The test also checks correctness of essential keys of output format returned by
 *     mediacodec.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class CodecDecoderValidationTest extends CodecDecoderTestBase {
    private static final String MEDIA_TYPE_RAW = MediaFormat.MIMETYPE_AUDIO_RAW;
    private static final String MEDIA_TYPE_AMRNB = MediaFormat.MIMETYPE_AUDIO_AMR_NB;
    private static final String MEDIA_TYPE_AMRWB = MediaFormat.MIMETYPE_AUDIO_AMR_WB;
    private static final String MEDIA_TYPE_MP3 = MediaFormat.MIMETYPE_AUDIO_MPEG;
    private static final String MEDIA_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final String MEDIA_TYPE_FLAC = MediaFormat.MIMETYPE_AUDIO_FLAC;
    private static final String MEDIA_TYPE_VORBIS = MediaFormat.MIMETYPE_AUDIO_VORBIS;
    private static final String MEDIA_TYPE_OPUS = MediaFormat.MIMETYPE_AUDIO_OPUS;
    private static final String MEDIA_TYPE_MPEG2 = MediaFormat.MIMETYPE_VIDEO_MPEG2;
    private static final String MEDIA_TYPE_H263 = MediaFormat.MIMETYPE_VIDEO_H263;
    private static final String MEDIA_TYPE_MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;
    private static final String MEDIA_TYPE_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MEDIA_TYPE_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String MEDIA_TYPE_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String MEDIA_TYPE_VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
    private static final String MEDIA_TYPE_AV1 = MediaFormat.MIMETYPE_VIDEO_AV1;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private final String[] mSrcFiles;
    private final String mRefFile;
    private final float mRmsError;
    private final long mRefCRC;
    private final int mSampleRate;
    private final int mChannelCount;
    private final int mWidth;
    private final int mHeight;
    private final SupportClass mSupportRequirements;

    public CodecDecoderValidationTest(String decoder, String mediaType, String[] srcFiles,
            String refFile, float rmsError, long refCRC, int sampleRate, int channelCount,
            int width, int height, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, null, allTestParams);
        mSrcFiles = srcFiles;
        mRefFile = MEDIA_DIR + refFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mWidth = width;
        mHeight = height;
        mSupportRequirements = supportRequirements;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, array list of test files (underlying elementary stream is same, except they
        // are placed in different containers), ref file, rms error, checksum, sample rate,
        // channel count, width, height, SupportClass
        // TODO(b/275171549) Add tests as per TV multimedia requirements in 2.3.2
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // vp9 test vectors with no-show frames signalled in alternate ways
                {MEDIA_TYPE_VP9, new String[]{"bbb_340x280_768kbps_30fps_vp9.webm",
                        "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm"},
                        null, -1.0f, 4122701060L, -1, -1, 340, 280, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_520x390_1mbps_30fps_vp9.webm",
                        "bbb_520x390_1mbps_30fps_split_non_display_frame_vp9.webm"},
                        null, -1.0f, 1201859039L, -1, -1, 520, 390, CODEC_ALL},

                // mpeg2 test vectors with interlaced fields signalled in alternate ways
                {MEDIA_TYPE_MPEG2, new String[]{
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts",
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4"},
                        null, -1.0f, -1L, -1, -1, 512, 288, CODEC_ALL},

                // misc mp3 test vectors
                {MEDIA_TYPE_MP3, new String[]{"bbb_1ch_16kHz_lame_vbr.mp3"},
                        "bbb_1ch_16kHz_s16le.raw", 119.256073f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_MP3, new String[]{"bbb_2ch_44kHz_lame_vbr.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 53.069080f, -1L, 44100, 2, -1, -1, CODEC_ALL},

                // mp3 test vectors with CRC
                {MEDIA_TYPE_MP3, new String[]{"bbb_2ch_44kHz_lame_crc.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 104.089027f, -1L, 44100, 2, -1, -1, CODEC_ALL},

                // video test vectors of non standard sizes
                {MEDIA_TYPE_MPEG2, new String[]{"bbb_642x642_2mbps_30fps_mpeg2.mp4"},
                        null, -1.0f, -1L, -1, -1, 642, 642,
                        MediaUtils.isTv() ? CODEC_ANY : CODEC_OPTIONAL},
                {MEDIA_TYPE_AVC, new String[]{"bbb_642x642_1mbps_30fps_avc.mp4"},
                        null, -1.0f, 3947092788L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_VP8, new String[]{"bbb_642x642_1mbps_30fps_vp8.webm"},
                        null, -1.0f, 516982978L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_HEVC, new String[]{"bbb_642x642_768kbps_30fps_hevc.mp4"},
                        null, -1.0f, 3018465268L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_VP9, new String[]{"bbb_642x642_768kbps_30fps_vp9.webm"},
                        null, -1.0f, 4032809269L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_AV1, new String[]{"bbb_642x642_768kbps_30fps_av1.mp4"},
                        null, -1.0f, 3684481474L, -1, -1, 642, 642, CODEC_ANY},
                {MEDIA_TYPE_MPEG4, new String[]{"bbb_130x130_192kbps_15fps_mpeg4.mp4"},
                        null, -1.0f, -1L, -1, -1, 130, 130, CODEC_ANY},

                // video test vectors covering cdd requirements
                // @CddTest(requirement="5.3.1/C-1-1")
                {MEDIA_TYPE_MPEG2, new String[]{"bbb_1920x1080_30fps_mpeg2_main_high.mp4"}, null,
                        -1.0f, -1L, -1, -1, 1920, 1080,
                        MediaUtils.isTv() ? CODEC_ANY : CODEC_OPTIONAL},

                // @CddTest(requirement="5.3.2/C-1-1")
                {MEDIA_TYPE_H263, new String[]{"bbb_352x288_384kbps_30fps_h263_baseline_l3.mp4"},
                        null, -1.0f, -1L, -1, -1, 352, 288, CODEC_ALL},
                {MEDIA_TYPE_H263, new String[]{"bbb_176x144_125kbps_15fps_h263_baseline_l45.mkv"},
                        null, -1.0f, -1L, -1, -1, 176, 144, CODEC_ALL},

                // @CddTest(requirement="5.3.3/C-1-1")
                {MEDIA_TYPE_MPEG4, new String[]{"bbb_352x288_384kbps_30fps_mpeg4_simple_l3.mp4"},
                        null, -1.0f, -1L, -1, -1, 352, 288, CODEC_ALL},

                // @CddTest(requirements={"5.3.4/C-1-1", "5.3.4/C-1-2", "5.3.4/C-2-1"})
                {MEDIA_TYPE_AVC, new String[]{"bbb_320x240_30fps_avc_baseline_l13.mp4"}, null,
                        -1.0f, 2227756491L, -1, -1, 320, 240, CODEC_ALL},
                {MEDIA_TYPE_AVC, new String[]{"bbb_320x240_30fps_avc_main_l31.mp4"}, null, -1.0f,
                        3167475817L, -1, -1, 320, 240, CODEC_ALL},
                {MEDIA_TYPE_AVC, new String[]{"bbb_720x480_30fps_avc_baseline_l30.mp4"}, null,
                        -1.0f, 256699624L, -1, -1, 720, 480, CODEC_ALL},
                {MEDIA_TYPE_AVC, new String[]{"bbb_720x480_30fps_avc_main_l31.mp4"}, null, -1.0f,
                        1729385096L, -1, -1, 720, 480, CODEC_ALL},
                // 5.3.4/C-1-2 mandates 720p support for avc decoders, hence this is being tested
                // without any resolution check unlike the higher resolution tests for other codecs
                {MEDIA_TYPE_AVC, new String[]{"bbb_1280x720_30fps_avc_baseline_l31.mp4"}, null,
                        -1.0f, 4290313980L, -1, -1, 1280, 720, CODEC_ALL},
                {MEDIA_TYPE_AVC, new String[]{"bbb_1280x720_30fps_avc_main_l31.mp4"}, null, -1.0f,
                        3895426718L, -1, -1, 1280, 720, CODEC_ALL},

                // @CddTest(requirement="5.3.5/C-1-1")
                {MEDIA_TYPE_HEVC, new String[]{"bbb_352x288_30fps_hevc_main_l2.mp4"}, null, -1.0f,
                        40958220L, -1, -1, 352, 288, CODEC_ALL},
                {MEDIA_TYPE_HEVC, new String[]{"bbb_720x480_30fps_hevc_main_l3.mp4"}, null, -1.0f,
                        3167173427L, -1, -1, 720, 480, CODEC_ALL},

                // @CddTest(requirement="5.3.6/C-1-1")
                {MEDIA_TYPE_VP8, new String[]{"bbb_320x180_30fps_vp8.mkv"}, null, -1.0f,
                        434981332L, -1, -1, 320, 180, CODEC_ALL},
                {MEDIA_TYPE_VP8, new String[]{"bbb_640x360_512kbps_30fps_vp8.webm"}, null, -1.0f,
                        1625674868L, -1, -1, 640, 360, CODEC_ALL},

                // @CddTest(requirement="5.3.7/C-1-1")
                {MEDIA_TYPE_VP9, new String[]{"bbb_320x180_30fps_vp9.mkv"}, null, -1.0f,
                        2746035687L, -1, -1, 320, 180, CODEC_ALL},
                {MEDIA_TYPE_VP9, new String[]{"bbb_640x360_512kbps_30fps_vp9.webm"}, null, -1.0f,
                        2974952943L, -1, -1, 640, 360, CODEC_ALL},

                // @CddTest(requirement="5.3.9/C-1-1")
                {MEDIA_TYPE_AV1, new String[]{"cosmat_720x480_30fps_av1_10bit.mkv"}, null,
                        -1.0f, 2380523095L, -1, -1, 720, 480, CODEC_ALL},
                {MEDIA_TYPE_AV1, new String[]{"bbb_720x480_30fps_av1.mkv"}, null, -1.0f,
                        3229978305L, -1, -1, 720, 480, CODEC_ALL},


                // audio test vectors covering cdd sec 5.1.3
                // amrnb
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_12.2kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_10.2kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_7.95kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_7.40kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_6.70kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_5.90kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_5.15kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRNB, new String[]{"audio/bbb_mono_8kHz_4.75kbps_amrnb.3gp"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},

                // amrwb
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_6.6kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_8.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_12.65kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_14.25kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_15.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_18.25kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_19.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_23.05kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_AMRWB, new String[]{"audio/bbb_mono_16kHz_23.85kbps_amrwb.3gp"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},

                // opus
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_1ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_2ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_5ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_8kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_12kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_16kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_24kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_32kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_OPUS, new String[]{"audio/bbb_6ch_48kHz_opus.ogg"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_ALL},

                // vorbis
                //TODO(b/285072724) Review the following once CDD specifies vorbis requirements
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_8kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_12kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 12000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_16kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_24kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_32kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_1ch_48kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_8kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_12kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 12000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_16kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_24kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_32kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/bbb_2ch_48kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/highres_1ch_96kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 96000, 1, -1, -1,
                        MediaUtils.isWatch() ? CODEC_OPTIONAL : CODEC_ALL},
                {MEDIA_TYPE_VORBIS, new String[]{"audio/highres_2ch_96kHz_q10_vorbis.ogg"},
                        null, -1.0f, -1L, 96000, 2, -1, -1,
                        MediaUtils.isWatch() ? CODEC_OPTIONAL : CODEC_ALL},

                // flac
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_8kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_12kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_12kHz_s16le_3s.raw", 0.0f, -1L, 12000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_16kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_22kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_24kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_32kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_44kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_1ch_48kHz_lvl4_flac.mka"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_8kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_12kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_12kHz_s16le_3s.raw", 0.0f, -1L, 12000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_16kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_22kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_24kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_32kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_44kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/bbb_2ch_48kHz_lvl4_flac.mka"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_96kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_176kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_1ch_192kHz_lvl4_flac.mka"},
                        "audio/highres_1ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_96kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_176kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/highres_2ch_192kHz_lvl4_flac.mka"},
                        "audio/highres_2ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_FLAC, new String[]{"audio/sd_2ch_48kHz_lvl4_flac.mka"},
                        "audio/sd_2ch_48kHz_s16le.raw", 1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},

                // raw
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_8kHz.wav"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_16kHz.wav"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_22kHz.wav"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_24kHz.wav"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_32kHz.wav"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_44kHz.wav"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_1ch_48kHz.wav"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 1, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_8kHz.wav"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 0.0f, -1L, 8000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_16kHz.wav"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 0.0f, -1L, 16000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_22kHz.wav"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 0.0f, -1L, 22050, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_24kHz.wav"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 0.0f, -1L, 24000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_32kHz.wav"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 0.0f, -1L, 32000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_44kHz.wav"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 0.0f, -1L, 44100, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bbb_2ch_48kHz.wav"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 0.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_96kHz.wav"},
                        "audio/highres_1ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_96kHz.wav"},
                        "audio/highres_2ch_96kHz_s16le_5s.raw", 0.0f, -1L, 96000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/sd_2ch_48kHz.wav"},
                        "audio/sd_2ch_48kHz_s16le.raw", 1.0f, -1L, 48000, 2, -1, -1, CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bellezza_2ch_48kHz_s32le.wav"},
                        "audio/bellezza_2ch_48kHz_s16le.raw", 1.0f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_RAW, new String[]{"audio/bellezza_2ch_48kHz_s24le.wav"},
                        "audio/bellezza_2ch_48kHz_s16le.raw", 1.0f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},

                // aac-lc
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_8kHz_s16le_3s.raw", 26.910906f, -1L, 8000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_12kHz_s16le_3s.raw", 23.380817f, -1L, 12000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", 21.368309f, -1L, 16000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 25.995440f, -1L, 22050, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 26.373266f, -1L, 24000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 28.642658f, -1L, 32000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 29.294861f, -1L, 44100, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 29.335669f, -1L, 48000, 1, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_8kHz_s16le_3s.raw", 26.381552f, -1L, 8000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_12kHz_s16le_3s.raw", 21.934900f, -1L, 12000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", 22.072184f, -1L, 16000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 25.334206f, -1L, 22050, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 25.653538f, -1L, 24000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 27.312286f, -1L, 32000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 27.316111f, -1L, 44100, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 27.684767f, -1L, 48000, 2, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_8kHz_s16le_3s.raw", 43.121964f, -1L, 8000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_12kHz_s16le_3s.raw", 35.983891f, -1L, 12000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_16kHz_s16le_3s.raw", 32.720196f, -1L, 16000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_22kHz_s16le_3s.raw", 39.286514f, -1L, 22050, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_24kHz_s16le_3s.raw", 40.963005f, -1L, 24000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_32kHz_s16le_3s.raw", 49.437782f, -1L, 32000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_44kHz_s16le_3s.raw", 43.891609f, -1L, 44100, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_5ch_48kHz_s16le_3s.raw", 44.275997f, -1L, 48000, 5, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_8kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_8kHz_s16le_3s.raw", 39.666485f, -1L, 8000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_12kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_12kHz_s16le_3s.raw", 34.979305f, -1L, 12000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_16kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_16kHz_s16le_3s.raw", 29.069729f, -1L, 16000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_22kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_22kHz_s16le_3s.raw", 29.440094f, -1L, 22050, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_24kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_24kHz_s16le_3s.raw", 30.333755f, -1L, 24000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_32kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_32kHz_s16le_3s.raw", 33.927166f, -1L, 32000, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_44kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_44kHz_s16le_3s.raw", 31.733339f, -1L, 44100, 6, -1, -1,
                        CODEC_ALL},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_48kHz_aac_lc.m4a"},
                        "audio/bbb_6ch_48kHz_s16le_3s.raw", 31.033596f, -1L, 48000, 6, -1, -1,
                        CODEC_ALL},

                // aac-he
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_5ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 5, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_16kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 16000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_22kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 22050, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_24kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 24000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_32kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 32000, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_44kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 44100, 6, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_6ch_48kHz_aac_he.m4a"},
                        null, -1.0f, -1L, 48000, 6, -1, -1, CODEC_DEFAULT},

                // aac-hev2
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_hev2.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},

                // aac-eld
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_16kHz_s16le_3s.raw", -1.000000f, -1L, 16000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_22kHz_s16le_3s.raw", 24.969662f, -1L, 22050, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_24kHz_s16le_3s.raw", 26.498655f, -1L, 24000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_32kHz_s16le_3s.raw", 31.468872f, -1L, 32000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_44kHz_s16le_3s.raw", 33.866409f, -1L, 44100, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_aac_eld.m4a"},
                        "audio/bbb_1ch_48kHz_s16le_3s.raw", 33.148113f, -1L, 48000, 1, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_16kHz_s16le_3s.raw", -1.000000f, -1L, 16000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_22kHz_s16le_3s.raw", 24.979313f, -1L, 22050, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_24kHz_s16le_3s.raw", 26.977774f, -1L, 24000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_32kHz_s16le_3s.raw", 27.790754f, -1L, 32000, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_44kHz_s16le_3s.raw", 29.236626f, -1L, 44100, 2, -1, -1,
                        CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_aac_eld.m4a"},
                        "audio/bbb_2ch_48kHz_s16le_3s.raw", 29.183796f, -1L, 48000, 2, -1, -1,
                        CODEC_DEFAULT},

                // aac-usac
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_8kHz_usac.m4a"},
                        null, -1.0f, -1L, 8000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_16kHz_usac.m4a"},
                        null, -1.0f, -1L, 16000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_22kHz_usac.m4a"},
                        null, -1.0f, -1L, 22050, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_24kHz_usac.m4a"},
                        null, -1.0f, -1L, 24000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_32kHz_usac.m4a"},
                        null, -1.0f, -1L, 32000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_44kHz_usac.m4a"},
                        null, -1.0f, -1L, 44100, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_1ch_48kHz_usac.m4a"},
                        null, -1.0f, -1L, 48000, 1, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_8kHz_usac.m4a"},
                        null, -1.0f, -1L, 8000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_16kHz_usac.m4a"},
                        null, -1.0f, -1L, 16000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_22kHz_usac.m4a"},
                        null, -1.0f, -1L, 22050, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_24kHz_usac.m4a"},
                        null, -1.0f, -1L, 24000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_32kHz_usac.m4a"},
                        null, -1.0f, -1L, 32000, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_44kHz_usac.m4a"},
                        null, -1.0f, -1L, 44100, 2, -1, -1, CODEC_DEFAULT},
                {MEDIA_TYPE_AAC, new String[]{"audio/bbb_2ch_48kHz_usac.m4a"},
                        null, -1.0f, -1L, 48000, 2, -1, -1, CODEC_DEFAULT},
        }));
        if (IS_AT_LEAST_U) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    // Support for 176kHz and 192kHz for c2.android.raw.decoder was added in
                    // Android U
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_176kHz.wav"},
                            "audio/highres_1ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 1, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_1ch_192kHz.wav"},
                            "audio/highres_1ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 1, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_176kHz.wav"},
                            "audio/highres_2ch_176kHz_s16le_5s.raw", 0.0f, -1L, 176400, 2, -1, -1,
                            CODEC_ALL},
                    {MEDIA_TYPE_RAW, new String[]{"audio/highres_2ch_192kHz.wav"},
                            "audio/highres_2ch_192kHz_s16le_5s.raw", 0.0f, -1L, 192000, 2, -1, -1,
                            CODEC_ALL},
            }));
        }

        // video test vectors covering cdd requirements
        if (MAX_DISPLAY_HEIGHT_LAND >= 2160) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    // @CddTest(requirements={"5.3.5/C-1-2", "5.3.5/C-2-1"})
                    {MEDIA_TYPE_HEVC, new String[]{"bbb_3840x2160_30fps_hevc_main_l50.mp4"}, null,
                            -1.0f, 2312004815L, -1, -1, 3840, 2160, CODEC_ANY},
                    // @CddTest(requirements={"5.3.7/C-2-1", "5.3.7/C-3-1"})
                    {MEDIA_TYPE_VP9, new String[]{"bbb_3840x2160_30fps_vp9.mkv"}, null, -1.0f,
                            279585450L, -1, -1, 3840, 2160, CODEC_ANY},
                    // @CddTest(requirements={"5.3.9/C-2-2"})
                    {MEDIA_TYPE_AV1, new String[]{"bbb_3840x2160_30fps_av1.mkv"}, null, -1.0f,
                            100543644L, -1, -1, 3840, 2160, CODEC_OPTIONAL},
                    {MEDIA_TYPE_AV1, new String[]{"cosmat_3840x2160_30fps_av1_10bit.mkv"}, null,
                            -1.0f, 4214931794L, -1, -1, 3840, 2160, CODEC_OPTIONAL},
            }));
        }
        if (MAX_DISPLAY_HEIGHT_LAND >= 1080) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    // @CddTest(requirement="5.3.4/C-2-2")
                    {MEDIA_TYPE_AVC, new String[]{"bbb_1920x1080_avc_baseline_l40.mp4"}, null,
                            -1.0f, 1332773556L, -1, -1, 1920, 1080, CODEC_ANY},
                    {MEDIA_TYPE_AVC, new String[]{"bbb_1920x1080_avc_main_l40.mp4"}, null, -1.0f,
                            2656846432L, -1, -1, 1920, 1080, CODEC_ANY},
                    // @CddTest(requirements={"5.3.5/C-1-2", "5.3.5/C-2-1"})
                    {MEDIA_TYPE_HEVC, new String[]{"bbb_1920x1080_hevc_main_l40.mp4"}, null,
                            -1.0f, 3214459078L, -1, -1, 1920, 1080, CODEC_ANY},
                    // @CddTest(requirement="5.3.6/C-2-2")
                    {MEDIA_TYPE_VP8, new String[]{"bbb_1920x1080_30fps_vp8.mkv"}, null, -1.0f,
                            2302247702L, -1, -1, 1920, 1080, CODEC_ANY},
                    // @CddTest(requirements={"5.3.7/C-2-1", "5.3.7/C-3-1"})
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1920x1080_vp9_main_l40.mkv"}, null, -1.0f,
                            2637993192L, -1, -1, 1920, 1080, CODEC_ANY},
                    // @CddTest(requirements={"5.3.9/C-2-2"})
                    {MEDIA_TYPE_AV1, new String[]{"bbb_1920x1080_30fps_av1.mkv"}, null, -1.0f,
                            3428220318L, -1, -1, 1920, 1080, CODEC_HW},
                    {MEDIA_TYPE_AV1, new String[]{"cosmat_1920x1080_30fps_av1_10bit.mkv"}, null,
                            -1.0f, 3477727836L, -1, -1, 1920, 1080, CODEC_HW},
            }));
        }
        if (MAX_DISPLAY_HEIGHT_LAND >= 720) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    // @CddTest(requirement="5.3.5/C-1-2")
                    {MEDIA_TYPE_HEVC, new String[]{"bbb_1280x720_1mbps_30fps_hevc_nob.mp4"},
                            null, -1.0f, 3576783828L, -1, -1, 1280, 720, CODEC_ANY},
                    // @CddTest(requirement="5.3.6/C-2-1")
                    {MEDIA_TYPE_VP8, new String[]{"bbb_1280x720_30fps_vp8.mkv"}, null, -1.0f,
                            2390565854L, -1, -1, 1280, 720, CODEC_ANY},
                    // @CddTest(requirements={"5.3.7/C-2-1", "5.3.7/C-3-1"})
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_2000kbps_30fps_vp9.webm"}, null,
                            -1.0f, 3902485256L, -1, -1, 1280, 720, CODEC_ANY},
                    // @CddTest(requirements={"5.3.9/C-2-1"})
                    {MEDIA_TYPE_AV1, new String[]{"bbb_1280x720_1mbps_30fps_av1.webm"}, null, -1.0f,
                            4202081555L, -1, -1, 1280, 720, CODEC_HW},
                    {MEDIA_TYPE_AV1, new String[]{"cosmat_1280x720_24fps_1200kbps_av1_10bit.mkv"},
                            null, -1.0f, 2039973562L, -1, -1, 1280, 720, CODEC_HW},

                    // vp9 test vectors with AQ mode enabled
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_800kbps_30fps_vp9.webm"},
                            null, -1.0f, 1319105122L, -1, -1, 1280, 720, CODEC_ANY},
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_1200kbps_30fps_vp9.webm"},
                            null, -1.0f, 4128150660L, -1, -1, 1280, 720, CODEC_ANY},
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_1600kbps_30fps_vp9.webm"},
                            null, -1.0f, 156928091L, -1, -1, 1280, 720, CODEC_ANY},
                    {MEDIA_TYPE_VP9, new String[]{"bbb_1280x720_2000kbps_30fps_vp9.webm"},
                            null, -1.0f, 3902485256L, -1, -1, 1280, 720, CODEC_ANY},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Extract, Decode and Validate. Check description of class {@link CodecDecoderValidationTest}
     */
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    // "5.1.3", "5.1.2/C-1-11" are covered partially
    @CddTest(requirements = {"5.1.2/C-1-1", "5.1.2/C-1-2", "5.1.2/C-1-3", "5.1.2/C-1-4",
            "5.1.2/C-1-11", "5.1.2/C-1-5", "5.1.2/C-1-6", "5.1.2/C-1-8", "5.1.2/C-1-9",
            "5.1.2/C-1-10", "5.1.2/C-2-1", "5.1.2/C-6-1", "5.1.3", "5.3.1/C-1-1", "5.3.2/C-1-1",
            "5.3.3/C-1-1", "5.3.4/C-1-1", "5.3.4/C-1-2", "5.3.4/C-2-1", "5.3.4/C-2-2",
            "5.3.5/C-1-1", "5.3.5/C-1-2", "5.3.5/C-2-1", "5.3.6/C-1-1", "5.3.6/C-2-1",
            "5.3.6/C-2-2", "5.3.7/C-1-1", "5.3.7/C-2-1", "5.3.7/C-3-1", "5.3.9/C-1-1",
            "5.3.9/C-2-1", "5.3.9/C-2-2"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeAndValidate() throws IOException, InterruptedException {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (String file : mSrcFiles) {
            formats.add(setUpSource(MEDIA_DIR + file));
            mExtractor.release();
        }
        checkFormatSupport(mCodecName, mMediaType, false, formats, null, mSupportRequirements);
        {
            OutputManager ref = new OutputManager();
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            mSaveToMem = true;
            int loopCounter = 0;
            for (String file : mSrcFiles) {
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mCodec = MediaCodec.createByCodecName(mCodecName);
                MediaFormat format = setUpSource(MEDIA_DIR + file);
                configureCodec(format, false, true, false);
                mCodec.start();
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mOutFormat = mCodec.getOutputFormat();
                mCodec.stop();
                mCodec.release();
                mExtractor.release();
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(mOutputBuff) :
                        ref.equals(mOutputBuff))) {
                    fail("Decoder output received for file " + mSrcFiles[0]
                            + " is not identical to the output received for file " + file + "\n"
                            + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
                }
                assertEquals("Output sample rate is different from configured sample rate \n"
                                + mTestConfig + mTestEnv, mSampleRate,
                        mOutFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1));
                assertEquals("Output channel count is different from configured channel count \n"
                                + mTestConfig + mTestEnv, mChannelCount,
                        mOutFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1));
                assertEquals("Output width is different from configured width \n" + mTestConfig
                        + mTestEnv, mWidth, getWidth(mOutFormat));
                assertEquals("Output height is different from configured height \n" + mTestConfig
                        + mTestEnv, mHeight, getHeight(mOutFormat));
                loopCounter++;
            }
            Assume.assumeFalse("skip checksum verification due to tone mapping",
                    mSkipChecksumVerification);
            if (mIsAudio) {
                int inputPcmEncoding = formats.get(0)
                        .getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                if (mMediaType.equals(MEDIA_TYPE_RAW)
                        && inputPcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                    int outputPcmEncoding = mOutFormat.getInteger(MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT);
                    Assume.assumeTrue("output pcm encoding is not 16 bit, skipping output"
                            + " validation", outputPcmEncoding == AudioFormat.ENCODING_PCM_16BIT);
                }
            }
            CodecDecoderTest.verify(ref, mRefFile, mRmsError, AudioFormat.ENCODING_PCM_16BIT,
                    mRefCRC, mTestConfig.toString() + mTestEnv.toString());
        }
    }
}
