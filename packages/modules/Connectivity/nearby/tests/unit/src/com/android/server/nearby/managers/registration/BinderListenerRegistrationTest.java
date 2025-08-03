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

package com.android.server.nearby.managers.registration;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.server.nearby.managers.ListenerMultiplexer;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

/**
 * Unit test for {@link BinderListenerRegistration} class.
 */
public class BinderListenerRegistrationTest {
    private TestMultiplexer mMultiplexer;
    private boolean mOnRegisterCalled;
    private boolean mOnUnRegisterCalled;

    @Before
    public void setUp() {
        mMultiplexer = new TestMultiplexer();
    }

    @Test
    public void test_addAndRemove() throws RemoteException {
        Runnable listener = mock(Runnable.class);
        IBinder binder = mock(IBinder.class);
        int value = 2;
        BinderListenerRegistration<Runnable> registration = mMultiplexer.addListener(binder,
                listener, value);
        // First element, onRegister should be called
        assertThat(mOnRegisterCalled).isTrue();
        verify(binder, times(1)).linkToDeath(any(), anyInt());
        mMultiplexer.notifyListeners();
        verify(listener, times(1)).run();
        synchronized (mMultiplexer.mMultiplexerLock) {
            assertThat(mMultiplexer.mMerged).isEqualTo(value);
        }
        reset(listener);

        Runnable listener2 = mock(Runnable.class);
        IBinder binder2 = mock(IBinder.class);
        int value2 = 1;
        BinderListenerRegistration<Runnable> registration2 = mMultiplexer.addListener(binder2,
                listener2, value2);
        verify(binder2, times(1)).linkToDeath(any(), anyInt());
        mMultiplexer.notifyListeners();
        verify(listener2, times(1)).run();
        synchronized (mMultiplexer.mMultiplexerLock) {
            assertThat(mMultiplexer.mMerged).isEqualTo(value);
        }
        reset(listener);
        reset(listener2);

        registration2.remove();
        verify(binder2, times(1)).unlinkToDeath(any(), anyInt());
        // Remove one element, onUnregister should NOT be called
        assertThat(mOnUnRegisterCalled).isFalse();
        mMultiplexer.notifyListeners();
        verify(listener, times(1)).run();
        synchronized (mMultiplexer.mMultiplexerLock) {
            assertThat(mMultiplexer.mMerged).isEqualTo(value);
        }
        reset(listener);
        reset(listener2);

        registration.remove();
        verify(binder, times(1)).unlinkToDeath(any(), anyInt());
        // Remove all elements, onUnregister should NOT be called
        assertThat(mOnUnRegisterCalled).isTrue();
        synchronized (mMultiplexer.mMultiplexerLock) {
            assertThat(mMultiplexer.mMerged).isEqualTo(Integer.MIN_VALUE);
        }
    }

    private class TestMultiplexer extends
            ListenerMultiplexer<Runnable, TestMultiplexer.TestListenerRegistration, Integer> {
        @Override
        public void onRegister() {
            mOnRegisterCalled = true;
        }

        @Override
        public void onUnregister() {
            mOnUnRegisterCalled = true;
        }

        @Override
        public Integer mergeRegistrations(
                @NonNull Collection<TestListenerRegistration> testListenerRegistrations) {
            int max = Integer.MIN_VALUE;
            for (TestListenerRegistration registration : testListenerRegistrations) {
                max = Math.max(max, registration.getValue());
            }
            return max;
        }

        @Override
        public void onMergedRegistrationsUpdated() {
        }

        public BinderListenerRegistration<Runnable> addListener(IBinder binder, Runnable runnable,
                int value) {
            TestListenerRegistration registration = new TestListenerRegistration(binder, runnable,
                    value);
            putRegistration(binder, registration);
            return registration;
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
