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

package android.media.codec.cts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.TestArgs;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Verification test for video encoding statistics.
 *
 * Check whether a higher bitrate gives a lower average QP reported from encoder
 *
 */
@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
public class VideoEncodingStatisticsTest extends VideoCodecTestBase {

    private static final String ENCODED_IVF_BASE = "football";
    private static final String INPUT_YUV = null;
    private static final String OUTPUT_YUV = SDCARD_DIR + File.separator +
            ENCODED_IVF_BASE + "_out.yuv";

    // YUV stream properties.
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int FPS = 30;
    // Default encoding bitrate.
    private static final int BITRATE = 400000;
    // List of bitrates used in quality and basic bitrate tests.
    private static final int[] TEST_BITRATES_SET = { 300000, 500000, 700000, 900000 };

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public String mCodecMimeType;

    @Parameterized.Parameter(2)
    public int mBitRateMode;

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String)arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] encodersForMime = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String encoder : encodersForMime) {
                if (TestArgs.shouldSkipCodec(encoder)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                testArgs[0] = encoder;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {AVC_MIME, VIDEO_ControlRateConstant},
                {AVC_MIME, VIDEO_ControlRateVariable},
                {HEVC_MIME, VIDEO_ControlRateConstant},
                {HEVC_MIME, VIDEO_ControlRateVariable},
        });
        return prepareParamList(exhaustiveArgsList);
    }

    private static CodecCapabilities getCodecCapabilities(
            String encoderName, String mime, boolean isEncoder) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (isEncoder != codecInfo.isEncoder()) {
                continue;
            }
            if (encoderName.equals(codecInfo.getName())) {
                return codecInfo.getCapabilitiesForType(mime);
            }
        }
        return null;
    }

    /**
     * Check whethera a higher bitrate gives a lower average QP
     *
     * Video streams with higher bitrate should have lower average qp.
     */
    private void testEncStatRateAvgQp(String codecName, String codecMimeType, int bitRateMode)
            throws Exception {
        int encodeSeconds = 9;      // Encoding sequence duration in seconds for each bitrate.
        float[] avgSeqQp = new float[TEST_BITRATES_SET.length];
        boolean[] completed = new boolean[TEST_BITRATES_SET.length];
        boolean skipped = true;
        ArrayList<MediaCodec.BufferInfo> bufInfos;

        CodecCapabilities caps = getCodecCapabilities(codecName, codecMimeType, true);
        Assume.assumeTrue(codecName + " does not support FEATURE_EncodingStatistics",
           caps.isFeatureSupported(CodecCapabilities.FEATURE_EncodingStatistics));

        for (int i = 0; i < TEST_BITRATES_SET.length; i++) {
            EncoderOutputStreamParameters params = getDefaultEncodingParameters(
                    INPUT_YUV,
                    ENCODED_IVF_BASE,
                    codecName,
                    codecMimeType,
                    encodeSeconds,
                    WIDTH,
                    HEIGHT,
                    FPS,
                    bitRateMode,
                    TEST_BITRATES_SET[i],
                    false);
            // Enable encoding statistics at VIDEO_ENCODING_STATISTICS_LEVEL_1
            params.encodingStatisticsLevel = MediaFormat.VIDEO_ENCODING_STATISTICS_LEVEL_1;
            ArrayList<ByteBuffer> codecConfigs = new ArrayList<>();
            VideoEncodeOutput videoEncodeOutput = encodeAsync(params, codecConfigs);
            bufInfos = videoEncodeOutput.bufferInfo;
            if (bufInfos == null) {
                // parameters not supported, try other bitrates
                completed[i] = false;
                continue;
            }
            completed[i] = true;
            skipped = false;
            if (videoEncodeOutput.encStat.encodedFrames > 0) {
                avgSeqQp[i] = (float) videoEncodeOutput.encStat.averageSeqQp
                                      / videoEncodeOutput.encStat.encodedFrames;
            }
        }

        if (skipped) {
            Log.i(TAG, "SKIPPING testEncodingStatisticsAvgQp(): no bitrates supported");
            return;
        }

        // First do a validity check - higher bitrates should results in lower QP.
        for (int i = 1; i < TEST_BITRATES_SET.length; i++) {
            if (!completed[i]) {
                continue;
            }
            for (int j = 0; j < i; j++) {
                if (!completed[j]) {
                    continue;
                }
                double differenceBitrate = TEST_BITRATES_SET[i] - TEST_BITRATES_SET[j];
                double differenceAvgQp = avgSeqQp[i] - avgSeqQp[j];
                if (differenceBitrate * differenceAvgQp >= 0) {
                    throw new RuntimeException("Target bitrates: " +
                            TEST_BITRATES_SET[j] + ", " + TEST_BITRATES_SET[i] +
                            ". Average QP: "
                            + avgSeqQp[j] + ", " + avgSeqQp[i]);
                }
            }
        }
    }

    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#FEATURE_EncodingStatistics",
            "android.media.MediaFormat#KEY_VIDEO_ENCODING_STATISTICS_LEVEL",
            "android.media.MediaFormat#KEY_VIDEO_QP_AVERAGE"})
    @Test
    public void testEncodingStatisticsAvgQp() throws Exception {
       testEncStatRateAvgQp(mCodecName, mCodecMimeType, mBitRateMode);
   }
}

