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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.os.Bundle;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This test is added to extend the testing coverage of dynamically changing bitrates during an
 * encoding session. This test verifies whether the rate control process of the encoder increases
 * bits or decreases bits as instructed.
 * <p></p>
 * Encode Configs:
 * <p>Input resolution = 1080p30fps/720p30fps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 * <ul>
 *     <li>Change the bitrate every segment of 3 seconds (3 GOPs) from 5->8->12->8->5 if the
 *     input resolution is 1080p30fps.</li>
 *     <li>Change the bitrate every segment of 3 seconds (3 GOPs) from 2->3->5->3->2 if the
 *     input resolution is 720p30fps.</li>
 * </ul>
 * Save the encoded size per segment as A->B->C->D->E.
 * <p></p>
 * The test expects B/A>=1.15, C/B>=1.15, C/D>=1.15 and D/E>=1.15.
 */
@RunWith(Parameterized.class)
public class VideoEncoderAdaptiveBitRateTest extends VideoEncoderValidationTestBase {
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private static final int SEGMENT_DURATION = 3;
    private static final int[] SEGMENT_BITRATES_FULLHD =
            new int[]{5000000, 8000000, 12000000, 8000000, 5000000};
    private static final int[] SEGMENT_BITRATES_HD =
            new int[]{2000000, 3000000, 5000000, 3000000, 2000000};
    private static final int KEY_FRAME_INTERVAL = 1;

    private final int[] mSegmentBitRates;
    private final float[] mSegmentSizes;
    private int mOutputSizeTillLastSegment = 0;

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

    private static void addParams(int width, int height, int[] segmentBitRates) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (String mediaType : mediaTypes) {
            for (int bitRateMode : bitRateModes) {
                // mediaType, cfg, segment bitrates, test label
                String label = String.format("%dx%d_%s", width, height,
                        bitRateModeToString(bitRateMode));
                exhaustiveArgsList.add(new Object[]{mediaType, getVideoEncoderCfgParams(mediaType,
                        width, height, segmentBitRates[0], bitRateMode), segmentBitRates, label});
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{4}")
    public static Collection<Object[]> input() {
        addParams(1920, 1080, SEGMENT_BITRATES_FULLHD);
        addParams(1080, 1920, SEGMENT_BITRATES_FULLHD);
        addParams(1280, 720, SEGMENT_BITRATES_HD);
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderAdaptiveBitRateTest(String encoder, String mediaType,
            EncoderConfigParams cfg, int[] segmentBitRates,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, cfg, allTestParams);
        mSegmentBitRates = segmentBitRates;
        mSegmentSizes = new float[segmentBitRates.length];
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    private void updateBitrate(int bitrate) {
        final Bundle bitrateUpdate = new Bundle();
        bitrateUpdate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
        mCodec.setParameters(bitrateUpdate);
    }

    protected void enqueueInput(int bufferIndex) {
        super.enqueueInput(bufferIndex);
        int segStartFrameIdx = SEGMENT_DURATION * mActiveEncCfg.mFrameRate;
        int segIdx = mInputCount / segStartFrameIdx;
        if ((mInputCount % segStartFrameIdx == 0) && (segIdx < mSegmentBitRates.length)) {
            updateBitrate(mSegmentBitRates[segIdx]);
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        super.dequeueOutput(bufferIndex, info);
        int segEndFrameIdx = SEGMENT_DURATION * mActiveEncCfg.mFrameRate;
        if (mOutputCount > 0 && mOutputCount % segEndFrameIdx == 0) {
            int segIdx = mOutputCount / segEndFrameIdx - 1;
            mSegmentSizes[segIdx] = mOutputBuff.getOutStreamSize() - mOutputSizeTillLastSegment;
            mOutputSizeTillLastSegment = mOutputBuff.getOutStreamSize();
        }
    }

    void passFailCriteria(int segA, int segB) {
        String msg = String.format("For segment %d, configured bitrate is %d, received segment size"
                        + " is %f \n For segment %d, configured bitrate is %d, received segment"
                        + " size is %f \n Segment Relative Ratio %f \n",
                segA, mSegmentBitRates[segA], mSegmentSizes[segA], segB, mSegmentBitRates[segB],
                mSegmentSizes[segB], mSegmentSizes[segB] / mSegmentSizes[segA]);
        if (mEncCfgParams[0].mBitRateMode == BITRATE_MODE_CBR) {
            assertTrue(msg + mTestConfig + mTestEnv,
                    mSegmentSizes[segB] / mSegmentSizes[segA] >= 1.15);
        } else {
            assumeTrue(msg + mTestConfig + mTestEnv,
                    mSegmentSizes[segB] / mSegmentSizes[segA] >= 1.15);
        }
    }

    @CddTest(requirements = {"5.2/C-2-1"})
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @Test
    public void testAdaptiveBitRate() throws IOException, InterruptedException,
            CloneNotSupportedException {
        int maxBitRate = 0;
        for (int bitrate : mSegmentBitRates) {
            maxBitRate = Math.max(bitrate, maxBitRate);
        }
        EncoderConfigParams cfg = mEncCfgParams[0].getBuilder().setBitRate(maxBitRate).build();
        MediaFormat format = cfg.getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));

        int limit = mSegmentBitRates.length * SEGMENT_DURATION * mEncCfgParams[0].mFrameRate;
        encodeToMemory(mCodecName, mEncCfgParams[0], limit, true, false);
        assertEquals("encoder did not encode the requested number of frames \n" + mTestConfig
                + mTestEnv, mOutputCount, limit);
        passFailCriteria(0, 1);
        passFailCriteria(1, 2);
        passFailCriteria(3, 2);
        passFailCriteria(4, 3);
    }
}
