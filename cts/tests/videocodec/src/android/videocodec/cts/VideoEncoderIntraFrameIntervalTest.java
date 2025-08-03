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
import static android.media.MediaFormat.PICTURE_TYPE_B;
import static android.media.MediaFormat.PICTURE_TYPE_I;
import static android.media.MediaFormat.PICTURE_TYPE_P;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediav2.common.cts.BitStreamUtils;
import android.mediav2.common.cts.EncoderConfigParams;

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
import java.util.Map;

/**
 * Test to verify if intra frames are correctly inserted by the encoder
 * <p></p>
 * Test Params:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Number of frames = 600</p>
 * <p>Target bitrate = 5 Mbps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>IFrameInterval = 0/2 seconds</p>
 * <p></p>
 * The distance between 2 intra frames should not exceed IFrameInterval
 */
@RunWith(Parameterized.class)
public class VideoEncoderIntraFrameIntervalTest extends VideoEncoderValidationTestBase {
    private static final int FRAME_LIMIT = 600;
    private static final int BIT_RATE = 5000000;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRateMode,
            int maxBFrames, int keyFrameInterval) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(BIT_RATE)
                .setKeyFrameInterval(keyFrameInterval)
                .setWidth(WIDTH)
                .setHeight(HEIGHT)
                .setBitRateMode(bitRateMode)
                .setMaxBFrames(maxBFrames)
                .build();
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};
        final int[] maxBFramesPerSubGop = new int[]{0, 1, 2, 3};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        final int[] intraIntervals = new int[]{0, 2};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                        && !mediaType.equals((MediaFormat.MIMETYPE_VIDEO_HEVC))
                        && maxBFrames != 0) {
                    continue;
                }
                for (int bitRateMode : bitRateModes) {
                    for (int intraInterval : intraIntervals) {
                        // mediaType, cfg, test label
                        String label = String.format("%dkbps_%dx%d_maxb-%d_%s_i-dist-%d",
                                BIT_RATE / 1000, WIDTH, HEIGHT, maxBFrames,
                                bitRateModeToString(bitRateMode), intraInterval);
                        exhaustiveArgsList.add(new Object[]{mediaType,
                                getVideoEncoderCfgParams(mediaType, bitRateMode, maxBFrames,
                                        intraInterval), label});
                    }
                }
            }
        }
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderIntraFrameIntervalTest(String encoder, String mediaType,
            EncoderConfigParams cfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
        mParser = BitStreamUtils.getParserObject(mMediaType);
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_I_FRAME_INTERVAL"})
    @Test
    public void testEncoderSyncFrameSupport() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        encodeToMemory(mCodecName, mEncCfgParams[0], FRAME_LIMIT, false, false);
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        int lastKeyFrameIdx = 0, currFrameIdx = 0, maxKeyFrameDistance = 0;
        for (Map.Entry<Long, Integer> pic : mPtsPicTypeMap.entrySet()) {
            int picType = pic.getValue();
            assertTrue("Test could not gather picture types of encoded frames \n" + mTestConfig
                    + mTestEnv, picType == PICTURE_TYPE_I || picType == PICTURE_TYPE_P
                    || picType == PICTURE_TYPE_B);
            if (picType == PICTURE_TYPE_I) {
                maxKeyFrameDistance = Math.max(maxKeyFrameDistance, currFrameIdx - lastKeyFrameIdx);
                lastKeyFrameIdx = currFrameIdx;
            }
            currFrameIdx++;
        }
        int expDistance =
                mEncCfgParams[0].mKeyFrameInterval == 0 ? 1 :
                        (int) (mEncCfgParams[0].mFrameRate * mEncCfgParams[0].mKeyFrameInterval);
        int tolerance = mEncCfgParams[0].mKeyFrameInterval == 0 ? 0 : mEncCfgParams[0].mMaxBFrames;
        String msg = String.format(
                "Number of frames between 2 Sync frames exceeds configured key frame interval.\n"
                        + " Expected max key frame distance %d.\nGot max key frame distance %d.\n",
                expDistance, maxKeyFrameDistance);
        if (mEncCfgParams[0].mMaxBFrames == 0) {
            assertEquals(msg + mTestConfig + mTestEnv, maxKeyFrameDistance - expDistance, 0);
        } else {
            Assume.assumeTrue(msg + mTestConfig + mTestEnv,
                    Math.abs(maxKeyFrameDistance - expDistance) <= tolerance);
        }
    }
}
