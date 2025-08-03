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

package com.android.adservices.service.consent;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.ui.data.UxStatesDao;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

/**
 * AppConsentStorageManager to handle user's consent related Apis in Android R.
 *
 * <p>It shares similarities with AppConsentStorageManager's logic, but adds additional storage
 * functionality specific to AdServicesExtDataStorageServiceManager.
 *
 * <p>Used in PPAPI_AND_ADEXT_SERVICE
 */
@RequiresApi(Build.VERSION_CODES.S)
public class AppConsentForRStorageManager extends AppConsentStorageManager {

    private final AdServicesExtDataStorageServiceManager mAdExtDataManager;
    /**
     * Constructor of AppConsentForRStorageManager
     *
     * @param datastore stores consent
     * @param appConsentDao mostly used by FLEDGE
     * @param uxStatesDao stores ux related data
     */
    public AppConsentForRStorageManager(
            BooleanFileDatastore datastore,
            AppConsentDao appConsentDao,
            UxStatesDao uxStatesDao,
            AdServicesExtDataStorageServiceManager adExtDataManager) {
        super(datastore, appConsentDao, uxStatesDao);
        this.mAdExtDataManager = adExtDataManager;
    }

    /** Clear ConsentForUninstalledApp, not support for Measurement. */
    @Override
    public void clearAllAppConsentData() {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement.
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "reset consent for apps"));
    }

    /** Clear ConsentForUninstalledApp, not support for Measurement. */
    @Override
    public void clearConsentForUninstalledApp(String packageName) {
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "clear consent for uninstalled app"));
    }

    /** Clear ConsentForUninstalledApp, not support for Measurement. */
    @Override
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "clear consent for uninstalled app"));
    }

    /** Clear KnownAppsWithConsent flag, not support for Measurement. */
    @Override
    public void clearKnownAppsWithConsent() {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement.
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "reset apps"));
    }

    /** Gets getAppsWithRevokedConsent flag, not support for Measurement. */
    @Override
    public ImmutableList<String> getAppsWithRevokedConsent() {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement.
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "fetch apps with revoked consent"));
    }

    /** Gets Consent by api flag. */
    @Override
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        if (apiType == AdServicesApiType.MEASUREMENTS) {
            return AdServicesApiConsent.getConsent(mAdExtDataManager.getMsmtConsent());
        }
        return AdServicesApiConsent.REVOKED;
    }

    /** Gets getKnownAppsWithConsent flag, not support for Measurement. */
    @Override
    public ImmutableList<String> getKnownAppsWithConsent() throws IOException {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement.
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "fetch apps with consent"));
    }

    /** Gets UserManualInteraction flag. */
    @Override
    public int getUserManualInteractionWithConsent() {
        return mAdExtDataManager.getManualInteractionWithConsentStatus();
    }

    /** Gets isAdultAccount flag. */
    @Override
    public boolean isAdultAccount() {
        return mAdExtDataManager.getIsAdultAccount();
    }

    /** Gets isConsentRevokedForApp flag, not support for Measurement. */
    @Override
    public boolean isConsentRevokedForApp(String packageName) {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement.
        throw new IllegalStateException(
                getAdExtExceptionMessage(
                        /* illegalAction= */ "check if consent has been revoked for" + " app"));
    }

    /** Gets isU18 account flag. */
    @Override
    public boolean isU18Account() {
        return mAdExtDataManager.getIsU18Account();
    }

    /** Records GA notification displayed. */
    @Override
    public void recordGaUxNotificationDisplayed(boolean wasGaUxDisplayed) {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
        // GA UX.
        throw new IllegalStateException(
                getAdExtExceptionMessage(
                        /* illegalAction= */ "store if GA notification was displayed"));
    }

    /** Records notification displayed. */
    @Override
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
        // Beta UX.
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "store if beta notif was displayed"));
    }

    /** Records user manual interaction bit. */
    @Override
    public void recordUserManualInteractionWithConsent(int interaction) {
        mAdExtDataManager.setManualInteractionWithConsentStatus(interaction);
    }

    /** Sets consent by api type. */
    @Override
    public void setAdultAccount(boolean isAdultAccount) {
        mAdExtDataManager.setIsAdultAccount(isAdultAccount);
    }

    /** Sets consent by api type. */
    @Override
    public void setConsent(AdServicesApiType apiType, boolean isGiven) throws IOException {
        if (apiType == AdServicesApiType.ALL_API) {
            super.setConsent(apiType, isGiven);
            return;
        }
        // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
        // Measurement. There should never be a call to set consent for other PPAPIs.
        if (apiType != AdServicesApiType.MEASUREMENTS) {
            throw new IllegalStateException(
                    getAdExtExceptionMessage(
                            /* illegalAction= */ "set consent for a non-msmt API"));
        }
        mAdExtDataManager.setMsmtConsent(isGiven);
    }

    /**
     * setConsentForApp.
     *
     * <p>PPAPI_AND_ADEXT_SERVICE is only set on R which only supports Measurement
     */
    @Override
    public void setConsentForApp(String packageName, boolean isConsentRevoked) {
        throw new IllegalStateException(
                getAdExtExceptionMessage(/* illegalAction= */ "revoke consent for app"));
    }

    /**
     * SetConsentForAppIfNew.
     *
     * <p>PPAPI_AND_ADEXT_SERVICE is only set on R which only supports Measurement
     */
    @Override
    public boolean setConsentForAppIfNew(String packageName, boolean isConsentRevoked) {
        throw new IllegalStateException(
                getAdExtExceptionMessage(
                        /* illegalAction= */ "check if consent has been revoked for" + " app"));
    }

    /** Stores isU18Account bit in AdExtData. */
    @Override
    public void setU18Account(boolean isU18Account) {
        mAdExtDataManager.setIsU18Account(isU18Account);
    }

    /** Stores U18 notification bit in AdExtData. */
    @Override
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        mAdExtDataManager.setNotificationDisplayed(wasU18NotificationDisplayed);
    }

    /** GA UX is never shown on R, so this info is not stored. */
    @Override
    public boolean wasGaUxNotificationDisplayed() {
        return false;
    }

    /** Beta UX is never shown on R, so this info is not stored. */
    @Override
    public boolean wasNotificationDisplayed() {
        return false;
    }

    /** Android R only U18 notification is allowed to be displayed. */
    @Override
    public boolean wasU18NotificationDisplayed() {
        return mAdExtDataManager.getNotificationDisplayed();
    }

    private static String getAdExtExceptionMessage(String illegalAction) {
        return String.format(
                "Attempting to %s using PPAPI_AND_ADEXT_SERVICE consent source of truth!",
                illegalAction);
    }
}
