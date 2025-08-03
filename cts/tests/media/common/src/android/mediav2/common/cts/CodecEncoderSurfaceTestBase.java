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

package android.mediav2.common.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.CodecEncoderTestBase.getMuxerFormatForMediaType;
import static android.mediav2.common.cts.CodecTestBase.hasSupportForColorFormat;
import static android.mediav2.common.cts.CodecTestBase.isHardwareAcceleratedCodec;
import static android.mediav2.common.cts.CodecTestBase.isSoftwareCodec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 * Wrapper class for trying and testing encoder components in surface mode.
 */
public class CodecEncoderSurfaceTestBase {
    private static final String LOG_TAG = CodecEncoderSurfaceTestBase.class.getSimpleName();
    private static final boolean ENABLE_LOGS = false;

    protected final String mEncoderName;
    protected final String mEncMediaType;
    protected final String mDecoderName;
    protected final String mTestFileMediaType;
    protected final String mTestFile;
    protected final EncoderConfigParams mEncCfgParams;
    protected final int mDecColorFormat;
    protected final boolean mIsOutputToneMapped;
    protected final boolean mUsePersistentSurface;
    protected final String mTestArgs;

    protected MediaExtractor mExtractor;
    protected MediaCodec mEncoder;
    protected MediaFormat mEncoderFormat;
    protected final CodecAsyncHandler mAsyncHandleEncoder = new CodecAsyncHandler();
    protected MediaCodec mDecoder;
    protected MediaFormat mDecoderFormat;
    protected final CodecAsyncHandler mAsyncHandleDecoder = new CodecAsyncHandler();
    protected boolean mIsCodecInAsyncMode;
    protected boolean mSignalEOSWithLastFrame;
    protected boolean mSawDecInputEOS;
    protected boolean mSawDecOutputEOS;
    protected boolean mSawEncOutputEOS;
    protected int mDecInputCount;
    protected int mDecOutputCount;
    protected int mEncOutputCount;
    protected int mLatency;
    protected boolean mReviseLatency;

    protected final StringBuilder mTestConfig = new StringBuilder();
    protected final StringBuilder mTestEnv = new StringBuilder();

    protected boolean mSaveToMem;
    protected OutputManager mOutputBuff;

    protected Surface mSurface;

    protected MediaMuxer mMuxer;
    protected int mTrackID = -1;

    public CodecEncoderSurfaceTestBase(String encoder, String mediaType, String decoder,
            String testFileMediaType, String testFile, EncoderConfigParams encCfgParams,
            int decColorFormat, boolean isOutputToneMapped, boolean usePersistentSurface,
            String allTestParams) {
        mEncoderName = encoder;
        mEncMediaType = mediaType;
        mDecoderName = decoder;
        mTestFileMediaType = testFileMediaType;
        mTestFile = testFile;
        mEncCfgParams = encCfgParams;
        mDecColorFormat = decColorFormat;
        mIsOutputToneMapped = isOutputToneMapped;
        mUsePersistentSurface = usePersistentSurface;
        mTestArgs = allTestParams;
        mLatency = mEncCfgParams.mMaxBFrames;
        mReviseLatency = false;
    }

    @Rule
    public TestName mTestName = new TestName();

