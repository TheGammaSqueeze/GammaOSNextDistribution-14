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

import android.media.MediaFormat;
import android.os.Handler;
import android.support.annotation.Nullable;

import com.android.tv.tuner.exoplayer2.buffer.BufferManager;
import com.android.tv.tuner.exoplayer2.buffer.PlaybackBufferListener;
import com.android.tv.tuner.exoplayer2.buffer.RecordingSampleBuffer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that plays a recorded stream without using {@link android.media.MediaExtractor}, since
 * all samples are extracted and stored to the permanent storage already.
 */
public class FileSampleExtractor implements SampleExtractor {
    private static final String TAG = "FileSampleExtractor";
    private static final boolean DEBUG = false;
    private final long mRecordingDurationMs;
    private IOException mOnPrepareException = null;

    private boolean mReleased;
    private final BufferManager mBufferManager;
    private final PlaybackBufferListener mBufferListener;
    private BufferManager.SampleBuffer mSampleBuffer;
    private final RecordingSampleBuffer.Factory mRecordingSampleBufferFactory;
    private TrackGroupArray mTrackGroupArray = new TrackGroupArray();
    private final Runnable mRunnable;
    private Callback mCallback;

    /**
     * Factory for {@link FileSampleExtractor}}.
     *
     * <p>This wrapper class keeps other classes from needing to reference the {@link AutoFactory}
     * generated class.
     */
    public interface Factory {
        FileSampleExtractor create(
                BufferManager bufferManager,
                PlaybackBufferListener bufferListener,
                long durationMs);
    }

    @AutoFactory(implementing = Factory.class)
    public FileSampleExtractor(
            BufferManager bufferManager,
            PlaybackBufferListener bufferListener,
            long durationMs,
            @Provided RecordingSampleBuffer.Factory recordingSampleBufferFactory) {
        mBufferManager = bufferManager;
        mBufferListener = bufferListener;
        mRecordingSampleBufferFactory = recordingSampleBufferFactory;
        mRecordingDurationMs = durationMs;
        mRunnable = () -> {
            try {
                handlePrepare();
            } catch (IOException e) {
                mOnPrepareException = e;
            }
        };
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (mOnPrepareException != null) {
            throw mOnPrepareException;
        }
    }

    @Override
    public void prepare(Callback callback) {
        mCallback = callback;
        mRunnable.run();
    }

    private void handlePrepare() throws IOException {
        List<BufferManager.TrackFormat> trackFormatList = mBufferManager.readTrackInfoFiles();
        if (trackFormatList == null || trackFormatList.isEmpty()) {
            throw new IOException("Cannot find meta files for the recording.");
        }
        List<Format> formats = ImmutableList.copyOf(
                Lists.transform(trackFormatList, tf -> createFormat(tf.mediaFormat)));
        Format videoFormat = Iterables.find(formats, f -> MimeTypes.isVideo(f.sampleMimeType));
        Iterable<TrackGroup> captionTrackGroups = new ArrayList<>();
        if (videoFormat != null) {
            Format textFormat = Format.createTextSampleFormat(
                    /* id= */ null,
                    MimeTypes.APPLICATION_CEA708,
                    /* selectionFlags= */ 0,
                    videoFormat.language,
                    /* drmInitData= */ null);
            captionTrackGroups = ImmutableList.of(new TrackGroup(textFormat));
        }
        Iterable<TrackGroup> trackGroups =
                Iterables.concat(Iterables.transform(formats, TrackGroup::new), captionTrackGroups);
        mTrackGroupArray = new TrackGroupArray(Iterables.toArray(trackGroups, TrackGroup.class));
        mSampleBuffer =
                mRecordingSampleBufferFactory.create(
                        mBufferManager,
                        mBufferListener,
                        true,
                        RecordingSampleBuffer.BUFFER_REASON_RECORDED_PLAYBACK);
        mSampleBuffer.init(Lists.transform(trackFormatList, tf -> tf.trackId), formats);
        mCallback.onPrepared();
    }

