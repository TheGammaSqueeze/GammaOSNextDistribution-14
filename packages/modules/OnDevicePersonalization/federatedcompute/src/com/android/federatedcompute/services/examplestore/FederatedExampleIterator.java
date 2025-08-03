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

package com.android.federatedcompute.services.examplestore;

import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESULT;
import static android.federatedcompute.common.ClientConstants.EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR_NEXT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR__NEXT_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.aidl.IExampleStoreIteratorCallback;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Pair;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.ErrorStatusException;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder.SingleQueryRecorder;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.internal.util.Preconditions;

import com.google.common.util.concurrent.SettableFuture;
import com.google.internal.federatedcompute.v1.Code;

import java.io.Closeable;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

/**
 * Implementation of an iterator that reads data from an app-hosted example store. This class's
 * methods generally do blocking IO, the class is not thread safe, and it should not be used on the
 * main thread.
 */
public final class FederatedExampleIterator implements ExampleIterator {
    private static final String TAG = FederatedExampleIterator.class.getSimpleName();
    // TODO: replace with PH flag.
    private static final long TIMEOUT_SECS = 2L;

    private boolean mClosed;
    @Nullable private final ProxyIteratorWrapper mIteratorWrapper;
    @Nullable private IteratorResult mCurrentResult;
    private byte[] mResumptionToken;
    @Nullable private final SingleQueryRecorder mRecorder;

    private enum NextResultState {
        /**
         * It is unknown whether there is a next result or not. To know, we need to try and fetch
         * one first.
         */
        UNKNOWN,
        /**
         * It is known that the end of the iterator has been reached, and no more results will
         * become available.
         */
        END_OF_ITERATOR,
        /** It is known that another result is available. */
        RESULT_AVAILABLE,
    }

    private NextResultState mNextResultState;

    public FederatedExampleIterator(
            IExampleStoreIterator exampleStoreIterator,
            byte[] resumptionToken,
            SingleQueryRecorder recorder) {
        this.mResumptionToken = resumptionToken;
        this.mNextResultState = NextResultState.UNKNOWN;
        this.mCurrentResult = null;
        this.mClosed = false;
        this.mRecorder = recorder;
        this.mIteratorWrapper = new ProxyIteratorWrapper(exampleStoreIterator);
    }

    @Override
    public boolean hasNext() throws InterruptedException, ErrorStatusException {
        Preconditions.checkState(!mClosed, "hasNext() called after close()");
        Preconditions.checkState(!isMainThread(), "hasNext() called on main thread");
        if (mNextResultState != NextResultState.UNKNOWN) {
            return mNextResultState == NextResultState.RESULT_AVAILABLE;
        }
        getNextResult();
        return mNextResultState == NextResultState.RESULT_AVAILABLE;
    }

    @Override
    public byte[] next() throws InterruptedException, ErrorStatusException {
        Preconditions.checkState(!mClosed, "next() called after close()");
        Preconditions.checkState(!isMainThread(), "next() called on main thread");
        if (mNextResultState == NextResultState.UNKNOWN) {
            getNextResult();
        }
        if (mNextResultState == NextResultState.END_OF_ITERATOR) {
            throw new NoSuchElementException("next() called but end of iterator reached");
        }
        byte[] result = mCurrentResult.mResultBytes;
        this.mResumptionToken = mCurrentResult.mResumptionToken;
        if (mRecorder != null) {
            mRecorder.incrementAndUpdateResumptionToken(mCurrentResult.mResumptionToken);
        }
        mCurrentResult = null;
        mNextResultState = NextResultState.UNKNOWN;
        return result;
    }

    @Override
    public void close() {
        Preconditions.checkState(!isMainThread(), "close() called on main thread");
        if (mClosed) {
            return;
        }
        mClosed = true;
        if (mIteratorWrapper != null) {
            mIteratorWrapper.close();
        }
    }

    private void getNextResult() throws InterruptedException, ErrorStatusException {
        mCurrentResult = mIteratorWrapper.next();
        if (mCurrentResult == null) {
            mNextResultState = NextResultState.END_OF_ITERATOR;
            LogUtil.d(TAG, "App example store returns null, end of iterator.");
        } else {
            mNextResultState = NextResultState.RESULT_AVAILABLE;
        }
    }

    private static final class IteratorResult {
        private final byte[] mResultBytes;
        private final byte[] mResumptionToken;

