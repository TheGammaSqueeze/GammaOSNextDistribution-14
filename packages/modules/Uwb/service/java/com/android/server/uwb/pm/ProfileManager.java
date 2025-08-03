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

package com.android.server.uwb.pm;

import static com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo.ADF_STATUS_CREATED;
import static com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo.ADF_STATUS_NOT_PROVISIONED;
import static com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo.ADF_STATUS_PROVISIONED;

import static com.google.uwb.support.fira.FiraParams.PACS_PROFILE_SERVICE_ID;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.util.Log;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;

import androidx.annotation.NonNull;

import com.android.server.uwb.UwbConfigStore;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.ServiceProfileData;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.secure.SecureFactory;
import com.android.server.uwb.secure.provisioning.ProvisioningManager;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.uwb.support.fira.FiraParams.ServiceID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProfileManager {

    private static final String LOG_TAG = "UwbServiceProfileStore";

    public final Map<UUID, ServiceProfileInfo> mServiceProfileMap =
            new HashMap<>();

    public final Map<Integer, List<ServiceProfileInfo>> mAppServiceProfileMap =
            new HashMap<>();

    public final Map<SessionHandle, RangingSessionController> mRangingSessionTable =
            new HashMap<>();

    private static final int MAX_RETRIES = 10;

    private final Context mContext;
    private final Handler mHandler;
    private final UwbConfigStore mUwbConfigStore;
    private final UwbInjector mUwbInjector;

    private boolean mHasNewDataToSerialize = false;


    public ProfileManager(@NonNull Context context, @NonNull Handler handler, @NonNull
             UwbConfigStore uwbConfigStore, UwbInjector uwbInjector) {
        mContext = context;
        mHandler = handler;
        mUwbConfigStore = uwbConfigStore;
        mUwbInjector = uwbInjector;
        mUwbConfigStore.registerStoreData(mUwbInjector
                .makeServiceProfileData(new ServiceProfileStoreData()));
    }

    private class ServiceProfileStoreData implements
            ServiceProfileData.DataSource {

        @Override
        public Map<UUID, ServiceProfileInfo> toSerialize() {
            mHasNewDataToSerialize = false;
            return mServiceProfileMap;
        }

        @Override
        public void fromDeserialized(Map<UUID, ServiceProfileInfo> serviceProfileDataMap) {
            loadServiceProfile(serviceProfileDataMap);
        }

        @Override
        public void reset() {
            mServiceProfileMap.clear();
            mAppServiceProfileMap.clear();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }
    }

    /** Check whether profile manager has an instance of SessionHandle */
    public boolean hasSession(SessionHandle sessionHandle) {
        return mRangingSessionTable.containsKey(sessionHandle);
    }

    public Optional<UUID> addServiceProfile(@ServiceID int serviceID) {
        UUID serviceInstanceID;
        int app_uid = Binder.getCallingUid();
        int num_tries = 0;
        do {
            serviceInstanceID = UUID.randomUUID();
            num_tries++;
        } while(mServiceProfileMap.containsKey(serviceInstanceID) && num_tries < MAX_RETRIES);

        if (num_tries == MAX_RETRIES) {
            return Optional.empty();
        }
        ServiceProfileInfo serviceProfileInfo = new ServiceProfileInfo(serviceInstanceID,
                Binder.getCallingUid(), mContext.getPackageName(), serviceID);
        mServiceProfileMap.put(serviceInstanceID, serviceProfileInfo);
        if (mAppServiceProfileMap.containsKey(app_uid)) {
            List<ServiceProfileInfo> appServiceProfileList = mAppServiceProfileMap.get(app_uid);
            appServiceProfileList.add(serviceProfileInfo);
            mAppServiceProfileMap.put(app_uid, appServiceProfileList);
        } else {
            List<ServiceProfileInfo> appServiceProfileList = new ArrayList();
            appServiceProfileList.add(serviceProfileInfo);
            mAppServiceProfileMap.put(app_uid, appServiceProfileList);
        }
        mHasNewDataToSerialize = true;
        mHandler.post(() -> mUwbConfigStore.saveToStore(true));
        return Optional.of(serviceInstanceID);
    }

    /** Remove existing service profile from profile manager */
    public int removeServiceProfile(UUID serviceInstanceID) {
        int app_uid = Binder.getCallingUid();
        if (mServiceProfileMap.containsKey(serviceInstanceID)) {
            ServiceProfileInfo serviceProfileInfo = mServiceProfileMap.get(serviceInstanceID);
            mServiceProfileMap.remove(serviceInstanceID);

            if (mAppServiceProfileMap.containsKey(app_uid)) {
                List<ServiceProfileInfo> appServiceProfileList =
                        mAppServiceProfileMap.get(app_uid);
                appServiceProfileList.remove(serviceProfileInfo);
                if (appServiceProfileList.isEmpty()) {
                    mAppServiceProfileMap.remove(app_uid);
                } else {
                    mAppServiceProfileMap.put(app_uid, appServiceProfileList);
                }
            }
        }
        else {
            return UwbUciConstants.STATUS_CODE_FAILED;
        }
        mHandler.post(() -> mUwbConfigStore.saveToStore(true));
        return UwbUciConstants.STATUS_CODE_OK;
    }

    /**
     * Create ADF, provision created ADF, import ADF or delete ADF with the CMS signed,
     * encrypted script.
     */

    public void provisioningAdf(@NonNull UUID serviceInstanceId, @NonNull byte[] script,
            AdfOpCallback adfOpCallback) {
        ServiceProfileInfo serviceProfileInfo = mServiceProfileMap.get(serviceInstanceId);
        if (serviceProfileInfo == null) {
            Log.e(LOG_TAG, "service profile info is not available, is it initialized?");
            adfOpCallback.onFailure(serviceInstanceId);
            return;
        }

        ProvisioningManager provisioningManager =
                SecureFactory.makeProvisioningManager(mContext, mHandler.getLooper());

        provisioningManager.provisioningAdf(serviceInstanceId, script,
                new ProvisioningManager.ProvisioningCallback() {
                    @Override
                    public void onAdfCreated(@NonNull UUID serviceInstanceId,
                            @androidx.annotation.NonNull ObjectIdentifier adfOid) {
                        tryToCleanUpStaleAdfOnNewAdfAvailable(
                                serviceInstanceId, serviceProfileInfo, adfOid);
                        serviceProfileInfo.setServiceAdfOid(adfOid);
                        serviceProfileInfo.setAdfStatus(ADF_STATUS_CREATED);
                        mHandler.post(() -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
                        adfOpCallback.onSuccess(serviceInstanceId, adfOid, AdfOp.CREATE_ADF);
                    }

                    @Override
                    public void onAdfProvisioned(
                            @NonNull UUID serviceInstanceId,
                            @NonNull ObjectIdentifier adfOid) {
                        if (serviceProfileInfo.getServiceAdfOid().isEmpty()
                                || !adfOid.equals(serviceProfileInfo.getServiceAdfOid().get())) {
                            Log.e(LOG_TAG,
                                    "something wrong, the ADF wasn't created before provisioning");
                            serviceProfileInfo.setServiceAdfOid(adfOid);
                        }
                        serviceProfileInfo.setAdfStatus(ADF_STATUS_PROVISIONED);
                        mHandler.post(() -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
                        adfOpCallback.onSuccess(serviceInstanceId, adfOid, AdfOp.PROVISIONING_ADF);
                    }

                    @Override
                    public void onAdfImported(@NonNull UUID serviceInstanceId,
                            @NonNull ObjectIdentifier adfOid,
                            @NonNull byte[] secureBlob) {
                        tryToCleanUpStaleAdfOnNewAdfAvailable(
                                serviceInstanceId, serviceProfileInfo, adfOid);
                        serviceProfileInfo.setServiceAdfOid(adfOid);
                        serviceProfileInfo.setSecureBlob(secureBlob);
                        serviceProfileInfo.setAdfStatus(ADF_STATUS_PROVISIONED);
                        mHandler.post(() -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
                        adfOpCallback.onSuccess(serviceInstanceId, adfOid, AdfOp.IMPORT_ADF);
                    }

                    @Override
                    public void onAdfDeleted(@NonNull UUID serviceInstanceId,
                            @NonNull ObjectIdentifier adfOid) {
                        serviceProfileInfo.setServiceAdfOid(null);
                        serviceProfileInfo.setAdfStatus(ADF_STATUS_NOT_PROVISIONED);
                        mHandler.post(() -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
                        adfOpCallback.onSuccess(serviceInstanceId, adfOid, AdfOp.DELETE_ADF);
                    }

                    @Override
                    public void onFail(@androidx.annotation.NonNull UUID serviceInstanceId) {
                        adfOpCallback.onFailure(serviceInstanceId);
                    }
                });
    }

    private void tryToCleanUpStaleAdfOnNewAdfAvailable(UUID serviceInstanceId,
            ServiceProfileInfo serviceProfileInfo,
            ObjectIdentifier newAdfOid) {
        if (serviceProfileInfo.getServiceAdfOid().isEmpty()
                || newAdfOid.equals(serviceProfileInfo.getServiceAdfOid().get())) {
            return;
        }
        Log.w(LOG_TAG, "The old ADF should be deleted, only 1 ADF is allowed.");
        if (serviceProfileInfo.getSecureBlob().isPresent()) {
            serviceProfileInfo.setServiceAdfOid(null);
            serviceProfileInfo.setSecureBlob(null);
            return;
        }

        ProvisioningManager provisioningManager =
                SecureFactory.makeProvisioningManager(mContext, mHandler.getLooper());
        // overwrite anyway
        serviceProfileInfo.setServiceAdfOid(null);
        provisioningManager.deleteAdf(serviceInstanceId,
                serviceProfileInfo.getServiceAdfOid().get(),
                new ProvisioningManager.DeleteAdfCallback() {
                    @Override
                    public void onSuccess(UUID serviceInstanceId, ObjectIdentifier adfOid) {
                        Log.d(LOG_TAG, "old ADF is deleted.");
                    }

                    @Override
                    public void onFail(UUID serviceInstanceId, ObjectIdentifier adfOid) {
                        // TODO: add the adfOid in a garbage queue, remove later.
                        Log.e(LOG_TAG, "old ADF is not deleted.");
                    }
                });
    }

    /** Deletes the ADF associated with the service instance. */
    public void deleteAdf(UUID serviceInstanceId, AdfOpCallback adfOpCallback) {
        ServiceProfileInfo serviceProfileInfo = mServiceProfileMap.get(serviceInstanceId);
        if (serviceProfileInfo == null || serviceProfileInfo.getServiceAdfOid().isEmpty()) {
            adfOpCallback.onFailure(serviceInstanceId);
            return;
        }
        if (serviceProfileInfo.getSecureBlob().isPresent()) {
            serviceProfileInfo.setServiceAdfOid(null);
            serviceProfileInfo.setSecureBlob(null);
            serviceProfileInfo.setAdfStatus(ADF_STATUS_NOT_PROVISIONED);
            mHandler.post(() -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
            adfOpCallback.onSuccess(serviceInstanceId,
                    serviceProfileInfo.getServiceAdfOid().get(), AdfOp.DELETE_ADF);
        } else {
            ProvisioningManager provisioningManager =
                    SecureFactory.makeProvisioningManager(mContext, mHandler.getLooper());
            provisioningManager.deleteAdf(serviceInstanceId,
                    serviceProfileInfo.getServiceAdfOid().get(),
                    new ProvisioningManager.DeleteAdfCallback() {
                        @Override
                        public void onSuccess(UUID serviceInstanceId, ObjectIdentifier adfOid) {
                            serviceProfileInfo.setServiceAdfOid(null);
                            serviceProfileInfo.setAdfStatus(ADF_STATUS_NOT_PROVISIONED);
                            mHandler.post(
                                    () -> mUwbConfigStore.saveToStore(/* forceWrite= */ true));
                            adfOpCallback.onSuccess(serviceInstanceId, adfOid, AdfOp.DELETE_ADF);
                        }

                        @Override
                        public void onFail(UUID serviceInstanceId, ObjectIdentifier adfOid) {
                            adfOpCallback.onFailure(serviceInstanceId);
                        }
                    });
        }

    }

    public void loadServiceProfile(Map<UUID, ServiceProfileInfo> serviceProfileDataMap) {
        mServiceProfileMap.clear();
        mAppServiceProfileMap.clear();
        for (Map.Entry<UUID, ServiceProfileInfo> entry : serviceProfileDataMap.entrySet()) {
            mServiceProfileMap.put(entry.getKey(), entry.getValue());
            int app_uid = entry.getValue().uid;
            if (mAppServiceProfileMap.containsKey(app_uid)) {
                List<ServiceProfileInfo> appServiceProfileList = mAppServiceProfileMap.get(app_uid);
                appServiceProfileList.add(entry.getValue());
                mAppServiceProfileMap.put(app_uid, appServiceProfileList);
            } else {
                List<ServiceProfileInfo> appServiceProfileList = new ArrayList();
                appServiceProfileList.add(entry.getValue());
                mAppServiceProfileMap.put(app_uid, appServiceProfileList);
            }
        }
    }

    //TODO Send all info related to app as a Persistable bundle
    public List<ServiceProfileInfo> getServiceProfiles() {
        int app_uid = Binder.getCallingUid();
        if (mAppServiceProfileMap.containsKey(app_uid)) {
            return mAppServiceProfileMap.get(app_uid);
        }
        return null;
    }

    /** Initializes state machine and session related info */
    public void activateProfile(AttributionSource attributionSource, SessionHandle sessionHandle,
            UUID serviceInstanceId, IUwbRangingCallbacks rangingCallbacks, String chipId) {

        if (!mServiceProfileMap.containsKey(serviceInstanceId)) {
            Log.e(LOG_TAG, "UUID not found");
            return;
        }
        ServiceProfileInfo profileInfo = mServiceProfileMap.get(serviceInstanceId);

        switch (profileInfo.serviceID) {
            /* Only PACS controlee/responder is supported now*/
            case PACS_PROFILE_SERVICE_ID :
                RangingSessionController rangingSessionController = new PacsControleeSession(
                        sessionHandle,  attributionSource, mContext, mUwbInjector, profileInfo,
                        rangingCallbacks, mHandler, chipId);
                mRangingSessionTable.put(sessionHandle, rangingSessionController);
                break;
            default:
                Log.e(LOG_TAG, "Service ID not supported yet");
                return;
        }

        /* Session has been initialized, notify app */
        try {
            rangingCallbacks.onRangingOpened(sessionHandle);
            Log.i(LOG_TAG, "IUwbRangingCallbacks - onRangingOpened");
        } catch (Exception e) {
            Log.e(LOG_TAG, "IUwbRangingCallbacks - onRangingOpened : Failed");
            e.printStackTrace();
        }
    }

    /** Start Ranging */
    public void startRanging(SessionHandle sessionHandle) {
        if (mRangingSessionTable.containsKey(sessionHandle)) {
            RangingSessionController rangingSessionController = mRangingSessionTable.get(
                    sessionHandle);
            rangingSessionController.startSession();
        } else {
            Log.e(LOG_TAG, "Session Handle not found");
        }
    }

    /** Stop Ranging, can be started again, session will not be reset */
    public void stopRanging(SessionHandle sessionHandle) {
        if (mRangingSessionTable.containsKey(sessionHandle)) {
            RangingSessionController rangingSessionController = mRangingSessionTable.get(
                    sessionHandle);
            rangingSessionController.stopSession();
        } else {
            Log.e(LOG_TAG, "Session Handle not found");
        }
    }

    /** End Ranging session, session info will be reset */
    public void closeRanging(SessionHandle sessionHandle) {
        if (mRangingSessionTable.containsKey(sessionHandle)) {
            RangingSessionController rangingSessionController = mRangingSessionTable.get(
                    sessionHandle);
            rangingSessionController.closeSession();
            mRangingSessionTable.remove(sessionHandle);
        } else {
            Log.e(LOG_TAG, "Session Handle not found");
        }
    }

    /** Operating to the ADF. */
    public enum AdfOp {
        CREATE_ADF,
        PROVISIONING_ADF,
        IMPORT_ADF,
        DELETE_ADF
    }

    /** Callback for the ADF operating result.*/
    public interface AdfOpCallback {

        /** The operating on the specified ADF is success */
        void onSuccess(UUID serviceInstanceId, ObjectIdentifier adfOid, AdfOp adfOp);

        /** The operating on the specified ADF is failed. */
        void onFailure(UUID serviceInstanceId);
    }
}
