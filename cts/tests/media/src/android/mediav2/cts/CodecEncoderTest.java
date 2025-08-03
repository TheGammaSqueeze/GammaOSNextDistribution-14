/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test mediacodec api, encoders and their interactions in bytebuffer mode.
 * <p>
 * The test feeds raw input data (audio/video) to the component and receives compressed bitstream
 * from the component.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li> For audio components, the test expects the output timestamps to be strictly
 *     increasing.</li>
 *     <li>For video components the test expects the output frame count to be identical to input
 *     frame count and the output timestamp list to be identical to input timestamp list.</li>
 *     <li>As encoders are expected to give consistent output for a given input and configuration
 *     parameters, the test checks for consistency across runs. For now, this attribute is not
 *     strictly enforced in this test.</li>
 * </ul>
 * <p>
 * The test does not validate the integrity of the encoder output. That is done by
 * CodecEncoderValidationTest. This test checks only the framework <-> plugin <-> encoder
 * interactions.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class CodecEncoderTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = CodecEncoderTest.class.getSimpleName();
    private static final ArrayList<String> ABR_MEDIATYPE_LIST = new ArrayList<>();

    private int mNumSyncFramesReceived;
    private final ArrayList<Integer> mSyncFramesPos = new ArrayList<>();

    static {
        System.loadLibrary("ctsmediav2codecenc_jni");

        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_VP8);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_AV1);
    }

    public CodecEncoderTest(String encoder, String mediaType, EncoderConfigParams[] cfgParams,
            String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumSyncFramesReceived = 0;
        mSyncFramesPos.clear();
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            mNumSyncFramesReceived += 1;
            mSyncFramesPos.add(mOutputCount);
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private void forceSyncFrame() {
        final Bundle syncFrame = new Bundle();
        syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting key frame");
        }
        mCodec.setParameters(syncFrame);
    }

    private void updateBitrate(int bitrate) {
        final Bundle bitrateUpdate = new Bundle();
        bitrateUpdate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting bitrate to be changed to " + bitrate);
        }
        mCodec.setParameters(bitrateUpdate);
    }

    private static EncoderConfigParams getVideoEncoderCfgParam(String mediaType, int width,
            int height, int bitRate, int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType).setWidth(width).setHeight(height)
                .setMaxBFrames(maxBFrames).setBitRate(bitRate).build();
    }

    private static EncoderConfigParams getAudioEncoderCfgParam(String mediaType, int sampleRate,
            int channelCount, int qualityPreset) {
        EncoderConfigParams.Builder foreman =
                new EncoderConfigParams.Builder(mediaType).setSampleRate(sampleRate)
                        .setChannelCount(channelCount);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            foreman = foreman.setCompressionLevel(qualityPreset);
        } else {
            foreman = foreman.setBitRate(qualityPreset);
        }
        return foreman.build();
    }

    private static EncoderConfigParams[] getAacCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AAC, 8000, 1, 128000);
        params[1] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2, 128000);
        return params;
    }

    private static EncoderConfigParams[] getOpusCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_OPUS, 16000, 1, 64000);
        params[1] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_OPUS, 16000, 1, 128000);
        return params;
    }

    private static EncoderConfigParams[] getAmrnbCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AMR_NB, 8000, 1, 4750);
        params[1] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AMR_NB, 8000, 1, 12200);
        return params;
    }

    private static EncoderConfigParams[] getAmrwbCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AMR_WB, 16000, 1, 6600);
        params[1] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_AMR_WB, 16000, 1, 23850);
        return params;
    }

    private static EncoderConfigParams[] getFlacCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_FLAC, 8000, 1, 6);
        params[1] = getAudioEncoderCfgParam(MediaFormat.MIMETYPE_AUDIO_FLAC, 48000, 2, 5);
        return params;
    }

    private static EncoderConfigParams[] getH263CfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_H263, 176, 144, 32000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_H263, 176, 144, 64000, 0);
        return params;
    }

    private static EncoderConfigParams[] getMpeg4CfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_MPEG4, 176, 144, 32000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_MPEG4, 176, 144, 64000, 0);
        return params;
    }

    private static EncoderConfigParams[] getAvcCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AVC, 176, 144, 512000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AVC, 352, 288, 512000, 0);
        return params;
    }

    private static EncoderConfigParams[] getHevcCfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_HEVC, 176, 144, 512000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_HEVC, 352, 288, 512000, 0);
        return params;
    }

    private static EncoderConfigParams[] getAvcCfgParamsWithBFrames() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240, 512000, 2);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 360, 768000, 2);
        return params;
    }

    private static EncoderConfigParams[] getHevcCfgParamsWithBFrames() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_HEVC, 320, 240, 384000, 2);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_HEVC, 480, 360, 512000, 2);
        return params;
    }

    private static EncoderConfigParams[] getVp8CfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_VP8, 176, 144, 512000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_VP8, 352, 288, 512000, 0);
        return params;
    }

    private static EncoderConfigParams[] getVp9CfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_VP9, 176, 144, 512000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_VP9, 352, 288, 512000, 0);
        return params;
    }

    private static EncoderConfigParams[] getAv1CfgParams() {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        params[0] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AV1, 176, 144, 512000, 0);
        params[1] = getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_AV1, 352, 288, 512000, 0);
        return params;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, cfg params
                {MediaFormat.MIMETYPE_AUDIO_AAC, getAacCfgParams()},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, getOpusCfgParams()},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, getAmrnbCfgParams()},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, getAmrwbCfgParams()},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, getFlacCfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_H263, getH263CfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, getMpeg4CfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_AVC, getAvcCfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_AVC, getAvcCfgParamsWithBFrames()},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, getHevcCfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, getHevcCfgParamsWithBFrames()},
                {MediaFormat.MIMETYPE_VIDEO_VP8, getVp8CfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_VP9, getVp9CfgParams()},
                {MediaFormat.MIMETYPE_VIDEO_AV1, getAv1CfgParams()},
        }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    @Before
    public void setUp() throws IOException {
        mActiveEncCfg = mEncCfgParams[0];
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
    }

    /**
     * Checks if the component under test can encode the test file correctly. The encoding
     * happens in synchronous, asynchronous mode, eos flag signalled with last raw frame and
     * eos flag signalled separately after sending all raw frames. It expects consistent
     * output in all these runs. That is, the ByteBuffer info and output timestamp list has to be
     * same in all the runs. Further for audio, the output timestamp has to be strictly
     * increasing. For video the output timestamp list has to be same as input timestamp list. As
     * encoders are expected to give consistent output for a given input and configuration
     * parameters, the test checks for consistency across runs. Although the test collects the
     * output in a byte buffer, no analysis is done that checks the integrity of the bitstream.
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.1", "5.2/C-1-1"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            assertEquals("codec name act/got: " + mCodec.getName() + '/' + mCodecName,
                    mCodec.getName(), mCodecName);
            assertTrue("error! codec canonical name is null or empty",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            mSaveToMem = false; /* TODO(b/149027258) */
            MediaFormat format = mActiveEncCfg.getFormat();
            {
                int loopCounter = 0;
                for (boolean eosType : boolStates) {
                    for (boolean isAsync : boolStates) {
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mInfoList.clear();
                        validateMetrics(mCodecName);
                        configureCodec(format, isAsync, eosType, true);
                        mCodec.start();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(mCodecName, format);
                        /* TODO(b/147348711) */
                        if (false) mCodec.stop();
                        else mCodec.reset();
                        if (loopCounter != 0 && !ref.equals(test)) {
                            fail("Encoder output is not consistent across runs \n" + mTestConfig
                                    + mTestEnv + test.getErrMsg());
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSimpleEncode(String encoder, String file, String mediaType,
            String cfgParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testSimpleEncode()} but uses ndk api
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.1", "5.1.7/C-1-3"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420SemiPlanar",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Planar",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mActiveEncCfg.getFormat();
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        }
        boolean isPass = nativeTestSimpleEncode(mCodecName, mActiveRawRes.mFileName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Checks component and framework behaviour on parameter (resolution, samplerate/channel
     * count, ...) change. The reconfiguring of media codec component happens at various points.
     * <ul>
     *     <li>After initial configuration (stopped state).</li>
     *     <li>In running state, before queueing any input.</li>
     *     <li>In running state, after queueing n frames.</li>
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
     * validation is done for outputs received before reconfigure. Post reconfigure, the encode
     * begins from a sync frame. So the test expects consistent output and this needs to be
     * identical to the reference.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     * <p>
     * During reconfiguration, the mode of operation is toggled. That is, if first configure
     * operates the codec in sync mode, then next configure operates the codec in async mode and
     * so on.
     */
    @Ignore("TODO(b/148523403)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {

        boolean[] boolStates = {true, false};
        {
            boolean saveToMem = false; /* TODO(b/149027258) */
            OutputManager configRef = null;
            OutputManager configTest = null;
            if (mEncCfgParams.length > 1) {
                encodeToMemory(mCodecName, mEncCfgParams[1], mActiveRawRes, Integer.MAX_VALUE,
                        saveToMem, mMuxOutput);
                configRef = mOutputBuff;
                configTest = new OutputManager(configRef.getSharedErrorLogs());
            }
            encodeToMemory(mCodecName, mEncCfgParams[0], mActiveRawRes, Integer.MAX_VALUE,
                    saveToMem, mMuxOutput);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            MediaFormat format = mEncCfgParams[0].getFormat();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff = test;
                configureCodec(format, isAsync, true, true);

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                doWork(23);

                if (mOutputCount != 0) validateMetrics(mCodecName, format);

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, true);
                mCodec.start();
                mSaveToMem = saveToMem;
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }

                /* test reconfigure codec for new format */
                if (mEncCfgParams.length > 1) {
                    mOutputBuff = configTest;
                    reConfigureCodec(mEncCfgParams[1].getFormat(), isAsync, false, true);
                    mCodec.start();
                    configTest.reset();
                    doWork(Integer.MAX_VALUE);
                    queueEOS();
                    waitForAllOutputs();
                    /* TODO(b/147348711) */
                    if (false) mCodec.stop();
                    else mCodec.reset();
                    if (!configRef.equals(configTest)) {
                        fail("Encoder output is not consistent across runs \n" + mTestConfig
                                + mTestEnv + configTest.getErrMsg());
                    }
                }
                mSaveToMem = false;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestReconfigure(String encoder, String file, String mediaType,
            String cfgParams, String cfgReconfigParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testReconfigure()} but uses ndk api
     */
    @Ignore("TODO(b/147348711, b/149981033)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigureNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        MediaFormat reconfigFormat = mEncCfgParams.length > 1 ? mEncCfgParams[1].getFormat() : null;
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mEncCfgParams[0].getBuilder().setColorFormat(colorFormat).build().getFormat();
            if (mEncCfgParams.length > 1) {
                reconfigFormat = mEncCfgParams[1].getBuilder().setColorFormat(colorFormat).build()
                        .getFormat();
            }
        }
        boolean isPass = nativeTestReconfigure(mCodecName, mActiveRawRes.mFileName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format), reconfigFormat == null ? null :
                        EncoderConfigParams.serializeMediaFormat(reconfigFormat),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test encoder for EOS only input. As BUFFER_FLAG_END_OF_STREAM is queued with an input buffer
     * of size 0, during dequeue the test expects to receive BUFFER_FLAG_END_OF_STREAM with an
     * output buffer of size 0. No input is given, so no output shall be received.
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = false; /* TODO(b/149027258) */
            int loopCounter = 0;
            MediaFormat format = mActiveEncCfg.getFormat();
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mInfoList.clear();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (loopCounter != 0 && !ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }
                loopCounter++;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestOnlyEos(String encoder, String mediaType, String cfgParams,
            String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testOnlyEos()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEosNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mActiveEncCfg.getFormat();
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        }
        boolean isPass = nativeTestOnlyEos(mCodecName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test video encoders for feature "request-sync". Video encoders are expected to give a sync
     * frame upon request. The test requests encoder to provide key frame every 'n' seconds.  The
     * test feeds encoder input for 'm' seconds. At the end, it expects to receive m/n key frames
     * at least. Also it checks if the key frame received is not too far from the point of request.
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrame()
            throws IOException, InterruptedException, CloneNotSupportedException {
        Assume.assumeTrue("Test is applicable only for video encoders", mIsVideo);
        EncoderConfigParams currCfg = mActiveEncCfg.getBuilder().setKeyFrameInterval(500.f).build();
        MediaFormat format = currCfg.getFormat();
        // Maximum allowed key frame interval variation from the target value.
        final int maxKeyframeIntervalVariation = 3;
        final int keyFrameInterval = 2; // force key frame every 2 seconds.
        final int keyFramePos = currCfg.mFrameRate * keyFrameInterval;
        final int numKeyFrameRequests = 7;

        setUpSource(mActiveRawRes.mFileName);
        mOutputBuff = new OutputManager();
        boolean[] boolStates = {true, false};
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                for (int i = 0; i < numKeyFrameRequests; i++) {
                    doWork(keyFramePos);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", keyFramePos, mInputCount));
                    }
                    forceSyncFrame();
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                String msg = String.format("Received only %d key frames for %d key frame "
                        + "requests \n", mNumSyncFramesReceived, numKeyFrameRequests);
                assertTrue(msg + mTestConfig + mTestEnv,
                        mNumSyncFramesReceived >= numKeyFrameRequests);
                for (int i = 0, expPos = 0, index = 0; i < numKeyFrameRequests; i++) {
                    int j = index;
                    for (; j < mSyncFramesPos.size(); j++) {
                        // Check key frame intervals:
                        // key frame position should not be greater than target value + 3
                        // key frame position should not be less than target value - 3
                        if (Math.abs(expPos - mSyncFramesPos.get(j)) <=
                                maxKeyframeIntervalVariation) {
                            index = j;
                            break;
                        }
                    }
                    if (j == mSyncFramesPos.size()) {
                        Log.w(LOG_TAG, "requested key frame at frame index " + expPos +
                                " none found near by");
                    }
                    expPos += keyFramePos;
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSetForceSyncFrame(String encoder, String file,
            String mediaType, String cfgParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testSetForceSyncFrame()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrameNative() throws IOException, CloneNotSupportedException {
        Assume.assumeTrue("Test is applicable only for encoders", mIsVideo);

        int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
        assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                colorFormat != -1);
        MediaFormat format =
                mActiveEncCfg.getBuilder().setColorFormat(colorFormat).setKeyFrameInterval(500.f)
                        .build().getFormat();
        boolean isPass = nativeTestSetForceSyncFrame(mCodecName, mActiveRawRes.mFileName,
                mMediaType, EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test video encoders for feature adaptive bitrate. Video encoders are expected to honor
     * bitrate changes upon request. The test requests encoder to encode at new bitrate every 'n'
     * seconds.  The test feeds encoder input for 'm' seconds. At the end, it expects the output
     * file size to be around {sum of (n * Bi) for i in the range [0, (m/n)]} and Bi is the
     * bitrate chosen for the interval 'n' seconds
     */
    @CddTest(requirements = "5.2/C-2-1")
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRate() throws IOException, InterruptedException {
        Assume.assumeTrue("Skipping AdaptiveBitrate test for " + mMediaType,
                ABR_MEDIATYPE_LIST.contains(mMediaType));
        MediaFormat format = mActiveEncCfg.getFormat();
        final int adaptiveBrInterval = 3; // change br every 3 seconds.
        final int adaptiveBrDurFrm = mActiveEncCfg.mFrameRate * adaptiveBrInterval;
        final int brChangeRequests = 7;
        // TODO(b/251265293) Reduce the allowed deviation after improving the test conditions
        final float maxBitrateDeviation = 60.0f; // allowed bitrate deviation in %

        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        mOutputBuff = new OutputManager();
        mSaveToMem = true;
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int expOutSize = 0;
                int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                for (int i = 0; i < brChangeRequests; i++) {
                    doWork(adaptiveBrDurFrm);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", adaptiveBrDurFrm, mInputCount));
                    }
                    expOutSize += adaptiveBrInterval * bitrate;
                    if ((i & 1) == 1) bitrate *= 2;
                    else bitrate /= 2;
                    updateBitrate(bitrate);
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                /* TODO: validate output br with sliding window constraints Sec 5.2 cdd */
                int outSize = mOutputBuff.getOutStreamSize() * 8;
                float brDev = Math.abs(expOutSize - outSize) * 100.0f / expOutSize;
                if (brDev > maxBitrateDeviation) {
                    fail("Relative Bitrate error is too large " + brDev + "\n" + mTestConfig
                            + mTestEnv);
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestAdaptiveBitRate(String encoder, String file, String mediaType,
            String cfgParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testAdaptiveBitRate()} but uses ndk api
     */
    @CddTest(requirements = "5.2/C-2-1")
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRateNative() throws IOException, CloneNotSupportedException {
        Assume.assumeTrue("Skipping Native AdaptiveBitrate test for " + mMediaType,
                ABR_MEDIATYPE_LIST.contains(mMediaType));
        int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
        assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                colorFormat != -1);
        MediaFormat format =
                mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        boolean isPass = nativeTestAdaptiveBitRate(mCodecName, mActiveRawRes.mFileName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }
}
