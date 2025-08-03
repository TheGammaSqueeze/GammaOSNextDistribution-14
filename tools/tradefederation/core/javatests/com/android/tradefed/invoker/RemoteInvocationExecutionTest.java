/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.invoker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.DeviceSelectionOptions.DeviceRequestedType;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.result.proto.ProtoResultReporter;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Unit tests for {@link RemoteInvocationExecution}. */
@RunWith(JUnit4.class)
public class RemoteInvocationExecutionTest {

    private RemoteInvocationExecution mRemoteInvocation;
    private IInvocationContext mContext;
    private IConfiguration mConfiguration;
    private ITestInvocationListener mMockListener;

    @Before
    public void setUp() {
        mRemoteInvocation = new RemoteInvocationExecution();
        mContext = new InvocationContext();
        mConfiguration = new Configuration("name", "description");
        mMockListener = Mockito.mock(ITestInvocationListener.class);
    }

    @Test
    public void testFetchBuild() throws Exception {
        StubBuildProvider originalProvider =
                new StubBuildProvider() {
                    @Override
                    public IBuildInfo getBuild() throws BuildRetrievalError {
                        // The original provider is never called.
                        throw new BuildRetrievalError(
                                "should not be called.",
                                InfraErrorIdentifier.ARTIFACT_UNSUPPORTED_PATH);
                    }
                };
        mConfiguration.setBuildProvider(originalProvider);
        OptionSetter setter = new OptionSetter(originalProvider);
        setter.setOptionValue("build-id", "5555");
        TestInformation testInfo =
                TestInformation.newBuilder().setInvocationContext(mContext).build();
        boolean fetched = mRemoteInvocation.fetchBuild(testInfo, mConfiguration, null, null);
        assertTrue(fetched);
        IBuildInfo info = mContext.getBuildInfos().get(0);
        // The build id is carried to the remote invocation build
        assertEquals("5555", info.getBuildId());
    }

    @Test
    public void testCreateRemoteConfig() throws Exception {
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder();
        DeviceSelectionOptions selection = new DeviceSelectionOptions();
        selection.setDeviceTypeRequested(DeviceRequestedType.REMOTE_DEVICE);
        deviceConfig.addSpecificConfig(selection);
        mConfiguration.setDeviceConfig(deviceConfig);
        File res = null;
        try {
            res =
                    mRemoteInvocation.createRemoteConfig(
                            mContext, mConfiguration, mMockListener, "path/");
            IConfiguration reparse =
                    ConfigurationFactory.getInstance()
                            .createConfigurationFromArgs(new String[] {res.getAbsolutePath()});
            List<ITestInvocationListener> listeners = reparse.getTestInvocationListeners();
            assertEquals(1, listeners.size());
            assertTrue(listeners.get(0) instanceof ProtoResultReporter);
            // Ensure the requested type is reset
            assertNull(
                    ((DeviceSelectionOptions)
                                    reparse.getDeviceConfig().get(0).getDeviceRequirements())
                            .getDeviceTypeRequested());
            assertEquals(
                    "",
                    reparse.getDeviceConfig().get(0).getDeviceOptions().getRemoteTf().getPath());
        } finally {
            FileUtil.deleteFile(res);
        }

        verify(mMockListener)
                .testLog(
                        Mockito.eq(RemoteInvocationExecution.REMOTE_CONFIG),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        Mockito.any());
    }

    @Test
    public void testCreateRemoteConfigForInvocationWorkUnitId() throws Exception {
        mContext.addInvocationAttribute("invocation_id", "foo");
        mContext.addInvocationAttribute("work_unit_id", "bar");
        File res = null;
        try {
            res =
                    mRemoteInvocation.createRemoteConfig(
                            mContext, mConfiguration, mMockListener, "path/");
            IConfiguration reparse =
                    ConfigurationFactory.getInstance()
                            .createConfigurationFromArgs(new String[] {res.getAbsolutePath()});
            assertEquals(
                    "invocation_id not set as expected",
                    "foo",
                    reparse.getCommandOptions().getInvocationData().get("invocation_id").get(0));
            assertEquals(
                    "work_unit_id not set as expected",
                    "bar",
                    reparse.getCommandOptions().getInvocationData().get("work_unit_id").get(0));
        } finally {
            FileUtil.deleteFile(res);
        }

        verify(mMockListener)
                .testLog(
                        Mockito.eq(RemoteInvocationExecution.REMOTE_CONFIG),
                        Mockito.eq(LogDataType.HARNESS_CONFIG),
                        Mockito.any());
    }

    @Test
    public void testFileValueOptionTransform() throws Exception {
        IConfigurationFactory configFactory =
                new ConfigurationFactory() {
                    @Override
                    protected String getConfigPrefix() {
                        return "testconfigs/";
                    }
                };
        IGlobalConfiguration globalConfig =
                configFactory.createGlobalConfigurationFromArgs(
                        new String[] {"global-config-file-option-value-test"},
                        Collections.emptyList());
        IGlobalConfiguration transformedConfig = null;
        File newConfig = null;
        try {
            newConfig =
                    globalConfig.cloneConfigWithFilter(
                            new HashSet<>(),
                            new RemoteInvocationExecution.FileOptionValueTransformer("/foo/"),
                            true,
                            new String[] {
                                GlobalConfiguration.HOST_OPTIONS_TYPE_NAME,
                                GlobalConfiguration.KEY_STORE_TYPE_NAME
                            });
            transformedConfig =
                    configFactory.createGlobalConfigurationFromArgs(
                            ArrayUtil.buildArray(new String[] {newConfig.toString()}),
                            Collections.emptyList());
        } finally {
            FileUtil.deleteFile(newConfig);
        }
        assertEquals(
                "expected filename transformation did not happen",
                "/foo/tradefed_api-key.json",
                transformedConfig
                        .getHostOptions()
                        .getServiceAccountJsonKeyFiles()
                        .get("api-key")
                        .toString());
        assertEquals(
                "non-file path should not be transformed",
                "gs:/tradefed/other-key.json",
                transformedConfig
                        .getHostOptions()
                        .getServiceAccountJsonKeyFiles()
                        .get("other-key")
                        .toString());
    }
}
