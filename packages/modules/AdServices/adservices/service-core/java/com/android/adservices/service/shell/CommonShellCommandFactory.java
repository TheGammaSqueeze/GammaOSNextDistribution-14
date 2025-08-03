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

import static com.android.adservices.service.shell.EchoCommand.CMD_ECHO;

import androidx.annotation.Nullable;


/**
 * Factory class which handles common shell commands. API specific shell commands should be part of
 * API specific factory.
 */
final class CommonShellCommandFactory implements ShellCommandFactory {
    static ShellCommandFactory getInstance() {
        return new CommonShellCommandFactory();
    }

    @Nullable
    @Override
    public ShellCommand getShellCommand(String cmd) {
        switch (cmd) {
            case CMD_ECHO:
                return new EchoCommand();
            default:
                return null;
        }
    }

    @Override
    public String getCommandPrefix() {
        return "";
    }
}
