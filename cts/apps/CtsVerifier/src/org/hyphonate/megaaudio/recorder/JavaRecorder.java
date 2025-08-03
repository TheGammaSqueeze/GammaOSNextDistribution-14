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
package org.hyphonate.megaaudio.recorder;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.common.StreamState;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

/**
 * Implementation of abstract Recorder class implemented for the Android Java-based audio record
 * API, i.e. AudioRecord.
 */
public class JavaRecorder extends Recorder {
    @SuppressWarnings("unused")
    private static final String TAG = JavaRecorder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    /**
     * The buffer to receive the recorder samples
     */
    private float[] mRecorderBuffer;

    /* The AudioRecord for recording the audio stream */
    private AudioRecord mAudioRecord = null;

    private AudioSink mAudioSink;

    @Override
    public int getRoutedDeviceId() {
        if (mAudioRecord != null) {
            AudioDeviceInfo routedDevice = mAudioRecord.getRoutedDevice();
            return routedDevice != null
                    ? routedDevice.getId() : BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        } else {
            return BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        }
    }

    /**
     * The listener to receive notifications of recording events
     */
    private JavaSinkHandler mListener = null;

    public JavaRecorder(RecorderBuilder builder, AudioSinkProvider sinkProvider) {
        super(sinkProvider);
        setupStream(builder);
    }

    //
    // Attributes
    //
    @Override
    public int getSharingMode() {
        // JAVA Audio API does not support a sharing mode
        return BuilderBase.SHARING_MODE_NOTSUPPORTED;
    }

    @Override
    public int getChannelCount() {
        return mAudioRecord != null ?  mAudioRecord.getChannelCount() : -1;
    }

    /**
     * The buff to receive the recorder samples
     */
    public float[] getFloatBuffer() {
        return mRecorderBuffer;
    }

    // JavaRecorder-specific extension
    public AudioRecord getAudioRecord() {
        return mAudioRecord;
    }

    private int setupStream(RecorderBuilder builder) {
        mChannelCount = builder.getChannelCount();
        mSampleRate = builder.getSampleRate();
        mNumExchangeFrames = builder.getNumExchangeFrames();
        mSharingMode = builder.getSharingMode();
        mPerformanceMode = builder.getPerformanceMode();
        mInputPreset = builder.getInputPreset();

        if (LOG) {
            Log.i(TAG, "setupStream()");
            Log.i(TAG, "  chans:" + mChannelCount);
            Log.i(TAG, "  rate: " + mSampleRate);
            Log.i(TAG, "  frames: " + mNumExchangeFrames);
            Log.i(TAG, "  perf mode: " + mPerformanceMode);
            Log.i(TAG, "  route device: " + builder.getRouteDeviceId());
            Log.i(TAG, "  preset: " + mInputPreset);
        }

        try {
//            int bufferSizeInBytes = mNumExchangeFrames * mChannelCount
//                    * sampleSizeInBytes(AudioFormat.ENCODING_PCM_FLOAT);
//            Log.i(TAG, "  bufferSizeInBytes:" + bufferSizeInBytes);
//            Log.i(TAG, "  (in frames)" + (bufferSizeInBytes / 4 / mChannelCount));

            AudioFormat.Builder formatBuilder = new AudioFormat.Builder();
            formatBuilder.setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(mSampleRate)
                    .setChannelIndexMask(StreamBase.channelCountToIndexMask(mChannelCount));

            AudioRecord.Builder recordBuilder = new AudioRecord.Builder();
            recordBuilder.setAudioFormat(formatBuilder.build())
                    /*.setBufferSizeInBytes(bufferSizeInBytes)*/;
            if (mInputPreset != Recorder.INPUT_PRESET_NONE) {
                recordBuilder.setAudioSource(mInputPreset);
            }
            mAudioRecord = recordBuilder.build();
            mNumExchangeFrames = mAudioRecord.getBufferSizeInFrames();
            if (LOG) {
                Log.i(TAG, "  mAudioRecord.getBufferSizeInFrames(): "
                        + mAudioRecord.getBufferSizeInFrames());
            }
            mAudioRecord.setPreferredDevice(builder.getRouteDevice());

            mRecorderBuffer = new float[mNumExchangeFrames * mChannelCount];

            if (mSinkProvider == null) {
                mSinkProvider = new NopAudioSinkProvider();
            }
            mAudioSink = mSinkProvider.allocJavaSink();
            mAudioSink.init(mNumExchangeFrames, mChannelCount);
            mListener = new JavaSinkHandler(this, mAudioSink, Looper.getMainLooper());
            return OK;
        } catch (UnsupportedOperationException ex) {
            if (LOG) {
                Log.e(TAG, "Couldn't open AudioRecord: " + ex);
            }
            return ERROR_UNSUPPORTED;
        } catch (java.lang.IllegalArgumentException ex) {
            if (LOG) {
                Log.e(TAG, "Invalid arguments to AudioRecord.Builder: " + ex);
            }
            return ERROR_UNSUPPORTED;
        }
    }

