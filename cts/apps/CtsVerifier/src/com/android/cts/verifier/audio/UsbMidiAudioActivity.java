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

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.midilib.JavaMidiTestModule;
import com.android.cts.verifier.audio.midilib.MidiTestModule;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Tests MIDI and Audio for an USB peripheral.
 */
@CddTest(requirements = {"5.9/C-1-2,C-1-3", "7.8.2/C-1-1,C-1-2"})
public class UsbMidiAudioActivity extends USBAudioPeripheralPlayerActivity {

    private static final String TAG = "UsbMidiAudioActivity";
    private static final boolean DEBUG = true;

    private MidiManager mMidiManager;

    // Flags
    private boolean mHasMIDI;

    private MidiTestModule mUsbMidiTestModule;

    // Widgets
    private Button mUsbMidiTestBtn;

    private TextView    mUsbMidiInputDeviceLbl;
    private TextView    mUsbMidiOutputDeviceLbl;
    private TextView    mUsbMidiTestStatusTxt;
    private TextView    mUsbAudioTestStatusTxt;

    private Button mPlayBtn;

    private LocalClickListener mButtonClickListener = new LocalClickListener();

    private boolean mHasAudioFinishedPlaying;

    public UsbMidiAudioActivity() {
        super(false); // Mandated peripheral is NOT required
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.midi_audio_usb_activity);

        // Standard PassFailButtons.Activity initialization
        setPassFailButtonClickListeners();
        setInfoResources(R.string.usb_midi_audio_test, R.string.usb_midi_audio_info, -1);

        // USB audio
        connectPeripheralStatusWidgets();

        mPlayBtn = (Button) findViewById(R.id.uap_playPlayBtn);
        mPlayBtn.setOnClickListener(mButtonClickListener);

        setupPlayer();

        connectUSBPeripheralUI();

        // Assume the device has host mode unless the user explicitly mentions it does not.
        mHasHostMode = true;

        // MIDI
        mMidiManager = getSystemService(MidiManager.class);
        mUsbMidiTestModule = new JavaMidiTestActivityModule(MidiDeviceInfo.TYPE_USB);

        mHasMIDI = hasMIDI();
        ((TextView) findViewById(R.id.midiHasMIDILbl)).setText(String.valueOf(mHasMIDI));

        mUsbMidiTestBtn = (Button) findViewById(R.id.midiTestUSBInterfaceBtn);
        mUsbMidiTestBtn.setOnClickListener(mButtonClickListener);
        mUsbMidiInputDeviceLbl = (TextView) findViewById(R.id.midiUSBInputLbl);
        mUsbMidiOutputDeviceLbl = (TextView) findViewById(R.id.midiUSBOutputLbl);
        mUsbMidiTestStatusTxt = (TextView) findViewById(R.id.midiUSBTestStatusLbl);
        mUsbAudioTestStatusTxt = (TextView) findViewById(R.id.audioUSBTestStatusLbl);

        scanMidiDevices();

        connectMidiDeviceListener();

