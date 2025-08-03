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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;

import android.text.TextUtils;
import android.util.Log;

import java.io.PrintWriter;

/**
 * This class implements echo shell command.
 *
 * <p>It just prints the given message.
 */
final class EchoCommand extends AbstractShellCommand {
    // This command is also used by the CTS and it should never change.
    static final String CMD_ECHO = "echo";
    static final String HELP_ECHO =
            CMD_ECHO + " <message>\n    Prints the given message (useful to check cmd is working).";

    @Override
    public int run(PrintWriter out, PrintWriter err, String[] args) {
        if (args.length != 2) {
            return invalidArgsError(HELP_ECHO, err, args);
        }
        String message = args[1];
        if (TextUtils.isEmpty(message)) {
            return invalidArgsError(HELP_ECHO, err, args);
        }

        Log.i(TAG, CMD_ECHO + " message='" + message + "'");
        out.println(message);
        return RESULT_OK;
    }

    @Override
    public String getCommandName() {
        return CMD_ECHO;
    }
}
