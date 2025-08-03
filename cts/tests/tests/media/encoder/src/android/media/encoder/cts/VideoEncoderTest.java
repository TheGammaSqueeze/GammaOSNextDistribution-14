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

package android.media.encoder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.cts.CodecUtils;
import android.media.cts.InputSurface;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.OutputSurface;
import android.media.cts.TestArgs;
import android.media.cts.TestUtils;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(Parameterized.class)
public class VideoEncoderTest extends MediaTestBase {
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private static final String TAG = "VideoEncoderTest";
    private static final long FRAME_TIMEOUT_MS = 1000;
    // use larger delay before we get first frame, some encoders may need more time
    private static final long INIT_TIMEOUT_MS = 2000;

    static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final String SOURCE_URL =
            mInpPrefix + "video_480x360_mp4_h264_871kbps_30fps.mp4";

    private final Encoder mEncHandle;
    private final int mWidth;
    private final int mHeight;
    private final boolean mFlexYuv;
    private final TestMode mMode;
    private final boolean DEBUG = false;

    enum TestMode {
        TEST_MODE_SPECIFIC, // test basic encoding for given configuration
        TEST_MODE_DETAILED, // test detailed encoding for given configuration
        TEST_MODE_INTRAREFRESH // test intra refresh
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }


    class VideoStorage {
        private LinkedList<Pair<ByteBuffer, BufferInfo>> mStream;
        private MediaFormat mFormat;
        private int mInputBufferSize;
        // Media buffers(no CSD, no EOS) enqueued.
        private int mMediaBuffersEnqueuedCount;
        // Media buffers decoded.
        private int mMediaBuffersDecodedCount;
        private final AtomicReference<String> errorMsg = new AtomicReference(null);

        public VideoStorage() {
            mStream = new LinkedList<Pair<ByteBuffer, BufferInfo>>();
        }

        public void setFormat(MediaFormat format) {
            mFormat = format;
        }

        public void addBuffer(ByteBuffer buffer, BufferInfo info) {
            ByteBuffer savedBuffer = ByteBuffer.allocate(info.size);
            savedBuffer.put(buffer);
            if (info.size > mInputBufferSize) {
                mInputBufferSize = info.size;
            }
            BufferInfo savedInfo = new BufferInfo();
            savedInfo.set(0, savedBuffer.position(), info.presentationTimeUs, info.flags);
            mStream.addLast(Pair.create(savedBuffer, savedInfo));
            if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ++mMediaBuffersEnqueuedCount;
            }
        }

