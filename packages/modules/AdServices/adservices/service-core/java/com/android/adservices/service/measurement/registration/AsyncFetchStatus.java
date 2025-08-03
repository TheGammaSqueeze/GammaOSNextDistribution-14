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

package com.android.adservices.service.measurement.registration;



/** POJO for storing source and trigger fetcher status */
public class AsyncFetchStatus {
    public enum ResponseStatus {
        UNKNOWN,
        SUCCESS,
        SERVER_UNAVAILABLE,
        NETWORK_ERROR,
        INVALID_URL,
        HEADER_SIZE_LIMIT_EXCEEDED
    }

    public enum EntityStatus {
        UNKNOWN,
        SUCCESS,
        HEADER_MISSING,
        HEADER_ERROR,
        PARSING_ERROR,
        VALIDATION_ERROR,
        INVALID_ENROLLMENT,
        STORAGE_ERROR
    }

    private ResponseStatus mResponseStatus;

    private EntityStatus mEntityStatus;

    private long mResponseSize;

    private long mRegistrationDelay;

    private boolean mIsRedirectError;
    private int mRetryCount;
    private boolean mIsRedirectOnly;

    public AsyncFetchStatus() {
        mResponseStatus = ResponseStatus.UNKNOWN;
        mEntityStatus = EntityStatus.UNKNOWN;
        mIsRedirectError = false;
        mResponseSize = 0L;
        mRegistrationDelay = 0L;
    }

    /** Get the status of a communication with an Ad Tech server. */
    public ResponseStatus getResponseStatus() {
        return mResponseStatus;
    }

    /**
     * Set the status of a communication with an Ad Tech server.
     *
     * @param status a {@link ResponseStatus} that is used up the call stack to determine errors in
     *     the Ad tech server during source and trigger fetching.
     */
    public void setResponseStatus(ResponseStatus status) {
        mResponseStatus = status;
    }

    /** Get entity status */
    public EntityStatus getEntityStatus() {
        return mEntityStatus;
    }

    /** Set entity status */
    public void setEntityStatus(EntityStatus entityStatus) {
        mEntityStatus = entityStatus;
    }

    /** Get response header size. */
    public long getResponseSize() {
        return mResponseSize;
    }

    /** Set response header size. */
    public void setResponseSize(long responseSize) {
        mResponseSize = responseSize;
    }

    /** Get registration delay. */
    public long getRegistrationDelay() {
        return mRegistrationDelay;
    }

    /** Set registration delay. */
    public void setRegistrationDelay(long registrationDelay) {
        mRegistrationDelay = registrationDelay;
    }

    /** Get redirect error status. */
    public boolean isRedirectError() {
        return mIsRedirectError;
    }

    /** Set redirect error status. */
    public void setRedirectError(boolean isRedirectError) {
        mIsRedirectError = isRedirectError;
    }

    /** Get retry count. */
    public int getRetryCount() {
        return mRetryCount;
    }

    /** Set retry count. */
    public void setRetryCount(int retryCount) {
        mRetryCount = retryCount;
    }

    /** Get redirect status. */
    public boolean isRedirectOnly() {
        return mIsRedirectOnly;
    }

    /** Set redirect status. */
    public void setRedirectOnlyStatus(boolean isRedirectOnly) {
        mIsRedirectOnly = isRedirectOnly;
    }

    /** Returns true if request is successful. */
    public boolean isRequestSuccess() {
        return mResponseStatus == ResponseStatus.SUCCESS;
    }

    /** Returns true if request can be retried. */
    public boolean canRetry() {
        return mResponseStatus == ResponseStatus.NETWORK_ERROR
                || mResponseStatus == ResponseStatus.SERVER_UNAVAILABLE;
    }
}
