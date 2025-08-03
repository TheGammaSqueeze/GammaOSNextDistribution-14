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
import android.media.AudioRecord;
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
import org.hyphonate.megaaudio.recorder.JavaRecorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

/*
 * Tests AudioRecord (re)Routing messages.
 */
public class AudioInputRoutingNotificationsActivity extends AudioWiredDeviceBaseActivity {
    private static final String TAG = "AudioInputRoutingNotificationsActivity";

    Context mContext;

    int mNumRoutingNotifications;

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    private JavaRecorder mAudioRecorder;
    private AudioRecordRoutingChangeListener mRouteChangeListener;
    private boolean mIsRecording;

    // ignore messages sent as a consequence of starting the player
    private static final int NUM_IGNORE_MESSAGES = 2;

    boolean mRoutingNotificationReceived;

    // ReportLog schema
    protected static final String SECTION_INPUT_ROUTING = "audio_in_routing_notifications";

    @Override
    void startAudio() {
        if (mAudioRecorder == null) {
            return; // failed to create the recorder
        }

        if (!mIsRecording) {
            mNumRoutingNotifications = 0;

            mAudioRecorder.startStream();

            AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
            audioRecord.addOnRoutingChangedListener(mRouteChangeListener,
                    new Handler());

            mIsRecording = true;
            enableTestButtons(false);
        }
    }

    @Override
    void stopAudio() {
        if (mAudioRecorder == null) {
            return; // failed to create the recorder
        }

        if (mIsRecording) {
            mAudioRecorder.stopStream();

            AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
            audioRecord.removeOnRoutingChangedListener(mRouteChangeListener);

            mIsRecording = false;
            enableTestButtons(true);
        }
    }

    private class AudioRecordRoutingChangeListener implements AudioRecord.OnRoutingChangedListener {
        public void onRoutingChanged(AudioRecord audioRecord) {
            // Starting recording triggers routing message, so ignore the first one.
            mNumRoutingNotifications++;
            if (mNumRoutingNotifications <= NUM_IGNORE_MESSAGES) {
                return;
            }

            TextView textView =
                    (TextView)findViewById(R.id.audio_routingnotification_audioRecord_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_recordRoutingMsg);
            AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
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
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_INPUT_ROUTING);
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
        setContentView(R.layout.audio_input_routingnotifications_test);

        mContext = this;

        connectProcessUI();

        enableTestButtons(false);

        // Setup Recorder
        int numExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);

        try {
            RecorderBuilder builder = new RecorderBuilder();
            builder.setRecorderType(RecorderBuilder.TYPE_JAVA)
                .setAudioSinkProvider(new NopAudioSinkProvider())
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(SAMPLE_RATE)
                .setNumExchangeFrames(numExchangeFrames);
            mAudioRecorder = (JavaRecorder) builder.build();
        } catch (RecorderBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaRecorder build.");
        }

        mRouteChangeListener = new AudioRecordRoutingChangeListener();
        AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
        audioRecord.addOnRoutingChangedListener(mRouteChangeListener, new Handler());

        // "Honor System" buttons
        super.setup();
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_input_routingnotifications_test,
                R.string.audio_input_routingnotification_instructions, -1);

        DisplayUtils.setKeepScreenOn(this, true);
    }
}
