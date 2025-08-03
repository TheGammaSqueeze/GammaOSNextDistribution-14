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
package com.android.tv.tuner.exoplayer2;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;

/** {@link MediaSource} that extracts sample data using a {@link SampleExtractor}. */

public final class MpegTsMediaSource extends BaseMediaSource {

    private static final String TAG = "MpegTsMediaSource";

    private final SampleExtractor mSampleExtractor;

    /**
     * Creates a new sample source that extracts samples using {@code mSampleExtractor}.
     *
     * @param sampleExtractor a sample extractor for accessing media samples
     */
    public MpegTsMediaSource(SampleExtractor sampleExtractor) {
        mSampleExtractor = Assertions.checkNotNull(sampleExtractor);
    }

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
        refreshSourceInfo(new SinglePeriodTimeline(0, false, false), null);
    }

    @Override
    protected void releaseSourceInternal() {
        // Do nothing
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() {
        // Do nothing
    }

    @Override
    public MpegTsMediaPeriod createPeriod(
            MediaPeriodId id,
            Allocator allocator,
            long startPositionUs) {
        return new MpegTsMediaPeriod(mSampleExtractor);
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((MpegTsMediaPeriod) mediaPeriod).release();
    }
}
