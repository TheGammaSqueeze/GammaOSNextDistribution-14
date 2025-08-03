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

import android.annotation.NonNull;
import android.annotation.Nullable;


/**
 * Base factory to run the shell command.
 *
 * <p>Common shell command will be part of {@code CommonShellCommandFactory}.
 *
 * <p>Each API can extend this factory to implement API specific shell commands.
 */
interface ShellCommandFactory {
    /**
     * Returns the implemented {@link ShellCommand} object for a particular cmd.
     *
     * <p>If the shell command is not implemented, returns null.
     */
    @Nullable
    ShellCommand getShellCommand(String cmd);

    /** Returns the prefix of shell commands implemented for this factory. */
    @NonNull
    String getCommandPrefix();

    // TODO(b/308009734): Add help command as part of factory which shows help for commands
    //  implemented in a factory.
}
