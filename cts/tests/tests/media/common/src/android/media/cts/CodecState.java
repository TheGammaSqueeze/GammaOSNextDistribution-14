/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.media.cts;

import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Class for directly managing both audio and video playback by
 * using {@link MediaCodec} and {@link AudioTrack}.
 */
public class CodecState {
    private static final String TAG = CodecState.class.getSimpleName();

    public static final int UNINITIALIZED_TIMESTAMP = Integer.MIN_VALUE;

    private boolean mSawInputEOS;
    private volatile boolean mSawOutputEOS;
    private boolean mLimitQueueDepth;
    private boolean mIsTunneled;
    private boolean mIsAudio;
    private int mAudioSessionId;
    private ByteBuffer[] mCodecInputBuffers;
    private ByteBuffer[] mCodecOutputBuffers;
    private int mTrackIndex;
    private int mAvailableInputBufferIndex;
    private LinkedList<Integer> mAvailableOutputBufferIndices;
    private LinkedList<MediaCodec.BufferInfo> mAvailableOutputBufferInfos;

    /**
     * The media timestamp of the latest frame decoded by this codec.
     *
     * Note: in tunnel mode, this coincides with the latest rendered frame.
     */
    private volatile long mDecodedFramePresentationTimeUs;
    private volatile long mRenderedVideoFramePresentationTimeUs;
    private volatile long mRenderedVideoFrameSystemTimeNano;
    private long mFirstSampleTimeUs;
    private long mPlaybackStartTimeUs;
    private long mLastPresentTimeUs;
    private MediaCodec mCodec;
    private MediaTimeProvider mMediaTimeProvider;
    private MediaExtractor mExtractor;
    private MediaFormat mFormat;
    private MediaFormat mOutputFormat;
    private NonBlockingAudioTrack mAudioTrack;
    private volatile OnFrameRenderedListener mOnFrameRenderedListener;
    /** A list of reported rendered video frames' timestamps. */
    private ArrayList<Long> mRenderedVideoFrameTimestampList;
    private ArrayList<Long> mRenderedVideoFrameSystemTimeList;
    private boolean mIsFirstTunnelFrameReady;
    private volatile OnFirstTunnelFrameReadyListener mOnFirstTunnelFrameReadyListener;
    /** If true, starves the underlying {@link MediaCodec} to simulate an underrun. */
    private boolean mShouldStopDrainingOutputBuffers;

    private static boolean mIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    /** If true the video/audio will start from the beginning when it reaches the end. */
    private boolean mLoopEnabled = false;

    /**
     * Manages audio and video playback using MediaCodec and AudioTrack.
     */
    public CodecState(
            MediaTimeProvider mediaTimeProvider,
            MediaExtractor extractor,
            int trackIndex,
            MediaFormat format,
            MediaCodec codec,
            boolean limitQueueDepth,
            boolean tunneled,
            int audioSessionId) {
        mMediaTimeProvider = mediaTimeProvider;
        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mFormat = format;
        mSawInputEOS = mSawOutputEOS = false;
        mLimitQueueDepth = limitQueueDepth;
        mIsTunneled = tunneled;
        mAudioSessionId = audioSessionId;
        mFirstSampleTimeUs = -1;
        mPlaybackStartTimeUs = 0;
        mLastPresentTimeUs = 0;

        mCodec = codec;

        mAvailableInputBufferIndex = -1;
        mAvailableOutputBufferIndices = new LinkedList<Integer>();
        mAvailableOutputBufferInfos = new LinkedList<MediaCodec.BufferInfo>();
        mRenderedVideoFrameTimestampList = new ArrayList<Long>();
        mRenderedVideoFrameSystemTimeList = new ArrayList<Long>();

        mDecodedFramePresentationTimeUs = UNINITIALIZED_TIMESTAMP;
        mRenderedVideoFramePresentationTimeUs = UNINITIALIZED_TIMESTAMP;
        mRenderedVideoFrameSystemTimeNano = UNINITIALIZED_TIMESTAMP;

        mIsFirstTunnelFrameReady = false;
        mShouldStopDrainingOutputBuffers = false;

        String mime = mFormat.getString(MediaFormat.KEY_MIME);
        Log.d(TAG, "CodecState::CodecState " + mime);
        mIsAudio = mime.startsWith("audio/");

        setFrameListeners(mCodec);
    }

