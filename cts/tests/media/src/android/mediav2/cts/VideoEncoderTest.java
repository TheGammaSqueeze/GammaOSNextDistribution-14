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
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ANY;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_HW;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_HW_RECOMMENDED;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_SHOULD;

import static org.junit.Assert.assertNotNull;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The test verifies encoders present in media codec list in bytebuffer mode. The test feeds raw
 * input data to the component and receives compressed bitstream from the component. This is
 * written to an output file using muxer.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li>The minimum PSNR of encoded output is at least the tolerance value.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class VideoEncoderTest extends CodecEncoderTestBase {
    private final SupportClass mSupportRequirements;

    public VideoEncoderTest(String encoder, String mediaType, EncoderConfigParams encCfgParams,
            SupportClass supportRequirements, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
        mSupportRequirements = supportRequirements;
    }

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRate,
            int width, int height, int frameRate, int colorFormat, int maxBFrames) {
        EncoderConfigParams.Builder foreman =
                new EncoderConfigParams.Builder(mediaType)
                        .setBitRate(bitRate)
                        .setWidth(width)
                        .setHeight(height)
                        .setFrameRate(frameRate)
                        .setColorFormat(colorFormat)
                        .setMaxBFrames(maxBFrames);
        if (colorFormat == COLOR_FormatYUVP010) {
            foreman.setProfile(Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType))[0]);
        }
        return foreman.build();
    }

    private static List<Object[]> prepareTestArgs(List<Object[]> args) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : args) {
            String mediaType = (String) arg[0];
            int bitRate = (int) arg[1];
            int width = (int) arg[2];
            int height = (int) arg[3];
            int frameRate = (int) arg[4];
            int colorFormat = (int) arg[5];
            int[] maxBFrames = {0, 2};
            for (int maxBframe : maxBFrames) {
                Object[] testArgs = new Object[4];
                if (maxBframe != 0) {
                    if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                            && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                        continue;
                    }
                }
                testArgs[0] = arg[0];
                testArgs[1] = getVideoEncoderCfgParams(mediaType, bitRate, width, height, frameRate,
                        colorFormat, maxBframe);
                testArgs[2] = arg[6];
                testArgs[3] = String.format("%dkbps_%dx%d_%s_%d-bframes", bitRate / 1000, width,
                        height, colorFormatToString(colorFormat, -1), maxBframe);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    private static SupportClass getSupportRequirementsDynamic(String mediaType, int width,
            int height) {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(MediaFormat.createVideoFormat(mediaType, width, height));
        return selectCodecs(mediaType, formats, null, true).size() != 0 ? CODEC_ANY : CODEC_SHOULD;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{4}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        List<Object[]> defArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, width, height, bit-rate, frame-rate, color format, support class
                {MediaFormat.MIMETYPE_VIDEO_H263, 64000, 128, 96, 15, COLOR_FormatYUV420Flexible,
                        CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_H263, 64000, 176, 144, 15, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 128, 96, 15, COLOR_FormatYUV420Flexible,
                        CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_H263, 128000, 176, 144, 15, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},

                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 128, 96, 15, COLOR_FormatYUV420Flexible,
                        CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 64000, 176, 144, 15, COLOR_FormatYUV420Flexible,
                        CODEC_OPTIONAL},

                {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 320, 240, 20, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 720, 480, 30, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 3000000, 1280, 720, 30,
                        COLOR_FormatYUV420Flexible, getSupportRequirementsDynamic(
                        MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 1920, 1080, 30,
                        COLOR_FormatYUV420Flexible, getSupportRequirementsDynamic(
                        MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)},

                {MediaFormat.MIMETYPE_VIDEO_VP8, 800000, 320, 180, 30, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 2000000, 640, 360, 30, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 4000000, 1280, 720, 30,
                        COLOR_FormatYUV420Flexible, getSupportRequirementsDynamic(
                        MediaFormat.MIMETYPE_VIDEO_VP8, 1280, 720)},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 10000000, 1920, 1080, 30,
                        COLOR_FormatYUV420Flexible, getSupportRequirementsDynamic(
                        MediaFormat.MIMETYPE_VIDEO_VP8, 1920, 1080)},

                {MediaFormat.MIMETYPE_VIDEO_VP9, 1600000, 720, 480, 30, COLOR_FormatYUV420Flexible,
                        CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 4000000, 1280, 720, 30, COLOR_FormatYUV420Flexible,
                        CODEC_HW_RECOMMENDED},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 5000000, 1920, 1080, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 20000000, 3840, 2160, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},

                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1000000, 512, 512, 30,
                        COLOR_FormatYUV420Flexible, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1600000, 720, 480, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 4000000, 1280, 720, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 5000000, 1920, 1080, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 3840, 2160, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW_RECOMMENDED},

                {MediaFormat.MIMETYPE_VIDEO_AV1, 5000000, 720, 480, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 8000000, 1280, 720, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 16000000, 1920, 1080, 30,
                        COLOR_FormatYUV420Flexible, CODEC_HW},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 50000000, 3840, 2160, 30,
                        COLOR_FormatYUV420Flexible, CODEC_SHOULD},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            defArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, 384000, 320, 240, 20, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, 2000000, 720, 480, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, 3000000, 1280, 720, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, 10000000, 1920, 1080, 30,
                            COLOR_FormatYUVP010, CODEC_OPTIONAL},

                    {MediaFormat.MIMETYPE_VIDEO_VP9, 1600000, 720, 480, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, 4000000, 1280, 720, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, 5000000, 1920, 1080, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, 20000000, 3840, 2160, 30,
                            COLOR_FormatYUVP010, CODEC_OPTIONAL},

                    {MediaFormat.MIMETYPE_VIDEO_HEVC, 1600000, 720, 480, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, 4000000, 1280, 720, 30, COLOR_FormatYUVP010,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, 5000000, 1920, 1080, 30,
                            COLOR_FormatYUVP010, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, 20000000, 3840, 2160, 30,
                            COLOR_FormatYUVP010, CODEC_OPTIONAL},

                    {MediaFormat.MIMETYPE_VIDEO_AV1, 5000000, 720, 480, 30, COLOR_FormatYUVP010,
                            CODEC_HW},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, 8000000, 1280, 720, 30, COLOR_FormatYUVP010,
                            CODEC_HW},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, 16000000, 1920, 1080, 30,
                            COLOR_FormatYUVP010, CODEC_HW},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, 50000000, 3840, 2160, 30,
                            COLOR_FormatYUVP010, CODEC_SHOULD},
            }));
        }
        List<Object[]> argsList = prepareTestArgs(defArgsList);
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Check description of class {@link VideoEncoderTest}
     */
    @CddTest(requirements = {"5.2.1/C-1-1", "5.2.2/C-1-2", "5.2.2/C-2-1", "5.2.3/C-1-1",
            "5.2.3/C-1-2", "5.2.3/C-2-1", "5.2.4/C-1-1", "5.2.6/C-2-1"})
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeAndValidate() throws IOException, InterruptedException {
        // pre run checks
        if (mEncCfgParams[0].mInputBitDepth > 8) {
            Assume.assumeTrue("Codec doesn't support high bit depth profile encoding",
                    doesCodecSupportHDRProfile(mCodecName, mMediaType));
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                            mEncCfgParams[0].mColorFormat, mEncCfgParams[0].mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType,
                            mEncCfgParams[0].mColorFormat));
        }
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(mEncCfgParams[0].getFormat());
        checkFormatSupport(mCodecName, mMediaType, true, formats, null, mSupportRequirements);

        // encode
        RawResource res = EncoderInput.getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, res);

        boolean muxOutput = true;
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) && CodecTestBase.IS_BEFORE_U) {
            muxOutput = false;
        }
        encodeToMemory(mCodecName, mEncCfgParams[0], res, Integer.MAX_VALUE, false, muxOutput);

        // validate output
        if (muxOutput) {
            validateEncodedPSNR(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack,
                    ACCEPTABLE_WIRELESS_TX_QUALITY);
        }
    }
}
