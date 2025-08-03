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

package com.android.tv.tuner.exoplayer2;

import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Pair;

import com.android.tv.tuner.exoplayer2.buffer.BufferManager;
import com.android.tv.tuner.exoplayer2.buffer.MemorySampleBuffer;
import com.android.tv.tuner.exoplayer2.buffer.PlaybackBufferListener;
import com.android.tv.tuner.exoplayer2.buffer.RecordingSampleBuffer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that extracts samples from a live broadcast stream while storing the sample on the disk.
 * For demux, this class relies on {@link com.google.android.exoplayer.extractor.ts.TsExtractor}.
 */
public class ExoPlayerSampleExtractor implements SampleExtractor {
    private static final String TAG = "ExoPlayerSampleExtractor";

    private static final int INVALID_TRACK_INDEX = -1;

    // ATSC/53 allows sample rate to be only 48Khz.
    // One AC3 sample has 1536 frames, and its duration is 32ms.
    private static final long AC3_SAMPLE_DURATION_US = 32000;
    // This is around 150ms, 150ms is big enough not to under-run AudioTrack,
    // and  150ms is also small enough to fill the buffer rapidly.
    private static final int BUFFERED_SAMPLES_IN_AUDIOTRACK = 5;
    private static final long INITIAL_AUDIO_BUFFERING_TIME_US =
            BUFFERED_SAMPLES_IN_AUDIOTRACK * AC3_SAMPLE_DURATION_US;

    private final HandlerThread mSourceReaderThread;
    private final long mId;

    private final Handler.Callback mSourceReaderWorker;

    private BufferManager.SampleBuffer mSampleBuffer;
    private Handler mSourceReaderHandler;
    private volatile boolean mPrepared;
    private final AtomicBoolean mOnCompletionCalled = new AtomicBoolean();
    private IOException mExceptionOnPrepare;
    private List<Format> mTrackFormats;
    private TrackGroupArray mTrackGroupArray;
    private int mVideoTrackIndex = INVALID_TRACK_INDEX;
    private boolean mVideoTrackMet;
    private long mBaseSamplePts = Long.MIN_VALUE;
    private final HashMap<Integer, Long> mLastExtractedPositionUsMap = new HashMap<>();
    private final List<Pair<Integer, DecoderInputBuffer>> mPendingSamples = new ArrayList<>();
    private OnCompletionListener mOnCompletionListener;
    private Handler mOnCompletionListenerHandler;
    private IOException mError;
    private MediaPeriod mMediaPeriod;
    private Callback mCallback;

    /**
     * Factory for {@link ExoPlayerSampleExtractor}.
     *
     * <p>This wrapper class keeps other classes from needing to reference the {@link AutoFactory}
     * generated class.
     */
    public interface Factory {
        ExoPlayerSampleExtractor create(
                Uri uri,
                DataSource source,
                @Nullable BufferManager bufferManager,
                PlaybackBufferListener bufferListener,
                boolean isRecording);
    }

    @AutoFactory(implementing = Factory.class)
    public ExoPlayerSampleExtractor(
            Uri uri,
            DataSource source,
            @Nullable BufferManager bufferManager,
            PlaybackBufferListener bufferListener,
            boolean isRecording,
            @Provided RecordingSampleBuffer.Factory recordingSampleBufferFactory) {
        this(
                uri,
                source,
                bufferManager,
                bufferListener,
                isRecording,
                new HandlerThread("SourceReaderThread"),
                recordingSampleBufferFactory);
    }

    @VisibleForTesting
    ExoPlayerSampleExtractor(
            Uri uri,
            DataSource source,
            BufferManager bufferManager,
            PlaybackBufferListener bufferListener,
            boolean isRecording,
            HandlerThread sourceReaderThread,
            RecordingSampleBuffer.Factory recordingSampleBufferFactory) {
        // It'll be used as a timeshift file chunk name's prefix.
        mId = System.currentTimeMillis();

        mSourceReaderThread = sourceReaderThread;
        mSourceReaderWorker =
                new SourceReaderWorker(
                        new ProgressiveMediaSource.Factory(() -> source).createMediaSource(uri));
        if (isRecording) {
            mSampleBuffer =
                    recordingSampleBufferFactory.create(
                            bufferManager,
                            bufferListener,
                            false,
                            RecordingSampleBuffer.BUFFER_REASON_RECORDING);
        } else {
            if (bufferManager == null) {
                mSampleBuffer = new MemorySampleBuffer(bufferListener);
            } else {
                mSampleBuffer =
                        recordingSampleBufferFactory.create(
                                bufferManager,
                                bufferListener,
                                true,
                                RecordingSampleBuffer.BUFFER_REASON_LIVE_PLAYBACK);
            }
        }
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener, Handler handler) {
        mOnCompletionListener = listener;
        mOnCompletionListenerHandler = handler;
    }

