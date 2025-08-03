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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.AdServicesCommon.ACTION_ADID_PROVIDER_SERVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdProviderService;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IAdIdProviderService;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.adid.IGetAdIdProviderCallback;
import android.adservices.common.UpdateAdIdRequest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

/** The class to manage AdId Cache. */
@ThreadSafe
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class AdIdCacheManager {
    @VisibleForTesting static final String SHARED_PREFS_IAPC = "adservices_PPAPI_IAPC";
    private static final String SHARE_PREFS_CACHED_AD_ID_KEY = "cached_ad_id";
    private static final String SHARE_PREFS_CACHED_LAT_KEY = "cached_lat";
    // A default AdId to indicate the AdId is not set in the cache.
    private static final AdId UNSET_AD_ID =
            new AdId(/* adId */ "", /* limitAdTrackingEnabled */ false);
    private static final Object SINGLETON_LOCK = new Object();

    private static AdIdCacheManager sSingleton;

    private final ServiceBinder<IAdIdProviderService> mServiceBinder;
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final Context mContext;

    @VisibleForTesting
    public AdIdCacheManager(Context context) {
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        ACTION_ADID_PROVIDER_SERVICE,
                        IAdIdProviderService.Stub::asInterface);
        mContext = context.getApplicationContext();
    }

    /** Gets a singleton instance of {@link AdIdCacheManager}. */
    public static AdIdCacheManager getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new AdIdCacheManager(ApplicationContextSingleton.get());
            }

            return sSingleton;
        }
    }

    /**
     * Fetches AdId from AdId cache.
     *
     * <p>If the AdId cache is empty, make a call to {@link AdIdProviderService} to fetch the AdId
     * from the device.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    public void getAdId(String packageName, int appUid, IGetAdIdCallback callback) {
        AdId adId = getAdIdInStorage();

        // If the cache is not set, get the AdId from the provider.
        if (adId.equals(UNSET_AD_ID)) {
            getAdIdFromProvider(packageName, appUid, callback);
            return;
        }

        LogUtil.v(
                "AdIdCacheManager.getAdId from cache for %s, %d, adId = %s",
                packageName, appUid, adId);

        setCallbackOnResult(callback, adId, /* shouldUnbindFromProvider= */ false);
    }

    /**
     * Updates the AdId cache.
     *
     * @param updateAdIdRequest the request contains new AdId to update the cache.
     */
    public void updateAdId(@NonNull UpdateAdIdRequest updateAdIdRequest) {
        LogUtil.v("AdIdCacheManager.updateAdId to %s", updateAdIdRequest);
        mReadWriteLock.writeLock().lock();

        try {
            setAdIdInStorage(
                    new AdId(
                            updateAdIdRequest.getAdId(),
                            updateAdIdRequest.isLimitAdTrackingEnabled()));
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Gets adId from {@link AdIdProviderService}.
     *
     * @param packageName is the app package name
     * @param appUid is the current UID of the calling app;
     * @param callback is used to return the result.
     */
    @VisibleForTesting
    void getAdIdFromProvider(String packageName, int appUid, IGetAdIdCallback callback) {
        LogUtil.v("AdIdCacheManager.getAdId for %s, %d from Provider", packageName, appUid);

        IAdIdProviderService service = getService();

        // Unable to find adId provider service. Return default values.
        if (service == null) {
            setCallbackOnResult(
                    callback,
                    new AdId(AdId.ZERO_OUT, false),
                    /* shouldUnbindFromProvider= */ false);
            return;
        }

        try {
            // Call adId provider service method to retrieve the AdId and lat.
            service.getAdIdProvider(
                    appUid,
                    packageName,
                    new IGetAdIdProviderCallback.Stub() {
                        @Override
                        public void onResult(GetAdIdResult resultParcel) {
                            AdId resultAdId =
                                    new AdId(resultParcel.getAdId(), resultParcel.isLatEnabled());

                            setAdIdInStorage(resultAdId);
                            LogUtil.v("AdId fetched from Provider is %s", resultAdId);

                            setCallbackOnResult(
                                    callback, resultAdId, /* shouldUnbindFromProvider= */ true);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            try {
                                callback.onError(STATUS_INTERNAL_ERROR);
                                LogUtil.e("Get AdId Error Message from Provider: %s", errorMessage);
                            } catch (RemoteException e) {
                                logRemoteException(e);
                            } finally {
                                // Since we are sure, the provider service api has returned,
                                // we can safely unbind the adId provider service.
                                unbindFromService();
                            }
                        }
                    });

        } catch (RemoteException e) {
            logRemoteException(e);

            try {
                callback.onError(STATUS_INTERNAL_ERROR);
            } catch (RemoteException err) {
                logRemoteException(err);
            } finally {
                unbindFromService();
            }
        }
    }

    @VisibleForTesting
    void setAdIdInStorage(@NonNull AdId adId) {
        // TODO(b/300147424): Remove the flag once the feature is rolled out.
        if (!FlagsFactory.getFlags().getAdIdCacheEnabled()) {
            LogUtil.d("Ad Id Cache is not enabled. Set nothing.");
            return;
        }
        Objects.requireNonNull(adId);

        mReadWriteLock.writeLock().lock();
        try {
            SharedPreferences.Editor editor = getSharedPreferences().edit();

            editor.putString(SHARE_PREFS_CACHED_AD_ID_KEY, adId.getAdId())
                    .putBoolean(SHARE_PREFS_CACHED_LAT_KEY, adId.isLimitAdTrackingEnabled());

            if (!editor.commit()) {
                // Since we are sure that the provider service api has returned, we can safely
                // unbind the adId provider service.
                LogUtil.e("Failed to update Ad Id Cache!");
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    AdId getAdIdInStorage() {
        // TODO(b/300147424): Remove the flag once the feature is rolled out.
        if (!FlagsFactory.getFlags().getAdIdCacheEnabled()) {
            LogUtil.d("Ad Id Cache is not enabled. Return default value.");
            return UNSET_AD_ID;
        }

        mReadWriteLock.readLock().lock();
        try {
            SharedPreferences sharedPreferences = getSharedPreferences();

            String adIdString =
                    sharedPreferences.getString(
                            SHARE_PREFS_CACHED_AD_ID_KEY, UNSET_AD_ID.getAdId());
            boolean isLatEnabled =
                    sharedPreferences.getBoolean(
                            SHARE_PREFS_CACHED_LAT_KEY, UNSET_AD_ID.isLimitAdTrackingEnabled());

            return new AdId(adIdString, isLatEnabled);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    @NonNull
    @VisibleForTesting
    IAdIdProviderService getService() {
        return mServiceBinder.getService();
    }

    @NonNull
    @VisibleForTesting
    SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(SHARED_PREFS_IAPC, Context.MODE_PRIVATE);
    }

    @NonNull
    private void unbindFromService() {
        mServiceBinder.unbindFromService();
    }

    // Logs the RemoteException to both logcat and CEL.
    private void logRemoteException(RemoteException e) {
        LogUtil.e(e, "RemoteException");
        ErrorLogUtil.e(
                e,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_REMOTE_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__AD_ID);
    }

    private void setCallbackOnResult(
            @NonNull IGetAdIdCallback callback,
            @NonNull AdId resultAdId,
            boolean shouldUnbindFromProvider) {
        Objects.requireNonNull(callback);
        Objects.requireNonNull(resultAdId);

        GetAdIdResult result =
                new GetAdIdResult.Builder()
                        .setStatusCode(STATUS_SUCCESS)
                        .setErrorMessage("")
                        .setAdId(resultAdId.getAdId())
                        .setLatEnabled(resultAdId.isLimitAdTrackingEnabled())
                        .build();

        try {
            callback.onResult(result);
        } catch (RemoteException e) {
            logRemoteException(e);
        } finally {
            if (shouldUnbindFromProvider) {
                // Since we are sure that the provider service api has returned, we can safely
                // unbind the adId provider service.
                unbindFromService();
            }
        }
    }
}
