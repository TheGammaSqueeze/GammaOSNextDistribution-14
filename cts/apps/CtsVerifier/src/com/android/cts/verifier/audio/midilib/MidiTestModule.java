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

package com.android.cts.verifier.audio.midilib;

import android.media.midi.MidiDeviceInfo;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A test module that tests MIDI
 */
public abstract class MidiTestModule {
    private static final String TAG = "MidiTestModule";
    private static final boolean DEBUG = true;

    // Test Status
    public static final int TESTSTATUS_NOTRUN = 0;
    public static final int TESTSTATUS_PASSED = 1;
    public static final int TESTSTATUS_FAILED_MISMATCH = 2;
    public static final int TESTSTATUS_FAILED_TIMEOUT = 3;
    public static final int TESTSTATUS_FAILED_OVERRUN = 4;
    public static final int TESTSTATUS_FAILED_DEVICE = 5;
    public static final int TESTSTATUS_FAILED_JNI = 6;
    public static final int TESTSTATUS_FAILED_SETUP = 7;
    public static final int TESTSTATUS_FAILED_SEND = 8;

    public static final int TESTID_NONE = 0;
    public static final int TESTID_USBLOOPBACK = 1;
    public static final int TESTID_VIRTUALLOOPBACK = 2;
    public static final int TESTID_BTLOOPBACK = 3;

    protected int mTestStatus = TESTSTATUS_NOTRUN;

    protected int mDeviceType;

    // The Test Peripheral
    protected MidiIODevice                mIODevice;

    // Test State
    protected final Object        mTestLock = new Object();
    protected boolean             mTestRunning;

    // Timeout handling
    protected static final int    TEST_TIMEOUT_MS = 5000;
    protected final Timer         mTimeoutTimer = new Timer();
    protected int                 mTestCounter = 0;

    public MidiTestModule(int deviceType) {
        mIODevice = new MidiIODevice(deviceType);
        mDeviceType = deviceType;
    }

    /**
     * Starts the loop-back test
     */
    public abstract void startLoopbackTest(int testID);

    /**
     * Returns whether the test has passed
     */
    public abstract boolean hasTestPassed();

    protected abstract void updateTestStateUIAbstract();
    protected abstract void showTimeoutMessageAbstract();
    protected abstract void enableTestButtonsAbstract(boolean enable);

    public int getTestStatus() {
        return mTestStatus;
    }

    /**
     * Returns whether the test is ready
     */
    public boolean isTestReady() {
        return mIODevice.mReceiveDevInfo != null && mIODevice.mSendDevInfo != null;
    }

    /**
     * Returns the input name of the IO device
     */
    public String getInputName() {
        return mIODevice.getInputName();
    }

    /**
     * Returns the output name of the IO device
     */
    public String getOutputName() {
        return mIODevice.getOutputName();
    }

    /**
     * Scans an array of MidiDeviceInfo
     */
    public void scanDevices(Collection<MidiDeviceInfo> devInfos) {
        mIODevice.scanDevices(devInfos);
    }

    /**
     * Starts a timeout timer that updates the UI if the timeout is triggered
     */
    public void startTimeoutHandler() {
        final int currentTestCounter = mTestCounter;
        // Start the timeout timer
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (mTestLock) {
                    if (mTestRunning && currentTestCounter == mTestCounter) {
                        // Timeout
                        showTimeoutMessageAbstract();
                        enableTestButtonsAbstract(true);
                    }
                }
            }
        };
        mTimeoutTimer.schedule(task, TEST_TIMEOUT_MS);
    }
}
