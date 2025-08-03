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

package android.appenumeration.cts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

import com.android.compatibility.common.util.SystemUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A class to support the split installation via the pm command.
 */
public class InstallMultiple {
    private final List<String> mArgs = new ArrayList<>();
    private final List<File> mApks = new ArrayList<>();

    /**
     * Adds an argument to the 'pm install-create' command.
     */
    public InstallMultiple addArg(String arg) {
        mArgs.add(arg);
        return this;
    }

    /**
     * Adds a file path of apk for the installation.
     */
    public InstallMultiple addApk(String apk) {
        mApks.add(new File(apk));
        return this;
    }

    /**
     * To indicate that this installation is used to add one or more split apks to an existing
     * base apk on the device.
     */
    public InstallMultiple inheritFrom(String packageName) {
        addArg("-p " + packageName);
        return this;
    }

    /**
     * Do not kill application while the split apks updated.
     */
    public InstallMultiple dontKill() {
        addArg("--dont-kill");
        return this;
    }

    /**
     * Invokes the pm command.
     */
    public void run() {
        final int sessionId = createSession();
        writeSession(sessionId);
        commitSession(sessionId);
    }

    private int createSession() {
        final StringBuilder cmd = new StringBuilder("pm install-create");
        for (String arg : mArgs) {
            cmd.append(' ').append(arg);
        }
        final String result = SystemUtil.runShellCommand(cmd.toString());
        assertThat(result, startsWith("Success"));

        final int start = result.lastIndexOf("[");
        final int end = result.lastIndexOf("]");
        int sessionId = -1;
        try {
            if (start != -1 && end != -1 && start < end) {
                sessionId = Integer.parseInt(result.substring(start + 1, end));
            }
        } catch (NumberFormatException e) {
        }
        if (sessionId == -1) {
            throw new IllegalStateException("Failed to create install session: " + result);
        }
        return sessionId;
    }

    private void writeSession(int sessionId) {
        for (int i = 0; i < mApks.size(); i++) {
            final File apk = mApks.get(i);
            final StringBuilder cmd = new StringBuilder("pm install-write");
            cmd.append(' ').append(sessionId);
            cmd.append(' ').append(i + "_" + apk.getName());
            cmd.append(' ').append(apk.getAbsolutePath());

            final String result = SystemUtil.runShellCommand(cmd.toString());
            assertThat(result, startsWith("Success"));
        }
    }

    private void commitSession(int sessionId) {
        final StringBuilder cmd = new StringBuilder("pm install-commit");
        cmd.append(' ').append(sessionId);

        final String result = SystemUtil.runShellCommand(cmd.toString());
        assertThat(result, startsWith("Success"));
    }
}
