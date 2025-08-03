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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System property info collector.
 */
public class PropertyDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        store.startArray("ro_property");
        try {
            ITestDevice device = getDevice();
            CommandResult commandResult = device.executeShellV2Command("getprop");
            if (commandResult.getExitCode() == null) {
                throw new NullPointerException("getprop exit code is null");
            }
            if (commandResult.getExitCode() != 0) {
                throw new IllegalStateException(
                        String.format("getprop returns %d: %s", commandResult.getExitCode(),
                                commandResult.getStderr()));
            }
            if (commandResult.getExitCode() == 0 && !commandResult.getStderr().isEmpty()) {
                CLog.w("Warnings occur when running getprop:\n%s",
                        commandResult.getStderr());
            }

            parseProps(commandResult.getStdout(), store);
        } finally {
            store.endArray();
        }
    }

    private void parseProps(String stdout, HostInfoStore store) throws Exception {
        Pattern pattern = Pattern.compile("\\[(ro.+)\\]: \\[(.+)\\]");
        if (stdout == null) stdout = "";
        boolean hasMatched = false;
        try (Scanner scanner = new Scanner(stdout)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String name = matcher.group(1);
                    String value = matcher.group(2);

                    store.startGroup();
                    store.addResult("name", name);
                    store.addResult("value", value);
                    store.endGroup();
                    hasMatched = true;
                }
            }
        }
        if (!hasMatched) {
            throw new IllegalStateException(
                    "Unable to find any read-only properties. Output is " + stdout);
        }
    }
}
