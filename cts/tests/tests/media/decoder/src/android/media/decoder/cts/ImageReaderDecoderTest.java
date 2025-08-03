/*
 * Copyright 2014 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.CodecUtils;
import android.media.cts.TestArgs;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Basic test for ImageReader APIs.
 * <p>
 * It uses MediaCodec to decode a short video stream, send the video frames to
 * the surface provided by ImageReader. Then compare if output buffers of the
 * ImageReader matches the output buffers of the MediaCodec. The video format
 * used here is AVC although the compression format doesn't matter for this
 * test. For decoder test, hw and sw decoders are tested,
 * </p>
 */
@Presubmit
@SmallTest
@RequiresDevice
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class ImageReaderDecoderTest {
    private static final String TAG = "ImageReaderDecoderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final long DEFAULT_TIMEOUT_US = 10000;
    private static final long WAIT_FOR_IMAGE_TIMEOUT_MS = 1000;
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/";
    private static final int NUM_FRAME_DECODED = 100;
    // video decoders only support a single outstanding image with the consumer
    private static final int MAX_NUM_IMAGES = 1;
    private static final float COLOR_STDEV_ALLOWANCE = 5f;
    private static final float COLOR_DELTA_ALLOWANCE = 5f;

    private final static int MODE_IMAGEREADER = 0;
    private final static int MODE_IMAGE       = 1;

    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private ImageReader mReader;
    private Surface mReaderSurface;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ImageListener mImageListener;

    public String mMime;
    public String mCodecName;
    public MediaAsset mMediaAsset;
    public int mMode;
    public String mTestId;
    MediaCodec mDecoder = null;
    MediaExtractor mExtractor = null;

    public ImageReaderDecoderTest(String mime, String codecName, MediaAsset asset, int mode,
                                  String testId) {
        mMime = mime;
        mCodecName = codecName;
        mMediaAsset = asset;
        mMode = mode;
        mTestId = testId;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{4}")
    public static Collection<Object[]> input() {
        final List<Object[]> argsList = new ArrayList<>();
        for (MediaAssets assets : ASSETS) {
            String mime = assets.getMime();
            if (TestArgs.shouldSkipMediaType(mime)) {
                continue;
            }
            String[] decoders = MediaUtils.getDecoderNamesForMime(mime);
            for (String decoder: decoders) {
                if (TestArgs.shouldSkipCodec(decoder)) {
                    continue;
                }
                for (MediaAsset asset : assets.getAssets()) {
                    String id = asset.getWidth() + "x" + asset.getHeight();
                    id += "_" + asset.getBitDepth() + "bit";
                    if (asset.getIsSwirl()) {
                        id += "_swirl";
                        argsList.add(new Object[]{mime, decoder, asset, MODE_IMAGE, id + "_image"});
                    }
                    argsList.add(new Object[]{mime, decoder, asset, MODE_IMAGEREADER,
                            id + "_imagereader"});
                }
            }
        }
        return argsList;
    }

    @Before
    public void setUp() throws Exception {
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mImageListener = new ImageListener();

        mDecoder = MediaCodec.createByCodecName(mCodecName);
        mExtractor = new MediaExtractor();
    }

    @After
    public void tearDown() throws Exception {
        closeImageReader();
        mHandlerThread.quitSafely();
        mHandler = null;
        if (mExtractor != null) {
            mExtractor.release();
        }
        if (mDecoder != null) {
            mDecoder.release();
        }
    }

    static class MediaAsset {
        public MediaAsset(String resource, int width, int height, boolean isSwirl,
                          int bitDepth) {
            mResource = resource;
            mWidth = width;
            mHeight = height;
            mIsSwirl = isSwirl;
            mBitDepth = bitDepth;
        }

        public MediaAsset(String resource, int width, int height) {
            this(resource, width, height, true, 8);
        }

        public MediaAsset(String resource, int width, int height, boolean isSwirl) {
            this(resource, width, height, isSwirl, 8);
        }

        public MediaAsset(String resource, int width, int height, int bitDepth) {
            this(resource, width, height, true, bitDepth);
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public boolean getIsSwirl() {
            return mIsSwirl;
        }

        public int getBitDepth() {
            return mBitDepth;
        }

        public String getResource() {
            return mResource;
        }

        private final String mResource;
        private final int mWidth;
        private final int mHeight;
        private final boolean mIsSwirl;
        private final int mBitDepth;
    }

    static class MediaAssets {
        public MediaAssets(String mime, MediaAsset... assets) {
            mMime = mime;
            mAssets = assets;
        }

        public String getMime() {
            return mMime;
        }

        public MediaAsset[] getAssets() {
            return mAssets;
        }

        private final String mMime;
        private final MediaAsset[] mAssets;
    }

    static final String mInpPrefix = WorkDir.getMediaDirString();

    private static MediaAssets H263_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_H263,
            new MediaAsset("swirl_176x144_h263.3gp", 176, 144),
            new MediaAsset("swirl_352x288_h263.3gp", 352, 288),
            new MediaAsset("swirl_128x96_h263.3gp", 128, 96));

    private static MediaAssets MPEG4_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_MPEG4,
            new MediaAsset("swirl_128x128_mpeg4.mp4", 128, 128),
            new MediaAsset("swirl_144x136_mpeg4.mp4", 144, 136),
            new MediaAsset("swirl_136x144_mpeg4.mp4", 136, 144),
            new MediaAsset("swirl_132x130_mpeg4.mp4", 132, 130),
            new MediaAsset("swirl_130x132_mpeg4.mp4", 130, 132));

    private static MediaAssets H264_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            new MediaAsset("swirl_128x128_h264.mp4", 128, 128),
            new MediaAsset("swirl_144x136_h264.mp4", 144, 136),
            new MediaAsset("swirl_136x144_h264.mp4", 136, 144),
            new MediaAsset("swirl_132x130_h264.mp4", 132, 130),
            new MediaAsset("swirl_130x132_h264.mp4", 130, 132),
            new MediaAsset("swirl_128x128_h264_10bit.mp4", 128, 128, 10),
            new MediaAsset("swirl_144x136_h264_10bit.mp4", 144, 136, 10),
            new MediaAsset("swirl_136x144_h264_10bit.mp4", 136, 144, 10),
            new MediaAsset("swirl_132x130_h264_10bit.mp4", 132, 130, 10),
            new MediaAsset("swirl_130x132_h264_10bit.mp4", 130, 132, 10),
            new MediaAsset("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                    480, 360, false));

    private static MediaAssets H265_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            new MediaAsset("swirl_128x128_h265.mp4", 128, 128),
            new MediaAsset("swirl_144x136_h265.mp4", 144, 136),
            new MediaAsset("swirl_136x144_h265.mp4", 136, 144),
            new MediaAsset("swirl_132x130_h265.mp4", 132, 130),
            new MediaAsset("swirl_130x132_h265.mp4", 130, 132),
            new MediaAsset("swirl_128x128_h265_10bit.mp4", 128, 128, 10),
            new MediaAsset("swirl_144x136_h265_10bit.mp4", 144, 136, 10),
            new MediaAsset("swirl_136x144_h265_10bit.mp4", 136, 144, 10),
            new MediaAsset("swirl_132x130_h265_10bit.mp4", 132, 130, 10),
            new MediaAsset("swirl_130x132_h265_10bit.mp4", 130, 132, 10));

    private static MediaAssets VP8_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_VP8,
            new MediaAsset("swirl_128x128_vp8.webm", 128, 128),
            new MediaAsset("swirl_144x136_vp8.webm", 144, 136),
            new MediaAsset("swirl_136x144_vp8.webm", 136, 144),
            new MediaAsset("swirl_132x130_vp8.webm", 132, 130),
            new MediaAsset("swirl_130x132_vp8.webm", 130, 132));

    private static MediaAssets VP9_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_VP9,
            new MediaAsset("swirl_128x128_vp9.webm", 128, 128),
            new MediaAsset("swirl_144x136_vp9.webm", 144, 136),
            new MediaAsset("swirl_136x144_vp9.webm", 136, 144),
            new MediaAsset("swirl_132x130_vp9.webm", 132, 130),
            new MediaAsset("swirl_130x132_vp9.webm", 130, 132),
            new MediaAsset("swirl_128x128_vp9_10bit.webm", 128, 128, 10),
            new MediaAsset("swirl_144x136_vp9_10bit.webm", 144, 136, 10),
            new MediaAsset("swirl_136x144_vp9_10bit.webm", 136, 144, 10),
            new MediaAsset("swirl_132x130_vp9_10bit.webm", 132, 130, 10),
            new MediaAsset("swirl_130x132_vp9_10bit.webm", 130, 132, 10));

    private static MediaAssets AV1_ASSETS = new MediaAssets(
            MediaFormat.MIMETYPE_VIDEO_AV1,
            new MediaAsset("swirl_128x128_av1.webm", 128, 128),
            new MediaAsset("swirl_144x136_av1.webm", 144, 136),
            new MediaAsset("swirl_136x144_av1.webm", 136, 144),
            new MediaAsset("swirl_132x130_av1.webm", 132, 130),
            new MediaAsset("swirl_130x132_av1.webm", 130, 132),
            new MediaAsset("swirl_128x128_av1_10bit.webm", 128, 128, 10),
            new MediaAsset("swirl_144x136_av1_10bit.webm", 144, 136, 10),
            new MediaAsset("swirl_136x144_av1_10bit.webm", 136, 144, 10),
            new MediaAsset("swirl_132x130_av1_10bit.webm", 132, 130, 10),
            new MediaAsset("swirl_130x132_av1_10bit.webm", 130, 132, 10));

    static final float SWIRL_FPS = 12.f;

    private static MediaAssets[] ASSETS = {H263_ASSETS, MPEG4_ASSETS, H264_ASSETS, H265_ASSETS,
            VP8_ASSETS, VP9_ASSETS, AV1_ASSETS};

   boolean isColorFormatSupported(CodecCapabilities caps, int colorFormat) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void decodeTest() throws Exception {
        int imageFormat = ImageFormat.YUV_420_888;
        int colorFormat = COLOR_FormatYUV420Flexible;
        String video = mMediaAsset.getResource();
        int width = mMediaAsset.getWidth();
        int height = mMediaAsset.getHeight();

        if (8 == mMediaAsset.getBitDepth()) {
            imageFormat = ImageFormat.YUV_420_888;
            colorFormat = COLOR_FormatYUV420Flexible;
        } else {
            imageFormat = ImageFormat.YCBCR_P010;
            colorFormat = COLOR_FormatYUVP010;
        }

        if (DEBUG) {
            Log.d(TAG, "videoDecode " + mCodecName + " " + width + "x" + height + " bit depth " +
                    mMediaAsset.getBitDepth());
        }

        MediaFormat mediaFormat = null;

        Preconditions.assertTestFileExists(mInpPrefix + video);
        mExtractor.setDataSource(mInpPrefix + video);

        mediaFormat = mExtractor.getTrackFormat(0);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9) && video.contains("10bit")) {
            // TODO: b/295804596 - parse color profiles in vp9
            // In some cases, webm extractor may not signal
            // profile for 10-bit VP9 clips. In such cases, set profile to a
            // 10-bit compatible profile.
            mediaFormat.setInteger(MediaFormat.KEY_PROFILE, CodecProfileLevel.VP9Profile2);
        }

        MediaCodecInfo info = mDecoder.getCodecInfo();
        CodecCapabilities caps = info.getCapabilitiesForType(mMime);
        VideoCapabilities videoCaps = caps.getVideoCapabilities();

        assumeTrue("Media format " + mediaFormat + " is not supported by " + mCodecName,
                caps.isFormatSupported(mediaFormat));
        assumeTrue(mMediaAsset.getWidth() + "x" + mMediaAsset.getHeight() + " @ " +
                SWIRL_FPS + " fps is not supported by " + mCodecName,
                videoCaps.areSizeAndRateSupported(mMediaAsset.getWidth(),
                mMediaAsset.getHeight(), SWIRL_FPS));
        assumeTrue("Color format " + colorFormat + " is not supported by " + mCodecName,
                isColorFormatSupported(caps, colorFormat));

        decodeFramesToImage(
                mDecoder, mExtractor, mediaFormat,
                width, height, imageFormat, mMode, mMediaAsset.getIsSwirl());

        mDecoder.stop();

    }

    private static class ImageListener implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Pair<Image, Exception>> mQueue =
                new LinkedBlockingQueue<Pair<Image, Exception>>();

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                mQueue.put(Pair.create(reader.acquireNextImage(), null /* Exception */));
            } catch (Exception e) {
                // pass any exception back to the other thread, taking the exception
                // here crashes the instrumentation in cts/junit.
                Log.e(TAG, "Can't handle Exceptions in onImageAvailable " + e);
                try {
                    mQueue.put(Pair.create(null /* Image */, e));
                } catch (Exception e2) {
                    // ignore the nested exception, other side will see a timeout.
                    Log.e(TAG, "Failed to send exception info across queue: " + e2);
                }
            }
        }

        /**
         * Get an image from the image reader.
         *
         * @param timeout Timeout value for the wait.
         * @return The image from the image reader.
         */
        public Image getImage(long timeout) throws InterruptedException {
            Pair<Image,Exception> imageResult = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
            Image image = imageResult.first;
            Exception e = imageResult.second;

            assertNull("onImageAvailable() generated an exception: " + e, e);
            assertNotNull("Wait for an image timed out in " + timeout + "ms", image);
            return image;
        }
    }

    /**
     * Decode video frames to image reader.
     */
    private void decodeFramesToImage(
            MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat,
            int width, int height, int imageFormat, int mode, boolean checkSwirl)
            throws InterruptedException {
        ByteBuffer[] decoderInputBuffers;
        ByteBuffer[] decoderOutputBuffers;

        // Configure decoder.
        if (VERBOSE) Log.v(TAG, "stream format: " + mediaFormat);
        if (mode == MODE_IMAGEREADER) {
            createImageReader(width, height, imageFormat, MAX_NUM_IMAGES, mImageListener);
            decoder.configure(mediaFormat, mReaderSurface, null /* crypto */, 0 /* flags */);
        } else {
            assertEquals(mode, MODE_IMAGE);
            decoder.configure(mediaFormat, null /* surface */, null /* crypto */, 0 /* flags */);
        }

        decoder.start();
        decoderInputBuffers = decoder.getInputBuffers();
        decoderOutputBuffers = decoder.getOutputBuffers();
        extractor.selectTrack(0);

        // Start decoding and get Image, only test the first NUM_FRAME_DECODED frames.
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int outputFrameCount = 0;
        while (!sawOutputEOS && outputFrameCount < NUM_FRAME_DECODED) {
            if (VERBOSE) Log.v(TAG, "loop:" + outputFrameCount);
            // Feed input frame.
            if (!sawInputEOS) {
                int inputBufIndex = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = decoderInputBuffers[inputBufIndex];
                    int sampleSize =
                        extractor.readSampleData(dstBuf, 0 /* offset */);

                    if (VERBOSE) Log.v(TAG, "queue a input buffer, idx/size: "
                        + inputBufIndex + "/" + sampleSize);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        if (VERBOSE) Log.v(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }

                    decoder.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }

            // Get output frame
            int res = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (VERBOSE) Log.v(TAG, "got a buffer: " + info.size + "/" + res);
            if (res == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) Log.v(TAG, "no output frame available");
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // decoder output buffers changed, need update.
                if (VERBOSE) Log.v(TAG, "decoder output buffers changed");
                decoderOutputBuffers = decoder.getOutputBuffers();
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // this happens before the first frame is returned.
                MediaFormat outFormat = decoder.getOutputFormat();
                if (VERBOSE) Log.v(TAG, "decoder output format changed: " + outFormat);
            } else if (res < 0) {
                // Should be decoding error.
                fail("unexpected result from decoder.dequeueOutputBuffer: " + res);
            } else {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }

                // res >= 0: normal decoding case, copy the output buffer.
                // Will use it as reference to valid the ImageReader output
                // Some decoders output a 0-sized buffer at the end. Ignore those.
                boolean doRender = (info.size != 0);

                if (doRender) {
                    outputFrameCount++;
                    String fileName = DEBUG_FILE_NAME_BASE + mCodecName + "_" + mTestId + ".yuv";

                    Image image = null;
                    try {
                        if (mode == MODE_IMAGE) {
                            image = decoder.getOutputImage(res);
                        } else {
                            decoder.releaseOutputBuffer(res, doRender);
                            res = -1;
                            // Read image and verify
                            image = mImageListener.getImage(WAIT_FOR_IMAGE_TIMEOUT_MS);
                        }
                        validateImage(image, width, height, imageFormat, fileName);

                        if (checkSwirl) {
                            try {
                                validateSwirl(image);
                            } catch (Throwable e) {
                                dumpFile(fileName, getDataFromImage(image));
                                throw e;
                            }
                        }
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                if (res >= 0) {
                    decoder.releaseOutputBuffer(res, false /* render */);
                }
            }
        }
    }

    /**
     * Validate image based on format and size.
     *
     * @param image The image to be validated.
     * @param width The image width.
     * @param height The image height.
     * @param format The image format.
     * @param filePath The debug dump file path, null if don't want to dump to file.
     */
    public static void validateImage(
            Image image, int width, int height, int format, String filePath) {
        if (VERBOSE) {
            Plane[] imagePlanes = image.getPlanes();
            Log.v(TAG, "Image " + filePath + " Info:");
            Log.v(TAG, "first plane pixelstride " + imagePlanes[0].getPixelStride());
            Log.v(TAG, "first plane rowstride " + imagePlanes[0].getRowStride());
            Log.v(TAG, "Image timestamp:" + image.getTimestamp());
        }

        assertNotNull("Input image is invalid", image);
        assertEquals("Format doesn't match", format, image.getFormat());
        assertEquals("Width doesn't match", width, image.getCropRect().width());
        assertEquals("Height doesn't match", height, image.getCropRect().height());

        if(VERBOSE) Log.v(TAG, "validating Image");
        byte[] data = getDataFromImage(image);
        assertTrue("Invalid image data", data != null && data.length > 0);

        validateYuvData(data, width, height, format, image.getTimestamp());

        if (VERBOSE && filePath != null) {
            dumpFile(filePath, data);
        }
    }

    private static void validateSwirl(Image image) {
        Rect crop = image.getCropRect();
        final int NUM_SIDES = 4;
        final int step = 8;      // the width of the layers
        long[][] rawStats = new long[NUM_SIDES][10];
        // expected colors for YUV 4:2:0 bit-depth 8
        int[][] colors = new int[][] {
            { 111, 96, 204 }, { 178, 27, 174 }, { 100, 192, 92 }, { 106, 117, 62 }
        };

        // successively accumulate statistics for each layer of the swirl
        // by using overlapping rectangles, and the observation that
        // layer_i = rectangle_i - rectangle_(i+1)
        int lastLayer = 0;
        int layer = 0;
        boolean lastLayerValid = false;
        for (int pos = 0; ; pos += step) {
            Rect area = new Rect(pos - step, pos, crop.width() / 2, crop.height() + 2 * step - pos);
            if (area.isEmpty()) {
                break;
            }
            area.offset(crop.left, crop.top);
            area.intersect(crop);
            for (int lr = 0; lr < 2; ++lr) {
                long[] oneStat = CodecUtils.getRawStats(image, area);
                if (VERBOSE) Log.v(TAG, "area=" + area + ", layer=" + layer + ", last="
                                    + lastLayer + ": " + Arrays.toString(oneStat));
                for (int i = 0; i < oneStat.length; i++) {
                    rawStats[layer][i] += oneStat[i];
                    if (lastLayerValid) {
                        rawStats[lastLayer][i] -= oneStat[i];
                    }
                }
                if (VERBOSE && lastLayerValid) {
                    Log.v(TAG, "layer-" + lastLayer + ": " + Arrays.toString(rawStats[lastLayer]));
                    Log.v(TAG, Arrays.toString(CodecUtils.Raw2YUVStats(rawStats[lastLayer])));
                }
                // switch to the opposite side
                layer ^= 2;      // NUM_SIDES / 2
                lastLayer ^= 2;  // NUM_SIDES / 2
                area.offset(crop.centerX() - area.left, 2 * (crop.centerY() - area.centerY()));
            }

            lastLayer = layer;
            lastLayerValid = true;
            layer = (layer + 1) % NUM_SIDES;
        }

        for (layer = 0; layer < NUM_SIDES; ++layer) {
            float[] stats = CodecUtils.Raw2YUVStats(rawStats[layer]);
            if (DEBUG) Log.d(TAG, "layer-" + layer + ": " + Arrays.toString(stats));
            if (VERBOSE) Log.v(TAG, Arrays.toString(rawStats[layer]));

            // check layer uniformity
            for (int i = 0; i < 3; i++) {
                assertTrue("color of layer-" + layer + " is not uniform: "
                        + Arrays.toString(stats),
                        stats[3 + i] < COLOR_STDEV_ALLOWANCE);
            }

            // check layer color
            for (int i = 0; i < 3; i++) {
                assertTrue("color of layer-" + layer + " mismatches target "
                        + Arrays.toString(colors[layer]) + " vs "
                        + Arrays.toString(Arrays.copyOf(stats, 3)),
                        Math.abs(stats[i] - colors[layer][i]) < COLOR_DELTA_ALLOWANCE);
            }
        }
    }

    private static void validateYuvData(byte[] yuvData, int width, int height, int format,
            long ts) {

        assertTrue("YUV format must be one of the YUV_420_888, NV21, YV12 or YCBCR_P010",
                format == ImageFormat.YUV_420_888 ||
                format == ImageFormat.NV21 ||
                format == ImageFormat.YV12 ||
                format == ImageFormat.YCBCR_P010);

        if (VERBOSE) Log.v(TAG, "Validating YUV data");
        int expectedSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        assertEquals("Yuv data doesn't match", expectedSize, yuvData.length);
    }

    private static void checkYuvFormat(int format) {
        if ((format != ImageFormat.YUV_420_888) &&
                (format != ImageFormat.NV21) &&
                (format != ImageFormat.YV12) &&
                (format != ImageFormat.YCBCR_P010)) {
            fail("Wrong formats: " + format);
        }
    }
    /**
     * <p>Check android image format validity for an image, only support below formats:</p>
     *
     * <p>Valid formats are YUV_420_888/NV21/YV12/P010 for video decoder</p>
     */
    private static void checkAndroidImageFormat(Image image) {
        int format = image.getFormat();
        Plane[] planes = image.getPlanes();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
            case ImageFormat.YCBCR_P010:
                assertEquals("YUV420 format Images should have 3 planes", 3, planes.length);
                break;
            default:
                fail("Unsupported Image Format: " + format);
        }
    }

    /**
     * Get a byte array image data from an Image object.
     * <p>
     * Read data from all planes of an Image into a contiguous unpadded,
     * unpacked 1-D linear byte array, such that it can be write into disk, or
     * accessed by software conveniently. It supports YUV_420_888/NV21/YV12/P010
     * input Image format.
     * </p>
     * <p>
     * For YUV_420_888/NV21/YV12/Y8/Y16, it returns a byte array that contains
     * the Y plane data first, followed by U(Cb), V(Cr) planes if there is any
     * (xstride = width, ystride = height for chroma and luma components).
     * </p>
     */
    private static byte[] getDataFromImage(Image image) {
        assertNotNull("Invalid image:", image);
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        int rowStride, pixelStride;
        byte[] data = null;

        // Read image data
        Plane[] planes = image.getPlanes();
        assertTrue("Fail to get image planes", planes != null && planes.length > 0);

        // Check image validity
        checkAndroidImageFormat(image);

        ByteBuffer buffer = null;

        int offset = 0;
        data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if(VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        for (int i = 0; i < planes.length; i++) {
            int shift = (i == 0) ? 0 : 1;
            buffer = planes[i].getBuffer();
            assertNotNull("Fail to get bytebuffer from plane", buffer);
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            assertTrue("pixel stride " + pixelStride + " is invalid", pixelStride > 0);
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
            }
            // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
            int w = crop.width() >> shift;
            int h = crop.height() >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            assertTrue("rowStride " + rowStride + " should be >= width " + w , rowStride >= w);
            for (int row = 0; row < h; row++) {
                // ImageFormat.getBitsPerPixel() returns total bits per pixel, which is 12 for
                // YUV 4:2:0 8-bit, whereas bytesPerPixel is for Y plane only
                int bytesPerPixel = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);
                int length;
                if (pixelStride == bytesPerPixel) {
                    // Special case: optimized read of the entire row
                    length = w * bytesPerPixel;
                    buffer.get(data, offset, length);
                    offset += length;
                } else {
                    // Generic case: should work for any pixelStride but slower.
                    // Use intermediate buffer to avoid read byte-by-byte from
                    // DirectByteBuffer, which is very bad for performance
                    length = (w - 1) * pixelStride + bytesPerPixel;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        for (int bytePos = 0; bytePos < bytesPerPixel; ++bytePos) {
                            data[offset++] = rowData[col * pixelStride + bytePos];
                        }
                    }
                }
                // Advance buffer the remainder of the row stride
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    private static void dumpFile(String fileName, byte[] data) {
        assertNotNull("fileName must not be null", fileName);
        assertNotNull("data must not be null", data);

        FileOutputStream outStream;
        try {
            Log.v(TAG, "output will be saved as " + fileName);
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create debug output file " + fileName, ioe);
        }

        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }

    private void createImageReader(
            int width, int height, int format, int maxNumImages,
            ImageReader.OnImageAvailableListener listener)  {
        closeImageReader();

        mReader = ImageReader.newInstance(width, height, format, maxNumImages);
        mReaderSurface = mReader.getSurface();
        mReader.setOnImageAvailableListener(listener, mHandler);
        if (VERBOSE) {
            Log.v(TAG, String.format("Created ImageReader size (%dx%d), format %d", width, height,
                    format));
        }
    }

    /**
     * Close the pending images then close current active {@link ImageReader} object.
     */
    private void closeImageReader() {
        if (mReader != null) {
            try {
                // Close all possible pending images first.
                Image image = mReader.acquireLatestImage();
                if (image != null) {
                    image.close();
                }
            } finally {
                mReader.close();
                mReader = null;
            }
        }
    }
}
