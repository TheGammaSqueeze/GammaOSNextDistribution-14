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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.CodecEncoderTestBase.colorFormatToString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecAsyncHandler;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.InputSurface;
import android.mediav2.common.cts.OutputManager;
import android.mediav2.common.cts.OutputSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.microedition.khronos.opengles.GL10;

/**
 * Color Primaries, Color Standard and Color Transfer are essential information to display the
 * decoded YUV on an RGB display accurately. Tests
 * {@link EncoderColorAspectsTest#testColorAspects()} and
 * {@link DecoderColorAspectsTest#testColorAspects()} checks if the encoder and decoder
 * components are signalling the configured color aspects correctly.
 * This test verifies if the device decoder/display is using this color aspects correctly
 * <p>
 * Test pipeline:
 * <pre> [[ Input RGB frames -> encoder -> muxer -> decoder -> display -> Output RGB frames ]]
 * </pre>
 * Assuming no quantization losses, the test verifies if the input rgb pixel values and output
 * rgb pixel values are within tolerance limits.
 */
@RunWith(Parameterized.class)
public class EncodeDecodeAccuracyTest extends CodecDecoderTestBase {
    private final String LOG_TAG = EncodeDecodeAccuracyTest.class.getSimpleName();
    // The bitrates are configured to large values. The content has zero motion, so in-time the
    // qp of the encoded clips shall drop down to < 10. Further the color bands are aligned to 2,
    // so from downsampling rgb24 to yuv420p, even if bilinear filters are used as opposed to
    // skipping samples, we may not see large color loss. Hence allowable tolerance is kept to 5.
    // until QP stabilizes, the tolerance is set at 7. For devices upgrading to T, thresholds are
    // relaxed to 8 and 10.
    private static final int TRANSIENT_STATE_COLOR_DELTA = FIRST_SDK_IS_AT_LEAST_T ? 7 : 10;
    private static final int STEADY_STATE_COLOR_DELTA = FIRST_SDK_IS_AT_LEAST_T ? 5 : 8;
    private final int[][] mColorBars = new int[][]{
            {66, 133, 244},
            {219, 68, 55},
            {244, 180, 0},
            {15, 157, 88},
            {186, 85, 211},
            {119, 136, 153},
            {225, 179, 120},
            {224, 204, 151},
            {236, 121, 154},
            {159, 2, 81},
            {120, 194, 87}
    };
    private final int FRAMES_TO_ENCODE = 30;
    private final int STEADY_STATE_FRAME_INDEX = 20;

    private final String mCompName;
    private final EncoderConfigParams mEncCfgParams;

    private final CodecAsyncHandler mAsyncHandleEncoder;
    private MediaCodec mEncoder;
    private Surface mInpSurface;
    private InputSurface mEGLWindowInpSurface;
    private OutputSurface mEGLWindowOutSurface;
    private boolean mSawInputEOSEnc;
    private boolean mSawOutputEOSEnc;
    private int mLatency;
    private boolean mReviseLatency;
    private int mInputCountEnc;
    private int mOutputCountEnc;
    private boolean mSaveToMemEnc;
    private OutputManager mOutputBuffEnc;
    ArrayList<MediaCodec.BufferInfo> mInfoListEnc;

    private final int mColorBarWidth;
    private final int xOffset;
    private final int yOffset = 64;
    private int mLargestColorDelta;
    private int mBadFrames;

    @After
    public void tearDown() {
        if (mEGLWindowInpSurface != null) {
            mEGLWindowInpSurface.release();
            mEGLWindowInpSurface = null;
        }
        if (mInpSurface != null) {
            mInpSurface.release();
            mInpSurface = null;
        }
        if (mEncoder != null) {
            mEncoder.release();
            mEncoder = null;
        }
        mSurface = null;
        if (mEGLWindowOutSurface != null) {
            mEGLWindowOutSurface.release();
            mEGLWindowOutSurface = null;
        }
    }

    public EncodeDecodeAccuracyTest(String encoder, String mediaType,
            EncoderConfigParams encCfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(null, mediaType, null, allTestParams);
        mCompName = encoder;
        mEncCfgParams = encCfgParams;
        mAsyncHandleEncoder = new CodecAsyncHandler();
        mLatency = 0;
        mReviseLatency = false;
        mInfoListEnc = new ArrayList<>();
        int barWidth = (mEncCfgParams.mWidth + mColorBars.length - 1) / mColorBars.length;
        // aligning color bands to 2 is done because, during chroma subsampling of rgb24 to yuv420p,
        // simple skipping alternate samples or bilinear filter would have same effect.
        mColorBarWidth = (barWidth % 2 == 0) ? barWidth : barWidth + 1;
        assertTrue(mColorBarWidth >= 16);
        xOffset = mColorBarWidth >> 2;
    }

