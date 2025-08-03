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
package com.android.tradefed.targetprep;

import static com.android.tradefed.targetprep.RunOnSdkSandboxTargetPreparer.RUN_TESTS_ON_SDK_SANDBOX;

import static org.mockito.Mockito.verify;

import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RunOnSdkSandboxTargetPreparerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private TestInformation mTestInfo;

    private RunOnSdkSandboxTargetPreparer mPreparer;

    @Before
    public void setUp() {
        mPreparer = new RunOnSdkSandboxTargetPreparer();
    }

    @Test
    public void testInstrumentToRunSdkSandbox() throws Exception {
        mPreparer.setUp(mTestInfo);

        verify(mTestInfo.properties()).put(RUN_TESTS_ON_SDK_SANDBOX, Boolean.TRUE.toString());
    }
}
