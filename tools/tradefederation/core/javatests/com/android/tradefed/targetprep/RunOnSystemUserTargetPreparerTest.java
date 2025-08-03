/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.UserHelper.RUN_TESTS_AS_USER_KEY;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RunOnSystemUserTargetPreparerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    private RunOnSystemUserTargetPreparer mPreparer;

    @Before
    public void setUp() throws Exception {
        mPreparer = new RunOnSystemUserTargetPreparer();
        when(mTestInfo.getDevice().isHeadlessSystemUserMode()).thenReturn(false);
        when(mTestInfo.getDevice().switchUser(anyInt())).thenReturn(true);
    }

    @Test
    public void setUp_alreadySwitchedToSystemUser_doesNotSwitchUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).switchUser(anyInt());
    }

    @Test
    public void setUp_alreadySwitchedToSystemUser_runsOnSystemUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "0");
    }

    @Test
    public void setUp_switchedToDifferentUser_switchesToSystemUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(10);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice()).switchUser(0);
    }

    @Test
    public void setUp_switchedToDifferentUser_runsOnSystemUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(10);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "0");
    }

    @Test
    public void setup_headlessSystemUser_doesNotSwitchUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(10);
        when(mTestInfo.getDevice().isHeadlessSystemUserMode()).thenReturn(true);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.getDevice(), never()).switchUser(anyInt());
    }

    @Test
    public void setup_headlessSystemUser_runsOnSystemUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(10);
        when(mTestInfo.getDevice().isHeadlessSystemUserMode()).thenReturn(true);

        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_AS_USER_KEY, "0");
    }

    @Test
    public void teardown_didNotSwitchUserInSetup_doesNotSwitchUser() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(0);
        mPreparer.setUp(mTestInfo);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice(), never()).switchUser(anyInt());
    }

    @Test
    public void teardown_switchedUserInSetup_switchesUserBack() throws Exception {
        when(mTestInfo.getDevice().getCurrentUser()).thenReturn(10);
        mPreparer.setUp(mTestInfo);
        Mockito.reset(mTestInfo);
        when(mTestInfo.getDevice().switchUser(anyInt())).thenReturn(true);

        mPreparer.tearDown(mTestInfo, /* throwable= */ null);

        verify(mTestInfo.getDevice()).switchUser(10);
    }
}
