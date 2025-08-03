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

package com.android.adservices.common;

import static android.os.Build.VERSION.SDK_INT;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FileUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.io.FileInputStream;
import java.io.IOException;

/** Device-side implementation of {@link AbstractAdServicesShellCommandHelper}. */
public final class AdServicesShellCommandHelper extends AbstractAdServicesShellCommandHelper {
    public AdServicesShellCommandHelper() {
        super(AndroidLogger.getInstance());
    }

    private static final int OUT_DESCRIPTOR_INDEX = 0;
    private static final int IN_DESCRIPTOR_INDEX = 1;
    private static final int ERR_DESCRIPTOR_INDEX = 2;

    @Override
    protected String runShellCommand(String cmd) {
        return SystemUtil.runShellCommand(cmd).strip();
    }

    @Override
    protected CommandResult runShellCommandRwe(String cmd) {
        String out = "";
        String err = "";
        try {
            ParcelFileDescriptor[] fds = executeShellCommandRwe(cmd);
            ParcelFileDescriptor fdOut = fds[OUT_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdIn = fds[IN_DESCRIPTOR_INDEX];
            ParcelFileDescriptor fdErr = fds[ERR_DESCRIPTOR_INDEX];

            if (fdIn != null) {
                try {
                    // not using stdin
                    fdIn.close();
                } catch (Exception e) {
                    // Ignore
                }
            }

            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdOut)) {
                out = new String(FileUtils.readInputStreamFully(fis)).strip();
            }
            try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(fdErr)) {
                err = new String(FileUtils.readInputStreamFully(fis)).strip();
            }
            return new CommandResult(out, err);
        } catch (IOException e) {
            Log.e(TAG, "Exception occurred while reading file descriptor: ", e);
            return new CommandResult(out, e.getMessage());
        }
    }

    @Override
    protected int getDeviceApiLevel() {
        return SDK_INT;
    }

    private static ParcelFileDescriptor[] executeShellCommandRwe(String cmd) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        return uiAutomation.executeShellCommandRwe(cmd);
    }
}
