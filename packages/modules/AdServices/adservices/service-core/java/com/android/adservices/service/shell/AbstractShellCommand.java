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

import java.io.PrintWriter;
import java.util.Arrays;

abstract class AbstractShellCommand implements ShellCommand {

    static final int RESULT_GENERIC_ERROR = -1;
    static final int RESULT_OK = 0;

    static final String ERROR_TEMPLATE_INVALID_ARGS = "Invalid cmd (%s).\n\nSyntax: %s\n";

    static int invalidArgsError(String syntax, PrintWriter err, String[] args) {
        err.printf(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax);
        return RESULT_GENERIC_ERROR;
    }
}
