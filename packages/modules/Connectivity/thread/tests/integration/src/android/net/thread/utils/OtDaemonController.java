/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.thread.utils;

import android.net.InetAddresses;
import android.os.SystemClock;

import com.android.compatibility.common.util.SystemUtil;

import java.net.Inet6Address;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper of the "/system/bin/ot-ctl" which can be used to send CLI commands to ot-daemon to
 * control its behavior.
 *
 * <p>Note that this class takes root privileged to run.
 */
public final class OtDaemonController {
    private static final String OT_CTL = "/system/bin/ot-ctl";

    /**
     * Factory resets ot-daemon.
     *
     * <p>This will erase all persistent data written into apexdata/com.android.apex/ot-daemon and
     * restart the ot-daemon service.
     */
    public void factoryReset() {
        executeCommand("factoryreset");

        // TODO(b/323164524): ot-ctl is a separate process so that the tests can't depend on the
        // time sequence. Here needs to wait for system server to receive the ot-daemon death
        // signal and take actions.
        // A proper fix is to replace "ot-ctl" with "cmd thread_network ot-ctl" which is
        // synchronized with the system server
        SystemClock.sleep(500);
    }

    /** Returns the list of IPv6 addresses on ot-daemon. */
    public List<Inet6Address> getAddresses() {
        String output = executeCommand("ipaddr");
        return Arrays.asList(output.split("\n")).stream()
                .map(String::trim)
                .filter(str -> !str.equals("Done"))
                .map(addr -> InetAddresses.parseNumericAddress(addr))
                .map(inetAddr -> (Inet6Address) inetAddr)
                .toList();
    }

    public String executeCommand(String cmd) {
        return SystemUtil.runShellCommand(OT_CTL + " " + cmd);
    }
}
