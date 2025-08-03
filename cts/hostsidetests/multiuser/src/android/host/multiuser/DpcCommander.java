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
package android.host.multiuser;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;

/**
 * Helper class to command this test's DPC (Device Policy Controller) app.
 */
public final class DpcCommander {

    public static final String PKG_NAME = "com.android.cts.multiuser.dpc";
    public static final String PKG_APK = "CtsMultiuserDpc.apk";

    private static final String ADMIN = PKG_NAME + "/.DpcReceiver";
    private static final String SERVICE = PKG_NAME + "/.DpcService";

    private final ITestDevice mDevice;
    private final String mUser;

    private boolean mReadyForCommands;

    private DpcCommander(ITestDevice device, String user)
            throws DeviceNotAvailableException {
        mDevice = device;
        mUser = user;
    }

    public static DpcCommander forCurrentUser(ITestDevice device)
            throws DeviceNotAvailableException {
        return new DpcCommander(device, "cur");
    }

    public void setProfileOwner() throws DeviceNotAvailableException {
        runShellCmd("cmd device_policy set-profile-owner --user %s %s", mUser, ADMIN);
    }

    public void removeActiveAdmin() throws DeviceNotAvailableException {
        runShellCmd("cmd device_policy remove-active-admin --user %s %s", mUser, ADMIN);
        mReadyForCommands = false;
    }

    public void addUserRestriction(String restriction) throws DeviceNotAvailableException {
        runDpcCmd("add-user-restriction %s", restriction);
    }

    public void clearUserRestriction(String restriction) throws DeviceNotAvailableException {
        runDpcCmd("clear-user-restriction %s", restriction);
    }

    /**
     * Sends a {@code cmd} to the DPC service using {@code dumpsys}.
     */
    private CommandResult runDpcCmd(String format, Object... args)
            throws DeviceNotAvailableException {
        waitUntilReady();
        String cmd = String.format(format, args);
        // DPC expect a commands when the first arg to dump() is "cmd"
        CommandResult result = dumpDpc("cmd %s", cmd);
        if (result.getExitCode() != 0 || !result.getStdout().contains("Command succeeded!")) {
            throw new IllegalStateException("Command '" + cmd + "' failed: " + result);
        }
        return result;
    }

    /**
     * Calls {@code dumpsys} in the DPC service.
     */
    private CommandResult dumpDpc(String format, Object... args)
            throws DeviceNotAvailableException {
        return runShellCmd("dumpsys activity --user %s service %s %s", mUser, SERVICE,
                String.format(format, args));
    }

    /**
     * Runs a {@code Shell} command.
     */
    private CommandResult runShellCmd(String format, Object... args)
            throws DeviceNotAvailableException {
        String command = String.format(format, args);
        CommandResult result = mDevice.executeShellV2Command(command);
        CLog.d("Result: %s", result);

        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Command '" + command + "' failed: " + result);
        }
        return result;
    }

    private void waitUntilReady() throws DeviceNotAvailableException {
        if (mReadyForCommands) {
            return;
        }
        int maxTries = 10;
        int sleepTime = 10_000;
        for (int i = 1; i <= maxTries; i++) {
            CommandResult result = dumpDpc("ping");
            if (result.getExitCode() == 0 && result.getStdout().contains("pong")) {
                mReadyForCommands = true;
                return;
            }
            try {
                RunUtil.getDefault().sleep(sleepTime);
            } catch (RunInterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for ping request");
            }
        }
        throw new IllegalStateException("DPC service not responding to dumpsys after "
                + (maxTries * sleepTime) + "ms");
    }
}
