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

package com.android.car.oem;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.Context;
import android.content.res.Resources;
import android.os.Process;
import android.os.RemoteException;

import com.android.car.R;
import com.android.car.oem.CarOemProxyServiceHelper.CallbackForDelayedResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CarOemProxyServiceHelperTest extends AbstractExtendedMockitoTestCase {
    private static final String CALLER_TAG = "test";

    private static final int TIMEOUT_MS = 60_000;

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;

    private CarOemProxyServiceHelper mCarOemProxyServiceHelper;

    @Before
    public void setUp() throws Exception {
        when(mContext.getResources()).thenReturn(mResources);
        mockCallTimeout(/* timeoutMs= */ 5000);
        mCarOemProxyServiceHelper = new CarOemProxyServiceHelper(mContext);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(Process.class);
    }

    @Test
    public void testDoBinderTimedCallWithDefault_returnCalculatedValue() throws Exception {
        assertThat(mCarOemProxyServiceHelper.doBinderTimedCallWithDefaultValue(CALLER_TAG,
                () -> "value", /* defaultValue= */ "default")).isEqualTo("value");
    }

    @Test
    public void testDoBinderTimedCallWithDefault_returnDefaultValue() throws Exception {
        mockCallTimeout(/* timeoutMs= */ 10);
        CarOemProxyServiceHelper carOemProxyServiceHelper = new CarOemProxyServiceHelper(mContext);

        assertThat(carOemProxyServiceHelper.doBinderTimedCallWithDefaultValue(CALLER_TAG, () -> {
            Thread.sleep(1000); // test will not wait for this timeout
            return "value";
        }, /* defaultValue= */ "default")).isEqualTo("default");

    }

    @Test
    public void testDoBinderTimedCall_timeoutException() throws Exception {
        assertThrows(TimeoutException.class, () -> {
            mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG, () -> {
                Thread.sleep(1000); // test will not wait for this timeout
                return 42;
            }, /* timeout= */ 10);
        });
    }

    @Test
    public void testDoBinderTimedCall_returnCalculatedValue() throws Exception {
        assertThat(mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG, () -> 42,
                /* timeout= */ 1000)).isEqualTo(42);
    }

    @Test
    public void testDoBinderCallTimeoutCrash_returnCalculatedValue() throws Exception {
        assertThat(
                mCarOemProxyServiceHelper.doBinderCallWithTimeoutCrash(CALLER_TAG, () -> 42).get())
                        .isEqualTo(42);
    }

    @Test
    public void testDoBinderCallTimeoutCrash_withCrash() throws Exception {
        doThrow(new IllegalStateException()).when(() -> Process.killProcess(anyInt()));

        mockCallTimeout(/* timeoutMs= */ 10);
        CarOemProxyServiceHelper carOemProxyServiceHelper = new CarOemProxyServiceHelper(mContext);

        assertThrows(IllegalStateException.class, () -> {
            carOemProxyServiceHelper.doBinderCallWithTimeoutCrash(CALLER_TAG, () -> {
                Thread.sleep(1000); // test will not wait for this timeout
                return 42;
            });
        });
    }

    @Test
    public void testDoBinderCallTimeoutCrash_withExecutionException() throws Exception {
        assertThat(mCarOemProxyServiceHelper.doBinderCallWithTimeoutCrash(CALLER_TAG, () -> {
            if (true) {
                throw new RemoteException();
            }
            return 42;
        }).isEmpty()).isTrue();
    }

    @Test
    public void testDoBinderCallWithDefaultValueAndDelayedWaitAndCrash() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        int delayFromOemCallMs  = 100;
        CallbackForDelayedResult<String> callback = (result) -> latch.countDown();

        Optional<String> result = mCarOemProxyServiceHelper
                .doBinderCallWithDefaultValueAndDelayedWaitAndCrash(
                        CALLER_TAG,
                        () -> {
                            Thread.sleep(delayFromOemCallMs);
                            return "result";
                        },
                        /* defaultTimeoutMs= */ 10,
                        callback);
        assertThat(result.isEmpty()).isTrue();
        JavaMockitoHelper.await(latch, 1000); //latch would be waiting at max delayFromOemCallMs
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    public void testCrashCarService() {
        doThrow(new IllegalStateException()).when(() -> Process.killProcess(anyInt()));

        assertThrows(IllegalStateException.class,
                () -> mCarOemProxyServiceHelper.crashCarService(""));
    }

    @Test
    public void testCircularCallSingleCaller() throws Exception {
        doThrow(new IllegalStateException()).when(() -> Process.killProcess(anyInt()));

        CountDownLatch latch = new CountDownLatch(
                CarOemProxyServiceHelper.MAX_CIRCULAR_CALLS_PER_CALLER);
        CountDownLatch latchForReleasing = new CountDownLatch(1);
        Callable<Integer> callable = () -> {
            latch.countDown();
            latchForReleasing.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return 42;
        };

        // set test process pid as OEM pid
        mCarOemProxyServiceHelper.updateOemPid(Process.myPid());
        // No exception till this allowable circular call
        for (int i = 0; i < CarOemProxyServiceHelper.MAX_CIRCULAR_CALLS_PER_CALLER; i++) {
            String threadName = "testCircularCallSingleCaller-" + i;
            new Thread(() -> {
                try {
                    mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG, callable,
                            /* timeoutMs= */ 10000);
                } catch (TimeoutException e) {
                }
            }, threadName).start();
        }
        try {

            // wait for all threads to queue operation
            assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

            assertThrows(IllegalStateException.class, () -> {
                mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG, callable,
                        /* timeoutMs= */ 3000);
            });
        } finally {
            latchForReleasing.countDown();
        }
    }

    @Test
    public void testCircularCallMultipleCaller() throws Exception {
        doThrow(new IllegalStateException()).when(() -> Process.killProcess(anyInt()));

        CountDownLatch latch = new CountDownLatch(
                CarOemProxyServiceHelper.MAX_CIRCULAR_CALL_TOTAL);
        CountDownLatch latchForReleasing = new CountDownLatch(1);

        Callable<Integer> callable = () -> {
            latch.countDown();
            latchForReleasing.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return 42;
        };

        // set test process pid as OEM pid
        mCarOemProxyServiceHelper.updateOemPid(Process.myPid());

        // No exception till this allowable circular call
        for (int i = 0; i < CarOemProxyServiceHelper.MAX_CIRCULAR_CALL_TOTAL; i++) {
            String threadName = "testCircularCallMultipleCaller-" + i;
            new Thread(() -> {
                try {
                    String callerTagId = Integer.toString((new Random()).nextInt());
                    mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG + callerTagId,
                            callable,
                            /* timeoutMs= */ 10000);
                } catch (TimeoutException e) {
                }
            }, threadName).start();
        }

        try {
            assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

            assertThrows(IllegalStateException.class, () -> {
                mCarOemProxyServiceHelper.doBinderTimedCallWithTimeout(CALLER_TAG, callable,
                        /* timeoutMs= */ 30000);
            });
        } finally {
            latchForReleasing.countDown();
        }
    }

    private void mockCallTimeout(int timeoutMs) {
        when(mResources.getInteger(R.integer.config_oemCarService_regularCall_timeout_ms))
                .thenReturn(timeoutMs);
        when(mResources.getInteger(R.integer.config_oemCarService_crashCall_timeout_ms))
                .thenReturn(timeoutMs);
    }
}
