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

package com.android.adservices.service.extdata;

import static android.adservices.extdata.AdServicesExtDataStorageService.AdServicesExtDataFieldId;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.extdata.AdServicesExtDataParams;
import android.adservices.extdata.AdServicesExtDataStorageService;
import android.adservices.extdata.GetAdServicesExtDataResult;
import android.adservices.extdata.IAdServicesExtDataStorageService;
import android.adservices.extdata.IGetAdServicesExtDataCallback;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Worker class that binds and interacts with {@link AdServicesExtDataStorageService}. All API calls
 * in this class are performed asynchronously.
 */
public final class AdServicesExtDataStorageServiceWorker {
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile AdServicesExtDataStorageServiceWorker sExtDataWorker;

    private final ServiceBinder<IAdServicesExtDataStorageService> mServiceBinder;

    private AdServicesExtDataStorageServiceDebugProxy mDebugProxy;

    private AdServicesExtDataStorageServiceWorker(Context context) {
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        ApplicationContextSingleton.get(),
                        AdServicesExtDataStorageService.SERVICE_INTERFACE,
                        IAdServicesExtDataStorageService.Stub::asInterface);
        mDebugProxy = AdServicesExtDataStorageServiceDebugProxy.getInstance(context);
    }

    /** Gets a singleton instance of {@link AdServicesExtDataStorageServiceWorker}. */
    @NonNull
    public static AdServicesExtDataStorageServiceWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sExtDataWorker == null) {
            synchronized (SINGLETON_LOCK) {
                if (sExtDataWorker == null) {
                    sExtDataWorker = new AdServicesExtDataStorageServiceWorker(context);
                }
            }
        }
        return sExtDataWorker;
    }

    /**
     * Calls {@link AdServicesExtDataStorageService} to fetch AdExt data.
     *
     * @param callback to return result.
     */
    public void getAdServicesExtData(
            @NonNull AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback) {
        Objects.requireNonNull(callback);

        IAdServicesExtDataStorageService service = getService();
        if (service == null) {
            if (FlagsFactory.getFlags().getEnableAdExtServiceDebugProxy()) {
                LogUtil.d(
                        "AdExt Service is null and enable debug proxy flag is true, "
                                + "getting data via proxy");
                Objects.requireNonNull(mDebugProxy);
                mDebugProxy.getAdServicesExtData(callback);
                return;
            }
            setCallbackOnError(
                    callback,
                    new IllegalStateException("Unable to find AdServicesExtDataStorageService!"),
                    /* shouldUnbind= */ false);
            return;
        }

        try {
            service.getAdServicesExtData(constructCallback(callback));
        } catch (Exception e) {
            setCallbackOnError(callback, e, /* shouldUnbind= */ true);
        }
    }

    /**
     * Calls {@link AdServicesExtDataStorageService} to update the desired fields.
     *
     * @param params data object that holds the values to be updated.
     * @param fieldsToUpdate explicit list of field IDs that correspond to the fields from params,
     *     that are to be updated.
     * @param callback callback to return result for confirming status of operation.
     */
    public void setAdServicesExtData(
            @NonNull AdServicesExtDataParams params,
            @NonNull @AdServicesExtDataFieldId int[] fieldsToUpdate,
            @NonNull AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(fieldsToUpdate);
        Objects.requireNonNull(callback);

        IAdServicesExtDataStorageService service = getService();
        if (service == null) {
            if (FlagsFactory.getFlags().getEnableAdExtServiceDebugProxy()) {
                LogUtil.d(
                        "AdExt Service is null and enable debug proxy flag is true putting data "
                                + "via debug proxy");
                Objects.requireNonNull(mDebugProxy);
                mDebugProxy.setAdServicesExtData(params, fieldsToUpdate, callback);
                return;
            }
            setCallbackOnError(
                    callback,
                    new IllegalStateException("Unable to find AdServicesExtDataStorageService!"),
                    /* shouldUnbind= */ false);
            return;
        }

        try {
            service.putAdServicesExtData(params, fieldsToUpdate, constructCallback(callback));
        } catch (Exception e) {
            setCallbackOnError(callback, e, /* shouldUnbind= */ true);
        }
    }

    private IGetAdServicesExtDataCallback constructCallback(
            AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback) {
        return new IGetAdServicesExtDataCallback.Stub() {
            @Override
            public void onResult(GetAdServicesExtDataResult responseParcel) {
                callback.onResult(responseParcel.getAdServicesExtDataParams());
                unbindFromService();
            }

            @Override
            public void onError(String errorMessage) {
                setCallbackOnError(
                        callback, new RuntimeException(errorMessage), /* shouldUnbind= */ true);
            }
        };
    }

    private void setCallbackOnError(
            AdServicesOutcomeReceiver<AdServicesExtDataParams, Exception> callback,
            Exception exception,
            boolean shouldUnbind) {
        callback.onError(exception);
        if (shouldUnbind) {
            unbindFromService();
        }
    }

    @VisibleForTesting
    IAdServicesExtDataStorageService getService() {
        return mServiceBinder.getService();
    }

    @VisibleForTesting
    void unbindFromService() {
        mServiceBinder.unbindFromService();
    }

    @VisibleForTesting
    public void setProxy(AdServicesExtDataStorageServiceDebugProxy proxy) {
        mDebugProxy = proxy;
    }
}
