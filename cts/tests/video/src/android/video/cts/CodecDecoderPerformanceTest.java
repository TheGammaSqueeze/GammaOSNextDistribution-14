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
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class CodecDecoderPerformanceTest extends CodecDecoderPerformanceTestBase {
    private static final String LOG_TAG = CodecDecoderPerformanceTest.class.getSimpleName();

    public CodecDecoderPerformanceTest(String decoderName, String testFile, int keyPriority,
            float scalingFactor) {
        super(decoderName, testFile, keyPriority, scalingFactor);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{2}_{3}")
    public static Collection<Object[]> input() throws IOException {
        final String[] fileList = new String[]{
                // Video - Filename
                // AVC
                "crowd_run_720x480_30fps_avc.mp4",
                "crowd_run_1280x720_30fps_avc.mp4",
                "crowd_run_1920x1080_30fps_avc.mp4",
                "crowd_run_3840x2160_30fps_avc.mp4",
                // HEVC
                "crowd_run_720x480_30fps_hevc.mp4",
                "crowd_run_1280x720_30fps_hevc.mp4",
                "crowd_run_1920x1080_30fps_hevc.mp4",
                "crowd_run_3840x2160_30fps_hevc.mp4",
                // TODO (b/194721211) Enable 8k tests
                //"crowd_run_7680x4320_30fps_hevc.mp4",
                // VP8
                "crowd_run_720x480_30fps_vp8.webm",
                "crowd_run_1280x720_30fps_vp8.webm",
                "crowd_run_1920x1080_30fps_vp8.webm",
                "crowd_run_3840x2160_30fps_vp8.webm",
                // VP9
                "crowd_run_720x480_30fps_vp9.webm",
                "crowd_run_1280x720_30fps_vp9.webm",
                "crowd_run_1920x1080_30fps_vp9.webm",
                "crowd_run_3840x2160_30fps_vp9.webm",
                // TODO (b/194721211) Enable 8k tests
                //"crowd_run_7680x4320_30fps_vp9.webm",
                // AV1
                "crowd_run_720x480_30fps_av1.mp4",
                "crowd_run_1280x720_30fps_av1.mp4",
                "crowd_run_1920x1080_30fps_av1.mp4",
                "crowd_run_3840x2160_30fps_av1.mp4",
                // TODO (b/194721211) Enable 8k tests
                //"crowd_run_7680x4320_30fps_av1.mp4",
                // MPEG-2
                "crowd_run_720x480_30fps_mpeg2.mp4",
                "crowd_run_1280x720_30fps_mpeg2.mp4",
                "crowd_run_1920x1080_30fps_mpeg2.mp4",
                "crowd_run_3840x2160_30fps_mpeg2.mp4",
                // TODO (b/194721211) Enable 8k tests
                //"crowd_run_7680x4320_30fps_mpeg2.mp4",
        };
        // Prepares the params list combining with supported Hardware decoders, key priority
        // and scaling factor.
        final List<Object[]> argsList = new ArrayList<>();
        for (String fileName : fileList) {
            // Gets the format for the first video track found
            MediaFormat format = getVideoFormat(fileName);
            if (format == null) {
                Log.e(LOG_TAG, "Test vector is ignored as it has no video tracks present " +
                        "in the file: " + fileName);
                continue;
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            ArrayList<MediaFormat> formatsList = new ArrayList<>();
            formatsList.add(format);
            ArrayList<String> listOfDecoders = selectHardwareCodecs(mime, formatsList, null,
                    false);
            for (String decoder : listOfDecoders) {
                for (int keyPriority : KEY_PRIORITIES_LIST) {
                    for (float scalingFactor : SCALING_FACTORS_LIST) {
                        if (keyPriority == 1 || (scalingFactor > 0.0 && scalingFactor <= 1.0)) {
                            argsList.add(new Object[]{decoder, fileName, keyPriority,
                                    scalingFactor});
                        }
                    }
                }
            }
        }
        return argsList;
    }

    /**
     * Validates performance of hardware accelerated video decoders
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PRIORITY",
            "android.media.MediaFormat#KEY_OPERATING_RATE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testPerformanceOfHardwareVideoDecoders() throws IOException, InterruptedException {
        decode();
        String log = String.format("DecodeMime: %s, Decoder: %s, resolution: %dp, Key-priority: " +
                "%d :: ", mDecoderMime, mDecoderName, mHeight, mKeyPriority);
        int maxExpectedFps = getMaxExpectedFps(mWidth, mHeight);
        double expectedFps =
                Math.min(mOperatingRateExpected * FPS_TOLERANCE_FACTOR, maxExpectedFps);
        Log.d(LOG_TAG, log + "act/exp fps: " + mAchievedFps + "/" + expectedFps);
        assertTrue("Unable to achieve the expected rate. " + log + "act/exp fps: " + mAchievedFps
                + "/" + expectedFps, mAchievedFps >= expectedFps);
    }
}
