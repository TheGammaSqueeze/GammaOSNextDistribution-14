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

package android.virtualdevice.cts.applaunch;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class RecentTasksTest {

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final ActivityManager mActivityManager =
            getInstrumentation().getContext().getSystemService(ActivityManager.class);


    @Test
    public void activityLaunchedOnVirtualDeviceWithDefaultRecentPolicy_includedInRecents() {
        VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(DEVICE_POLICY_DEFAULT);
        VirtualDisplay virtualDisplay = createPublicVirtualDisplay(virtualDevice);

        Activity activity = mRule.startActivityOnDisplaySync(virtualDisplay, EmptyActivity.class);
        final int taskId = activity.getTaskId();
        assertThat(isTaskIncludedInRecents(taskId)).isTrue();

        if (Flags.dynamicPolicy()) {
            virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM);
            assertThat(isTaskIncludedInRecents(taskId)).isFalse();
            virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
            assertThat(isTaskIncludedInRecents(taskId)).isTrue();
        }

        // Make sure the policy is respected even after the device / display are gone.
        virtualDevice.close();
        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        assertThat(isTaskIncludedInRecents(taskId)).isTrue();
    }

    @Test
    public void activityLaunchedOnVirtualDeviceWithCustomRecentPolicy_excludedFromRecents() {
        VirtualDevice virtualDevice = createVirtualDeviceWithRecentsPolicy(DEVICE_POLICY_CUSTOM);
        VirtualDisplay virtualDisplay = createPublicVirtualDisplay(virtualDevice);

        Activity activity = mRule.startActivityOnDisplaySync(virtualDisplay, EmptyActivity.class);
        final int taskId = activity.getTaskId();
        assertThat(isTaskIncludedInRecents(taskId)).isFalse();

        if (Flags.dynamicPolicy()) {
            virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
            assertThat(isTaskIncludedInRecents(taskId)).isTrue();
            virtualDevice.setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM);
            assertThat(isTaskIncludedInRecents(taskId)).isFalse();
        }

        // Make sure the policy is respected even after the device / display are gone.
        virtualDevice.close();
        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        assertThat(isTaskIncludedInRecents(taskId)).isFalse();
    }

    private VirtualDevice createVirtualDeviceWithRecentsPolicy(
            @VirtualDeviceParams.DevicePolicy int recentsPolicy) {
        return mRule.createManagedVirtualDevice(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_RECENTS, recentsPolicy)
                .build());
    }

    private VirtualDisplay createPublicVirtualDisplay(VirtualDevice virtualDevice) {
        return mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
    }

    private boolean isTaskIncludedInRecents(int taskId) {
        return mActivityManager.getRecentTasks(Integer.MAX_VALUE, /*flags=*/0)
                .stream().anyMatch(task -> task.taskId == taskId);
    }
}
