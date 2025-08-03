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
package com.android.cts.multiuser.dpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;

final class DpcShellCommand {

    private static final String TAG = DpcShellCommand.class.getSimpleName();

    private final DevicePolicyManager mDpm;
    private final ComponentName mAdmin;
    private final PrintWriter mWriter;
    private final String[] mArgs;

    DpcShellCommand(Context context, PrintWriter writer, String[] args) {
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mAdmin = new ComponentName(context, DpcReceiver.class.getName());
        Log.d(TAG, "Created for user " + Process.myUserHandle() + " and component " + mAdmin
                + " for command " + Arrays.toString(args));
        mWriter = writer;
        mArgs = args;
    }

    void run() {
        if (mArgs.length == 0) {
            mWriter.println("Missing cmd");
            return;
        }
        String cmd = mArgs[0];
        try {
            switch (cmd) {
                case "add-user-restriction":
                    runAddUserRestriction();
                    break;
                case "clear-user-restriction":
                    runClearUserRestriction();
                    break;
                default:
                    mWriter.println("Invalid command: " + cmd);
                    return;
            }
        } catch (Exception e) {
            mWriter.println("Failed to execute " + Arrays.toString(mArgs) + ": " + e);
            return;
        }
        // Must have that string on success as CTS checks it
        mWriter.println("Command succeeded!");
    }

    private void runAddUserRestriction() {
        String restriction = mArgs[1];
        Log.i(TAG, "Calling addUserRestriction(" + restriction + ")");
        mDpm.addUserRestriction(mAdmin, restriction);
    }

    private void runClearUserRestriction() {
        String restriction = mArgs[1];
        Log.i(TAG, "Calling clearUserRestriction(" + restriction + ")");
        mDpm.clearUserRestriction(mAdmin, restriction);
    }
}
