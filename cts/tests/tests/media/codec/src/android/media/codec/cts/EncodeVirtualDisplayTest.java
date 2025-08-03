/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Presentation;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.cts.OutputSurface;
import android.media.cts.TestArgs;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests connecting a virtual display to the input of a MediaCodec encoder.
 * <p>
 * Other test cases exercise these independently in more depth.  The goal here is to make sure
 * that virtual displays and MediaCodec can be used together.
 * <p>
 * We can't control frame-by-frame what appears on the virtual display, because we're
 * just throwing a Presentation and a View at it.  Further, it's possible that frames
 * will be dropped if they arrive faster than they are consumed, so any given frame
 * may not appear at all.  We can't wait for a series of actions to complete by watching
 * the output, because the frames are going directly to the encoder, and the encoder may
 * collect a number of frames before producing output.
 * <p>
 * The test puts up a series of colored screens, expecting to see all of them, and in order.
 * Any black screens that appear before or after are ignored.
 */
@Presubmit
@SmallTest
@RequiresDevice
@RunWith(Parameterized.class)
public class EncodeVirtualDisplayTest {
    private static final String TAG = "EncodeVirtualTest";
    private static final boolean VERBOSE = false;           // lots of logging
    private static final boolean DEBUG_SAVE_FILE = false;   // save copy of encoded movie
    private static final String DEBUG_FILE_NAME_BASE = "/sdcard/test.";

    // Virtual display characteristics.  Scaled down from full display size because not all
    // devices can encode at the resolution of their own display.
    private static final String NAME = TAG;
    private static final int DENSITY = DisplayMetrics.DENSITY_HIGH;
    private static final int UI_TIMEOUT_MS = 2000;
    private static final int UI_RENDER_PAUSE_MS = 400;

    // 100 days between I-frames
    private static final int IFRAME_INTERVAL = 60 * 60 * 24 * 100;

    // Colors to test (RGB).  These must convert cleanly to and from BT.601 YUV.
    private static final int TEST_COLORS[] = {
        makeColor(0, 0, 0),             // YCbCr 16,128,128
        makeColor(10, 100, 200),        // YCbCr 89,186,82
        makeColor(100, 200, 10),        // YCbCr 144,60,98
        makeColor(200, 10, 100),        // YCbCr 203,10,103
        makeColor(10, 200, 100),        // YCbCr 130,113,52
        makeColor(100, 10, 200),        // YCbCr 67,199,154
        makeColor(200, 100, 10),        // YCbCr 119,74,179
    };

    private final ByteBuffer mPixelBuf = ByteBuffer.allocateDirect(4);
    private Handler mUiHandler;                             // Handler on main Looper
    private DisplayManager mDisplayManager;
    volatile boolean mInputDone;
    private Context mContext;
    private String mEncoderName;
    private String mMediaType;
    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mFrameRate;
    private MediaFormat mFormat;

    /* TEST_COLORS static initialization; need ARGB for ColorDrawable */
    private static int makeColor(int red, int green, int blue) {
        return 0xff << 24 | (red & 0xff) << 16 | (green & 0xff) << 8 | (blue & 0xff);
    }

    private static boolean sIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private static boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    public EncodeVirtualDisplayTest(String encodername, String mediaType, int width, int height,
            int bitrate, int framerate, @SuppressWarnings("unused") String level) {
        mEncoderName = encodername;
        mMediaType = mediaType;
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mFrameRate = framerate;
    }

