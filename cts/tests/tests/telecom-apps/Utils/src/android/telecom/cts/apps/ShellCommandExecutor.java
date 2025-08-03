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

package android.telecom.cts.apps;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * This class should be used in Telecom CTS test classes to statically execute shell commands.
 */
public class ShellCommandExecutor {
    private static final String sTAG = ShellCommandExecutor.class.getSimpleName();
    public static final String COMMAND_RESET_CAR = "telecom cleanup";
    public static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";
    public static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    public static final String COMMAND_ENABLE = "telecom set-phone-account-enabled ";
    public static final String COMMAND_CLEANUP_STUCK_CALLS = "telecom cleanup-stuck-calls";

    private static final String COMMAND_SET_DEFAULT_PHONE_ACCOUNT =
            "telecom set-user-selected-outgoing-phone-account ";

    /**
     * Executes the given shell command and returns the output in a string. Note that even
     * if we don't care about the output, we have to read the stream completely to make the
     * command execute.
     */
    public static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                closeQuietly(br);
            }
            closeQuietly(pfd);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
                Log.w(sTAG, "closeQuietly: exception thrown: e=" + ignored);
            }
        }
    }

    public static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    public static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    public static void enablePhoneAccount(Instrumentation instrumentation,
            PhoneAccountHandle handle) throws Exception {
        final ComponentName component = handle.getComponentName();
        final long userSerial = getUserSerialNumber(instrumentation, handle.getUserHandle());
        executeShellCommand(instrumentation, COMMAND_ENABLE
                + component.getPackageName() + "/" + component.getClassName() + " "
                + handle.getId() + " " + userSerial);
    }

    public static void setUserDefaultPhoneAccount(Instrumentation instrumentation,
            PhoneAccountHandle handle) throws Exception {
        String result;
        if (handle != null) {
            final ComponentName component = handle.getComponentName();
            final long userSerial = getUserSerialNumber(instrumentation, handle.getUserHandle());
            result = executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_PHONE_ACCOUNT
                    + component.getPackageName() + "/" + component.getClassName() + " "
                    + handle.getId() + " " + userSerial);
        } else {
            result = executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_PHONE_ACCOUNT);
        }
        Log.d(sTAG, "setUserDefaultPhoneAccount to " + handle + ", result=" + result);
    }

    private static long getUserSerialNumber(Instrumentation instrumentation, UserHandle handle) {
        UserManager userManager =
                instrumentation.getContext().getSystemService(UserManager.class);
        return userManager.getSerialNumberForUser(handle);
    }
}
