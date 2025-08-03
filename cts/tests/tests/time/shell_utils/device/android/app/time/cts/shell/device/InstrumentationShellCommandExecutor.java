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
package android.app.time.cts.shell.device;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.io.ByteStreams;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

public final class InstrumentationShellCommandExecutor
        extends DeviceShellCommandExecutor {

    private static final String LOG_TAG = "ShellCommandExecutor";
    private final UiAutomation mUiAutomation;

    public InstrumentationShellCommandExecutor(@NonNull UiAutomation uiAutomation) {
        mUiAutomation = Objects.requireNonNull(uiAutomation);
    }

    @Override
    protected byte[] executeToBytesInternal(String command) throws Exception {
        ParcelFileDescriptor[] parcelFileDescriptors =
                mUiAutomation.executeShellCommandRwe(command);
        // Close stdin immediately.
        parcelFileDescriptors[1].close();

        byte[] stdOut = readBytesAndClose(parcelFileDescriptors[0]);
        byte[] stdErr = readBytesAndClose(parcelFileDescriptors[2]);
        if (stdErr.length > 0) {
            fail("Command \'" + command + "\' produced stderr: "
                    + parseBytesAsString(stdErr).trim());
        }
        return stdOut;
    }

    private static byte[] readBytesAndClose(ParcelFileDescriptor parcelFileDescriptor)
            throws IOException {
        try (FileInputStream output = new FileInputStream(
                parcelFileDescriptor.getFileDescriptor())) {
            return ByteStreams.toByteArray(output);
        }
    }

    @Override
    protected void log(String msg) {
        Log.v(LOG_TAG, msg);
    }
}
