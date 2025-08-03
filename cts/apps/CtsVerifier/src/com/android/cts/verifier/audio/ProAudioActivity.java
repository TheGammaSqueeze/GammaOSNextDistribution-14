/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;

@CddTest(requirement = "5.10/C-1-1,C-1-3,C-1-4")
public class ProAudioActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = ProAudioActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Flags
    private boolean mClaimsProAudio;
    private boolean mClaimsLowLatencyAudio;    // CDD ProAudio section C-1-1
    private boolean mClaimsMIDI;               // CDD ProAudio section C-1-4
    private boolean mClaimsUSBHostMode;        // CDD ProAudio section C-1-3
    private boolean mClaimsUSBPeripheralMode;  // CDD ProAudio section C-1-3
    private boolean mClaimsHDMI;               // CDD ProAudio section C-1-3

    AudioDeviceInfo mHDMIDeviceInfo;

    // Widgets
    TextView mHDMISupportLbl;

    CheckBox mClaimsHDMICheckBox;

    TextView mTestStatusLbl;

    // Borrowed from PassFailButtons.java
    private static final int INFO_DIALOG_ID = 1337;
    private static final String INFO_DIALOG_TITLE_ID = "infoDialogTitleId";
    private static final String INFO_DIALOG_MESSAGE_ID = "infoDialogMessageId";

    // ReportLog Schema
    private static final String SECTION_PRO_AUDIO_ACTIVITY = "pro_audio_activity";
    private static final String KEY_CLAIMS_PRO = "claims_pro_audio";
    private static final String KEY_CLAIMS_LOW_LATENCY = "claims_low_latency_audio";
    private static final String KEY_CLAIMS_MIDI = "claims_midi";
    private static final String KEY_CLAIMS_USB_HOST = "claims_usb_host";
    private static final String KEY_CLAIMS_USB_PERIPHERAL = "claims_usb_peripheral";
    private static final String KEY_CLAIMS_HDMI = "claims_hdmi";

    public ProAudioActivity() {
    }

    // HDMI Stuff
    private boolean isHDMIConnected() {
        return mHDMIDeviceInfo != null;
    }

    private boolean isHDMIValid() {
        if (mHDMIDeviceInfo == null) {
            return false;
        }

        // MUST support output in stereo and eight channels...
        boolean has2Chans = false;
        boolean has8Chans = false;
        int[] channelCounts = mHDMIDeviceInfo.getChannelCounts();
        for (int count : channelCounts) {
            if (count == 2) {
                has2Chans = true;
            } else if (count == 8) {
                has8Chans = true;
            }
        }
        if (!has2Chans || !has8Chans) {
            return false;
        }

        // at 20-bit or 24-bit depth
        boolean hasFloatEncoding = false;
        int[] encodings = mHDMIDeviceInfo.getEncodings();
        for (int encoding : encodings) {
            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                hasFloatEncoding = true;
                break;
            }
        }
        if (!hasFloatEncoding) {
            return false;
        }

         // and 192 kHz
        boolean has192K = false;
        int[] sampleRates = mHDMIDeviceInfo.getSampleRates();
        for (int rate : sampleRates) {
            if (rate >= 192000) {
                has192K = true;
            }
        }
        if (!has192K) {
            return false;
        }

        // without bit-depth loss or resampling (hmmmmm....).

        return true;
    }

    protected void handleDeviceConnection(AudioDeviceInfo[] addedDevices) {
        mHDMIDeviceInfo = null;
        for (AudioDeviceInfo deviceInfo : addedDevices) {
            Log.i(TAG, "  " + deviceInfo.getProductName() + " type:" + deviceInfo.getType());
            if (deviceInfo.isSink() && deviceInfo.getType() == AudioDeviceInfo.TYPE_HDMI) {
                mHDMIDeviceInfo = deviceInfo;
                break;
            }
        }

        if (mHDMIDeviceInfo != null) {
            mClaimsHDMICheckBox.setChecked(true);
        }

        displayTestResults();
    }

    private boolean calculatePass() {
        boolean usbOK = mClaimsUSBHostMode && mClaimsUSBPeripheralMode;
        boolean hdmiOK = !mClaimsHDMI || isHDMIValid();

        boolean hasPassed = isReportLogOkToPass()
                && !mClaimsProAudio
                || (mClaimsLowLatencyAudio && mClaimsMIDI && usbOK && hdmiOK);

        getPassButton().setEnabled(hasPassed);
        return hasPassed;
    }

    private void displayTestResults() {
        boolean hasPassed = calculatePass();

        Resources strings = getResources();
        if (!isReportLogOkToPass()) {
            mTestStatusLbl.setText(getResources().getString(R.string.audio_general_reportlogtest));
        } else  if (hasPassed) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_pass));
        } else if (!mClaimsMIDI) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_midinotreported));
        } else if (!mClaimsUSBHostMode) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_usbhostnotreported));
        } else if (!mClaimsUSBPeripheralMode) {
            mTestStatusLbl.setText(strings.getString(
                    R.string.audio_proaudio_usbperipheralnotreported));
        } else if (mClaimsHDMI) {
            if (!isHDMIConnected()) {
                mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_hdmiNotFound));
            } else if (!isHDMIValid()) {
                mTestStatusLbl.setText(strings.getString(R.string.hdmi_insufficient));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.pro_audio);

        super.onCreate(savedInstanceState);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.proaudio_test, R.string.proaudio_info, -1);

        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        ((TextView)findViewById(R.id.proAudioHasProAudioLbl)).setText("" + mClaimsProAudio);

        if (!mClaimsProAudio) {
            Bundle args = new Bundle();
            args.putInt(INFO_DIALOG_TITLE_ID, R.string.pro_audio_latency_test);
            args.putInt(INFO_DIALOG_MESSAGE_ID, R.string.audio_proaudio_nopa_message);
            showDialog(INFO_DIALOG_ID, args);
        }

        mClaimsLowLatencyAudio = AudioSystemFlags.claimsLowLatencyAudio(this);
        ((TextView)findViewById(R.id.proAudioHasLLALbl)).setText("" + mClaimsLowLatencyAudio);

        mClaimsMIDI = AudioSystemFlags.claimsMIDI(this);
        ((TextView)findViewById(R.id.proAudioHasMIDILbl)).setText("" + mClaimsMIDI);

        mClaimsUSBHostMode = AudioSystemFlags.claimsUSBHostMode(this);
        ((TextView)findViewById(R.id.proAudioMidiHasUSBHostLbl)).setText("" + mClaimsUSBHostMode);

        mClaimsUSBPeripheralMode = AudioSystemFlags.claimsUSBPeripheralMode(this);
        ((TextView)findViewById(
                R.id.proAudioMidiHasUSBPeripheralLbl)).setText("" + mClaimsUSBPeripheralMode);

        // HDMI
        mHDMISupportLbl = (TextView)findViewById(R.id.proAudioHDMISupportLbl);
        mClaimsHDMICheckBox = (CheckBox)findViewById(R.id.proAudioHasHDMICheckBox);
        mClaimsHDMICheckBox.setOnClickListener(this);

        mTestStatusLbl = (TextView)findViewById(R.id.proAudioTestStatusLbl);

        AudioManager audioManager = getSystemService(AudioManager.class);
        audioManager.registerAudioDeviceCallback(new TestAudioDeviceCallback(), null);

        displayTestResults();
    }

    /**
     * Store test results in log
     */
    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_PRO_AUDIO_ACTIVITY);
    }

    @Override
    public void recordTestResults() {

        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_CLAIMS_PRO,
                mClaimsProAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_LOW_LATENCY,
                mClaimsLowLatencyAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_MIDI,
                mClaimsMIDI,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_USB_HOST,
                mClaimsUSBHostMode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_USB_PERIPHERAL,
                mClaimsUSBPeripheralMode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_HDMI,
                mClaimsHDMI,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.proAudioHasHDMICheckBox) {
            if (mClaimsHDMICheckBox.isChecked()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        this, android.R.style.Theme_Material_Dialog_Alert);
                builder.setTitle(getResources().getString(R.string.proaudio_hdmi_infotitle));
                builder.setMessage(getResources().getString(R.string.proaudio_hdmi_message));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.show();

                mClaimsHDMI = true;
                mHDMISupportLbl.setText(
                        getResources().getString(R.string.audio_proaudio_hdmiPending));
            } else {
                mClaimsHDMI = false;
                mHDMISupportLbl.setText(getResources().getString(R.string.audio_proaudio_NA));
            }
            displayTestResults();
        }
    }

    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            handleDeviceConnection(addedDevices);
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            // NOP
        }
    }
}
