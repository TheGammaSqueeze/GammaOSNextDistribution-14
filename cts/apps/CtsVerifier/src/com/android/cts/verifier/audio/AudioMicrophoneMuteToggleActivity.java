/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test for manual verification of microphone privacy hardware switches
 */
public class AudioMicrophoneMuteToggleActivity extends PassFailButtons.Activity {

    public enum Status {
        START, RECORDING, DONE, PLAYER
    }

    private static final String TAG = "AudioMicrophoneMuteToggleActivity";

    private Status mStatus = Status.START;
    private TextView mInfoText;
    private Button mRecorderButton;

    private int mAudioSource = -1;
    private int mRecordRate = 0;

    // keys for report log
    private static final String SECTION_MIC_MUTE_TOGGLE = "audio_mic_mute_toggle";
    private static final String KEY_REC_RATE = "rec_rate";
    private static final String KEY_AUDIO_SOURCE = "audio_source";

    public AudioMicrophoneMuteToggleActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mic_hw_toggle);
        setInfoResources(R.string.audio_mic_toggle_test, R.string.audio_mic_toggle_test_info, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mInfoText = findViewById(R.id.info_text);
        mInfoText.setMovementMethod(new ScrollingMovementMethod());
        mInfoText.setText(R.string.audio_mic_toggle_test_instruction1);

        mRecorderButton = findViewById(R.id.recorder_button);
        mRecorderButton.setEnabled(true);

        final AudioRecordHelper audioRecorder = AudioRecordHelper.getInstance();
        mRecordRate = audioRecorder.getSampleRate();

        mRecorderButton.setOnClickListener(new View.OnClickListener() {
            private WavAnalyzerTask mWavAnalyzerTask = null;

            private void stopRecording() {
                audioRecorder.stop();
                mInfoText.append(getString(R.string.audio_mic_toggle_test_analyzing));
                mWavAnalyzerTask = new WavAnalyzerTask(audioRecorder.getByte());
                mWavAnalyzerTask.execute();
                mStatus = Status.DONE;
            }

            @Override
            public void onClick(View v) {
                switch (mStatus) {
                    case START:
                        mInfoText.append("Recording at " + mRecordRate + "Hz using ");
                        mAudioSource = audioRecorder.getAudioSource();
                        switch (mAudioSource) {
                            case MediaRecorder.AudioSource.MIC:
                                mInfoText.append("MIC");
                                break;
                            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                                mInfoText.append("VOICE_RECOGNITION");
                                break;
                            default:
                                mInfoText.append("UNEXPECTED " + mAudioSource);
                                break;
                        }
                        mInfoText.append("\n");
                        mStatus = Status.RECORDING;

                        mRecorderButton.setEnabled(false);
                        audioRecorder.start();

                        final View finalV = v;
                        new Thread() {
                            @Override
                            public void run() {
                                double recordingDuration_millis = (1000 * (2.5
                                        + Common.PREFIX_LENGTH_S
                                        + Common.PAUSE_BEFORE_PREFIX_DURATION_S
                                        + Common.PAUSE_AFTER_PREFIX_DURATION_S
                                        + Common.PIP_NUM * (Common.PIP_DURATION_S
                                        + Common.PAUSE_DURATION_S)
                                        * Common.REPETITIONS));
                                Log.d(TAG, "Recording for " + recordingDuration_millis + "ms");
                                try {
                                    Thread.sleep((long) recordingDuration_millis);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        stopRecording();
                                    }
                                });
                            }
                        }.start();

                        break;

                    default:
                        break;
                }
            }
        });

    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_MIC_MUTE_TOGGLE);
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();

        reportLog.addValue(
                KEY_REC_RATE,
                mRecordRate,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_AUDIO_SOURCE,
                mAudioSource,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    /**
     * AsyncTask class for the analyzing.
     */
    private class WavAnalyzerTask extends AsyncTask<Void, String, String>
            implements WavAnalyzer.Listener {

        private static final String TAG = "WavAnalyzerTask";
        private final WavAnalyzer mWavAnalyzer;

        public WavAnalyzerTask(byte[] recording) {
            mWavAnalyzer = new WavAnalyzer(recording, Common.RECORDING_SAMPLE_RATE_HZ,
                    WavAnalyzerTask.this);
        }

        @Override
        protected String doInBackground(Void... params) {
            boolean result = mWavAnalyzer.doWork();
            if (result) {
                return getString(R.string.pass_button_text);
            }
            return getString(R.string.fail_button_text);
        }

        @Override
        protected void onPostExecute(String result) {
            if (mWavAnalyzer.isSilence()) {
                mInfoText.append(getString(R.string.passed));
                getPassButton().setEnabled(true);
            } else {
                mInfoText.append(getString(R.string.failed));
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            for (String message : values) {
                Log.d(TAG, message);
            }
        }

        @Override
        public void sendMessage(String message) {
            publishProgress(message);
        }
    }
}
