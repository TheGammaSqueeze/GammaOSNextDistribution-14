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
package com.google.android.chre.test.setting;

import android.app.Instrumentation;
import android.hardware.location.NanoAppBinary;

import androidx.test.InstrumentationRegistry;

import com.google.android.chre.nanoapp.proto.ChreSettingsTest;
import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

/**
 * A test to check for behavior when MIC_DISABLE  settings are changed.
 */
public class ContextHubMicDisableSettingsTestExecutor {
    private static final String TAG = "ContextHubMicDisableSettingsTest";
    private final ContextHubSettingsTestExecutor mExecutor;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    /* TODO: Until we implement an API to query the setting state from within CHRE, assume that
     * microphone access is always enabled.
     */
    private final boolean mInitialMicDisabledState = false;

    public ContextHubMicDisableSettingsTestExecutor(NanoAppBinary binary) {
        mExecutor = new ContextHubSettingsTestExecutor(binary);
    }

    /**
     * Should be called in a @Before method.
     */
    public void setUp() {
        mExecutor.init();
    }

    public void runMicDisableSettingsTest() throws InterruptedException {
        setMicrophoneDisableSetting(false /* disableAccess */);
        runTest(ChreSettingsTest.TestCommand.Feature.AUDIO, false /* enableFeature */);

        setMicrophoneDisableSetting(true /* disableAccess */);
        runTest(ChreSettingsTest.TestCommand.Feature.AUDIO, true /* enableFeature */);
    }

    /**
     * Should be called in an @After method.
     */
    public void tearDown() {
        mExecutor.deinit();
        setMicrophoneDisableSetting(mInitialMicDisabledState);
    }

    /**
     * Helper function to set the Microphone Disable user setting
     * @param enable True to disable microphone access, false to allow it.
     */
    private void setMicrophoneDisableSetting(boolean disableAccess) {
        String settingEnableStr = disableAccess ? "enable" : "disable";
        ChreTestUtil.executeShellCommand(mInstrumentation,
                "cmd sensor_privacy " + settingEnableStr + " 0 microphone");

        try {
            Thread.sleep(2000);   // wait for setting to propagate
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Helper function to run the test.
     * @param feature The feature to test.
     * @param enableFeature True for enable.
     */
    private void runTest(ChreSettingsTest.TestCommand.Feature feature, boolean enableFeature)
            throws InterruptedException {
        ChreSettingsTest.TestCommand.State state = enableFeature
                ? ChreSettingsTest.TestCommand.State.ENABLED
                : ChreSettingsTest.TestCommand.State.DISABLED;
        mExecutor.startTestAssertSuccess(feature, state);
    }
}
