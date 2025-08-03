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

package com.android.car.test;

import android.annotation.NonNull;
import android.car.hardware.property.CarPropertyManager;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TestPropertyAsyncCallback implements CarPropertyManager.GetPropertyCallback,
        CarPropertyManager.SetPropertyCallback {
    private final int mNumberOfRequests;
    private final CountDownLatch mCountDownLatch;
    private final Set<Integer> mPendingRequests;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final List<String> mTestErrors = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<CarPropertyManager.GetPropertyResult<?>> mGetResultList =
            new ArrayList<>();
    @GuardedBy("mLock")
    private final List<CarPropertyManager.SetPropertyResult> mSetResultList = new ArrayList<>();
    @GuardedBy("mLock")
    private final List<CarPropertyManager.PropertyAsyncError> mErrorList = new ArrayList<>();

    public TestPropertyAsyncCallback(Set<Integer> pendingRequests) {
        mNumberOfRequests = pendingRequests.size();
        mCountDownLatch = new CountDownLatch(mNumberOfRequests);
        mPendingRequests = pendingRequests;
    }

    @Override
    public void onSuccess(@NonNull CarPropertyManager.GetPropertyResult<?> getPropertyResult) {
        int requestId = getPropertyResult.getRequestId();
        synchronized (mLock) {
            if (!mPendingRequests.contains(requestId)) {
                mTestErrors.add("Request ID: " + requestId + " not present");
                return;
            } else {
                mGetResultList.add(getPropertyResult);
                mPendingRequests.remove(Integer.valueOf(requestId));
            }
        }
        mCountDownLatch.countDown();
    }

    @Override
    public void onSuccess(@NonNull CarPropertyManager.SetPropertyResult setPropertyResult) {
        int requestId = setPropertyResult.getRequestId();
        synchronized (mLock) {
            if (!mPendingRequests.contains(requestId)) {
                mTestErrors.add("Request ID: " + requestId + " not present");
                return;
            } else {
                mSetResultList.add(setPropertyResult);
                mPendingRequests.remove(Integer.valueOf(requestId));
            }
        }
        mCountDownLatch.countDown();
    }

    @Override
    public void onFailure(@NonNull CarPropertyManager.PropertyAsyncError propertyAsyncError) {
        int requestId = propertyAsyncError.getRequestId();
        synchronized (mLock) {
            if (!mPendingRequests.contains(requestId)) {
                mTestErrors.add("Request ID: " + requestId + " not present");
                return;
            } else {
                mErrorList.add(propertyAsyncError);
                mPendingRequests.remove(Integer.valueOf(requestId));
            }
        }
        mCountDownLatch.countDown();
    }

    public void waitAndFinish(int timeoutInMs) throws InterruptedException {
        boolean res = mCountDownLatch.await(timeoutInMs, TimeUnit.MILLISECONDS);
        synchronized (mLock) {
            if (!res) {
                int gotRequestsCount = mNumberOfRequests - mPendingRequests.size();
                mTestErrors.add(
                        "Not enough responses received before timeout "
                                + "(" + timeoutInMs
                                + "ms), expected " + mNumberOfRequests + " responses, got "
                                + gotRequestsCount);
            }
        }
    }

    public List<String> getTestErrors() {
        synchronized (mLock) {
            return new ArrayList<>(mTestErrors);
        }
    }

    public List<CarPropertyManager.GetPropertyResult<?>> getGetResultList() {
        synchronized (mLock) {
            return new ArrayList<>(mGetResultList);
        }
    }

    public List<CarPropertyManager.SetPropertyResult> getSetResultList() {
        synchronized (mLock) {
            return new ArrayList<>(mSetResultList);
        }
    }

    public List<CarPropertyManager.PropertyAsyncError> getErrorList() {
        synchronized (mLock) {
            return new ArrayList<>(mErrorList);
        }
    }
}
