/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * The following class decode and render the audio (will be audible), until loadStatus is finished.
 * If input reaches eos, it will rewind the input to start position.
 */
class AudioPlaybackLoad extends CodecDecoderTestBase {
    private final String mDecoderName;
    private final LoadStatus mLoadStatus;

    private long mBasePts;
    private long mMaxPts;
    private AudioTrack mTrack;

    AudioPlaybackLoad(String mime, String testFile, String decoderName, LoadStatus loadStatus) {
        super(mime, testFile);
        mDecoderName = decoderName;
        mLoadStatus = loadStatus;
        mBasePts = 0;
        mMaxPts = 0;
    }

    public void doDecodeAndPlayback() throws Exception {
        MediaFormat format = setUpSource(mTestFile);
        mTrack = createAudioTrack(format);
        mTrack.play();
        mCodec = MediaCodec.createByCodecName(mDecoderName);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, false, false, false);
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        mTrack.pause();
        mTrack.flush();
        mTrack.release();
    }

    private AudioTrack createAudioTrack(MediaFormat format) {
        final int channelMask = getChannelMask(format);
        final int encoding = AudioFormat.ENCODING_PCM_16BIT;
        final int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        final AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();
        final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
        final int bufferSize = 2 * minBufferSize;
        return new AudioTrack.Builder()
                .setBufferSizeInBytes(bufferSize)
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .build();
    }

    private int getChannelMask(MediaFormat format) {
        final int count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (count == 1) {
            return AudioFormat.CHANNEL_OUT_MONO;
        } else if (count == 2) {
            return AudioFormat.CHANNEL_OUT_STEREO;
        } else if (count == 6) {
            return AudioFormat.CHANNEL_OUT_5POINT1;
        }
        return -1;
    }

    // Enqueue input to decoder until loadStatus is finished or unexpected eos is seen.
    @Override
    void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0 || mLoadStatus.isLoadFinished()) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            int size = mExtractor.readSampleData(inputBuffer, 0);
            long pts = mExtractor.getSampleTime();
            mMaxPts = Math.max(mMaxPts, mBasePts + pts);
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, mBasePts + pts, codecFlags);
            if (size > 0 && (codecFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mInputCount++;
            }
            // If eos is reached, seek to start position.
            if (!mExtractor.advance()) {
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                mBasePts = mMaxPts + 1000000L;
            }
        }
    }

    @Override
    void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        final ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
        final byte[] audio = new byte[info.size];
        buffer.clear(); // prepare buffer for reading
        buffer.get(audio);
        mTrack.write(audio, 0, audio.length);
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }
}
