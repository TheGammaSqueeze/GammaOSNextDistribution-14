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
package com.android.tradefed.config.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.service.TradefedFeatureClient;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.PartResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link GlobalFilterGetter}. */
@RunWith(JUnit4.class)
public class GlobalFilterGetterTest {

    @Mock TradefedFeatureClient mMockClient;
    private GlobalFilterGetter mGlobalFilterGetter;
    private IConfiguration mConfiguration;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mGlobalFilterGetter = new GlobalFilterGetter();
        mConfiguration = new Configuration("name", "description");
    }

    @Test
    public void testNoConfig() {
        FeatureRequest.Builder builder = FeatureRequest.newBuilder();
        FeatureResponse response = mGlobalFilterGetter.execute(builder.build());
        assertTrue(response.hasErrorInfo());
    }

    @Test
    public void testGetFilter() throws Exception {
        FeatureRequest.Builder builder = FeatureRequest.newBuilder();
        GlobalTestFilter glob = new GlobalTestFilter();
        OptionSetter setter = new OptionSetter(glob);
        setter.setOptionValue(GlobalTestFilter.INCLUDE_FILTER_OPTION, "filter1");
        setter.setOptionValue(GlobalTestFilter.INCLUDE_FILTER_OPTION, "filter2");
        mConfiguration.setConfigurationObject(Configuration.GLOBAL_FILTERS_TYPE_NAME, glob);
        mGlobalFilterGetter.setConfiguration(mConfiguration);
        FeatureResponse response = mGlobalFilterGetter.execute(builder.build());
        String includeFilters = null;
        String delimiter = null;
        for (PartResponse partResponse : response.getMultiPartResponse().getResponsePartList()) {
            if (partResponse.getKey().equals(GlobalTestFilter.INCLUDE_FILTER_OPTION)) {
                includeFilters = partResponse.getValue();
            } else if (partResponse.getKey().equals(GlobalTestFilter.DELIMITER_NAME)) {
                delimiter = partResponse.getValue();
            }
        }
        assertNotNull(includeFilters);
        assertNotNull(delimiter);
        String[] split = includeFilters.split(delimiter);
        assertEquals("filter1", split[0]);
        assertEquals("filter2", split[1]);
    }
}
