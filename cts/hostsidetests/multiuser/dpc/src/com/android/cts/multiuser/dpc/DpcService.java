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

import android.app.admin.DeviceAdminService;
import android.os.Process;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

public final class DpcService extends DeviceAdminService {

    private static final String TAG = DpcService.class.getSimpleName();

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created (on user " + Process.myUserHandle() + ")");

        super.onCreate();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.d(TAG, "dump(): "  + Arrays.toString(args));
        if (args != null && args.length > 0) {
            switch (args[0]) {
                case "ping":
                    // Used by CTS to wait until it's ready
                    writer.println("pong");
                    return;
                case "cmd":
                    String[] cmdArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);
                    new DpcShellCommand(this, writer, cmdArgs).run();
                    return;
            }
        }
        super.dump(fd, writer, args);
    }
}
