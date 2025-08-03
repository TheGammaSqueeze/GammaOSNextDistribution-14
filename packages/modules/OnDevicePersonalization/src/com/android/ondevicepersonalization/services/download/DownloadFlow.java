/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.download;

import static com.android.ondevicepersonalization.services.statsd.ApiCallStats.API_SERVICE_ON_DOWNLOAD_COMPLETED;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.DownloadCompletedOutputParcel;
import android.adservices.ondevicepersonalization.DownloadInputParcel;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.JsonReader;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.download.mdd.MobileDataDownloadFactory;
import com.android.ondevicepersonalization.services.download.mdd.OnDevicePersonalizationFileGroupPopulator;
import com.android.ondevicepersonalization.services.federatedcompute.FederatedComputeServiceImpl;
import com.android.ondevicepersonalization.services.inference.IsolatedModelServiceProvider;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlow;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.PackageUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.RemoveFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mobiledatadownload.ClientConfigProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DownloadFlow implements ServiceFlow<DownloadCompletedOutputParcel> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = DownloadFlow.class.getSimpleName();
    private final String mPackageName;
    private final Context mContext;
    private OnDevicePersonalizationVendorDataDao mDao;

    @NonNull
    private IsolatedModelServiceProvider mModelServiceProvider;
    private long mStartServiceTimeMillis;
    private ComponentName mService;
    private Map<String, VendorData> mProcessedVendorDataMap;
    private long mProcessedSyncToken;

    private final Injector mInjector;
    private final FutureCallback<DownloadCompletedOutputParcel> mCallback;

    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }
    }

    public DownloadFlow(String packageName,
            Context context, FutureCallback<DownloadCompletedOutputParcel> callback) {
        mPackageName = packageName;
        mContext = context;
        mCallback = callback;
        mInjector = new Injector();
    }

    @Override
    public boolean isServiceFlowReady() {
        try {
            mStartServiceTimeMillis = mInjector.getClock().elapsedRealtime();

            Uri uri = Objects.requireNonNull(getClientFileUri());

            long syncToken = -1;
            Map<String, VendorData> vendorDataMap = null;

            SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
            try (InputStream in = fileStorage.open(uri, ReadStreamOpener.create())) {
                try (JsonReader reader = new JsonReader(new InputStreamReader(in))) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (name.equals("syncToken")) {
                            syncToken = reader.nextLong();
                        } else if (name.equals("contents")) {
                            vendorDataMap = readContentsArray(reader);
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
            } catch (IOException e) {
                sLogger.d(TAG + mPackageName + " Failed to process downloaded JSON file");
                mCallback.onFailure(e);
                return false;
            }

            if (syncToken == -1 || !validateSyncToken(syncToken)) {
                sLogger.d(TAG + mPackageName
                        + " downloaded JSON file has invalid syncToken provided");
                mCallback.onFailure(new IllegalArgumentException("Invalid syncToken provided."));
                return false;
            }

            if (vendorDataMap == null || vendorDataMap.size() == 0) {
                sLogger.d(TAG + mPackageName + " downloaded JSON file has no content provided");
                mCallback.onFailure(new IllegalArgumentException("No content provided."));
                return false;
            }

            mDao = OnDevicePersonalizationVendorDataDao.getInstance(mContext, getService(),
                    PackageUtils.getCertDigest(mContext, mPackageName));
            long existingSyncToken = mDao.getSyncToken();

            // If existingToken is greaterThan or equal to the new token, skip as there is
            // no new data.
            if (existingSyncToken >= syncToken) {
                sLogger.d(TAG + ": syncToken is not newer than existing token.");
                mCallback.onFailure(new IllegalArgumentException("SyncToken is stale."));
                return false;
            }

            mProcessedVendorDataMap = vendorDataMap;
            mProcessedSyncToken = syncToken;

            return true;
        } catch (Exception e) {
            mCallback.onFailure(e);
            return false;
        }
    }

    @Override
    public ComponentName getService() {
        if (mService != null) return mService;

        mService = ComponentName.createRelative(mPackageName,
                AppManifestConfigHelper.getServiceNameFromOdpSettings(mContext, mPackageName));
        return mService;
    }

    @Override
    public Bundle getServiceParams() {
        Bundle serviceParams = new Bundle();

        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER,
                new DataAccessServiceImpl(getService(), mContext, /* includeLocalData */ true,
                        /* includeEventData */ true));

        serviceParams.putBinder(Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new FederatedComputeServiceImpl(getService(), mContext));

        Map<String, byte[]> downloadedContent = new HashMap<>();
        for (String key : mProcessedVendorDataMap.keySet()) {
            downloadedContent.put(key, mProcessedVendorDataMap.get(key).getData());
        }

        DataAccessServiceImpl downloadedContentBinder = new DataAccessServiceImpl(
                getService(), mContext, /* remoteData */ downloadedContent,
                /* includeLocalData */ false, /* includeEventData */ false);

        serviceParams.putParcelable(Constants.EXTRA_INPUT,
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(downloadedContentBinder)
                        .build());

        serviceParams.putParcelable(Constants.EXTRA_USER_DATA,
                new UserDataAccessor().getUserData());

        mModelServiceProvider = new IsolatedModelServiceProvider();
        IIsolatedModelService modelService = mModelServiceProvider.getModelService(mContext);
        serviceParams.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, modelService.asBinder());

        return serviceParams;
    }

    @Override
    public void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {
        var unused = FluentFuture.from(runServiceFuture)
                .transform(
                        val -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_DOWNLOAD_COMPLETED,
                                    val, mInjector.getClock(), Constants.STATUS_SUCCESS,
                                    mStartServiceTimeMillis);
                            return val;
                        },
                        mInjector.getExecutor())
                .catchingAsync(
                        Exception.class,
                        e -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_DOWNLOAD_COMPLETED,
                                    /* result= */ null, mInjector.getClock(),
                                    Constants.STATUS_INTERNAL_ERROR,
                                    mStartServiceTimeMillis);
                            return Futures.immediateFailedFuture(e);
                        },
                        mInjector.getExecutor());
    }

    @Override
    public ListenableFuture<DownloadCompletedOutputParcel> getServiceFlowResultFuture(
            ListenableFuture<Bundle> runServiceFuture) {
        return FluentFuture.from(runServiceFuture)
                .transform(
                        result -> {
                            DownloadCompletedOutputParcel downloadResult =
                                    result.getParcelable(Constants.EXTRA_RESULT,
                                            DownloadCompletedOutputParcel.class);

                            List<String> retainedKeys = downloadResult.getRetainedKeys();
                            if (retainedKeys == null) {
                                // TODO(b/270710021): Determine how to correctly handle null
                                //  retainedKeys.
                                return null;
                            }

                            List<VendorData> filteredList = new ArrayList<>();
                            for (String key : retainedKeys) {
                                if (mProcessedVendorDataMap.containsKey(key)) {
                                    filteredList.add(mProcessedVendorDataMap.get(key));
                                }
                            }

                            boolean transactionResult =
                                    mDao.batchUpdateOrInsertVendorDataTransaction(filteredList,
                                            retainedKeys, mProcessedSyncToken);

                            sLogger.d(TAG + ": filter and store data completed, transaction"
                                    + " successful: "
                                    + transactionResult);

                            return downloadResult;
                        },
                        mInjector.getExecutor())
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(TAG + ": Processing failed.", e);
                            return null;
                        },
                        mInjector.getExecutor());
    }

    @Override
    public void returnResultThroughCallback(
            ListenableFuture<DownloadCompletedOutputParcel> serviceFlowResultFuture) {
        try {
            MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(mContext);
            String fileGroupName =
                    OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                            mPackageName, mContext);

            ListenableFuture<Boolean> removeFileGroupFuture =
                    FluentFuture.from(serviceFlowResultFuture)
                            .transformAsync(
                                    result -> mdd.removeFileGroup(
                                            RemoveFileGroupRequest.newBuilder()
                                                    .setGroupName(fileGroupName).build()),
                                    mInjector.getExecutor());

            Futures.addCallback(removeFileGroupFuture,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            try {
                                mCallback.onSuccess(serviceFlowResultFuture.get());
                            } catch (Exception e) {
                                mCallback.onFailure(e);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            mCallback.onFailure(t);
                        }
                    }, mInjector.getExecutor());
        } catch (Exception e) {
            mCallback.onFailure(e);
        }
    }

    @Override
    public void cleanUpServiceParams() {
        mModelServiceProvider.unBindFromModelService();
    }

    private Map<String, VendorData> readContentsArray(JsonReader reader) throws IOException {
        Map<String, VendorData> vendorDataMap = new HashMap<>();
        reader.beginArray();
        while (reader.hasNext()) {
            VendorData data = readContent(reader);
            if (data != null) {
                vendorDataMap.put(data.getKey(), data);
            }
        }
        reader.endArray();

        return vendorDataMap;
    }

    private VendorData readContent(JsonReader reader) throws IOException {
        String key = null;
        byte[] data = null;
        String encoding = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("key")) {
                key = reader.nextString();
            } else if (name.equals("data")) {
                data = reader.nextString().getBytes(StandardCharsets.UTF_8);
            } else if (name.equals("encoding")) {
                encoding = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (key == null || data == null) {
            return null;
        }
        if (encoding != null && !encoding.isBlank()) {
            if (encoding.strip().equalsIgnoreCase("base64")) {
                data = Base64.getDecoder().decode(data);
            } else if (!encoding.strip().equalsIgnoreCase("utf8")) {
                return null;
            }
        }
        return new VendorData.Builder().setKey(key).setData(data).build();
    }

    private Uri getClientFileUri() throws Exception {
        MobileDataDownload mdd = MobileDataDownloadFactory.getMdd(mContext);

        String fileGroupName =
                OnDevicePersonalizationFileGroupPopulator.createPackageFileGroupName(
                        mPackageName, mContext);

        ClientConfigProto.ClientFileGroup cfg = mdd.getFileGroup(
                        GetFileGroupRequest.newBuilder()
                                .setGroupName(fileGroupName)
                                .build())
                .get();

        if (cfg == null || cfg.getStatus() != ClientConfigProto.ClientFileGroup.Status.DOWNLOADED) {
            sLogger.d(TAG + mPackageName + " has no completed downloads.");
            mCallback.onFailure(new IllegalArgumentException("No completed downloads."));
            return null;
        }

        // It is currently expected that we will only download a single file per package.
        if (cfg.getFileCount() != 1) {
            sLogger.d(TAG + ": package : "
                    + mPackageName + " has "
                    + cfg.getFileCount() + " files in the fileGroup");
            mCallback.onFailure(new IllegalArgumentException("Invalid file count."));
            return null;
        }

        ClientConfigProto.ClientFile clientFile = cfg.getFile(0);
        return Uri.parse(clientFile.getFileUri());
    }

    private static boolean validateSyncToken(long syncToken) {
        // TODO(b/249813538) Add any additional requirements
        return syncToken % 3600 == 0;
    }
}