    @Before
    public void setUpCodecEncoderSurfaceTestBase() throws IOException, CloneNotSupportedException {
        mTestConfig.setLength(0);
        mTestConfig.append("\n##################       Test Details        ####################\n");
        mTestConfig.append("Test Name :- ").append(mTestName.getMethodName()).append("\n");
        mTestConfig.append("Test Parameters :- ").append(mTestArgs).append("\n");
        if (mEncoderName.startsWith(CodecTestBase.INVALID_CODEC) || mDecoderName.startsWith(
                CodecTestBase.INVALID_CODEC)) {
            fail("no valid component available for current test. \n" + mTestConfig);
        }
        mDecoderFormat = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> decoderFormatList = new ArrayList<>();
        decoderFormatList.add(mDecoderFormat);
        Assume.assumeTrue("Decoder: " + mDecoderName + " doesn't support format: " + mDecoderFormat,
                CodecTestBase.areFormatsSupported(mDecoderName, mTestFileMediaType,
                        decoderFormatList));
        if (CodecTestBase.doesAnyFormatHaveHDRProfile(mTestFileMediaType, decoderFormatList)
                || mTestFile.contains("10bit")) {
            // Check if encoder is capable of supporting HDR profiles.
            // Previous check doesn't verify this as profile isn't set in the format
            Assume.assumeTrue(mEncoderName + " doesn't support HDR encoding",
                    CodecTestBase.doesCodecSupportHDRProfile(mEncoderName, mEncMediaType));
        }

        if (mDecColorFormat == COLOR_FormatSurface) {
            // TODO(b/253492870) Remove the following assumption check once this is supported
            Assume.assumeFalse(mDecoderName + "is hardware accelerated and " + mEncoderName
                            + "is software only.",
                    isHardwareAcceleratedCodec(mDecoderName) && isSoftwareCodec(mEncoderName));
        } else {
            // findDecoderForFormat() ignores color-format and decoder returned may not be
            // supporting the color format set in mDecoderFormat. Following check will
            // skip the test if decoder doesn't support the color format that is set.
            boolean decoderSupportsColorFormat =
                    hasSupportForColorFormat(mDecoderName, mTestFileMediaType, mDecColorFormat);
            if (mDecColorFormat == COLOR_FormatYUVP010) {
                assumeTrue(mDecoderName + " doesn't support P010 output.",
                        decoderSupportsColorFormat);
            } else {
                assertTrue(mDecoderName + " doesn't support 420p 888 flexible output.",
                        decoderSupportsColorFormat);
            }
        }
        EncoderConfigParams.Builder foreman = mEncCfgParams.getBuilder()
                .setWidth(mDecoderFormat.getInteger(MediaFormat.KEY_WIDTH))
                .setHeight(mDecoderFormat.getInteger(MediaFormat.KEY_HEIGHT));
        mEncoderFormat = foreman.build().getFormat();
    }

    @After
    public void tearDownCodecEncoderSurfaceTestBase() {
        if (mDecoder != null) {
            mDecoder.release();
            mDecoder = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mEncoder != null) {
            mEncoder.release();
            mEncoder = null;
        }
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
    }

    protected boolean hasSeenError() {
        return mAsyncHandleDecoder.hasSeenError() || mAsyncHandleEncoder.hasSeenError();
    }

