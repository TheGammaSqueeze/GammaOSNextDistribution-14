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

package android.server.wm;

import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.ShellCommandHelper.executeShellCommandAndGetStdout;
import static android.view.Surface.ROTATION_0;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.server.wm.settings.SettingsSession;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

/** Helper class to save, set & wait, and restore rotation related preferences. */
public class RotationSession extends SettingsSession<Integer> {
    private static final String FIXED_TO_USER_ROTATION_COMMAND =
            "cmd window fixed-to-user-rotation ";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private final SettingsSession<Integer> mAccelerometerRotation;
    private final HandlerThread mThread;
    private final SettingsObserver mRotationObserver;
    private int mPreviousDegree;
    private final String mPreviousFixedToUserRotationMode;

    private final WindowManagerStateHelper mWmState;

    public RotationSession() {
        this(new WindowManagerStateHelper());
    }

    public RotationSession(WindowManagerStateHelper wmState) {
        // Save user_rotation and accelerometer_rotation preferences.
        super(Settings.System.getUriFor(Settings.System.USER_ROTATION),
                Settings.System::getInt, Settings.System::putInt);
        mAccelerometerRotation = new SettingsSession<>(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                Settings.System::getInt, Settings.System::putInt);
        mWmState = wmState;

        mThread = new HandlerThread("Observer_Thread");
        mThread.start();
        Handler runnableHandler = new Handler(mThread.getLooper());
        mRotationObserver = new SettingsObserver(runnableHandler);

        // Disable fixed to user rotation
        mPreviousFixedToUserRotationMode = executeShellCommandAndGetStdout(
                FIXED_TO_USER_ROTATION_COMMAND);
        executeShellCommand(FIXED_TO_USER_ROTATION_COMMAND + "disabled");

        mPreviousDegree = get();
        // Disable accelerometer_rotation.
        mAccelerometerRotation.set(0);
    }

    @Override
    public void set(@NonNull Integer value) {
        set(value, true /* waitDeviceRotation */);
    }

    /**
     * Sets the rotation preference.
     *
     * @param value The rotation between {@link android.view.Surface#ROTATION_0} ~
     *              {@link android.view.Surface#ROTATION_270}
     * @param waitDeviceRotation If {@code true}, it will wait until the display has applied the
     *                           rotation. Otherwise it only waits for the settings value has
     *                           been changed.
     */
    public void set(@NonNull Integer value, boolean waitDeviceRotation) {
        // When the rotation is locked and the SystemUI receives the rotation becoming 0deg, it
        // will call freezeRotation to WMS, which will cause USER_ROTATION be set to zero again.
        // In order to prevent our test target from being overwritten by SystemUI during
        // rotation test, wait for the USER_ROTATION changed then continue testing.
        final boolean waitSystemUI = value == ROTATION_0 && mPreviousDegree != ROTATION_0;
        final boolean observeRotationSettings = waitSystemUI || !waitDeviceRotation;
        if (observeRotationSettings) {
            mRotationObserver.observe();
        }
        super.set(value);
        mPreviousDegree = value;

        if (waitSystemUI) {
            Condition.waitFor(new Condition<>("rotation notified",
                    // There will receive USER_ROTATION changed twice because when the device
                    // rotates to 0deg, RotationContextButton will also set ROTATION_0 again.
                    () -> mRotationObserver.mCount == 2).setRetryIntervalMs(500));
        }

        if (waitDeviceRotation) {
            // Wait for the display to apply the rotation.
            mWmState.waitForRotation(value);
        } else {
            // Wait for the settings have been changed.
            Condition.waitFor(new Condition<>("rotation setting changed",
                    () -> mRotationObserver.mCount > 0).setRetryIntervalMs(100));
        }

        if (observeRotationSettings) {
            mRotationObserver.stopObserver();
        }
    }

    @Override
    public void close() {
        // Restore fixed to user rotation to default
        executeShellCommand(FIXED_TO_USER_ROTATION_COMMAND + mPreviousFixedToUserRotationMode);
        mThread.quitSafely();
        super.close();
        // Restore accelerometer_rotation preference.
        mAccelerometerRotation.close();
    }

    private class SettingsObserver extends ContentObserver {
        private int mCount;

        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mCount = 0;
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.USER_ROTATION), false, this);
        }

        void stopObserver() {
            mCount = 0;
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mCount++;
        }
    }
}
