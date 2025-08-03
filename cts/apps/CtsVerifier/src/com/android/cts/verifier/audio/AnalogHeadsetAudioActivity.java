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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;

@CddTest(requirement = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")
public class AnalogHeadsetAudioActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = AnalogHeadsetAudioActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private AudioManager    mAudioManager;
    private boolean mIsTVOrFixedVolume;

    // UI
    private TextView mHasPortQueryText;
    private Button mHasAnalogPortYesBtn;
    private Button mHasAnalogPortNoBtn;

    private Button mPlayButton;
    private Button mStopButton;
    private Button mPlaybackSuccessBtn;
    private Button mPlaybackFailBtn;
    private TextView mPlaybackStatusTxt;

    private TextView mHeadsetNameText;

    private TextView mButtonsPromptTxt;
    private TextView mHeadsetHookText;
    private TextView mHeadsetVolUpText;
    private TextView mHeadsetVolDownText;

    // Devices
    private AudioDeviceInfo mHeadsetDeviceInfo;
    private boolean mPlaybackSuccess;

    private static final int HEADSETSTATE_UNKNOWN = 0;
    private static final int HEADSETSTATE_HAS   = 2;
    private static final int HEADSETSTATE_NONE  = 1;
    private int mHeadsetState = HEADSETSTATE_UNKNOWN;

    // Buttons
    private boolean mHasHeadsetHook;
    private boolean mHasPlayPause;
    private boolean mHasVolUp;
    private boolean mHasVolDown;

    private TextView mResultsTxt;

    // Player
    protected boolean mIsPlaying = false;

    // Mega Player
    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    JavaPlayer mAudioPlayer;

    // ReportLog Schema
    private static final String SECTION_ANALOG_HEADSET = "analog_headset_activity";
    private static final String KEY_HAS_HEADSET_PORT = "has_headset_port";
    private static final String KEY_CLAIMS_HEADSET_PORT = "claims_headset_port";
    private static final String KEY_HEADSET_CONNECTED = "headset_connected";
    private static final String KEY_KEYCODE_HEADSETHOOK = "keycode_headset_hook";
    private static final String KEY_KEYCODE_PLAY_PAUSE = "keycode_play_pause";
    private static final String KEY_KEYCODE_VOLUME_UP = "keycode_volume_up";
    private static final String KEY_KEYCODE_VOLUME_DOWN = "keycode_volume_down";

    public AnalogHeadsetAudioActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_headset_audio_activity);

        mAudioManager = getSystemService(AudioManager.class);
        mIsTVOrFixedVolume = AudioSystemFlags.isTV(this) || mAudioManager.isVolumeFixed();

        mHeadsetNameText = (TextView)findViewById(R.id.headset_analog_name);

        // Analog Port?
        mHasPortQueryText = (TextView)findViewById(R.id.analog_headset_query) ;
        mHasAnalogPortYesBtn = (Button)findViewById(R.id.headset_analog_port_yes);
        mHasAnalogPortYesBtn.setOnClickListener(this);
        mHasAnalogPortNoBtn = (Button)findViewById(R.id.headset_analog_port_no);
        mHasAnalogPortNoBtn.setOnClickListener(this);

        // Player Controls.
        mPlayButton = (Button)findViewById(R.id.headset_analog_play);
        mPlayButton.setOnClickListener(this);
        mStopButton = (Button)findViewById(R.id.headset_analog_stop);
        mStopButton.setOnClickListener(this);
        mPlaybackStatusTxt = (TextView)findViewById(R.id.analog_headset_playback_status);

        // Play Status
        mPlaybackSuccessBtn = (Button)findViewById(R.id.headset_analog_play_yes);
        mPlaybackSuccessBtn.setOnClickListener(this);
        mPlaybackFailBtn = (Button)findViewById(R.id.headset_analog_play_no);
        mPlaybackFailBtn.setOnClickListener(this);
        mPlaybackSuccessBtn.setEnabled(false);
        mPlaybackFailBtn.setEnabled(false);

        // Keycodes
        mButtonsPromptTxt = (TextView)findViewById(R.id.analog_headset_keycodes_prompt);
        mHeadsetHookText = (TextView)findViewById(R.id.headset_keycode_headsethook);
        mHeadsetVolUpText = (TextView)findViewById(R.id.headset_keycode_volume_up);
        mHeadsetVolDownText = (TextView)findViewById(R.id.headset_keycode_volume_down);

        if (mIsTVOrFixedVolume) {
            mButtonsPromptTxt.setVisibility(View.GONE);
            mHeadsetHookText.setVisibility(View.GONE);
            mHeadsetVolUpText.setVisibility(View.GONE);
            mHeadsetVolDownText.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.headset_keycodes)).setVisibility(View.GONE);
        }

        mResultsTxt = (TextView)findViewById(R.id.headset_results);

        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());

        showKeyMessagesState();
        enablePlayerButtons(false, false);

        setInfoResources(R.string.analog_headset_test, mIsTVOrFixedVolume
                ? R.string.analog_headset_test_info_tv : R.string.analog_headset_test_info, -1);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onStop() {
        stopPlay();
        super.onStop();
    }

    private String generateStateString() {
        Resources res = getResources();
        StringBuilder sb = new StringBuilder();

        if (mHeadsetState == HEADSETSTATE_UNKNOWN) {
            sb.append("Headset Unknown.");
        } else if (mHeadsetState == HEADSETSTATE_NONE) {
            sb.append(res.getString(R.string.analog_headset_reportnojack));
        } else if (mHeadsetDeviceInfo == null) {
            sb.append(res.getString(R.string.analog_headset_connect_headset));
        } else if (!mPlaybackSuccess) {
            sb.append(res.getString(R.string.analog_headset_play_audio));
        } else if (!mIsTVOrFixedVolume
                && mHeadsetDeviceInfo != null
                && (!(mHasHeadsetHook || mHasPlayPause)
                        || !mHasVolUp || !mHasVolDown)) {
            sb.append(res.getString(R.string.analog_headset_test_buttons));
        } else {
            sb.append(res.getString(R.string.analog_headset_pass));
        }
        return sb.toString();
    }

    private boolean calculatePass() {
        if (mHeadsetState == HEADSETSTATE_UNKNOWN) {
            mResultsTxt.setText(getResources().getString(R.string.analog_headset_headset_unknown));
            return false;
        } else if (mHeadsetState == HEADSETSTATE_NONE) {
            mResultsTxt.setText(getResources().getString(R.string.analog_headset_pass_noheadset));
            return true;
        } else if (!isReportLogOkToPass()) {
            mResultsTxt.setText(getResources().getString(R.string.audio_general_reportlogtest));
            return false;
        } else {
            boolean pass = mHeadsetDeviceInfo != null
                    && mPlaybackSuccess
                    && (mIsTVOrFixedVolume
                        || ((mHasHeadsetHook || mHasPlayPause) && mHasVolUp && mHasVolDown));
            if (pass) {
                mResultsTxt.setText(getResources().getString(R.string.analog_headset_pass));
            }
            mResultsTxt.setText(generateStateString());
            return pass;
        }
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_ANALOG_HEADSET);
    }

    //
    // Reporting
    //
    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_HAS_HEADSET_PORT,
                mHeadsetDeviceInfo != null ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_HEADSET_PORT,
                mPlaybackSuccess ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_HEADSET_CONNECTED,
                mHeadsetDeviceInfo != null ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_HEADSETHOOK,
                mHasHeadsetHook ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_PLAY_PAUSE,
                mHasPlayPause ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_VOLUME_UP,
                mHasVolUp ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_VOLUME_DOWN,
                mHasVolDown ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    private void reportPlaybackStatus(boolean success) {
        // [C-1-1] MUST support audio playback to stereo headphones
        // and stereo headsets with a microphone.
        mPlaybackSuccess = success;

        mPlaybackSuccessBtn.setEnabled(success);
        mPlaybackFailBtn.setEnabled(!success);

        getPassButton().setEnabled(calculatePass());

        if (mPlaybackSuccess) {
            int strID = mHeadsetDeviceInfo != null
                    ? R.string.analog_headset_press_buttons : R.string.analog_headset_no_buttons;
            mButtonsPromptTxt.setText(getResources().getString(strID));
        }
    }

    //
    // UI
    //
    private void showConnectedDevice() {
        if (mHeadsetDeviceInfo != null) {
            mHeadsetNameText.setText(getResources().getString(
                    R.string.analog_headset_headset_connected));
        } else {
            mHeadsetNameText.setText(getResources().getString(R.string.analog_headset_no_headset));
        }
    }

    private void setHeadsetConfirmationButtons() {
        switch (mHeadsetState) {
            case HEADSETSTATE_UNKNOWN:
                mHasAnalogPortYesBtn.setEnabled(true);
                mHasAnalogPortNoBtn.setEnabled(true);
                break;
            case HEADSETSTATE_NONE:
                mHasAnalogPortYesBtn.setEnabled(false);
                mHasAnalogPortNoBtn.setEnabled(true);
                break;
            case HEADSETSTATE_HAS:
                mHasAnalogPortYesBtn.setEnabled(true);
                mHasAnalogPortNoBtn.setEnabled(false);
                break;
        }
    }

    private void resetButtonMessages() {
        mHasHeadsetHook = false;
        mHasPlayPause = false;
        mHasVolUp = false;
        mHasVolDown = false;
        showKeyMessagesState();
    }

    private void enablePlayerButtons(boolean playEnabled, boolean stopEnabled) {
        mPlayButton.setEnabled(playEnabled);
        mStopButton.setEnabled(stopEnabled);
    }

    private void showKeyMessagesState() {
        mHeadsetHookText.setTextColor((mHasHeadsetHook || mHasPlayPause)
                ? Color.WHITE : Color.GRAY);
        mHeadsetVolUpText.setTextColor(mHasVolUp ? Color.WHITE : Color.GRAY);
        mHeadsetVolDownText.setTextColor(mHasVolDown ? Color.WHITE : Color.GRAY);
    }

    //
    // Player
    //
    protected void setupPlayer() {
        StreamBase.setup(this);
        int numBufferFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_JAVA);

        //
        // Allocate the source provider for the sort of signal we want to play
        //
        AudioSourceProvider sourceProvider = new SinAudioSourceProvider();
        try {
            PlayerBuilder builder = (PlayerBuilder) new PlayerBuilder()
                    .setChannelCount(NUM_CHANNELS)
                    .setSampleRate(SAMPLE_RATE)
                    .setNumExchangeFrames(numBufferFrames)
                    .setRouteDevice(mHeadsetDeviceInfo);
            mAudioPlayer = (JavaPlayer)builder
                    // choose one or the other of these for a Java or an Oboe player
                    .setPlayerType(PlayerBuilder.TYPE_JAVA)
                    // .setPlayerType(PlayerBuilder.TYPE_OBOE)
                    .setSourceProvider(sourceProvider)
                    .build();
        } catch (PlayerBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaPlayer build.");
        }
    }

    protected void startPlay() {
        if (!mIsPlaying && mHeadsetDeviceInfo != null) {
            setupPlayer();
            mAudioPlayer.startStream();
            mIsPlaying = true;
        }
        enablePlayerButtons(false, true);
    }

    protected void stopPlay() {
        if (mIsPlaying) {
            mAudioPlayer.stopStream();
            mAudioPlayer.teardownStream();
            mIsPlaying = false;

            mPlaybackStatusTxt.setText(getResources().getString(
                    R.string.analog_headset_playback_query));
        }
        enablePlayerButtons(true, false);
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.headset_analog_port_no) {
            mHeadsetState = HEADSETSTATE_NONE;
            setHeadsetConfirmationButtons();
            getPassButton().setEnabled(calculatePass());
        } else if (id == R.id.headset_analog_port_yes) {
            mHeadsetState = HEADSETSTATE_HAS;
            setHeadsetConfirmationButtons();
            getPassButton().setEnabled(calculatePass());
        } else if (id == R.id.headset_analog_play) {
            startPlay();
        } else if (id == R.id.headset_analog_stop) {
            stopPlay();
            mPlaybackSuccessBtn.setEnabled(true);
            mPlaybackFailBtn.setEnabled(true);
        } else if (id == R.id.headset_analog_play_yes) {
            reportPlaybackStatus(true);
        } else if (id == R.id.headset_analog_play_no) {
            reportPlaybackStatus(false);
        }
    }

    //
    // Devices
    //
    private void scanPeripheralList(AudioDeviceInfo[] devices) {
        mHeadsetDeviceInfo = null;
        for (AudioDeviceInfo devInfo : devices) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mHeadsetDeviceInfo = devInfo;
                break;
            }
        }

        if (mHeadsetDeviceInfo != null) {
            mHeadsetState = HEADSETSTATE_HAS;
            enablePlayerButtons(true, false);
        } else {
            stopPlay(); // might have disconnected while playing
            mPlaybackSuccess = false;
            enablePlayerButtons(false, false);
        }

        setHeadsetConfirmationButtons();

        showConnectedDevice();

        mResultsTxt.setText(generateStateString());
    }

    private class ConnectListener extends AudioDeviceCallback {
        /*package*/ ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            resetButtonMessages();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            resetButtonMessages();
        }
    }

    //
    // Keycodes
    //
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mHeadsetDeviceInfo != null) {
            // we have an analog headset plugged in
            switch (keyCode) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    mHasHeadsetHook = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    mHasPlayPause = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_VOLUME_UP:
                    mHasVolUp = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    mHasVolDown = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