    private Format createFormat(MediaFormat mediaFormat) {
        String mimeType = mediaFormat.getString(android.media.MediaFormat.KEY_MIME);
        String language = getOptionalStringV16(mediaFormat, android.media.MediaFormat.KEY_LANGUAGE);
        int maxInputSize =
                getOptionalIntegerV16(mediaFormat, android.media.MediaFormat.KEY_MAX_INPUT_SIZE);
        int width = getOptionalIntegerV16(mediaFormat, android.media.MediaFormat.KEY_WIDTH);
        int height = getOptionalIntegerV16(mediaFormat, android.media.MediaFormat.KEY_HEIGHT);
        int rotationDegrees = getOptionalIntegerV16(mediaFormat, "rotation-degrees");
        int channelCount =
                getOptionalIntegerV16(mediaFormat, android.media.MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate =
                getOptionalIntegerV16(mediaFormat, android.media.MediaFormat.KEY_SAMPLE_RATE);
        ArrayList<byte[]> initializationData = new ArrayList<>();
        for (int i = 0; mediaFormat.containsKey("csd-" + i); i++) {
            ByteBuffer buffer = mediaFormat.getByteBuffer("csd-" + i);
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            initializationData.add(data);
            buffer.flip();
        }
        long durationUs =
                mediaFormat.containsKey(android.media.MediaFormat.KEY_DURATION)
                        ? mediaFormat.getLong(android.media.MediaFormat.KEY_DURATION)
                        : C.TIME_UNSET;
        int pcmEncoding =
                MimeTypes.AUDIO_RAW.equals(mimeType) ? C.ENCODING_PCM_16BIT : Format.NO_VALUE;
        if (MimeTypes.isAudio(mimeType)) {
            return Format.createAudioSampleFormat(
                    null,
                    mimeType,
                    null,
                    Format.NO_VALUE,
                    maxInputSize,
                    channelCount,
                    sampleRate,
                    pcmEncoding,
                    initializationData,
                    null,
                    0,
                    language);
        } else if(MimeTypes.isVideo(mimeType)) {
            return Format.createVideoSampleFormat(
                    null,
                    mimeType,
                    null,
                    Format.NO_VALUE,
                    maxInputSize,
                    width,
                    height,
                    Format.NO_VALUE,
                    initializationData,
                    rotationDegrees,
                    Format.NO_VALUE,
                    null);
        } else if(MimeTypes.isText(mimeType)) {
            return Format.createTextSampleFormat(
                    null,
                    mimeType,
                    null,
                    Format.NO_VALUE,
                    0,
                    language,
                    Format.NO_VALUE,
                    null,
                    durationUs,
                    initializationData);
        } else {
            return Format.createSampleFormat(null, mimeType, durationUs);
        }
    }

    @Nullable
    private static String getOptionalStringV16(MediaFormat mediaFormat, String key) {
        return mediaFormat.containsKey(key) ? mediaFormat.getString(key) : null;
    }

    private static int getOptionalIntegerV16(MediaFormat mediaFormat, String key) {
        return mediaFormat.containsKey(key) ? mediaFormat.getInteger(key) : Format.NO_VALUE;
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return mTrackGroupArray;
    }

    @Override
    public void getTrackMediaFormat(int track, FormatHolder outMediaFormatHolder) {
        outMediaFormatHolder.format = mTrackGroupArray.get(track).getFormat(0);
        outMediaFormatHolder.format.copyWithDrmInitData(null);
    }

    @Override
    public void release() {
        if (!mReleased) {
            if (mSampleBuffer != null) {
                try {
                    mSampleBuffer.release();
                } catch (IOException e) {
                    // Do nothing. Playback ends now.
                }
            }
        }
        mReleased = true;
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
        return C.msToUs(mRecordingDurationMs);
    }

    @Override
    public long getNextLoadPositionUs() {
        return C.TIME_END_OF_SOURCE;
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
    public boolean continueLoading(long positionUs) {
        return mSampleBuffer.continueLoading(positionUs);
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener, Handler handler) {}
}
