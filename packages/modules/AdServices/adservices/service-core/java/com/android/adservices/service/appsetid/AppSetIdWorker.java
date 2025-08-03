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

package com.android.adservices.service.appsetid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.AdServicesCommon.ACTION_APPSETID_PROVIDER_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID;

import android.adservices.appsetid.GetAppSetIdResult;
import android.adservices.appsetid.IAppSetIdProviderService;
import android.adservices.appsetid.IGetAppSetIdCallback;
import android.adservices.appsetid.IGetAppSetIdProviderCallback;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.content.Context;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle AppSetId API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class AppSetIdWorker {
    // Singleton instance of the AppSetIdWorker.
    private static volatile AppSetIdWorker sAppSetIdWorker;
    private static final String APPSETID_DEFAULT = "00000000-0000-0000-0000-000000000000";

    private final Context mContext;
    private final Flags mFlags;
    private final ServiceBinder<IAppSetIdProviderService> mServiceBinder;

    // @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public AppSetIdWorker(Context context, Flags flags) {
        mContext = context;
        mFlags = flags;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        ACTION_APPSETID_PROVIDER_SERVICE,
                        IAppSetIdProviderService.Stub::asInterface);
    }

    /**
     * Gets an instance of AppSetIdWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static AppSetIdWorker getInstance() {
        if (sAppSetIdWorker == null) {
            synchronized (AppSetIdWorker.class) {
                if (sAppSetIdWorker == null) {
                    sAppSetIdWorker =
                            new AppSetIdWorker(
                                    ApplicationContextSingleton.get(), FlagsFactory.getFlags());
                }
            }
        }
        return sAppSetIdWorker;
    }

    @NonNull
    @VisibleForTesting
    IAppSetIdProviderService getService() {
        IAppSetIdProviderService service = mServiceBinder.getService();
        return service;
    }

    @NonNull
    private void unbindFromService() {
        mServiceBinder.unbindFromService();
    }

    /**
     * Get appSetId for the specified app and sdk.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    @NonNull
    public void getAppSetId(
            @NonNull String packageName, int appUid, @NonNull IGetAppSetIdCallback callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(callback);
        LogUtil.v("AppSetIdWorker.getAppSetId for %s, %d", packageName, appUid);
        final IAppSetIdProviderService service = getService();

        // Unable to find appSetId provider service. Return default values.
        if (service == null) {
            GetAppSetIdResult result =
                    new GetAppSetIdResult.Builder()
                            .setStatusCode(STATUS_SUCCESS)
                            .setErrorMessage("")
                            .setAppSetId(APPSETID_DEFAULT)
                            .setAppSetIdScope(0)
                            .build();
            try {
                callback.onResult(result);
            } catch (RemoteException e) {
                LogUtil.e("RemoteException");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
            } finally {
                return;
            }
        }

        try {
            // Call appSetId provider service method to retrieve the appsetid and lat.
            service.getAppSetId(
                    appUid,
                    packageName,
                    new IGetAppSetIdProviderCallback.Stub() {
                        @Override
                        public void onResult(GetAppSetIdResult resultParcel) {
                            GetAppSetIdResult result =
                                    new GetAppSetIdResult.Builder()
                                            .setStatusCode(resultParcel.getStatusCode())
                                            .setErrorMessage(resultParcel.getErrorMessage())
                                            .setAppSetId(resultParcel.getAppSetId())
                                            .setAppSetIdScope(resultParcel.getAppSetIdScope())
                                            .build();
                            try {
                                callback.onResult(result);
                            } catch (RemoteException e) {
                                LogUtil.e("RemoteException");
                                ErrorLogUtil.e(
                                        e,
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the appSetId provider service.
                                unbindFromService();
                            }
                        }

                        // TODO(b/296928283): Handle the Security Exception thrown from Provider
                        // properly.
                        @Override
                        public void onError(String errorMessage) {
                            try {
                                LogUtil.e(
                                        "Get AppSetId Error Message from Provider: %s",
                                        errorMessage);
                                callback.onError(STATUS_INTERNAL_ERROR);
                            } catch (RemoteException e) {
                                LogUtil.e("RemoteException");
                                ErrorLogUtil.e(
                                        e,
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the appSetId provider service.
                                unbindFromService();
                            }
                        }
                    });

        } catch (RemoteException e) {
            LogUtil.e(e, "RemoteException");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__APP_SET_ID);
            try {
                callback.onError(STATUS_INTERNAL_ERROR);
            } catch (RemoteException err) {
                LogUtil.e("RemoteException");
            } finally {
                unbindFromService();
            }
        }
    }
}
