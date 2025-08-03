/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.EncoderProfileLevelTestBase;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * EncoderProfileLevelTest validates the profile, level information advertised by the component
 * in its codec capabilities. The test sets profile and level keys in media format and uses it
 * during encoder configuration. Upon successful configuration, frames are queued for encoding
 * (byte buffer mode) and the encoded output (bitstream) is expected to contain the same profile
 * that was used during configure. The level shall be at least the input configured level.
 * <p>
 * NOTE: The test configures level information basing on standard guidelines, not arbitrarily so
 * encoders are expected to maintain at least the input configured level
 * <p>
 * The test parses the bitstream (csd or frame header) and determines profile and level
 * information. This serves as reference for further validation. The test checks if the output
 * format returned by component contains same profile and level information as the bitstream. The
 * output of encoder is muxed and is extracted. The extracted format is expected to contain same
 * profile and level information as the bitstream.
 * <p>
 * As per cdd, if a device contains an encoder capable of encoding a profile/level combination
 * then it should contain a decoder capable of decoding the same profile/level combination. This
 * is verified.
 * <p>
 * If device implementations support encoding in a media type, then as per cdd they are expected to
 * handle certain profile and level configurations. This is verified.
 */
@RunWith(Parameterized.class)
public class EncoderProfileLevelTest extends EncoderProfileLevelTestBase {
    private static final String LOG_TAG = EncoderProfileLevelTest.class.getSimpleName();
    private static final HashMap<String, CddRequirements> CDD_REQUIREMENTS_MAP = new HashMap<>();

    private static class CddRequirements {
        private int[] mProfiles;
        private int mLevel;
        private int mHeight;
        private int mWidth;

        CddRequirements(int[] profiles, int level, int width, int height) {
            mProfiles = profiles;
            mLevel = level;
            mWidth = width;
            mHeight = height;
        }

        CddRequirements(int[] profiles) {
            this(profiles, -1 /* level */, -1 /* width */, -1 /* height */);
        }

        CddRequirements(int[] profiles, int level) {
            this(profiles, level, -1 /* width */, -1 /* height */);
        }

        public int[] getProfiles() {
            return mProfiles;
        }