    @Before
    public void setUp() throws Exception {
        mUiHandler = new Handler(Looper.getMainLooper());
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDisplayManager = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
    }

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String)arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] componentNames = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String name : componentNames) {
                if (TestArgs.shouldSkipCodec(name)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                // Add codec name as first argument and then copy all other arguments passed
                testArgs[0] = name;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{6}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // mediaType, width, height,  bitrate, framerate, level
                {MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 14000000, 30, "AVCLevel31"},
                {MediaFormat.MIMETYPE_VIDEO_AVC,  720, 480, 10000000, 30, "AVCLevel3"},
                {MediaFormat.MIMETYPE_VIDEO_AVC,  720, 480,  4000000, 15, "AVCLevel22"},
                {MediaFormat.MIMETYPE_VIDEO_AVC,  352, 576,  4000000, 25, "AVCLevel21"},
        });
        return prepareParamList(exhaustiveArgsList);
    }

    /**
     * Basic test.
     *
     * @throws Exception
     */
    @ApiTest(apis = {"AMediaCodec_createInputSurface",
            "android.hardware.display.DisplayManager#createVirtualDisplay",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface",
            "android.opengl.GLES20#glReadPixels",
            "android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @Test
    public void testEncodeVirtualDisplay() throws Throwable {
        if (!MediaUtils.check(sIsAtLeastR, "test needs Android 11")) return;
        // Encoded video resolution matches virtual display.
        mFormat = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        mFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        mFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_PAL);
        mFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        assumeTrue(mEncoderName + " doesn't support format " + mFormat,
                MediaUtils.supports(mEncoderName, mFormat));

        EncodeVirtualWrapper.runTest(this);
    }

    /**
     * Wraps encodeVirtualTest, running it in a new thread.  Required because of the way
     * SurfaceTexture.OnFrameAvailableListener works when the current thread has a Looper
     * configured.
     */
    private static class EncodeVirtualWrapper implements Runnable {
        private Throwable mThrowable;
        private EncodeVirtualDisplayTest mTest;

        private EncodeVirtualWrapper(EncodeVirtualDisplayTest test) {
            mTest = test;
        }

        @Override
        public void run() {
            try {
                mTest.encodeVirtualDisplayTest();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }

        /** Entry point. */
        public static void runTest(EncodeVirtualDisplayTest obj) throws Throwable {
            EncodeVirtualWrapper wrapper = new EncodeVirtualWrapper(obj);
            Thread th = new Thread(wrapper, "codec test");
            th.start();
            th.join();
            if (wrapper.mThrowable != null) {
                throw wrapper.mThrowable;
            }
        }
    }

    /**
     * Prepares the encoder, decoder, and virtual display.
     */
    private void encodeVirtualDisplayTest() throws IOException {
        MediaCodec encoder = null;
        MediaCodec decoder = null;
        OutputSurface outputSurface = null;
        VirtualDisplay virtualDisplay = null;
        ColorSlideShow slideShow = null;

        try {
            encoder = MediaCodec.createByCodecName(mEncoderName);
            encoder.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            encoder.start();

            // Create a virtual display that will output to our encoder.
            virtualDisplay = mDisplayManager.createVirtualDisplay(NAME,
                    mWidth, mHeight, DENSITY, inputSurface, 0);

            // We also need a decoder to check the output of the encoder.
            decoder = MediaCodec.createDecoderByType(mMediaType);
            MediaFormat decoderFormat = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
            outputSurface = new OutputSurface(mWidth, mHeight);
            decoder.configure(decoderFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            // Run the color slide show on a separate thread.
            mInputDone = false;
            slideShow = new ColorSlideShow(virtualDisplay.getDisplay());
            slideShow.start();

            // Record everything we can and check the results.
            doTestEncodeVirtual(encoder, decoder, outputSurface);

        } finally {
            if (VERBOSE) Log.d(TAG, "releasing codecs, surfaces, and virtual display");
            if (slideShow != null) {
                try {
                    slideShow.join();
                } catch (InterruptedException ignore) {}
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
    }

    /**
     * Drives the encoder and decoder.
     */
    private void doTestEncodeVirtual(MediaCodec encoder, MediaCodec decoder,
            OutputSurface outputSurface) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputEosSignaled = false;
        int lastIndex = -1;
        int goodFrames = 0;
        int debugFrameCount = 0;

        // Save a copy to disk.  Useful for debugging the test.
        MediaMuxer muxer = null;
        int trackIndex = -1;
        if (DEBUG_SAVE_FILE) {
            String fileName = DEBUG_FILE_NAME_BASE + mWidth + "x" + mHeight + ".mp4";
            try {
                muxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                Log.d(TAG, "encoded output will be saved as " + fileName);
            } catch (IOException ioe) {
                Log.w(TAG, "Unable to create debug output file " + fileName);
                throw new RuntimeException(ioe);
            }
        }

        // Loop until the output side is done.
        boolean encoderDone = false;
        boolean outputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "loop");

            if (!inputEosSignaled && mInputDone) {
                if (VERBOSE) Log.d(TAG, "signaling input EOS");
                encoder.signalEndOfInputStream();
                inputEosSignaled = true;
            }

            boolean decoderOutputAvailable = true;
            boolean encoderOutputAvailable = !encoderDone;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the decoder.  It's important to
                // do this before we try to stuff any more data in.
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    decoderOutputAvailable = false;
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed (but we don't care)");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // this happens before the first frame is returned
                    MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " +
                            decoderOutputFormat);
                } else if (decoderStatus < 0) {
                    fail("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else {  // decoderStatus >= 0
                    if (VERBOSE) Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }

                    // The ByteBuffers are null references, but we still get a nonzero size for
                    // the decoded data.
                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.  If we don't
                    // wait, we risk dropping frames.
                    outputSurface.makeCurrent();
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d(TAG, "awaiting frame " + (lastIndex+1));
                        outputSurface.awaitNewImage();
                        outputSurface.drawImage();
                        int foundIndex = checkSurfaceFrame();
                        if (foundIndex == lastIndex + 1) {
                            // found the next one in the series
                            lastIndex = foundIndex;
                            goodFrames++;
                        } else if (foundIndex == lastIndex) {
                            // Sometimes we see the same color two frames in a row.
                            if (VERBOSE) Log.d(TAG, "Got another " + lastIndex);
                        } else if (foundIndex > 0) {
                            // Looks like we missed a color frame.  It's possible something
                            // stalled and we dropped a frame.  Skip forward to see if we
                            // can catch the rest.
                            if (foundIndex < lastIndex) {
                                Log.w(TAG, "Ignoring backward skip from " +
                                    lastIndex + " to " + foundIndex);
                            } else {
                                Log.w(TAG, "Frame skipped, advancing lastIndex from " +
                                        lastIndex + " to " + foundIndex);
                                goodFrames++;
                                lastIndex = foundIndex;
                            }
                        }
                    }
                }
                if (decoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }

                // Decoder is drained, check to see if we've got a new buffer of output from
                // the encoder.
                if (!encoderDone) {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from encoder available");
                        encoderOutputAvailable = false;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        encoderOutputBuffers = encoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // received before first buffer
                        MediaFormat newFormat = encoder.getOutputFormat();
                        if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                        if (muxer != null && trackIndex == -1) {
                            trackIndex = muxer.addTrack(newFormat);
                            muxer.start();
                        }
                    } else if (encoderStatus < 0) {
                        fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else { // encoderStatus >= 0
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            fail("encoderOutputBuffer " + encoderStatus + " was null");
                        }

                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        if (muxer != null) {
                            muxer.writeSampleData(trackIndex, encodedData, info);
                            debugFrameCount++;
                        }

                        // Get a decoder input buffer, blocking until it's available.  We just
                        // drained the decoder output, so we expect there to be a free input
                        // buffer now or in the near future (i.e. this should never deadlock
                        // if the codec is meeting requirements).
                        //
                        // The first buffer of data we get will have the BUFFER_FLAG_CODEC_CONFIG
                        // flag set; the decoder will see this and finish configuring itself.
                        int inputBufIndex = decoder.dequeueInputBuffer(-1);
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputBuf.put(encodedData);
                        decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                info.presentationTimeUs, info.flags);

                        // If everything from the encoder has been passed to the decoder, we
                        // can stop polling the encoder output.  (This just an optimization.)
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true;
                            encoderOutputAvailable = false;
                        }
                        if (VERBOSE) Log.d(TAG, "passed " + info.size + " bytes to decoder"
                                + (encoderDone ? " (EOS)" : ""));

                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                }
            }
        }

        if (muxer != null) {
            muxer.stop();
            muxer.release();
            if (VERBOSE) Log.d(TAG, "Wrote " + debugFrameCount + " frames");
        }

        if (goodFrames != TEST_COLORS.length) {
            fail("Found " + goodFrames + " of " + TEST_COLORS.length + " expected frames");
        }
    }

    /**
     * Checks the contents of the current EGL surface to see if it matches expectations.
     * <p>
     * The surface may be black or one of the colors we've drawn.  We have sufficiently little
     * control over the rendering process that we don't know how many (if any) black frames
     * will appear between each color frame.
     * <p>
     * @return the color index, or -2 for black
     * @throw RuntimeException if the color isn't recognized (probably because the RGB<->YUV
     *     conversion introduced too much variance)
     */
    private int checkSurfaceFrame() {
        boolean frameFailed = false;

        // Read a pixel from the center of the surface.  Might want to read from multiple points
        // and average them together.
        int x = mWidth / 2;
        int y = mHeight / 2;
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);
        int r = mPixelBuf.get(0) & 0xff;
        int g = mPixelBuf.get(1) & 0xff;
        int b = mPixelBuf.get(2) & 0xff;
        if (VERBOSE) Log.d(TAG, "GOT: r=" + r + " g=" + g + " b=" + b);

        // Walk through the color list and try to find a match.  These may have gone through
        // RGB<->YCbCr conversions, so don't expect exact matches.
        for (int i = 0; i < TEST_COLORS.length; i++) {
            int testRed = (TEST_COLORS[i] >> 16) & 0xff;
            int testGreen = (TEST_COLORS[i] >> 8) & 0xff;
            int testBlue = TEST_COLORS[i] & 0xff;
            if (approxEquals(testRed, r) && approxEquals(testGreen, g) &&
                    approxEquals(testBlue, b)) {
                if (VERBOSE) Log.d(TAG, "Matched color " + i + ": r=" + r + " g=" + g + " b=" + b);
                return i;
            }
        }

        throw new RuntimeException("No match for color r=" + r + " g=" + g + " b=" + b);
    }

    /**
     * Determines if two color values are approximately equal.
     */
    private static boolean approxEquals(int expected, int actual) {
        final int MAX_DELTA = 7;
        return Math.abs(expected - actual) <= MAX_DELTA;
    }

    /**
     * Creates a series of colorful Presentations on the specified Display.
     */
    private class ColorSlideShow extends Thread {
        private Display mDisplay;

        public ColorSlideShow(Display display) {
            mDisplay = display;
        }

        @Override
        public void run() {
            for (int testColor : TEST_COLORS) {
                showPresentation(testColor);
            }

            if (VERBOSE) Log.d(TAG, "slide show finished");
            mInputDone = true;
        }

        private void showPresentation(final int color) {
            final TestPresentation[] presentation = new TestPresentation[1];
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                runOnUiThread(() -> {
                    // Want to create presentation on UI thread so it finds the right Looper
                    // when setting up the Dialog.
                    presentation[0] = new TestPresentation(mContext, mDisplay, color);
                    if (VERBOSE) Log.d(TAG, "showing color=0x" + Integer.toHexString(color));
                    presentation[0].show();
                    latch.countDown();
                });

                // Give the presentation an opportunity to render.  We don't have a way to
                // monitor the output, so we just sleep for a bit.
                try {
                    // wait for the UI thread execution to finish
                    latch.await(5, TimeUnit.SECONDS);
                    Thread.sleep(UI_RENDER_PAUSE_MS);
                } catch (InterruptedException ignore) {
                }
            } finally {
                if (presentation[0] != null) {
                    presentation[0].dismiss();
                    presentation[0] = null;
                }
            }
        }
    }

    /**
     * Executes a runnable on the UI thread, and waits for it to complete.
     */
    private void runOnUiThread(Runnable runnable) {
        Runnable waiter = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    notifyAll();
                }
            }
        };
        synchronized (waiter) {
            mUiHandler.post(runnable);
            mUiHandler.post(waiter);
            try {
                waiter.wait(UI_TIMEOUT_MS);
            } catch (InterruptedException ex) {
            }
        }
    }

    /**
     * Presentation we can show on a virtual display.  The view is set to a single color value.
     */
    private class TestPresentation extends Presentation {
        private final int mColor;

        public TestPresentation(Context context, Display display, int color) {
            super(context, display);
            mColor = color;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setTitle("Encode Virtual Test");

            // Create a solid color image to use as the content of the presentation.
            ImageView view = new ImageView(mContext);
            view.setImageDrawable(new ColorDrawable(mColor));
            view.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setContentView(view);
        }
    }
}
