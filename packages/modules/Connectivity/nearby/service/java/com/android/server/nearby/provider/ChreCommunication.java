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

package com.android.server.nearby.provider;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppMessage;
import android.hardware.location.NanoAppState;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.injector.Injector;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for setting up communication with the appropriate contexthub on the device and
 * handling nanoapp messages to / from it.
 */
public class ChreCommunication extends ContextHubClientCallback {

    public static final int INVALID_NANO_APP_VERSION = -1;

    /** Callback that receives messages forwarded from the context hub. */
    public interface ContextHubCommsCallback {
        /** Indicates whether {@link ChreCommunication} was started successfully. */
        void started(boolean success);

        /** Indicates the ContextHub has been restarted. */
        void onHubReset();

        /**
         * Indicates the given {@code nanoAppId} has been restarted. Either via code download or by
         * being enabled by CHRE.
         */
        void onNanoAppRestart(long nanoAppId);

        /** Indicates a new {@link NanoAppMessage} has been received. */
        void onMessageFromNanoApp(NanoAppMessage message);
    }

    private final Injector mInjector;
    private final Context mContext;
    private final Executor mExecutor;

    private boolean mStarted = false;
    // null when CHRE availability result has not been returned
    @Nullable private Boolean mChreSupport = null;
    private long mNanoAppVersion = INVALID_NANO_APP_VERSION;
    @Nullable private ContextHubCommsCallback mCallback;
    @Nullable private ContextHubClient mContextHubClient;
    private CountDownLatch mCountDownLatch;

    public ChreCommunication(Injector injector, Context context, Executor executor) {
        mInjector = injector;
        mContext = context;
        mExecutor = executor;
    }

    /**
     * @return {@code true} if NanoApp is available and {@code null} when CHRE availability result
     * has not been returned
     */
    @Nullable
    public Boolean available() {
        return mChreSupport;
    }

    /**
     * Starts communication with the contexthub. This will invoke {@link
     * ContextHubCommsCallback#start(boolean)} on completion.
     *
     * @param nanoAppIds - List of IDs that must have at least one match inside the chosen
     *     contexthub.
     */
    public synchronized void start(ContextHubCommsCallback callback, Set<Long> nanoAppIds) {
        ContextHubManager manager = mInjector.getContextHubManager();
        if (manager == null) {
            Log.e(TAG, "ContexHub not available in this device");
            return;
        } else {
            Log.i(TAG, "[ChreCommunication] Start ChreCommunication");
        }
        Preconditions.checkNotNull(callback);
        Preconditions.checkArgument(!nanoAppIds.isEmpty());
        if (mStarted) {
            Log.i(TAG, "ChreCommunication already started");
            this.mCallback.started(true);
            return;
        }

        // Use this to indicate whether stop was called before the transaction below
        // completes.
        mStarted = true;
        this.mCallback = callback;

        List<ContextHubInfo> contextHubs = manager.getContextHubs();

        // Make a copy of the list so we can modify it during our async callbacks (in case the code
        // is still iterating)
        List<ContextHubInfo> validContextHubs = new ArrayList<>(contextHubs);

        for (ContextHubInfo info : contextHubs) {
            ContextHubTransaction<List<NanoAppState>> transaction = manager.queryNanoApps(info);
            Log.i(TAG, "After query Nano Apps ");
            transaction.setOnCompleteListener(
                    new OnQueryCompleteListener(info, validContextHubs, nanoAppIds, manager),
                    mExecutor);
        }
    }