    public void release() {
        mCodec.stop();
        mCodecInputBuffers = null;
        mCodecOutputBuffers = null;
        mOutputFormat = null;

        mAvailableOutputBufferIndices.clear();
        mAvailableOutputBufferInfos.clear();

        mAvailableInputBufferIndex = -1;
        mAvailableOutputBufferIndices = null;
        mAvailableOutputBufferInfos = null;

        releaseFrameListeners();

        mCodec.release();
        mCodec = null;

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public void startCodec() {
        mCodec.start();
        mCodecInputBuffers = mCodec.getInputBuffers();
        if (!mIsTunneled || mIsAudio) {
            mCodecOutputBuffers = mCodec.getOutputBuffers();
        }
    }

    public void play() {
        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    public void pause() {
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    /**
     * Returns the media timestamp of the latest decoded sample/frame.
     *
     * TODO(b/202710709): Disambiguate getCurrentPosition's meaning
     */
    public long getCurrentPositionUs() {
        // Use decoded frame time when available, otherwise default to render time (typically, in
        // tunnel mode).
        if (mDecodedFramePresentationTimeUs != UNINITIALIZED_TIMESTAMP) {
            return mDecodedFramePresentationTimeUs;
        } else {
            return mRenderedVideoFramePresentationTimeUs;
        }
    }

    /** Returns the system time of the latest rendered video frame. */
    public long getRenderedVideoSystemTimeNano() {
        return mRenderedVideoFrameSystemTimeNano;
    }

    public void flush() {
        if (!mIsTunneled || mIsAudio) {
            mAvailableOutputBufferIndices.clear();
            mAvailableOutputBufferInfos.clear();
        }

        mAvailableInputBufferIndex = -1;
        mSawInputEOS = false;
        mSawOutputEOS = false;

        if (mAudioTrack != null
                && mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.flush();
        }

        mCodec.flush();
        mDecodedFramePresentationTimeUs = UNINITIALIZED_TIMESTAMP;
        mRenderedVideoFramePresentationTimeUs = UNINITIALIZED_TIMESTAMP;
        mRenderedVideoFrameSystemTimeNano = UNINITIALIZED_TIMESTAMP;
        mRenderedVideoFrameTimestampList = new ArrayList<Long>();
        mRenderedVideoFrameSystemTimeList = new ArrayList<Long>();
        mIsFirstTunnelFrameReady = false;
    }

    public boolean isEnded() {
        return mSawInputEOS && mSawOutputEOS;
    }

    /** @see #doSomeWork(Boolean) */
    public Long doSomeWork() {
        return doSomeWork(false /* mustWait */);
    }

    /**
     * {@code doSomeWork} is the worker function that does all buffer handling and decoding works.
     * It first reads data from {@link MediaExtractor} and pushes it into {@link MediaCodec}; it
     * then dequeues buffer from {@link MediaCodec}, consumes it and pushes back to its own buffer
     * queue for next round reading data from {@link MediaExtractor}.
     *
     * @param boolean  Whether to block on input buffer retrieval
     *
     * @return timestamp of the queued frame, if any.
     */
    public Long doSomeWork(boolean mustWait) {
        // Extract input data, if relevant
        Long sampleTime = null;
        if (mAvailableInputBufferIndex == -1) {
            int indexInput = mCodec.dequeueInputBuffer(mustWait ? -1 : 0 /* timeoutUs */);
            if (indexInput != MediaCodec.INFO_TRY_AGAIN_LATER) {
                mAvailableInputBufferIndex = indexInput;
            }
        }
        if (mAvailableInputBufferIndex != -1) {
            sampleTime = feedInputBuffer(mAvailableInputBufferIndex);
            if (sampleTime != null) {
                mAvailableInputBufferIndex = -1;
            }
        }

        // Queue output data, if relevant
        if (mIsAudio || !mIsTunneled) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int indexOutput = mCodec.dequeueOutputBuffer(info, 0 /* timeoutUs */);

            if (indexOutput == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mOutputFormat = mCodec.getOutputFormat();
                onOutputFormatChanged();
            } else if (indexOutput == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mCodecOutputBuffers = mCodec.getOutputBuffers();
            } else if (indexOutput != MediaCodec.INFO_TRY_AGAIN_LATER) {
                mAvailableOutputBufferIndices.add(indexOutput);
                mAvailableOutputBufferInfos.add(info);
            }

            while (drainOutputBuffer()) {
            }
        }

        return sampleTime;
    }

    public void setLoopEnabled(boolean enabled) {
        mLoopEnabled = enabled;
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    private void setFrameListeners(MediaCodec codec) {
        if (!mIsAudio) {
            // Setup frame rendered callback for video codecs
            mOnFrameRenderedListener = new OnFrameRenderedListener();
            codec.setOnFrameRenderedListener(mOnFrameRenderedListener,
                    new Handler(Looper.getMainLooper()));

            if (mIsTunneled) {
                mOnFirstTunnelFrameReadyListener = new OnFirstTunnelFrameReadyListener();
                codec.setOnFirstTunnelFrameReadyListener(new Handler(Looper.getMainLooper()),
                        mOnFirstTunnelFrameReadyListener);
            }
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    private void releaseFrameListeners() {
        if (mOnFrameRenderedListener != null) {
            mCodec.setOnFrameRenderedListener(null, null);
            mOnFrameRenderedListener = null;
        }
        if (mOnFirstTunnelFrameReadyListener != null) {
            mCodec.setOnFirstTunnelFrameReadyListener(null, null);
            mOnFirstTunnelFrameReadyListener = null;
        }
    }

    /**
     * Extracts some data from the configured MediaExtractor and feeds it to the configured
     * MediaCodec.
     *
     * Returns the timestamp of the queued buffer, if any.
     * Returns null once all data has been extracted and queued.
     */
    private Long feedInputBuffer(int inputBufferIndex)
            throws MediaCodec.CryptoException, IllegalStateException {
        if (mSawInputEOS || inputBufferIndex == -1) {
            return null;
        }

        // stalls read if audio queue is larger than 2MB full so we will not occupy too much heap
        if (mLimitQueueDepth && mAudioTrack != null &&
                mAudioTrack.getNumBytesQueued() > 2 * 1024 * 1024) {
            return null;
        }

        ByteBuffer codecData = mCodecInputBuffers[inputBufferIndex];

        int trackIndex = mExtractor.getSampleTrackIndex();

        if (trackIndex == mTrackIndex) {
            int sampleSize =
                mExtractor.readSampleData(codecData, 0 /* offset */);

            long sampleTime = mExtractor.getSampleTime();

            int sampleFlags = mExtractor.getSampleFlags();

            if (sampleSize <= 0) {
                Log.d(TAG, "sampleSize: " + sampleSize + " trackIndex:" + trackIndex +
                        " sampleTime:" + sampleTime + " sampleFlags:" + sampleFlags);
                mSawInputEOS = true;
                return null;
            }

            if (mIsTunneled) {
                if (mFirstSampleTimeUs == -1) {
                    mFirstSampleTimeUs = sampleTime;
                }
                sampleTime -= mFirstSampleTimeUs;
            }

            mLastPresentTimeUs = mPlaybackStartTimeUs + sampleTime;

            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
                mExtractor.getSampleCryptoInfo(info);

                mCodec.queueSecureInputBuffer(
                        inputBufferIndex, 0 /* offset */, info, mLastPresentTimeUs, 0 /* flags */);
            } else {
                mCodec.queueInputBuffer(
                        inputBufferIndex, 0 /* offset */, sampleSize, mLastPresentTimeUs, 0 /* flags */);
            }

            mExtractor.advance();
            return mLastPresentTimeUs;
        } else if (trackIndex < 0) {
            Log.d(TAG, "saw input EOS on track " + mTrackIndex);

            if (mLoopEnabled) {
                Log.d(TAG, "looping from the beginning");
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                mPlaybackStartTimeUs = mLastPresentTimeUs;
                return null;
            }

            mSawInputEOS = true;
            mCodec.queueInputBuffer(
                    inputBufferIndex, 0 /* offset */, 0 /* sampleSize */,
                    0 /* sampleTime */, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }

        return null;
    }

    private void onOutputFormatChanged() {
        String mime = mOutputFormat.getString(MediaFormat.KEY_MIME);
        // b/9250789
        Log.d(TAG, "CodecState::onOutputFormatChanged " + mime);

        mIsAudio = false;
        if (mime.startsWith("audio/")) {
            mIsAudio = true;
            int sampleRate =
                mOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

            int channelCount =
                mOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            Log.d(TAG, "CodecState::onOutputFormatChanged Audio" +
                    " sampleRate:" + sampleRate + " channels:" + channelCount);
            // We do a check here after we receive data from MediaExtractor and before
            // we pass them down to AudioTrack. If MediaExtractor works properly, this
            // check is not necessary, however, in our tests, we found that there
            // are a few cases where ch=0 and samplerate=0 were returned by MediaExtractor.
            if (channelCount < 1 || channelCount > 8 ||
                    sampleRate < 8000 || sampleRate > 128000) {
                return;
            }
            mAudioTrack = new NonBlockingAudioTrack(sampleRate, channelCount,
                                    mIsTunneled, mAudioSessionId);
            mAudioTrack.play();
        }

        if (mime.startsWith("video/")) {
            int width = mOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            Log.d(TAG, "CodecState::onOutputFormatChanged Video" +
                    " width:" + width + " height:" + height);
        }
    }

    /** Returns true if more output data could be drained. */
    private boolean drainOutputBuffer() {
        if (mSawOutputEOS || mAvailableOutputBufferIndices.isEmpty()
                || mShouldStopDrainingOutputBuffers) {
            return false;
        }

        int index = mAvailableOutputBufferIndices.peekFirst().intValue();
        MediaCodec.BufferInfo info = mAvailableOutputBufferInfos.peekFirst();

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG, "saw output EOS on track " + mTrackIndex);

            mSawOutputEOS = true;

            // Do not stop audio track here. Video presentation may not finish
            // yet, stopping the audio track now would result in getAudioTimeUs
            // returning 0 and prevent video samples from being presented.
            // We stop the audio track before the playback thread exits.
            if (mAudioTrack != null) {
                mAudioTrack.setEndOfStream();
            }
            return false;
        }

        if (mAudioTrack != null) {
            ByteBuffer buffer = mCodecOutputBuffers[index];
            byte[] audioArray = new byte[info.size];
            buffer.get(audioArray);
            buffer.clear();

            mAudioTrack.write(ByteBuffer.wrap(audioArray), info.size,
                    info.presentationTimeUs * 1000);

            mCodec.releaseOutputBuffer(index, false /* render */);

            mDecodedFramePresentationTimeUs = info.presentationTimeUs;

            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        } else {
            // video
            boolean render;
            long realTimeUs =
                    mMediaTimeProvider.getRealTimeUsForMediaTime(info.presentationTimeUs);

            long nowUs = mMediaTimeProvider.getNowUs();

            long lateUs = nowUs - realTimeUs;

            if (lateUs < -45000) {
                // too early;
                return false;
            } else if (lateUs > 30000) {
                Log.d(TAG, "video late by " + lateUs + " us.");
                render = false;
            } else {
                render = true;
                mDecodedFramePresentationTimeUs = info.presentationTimeUs;
            }

            mCodec.releaseOutputBuffer(index, render);

            mAvailableOutputBufferIndices.removeFirst();
            mAvailableOutputBufferInfos.removeFirst();
            return true;
        }
    }

    /**
     * Callback called by {@link MediaCodec} when it is notified that a decoded video frame has been
     * rendered on the attached {@link Surface}.
    */
    private class OnFrameRenderedListener implements MediaCodec.OnFrameRenderedListener {
        private static final long TUNNELING_EOS_PRESENTATION_TIME_US = Long.MAX_VALUE;

        @Override
        public void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime) {
            if (this != mOnFrameRenderedListener) {
                return; // stale event
            }
            if (presentationTimeUs == TUNNELING_EOS_PRESENTATION_TIME_US) {
                 mSawOutputEOS = true;
            } else {
                mRenderedVideoFramePresentationTimeUs = presentationTimeUs;
            }
            mRenderedVideoFrameSystemTimeNano = nanoTime;
            mRenderedVideoFrameTimestampList.add(presentationTimeUs);
            mRenderedVideoFrameSystemTimeList.add(mRenderedVideoFrameSystemTimeNano);
        }
    }

    public long getAudioTimeUs() {
        if (mAudioTrack == null) {
            return 0;
        }

        return mAudioTrack.getAudioTimeUs();
    }

    /** Returns the presentation timestamp of the last rendered video frame. */
    public long getVideoTimeUs() {
        return mRenderedVideoFramePresentationTimeUs;
    }

    /** Callback called in tunnel mode when video peek is ready */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    private class OnFirstTunnelFrameReadyListener
        implements MediaCodec.OnFirstTunnelFrameReadyListener {

        @Override
        public void onFirstTunnelFrameReady(MediaCodec codec) {
            if (this != mOnFirstTunnelFrameReadyListener) {
                return; // stale event
            }
            mIsFirstTunnelFrameReady = true;
        }
    }

    /**
     * If a video codec, returns the list of rendered frames' timestamps. Otherwise, returns an
     * empty list.
     */
    public ImmutableList<Long> getRenderedVideoFrameTimestampList() {
        return ImmutableList.<Long>copyOf(mRenderedVideoFrameTimestampList);
    }

    /**
     * If a video codec, returns the list system times when frames were rendered. Otherwise, returns
     * an empty list.
     */
    public ImmutableList<Long> getRenderedVideoFrameSystemTimeList() {
        return ImmutableList.<Long>copyOf(mRenderedVideoFrameSystemTimeList);
    }


    /** Process the attached {@link AudioTrack}, if any. */
    public void processAudioTrack() {
        if (mAudioTrack != null) {
            mAudioTrack.process();
        }
    }

    public int getFramesWritten() {
        if (mAudioTrack != null) {
            return mAudioTrack.getFramesWritten();
        }
        return 0;
    }

    public AudioTimestamp getTimestamp() {
        if (mAudioTrack == null) {
            return null;
        }

        return mAudioTrack.getTimestamp();
    }

    /** Stop the attached {@link AudioTrack}, if any. */
    public void stopAudioTrack() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
        }
    }

