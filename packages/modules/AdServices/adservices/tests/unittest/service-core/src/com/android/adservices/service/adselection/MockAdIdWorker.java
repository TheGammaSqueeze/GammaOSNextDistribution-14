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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import android.adservices.adid.AdId;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IGetAdIdCallback;
import android.annotation.NonNull;
import android.os.RemoteException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adid.AdIdWorker;

import java.util.concurrent.TimeUnit;

public class MockAdIdWorker extends AdIdWorker {

    public static final String MOCK_AD_ID = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private String mAdId;
    private boolean mIsLadEnabled;
    private int mErrorCode;
    private long mDelayMs;

    public MockAdIdWorker(AdIdCacheManager adIdCacheManager) {
        super(adIdCacheManager);
    }

    public void setResult(String adId, boolean isLatEnabled) {
        mAdId = adId;
        mIsLadEnabled = isLatEnabled;
        mErrorCode = 0;
        mDelayMs = 0L;
    }

    public void setDelay(long delayMs) {
        mDelayMs = delayMs;
    }

    public boolean getResult() {
        return AdId.ZERO_OUT.equals(mAdId) || mIsLadEnabled;
    }

    public void setError(int errCode) {
        mErrorCode = errCode;
        mAdId = null;
        mIsLadEnabled = false;
        mDelayMs = 0L;
    }

    public int getError() {
        return mErrorCode;
    }

    @Override
    public void getAdId(
            @NonNull String packageName, int appUid, @NonNull IGetAdIdCallback callback) {
        if (mAdId != null) {
            if (mDelayMs > 0) {
                try {
                    sLogger.v("Sleeping for %d", mDelayMs);
                    TimeUnit.MILLISECONDS.sleep(mDelayMs);
                } catch (InterruptedException interruptedException) {
                    throw new RuntimeException("Delay configured but cannot be executed");
                }
            }
            sLogger.v(
                    "return GetAdIdResult with adId: %s and isLatEnabled as %b",
                    mAdId, mIsLadEnabled);
            GetAdIdResult result =
                    new GetAdIdResult.Builder()
                            .setStatusCode(STATUS_SUCCESS)
                            .setErrorMessage("")
                            .setAdId(mAdId)
                            .setLatEnabled(mIsLadEnabled)
                            .build();
            try {
                callback.onResult(result);
            } catch (RemoteException exception) {
                sLogger.e(exception, "Remote exception on calling callback.onResult");
            }
        } else if (mErrorCode != 0) {
            try {
                callback.onError(mErrorCode);
            } catch (RemoteException exception) {
                sLogger.e(exception, "Remote exception on calling callback.onError");
            }
        } else {
            throw new NullPointerException("Neither result nor error are set.");
        }
    }
}
