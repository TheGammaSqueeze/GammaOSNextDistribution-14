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

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_EMBEDDED;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.server.wm.ShellCommandHelper.executeShellCommandAndGetStdout;
import static android.server.wm.StateLogger.log;
import static android.server.wm.StateLogger.logE;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.UiDeviceUtils.pressEnterButton;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.UiDeviceUtils.pressSleepButton;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.UiDeviceUtils.waitForDeviceIdle;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.view.Display.DEFAULT_DISPLAY;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

public class LockScreenSession implements AutoCloseable {
    enum LockState {
        LOCK_DISABLED,
        LOCK_ENABLED
    }

    private static final boolean DEBUG = true;
    private static final String LOCK_CREDENTIAL = "1234";

    private final Instrumentation mInstrumentation;
    private final Context mContext;

    private final LockState mInitialState;
    private final AmbientDisplayConfiguration mAmbientDisplayConfiguration;

    private final WindowManagerStateHelper mWmState;
    private final TouchHelper mTouchHelper;

    private final DisplayManager mDm;
    private final KeyguardManager mKm;

    private boolean mLockCredentialSet;

    public LockScreenSession(Instrumentation instrumentation, WindowManagerStateHelper wmState) {
        mInstrumentation = instrumentation;
        mWmState = wmState;
        mContext = instrumentation.getContext();
        mTouchHelper = new TouchHelper(instrumentation, wmState);
        mDm = mContext.getSystemService(DisplayManager.class);
        mKm = mContext.getSystemService(KeyguardManager.class);

        // Store the initial state so that it can be restored when the session
        // goes out of scope.
        mInitialState = isLockDisabled() ? LockState.LOCK_DISABLED : LockState.LOCK_ENABLED;

        // Enable lock screen (swipe) by default.
        setLockDisabled(false);
        mAmbientDisplayConfiguration = new AmbientDisplayConfiguration(mContext);

        // On devices that don't support any insecure locks but supports a secure lock, let's
        // enable a secure lock.
        if (!supportsInsecureLock() && supportsSecureLock()) {
            setLockCredential();
        }
    }

    private boolean getSupportsInsecureLockScreen() {
        boolean insecure;
        try {
            insecure = mContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsInsecureLockScreen", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            insecure = true;
        }
        return insecure;
    }

    /** Whether or not the device supports pin/pattern/password lock. */
    private boolean supportsSecureLock() {
        return FeatureUtil.hasSystemFeature(FEATURE_SECURE_LOCK_SCREEN);
    }

    /** Whether or not the device supports "swipe" lock. */
    private boolean supportsInsecureLock() {
        return !FeatureUtil.hasAnySystemFeature(
                FEATURE_LEANBACK, FEATURE_WATCH, FEATURE_EMBEDDED, FEATURE_AUTOMOTIVE)
                && getSupportsInsecureLockScreen();
    }

    protected static String runCommandAndPrintOutput(String command) {
        final String output = executeShellCommandAndGetStdout(command);
        log(output);
        return output;
    }

    /**
     * Sets a credential to use with a secure lock method.
     */
    public LockScreenSession setLockCredential() {
        if (mLockCredentialSet) {
            // "set-pin" command isn't idempotent. We need to provide the old credential in
            // order to change it to a new one. However we never use a different credential in
            // CTS so we don't need to do anything if the credential is already set.
            return this;
        }
        mLockCredentialSet = true;
        runCommandAndPrintOutput(
                "locksettings set-pin " + LOCK_CREDENTIAL);
        return this;
    }

    /**
     * Unlocks a device by entering a lock credential.
     */
    public LockScreenSession enterAndConfirmLockCredential() {
        // Ensure focus will switch to default display. Meanwhile we cannot tap on center area,
        // which may tap on input credential area.
        mTouchHelper.touchAndCancelOnDisplayCenterSync(DEFAULT_DISPLAY);

        waitForDeviceIdle(3000);
        SystemUtil.runWithShellPermissionIdentity(
                () -> mInstrumentation.sendStringSync(LOCK_CREDENTIAL));
        pressEnterButton();
        return this;
    }

    private static void removeLockCredential() {
        runCommandAndPrintOutput("locksettings clear --old " + LOCK_CREDENTIAL);
    }

    /**
     * Disables the lock screen. Clears the secure credential first if one is set.
     */
    public LockScreenSession disableLockScreen() {
        // Lock credentials need to be cleared before disabling the lock.
        if (mLockCredentialSet) {
            removeLockCredential();
            mLockCredentialSet = false;
        }
        setLockDisabled(true);
        return this;
    }

    private boolean isDisplayOn() {
        final Display display = mDm.getDisplay(DEFAULT_DISPLAY);
        return display != null && display.getState() == Display.STATE_ON;
    }

