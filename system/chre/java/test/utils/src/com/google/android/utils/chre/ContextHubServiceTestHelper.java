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

package com.google.android.utils.chre;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppState;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The helper class to facilitate CHQTS test.
 *
 * <p> A test class using this helper should run {@link #init()} before starting any test and run
 * {@link #deinit()} after any test.</p>
 */
public class ContextHubServiceTestHelper {
    private static final long TIMEOUT_SECONDS_QUERY = 5;
    public static final long TIMEOUT_SECONDS_LOAD = 30;
    public static final long TIMEOUT_SECONDS_UNLOAD = 5;
    public static final long TIMEOUT_SECONDS_MESSAGE = 1;

    private ContextHubClient mHubResetClient = null;
    private final ContextHubInfo mContextHubInfo;
    private final ContextHubManager mContextHubManager;
    private final AtomicBoolean mChreReset = new AtomicBoolean(false);

    public ContextHubServiceTestHelper(ContextHubInfo info, ContextHubManager manager) {
        mContextHubInfo = info;
        mContextHubManager = manager;
    }

    public void init() {
        // Registers a client to record a hub reset.
        registerHubResetClient();
    }

    public void initAndUnloadAllNanoApps() throws InterruptedException, TimeoutException {
        init();

        // We only need to unload all nanoapps when the device has version < U, so the
        // tests remain the same on those devices. On newer devices, test mode will
        // handle this.
        if (VERSION.SDK_INT < VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Unload all nanoapps to ensure test starts at a clean state.
            unloadAllNanoApps();
        }
    }

    public void deinit() {
        // unregister to detect any hub reset.
        unregisterHubResetClient();
    }

    /** Creates a registered callback-based ContextHubClient. */
    public ContextHubClient createClient(ContextHubClientCallback callback) {
        return mContextHubManager.createClient(mContextHubInfo, callback);
    }

    /**
     * Creates a PendingIntent-based ContextHubClient object.
     *
     * @param pendingIntent the PendingIntent object to associate with the ContextHubClient
     * @param nanoAppId     the ID of the nanoapp to receive Intent events for
     * @return the registered ContextHubClient object
     */
    public ContextHubClient createClient(PendingIntent pendingIntent, long nanoAppId) {
        return mContextHubManager.createClient(mContextHubInfo, pendingIntent, nanoAppId);
    }

    /**
     * Registers a {@link ContextHubClient} client with a callback so that a hub reset can be
     * recorded.
     *
     * <p> Caller needs to call {@link #unregisterHubResetClient()} to check if any reset happens.
     */
    public void registerHubResetClient() {
        ContextHubClientCallback callback = new ContextHubClientCallback() {
            @Override
            public void onHubReset(ContextHubClient client) {
                mChreReset.set(true);
            }
        };
        mHubResetClient = createClient(callback);
    }

    /** Unregisters the hub reset client after the test. */
    public void unregisterHubResetClient() {
        assertWithMessage("Context Hub reset unexpectedly while testing").that(
                mChreReset.get()).isFalse();
        if (mHubResetClient != null) {
            mHubResetClient.close();
            mHubResetClient = null;
        }
    }

    /** Loads a nanoapp binary asynchronously. */
    public ContextHubTransaction<Void> loadNanoApp(NanoAppBinary nanoAppBinary) {
        return mContextHubManager.loadNanoApp(mContextHubInfo, nanoAppBinary);
    }

    /**
     * Loads a nanoapp binary and asserts that it succeeded synchronously.
     *
     * @param nanoAppBinary the binary to load
     */
    public void loadNanoAppAssertSuccess(NanoAppBinary nanoAppBinary) {
        ContextHubTransaction<Void> transaction = loadNanoApp(nanoAppBinary);
        assertTransactionSuccessSync(transaction, TIMEOUT_SECONDS_LOAD, TimeUnit.SECONDS);
    }

    /** Unloads a nanoapp asynchronously. */
    public ContextHubTransaction<Void> unloadNanoApp(long nanoAppId) {
        return mContextHubManager.unloadNanoApp(mContextHubInfo, nanoAppId);
    }

    /** Unloads a nanoapp and asserts that it succeeded synchronously. */
    public void unloadNanoAppAssertSuccess(long nanoAppId) {
        ContextHubTransaction<Void> transaction = unloadNanoApp(nanoAppId);
        assertTransactionSuccessSync(transaction, TIMEOUT_SECONDS_UNLOAD, TimeUnit.SECONDS);
    }

    /** Queries for a nanoapp synchronously and returns the list of loaded nanoapps. */
    public List<NanoAppState> queryNanoApps() throws InterruptedException, TimeoutException {
        ContextHubTransaction<List<NanoAppState>> transaction = mContextHubManager.queryNanoApps(
                mContextHubInfo);

        assertTransactionSuccessSync(transaction, TIMEOUT_SECONDS_QUERY, TimeUnit.SECONDS);
        ContextHubTransaction.Response<List<NanoAppState>> response = transaction.waitForResponse(
                TIMEOUT_SECONDS_QUERY, TimeUnit.SECONDS);

        return response.getContents();
    }

    /** Unloads all nanoapps currently loaded at the Context Hub. */
    public void unloadAllNanoApps() throws InterruptedException, TimeoutException {
        List<NanoAppState> nanoAppStateList = queryNanoApps();
        for (NanoAppState state : nanoAppStateList) {
            unloadNanoAppAssertSuccess(state.getNanoAppId());
        }
    }

    /** Asserts that a list of nanoapps are loaded at the hub. */
    public void assertNanoAppsLoaded(List<Long> nanoAppIdList)
            throws InterruptedException, TimeoutException {
        StringBuilder unloadedApps = new StringBuilder();
        for (Map.Entry<Long, Boolean> entry : findNanoApps(nanoAppIdList).entrySet()) {
            if (!entry.getValue()) {
                unloadedApps.append(Long.toHexString(entry.getKey())).append(";");
            }
        }
        assertWithMessage("Did not find following nanoapps: " + unloadedApps).that(
                unloadedApps.length()).isEqualTo(0);
    }

    /** Asserts that a list of nanoapps are not loaded at the hub. */
    public void assertNanoAppsNotLoaded(List<Long> nanoAppIdList)
            throws InterruptedException, TimeoutException {
        StringBuilder loadedApps = new StringBuilder();
        for (Map.Entry<Long, Boolean> entry : findNanoApps(nanoAppIdList).entrySet()) {
            if (entry.getValue()) {
                loadedApps.append(Long.toHexString(entry.getKey())).append(";");
            }
        }
        assertWithMessage("Following nanoapps are loaded: " + loadedApps).that(
                loadedApps.length()).isEqualTo(0);
    }

    /**
     * Determines if a nanoapp is loaded at the hub using a query.
     *
     * @param nanoAppIds the list of nanoapps to verify as loaded
     * @return a boolean array populated as true if the nanoapp is loaded, false otherwise
     */
    private Map<Long, Boolean> findNanoApps(List<Long> nanoAppIds)
            throws InterruptedException, TimeoutException {
        Map<Long, Boolean> foundNanoApps = new HashMap<>();
        for (Long nanoAppId : nanoAppIds) {
            foundNanoApps.put(nanoAppId, false);
        }
        List<NanoAppState> nanoAppStateList = queryNanoApps();
        for (NanoAppState nanoAppState : nanoAppStateList) {
            Long nanoAppId = nanoAppState.getNanoAppId();
            if (foundNanoApps.containsKey(nanoAppId)) {
                assertWithMessage("Nanoapp 0x" + Long.toHexString(nanoAppState.getNanoAppId())
                        + " was found twice in query response").that(
                        foundNanoApps.get(nanoAppId)).isFalse();
                foundNanoApps.put(nanoAppId, true);
            }
        }
        return foundNanoApps;
    }

    /**
     * Waits for a result of a transaction synchronously, and asserts that it succeeded.
     *
     * @param transaction the transaction to wait on
     * @param timeout     the timeout duration
     * @param unit        the timeout unit
     */
    public void assertTransactionSuccessSync(ContextHubTransaction<?> transaction, long timeout,
            TimeUnit unit) {
        assertWithMessage("ContextHubTransaction cannot be null").that(transaction).isNotNull();
        String type = ContextHubTransaction.typeToString(transaction.getType(),
                true /* upperCase */);
        ContextHubTransaction.Response<?> response;
        try {
            response = transaction.waitForResponse(timeout, unit);
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError("Failed to get a response for " + type + " transaction", e);
        }
        assertWithMessage(
                type + " transaction failed with error code " + response.getResult()).that(
                response.getResult()).isEqualTo(ContextHubTransaction.RESULT_SUCCESS);
    }

    /**
     * Run tasks in separate threads concurrently and wait until completion.
     *
     * @param tasks   a list of tasks to start
     * @param timeout the timeout duration
     * @param unit    the timeout unit
     */
    public void runConcurrentTasks(List<Runnable> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (Runnable task : tasks) {
            executorService.submit(task);
        }
        executorService.shutdown();
        boolean unused = executorService.awaitTermination(timeout, unit);
    }
}