        private IteratorResult(byte[] resultBytes, byte[] resumptionToken) {
            this.mResultBytes = resultBytes;
            this.mResumptionToken = resumptionToken;
        }
    }

    private final class ProxyIteratorWrapper implements Closeable {
        private final IExampleStoreIterator mExampleStoreIterator;
        private boolean mIteratorClosed = false;
        private final FederatedExampleStoreIteratorCallback mIteratorCallback =
                new FederatedExampleStoreIteratorCallback();

        private ProxyIteratorWrapper(IExampleStoreIterator iterator) {
            this.mExampleStoreIterator = iterator;
        }

        private IteratorResult next() throws InterruptedException, ErrorStatusException {
            Preconditions.checkState(
                    !mIteratorClosed, "next() called after ProxyIteratorWrapper close()");
            SettableFuture<Pair<IteratorResult, Integer>> resultOrErrorCodeFuture =
                    SettableFuture.create();
            mIteratorCallback.initializeForNextResult(resultOrErrorCodeFuture);
            try {
                mExampleStoreIterator.next(mIteratorCallback);
            } catch (RemoteException e) {
                close();
                ClientErrorLogger.getInstance()
                        .logErrorWithExceptionInfo(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR_NEXT_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                throw ErrorStatusException.create(
                        Code.UNAVAILABLE_VALUE, e, "Failed to call next()");
            }

            Pair<IteratorResult, Integer> resultOrFailure;
            try {
                resultOrFailure = resultOrErrorCodeFuture.get(TIMEOUT_SECS, SECONDS);
            } catch (ExecutionException e) {
                close();
                ClientErrorLogger.getInstance()
                        .logErrorWithExceptionInfo(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR_NEXT_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                throw new IllegalStateException("Failed to get iterator result", e);
            } catch (TimeoutException e) {
                close();
                ClientErrorLogger.getInstance()
                        .logErrorWithExceptionInfo(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR__NEXT_TIMEOUT,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                throw ErrorStatusException.create(
                        Code.UNAVAILABLE_VALUE, "next() timed out (%ss)", TIMEOUT_SECS);
            }

            if (resultOrFailure.second != null) {
                close();
                ErrorStatusException e = ErrorStatusException.create(
                        Code.UNAVAILABLE_VALUE,
                        "OnIteratorNextFailure: %s",
                        resultOrFailure.second);
                ClientErrorLogger.getInstance()
                        .logErrorWithExceptionInfo(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ITERATOR_NEXT_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                throw e;
            }
            if (resultOrFailure.first == null) {
                close();
            }
            return resultOrFailure.first;
        }

        @Override
        public void close() {
            if (mIteratorClosed) {
                return;
            }
            mIteratorClosed = true;
            if (mExampleStoreIterator != null) {
                try {
                    mExampleStoreIterator.close();
                } catch (RemoteException e) {
                    LogUtil.w(TAG, e, "Exception during call to IExampleStoreIterator.close");
                    ClientErrorLogger.getInstance()
                            .logErrorWithExceptionInfo(
                                    e,
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE);
                }
            }
        }
    }

    private static final class FederatedExampleStoreIteratorCallback
            extends IExampleStoreIteratorCallback.Stub {
        private SettableFuture<Pair<IteratorResult, Integer>> mResultOrErrorCodeFuture;

        void initializeForNextResult(
                SettableFuture<Pair<IteratorResult, Integer>> resultOrErrorCodeFuture) {
            this.mResultOrErrorCodeFuture = resultOrErrorCodeFuture;
        }

        @Override
        public void onIteratorNextSuccess(Bundle result) {
            if (result == null) {
                // Reach the end of data collection.
                mResultOrErrorCodeFuture.set(Pair.create(null, null));
                return;
            }
            byte[] example = result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESULT);
            if (example == null) {
                // Reaches the end of data collection.
                mResultOrErrorCodeFuture.set(Pair.create(null, null));
                return;
            }

            byte[] resumptionToken = result.getByteArray(EXTRA_EXAMPLE_ITERATOR_RESUMPTION_TOKEN);
            if (resumptionToken == null) {
                resumptionToken = new byte[] {};
            }
            mResultOrErrorCodeFuture.set(
                    Pair.create(new IteratorResult(example, resumptionToken), null));
        }

        @Override
        public void onIteratorNextFailure(int errorCode) {
            mResultOrErrorCodeFuture.set(Pair.create(null, errorCode));
        }
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
