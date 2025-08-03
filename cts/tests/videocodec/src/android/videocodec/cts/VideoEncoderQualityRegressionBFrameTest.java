/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.prepareParamList;
import static android.videocodec.cts.VideoEncoderInput.BIRTHDAY_FULLHD_LANDSCAPE;
import static android.videocodec.cts.VideoEncoderInput.getRawResource;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * This test is to ensure no quality regression is seen from '0' b frames to '1' b frame.
 * <p></p>
 * Global Encode Config:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 */
@RunWith(Parameterized.class)
public class VideoEncoderQualityRegressionBFrameTest extends VideoEncoderQualityRegressionTestBase {
    private static final String[] MEDIA_TYPES =
            {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC};
    private static final VideoEncoderInput.CompressedResource RES = BIRTHDAY_FULLHD_LANDSCAPE;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    protected static final int[] BIT_RATES =
            {2000000, 4000000, 6000000, 8000000, 10000000, 12000000};
    protected static final int[] BIT_RATE_MODES = {BITRATE_MODE_CBR, BITRATE_MODE_VBR};
    protected static final int[] B_FRAMES = {0, 1};
    private static final int FRAME_RATE = 30;
    private static final int KEY_FRAME_INTERVAL = 1;
    private static final int FRAME_LIMIT = 300;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private final int mBitRateMode;

    @Parameterized.Parameters(name = "{index}_{0}_{4}")
    public static Collection<Object[]> input() {
        RESOURCES.add(RES);
        for (String mediaType : MEDIA_TYPES) {
            for (int bitRateMode : BIT_RATE_MODES) {
                exhaustiveArgsList.add(new Object[]{mediaType, RES, bitRateMode,
                        CodecEncoderTestBase.bitRateModeToString(bitRateMode)});
            }
        }
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderQualityRegressionBFrameTest(String encoder, String mediaType,
            VideoEncoderInput.CompressedResource cRes, int bitRateMode,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, cRes, allTestParams);
        mBitRateMode = bitRateMode;
    }

    void qualityRegressionOverBFrames() throws IOException, InterruptedException {
        RawResource res = getRawResource(mCRes);
        VideoEncoderValidationTestBase[] testInstances =
                new VideoEncoderValidationTestBase[B_FRAMES.length];
        String[] encoderNames = new String[B_FRAMES.length];
        List<EncoderConfigParams[]> cfgsUnion = new ArrayList<>();
        for (int i = 0; i < B_FRAMES.length; i++) {
            testInstances[i] = new VideoEncoderValidationTestBase(null, mMediaType, null,
                    mAllTestParams);
            EncoderConfigParams[] cfgs = new EncoderConfigParams[BIT_RATES.length];
            cfgsUnion.add(cfgs);
            ArrayList<MediaFormat> fmts = new ArrayList<>();
            for (int j = 0; j < cfgs.length; j++) {
                cfgs[j] = getVideoEncoderCfgParams(mMediaType, WIDTH, HEIGHT, BIT_RATES[j],
                        mBitRateMode, KEY_FRAME_INTERVAL, FRAME_RATE, B_FRAMES[i]);
                fmts.add(cfgs[j].getFormat());
            }
            Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support formats.",
                    areFormatsSupported(mCodecName, mMediaType, fmts));
            encoderNames[i] = mCodecName;
        }
        Predicate<Double> predicate = bdRate -> bdRate < 0.000001d;
        getQualityRegressionForCfgs(cfgsUnion, testInstances, encoderNames, res, FRAME_LIMIT,
                FRAME_RATE, true, predicate);
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE",
            "android.media.MediaFormat#KEY_BITRATE_MODE",
            "android.media.MediaFormat#KEY_MAX_B_FRAMES"})
    @Test
    public void testQualityRegressionOverBFrames() throws IOException, InterruptedException {
        qualityRegressionOverBFrames();
    }
}
