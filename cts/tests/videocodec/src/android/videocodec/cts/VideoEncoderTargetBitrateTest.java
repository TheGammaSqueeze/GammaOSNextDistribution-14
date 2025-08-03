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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This test verifies encoders support for bitrate modes VBR and CBR.
 * <p></p>
 * Test Params:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Number of frames = 300</p>
 * <p>Target bitrate = 5, 8, 10 Mbps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 * For VBR, the encoded size of the frames in sliding window of 1 second should not be greater
 * than (bitrate / 8) * 2 bytes
 * For CBR, the encoded size of the frames in sliding window of 1 second should not be greater
 * than 1.15 * (bitrate / 8) bytes
 */
@RunWith(Parameterized.class)
public class VideoEncoderTargetBitrateTest extends VideoEncoderValidationTestBase {
    private static final int KEY_FRAME_INTERVAL = 1;
    private static final int FRAME_LIMIT = 300;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private final float mThreshold;
    private final Queue<Integer> mBufferSize = new LinkedList<>();
    private int mTotalOvershoots = 0;
    private final StringBuilder mMsg = new StringBuilder();

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int bitRate, int bitRateMode, int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(bitRate)
                .setKeyFrameInterval(KEY_FRAME_INTERVAL)
                .setWidth(width)
                .setHeight(height)
                .setBitRateMode(bitRateMode)
                .setMaxBFrames(maxBFrames)
                .build();
    }

    private static void addParams(int width, int height) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};
        final int[] bitRates = new int[]{5000000, 8000000, 10000000};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (String mediaType : mediaTypes) {
            for (int bitRate : bitRates) {
                for (int maxBFrames : maxBFramesPerSubGop) {
                    if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                            && !mediaType.equals((MediaFormat.MIMETYPE_VIDEO_HEVC))
                            && maxBFrames != 0) {
                        continue;
                    }
                    for (int bitRateMode : bitRateModes) {
                        // mediaType, cfg, test label
                        String label = String.format("%dkbps_%dx%d_maxb-%d_%s", bitRate / 1000,
                                width, height, maxBFrames, bitRateModeToString(bitRateMode));
                        exhaustiveArgsList.add(new Object[]{mediaType,
                                getVideoEncoderCfgParams(mediaType, width, height, bitRate,
                                        bitRateMode, maxBFrames), label});
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        addParams(1920, 1080);
        addParams(1080, 1920);
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderTargetBitrateTest(String encoder, String mediaType,
            EncoderConfigParams cfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
        float sf = 1.f;
        if (cfgParams.mBitRateMode == BITRATE_MODE_VBR) {
            sf = 2.f;
        } else if (cfgParams.mBitRateMode == BITRATE_MODE_CBR) {
            sf = 1.15f;
        }
        mThreshold = cfgParams.mBitRate / 8.f * sf;
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0)) {
            mBufferSize.add(mOutputBuff.getOutStreamSize());
        }
        super.dequeueOutput(bufferIndex, info);
        if (mOutputCount >= mActiveEncCfg.mFrameRate) {
            int size = mOutputBuff.getOutStreamSize() - mBufferSize.remove();
            if (size > mThreshold) {
                mTotalOvershoots++;
                mMsg.append(String.format("At frame %d, total size of the frames in last 1 second"
                        + " is %d > %f (Threshold) \n", mOutputCount, size, mThreshold));
            }
        }
    }

    @CddTest(requirements = {"5.2/C-5-2", "5.2/C-6-1"})
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE_MODE"})
    @Test
    public void testEncoderBitRateModeSupport() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        encodeToMemory(mCodecName, mEncCfgParams[0], FRAME_LIMIT, true, false);
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        Assume.assumeTrue(mMsg.toString() + mTestConfig + mTestEnv, mTotalOvershoots == 0);
    }
}
