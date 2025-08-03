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

package android.adpf.hintsession.app;

import static android.adpf.common.ADPFHintSessionConstants.TESTS_ENABLED;
import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;
import static android.adpf.common.ADPFHintSessionConstants.IS_HINT_SESSION_SUPPORTED_KEY;

import android.app.KeyguardManager;
import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple activity to create and use hint session APIs.
 */
public class ADPFHintSessionDeviceActivity
        extends NativeActivity {

    static {
        System.loadLibrary(
                "adpfhintsession_test_helper_jni");
    }

    protected void onResume() {
        super.onResume();
        setFullscreen();
    }

    protected void setFailure(String message) {
        synchronized (mMetrics) {
            mMetrics.put("failure", message);
        }
    }

    private static final String TAG = android.adpf.hintsession.app
            .ADPFHintSessionDeviceActivity.class.getSimpleName();

    private final Map<String, String> mMetrics = new HashMap<>();

    /**
     * Flag used to indicate tests are finished, used by
     * waitForTestFinished to allow the instrumentation to block
     * on test completion correctly.
     */
    private Boolean mFinished = false;
    private final Object mFinishedLock = new Object();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        keepScreenOn();
        Intent intent = getIntent();
        String testName = intent.getStringExtra(
                TEST_NAME_KEY);
        if (testName == null) {
            setFailure("Test starts without name");
            return;
        }
        Log.i(TAG, "Device activity created");
        sendConfigToNative(TESTS_ENABLED);
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null) {
            km.requestDismissKeyguard(this, null);
        }
    }

    private void setFullscreen() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Sends test information to the native code and signals
     * it to start running performance tests.
     */
    public native void sendConfigToNative(String[] data);

    /**
     * Sends test information from the native code back up
     * to the Java code once testing is complete
     */
    public void sendResultsToJava(String[] names,
            String[] values) {
        synchronized (mMetrics) {
            for (int i = 0; i < names.length; ++i) {
                mMetrics.put(names[i], values[i]);
            }
            String key = mMetrics.get(IS_HINT_SESSION_SUPPORTED_KEY);
            if (key != null && key.equals("false")) {
                Log.i(TAG, "Skipping the test as the hint session is not supported");
            }
        }

        setFinished();
    }

    /**
     * Signals to the app that everything is finished,
     */
    public void setFinished() {
        synchronized (mFinishedLock) {
            mFinished = true;
            mFinishedLock.notifyAll();
        }
    }

    /**
     * Blocks until the test has completed, to allow instrumentation
     * to wait for the test to completely finish
     */
    public void waitForTestFinished() {
        while (true) {
            synchronized (mFinishedLock) {
                if (mFinished) {
                    break;
                }
                try {
                    mFinishedLock.wait();
                } catch (InterruptedException e) {
                    System.err.println("Interrupted!");
                    break;
                }
            }
        }
    }

    /**
     * Gets the hint session test metrics.
     */
    public Map<String, String> getMetrics() {
        synchronized (mMetrics) {
            return new HashMap<>(mMetrics);
        }
    }
}
