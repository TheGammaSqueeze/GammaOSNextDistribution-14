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

import android.os.Handler;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.TrackGroupArray;

import java.io.IOException;

/**
 * Extractor for reading track metadata and samples stored in tracks.
 *
 * <p>Call {@link #prepare} until it returns {@code true}, then access track metadata via {@link
 * #getTrackGroups and {@link #getTrackMediaFormat}.
 *
 * <p>Pass indices of tracks to read from to {@link #selectTrack}. A track can later be deselected
 * by calling {@link #deselectTrack}. It is safe to select/deselect tracks after reading sample data
 * or seeking. Initially, all tracks are deselected.
 *
 * <p>Call {@link #release()} when the extractor is no longer needed to free resources.
 */
// TODO: Should be replaced by {@link com.google.android.exoplayer2.source.MediaPeriod}.
public interface SampleExtractor {

    /**
     * If the extractor is currently having difficulty preparing or loading samples, then this
     * method throws the underlying error. Otherwise does nothing.
     *
     * @throws IOException The underlying error.
     */
    void maybeThrowError() throws IOException;

    /**
     * Prepares the extractor for reading track metadata and samples.
     *
     * @param callback Callback to receive updates from this sample extractor, including being
     *                 notified when preparation completes.
     * @throws IOException thrown if the source can't be read
     */
    void prepare(Callback callback) throws IOException;

    /** Returns track information about all tracks that can be selected. */
    TrackGroupArray getTrackGroups();

    /** Selects the track at {@code index} for reading sample data. */
    void selectTrack(int index);

    /** Deselects the track at {@code index}, so no more samples will be read from that track. */
    void deselectTrack(int index);

    /**
     * Returns an estimate of the position up to which data is buffered.
     *
     * <p>This method should not be called until after the extractor has been successfully prepared.
     *
     * @return an estimate of the absolute position in microseconds up to which data is buffered, or
     *     {@link C#TIME_END_OF_SOURCE} if data is buffered to the end of the stream, or
     *     {@link C#TIME_UNSET} if no estimate is available.
     */
    long getBufferedPositionUs();

    /**
     * Returns the next load time, or {@link C#TIME_END_OF_SOURCE} if loading has finished.
     *
     * <p>This method is only called after the period has been prepared. It may be called when no
     * tracks are selected.
     */
    long getNextLoadPositionUs();

    /**
     * Seeks to the specified time in microseconds.
     *
     * <p>This method should not be called until after the extractor has been successfully prepared.
     *
     * @param positionUs the seek position in microseconds
     */
    void seekTo(long positionUs);

    /** Stores the {@link Format} of {@code track}. */
    void getTrackMediaFormat(int track, FormatHolder outMediaFormatHolder);

    /**
     * Reads the next sample in the track at index {@code track} into {@code sampleHolder},
     * returning {@link C#RESULT_BUFFER_READ} if it is available.
     *
     * <p>Advances to the next sample if a sample was read.
     *
     * @param track the index of the track from which to read a sample
     * @param sampleHolder the holder for read sample data, if {@link C#RESULT_BUFFER_READ} is
     *     returned
     * @return {@link C#RESULT_BUFFER_READ} if a sample was read into {@code sampleHolder}, or
     *     {@link C#RESULT_END_OF_INPUT} if the last samples in all tracks have been read, or
     *     {@link C#RESULT_NOTHING_READ} if the sample cannot be read immediately as it is not
     *     loaded.
     */
    int readSample(int track, DecoderInputBuffer sampleHolder);

    /** Releases resources associated with this extractor. */
    void release();

    /** Indicates to the source that it should still be buffering data. */
    boolean continueLoading(long positionUs);

    /**
     * Sets OnCompletionListener for notifying the completion of SampleExtractor.
     *
     * @param listener the OnCompletionListener
     * @param handler the {@link Handler} for {@link Handler#post(Runnable)} of OnCompletionListener
     */
    void setOnCompletionListener(OnCompletionListener listener, Handler handler);

    /** The listener for SampleExtractor being completed. */
    interface OnCompletionListener {

        /**
         * Called when sample extraction is completed.
         *
         * @param result {@code true} when the extractor is finished without an error, {@code false}
         *     otherwise (storage error, weak signal, being reached at EoS prematurely, etc.)
         * @param lastExtractedPositionUs the last extracted position when extractor is completed
         */
        void onCompletion(boolean result, long lastExtractedPositionUs);
    }

    /** A callback to be notified of {@link SampleExtractor} events. */
    interface Callback {

        /** Called when preparation completes. */
        void onPrepared();
    }
}