        public int getLevel() {
            return mLevel;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }
    }
    public EncoderProfileLevelTest(String encoder, String mediaType,
            EncoderConfigParams[] encCfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, encCfgParams, allTestParams);
    }

    private static List<Object[]> prepareTestArgs(Object[] arg, int[] profiles, int colorFormat) {
        List<Object[]> argsList = new ArrayList<>();
        final int[] maxBFrames = {0, 2};
        final String mediaType = (String) arg[0];
        boolean isVideo = mediaType.startsWith("video/");
        final int br = (int) arg[1];
        final int param1 = (int) arg[2];
        final int param2 = (int) arg[3];
        final int fps = (int) arg[4];
        final int level = (int) arg[5];
        boolean[] boolStates = {false, true};
        if (isVideo) {
            for (int maxBframe : maxBFrames) {
                if (maxBframe != 0) {
                    if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                            && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                        continue;
                    }
                }
                // test each resolution in both landscape and portrait orientation
                for (boolean rotate : boolStates) {
                    int width, height;
                    if (rotate) {
                        width = param2;
                        height = param1;
                    } else {
                        width = param1;
                        height = param2;
                    }

                    // H.263 doesn't support portait mode, so skip cases where width is smaller
                    // than height.
                    if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263) && width <= height) {
                        continue;
                    }

                    Object[] testArgs = new Object[3];
                    testArgs[0] = arg[0];
                    testArgs[1] = getVideoEncoderCfgParams(mediaType, br, width, height, fps,
                            colorFormat, maxBframe, profiles, level);
                    testArgs[2] = String.format("%dkbps_%dx%d_%dfps_%s_%d_%d-bframes", br / 1000,
                            width, height, fps, colorFormatToString(colorFormat, -1),
                            level, maxBframe);
                    argsList.add(testArgs);
                }
            }
        } else {
            Object[] testArgs = new Object[3];
            testArgs[0] = arg[0];
            testArgs[1] = getAudioEncoderCfgParams(mediaType, br, param1, param2, profiles);
            testArgs[2] = String.format("%dkbps_%dkHz_%dch", br / 1000, param1 / 1000, param2);
            argsList.add(testArgs);
        }
        return argsList;
    }

    private static EncoderConfigParams[] getVideoEncoderCfgParams(String mediaType, int bitRate,
            int width, int height, int frameRate, int colorFormat, int maxBframe, int[] profiles,
            int level) {
        ArrayList<EncoderConfigParams> cfgParams = new ArrayList<>();
        for (int profile : profiles) {
            if (maxBframe != 0) {
                if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC) && (
                        profile == AVCProfileBaseline
                                || profile == AVCProfileConstrainedBaseline)) {
                    continue;
                }
            }
            cfgParams.add(new EncoderConfigParams.Builder(mediaType)
                    .setBitRate(bitRate)
                    .setWidth(width)
                    .setHeight(height)
                    .setFrameRate(frameRate)
                    .setMaxBFrames(maxBframe)
                    .setProfile(profile)
                    .setLevel(level)
                    .setColorFormat(colorFormat)
                    .build());
        }
        return cfgParams.toArray(new EncoderConfigParams[0]);
    }

    private static EncoderConfigParams[] getAudioEncoderCfgParams(String mediaType, int bitRate,
            int sampleRate, int channelCount, int[] profiles) {
        EncoderConfigParams[] cfgParams = new EncoderConfigParams[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            cfgParams[i] = new EncoderConfigParams.Builder(mediaType)
                    .setBitRate(bitRate)
                    .setSampleRate(sampleRate)
                    .setChannelCount(channelCount)
                    .setProfile(profiles[i])
                    .build();
        }
        return cfgParams;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final Object[][] exhaustiveArgsList = new Object[][]{
                // Audio - CodecMediaType, bit-rate, sample rate, channel count, level
                {MediaFormat.MIMETYPE_AUDIO_AAC, 64000, 48000, 1, -1, -1},
                {MediaFormat.MIMETYPE_AUDIO_AAC, 128000, 48000, 2, -1, -1},
                // Video - CodecMediaType, bit-rate, width, height, frame-rate, level

                // ITU-T H.264
                // Table A-6 – Maximum frame rates (frames per second) for some example frame sizes
                {MediaFormat.MIMETYPE_VIDEO_AVC, 64000, 176, 144, 15, AVCLevel1},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 128000, 176, 144, 15, AVCLevel1b},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 192000, 352, 288, 7, AVCLevel11},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 352, 288, 15, AVCLevel12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 512000, 352, 288, 30, AVCLevel13},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 832000, 352, 288, 30, AVCLevel2},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1000000, 352, 576, 25, AVCLevel21},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1500000, 720, 576, 12, AVCLevel22},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 720, 576, 25, AVCLevel3},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 3000000, 1280, 720, 30, AVCLevel31},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 6000000, 1280, 1024, 42, AVCLevel32},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 2048, 1024, 30, AVCLevel4},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 25000000, 2048, 1024, 30, AVCLevel41},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 2048, 1088, 60, AVCLevel42},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 60000000, 3680, 1526, 26, AVCLevel5},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 80000000, 4096, 2304, 26, AVCLevel51},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 120000000, 4096, 2304, 56, AVCLevel52},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 8192, 4320, 30, AVCLevel6},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 480000000, 8192, 4320, 60, AVCLevel61},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 800000000, 8192, 4320, 120, AVCLevel62},

                // The entries below have width being twice that of the widths and height being
                // half of the heights in Table A-6
                // Since AVC specification has level limits in terms MacroBlocks and not in terms
                // of pixels, the height is floored to multiple of 16 to fit within the level being
                // tested
                {MediaFormat.MIMETYPE_VIDEO_AVC, 64000, 352, 64, 15, AVCLevel1},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 128000, 352, 64, 15, AVCLevel1b},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 192000, 704, 144, 7, AVCLevel11},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 704, 144, 15, AVCLevel12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 512000, 704, 144, 30, AVCLevel13},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 832000, 704, 144, 30, AVCLevel2},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1000000, 704, 288, 25, AVCLevel21},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1500000, 1440, 288, 12, AVCLevel22},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 1440, 288, 25, AVCLevel3},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 3000000, 2560, 352, 30, AVCLevel31},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 6000000, 2560, 512, 42, AVCLevel32},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 4096, 512, 30, AVCLevel4},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 25000000, 4096, 512, 30, AVCLevel41},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 4096, 544, 60, AVCLevel42},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 60000000, 7360, 752, 26, AVCLevel5},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 80000000, 8192, 1152, 26, AVCLevel51},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 120000000, 8192, 1152, 56, AVCLevel52},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 16384, 2160, 30, AVCLevel6},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 480000000, 16384, 2160, 60, AVCLevel61},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 800000000, 16384, 2160, 120, AVCLevel62},

                // Resolutions listed in CDD Section 5.2
                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 320, 240, 20, AVCLevel12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 720, 480, 30, AVCLevel3},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 4000000, 1280, 720, 30, AVCLevel31},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 1920, 1088, 30, AVCLevel4},

                // Clips at Maximum frame rates and bitrates
                {MediaFormat.MIMETYPE_VIDEO_AVC, 64000, 128, 96, 30, AVCLevel1},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 128000, 128, 96, 30, AVCLevel1b},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 192000, 128, 96, 62, AVCLevel11},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 128, 96, 125, AVCLevel12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 768000, 128, 96, 172, AVCLevel13},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 128, 96, 172, AVCLevel2},
                // Following entry covers level 2.1 and 2.2
                {MediaFormat.MIMETYPE_VIDEO_AVC, 4000000, 176, 144, 172, AVCLevel21},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 176, 144, 172, AVCLevel3},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 14000000, 352, 288, 172, AVCLevel31},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 20000000, 640, 480, 172, AVCLevel32},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 20000000, 720, 480, 172, AVCLevel4},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 720, 480, 172, AVCLevel41},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 50000000, 800, 600, 172, AVCLevel42},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 135000000, 1024, 768, 172, AVCLevel5},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 1408, 960, 172, AVCLevel51},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 2048, 1088, 172, AVCLevel52},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 240000000, 2048, 1526, 300, AVCLevel6},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 480000000, 3680, 1536, 300, AVCLevel61},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 800000000, 4096, 2304, 300, AVCLevel62},

                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 4000000, 352, 288, 30, MPEG2LevelLL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 15000000, 720, 480, 30, MPEG2LevelML},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 60000000, 1440, 1088, 30, MPEG2LevelH14},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 80000000, 1920, 1088, 30, MPEG2LevelHL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, 80000000, 1920, 1088, 60, MPEG2LevelHP},

                // Resolutions listed in https://www.webmproject.org/vp9/levels/
                {MediaFormat.MIMETYPE_VIDEO_VP9, 200000, 256, 144, 15, VP9Level1},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 512000, 384, 192, 30, VP9Level11},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1000000, 480, 256, 30, VP9Level2},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1500000, 640, 384, 30, VP9Level21},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1600000, 1080, 512, 30, VP9Level3},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 4000000, 1280, 768, 30, VP9Level31},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 5000000, 2048, 1088, 30, VP9Level4},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 16000000, 2048, 1088, 60, VP9Level41},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 20000000, 4096, 2176, 30, VP9Level5},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 80000000, 4096, 2176, 60, VP9Level51},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 160000000, 4096, 2176, 120, VP9Level52},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 180000000, 8192, 4352, 30, VP9Level6},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 240000000, 8192, 4352, 60, VP9Level61},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 480000000, 8192, 4352, 120, VP9Level62},

                // The entries below have width being twice that of the widths and height being
                // half of the heights in https://www.webmproject.org/vp9/levels/
                // Some of the cases where max dimension is limited by specification, width is
                // clipped.
                {MediaFormat.MIMETYPE_VIDEO_VP9, 200000, 512, 72, 15, VP9Level1},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 512000, 768, 96, 30, VP9Level11},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1000000, 960, 128, 30, VP9Level2},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1500000, 1280, 192, 30, VP9Level21},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1600000, 2048, 256, 30, VP9Level3},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 4000000, 2560, 384, 30, VP9Level31},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 5000000, 4096, 544, 30, VP9Level4},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 16000000, 4096, 544, 60, VP9Level41},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 20000000, 8192, 1088, 30, VP9Level5},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 80000000, 8192, 1088, 60, VP9Level51},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 160000000, 8192, 1088, 120, VP9Level52},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 180000000, 16384, 2176, 30, VP9Level6},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 240000000, 16384, 2176, 60, VP9Level61},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 480000000, 16384, 2176, 120, VP9Level62},

                // Resolutions listed in CDD Section 5.2
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1600000, 720, 480, 30, VP9Level3},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 4000000, 1280, 720, 30, VP9Level31},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 5000000, 1920, 1080, 30, VP9Level4},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 20000000, 3840, 2160, 30, VP9Level5},

                // ITU-T H.263
                // Table X.2/H.263 − Levels of operation
                // This also includes 176x144 which is the CDD
                {MediaFormat.MIMETYPE_VIDEO_H263, 64000, 176, 144, 15, H263Level10},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 176, 144, 15, H263Level45},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 352, 288, 15, H263Level20},
                {MediaFormat.MIMETYPE_VIDEO_H263, 384000, 352, 288, 30, H263Level30},
                {MediaFormat.MIMETYPE_VIDEO_H263, 2048000, 352, 288, 30, H263Level40},
                {MediaFormat.MIMETYPE_VIDEO_H263, 4096000, 352, 240, 60, H263Level50},
                {MediaFormat.MIMETYPE_VIDEO_H263, 8192000, 720, 240, 60, H263Level60},
                {MediaFormat.MIMETYPE_VIDEO_H263, 16384000, 720, 576, 50, H263Level70},

                // From ITU-T H.265
                // Table A.11 – Maximum picture rates (pictures per second) at level 1 to 4.1 for
                // some example picture sizes when MinCbSizeY is equal to 64
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 128000, 176, 144, 15, HEVCMainTierLevel1},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 512000, 352, 288, 30, HEVCMainTierLevel2},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1000000, 640, 360, 30, HEVCMainTierLevel21},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1600000, 960, 540, 30, HEVCMainTierLevel3},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 4000000, 1280, 720, 33, HEVCMainTierLevel31},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 6000000, 2048, 1080, 30, HEVCMainTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 16000000, 2048, 1080, 30, HEVCHighTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 2048, 1080, 60, HEVCMainTierLevel41},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 30000000, 2048, 1080, 60, HEVCHighTierLevel41},

                // From ITU-T H.265
                // Table A.12 – Maximum picture rates (pictures per second) at level 5 to 6.2 for
                // some example picture sizes when MinCbSizeY is equal to 64
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 4096, 2160, 30, HEVCMainTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 4096, 2160, 30, HEVCHighTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 40000000, 4096, 2160, 60, HEVCMainTierLevel51},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 80000000, 4096, 2160, 60, HEVCHighTierLevel51},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 4096, 2160, 120, HEVCMainTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 100000000, 4096, 2160, 120, HEVCHighTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 8192, 4320, 30, HEVCMainTierLevel6},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 80000000, 8192, 4320, 30, HEVCHighTierLevel6},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 100000000, 8192, 4320, 60, HEVCMainTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 240000000, 8192, 4320, 60, HEVCHighTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 200000000, 8192, 4320, 120, HEVCMainTierLevel62},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 360000000, 8192, 4320, 120, HEVCHighTierLevel62},

                // The entries below have width being twice that of the widths and height being
                // half of the heights in Table A.11 and A.12
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 128000, 352, 72, 15, HEVCMainTierLevel1},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 512000, 704, 144, 30, HEVCMainTierLevel2},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1000000, 1280, 180, 30, HEVCMainTierLevel21},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1600000, 1920, 270, 30, HEVCMainTierLevel3},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 4000000, 2560, 360, 33, HEVCMainTierLevel31},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 6000000, 4096, 540, 30, HEVCMainTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 16000000, 4096, 540, 30, HEVCHighTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 4096, 540, 60, HEVCMainTierLevel41},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 30000000, 4096, 540, 60, HEVCHighTierLevel41},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 8192, 1080, 30, HEVCMainTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 8192, 1080, 30, HEVCHighTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 40000000, 8192, 1080, 60, HEVCMainTierLevel51},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 80000000, 8192, 1080, 60, HEVCHighTierLevel51},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 8192, 1080, 120, HEVCMainTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 100000000, 8192, 1080, 120, HEVCHighTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 16384, 2160, 30, HEVCMainTierLevel6},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 80000000, 16384, 2160, 30, HEVCHighTierLevel6},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 100000000, 16384, 2160, 60, HEVCMainTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 240000000, 16384, 2160, 60, HEVCHighTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 200000000, 16384, 2160, 120, HEVCMainTierLevel62},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 360000000, 16384, 2160, 120, HEVCHighTierLevel62},

                // Resolutions listed in CDD Section 5.2
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1000000, 512, 512, 30, HEVCMainTierLevel3},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1600000, 720, 480, 30, HEVCMainTierLevel3},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 4000000, 1280, 720, 30, HEVCMainTierLevel31},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 5000000, 1920, 1080, 30, HEVCMainTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 3840, 2160, 30, HEVCMainTierLevel5},

                // Clips at Maximum frame rates and bitrates
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 128000, 128, 96, 33, HEVCMainTierLevel1},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1500000, 128, 96, 225, HEVCMainTierLevel2},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 3000000, 128, 96, 300, HEVCMainTierLevel21},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 6000000, 176, 144, 300, HEVCMainTierLevel3},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 10000000, 352, 240, 300, HEVCMainTierLevel31},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 12000000, 352, 576, 300, HEVCMainTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 30000000, 352, 576, 300, HEVCHighTierLevel4},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 720, 576, 300, HEVCMainTierLevel41},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 50000000, 720, 576, 300, HEVCHighTierLevel41},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 25000000, 1024, 768, 300, HEVCMainTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 100000000, 1024, 768, 300, HEVCHighTierLevel5},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 40000000, 1408, 1152, 300, HEVCMainTierLevel51},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 160000000, 1408, 1152, 300, HEVCHighTierLevel51},
                // Following two entries cover Level 5.2 and Level 6.0
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 60000000, 2048, 1526, 300, HEVCMainTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 240000000, 2048, 1526, 300, HEVCHighTierLevel52},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 120000000, 3672, 1536, 300, HEVCMainTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 480000000, 3672, 1536, 300, HEVCHighTierLevel61},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 240000000, 4096, 3072, 300, HEVCMainTierLevel62},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 800000000, 4096, 3072, 300, HEVCHighTierLevel62},

                // Resolutions listed in https://aomedia.org/av1/specification/annex-a/#levels
                {MediaFormat.MIMETYPE_VIDEO_AV1, 1500000, 426, 240, 30, AV1Level2},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 3000000, 640, 360, 30, AV1Level21},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 6000000, 854, 480, 30, AV1Level3},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 10000000, 1280, 720, 30, AV1Level31},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 12000000, 1920, 1080, 30, AV1Level4},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 20000000, 1920, 1080, 60, AV1Level41},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 30000000, 3840, 2160, 30, AV1Level5},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 40000000, 3840, 2160, 60, AV1Level51},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 3840, 2160, 120, AV1Level52},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 7680, 4320, 30, AV1Level6},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 100000000, 7680, 4320, 60, AV1Level61},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 160000000, 7680, 4320, 120, AV1Level62},

                // The entries below have width being twice that of the widths and height being
                // half of the heights in https://aomedia.org/av1/specification/annex-a/#levels
                {MediaFormat.MIMETYPE_VIDEO_AV1, 1500000, 852, 120, 30, AV1Level2},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 3000000, 1280, 180, 30, AV1Level21},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 6000000, 1708, 240, 30, AV1Level3},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 10000000, 2560, 360, 30, AV1Level31},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 12000000, 3840, 540, 30, AV1Level4},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 20000000, 3840, 540, 60, AV1Level41},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 30000000, 7680, 1080, 30, AV1Level5},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 40000000, 7680, 1080, 60, AV1Level51},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 7680, 1080, 120, AV1Level52},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 60000000, 15360, 2160, 30, AV1Level6},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 100000000, 15360, 2160, 60, AV1Level61},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 160000000, 15360, 2160, 120, AV1Level62},

                // Resolutions listed in CDD Section 5.2
                {MediaFormat.MIMETYPE_VIDEO_AV1, 5000000, 720, 480, 30, AV1Level3},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 8000000, 1280, 720, 30, AV1Level31},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 16000000, 1920, 1080, 30, AV1Level4},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 50000000, 3840, 2160, 30, AV1Level5},
        };
        final List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            final String mediaType = (String) arg[0];
            argsList.addAll(prepareTestArgs(arg,
                    Objects.requireNonNull(PROFILE_SDR_MAP.get(mediaType)),
                    COLOR_FormatYUV420Flexible));
            // P010 support was added in Android T, hence limit the following tests to Android
            // T and above
            if (IS_AT_LEAST_T && PROFILE_HLG_MAP.get(mediaType) != null) {
                argsList.addAll(prepareTestArgs(arg,
                        Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType)),
                        COLOR_FormatYUVP010));
            }
        }
        final Object[][] mpeg4SimpleProfileArgsList = new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 176, 144, 15, MPEG4Level0},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 176, 144, 15, MPEG4Level0b},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 128, 96, 30, MPEG4Level1},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 352, 288, 15, MPEG4Level2},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 384000, 352, 288, 30, MPEG4Level3},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 4000000, 640, 480, 30, MPEG4Level4a},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 8000000, 720, 576, 24, MPEG4Level5},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 12000000, 1280, 720, 30, MPEG4Level6},
        };
        for (Object[] arg : mpeg4SimpleProfileArgsList) {
            argsList.addAll(prepareTestArgs(arg, new int[]{MPEG4ProfileSimple},
                    COLOR_FormatYUV420Flexible));
        }
        final Object[][] mpeg4AdvSimpleProfileArgsList = new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 128000, 176, 144, 30, MPEG4Level1},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 384000, 352, 288, 15, MPEG4Level2},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 768000, 352, 288, 30, MPEG4Level3},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 1500000, 352, 288, 30, MPEG4Level3b},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 3000000, 704, 576, 15, MPEG4Level4},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 8000000, 720, 576, 30, MPEG4Level5},
        };
        for (Object[] arg : mpeg4AdvSimpleProfileArgsList) {
            argsList.addAll(prepareTestArgs(arg, new int[]{MPEG4ProfileAdvancedSimple},
                    COLOR_FormatYUV420Flexible));
        }
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    static {
        // Following lists profiles, level, maxWidth and maxHeight mandated by the CDD.
        // CodecMediaType, profiles, level, maxWidth, maxHeight
        CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_AUDIO_AAC,
                new CddRequirements(new int[]{AACObjectLC, AACObjectHE, AACObjectELD}));
        CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_VIDEO_H263,
                new CddRequirements(new int[]{H263ProfileBaseline}, H263Level45));
        CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_VIDEO_AVC,
                new CddRequirements(new int[]{AVCProfileBaseline}, AVCLevel3));
        CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC,
                new CddRequirements(new int[]{HEVCProfileMain}, HEVCMainTierLevel3, 512, 512));
        CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9,
                new CddRequirements(new int[]{VP9Profile0}, VP9Level3));
        if (IS_AT_LEAST_U) {
            CDD_REQUIREMENTS_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1,
                    new CddRequirements(new int[]{AV1ProfileMain8, AV1ProfileMain10}));
        }
    }

    void checkIfTrackFormatIsOk(MediaFormat trackFormat) {
        assertEquals("Input media type and extracted media type are not identical " + mTestEnv
                        + mTestConfig, mActiveEncCfg.mMediaType,
                trackFormat.getString(MediaFormat.KEY_MIME));
        if (mIsVideo) {
            assertEquals("Input width and extracted width are not same " + mTestEnv + mTestConfig,
                    mActiveEncCfg.mWidth, getWidth(trackFormat));
            assertEquals("Input height and extracted height are not same " + mTestEnv + mTestConfig,
                    mActiveEncCfg.mHeight, getHeight(trackFormat));
        } else {
            int expSampleRate = mActiveEncCfg.mProfile != AACObjectHE ? mActiveEncCfg.mSampleRate
                    : mActiveEncCfg.mSampleRate / 2;
            int expChCount = mActiveEncCfg.mProfile != AACObjectHE_PS ? mActiveEncCfg.mChannelCount
                    : mActiveEncCfg.mChannelCount / 2;
            assertEquals("Input sample rate and extracted sample rate are not same " + mTestEnv
                            + mTestConfig, expSampleRate,
                    trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            assertEquals("Input channe count and extracted channel count are not same " + mTestEnv
                            + mTestConfig, expChCount,
                    trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        }
    }

    private boolean shallSupportProfileAndLevel(EncoderConfigParams cfg) {
        CddRequirements requirement =
                Objects.requireNonNull(CDD_REQUIREMENTS_MAP.get(cfg.mMediaType));
        int[] profileCdd = requirement.getProfiles();
        int levelCdd = requirement.getLevel();
        int widthCdd = requirement.getWidth();
        int heightCdd = requirement.getHeight();

        // Check if CDD doesn't require support beyond certain resolutions.
        if (widthCdd != -1 && mActiveEncCfg.mWidth > widthCdd) {
            return false;
        }
        if (heightCdd != -1 && mActiveEncCfg.mHeight > heightCdd) {
            return false;
        }

        for (int cddProfile : profileCdd) {
            if (cfg.mProfile == cddProfile) {
                if (!cfg.mIsAudio) {
                    if (cfg.mLevel <= levelCdd) {
                        if (cfg.mMediaType.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263)
                                && cfg.mLevel != MediaCodecInfo.CodecProfileLevel.H263Level45
                                && cfg.mLevel > MediaCodecInfo.CodecProfileLevel.H263Level10) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Check description of class {@link EncoderProfileLevelTest}
     */
    @CddTest(requirements = {"2.2.2/5.1/H-0-3", "2.2.2/5.1/H-0-4", "2.2.2/5.1/H-0-5", "5/C-0-3",
            "5.2.1/C-1-1", "5.2.2/C-1-1", "5.2.4/C-1-2", "5.2.5/C-1-1", "5.2.6/C-1-1"})
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PROFILE",
            "android.media.MediaFormat#KEY_AAC_PROFILE",
            "android.media.MediaFormat#KEY_LEVEL"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testValidateProfileLevel() throws IOException, InterruptedException {
        int minLevel = getMinLevel(mMediaType, mEncCfgParams[0].mWidth, mEncCfgParams[0].mHeight,
                mEncCfgParams[0].mFrameRate, mEncCfgParams[0].mBitRate, mEncCfgParams[0].mProfile);
        boolean validateMinLevel = true;
        // MPEG4 and AV1 have independent checks on width and height and because of that,
        // level limits that are passed to landscape resolution in the test table, do not
        // match the minimum level computed later. For such cases, disable the minLevel check.
        if ((mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1))
                && (mEncCfgParams[0].mWidth < mEncCfgParams[0].mHeight)) {
            validateMinLevel = false;
        }
        if (validateMinLevel) {
            assertEquals("Calculated minimum acceptable level does not match the entry in test "
                    + "table " + mTestConfig, mEncCfgParams[0].mLevel, minLevel);
        }

        if (mIsVideo && mEncCfgParams[0].mInputBitDepth != 8) {
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                            mEncCfgParams[0].mColorFormat, mEncCfgParams[0].mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType,
                            mEncCfgParams[0].mColorFormat));
        }

        boolean cddSupportedMediaType = CDD_REQUIREMENTS_MAP.get(mMediaType) != null;
        {
            mActiveRawRes = EncoderInput.getRawResource(mEncCfgParams[0]);
            assertNotNull("no raw resource found for testing config : "
                    + mEncCfgParams[0] + mTestConfig + mTestEnv, mActiveRawRes);
            setUpSource(mActiveRawRes.mFileName);
            mSaveToMem = true;
            mMuxOutput = true;
            mOutputBuff = new OutputManager();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            MediaCodecInfo.CodecCapabilities codecCapabilities =
                    mCodec.getCodecInfo().getCapabilitiesForType(mMediaType);
            int configsTested = 0;
            for (EncoderConfigParams cfg : mEncCfgParams) {
                mActiveEncCfg = cfg;
                MediaFormat format = cfg.getFormat();
                if (!codecCapabilities.isFormatSupported(format)) {
                    if (cddSupportedMediaType) {
                        if (shallSupportProfileAndLevel(cfg)) {
                            ArrayList<MediaFormat> formats = new ArrayList<>();
                            formats.add(format);
                            assertFalse("No components present on the device supports cdd "
                                    + "required encode format:- " + format + mTestConfig + mTestEnv,
                                    selectCodecs(mMediaType, formats, null, true).isEmpty());
                        }
                        Log.d(LOG_TAG, mCodecName + " doesn't support format: " + format);
                    }
                    continue;
                }

                mOutputBuff.reset();
                configureCodec(format, false, true, true);
                mCodec.start();
                doWork(5);
                queueEOS();
                waitForAllOutputs();
                mCodec.reset();

                MediaFormat trackFormat = validateProfileAndLevel();

                deleteMuxedFile();

                // validate extracted format for mandatory keys
                if (trackFormat != null) checkIfTrackFormatIsOk(trackFormat);

                // Verify if device has an equivalent decoder for the current format
                ArrayList<MediaFormat> formatList = new ArrayList<>();
                if (mProfileLevel != null && mProfileLevel.second != -1
                        && cfg.mLevel != mProfileLevel.second) {
                    format.setInteger(MediaFormat.KEY_LEVEL, mProfileLevel.second);
                }
                formatList.add(format);
                assertTrue("Device advertises support for encoding " + format + " but cannot"
                                + " decode it. \n" + mTestConfig + mTestEnv,
                        selectCodecs(mMediaType, formatList, null, false).size() > 0);
                configsTested++;
            }
            mCodec.release();
            Assume.assumeTrue("skipping test, formats not supported by component",
                    configsTested > 0);
        }
    }
}
