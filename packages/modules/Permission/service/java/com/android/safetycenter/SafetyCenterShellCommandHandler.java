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

package com.android.safetycenter;

import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_REBOOT;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED;

import static java.util.Collections.unmodifiableMap;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.RemoteException;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;

import androidx.annotation.Nullable;

import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.build.SdkLevel;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link BasicShellCommandHandler} implementation to handle Safety Center commands.
 *
 * <p>Example usage: $ adb shell cmd safety_center refresh --reason PAGE_OPEN --user 10
 */
final class SafetyCenterShellCommandHandler extends BasicShellCommandHandler {

    private static final Map<String, Integer> REASONS = createReasonMap();

    private final Context mContext;
    private final ISafetyCenterManager mSafetyCenterManager;
    private final boolean mDeviceSupportsSafetyCenter;

    SafetyCenterShellCommandHandler(
            Context context,
            ISafetyCenterManager safetyCenterManager,
            boolean deviceSupportsSafetyCenter) {
        mContext = context;
        mSafetyCenterManager = safetyCenterManager;
        mDeviceSupportsSafetyCenter = deviceSupportsSafetyCenter;
    }

    @Override
    public int onCommand(@Nullable String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(null);
        }
        try {
            // Hey! Are you adding a new command to this switch? Then don't forget to add
            // instructions for it in the onHelp function below!
            switch (cmd) {
                case "enabled":
                    return onEnabled();
                case "supported":
                    return onSupported();
                case "refresh":
                    return onRefresh();
                case "clear-data":
                    return onClearData();
                case "package-name":
                    return onPackageName();
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (RemoteException | IllegalArgumentException e) {
            printError(e);
            return 1;
        }
    }

    // We want to log the stack trace on a specific PrintWriter here, this is a false positive as
    // the warning does not consider the overload that takes a PrintWriter as an argument (yet).
    @SuppressWarnings("CatchAndPrintStackTrace")
    private void printError(Throwable error) {
        error.printStackTrace(getErrPrintWriter());
    }

    private int onEnabled() throws RemoteException {
        getOutPrintWriter().println(mSafetyCenterManager.isSafetyCenterEnabled());
        return 0;
    }

    private int onSupported() {
        getOutPrintWriter().println(mDeviceSupportsSafetyCenter);
        return 0;
    }

    private int onRefresh() throws RemoteException {
        int reason = REFRESH_REASON_OTHER;
        int userId = 0;
        String opt = getNextOption();
        while (opt != null) {
            switch (opt) {
                case "--reason":
                    reason = parseReason();
                    break;
                case "--user":
                    userId = parseUserId();
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected option: " + opt);
            }
            opt = getNextOption();
        }
        getOutPrintWriter().println("Starting refresh…");
        mSafetyCenterManager.refreshSafetySources(reason, userId);
        return 0;
    }

    @RefreshReason
    private int parseReason() {
        String arg = getNextArgRequired();
        Integer reason = REASONS.get(arg);
        if (reason != null) {
            return reason;
        } else {
            throw new IllegalArgumentException("Invalid --reason arg: " + arg);
        }
    }

    @UserIdInt
    private int parseUserId() {
        String arg = getNextArgRequired();
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid --user arg: " + arg, e);
        }
    }

    private int onClearData() throws RemoteException {
        getOutPrintWriter().println("Clearing all data…");
        mSafetyCenterManager.clearAllSafetySourceDataForTests();
        return 0;
    }

    private int onPackageName() {
        getOutPrintWriter()
                .println(mContext.getPackageManager().getPermissionControllerPackageName());
        return 0;
    }

    @Override
    public void onHelp() {
        getOutPrintWriter().println("Safety Center (safety_center) commands:");
        printCmd("help or -h", "Print this help text");
        printCmd(
                "enabled",
                "Check if Safety Center is enabled",
                "Prints \"true\" if enabled, \"false\" otherwise");
        printCmd(
                "supported",
                "Check if this device supports Safety Center (i.e. Safety Center could be enabled)",
                "Prints \"true\" if supported, \"false\" otherwise");
        printCmd(
                "refresh [--reason REASON] [--user USERID]",
                "Start a refresh of all sources",
                "REASON is one of "
                        + String.join(", ", REASONS.keySet())
                        + "; determines whether sources fetch fresh data (default OTHER)",
                "USERID is a user ID; refresh sources in this user profile group (default 0)");
        printCmd(
                "clear-data",
                "Clear all data held by Safety Center",
                "Includes data held in memory and persistent storage but not the listeners.");
        printCmd("package-name", "Prints the name of the package that contains Safety Center");
    }

    /** Helper function to standardise pretty-printing of the help text. */
    private void printCmd(String cmd, String... description) {
        PrintWriter pw = getOutPrintWriter();
        pw.println("  " + cmd);
        for (int i = 0; i < description.length; i++) {
            pw.println("    " + description[i]);
        }
    }

    private static Map<String, Integer> createReasonMap() {
        // LinkedHashMap so that options get printed in order
        LinkedHashMap<String, Integer> reasons = new LinkedHashMap<>(6);
        reasons.put("PAGE_OPEN", REFRESH_REASON_PAGE_OPEN);
        reasons.put("BUTTON_CLICK", REFRESH_REASON_RESCAN_BUTTON_CLICK);
        reasons.put("REBOOT", REFRESH_REASON_DEVICE_REBOOT);
        reasons.put("LOCALE_CHANGE", REFRESH_REASON_DEVICE_LOCALE_CHANGE);
        reasons.put("SAFETY_CENTER_ENABLED", REFRESH_REASON_SAFETY_CENTER_ENABLED);
        reasons.put("OTHER", REFRESH_REASON_OTHER);
        if (SdkLevel.isAtLeastU()) {
            reasons.put("PERIODIC", REFRESH_REASON_PERIODIC);
        }
        return unmodifiableMap(reasons);
    }
}
