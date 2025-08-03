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

package com.android.server.adservices;

import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.adservices.shell.IShellCommand;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.ServiceBinder;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@code cmd adservices_manager}.
 *
 * <p>Currently, it only provides functionalities that can be provided locally by the AdServices
 * system server classes. But eventually it will require a connection with the AdServices process,
 * which in turn might require giving more permissions to Shell.
 */
// NOTE: not final because it's extended by unit test to set streams
class AdServicesShellCommand extends BasicShellCommandHandler {

    @VisibleForTesting
    static final String WRONG_UID_TEMPLATE =
            AD_SERVICES_SYSTEM_SERVICE + " shell cmd is only callable by ADB (called by %d)";

    @VisibleForTesting
    static final String CMD_IS_SYSTEM_SERVICE_ENABLED = "is-system-service-enabled";

    @VisibleForTesting static final String CMD_SHORT_HELP = "-h";
    @VisibleForTesting static final String CMD_HELP = "help";

    private static final String TIMEOUT_ARG = "--timeout";
    // Default timeout value to wait for the shell command output when we bind to the adservices
    // process.
    private static final int DEFAULT_TIMEOUT_MILLIS = 5_000;

    private final Injector mInjector;
    private final Flags mFlags;
    private final Context mContext;
    private int mTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    AdServicesShellCommand(Context context) {
        this(new Injector(), PhFlags.getInstance(), context);
    }

    @VisibleForTesting
    AdServicesShellCommand(Injector injector, Flags flags, Context context) {
        mInjector = Objects.requireNonNull(injector);
        mFlags = Objects.requireNonNull(flags);
        mContext = Objects.requireNonNull(context);
    }

    @Override
    public int onCommand(String cmd) {
        int callingUid = mInjector.getCallingUid();
        if (callingUid != Process.ROOT_UID
                && callingUid != Process.SHELL_UID
                && callingUid != Process.SYSTEM_UID) {
            throw new SecurityException(String.format(WRONG_UID_TEMPLATE, callingUid));
        }
        if (cmd == null || cmd.isEmpty() || cmd.equals(CMD_SHORT_HELP) || cmd.equals(CMD_HELP)) {
            onHelp();
            runAdServicesShellCommand(mContext, new String[] {CMD_HELP});
            return 0;
        }
        switch (cmd) {
                // Below commands are handled by the System Server
            case CMD_IS_SYSTEM_SERVICE_ENABLED:
                return runIsSystemServiceEnabled();

                // If there is no explicit case is there, we assume we want to run the shell command
                // in the adservices process.
            default:
                return runAdServicesShellCommand(mContext, getAllArgs());
        }
    }

    private int runAdServicesShellCommand(Context context, String[] args) {
        // Shell always runs on System User (User 0), if secondary user (Eg. User 10) is running
        // then change the context to the secondary user.
        Context curUserContext = getContextForUser(context, ActivityManager.getCurrentUser());
        IShellCommand service = mInjector.getShellCommandService(curUserContext);
        if (service == null) {
            getErrPrintWriter().println("Failed to connect to shell command service");
            return -1;
        }
        String[] realArgs = handleAdServicesArgs(args);
        ShellCommandParam param = new ShellCommandParam(realArgs);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger resultCode = new AtomicInteger(-1);
        try {
            service.runShellCommand(
                    param,
                    new IShellCommandCallback.Stub() {
                        @Override
                        public void onResult(ShellCommandResult response) {
                            if (response.isSuccess()) {
                                getOutPrintWriter().println(response.getOut());
                                resultCode.set(response.getResultCode());
                            } else {
                                getErrPrintWriter().println(response.getErr());
                            }
                            latch.countDown();
                        }
                    });
        } catch (RemoteException e) {
            getErrPrintWriter()
                    .printf(
                            "Remote exception occurred while executing %s\n",
                            Arrays.toString(realArgs));

            latch.countDown();
        }

        try {
            if (!latch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)) {
                getErrPrintWriter()
                        .printf(
                                "Elapsed time: %d Millisecond. Timeout occurred , failed to "
                                        + "complete shell command\n",
                                mTimeoutMillis);
                return -1;
            }
        } catch (InterruptedException e) {
            getErrPrintWriter().println("Thread interrupted, failed to complete shell command");
            Thread.currentThread().interrupt();
            return -1;
        }
        return resultCode.get();
    }

    private String[] handleAdServicesArgs(String[] args) {
        // Contains all the args except --user, --timeout arg and its value.
        List<String> realArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case TIMEOUT_ARG:
                    mTimeoutMillis = parseTimeoutArg(args, ++i);
                    break;
                    // TODO(b/308009734): Check for --user args in the follow-up cl and change
                    //  context and bind to the service accordingly.
                default:
                    realArgs.add(arg);
            }
        }
        return realArgs.toArray(String[]::new);
    }

    private int parseTimeoutArg(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument expected after " + args[index - 1]);
        }

        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad timeout value: " + args[index]);
        }
    }

    private int runIsSystemServiceEnabled() {
        PrintWriter pw = getOutPrintWriter();
        boolean verbose = false;

        String opt;
        if ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-v":
                case "--verbose":
                    verbose = true;
                    break;
                default:
                    PrintWriter errPw = getErrPrintWriter();
                    errPw.printf("Invalid option: %s\n\n", opt);
                    errPw.print("Syntax: ");
                    showIsSystemServerEnabledHelpCommand(errPw);
                    return -1;
            }
        }

        boolean enabled = mFlags.getAdServicesSystemServiceEnabled();

        if (!verbose) {
            // NOTE: must always print just the boolean, as it might be used by tests.
            pw.println(enabled);
            return 0;
        }

        // Here it's ok to print whatever we want...
        pw.printf(
                "Enabled: %b Default value: %b DeviceConfig key: %s\n",
                enabled,
                PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED,
                PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED);
        return 0;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("AdServices (adservices_manager) commands: ");
        showValidCommands(pw);
    }

    private static void showIsSystemServerEnabledHelpCommand(PrintWriter pw) {
        pw.println("is-system-service-enabled [-v || --verbose]");
        pw.println(
                "    Returns a boolean indicating whether the AdServices System Service is"
                        + "enabled.");
        pw.println("    Use [-v || --verbose] to also show the default value");
        pw.println();
    }

    private static void showHelpCommand(PrintWriter pw) {
        pw.println("help: ");
        pw.println("    Prints this help text.");
        pw.println();
    }

    private static void showValidCommands(PrintWriter pw) {
        showHelpCommand(pw);
        showIsSystemServerEnabledHelpCommand(pw);
    }

    private Context getContextForUser(Context context, int userId) {
        if (userId == context.getUser().getIdentifier()) {
            return context;
        }
        return context.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
    }

    // Needed because Binder.getCallingUid() is native and cannot be mocked
    @VisibleForTesting
    static class Injector {
        int getCallingUid() {
            return Binder.getCallingUid();
        }

        IShellCommand getShellCommandService(Context context) {
            ServiceBinder<IShellCommand> serviceBinder =
                    ServiceBinder.getServiceBinder(
                            context,
                            AdServicesCommon.ACTION_SHELL_COMMAND_SERVICE,
                            IShellCommand.Stub::asInterface);
            return serviceBinder.getService();
        }
    }
}
