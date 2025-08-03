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

package com.android.adservices.service.adid;

import android.adservices.adid.IGetAdIdCallback;
import android.adservices.common.UpdateAdIdRequest;
import android.annotation.NonNull;
import android.annotation.WorkerThread;

import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle AdId API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class AdIdWorker {
    // Singleton instance of the AdIdWorker.
    private static volatile AdIdWorker sAdIdWorker;

    private final AdIdCacheManager mAdIdCacheManager;

    @VisibleForTesting
    public AdIdWorker(@NonNull AdIdCacheManager adIdCacheManager) {
        mAdIdCacheManager = Objects.requireNonNull(adIdCacheManager);
    }

    /**
     * Gets an instance of AdIdWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static AdIdWorker getInstance() {
        if (sAdIdWorker == null) {
            synchronized (AdIdWorker.class) {
                if (sAdIdWorker == null) {
                    sAdIdWorker = new AdIdWorker(AdIdCacheManager.getInstance());
                }
            }
        }
        return sAdIdWorker;
    }

    /**
     * Gets adId for the specified app and sdk.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    public void getAdId(String packageName, int appUid, IGetAdIdCallback callback) {
        LogUtil.v("AdIdWorker.getAdId for %s, %d", packageName, appUid);

        mAdIdCacheManager.getAdId(packageName, appUid, callback);
    }

    /**
     * Updates the AdId Cache.
     *
     * @param updateAdIdRequest the request contains new AdId to update the cache.
     */
    public void updateAdId(UpdateAdIdRequest updateAdIdRequest) {
        Objects.requireNonNull(updateAdIdRequest);

        LogUtil.v("AdIdWorker.updateAdId with request: %s", updateAdIdRequest);
        mAdIdCacheManager.updateAdId(updateAdIdRequest);
    }
}
