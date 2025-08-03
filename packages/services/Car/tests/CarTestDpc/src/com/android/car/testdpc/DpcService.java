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
package com.android.car.testdpc;

import android.app.admin.DeviceAdminService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Set;

/**
 * Service that handles calls to Device Policy Manager from shell.
 *
 * <p>This service handles binding of remote device policy managers to the current user
 * by checking if current user is device or profile owner. If it is either then it accordingly
 * binds and assigns all affiliated users to the appropriate {@code DevicePolicyManagerInterface}
 * implementation ({@code RemoteDevicePolicyManager}/{@code LocalDevicePolicyManager})
 */

public final class DpcService extends DeviceAdminService {

    private static final String TAG = DpcService.class.getSimpleName();
    private static final Set<String> AFFILIATION_IDS = Set.of("42");

    private ComponentName mAdmin;
    private Context mContext;
    private DpcFactory mDpcFactory;
    private DevicePolicyManager mDpm;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Service created (on user " + Process.myUserHandle() + ")");

        mContext = getApplicationContext();
        mAdmin = DpcReceiver.getComponentName(mContext);
        mDpm = mContext.getSystemService(DevicePolicyManager.class);

        mDpm.setAffiliationIds(mAdmin, AFFILIATION_IDS);
        Log.i(TAG, "setAffiliationIds(" + mAdmin.flattenToShortString() + ", "
                + AFFILIATION_IDS + ")");

        mDpcFactory = new DpcFactory(mContext);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.d(TAG, "dump(): "  + Arrays.toString(args));
        printInternalState(writer);
        if (args != null && args.length > 0) {
            switch (args[0]) {
                case "cmd":
                    String[] cmdArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);
                    new DpcShellCommand(this, mDpcFactory, writer, cmdArgs).run();
                    return;
                default:
                    break;
            }
        }
    }

    private void printInternalState(PrintWriter writer) {
        writer.printf("Current User: %s\n", Process.myUserHandle());
        writer.printf("Admin Component Name: %s\n", mAdmin.flattenToShortString());
        writer.printf("isProfileOwner()? %b\n", mDpm.isProfileOwnerApp(mContext.getPackageName()));
        writer.printf("isDeviceOwner()? %b\n", mDpm.isDeviceOwnerApp(mContext.getPackageName()));
        writer.printf("AFFILIATION_IDS=%s\n\n", AFFILIATION_IDS);
    }
}
