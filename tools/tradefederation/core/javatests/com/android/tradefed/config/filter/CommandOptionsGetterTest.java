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

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CommandOptionsGetter}.
 */
@RunWith(JUnit4.class)
public class CommandOptionsGetterTest {

    private IConfiguration mConfiguration;
    private CommandOptionsGetter mGetter;

    @Before
    public void setUp() {
        mConfiguration = new Configuration("name", "description");
        mGetter = new CommandOptionsGetter();
        mGetter.setConfiguration(mConfiguration);
    }

    @Test
    public void getCommandOptionsValue() throws Exception {
        OptionSetter setter = new OptionSetter(mConfiguration.getCommandOptions());
        setter.setOptionValue("filter-previous-passed", "false");
        FeatureResponse response = mGetter.execute(FeatureRequest.newBuilder()
                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                .putArgs(CommandOptionsGetter.OPTION_NAME, "filter-previous-passed").build());
        assertEquals("false", response.getResponse());
    }

    @Test
    public void getCommandOptionsValue_updated() throws Exception {
        OptionSetter setter = new OptionSetter(mConfiguration.getCommandOptions());
        setter.setOptionValue("filter-previous-passed", "true");
        FeatureResponse response = mGetter.execute(FeatureRequest.newBuilder()
                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                .putArgs(CommandOptionsGetter.OPTION_NAME, "filter-previous-passed").build());

        assertEquals("true", response.getResponse());
    }

    @Test
    public void getCommandOptionsValue_multiValue() throws Exception {
        OptionSetter setter = new OptionSetter(mConfiguration.getCommandOptions());
        setter.setOptionValue("filter-previous-passed", "true");
        setter.setOptionValue("test-tag", "mytag");
        FeatureResponse response =
                mGetter.execute(
                        FeatureRequest.newBuilder()
                                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                                .putArgs(
                                        CommandOptionsGetter.OPTION_NAME,
                                        "filter-previous-passed,test-tag,auto-collect")
                                .build());
        assertEquals(2, response.getMultiPartResponse().getResponsePartList().size());

        PartResponse part1 = response.getMultiPartResponse().getResponsePart(0);
        assertEquals("filter-previous-passed", part1.getKey());
        assertEquals("true", part1.getValue());

        PartResponse part2 = response.getMultiPartResponse().getResponsePart(1);
        assertEquals("test-tag", part2.getKey());
        assertEquals("mytag", part2.getValue());
    }

    @Test
    public void getCommandOptionsValue_set() throws Exception {
        OptionSetter setter = new OptionSetter(mConfiguration.getCommandOptions());
        setter.setOptionValue("filter-previous-passed", "true");
        setter.setOptionValue("test-tag", "mytag");
        setter.setOptionValue("auto-collect", "DEVICE_TRACE");
        setter.setOptionValue("auto-collect", "LOGCAT_ON_FAILURE");
        FeatureResponse response =
                mGetter.execute(
                        FeatureRequest.newBuilder()
                                .setName(CommandOptionsGetter.COMMAND_OPTIONS_GETTER)
                                .putArgs(
                                        CommandOptionsGetter.OPTION_NAME,
                                        "filter-previous-passed,test-tag,auto-collect")
                                .build());
        assertEquals(4, response.getMultiPartResponse().getResponsePartList().size());

        PartResponse part1 = response.getMultiPartResponse().getResponsePart(0);
        assertEquals("filter-previous-passed", part1.getKey());
        assertEquals("true", part1.getValue());

        PartResponse part2 = response.getMultiPartResponse().getResponsePart(1);
        assertEquals("test-tag", part2.getKey());
        assertEquals("mytag", part2.getValue());

        PartResponse part3 = response.getMultiPartResponse().getResponsePart(2);
        assertEquals("auto-collect", part3.getKey());
        assertEquals("DEVICE_TRACE", part3.getValue());

        PartResponse part4 = response.getMultiPartResponse().getResponsePart(3);
        assertEquals("auto-collect", part4.getKey());
        assertEquals("LOGCAT_ON_FAILURE", part4.getValue());
    }
}
