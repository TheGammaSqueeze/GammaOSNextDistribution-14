/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.config.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.metric.AutoLogCollector;
import com.android.tradefed.invoker.logger.CurrentInvocation.IsolationGrade;
import com.android.tradefed.retry.BaseRetryDecision;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.testtype.SubprocessTfLauncher;
import com.google.common.truth.Truth;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.MultiPartResponse;
import com.proto.tradefed.feature.PartResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


/**
 * Unit tests for {@link OptionFetcher}.
 */
@RunWith(JUnit4.class)
public class OptionFetcherTest {

    @Mock TradefedFeatureClient mMockClient;
    private OptionFetcher mOptionFetcher;
    private IConfiguration mConfiguration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mOptionFetcher = new OptionFetcher(mMockClient);
        mConfiguration = new Configuration("name", "description");
        mConfiguration.getCommandOptions()
                .getInvocationData().put(SubprocessTfLauncher.SUBPROCESS_TAG_NAME, "true");
        OptionSetter setter = new OptionSetter(
                mConfiguration.getCommandOptions(), mConfiguration.getRetryDecision());
        setter.setOptionValue("filter-previous-passed", "false");
        setter.setOptionValue("retry-isolation-grade", "FULLY_ISOLATED");
    }

    @Test
    public void testOptionFetch() {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        responseBuilder.setMultiPartResponse(
                MultiPartResponse.newBuilder()
                        .addResponsePart(
                                PartResponse.newBuilder()
                                        .setKey("filter-previous-passed")
                                        .setValue("true"))
                        .addResponsePart(
                                PartResponse.newBuilder()
                                        .setKey("retry-isolation-grade")
                                        .setValue("REBOOT_ISOLATED"))
                        .addResponsePart(
                                PartResponse.newBuilder()
                                        .setKey("auto-collect")
                                        .setValue("DEVICE_TRACE")));

        when(mMockClient.triggerFeature(
                Mockito.eq(CommandOptionsGetter.COMMAND_OPTIONS_GETTER), Mockito.any()))
                .thenReturn(responseBuilder.build());
        mOptionFetcher.fetchParentOptions(mConfiguration);

        assertTrue(mConfiguration.getCommandOptions().filterPreviousPassedTests());
        assertEquals(IsolationGrade.REBOOT_ISOLATED,
                ((BaseRetryDecision) mConfiguration.getRetryDecision()).getIsolationGrade());
        Truth.assertThat(mConfiguration.getCommandOptions().getAutoLogCollectors())
                .contains(AutoLogCollector.DEVICE_TRACE);
    }

    @Test
    public void testOptionFetch_single() {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        responseBuilder.setResponse("REBOOT_ISOLATED");

        when(mMockClient.triggerFeature(
                Mockito.eq(CommandOptionsGetter.COMMAND_OPTIONS_GETTER), Mockito.any()))
                .thenReturn(responseBuilder.build());
        mOptionFetcher.fetchParentOptions(mConfiguration);

        assertEquals(IsolationGrade.REBOOT_ISOLATED,
                ((BaseRetryDecision) mConfiguration.getRetryDecision()).getIsolationGrade());
    }

    /**
     * Test that if we receive an option value that doesn't exist we do not throw an exception
     * and simply ignore it.
     */
    @Test
    public void testOptionFetch_notExist() {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        responseBuilder.setMultiPartResponse(
                MultiPartResponse.newBuilder()
                    .addResponsePart(PartResponse.newBuilder()
                            .setKey("filter-previous-passed").setValue("true"))
                    .addResponsePart(PartResponse.newBuilder()
                            .setKey("retry-isolation-grade").setValue("REBOOT_ISOLATED"))
                    .addResponsePart(PartResponse.newBuilder()
                            .setKey("does-not-exist-yet").setValue("newvalue")));

        when(mMockClient.triggerFeature(
                Mockito.eq(CommandOptionsGetter.COMMAND_OPTIONS_GETTER), Mockito.any()))
                .thenReturn(responseBuilder.build());
        mOptionFetcher.fetchParentOptions(mConfiguration);

        assertTrue(mConfiguration.getCommandOptions().filterPreviousPassedTests());
        assertEquals(IsolationGrade.REBOOT_ISOLATED,
                ((BaseRetryDecision) mConfiguration.getRetryDecision()).getIsolationGrade());
    }
}
