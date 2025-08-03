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

package com.android.adservices.shell;

import android.annotation.Nullable;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.shell.AdServicesShellCommandHandler;
import com.android.adservices.service.shell.AdservicesShellCommandFactorySupplier;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Activity to run shell command for Android R/S using dumpsys.
 *
 * <p>It is disabled by default in manifest and is also flag guarded. It is enabled and started
 * using shell commands. It doesn't have a launcher icon. It's only alive for the duration of the
 * one command and once dump cmd is run, it is closed. When done with all the shell commands,
 * disable back the activity.
 */
public final class ShellCommandActivity extends Activity {
    private static final String TAG = "AdServicesShellCommand";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shell_command_activity);
    }

    @Override
    public void dump(
            String prefix,
            @Nullable FileDescriptor fd,
            PrintWriter writer,
            @Nullable String[] args) {
        if (args != null && args.length > 0 && args[0].equals("cmd")) {
            boolean enabled = FlagsFactory.getFlags().getAdServicesShellCommandEnabled();
            if (!enabled) {
                Log.e(
                        TAG,
                        String.format(
                                "dump(%s) called on AdServicesShellCommandService when shell"
                                        + " command flag was disabled",
                                Arrays.toString(args)));
                return;
            }
            // TODO(b/308009734): Move this logic and run shell command when we start the intent.
            //  Use dumpsys to get the result of the shell command.

            // need to strip the "cmd" arg
            String[] realArgs = new String[args.length - 1];
            System.arraycopy(args, 1, realArgs, 0, args.length - 1);
            Log.w(
                    TAG,
                    "Using dump to call AdServicesShellCommandHandler - should NOT happen on"
                            + " production");
            // TODO(b/308009734): Move running of shell command to a background thread.
            new AdServicesShellCommandHandler(writer, new AdservicesShellCommandFactorySupplier())
                    .run(realArgs);
            // TODO(b/308009734): Explicitly disable the activity after running the command.
            finish();
            return;
        }

        super.dump(prefix, fd, writer, args);
    }
}
