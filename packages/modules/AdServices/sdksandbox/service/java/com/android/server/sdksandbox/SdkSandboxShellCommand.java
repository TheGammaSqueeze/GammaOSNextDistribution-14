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

package com.android.server.sdksandbox;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.sdksandbox.ISdkSandboxService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SdkSandboxShellCommand extends BasicShellCommandHandler {

    @VisibleForTesting static final String ADSERVICES_CMD = "adservices-cmd";
    private static final String TAG = SdkSandboxShellCommand.class.getSimpleName();

    private final SdkSandboxManagerService mService;
    private final Context mContext;
    private final Injector mInjector;
    private final boolean mSupportsAdServicesShellCmd;

    private int mUserId = UserHandle.CURRENT.getIdentifier();
    private CallingInfo mCallingInfo;

    static class Injector {
        int getCallingUid() {
            return Binder.getCallingUid();
        }
    }

    @VisibleForTesting
    SdkSandboxShellCommand(
            SdkSandboxManagerService service,
            Context context,
            boolean supportsAdServicesShellCmd,
            Injector injector) {
        mService = service;
        mContext = context;
        mSupportsAdServicesShellCmd = supportsAdServicesShellCmd;
        mInjector = injector;
    }

    @VisibleForTesting
    SdkSandboxShellCommand(SdkSandboxManagerService service, Context context, Injector injector) {
        this(service, context, /* supportsAdServicesShellCmd= */ false, injector);
    }

    SdkSandboxShellCommand(
            SdkSandboxManagerService service, Context context, boolean supportsAdServicesShellCmd) {
        this(service, context, supportsAdServicesShellCmd, new Injector());
    }

    @Override
    public int onCommand(String cmd) {
        int callingUid = mInjector.getCallingUid();

        if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
            throw new SecurityException("sdk_sandbox shell command is only callable by ADB");
        }
        final long token = Binder.clearCallingIdentity();

        int result;
        try {
            if (cmd == null) {
                result = handleDefaultCommands(null);
            } else {
                switch (cmd) {
                    case "start":
                        result = runStart();
                        break;
                    case "stop":
                        result = runStop();
                        break;
                    case "set-state":
                        result = runSetState();
                        break;
                    case ADSERVICES_CMD:
                        result = runAdServicesShellCommand();
                        break;
                    default:
                        result = handleDefaultCommands(cmd);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return result;
    }

    /* Delegates the shell command and args to adservice manager, executes the shell
    command and returns the result back. */
    private int runAdServicesShellCommand() {
        int result = -1;
        if (!mSupportsAdServicesShellCmd) {
            getErrPrintWriter()
                    .println(
                            "AdServices shell command not supported through sdk_sandbox service."
                                    + " Trying calling it using adservices_manager service.");
            return result;
        }
        String[] args = getAllArgs();
        // strip "adservices-cmd" which is the first argument from the args .
        String[] realArgs = new String[args.length - 1];
        System.arraycopy(args, 1, realArgs, 0, args.length - 1);

        try (ParcelFileDescriptor pfdIn = ParcelFileDescriptor.dup(getInFileDescriptor());
                ParcelFileDescriptor pfdOut = ParcelFileDescriptor.dup(getOutFileDescriptor());
                ParcelFileDescriptor pfdErr = ParcelFileDescriptor.dup(getErrFileDescriptor())) {
            Binder adServicesBinder = (Binder) mService.getAdServicesManager();
            result = adServicesBinder.handleShellCommand(pfdIn, pfdOut, pfdErr, realArgs);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file descriptor for cmd: " + Arrays.toString(args), e);
        }
        return result;
    }

    // Suppress lint warning for context.getUser in R since this code is unused in R
    @SuppressWarnings("NewApi")
    private void handleSandboxArguments() {
        String opt;
        while ((opt = getNextOption()) != null) {
            if (opt.equals("--user")) {
                mUserId = parseUserArg(getNextArgRequired());
            } else {
                throw new IllegalArgumentException("Unknown option: " + opt);
            }
        }

        if (mUserId == UserHandle.CURRENT.getIdentifier()) {
            mUserId = mContext.getUser().getIdentifier();
        }

        String callingPackageName = getNextArgRequired();
        try {
            ApplicationInfo info = mContext.getPackageManager().getApplicationInfoAsUser(
                    callingPackageName, /* flags */ 0, UserHandle.of(mUserId));

            if ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                throw new IllegalArgumentException(
                        "Package " + callingPackageName + " must be debuggable.");
            }
            mCallingInfo = new CallingInfo(info.uid, callingPackageName);
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(
                    "No such package " + callingPackageName + " for user " + mUserId);
        }
    }

    // Suppress lint warning for context.getUser in R since this code is unused in R
    @SuppressWarnings("NewApi")
    private int parseUserArg(String arg) {
        switch (arg) {
            case "all":
                throw new IllegalArgumentException("Cannot run sdk_sandbox command for user 'all'");
            case "current":
                return mContext.getUser().getIdentifier();
            default:
                try {
                    return Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad user number: " + arg);
                }
        }
    }

    /** Callback for binding sandbox. Provides blocking interface {@link #isSuccessful()}. */
    private class LatchSandboxServiceConnectionCallback
            implements SdkSandboxManagerService.SandboxBindingCallback {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mSuccess = false;
        public static final int SANDBOX_BIND_TIMEOUT_S = 5;

        @Override
        public void onBindingSuccessful(
                ISdkSandboxService service, SandboxLatencyInfo sandboxLatencyInfo) {
            mSuccess = true;
            mLatch.countDown();
        }

        @Override
        public void onBindingFailed(LoadSdkException e, SandboxLatencyInfo sandboxLatencyInfo) {
            mLatch.countDown();
        }

        public boolean isSuccessful() {
            try {
                boolean completed = mLatch.await(SANDBOX_BIND_TIMEOUT_S, TimeUnit.SECONDS);
                if (!completed) {
                    getErrPrintWriter()
                            .println(
                                    "Error: Sdk sandbox failed to start in "
                                            + SANDBOX_BIND_TIMEOUT_S
                                            + " seconds");
                    return false;
                }
                if (!mSuccess) {
                    getErrPrintWriter().println("Error: Sdk sandbox failed to start");
                    return false;
                }
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private int runStart() {
        handleSandboxArguments();
        if (mService.isSdkSandboxServiceRunning(mCallingInfo)) {
            getErrPrintWriter().println("Error: Sdk sandbox already running for "
                    + mCallingInfo.getPackageName() + " and user " + mUserId);
            return -1;
        }

        LatchSandboxServiceConnectionCallback callback =
                new LatchSandboxServiceConnectionCallback();
        final SandboxLatencyInfo sandboxLatencyInfo = new SandboxLatencyInfo();

        mService.startSdkSandboxIfNeeded(mCallingInfo, callback, sandboxLatencyInfo);
        if (callback.isSuccessful()) {
            if (mService.isSdkSandboxDisabled()) {
                getErrPrintWriter().println("Error: SDK sandbox is disabled.");
                mService.stopSdkSandboxService(
                        mCallingInfo,
                        "Shell command `sdk_sandbox start` failed due to sandbox disabled.");
                return -1;
            }
            return 0;
        }
        getErrPrintWriter()
                .println("Error: Could not start SDK sandbox for " + mCallingInfo.getPackageName());
        return -1;
    }

    private int runStop() {
        handleSandboxArguments();
        if (!mService.isSdkSandboxServiceRunning(mCallingInfo)) {
            getErrPrintWriter().println("Sdk sandbox not running for "
                    + mCallingInfo.getPackageName() + " and user " + mUserId);
            return -1;
        }
        mService.stopSdkSandboxService(mCallingInfo, "Shell command 'sdk_sandbox stop' issued");
        return 0;
    }

    private int runSetState() {
        String opt;
        if ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--enabled":
                    mService.forceEnableSandbox();
                    break;
                case "--reset":
                    mService.clearSdkSandboxState();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + opt);
            }
        } else {
            throw new IllegalArgumentException("No argument supplied to `sdk_sandbox set-state`");
        }
        return 0;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("SDK sandbox (sdk_sandbox) commands: ");
        pw.println("    help: ");
        pw.println("        Prints this help text.");
        pw.println();
        pw.println("    start [--user <USER_ID> | current] <PACKAGE>");
        pw.println("        Start the SDK sandbox for the app <PACKAGE>. Options are:");
        pw.println("        --user <USER_ID> | current: Specify user for app; uses current user");
        pw.println("            if not specified");
        pw.println();
        pw.println("    stop [--user <USER_ID> | current] <PACKAGE>");
        pw.println("        Stop the SDK sandbox for the app <PACKAGE>. Options are:");
        pw.println("        --user <USER_ID> | current: Specify user for app; uses current user");
        pw.println("            if not specified");
        pw.println();
        pw.println("    set-state [--enabled | --reset]");
        pw.println("        Sets the SDK sandbox state for testing purposes. Options are:");
        pw.println("        --enabled: Sets the state to enabled");
        pw.println("        --reset: Resets the state. It will be calculated the next time an");
        pw.println("                 SDK is loaded");
    }
}
