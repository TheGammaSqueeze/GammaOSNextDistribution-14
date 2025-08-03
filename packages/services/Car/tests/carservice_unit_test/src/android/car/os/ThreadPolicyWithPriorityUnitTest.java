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

package android.car.os;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ThreadPolicyWithPriorityUnitTest {

    @Test
    public void testThreadPolicyWithPriorityMinPriority() {
        int expectedPolicy = ThreadPolicyWithPriority.SCHED_FIFO;
        int expectedPriority = ThreadPolicyWithPriority.PRIORITY_MIN;

        ThreadPolicyWithPriority gotPolicyPriority = new ThreadPolicyWithPriority(
                expectedPolicy, expectedPriority);

        assertThat(gotPolicyPriority.getPolicy()).isEqualTo(expectedPolicy);
        assertThat(gotPolicyPriority.getPriority()).isEqualTo(expectedPriority);
    }

    @Test
    public void testThreadPolicyWithPriorityMaxPriority() {
        int expectedPolicy = ThreadPolicyWithPriority.SCHED_FIFO;
        int expectedPriority = ThreadPolicyWithPriority.PRIORITY_MAX;

        ThreadPolicyWithPriority gotPolicyPriority = new ThreadPolicyWithPriority(
                expectedPolicy, expectedPriority);

        assertThat(gotPolicyPriority.getPolicy()).isEqualTo(expectedPolicy);
        assertThat(gotPolicyPriority.getPriority()).isEqualTo(expectedPriority);
    }

    @Test
    public void testThreadPolicyWithPriorityInvalidPolicy() {
        int policy = -1;
        int priority = ThreadPolicyWithPriority.PRIORITY_MIN;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPolicyWithPriority(policy, priority));

        assertWithMessage("thrown exception has expected message").that(thrown).hasMessageThat()
                .contains("invalid policy");
    }

    @Test
    public void testThreadPolicyWithPriorityPriorityTooSmall() {
        int policy = ThreadPolicyWithPriority.SCHED_FIFO;
        int priority = ThreadPolicyWithPriority.PRIORITY_MIN - 1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPolicyWithPriority(policy, priority));

        assertWithMessage("thrown exception has expected message").that(thrown).hasMessageThat()
                .contains("invalid priority");
    }

    @Test
    public void testThreadPolicyWithPriorityPriorityTooLarge() {
        int policy = ThreadPolicyWithPriority.SCHED_FIFO;
        int priority = ThreadPolicyWithPriority.PRIORITY_MAX + 1;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new ThreadPolicyWithPriority(policy, priority));

        assertWithMessage("thrown exception has expected message").that(thrown).hasMessageThat()
                .contains("invalid priority");
    }

    @Test
    public void testDefaultThreadPolicy() {
        ThreadPolicyWithPriority gotPolicyPriority = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 1);

        assertThat(gotPolicyPriority.getPolicy()).isEqualTo(ThreadPolicyWithPriority.SCHED_DEFAULT);
        assertThat(gotPolicyPriority.getPriority()).isEqualTo(0);
    }
}
