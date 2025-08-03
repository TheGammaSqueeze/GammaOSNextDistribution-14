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
package android.app.time.cts.shell.host;

import static org.junit.Assert.fail;

import android.app.time.cts.shell.DeviceShellCommandExecutor;

import androidx.annotation.NonNull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class HostShellCommandExecutor extends DeviceShellCommandExecutor {

    private static final long MAX_TIMEOUT_FOR_COMMAND_MILLIS = 2 * 60 * 1000;
    private static final int RETRY_ATTEMPTS = 1;

    private final ITestDevice mDevice;

    public HostShellCommandExecutor(@NonNull ITestDevice device) {
        mDevice = Objects.requireNonNull(device);
    }

    @Override
    @NonNull
    protected byte[] executeToBytesInternal(String command) throws Exception {
        ByteArrayOutputStream stdOutBytesReceiver = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErrBytesReceiver = new ByteArrayOutputStream();
        CommandResult result = mDevice.executeShellV2Command(
                command, /*pipeAsInput=*/null, stdOutBytesReceiver, stdErrBytesReceiver,
                MAX_TIMEOUT_FOR_COMMAND_MILLIS, TimeUnit.MILLISECONDS, RETRY_ATTEMPTS);
        if (result.getExitCode() != 0 || stdErrBytesReceiver.size() > 0) {
            fail("Command \'" + command + "\' produced exitCode=" + result.getExitCode()
                    + " and stderr="
                    + parseBytesAsString(stdErrBytesReceiver.toByteArray()).trim());
        }
        return stdOutBytesReceiver.toByteArray();
    }

    @Override
    protected void log(String msg) {
        System.out.println("HostShellCommandExecutor: " + msg);
    }
}
