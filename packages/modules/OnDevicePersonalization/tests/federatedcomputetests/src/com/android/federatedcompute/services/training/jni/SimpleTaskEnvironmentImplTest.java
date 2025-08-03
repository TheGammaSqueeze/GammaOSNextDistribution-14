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

package com.android.federatedcompute.services.training.jni;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.federatedcompute.services.examplestore.ExampleIterator;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class SimpleTaskEnvironmentImplTest {
    private SimpleTaskEnvironmentImpl mNativeRunnerDeps;

    @Mock ListenableSupplier<Boolean> mInterruptionFlag;
    @Mock ExampleIterator mExampleIterator;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mInterruptionFlag.get()).thenReturn(false);
        when(mExampleIterator.hasNext()).thenReturn(false);
        mNativeRunnerDeps = new SimpleTaskEnvironmentImpl(mInterruptionFlag, mExampleIterator);
    }

    @After
    public void tearDown() {
        if (mNativeRunnerDeps != null) {
            mNativeRunnerDeps.close();
        }
    }

    @Test
    public void testTrainingConditionsSatisfied() {
        assertThat(mNativeRunnerDeps.trainingConditionsSatisfied()).isTrue();
    }

    @Test
    public void setTrainingConditionsInterruptionFlag() {
        when(mInterruptionFlag.get()).thenReturn(Boolean.TRUE);

        assertThat(mNativeRunnerDeps.trainingConditionsSatisfied()).isFalse();
    }

    @Test
    public void testGetCacheDir() {
        assertThrows(UnsupportedOperationException.class, () -> mNativeRunnerDeps.getCacheDir());
    }

    @Test
    public void testGetBaseDir() {
        assertThrows(UnsupportedOperationException.class, () -> mNativeRunnerDeps.getBaseDir());
    }
}