    /** Start associated audio track, if any. */
    public void playAudioTrack() {
        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    public void setOutputSurface(Surface surface) {
        if (mAudioTrack != null) {
            throw new UnsupportedOperationException("Cannot set surface on audio codec");
        }
        mCodec.setOutputSurface(surface);
    }

    /** Configure video peek. */
    public void setVideoPeek(boolean enable) {
        if (MediaUtils.check(mIsAtLeastS, "setVideoPeek requires Android S")) {
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_TUNNEL_PEEK, enable ? 1 : 0);
            mCodec.setParameters(parameters);
        }
    }

    /** In tunnel mode, queries whether the first video frame is ready for video peek. */
    public boolean isFirstTunnelFrameReady() {
        return mIsFirstTunnelFrameReady;
    }

    /**
     * Stop draining output buffers which can simulate underrun condition.
     */
    public void stopDrainingOutputBuffers(boolean stop) {
        mShouldStopDrainingOutputBuffers = stop;
        if (mAudioTrack != null) {
            mAudioTrack.setStopWriting(stop);
        }
    }

    /**
     * Option to introduce an offset (positive or negative, in Ns) to content queued to the
     * {@link AudioTrack}.
     */
    public void setAudioOffsetNs(long audioOffsetNs) {
        if (mAudioTrack != null) {
            mAudioTrack.setAudioOffsetNs(audioOffsetNs);
        }
    }

    /** Returns the underlying {@code AudioTrack}, if any. */
    public AudioTrack getAudioTrack() {
        if (mAudioTrack != null) {
            return mAudioTrack.getAudioTrack();
        }
        return null;
    }

    /**
     * Seek media extractor to the beginning of the configured track.
     *
     * @param presentationTimeOffsetUs The offset for the presentation time to start at.
     */
    public void seekToBeginning(long presentationTimeOffsetUs) {
        mExtractor.seekTo(mFirstSampleTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        mPlaybackStartTimeUs = presentationTimeOffsetUs;
    }
}
