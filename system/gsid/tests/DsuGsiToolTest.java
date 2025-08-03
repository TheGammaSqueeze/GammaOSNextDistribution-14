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

package com.android.tests.dsu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class DsuGsiToolTest extends DsuTestBase {
    private static final long DSU_MAX_WAIT_SEC = 10 * 60;

    private String getDsuInstallCommand(String slotName) {
        return String.format("am start-activity"
                + " -n com.android.dynsystem/com.android.dynsystem.VerificationActivity"
                + " -a android.os.image.action.START_INSTALL"
                + " --el KEY_USERDATA_SIZE 2147483648"
                + " --ez KEY_ENABLE_WHEN_COMPLETED true"
                + " --es KEY_DSU_SLOT %s", slotName);
    }

    @Before
    public void setUp() throws DeviceNotAvailableException {
        if (isDsuRunning()) {
            assertAdbRoot();
            CLog.i("Wipe existing DSU installation");
            assertShellCommand("gsi_tool wipe");
            getDevice().reboot();
            assertDsuNotRunning();
        }
        getDevice().disableAdbRoot();
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        if (isDsuRunning()) {
            getDevice().reboot();
        }
        getDevice().executeShellCommand("gsi_tool wipe");
    }

    @Test
    public void testNonLockedDsu() throws DeviceNotAvailableException {
        final String slotName = "foo";
        assertShellCommand(getDsuInstallCommand(slotName));
        CLog.i("Wait for DSU installation complete and reboot");
        assertTrue(
                "Timed out waiting for DSU installation complete",
                getDevice().waitForDeviceNotAvailable(DSU_MAX_WAIT_SEC * 1000));
        CLog.i("DSU installation is complete and device is disconnected");

        getDevice().waitForDeviceAvailable();
        assertDsuRunning();
        CLog.i("Successfully booted with DSU");

        // These commands should run without any error
        assertShellCommand("gsi_tool enable");
        assertShellCommand("gsi_tool disable");
        assertShellCommand("gsi_tool wipe");
    }

    @Test
    public void testLockedDsu() throws DeviceNotAvailableException {
        final String slotName = "foo.lock";
        assertShellCommand(getDsuInstallCommand(slotName));
        CLog.i("Wait for DSU installation complete and reboot");
        assertTrue(
                "Timed out waiting for DSU installation complete",
                getDevice().waitForDeviceNotAvailable(DSU_MAX_WAIT_SEC * 1000));
        CLog.i("DSU installation is complete and device is disconnected");

        getDevice().waitForDeviceAvailable();
        assertDsuRunning();
        CLog.i("Successfully booted with DSU");

        // These commands should fail on a locked DSU
        var result = getDevice().executeShellV2Command("gsi_tool enable");
        assertFalse(result.getStatus() == CommandStatus.SUCCESS);
        result = getDevice().executeShellV2Command("gsi_tool disable");
        assertFalse(result.getStatus() == CommandStatus.SUCCESS);
        result = getDevice().executeShellV2Command("gsi_tool wipe");
        assertFalse(result.getStatus() == CommandStatus.SUCCESS);
    }
}
