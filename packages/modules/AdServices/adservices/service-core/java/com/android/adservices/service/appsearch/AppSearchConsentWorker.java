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

package com.android.adservices.service.appsearch;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GlobalSearchSession;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.platformstorage.PlatformStorage;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * This class provides an interface to read/write consent data to AppSearch. This is used as the
 * source of truth for S-. When a device upgrades from S- to T+, the consent is initialized from
 * AppSearch.
 */
@RequiresApi(Build.VERSION_CODES.S)
class AppSearchConsentWorker {
    // At the worker level, we ensure that writes do not conflict with any other writes/reads.
    private static final ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();

    private static final String CONSENT_DATABASE_NAME = "adservices_consent";
    private static final String APP_CONSENT_DATABASE_NAME = "adservices_app_consent";
    private static final String NOTIFICATION_DATABASE_NAME = "adservices_notification";
    private static final String INTERACTIONS_DATABASE_NAME = "adservices_interactions";
    private static final String TOPICS_DATABASE_NAME = "adservices-topics";
    private static final String UX_STATES_DATABASE_NAME = "adservices-ux-states";

    // Required for allowing AdServices apk access to read consent written by ExtServices module.
    private final String mAdservicesPackageName;

    private final ListenableFuture<AppSearchSession> mConsentSearchSession;
    private final ListenableFuture<AppSearchSession> mAppConsentSearchSession;
    private final ListenableFuture<AppSearchSession> mNotificationSearchSession;
    private final ListenableFuture<AppSearchSession> mInteractionsSearchSession;
    private final ListenableFuture<AppSearchSession> mTopicsSearchSession;
    private final ListenableFuture<AppSearchSession> mUxStatesSearchSession;

    // When reading across APKs, a GlobalSearchSession is needed, hence we use it when reading.
    private final ListenableFuture<GlobalSearchSession> mGlobalSearchSession;
    private final Executor mExecutor = AdServicesExecutors.getBackgroundExecutor();

    private final List<PackageIdentifier> mPackageIdentifiers = new ArrayList<>();
    // There is a single user ID for a given process, so this class would not be instantiated
    // across two user IDs.
    private final String mUid = getUserIdentifierFromBinderCallingUid();
    private static final String SPLITTER = ",";

