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
package org.hyphonate.megaaudio.duplex;

import android.media.AudioDeviceInfo;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;

public class DuplexAudioManager {
    @SuppressWarnings("unused")
    private static final String TAG = DuplexAudioManager.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    // Player
    //TODO - explain these constants
    private int mNumPlayerChannels = 2;
    private int mPlayerSampleRate = 48000;
    private int mNumPlayerBurstFrames;

    private Player mPlayer;
    private AudioSourceProvider mSourceProvider;
    private AudioDeviceInfo mPlayerSelectedDevice;

    // Recorder
    private int mNumRecorderChannels = 2;
    private int mRecorderSampleRate = 48000;
    private int mNumRecorderBufferFrames;

    private Recorder mRecorder;
    private AudioSinkProvider mSinkProvider;
    private AudioDeviceInfo mRecorderSelectedDevice;
    private int mInputPreset = Recorder.INPUT_PRESET_NONE;

    public DuplexAudioManager(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
        setSources(sourceProvider, sinkProvider);
    }

    /**
     * Specify the source providers for the source and sink.
     * @param sourceProvider The AudioSourceProvider for the output stream
     * @param sinkProvider The AudioSinkProvider for the input stream.
     */
    public void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
        mSourceProvider = sourceProvider;
        mSinkProvider = sinkProvider;

