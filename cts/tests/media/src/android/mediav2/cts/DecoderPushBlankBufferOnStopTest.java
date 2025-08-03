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

package android.mediav2.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.ImageSurface;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Verify support for MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP
 * <p>
 * The component decodes a test clip to the surface provided by image reader. As the test has no
 * interest in the contents of input clip, the frames decoded are not rendered. After decoding
 * few frames, a call to stop is made. The image surface is observed for frames received. If
 * KEY_PUSH_BLANK_BUFFERS_ON_STOP is configured as '0' then image surface is expected to receive no
 * frames and if KEY_PUSH_BLANK_BUFFERS_ON_STOP is configured to 1, the image surface is expected
 * to receive frame(s) and they are expected to be blank
 */
@RunWith(Parameterized.class)
public class DecoderPushBlankBufferOnStopTest extends CodecDecoderTestBase {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final long WAIT_FOR_IMAGE_TIMEOUT_MS = 300;

    private final boolean mPushBlankBuffersOnStop;

    public DecoderPushBlankBufferOnStopTest(String decoder, String mediaType, String testFile,
            boolean pushBlankBuffersOnStop, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mPushBlankBuffersOnStop = pushBlankBuffersOnStop;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{4}")
    public static Collection<Object[]> input() {
        final List<Object[]> args = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_340x280_768kbps_30fps_hevc.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp"},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm"},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm"},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4"},
        }));
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        boolean[] boolStates = new boolean[]{true, false};
        for (Object[] arg : args) {
            for (boolean pushBlankOnStop : boolStates) {
                Object[] testArgs = new Object[arg.length + 2];
                testArgs[0] = arg[0];   // mediaType
                testArgs[1] = arg[1];   // test file
                testArgs[2] = pushBlankOnStop;
                testArgs[3] = String.format("%s", pushBlankOnStop ? "pushBlank" : "pushNone");
                exhaustiveArgsList.add(testArgs);
            }
        }
        return prepareParamList(exhaustiveArgsList, false, false, true, false,
                ComponentClass.SOFTWARE);
    }

    private boolean isBlankFrame(Image image) {
        int threshold = 0;
        for (Image.Plane plane : image.getPlanes()) {
            ByteBuffer buffer = plane.getBuffer();
            while (buffer.hasRemaining()) {
                int pixelValue = buffer.get() & 0xFF;
                if (pixelValue > threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check description of class {@link DecoderPushBlankBufferOnStopTest}
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PUSH_BLANK_BUFFERS_ON_STOP"})
    @SmallTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSimpleDecodeToSurface() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        assertTrue("Codec: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formatList));
        mImageSurface = new ImageSurface();
        setUpSurface(getWidth(format), getHeight(format), PixelFormat.RGBX_8888, 1,
                this::isBlankFrame);
        mSurface = mImageSurface.getSurface();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = new OutputManager();
        if (mPushBlankBuffersOnStop) {
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
        }
        configureCodec(format, true, false, false);
        mCodec.start();
        doWork(10);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        try (Image img = mImageSurface.getImage(WAIT_FOR_IMAGE_TIMEOUT_MS)) {
            if (!mPushBlankBuffersOnStop) {
                assertNull("Blank buffers are received by image surface for format: "
                        + format + "\n" + mTestConfig + mTestEnv, img);
            } else {
                assertNotNull("Blank buffers are not received by image surface for format: "
                        + format + "\n" + mTestConfig + mTestEnv, img);
                assertTrue("received image is not a blank buffer \n" + mTestConfig + mTestEnv,
                        isBlankFrame(img));
            }
        }
        mCodec.release();
        mExtractor.release();
    }
}