    /**
     * Puts the device to sleep with intention of locking if a lock is enabled.
     */
    public LockScreenSession sleepDevice() {
        pressSleepButton();
        // Not all device variants lock when we go to sleep, so we need to explicitly lock the
        // device. Note that pressSleepButton() above is redundant because the action also
        // puts the device to sleep, but kept around for clarity.
        if (FeatureUtil.isWatch()) {
            mInstrumentation.getUiAutomation().performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
        if (mAmbientDisplayConfiguration.alwaysOnEnabled(
                android.os.Process.myUserHandle().getIdentifier())) {
            mWmState.waitForAodShowing();
        } else {
            Condition.waitFor("display to turn off", () -> !isDisplayOn());
        }
        if (!isLockDisabled()) {
            mWmState.waitFor(
                    state -> state.getKeyguardControllerState().keyguardShowing,
                    "Keyguard showing");
        }
        return this;
    }

    /**
     * Wakes the device up.
     */
    public LockScreenSession wakeUpDevice() {
        pressWakeupButton();
        return this;
    }

    /**
     * Unlocks the device by using the unlock button.
     */
    public LockScreenSession unlockDevice() {
        // Make sure the unlock button event is send to the default display.
        mTouchHelper.touchAndCancelOnDisplayCenterSync(DEFAULT_DISPLAY);

        pressUnlockButton();
        return this;
    }

    /**
     * Locks the device and wakes it up so that the keyguard is shown.
     * @param showWhenLockedActivities Activities to check for after showing the keyguard.
     */
    public LockScreenSession gotoKeyguard(ComponentName... showWhenLockedActivities) {
        if (DEBUG && isLockDisabled()) {
            logE("LockScreenSession.gotoKeyguard() is called without lock enabled.");
        }
        sleepDevice();
        wakeUpDevice();
        if (showWhenLockedActivities.length == 0) {
            mWmState.waitForKeyguardShowingAndNotOccluded();
        } else {
            mWmState.waitForValidState(showWhenLockedActivities);
        }
        return this;
    }

    private boolean isKeyguardLocked() {
        return mKm != null && mKm.isKeyguardLocked();
    }

    @Override
    public void close() {
        final boolean wasCredentialSet = mLockCredentialSet;
        boolean wasDeviceLocked = false;
        if (mLockCredentialSet) {
            wasDeviceLocked = mKm != null && mKm.isDeviceLocked();
            removeLockCredential();
            mLockCredentialSet = false;
        }

        // Restore the initial state.
        switch (mInitialState) {
            case LOCK_DISABLED -> setLockDisabled(true);
            case LOCK_ENABLED -> setLockDisabled(false);
        }

        // Dismiss active keyguard after credential is cleared, so keyguard doesn't ask for
        // the stale credential.
        // TODO (b/112015010) If keyguard is occluded, credential cannot be removed as expected.
        // LockScreenSession#close is always called before stopping all test activities,
        // which could cause the keyguard to stay occluded after wakeup.
        // If Keyguard is occluded, pressing the back key can hide the ShowWhenLocked activity.
        wakeUpDevice();
        pressBackButton();

        // If the credential wasn't set, the steps for restoring can be simpler.
        if (!wasCredentialSet) {
            mWmState.computeState();
            if (WindowManagerStateHelper.isKeyguardShowingAndNotOccluded(mWmState)) {
                // Keyguard is showing and not occluded so only need to unlock.
                unlockDevice();
                return;
            }

            final ComponentName home = mWmState.getHomeActivityName();
            if (home != null && mWmState.hasActivityState(home, STATE_RESUMED)) {
                // Home is resumed so nothing to do (e.g. after finishing show-when-locked app).
                return;
            }
        }

        // If device is unlocked, there might have ShowWhenLocked activity runs on,
        // use home key to clear all activity at foreground.
        pressHomeButton();
        if (wasDeviceLocked) {
            // The removal of credential needs an extra cycle to take effect.
            sleepDevice();
            wakeUpDevice();
        }
        if (isKeyguardLocked()) {
            unlockDevice();
        }
    }

    /**
     * Returns whether the lock screen is disabled.
     *
     * @return true if the lock screen is disabled, false otherwise.
     */
    private boolean isLockDisabled() {
        final String isLockDisabled = runCommandAndPrintOutput(
                "locksettings get-disabled " + oldIfNeeded()).trim();
        return !"null".equals(isLockDisabled) && Boolean.parseBoolean(isLockDisabled);
    }

    /**
     * Disable the lock screen.
     *
     * @param lockDisabled true if should disable, false otherwise.
     */
    private void setLockDisabled(boolean lockDisabled) {
        runCommandAndPrintOutput("locksettings set-disabled " + lockDisabled);
    }

    @NonNull
    private String oldIfNeeded() {
        if (mLockCredentialSet) {
            return " --old " + ActivityManagerTestBase.LOCK_CREDENTIAL + " ";
        }
        return "";
    }
}
