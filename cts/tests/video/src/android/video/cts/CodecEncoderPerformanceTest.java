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

package android.video.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Operating rate is expected to be met by encoder only in surface mode and not in byte buffer mode.
 * As camera has limited frame rates and resolutions, it is not possible to test encoder
 * operating rate alone. So we are going ahead with transcode tests as a way to verify
 * encoder performances. This test calls decoder to decode to a surface that is coupled to encoder.
 * This assumes encoder will not be faster than decode and expects half the operating rate
 * to be met for encoders.
 */
@RunWith(Parameterized.class)
public class CodecEncoderPerformanceTest extends CodecEncoderPerformanceTestBase {
    private static final String LOG_TAG = CodecEncoderPerformanceTest.class.getSimpleName();

    public CodecEncoderPerformanceTest(String decoderName, String testFile, String encoderMime,
            String encoderName, int bitrate, int keyPriority, float scalingFactor,
            boolean isAsync, int maxBFrames) {
        super(decoderName, testFile, encoderMime, encoderName, bitrate, keyPriority, scalingFactor,
                isAsync, maxBFrames);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{2}_{3}_{5}_{6}_{7}_{8}")
    public static Collection<Object[]> input() throws IOException {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // Filename, Recommended AVC bitrate
                {"crowd_run_720x480_30fps_avc.mp4", 2000000},
                {"crowd_run_1280x720_30fps_avc.mp4", 4000000},
                {"crowd_run_1920x1080_30fps_avc.mp4", 8000000},
                {"crowd_run_3840x2160_30fps_hevc.mp4", 20000000},
                // TODO (b/194721211) Enable 8k tests
                //{"crowd_run_7680x4320_30fps_hevc.mp4", 40000000},
        });
        // Prepares the params list with the supported Hardware decoder, encoders in the device
        // combined with the key priority and scaling factor
        final List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            // Gets the format for the first video track found
            MediaFormat format = getVideoFormat((String) arg[0]);
            if (format == null) {
                Log.e(LOG_TAG, "Test vector is ignored as it has no video tracks present " +
                        "in the file: " + ((String) arg[0]));
                continue;
            }
            String decoderMime = format.getString(MediaFormat.KEY_MIME);
            ArrayList<MediaFormat> formatsList = new ArrayList<>();
            formatsList.add(format);
            ArrayList<String> listOfDecoders = selectHardwareCodecs(decoderMime, formatsList,
                    null, false);
            if (listOfDecoders.size() == 0) continue;
            String decoder = listOfDecoders.get(0);
            boolean[] boolStates = {true, false};
            for (String encoderMime : getMimesOfAvailableHardwareVideoEncoders()) {
                // Calculate the bitrate based on the encode mime.
                int bitrate = (int)(((int) arg[1]) * getBitrateScalingFactor(encoderMime));
                MediaFormat mockFmt = setUpEncoderFormat(format, encoderMime, bitrate);
                ArrayList<MediaFormat> mockFmtList = new ArrayList<>();
                mockFmtList.add(mockFmt);
                ArrayList<String> listOfEncoders = selectHardwareCodecs(encoderMime, mockFmtList,
                        null, true);
                for (String encoder : listOfEncoders) {
                    for (int keyPriority : KEY_PRIORITIES_LIST) {
                        for (float scalingFactor : SCALING_FACTORS_LIST) {
                            for (boolean isAsync : boolStates) {
                                if (keyPriority == 1 || (scalingFactor > 0.0
                                        && scalingFactor <= 1.0)) {
                                    argsList.add(
                                            new Object[]{decoder, arg[0], encoderMime, encoder,
                                                    bitrate, keyPriority, scalingFactor, isAsync,
                                                    0});
                                    if (encoderMime.equals("video/avc") || encoderMime.equals(
                                            "video/hevc")) {
                                        argsList.add(
                                                new Object[]{decoder, arg[0], encoderMime, encoder,
                                                        bitrate, keyPriority, scalingFactor,
                                                        isAsync, 2});
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return argsList;
    }

    /**
     * Validates performance of hardware accelerated video encoders
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PRIORITY",
            "android.media.MediaFormat#KEY_OPERATING_RATE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testPerformanceOfHardwareVideoEncoders() throws IOException, InterruptedException {
        encode();
        String log = String.format("DecodeMime: %s, Decoder: %s, resolution: %dp, EncodeMime: %s," +
                " Encoder: %s, Key-priority: %d :: ", mDecoderMime, mDecoderName, mHeight,
                mEncoderMime, mEncoderName, mKeyPriority);
        int maxExpectedFps = getMaxExpectedFps(mWidth, mHeight);
        double fpsToleranceFactor = FPS_TOLERANCE_FACTOR;
        if (VNDK_VERSION <= Build.VERSION_CODES.TIRAMISU) {
            fpsToleranceFactor = Math.min(0.9, fpsToleranceFactor);
        }
        double expectedFps =
                Math.min(mOperatingRateExpected * fpsToleranceFactor, maxExpectedFps);
        Log.d(LOG_TAG, log + "act/exp fps: " + mAchievedFps + "/" + expectedFps);
        assertTrue("Unable to achieve the expected rate. " + log + "act/exp fps: " + mAchievedFps
                + "/" + expectedFps, mAchievedFps >= expectedFps);
    }
}