        mPlayerSampleRate =  StreamBase.getSystemSampleRate();
        mRecorderSampleRate = StreamBase.getSystemSampleRate();
    }

    //
    // Be careful using these, they will change after setupStreams is called.
    //
    public Player getPlayer() {
        return mPlayer;
    }
    public Recorder getRecorder() {
        return mRecorder;
    }

    public void setPlayerSampleRate(int sampleRate) {
        mPlayerSampleRate = sampleRate;
    }

    public void setRecordererSampleRate(int sampleRate) {
        mPlayerSampleRate = sampleRate;
    }

    public void setPlayerRouteDevice(AudioDeviceInfo deviceInfo) {
        mPlayerSelectedDevice = deviceInfo;
    }

    public void setRecorderRouteDevice(AudioDeviceInfo deviceInfo) {
        mRecorderSelectedDevice = deviceInfo;
    }

    public void setNumPlayerChannels(int numChannels) {
        mNumPlayerChannels = numChannels;
    }

    public void setNumRecorderChannels(int numChannels) {
        mNumRecorderChannels = numChannels;
    }
    public void setRecorderSampleRate(int sampleRate) {
        mRecorderSampleRate = sampleRate;
    }

    private int mPlayerSharingMode = BuilderBase.SHARING_MODE_SHARED;
    private int mRecorderSharingMode = BuilderBase.SHARING_MODE_SHARED;

    public void setPlayerSharingMode(int mode) {
        mPlayerSharingMode = mode;
    }

    public void setRecorderSharingMode(int mode) {
        mRecorderSharingMode = mode;
    }

    public int getPlayerChannelCount() {
        return mPlayer != null ? mPlayer.getChannelCount() : -1;
    }

    public int getRecorderChannelCount() {
        return mRecorder != null ? mRecorder.getChannelCount() : -1;

    }

    /**
     * Specifies the input preset to use for the recorder.
     * @param preset
     */
    public void setInputPreset(int preset) {
        mInputPreset = preset;
    }

    /**
     * Initializes (but does not start) the player and recorder streams.
     * @param playerType    The API constant for the player
     * @param recorderType  The API constant for the recorder
     * @return a StreamBase status code specifying the result.
     */
    public int buildStreams(int playerType, int recorderType) {
        // Recorder
        if ((recorderType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            try {
//                mNumRecorderBufferFrames = Recorder.calcMinBufferFramesStatic(
//                        mNumRecorderChannels, mRecorderSampleRate);
                mNumRecorderBufferFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);
                RecorderBuilder builder = (RecorderBuilder) new RecorderBuilder()
                        .setRecorderType(recorderType)
                        .setAudioSinkProvider(mSinkProvider)
                        .setInputPreset(mInputPreset)
                        .setSharingMode(mRecorderSharingMode)
                        .setRouteDevice(mRecorderSelectedDevice)
                        .setSampleRate(mRecorderSampleRate)
                        .setChannelCount(mNumRecorderChannels)
                        .setNumExchangeFrames(mNumRecorderBufferFrames);
                mRecorder = builder.build();
            } catch (RecorderBuilder.BadStateException ex) {
                Log.e(TAG, "Recorder - BadStateException" + ex);
                return StreamBase.ERROR_UNSUPPORTED;
            }
        }

        // Player
        if ((playerType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            try {
                mNumPlayerBurstFrames = StreamBase.getNumBurstFrames(playerType);
                PlayerBuilder builder = (PlayerBuilder) new PlayerBuilder()
                        .setPlayerType(playerType)
                        .setSourceProvider(mSourceProvider)
                        .setSampleRate(mPlayerSampleRate)
                        .setChannelCount(mNumPlayerChannels)
                        .setSharingMode(mPlayerSharingMode)
                        .setRouteDevice(mPlayerSelectedDevice)
                        .setNumExchangeFrames(mNumPlayerBurstFrames)
                        .setPerformanceMode(BuilderBase.PERFORMANCE_MODE_LOWLATENCY);
                mPlayer = builder.build();
            } catch (PlayerBuilder.BadStateException ex) {
                Log.e(TAG, "Player - BadStateException" + ex);
                return StreamBase.ERROR_UNSUPPORTED;
            } catch (Exception ex) {
                Log.e(TAG, "Uncaught Error in Player Setup for DuplexAudioManager ex:" + ex);
            }
        }

        return StreamBase.OK;
    }

    public int start() {
        if (LOG) {
            Log.i(TAG, "start()...");
        }

        int result = StreamBase.OK;
        if (mPlayer != null && (result = mPlayer.startStream()) != StreamBase.OK) {
            if (LOG) {
                Log.i(TAG, "  player fails result:" + result);
            }
            return result;
        }

        if (mRecorder != null && (result = mRecorder.startStream()) != StreamBase.OK) {
            if (LOG) {
                Log.i(TAG, "  recorder fails result:" + result);
            }
            // Shut down
            stop();

            return result;
        }

        if (LOG) {
            Log.i(TAG, "  result:" + result);
        }
        return result;
    }

    public int stop() {
        if (LOG) {
            Log.i(TAG, "stop()");
        }
        int playerResult = StreamBase.OK;
        if (LOG) {
            Log.i(TAG, "  mPlayer:" + mPlayer);
        }
        if (mPlayer != null) {
            int result1 = mPlayer.stopStream();
            int result2 = mPlayer.teardownStream();
            playerResult = result1 != StreamBase.OK ? result1 : result2;
        }

        int recorderResult = StreamBase.OK;
        if (LOG) {
            Log.i(TAG, "  mRecorder:" + mRecorder);
        }
        if (mRecorder != null) {
            int result1 = mRecorder.stopStream();
            int result2 = mRecorder.teardownStream();
            recorderResult = result1 != StreamBase.OK ? result1 : result2;
        }

        int ret = playerResult != StreamBase.OK ? playerResult : recorderResult;

        if (LOG) {
            Log.i(TAG, "  returns:" + ret);
        }
        return ret;
    }

    public int getNumPlayerBufferFrames() {
        return mPlayer != null ? mPlayer.getSystemBurstFrames() : 0;
    }

    public int getNumRecorderBufferFrames() {
        return mRecorder != null ? mRecorder.getSystemBurstFrames() : 0;
    }

    public AudioSource getAudioSource() {
        return mPlayer != null ? mPlayer.getAudioSource() : null;
    }

    /**
     * Don't call this until the streams are started
     * @return true if both player and recorder are routed to the devices specified
     * with setRecorderRouteDevice() and setPlayerRouteDevice().
     */
    public boolean validateRouting() {
        if (mPlayerSelectedDevice == null && mRecorderSelectedDevice == null) {
            return true;
        }

        if (mPlayer == null || !mPlayer.isPlaying()
                || mRecorder == null || !mRecorder.isRecording()) {
            return false;
        }

        if (mPlayerSelectedDevice != null
                && mPlayer.getRoutedDeviceId() != mPlayerSelectedDevice.getId()) {
            return false;
        }

        if (mRecorderSelectedDevice != null
                && mRecorder.getRoutedDeviceId() != mRecorderSelectedDevice.getId()) {
            return false;
        }

        // Everything checks out OK.
        return true;
    }

    /**
     * Don't call this until the streams are started
     * @return true if both player and recorder are using the specified sharing mode set with
     * with setPlayerSharingMode() and setRecorderSharingMode().
     */
    public boolean validateSharingModes() {
        boolean playerOK = false;
        if (mPlayer != null) {
            int sharingMode = mPlayer.getSharingMode();
            playerOK = sharingMode == mPlayerSharingMode
                    || sharingMode == BuilderBase.SHARING_MODE_NOTSUPPORTED;
        }

        boolean recorderOK = false;
        if (mRecorder != null) {
            int sharingMode = mRecorder.getSharingMode();
            recorderOK = sharingMode == mRecorderSharingMode
                    || sharingMode == BuilderBase.SHARING_MODE_NOTSUPPORTED;
        }

        return playerOK && recorderOK;
    }
}
