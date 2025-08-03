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

package android.hardware.input.cts.tests;

import android.app.ActivityOptions;
import android.companion.virtual.VirtualDeviceManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.server.wm.WindowManagerStateHelper;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.Nullable;

import org.junit.Rule;

public abstract class VirtualDeviceTestCase extends InputTestCase {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    VirtualDeviceManager.VirtualDevice mVirtualDevice;
    VirtualDisplay mVirtualDisplay;

    @Override
    void onBeforeLaunchActivity() {
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.getDisplay().getDisplayId());
    }

    @Override
    void onSetUp() {
        onSetUpVirtualInputDevice();
        // Wait for any pending transitions
        WindowManagerStateHelper windowManagerStateHelper = new WindowManagerStateHelper();
        windowManagerStateHelper.waitForAppTransitionIdleOnDisplay(mTestActivity.getDisplayId());
        mInstrumentation.getUiAutomation().syncInputTransactions();
    }

    @Override
    void onTearDown() {
        if (mTestActivity != null) {
            mTestActivity.finish();
        }
    }

    abstract void onSetUpVirtualInputDevice();

    @Override
    @Nullable
    Bundle getActivityOptions() {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                .toBundle();
    }
}
