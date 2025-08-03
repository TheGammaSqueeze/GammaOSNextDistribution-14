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

package android.translation.cts;

import android.content.Context;
import android.icu.util.ULocale;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.translation.TranslationService;
import android.util.Log;
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationSpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.RetryableException;
import com.android.compatibility.common.util.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Implementation of {@link TranslationService} used in CTS tests.
 */
public class CtsTranslationService extends TranslationService {

    private static final String TAG = CtsTranslationService.class.getSimpleName();

    public static final String SERVICE_PACKAGE = "android.translation.cts";
    public static final String SERVICE_CLASS = CtsTranslationService.class.getSimpleName();
    public static final String SERVICE_NAME = SERVICE_PACKAGE + "/." + SERVICE_CLASS;

    private static ServiceWatcher sServiceWatcher;

    private static final TranslationReplier sTranslationReplier = new TranslationReplier();

    private final Handler mHandler;

    private final CountDownLatch mSessionDestroyedLatch = new CountDownLatch(1);
    private CountDownLatch mSessionCreatedLatch;

    private TranslationContext mTranslationContext;

    private int mLastSessionId;

    /**
     * Timeout for Translation cts.
     */
    private static final int TRANSLATION_TIMEOUT_MS = 20_000;

    public CtsTranslationService() {
        final HandlerThread handlerThread = new HandlerThread("CtsTranslationServiceWorker");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    /**
     * Resets the static state of this Service. Called before each test.
     */
    public static void resetStaticState() {
        sServiceWatcher = null;
        sTranslationReplier.reset();
    }

    @Override
    public void onConnected() {
        super.onConnected();
        Log.v(TAG, "onConnected");
        if (sServiceWatcher != null) {
            sServiceWatcher.mService = this;
            sServiceWatcher.mConnected.countDown();
        }
        mSessionCreatedLatch = new CountDownLatch(1);
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.v(TAG, "onDisconnected");
        if (sServiceWatcher != null) {
            sServiceWatcher.mService = null;
            sServiceWatcher.mDisconnected.countDown();
            sServiceWatcher = null;
        }
        mSessionCreatedLatch = null;
    }

    public void initSessionCreatedLatch() {
        mSessionCreatedLatch = new CountDownLatch(1);
    }

    public void waitForSessionCreated() throws Exception {
        if (mSessionCreatedLatch == null) {
            throw new AssertionError("Session Latch doesn't set.");
        }
        try {
            boolean reachZeroResult = mSessionCreatedLatch.await(5_000, TimeUnit.MILLISECONDS);
            if (!reachZeroResult) {
                throw new AssertionError("Session does not create.");
            }
        } finally {
            mSessionCreatedLatch = null;
        }
    }

    @Override
    public void onCreateTranslationSession(@NonNull TranslationContext translationContext,
            int sessionId, @NonNull Consumer<Boolean> callback) {
        Log.v(TAG, "onCreateTranslationSession, sessionId: " + sessionId);
        mTranslationContext = translationContext;
        mLastSessionId = sessionId;
        callback.accept(true);
        if (mSessionCreatedLatch != null) {
            mSessionCreatedLatch.countDown();
        }
    }

    @Override
    public void onFinishTranslationSession(int sessionId) {
        Log.v(TAG, "onFinishTranslationSession");
        mSessionDestroyedLatch.countDown();
        mTranslationContext = null;
    }

    @Override
    public void onTranslationRequest(@NonNull TranslationRequest request, int sessionId,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull Consumer<TranslationResponse> callback) {
        Log.v(TAG, "onTranslationRequest(" + request + ")");

        mHandler.post(() -> sTranslationReplier.handleOnTranslationRequest(getApplicationContext(),
                request, sessionId, cancellationSignal, callback));
    }

    @Override
    public void onTranslationCapabilitiesRequest(int sourceFormat, int targetFormat,
            @NonNull Consumer<Set<TranslationCapability>> callback) {
        //TODO: Implement properly with replier?
        final HashSet<TranslationCapability> capabilities = new HashSet<>();
        capabilities.add(new TranslationCapability(TranslationCapability.STATE_ON_DEVICE,
                new TranslationSpec(ULocale.ENGLISH, sourceFormat),
                new TranslationSpec(ULocale.FRENCH, targetFormat),
                /* uiTranslationEnabled= */ true, 0));
        callback.accept(capabilities);
    }

    @NonNull
    public static ServiceWatcher setServiceWatcher() {
        if (sServiceWatcher != null) {
            throw new IllegalStateException("There Can Be Only One!");
        }
        sServiceWatcher = new ServiceWatcher();
        return sServiceWatcher;
    }

    int getLastSessionId() {
        return mLastSessionId;
    }

    TranslationContext getTranslationContext() {
        return mTranslationContext;
    }

    /**
     * Wait the Translation session destroyed.
     */
    void awaitSessionDestroyed() {
        try {
            mSessionDestroyedLatch.await(TRANSLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    /**
     * Gets the {@link TranslationReplier} singleton.
     */
    public static TranslationReplier getTranslationReplier() {
        return sTranslationReplier;
    }

    /**
     * Object used to answer a
     * {@link TranslationService#onTranslationRequest(TranslationRequest, int, CancellationSignal,
     * OnTranslationResultCallback)} on behalf of a unit test method.
     */
    public static final class TranslationReplier {

        private final BlockingQueue<TranslationResponse> mResponses =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<TranslationRequest> mTranslationRequests =
                new LinkedBlockingQueue<>();

        private List<Throwable> mExceptions;
        private boolean mReportUnhandledTranslationRequest = true;

        private TranslationReplier() {
        }

        /**
         * Gets the exceptions thrown asynchronously, if any.
         */
        @Nullable
        public List<Throwable> getExceptions() {
            return mExceptions;
        }

        private void addException(@Nullable Throwable e) {
            if (e == null) return;

            if (mExceptions == null) {
                mExceptions = new ArrayList<>();
            }
            mExceptions.add(e);
        }

        /**
         * Sets the expectation for the next {@code onFillRequest}.
         */
        public TranslationReplier addResponse(@NonNull TranslationResponse response) {
            Objects.requireNonNull(response, "response should not be null");

            mResponses.add(response);
            return this;
        }
        /**
         * Gets the next translation request, in the order received.
         */
        public TranslationRequest getNextTranslationRequest() {
            TranslationRequest request;
            try {
                request = mTranslationRequests.poll(TRANSLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", e);
            }
            if (request == null) {
                final Timeout timeout = new Timeout("CONNECTION_TIMEOUT", 
                        TRANSLATION_TIMEOUT_MS, 2F, TRANSLATION_TIMEOUT_MS);
                throw new RetryableException(timeout, "onTranslationRequest() not called");
            }
            return request;
        }

        /**
         * Asserts all {@link TranslationService#onTranslationRequest(TranslationRequest, int,
         * CancellationSignal, OnTranslationResultCallback)} received by the service were properly
         * {@link #getNextTranslationRequest()} handled by the test case.
         */
        public void assertNoUnhandledTranslationRequests() {
            if (mTranslationRequests.isEmpty()) return; // Good job, test case!

            if (!mReportUnhandledTranslationRequest) {
                // Just log, so it's not thrown again on @After if already thrown on main body
                Log.d(TAG, "assertNoUnhandledFillRequests(): already reported, "
                        + "but logging just in case: " + mTranslationRequests);
                return;
            }

            mReportUnhandledTranslationRequest = false;
            throw new AssertionError(mTranslationRequests.size() + " unhandled fill requests: "
                    + mTranslationRequests);
        }

        /**
         * Gets the current number of unhandled requests.
         */
        public int getNumberUnhandledTranslationRequests() {
            return mTranslationRequests.size();
        }

        /**
         * Resets its internal state.
         */
        public void reset() {
            mResponses.clear();
            mTranslationRequests.clear();
            mExceptions = null;
            mReportUnhandledTranslationRequest = true;
        }

        private void handleOnTranslationRequest(@NonNull Context context,
                @NonNull TranslationRequest request, int sessionId,
                @NonNull CancellationSignal cancellationSignal,
                @NonNull Consumer<TranslationResponse> callback) {
            Log.d(TAG, "offering " + request);
            offer(mTranslationRequests, request, TRANSLATION_TIMEOUT_MS);
            try {
                TranslationResponse response;
                while (mResponses.peek() != null) {
                    try {
                        response = mResponses.poll(TRANSLATION_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted getting TranslationResponse: " + e);
                        Thread.currentThread().interrupt();
                        addException(e);
                        return;
                    }

                    if (response == null) {
                        Log.w(TAG, "onTranslationRequest() for " + request
                                + " received when no response was set.");
                        return;
                    }

                    Log.v(TAG, "onTranslationRequest(): response = " + response);
                    callback.accept(response);
                }
            } catch (Throwable t) {
                addException(t);
            }
        }

        /**
         * Offers an object to a queue or times out.
         *
         * @return {@code true} if the offer was accepted, {$code false} if it timed out or was
         * interrupted.
         */
        private static <T> boolean offer(BlockingQueue<T> queue, T obj, long timeoutMs) {
            boolean offered = false;
            try {
                offered = queue.offer(obj, timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.w(TAG, "interrupted offering", e);
                Thread.currentThread().interrupt();
            }
            if (!offered) {
                Log.e(TAG, "could not offer " + obj + " in " + timeoutMs + "ms");
            }
            return offered;
        }
    }

    /**
     * Helper class to monitor the state of the service.
     */
    public static final class ServiceWatcher {
        private final CountDownLatch mConnected = new CountDownLatch(1);
        private final CountDownLatch mDisconnected = new CountDownLatch(1);

        private CtsTranslationService mService;

        @NonNull
        public CtsTranslationService waitOnConnected() throws InterruptedException {
            await(mConnected, "not created");

            if (mService == null) {
                throw new IllegalStateException("not created");
            }

            return mService;
        }

        public CtsTranslationService getService() {
            return mService;
        }

        public void waitOnDisconnected() throws InterruptedException {
            await(mDisconnected, "not destroyed");
        }

        @Override
        public String toString() {
            return "mService: " + mService + " created: " + (mConnected.getCount() == 0)
                    + " destroyed: " + (mDisconnected.getCount() == 0);
        }

        private static void await(@NonNull CountDownLatch latch, @NonNull String fmt,
                @Nullable Object... args)
                throws InterruptedException {
            final boolean called = latch.await(TRANSLATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!called) {
                throw new IllegalStateException(String.format(fmt, args)
                        + " in " + TRANSLATION_TIMEOUT_MS + "ms");
            }
        }
    }
}
