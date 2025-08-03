/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.midilib.MidiTestModule;
import com.android.midi.VerifierMidiEchoService;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Common information and behaviors for the MidiJavaTestActivity and MidiNativeTestActivity
 */
public abstract class MidiTestActivityBase
        extends PassFailButtons.Activity
        implements View.OnClickListener {

    private static final String TAG = "MidiTestActivityBase";
    private static final boolean DEBUG = true;

    protected MidiManager mMidiManager;

    protected Intent mMidiServiceIntent;
    private MidiServiceConnection mMidiServiceConnection;

    // Flags
    protected boolean mHasMIDI;

    private MidiTestModule mUSBTestModule;
    private MidiTestModule mVirtualTestModule;
    private MidiTestModule mBTTestModule;

    // Widgets
    protected Button mUSBTestBtn;
    protected Button mVirtTestBtn;
    protected Button mBTTestBtn;

    protected TextView    mUSBIInputDeviceLbl;
    protected TextView    mUSBOutputDeviceLbl;
    protected TextView    mUSBTestStatusTxt;

    protected TextView    mVirtInputDeviceLbl;
    protected TextView    mVirtOutputDeviceLbl;
    protected TextView    mVirtTestStatusTxt;

    protected TextView    mBTInputDeviceLbl;
    protected TextView    mBTOutputDeviceLbl;
    protected TextView    mBTTestStatusTxt;

    public MidiTestActivityBase() {
    }

    protected void initTestModules(MidiTestModule USBTestModule,
                                    MidiTestModule virtualTestModule,
                                    MidiTestModule BTTestModule) {
        mUSBTestModule = USBTestModule;
        mVirtualTestModule = virtualTestModule;
        mBTTestModule = BTTestModule;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMidiManager = getSystemService(MidiManager.class);

        // Standard PassFailButtons.Activity initialization
        setPassFailButtonClickListeners();
        setInfoResources(R.string.midi_test, R.string.midi_info, -1);

        // May as well calculate this right off the bat.
        mHasMIDI = hasMIDI();
        ((TextView)findViewById(R.id.midiHasMIDILbl)).setText("" + mHasMIDI);

        mUSBTestBtn = (Button)findViewById(R.id.midiTestUSBInterfaceBtn);
        mUSBTestBtn.setOnClickListener(this);
        mUSBIInputDeviceLbl = (TextView)findViewById(R.id.midiUSBInputLbl);
        mUSBOutputDeviceLbl = (TextView)findViewById(R.id.midiUSBOutputLbl);
        mUSBTestStatusTxt = (TextView)findViewById(R.id.midiUSBTestStatusLbl);

        mVirtTestBtn = (Button)findViewById(R.id.midiTestVirtInterfaceBtn);
        mVirtTestBtn.setOnClickListener(this);
        mVirtInputDeviceLbl = (TextView)findViewById(R.id.midiVirtInputLbl);
        mVirtOutputDeviceLbl = (TextView)findViewById(R.id.midiVirtOutputLbl);
        mVirtTestStatusTxt = (TextView)findViewById(R.id.midiVirtTestStatusLbl);

        mBTTestBtn = (Button)findViewById(R.id.midiTestBTInterfaceBtn);
        mBTTestBtn.setOnClickListener(this);
        mBTInputDeviceLbl = (TextView)findViewById(R.id.midiBTInputLbl);
        mBTOutputDeviceLbl = (TextView)findViewById(R.id.midiBTOutputLbl);
        mBTTestStatusTxt = (TextView)findViewById(R.id.midiBTTestStatusLbl);

        calcTestPassed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.i(TAG, "---- Loading Virtual MIDI Service ...");
        }
        mMidiServiceConnection = new MidiServiceConnection();
        boolean isBound =
                bindService(mMidiServiceIntent,  mMidiServiceConnection,  Context.BIND_AUTO_CREATE);
        if (DEBUG) {
            Log.i(TAG, "---- Virtual MIDI Service loaded: " + isBound);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (DEBUG) {
            Log.i(TAG, "---- onPause()");
        }

        unbindService(mMidiServiceConnection);
        mMidiServiceConnection = null;
    }

    private boolean hasMIDI() {
        // CDD Section C-1-4: android.software.midi
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI);
    }

    void startMidiEchoServer() {
        // Init MIDI Stuff
        mMidiServiceIntent = new Intent(this, VerifierMidiEchoService.class);
    }

    void connectDeviceListener() {
        // Plug in device connect/disconnect callback
        final Handler handler = new Handler(Looper.getMainLooper());
        final Executor executor = handler::post;
        mMidiManager.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                executor, new MidiDeviceCallback());
    }

    void startWiredLoopbackTest() {
        mUSBTestModule.startLoopbackTest(MidiTestModule.TESTID_USBLOOPBACK);
    }

    void startVirtualLoopbackTest() {
        mVirtualTestModule.startLoopbackTest(MidiTestModule.TESTID_VIRTUALLOOPBACK);
    }

    void startBTLoopbackTest() {
        mBTTestModule.startLoopbackTest(MidiTestModule.TESTID_BTLOOPBACK);
    }

    boolean calcTestPassed() {
        boolean hasPassed = false;
        if (!mHasMIDI) {
            // if it doesn't report MIDI support, then it doesn't have to pass the other tests.
            hasPassed = true;
        } else {
            hasPassed = mUSBTestModule.hasTestPassed() &&
                    mVirtualTestModule.hasTestPassed() &&
                    mBTTestModule.hasTestPassed();
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

        // Let each module select (if available) the associated device for their type
        mUSBTestModule.scanDevices(devInfos);
        mVirtualTestModule.scanDevices(devInfos);
        mBTTestModule.scanDevices(devInfos);

        showConnectedMIDIPeripheral();
    }

    //
    // UI Updaters
    //
    void showConnectedMIDIPeripheral() {
        // USB
        mUSBIInputDeviceLbl.setText(mUSBTestModule.getInputName());
        mUSBOutputDeviceLbl.setText(mUSBTestModule.getOutputName());
        mUSBTestBtn.setEnabled(mUSBTestModule.isTestReady());

        // Virtual MIDI
        mVirtInputDeviceLbl.setText(mVirtualTestModule.getInputName());
        mVirtOutputDeviceLbl.setText(mVirtualTestModule.getOutputName());
        mVirtTestBtn.setEnabled(mVirtualTestModule.isTestReady());

        // Bluetooth
        mBTInputDeviceLbl.setText(mBTTestModule.getInputName());
        mBTOutputDeviceLbl.setText(mBTTestModule.getOutputName());
        // use mUSBTestModule.isTestReady() as a proxy for knowing the interface loopback
        // is connected
        mBTTestBtn.setEnabled(mBTTestModule.isTestReady() && mUSBTestModule.isTestReady());
    }

    //
    // UI Updaters
    //
    void showUSBTestStatus() {
        mUSBTestStatusTxt.setText(getTestStatusString(mUSBTestModule.getTestStatus()));
    }

    void showVirtTestStatus() {
        mVirtTestStatusTxt.setText(getTestStatusString(mVirtualTestModule.getTestStatus()));
    }

    void showBTTestStatus() {
        mBTTestStatusTxt.setText(getTestStatusString(mBTTestModule.getTestStatus()));
    }

    void enableTestButtons(boolean enable) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (enable) {
                    // remember, a given test might not be enabled, so we can't just enable
                    // all of the buttons
                    showConnectedMIDIPeripheral();
                } else {
                    mUSBTestBtn.setEnabled(enable);
                    mVirtTestBtn.setEnabled(enable);
                    mBTTestBtn.setEnabled(enable);
                }
            }
        });
    }

    // Need this to update UI from MIDI read thread
    public void updateTestStateUI() {
        runOnUiThread(new Runnable() {
            public void run() {
                calcTestPassed();
                showUSBTestStatus();
                showVirtTestStatus();
                showBTTestStatus();
            }
        });
    }

    // UI Helper
    public String getTestStatusString(int status) {
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

    //
    // View.OnClickListener Override - Handles button clicks
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.midiTestUSBInterfaceBtn) {
            startWiredLoopbackTest();
        } else if (id == R.id.midiTestVirtInterfaceBtn) {
            startVirtualLoopbackTest();
        } else if (id == R.id.midiTestBTInterfaceBtn) {
            startBTLoopbackTest();
        } else {
            assert false : "Unhandled button click";
        }
    }

    class MidiServiceConnection implements ServiceConnection {
        private static final String TAG = "MidiServiceConnection";
        @Override
        public void  onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) {
                Log.i(TAG, "MidiServiceConnection.onServiceConnected()");
            }
            scanMidiDevices();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) {
                Log.i(TAG, "MidiServiceConnection.onServiceDisconnected()");
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
