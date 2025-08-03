/*
 * Copyright 2022 The Android Open Source Project
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

package android.media.encoder.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.media.MediaFormat;
import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
public class VideoEncoderCapabilitiesTest {
    final private String mMediaType;
    final private int mWidth;
    final private int mHeight;
    final private int mFrameRate;
    final private int mBitRate;
    final private boolean mOptional;

    @Parameterized.Parameters(name = "{index}_{0}_{1}x{2}_{3}_{4}_{5}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // MediaType, width, height, frame-rate, bit-rate, optional
                // CDD 5.2.2 C-1-2, C-2-1
                {MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240, 20, 384000, false},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 720, 480, 30, 2000000, false},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 30, 4000000, true},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080, 30, 10000000, true},

                // CDD 5.2.3 C-1-1, C-2-1
                {MediaFormat.MIMETYPE_VIDEO_VP8, 320, 180, 30, 800000, false},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 640, 360, 30, 2000000, false},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 1280, 720, 30, 4000000, true},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 1920, 1080, 30, 10000000, true},

                // CDD 5.2.4
                {MediaFormat.MIMETYPE_VIDEO_VP9, 720, 480, 30, 1600000, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1280, 720, 30, 4000000, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 1920, 1080, 30, 5000000, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 3840, 2160, 30, 20000000, true},

                // CDD 5.2.5
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 720, 480, 30, 1600000, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1280, 720, 30, 4000000, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080, 30, 5000000, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 3840, 2160, 30, 20000000, true},

                {MediaFormat.MIMETYPE_VIDEO_AV1, 320, 180, 30, 800000, true},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 640, 360, 30, 2000000, true},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 720, 480, 30, 1600000, true},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 1280, 720, 30, 4000000, true},

        });
        return exhaustiveArgsList;
    }

    public VideoEncoderCapabilitiesTest(String mediaType, int width, int height, int frameRate,
                                        int bitRate, boolean optional) {
        mMediaType = mediaType;
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        mBitRate = bitRate;
        mOptional = optional;
    }

    // Tests encoder profiles required by CDD.
    @CddTest(requirements = {"5.2.2/C-1-2", "5.2.3/C-1-1", "5.2.4/C-SR-1", "5.2.5/C-SR-1"})
    @Test
    public void testEncoderAvailability() {
        MediaFormat format = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        if (mOptional) {
            assumeTrue("Device doesn't support encoding an optional format: " + format,
                    MediaUtils.canEncode(format));
        } else {
            assertTrue("Device doesn't support encoding a mandatory format: " + format,
                    MediaUtils.canEncode(format));
        }
    }
}
