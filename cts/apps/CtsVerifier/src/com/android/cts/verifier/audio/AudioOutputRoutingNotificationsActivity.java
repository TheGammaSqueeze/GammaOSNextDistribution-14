/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;

/**
 * Tests AudioTrack and AudioRecord (re)Routing messages.
 */
public class AudioOutputRoutingNotificationsActivity extends AudioWiredDeviceBaseActivity {
    private static final String TAG = "AudioOutputRoutingNotificationsActivity";

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    Context mContext;

    int mNumRoutingNotifications;

    // ignore messages sent as a consequence of starting the player
    private static final int NUM_IGNORE_MESSAGES = 1;

    // Mega Player
    private JavaPlayer mAudioPlayer;
    private AudioTrackRoutingChangeListener mRoutingChangeListener;
    private boolean mIsPlaying;

    private boolean mInitialRoutingMessageHandled;

    boolean mRoutingNotificationReceived;

    // ReportLog schema
    private static final String SECTION_OUTPUT_ROUTING = "audio_out_routing_notifications";

    @Override
    void startAudio() {
        if (mAudioPlayer == null) {
            return; // failed to create the player
        }
        if (!mIsPlaying) {
            mNumRoutingNotifications = 0;

            mAudioPlayer.startStream();

            AudioTrack audioTrack = mAudioPlayer.getAudioTrack();
            audioTrack.addOnRoutingChangedListener(mRoutingChangeListener,
                    new Handler());

            mIsPlaying = true;

            enableTestButtons(false);
        }
        Log.i(TAG, "  ... done startAudio()");
    }

    @Override
    void stopAudio() {
        if (mAudioPlayer == null) {
            return; // failed to create the player
        }
        if (mIsPlaying) {
            mAudioPlayer.stopStream();

            AudioTrack audioTrack = mAudioPlayer.getAudioTrack();
            audioTrack.removeOnRoutingChangedListener(mRoutingChangeListener);

            mIsPlaying = false;

            enableTestButtons(true);
        }
    }

    private class AudioTrackRoutingChangeListener implements AudioTrack.OnRoutingChangedListener {
        public void onRoutingChanged(AudioTrack audioTrack) {
            // Starting playback triggers a message, so ignore the first one.
            mNumRoutingNotifications++;
            if (mNumRoutingNotifications <= NUM_IGNORE_MESSAGES) {
                return;
            }

            TextView textView =
                (TextView)findViewById(R.id.audio_routingnotification_audioTrack_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_trackRoutingMsg);
            AudioDeviceInfo routedDevice = audioTrack.getRoutedDevice();
            mConnectedPeripheralName = AudioDeviceUtils.formatDeviceName(routedDevice);
            textView.setText(msg + ": " + mConnectedPeripheralName);
            mRoutingNotificationReceived = true;
            stopAudio();
            calculatePass();
        }
    }

    @Override
    protected void calculatePass() {
        getPassButton().setEnabled(isReportLogOkToPass()
                && mRoutingNotificationReceived || !mSupportsWiredPeripheral);
        TextView tv = ((TextView) findViewById(R.id.audio_routingnotification_testresult));
        if (!isReportLogOkToPass()) {
            tv.setText(getResources().getString(R.string.audio_general_reportlogtest));
        } else if (mRoutingNotificationReceived) {
            tv.setText("Test PASSES - Routing notification received");
        } else if (!mSupportsWiredPeripheral) {
            tv.setText("Test PASSES - No peripheral support");
        } else {
            tv.setText("");
        }
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_OUTPUT_ROUTING);
    }

    @Override
    public void recordTestResults() {
        super.recordTestResults();

        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_ROUTING_RECEIVED,
                mRoutingNotificationReceived ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_output_routingnotifications_test);

        mContext = this;

        connectProcessUI();

        enableTestButtons(false);

        // Setup Player
        //
        // Allocate the source provider for the sort of signal we want to play
        //
        int numExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);
        try {
            PlayerBuilder builder = new PlayerBuilder();
            builder.setSourceProvider(new SinAudioSourceProvider())
                .setPlayerType(PlayerBuilder.TYPE_JAVA)
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(SAMPLE_RATE)
                .setNumExchangeFrames(numExchangeFrames);
            mAudioPlayer = (JavaPlayer) builder.build();
        } catch (PlayerBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaPlayer build.");
        }

        mRoutingChangeListener = new AudioTrackRoutingChangeListener();

        // "Honor System" buttons
        super.setup();
        setInfoResources(R.string.audio_output_routingnotifications_test,
                R.string.audio_output_routingnotification_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        DisplayUtils.setKeepScreenOn(this, true);
    }
}
