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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestActivity;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.view.Surface;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test mediacodec api, video decoders and their interactions in surface mode.
 * <p>
 * When video decoders are configured in surface mode, the getOutputImage() returns null. So
 * there is no way to validate the decoded output frame analytically. The tests in this class
 * however ensures that,
 * <ul>
 *     <li> The number of decoded frames are equal to the number of input frames.</li>
 *     <li> The output timestamp list is same as the input timestamp list.</li>
 *     <li> The timestamp information obtained is consistent with results seen in byte buffer
 *     mode.</li>
 * </ul>
 * <p>
 * The test verifies all the above needs by running mediacodec in both sync and async mode.
 */
@RunWith(Parameterized.class)
public class CodecDecoderSurfaceTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderSurfaceTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final String mReconfigFile;
    private final SupportClass mSupportRequirements;

    static {
        System.loadLibrary("ctsmediav2codecdecsurface_jni");
    }

    public CodecDecoderSurfaceTest(String decoder, String mediaType, String testFile,
            String reconfigFile, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mReconfigFile = MEDIA_DIR + reconfigFile;
        mSupportRequirements = supportRequirements;
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
    }

    private void decodeAndSavePts(String file, String decoder, long pts, int mode, int frameLimit)
            throws IOException, InterruptedException {
        Surface sf = mSurface;
        mSurface = null; // for reference, decode in non-surface mode
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(decoder);
        MediaFormat format = setUpSource(file);
        configureCodec(format, false, true, false);
        mCodec.start();
        mExtractor.seekTo(pts, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        endCodecSession(mCodec);
        mCodec.release();
        mExtractor.release();
        mSurface = sf; // restore surface
    }

    @Rule
    public ActivityScenarioRule<CodecTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecTestActivity.class);

    @Before
    public void setUp() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        if (IS_Q) {
            Log.i(LOG_TAG, "Android 10: skip checkFormatSupport() for format " + format);
        } else {
            ArrayList<MediaFormat> formatList = new ArrayList<>();
            formatList.add(format);
            checkFormatSupport(mCodecName, mMediaType, false, formatList, null,
                    mSupportRequirements);
        }
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        setUpSurface(mActivity);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, test file, reconfig test file, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4",
                        "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4",
                        "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts",
                        "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4",
                        "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_360x640_768kbps_30fps_avc.mp4",
                        "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_160x1024_1500kbps_30fps_avc.mp4",
                        "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x120_1500kbps_30fps_avc.mp4",
                        "bbb_340x280_768kbps_30fps_avc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4",
                        "bbb_340x280_768kbps_30fps_hevc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                        "bbb_176x144_192kbps_15fps_mpeg4.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp",
                        "bbb_176x144_192kbps_10fps_h263.3gp", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm",
                        "bbb_520x390_1mbps_30fps_vp8.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm",
                        "bbb_520x390_1mbps_30fps_vp9.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm",
                        "bbb_520x390_1mbps_30fps_split_non_display_frame_vp9.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4",
                        "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_av1.mp4",
                        "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            "cosmat_520x390_24fps_crf22_avc_10bit.mkv", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            "cosmat_520x390_24fps_crf22_hevc_10bit.mkv", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            "cosmat_520x390_24fps_crf22_vp9_10bit.mkv", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            "cosmat_520x390_24fps_768kbps_av1_10bit.mkv", CODEC_ALL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            "bbb_520x390_1mbps_30fps_hevc.mp4", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            "bbb_520x390_1mbps_30fps_vp9.webm", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                            "bbb_340x280_768kbps_30fps_avc.mp4", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                            "bbb_340x280_768kbps_30fps_hevc.mp4", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                            "bbb_340x280_768kbps_30fps_vp9.webm", CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                            "bbb_340x280_768kbps_30fps_av1.mp4", CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    /**
     * Checks if the component under test can decode the test file to surface. The test runs
     * mediacodec in both synchronous and asynchronous mode. It expects consistent output
     * timestamp list in all runs and this list to be identical to the reference list. The
     * reference list is obtained from the same decoder running in byte buffer mode
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeToSurface() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        {
            decodeAndSavePts(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            MediaFormat format = setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mOutputBuff = test;
            mActivity.setScreenParams(getWidth(format), getHeight(format), true);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mExtractor.seekTo(pts, mode);
                configureCodec(format, isAsync, true, false);
                mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    /**
     * Checks component and framework behaviour to flush API when the codec is operating in
     * surface mode.
     * <p>
     * While the component is decoding the test clip to surface, mediacodec flush() is called.
     * The flush API is called at various points :-
     * <ul>
     *     <li>In running state but before queueing any input (might have to resubmit csd as they
     *     may not have been processed).</li>
     *     <li>In running state, after queueing 1 frame.</li>
     *     <li>In running state, after queueing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * <p>
     * In all situations (pre-flush or post-flush), the test expects the output timestamps to be
     * strictly increasing. The flush call makes the output received non-deterministic even for a
     * given input. Hence, besides timestamp checks, no additional validation is done for outputs
     * received before flush. Post flush, the decode begins from a sync frame. So the test
     * expects consistent output and this needs to be identical to the reference. The reference
     * is obtained from the same decoder running in byte buffer mode.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     */
    @ApiTest(apis = {"android.media.MediaCodec#flush"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mCsdBuffers.clear();
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
            } else break;
        }
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        {
            decodeAndSavePts(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            mOutputBuff = test;
            setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mActivity.setScreenParams(getWidth(format), getHeight(format), false);
            for (boolean isAsync : boolStates) {
                if (isAsync) continue;  // TODO(b/147576107)
                mExtractor.seekTo(0, mode);
                configureCodec(format, isAsync, true, false);
                mCodec.start();

                /* test flush in running state before queuing input */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                doWork(1);
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                mExtractor.seekTo(0, mode);
                test.reset();
                doWork(23);
                if (!test.isPtsStrictlyIncreasing(mPrevOutputPts)) {
                    fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
                }

                /* test flush in running state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }

                /* test flush in eos state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    /**
     * Checks component and framework behaviour for resolution change in surface mode. The
     * resolution change is not seamless (AdaptivePlayback) but done via reconfigure.
     * <p>
     * The reconfiguring of media codec component happens at various points :-
     * <ul>
     *     <li>After initial configuration (stopped state).</li>
     *     <li>In running state, before queueing any input.</li>
     *     <li>In running state, after queuing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * In eos state,
     * <ul>
     *     <li>reconfigure with same clip.</li>
     *     <li>reconfigure with different clip (different resolution).</li>
     * </ul>
     * <p>
     * In all situations (pre-reconfigure or post-reconfigure), the test expects the output
     * timestamps to be strictly increasing. The reconfigure call makes the output received
     * non-deterministic even for a given input. Hence, besides timestamp checks, no additional
     * validation is done for outputs received before reconfigure. Post reconfigure, the decode
     * begins from a sync frame. So the test expects consistent output and this needs to be
     * identical to the reference. The reference is obtained from the same decoder running in
     * byte buffer mode.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     * <p>
     * During reconfiguration, the mode of operation is toggled. That is, if first configure
     * operates the codec in sync mode, then next configure operates the codec in async mode and
     * so on.
     */
    @ApiTest(apis = "android.media.MediaCodec#configure")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        Assume.assumeTrue("Test needs Android 11", IS_AT_LEAST_R);

        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        MediaFormat newFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(newFormat);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, mSupportRequirements);
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        {
            decodeAndSavePts(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            decodeAndSavePts(mReconfigFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager configRef = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            OutputManager configTest = new OutputManager(configRef.getSharedErrorLogs());
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mActivity.setScreenParams(getWidth(format), getHeight(format), false);
            for (boolean isAsync : boolStates) {
                mOutputBuff = test;
                setUpSource(mTestFile);
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                configureCodec(format, isAsync, true, false);

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                doWork(23);

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, false);
                mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }
                mExtractor.release();

                /* test reconfigure codec for new file */
                mOutputBuff = configTest;
                setUpSource(mReconfigFile);
                mActivity.setScreenParams(getWidth(newFormat), getHeight(newFormat), true);
                reConfigureCodec(newFormat, isAsync, false, false);
                mCodec.start();
                configTest.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? configRef.equalsDequeuedOutput(configTest) :
                        configRef.equals(configTest))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + configTest.getErrMsg());
                }
                mExtractor.release();
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSimpleDecode(String decoder, Surface surface, String mediaType,
            String testFile, String refFile, int colorFormat, float rmsError, long checksum,
            StringBuilder retMsg);

    /**
     * Tests is similar to {@link #testSimpleDecodeToSurface()} but uses ndk api
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeToSurfaceNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mActivity.setScreenParams(getWidth(format), getHeight(format), false);
        boolean isPass = nativeTestSimpleDecode(mCodecName, mSurface, mMediaType, mTestFile,
                mReconfigFile, format.getInteger(MediaFormat.KEY_COLOR_FORMAT), -1.0f, 0L,
                mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    private native boolean nativeTestFlush(String decoder, Surface surface, String mediaType,
            String testFile, int colorFormat, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testFlush()} but uses ndk api
     */
    @ApiTest(apis = {"android.media.MediaCodec#flush"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlushNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mActivity.setScreenParams(getWidth(format), getHeight(format), true);
        boolean isPass = nativeTestFlush(mCodecName, mSurface, mMediaType, mTestFile,
                format.getInteger(MediaFormat.KEY_COLOR_FORMAT), mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }
}
