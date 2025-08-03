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

import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.util.Log;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.midilib.MidiIODevice;
import com.android.cts.verifier.audio.midilib.MidiTestModule;
import com.android.cts.verifier.audio.midilib.NativeMidiManager;

import java.io.IOException;
import java.util.Collection;

/*
 * A note about the USB MIDI device.
 * Any USB MIDI peripheral with standard female DIN jacks can be used. A standard MIDI cable
 * plugged into both input and output is required for the USB Loopback Test. A Bluetooth MIDI
 * device like the Yamaha MD-BT01 plugged into both input and output is required for the
 * Bluetooth Loopback test.
 */

/*
 * A note about the "virtual MIDI" device...
 * See file MidiEchoService for implementation of the echo server itself.
 * This service is started by the main manifest file (AndroidManifest.xml).
 */

/*
 * A note about Bluetooth MIDI devices...
 * Any Bluetooth MIDI device needs to be paired with the DUT with the "MIDI+BTLE" application
 * available in the Play Store:
 * (https://play.google.com/store/apps/details?id=com.mobileer.example.midibtlepairing).
 */

/**
 * CTS Verifier Activity for MIDI test
 */
@CddTest(requirement = "5.9/C-1-3,C-1-2")
public class MidiNativeTestActivity extends MidiTestActivityBase {
    private static final String TAG = "MidiNativeTestActivity";
    private static final boolean DEBUG = false;

    public MidiNativeTestActivity() {
        super();
        initTestModules(new NativeMidiTestModule(MidiDeviceInfo.TYPE_USB),
                new NativeMidiTestModule(MidiDeviceInfo.TYPE_VIRTUAL),
                new BTMidiTestModule());

        NativeMidiManager.loadNativeAPI();
        NativeMidiManager.initN();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) {
            Log.i(TAG, "---- onCreate()");
        }

        setContentView(R.layout.ndk_midi_activity);

        super.onCreate(savedInstanceState);

        startMidiEchoServer();
        scanMidiDevices();

