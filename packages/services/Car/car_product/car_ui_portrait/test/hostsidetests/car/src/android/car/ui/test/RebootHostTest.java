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
package android.car.ui;

import android.car.cts.CarHostJUnit4TestCase;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

/** Check portrait UI resumes after reboot. */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class RebootHostTest extends CarHostJUnit4TestCase {
    private static final int SYSTEM_RESTART_TIMEOUT_SEC = 100;
    private static final String APP_GRID =
            "com.android.car.portraitlauncher/com.android.car.carlauncher.AppGridActivity";
    private static final String BACKGROUND_BASE =
            "com.android.car.portraitlauncher/com.android.car.portraitlauncher.homeactivities"
                    + ".BackgroundPanelBaseActivity";

    @Test
    public void reboot_requiredTaskShows() throws Exception {
        restartOrReboot();
        CommonTestUtils.waitUntil("timed out waiting for carlauncher", SYSTEM_RESTART_TIMEOUT_SEC,
                this::isCarPortraitLauncherReady);
    }

    private boolean isCarPortraitLauncherReady() {
        String cmd = "am stack list";
        String stackList = null;
        try {
            stackList = getDevice().executeShellCommand(cmd);
        } catch (DeviceNotAvailableException e) {
            CLog.i("%s failed: %s", cmd, e.getMessage());
            return false;
        }

        CLog.i("Stack list: %s" + stackList);
        return isCarPortraitLauncherActivitiesLaunched(stackList);
    }

    private boolean isCarPortraitLauncherActivitiesLaunched(String stackList) {
        HashMap<String, TaskInfo> activeComponents = TaskInfo.getActiveComponents(stackList);
        return isTaskVisible(BACKGROUND_BASE, activeComponents) && isTaskVisible(APP_GRID,
                activeComponents);
    }

    private boolean isTaskVisible(String componentString,
            HashMap<String, TaskInfo> activeComponents) {
        return activeComponents.containsKey(componentString) && activeComponents.get(
                componentString).isVisible();
    }

    private void restartOrReboot() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();

        if (device.isAdbRoot()) {
            CLog.d("Restarting system server");
            device.executeShellCommand("stop");
            device.executeShellCommand("start");
            return;
        }

        CLog.d("Only root user can restart system server; rebooting instead");
        getDevice().reboot();
    }
}
