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

package com.android.server.scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.DeviceConfig;
import android.scheduling.RebootReadinessManager;

import com.android.modules.utils.BasicShellCommandHandler;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Interprets and executes "adb shell cmd reboot_readiness [args]".
 */
class RebootReadinessShellCommand extends BasicShellCommandHandler {

    final RebootReadinessManagerService mService;
    final Context mContext;

    // How long to perform reboot readiness checks for.
    private long mTimeoutSecs = TimeUnit.MINUTES.toSeconds(5);

    // When true, blocking app uids or subsystem identifiers may be listed.
    private boolean mListBlocking;

    // DeviceConfig properties
    private static final String PROPERTY_ACTIVE_POLLING_INTERVAL_MS = "active_polling_interval_ms";
    private static final String PROPERTY_INTERACTIVITY_THRESHOLD_MS = "interactivity_threshold_ms";
    private static final String PROPERTY_DISABLE_INTERACTIVITY_CHECK =
            "disable_interactivity_check";
    private static final String PROPERTY_DISABLE_APP_ACTIVITY_CHECK = "disable_app_activity_check";
    private static final String PROPERTY_DISABLE_SUBSYSTEMS_CHECK = "disable_subsystems_check";

    RebootReadinessShellCommand(RebootReadinessManagerService service, Context context) {
        mService = service;
        mContext = context;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }

        switch (cmd) {
            case "check-interactivity-state":
                runCheckInteractivityState();
                break;
            case "check-subsystems-state":
                runCheckSubsystemsState();
                break;
            case "check-app-activity-state":
                runCheckAppActivityState();
                break;
            case "start-readiness-checks":
                runStartReadinessChecks();
                break;
            default:
                return handleDefaultCommands(cmd);
        }
        return 1;
    }

    private void handleOptions()  {
        String arg;
        while ((arg = getNextArg()) != null) {
            switch (arg) {
                case "--timeout-secs":
                    mTimeoutSecs = Long.parseLong(getNextArgRequired());
                    break;
                case "--list-blocking":
                    mListBlocking = true;
                    break;
                case "--polling-interval-ms":
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                            PROPERTY_ACTIVE_POLLING_INTERVAL_MS, getNextArgRequired(), false);
                    break;
                case "--interactivity-threshold-ms":
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                            PROPERTY_INTERACTIVITY_THRESHOLD_MS, getNextArgRequired(), false);
                    break;
                case "--disable-app-activity-check":
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                            PROPERTY_DISABLE_APP_ACTIVITY_CHECK, "true", false);
                    break;
                case "--disable-subsystems-check":
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                            PROPERTY_DISABLE_SUBSYSTEMS_CHECK, "true", false);
                    break;
                case "disable-interactivity-check":
                case "--disable-interactivity-check":
                    DeviceConfig.setProperty(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                            PROPERTY_DISABLE_INTERACTIVITY_CHECK, "true", false);
                    break;
                default:
                    break;
            }
        }
        // Allow DeviceConfig values to propagate.
        try {
            Thread.sleep(1000);
        } catch (Exception ignored) {
        }
    }

    /**
     * Registers for reboot readiness state change broadcasts for a certain amount of time. If
     * the state changes, it will be printed along with a timestamp.
     */
    private void runStartReadinessChecks() {
        handleOptions();
        IntentFilter filter = new IntentFilter(RebootReadinessManager.ACTION_REBOOT_READY);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalDateTime dt = LocalDateTime.now();
                getOutPrintWriter().println("State changed to " + intent.getBooleanExtra(
                        RebootReadinessManager.EXTRA_IS_READY_TO_REBOOT, false)
                        + " at time: " + dt.format(DateTimeFormatter.ISO_LOCAL_TIME));
                getOutPrintWriter().flush();
            }
        };
        try {
            mContext.registerReceiver(receiver, filter);
            getOutPrintWriter().println("Initial state: " + mService.isReadyToReboot());
            getOutPrintWriter().flush();
            mService.markRebootPending(mContext.getPackageName());
            while (mTimeoutSecs-- > 0) {
                Thread.sleep(1000);
            }
        } catch (Exception ignored) {
        } finally {
            mService.cancelPendingReboot(mContext.getPackageName());
        }
    }

    /**
     * Checks the device interactivity state. Prints false if the reboot is blocked by device
     * interactivity, true otherwise.
     */
    private void runCheckInteractivityState() {
        handleOptions();
        getOutPrintWriter().println("Interactivity state: " + mService.checkDeviceInteractivity());
    }

    /**
     * Checks the subsystem reboot readiness. Prints false if the reboot is blocked by any
     * subsystems, true otherwise. If --list-blocking is passed, the culprit subsystems
     * will be printed.
     */
    private void runCheckSubsystemsState() {
        handleOptions();
        getOutPrintWriter().println("Subsystem state: " + mService.checkSystemComponentsState());
        if (mListBlocking) {
            mService.writeBlockingSubsystems(getOutPrintWriter());
        }
    }

    /**
     * Checks the app activity reboot readiness. Prints false if the reboot is blocked by any
     * app uids, true otherwise. If --list-blocking is passed, the culprit packages will be printed.
     */
    private void runCheckAppActivityState() {
        handleOptions();
        getOutPrintWriter().println("App activity state: " + mService.checkBackgroundAppActivity());
        if (mListBlocking) {
            mService.writeBlockingUids(getOutPrintWriter());
        }
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Reboot readiness (reboot_readiness) commands: ");
        pw.println("    help: ");
        pw.println("        Prints this help text.");
        pw.println("    check-interactivity-state:");
        pw.println("        Checks interactivity state.");
        pw.println("    check-app-activity-state [--list-blocking]:");
        pw.println("        Checks background app activity state. If --list-blocking is passed, a");
        pw.println("        list of blocking uids will be printed if any exist.");
        pw.println("    check-subsystems-state [--list-blocking]:");
        pw.println("        Checks subsystems state. If --list-blocking is passed, a list of");
        pw.println("        blocking subsystems will be printed if any exist.");
        pw.println("    start-readiness-checks [--timeout-secs <TIMEOUT-SECS>]:");
        pw.println("        Performs reboot readiness checks for either 5 minutes, or the");
        pw.println("        number of seconds declared by TIMEOUT-SECS. Prints the new reboot");
        pw.println("        readiness state along with a timestamp whenever the state changes.");
        pw.println();
        pw.println("Additional flags that may be passed:");
        pw.println("    --polling-interval-ms <POLLING-INTERVAL-MS>:");
        pw.println("        How frequently the reboot readiness state is polled, in milliseconds.");
        pw.println("    --interactivity-threshold-ms <INTERACTIVITY-THRESHOLD-MS>:");
        pw.println("        How long the device must not have been interacted with before");
        pw.println("        being deemed ready to reboot.");
        pw.println("    --disable-interactivity-check / disable-interactivity-check:");
        pw.println("        Disable interactivity checks.");
        pw.println("    --disable-subsystems-check:");
        pw.println("        Disable subsystems checks:");
        pw.println("    --disable-app-activity-check:");
        pw.println("        Disable app activity checks.");
    }
}