    @TargetApi(33)
    protected MediaFormat setUpSource(String srcFile) throws IOException {
        Preconditions.assertTestFileExists(srcFile);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            if (mediaType.equals(mTestFileMediaType)) {
                mExtractor.selectTrack(trackID);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mDecColorFormat);
                if (mIsOutputToneMapped) {
                    format.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                            MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                }
                return format;
            }
        }
        mExtractor.release();
        fail("No video track found in file: " + srcFile + ". \n" + mTestConfig + mTestEnv);
        return null;
    }

    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandleDecoder.resetContext();
        mAsyncHandleEncoder.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mSawDecInputEOS = false;
        mSawDecOutputEOS = false;
        mSawEncOutputEOS = false;
        mDecInputCount = 0;
        mDecOutputCount = 0;
        mEncOutputCount = 0;
    }

    protected void configureCodec(MediaFormat decFormat, MediaFormat encFormat, boolean isAsync,
            boolean signalEOSWithLastFrame) {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandleEncoder.setCallBack(mEncoder, isAsync);
        mEncoder.configure(encFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        if (mEncoder.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
            mReviseLatency = true;
            mLatency = mEncoder.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
        }
        if (mUsePersistentSurface) {
            mSurface = MediaCodec.createPersistentInputSurface();
            mEncoder.setInputSurface(mSurface);
        } else {
            mSurface = mEncoder.createInputSurface();
        }
        assertTrue("Surface is not valid", mSurface.isValid());
        mAsyncHandleDecoder.setCallBack(mDecoder, isAsync);
        mDecoder.configure(decFormat, mSurface, null, 0);
        mTestEnv.setLength(0);
        mTestEnv.append("###################      Test Environment       #####################\n");
        mTestEnv.append(String.format("Encoder under test :- %s \n", mEncoderName));
        mTestEnv.append(String.format("Format under test :- %s \n", encFormat));
        mTestEnv.append(String.format("Encoder is fed with output of :- %s \n", mDecoderName));
        mTestEnv.append(String.format("Format of Decoder Input :- %s", decFormat));
        mTestEnv.append(String.format("Encoder and Decoder are operating in :- %s mode \n",
                (isAsync ? "asynchronous" : "synchronous")));
        mTestEnv.append(String.format("Components received input eos :- %s \n",
                (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer")));
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    protected void enqueueDecoderEOS(int bufferIndex) {
        if (!mSawDecInputEOS) {
            mDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawDecInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    protected void enqueueDecoderInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueDecoderEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mDecoder.getInputBuffer(bufferIndex);
            mExtractor.readSampleData(inputBuffer, 0);
            int size = (int) mExtractor.getSampleSize();
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawDecInputEOS = true;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                        + " flags: " + codecFlags);
            }
            mDecoder.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mDecInputCount++;
            }
        }
    }

    protected void dequeueDecoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawDecOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mDecOutputCount++;
        }
        mDecoder.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    protected void dequeueEncoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "encoder output: id: " + bufferIndex + " flags: " + info.flags
                    + " size: " + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawEncOutputEOS = true;
        }
        if (info.size > 0) {
            ByteBuffer buf = mEncoder.getOutputBuffer(bufferIndex);
            if (mSaveToMem) {
                mOutputBuff.saveToMemory(buf, info);
            }
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(mEncoder.getOutputFormat());
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mEncOutputCount++;
            }
        }
        mEncoder.releaseOutputBuffer(bufferIndex, false);
    }

    protected void tryEncoderOutput(long timeOutUs) throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            if (!hasSeenError() && !mSawEncOutputEOS) {
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
            if (!mSawEncOutputEOS) {
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

    protected void queueEOS() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandleDecoder.hasSeenError() && !mSawDecInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleDecoder.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueDecoderOutput(bufferID, info);
                    } else {
                        enqueueDecoderEOS(element.first);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecInputEOS) {
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                int inputBufferId = mDecoder.dequeueInputBuffer(CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueDecoderEOS(inputBufferId);
                }
            }
        }
        if (mIsCodecInAsyncMode) {
            while (!hasSeenError() && !mSawDecOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> decOp = mAsyncHandleDecoder.getOutput();
                if (decOp != null) dequeueDecoderOutput(decOp.first, decOp.second);
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecOutputEOS) {
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        }
    }

    protected void doWork(int frameLimit) throws InterruptedException {
        int frameCnt = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!hasSeenError() && !mSawDecInputEOS && frameCnt < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleDecoder.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueDecoderOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueDecoderInput(bufferID);
                        frameCnt++;
                    }
                }
                // check decoder EOS
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                // encoder output
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecInputEOS && frameCnt < frameLimit) {
                // decoder input
                int inputBufferId = mDecoder.dequeueInputBuffer(CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueDecoderInput(inputBufferId);
                    frameCnt++;
                }
                // decoder output
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                // check decoder EOS
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                // encoder output
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        }
    }

    protected void waitForAllEncoderOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!hasSeenError() && !mSawEncOutputEOS) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        } else {
            while (!mSawEncOutputEOS) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        }
        validateTestState();
    }

    private void validateTestState() {
        assertFalse("Decoder has encountered error in async mode. \n"
                        + mTestConfig + mTestEnv + mAsyncHandleDecoder.getErrMsg(),
                mAsyncHandleDecoder.hasSeenError());
        assertFalse("Encoder has encountered error in async mode. \n"
                        + mTestConfig + mTestEnv + mAsyncHandleEncoder.getErrMsg(),
                mAsyncHandleEncoder.hasSeenError());
        assertTrue("Decoder has not received any input \n" + mTestConfig + mTestEnv,
                0 != mDecInputCount);
        assertTrue("Decoder has not sent any output \n" + mTestConfig + mTestEnv,
                0 != mDecOutputCount);
        assertTrue("Encoder has not sent any output \n" + mTestConfig + mTestEnv,
                0 != mEncOutputCount);
        assertEquals("Decoder output count is not equal to decoder input count \n"
                + mTestConfig + mTestEnv, mDecInputCount, mDecOutputCount);
        /* TODO(b/153127506) - Currently disabling all encoder output checks */
        /*assertEquals("Encoder output count is not equal to Decoder input count \n"
                + mTestConfig + mTestEnv, mDecInputCount, mEncOutputCount);
        if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList((mEncCfgParams.mMaxBFrames != 0))) {
            fail("Input pts list and Output pts list are not identical \n" + mTestConfig
                    + mTestEnv + mOutputBuff.getErrMsg());
        }*/
        if (mEncCfgParams.mMaxBFrames == 0 && !mOutputBuff.isPtsStrictlyIncreasing(
                Long.MIN_VALUE)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
    }

    protected void validateToneMappedFormat(MediaFormat format, String descriptor) {
        assertEquals("unexpected color transfer in " + descriptor + " after tone mapping",
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                format.getInteger(MediaFormat.KEY_COLOR_TRANSFER, 0));
        assertNotEquals("unexpected color standard in " + descriptor + " after tone mapping",
                MediaFormat.COLOR_STANDARD_BT2020,
                format.getInteger(MediaFormat.KEY_COLOR_STANDARD, 0));

        int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
        int[] profileArray = CodecTestBase.PROFILE_HDR_MAP.get(mEncMediaType);
        assertFalse(descriptor + " must not contain HDR profile after tone mapping",
                IntStream.of(profileArray).anyMatch(x -> x == profile));
    }

    @TargetApi(33)
    protected void encodeToMemory(boolean isAsync, boolean signalEOSWithLastFrame,
            boolean saveToMem, OutputManager outBuff, boolean muxOutput, String outPath)
            throws IOException, InterruptedException {
        mSaveToMem = saveToMem;
        mOutputBuff = outBuff;
        mOutputBuff.reset();
        if (muxOutput) {
            int muxerFormat = getMuxerFormatForMediaType(mEncMediaType);
            mMuxer = new MediaMuxer(outPath, muxerFormat);
        }
        setUpSource(mTestFile);
        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        mEncoder = MediaCodec.createByCodecName(mEncoderName);
        configureCodec(mDecoderFormat, mEncoderFormat, isAsync, signalEOSWithLastFrame);
        if (mIsOutputToneMapped) {
            MediaFormat inpFormat = mDecoder.getInputFormat();
            int transferRequest = inpFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, 0);
            assumeTrue(mDecoderName + " does not support HDR to SDR tone mapping",
                    0 != transferRequest);
        }
        mEncoder.start();
        mDecoder.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllEncoderOutputs();
        if (muxOutput) {
            if (mTrackID != -1) {
                mMuxer.stop();
                mTrackID = -1;
            }
            if (mMuxer != null) {
                mMuxer.release();
                mMuxer = null;
            }
        }
        if (mIsOutputToneMapped) {
            MediaFormat encoderOutputFormat = mEncoder.getOutputFormat();
            MediaFormat decoderOutputFormat = mDecoder.getOutputFormat();
            validateToneMappedFormat(decoderOutputFormat, "decoder output format");
            validateToneMappedFormat(encoderOutputFormat, "encoder output format");
            if (outPath != null) {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(outPath);
                MediaFormat extractorFormat = extractor.getTrackFormat(0);
                extractor.release();
                validateToneMappedFormat(extractorFormat, "extractor format");
            }
        }
        mDecoder.reset();
        mEncoder.reset();
        mSurface.release();
        mSurface = null;
        mDecoder.release();
        mEncoder.release();
        mExtractor.release();
        mSaveToMem = false;
    }
}