    private class SourceReaderWorker implements Handler.Callback, MediaPeriod.Callback {
        private static final int MSG_PREPARE = 1;
        private static final int MSG_FETCH_SAMPLES = 2;
        private static final int MSG_RELEASE = 3;
        private static final int RETRY_INTERVAL_MS = 50;

        private final MediaSource mSampleSource;
        private final MediaSource.SourceInfoRefreshListener mSampleSourceListener;
        private SampleStream[] mStreams;
        private boolean[] mTrackMetEos;
        private boolean mMetEos = false;
        private long mCurrentPosition;
        private final DecoderInputBuffer mDecoderInputBuffer;
        private final DecoderInputBuffer mDecoderInputBufferDuplicate;
        private boolean mPrepareRequested;

        SourceReaderWorker(MediaSource sampleSource) {
            mSampleSource = sampleSource;
            mSampleSourceListener =
                    (source, timeline, manifest) -> {
                        // Dynamic stream change is not supported yet. b/28169263
                        // For now, this will cause EOS and playback reset.
                    };
            mSampleSource.prepareSource(mSampleSourceListener, null);
            mDecoderInputBuffer =
                    new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
            mDecoderInputBufferDuplicate =
                    new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
        }

        @Override
        public void onPrepared(MediaPeriod mediaPeriod) {
            if (mMediaPeriod == null) {
                // This instance is already released while the extractor is preparing.
                return;
            }
            TrackGroupArray trackGroupArray = mMediaPeriod.getTrackGroups();
            mTrackGroupArray = trackGroupArray;
            TrackSelection[] selections = new TrackSelection[trackGroupArray.length];
            for (int i = 0; i < selections.length; ++i) {
                selections[i] = new FixedTrackSelection(trackGroupArray.get(i), 0);
            }
            boolean[] retain = new boolean[trackGroupArray.length];
            boolean[] reset = new boolean[trackGroupArray.length];
            mStreams = new SampleStream[trackGroupArray.length];
            mMediaPeriod.selectTracks(selections, retain, mStreams, reset, 0);
            if (mTrackFormats == null) {
                int trackCount = trackGroupArray.length;
                mTrackMetEos = new boolean[trackCount];
                List<Format> trackFormats = new ArrayList<>();
                int videoTrackCount = 0;
                for (int i = 0; i < trackCount; i++) {
                    Format format = trackGroupArray.get(i).getFormat(0);
                    if (format.sampleMimeType.startsWith("video/")) {
                        videoTrackCount++;
                        mVideoTrackIndex = i;
                    }
                    trackFormats.add(format);
                }
                if (videoTrackCount > 1) {
                    // Disable dropping samples when there are multiple video tracks.
                    mVideoTrackIndex = INVALID_TRACK_INDEX;
                }
                mTrackFormats = trackFormats;
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < mTrackFormats.size(); i++) {
                    ids.add(String.format(Locale.ENGLISH, "%s_%x", Long.toHexString(mId), i));
                }
                try {
                    mSampleBuffer.init(ids, mTrackFormats);
                } catch (IOException e) {
                    // In this case, we will not schedule any further operation.
                    // mExceptionOnPrepare will be notified to ExoPlayer, and ExoPlayer will
                    // call release() eventually.
                    mExceptionOnPrepare = e;
                    return;
                }
                mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                mPrepared = true;
                mCallback.onPrepared();
            }
        }

