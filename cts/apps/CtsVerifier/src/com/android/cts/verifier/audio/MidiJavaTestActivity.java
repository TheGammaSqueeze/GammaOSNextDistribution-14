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
import com.android.cts.verifier.audio.midilib.JavaMidiTestModule;
import com.android.cts.verifier.audio.midilib.MidiIODevice;

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
 *  A note about the "virtual MIDI" device...
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
@CddTest(requirement = "5.9/C-1-4,C-1-2")
public class MidiJavaTestActivity extends MidiTestActivityBase {
    private static final String TAG = "MidiJavaTestActivity";
    private static final boolean DEBUG = true;

    public MidiJavaTestActivity() {
        super();
        initTestModules(new JavaMidiTestActivityModule(MidiDeviceInfo.TYPE_USB),
                new JavaMidiTestActivityModule(MidiDeviceInfo.TYPE_VIRTUAL),
                new BTMidiTestModule());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) {
            Log.i(TAG, "---- onCreate()");
        }

        setContentView(R.layout.midi_activity);

        super.onCreate(savedInstanceState);

        startMidiEchoServer();
        scanMidiDevices();

        connectDeviceListener();
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

    /**
     * Test Module for Bluetooth Loopback.
     * This is a specialization of JavaMidiTestModule (which has the connections for the BL device
     * itself) with and added MidiIODevice object for the USB audio device which does the
     * "looping back".
     */
    private class BTMidiTestModule extends JavaMidiTestActivityModule {
        private static final String TAG = "BTMidiTestModule";
        private MidiIODevice mUSBLoopbackDevice = new MidiIODevice(MidiDeviceInfo.TYPE_USB);

        public BTMidiTestModule() {
            super(MidiDeviceInfo.TYPE_BLUETOOTH );
        }

        @Override
        public void scanDevices(Collection<MidiDeviceInfo> devInfos) {
            // (normal) Scan for BT MIDI device
            super.scanDevices(devInfos);
            // Find a USB Loopback Device
            mUSBLoopbackDevice.scanDevices(devInfos);
        }

        private void openUSBEchoDevice(MidiDevice device) {
            MidiDeviceInfo deviceInfo = device.getInfo();
            int numOutputs = deviceInfo.getOutputPortCount();
            if (numOutputs > 0) {
                mUSBLoopbackDevice.mReceivePort = device.openOutputPort(0);
                mUSBLoopbackDevice.mReceivePort.connect(new USBMidiEchoReceiver());
            }

            int numInputs = deviceInfo.getInputPortCount();
            if (numInputs != 0) {
                mUSBLoopbackDevice.mSendPort = device.openInputPort(0);
            }
        }

        protected void closePorts() {
            super.closePorts();
            mUSBLoopbackDevice.closePorts();
        }

        @Override
        public void startLoopbackTest(int testID) {
            if (DEBUG) {
                Log.i(TAG, "---- startLoopbackTest()");
            }

            super.startLoopbackTest(testID);

            // Setup the USB Loopback Device
            mUSBLoopbackDevice.closePorts();

            if (mIODevice.mSendDevInfo != null) {
                mMidiManager.openDevice(
                        mUSBLoopbackDevice.mSendDevInfo, new USBLoopbackOpenListener(), null);
            }
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
        // code in MidiNativeTestActivity if we pass in the USB Loopback Device object rather
        // than accessing it from the enclosing BTMidiTestModule class.
        private class USBMidiEchoReceiver extends MidiReceiver {
            private int mTotalBytesEchoed;

            @Override
            public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
                mTotalBytesEchoed += count;
                if (DEBUG) {
                    logByteArray("echo: ", msg, offset, count);
                }
                if (mUSBLoopbackDevice.mSendPort == null) {
                    Log.e(TAG, "(java) mUSBLoopbackDevice.mSendPort is null");
                } else {
                    mUSBLoopbackDevice.mSendPort.onSend(msg, offset, count, timestamp);
                }
            }
        } /* class USBMidiEchoReceiver */
    } /* class BTMidiTestModule */
} /* class MidiActivity */