        private void play(MediaCodec decoder, Surface surface) {
            decoder.reset();
            final Object condition = new Object();
            final Iterator<Pair<ByteBuffer, BufferInfo>> it = mStream.iterator();
            decoder.setCallback(new MediaCodec.Callback() {
                public void onOutputBufferAvailable(MediaCodec codec, int ix, BufferInfo info) {
                    if (info.size > 0) {
                        ++mMediaBuffersDecodedCount;
                    }
                    codec.releaseOutputBuffer(ix, info.size > 0);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        synchronized (condition) {
                            condition.notifyAll();
                        }
                    }
                }
                public void onInputBufferAvailable(MediaCodec codec, int ix) {
                    if (it.hasNext()) {
                        try {
                            Pair<ByteBuffer, BufferInfo> el = it.next();
                            el.first.clear();
                            try {
                                codec.getInputBuffer(ix).put(el.first);
                            } catch (java.nio.BufferOverflowException e) {
                                String diagnostic = "cannot fit " + el.first.limit()
                                        + "-byte encoded buffer into "
                                        + codec.getInputBuffer(ix).remaining()
                                        + "-byte input buffer of " + codec.getName()
                                        + " configured for " + codec.getInputFormat();
                                Log.e(TAG, diagnostic);
                                errorMsg.set(diagnostic + e);
                                synchronized (condition) {
                                    condition.notifyAll();
                                }
                                // no sense trying to enqueue the failed buffer
                                return;
                            }
                            BufferInfo info = el.second;
                                codec.queueInputBuffer(
                                    ix, 0, info.size, info.presentationTimeUs, info.flags);
                        } catch (Throwable t) {
                          errorMsg.set("exception in onInputBufferAvailable( "
                                       +  codec.getName() + "," + ix
                                       + "): " + t);
                          synchronized (condition) {
                              condition.notifyAll();
                          }
                        }
                    }
                }
                public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                    Log.i(TAG, "got codec exception", e);
                    errorMsg.set("received codec error during decode" + e);
                    synchronized (condition) {
                        condition.notifyAll();
                    }
                }
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                    Log.i(TAG, "got output format " + format);
                }
            });
            mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mInputBufferSize);
            decoder.configure(mFormat, surface, null /* crypto */, 0 /* flags */);
            decoder.start();
            synchronized (condition) {
                try {
                    condition.wait();
                } catch (InterruptedException e) {
                    fail("playback interrupted");
                }
            }
            decoder.stop();
            assertNull(errorMsg.get(), errorMsg.get());
            // All enqueued media data buffers should have got decoded.
            if (mMediaBuffersEnqueuedCount != mMediaBuffersDecodedCount) {
                Log.i(TAG, "mMediaBuffersEnqueuedCount:" + mMediaBuffersEnqueuedCount);
                Log.i(TAG, "mMediaBuffersDecodedCount:" + mMediaBuffersDecodedCount);
                fail("not all enqueued encoded media buffers were decoded");
            }
            mMediaBuffersDecodedCount = 0;
        }

        public boolean playAll(Surface surface) {
            boolean skipped = true;
            if (mFormat == null) {
                Log.i(TAG, "no stream to play");
                return !skipped;
            }
            String mime = mFormat.getString(MediaFormat.KEY_MIME);
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (info.isEncoder() || info.isAlias()) {
                    continue;
                }
                MediaCodec codec = null;
                try {
                    CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (!caps.isFormatSupported(mFormat)) {
                        continue;
                    }
                    codec = MediaCodec.createByCodecName(info.getName());
                } catch (IllegalArgumentException | IOException e) {
                    continue;
                }
                play(codec, surface);
                codec.release();
                skipped = false;
            }
            return !skipped;
        }
    }

    abstract class VideoProcessorBase extends MediaCodec.Callback {
        private static final String TAG = "VideoProcessorBase";

        /*
         * Set this to true to save the encoding results to /data/local/tmp
         * You will need to make /data/local/tmp writeable, run "setenforce 0",
         * and remove files left from a previous run.
         */
        private boolean mSaveResults = false;
        private static final String FILE_DIR = "/data/local/tmp";
        protected int mMuxIndex = -1;

        protected String mProcessorName = "VideoProcessor";
        private MediaExtractor mExtractor;
        protected MediaMuxer mMuxer;
        private ByteBuffer mBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
        protected int mTrackIndex = -1;
        private boolean mSignaledDecoderEOS;

        protected boolean mCompleted;
        protected boolean mEncoderIsActive;
        protected boolean mEncodeOutputFormatUpdated;
        protected final Object mCondition = new Object();
        protected final Object mCodecLock = new Object();

        protected MediaFormat mDecFormat;
        protected MediaCodec mDecoder, mEncoder;

        private VideoStorage mEncodedStream;
        protected int mFrameRate = 0;
        protected int mBitRate = 0;

        protected Function<MediaFormat, Boolean> mUpdateConfigFormatHook;
        protected Function<MediaFormat, Boolean> mCheckOutputFormatHook;

        public void setProcessorName(String name) {
            mProcessorName = name;
        }

        public void setUpdateConfigHook(Function<MediaFormat, Boolean> hook) {
            mUpdateConfigFormatHook = hook;
        }

        public void setCheckOutputFormatHook(Function<MediaFormat, Boolean> hook) {
            mCheckOutputFormatHook = hook;
        }

        protected void open(String path) throws IOException {
            mExtractor = new MediaExtractor();
            if (path.startsWith("android.resource://")) {
                mExtractor.setDataSource(mContext, Uri.parse(path), null);
            } else {
                mExtractor.setDataSource(path);
            }

            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat fmt = mExtractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME).toLowerCase();
                if (mime.startsWith("video/")) {
                    mTrackIndex = i;
                    mDecFormat = fmt;
                    mExtractor.selectTrack(i);
                    break;
                }
            }
            mEncodedStream = new VideoStorage();
            assertTrue("file " + path + " has no video", mTrackIndex >= 0);
        }

        // returns true if encoder supports the size
        protected boolean initCodecsAndConfigureEncoder(
                String videoEncName, String outMime, int width, int height,
                int colorFormat) throws IOException {
            mDecFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String videoDecName = mcl.findDecoderForFormat(mDecFormat);
            Log.i(TAG, "decoder for " + mDecFormat + " is " + videoDecName);
            mDecoder = MediaCodec.createByCodecName(videoDecName);
            mEncoder = MediaCodec.createByCodecName(videoEncName);

            mDecoder.setCallback(this);
            mEncoder.setCallback(this);

            VideoCapabilities encCaps =
                mEncoder.getCodecInfo().getCapabilitiesForType(outMime).getVideoCapabilities();
            if (!encCaps.isSizeSupported(width, height)) {
                Log.i(TAG, videoEncName + " does not support size: " + width + "x" + height);
                return false;
            }

            MediaFormat outFmt = MediaFormat.createVideoFormat(outMime, width, height);
            int bitRate = 0;
            MediaUtils.setMaxEncoderFrameAndBitrates(encCaps, outFmt, 30);
            if (mFrameRate > 0) {
                outFmt.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            }
            if (mBitRate > 0) {
                outFmt.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            }
            outFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            outFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            // Some extra configure before starting the encoder.
            if (mUpdateConfigFormatHook != null) {
                if (!mUpdateConfigFormatHook.apply(outFmt)) {
                    return false;
                }
            }
            mEncoder.configure(outFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(TAG, "encoder input format " + mEncoder.getInputFormat() + " from " + outFmt);
            if (mSaveResults) {
                try {
                    String outFileName =
                            FILE_DIR + mProcessorName + "_" + bitRate + "bps";
                    if (outMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8) ||
                            outMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                        mMuxer = new MediaMuxer(
                                outFileName + ".webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
                    } else {
                        mMuxer = new MediaMuxer(
                                outFileName + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    }
                    // The track can't be added until we have the codec specific data
                } catch (Exception e) {
                    Log.i(TAG, "couldn't create muxer: " + e);
                }
            }
            return true;
        }

        protected void close() {
            synchronized (mCodecLock) {
                if (mDecoder != null) {
                    mDecoder.release();
                    mDecoder = null;
                }
                if (mEncoder != null) {
                    mEncoder.release();
                    mEncoder = null;
                }
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mMuxer != null) {
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
            }
        }

        // returns true if filled buffer
        protected boolean fillDecoderInputBuffer(int ix) {
            if (DEBUG) Log.v(TAG, "decoder received input #" + ix);
            while (!mSignaledDecoderEOS) {
                int track = mExtractor.getSampleTrackIndex();
                if (track >= 0 && track != mTrackIndex) {
                    mExtractor.advance();
                    continue;
                }
                int size = mExtractor.readSampleData(mBuffer, 0);
                if (size < 0) {
                    // queue decoder input EOS
                    if (DEBUG) Log.v(TAG, "queuing decoder EOS");
                    mDecoder.queueInputBuffer(
                            ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mSignaledDecoderEOS = true;
                } else {
                    mBuffer.limit(size);
                    mBuffer.position(0);
                    BufferInfo info = new BufferInfo();
                    info.set(
                            0, mBuffer.limit(), mExtractor.getSampleTime(),
                            mExtractor.getSampleFlags());
                    mDecoder.getInputBuffer(ix).put(mBuffer);
                    if (DEBUG) Log.v(TAG, "queing input #" + ix + " for decoder with timestamp "
                            + info.presentationTimeUs);
                    mDecoder.queueInputBuffer(
                            ix, 0, mBuffer.limit(), info.presentationTimeUs, 0);
                }
                mExtractor.advance();
                return true;
            }
            return false;
        }

        protected void emptyEncoderOutputBuffer(int ix, BufferInfo info) {
            if (DEBUG) Log.v(TAG, "encoder received output #" + ix
                     + " (sz=" + info.size + ", f=" + info.flags
                     + ", ts=" + info.presentationTimeUs + ")");
            ByteBuffer outputBuffer = mEncoder.getOutputBuffer(ix);
            mEncodedStream.addBuffer(outputBuffer, info);

            if (mMuxer != null) {
                // reset position as addBuffer() modifies it
                outputBuffer.position(info.offset);
                outputBuffer.limit(info.offset + info.size);
                mMuxer.writeSampleData(mMuxIndex, outputBuffer, info);
            }

            if (!mCompleted) {
                mEncoder.releaseOutputBuffer(ix, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "encoder received output EOS");
                    synchronized(mCondition) {
                        mCompleted = true;
                        mCondition.notifyAll(); // condition is always satisfied
                    }
                } else {
                    synchronized(mCondition) {
                        mEncoderIsActive = true;
                    }
                }
            }
        }

        @SuppressWarnings("ReturnValueIgnored") // TODO: mCheckOutputFormatHook should be a Consumer
        protected void saveEncoderFormat(MediaFormat format) {
            mEncodedStream.setFormat(format);
            if (mCheckOutputFormatHook != null) {
                mCheckOutputFormatHook.apply(format);
            }
            if (mMuxer != null) {
                if (mMuxIndex < 0) {
                    mMuxIndex = mMuxer.addTrack(format);
                    mMuxer.start();
                }
            }
        }

        public boolean playBack(Surface surface) { return mEncodedStream.playAll(surface); }

        public void setFrameAndBitRates(int frameRate, int bitRate) {
            mFrameRate = frameRate;
            mBitRate = bitRate;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec mediaCodec, int ix) {
            synchronized (mCodecLock) {
                if (mEncoder != null && mDecoder != null) {
                    onInputBufferAvailableLocked(mediaCodec, ix);
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            synchronized (mCodecLock) {
                if (mEncoder != null && mDecoder != null) {
                    onOutputBufferAvailableLocked(mediaCodec, ix, info);
                }
            }
        }

        public abstract boolean processLoop(
                String path, String outMime, String videoEncName,
                int width, int height, boolean optional);
        protected abstract void onInputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix);
        protected abstract void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info);
    }

    class VideoProcessor extends VideoProcessorBase {
        private static final String TAG = "VideoProcessor";
        private boolean mWorkInProgress;
        private boolean mGotDecoderEOS;
        private boolean mSignaledEncoderEOS;

        private LinkedList<Pair<Integer, BufferInfo>> mBuffersToRender =
            new LinkedList<Pair<Integer, BufferInfo>>();
        private LinkedList<Integer> mEncInputBuffers = new LinkedList<Integer>();

        private int mEncInputBufferSize = -1;
        private final AtomicReference<String> errorMsg = new AtomicReference(null);

        @Override
        public boolean processLoop(
                 String path, String outMime, String videoEncName,
                 int width, int height, boolean optional) {
            boolean skipped = true;
            try {
                open(path);
                if (!initCodecsAndConfigureEncoder(
                        videoEncName, outMime, width, height,
                        CodecCapabilities.COLOR_FormatYUV420Flexible)) {
                    assertTrue("could not configure encoder for supported size", optional);
                    return !skipped;
                }
                skipped = false;

                mDecoder.configure(mDecFormat, null /* surface */, null /* crypto */, 0);

                mDecoder.start();
                mEncoder.start();

                // main loop - process GL ops as only main thread has GL context
                while (!mCompleted && errorMsg.get() == null) {
                    Pair<Integer, BufferInfo> decBuffer = null;
                    int encBuffer = -1;
                    synchronized (mCondition) {
                        try {
                            // wait for an encoder input buffer and a decoder output buffer
                            // Use a timeout to avoid stalling the test if it doesn't arrive.
                            if (!haveBuffers() && !mCompleted) {
                                mCondition.wait(mEncodeOutputFormatUpdated ?
                                        FRAME_TIMEOUT_MS : INIT_TIMEOUT_MS);
                            }
                        } catch (InterruptedException ie) {
                            fail("wait interrupted");  // shouldn't happen
                        }
                        if (mCompleted) {
                            break;
                        }
                        if (!haveBuffers()) {
                            if (mEncoderIsActive) {
                                mEncoderIsActive = false;
                                Log.d(TAG, "No more input but still getting output from encoder.");
                                continue;
                            }
                            fail("timed out after " + mBuffersToRender.size()
                                    + " decoder output and " + mEncInputBuffers.size()
                                    + " encoder input buffers");
                        }

                        if (DEBUG) Log.v(TAG, "got image");
                        decBuffer = mBuffersToRender.removeFirst();
                        encBuffer = mEncInputBuffers.removeFirst();
                        if (isEOSOnlyBuffer(decBuffer)) {
                            queueEncoderEOS(decBuffer, encBuffer);
                            continue;
                        }
                        mWorkInProgress = true;
                    }

                    if (mWorkInProgress) {
                        renderDecodedBuffer(decBuffer, encBuffer);
                        synchronized(mCondition) {
                            mWorkInProgress = false;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                fail("received exception " + e);
            } finally {
                close();
            }
            assertNull(errorMsg.get(), errorMsg.get());
            return !skipped;
        }

        @Override
        public void onInputBufferAvailableLocked(MediaCodec mediaCodec, int ix) {
            if (mediaCodec == mDecoder) {
                // fill input buffer from extractor
                fillDecoderInputBuffer(ix);
            } else if (mediaCodec == mEncoder) {
                synchronized(mCondition) {
                    mEncInputBuffers.addLast(ix);
                    tryToPropagateEOS();
                    if (haveBuffers()) {
                        mCondition.notifyAll();
                    }
                }
            } else {
                fail("received input buffer on " + mediaCodec.getName());
            }
        }

        @Override
        public void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            if (mediaCodec == mDecoder) {
                if (DEBUG) Log.v(TAG, "decoder received output #" + ix
                         + " (sz=" + info.size + ", f=" + info.flags
                         + ", ts=" + info.presentationTimeUs + ")");
                // render output buffer from decoder
                if (!mGotDecoderEOS) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    // can release empty buffers now
                    if (info.size == 0) {
                        mDecoder.releaseOutputBuffer(ix, false /* render */);
                        ix = -1; // fake index used by render to not render
                    }
                    synchronized(mCondition) {
                        if (ix < 0 && eos && mBuffersToRender.size() > 0) {
                            // move lone EOS flag to last buffer to be rendered
                            mBuffersToRender.peekLast().second.flags |=
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        } else if (ix >= 0 || eos) {
                            mBuffersToRender.addLast(Pair.create(ix, info));
                        }
                        if (eos) {
                            tryToPropagateEOS();
                            mGotDecoderEOS = true;
                        }
                        if (haveBuffers()) {
                            mCondition.notifyAll();
                        }
                    }
                }
            } else if (mediaCodec == mEncoder) {
                emptyEncoderOutputBuffer(ix, info);
            } else {
                fail("received output buffer on " + mediaCodec.getName());
            }
        }

        private void renderDecodedBuffer(Pair<Integer, BufferInfo> decBuffer, int encBuffer) {
            // process heavyweight actions under instance lock
            Image encImage = mEncoder.getInputImage(encBuffer);
            Image decImage = mDecoder.getOutputImage(decBuffer.first);
            assertNotNull("could not get encoder image for " + mEncoder.getInputFormat(), encImage);
            assertNotNull("could not get decoder image for " + mDecoder.getInputFormat(), decImage);
            assertEquals("incorrect decoder format",decImage.getFormat(), ImageFormat.YUV_420_888);
            assertEquals("incorrect encoder format", encImage.getFormat(), ImageFormat.YUV_420_888);

            CodecUtils.copyFlexYUVImage(encImage, decImage);

            // TRICKY: need this for queueBuffer
            if (mEncInputBufferSize < 0) {
                mEncInputBufferSize = mEncoder.getInputBuffer(encBuffer).capacity();
            }
            Log.d(TAG, "queuing input #" + encBuffer + " for encoder (sz="
                    + mEncInputBufferSize + ", f=" + decBuffer.second.flags
                    + ", ts=" + decBuffer.second.presentationTimeUs + ")");
            mEncoder.queueInputBuffer(
                    encBuffer, 0, mEncInputBufferSize, decBuffer.second.presentationTimeUs,
                    decBuffer.second.flags);
            if ((decBuffer.second.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSignaledEncoderEOS = true;
            }
            mDecoder.releaseOutputBuffer(decBuffer.first, false /* render */);
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            String codecName = null;
            try {
                codecName = mediaCodec.getName();
            } catch (Exception ex) {
                codecName = "(error getting codec name)";
            }
            errorMsg.set("received error on " + codecName + ": " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.i(TAG, mediaCodec.getName() + " got new output format " + mediaFormat);
            if (mediaCodec == mEncoder) {
                mEncodeOutputFormatUpdated = true;
                saveEncoderFormat(mediaFormat);
            }
        }

        // next methods are synchronized on mCondition
        private boolean haveBuffers() {
            return mEncInputBuffers.size() > 0 && mBuffersToRender.size() > 0
                    && !mSignaledEncoderEOS;
        }

        private boolean isEOSOnlyBuffer(Pair<Integer, BufferInfo> decBuffer) {
            return decBuffer.first < 0 || decBuffer.second.size == 0;
        }

        protected void tryToPropagateEOS() {
            if (!mWorkInProgress && haveBuffers() && isEOSOnlyBuffer(mBuffersToRender.getFirst())) {
                Pair<Integer, BufferInfo> decBuffer = mBuffersToRender.removeFirst();
                int encBuffer = mEncInputBuffers.removeFirst();
                queueEncoderEOS(decBuffer, encBuffer);
            }
        }

        void queueEncoderEOS(Pair<Integer, BufferInfo> decBuffer, int encBuffer) {
            Log.d(TAG, "signaling encoder EOS");
            mEncoder.queueInputBuffer(encBuffer, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSignaledEncoderEOS = true;
            if (decBuffer.first >= 0) {
                mDecoder.releaseOutputBuffer(decBuffer.first, false /* render */);
            }
        }
    }


    class SurfaceVideoProcessor extends VideoProcessorBase
            implements SurfaceTexture.OnFrameAvailableListener {
        private static final String TAG = "SurfaceVideoProcessor";
        private boolean mFrameAvailable;
        private boolean mGotDecoderEOS;
        private boolean mSignaledEncoderEOS;

        private InputSurface mEncSurface;
        private OutputSurface mDecSurface;
        private BufferInfo mInfoOnSurface;

        private LinkedList<Pair<Integer, BufferInfo>> mBuffersToRender =
            new LinkedList<Pair<Integer, BufferInfo>>();

        private final AtomicReference<String> errorMsg = new AtomicReference(null);

        @Override
        public boolean processLoop(
                String path, String outMime, String videoEncName,
                int width, int height, boolean optional) {
            boolean skipped = true;
            try {
                open(path);
                if (!initCodecsAndConfigureEncoder(
                        videoEncName, outMime, width, height,
                        CodecCapabilities.COLOR_FormatSurface)) {
                    assertTrue("could not configure encoder for supported size", optional);
                    return !skipped;
                }
                skipped = false;

                mEncSurface = new InputSurface(mEncoder.createInputSurface());
                mEncSurface.makeCurrent();

                mDecSurface = new OutputSurface(this);
                //mDecSurface.changeFragmentShader(FRAGMENT_SHADER);
                mDecoder.configure(mDecFormat, mDecSurface.getSurface(), null /* crypto */, 0);

                mDecoder.start();
                mEncoder.start();

                // main loop - process GL ops as only main thread has GL context
                while (!mCompleted && errorMsg.get() == null) {
                    BufferInfo info = null;
                    synchronized (mCondition) {
                        try {
                            // wait for mFrameAvailable, which is set by onFrameAvailable().
                            // Use a timeout to avoid stalling the test if it doesn't arrive.
                            if (!mFrameAvailable && !mCompleted && !mEncoderIsActive) {
                                mCondition.wait(mEncodeOutputFormatUpdated ?
                                        FRAME_TIMEOUT_MS : INIT_TIMEOUT_MS);
                            }
                        } catch (InterruptedException ie) {
                            fail("wait interrupted");  // shouldn't happen
                        }
                        if (mCompleted) {
                            break;
                        }
                        if (mEncoderIsActive) {
                            mEncoderIsActive = false;
                            if (DEBUG) Log.d(TAG, "encoder is still active, continue");
                            continue;
                        }
                        assertTrue("still waiting for image", mFrameAvailable);
                        if (DEBUG) Log.v(TAG, "got image");
                        info = mInfoOnSurface;
                    }
                    if (info == null) {
                        continue;
                    }
                    if (info.size > 0) {
                        mDecSurface.latchImage();
                        if (DEBUG) Log.v(TAG, "latched image");
                        mFrameAvailable = false;

                        mDecSurface.drawImage();
                        Log.d(TAG, "encoding frame at " + info.presentationTimeUs * 1000);

                        mEncSurface.setPresentationTime(info.presentationTimeUs * 1000);
                        mEncSurface.swapBuffers();
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mSignaledEncoderEOS = true;
                        Log.d(TAG, "signaling encoder EOS");
                        mEncoder.signalEndOfInputStream();
                    }

                    synchronized (mCondition) {
                        mInfoOnSurface = null;
                        if (mBuffersToRender.size() > 0 && mInfoOnSurface == null) {
                            if (DEBUG) Log.v(TAG, "handling postponed frame");
                            Pair<Integer, BufferInfo> nextBuffer = mBuffersToRender.removeFirst();
                            renderDecodedBuffer(nextBuffer.first, nextBuffer.second);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                fail("received exception " + e);
            } finally {
                close();
                if (mEncSurface != null) {
                    mEncSurface.release();
                    mEncSurface = null;
                }
                if (mDecSurface != null) {
                    mDecSurface.release();
                    mDecSurface = null;
                }
            }
            assertNull(errorMsg.get(), errorMsg.get());
            return !skipped;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            if (DEBUG) Log.v(TAG, "new frame available");
            synchronized (mCondition) {
                assertFalse("mFrameAvailable already set, frame could be dropped", mFrameAvailable);
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onInputBufferAvailableLocked(MediaCodec mediaCodec, int ix) {
            if (mediaCodec == mDecoder) {
                // fill input buffer from extractor
                fillDecoderInputBuffer(ix);
            } else {
                fail("received input buffer on " + mediaCodec.getName());
            }
        }

        @Override
        public void onOutputBufferAvailableLocked(
                MediaCodec mediaCodec, int ix, BufferInfo info) {
            if (mediaCodec == mDecoder) {
                if (DEBUG) Log.v(TAG, "decoder received output #" + ix
                         + " (sz=" + info.size + ", f=" + info.flags
                         + ", ts=" + info.presentationTimeUs + ")");
                // render output buffer from decoder
                if (!mGotDecoderEOS) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (eos) {
                        mGotDecoderEOS = true;
                    }
                    // can release empty buffers now
                    if (info.size == 0) {
                        mDecoder.releaseOutputBuffer(ix, false /* render */);
                        ix = -1; // fake index used by render to not render
                    }
                    if (eos || info.size > 0) {
                        synchronized(mCondition) {
                            if (mInfoOnSurface != null || mBuffersToRender.size() > 0) {
                                if (DEBUG) Log.v(TAG, "postponing render, surface busy");
                                mBuffersToRender.addLast(Pair.create(ix, info));
                            } else {
                                renderDecodedBuffer(ix, info);
                            }
                        }
                    }
                }
            } else if (mediaCodec == mEncoder) {
                emptyEncoderOutputBuffer(ix, info);
                synchronized(mCondition) {
                    if (!mCompleted) {
                        mEncoderIsActive = true;
                        mCondition.notifyAll();
                    }
                }
            } else {
                fail("received output buffer on " + mediaCodec.getName());
            }
        }

        private void renderDecodedBuffer(int ix, BufferInfo info) {
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            mInfoOnSurface = info;
            if (info.size > 0) {
                Log.d(TAG, "rendering frame #" + ix + " at " + info.presentationTimeUs * 1000
                        + (eos ? " with EOS" : ""));
                mDecoder.releaseOutputBuffer(ix, info.presentationTimeUs * 1000);
            }

            if (eos && info.size == 0) {
                if (DEBUG) Log.v(TAG, "decoder output EOS available");
                mFrameAvailable = true;
                mCondition.notifyAll();
            }
        }

        @Override
        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
            String codecName = null;
            try {
                codecName = mediaCodec.getName();
            } catch (Exception ex) {
                codecName = "(error getting codec name)";
            }
            errorMsg.set("received error on " + codecName + ": " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
            Log.i(TAG, mediaCodec.getName() + " got new output format " + mediaFormat);
            if (mediaCodec == mEncoder) {
                mEncodeOutputFormatUpdated = true;
                saveEncoderFormat(mediaFormat);
            }
        }
    }

    static class EncoderSize {
        private final boolean DEBUG = false;
        private static final String TAG = "EncoderSize";
        final private String mName;
        final private String mMime;
        final private CodecCapabilities mCaps;
        final private VideoCapabilities mVideoCaps;

        final public Map<Size, Set<Size>> mMinMax;     // extreme sizes
        final public Map<Size, Set<Size>> mNearMinMax; // sizes near extreme
        final public Set<Size> mArbitraryW;            // arbitrary widths in the middle
        final public Set<Size> mArbitraryH;            // arbitrary heights in the middle
        final public Set<Size> mSizes;                 // all non-specifically tested sizes

        final private int xAlign;
        final private int yAlign;

        EncoderSize(String name, String mime, CodecCapabilities caps) {
            mName = name;
            mMime = mime;
            mCaps = caps;
            mVideoCaps = caps.getVideoCapabilities();

            /* calculate min/max sizes */
            mMinMax = new HashMap<Size, Set<Size>>();
            mNearMinMax = new HashMap<Size, Set<Size>>();
            mArbitraryW = new HashSet<Size>();
            mArbitraryH = new HashSet<Size>();
            mSizes = new HashSet<Size>();

            xAlign = mVideoCaps.getWidthAlignment();
            yAlign = mVideoCaps.getHeightAlignment();

            initializeSizes();
        }

        private void initializeSizes() {
            for (int x = 0; x < 2; ++x) {
                for (int y = 0; y < 2; ++y) {
                    addExtremeSizesFor(x, y);
                }
            }

            // initialize arbitrary sizes
            for (int i = 1; i <= 7; ++i) {
                int j = ((7 * i) % 11) + 1;
                int width, height;
                try {
                    width = alignedPointInRange(i * 0.125, xAlign, mVideoCaps.getSupportedWidths());
                    height = alignedPointInRange(j * 0.077, yAlign,
                            mVideoCaps.getSupportedHeightsFor(width));
                    mArbitraryW.add(new Size(width, height));
                } catch (IllegalArgumentException e) {
                }

                try {
                    height = alignedPointInRange(i * 0.125, yAlign,
                            mVideoCaps.getSupportedHeights());
                    width = alignedPointInRange(j * 0.077, xAlign,
                            mVideoCaps.getSupportedWidthsFor(height));
                    mArbitraryH.add(new Size(width, height));
                } catch (IllegalArgumentException e) {
                }
            }
            mArbitraryW.removeAll(mArbitraryH);
            mArbitraryW.removeAll(mSizes);
            mSizes.addAll(mArbitraryW);
            mArbitraryH.removeAll(mSizes);
            mSizes.addAll(mArbitraryH);
            if (DEBUG) Log.i(TAG, "arbitrary=" + mArbitraryW + "/" + mArbitraryH);
        }

        private void addExtremeSizesFor(int x, int y) {
            Set<Size> minMax = new HashSet<Size>();
            Set<Size> nearMinMax = new HashSet<Size>();

            for (int dx = 0; dx <= xAlign; dx += xAlign) {
                for (int dy = 0; dy <= yAlign; dy += yAlign) {
                    Set<Size> bucket = (dx + dy == 0) ? minMax : nearMinMax;
                    try {
                        int width = getExtreme(mVideoCaps.getSupportedWidths(), x, dx);
                        int height = getExtreme(mVideoCaps.getSupportedHeightsFor(width), y, dy);
                        bucket.add(new Size(width, height));

                        // try max max with more reasonable ratio if too skewed
                        if (x + y == 2 && width >= 4 * height) {
                            Size wideScreen = getLargestSizeForRatio(16, 9);
                            width = getExtreme(
                                    mVideoCaps.getSupportedWidths()
                                            .intersect(0, wideScreen.getWidth()), x, dx);
                            height = getExtreme(mVideoCaps.getSupportedHeightsFor(width), y, 0);
                            bucket.add(new Size(width, height));
                        }
                    } catch (IllegalArgumentException e) {
                    }

                    try {
                        int height = getExtreme(mVideoCaps.getSupportedHeights(), y, dy);
                        int width = getExtreme(mVideoCaps.getSupportedWidthsFor(height), x, dx);
                        bucket.add(new Size(width, height));

                        // try max max with more reasonable ratio if too skewed
                        if (x + y == 2 && height >= 4 * width) {
                            Size wideScreen = getLargestSizeForRatio(9, 16);
                            height = getExtreme(
                                    mVideoCaps.getSupportedHeights()
                                            .intersect(0, wideScreen.getHeight()), y, dy);
                            width = getExtreme(mVideoCaps.getSupportedWidthsFor(height), x, dx);
                            bucket.add(new Size(width, height));
                        }
                    } catch (IllegalArgumentException e) {
                    }
                }
            }

            // keep unique sizes
            minMax.removeAll(mSizes);
            mSizes.addAll(minMax);
            nearMinMax.removeAll(mSizes);
            mSizes.addAll(nearMinMax);

            mMinMax.put(new Size(x, y), minMax);
            mNearMinMax.put(new Size(x, y), nearMinMax);
            if (DEBUG) Log.i(TAG, x + "x" + y + ": minMax=" + mMinMax + ", near=" + mNearMinMax);
        }

        private int alignInRange(double value, int align, Range<Integer> range) {
            return range.clamp(align * (int)Math.round(value / align));
        }

        /* point should be between 0. and 1. */
        private int alignedPointInRange(double point, int align, Range<Integer> range) {
            return alignInRange(
                    range.getLower() + point * (range.getUpper() - range.getLower()), align, range);
        }

        private int getExtreme(Range<Integer> range, int i, int delta) {
            int dim = i == 1 ? range.getUpper() - delta : range.getLower() + delta;
            if (delta == 0
                    || (dim > range.getLower() && dim < range.getUpper())) {
                return dim;
            }
            throw new IllegalArgumentException();
        }

        private Size getLargestSizeForRatio(int x, int y) {
            Range<Integer> widthRange = mVideoCaps.getSupportedWidths();
            Range<Integer> heightRange = mVideoCaps.getSupportedHeightsFor(widthRange.getUpper());
            final int xAlign = mVideoCaps.getWidthAlignment();
            final int yAlign = mVideoCaps.getHeightAlignment();

            // scale by alignment
            int width = alignInRange(
                    Math.sqrt(widthRange.getUpper() * heightRange.getUpper() * (double)x / y),
                    xAlign, widthRange);
            int height = alignInRange(
                    width * (double)y / x, yAlign, mVideoCaps.getSupportedHeightsFor(width));
            return new Size(width, height);
        }
    }

    class Encoder {
        final private String mName;
        final private String mMime;
        final private CodecCapabilities mCaps;
        final private VideoCapabilities mVideoCaps;


        Encoder(String name, String mime, CodecCapabilities caps) {
            mName = name;
            mMime = mime;
            mCaps = caps;
            mVideoCaps = caps.getVideoCapabilities();
        }

        public boolean testSpecific(int width, int height, boolean flexYUV) {
            return test(width, height, true /* optional */, flexYUV);
        }

        public boolean testIntraRefresh(int width, int height) {
            if (!mCaps.isFeatureSupported(CodecCapabilities.FEATURE_IntraRefresh)) {
                return false;
            }

            final int refreshPeriod[] = new int[] {10, 13, 17, 22, 29, 38, 50, 60};

            // Test the support of refresh periods in the range of 10 - 60 frames
            for (int period : refreshPeriod) {
                Function<MediaFormat, Boolean> updateConfigFormatHook =
                new Function<MediaFormat, Boolean>() {
                    public Boolean apply(MediaFormat fmt) {
                        // set i-frame-interval to 10000 so encoded video only has 1 i-frame.
                        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10000);
                        fmt.setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, period);
                        return true;
                    }
                };

                Function<MediaFormat, Boolean> checkOutputFormatHook =
                new Function<MediaFormat, Boolean>() {
                    public Boolean apply(MediaFormat fmt) {
                        int intraPeriod = fmt.getInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD);
                        // Make sure intra period is correct and carried in the output format.
                        // intraPeriod must be larger than 0 and operate within 20% of refresh
                        // period.
                        if (intraPeriod > 1.2 * period || intraPeriod < 0.8 * period) {
                            throw new RuntimeException("Intra period mismatch");
                        }
                        return true;
                    }
                };

                String testName =
                mName + '_' + width + "x" + height + '_' + "flexYUV_intraRefresh";

                Consumer<VideoProcessorBase> configureVideoProcessor =
                new Consumer<VideoProcessorBase>() {
                    public void accept(VideoProcessorBase processor) {
                        processor.setProcessorName(testName);
                        processor.setUpdateConfigHook(updateConfigFormatHook);
                        processor.setCheckOutputFormatHook(checkOutputFormatHook);
                    }
                };

                if (!test(width, height, 0 /* frameRate */, 0 /* bitRate */, true /* optional */,
                    true /* flex */, configureVideoProcessor)) {
                    return false;
                }
            }

            return true;
        }

        public boolean testDetailed(
                int width, int height, int frameRate, int bitRate, boolean flexYUV) {
            String testName =
                    mName + '_' + width + "x" + height + '_' + (flexYUV ? "flexYUV" : " surface");
            Consumer<VideoProcessorBase> configureVideoProcessor =
                    new Consumer<VideoProcessorBase>() {
                public void accept(VideoProcessorBase processor) {
                    processor.setProcessorName(testName);
                }
            };
            return test(width, height, frameRate, bitRate, true /* optional */, flexYUV,
                    configureVideoProcessor);
        }

        public boolean testSupport(int width, int height, int frameRate, int bitRate) {
            return mVideoCaps.areSizeAndRateSupported(width, height, frameRate) &&
                    mVideoCaps.getBitrateRange().contains(bitRate);
        }

        private boolean test(
                int width, int height, boolean optional, boolean flexYUV) {
            String testName =
                    mName + '_' + width + "x" + height + '_' + (flexYUV ? "flexYUV" : " surface");
            Consumer<VideoProcessorBase> configureVideoProcessor =
                    new Consumer<VideoProcessorBase>() {
                public void accept(VideoProcessorBase processor) {
                    processor.setProcessorName(testName);
                }
            };
            return test(width, height, 0 /* frameRate */, 0 /* bitRate */,
                    optional, flexYUV, configureVideoProcessor);
        }

        private boolean test(
                int width, int height, int frameRate, int bitRate, boolean optional,
                boolean flexYUV, Consumer<VideoProcessorBase> configureVideoProcessor) {
            Log.i(TAG, "testing " + mMime + " on " + mName + " for " + width + "x" + height
                    + (flexYUV ? " flexYUV" : " surface"));

            Preconditions.assertTestFileExists(SOURCE_URL);

            VideoProcessorBase processor =
                flexYUV ? new VideoProcessor() : new SurfaceVideoProcessor();

            processor.setFrameAndBitRates(frameRate, bitRate);
            configureVideoProcessor.accept(processor);

            // We are using a resource URL as an example
            boolean success = processor.processLoop(
                    SOURCE_URL, mMime, mName, width, height, optional);
            if (success) {
                success = processor.playBack(getActivity().getSurfaceHolder().getSurface());
            }
            return success;
        }
    }
    private static CodecCapabilities getCodecCapabities(String encoderName, String mime,
                                                        boolean isEncoder) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (isEncoder != codecInfo.isEncoder()) {
                continue;
            }
            if (encoderName.equals(codecInfo.getName())) {
                return codecInfo.getCapabilitiesForType(mime);
            }
        }
        return null;
    }
    private Encoder getEncHandle(String encodername, String mime) {
        CodecCapabilities caps = getCodecCapabities(encodername, mime, true);
        assertNotNull(caps);
        Encoder encoder = new Encoder(encodername, mime, caps);
        return encoder;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}x{3}_{4}_{5}")
    public static Collection<Object[]> input() {
        final String[] mediaTypesList = new String[] {
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_H263,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_MPEG4,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9,
                MediaFormat.MIMETYPE_VIDEO_AV1,
        };
        final List<Object[]> argsList = new ArrayList<>();
        for (String mediaType : mediaTypesList) {
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] encoders = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String encoder : encoders) {
                if (TestArgs.shouldSkipCodec(encoder)) {
                    continue;
                }
                if (!TestUtils.isTestableCodecInCurrentMode(encoder)) {
                    Log.d(TAG, "Skipping tests for codec: " + encoder);
                    continue;
                }
                CodecCapabilities caps = getCodecCapabities(encoder, mediaType, true);
                assertNotNull(caps);
                EncoderSize encoderSize = new EncoderSize(encoder, mediaType, caps);
                final Set<Size> sizes = new HashSet<Size>();
                for (boolean near : new boolean[] {false, true}) {
                    Map<Size, Set<Size>> testSizes =
                            near ? encoderSize.mNearMinMax : encoderSize.mMinMax;
                    for (int x = 0; x < 2; x++) {
                        for (int y = 0; y < 2; y++) {
                            for (Size s : testSizes.get(new Size(x, y))) {
                                sizes.add(new Size(s.getWidth(), s.getHeight()));
                            }
                        }
                    }
                }
                for (boolean widths : new boolean[] {false, true}) {
                    for (Size s : (widths ? encoderSize.mArbitraryW : encoderSize.mArbitraryH)) {
                        sizes.add(new Size(s.getWidth(), s.getHeight()));
                    }
                }
                final Set<Size> specificSizes = new HashSet<Size>();
                specificSizes.add(new Size(176, 144));
                specificSizes.add(new Size(320, 180));
                specificSizes.add(new Size(320, 240));
                specificSizes.add(new Size(720, 480));
                specificSizes.add(new Size(1280, 720));
                specificSizes.add(new Size(1920, 1080));

                for (boolean flexYuv : new boolean[] {false, true}) {
                    for (Size s : specificSizes) {
                        argsList.add(new Object[]{encoder, mediaType, s.getWidth(), s.getHeight(),
                                flexYuv, TestMode.TEST_MODE_DETAILED});
                    }
                }

                argsList.add(new Object[]{encoder, mediaType, 480, 360, true,
                        TestMode.TEST_MODE_INTRAREFRESH});
                sizes.removeAll(specificSizes);
                specificSizes.addAll(sizes);
                for (boolean flexYuv : new boolean[] {false, true}) {
                    for (Size s : specificSizes) {
                        argsList.add(new Object[]{encoder, mediaType, s.getWidth(), s.getHeight(),
                                flexYuv, TestMode.TEST_MODE_SPECIFIC});
                    }
                }
            }
        }
        return argsList;
    }

    public VideoEncoderTest(String encoderName, String mime, int width, int height, boolean flexYuv,
                            TestMode mode) {
        mEncHandle = getEncHandle(encoderName, mime);
        mWidth = width;
        mHeight = height;
        mFlexYuv = flexYuv;
        mMode = mode;
    }

    @CddTest(requirements = {"5.1.7/C-3-1"})
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#FEATURE_IntraRefresh",
            "android.media.MediaFormat#KEY_WIDTH",
            "android.media.MediaFormat#KEY_HEIGHT",
            "android.media.MediaFormat#KEY_FRAME_RATE",
            "android.media.MediaFormat#KEY_BIT_RATE",
            "android.media.MediaFormat#KEY_I_FRAME_INTERVAL",
            "android.media.MediaFormat#KEY_INTRA_REFRESH_PERIOD",
            "android.media.MediaFormat#KEY_MAX_INPUT_SIZE",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @Test
    public void testEncode() {
        int frameRate = 30;
        int bitRate;
        int lumaSamples = mWidth * mHeight;
        if (lumaSamples <= 320 * 240) {
            bitRate = 384 * 1000;
        } else if (lumaSamples <= 720 * 480) {
            bitRate = 2 * 1000000;
        } else if (lumaSamples <= 1280 * 720) {
            bitRate = 4 * 1000000;
        } else {
            bitRate = 10 * 1000000;
        }
        switch (mMode) {
            case TEST_MODE_SPECIFIC:
                specific(new Encoder[]{mEncHandle}, mWidth, mHeight, mFlexYuv);
                break;
            case TEST_MODE_DETAILED:
                detailed(new Encoder[]{mEncHandle}, mWidth, mHeight, frameRate, bitRate, mFlexYuv);
                break;
            case TEST_MODE_INTRAREFRESH:
                intraRefresh(new Encoder[]{mEncHandle}, mWidth, mHeight);
                break;
        }
    }

    /* test specific size */
    private void specific(Encoder[] encoders, int width, int height, boolean flexYUV) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder : encoders) {
            if (encoder.testSpecific(width, height, flexYUV)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("duplicate or unsupported resolution");
        }
    }

    /* test intra refresh with flexYUV */
    private void intraRefresh(Encoder[] encoders, int width, int height) {
        boolean skipped = true;
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        for (Encoder encoder : encoders) {
            if (encoder.testIntraRefresh(width, height)) {
                skipped = false;
            }
        }
        if (skipped) {
            MediaUtils.skipTest("intra-refresh unsupported");
        }
    }

    /* test size, frame rate and bit rate */
    private void detailed(
            Encoder[] encoders, int width, int height, int frameRate, int bitRate,
            boolean flexYUV) {
        Assume.assumeTrue("Test is currently enabled only for avc and vp8 encoders",
                mEncHandle.mMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC) ||
                        mEncHandle.mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8));
        if (encoders.length == 0) {
            MediaUtils.skipTest("no such encoder present");
            return;
        }
        boolean skipped = true;
        for (Encoder encoder : encoders) {
            if (encoder.testSupport(width, height, frameRate, bitRate)) {
                skipped = false;
                encoder.testDetailed(width, height, frameRate, bitRate, flexYUV);
            }
        }
        if (skipped) {
            MediaUtils.skipTest("unsupported resolution and rate");
        }
    }

}