        @Override
        public void onContinueLoadingRequested(MediaPeriod source) {
            source.continueLoading(mCurrentPosition);
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_PREPARE:
                    if (!mPrepareRequested) {
                        mPrepareRequested = true;
                        mMediaPeriod =
                                mSampleSource.createPeriod(
                                        new MediaSource.MediaPeriodId(0),
                                        new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                                        0);
                        mMediaPeriod.prepare(this, 0);
                        try {
                            mMediaPeriod.maybeThrowPrepareError();
                        } catch (IOException e) {
                            mError = e;
                        }
                    }
                    return true;
                case MSG_FETCH_SAMPLES:
                    boolean didSomething = false;
                    ConditionVariable conditionVariable = new ConditionVariable();
                    int trackCount = mStreams.length;
                    for (int i = 0; i < trackCount; ++i) {
                        if (!mTrackMetEos[i]
                                && C.RESULT_NOTHING_READ != fetchSample(i, conditionVariable)) {
                            if (mMetEos) {
                                // If mMetEos was on during fetchSample() due to an error,
                                // fetching from other tracks is not necessary.
                                break;
                            }
                            didSomething = true;
                        }
                    }
                    mMediaPeriod.continueLoading(mCurrentPosition);
                    if (!mMetEos) {
                        if (didSomething) {
                            mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                        } else {
                            mSourceReaderHandler.sendEmptyMessageDelayed(
                                    MSG_FETCH_SAMPLES, RETRY_INTERVAL_MS);
                        }
                    } else {
                        notifyCompletionIfNeeded(false);
                    }
                    return true;
                case MSG_RELEASE:
                    if (mMediaPeriod != null) {
                        mSampleSource.releasePeriod(mMediaPeriod);
                        mSampleSource.releaseSource(mSampleSourceListener);
                        mMediaPeriod = null;
                    }
                    cleanUp();
                    mSourceReaderHandler.removeCallbacksAndMessages(null);
                    return true;
                default: // fall out
            }
            return false;
        }

        private int fetchSample(int track, ConditionVariable conditionVariable) {
            FormatHolder dummyFormatHolder = new FormatHolder();
            mDecoderInputBuffer.clear();
            int ret = mStreams[track].readData(dummyFormatHolder, mDecoderInputBuffer, false);
            if (ret == C.RESULT_BUFFER_READ
                    // Double-check if the extractor provided the data to prevent NPE. b/33758354
                    && mDecoderInputBuffer.data != null) {
                if (mCurrentPosition < mDecoderInputBuffer.timeUs) {
                    mCurrentPosition = mDecoderInputBuffer.timeUs;
                }
                if (mMediaPeriod != null) {
                    mMediaPeriod.discardBuffer(mCurrentPosition, false);
                }
                try {
                    Long lastExtractedPositionUs = mLastExtractedPositionUsMap.get(track);
                    if (lastExtractedPositionUs == null) {
                        mLastExtractedPositionUsMap.put(track, mDecoderInputBuffer.timeUs);
                    } else {
                        mLastExtractedPositionUsMap.put(
                                track,
                                Math.max(lastExtractedPositionUs, mDecoderInputBuffer.timeUs));
                    }
                    queueSample(track, conditionVariable);
                } catch (IOException e) {
                    mLastExtractedPositionUsMap.clear();
                    mMetEos = true;
                    mSampleBuffer.setEos();
                }
            } else if (ret == C.RESULT_END_OF_INPUT) {
                mTrackMetEos[track] = true;
                for (int i = 0; i < mTrackMetEos.length; ++i) {
                    if (!mTrackMetEos[i]) {
                        break;
                    }
                    if (i == mTrackMetEos.length - 1) {
                        mMetEos = true;
                        mSampleBuffer.setEos();
                    }
                }
            }
            // TODO: Handle C.RESULT_FORMAT_READ for dynamic resolution change. b/28169263
            return ret;
        }

        private void queueSample(int index, ConditionVariable conditionVariable)
                throws IOException {
            if (mVideoTrackIndex != INVALID_TRACK_INDEX) {
                if (!mVideoTrackMet) {
                    if (index != mVideoTrackIndex) {
                        DecoderInputBuffer sample =
                                new DecoderInputBuffer(
                                        DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
                        sample.setFlags(
                                (mDecoderInputBuffer.isDecodeOnly() ? C.BUFFER_FLAG_DECODE_ONLY : 0)
                                | (mDecoderInputBuffer.isEncrypted() ? C.BUFFER_FLAG_ENCRYPTED : 0)
                                | (mDecoderInputBuffer.isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0));
                        sample.timeUs = mDecoderInputBuffer.timeUs;
                        int size = mDecoderInputBuffer.data.position();
                        sample.ensureSpaceForWrite(size);
                        mDecoderInputBuffer.flip();
                        sample.data.position(0).limit(size);
                        sample.data.put(mDecoderInputBuffer.data);
                        sample.data.flip();
                        mPendingSamples.add(Pair.create(index, sample));
                        return;
                    }
                    mVideoTrackMet = true;
                    mBaseSamplePts = mDecoderInputBuffer.timeUs - INITIAL_AUDIO_BUFFERING_TIME_US;
                    for (Pair<Integer, DecoderInputBuffer> pair : mPendingSamples) {
                        if (pair.second.timeUs >= mBaseSamplePts) {
                            mSampleBuffer.writeSample(pair.first, pair.second, conditionVariable);
                        }
                    }
                    mPendingSamples.clear();
                } else {
                    if (mDecoderInputBuffer.timeUs < mBaseSamplePts && mVideoTrackIndex != index) {
                        return;
                    }
                }
            }
            // Copy the decoder input buffer to pass to sample buffer.
            mDecoderInputBufferDuplicate.clear();
            mDecoderInputBufferDuplicate.setFlags(
                    (mDecoderInputBuffer.isDecodeOnly() ? C.BUFFER_FLAG_DECODE_ONLY : 0)
                            | (mDecoderInputBuffer.isEncrypted() ? C.BUFFER_FLAG_ENCRYPTED : 0)
                            | (mDecoderInputBuffer.isKeyFrame() ? C.BUFFER_FLAG_KEY_FRAME : 0));
            mDecoderInputBufferDuplicate.timeUs = mDecoderInputBuffer.timeUs;
            int size = mDecoderInputBuffer.data.position();
            mDecoderInputBufferDuplicate.ensureSpaceForWrite(size);
            mDecoderInputBuffer.flip();
            mDecoderInputBufferDuplicate.data.position(0);
            mDecoderInputBufferDuplicate.data.put(mDecoderInputBuffer.data);
            long writeStartTimeNs = SystemClock.elapsedRealtimeNanos();
            mSampleBuffer.writeSample(index, mDecoderInputBufferDuplicate, conditionVariable);
            // Checks whether the storage has enough bandwidth for recording samples.
            if (mSampleBuffer.isWriteSpeedSlow(
                    mDecoderInputBufferDuplicate.data.position(),
                    SystemClock.elapsedRealtimeNanos() - writeStartTimeNs)) {
                mSampleBuffer.handleWriteSpeedSlow();
            }
        }
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (mError != null) {
            IOException e = mError;
            mError = null;
            throw e;
        }
    }

    @Override
    public void prepare(Callback callback) throws IOException {
        mCallback = callback;
        if (!mSourceReaderThread.isAlive()) {
            mSourceReaderThread.start();
            mSourceReaderHandler =
                    new Handler(mSourceReaderThread.getLooper(), mSourceReaderWorker);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_PREPARE);
        }
        if (mExceptionOnPrepare != null) {
            IOException e = mExceptionOnPrepare;
            mExceptionOnPrepare = null;
            throw e;
        }
    }

    @Override
    public void getTrackMediaFormat(int track, FormatHolder outMediaFormatHolder) {
        outMediaFormatHolder.format = mTrackGroupArray.get(track).getFormat(0);
    }

    @Override
    public void selectTrack(int index) {
        mSampleBuffer.selectTrack(index);
    }

    @Override
    public void deselectTrack(int index) {
        mSampleBuffer.deselectTrack(index);
    }

    @Override
    public long getBufferedPositionUs() {
        return (mPrepared ? mMediaPeriod.getBufferedPositionUs() : 0);
    }

    @Override
    public long getNextLoadPositionUs() {
        return (mPrepared ? mMediaPeriod.getNextLoadPositionUs() : 0);
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return mSampleBuffer.continueLoading(positionUs);
    }

    @Override
    public void seekTo(long positionUs) {
        mSampleBuffer.seekTo(positionUs);
    }

    @Override
    public int readSample(int track, DecoderInputBuffer sampleHolder) {
        return mSampleBuffer.readSample(track, sampleHolder);
    }

    @Override
    public void release() {
        if (mSourceReaderThread.isAlive()) {
            mSourceReaderHandler.removeCallbacksAndMessages(null);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_RELEASE);
            mSourceReaderThread.quitSafely();
            // Return early in this case so that session worker can start working on the next
            // request as early as it can. The clean up will be done in the reader thread while
            // handling MSG_RELEASE.
        } else {
            cleanUp();
        }
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return mTrackGroupArray;
    }

    private void cleanUp() {
        boolean result = true;
        try {
            if (mSampleBuffer != null) {
                mSampleBuffer.release();
                mSampleBuffer = null;
            }
        } catch (IOException e) {
            result = false;
        }
        notifyCompletionIfNeeded(result);
        setOnCompletionListener(null, null);
    }

    private void notifyCompletionIfNeeded(final boolean result) {
        if (!mOnCompletionCalled.getAndSet(true)) {
            final OnCompletionListener listener = mOnCompletionListener;
            final long lastExtractedPositionUs = getLastExtractedPositionUs();
            if (mOnCompletionListenerHandler != null && mOnCompletionListener != null) {
                mOnCompletionListenerHandler.post(
                        () -> listener.onCompletion(result, lastExtractedPositionUs));
            }
        }
    }

    private long getLastExtractedPositionUs() {
        long lastExtractedPositionUs = Long.MIN_VALUE;
        for (Map.Entry<Integer, Long> entry : mLastExtractedPositionUsMap.entrySet()) {
            if (mVideoTrackIndex != entry.getKey()) {
                lastExtractedPositionUs = Math.max(lastExtractedPositionUs, entry.getValue());
            }
        }
        if (lastExtractedPositionUs == Long.MIN_VALUE) {
            lastExtractedPositionUs = com.google.android.exoplayer.C.UNKNOWN_TIME_US;
        }
        return lastExtractedPositionUs;
    }
}
