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

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class CustomAudienceShellCommandFactoryTest extends AdServicesMockitoTestCase {
    private static final boolean CUSTOM_AUDIENCE_CLI_ENABLED = true;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Mock private BackgroundFetchRunner mBackgroundFetchRunner;
    private ShellCommandFactory mFactory;

    @Before
    public void setup() {
        mFactory =
                new CustomAudienceShellCommandFactory(
                        CUSTOM_AUDIENCE_CLI_ENABLED, mBackgroundFetchRunner, mCustomAudienceDao);
    }

    @Test
    public void test_viewCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(CustomAudienceViewCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(CustomAudienceViewCommand.class);
    }

    @Test
    public void test_listCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(CustomAudienceListCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(CustomAudienceListCommand.class);
    }

    @Test
    public void test_refreshCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(CustomAudienceRefreshCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(CustomAudienceRefreshCommand.class);
    }

    @Test
    public void test_invalidCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        Truth.assertThat(shellCommand).isNull();
    }

    @Test
    public void test_cliDisabled() {
        mFactory =
                new CustomAudienceShellCommandFactory(
                        false, mBackgroundFetchRunner, mCustomAudienceDao);
        ShellCommand shellCommand = mFactory.getShellCommand(CustomAudienceListCommand.CMD);
        Truth.assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void test_invalidCmdCLIDisabled() {
        mFactory =
                new CustomAudienceShellCommandFactory(
                        false, mBackgroundFetchRunner, mCustomAudienceDao);
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        Truth.assertThat(shellCommand).isNull();
    }
}