        calcTestPassed();
    }

    /**
     * Overrides parts of JavaMidiTestModule that needs a higher scope.
     */
    private class JavaMidiTestActivityModule extends JavaMidiTestModule {
        private static final String TAG = "JavaMidiTestActivityModule";

        JavaMidiTestActivityModule(int deviceType) {
            super(deviceType);
        }

        @Override
        protected void updateTestStateUIAbstract() {
            updateTestStateUI();
        }

        @Override
        protected void showTimeoutMessageAbstract() {
            showTimeoutMessage();
        }

        @Override
        protected void enableTestButtonsAbstract(boolean enable) {
            enableTestButtons(enable);
        }

        @Override
        protected void openMidiDevice() {
            mMidiManager.openDevice(mIODevice.mSendDevInfo, new TestModuleOpenListener(), null);
        }

        void showTimeoutMessage() {
            runOnUiThread(new Runnable() {
                public void run() {
                    synchronized (mTestLock) {
                        if (mTestRunning) {
                            if (DEBUG) {
                                Log.i(TAG, "---- Test Failed - TIMEOUT");
                            }
                            mTestStatus = TESTSTATUS_FAILED_TIMEOUT;
                            updateTestStateUIAbstract();
                        }
                    }
                }
            });
        }
    } /* class JavaMidiTestActivityModule */

    private boolean hasMIDI() {
        // CDD Section C-1-4: android.software.midi
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI);
    }

    void connectMidiDeviceListener() {
        // Plug in device connect/disconnect callback
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        mMidiManager.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                executor, new MidiDeviceCallback());
    }

    void startWiredLoopbackTest() {
        mUsbMidiTestModule.startLoopbackTest(MidiTestModule.TESTID_USBLOOPBACK);
    }

    boolean calcTestPassed() {
        boolean hasPassed = false;
        if (!mHasMIDI || !mHasHostMode) {
            // If this device doesn't report MIDI support or host mode, it does not need to pass
            // this test.
            hasPassed = true;
        } else {
            hasPassed = mUsbMidiTestModule.hasTestPassed() && mHasAudioFinishedPlaying;
        }

        getPassButton().setEnabled(hasPassed);
        return hasPassed;
    }

    void scanMidiDevices() {
        if (DEBUG) {
            Log.i(TAG, "scanMidiDevices()....");
        }

        // Get the list of all MIDI devices attached
        Collection<MidiDeviceInfo> devInfos = mMidiManager.getDevicesForTransport(
                MidiManager.TRANSPORT_MIDI_BYTE_STREAM);
        if (DEBUG) {
            Log.i(TAG, "  numDevices:" + devInfos.size());
        }

        mUsbMidiTestModule.scanDevices(devInfos);

        showConnectedMIDIPeripheral();
    }

    //
    // UI Updaters
    //
    void showConnectedMIDIPeripheral() {
        // USB
        mUsbMidiInputDeviceLbl.setText(mUsbMidiTestModule.getInputName());
        mUsbMidiOutputDeviceLbl.setText(mUsbMidiTestModule.getOutputName());
        mUsbMidiTestBtn.setEnabled(mUsbMidiTestModule.isTestReady());
    }

    //
    // UI Updaters
    //
    void showUsbMidiTestStatus() {
        mUsbMidiTestStatusTxt.setText(getTestStatusString(mUsbMidiTestModule.getTestStatus()));
    }

    void enableTestButtons(boolean enable) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (enable) {
                    // remember, a given test might not be enabled, so we can't just enable
                    // all of the buttons
                    showConnectedMIDIPeripheral();
                } else {
                    mUsbMidiTestBtn.setEnabled(enable);
                }
            }
        });
    }

    // Need this to update UI from MIDI read thread
    void updateTestStateUI() {
        runOnUiThread(new Runnable() {
            public void run() {
                calcTestPassed();
                showUsbMidiTestStatus();
            }
        });
    }

    // UI Helper
    String getTestStatusString(int status) {
        Resources appResources = getApplicationContext().getResources();
        switch (status) {
            case MidiTestModule.TESTSTATUS_NOTRUN:
                return appResources.getString(R.string.midiNotRunLbl);

            case MidiTestModule.TESTSTATUS_PASSED:
                return appResources.getString(R.string.midiPassedLbl);

            case MidiTestModule.TESTSTATUS_FAILED_MISMATCH:
                return appResources.getString(R.string.midiFailedMismatchLbl);

            case MidiTestModule.TESTSTATUS_FAILED_TIMEOUT:
                return appResources.getString(R.string.midiFailedTimeoutLbl);

            case MidiTestModule.TESTSTATUS_FAILED_OVERRUN:
                return appResources.getString(R.string.midiFailedOverrunLbl);

            case MidiTestModule.TESTSTATUS_FAILED_DEVICE:
                return appResources.getString(R.string.midiFailedDeviceLbl);

            case MidiTestModule.TESTSTATUS_FAILED_JNI:
                return appResources.getString(R.string.midiFailedJNILbl);

            case MidiTestModule.TESTSTATUS_FAILED_SETUP:
                return appResources.getString(R.string.midiFailedSetupLbl);

            case MidiTestModule.TESTSTATUS_FAILED_SEND:
                return appResources.getString(R.string.midiFailedSendLbl);

            default:
                return "Unknown Test Status.";
        }
    }

    /**
     * Overriding USBAudioPeripheralPlayerActivity to set whether the audio play button is enabled
     */
    public void updateConnectStatus() {
        mPlayBtn.setEnabled(mIsPeripheralAttached);
    }

    class LocalClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.midiTestUSBInterfaceBtn) {
                startWiredLoopbackTest();
            } else if (view.getId() == R.id.uap_playPlayBtn) {
                Log.i(TAG, "Play Button Pressed");
                if (!isPlaying()) {
                    boolean result = startPlay();
                    if (result) {
                        mPlayBtn.setText(getString(R.string.audio_uap_play_stopBtn));
                        if (!mHasAudioFinishedPlaying) {
                            mUsbAudioTestStatusTxt.setText(getString(R.string.uap_test_playing));
                        }
                    } else {
                        mUsbAudioTestStatusTxt.setText(getString(R.string.audio_general_fail));
                    }
                } else {
                    boolean result = stopPlay();
                    mHasAudioFinishedPlaying = result;
                    if (result) {
                        mPlayBtn.setText(getString(R.string.audio_uap_play_playBtn));
                        mUsbAudioTestStatusTxt.setText(getString(R.string.audio_general_ok));
                    } else {
                        mUsbAudioTestStatusTxt.setText(getString(R.string.audio_general_fail));
                    }
                    updateTestStateUI();
                }
            }
        }
    }

    /**
     * Callback class for MIDI device connect/disconnect.
     */
    class MidiDeviceCallback extends MidiManager.DeviceCallback {
        private static final String TAG = "MidiDeviceCallback";

        @Override
        public void onDeviceAdded(MidiDeviceInfo device) {
            scanMidiDevices();
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo device) {
            scanMidiDevices();
        }
    } /* class MidiDeviceCallback */
}