    @Override
    public int teardownStream() {
        if (LOG) {
            Log.i(TAG, "teardownStream()");
        }
        stopStream();

        waitForStreamThreadToExit();

        if (mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }

        mChannelCount = 0;
        mSampleRate = 0;

        //TODO Retrieve errors from above
        return OK;
    }

    @Override
    public int startStream() {
        if (LOG) {
            Log.i(TAG, "startStream() mAudioRecord:" + mAudioRecord);
        }
        if (mAudioRecord == null) {
            return ERROR_INVALID_STATE;
        }
        if (mListener != null) {
            mListener.sendEmptyMessage(JavaSinkHandler.MSG_START);
        }

        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException ex) {
            Log.e(TAG, "startRecording exception: " + ex);
        }

        waitForStreamThreadToExit(); // just to be sure.

        mStreamThread = new Thread(new RecorderRunnable(), "JavaRecorder Thread");
        mRecording = true;
        mStreamThread.start();

        return OK;
    }

    /**
     * Marks the stream for stopping on the next callback from the underlying system.
     *
     * Returns immediately, though a call to AudioSource.push() may be in progress.
     */
    @Override
    public int stopStream() {
        mRecording = false;
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

    // @Override
    // Used in JavaSinkHandler
    public float[] getDataBuffer() {
        return mRecorderBuffer;
    }

    /*
     * Recorder Thread
     */
    /**
     * Implements the <code>run</code> method for the record thread.
     * Starts the AudioRecord, then continuously reads audio data
     * until the flag <code>mRecording</code> is set to false (in the stop() method).
     */
    private class RecorderRunnable implements Runnable {
        @Override
        public void run() {
            final int numRecordSamples = mNumExchangeFrames * mChannelCount;
            if (LOG) {
                Log.i(TAG, "numRecordSamples: " + numRecordSamples);
            }

            int numReadSamples = 0;
            while (mRecording) {
                numReadSamples = mAudioRecord.read(
                        mRecorderBuffer, 0, numRecordSamples, AudioRecord.READ_BLOCKING);
                if (numReadSamples < 0) {
                    // error
                    if (LOG) {
                        Log.e(TAG, "AudioRecord write error - numReadSamples: " + numReadSamples);
                    }
                    stopStream();
                } else if (numReadSamples < numRecordSamples) {
                    // got less than requested?
                    if (LOG) {
                        Log.e(TAG, "AudioRecord Underflow: " + numReadSamples +
                                " vs. " + numRecordSamples);
                    }
                    stopStream();
                }

                if (mListener != null) {
                    // TODO: on error or underrun we may be send bogus data.
                    mListener.sendEmptyMessage(JavaSinkHandler.MSG_BUFFER_FILL);
                }
            }

            if (mListener != null) {
                // TODO: on error or underrun we may be send bogus data.
                Message message = new Message();
                message.what = JavaSinkHandler.MSG_STOP;
                message.arg1 = numReadSamples;
                mListener.sendMessage(message);
            }
            mAudioRecord.stop();
        }
    }
}
