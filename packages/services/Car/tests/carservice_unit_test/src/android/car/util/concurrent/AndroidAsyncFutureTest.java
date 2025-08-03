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
package android.car.util.concurrent;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class AndroidAsyncFutureTest {

    @Mock
    private AndroidFuture<Integer> mFuture;

    private MockitoSession mMockSession;

    private AndroidAsyncFuture<Integer> mAndroidAsyncFuture;

    @Before
    public void setupMocks() {
        mMockSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mAndroidAsyncFuture = new AndroidAsyncFuture<Integer>(mFuture);
    }

    @After
    public void tearDown() {
        try {
            mMockSession.finishMocking();
        } finally {
            // When using inline mock maker, clean up inline mocks to prevent OutOfMemory errors.
            // See https://github.com/mockito/mockito/issues/1614 and b/259280359.
            Mockito.framework().clearInlineMocks();
        }
    }

    @Test
    public void testGet() throws Exception {
        Integer input = 0;
        when(mFuture.get()).thenReturn(input);

        Integer result = mAndroidAsyncFuture.get();

        assertThat(result).isEqualTo(input);
        verify(mFuture).get();
    }

    @Test
    public void testGetWithTimeout() throws Exception {
        long timeout = 100;
        TimeUnit timeUnit = TimeUnit.SECONDS;
        Integer input = 0;
        when(mFuture.get(timeout, timeUnit)).thenReturn(input);

        Integer result = mAndroidAsyncFuture.get(timeout, timeUnit);

        assertThat(result).isEqualTo(input);
        verify(mFuture).get(timeout, timeUnit);
    }

    @Test
    public void testWhenCompleteAsync() throws Exception {
        Executor executor = (a) -> {};
        BiConsumer<Integer, Throwable> biConsumer = (l, m) -> {};

        AsyncFuture asyncFuture = mAndroidAsyncFuture.whenCompleteAsync(biConsumer, executor);

        assertThat(asyncFuture).isEqualTo(mAndroidAsyncFuture);
        verify(mFuture).whenCompleteAsync(biConsumer, executor);
    }
}
