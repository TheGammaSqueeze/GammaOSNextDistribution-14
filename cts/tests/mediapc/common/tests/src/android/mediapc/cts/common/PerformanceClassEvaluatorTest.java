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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PerformanceClassEvaluatorTest {

    @Mock TestName mMockTestName;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void constructorTest_replacesNullWithEmpty() {
        Mockito.when(mMockTestName.getMethodName()).thenReturn(null);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(mMockTestName);
        assertThat(pce.getTestName()).isEqualTo("");
    }

    @Test
    public void constructorTest_replacesCurlyBraces() {
        Mockito.when(mMockTestName.getMethodName()).thenReturn("{}");

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(mMockTestName);
        assertThat(pce.getTestName()).isEqualTo("()");
    }
}
