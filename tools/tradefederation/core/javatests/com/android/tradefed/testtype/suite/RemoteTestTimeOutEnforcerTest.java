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
package com.android.tradefed.testtype.suite;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.Mockito.mock;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.HashMap;

/** Unit tests for {@link RemoteTestTimeOutEnforcer}. */
@RunWith(JUnit4.class)
public class RemoteTestTimeOutEnforcerTest {

    private RemoteTestTimeOutEnforcer mEnforcer;
    private InvocationContext mModuleInvocationContext;
    private ConfigurationDescriptor mConfigurationDescriptor;
    private ModuleListener mListener;
    private IRemoteTest mIRemoteTest;
    private ModuleDefinition mModuleDefinition;
    private String mModuleName = "module";
    private String mTestMappingPath = "a/b/c";
    private Duration mTimeout = Duration.ofSeconds(100);

    @Before
    public void setUp() {
        mIRemoteTest = new StubTest();
        mConfigurationDescriptor = new ConfigurationDescriptor();
        mModuleDefinition = Mockito.mock(ModuleDefinition.class);
        mModuleInvocationContext = new InvocationContext();
        mConfigurationDescriptor.addMetadata(
                Integer.toString(mIRemoteTest.hashCode()), mTestMappingPath);
        mModuleInvocationContext.setConfigurationDescriptor(mConfigurationDescriptor);
        Mockito.when(mModuleDefinition.getId()).thenReturn(mModuleName);
        Mockito.when(mModuleDefinition.getModuleInvocationContext())
                .thenReturn(mModuleInvocationContext);
        mListener =
                new ModuleListener(mock(ITestInvocationListener.class), mModuleInvocationContext);
        mEnforcer =
                new RemoteTestTimeOutEnforcer(mListener, mModuleDefinition, mIRemoteTest, mTimeout);
    }

    @Test
    public void testTimeout() {
        mEnforcer.testRunEnded(200000L, new HashMap<String, Metric>());
        assertTrue(
                mListener
                        .getCurrentRunResults()
                        .getRunFailureDescription()
                        .getErrorMessage()
                        .contains(
                                String.format(
                                        "%s defined in [%s] took 200 seconds while timeout is %s"
                                                + " seconds",
                                        mModuleName, mTestMappingPath, mTimeout.getSeconds())));
        assertFalse(mListener.getCurrentRunResults().getRunFailureDescription().isRetriable());
    }

    @Test
    public void testNoTimeout() {
        mEnforcer.testRunEnded(10000L, new HashMap<String, Metric>());
        assertNull(mListener.getCurrentRunResults().getRunFailureDescription());
    }
}