    /**
     * Closes the connection to the {@link ContextHub} chosen during start.
     *
     * <p>NOTE: Do not invoke any other methods on this class after this returns.
     */
    public synchronized void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        if (mContextHubClient != null) {
            mContextHubClient.close();
            mContextHubClient = null;
            mChreSupport = null;
        }
    }

    /** Sends a {@link NanoAppMessage} to Context Hub Nearby nanoapp. */
    public synchronized boolean sendMessageToNanoApp(NanoAppMessage message) {
        if (mContextHubClient == null) {
            Log.i(TAG, "Error sending message to nanoapp, contextHubClient is null");
            return false;
        }
        int result = mContextHubClient.sendMessageToNanoApp(message);
        if (result != ContextHubTransaction.RESULT_SUCCESS) {
            Log.i(
                    TAG,
                    String.format(
                            Locale.getDefault(),
                            "Error sending message to nanoapp: %s",
                            contextHubTransactionResultToString(result)));
            return false;
        }
        return true;
    }

    /**
     * Checks the Nano App version
     */
    public long queryNanoAppVersion() {
        if (mCountDownLatch == null || mCountDownLatch.getCount() == 0) {
            // already gets result from CHRE
            return mNanoAppVersion;
        }
        try {
            boolean success = mCountDownLatch.await(1, TimeUnit.SECONDS);
            if (!success) {
                Log.w(TAG, "Failed to get ContextHubTransaction result before the timeout.");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return mNanoAppVersion;
    }

    @Override
    public synchronized void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        mCallback.onMessageFromNanoApp(message);
    }

    @Override
    public synchronized void onHubReset(ContextHubClient client) {
        mCallback.onHubReset();
    }

    @Override
    public synchronized void onNanoAppLoaded(ContextHubClient client, long nanoAppId) {
        Log.i(TAG, String.format("Nanoapp ID loaded: %s", nanoAppId));
        mCallback.onNanoAppRestart(nanoAppId);
    }

    @VisibleForTesting
    static String contextHubTransactionResultToString(int result) {
        switch (result) {
            case ContextHubTransaction.RESULT_SUCCESS:
                return "RESULT_SUCCESS";
            case ContextHubTransaction.RESULT_FAILED_UNKNOWN:
                return "RESULT_FAILED_UNKNOWN";
            case ContextHubTransaction.RESULT_FAILED_BAD_PARAMS:
                return "RESULT_FAILED_BAD_PARAMS";
            case ContextHubTransaction.RESULT_FAILED_UNINITIALIZED:
                return "RESULT_FAILED_UNINITIALIZED";
            case ContextHubTransaction.RESULT_FAILED_BUSY:
                return "RESULT_FAILED_BUSY";
            case ContextHubTransaction.RESULT_FAILED_AT_HUB:
                return "RESULT_FAILED_AT_HUB";
            case ContextHubTransaction.RESULT_FAILED_TIMEOUT:
                return "RESULT_FAILED_TIMEOUT";
            case ContextHubTransaction.RESULT_FAILED_SERVICE_INTERNAL_FAILURE:
                return "RESULT_FAILED_SERVICE_INTERNAL_FAILURE";
            case ContextHubTransaction.RESULT_FAILED_HAL_UNAVAILABLE:
                return "RESULT_FAILED_HAL_UNAVAILABLE";
            default:
                return String.format(Locale.getDefault(), "UNKNOWN_RESULT value=%d", result);
        }
    }

    /**
     * Used when initializing the class to identify the appropriate {@link ContextHubInfo} to listen
     * to.
     */
    class OnQueryCompleteListener
            implements ContextHubTransaction.OnCompleteListener<List<NanoAppState>> {

        private final ContextHubInfo mQueriedContextHub;
        private final List<ContextHubInfo> mContextHubs;
        private final Set<Long> mNanoAppIds;
        private final ContextHubManager mManager;

        OnQueryCompleteListener(
                ContextHubInfo queriedContextHub,
                List<ContextHubInfo> contextHubs,
                Set<Long> nanoAppIds,
                ContextHubManager manager) {
            this.mQueriedContextHub = queriedContextHub;
            this.mContextHubs = contextHubs;
            this.mNanoAppIds = nanoAppIds;
            this.mManager = manager;
        }

        @Override
        public void onComplete(
                ContextHubTransaction<List<NanoAppState>> transaction,
                ContextHubTransaction.Response<List<NanoAppState>> response) {
            Log.i(TAG, "query nano app onComplete");
            // Ensure the class hasn't found a client already or stop hasn't been called before
            // the transaction completed to avoid messing with state.
            if (mContextHubClient != null || !mStarted) {
                return;
            }

            mCountDownLatch = new CountDownLatch(1);
            if (response.getResult() == ContextHubTransaction.RESULT_SUCCESS) {
                for (NanoAppState state : response.getContents()) {
                    long version = state.getNanoAppVersion();
                    NearbyConfiguration configuration = new NearbyConfiguration();
                    long minVersion = configuration.getNanoAppMinVersion();
                    if (version < minVersion) {
                        Log.w(TAG, String.format("Current nano app version is %s, which does not  "
                                + "meet minimum version required %s", version, minVersion));
                        continue;
                    }
                    if (mNanoAppIds.contains(state.getNanoAppId())) {
                        Log.i(
                                TAG,
                                String.format(
                                        "Found valid contexthub: %s", mQueriedContextHub.getId()));
                        mContextHubClient = mManager.createClient(mContext, mQueriedContextHub,
                                mExecutor, ChreCommunication.this);
                        mChreSupport = true;
                        mCallback.started(true);
                        mNanoAppVersion = version;
                        mCountDownLatch.countDown();
                        return;
                    }
                }
                Log.i(
                        TAG,
                        String.format(
                                "Didn't find the nanoapp on contexthub: %s",
                                mQueriedContextHub.getId()));
            } else {
                Log.e(
                        TAG,
                        String.format(
                                "Failed to communicate with contexthub: %s",
                                mQueriedContextHub.getId()));
            }

            mContextHubs.remove(mQueriedContextHub);
            mCountDownLatch.countDown();
            // If this is the last context hub response left to receive, indicate that
            // there isn't a valid context available on this device.
            if (mContextHubs.isEmpty()) {
                mCallback.started(false);
                mChreSupport = false;
            }
        }
    }
}
