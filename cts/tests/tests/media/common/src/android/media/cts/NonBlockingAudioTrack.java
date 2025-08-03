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

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class for playing audio by using audio track.
 * {@link #write(byte[], int, int)} and {@link #write(short[], int, int)} methods will
 * block until all data has been written to system. In order to avoid blocking, this class
 * caculates available buffer size first then writes to audio sink.
 */
public class NonBlockingAudioTrack {
    private static final String TAG = NonBlockingAudioTrack.class.getSimpleName();

    private static final long END_OF_STREAM_PTS = Long.MAX_VALUE;

    private static class QueueElement {
        ByteBuffer data;
        int size;
        long pts;
    }

    private AudioTrack mAudioTrack;
    private int mSampleRate;
    private int mNumBytesQueued = 0;
    private AtomicInteger mTotalBytesWritten = new AtomicInteger(0);
    private LinkedList<QueueElement> mQueue = new LinkedList<QueueElement>();
    private boolean mStopped;
    private int mBufferSizeInBytes;
    private AtomicBoolean mStopWriting = new AtomicBoolean(false);

    /**
     * An offset (in nanoseconds) to add to presentation timestamps fed to the {@link AudioTrack}.
     * This is used to simulate desynchronization between tracks.
     */
    private AtomicLong mAudioOffsetNs = new AtomicLong(0);

    public NonBlockingAudioTrack(int sampleRate, int channelCount, boolean hwAvSync,
                    int audioSessionId) {
        int channelConfig;
        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            default:
                throw new IllegalArgumentException();
        }

        int minBufferSize =
            AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);

        mBufferSizeInBytes = 2 * minBufferSize;

        if (!hwAvSync) {
            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSizeInBytes,
                    AudioTrack.MODE_STREAM);
        }
        else {
            // build AudioTrack using Audio Attributes and FLAG_HW_AV_SYNC
            AudioAttributes audioAttributes = (new AudioAttributes.Builder())
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .setFlags(AudioAttributes.FLAG_HW_AV_SYNC)
                            .build();
            AudioFormat audioFormat = (new AudioFormat.Builder())
                            .setChannelMask(channelConfig)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .build();
            mAudioTrack = new AudioTrack(audioAttributes, audioFormat, mBufferSizeInBytes,
                                    AudioTrack.MODE_STREAM, audioSessionId);
        }

        mSampleRate = sampleRate;
    }

    public long getAudioTimeUs() {
        int numFramesPlayed = mAudioTrack.getPlaybackHeadPosition();

        return (numFramesPlayed * 1000000L) / mSampleRate;
    }

    public AudioTimestamp getTimestamp() {
        AudioTimestamp timestamp = new AudioTimestamp();
        mAudioTrack.getTimestamp(timestamp);
        return timestamp;
    }

    public int getNumBytesQueued() {
        return mNumBytesQueued;
    }

    public void play() {
        mStopped = false;
        mAudioTrack.play();
    }

    public void setEndOfStream() {
        QueueElement element = new QueueElement();
        element.pts  = END_OF_STREAM_PTS;
        mQueue.add(element);
    }

    public void stop() {
        if (mQueue.isEmpty()) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
        } else {
            mStopped = true;
        }
    }

    public void setStopWriting(boolean stop) {
        mStopWriting.set(stop);
    }

    public void setAudioOffsetNs(long audioOffsetNs) {
        mAudioOffsetNs.set(audioOffsetNs);
    }

    public void pause() {
        mAudioTrack.pause();
    }

    public void flush() {
        if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        mAudioTrack.flush();
        mQueue.clear();
        mNumBytesQueued = 0;
        mStopped = false;
    }

    public void release() {
        mQueue.clear();
        mNumBytesQueued = 0;
        mAudioTrack.release();
        mAudioTrack = null;
        mStopped = false;
    }

    public void process() {
        while (!mQueue.isEmpty()) {
            QueueElement element = mQueue.peekFirst();
            if (mStopWriting.get()) {
                break;
            }

            if (element.pts == END_OF_STREAM_PTS) {
                // For tunnel mode, when an audio PTS gap is encountered, silence is rendered
                // during the gap. As such, it's necessary to fade down the audio to avoid a
                // bad user experience. This necessitates that the Audio HAL holds onto the
                // last audio frame and delays releasing it to the output device until the
                // subsequent audio frame is seen so that it knows whether there's a PTS gap
                // or not. When the end-of-stream is reached, this means that the last audio
                // frame has not been rendered yet. So, in order to release the last audio
                // frame, a signal must be sent to the Audio HAL so the last frame gets
                // released.
                int written = mAudioTrack.write(ByteBuffer.allocate(0), 0,
                                                AudioTrack.WRITE_NON_BLOCKING,
                                                END_OF_STREAM_PTS);
                if (written < 0) {
                   throw new RuntimeException("AudioTrack.write failed (" + written + ")");
                }
                mQueue.removeFirst();
                break;
            }

            int written = mAudioTrack.write(element.data, element.size,
                    AudioTrack.WRITE_NON_BLOCKING, element.pts + mAudioOffsetNs.get());
            if (written < 0) {
                throw new RuntimeException("AudioTrack.write failed (" + written + ")");
            }

            mTotalBytesWritten.addAndGet(written);
            mNumBytesQueued -= written;
            element.size -= written;
            if (element.size != 0) {
                break;
            }
            mQueue.removeFirst();
        }
        if (mStopped) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
            mStopped = false;
        }
    }

    public int getFramesWritten() {
        if (mAudioTrack == null) {
            return -1;
        }
        return mTotalBytesWritten.get() / mAudioTrack.getFormat().getFrameSizeInBytes();
    }

    public int getPlayState() {
        return mAudioTrack.getPlayState();
    }

    public void write(ByteBuffer data, int size, long pts) {
        QueueElement element = new QueueElement();
        element.data = data;
        element.size = size;
        element.pts  = pts;

        // accumulate size written to queue
        mNumBytesQueued += size;
        mQueue.add(element);
    }

    /** Returns the underlying {@code AudioTrack}. */
    public AudioTrack getAudioTrack() {
        return mAudioTrack;
    }
}
