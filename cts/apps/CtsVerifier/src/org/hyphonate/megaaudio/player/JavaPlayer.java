/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.player;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.common.StreamState;

/**
 * Implementation of abstract Player class implemented for the Android Java-based audio playback
 * API, i.e. AudioTrack.
 */
public class JavaPlayer extends Player {
    @SuppressWarnings("unused")
    private static final String TAG = JavaPlayer.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    /*
     * Player infrastructure
     */
    /* The AudioTrack for playing the audio stream */
    private AudioTrack mAudioTrack;

    /*
     * Data buffers
     */
    /** The Burst Buffer. This is the buffer we fill with audio and feed into the AudioTrack. */
    private float[] mAudioBuffer;

    // Player-specific extension

    /**
     * @return The underlying Java API AudioTrack object
     */
    public AudioTrack getAudioTrack() { return mAudioTrack; }

    /**
     * Constructs a JavaPlayer object. Create and sets up the AudioTrack for playback.
     * @param builder   Provides the attributes for the underlying AudioTrack.
     * @param sourceProvider The AudioSource object providing audio data to play.
     */
    public JavaPlayer(PlayerBuilder builder, AudioSourceProvider sourceProvider) {
        super(sourceProvider);
        mNumExchangeFrames = -1;   // TODO need error defines

        setupStream(builder);
    }

    @Override
    public int getSharingMode() {
        // JAVA Audio API does not support a sharing mode
        return BuilderBase.SHARING_MODE_NOTSUPPORTED;
    }

    @Override
    public int getChannelCount() {
        return mAudioTrack != null ? mAudioTrack.getChannelCount() : -1;
    }

    /**
     * Allocates the array for the burst buffer.
     */
    private void allocBurstBuffer() {
        if (LOG) {
            Log.i(TAG, "allocBurstBuffer() mNumExchangeFrames:" + mNumExchangeFrames);
        }
        // pad it by 1 frame. This allows some sources to not have to worry about
        // handling the end-of-buffer edge case. i.e. a "Guard Point" for interpolation.
        mAudioBuffer = new float[(mNumExchangeFrames + 1) * mChannelCount];
    }

    //
    // Attributes
    //
    @Override
    public int getRoutedDeviceId() {
        if (mAudioTrack != null) {
            AudioDeviceInfo routedDevice = mAudioTrack.getRoutedDevice();
            return routedDevice != null
                    ? routedDevice.getId() : BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        } else {
            return BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        }
    }

    /*
     * State
     */
    private int setupStream(PlayerBuilder builder) {
        mChannelCount = builder.getChannelCount();
        mSampleRate = builder.getSampleRate();
        mNumExchangeFrames = builder.getNumExchangeFrames();
        mPerformanceMode = builder.getJavaPerformanceMode();
        int routeDeviceId = builder.getRouteDeviceId();
        if (LOG) {
            Log.i(TAG, "setupStream()");
            Log.i(TAG, "  chans:" + mChannelCount);
            Log.i(TAG, "  rate: " + mSampleRate);
            Log.i(TAG, "  frames: " + mNumExchangeFrames);
            Log.i(TAG, "  perf mode: " + mPerformanceMode);
            Log.i(TAG, "  route device: " + routeDeviceId);
        }

        mAudioSource = mSourceProvider.getJavaSource();
        mAudioSource.init(mNumExchangeFrames, mChannelCount);

        try {
            AudioFormat.Builder formatBuilder = new AudioFormat.Builder();
            formatBuilder.setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(mSampleRate)
                // setChannelIndexMask() won't give us a FAST_PATH
                // .setChannelIndexMask(
                //      StreamBase.channelCountToIndexMask(mChannelCount))
                .setChannelMask(StreamBase.channelCountToOutPositionMask(mChannelCount));
            AudioTrack.Builder audioTrackBuilder = new AudioTrack.Builder();
            audioTrackBuilder.setAudioFormat(formatBuilder.build())
                .setPerformanceMode(mPerformanceMode);
            mAudioTrack = audioTrackBuilder.build();

            allocBurstBuffer();
            mAudioTrack.setPreferredDevice(builder.getRouteDevice());

            if (LOG) {
                Log.i(TAG, "  mAudioTrack.getBufferSizeInFrames(): "
                        + mAudioTrack.getBufferSizeInFrames());
                Log.i(TAG, "  mAudioTrack.getBufferCapacityInFrames() :"
                        + mAudioTrack.getBufferCapacityInFrames());
            }
        }  catch (UnsupportedOperationException ex) {
            Log.e(TAG, "Couldn't open AudioTrack: " + ex);
            return ERROR_UNSUPPORTED;
        } catch (java.lang.IllegalArgumentException ex) {
            Log.e(TAG, "Invalid arguments to AudioTrack.Builder: " + ex);
            return ERROR_UNSUPPORTED;
        }

        return OK;
    }

