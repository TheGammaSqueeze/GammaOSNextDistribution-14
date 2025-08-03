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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.TestInformation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit Tests for {@link KnownFailurePreparer}. */
@RunWith(JUnit4.class)
public class KnownFailurePreparerTest {

    private KnownFailurePreparer mKnownFailurePreparer;
    private IConfiguration mMockConfig;
    private TestInformation mTestInformation;
    private static final String KNOWN_FAILURE_LIST = "known-failure-list";
    private static final String SKIP_RETRYING_LIST = "skip-retrying-list";

    @Before
    public void setUp() {
        mKnownFailurePreparer = new KnownFailurePreparer();
        mTestInformation = Mockito.mock(TestInformation.class);
        mMockConfig = Mockito.mock(IConfiguration.class);
        mKnownFailurePreparer.setConfiguration(mMockConfig);
    }

    @Test
    public void testSetup() throws Exception {
        OptionSetter setter = new OptionSetter(mKnownFailurePreparer);
        String moduleId1 = "s86 module1";
        String moduleId2 = "abi2 module2";
        setter.setOptionValue(KNOWN_FAILURE_LIST, moduleId1);
        setter.setOptionValue(KNOWN_FAILURE_LIST, moduleId2);
        mKnownFailurePreparer.setUp(mTestInformation);
        Mockito.verify(mMockConfig, Mockito.times(1))
                .injectOptionValue(SKIP_RETRYING_LIST, moduleId1);
        Mockito.verify(mMockConfig, Mockito.times(1))
                .injectOptionValue(SKIP_RETRYING_LIST, moduleId2);
    }
}