        connectDeviceListener();
    }

    @Override
    protected void onPause () {
        super.onPause();
        if (DEBUG) {
            Log.i(TAG, "---- onPause()");
        }

        boolean isFound = stopService(mMidiServiceIntent);
        if (DEBUG) {
            Log.i(TAG, "---- Stop Service: " + isFound);
        }
    }

    /**
     * A class to control and represent the state of a given test.
     * It hold the data needed for IO, and the logic for sending, receiving and matching
     * the MIDI data stream.
     */
    public class NativeMidiTestModule extends MidiTestModule {
        private static final String TAG = "NativeMidiTestModule";
        private static final boolean DEBUG = true;

        private NativeMidiManager   mNativeMidiManager;

        public NativeMidiTestModule(int deviceType) {
            super(deviceType);
            mNativeMidiManager = new NativeMidiManager();

            // this call is just to keep the build from stripping out "endTest", because
            // it is only called from JNI.
            endTest(TESTSTATUS_NOTRUN);
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

        protected void closePorts() {
            // NOP
        }

        @Override
        public void startLoopbackTest(int testID) {
            synchronized (mTestLock) {
                mTestCounter++;
                mTestRunning = true;
                enableTestButtons(false);
            }

            if (DEBUG) {
                Log.i(TAG, "---- startLoopbackTest()");
            }

            synchronized (mTestLock) {
                mTestStatus = TESTSTATUS_NOTRUN;
            }

            if (mIODevice.mSendDevInfo != null) {
                mMidiManager.openDevice(mIODevice.mSendDevInfo, new TestModuleOpenListener(), null);
            }

            startTimeoutHandler();
        }

        @Override
        public boolean hasTestPassed() {
            int status;
            synchronized (mTestLock) {
                status = mTestStatus;
            }
            return status == TESTSTATUS_PASSED;
        }

        public void endTest(int endCode) {
            synchronized (mTestLock) {
                mTestRunning = false;
                mTestStatus = endCode;
            }
            if (endCode != TESTSTATUS_NOTRUN) {
                updateTestStateUI();
                enableTestButtons(true);
            }

            closePorts();
        }

        /**
         * Listens for MIDI device opens. Opens I/O ports and sends out the apriori
         * setup messages.
         */
        class TestModuleOpenListener implements MidiManager.OnDeviceOpenedListener {
            //
            // This is where the logical part of the test starts
            //
            @Override
            public void onDeviceOpened(MidiDevice device) {
                if (DEBUG) {
                    Log.i(TAG, "---- onDeviceOpened()");
                }
                mNativeMidiManager.startTest(NativeMidiTestModule.this, device,
                        false /*throttleData*/);
            }
        }
    } /* class NativeMidiTestModule */

    /**
     * Test Module for Bluetooth Loopback.
     * This is a specialization of NativeMidiTestModule (which has the connections for the BL device
     * itself) with and added MidiIODevice object for the USB audio device which does the
     * "looping back".
     */
    private class BTMidiTestModule extends NativeMidiTestModule {
        private static final String TAG = "BTMidiTestModule";
        private MidiIODevice mUSBLoopbackDevice = new MidiIODevice(MidiDeviceInfo.TYPE_USB);

        public BTMidiTestModule() {
            super(MidiDeviceInfo.TYPE_BLUETOOTH);
        }

        @Override
        public void scanDevices(Collection<MidiDeviceInfo> devInfos) {
            // (normal) Scan for BT MIDI device
            super.scanDevices(devInfos);
            // Find a USB Loopback Device
            mUSBLoopbackDevice.scanDevices(devInfos);
        }

        protected void closePorts() {
            super.closePorts();
            if (mUSBLoopbackDevice != null) {
                mUSBLoopbackDevice.closePorts();
            }
        }

        @Override
        public void startLoopbackTest(int testID) {
            if (DEBUG) {
                Log.i(TAG, "---- startLoopbackTest()");
            }
            // Setup the USB Loopback Device
            mUSBLoopbackDevice.closePorts();

            if (mIODevice.mSendDevInfo != null) {
                mMidiManager.openDevice(
                        mUSBLoopbackDevice.mSendDevInfo, new USBLoopbackOpenListener(), null);
            }

            // Now start the test as usual
            super.startLoopbackTest(testID);
        }

        /**
         * We need this OnDeviceOpenedListener to open the USB-Loopback device
         */
        private class USBLoopbackOpenListener implements MidiManager.OnDeviceOpenedListener {
            @Override
            public void onDeviceOpened(MidiDevice device) {
                if (DEBUG) {
                    Log.i("USBLoopbackOpenListener", "---- onDeviceOpened()");
                }
                mUSBLoopbackDevice.openPorts(device, new USBMidiEchoReceiver());
            }
        } /* class USBLoopbackOpenListener */

        /**
         * MidiReceiver subclass for BlueTooth Loopback Test
         *
         * This class receives bytes from the USB Interface (presumably coming from the
         * Bluetooth MIDI peripheral) and echoes them back out (presumably to the Bluetooth
         * MIDI peripheral).
         */
        //TODO - This could be pulled out into a separate class and shared with the identical
        // code in MidiJavaTestActivity is we pass in the send port
        private class USBMidiEchoReceiver extends MidiReceiver {
            private int mTotalBytesEchoed;

            @Override
            public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
                mTotalBytesEchoed += count;
                if (DEBUG) {
                    Log.i(TAG, "---- USBMidiEchoReceiver.onSend() count:" + count +
                            " total:" + mTotalBytesEchoed);
                }
                if (mUSBLoopbackDevice.mSendPort == null) {
                    Log.e(TAG, "(native) mUSBLoopbackDevice.mSendPort is null");
                } else {
                    mUSBLoopbackDevice.mSendPort.onSend(msg, offset, count, timestamp);
                }
            }
        } /* class USBMidiEchoReceiver */
    } /* class BTMidiTestModule */
} /* class MidiNativeTestActivity */
