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

package com.android.cts.verifier.audio.midilib;

import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.Collection;

/**
 * A class to hold the MidiIODevice and ports objects associated with a MIDI I/O peripheral.
 */
public class MidiIODevice {
    private static final String TAG = "MidiIODevice";
    private static final boolean DEBUG = false;

    private final int mDeviceType;

    public MidiDeviceInfo mSendDevInfo;
    public MidiDeviceInfo mReceiveDevInfo;

    public MidiDevice      mMidiDevice;
    public MidiInputPort   mSendPort;
    public MidiOutputPort  mReceivePort;

    public MidiIODevice(int deviceType) {
        mDeviceType = deviceType;
    }

    /**
     * Sets MidiDevice and info about sender and receiver ports from a collection of
     * MidiDeviceInfo. It uses mDeviceType to match the device.
     *
     * @param devInfos the set of devices to check
     */
    public void scanDevices(Collection<MidiDeviceInfo> devInfos) {
        if (DEBUG) {
            Log.i(TAG, "---- scanDevices() typeID: " + mDeviceType);
        }
        mSendDevInfo = null;
        mReceiveDevInfo = null;
        mSendPort = null;
        mReceivePort = null;
        mMidiDevice = null;

        for(MidiDeviceInfo devInfo : devInfos) {
            Bundle devBundle = devInfo.getProperties();
            if (DEBUG) {
                Log.i(TAG, "  mfg:" + devBundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
                        + " prod:" + devBundle.getString(MidiDeviceInfo.PROPERTY_PRODUCT));
            }

            // Inputs?
            int numInPorts = devInfo.getInputPortCount();
            if (numInPorts <= 0) {
                continue; // none?
            }

            // For virtual MIDI devices, we need to find
            // manufacturer=AndroidCTSVerifier and product=VerifierMidiEcho or else
            // it might be some other MIDI app providing virtual MIDI services
            if (mDeviceType == MidiDeviceInfo.TYPE_VIRTUAL) {
                if (!"AndroidCTSVerifier".equals(
                        devBundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER))
                        || !"VerifierMidiEcho".equals(
                                devBundle.getString(MidiDeviceInfo.PROPERTY_PRODUCT))) {
                    // Virtual [but not ours]
                    Log.d(TAG, "Virtual Midi Device:" + devInfo);
                    continue;
                }
            }

            if (devInfo.getType() == mDeviceType && mSendDevInfo == null) {
                mSendDevInfo = devInfo;
            }

            // Outputs?
            int numOutPorts = devInfo.getOutputPortCount();
            if (numOutPorts <= 0) {
                continue; // none?
            }
            if (devInfo.getType() == mDeviceType && mReceiveDevInfo == null) {
                mReceiveDevInfo = devInfo;
            }

            if (mSendDevInfo != null && mReceiveDevInfo != null) {
                break;  // we have an in and out device, so we can stop scanning
            }
        }

        if (DEBUG) {
            Log.i(TAG, "---- mSendDevInfo: "
                    + (mSendDevInfo != null ? mSendDevInfo.toString() : "NONE"));
            Log.i(TAG, "---- mReceiveDevInfo: "
                    + (mReceiveDevInfo != null ? mReceiveDevInfo.toString() : "NONE"));
        }
    }

    public void openPorts(MidiDevice device, MidiReceiver receiver) {
        if (DEBUG) {
            Log.i(TAG, "---- openPorts()");
        }
        MidiDeviceInfo deviceInfo = device.getInfo();
        int numOutputs = deviceInfo.getOutputPortCount();
        if (numOutputs > 0) {
            mReceivePort = device.openOutputPort(0);
            mReceivePort.connect(receiver);
        }

        int numInputs = deviceInfo.getInputPortCount();
        if (numInputs != 0) {
            mSendPort = device.openInputPort(0);
        }
        mMidiDevice = device;

        if (DEBUG) {
            Log.i(TAG, "---- mSendPort:" + mSendPort);
        }
    }

    public void closePorts() {
        if (DEBUG) {
            Log.i(TAG, "---- closePorts()");
        }
        try {
            if (mSendPort != null) {
                mSendPort.close();
                mSendPort = null;
            }
            if (mReceivePort != null) {
                mReceivePort.close();
                mReceivePort = null;
            }
            if (mMidiDevice != null) {
                mMidiDevice.close();
                mMidiDevice = null;
            }
        } catch (IOException ex) {
            Log.e(TAG, "IOException Closing MIDI ports: " + ex);
        }
    }

    public String getInputName() {
        if (mReceiveDevInfo != null) {
            return mDeviceType == MidiDeviceInfo.TYPE_VIRTUAL
                    ? "Virtual MIDI Device"
                    : mReceiveDevInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        } else {
            return "";
        }
    }

    public String getOutputName() {
        if (mSendDevInfo != null) {
            return mDeviceType == MidiDeviceInfo.TYPE_VIRTUAL
                    ? "Virtual MIDI Device"
                    : mSendDevInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        } else {
            return "";
        }
    }
}   /* class MidiIODevice */
