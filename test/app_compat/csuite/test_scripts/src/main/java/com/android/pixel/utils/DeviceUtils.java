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

package com.android.pixel.utils;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import com.google.common.base.Preconditions;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

public class DeviceUtils {
    private static final String TAG = DeviceUtils.class.getSimpleName();
    private static final String LOG_DATA_DIR = "/sdcard/logData";
    private static final int MAX_RECORDING_PARTS = 5;
    private static final long WAIT_ONE_SECOND_IN_MS = 1000;
    private static final long VIDEO_TAIL_BUFFER = 500;
    private static final String DISMISS_KEYGUARD = "wm dismiss-keyguard";

    private String mFolderDir = LOG_DATA_DIR;
    private String mTestName = TAG;
    private RecordingThread mCurrentThread;
    private File mLogDataDir;
    private UiDevice mDevice;

    public DeviceUtils(UiDevice device) {
        mDevice = device;
    }

    /**
     * Sets the test name and the folder path for the current test.
     *
     * @param testName The test name.
     */
    public void setTestName(String testName) {
        Optional<String> optionalTestName = Optional.ofNullable(testName);
        if (optionalTestName.isPresent()) {
            mTestName = optionalTestName.get();
            mFolderDir = String.join("/", LOG_DATA_DIR, optionalTestName.get());
        } else {
            Preconditions.checkNotNull(testName, "testName cannot be null");
        }
    }

    /** Create a directory to save test screenshots, screenrecord and text files. */
    public void createLogDataDir() {
        mLogDataDir = new File(mFolderDir);
        if (mLogDataDir.exists()) {
            String[] children = mLogDataDir.list();
            for (String file : children) {
                new File(mLogDataDir, file).delete();
            }
        } else {
            mLogDataDir.mkdirs();
        }
    }

    /** Wake up the device and dismiss the keyguard. */
    public void wakeAndUnlockScreen() throws Exception {
        mDevice.wakeUp();
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
        mDevice.executeShellCommand(DISMISS_KEYGUARD);
        SystemClock.sleep(WAIT_ONE_SECOND_IN_MS);
    }

    /**
     * Go back to home screen by pressing back key five times and home key to avoid the infinite
     * loop since some apps' activities cannot be exited to home screen by back key event.
     */
    public void backToHome(String launcherPkg) {
        for (int i = 0; i < 5; i++) {
            mDevice.pressBack();
            mDevice.waitForIdle();
            if (mDevice.hasObject(By.pkg(launcherPkg))) {
                break;
            }
        }
        mDevice.pressHome();
    }

    /**
     * Launch an app with the given package name
     *
     * @param packageName Name of package to be launched
     */
    public void launchApp(String packageName) {
        Context context = getInstrumentation().getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Take a screenshot on the device and save it in {@code logDataDir}.
     *
     * @param packageName The package name of 3P apps screenshotted.
     * @param description The description of actions or operations on the device.
     */
    public void takeScreenshot(String packageName, String description) {
        File screenshot =
                new File(
                        mFolderDir,
                        String.format(
                                "%s_%s_screenshot_%s.png", mTestName, packageName, description));
        mDevice.takeScreenshot(screenshot);
    }

    /**
     * Start the screen recording.
     *
     * @param packageName The package name of 3P apps screenrecorded.
     */
    public void startRecording(String packageName) {
        Log.v(TAG, "Started Recording");
        mCurrentThread =
                new RecordingThread(
                        "test-screen-record",
                        String.format("%s_%s_screenrecord", mTestName, packageName));
        mCurrentThread.start();
    }

    /** Stop already started screen recording. */
    public void stopRecording() {
        // Skip if not directory.
        if (mLogDataDir == null) {
            return;
        }
        // Add some extra time to the video end.
        SystemClock.sleep(VIDEO_TAIL_BUFFER);
        // Ctrl + C all screen record processes.
        mCurrentThread.cancel();
        // Wait for the thread to completely die.
        try {
            mCurrentThread.join();
        } catch (InterruptedException ex) {
            Log.e(TAG, "Interrupted when joining the recording thread.", ex);
        }
        Log.v(TAG, "Stopped Recording");
    }

    /** Returns the recording's name for {@code part} of launch description. */
    public File getOutputFile(String description, int part) {
        // Omit the iteration number for the first iteration.
        final String fileName = String.format("%s-video%s.mp4", description, part == 1 ? "" : part);
        return Paths.get(mLogDataDir.getAbsolutePath(), fileName).toFile();
    }

    /**
     * Encapsulates the start and stop screen recording logic. Copied from ScreenRecordCollector.
     */
    private class RecordingThread extends Thread {
        private final String mDescription;

        private boolean mContinue;

        RecordingThread(String name, String description) {
            super(name);

            mContinue = true;

            Assert.assertNotNull("No test description provided for recording.", description);
            mDescription = description;
        }

        @Override
        public void run() {
            try {
                // Start at i = 1 to encode parts as X.mp4, X2.mp4, X3.mp4, etc.
                for (int i = 1; i <= MAX_RECORDING_PARTS && mContinue; i++) {
                    File output = getOutputFile(mDescription, i);
                    Log.d(TAG, String.format("Recording screen to %s", output.getAbsolutePath()));
                    // Make sure not to block on this background command in the main thread so
                    // that the test continues to run, but block in this thread so it does not
                    // trigger a new screen recording session before the prior one completes.
                    mDevice.executeShellCommand(
                            String.format("screenrecord %s", output.getAbsolutePath()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Caught exception while screen recording.");
            }
        }

        public void cancel() {
            mContinue = false;
            // Identify the screenrecord PIDs and send SIGINT 2 (Ctrl + C) to each.
            try {
                String[] pids = mDevice.executeShellCommand("pidof screenrecord").split(" ");
                for (String pid : pids) {
                    // Avoid empty process ids, because of weird splitting behavior.
                    if (pid.isEmpty()) {
                        continue;
                    }
                    mDevice.executeShellCommand(String.format("kill -2 %s", pid));
                    Log.d(TAG, String.format("Sent SIGINT 2 to screenrecord process (%s)", pid));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to kill screen recording process.");
            }
        }
    }
}
