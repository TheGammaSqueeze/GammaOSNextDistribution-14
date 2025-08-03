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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class CommonShellCommandFactoryTest extends AdServicesUnitTestCase {
    private final ShellCommandFactory mFactory = new CommonShellCommandFactory();

    @Test
    public void testGetShellCommand() {
        expect.withMessage(CMD_ECHO)
                .that(mFactory.getShellCommand(CMD_ECHO))
                .isInstanceOf(EchoCommand.class);
    }

    @Test
    public void testGetShellCommand_invalidCommand() {
        String cmd = "abc";

        expect.withMessage(cmd).that(mFactory.getShellCommand(cmd)).isNull();
    }
}
