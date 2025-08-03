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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.VP9Profile2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.cts.InputSurface;
import android.media.cts.OutputSurface;
import android.media.cts.TestArgs;
import android.media.cts.TestUtils;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;
import android.platform.test.annotations.PlatinumTest;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This test has three steps:
 * <ol>
 *   <li>Generate a video test stream.
 *   <li>Decode the video from the stream, rendering frames into a SurfaceTexture.
 *       Render the texture onto a Surface that feeds a video encoder, modifying
 *       the output with a fragment shader.
 *   <li>Decode the second video and compare it to the expected result.
 * </ol><p>
 * The second step is a typical scenario for video editing.  We could do all this in one
 * step, feeding data through multiple stages of MediaCodec, but at some point we're
 * no longer exercising the code in the way we expect it to be used (and the code
 * gets a bit unwieldy).
 */
@PlatinumTest(focusArea = "media")
@RunWith(Parameterized.class)
public class DecodeEditEncodeTest {
    private static final String TAG = "DecodeEditEncode";
    private static final boolean WORK_AROUND_BUGS = false;  // avoid fatal codec bugs
    private static final boolean VERBOSE = false;           // lots of logging
    private static final boolean DEBUG_SAVE_FILE = false;   // save copy of encoded movie
    //TODO(b/248315681) Remove codenameEquals() check once devices return correct version for U
    private static final boolean IS_AFTER_T = ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)
            || ApiLevelUtil.codenameEquals("UpsideDownCake");

    // parameters for the encoder
    private static final int FRAME_RATE = 15;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final String KEY_ALLOW_FRAME_DROP = "allow-frame-drop";

    // movie length, in frames
    private static final int NUM_FRAMES = FRAME_RATE * 3;   // three seconds of video

    // since encoders are lossy, we treat the first N frames differently, with a different
    // tolerance, than the remainder of the clip.  The # of such frames is
    // INITIAL_TOLERANCE_FRAME_LIMIT, the tolerance within that window is defined by
    // INITIAL_TOLERANCE and the tolerance afterwards is defined by TOLERANCE
    private final int INITIAL_TOLERANCE_FRAME_LIMIT = FRAME_RATE * 2;

    // allowed error between input and output
    private static final int TOLERANCE = 8;

    // allowed error between input and output for initial INITIAL_TOLERANCE_FRAME_LIMIT frames
    private static final int INITIAL_TOLERANCE = 10;

    private static final int TEST_R0 = 0;                   // dull green background
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // pink; BT.601 YUV {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;

    // Replaces TextureRender.FRAGMENT_SHADER during edit; swaps green and blue channels.
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord).rbga;\n" +
            "}\n";

    // component names
    private final String mEncoderName;
    private final String mDecoderName;
    // mime
    private final String mMediaType;
    // size of a frame, in pixels
    private final int mWidth;
    private final int mHeight;
    // bit rate, in bits per second
    private final int mBitRate;
    private final boolean mUseHighBitDepth;

    // largest color component delta seen (i.e. actual vs. expected)
    private int mLargestColorDelta;

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String)arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] encoderNames = MediaUtils.getEncoderNamesForMime(mediaType);
            String[] decoderNames = MediaUtils.getDecoderNamesForMime(mediaType);
            // First pair of decoder and encoder that supports given mediaType is chosen
            outerLoop:
            for (String decoder : decoderNames) {
                if (TestArgs.shouldSkipCodec(decoder)) {
                    continue;
                }

                for (String encoder : encoderNames) {
                    if (TestArgs.shouldSkipCodec(encoder)) {
                        continue;
                    }
                    Object[] testArgs = new Object[argLength + 2];
                    // Add encoder name and decoder name as first two arguments and then
                    // copy arguments passed
                    testArgs[0] = encoder;
                    testArgs[1] = decoder;
                    System.arraycopy(arg, 0, testArgs, 2, argLength);
                    argsList.add(testArgs);
                    // Only one combination of encoder and decoder is tested
                    break outerLoop;
                }
            }
        }
        return argsList;
    }

    private static boolean hasSupportForColorFormat(String name, String mediaType,
            int colorFormat, boolean isEncoder) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (isEncoder != codecInfo.isEncoder()) {
                continue;
            }
            if (!name.equals(codecInfo.getName())) {
                continue;
            }
            MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(mediaType);
            for (int c : cap.colorFormats) {
                if (c == colorFormat) {
                    return true;
                }
            }
        }
        return false;
    }

    @Before
    public void shouldSkip() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (mUseHighBitDepth) {
            assumeTrue(mEncoderName + " doesn't support RGBA1010102",
                    hasSupportForColorFormat(mEncoderName, mMediaType,
                            COLOR_Format32bitABGR2101010, /* isEncoder */ true));

            switch (mMediaType) {
                case MediaFormat.MIMETYPE_VIDEO_AVC:
                    format.setInteger(MediaFormat.KEY_PROFILE, AVCProfileHigh10);
                    break;
                case MediaFormat.MIMETYPE_VIDEO_HEVC:
                    format.setInteger(MediaFormat.KEY_PROFILE, HEVCProfileMain10);
                    break;
                case MediaFormat.MIMETYPE_VIDEO_VP9:
                    format.setInteger(MediaFormat.KEY_PROFILE, VP9Profile2);
                    break;
                case MediaFormat.MIMETYPE_VIDEO_AV1:
                    format.setInteger(MediaFormat.KEY_PROFILE, AV1ProfileMain10);
                    break;
                default:
                    fail("MediaType " + mMediaType + " is not supported for 10-bit testing.");
                    break;
            }
        }
        assumeTrue(MediaUtils.supports(mEncoderName, format));
        assumeTrue(MediaUtils.supports(mDecoderName, format));
        // Few cuttlefish specific color conversion issues were fixed after Android T.
        if (MediaUtils.onCuttlefish()) {
            assumeTrue("Color conversion related tests are not valid on cuttlefish releases "
                    + "through android T for format: " + format, IS_AFTER_T);
        }
        // Pre Android U, this test only checked the 1st codec (which is usually a hardware codec)
        // and software codecs exercised a problem in the underlying graphis code.
        // So we will only run this for CTS mode or if we're on versions after Android T
        // (where the graphics code is fixed)
        if (TestUtils.isMtsMode()) {
            assumeTrue("Color conversion related tests are skipped in MTS on releases "
                    + "through android T for format: " + format, IS_AFTER_T);
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}_{4}_{5}")
    public static Collection<Object[]> input() {
        final List<Object[]> baseArgsList = Arrays.asList(new Object[][]{
                // width, height, bitrate
                {176, 144, 1000000},
                {320, 240, 2000000},
                {1280, 720, 6000000}
        });
        final String[] mediaTypes = {MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AV1};
        final boolean[] useHighBitDepthModes = {false, true};
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        for (boolean useHighBitDepth : useHighBitDepthModes) {
            for (String mediaType : mediaTypes) {
                for (Object[] obj : baseArgsList) {
                    if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP8) && useHighBitDepth) {
                        continue;
                    }
                    exhaustiveArgsList.add(
                            new Object[]{mediaType, obj[0], obj[1], obj[2], useHighBitDepth});
                }
            }
        }
        return prepareParamList(exhaustiveArgsList);
    }

    public DecodeEditEncodeTest(String encoder, String decoder, String mimeType, int width,
            int height, int bitRate, boolean useHighBitDepth) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mEncoderName = encoder;
        mDecoderName = decoder;
        mMediaType = mimeType;
        mWidth = width;
        mHeight = height;
        mBitRate = bitRate;
        mUseHighBitDepth = useHighBitDepth;
    }

    @ApiTest(apis = {"android.opengl.GLES20#GL_FRAGMENT_SHADER",
            "android.opengl.GLES20#glReadPixels",
            "android.opengl.GLES30#glReadPixels",
            "android.media.format.MediaFormat#KEY_ALLOW_FRAME_DROP",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_Format32bitABGR2101010",
            "android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @Test
    public void testVideoEdit() throws Throwable {
        VideoEditWrapper.runTest(this);
    }

    /**
     * Wraps testEditVideo, running it in a new thread.  Required because of the way
     * SurfaceTexture.OnFrameAvailableListener works when the current thread has a Looper
     * configured.
     */
    private static class VideoEditWrapper implements Runnable {
        private Throwable mThrowable;
        private DecodeEditEncodeTest mTest;

        private VideoEditWrapper(DecodeEditEncodeTest test) {
            mTest = test;
        }

        @Override
        public void run() {
            try {
                mTest.videoEditTest();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }

        /** Entry point. */
        public static void runTest(DecodeEditEncodeTest obj) throws Throwable {
            VideoEditWrapper wrapper = new VideoEditWrapper(obj);
            Thread th = new Thread(wrapper, "codec test");
            th.start();
            th.join();
            if (wrapper.mThrowable != null) {
                throw wrapper.mThrowable;
            }
        }
    }

    /**
     * Tests editing of a video file with GL.
     */
    private void videoEditTest()
            throws IOException {
        VideoChunks sourceChunks = new VideoChunks();

        generateVideoFile(sourceChunks);

        if (DEBUG_SAVE_FILE) {
            // Save a copy to a file.  We call it ".mp4", but it's actually just an elementary
            // stream, so not all video players will know what to do with it.
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            String dirName = context.getFilesDir().getAbsolutePath();
            String fileName = "vedit1_" + mWidth + "x" + mHeight + ".mp4";
            sourceChunks.saveToFile(new File(dirName, fileName));
        }

        VideoChunks destChunks = editVideoFile(sourceChunks);

        if (DEBUG_SAVE_FILE) {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            String dirName = context.getFilesDir().getAbsolutePath();
            String fileName = "vedit2_" + mWidth + "x" + mHeight + ".mp4";
            destChunks.saveToFile(new File(dirName, fileName));
        }

        checkVideoFile(destChunks);
    }

    /**
     * Generates a test video file, saving it as VideoChunks.  We generate frames with GL to
     * avoid having to deal with multiple YUV formats.
     */
    private void generateVideoFile(VideoChunks output)
            throws IOException {
        if (VERBOSE) Log.d(TAG, "generateVideoFile " + mWidth + "x" + mHeight);
        MediaCodec encoder = null;
        InputSurface inputSurface = null;

        try {
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);

            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            if (mUseHighBitDepth) {
                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
                format.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                        MediaFormat.COLOR_STANDARD_BT2020);
                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                        MediaFormat.COLOR_TRANSFER_ST2084);
            } else {
                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
                format.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                        MediaFormat.COLOR_STANDARD_BT601_PAL);
                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            }
            if (VERBOSE) Log.d(TAG, "format: " + format);
            output.setMediaFormat(format);

            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties.
            encoder = MediaCodec.createByCodecName(mEncoderName);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface(), mUseHighBitDepth);
            inputSurface.makeCurrent();
            encoder.start();

            generateVideoData(encoder, inputSurface, output);
        } finally {
            if (encoder != null) {
                if (VERBOSE) Log.d(TAG, "releasing encoder");
                encoder.stop();
                encoder.release();
                if (VERBOSE) Log.d(TAG, "released encoder");
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
        }
    }

    /**
     * Generates video frames, feeds them into the encoder, and writes the output to the
     * VideoChunks instance.
     */
    private void generateVideoData(MediaCodec encoder, InputSurface inputSurface,
            VideoChunks output) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int generateIndex = 0;
        int outputCount = 0;

        // Loop until the output side is done.
        boolean inputDone = false;
        boolean outputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "gen loop");

            // If we're not done submitting frames, generate a new one and submit it.  The
            // eglSwapBuffers call will block if the input is full.
            if (!inputDone) {
                if (generateIndex == NUM_FRAMES) {
                    // Send an empty frame with the end-of-stream flag set.
                    if (VERBOSE) Log.d(TAG, "signaling input EOS");
                    if (WORK_AROUND_BUGS) {
                        // Might drop a frame, but at least we won't crash mediaserver.
                        try { Thread.sleep(500); } catch (InterruptedException ie) {}
                        outputDone = true;
                    } else {
                        encoder.signalEndOfInputStream();
                    }
                    inputDone = true;
                } else {
                    generateSurfaceFrame(generateIndex);
                    inputSurface.setPresentationTime(computePresentationTime(generateIndex) * 1000);
                    if (VERBOSE) Log.d(TAG, "inputSurface swapBuffers");
                    inputSurface.swapBuffers();
                }
                generateIndex++;
            }

            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // If we do find output, drain it all before supplying more input.
            while (true) {
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    break;      // out of while
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // expected on API 18+
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        fail("encoderOutputBuffer " + encoderStatus + " was null");
                    }

                    if (info.size != 0) {
                        // Adjust the ByteBuffer values to match BufferInfo.
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        output.addChunk(encodedData, info.flags, info.presentationTimeUs);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            outputCount++;
                        }
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                        break;      // out of while
                    }
                }
            }
        }

        assertEquals("Frame count", NUM_FRAMES, outputCount);
    }

    /**
     * Generates a frame of data using GL commands.
     * <p>
     * We have an 8-frame animation sequence that wraps around.  It looks like this:
     * <pre>
     *   0 1 2 3
     *   7 6 5 4
     * </pre>
     * We draw one of the eight rectangles and leave the rest set to the zero-fill color.     */
    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;

        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mWidth / 4);
            startY = mHeight / 2;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = 0;
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mWidth / 4, mHeight / 2);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Edits a video file, saving the contents to a new file.  This involves decoding and
     * re-encoding, not to mention conversions between YUV and RGB, and so may be lossy.
     * <p>
     * If we recognize the decoded format we can do this in Java code using the ByteBuffer[]
     * output, but it's not practical to support all OEM formats.  By using a SurfaceTexture
     * for output and a Surface for input, we can avoid issues with obscure formats and can
     * use a fragment shader to do transformations.
     */
    private VideoChunks editVideoFile(VideoChunks inputData)
            throws IOException {
        if (VERBOSE) Log.d(TAG, "editVideoFile " + mWidth + "x" + mHeight);
        VideoChunks outputData = new VideoChunks();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;

        try {
            MediaFormat inputFormat = inputData.getMediaFormat();

            // Create an encoder format that matches the input format.  (Might be able to just
            // re-use the format used to generate the video, since we want it to be the same.)
            MediaFormat outputFormat = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                    inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
                    inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                    inputFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));
            if (mUseHighBitDepth) {
                outputFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
                outputFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                        MediaFormat.COLOR_STANDARD_BT2020);
                outputFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                        MediaFormat.COLOR_TRANSFER_ST2084);
            } else {
                outputFormat.setInteger(MediaFormat.KEY_COLOR_RANGE,
                        MediaFormat.COLOR_RANGE_LIMITED);
                outputFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                        MediaFormat.COLOR_STANDARD_BT601_PAL);
                outputFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            }
            outputData.setMediaFormat(outputFormat);

            encoder = MediaCodec.createByCodecName(mEncoderName);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface(), mUseHighBitDepth);
            inputSurface.makeCurrent();
            encoder.start();

            // OutputSurface uses the EGL context created by InputSurface.
            decoder = MediaCodec.createByCodecName(mDecoderName);
            outputSurface = new OutputSurface();
            outputSurface.changeFragmentShader(FRAGMENT_SHADER);
            // do not allow frame drops
            inputFormat.setInteger(KEY_ALLOW_FRAME_DROP, 0);

            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            // verify that we are not dropping frames
            inputFormat = decoder.getInputFormat();
            assertEquals("Could not prevent frame dropping",
                         0, inputFormat.getInteger(KEY_ALLOW_FRAME_DROP));

            editVideoData(inputData, decoder, outputSurface, inputSurface, encoder, outputData);
        } finally {
            if (VERBOSE) Log.d(TAG, "shutting down encoder, decoder");
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
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

        return outputData;
    }

    /**
     * Edits a stream of video data.
     */
    private void editVideoData(VideoChunks inputData, MediaCodec decoder,
            OutputSurface outputSurface, InputSurface inputSurface, MediaCodec encoder,
            VideoChunks outputData) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "edit loop");

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (inputChunk == inputData.getNumChunks()) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputData.getChunkData(inputChunk, inputBuf);
                        int flags = inputData.getChunkFlags(inputChunk);
                        long time = inputData.getChunkTime(inputChunk);
                        decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                time, flags);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    inputBuf.position() + " flags=" + flags);
                        }
                        inputChunk++;
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the encoder.  It's important to
                // do this before we try to stuff any more data in.
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        fail("encoderOutputBuffer " + encoderStatus + " was null");
                    }

                    // Write the data to the output "file".
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        outputData.addChunk(encodedData, info.flags, info.presentationTimeUs);
                        outputCount++;

                        if (VERBOSE) Log.d(TAG, "encoder output " + info.size + " bytes");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }

                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) Log.d(TAG, "no output from decoder available");
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //decoderOutputBuffers = decoder.getOutputBuffers();
                        if (VERBOSE) Log.d(TAG, "decoder output buffers changed (we don't care)");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // expected before first buffer of data
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                    } else if (decoderStatus < 0) {
                        fail("unexpected result from decoder.dequeueOutputBuffer: "+decoderStatus);
                    } else { // decoderStatus >= 0
                        if (VERBOSE) Log.d(TAG, "surface decoder given buffer "
                                + decoderStatus + " (size=" + info.size + ")");
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        boolean doRender = (info.size != 0);

                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            if (VERBOSE) Log.d(TAG, "awaiting frame");
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();

                            // Send it to the encoder.
                            inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                            if (VERBOSE) Log.d(TAG, "swapBuffers");
                            inputSurface.swapBuffers();
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // forward decoder EOS to encoder
                            if (VERBOSE) Log.d(TAG, "signaling input EOS");
                            if (WORK_AROUND_BUGS) {
                                // Bail early, possibly dropping a frame.
                                return;
                            } else {
                                encoder.signalEndOfInputStream();
                            }
                        }
                    }
                }
            }
        }

        if (inputChunk != outputCount) {
            throw new RuntimeException("frame lost: " + inputChunk + " in, " +
                    outputCount + " out");
        }
    }

    /**
     * Checks the video file to see if the contents match our expectations.  We decode the
     * video to a Surface and check the pixels with GL.
     */
    private void checkVideoFile(VideoChunks inputData)
            throws IOException {
        OutputSurface surface = null;
        MediaCodec decoder = null;

        mLargestColorDelta = -1;

        if (VERBOSE) Log.d(TAG, "checkVideoFile");

        try {
            surface = new OutputSurface(mWidth, mHeight, mUseHighBitDepth);

            MediaFormat format = inputData.getMediaFormat();
            decoder = MediaCodec.createByCodecName(mDecoderName);
            format.setInteger(KEY_ALLOW_FRAME_DROP, 0);
            decoder.configure(format, surface.getSurface(), null, 0);
            decoder.start();

            int badFrames = checkVideoData(inputData, decoder, surface);
            if (badFrames != 0) {
                fail("Found " + badFrames + " bad frames");
            }
        } finally {
            if (surface != null) {
                surface.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }

            Log.i(TAG, "Largest color delta: " + mLargestColorDelta);
        }
    }

    /**
     * Checks the video data.
     *
     * @return the number of bad frames
     */
    private int checkVideoData(VideoChunks inputData, MediaCodec decoder, OutputSurface surface) {
        final int TIMEOUT_USEC = 1000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int checkIndex = 0;
        int badFrames = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            if (VERBOSE) Log.d(TAG, "check loop");

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (inputChunk == inputData.getNumChunks()) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputData.getChunkData(inputChunk, inputBuf);
                        int flags = inputData.getChunkFlags(inputChunk);
                        long time = inputData.getChunkTime(inputChunk);
                        decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                time, flags);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    inputBuf.position() + " flags=" + flags);
                        }
                        inputChunk++;
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    decoderOutputBuffers = decoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    fail("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
                    ByteBuffer decodedData = decoderOutputBuffers[decoderStatus];

                    if (VERBOSE) Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        if (VERBOSE) Log.d(TAG, "awaiting frame " + checkIndex);
                        assertEquals("Wrong time stamp", computePresentationTime(checkIndex),
                                info.presentationTimeUs);
                        surface.awaitNewImage();
                        surface.drawImage();
                        if (!checkSurfaceFrame(checkIndex)) {
                            badFrames++;
                        }
                        checkIndex++;
                    }
                }
            }
        }

        return badFrames;
    }

    /**
     * Checks the frame for correctness, using GL to check RGB values.
     *
     * @return true if the frame looks good
     */
    private boolean checkSurfaceFrame(int frameIndex) {
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(4); // TODO - reuse this
        boolean frameFailed = false;
        // Choose the appropriate initial/regular tolerance
        int maxDelta = frameIndex < INITIAL_TOLERANCE_FRAME_LIMIT ? INITIAL_TOLERANCE : TOLERANCE;
        for (int i = 0; i < 8; i++) {
            // Note the coordinates are inverted on the Y-axis in GL.
            int x, y;
            if (i < 4) {
                x = i * (mWidth / 4) + (mWidth / 8);
                y = (mHeight * 3) / 4;
            } else {
                x = (7 - i) * (mWidth / 4) + (mWidth / 8);
                y = mHeight / 4;
            }

            int r, g, b;
            if (mUseHighBitDepth) {
                GLES30.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA,
                        GLES30.GL_UNSIGNED_INT_2_10_10_10_REV, pixelBuf);
                r = (pixelBuf.get(1) & 0x03) << 8 | (pixelBuf.get(0) & 0xFF);
                g = (pixelBuf.get(2) & 0x0F) << 6 | ((pixelBuf.get(1) >> 2) & 0x3F);
                b = (pixelBuf.get(3) & 0x3F) << 4 | ((pixelBuf.get(2) >> 4) & 0x0F);
                // Convert the values to 8 bit (using rounding division by 4) as comparisons
                // later are with 8 bit RGB values
                r = (r + 2) >> 2;
                g = (g + 2) >> 2;
                b = (b + 2) >> 2;
            } else {
                GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
                r = pixelBuf.get(0) & 0xFF;
                g = pixelBuf.get(1) & 0xFF;
                b = pixelBuf.get(2) & 0xFF;
            }
            //Log.d(TAG, "GOT(" + frameIndex + "/" + i + "): r=" + r + " g=" + g + " b=" + b);

            int expR, expG, expB;
            if (i == frameIndex % 8) {
                // colored rect (green/blue swapped)
                expR = TEST_R1;
                expG = TEST_B1;
                expB = TEST_G1;
            } else {
                // zero background color (green/blue swapped)
                expR = TEST_R0;
                expG = TEST_B0;
                expB = TEST_G0;
            }
            if (!isColorClose(r, expR, maxDelta) ||
                    !isColorClose(g, expG, maxDelta) ||
                    !isColorClose(b, expB, maxDelta)) {
                Log.w(TAG, "Bad frame " + frameIndex + " (rect=" + i + ": rgb=" + r +
                        "," + g + "," + b + " vs. expected " + expR + "," + expG +
                        "," + expB + ") for allowed error of " + maxDelta);
                frameFailed = true;
            }
        }

        return !frameFailed;
    }

    /**
     * Returns true if the actual color value is close to the expected color value.  Updates
     * mLargestColorDelta.
     */
    boolean isColorClose(int actual, int expected, int maxDelta) {
        int delta = Math.abs(actual - expected);
        if (delta > mLargestColorDelta) {
            mLargestColorDelta = delta;
        }
        return (delta <= maxDelta);
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 123 + frameIndex * 1000000 / FRAME_RATE;
    }


    /**
     * The elementary stream coming out of the encoder needs to be fed back into
     * the decoder one chunk at a time.  If we just wrote the data to a file, we would lose
     * the information about chunk boundaries.  This class stores the encoded data in memory,
     * retaining the chunk organization.
     */
    private static class VideoChunks {
        private MediaFormat mMediaFormat;
        private ArrayList<byte[]> mChunks = new ArrayList<byte[]>();
        private ArrayList<Integer> mFlags = new ArrayList<Integer>();
        private ArrayList<Long> mTimes = new ArrayList<Long>();

        /**
         * Sets the MediaFormat, for the benefit of a future decoder.
         */
        public void setMediaFormat(MediaFormat format) {
            mMediaFormat = format;
        }

        /**
         * Gets the MediaFormat that was used by the encoder.
         */
        public MediaFormat getMediaFormat() {
            return new MediaFormat(mMediaFormat);
        }

        /**
         * Adds a new chunk.  Advances buf.position to buf.limit.
         */
        public void addChunk(ByteBuffer buf, int flags, long time) {
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            mChunks.add(data);
            mFlags.add(flags);
            mTimes.add(time);
        }

        /**
         * Returns the number of chunks currently held.
         */
        public int getNumChunks() {
            return mChunks.size();
        }

        /**
         * Copies the data from chunk N into "dest".  Advances dest.position.
         */
        public void getChunkData(int chunk, ByteBuffer dest) {
            byte[] data = mChunks.get(chunk);
            dest.put(data);
        }

        /**
         * Returns the flags associated with chunk N.
         */
        public int getChunkFlags(int chunk) {
            return mFlags.get(chunk);
        }

        /**
         * Returns the timestamp associated with chunk N.
         */
        public long getChunkTime(int chunk) {
            return mTimes.get(chunk);
        }

        /**
         * Writes the chunks to a file as a contiguous stream.  Useful for debugging.
         */
        public void saveToFile(File file) {
            Log.d(TAG, "saving chunk data to file " + file);
            FileOutputStream fos = null;
            BufferedOutputStream bos = null;

            try {
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);
                fos = null;     // closing bos will also close fos

                int numChunks = getNumChunks();
                for (int i = 0; i < numChunks; i++) {
                    byte[] chunk = mChunks.get(i);
                    bos.write(chunk);
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } finally {
                try {
                    if (bos != null) {
                        bos.close();
                    }
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }
    }
}