    @Override
    public int teardownStream() {
        if (LOG) {
            Log.i(TAG, "teardownStream()");
        }
        stopStream();

        waitForStreamThreadToExit();

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }

        mChannelCount = 0;
        mSampleRate = 0;

        //TODO - Retrieve errors from above
        return OK;
    }

    /**
     * Allocates the underlying AudioTrack and begins Playback.
     * @return True if the stream is successfully started.
     *
     * This method returns when the start operation is complete, but before the first
     * call to the AudioSource.pull() method.
     */
    @Override
    public int startStream() {
        if (mAudioTrack == null) {
            return ERROR_INVALID_STATE;
        }
        waitForStreamThreadToExit(); // just to be sure.

        mStreamThread = new Thread(new StreamPlayerRunnable(), "StreamPlayer Thread");
        mPlaying = true;
        mStreamThread.start();

        return OK;
    }

    /**
     * Marks the stream for stopping on the next callback from the underlying system.
     *
     * Returns immediately, though a call to AudioSource.pull() may be in progress.
     */
    @Override
    public int stopStream() {
        mPlaying = false;
        return OK;
    }

    /**
     * @return See StreamState constants
     */
    public int getStreamState() {
        //TODO - track state so we can return something meaningful here.
        return StreamState.UNKNOWN;
    }

    /**
     * @return The last error callback result (these must match Oboe). See Oboe constants
     */
    public int getLastErrorCallbackResult() {
        //TODO - track errors so we can return something meaningful here.
        return ERROR_UNKNOWN;
    }

    /**
     * Gets a timestamp from the audio stream
     * @param timestamp
     * @return
     */
    public boolean getTimestamp(AudioTimestamp timestamp) {
        return mPlaying ? mAudioTrack.getTimestamp(timestamp) : false;
    }

    //
    // StreamPlayerRunnable
    //
    /**
     * Implements the <code>run</code> method for the playback thread.
     * Gets initial audio data and starts the AudioTrack. Then continuously provides audio data
     * until the flag <code>mPlaying</code> is set to false (in the stop() method).
     */
    private class StreamPlayerRunnable implements Runnable {
        @Override
        public void run() {
            final int mNumPlaySamples = mNumExchangeFrames * mChannelCount;
            if (LOG) {
                Log.i(TAG, "mNumPlaySamples: " + mNumPlaySamples);
            }
            mAudioTrack.play();
            while (mPlaying) {
                mAudioSource.pull(mAudioBuffer, mNumExchangeFrames, mChannelCount);

                onPull();

                int numSamplesWritten = mAudioTrack.write(
                        mAudioBuffer, 0, mNumPlaySamples, AudioTrack.WRITE_BLOCKING);
                if (numSamplesWritten < 0) {
                    // error
                    Log.e(TAG, "AudioTrack write error - numSamplesWritten: " + numSamplesWritten);
                    stopStream();
                } else if (numSamplesWritten < mNumPlaySamples) {
                    // end of stream
                    if (LOG) {
                        Log.i(TAG, "Stream Complete.");
                    }
                    stopStream();
                }
            }
        }
    }
}
