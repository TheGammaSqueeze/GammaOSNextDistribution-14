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

package com.android.bedstead.nene.device;

import static com.android.bedstead.nene.permissions.CommonPermissions.DISABLE_KEYGUARD;

import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.support.test.uiautomator.UiDevice;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;

/** Helper methods related to the device. */
public final class Device {
    public static final Device sInstance = new Device();
    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static final KeyguardManager sKeyguardManager =
            TestApis.context().instrumentedContext()
            .getSystemService(KeyguardManager.class);
    private static KeyguardManager.KeyguardLock sKeyguardLock;

    private static PackageManager sPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();

    private Device() {

    }

    private KeyguardManager.KeyguardLock keyGuardLock() {
        if (sKeyguardManager == null) {
            if (TestApis.packages().instrumented().isInstantApp()) {
                throw new NeneException("Cannot manage keyguard with instant app");
            }

            throw new NeneException("KeyguardManager cannot be found.");
        }

        if (sKeyguardLock == null) {
            synchronized (Device.class) {
                if (sKeyguardLock == null) {
                    sKeyguardLock = sKeyguardManager.newKeyguardLock("Nene");
                }
            }
        }

        return sKeyguardLock;
    }

    /**
     * Turn the screen on.
     */
    public void wakeUp() {
        ShellCommand.builder("input keyevent")
                .addOperand("KEYCODE_WAKEUP")
                .allowEmptyOutput(true)
                .validate(String::isEmpty)
                .executeOrThrowNeneException("Error waking up device");

        Poll.forValue("isScreenOn", this::isScreenOn)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    /**
     * Dismiss the keyguard.
     */
    public void unlock() {
        ShellCommand.builder("wm dismiss-keyguard")
                .allowEmptyOutput(true)
                .validate(String::isEmpty)
                .executeOrThrowNeneException("Error dismissing keyguard");
    }

    /**
     * Set the screen on setting.
     *
     * <p>When enabled, the device will never sleep.
     */
    @Experimental
    public void keepScreenOn(boolean stayOn) {
        // one day vs default
        TestApis.settings().system().putInt("screen_off_timeout", stayOn ? 86400000 : 121000);
        ShellCommand.builder("svc power stayon")
                .addOperand(stayOn ? "true" : "false")
                .allowEmptyOutput(true)
                .validate(String::isEmpty)
                .executeOrThrowNeneException("Error setting stayOn");
        unlock();
    }

    @Experimental
    public void setKeyguardEnabled(boolean keyguardEnabled) {
        try (PermissionContext p = TestApis.permissions().withPermission(DISABLE_KEYGUARD)) {
            if (keyguardEnabled) {
                keyGuardLock().reenableKeyguard();
            } else {
                keyGuardLock().disableKeyguard();
            }
        }
    }

    /**
     * True if the screen is on.
     */
    public boolean isScreenOn() {
        try {
            return sDevice.isScreenOn();
        } catch (RemoteException e) {
            throw new NeneException("Error getting isScreenOn", e);
        }
    }

    /**
     * Returns True if this device is a handheld device.
     */
    @Experimental
    public boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch, NOT tv, NOT car and NOT PC.
        return sPackageManager.hasSystemFeature(sPackageManager.FEATURE_TOUCHSCREEN)
                && !sPackageManager.hasSystemFeature(sPackageManager.FEATURE_WATCH)
                && !sPackageManager.hasSystemFeature(sPackageManager.FEATURE_TELEVISION)
                && !sPackageManager.hasSystemFeature(sPackageManager.FEATURE_AUTOMOTIVE)
                && !sPackageManager.hasSystemFeature(sPackageManager.FEATURE_PC);
    }
}
