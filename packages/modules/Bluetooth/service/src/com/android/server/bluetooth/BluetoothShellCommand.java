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

package com.android.server.bluetooth;

import static java.util.Objects.requireNonNull;

import android.bluetooth.BluetoothAdapter;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.PrintWriter;

class BluetoothShellCommand extends BasicShellCommandHandler {
    private static final String TAG = BluetoothShellCommand.class.getSimpleName();

    private final BluetoothManagerService mManagerService;
    private final Context mContext;

    @VisibleForTesting
    final BluetoothCommand[] mBluetoothCommands = {
        new Enable(),
        new Disable(),
        new WaitForAdapterState(),
    };

    @VisibleForTesting
    abstract static class BluetoothCommand {
        final boolean mIsPrivileged;
        final String mName;

        BluetoothCommand(boolean isPrivileged, String name) {
            mIsPrivileged = isPrivileged;
            mName = requireNonNull(name, "Command name cannot be null");
        }

        String getName() {
            return mName;
        }
        boolean isMatch(String cmd) {
            return mName.equals(cmd);
        }
        boolean isPrivileged() {
            return mIsPrivileged;
        }

        abstract int exec(String cmd) throws RemoteException;
        abstract void onHelp(PrintWriter pw);
    }

    @VisibleForTesting
    class Enable extends BluetoothCommand {
        Enable() {
            super(false, "enable");
        }
        @Override
        public int exec(String cmd) throws RemoteException {
            return mManagerService.getBinder().enable(AttributionSource.myAttributionSource())
                    ? 0
                    : -1;
        }
        @Override
        public void onHelp(PrintWriter pw) {
            pw.println("  " + getName());
            pw.println("    Enable Bluetooth on this device.");
        }
    }

    @VisibleForTesting
    class Disable extends BluetoothCommand {
        Disable() {
            super(false, "disable");
        }
        @Override
        public int exec(String cmd) throws RemoteException {
            return mManagerService
                            .getBinder()
                            .disable(AttributionSource.myAttributionSource(), true)
                    ? 0
                    : -1;
        }
        @Override
        public void onHelp(PrintWriter pw) {
            pw.println("  " + getName());
            pw.println("    Disable Bluetooth on this device.");
        }
    }

    @VisibleForTesting
    class WaitForAdapterState extends BluetoothCommand {
        WaitForAdapterState() {
            super(false, "wait-for-state");
        }
        private int getWaitingState(String in) {
            if (!in.startsWith(getName() + ":")) return -1;
            String[] split = in.split(":", 2);
            if (split.length != 2 || !getName().equals(split[0])) {
                String msg = getName() + ": Invalid state format: " + in;
                Log.e(TAG, msg);
                PrintWriter pw = getErrPrintWriter();
                pw.println(TAG + ": " + msg);
                printHelp(pw);
                throw new IllegalArgumentException();
            }
            switch (split[1]) {
                case "STATE_OFF":
                    return BluetoothAdapter.STATE_OFF;
                case "STATE_ON":
                    return BluetoothAdapter.STATE_ON;
                default:
                    String msg = getName() + ": Invalid state value: " + split[1] + ". From: " + in;
                    Log.e(TAG, msg);
                    PrintWriter pw = getErrPrintWriter();
                    pw.println(TAG + ": " + msg);
                    printHelp(pw);
                    throw new IllegalArgumentException();
            }
        }
        @Override
        boolean isMatch(String cmd) {
            return getWaitingState(cmd) != -1;
        }
        @Override
        public int exec(String cmd) throws RemoteException {
            int ret = mManagerService.waitForManagerState(getWaitingState(cmd)) ? 0 : -1;
            Log.d(TAG, cmd + ": Return value is " + ret); // logging as this method can take time
            return ret;
        }
        @Override
        public void onHelp(PrintWriter pw) {
            pw.println("  " + getName() + ":<STATE>");
            pw.println("    Wait until the adapter state is <STATE>."
                    + " <STATE> can be one of STATE_OFF | STATE_ON");
            pw.println("    Note: This command can timeout and failed");
        }
    }

    BluetoothShellCommand(BluetoothManagerService managerService, Context context) {
        mManagerService = managerService;
        mContext = context;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) return handleDefaultCommands(null);

        for (BluetoothCommand bt_cmd : mBluetoothCommands) {
            if (!bt_cmd.isMatch(cmd)) continue;
            if (bt_cmd.isPrivileged()) {
                final int uid = Binder.getCallingUid();
                if (uid != Process.ROOT_UID) {
                    throw new SecurityException("Uid " + uid + " does not have access to "
                            + cmd + " bluetooth command");
                }
            }
            try {
                getOutPrintWriter().println(TAG + ": Exec " + cmd);
                Log.d(TAG, "Exec " + cmd);
                int ret = bt_cmd.exec(cmd);
                if (ret == 0) {
                    String msg = cmd + ": Success";
                    Log.d(TAG, msg);
                    getOutPrintWriter().println(msg);
                } else {
                    String msg = cmd + ": Failed with status=" + ret;
                    Log.e(TAG, msg);
                    getErrPrintWriter().println(TAG + ": " + msg);
                }
                return ret;
            } catch (RemoteException e) {
                Log.w(TAG, cmd + ": error\nException: " + e.getMessage());
                getErrPrintWriter().println(cmd + ": error\nException: " + e.getMessage());
                e.rethrowFromSystemServer();
            }
        }
        return handleDefaultCommands(cmd);
    }

    private void printHelp(PrintWriter pw) {
        pw.println("Bluetooth Manager Commands:");
        pw.println("  help or -h");
        pw.println("    Print this help text.");
        for (BluetoothCommand bt_cmd : mBluetoothCommands) {
            bt_cmd.onHelp(pw);
        }
    }
    @Override
    public void onHelp() {
        printHelp(getOutPrintWriter());
    }
}
