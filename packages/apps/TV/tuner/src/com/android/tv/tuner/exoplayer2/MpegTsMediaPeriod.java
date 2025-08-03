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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.Assertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A {@link MediaPeriod} that extracts data using an {@link SampleExtractor}. */
/* package */ final class MpegTsMediaPeriod implements MediaPeriod, SampleExtractor.Callback {

    private static final int TRACK_STATE_DISABLED = 0;
    private static final int TRACK_STATE_ENABLED = 1;
    private static final int TRACK_STATE_FORMAT_SENT = 2;

    private final SampleExtractor mExtractor;
    private final ArrayList<SampleStreamImpl> mSampleStreams = new ArrayList<>();
    private final List<Integer> mTrackStates = new ArrayList<>();
    private final List<Boolean> mPendingDiscontinuities = new ArrayList<>();

    private boolean mPrepared;
    private long mLastSeekPositionUs;
    private long mPendingSeekPositionUs;
    private Callback mCallback;
    private IOException mExceptionOnPrepare;

    public MpegTsMediaPeriod(SampleExtractor extractor) {
        this.mExtractor = extractor;
    }

    @Override
    public void prepare(Callback callback, long positionUs) {
        mCallback = callback;
        try {
            mExtractor.prepare(this);
        } catch (IOException e) {
            mExceptionOnPrepare = e;
        }
    }

    private void enable(int track) {
        Assertions.checkState(mPrepared);
        Assertions.checkState(mTrackStates.get(track) == TRACK_STATE_DISABLED);
        mTrackStates.set(track, TRACK_STATE_ENABLED);
        mExtractor.selectTrack(track);
    }

    private void disable(int track) {
        Assertions.checkState(mPrepared);
        Assertions.checkState(mTrackStates.get(track) != TRACK_STATE_DISABLED);
        mExtractor.deselectTrack(track);
        mPendingDiscontinuities.set(track, false);
        mTrackStates.set(track, TRACK_STATE_DISABLED);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
        if (mExceptionOnPrepare != null) {
            IOException e = mExceptionOnPrepare;
            mExceptionOnPrepare = null;
            throw e;
        }
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return mExtractor.getTrackGroups();
    }

    @Override
    public long selectTracks(
            TrackSelection[] selections,
            boolean[] mayRetainStreamFlags,
            SampleStream[] streams,
            boolean[] streamResetFlags,
            long positionUs) {
        TrackGroupArray trackGroups = mExtractor.getTrackGroups();
        for (int i = 0; i < selections.length; i++) {
            if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                SampleStreamImpl stream = (SampleStreamImpl) streams[i];
                disable(stream.mIndex);
                mSampleStreams.remove(stream);
                streams[i] = null;
            }
            if (streams[i] == null && selections[i] != null) {
                int index = trackGroups.indexOf(selections[i].getTrackGroup());
                SampleStreamImpl stream = new SampleStreamImpl(index);
                mSampleStreams.add(stream);
                streams[i] = stream;
                streamResetFlags[i] = true;
                enable(index);
            }
        }
        seekToUsInternal(positionUs, positionUs != 0);
        return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs, boolean toKeyframe) {
        // Handled by extractor
    }

    @Override
    public void reevaluateBuffer(long positionUs) {
        // Do nothing.
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return mExtractor.continueLoading(positionUs);
    }

    @Override
    public long readDiscontinuity() {
        boolean notifyDiscontinuity = false;
        for (int i = 0; i < mPendingDiscontinuities.size(); i++) {
            if (mPendingDiscontinuities.get(i)) {
                mPendingDiscontinuities.set(i, false);
                notifyDiscontinuity = true;
            }
        }
        return (notifyDiscontinuity ? mLastSeekPositionUs : C.TIME_UNSET);
    }

    @Override
    public long getNextLoadPositionUs() {
        return mExtractor.getNextLoadPositionUs();
    }

    @Override
    public long getBufferedPositionUs() {
        return mExtractor.getBufferedPositionUs();
    }

    @Override
    public long seekToUs(long positionUs) {
        for (int i = 0; i < mSampleStreams.size(); i++) {
            mSampleStreams.get(i).reset();
        }
        seekToUsInternal(positionUs, false);
        return positionUs;
    }

    private void seekToUsInternal(long positionUs, boolean force) {
        // Unless forced, avoid duplicate calls to the underlying extractor's seek method
        // in the case that there have been no interleaving calls to readSample.
        if (force || mPendingSeekPositionUs != positionUs) {
            mLastSeekPositionUs = positionUs;
            mPendingSeekPositionUs = positionUs;
            mExtractor.seekTo(positionUs);
            for (int i = 0; i < mTrackStates.size(); ++i) {
                if (mTrackStates.get(i) != TRACK_STATE_DISABLED) {
                    mPendingDiscontinuities.set(i, true);
                }
            }
        }
    }

    @Override
    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return positionUs;
    }

    @Override
    public void onPrepared() {
        mPrepared = true;
        int trackCount = mExtractor.getTrackGroups().length;
        mTrackStates.clear();
        mPendingDiscontinuities.clear();
        for (int i = 0; i < trackCount; ++i) {
            mTrackStates.add(i, TRACK_STATE_DISABLED);
            mPendingDiscontinuities.add(i, false);
        }
        mCallback.onPrepared(this);
    }

    public void release() {
        mExtractor.release();
    }

    private final class SampleStreamImpl implements SampleStream {

        private static final int STREAM_STATE_SEND_FORMAT = 0;
        private static final int STREAM_STATE_SEND_SAMPLE = 1;
        private static final int STREAM_STATE_END_OF_STREAM = 2;
        private final int mIndex;

        private int streamState;

        SampleStreamImpl(int index) {
            mIndex = index;
        }

        void reset() {
            if (streamState == STREAM_STATE_END_OF_STREAM) {
                streamState = STREAM_STATE_SEND_SAMPLE;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void maybeThrowError() throws IOException {
            mExtractor.maybeThrowError();
        }

        @Override
        public int readData(
                FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
            Assertions.checkState(mPrepared);
            Assertions.checkState(mTrackStates.get(mIndex) != TRACK_STATE_DISABLED);
            if (mPendingDiscontinuities.get(mIndex)) {
                return C.RESULT_NOTHING_READ;
            }
            if (requireFormat || mTrackStates.get(mIndex) != TRACK_STATE_FORMAT_SENT) {
                mExtractor.getTrackMediaFormat(mIndex, formatHolder);
                mTrackStates.set(mIndex, TRACK_STATE_FORMAT_SENT);
                return C.RESULT_FORMAT_READ;
            }
            mPendingSeekPositionUs = C.TIME_UNSET;
            return mExtractor.readSample(mIndex, buffer);
        }

        @Override
        public int skipData(long positionUs) {
            if (positionUs > 0 && streamState != STREAM_STATE_END_OF_STREAM) {
                streamState = STREAM_STATE_END_OF_STREAM;
                return 1;
            }
            return 0;
        }
    }
}
