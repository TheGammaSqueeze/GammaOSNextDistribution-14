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
package android.autofillservice.cts.testcore;

import static android.autofillservice.cts.testcore.Timeouts.CONNECTION_TIMEOUT;
import static android.autofillservice.cts.testcore.Timeouts.FILL_TIMEOUT;

import android.app.assist.AssistStructure;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.OutcomeReceiver;
import android.service.assist.classification.FieldClassificationRequest;
import android.service.assist.classification.FieldClassificationResponse;
import android.service.assist.classification.FieldClassificationService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.RetryableException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link FieldClassificationService} instrumented FieldClassificationService
 */
public class InstrumentedFieldClassificationService extends FieldClassificationService {

    private static final String TAG = InstrumentedFieldClassificationService.class.getSimpleName();
    public static final String SERVICE_PACKAGE = Helper.MY_PACKAGE;
    public static final String SERVICE_CLASS =
            InstrumentedFieldClassificationService.class.getSimpleName();

    public static final String SERVICE_NAME = SERVICE_PACKAGE + "/.testcore." + SERVICE_CLASS;

    private static final Replier sReplier = new Replier();

    // We must handle all requests in a separate thread as the service's main thread is the also
    // the UI thread of the test process and we don't want to hose it in case of failures here
    private static final HandlerThread sMyThread =
            new HandlerThread("MyInstrumentedFieldClassificationServiceThread");

    private final Handler mHandler;

    private final CountDownLatch mConnectedLatch = new CountDownLatch(1);
    private final CountDownLatch mDisconnectedLatch = new CountDownLatch(1);

    private static volatile ServiceWatcher sServiceWatcher;

    static {
        Log.i(TAG, "Starting thread " + sMyThread);
        sMyThread.start();
    }

    public InstrumentedFieldClassificationService() {
        mHandler = Handler.createAsync(sMyThread.getLooper());
        sReplier.setHandler(mHandler);
    }

    public static ServiceWatcher setServiceWatcher() {
        if (sServiceWatcher != null) {
            throw new IllegalStateException("There can be only one pcc service");
        }
        sServiceWatcher = new ServiceWatcher();
        return sServiceWatcher;
    }

    /**
     * Waits until the system calls {@link #onConnected()}.
     */
    public void waitUntilConnected() throws InterruptedException {
        await(mConnectedLatch, "not connected");
    }

    /**
     * Awaits for a latch to be counted down.
     */
    public static void await(@NonNull CountDownLatch latch, @NonNull String fmt,
            @Nullable Object... args) throws InterruptedException {
        final boolean called = latch.await(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException(String.format(fmt, args)
                + " in " + CONNECTION_TIMEOUT.ms() + "ms");
        }
    }

    @Override
    public void onClassificationRequest(
            android.service.assist.classification.FieldClassificationRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<FieldClassificationResponse, Exception> outcomeReceiver) {

        sReplier.onClassificationRequest(request.getAssistStructure(), cancellationSignal,
                outcomeReceiver);
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "onConnected(): sServiceWatcher=" + sServiceWatcher);

        if (sServiceWatcher == null) {
            Log.w(TAG, "onConnected() without a watcher");
            return;
        }

        if (sServiceWatcher.mService != null) {
            Log.w(TAG, "onConnected(): already created: " + sServiceWatcher);
            return;
        }

        sServiceWatcher.mService = this;
        sServiceWatcher.mCreated.countDown();

