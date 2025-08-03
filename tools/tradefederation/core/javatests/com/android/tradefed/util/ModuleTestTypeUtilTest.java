/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tradefed.util;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.IConfiguration;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ModuleTestTypeUtil}. */
@RunWith(JUnit4.class)
public class ModuleTestTypeUtilTest {

    public static final String TEST_TYPE_KEY = "test-type";
    public static final String TEST_TYPE_VALUE_PERFORMANCE = "performance";

    @Mock IConfiguration mMockPerformanceModule;
    @Mock IConfiguration mMockUnspecifiedModule;
    @Mock ConfigurationDescriptor mMockPerformanceConfigDescriptor;
    @Mock ConfigurationDescriptor mMockUnspecifiedConfigDescriptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockPerformanceModule.getConfigurationDescription())
                .thenReturn(mMockPerformanceConfigDescriptor);
        when(mMockUnspecifiedModule.getConfigurationDescription())
                .thenReturn(mMockUnspecifiedConfigDescriptor);
        when(mMockPerformanceConfigDescriptor.getMetaData(ModuleTestTypeUtil.TEST_TYPE_KEY))
                .thenReturn(Arrays.asList(ModuleTestTypeUtil.TEST_TYPE_VALUE_PERFORMANCE));
        when(mMockUnspecifiedConfigDescriptor.getMetaData(ModuleTestTypeUtil.TEST_TYPE_KEY))
                .thenReturn(new ArrayList<String>());
    }

    @Test
    public void testPositiveTestTypeMatching() {
        List<String> matched =
                ModuleTestTypeUtil.getMatchedConfigTestTypes(
                        mMockPerformanceModule,
                        Arrays.asList(ModuleTestTypeUtil.TEST_TYPE_VALUE_PERFORMANCE));

        assertEquals(matched, Arrays.asList(ModuleTestTypeUtil.TEST_TYPE_VALUE_PERFORMANCE));
    }

    @Test
    public void testNegativeTestTypeMatching() {
        List<String> matched =
                ModuleTestTypeUtil.getMatchedConfigTestTypes(
                        mMockUnspecifiedModule,
                        Arrays.asList(ModuleTestTypeUtil.TEST_TYPE_VALUE_PERFORMANCE));

        assertTrue(matched.isEmpty());
    }

    @Test
    public void testPositivePerformanceTestTypeMatching() {
        boolean isPerformanceModule =
                ModuleTestTypeUtil.isPerformanceModule(mMockPerformanceModule);

        assertTrue(isPerformanceModule);
    }

    @Test
    public void testNegativePerformanceTestTypeMatching() {
        boolean isPerformanceModule =
                ModuleTestTypeUtil.isPerformanceModule(mMockUnspecifiedModule);

        assertFalse(isPerformanceModule);
    }
}
