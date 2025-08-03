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
 * This test is to verify the quality of encoded bitstream
 * <p></p>
 * Test Params:
 * <p>Input resolution = fullhd, hd</p>
 * <p>Number of frames = 300</p>
 * <p>Target bitrate = 25 mbps, 15 mbps</p>
 * <p>bitrate mode = cbr/vbr</p>
 * <p>IFrameInterval = 1 second</p>
 * <p></p>
 * For the chosen clip and above encoder configuration, the test expects psnr of each plane of
 * each frame to be at least 30.0db
 */
@RunWith(Parameterized.class)
public class VideoEncoderPsnrTest extends VideoEncoderValidationTestBase {
    private static final float MIN_ACCEPTABLE_QUALITY = 30.0f;  // dB
    private static final float AVG_ACCEPTABLE_QUALITY = 35.0f;  // dB
    private static final int KEY_FRAME_INTERVAL = 1;
    private static final int FRAME_LIMIT = 300;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int bitRate, int bitRateMode) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(bitRate)
                .setKeyFrameInterval(KEY_FRAME_INTERVAL)
                .setWidth(width)
                .setHeight(height)
                .setBitRateMode(bitRateMode)
                .build();
    }

    private static void addParams(int bitRate, int width, int height) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (String mediaType : mediaTypes) {
            for (int bitRateMode : bitRateModes) {
                // mediaType, cfg, test label
                String label = String.format("%.1fmbps_%dx%d_%s", bitRate / 1000000.f, width,
                        height, bitRateModeToString(bitRateMode));
                exhaustiveArgsList.add(new Object[]{mediaType, getVideoEncoderCfgParams(mediaType,
                        width, height, bitRate, bitRateMode), label});
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        addParams(25000000, 1920, 1080);
        addParams(25000000, 1080, 1920);
        addParams(15000000, 1280, 720);
        addParams(15000000, 720, 1280);
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderPsnrTest(String encoder, String mediaType, EncoderConfigParams cfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE_MODE",
            "android.media.MediaFormat#KEY_BIT_RATE"})
    @Test
    public void testPsnr() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        RawResource res = getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : " + mEncCfgParams[0] + mTestConfig
                + mTestEnv + DIAGNOSTICS, res);
        encodeToMemory(mCodecName, mEncCfgParams[0], res, FRAME_LIMIT, false, true);
        CompareStreams cs = null;
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        try {
            cs = new CompareStreams(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack);
            final ArrayList<double[]> framesPSNR = cs.getFramesPSNR();
            for (int j = 0; j < framesPSNR.size(); j++) {
                double[] framePSNR = framesPSNR.get(j);
                for (double v : framePSNR) {
                    if (v < MIN_ACCEPTABLE_QUALITY) {
                        msg.append(String.format("Frame %d - PSNR Y: %f, PSNR U: %f, PSNR V: %f \n",
                                j, framePSNR[0], framePSNR[1], framePSNR[2]));
                        isOk = false;
                        break;
                    }
                }
            }
            final double[] avgPSNR = cs.getAvgPSNR();
            // weighted avg for yuv420
            final double weightedAvgPSNR = (4 * avgPSNR[0] + avgPSNR[1] + avgPSNR[2]) / 6;
            if (weightedAvgPSNR < AVG_ACCEPTABLE_QUALITY) {
                msg.append(String.format("Average PSNR of the sequence: %f is < threshold : %f\n",
                        weightedAvgPSNR, AVG_ACCEPTABLE_QUALITY));
                isOk = false;
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
