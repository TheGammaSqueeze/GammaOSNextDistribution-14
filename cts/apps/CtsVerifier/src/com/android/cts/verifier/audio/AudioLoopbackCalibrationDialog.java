/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

// MegaAudio
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

class AudioLoopbackCalibrationDialog extends Dialog
        implements OnClickListener, AppCallback, AdapterView.OnItemSelectedListener {
    public static final String TAG = "AudioLoopbackCalibrationDialog";

    private Context mContext;
    private AudioManager mAudioManager;

    private DuplexAudioManager mDuplexAudioManager;

    private AudioSourceProvider mLeftSineSourceProvider;
    private AudioSourceProvider mRightSineSourceProvider;

    private AudioSinkProvider mAudioSinkProvider;
    private AppCallback mAudioCallbackHandler;

    private boolean mPlaying;
    private int mNumDisplayChannels;
    private WaveScopeView mWaveView = null;

    private WebView mInfoPanel;

    Spinner mInputsSpinner;
    Spinner mOutputsSpinner;

    AudioDeviceInfo[] mInputDevices;
    AudioDeviceInfo[] mOutputDevices;

    AudioDeviceInfo mSelectedInputDevice;
    AudioDeviceInfo mSelectedOutputDevice;

    AudioLoopbackCalibrationDialog(Context context) {
        super(context);

        mContext = context;

        mAudioManager = context.getSystemService(AudioManager.class);

        mAudioCallbackHandler = this;

        mLeftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        mRightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);
        mAudioSinkProvider =
                new AppCallbackAudioSinkProvider(mAudioCallbackHandler);

        mDuplexAudioManager = new DuplexAudioManager(null, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(mContext.getString(R.string.audio_datapaths_calibratetitle));

        setContentView(R.layout.audio_loopback_calibration_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);
        mWaveView.setDisplayMaxMagnitudes(true);
        mWaveView.setDisplayLimits(true);
        mWaveView.setDisplayZero(true);

        findViewById(R.id.audio_calibration_left).setOnClickListener(this);
        findViewById(R.id.audio_calibration_right).setOnClickListener(this);
        findViewById(R.id.audio_calibration_stop).setOnClickListener(this);
        findViewById(R.id.audio_calibration_done).setOnClickListener(this);

        // Setup the Devices spinners
        mInputsSpinner = (Spinner) findViewById(R.id.input_devices_spinner);
        mInputsSpinner.setOnItemSelectedListener(this);

        mOutputsSpinner = (Spinner) findViewById(R.id.output_devices_spinner);
        mOutputsSpinner.setOnItemSelectedListener(this);

        mAudioManager.registerAudioDeviceCallback(new AudioDeviceConnectionCallback(), null);

        mInfoPanel = (WebView) findViewById(R.id.audio_calibration_info);
        mInfoPanel.loadUrl("file:///android_asset/html/AudioCalibrationInfo.html");
    }

    ArrayAdapter fillAdapter(AudioDeviceInfo[] deviceInfos) {
        ArrayAdapter arrayAdapter =
                new ArrayAdapter(mContext, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        arrayAdapter.add(mContext.getString(R.string.audio_loopback_calibrate_default));
        if (deviceInfos != null) {
            for (AudioDeviceInfo devInfo : deviceInfos) {
                String devTypeString = AudioDeviceUtils.getDeviceTypeName(devInfo.getType());
                String devNameString = devInfo.getAddress();
                StringBuilder sb = new StringBuilder();
                sb.append(devTypeString);
                // We won't filter out "meaningless" address strings
                // like "0" and "card=1;device=0".
                if (devNameString.length() != 0) {
                    sb.append(" (").append(devNameString).append(")");
                }
                arrayAdapter.add(sb.toString());
            }
        }
        return arrayAdapter;
    }

    @Override
    public void onStop() {
        stopAudio();
    }

    private static final int CHANNEL_LEFT = 0;
    private static final int CHANNEL_RIGHT = 1;

    void startAudio(int channel) {
        stopAudio();

        AudioSourceProvider sourceProvider =
                channel == CHANNEL_LEFT ? mLeftSineSourceProvider : mRightSineSourceProvider;

        // Player
        mDuplexAudioManager.setSources(sourceProvider, mAudioSinkProvider);
        mDuplexAudioManager.setPlayerRouteDevice(mSelectedOutputDevice);
        mDuplexAudioManager.setNumPlayerChannels(2);

        // Recorder
        mDuplexAudioManager.setRecorderRouteDevice(mSelectedInputDevice);
        mNumDisplayChannels = 2;
        if (mSelectedInputDevice != null && AudioDeviceUtils.isMicDevice(mSelectedInputDevice)) {
            mNumDisplayChannels = 1;
        }
        Log.i(TAG, "mNumDisplayChannels:" + mNumDisplayChannels);
        mWaveView.setNumChannels(mNumDisplayChannels);
        mDuplexAudioManager.setNumRecorderChannels(mNumDisplayChannels);

        // Open the streams.
        // Note AudioSources and AudioSinks get allocated at this point
        if (mDuplexAudioManager.buildStreams(BuilderBase.TYPE_OBOE, BuilderBase.TYPE_OBOE)
                    ==  StreamBase.OK
                && mDuplexAudioManager.start() == StreamBase.OK) {
            mPlaying = true;
        } else {
            mPlaying = false;
        }
    }

    void stopAudio() {
        if (mPlaying) {
            mDuplexAudioManager.stop();
            mPlaying = false;
        }
    }

    //
    // OnClickListener
    //
    public void onClick(View v) {
        if (v.getId() == R.id.audio_calibration_left) {
            startAudio(CHANNEL_LEFT);
        } else if (v.getId() == R.id.audio_calibration_right) {
            startAudio(CHANNEL_RIGHT);
        } else if (v.getId() == R.id.audio_calibration_stop) {
            stopAudio();
        } else if (v.getId() == R.id.audio_calibration_done) {
            dismiss();
        }
    }

    //
    // MegaAudio AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        mWaveView.setPCMFloatBuff(audioData, mNumDisplayChannels, numFrames);
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        void stateChangeHandler() {
            stopAudio();

            mInputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            mInputsSpinner.setAdapter(fillAdapter(mInputDevices));

            mOutputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            mOutputsSpinner.setAdapter(fillAdapter(mOutputDevices));
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            stateChangeHandler();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            stateChangeHandler();
        }
    }

    //
    // AdapterView.OnItemSelectedListener overrides
    //
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        stopAudio();
        if (parent.getId() == R.id.input_devices_spinner) {
            if (position == 0) {
                mSelectedInputDevice = null;
            } else {
                mSelectedInputDevice = mInputDevices[position - 1];
            }
        } else {
            if (position == 0) {
                mSelectedOutputDevice = null;
            } else {
                mSelectedOutputDevice = mOutputDevices[position - 1];
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // NOP
    }
}
