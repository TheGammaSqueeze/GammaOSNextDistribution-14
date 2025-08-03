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

package com.android.server.art;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.lenient;

import androidx.test.filters.SmallTest;

import com.android.server.art.testing.MockClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DebouncerTest {
    private MockClock mMockClock;
    private Debouncer mDebouncer;

    @Before
    public void setUp() throws Exception {
        mMockClock = new MockClock();
        mDebouncer =
                new Debouncer(100 /* intervalMs */, () -> mMockClock.createScheduledExecutor());
    }

    @Test
    public void test() throws Exception {
        List<Integer> list = new ArrayList<>();

        mDebouncer.maybeRunAsync(() -> list.add(1));
        mDebouncer.maybeRunAsync(() -> list.add(2));
        mMockClock.advanceTime(100);
        mDebouncer.maybeRunAsync(() -> list.add(3));
        mMockClock.advanceTime(99);
        mDebouncer.maybeRunAsync(() -> list.add(4));
        mMockClock.advanceTime(99);
        mDebouncer.maybeRunAsync(() -> list.add(5));
        mMockClock.advanceTime(1000);

        assertThat(list).containsExactly(2, 5).inOrder();

        // Verify that we don't create too many executors, and all the executors we create are
        // eventually shut down.
        List<MockClock.ScheduledExecutor> executors = mMockClock.getCreatedExecutors();
        assertThat(executors).hasSize(2);
        for (MockClock.ScheduledExecutor executor : executors) {
            assertThat(executor.isShutdown()).isTrue();
        }
    }
}
