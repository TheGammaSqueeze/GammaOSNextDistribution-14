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

package com.android.server.nearby.managers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.IBinder;

import androidx.annotation.NonNull;

import com.android.server.nearby.managers.registration.BinderListenerRegistration;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class ListenerMultiplexerTest {

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testAdd() {
        TestMultiplexer multiplexer = new TestMultiplexer();

        Runnable listener = mock(Runnable.class);
        IBinder binder = mock(IBinder.class);
        int value = 2;
        multiplexer.addListener(binder, listener, value);

        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(1);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
        Runnable listener2 = mock(Runnable.class);
        IBinder binder2 = mock(IBinder.class);
        int value2 = 1;
        multiplexer.addListener(binder2, listener2, value2);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(2);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
    }

    @Test
    public void testReplace() {
        TestMultiplexer multiplexer = new TestMultiplexer();
        Runnable listener = mock(Runnable.class);
        IBinder binder = mock(IBinder.class);
        int value = 2;
        multiplexer.addListener(binder, listener, value);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
        multiplexer.notifyListeners();
        verify(listener, times(1)).run();
        reset(listener);

        // Same key, different value
        Runnable listener2 = mock(Runnable.class);
        int value2 = 1;
        multiplexer.addListener(binder, listener2, value2);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            // Should not be called again
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mOnUnregisterCalledCount).isEqualTo(0);
            assertThat(multiplexer.mMerged).isEqualTo(value2);
        }
        // Run on the new listener
        multiplexer.notifyListeners();
        verify(listener, never()).run();
        verify(listener2, times(1)).run();

        multiplexer.removeRegistration(binder);

        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isFalse();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mOnUnregisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(Integer.MIN_VALUE);
        }
    }

    @Test
    public void testRemove() {
        TestMultiplexer multiplexer = new TestMultiplexer();
        Runnable listener = mock(Runnable.class);
        IBinder binder = mock(IBinder.class);
        int value = 2;
        multiplexer.addListener(binder, listener, value);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
        multiplexer.notifyListeners();
        verify(listener, times(1)).run();
        reset(listener);

        multiplexer.removeRegistration(binder);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isFalse();
            assertThat(multiplexer.mMerged).isEqualTo(Integer.MIN_VALUE);
        }
        multiplexer.notifyListeners();
        verify(listener, never()).run();
    }

    @Test
    public void testMergeMultiple() {
        TestMultiplexer multiplexer = new TestMultiplexer();

        Runnable listener = mock(Runnable.class);
        IBinder binder = mock(IBinder.class);
        int value = 2;

        Runnable listener2 = mock(Runnable.class);
        IBinder binder2 = mock(IBinder.class);
        int value2 = 1;

        Runnable listener3 = mock(Runnable.class);
        IBinder binder3 = mock(IBinder.class);
        int value3 = 5;

        multiplexer.addListener(binder, listener, value);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(1);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
        multiplexer.notifyListeners();
        verify(listener, times(1)).run();
        verify(listener2, never()).run();
        verify(listener3, never()).run();

        multiplexer.addListener(binder2, listener2, value2);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(2);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(1);
            assertThat(multiplexer.mMerged).isEqualTo(value);
        }
        multiplexer.notifyListeners();
        verify(listener, times(2)).run();
        verify(listener2, times(1)).run();
        verify(listener3, never()).run();

        multiplexer.addListener(binder3, listener3, value3);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(3);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(2);
            assertThat(multiplexer.mMerged).isEqualTo(value3);
        }
        multiplexer.notifyListeners();
        verify(listener, times(3)).run();
        verify(listener2, times(2)).run();
        verify(listener3, times(1)).run();

        multiplexer.removeRegistration(binder);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(4);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(2);
            assertThat(multiplexer.mMerged).isEqualTo(value3);
        }
        multiplexer.notifyListeners();
        verify(listener, times(3)).run();
        verify(listener2, times(3)).run();
        verify(listener3, times(2)).run();

        multiplexer.removeRegistration(binder3);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isTrue();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(5);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(3);
            assertThat(multiplexer.mMerged).isEqualTo(value2);
        }
        multiplexer.notifyListeners();
        verify(listener, times(3)).run();
        verify(listener2, times(4)).run();
        verify(listener3, times(2)).run();

        multiplexer.removeRegistration(binder2);
        synchronized (multiplexer.mMultiplexerLock) {
            assertThat(multiplexer.mRegistered).isFalse();
            assertThat(multiplexer.mOnRegisterCalledCount).isEqualTo(1);
            assertThat(multiplexer.mMergeOperationCount).isEqualTo(6);
            assertThat(multiplexer.mMergeUpdatedCount).isEqualTo(4);
            assertThat(multiplexer.mMerged).isEqualTo(Integer.MIN_VALUE);
        }
        multiplexer.notifyListeners();
        verify(listener, times(3)).run();
        verify(listener2, times(4)).run();
        verify(listener3, times(2)).run();
    }

    private class TestMultiplexer extends
            ListenerMultiplexer<Runnable, TestMultiplexer.TestListenerRegistration, Integer> {
        int mOnRegisterCalledCount;
        int mOnUnregisterCalledCount;
        boolean mRegistered;
        private int mMergeOperationCount;
        private int mMergeUpdatedCount;

        @Override
        public void onRegister() {
            mOnRegisterCalledCount++;
            mRegistered = true;
        }

        @Override
        public void onUnregister() {
            mOnUnregisterCalledCount++;
            mRegistered = false;
        }

        @Override
        public Integer mergeRegistrations(
                @NonNull Collection<TestListenerRegistration> testListenerRegistrations) {
            mMergeOperationCount++;
            int max = Integer.MIN_VALUE;
            for (TestListenerRegistration registration : testListenerRegistrations) {
                max = Math.max(max, registration.getValue());
            }
            return max;
        }

        @Override
        public void onMergedRegistrationsUpdated() {
            mMergeUpdatedCount++;
        }

        public void addListener(IBinder binder, Runnable runnable, int value) {
            TestListenerRegistration registration = new TestListenerRegistration(binder, runnable,
                    value);
            putRegistration(binder, registration);
        }

        public void notifyListeners() {
            deliverToListeners(registration -> Runnable::run);
        }

        private class TestListenerRegistration extends BinderListenerRegistration<Runnable> {
            private final int mValue;

            protected TestListenerRegistration(IBinder binder, Runnable runnable, int value) {
                super(binder, MoreExecutors.directExecutor(), runnable);
                mValue = value;
            }

            @Override
            public TestMultiplexer getOwner() {
                return TestMultiplexer.this;
            }

            public int getValue() {
                return mValue;
            }
        }
    }
}
