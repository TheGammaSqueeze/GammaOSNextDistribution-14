/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.sandbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link SandboxInvocationRunner}. */
@RunWith(JUnit4.class)
public class SandboxInvocationRunnerTest {

    private IConfiguration mConfig;
    private IInvocationContext mContext;
    @Mock ISandbox mMockSandbox;
    @Mock ITestInvocationListener mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mConfig = new Configuration("name", "description");
        mContext = new InvocationContext();
    }

    /** Test a run that is successful. */
    @Test
    public void testPrepareAndRun() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        mConfig.setConfigurationObject(Configuration.SANDBOX_TYPE_NAME, mMockSandbox);
        when(mMockSandbox.prepareEnvironment(mContext, mConfig, mMockListener)).thenReturn(null);
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        when(mMockSandbox.run(info, mConfig, mMockListener)).thenReturn(res);

        SandboxInvocationRunner.prepareAndRun(info, mConfig, mMockListener);

        verify(mMockSandbox, times(1)).tearDown();
    }

    /** Test a failure to prepare the environment. The exception will be send up. */
    @Test
    public void testPrepareAndRun_prepFailure() throws Throwable {
        TestInformation info = TestInformation.newBuilder().setInvocationContext(mContext).build();
        mConfig.setConfigurationObject(Configuration.SANDBOX_TYPE_NAME, mMockSandbox);
        when(mMockSandbox.prepareEnvironment(mContext, mConfig, mMockListener))
                .thenReturn(new RuntimeException("my exception"));
        try {
            SandboxInvocationRunner.prepareAndRun(info, mConfig, mMockListener);
            fail("Should have thrown an exception.");
        } catch (RuntimeException expected) {
            assertEquals("my exception", expected.getMessage());
        }
        verify(mMockSandbox, times(1)).tearDown();
    }
}
