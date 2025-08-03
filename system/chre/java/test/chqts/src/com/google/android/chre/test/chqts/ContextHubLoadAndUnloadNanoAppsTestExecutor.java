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

package com.google.android.chre.test.chqts;

import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_LOAD;
import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_UNLOAD;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;

import com.google.android.utils.chre.ContextHubServiceTestHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ContextHubLoadAndUnloadNanoAppsTestExecutor {
    private static final int NUM_TEST_CYCLES = 10;
    private final ContextHubServiceTestHelper mTestHelper;

    private static class OnLoadUnloadCompleteListener
            implements ContextHubTransaction.OnCompleteListener<Void> {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public void onComplete(
                ContextHubTransaction<Void> transaction,
                ContextHubTransaction.Response<Void> response) {
            int result = response.getResult();
            String type =
                    ContextHubTransaction.typeToString(transaction.getType(), true /* upperCase */);
            assertWithMessage("%s transaction failed with error code %s", type, result)
                    .that(result)
                    .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);
            mCountDownLatch.countDown();
        }

        CountDownLatch getCountDownLatch() {
            return mCountDownLatch;
        }
    }

    private static class ContextHubClientTestCallback extends ContextHubClientCallback {
        private final AtomicInteger mNanoAppLoadedCount = new AtomicInteger(0);
        private final AtomicInteger mRemainingTransactionCount;
        private final long mExpectedNanoAppId;
        final CountDownLatch mDoneCountDownLatch = new CountDownLatch(1);

        ContextHubClientTestCallback(long expectedNanoAppId, int numOfTestCycles) {
            mExpectedNanoAppId = expectedNanoAppId;
            mRemainingTransactionCount = new AtomicInteger(numOfTestCycles * 2);
        }

        @Override
        public void onNanoAppLoaded(ContextHubClient client, long nanoAppId) {
            if (nanoAppId == mExpectedNanoAppId) {
                // After loading the nanoapp, the count must be 1
                assertThat(mNanoAppLoadedCount.incrementAndGet()).isEqualTo(1);
                // At least one remaining transaction to unload the nanoapp
                assertThat(mRemainingTransactionCount.decrementAndGet()).isGreaterThan(0);
            }
        }

        @Override
        public void onNanoAppUnloaded(ContextHubClient client, long nanoAppId) {
            if (nanoAppId == mExpectedNanoAppId) {
                // After unloading the nanoapp, the count must be back to 0
                assertThat(mNanoAppLoadedCount.decrementAndGet()).isEqualTo(0);
                // Declare the test is done after all the expected transactions are fulfilled
                if (mRemainingTransactionCount.decrementAndGet() == 0) {
                    mDoneCountDownLatch.countDown();
                }
            }
        }

        CountDownLatch getDoneCountDownLatch() {
            return mDoneCountDownLatch;
        }
    }

    public ContextHubLoadAndUnloadNanoAppsTestExecutor(
            ContextHubManager contextHubManager, ContextHubInfo contextHubInfo) {
        mTestHelper = new ContextHubServiceTestHelper(contextHubInfo, contextHubManager);
    }

    public void init() throws Exception {
        mTestHelper.init();
    }

    public void deinit() {
        mTestHelper.deinit();
    }

    /**
     * Repeatedly loads and unloads a nanoapp synchronously, and verifies that the naonapp is loaded
     * successfully.
     */
    public void loadUnloadSyncTest(NanoAppBinary nanoAppBinary) throws Exception {
        List<Long> nanoAppIds = Collections.singletonList(nanoAppBinary.getNanoAppId());
        for (int i = 0; i < NUM_TEST_CYCLES; i++) {
            mTestHelper.loadNanoAppAssertSuccess(nanoAppBinary);
            mTestHelper.assertNanoAppsLoaded(nanoAppIds);

            mTestHelper.unloadNanoAppAssertSuccess(nanoAppBinary.getNanoAppId());
            mTestHelper.assertNanoAppsNotLoaded(nanoAppIds);
        }
    }

    /**
     * Repeatedly loads and unloads a nanoapp asynchronously, and verifies that the naonapp is
     * loaded successfully.
     */
    public void loadUnloadAsyncTest(NanoAppBinary nanoAppBinary) throws Exception {
        List<Long> nanoAppIds = Collections.singletonList(nanoAppBinary.getNanoAppId());
        ContextHubTransaction<Void> transaction;
        for (int i = 0; i < NUM_TEST_CYCLES; i++) {
            transaction = mTestHelper.loadNanoApp(nanoAppBinary);
            waitForCompleteAsync(transaction);
            mTestHelper.assertNanoAppsLoaded(nanoAppIds);

            transaction = mTestHelper.unloadNanoApp(nanoAppBinary.getNanoAppId());
            waitForCompleteAsync(transaction);
            mTestHelper.assertNanoAppsLoaded(Collections.emptyList());
        }
    }

    /** Load and unload 2 nanoapps concurrently and verify that we can find them through a query. */
    public void loadUnloadConcurrentTest(
            NanoAppBinary nanoAppBinary1, NanoAppBinary nanoAppBinary2, int numOfTestCycles)
            throws InterruptedException, TimeoutException {
        long nanoAppId1 = nanoAppBinary1.getNanoAppId();
        long nanoAppId2 = nanoAppBinary2.getNanoAppId();
        List<Long> nanoAppIdList = Arrays.asList(nanoAppId1, nanoAppId2);
        List<Runnable> loadRunnables =
                Arrays.asList(
                        () -> mTestHelper.loadNanoAppAssertSuccess(nanoAppBinary1),
                        () -> mTestHelper.loadNanoAppAssertSuccess(nanoAppBinary2));
        List<Runnable> unloadRunnables =
                Arrays.asList(
                        () -> mTestHelper.unloadNanoAppAssertSuccess(nanoAppId1),
                        () -> mTestHelper.unloadNanoAppAssertSuccess(nanoAppId2));

        for (int i = 0; i < numOfTestCycles; i++) {
            mTestHelper.runConcurrentTasks(
                    loadRunnables, 2 * TIMEOUT_SECONDS_LOAD, TimeUnit.SECONDS);
            mTestHelper.assertNanoAppsLoaded(nanoAppIdList);

            mTestHelper.runConcurrentTasks(
                    unloadRunnables, 2 * TIMEOUT_SECONDS_UNLOAD, TimeUnit.SECONDS);
            mTestHelper.assertNanoAppsNotLoaded(nanoAppIdList);
        }
    }

    /**
     * Starts multiple load and unload transactions asynchronously (queued up at the service), and
     * verifies all transactions succeed.
     */
    public void queuedLoadUnloadTest(NanoAppBinary nanoAppBinary, int numOfTestCycles)
            throws InterruptedException {
        ContextHubClientTestCallback callback =
                new ContextHubClientTestCallback(nanoAppBinary.getNanoAppId(), numOfTestCycles);
        CountDownLatch latch = callback.getDoneCountDownLatch();
        // create the client to activate the callback
        ContextHubClient client = mTestHelper.createClient(callback);

        for (int i = 0; i < numOfTestCycles; i++) {
            mTestHelper.loadNanoApp(nanoAppBinary);
            mTestHelper.unloadNanoApp(nanoAppBinary.getNanoAppId());
        }

        long timeoutThreshold = numOfTestCycles * (TIMEOUT_SECONDS_LOAD + TIMEOUT_SECONDS_UNLOAD);
        boolean isCountedDown = latch.await(timeoutThreshold, TimeUnit.SECONDS);
        assertWithMessage(
                        "Waiting for latch to count down timeout after %s seconds",
                        timeoutThreshold)
                .that(isCountedDown)
                .isTrue();

        client.close();
    }

    private void waitForCompleteAsync(ContextHubTransaction<Void> transaction)
            throws InterruptedException {
        OnLoadUnloadCompleteListener listener = new OnLoadUnloadCompleteListener();
        transaction.setOnCompleteListener(listener);
        String type =
                ContextHubTransaction.typeToString(transaction.getType(), /* upperCase= */ false);
        long timeoutThreshold =
                (transaction.getType() == ContextHubTransaction.TYPE_LOAD_NANOAPP)
                        ? TIMEOUT_SECONDS_LOAD
                        : TIMEOUT_SECONDS_UNLOAD;
        boolean isCountedDown =
                listener.getCountDownLatch().await(timeoutThreshold, TimeUnit.SECONDS);
        assertWithMessage(
                        "Waiting for %s transaction timeout after %s seconds",
                        type, timeoutThreshold)
                .that(isCountedDown)
                .isTrue();
    }
}
