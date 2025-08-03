/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.InputSurface;
import android.mediav2.common.cts.OutputManager;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Color Primaries, Color Standard and Color Transfer are essential information to display the
 * decoded YUV on an RGB display accurately. These 3 parameters can be signalled via containers
 * (mp4, mkv, ...) and some video standards also allow signalling this information in elementary
 * stream. Avc, Hevc, Av1, ... allow signalling this information in elementary stream, vpx relies
 * on webm/mkv or some other container for signalling.
 * <p>
 * If the encoder is configured with color aspects, then it is expected to place this information
 * in the elementary stream as-is if possible. The same goes for container as well. The test
 * validates this.
 * <p>
 * Hybrid log gamma transfer characteristics are applicable for high bit depth profiles. Standard
 * gamma curve characteristics are applicable for standard dynamic ranges. The test doesn't
 * exhaustively try all combinations of primaries, standard, transfer on all encoding profiles.
 * SDR specific characteristics are restricted to sdr profiles and HLG/HDR specific profiles are
 * restricted to HLG/HDR profiles.
 */
@RunWith(Parameterized.class)
public class EncoderColorAspectsTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderColorAspectsTest.class.getSimpleName();

    private Surface mInpSurface;
    private InputSurface mEGLWindowInpSurface;

    private int mLatency;
    private boolean mReviseLatency;

    private static final ArrayList<String> IGNORE_COLOR_BOX_LIST = new ArrayList<>();

    static {
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    private static boolean sIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);

    public EncoderColorAspectsTest(String encoder, String mediaType,
            EncoderConfigParams encCfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
        mLatency = encCfgParams.mMaxBFrames;
    }

    private static void prepareArgsList(List<Object[]> exhaustiveArgsList, String[] mediaTypes,
            int[] ranges, int[] standards, int[] transfers, int colorFormat, int bitDepth) {
        // Assuming all combinations are supported by the standard which is true for AVC, HEVC, AV1,
        // VP8 and VP9.
        int[] maxBFrames = {0, 2};
        for (String mediaType : mediaTypes) {
            for (int range : ranges) {
                for (int standard : standards) {
                    for (int transfer : transfers) {
                        for (int maxBFrame : maxBFrames) {
                            if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                                    && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)
                                    && maxBFrame != 0) {
                                continue;
                            }
                            Object[] testArgs = new Object[3];
                            testArgs[0] = mediaType;
                            EncoderConfigParams.Builder foreman =
                                    new EncoderConfigParams.Builder(mediaType)
                                            .setRange(range)
                                            .setStandard(standard)
                                            .setTransfer(transfer)
                                            .setMaxBFrames(maxBFrame)
                                            .setColorFormat(colorFormat)
                                            .setInputBitDepth(bitDepth);
                            if ((colorFormat == COLOR_FormatSurface && bitDepth == 10)
                                    || colorFormat == COLOR_FormatYUVP010) {
                                foreman.setProfile(
                                        Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType))[0]);
                            }
                            EncoderConfigParams cfg = foreman.build();
                            testArgs[1] = cfg;
                            testArgs[2] = String.format("%s_%s_%s_%s_%d-bframes",
                                    rangeToString(range),
                                    colorStandardToString(standard),
                                    colorTransferToString(transfer),
                                    colorFormatToString(colorFormat, bitDepth),
                                    maxBFrame);
                            exhaustiveArgsList.add(testArgs);
                        }
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;

        List<Object[]> exhaustiveArgsList = new ArrayList<>();

        String[] mediaTypes = {MediaFormat.MIMETYPE_VIDEO_AV1,
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9};
        // ColorAspects for SDR profiles
        int[] ranges = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_RANGE_FULL,
                MediaFormat.COLOR_RANGE_LIMITED};
        int[] standards = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_STANDARD_BT601_NTSC};
        int[] transfers = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_TRANSFER_LINEAR,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO};

        prepareArgsList(exhaustiveArgsList, mediaTypes, ranges, standards, transfers,
                COLOR_FormatYUV420Flexible, -1);
        prepareArgsList(exhaustiveArgsList, mediaTypes, ranges, standards, transfers,
                COLOR_FormatSurface, 8);
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            // ColorAspects for HDR profiles
            String[] mediaTypesHighBitDepth = {MediaFormat.MIMETYPE_VIDEO_AV1,
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9};
            int[] standardsHighBitDepth = {-1,
                    UNSPECIFIED,
                    MediaFormat.COLOR_STANDARD_BT709,
                    MediaFormat.COLOR_STANDARD_BT2020};
            int[] transfersHighBitDepth = {-1,
                    UNSPECIFIED,
                    MediaFormat.COLOR_TRANSFER_HLG,
                    MediaFormat.COLOR_TRANSFER_ST2084};

            prepareArgsList(exhaustiveArgsList, mediaTypesHighBitDepth, ranges,
                    standardsHighBitDepth, transfersHighBitDepth, COLOR_FormatYUVP010, -1);
            prepareArgsList(exhaustiveArgsList, mediaTypesHighBitDepth, ranges,
                    standardsHighBitDepth, transfersHighBitDepth, COLOR_FormatSurface, 10);
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    private long computePresentationTime(int frameIndex) {
        return frameIndex * 1000000L / mActiveEncCfg.mFrameRate;
    }

    private void generateSurfaceFrame() {
        GLES20.glViewport(0, 0, mActiveEncCfg.mWidth, mActiveEncCfg.mHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(128.0f, 128.0f, 128.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void tryEncoderOutput() throws InterruptedException {
        if (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
            while (mReviseLatency) {
                mAsyncHandle.waitOnFormatChange();
                mReviseLatency = false;
                int actualLatency = mAsyncHandle.getOutputFormat()
                        .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                if (mLatency < actualLatency) {
                    mLatency = actualLatency;
                    return;
                }
            }
            Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
            if (element != null) {
                dequeueOutput(element.first, element.second);
            }
        }
    }

    protected void queueEOS() throws InterruptedException {
        if (mActiveEncCfg.mColorFormat != COLOR_FormatSurface) {
            super.queueEOS();
        } else {
            if (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                mCodec.signalEndOfInputStream();
                mSawInputEOS = true;
                if (ENABLE_LOGS) Log.d(LOG_TAG, "signalled end of stream");
            }
        }
    }

    protected void doWork(int frameLimit) throws IOException, InterruptedException {
        if (mActiveEncCfg.mColorFormat != COLOR_FormatSurface) {
            super.doWork(frameLimit);
        } else {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS &&
                    mInputCount < frameLimit) {
                if (mInputCount - mOutputCount > mLatency) {
                    tryEncoderOutput();
                }
                mEGLWindowInpSurface.makeCurrent();
                generateSurfaceFrame();
                long pts = computePresentationTime(mInputCount);
                mEGLWindowInpSurface.setPresentationTime(pts * 1000);
                if (ENABLE_LOGS) Log.d(LOG_TAG, "inputSurface swapBuffers");
                mEGLWindowInpSurface.swapBuffers();
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
        }
    }

    /**
     * ColorAspects are passed to the encoder at the time of configuration. The encoder is
     * expected to pass this information to outputFormat() so that muxer can use this information
     * to populate color metadata. If the bitstream is capable of capturing color metadata
     * losslessly then encoder is also expected to use this information during bitstream
     * generation. Although a given media type can be muxed using many containers, the test does
     * not use all available ones. Instead the most preferred one is selected.
     * vpx streams are muxed using webm writer and others are muxed using mp4 writer.
     * Briefly, the test checks OMX/c2 framework, plugins, encoder, muxer ability to SIGNAL color
     * metadata.
     *
     * When running CTS, both testColorAspectsEndToEnd and testColorAspectsEncoderOnly
     * execute, with some duplication of effort.
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @NonMainlineTest
    public void testColorAspectsEndToEnd() throws IOException, InterruptedException {
        doFullColorAspects(true /* includeMuxing */);
    }

    /**
     * ColorAspects are passed to the encoder at the time of configuration. The encoder is
     * expected to pass this information to outputFormat() so that a consumer can use this
     * information to populate color metadata.
     * ColorAspects encoded in the bitstream itself -- the test only checks those after muxing
     * the outputs (e.g. the EndToEnd test above).
     *
     * This is therefore a subset of the testColorAspectsEndToEnd() test above, skipping
     * the muxing (which is outside of mainline), the re-reading via extractors, and
     * the bitstream contents (e.g. the CSD) that we get from decoding.
     * TODO: It would be good if we could validate the bitstream (CSD) without the
     * muxer/extractor steps in between.
     *
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testColorAspectsEncoderOnly() throws IOException, InterruptedException {
        doFullColorAspects(false /* includeMuxing */);
    }

    private void doFullColorAspects(boolean includeMuxing) throws IOException,
            InterruptedException {
        Assume.assumeTrue("Test introduced with Android 11", sIsAtLeastR);

        mActiveEncCfg = mEncCfgParams[0];
        if (mActiveEncCfg.mInputBitDepth > 8) {
            // Check if encoder is capable of supporting HDR profiles.
            // Previous check doesn't verify this as profile isn't set in the format
            Assume.assumeTrue(mCodecName + " doesn't support HDR encoding",
                    CodecTestBase.doesCodecSupportHDRProfile(mCodecName, mMediaType));

            // Encoder surface mode tests are to be enabled only if an encoder supports
            // COLOR_Format32bitABGR2101010
            if (mActiveEncCfg.mColorFormat == COLOR_FormatSurface) {
                Assume.assumeTrue(mCodecName + " doesn't support RGBA1010102",
                        hasSupportForColorFormat(mCodecName, mMediaType,
                                                 COLOR_Format32bitABGR2101010));
            } else {
                Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                                mActiveEncCfg.mColorFormat, mActiveEncCfg.mInputBitDepth),
                        hasSupportForColorFormat(mCodecName, mMediaType,
                                                 mActiveEncCfg.mColorFormat));
            }
        }

        if (mActiveEncCfg.mColorFormat == COLOR_FormatSurface) {
            Assume.assumeTrue("Surface mode tests are limited to devices launching with Android T",
                    FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T);
            // Few cuttlefish specific color conversion issues were fixed after Android T.
            if (MediaUtils.onCuttlefish()) {
                Assume.assumeTrue("Color conversion related tests are not valid on cuttlefish "
                        + "releases through android T", IS_AT_LEAST_U);
            }
        } else {
            mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
            assertNotNull("no raw resource found for testing config : " + mActiveEncCfg
                    + mTestConfig + mTestEnv, mActiveRawRes);
            setUpSource(mActiveRawRes.mFileName);
        }

        /* TODO(b/181126614, b/268175825) */
        if (MediaUtils.isPc()) {
            Log.d(LOG_TAG, "test skipped due to b/181126614, b/268175825");
            return;
        }

        {
            mSaveToMem = true;
            mOutputBuff = new OutputManager();
            mCodec = MediaCodec.createByCodecName(mCodecName);

            // When in surface mode, encoder needs to be configured in async mode
            boolean isAsync = mActiveEncCfg.mColorFormat == COLOR_FormatSurface;
            configureCodec(mActiveEncCfg.getFormat(), isAsync, true, true);

            if (mActiveEncCfg.mColorFormat == COLOR_FormatSurface) {
                mInpSurface = mCodec.createInputSurface();
                assertTrue("Surface is not valid \n" + mTestConfig + mTestEnv,
                        mInpSurface.isValid());
                mEGLWindowInpSurface =
                        new InputSurface(mInpSurface, false, mActiveEncCfg.mInputBitDepth == 10);
                if (mCodec.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
                    mReviseLatency = true;
                    mLatency = mCodec.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
                }
            }
            mCodec.start();
            doWork(4);
            queueEOS();
            waitForAllOutputs();

            if (mEGLWindowInpSurface != null) {
                mEGLWindowInpSurface.release();
                mEGLWindowInpSurface = null;
            }
            if (mInpSurface != null) {
                mInpSurface.release();
                mInpSurface = null;
            }

            // verify if the out fmt contains color aspects as expected
            MediaFormat fmt = mCodec.getOutputFormat();
            validateColorAspects(fmt, mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                    mActiveEncCfg.mTransfer);
            mCodec.stop();
            mCodec.release();

            if (includeMuxing) {
                int muxerFormat = getMuxerFormatForMediaType(mMediaType);
                String tmpPath = getTempFilePath((mActiveEncCfg.mInputBitDepth == 10) ? "10bit"
                                                                                      : "");
                muxOutput(tmpPath, muxerFormat, fmt, mOutputBuff.getBuffer(), mInfoList);

                // verify if the muxed file contains color aspects as expected
                MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                String decoder = codecList.findDecoderForFormat(mActiveEncCfg.getFormat());
                assertNotNull("Device advertises support for encoding " + mActiveEncCfg.getFormat()
                        + " but not decoding it. \n" + mTestConfig + mTestEnv, decoder);
                CodecDecoderTestBase cdtb = new CodecDecoderTestBase(decoder, mMediaType, tmpPath,
                        mAllTestParams);
                cdtb.validateColorAspects(mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                        mActiveEncCfg.mTransfer, false);

                // if color metadata can also be signalled via elementary stream then verify if the
                // elementary stream contains color aspects as expected
                if (IGNORE_COLOR_BOX_LIST.contains(mMediaType)) {
                    cdtb.validateColorAspects(mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                            mActiveEncCfg.mTransfer, true);
                }
                new File(tmpPath).delete();
            }
        }
    }
}
