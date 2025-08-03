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

package android.videocodec.cts;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;
import static android.videocodec.cts.VideoEncoderInput.getRawResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This test is to validate the encoder's support for standard resolutions
 * <p></p>
 * Test Params:
 * <p>Input resolution = standard resolutions of social media apps</p>
 * <p>Number of frames = 8</p>
 * <p>Target bitrate = 5 mbps</p>
 * <p>bitrate mode = cbr/vbr</p>
 * <p>max b frames = 0/1</p>
 * <p>IFrameInterval = 0/1 second</p>
 * <p></p>
 * For the chosen clip and above encoder configuration, the test expects the encoded clip to be
 * decodable and the decoded resolution is as expected
 */
@RunWith(Parameterized.class)
public class VideoEncoderMultiResTest extends VideoEncoderValidationTestBase {
    private static final float MIN_ACCEPTABLE_QUALITY = 20.0f;  // psnr in dB
    private static final int FRAME_LIMIT = 30;
    private static final int BIT_RATE = 5000000;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int frameRate, int bitRateMode, int maxBFrames, int intraFrameInterval) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(BIT_RATE)
                .setKeyFrameInterval(intraFrameInterval)
                .setFrameRate(frameRate)
                .setWidth(width)
                .setHeight(height)
                .setMaxBFrames(maxBFrames)
                .setBitRateMode(bitRateMode)
                .build();
    }

    private static void addParams(int width, int height, int frameRate) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] intraIntervals = new int[]{0, 1};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                        && !mediaType.equals((MediaFormat.MIMETYPE_VIDEO_HEVC))
                        && maxBFrames != 0) {
                    continue;
                }
                for (int bitRateMode : bitRateModes) {
                    for (int intraInterval : intraIntervals) {
                        // mediaType, cfg, label
                        String label = String.format("%dx%d_%dfps_maxb-%d_%s_i-dist-%d", width,
                                height, frameRate, maxBFrames, bitRateModeToString(bitRateMode),
                                intraInterval);
                        exhaustiveArgsList.add(new Object[]{mediaType,
                                getVideoEncoderCfgParams(mediaType, width, height, frameRate,
                                        bitRateMode, maxBFrames, intraInterval), label});
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        addParams(1080, 1920, 30);
        addParams(720, 1280, 30);
        addParams(720, 720, 30);
        addParams(512, 896, 30);
        addParams(1920, 960, 30);
        addParams(544, 1088, 30);
        addParams(360, 640, 24);
        addParams(720, 1280, 30);
        addParams(512, 896, 30);
        addParams(544, 1104, 30);

        addParams(544, 1120, 30);
        addParams(1552, 688, 30);
        addParams(576, 1024, 30);
        addParams(720, 1472, 30);
        addParams(1920, 864, 30);

        addParams(426, 240, 30);
        addParams(640, 360, 30);
        addParams(854, 480, 30);
        addParams(2560, 1440, 30);
        addParams(3840, 2160, 30);

        addParams(240, 426, 30);
        addParams(360, 640, 30);
        addParams(480, 854, 30);
        addParams(1440, 2560, 30);
        addParams(2160, 3840, 30);

        addParams(360, 360, 30);
        addParams(1920, 1920, 30);

        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderMultiResTest(String encoder, String mediaType, EncoderConfigParams cfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_WIDTH",
            "android.media.MediaFormat#KEY_HEIGHT",
            "android.media.MediaFormat#KEY_FRAME_RATE"})
    @Test
    public void testMultiRes() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        RawResource res = getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : " + mEncCfgParams[0] + mTestConfig
                + mTestEnv + DIAGNOSTICS, res);
        encodeToMemory(mCodecName, mEncCfgParams[0], res, FRAME_LIMIT, false, true);
        assertEquals("Output width is different from configured width \n" + mTestConfig
                + mTestEnv, mEncCfgParams[0].mWidth, getWidth(getOutputFormat()));
        assertEquals("Output height is different from configured height \n" + mTestConfig
                + mTestEnv, mEncCfgParams[0].mHeight, getHeight(getOutputFormat()));
        CompareStreams cs = null;
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        try {
            cs = new CompareStreams(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack);
            final double[] minPSNR = cs.getMinimumPSNR();
            for (int i = 0; i < minPSNR.length; i++) {
                if (minPSNR[i] < MIN_ACCEPTABLE_QUALITY) {
                    msg.append(String.format("For %d plane, minPSNR is less than tolerance"
                            + " threshold, Got %f, Threshold %f", i, minPSNR[i],
                            MIN_ACCEPTABLE_QUALITY));
                    isOk = false;
                    break;
                }
            }
        } finally {
            if (cs != null) cs.cleanUp();
        }
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        assertTrue("Encountered frames with PSNR less than configured threshold "
                + MIN_ACCEPTABLE_QUALITY + "dB \n" + msg + mTestConfig + mTestEnv, isOk);
    }
}