    private AppSearchConsentWorker(@NonNull Context context) {
        Objects.requireNonNull(context);

        // We write with multiple schemas, so we need to initialize sessions per db.
        mConsentSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(context, CONSENT_DATABASE_NAME)
                                .build());
        mAppConsentSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(
                                        context, APP_CONSENT_DATABASE_NAME)
                                .build());
        mNotificationSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(
                                        context, NOTIFICATION_DATABASE_NAME)
                                .build());
        mInteractionsSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(
                                        context, INTERACTIONS_DATABASE_NAME)
                                .build());
        mTopicsSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(context, TOPICS_DATABASE_NAME)
                                .build());
        mUxStatesSearchSession =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(context, UX_STATES_DATABASE_NAME)
                                .build());

        // We use global session for reads since we may perform read on T+ AdServices package to
        // restore consent data post OTA.
        mGlobalSearchSession =
                PlatformStorage.createGlobalSearchSessionAsync(
                        new PlatformStorage.GlobalSearchContext.Builder(context).build());

        // The package identifier of the AdServices package on T+ should always have access to read
        // data written by AdExtServices package on S-.
        mAdservicesPackageName = getAdServicesPackageName(context);

        String shaCertsFlagValue = FlagsFactory.getFlags().getAdservicesApkShaCertificate();
        for (String shaCert : shaCertsFlagValue.split(SPLITTER)) {
            mPackageIdentifiers.add(
                    new PackageIdentifier(
                            mAdservicesPackageName, new Signature(shaCert).toByteArray()));
        }

    }

    /** Get an instance of AppSearchConsentWorker. */
    static AppSearchConsentWorker getInstance() {
        return new AppSearchConsentWorker(ApplicationContextSingleton.get());
    }

    /**
     * Get the consent for this user ID for this API type, as stored in AppSearch. Returns false if
     * the database doesn't exist in AppSearch.
     */
    boolean getConsent(@NonNull String apiType) {
        Objects.requireNonNull(apiType);
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchConsentDao.readConsentData(
                    mGlobalSearchSession, mExecutor, mUid, apiType, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /**
     * Sets the consent for this user ID for this API type in AppSearch. If we do not get
     * confirmation that the write operation was successful, then we throw an exception so that user
     * does not incorrectly think that the consent is updated.
     */
    void setConsent(@NonNull String apiType, @NonNull Boolean consented) {
        Objects.requireNonNull(apiType);
        Objects.requireNonNull(consented);
        READ_WRITE_LOCK.writeLock().lock();
        try {
            // The ID of the row needs to be unique per row. For a given user, we store multiple
            // rows, one per each apiType.
            AppSearchConsentDao dao =
                    new AppSearchConsentDao(
                            AppSearchConsentDao.getRowId(mUid, apiType),
                            mUid,
                            AppSearchConsentDao.NAMESPACE,
                            apiType,
                            consented.toString());
            dao.writeData(mConsentSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote consent data to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /**
     * Get the apps with consent as stored in AppSearch. If no such list was stored, empty list is
     * returned.
     */
    List<String> getAppsWithConsent(@NonNull String consentType) {
        Objects.requireNonNull(consentType);
        READ_WRITE_LOCK.readLock().lock();
        try {
            AppSearchAppConsentDao dao =
                    AppSearchAppConsentDao.readConsentData(
                            mGlobalSearchSession,
                            mExecutor,
                            mUid,
                            consentType,
                            mAdservicesPackageName);
            return (dao == null || dao.getApps() == null) ? List.of() : dao.getApps();
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Clear app consent data for this user for the given type of consent. */
    void clearAppsWithConsent(@NonNull String consentType) {
        Objects.requireNonNull(consentType);
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchDao.deleteData(
                    AppSearchAppConsentDao.class,
                    mAppConsentSearchSession,
                    mExecutor,
                    AppSearchAppConsentDao.getRowId(mUid, consentType),
                    AppSearchAppConsentDao.NAMESPACE);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Adds an app to the list of apps with this consentType for this user. */
    boolean addAppWithConsent(@NonNull String consentType, @NonNull String app) {
        Objects.requireNonNull(consentType);
        Objects.requireNonNull(app);
        READ_WRITE_LOCK.writeLock().lock();

        try {
            // Since AppSearch doesn't support PATCH api, we need to do a {read, modify, write}. See
            // b/274507022 for details.
            AppSearchAppConsentDao dao =
                    AppSearchAppConsentDao.readConsentData(
                            mGlobalSearchSession,
                            mExecutor,
                            mUid,
                            consentType,
                            mAdservicesPackageName);
            // If there was no such row in the table, create one. Else, update existing one.
            if (dao == null) {
                dao =
                        new AppSearchAppConsentDao(
                                AppSearchAppConsentDao.getRowId(mUid, consentType),
                                mUid,
                                AppSearchAppConsentDao.NAMESPACE,
                                consentType,
                                List.of(app));
            } else {
                // If this app was already present in the consent list, no need to rewrite.
                if (dao.getApps() != null && dao.getApps().contains(app)) {
                    return true;
                }
                List<String> apps =
                        dao.getApps() != null ? new ArrayList<>(dao.getApps()) : new ArrayList<>();
                apps.add(app);
                dao.setApps(apps);
            }
            dao.writeData(mAppConsentSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote app consent data to AppSearch (add): " + dao);
            return true;
        } catch (RuntimeException e) {
            LogUtil.e(e, "Failed to write consent to AppSearch");
            return false;
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /**
     * Removes an app from the list of apps with this consentType for this user. If we do not get
     * confirmation that the write operation was successful, then we throw an exception so that user
     * does not incorrectly think that the consent is updated.
     */
    void removeAppWithConsent(@NonNull String consentType, @NonNull String app) {
        Objects.requireNonNull(consentType);
        Objects.requireNonNull(app);
        READ_WRITE_LOCK.readLock().lock();

        try {
            // Since AppSearch doesn't support PATCH api, we need to do a {read, modify, write}. See
            // b/274507022 for details.
            AppSearchAppConsentDao dao =
                    AppSearchAppConsentDao.readConsentData(
                            mGlobalSearchSession,
                            mExecutor,
                            mUid,
                            consentType,
                            mAdservicesPackageName);
            // If there was no such row in the table, do nothing. Else, update existing one.
            if (dao == null || dao.getApps() == null || !dao.getApps().contains(app)) {
                return;
            }
            dao.setApps(
                    dao.getApps().stream()
                            .filter(filterApp -> !filterApp.equals(app))
                            .collect(Collectors.toList()));
            dao.writeData(mAppConsentSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote app consent data to AppSearch (remove): " + dao);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Returns whether the beta UX notification was displayed to this user on this device. */
    boolean wasNotificationDisplayed() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchNotificationDao.wasNotificationDisplayed(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Returns whether the GA UX notification was displayed to this user on this device. */
    boolean wasGaUxNotificationDisplayed() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /**
     * Record having shown the beta UX notification to this user on this device. We cannot reset
     * this, i.e., once a notification is shown, it is forever recorded as shown.
     */
    void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchNotificationDao dao =
                    new AppSearchNotificationDao(
                            AppSearchNotificationDao.getRowId(mUid),
                            mUid,
                            AppSearchNotificationDao.NAMESPACE,
                            /* wasNotificationDisplayed= */ wasNotificationDisplayed,
                            /* wasGaUxNotificationDisplayed= */ wasGaUxNotificationDisplayed());
            dao.writeData(mNotificationSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote notification data to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Record having shown the GA UX notification to this user on this device. */
    void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchNotificationDao dao =
                    new AppSearchNotificationDao(
                            AppSearchNotificationDao.getRowId(mUid),
                            mUid,
                            AppSearchNotificationDao.NAMESPACE,
                            /* wasNotificationDisplayed= */ wasNotificationDisplayed(),
                            /* wasGaUxNotificationDisplayed= */ wasNotificationDisplayed);
            dao.writeData(mNotificationSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote notification data to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /**
     * Returns the PrivacySandboxFeature recorded for this user on this device. Possible values are
     * as per {@link com.android.adservices.service.common.feature.PrivacySandboxFeatureType}.
     */
    PrivacySandboxFeatureType getPrivacySandboxFeature() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchInteractionsDao.getPrivacySandboxFeatureType(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Record the current privacy sandbox feature. */
    void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType currentFeatureType) {
        String apiType = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchInteractionsDao dao =
                    new AppSearchInteractionsDao(
                            AppSearchInteractionsDao.getRowId(mUid, apiType),
                            mUid,
                            AppSearchInteractionsDao.NAMESPACE,
                            apiType,
                            currentFeatureType.ordinal());
            dao.writeData(mInteractionsSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote feature type data to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    @ConsentManager.UserManualInteraction
    int getUserManualInteractionWithConsent() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchInteractionsDao.getManualInteractions(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves information to the storage that user interacted with consent manually. */
    void recordUserManualInteractionWithConsent(
            @ConsentManager.UserManualInteraction int interaction) {
        String apiType = AppSearchInteractionsDao.API_TYPE_INTERACTIONS;
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchInteractionsDao dao =
                    new AppSearchInteractionsDao(
                            AppSearchInteractionsDao.getRowId(mUid, apiType),
                            mUid,
                            AppSearchInteractionsDao.NAMESPACE,
                            apiType,
                            interaction);
            dao.writeData(mInteractionsSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote interactions data to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns the list of blocked topics. */
    List<Topic> getBlockedTopics() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchTopicsConsentDao.getBlockedTopics(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Record a blocked topic. */
    void recordBlockedTopic(Topic topic) {
        Objects.requireNonNull(topic);
        READ_WRITE_LOCK.writeLock().lock();

        try {
            // Since AppSearch doesn't support PATCH api, we need to do a {read, modify, write}. See
            // b/274507022 for details.
            AppSearchTopicsConsentDao dao =
                    AppSearchTopicsConsentDao.readConsentData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            // If there was no such row in the table, create the row. Else, update existing one.
            if (dao == null) {
                dao =
                        new AppSearchTopicsConsentDao(
                                mUid,
                                mUid,
                                AppSearchTopicsConsentDao.NAMESPACE,
                                List.of(topic.getTopic()),
                                List.of(topic.getTaxonomyVersion()),
                                List.of(topic.getModelVersion()));
            } else {
                dao.addBlockedTopic(topic);
            }
            dao.writeData(mTopicsSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote topics consent data to AppSearch (block): " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Remove a previously recorded blocked topic. */
    void recordUnblockedTopic(Topic topic) {
        Objects.requireNonNull(topic);
        READ_WRITE_LOCK.writeLock().lock();

        try {
            // Since AppSearch doesn't support PATCH api, we need to do a {read, modify, write}. See
            // b/274507022 for details.
            AppSearchTopicsConsentDao dao =
                    AppSearchTopicsConsentDao.readConsentData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            // If there was no such row in the table, do nothing. Else, update existing one.
            if (dao == null) {
                return;
            }
            dao.removeBlockedTopic(topic);
            dao.writeData(mTopicsSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote topics consent data to AppSearch (unblock): " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Clears the list of blocked topics. */
    void clearBlockedTopics() {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            // We don't do {read, modify, write} here since the DAO has no other information besides
            // blocked topics, so we can rewrite it.
            AppSearchTopicsConsentDao dao =
                    new AppSearchTopicsConsentDao(
                            mUid,
                            mUid,
                            AppSearchTopicsConsentDao.NAMESPACE,
                            List.of(),
                            List.of(),
                            List.of());
            dao.writeData(mTopicsSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote topics consent data to AppSearch (clear): " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns the User Identifier from the CallingUid. */
    @VisibleForTesting
    String getUserIdentifierFromBinderCallingUid() {
        return "" + UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    /**
     * This method returns the package name of the AdServices APK from AdServices apex (T+). On an
     * S- device, it removes the "ext." substring from the package name.
     */
    public static String getAdServicesPackageName(Context context) {
        Intent serviceIntent = new Intent(AdServicesCommon.ACTION_TOPICS_SERVICE);
        List<ResolveInfo> resolveInfos =
                context.getPackageManager()
                        .queryIntentServices(
                                serviceIntent,
                                PackageManager.GET_SERVICES
                                        | PackageManager.MATCH_SYSTEM_ONLY
                                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        final ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, serviceIntent.getAction());
        if (serviceInfo != null) {
            // Return the AdServices package name based on the current package name.
            String packageName = serviceInfo.packageName;
            if (packageName == null || packageName.isEmpty()) {
                throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
            }
            return packageName.replace(
                    AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX,
                    AdServicesCommon.ADSERVICES_APK_PACKAGE_NAME_SUFFIX);
        }
        // If we don't know the AdServices package name, we can't write.
        throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    /** Returns whether isAdIdEnabled bit is true. */
    boolean isAdIdEnabled() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readIsAdIdEnabled(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the isAdIdEnabled bit in app search. */
    void setAdIdEnabled(boolean isAdIdEnabled) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setAdIdEnabled(isAdIdEnabled);
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote the isAdIdEnabled bit to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns whether isU18Account bit is true. */
    boolean isU18Account() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readIsU18Account(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the isU18Account bit in app search. */
    void setU18Account(boolean isU18Account) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setU18Account(isU18Account);
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote the isU18Account bit to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns whether isEntryPointEnabled bit is true. */
    boolean isEntryPointEnabled() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readIsEntryPointEnabled(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the isEntryPointEnabled bit in app search. */
    void setEntryPointEnabled(boolean isEntryPointEnabled) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setEntryPointEnabled(isEntryPointEnabled);
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote the isEntryPointEnabled bit to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns whether isAdultAccount bit is true. */
    boolean isAdultAccount() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readIsAdultAccount(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the isAdultAccount bit in app search. */
    void setAdultAccount(boolean isAdultAccount) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setAdultAccount(isAdultAccount);
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote the isAdultAccount bit to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns whether wasU18NotificationDisplayed bit is true. */
    boolean wasU18NotificationDisplayed() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readIsU18NotificationDisplayed(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the wasU18NotificationDisplayed bit in app search. */
    void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setU18NotificationDisplayed(wasU18NotificationDisplayed);
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote the wasU18NotificationDisplayed bit to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns the current privacy sandbox UX. */
    PrivacySandboxUxCollection getUx() {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readUx(
                    mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the current privacy sandbox UX. */
    void setUx(PrivacySandboxUxCollection ux) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setUx(ux.toString());
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote PrivacySandboxUx to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    /** Returns the privacy sandbox enrollment channel. */
    PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(PrivacySandboxUxCollection ux) {
        READ_WRITE_LOCK.readLock().lock();
        try {
            return AppSearchUxStatesDao.readEnrollmentChannel(
                    mGlobalSearchSession, mExecutor, mUid, ux, mAdservicesPackageName);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /** Saves the current privacy sandbox enrollment channel. */
    void setEnrollmentChannel(
            PrivacySandboxUxCollection ux,
            PrivacySandboxEnrollmentChannelCollection enrollmentChannel) {
        READ_WRITE_LOCK.writeLock().lock();
        try {
            if (!Arrays.asList(ux.getEnrollmentChannelCollection()).contains(enrollmentChannel)) {
                // setting an enrollment channel that is not part of the given UX is a no-op.
                return;
            }
            AppSearchUxStatesDao dao =
                    AppSearchUxStatesDao.readData(
                            mGlobalSearchSession, mExecutor, mUid, mAdservicesPackageName);
            if (dao == null) {
                dao =
                        new AppSearchUxStatesDao(
                                AppSearchUxStatesDao.getRowId(mUid),
                                mUid,
                                AppSearchUxStatesDao.NAMESPACE);
            }
            dao.setEnrollmentChannel(enrollmentChannel.toString());
            dao.writeData(mUxStatesSearchSession, mPackageIdentifiers, mExecutor);
            LogUtil.d("Wrote PrivacySandboxUx to AppSearch: " + dao);
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }
}