        if (mConnectedLatch.getCount() == 0) {
            Log.w(TAG, "already connected: " + mConnectedLatch);
        }
        mConnectedLatch.countDown();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "onDisconnected(): sServiceWatcher=" + sServiceWatcher);

        if (mDisconnectedLatch.getCount() == 0) {
            Log.w(TAG, "already disconnected: " +  mConnectedLatch);
        }
        mDisconnectedLatch.countDown();

        if (sServiceWatcher == null) {
            Log.w(TAG, "onDisconnected() without a watcher");
            return;
        }
        if (sServiceWatcher.mService == null) {
            Log.w(TAG, "onDisconnected(): no service on " + sServiceWatcher);
            return;
        }
        sServiceWatcher.mDestroyed.countDown();
        sServiceWatcher.mService = null;
        sServiceWatcher = null;
    }

    /**
     * Gets the {@link Replier} singleton.
     */
    public static Replier getReplier() {
        return sReplier;
    }

    /**
     * POJO representation of a FieldClassificationRequest
     */
    public static final class FieldClassificationRequest {
        public final AssistStructure assistStructure;
        public final CancellationSignal cancellationSignal;
        public final OutcomeReceiver<FieldClassificationResponse, Exception> outcomeReceiver;

        private FieldClassificationRequest(AssistStructure assistStructure,
                CancellationSignal cancellationSignal,
                OutcomeReceiver<FieldClassificationResponse, Exception> outcomeReceiver) {
            this.assistStructure = assistStructure;
            this.cancellationSignal = cancellationSignal;
            this.outcomeReceiver = outcomeReceiver;
        }
    }

    /**
     * Object used to answer a
     * {@link FieldClassificationService#onClassificationRequest(
     * android.service.assist.classification.FieldClassificationRequest,
     * CancellationSignal, OutcomeReceiver<FieldClassificationResponse, Exception>)}
     * on behalf of a unit test method.
     */
    public static final class Replier {
        private final BlockingQueue<CannedFieldClassificationResponse> mResponses =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<FieldClassificationRequest> mFieldClassificationRequests =
                new LinkedBlockingQueue<>();

        private Handler mHandler;

        private Replier() {
        }

        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        /**
         * Enqueue the new FieldClassification Request
         */
        public void onClassificationRequest(AssistStructure assistStructure,
                CancellationSignal cancellationSignal,
                OutcomeReceiver<FieldClassificationResponse, Exception> outcomeReceiver) {
            try {
                CannedFieldClassificationResponse response = null;
                try {
                    response = mResponses.poll(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted getting CannedResponse: " + e);
                    Thread.currentThread().interrupt();
                    return;
                }
                FieldClassificationResponse classificationResponse = response.asResponse(
                        (id) -> Helper.findNodeByResourceId(assistStructure, id));

                Log.v(TAG, "onClassificationRequest: FieldClassificationResponse = "
                        + classificationResponse);
                outcomeReceiver.onResult(classificationResponse);
            } finally {
                Helper.offer(mFieldClassificationRequests, new FieldClassificationRequest(
                        assistStructure, cancellationSignal, outcomeReceiver),
                        CONNECTION_TIMEOUT.ms());
            }
        }

        /**
         * Gets the next field classification request, in the order received.
         */
        public FieldClassificationRequest getNextFieldClassificationRequest() {
            FieldClassificationRequest request;
            try {
                request =
                    mFieldClassificationRequests.poll(FILL_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", e);
            }
            if (request == null) {
                throw new RetryableException(FILL_TIMEOUT, "onClassificationRequest() not called");
            }
            return request;
        }

        /**
         * Asserts all {@link FieldClassificationService#onClassificationRequest(
         * android.service.assist.classification.FieldClassificationRequest,
         * CancellationSignal, OutcomeReceiver<FieldClassificationResponse, Exception>)}
         * received by the service were properly {@link #getNextFieldClassificationRequest()}
         * handled by the test case.
         */
        public void assertNoUnhandledFieldClassificationRequests() {
            if (mFieldClassificationRequests.isEmpty()) return; // Good job, test case!

            throw new AssertionError(mFieldClassificationRequests.size()
                + " unhandled field classification requests: " + mFieldClassificationRequests);
        }

        public void addResponse(CannedFieldClassificationResponse response) {
            mResponses.add(response);
        }

        /**
         * Resets its internal state.
         */
        public void reset() {
            mFieldClassificationRequests.clear();
        }
    }

    public static final class ServiceWatcher {

        private final CountDownLatch mCreated = new CountDownLatch(1);
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        private InstrumentedFieldClassificationService mService;

        @NonNull
        public InstrumentedFieldClassificationService waitOnConnected()
                throws InterruptedException {
            await(mCreated, "not created");

            if (mService == null) {
                throw new IllegalStateException("not created");
            }

            return mService;
        }

        public void waitOnDisconnected() throws InterruptedException {
            await(mDestroyed, "not destroyed");
        }

        @Override
        public String toString() {
            return "mService: " + mService + " created: " + (mCreated.getCount() == 0)
                + " destroyed: " + (mDestroyed.getCount() == 0);
        }
    }
}