    @Before
    public void setUp() throws IOException {
        // Few cuttlefish specific color conversion issues were fixed after Android T.
        if (MediaUtils.onCuttlefish()) {
            assumeTrue("Color conversion related tests are not valid on cuttlefish releases "
                    + "through android T", IS_AT_LEAST_U);
        }
        if (mEncCfgParams.mInputBitDepth == 10) {
            assumeTrue("Codec doesn't support ABGR2101010",
                    hasSupportForColorFormat(mCompName, mMediaType, COLOR_Format32bitABGR2101010));
            assumeTrue("Codec doesn't support high bit depth profile encoding",
                    doesCodecSupportHDRProfile(mCompName, mMediaType));
        }
    }

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int frameRate, int bitRate, int range, int standard, int transfer,
            int colorFormat, int bitDepth) {
        EncoderConfigParams.Builder foreman = new EncoderConfigParams.Builder(mediaType)
                .setWidth(width)
                .setHeight(height)
                .setBitRate(bitRate)
                .setFrameRate(frameRate)
                .setRange(range)
                .setStandard(standard)
                .setTransfer(transfer)
                .setColorFormat(colorFormat)
                .setInputBitDepth(bitDepth);
        if (colorFormat == COLOR_FormatSurface && bitDepth == 10) {
            foreman.setProfile(Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType))[0]);
        }
        return foreman.build();
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final Object[][] baseArgsList = new Object[][]{
                // "video/*", width, height, framerate, bitrate, range, standard, transfer,
                // useHighBitDepth
                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO, false},
                {720, 576, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                        false},
                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT2020,
                        MediaFormat.COLOR_TRANSFER_ST2084, true},

                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                    MediaFormat.COLOR_STANDARD_BT2020, MediaFormat.COLOR_TRANSFER_ST2084, true},
                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                    MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_TRANSFER_SDR_VIDEO, true},
                // TODO (b/186511593)
                /*
                {1280, 720, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {720, 576, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {1280, 720, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},

                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {720, 576, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {1280, 720, 30, 3000000, MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {720, 480, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {720, 576, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {1280, 720, 30, 3000000, MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                 */
        };
        // Note: although vp8 and vp9 donot contain fields to signal color aspects properly, this
        // information can be muxed in to containers of mkv and mp4. So even those clips
        // should pass these tests
        final String[] mediaTypes =
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                        MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9,
                        MediaFormat.MIMETYPE_VIDEO_AV1};
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        for (Object[] obj : baseArgsList) {
            final int width = (int) obj[0];
            final int height = (int) obj[1];
            final int fps = (int) obj[2];
            final int br = (int) obj[3];
            final int range = (int) obj[4];
            final int std = (int) obj[5];
            final int tfr = (int) obj[6];
            final int bd = (boolean) obj[7] ? 10 : 8;
            for (String mediaType : mediaTypes) {
                // the vp8 spec only supports 8 bit
                if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP8) && bd == 10) continue;
                Object[] testArgs = new Object[3];
                testArgs[0] = mediaType;
                testArgs[1] = getVideoEncoderCfgParams(mediaType, width, height, fps, br, range,
                        std, tfr, COLOR_FormatSurface, bd);
                testArgs[2] = String.format("%d_%d_%dkbps_%dfps_%d_%d_%d_%s", width, height,
                        br / 1000, fps, range, std, tfr,
                        colorFormatToString(COLOR_FormatSurface, bd));
                exhaustiveArgsList.add(testArgs);
            }
        }
        return CodecTestBase.prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                false);
    }

    private void resetContext(boolean isAsync) {
        super.resetContext(isAsync, false);
        mAsyncHandleEncoder.resetContext();
        mSawInputEOSEnc = false;
        mSawOutputEOSEnc = false;
        mLatency = 0;
        mReviseLatency = false;
        mInputCountEnc = 0;
        mOutputCountEnc = 0;
        mInfoListEnc.clear();
        mLargestColorDelta = 0;
        mBadFrames = 0;
    }

    private void configureCodec(MediaFormat encFormat, boolean isAsync) {
        resetContext(isAsync);
        mAsyncHandleEncoder.setCallBack(mEncoder, isAsync);
        mEncoder.configure(encFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        if (mEncoder.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
            mReviseLatency = true;
            mLatency = mEncoder.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
        }
        mInpSurface = mEncoder.createInputSurface();
        assertTrue("Surface is not valid \n" + mTestConfig + mTestEnv, mInpSurface.isValid());
        mEGLWindowInpSurface =
                new InputSurface(mInpSurface, false, mEncCfgParams.mInputBitDepth == 10);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    private void dequeueEncoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "encoder output: id: " + bufferIndex + " flags: " + info.flags +
                    " size: " + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOSEnc = true;
        }
        if (info.size > 0) {
            if (mSaveToMemEnc) {
                ByteBuffer buf = mEncoder.getOutputBuffer(bufferIndex);
                MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                copy.set(mOutputBuffEnc.getOutStreamSize(), info.size, info.presentationTimeUs,
                        info.flags);
                mInfoListEnc.add(copy);
                mOutputBuffEnc.saveToMemory(buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuffEnc.saveOutPTS(info.presentationTimeUs);
                mOutputCountEnc++;
            }
        }
        mEncoder.releaseOutputBuffer(bufferIndex, false);
    }

    private void tryEncoderOutput(long timeOutUs) throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            if (!mAsyncHandleEncoder.hasSeenError() && !mSawOutputEOSEnc) {
                while (mReviseLatency) {
                    mAsyncHandleEncoder.waitOnFormatChange();
                    mReviseLatency = false;
                    int actualLatency = mAsyncHandleEncoder.getOutputFormat()
                            .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                    if (mLatency < actualLatency) {
                        mLatency = actualLatency;
                        return;
                    }
                }
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleEncoder.getOutput();
                if (element != null) {
                    dequeueEncoderOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            if (!mSawOutputEOSEnc) {
                int outputBufferId = mEncoder.dequeueOutputBuffer(outInfo, timeOutUs);
                if (outputBufferId >= 0) {
                    dequeueEncoderOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mLatency = mEncoder.getOutputFormat()
                            .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                }
            }
        }
    }

    private void waitForAllEncoderOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandleEncoder.hasSeenError() && !mSawOutputEOSEnc) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        } else {
            while (!mSawOutputEOSEnc) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        }
    }

    private void queueEOSEnc() {
        if (!mAsyncHandleEncoder.hasSeenError() && !mSawInputEOSEnc) {
            mEncoder.signalEndOfInputStream();
            mSawInputEOSEnc = true;
            if (ENABLE_LOGS) Log.d(LOG_TAG, "signalled end of stream");
        }
    }

    private void doWorkEnc(int frameLimit) throws InterruptedException {
        while (!mAsyncHandleEncoder.hasSeenError() && !mSawInputEOSEnc &&
                mInputCountEnc < frameLimit) {
            if (mInputCountEnc - mOutputCountEnc > mLatency) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
            mEGLWindowInpSurface.makeCurrent();
            generateSurfaceFrame();
            mEGLWindowInpSurface
                    .setPresentationTime(computePresentationTime(mInputCountEnc) * 1000);
            if (ENABLE_LOGS) Log.d(LOG_TAG, "inputSurface swapBuffers");
            mEGLWindowInpSurface.swapBuffers();
            mInputCountEnc++;
        }
    }

    private long computePresentationTime(int frameIndex) {
        return frameIndex * 1000000L / mEncCfgParams.mFrameRate;
    }

    private void generateSurfaceFrame() {
        GLES20.glViewport(0, 0, mEncCfgParams.mWidth, mEncCfgParams.mHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        for (int i = 0; i < mColorBars.length; i++) {
            int startX = i * mColorBarWidth;
            int endX = Math.min(startX + mColorBarWidth, mEncCfgParams.mWidth);
            GLES20.glScissor(startX, 0, endX, mEncCfgParams.mHeight);
            GLES20.glClearColor(mColorBars[i][0] / 255.0f, mColorBars[i][1] / 255.0f,
                    mColorBars[i][2] / 255.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

    boolean isColorClose(int actual, int expected) {
        int delta = Math.abs(actual - expected);
        if (delta > mLargestColorDelta) mLargestColorDelta = delta;
        int maxAllowedDelta = (mOutputCount >= STEADY_STATE_FRAME_INDEX ? STEADY_STATE_COLOR_DELTA :
                TRANSIENT_STATE_COLOR_DELTA);
        return (delta <= maxAllowedDelta);
    }

    private boolean checkSurfaceFrame(int frameIndex) {
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(4);
        boolean frameFailed = false;
        for (int i = 0; i < mColorBars.length; i++) {
            int x = mColorBarWidth * i + xOffset;
            int y = yOffset;
            int r, g, b;
            if (mEncCfgParams.mInputBitDepth == 10) {
                GLES30.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GLES30.GL_UNSIGNED_INT_2_10_10_10_REV,
                        pixelBuf);
                r = (pixelBuf.get(1) & 0x03) << 8 | (pixelBuf.get(0) & 0xFF);
                g = (pixelBuf.get(2) & 0x0F) << 6 | ((pixelBuf.get(1) >> 2) & 0x3F);
                b = (pixelBuf.get(3) & 0x3F) << 4 | ((pixelBuf.get(2) >> 4) & 0x0F);
                // Convert the values to 8 bit as comparisons later are with 8 bit RGB values
                r = (r + 2) >> 2;
                g = (g + 2) >> 2;
                b = (b + 2) >> 2;
            } else {
                GLES20.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelBuf);
                r = pixelBuf.get(0) & 0xFF;
                g = pixelBuf.get(1) & 0xFF;
                b = pixelBuf.get(2) & 0xFF;
            }
            if (!(isColorClose(r, mColorBars[i][0]) && isColorClose(g, mColorBars[i][1]) &&
                    isColorClose(b, mColorBars[i][2]))) {
                Log.w(LOG_TAG, "Bad frame " + frameIndex + " (rect={" + x + " " + y + "} :rgb=" +
                        r + "," + g + "," + b + " vs. expected " + mColorBars[i][0] +
                        "," + mColorBars[i][1] + "," + mColorBars[i][2] + ")");
                frameFailed = true;
            }
        }
        return frameFailed;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0) {
            mEGLWindowOutSurface.awaitNewImage();
            mEGLWindowOutSurface.drawImage();
            if (checkSurfaceFrame(mOutputCount - 1)) mBadFrames++;
        }
    }

    private void decodeElementaryStream(MediaFormat format)
            throws IOException, InterruptedException {
        mEGLWindowOutSurface = new OutputSurface(mEncCfgParams.mWidth, mEncCfgParams.mHeight,
                mEncCfgParams.mInputBitDepth == 10);
        mSurface = mEGLWindowOutSurface.getSurface();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        ArrayList<String> listOfDecoders =
                CodecDecoderTestBase.selectCodecs(mMediaType, formats, null, false);
        assertFalse("no suitable codecs found for : " + format + "\n" + mTestConfig + mTestEnv,
                listOfDecoders.isEmpty());
        for (String decoder : listOfDecoders) {
            if (mEncCfgParams.mInputBitDepth == 10
                    && !hasSupportForColorFormat(decoder, mMediaType, COLOR_FormatYUVP010)
                    && !hasSupportForColorFormat(decoder, mMediaType,
                                                 COLOR_Format32bitABGR2101010)) {
                continue;
            }
            mCodec = MediaCodec.createByCodecName(decoder);
            configureCodec(format, true, true, false);
            mOutputBuff = new OutputManager();
            mCodec.start();
            doWork(mOutputBuffEnc.getBuffer(), mInfoListEnc);
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
        }
        mSurface = null;
        mEGLWindowOutSurface.release();
        mEGLWindowOutSurface = null;
    }

    /**
     * Check description of class {@link EncodeDecodeAccuracyTest}
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeDecodeAccuracyRGB() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams.getFormat();
        final boolean isAsync = true;
        mSaveToMemEnc = true;
        mOutputBuffEnc = new OutputManager();
        {
            mEncoder = MediaCodec.createByCodecName(mCompName);
            mOutputBuffEnc.reset();
            mInfoListEnc.clear();
            configureCodec(format, isAsync);
            mEncoder.start();
            doWorkEnc(FRAMES_TO_ENCODE);
            queueEOSEnc();
            waitForAllEncoderOutputs();
            MediaFormat outputFormat = mEncoder.getOutputFormat();
            assertTrue(outputFormat.containsKey(MediaFormat.KEY_COLOR_RANGE));
            assertTrue(outputFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD));
            assertTrue(outputFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER));
            if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                    || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                outputFormat.removeKey(MediaFormat.KEY_COLOR_RANGE);
                outputFormat.removeKey(MediaFormat.KEY_COLOR_STANDARD);
                outputFormat.removeKey(MediaFormat.KEY_COLOR_TRANSFER);
            }
            mEGLWindowInpSurface.release();
            mEGLWindowInpSurface = null;
            mInpSurface.release();
            mInpSurface = null;
            mEncoder.reset();
            mEncoder.release();
            decodeElementaryStream(outputFormat);
            assertEquals("color difference exceeds allowed tolerance in " + mBadFrames + " out of "
                    + FRAMES_TO_ENCODE + " frames \n" + mTestConfig + mTestEnv, 0, mBadFrames);
        }
    }
}

