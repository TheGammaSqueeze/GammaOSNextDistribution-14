/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.scenario;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

/** Unit tests for {@link AppSetup}. */
@RunWith(JUnit4.class)
public class AppSetupTest {
    private static final String DROP_CACHE_OPTION = "drop-cache-when-finished";
    private static final String KILL_APPS_OPTION = "apps-to-kill-when-finished";

    private AppSetup mAppSetup;
    private OptionSetter mOptionSetter;

    @Mock ITestDevice mTestDevice;
    @Mock ITestInvocationListener mListener;
    @Mock TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mAppSetup = spy(new AppSetup());
        doReturn(mTestDevice).when(mAppSetup).getDevice();
        doNothing().when(mAppSetup).runTest(any(),any());
        mOptionSetter = new OptionSetter(mAppSetup);
    }

    /** Test that the test drops cache when configured thusly. */
    @Test
    public void testDropCachesOption_set() throws Exception {
        mOptionSetter.setOptionValue(DROP_CACHE_OPTION, String.valueOf(true));
        mAppSetup.run(mTestInfo, mListener);
        verify(mTestDevice).executeShellCommand(eq(AppSetup.DROP_CACHE_COMMAND));
    }

    /** Test that the test does not drop cache when not configured to. */
    @Test
    public void testDropCachesOption_notSet() throws Exception {
        mAppSetup.run(mTestInfo, mListener);
        verify(mTestDevice, never()).executeShellCommand(eq(AppSetup.DROP_CACHE_COMMAND));
    }

    /** Test that the test kills the list of apps as supplied. */
    @Test
    public void testKillAppsOption() throws Exception {
        mOptionSetter.setOptionValue(KILL_APPS_OPTION, "app1");
        mOptionSetter.setOptionValue(KILL_APPS_OPTION, "app2");
        mAppSetup.run(mTestInfo, mListener);
        verify(mTestDevice, times(2))
                .executeShellCommand(
                        startsWith(String.format(AppSetup.KILL_APP_COMMAND_TEMPLATE, "")));
        verify(mTestDevice, times(1))
                .executeShellCommand(eq(String.format(AppSetup.KILL_APP_COMMAND_TEMPLATE, "app1")));
        verify(mTestDevice, times(1))
                .executeShellCommand(eq(String.format(AppSetup.KILL_APP_COMMAND_TEMPLATE, "app2")));
    }

    /** Test setting disable option exits from the run method early.*/
    @Test
    public void testDisableOption() throws Exception {
        mOptionSetter.setOptionValue("disable", String.valueOf(true));
        mAppSetup.run(mTestInfo, mListener);
        verify(mAppSetup, times(0)).runTest(any(), any());
    }

    /** Test that we run the default scenario package if not otherwise specified. */
    @Test
    public void testDefaultsToUsingScenariosPackage() throws Exception {
        doAnswer(
                        invocation -> {
                            assertEquals(
                                    mAppSetup.getPackageName(), AppSetup.DEFAULT_SCENARIOS_PACKAGE);
                            return null;
                        })
                .when(mAppSetup)
                .runTest(any(), any());
        mAppSetup.run(mTestInfo, mListener);
    }

    /** Test that an explicitly specified package name is not overridden. */
    @Test
    public void testSupportsExplicitPackageName() throws Exception {
        String packageName = "another." + AppSetup.DEFAULT_SCENARIOS_PACKAGE;
        mOptionSetter.setOptionValue("package", packageName);
        doAnswer(
                        invocation -> {
                            assertEquals(mAppSetup.getPackageName(), packageName);
                            return null;
                        })
                .when(mAppSetup)
                .runTest(any(), any());
        mAppSetup.run(mTestInfo, mListener);
    }
}
