/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.media.decoder.cts;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.fail;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.TestArgs;
import android.media.cts.TestUtils;
import android.os.Build;
import android.os.Environment;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.View;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetApi(24)
@RunWith(Parameterized.class)
@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to accuracy")
public class DecodeAccuracyTest extends DecodeAccuracyTestBase {

    private static final boolean IS_AT_LEAST_U = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)
            || ApiLevelUtil.codenameEquals("UpsideDownCake");
    private static final boolean IS_BEFORE_U = !IS_AT_LEAST_U;

    private static final String TAG = DecodeAccuracyTest.class.getSimpleName();
    private static final Field[] fields = R.raw.class.getFields();
    private static final int ALLOWED_GREATEST_PIXEL_DIFFERENCE = 90;
    private static final int OFFSET = 10;
    private static final long PER_TEST_TIMEOUT_MS = 60000;
    private static final String[] VIDEO_FILES = {
        // 144p
        "video_decode_accuracy_and_capability-h264_256x108_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_256x144_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_192x144_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_82x144_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_256x108_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_256x144_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_192x144_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_82x144_30fps.webm",
        // 240p
        "video_decode_accuracy_and_capability-h264_426x182_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_426x240_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_320x240_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_136x240_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_426x182_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_426x240_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_320x240_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_136x240_30fps.webm",
        // 360p
        "video_decode_accuracy_and_capability-h264_640x272_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_640x360_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_480x360_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_202x360_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_640x272_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_640x360_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_480x360_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_202x360_30fps.webm",
        // 480p
        "video_decode_accuracy_and_capability-h264_854x362_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_854x480_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_640x480_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_270x480_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_854x362_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_854x480_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_640x480_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_270x480_30fps.webm",
        // 720p
        "video_decode_accuracy_and_capability-h264_1280x544_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1280x720_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_960x720_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_406x720_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_1280x544_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1280x720_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_960x720_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_406x720_30fps.webm",
        // 1080p
        "video_decode_accuracy_and_capability-h264_1920x818_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1920x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1440x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_608x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_1920x818_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1920x1080_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1440x1080_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_608x1080_30fps.webm",
        // 1440p
        "video_decode_accuracy_and_capability-h264_2560x1090_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_2560x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1920x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_810x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_2560x1090_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_2560x1440_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1920x1440_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_810x1440_30fps.webm",
        // 2160p
        "video_decode_accuracy_and_capability-h264_3840x1634_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_3840x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_2880x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1216x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_3840x1634_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_3840x2160_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_2880x2160_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1216x2160_30fps.webm",
        // cropped
        "video_decode_with_cropping-h264_520x360_30fps.mp4",
        "video_decode_with_cropping-vp9_520x360_30fps.webm"
    };

    private static final String INP_PREFIX = WorkDir.getMediaDirString() +
            "assets/decode_accuracy/";

    private View videoView;
    private VideoViewFactory videoViewFactory;
    private String testName;
    private String fileName;
    private String decoderName;
    private String methodName;
    private SimplePlayer player;

    public DecodeAccuracyTest(String decoderName, String fileName, String testName) {
        this.testName = testName;
        this.fileName = fileName;
        this.decoderName = decoderName;
    }

    @After
    @Override
    public void tearDown() throws Exception {
        if (player != null) {
            player.release();
        }
        if (videoView != null) {
            getHelper().cleanUpView(videoView);
        }
        if (videoViewFactory != null) {
            videoViewFactory.release();
        }
        super.tearDown();
    }

    @Parameters(name = "{index}({0}_{2})")
    public static Collection<Object[]> input() throws IOException {
        final List<Object[]> testParams = new ArrayList<>();
        for (String file : VIDEO_FILES) {
            Pattern regex = Pattern.compile("^\\w+-(\\w+)_\\d+fps\\.\\w+");
            Matcher matcher = regex.matcher(file);
            String testName = "";
            if (matcher.matches()) {
                testName = matcher.group(1);
            }
            MediaFormat mediaFormat =
                    MediaUtils.getTrackFormatForResource(INP_PREFIX + file, "video");
            String mediaType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] componentNames = MediaUtils.getDecoderNamesForMime(mediaType);
            for (String componentName : componentNames) {
                if (TestArgs.shouldSkipCodec(componentName)) {
                    continue;
                }
                if (MediaUtils.supports(componentName, mediaFormat)) {
                    testParams.add(new Object[] {componentName, file, testName});
                    // Test only the first decoder that supports given format.
                    // Remove the following break statement to test all decoders on the device.
                    break;
                }
            }
        }
        return testParams;
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testGLViewDecodeAccuracy() throws Exception {
        this.methodName = "testGLViewDecodeAccuracy";
        runTest(new GLSurfaceViewFactory(), new VideoFormat(fileName), decoderName);
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testGLViewLargerHeightDecodeAccuracy() throws Exception {
        this.methodName = "testGLViewLargerHeightDecodeAccuracy";
        runTest(new GLSurfaceViewFactory(), getLargerHeightVideoFormat(new VideoFormat(fileName)),
            decoderName);
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testGLViewLargerWidthDecodeAccuracy() throws Exception {
        this.methodName = "testGLViewLargerWidthDecodeAccuracy";
        runTest(new GLSurfaceViewFactory(), getLargerWidthVideoFormat(new VideoFormat(fileName)),
            decoderName);
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewVideoDecodeAccuracy() throws Exception {
        this.methodName = "testSurfaceViewVideoDecodeAccuracy";
        runTest(new SurfaceViewFactory(), new VideoFormat(fileName), decoderName);
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewLargerHeightDecodeAccuracy() throws Exception {
        this.methodName = "testSurfaceViewLargerHeightDecodeAccuracy";
        runTest(new SurfaceViewFactory(), getLargerHeightVideoFormat(new VideoFormat(fileName)),
            decoderName);
    }

    @Test(timeout = PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewLargerWidthDecodeAccuracy() throws Exception {
        this.methodName = "testSurfaceViewLargerWidthDecodeAccuracy";
        runTest(new SurfaceViewFactory(), getLargerWidthVideoFormat(new VideoFormat(fileName)),
            decoderName);
    }

    private void runTest(VideoViewFactory videoViewFactory, VideoFormat vf, String decoderName) {
        Log.i(TAG, "Running test for " + vf.toPrettyString());
        if (!MediaUtils.canDecodeVideo(vf.getMimeType(), vf.getWidth(), vf.getHeight(), 30)) {
            MediaUtils.skipTest(TAG, "No supported codec is found.");
            return;
        }
        this.videoViewFactory = checkNotNull(videoViewFactory);
        this.videoView = videoViewFactory.createView(getHelper().getContext());
        final int maxRetries = 3;
        for (int retry = 1; retry <= maxRetries; retry++) {
            // If view is intended and available to display.
            if (videoView != null) {
                getHelper().generateView(videoView);
            }
            try {
                videoViewFactory.waitForViewIsAvailable();
                break;
            } catch (Exception exception) {
                Log.e(TAG, exception.getMessage());
                if (retry == maxRetries) {
                    fail("Timeout waiting for a valid surface.");
                } else {
                    Log.w(TAG, "Try again...");
                    bringActivityToFront();
                }
            }
        }
        final int golden = getGoldenId(vf.getDescription(), vf.getOriginalSize());
        assertTrue("No golden found.", golden != 0);
        decodeVideo(vf, videoViewFactory, decoderName);
        validateResult(vf, videoViewFactory.getVideoViewSnapshot(), golden, decoderName);
    }

    private void decodeVideo(VideoFormat videoFormat, VideoViewFactory videoViewFactory,
            String decoderName) {
        this.player = new SimplePlayer(getHelper().getContext(), decoderName);
        final SimplePlayer.PlayerResult playerResult = player.decodeVideoFrames(
                videoViewFactory.getSurface(), videoFormat, 10);
        assertTrue(playerResult.getFailureMessage(), playerResult.isSuccess());
    }

    private void validateResult(
            VideoFormat videoFormat, VideoViewSnapshot videoViewSnapshot, int goldenId,
            String decoderName) {
        final Bitmap result = checkNotNull("The expected bitmap from snapshot is null",
                getHelper().generateBitmapFromVideoViewSnapshot(videoViewSnapshot));
        final Bitmap golden = getHelper().generateBitmapFromImageResourceId(goldenId);

        int ignorePixels = 0;
        if (IS_BEFORE_U && TestUtils.isMtsMode()) {
            if (TestUtils.isMainlineCodec(decoderName)) {
                // some older systems don't give proper behavior at the edges (in system code).
                // while we can't fix the behavior at the edges, we can verify that the rest
                // of the image is within tolerance. b/256807044
                ignorePixels = 1;
            }
        }
        final BitmapCompare.Difference difference = BitmapCompare.computeMinimumDifference(
                result, golden, ignorePixels, videoFormat.getOriginalWidth(),
                videoFormat.getOriginalHeight());

        if (difference.greatestPixelDifference > ALLOWED_GREATEST_PIXEL_DIFFERENCE) {
            /* save failing file */
            File failed = new File(Environment.getExternalStorageDirectory(),
                                   "failed_" + methodName + "_" + testName + ".png");
            try (FileOutputStream fileStream = new FileOutputStream(failed)) {
                result.compress(Bitmap.CompressFormat.PNG, 0 /* ignored for PNG */, fileStream);
                fileStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.d(TAG, testName + " saved " + failed.getAbsolutePath());
        }

        assertTrue("With the best matched border crop ("
                + difference.bestMatchBorderCrop.first + ", "
                + difference.bestMatchBorderCrop.second + "), "
                + "greatest pixel difference is "
                + difference.greatestPixelDifference
                + (difference.greatestPixelDifferenceCoordinates != null
                        ? " at (" + difference.greatestPixelDifferenceCoordinates.first + ", "
                            + difference.greatestPixelDifferenceCoordinates.second + ")" : "")
                + " which is over the allowed difference " + ALLOWED_GREATEST_PIXEL_DIFFERENCE,
                difference.greatestPixelDifference <= ALLOWED_GREATEST_PIXEL_DIFFERENCE);
    }

    private static VideoFormat getLargerHeightVideoFormat(VideoFormat videoFormat) {
        return new VideoFormat(videoFormat) {
            @Override
            public int getHeight() {
                return super.getHeight() + OFFSET;
            }

            @Override
            public boolean isAbrEnabled() {
                return true;
            }
        };
    }

    private static VideoFormat getLargerWidthVideoFormat(VideoFormat videoFormat) {
        return new VideoFormat(videoFormat) {
            @Override
            public int getWidth() {
                return super.getWidth() + OFFSET;
            }

            @Override
            public boolean isAbrEnabled() {
                return true;
            }
        };
    }

    /**
     * Returns the resource id by matching parts of the video and golden file name.
     */
    private static int getGoldenId(String description, String size) {
        for (Field field : fields) {
            try {
                final String name = field.getName();
                if (name.contains("golden") && name.contains(description) && name.contains(size)) {
                    int id = field.getInt(null);
                    return field.getInt(null);
                }
            } catch (IllegalAccessException | NullPointerException e) {
                // No file found.
            }
        }
        return 0;
    }

}
