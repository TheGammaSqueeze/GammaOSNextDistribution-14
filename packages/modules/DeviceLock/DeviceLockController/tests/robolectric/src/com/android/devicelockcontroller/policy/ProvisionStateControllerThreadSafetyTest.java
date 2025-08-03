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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateControllerImpl.StateTransitionException;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
public class ProvisionStateControllerThreadSafetyTest {
    private ProvisionStateController mProvisionStateController;

    private static final int NUMBER_OF_THREADS = 100;

    @Before
    public void setUp() {
        TestDeviceLockControllerApplication testApplication =
                ApplicationProvider.getApplicationContext();
        UserParameters.setProvisionState(testApplication, UNPROVISIONED);
        DevicePolicyController policyController = testApplication.getPolicyController();
        mProvisionStateController = new ProvisionStateControllerImpl(testApplication,
                policyController, testApplication.getDeviceStateController(),
                Executors.newCachedThreadPool());
        when(policyController.enforceCurrentPolicies()).thenReturn(Futures.immediateVoidFuture());
    }

    @Test
    public void setNextStateForEvent_shouldSetStateOnlyOnce_whenMultithreading() {
        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        Collection<ListenableFuture<Void>> results = Collections.synchronizedCollection(
                new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(NUMBER_OF_THREADS);

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    fail(String.format("Test threads interrupted, exception: %s", e));
                }
                results.add(
                        mProvisionStateController.setNextStateForEvent(PROVISION_READY));
                finishLatch.countDown();
            });
        }
        // This aims to increase the possibility of a race condition by letting all the threads wait
        // for the CountDownLatch and unblock them at the same time.
        startLatch.countDown();

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            fail(String.format("Test threads interrupted, exception: %s", e));
        }
        int numOfSuccess = 0;

        for (ListenableFuture<Void> result : results) {
            try {
                Futures.getUnchecked(result);
                numOfSuccess++;
            } catch (UncheckedExecutionException e) {
                if (!e.getCause().getClass().equals(StateTransitionException.class)) {
                    throw e;
                }
                // We expect ONLY 1 thread to successfully return the next state, so
                // most of the results returned will produce a StateTransitionException as expected.
            }
        }
        assertEquals(1, numOfSuccess);
    }
}
