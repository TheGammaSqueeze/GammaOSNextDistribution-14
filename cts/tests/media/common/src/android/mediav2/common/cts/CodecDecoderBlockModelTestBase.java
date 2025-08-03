/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.junit.After;

import java.nio.ByteBuffer;

/**
 * Wrapper class for trying and testing mediacodec decoder components in block model mode.
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class CodecDecoderBlockModelTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderBlockModelTestBase.class.getSimpleName();

    protected final LinearBlockWrapper mLinearInputBlock = new LinearBlockWrapper();

    /**
     * Wrapper class for {@link MediaCodec.LinearBlock}
     */
    public static class LinearBlockWrapper {
        private MediaCodec.LinearBlock mBlock;
        private ByteBuffer mBuffer;
        private int mOffset;

        public MediaCodec.LinearBlock getBlock() {
            return mBlock;
        }

        public ByteBuffer getBuffer() {
            return mBuffer;
        }

        public int getBufferCapacity() {
            return mBuffer == null ? 0 : mBuffer.capacity();
        }

        public int getOffset() {
            return mOffset;
        }

        public void setOffset(int size) {
            mOffset = size;
        }

        public void allocateBlock(String codec, int size) {
            recycle();
            mBlock = MediaCodec.LinearBlock.obtain(size, new String[]{codec});
            assumeNotNull("failed to obtain LinearBlock for component " + codec + "\n", mBlock);
            assertTrue("Blocks obtained through LinearBlock.obtain must be mappable" + "\n",
                    mBlock.isMappable());
            mBuffer = mBlock.map();
            mOffset = 0;
        }

        public void recycle() {
            if (mBlock != null) {
                mBlock.recycle();
                mBlock = null;
            }
            mBuffer = null;
            mOffset = 0;
        }
    }

    public CodecDecoderBlockModelTestBase(String decoder, String mediaType, String testFile,
            String allTestParams) {
        super(decoder, mediaType, testFile, allTestParams);
    }

    @After
    public void tearDownCodecDecoderBlockModelTestBase() {
        mLinearInputBlock.recycle();
    }

    @Override
    protected void configureCodec(MediaFormat format, boolean isAsyncUnUsed,
            boolean signalEOSWithLastFrameUnUsed, boolean isEncoder) {
        if (ENABLE_LOGS) {
            if (!isAsyncUnUsed) {
                Log.d(LOG_TAG, "Ignoring synchronous mode of operation request");
            }
            if (!signalEOSWithLastFrameUnUsed) {
                Log.d(LOG_TAG, "Ignoring signal eos separately request");
            }
        }
        configureCodec(format, true, true, isEncoder, MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mLinearInputBlock.recycle();
        super.resetContext(isAsync, signalEOSWithLastFrame);
    }

    @Override
    void enqueueCodecConfig(int bufferIndex) {
        throw new RuntimeException("In block model mode, client MUST NOT submit csd(s) explicitly."
                + " These are to be sent via format during configure");
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        int sampleSize = (int) mExtractor.getSampleSize();
        if (mLinearInputBlock.getOffset() + sampleSize > mLinearInputBlock.getBufferCapacity()) {
            int requestSize = 8192;
            requestSize = Math.max(sampleSize, requestSize);
            mLinearInputBlock.allocateBlock(mCodecName, requestSize);
        }
        long pts = mExtractor.getSampleTime();
        mExtractor.readSampleData(mLinearInputBlock.getBuffer(), mLinearInputBlock.getOffset());
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
            mSawInputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + sampleSize + " pts: " + pts
                    + " flags: " + codecFlags);
        }
        MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
        request.setLinearBlock(mLinearInputBlock.getBlock(), mLinearInputBlock.getOffset(),
                sampleSize);
        request.setPresentationTimeUs(pts);
        request.setFlags(codecFlags);
        request.queue();
        if (sampleSize > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
            mLinearInputBlock.setOffset(mLinearInputBlock.getOffset() + sampleSize);
        }
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        MediaCodec.OutputFrame frame = mCodec.getOutputFrame(bufferIndex);
        long framePts = frame.getPresentationTimeUs();
        long infoPts = info.presentationTimeUs;
        int frameFlags = frame.getFlags();
        int infoFlags = info.flags;
        assertEquals("presentation timestamps from OutputFrame does not match with the value "
                + "obtained from callback: framePts=" + framePts + ", infoPts=" + infoPts + "\n"
                + mTestConfig + mTestEnv, framePts, infoPts);
        assertEquals("Flags from OutputFrame does not match with the value obtained from "
                + "callback: frameFlags=" + frameFlags + ", infoFlags=" + infoFlags + "\n"
                + mTestConfig + mTestEnv, frameFlags, infoFlags);
        if (info.size > 0 && mSaveToMem) {
            flattenBufferInfo(info, mIsAudio);
            mOutputBuff.checksum(mFlatBuffer, mFlatBuffer.limit());
            if (frame.getLinearBlock() != null) {
                ByteBuffer buf = frame.getLinearBlock().map();
                mOutputBuff.checksum(buf, info.size);
                mOutputBuff.saveToMemory(buf, info);
                frame.getLinearBlock().recycle();
            }
        }
        if ((infoFlags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + infoFlags + " size: "
                    + info.size + " timestamp: " + infoPts);
        }
        if (info.size > 0 && (infoFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(infoPts);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }
}
