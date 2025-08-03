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
package com.android.tradefed.result.skipped;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.skipped.SkipReason.DemotionTrigger;

import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SkipManager}. */
@RunWith(JUnit4.class)
public class SkipManagerTest {

    private SkipManager mManager;
    private TestInformation mTestInformation;
    private IConfiguration mConfiguration;
    private IInvocationContext mContext;

    @Before
    public void setup() {
        mManager = new SkipManager();
        mManager.setSkipDecision(true);
        mContext = new InvocationContext();
        mConfiguration = new Configuration("test", "name");
        mTestInformation = TestInformation.newBuilder().setInvocationContext(mContext).build();
    }

    @Test
    public void testSkipInvocation() {
        Truth.assertThat(mManager.shouldSkipInvocation(mTestInformation)).isFalse();
        mManager.reportDiscoveryWithNoTests();
        Truth.assertThat(mManager.shouldSkipInvocation(mTestInformation)).isTrue();
    }

    @Test
    public void testEmptySetup() {
        mManager.setup(mConfiguration, mContext);
        Truth.assertThat(mManager.getDemotedTests()).isEmpty();
    }

    @Test
    public void testSetupAndFilters() throws Exception {
        OptionSetter setter = new OptionSetter(mManager);
        setter.setOptionValue(
                "demotion-filters",
                "x86 module-name",
                new SkipReason("message", DemotionTrigger.ERROR_RATE).toString());
        mManager.setup(mConfiguration, mContext);
        Truth.assertThat(mManager.getDemotedTests().size()).isEqualTo(1);
    }
}
