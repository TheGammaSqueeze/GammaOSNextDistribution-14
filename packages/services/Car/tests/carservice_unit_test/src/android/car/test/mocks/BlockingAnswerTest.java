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

package android.car.test.mocks;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(MockitoJUnitRunner.class)
public final class BlockingAnswerTest {

    private static final long TIMEOUT_MS = 2_000L;

    @Mock private SomeService mService;

    @Test
    public void testForReturn() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        int returnValue = 1;
        when(mService.intMethod()).thenReturn(returnValue);
        final AtomicBoolean invoked = new AtomicBoolean();
        BlockingAnswer<Integer> blockingAnswer = BlockingAnswer.forReturn(TIMEOUT_MS, inv -> {
            invoked.set(true);
            latch.countDown();
        }, returnValue);
        when(mService.intMethod()).thenAnswer(blockingAnswer);

        mService.intMethod();

        // By unblocking we release the code triggered by blocking answer to run on a
        // separated thread.
        blockingAnswer.unblock();

        // Since code from BlockingAnswer is triggered in a separated thread, will use the
        // countdown latch to sync up.
        latch.await(1, TimeUnit.SECONDS);
        assertThat(invoked.get()).isTrue();
    }

    @Test
    public void testForVoidReturn() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean invoked = new AtomicBoolean();
        BlockingAnswer<Void> blockingAnswer = BlockingAnswer.forVoidReturn(TIMEOUT_MS, inv -> {
            invoked.set(true);
            latch.countDown();
        });
        doAnswer(blockingAnswer).when(mService).voidMethod();

        mService.voidMethod();

        // By unblocking we release the code triggered by blocking answer to run on a
        // separated thread.
        blockingAnswer.unblock();

        // Since code from BlockingAnswer is triggered in a separated thread, will use the
        // countdown latch to sync up.
        latch.await(1, TimeUnit.SECONDS);
        assertThat(invoked.get()).isTrue();
    }

    private interface SomeService {

        void voidMethod();

        int intMethod();
    }
}
