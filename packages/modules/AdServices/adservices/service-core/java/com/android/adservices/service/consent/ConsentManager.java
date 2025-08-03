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

package com.android.adservices.service.consent;

import static com.android.adservices.AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_SEARCH_DATA_MIGRATION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_DEFAULT_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_MANUAL_CONSENT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_NOTIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_RESET_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.U18_UX;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.MeasurementWipeoutStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manager to handle user's consent.
 *
 * <p>For Beta the consent is given for all {@link AdServicesApiType} or for none.
 *
 * <p>Currently there are three types of source of truth to store consent data,
 *
 * <ul>
 *   <li>SYSTEM_SERVER_ONLY: Write and read consent from system server only.
 *   <li>PPAPI_ONLY: Write and read consent from PPAPI only.
 *   <li>PPAPI_AND_SYSTEM_SERVER: Write consent to both PPAPI and system server. Read consent from
 *       system server only.
 * </ul>
 */
// TODO(b/259791134): Add a CTS/UI test to test the Consent Migration
// TODO(b/269798827): Enable for R.
// TODO(b/279042385): move UI logs to UI.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentManager {
    private static volatile ConsentManager sConsentManager;

    @IntDef(value = {NO_MANUAL_INTERACTIONS_RECORDED, UNKNOWN, MANUAL_INTERACTIONS_RECORDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserManualInteraction {}

    public static final int NO_MANUAL_INTERACTIONS_RECORDED = -1;
    public static final int UNKNOWN = 0;
    public static final int MANUAL_INTERACTIONS_RECORDED = 1;

    private final Flags mFlags;
    private final TopicsWorker mTopicsWorker;
    private final BooleanFileDatastore mDatastore;
    private final AppConsentDao mAppConsentDao;
    private final EnrollmentDao mEnrollmentDao;
    private final MeasurementImpl mMeasurementImpl;
    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final AdServicesManager mAdServicesManager;
    private final int mConsentSourceOfTruth;
    private final AppSearchConsentManager mAppSearchConsentManager;
    private final UserProfileIdManager mUserProfileIdManager;
    private final UxStatesDao mUxStatesDao;
    private final AdServicesExtDataStorageServiceManager mAdExtDataManager;

    private static final Object LOCK = new Object();
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    ConsentManager(
            @NonNull TopicsWorker topicsWorker,
            @NonNull AppConsentDao appConsentDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull MeasurementImpl measurementImpl,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AppInstallDao appInstallDao,
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull FrequencyCapDao frequencyCapDao,
            @NonNull AdServicesManager adServicesManager,
            @NonNull BooleanFileDatastore booleanFileDatastore,
            @NonNull AppSearchConsentManager appSearchConsentManager,
            @NonNull UserProfileIdManager userProfileIdManager,
            @NonNull UxStatesDao uxStatesDao,
            @NonNull AdServicesExtDataStorageServiceManager adExtDataManager,
            @NonNull Flags flags,
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth,
            boolean enableAppsearchConsentData,
            boolean enableAdExtServiceConsentData) {
        Objects.requireNonNull(topicsWorker);
        Objects.requireNonNull(appConsentDao);
        Objects.requireNonNull(measurementImpl);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(frequencyCapDao);
        Objects.requireNonNull(booleanFileDatastore);
        Objects.requireNonNull(userProfileIdManager);

        if (consentSourceOfTruth == Flags.SYSTEM_SERVER_ONLY
                || consentSourceOfTruth == Flags.PPAPI_AND_SYSTEM_SERVER) {
            Objects.requireNonNull(adServicesManager);
        }

        if (enableAppsearchConsentData) {
            Objects.requireNonNull(appSearchConsentManager);
        }

        if (enableAdExtServiceConsentData) {
            Objects.requireNonNull(adExtDataManager);
        }

        mAdServicesManager = adServicesManager;
        mTopicsWorker = topicsWorker;
        mDatastore = booleanFileDatastore;
        mAppConsentDao = appConsentDao;
        mEnrollmentDao = enrollmentDao;
        mMeasurementImpl = measurementImpl;
        mCustomAudienceDao = customAudienceDao;
        mAppInstallDao = appInstallDao;
        mProtectedSignalsDao = protectedSignalsDao;
        mFrequencyCapDao = frequencyCapDao;
        mUxStatesDao = uxStatesDao;

        mAppSearchConsentManager = appSearchConsentManager;
        mUserProfileIdManager = userProfileIdManager;
        mAdExtDataManager = adExtDataManager;
        mFlags = flags;
        mConsentSourceOfTruth = consentSourceOfTruth;
    }

    /**
     * Gets an instance of {@link ConsentManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManager getInstance() {
        Context context = ApplicationContextSingleton.get();

        Trace.beginSection("ConsentManager#Initialization");
        if (sConsentManager == null) {
            synchronized (LOCK) {
                if (sConsentManager == null) {
                    // Execute one-time consent migration if needed.
                    int consentSourceOfTruth = FlagsFactory.getFlags().getConsentSourceOfTruth();
                    BooleanFileDatastore datastore = createAndInitializeDataStore(context);
                    AdServicesManager adServicesManager = AdServicesManager.getInstance(context);
                    AppConsentDao appConsentDao = AppConsentDao.getInstance(context);

                    // It is possible that the old value of the flag lingers after OTA until the
                    // first
                    // PH sync. In that case, we should not use the stale value, but use the default
                    // instead. The next PH sync will restore the T+ value.
                    if (SdkLevel.isAtLeastT() && consentSourceOfTruth == Flags.APPSEARCH_ONLY) {
                        consentSourceOfTruth = Flags.DEFAULT_CONSENT_SOURCE_OF_TRUTH;
                    }
                    AppSearchConsentManager appSearchConsentManager = null;
                    StatsdAdServicesLogger statsdAdServicesLogger =
                            StatsdAdServicesLogger.getInstance();
                    // Flag enable_appsearch_consent_data is true on S- and T+ only when we want to
                    // use AppSearch to write to or read from.
                    boolean enableAppsearchConsentData =
                            FlagsFactory.getFlags().getEnableAppsearchConsentData();
                    if (enableAppsearchConsentData) {
                        appSearchConsentManager = AppSearchConsentManager.getInstance();
                        handleConsentMigrationFromAppSearchIfNeeded(
                                context,
                                datastore,
                                appConsentDao,
                                appSearchConsentManager,
                                adServicesManager,
                                statsdAdServicesLogger);
                    }

                    AdServicesExtDataStorageServiceManager adServicesExtDataManager = null;
                    // Flag enable_adext_service_consent_data is true on R and S+ only when
                    // we want to use AdServicesExtDataStorageService to write to or read from.
                    boolean enableAdExtServiceConsentData =
                            FlagsFactory.getFlags().getEnableAdExtServiceConsentData();
                    if (enableAdExtServiceConsentData) {
                        adServicesExtDataManager =
                                AdServicesExtDataStorageServiceManager.getInstance(context);
                        // NOTE: To disable migration from AdExtService to AppSearch on 2024 M03-
                        // builds, use the deprecated flag
                        // enable_adext_service_to_appsearch_migration.
                        if (FlagsFactory.getFlags().getEnableMigrationFromAdExtService()) {
                            AdExtDataConsentMigrationUtils
                                    .handleConsentMigrationFromAdExtDataIfNeeded(
                                            context,
                                            appSearchConsentManager,
                                            adServicesExtDataManager,
                                            statsdAdServicesLogger,
                                            adServicesManager);
                        }
                    }

                    // Attempt to migrate consent data from PPAPI to System server if needed.
                    handleConsentMigrationIfNeeded(
                            context,
                            datastore,
                            adServicesManager,
                            statsdAdServicesLogger,
                            consentSourceOfTruth);

                    sConsentManager =
                            new ConsentManager(
                                    TopicsWorker.getInstance(context),
                                    appConsentDao,
                                    EnrollmentDao.getInstance(context),
                                    MeasurementImpl.getInstance(context),
                                    CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                                    SharedStorageDatabase.getInstance(context).appInstallDao(),
                                    ProtectedSignalsDatabase.getInstance(context)
                                            .protectedSignalsDao(),
                                    SharedStorageDatabase.getInstance(context).frequencyCapDao(),
                                    adServicesManager,
                                    datastore,
                                    appSearchConsentManager,
                                    UserProfileIdManager.getInstance(context),
                                    // TODO(b/260601944): Remove Flag Instance.
                                    UxStatesDao.getInstance(context),
                                    adServicesExtDataManager,
                                    FlagsFactory.getFlags(),
                                    consentSourceOfTruth,
                                    enableAppsearchConsentData,
                                    enableAdExtServiceConsentData);
                }
            }
        }
        Trace.endSection();
        return sConsentManager;
    }

    /**
     * Enables all PP API services. It gives consent to Topics, Fledge and Measurements services.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     */
    public void enable(@NonNull Context context) {
        Objects.requireNonNull(context);

        // Check current value, if it is already enabled, skip this enable process. so that the Api
        // won't be reset. Only add this logic to "enable" not "disable", since if it already
        // disabled, there is no harm to reset the api again.
        if (mFlags.getConsentManagerLazyEnableMode() && getConsentFromSourceOfTruth()) {
            LogUtil.d("CONSENT_KEY already enable. Skipping enable process.");
            return;
        }
        UiStatsLogger.logOptInSelected();

        BackgroundJobsManager.scheduleAllBackgroundJobs(context);

        try {
            // reset all state data which should be removed
            resetTopicsAndBlockedTopics();
            resetAppsAndBlockedApps();
            resetMeasurement();
            resetUserProfileId();
            mUserProfileIdManager.getOrCreateId();
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setConsentToSourceOfTruth(/* isGiven */ true);
    }

    /**
     * Disables all PP API services. It revokes consent to Topics, Fledge and Measurements services.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     */
    public void disable(@NonNull Context context) {
        Objects.requireNonNull(context);

        UiStatsLogger.logOptOutSelected();
        // Disable all the APIs
        try {
            // reset all data
            resetTopicsAndBlockedTopics();
            resetAppsAndBlockedApps();
            resetMeasurement();
            resetEnrollment();
            resetUserProfileId();

            BackgroundJobsManager.unscheduleAllBackgroundJobs(
                    context.getSystemService(JobScheduler.class));
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setConsentToSourceOfTruth(/* isGiven */ false);
    }

    /**
     * Enables the {@code apiType} PP API service. It gives consent to an API which is provided in
     * the parameter.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     *
     * @param context Context of the application.
     * @param apiType Type of the API (Topics, Fledge, Measurement) which should be enabled.
     */
    public void enable(@NonNull Context context, AdServicesApiType apiType) {
        Objects.requireNonNull(context);
        // Check current value, if it is already enabled, skip this enable process. so that the Api
        // won't be reset.
        if (mFlags.getConsentManagerLazyEnableMode()
                && getPerApiConsentFromSourceOfTruth(apiType)) {
            LogUtil.d(
                    "ApiType: is %s already enable. Skipping enable process.",
                    apiType.toPpApiDatastoreKey());
            return;
        }

        UiStatsLogger.logOptInSelected(apiType);

        BackgroundJobsManager.scheduleJobsPerApi(context, apiType);

        try {
            // reset all state data which should be removed
            resetByApi(apiType);

            if (AdServicesApiType.FLEDGE == apiType) {
                mUserProfileIdManager.getOrCreateId();
            }
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setPerApiConsentToSourceOfTruth(/* isGiven */ true, apiType);
    }

    /**
     * Disables {@code apiType} PP API service. It revokes consent to an API which is provided in
     * the parameter.
     *
     * <p>To write consent to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To
     * write to system server consent if source of truth is system server or dual sources.
     */
    public void disable(@NonNull Context context, AdServicesApiType apiType) {
        Objects.requireNonNull(context);

        UiStatsLogger.logOptOutSelected(apiType);

        try {
            resetByApi(apiType);
            BackgroundJobsManager.unscheduleJobsPerApi(
                    context.getSystemService(JobScheduler.class), apiType);
        } catch (IOException e) {
            throw new RuntimeException(ConsentConstants.ERROR_MESSAGE_WHILE_SET_CONTENT, e);
        }

        setPerApiConsentToSourceOfTruth(/* isGiven */ false, apiType);

        if (areAllApisDisabled()) {
            BackgroundJobsManager.unscheduleAllBackgroundJobs(
                    context.getSystemService(JobScheduler.class));
        }
    }

    private boolean areAllApisDisabled() {
        if (getConsent(AdServicesApiType.TOPICS).isGiven()
                || getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                || getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            return false;
        }
        return true;
    }

    /**
     * Retrieves the consent for all PP API services.
     *
     * <p>To read from PPAPI consent if source of truth is PPAPI. To read from system server consent
     * if source of truth is system server or dual sources.
     *
     * @return AdServicesApiConsent the consent
     */
    public AdServicesApiConsent getConsent() {
        if (mFlags.getConsentManagerDebugMode()) {
            return AdServicesApiConsent.GIVEN;
        }

        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ AdServicesApiConsent.REVOKED,
                () -> AdServicesApiConsent.getConsent(mDatastore.get(ConsentConstants.CONSENT_KEY)),
                () ->
                        AdServicesApiConsent.getConsent(
                                mAdServicesManager.getConsent(ConsentParcel.ALL_API).isIsGiven()),
                () ->
                        AdServicesApiConsent.getConsent(
                                mAppSearchConsentManager.getConsent(
                                        ConsentConstants.CONSENT_KEY_FOR_ALL)),
                // Beta UX is never shown on R, so this info is not stored.
                () -> AdServicesApiConsent.getConsent(false),
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /**
     * Retrieves the consent per API.
     *
     * @param apiType apiType for which the consent should be provided
     * @return {@link AdServicesApiConsent} providing information whether the consent was given or
     *     revoked.
     */
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        if (mFlags.getConsentManagerDebugMode()) {
            return AdServicesApiConsent.GIVEN;
        }

        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ AdServicesApiConsent.REVOKED,
                () ->
                        AdServicesApiConsent.getConsent(
                                mDatastore.get(apiType.toPpApiDatastoreKey())),
                () ->
                        AdServicesApiConsent.getConsent(
                                mAdServicesManager
                                        .getConsent(apiType.toConsentApiType())
                                        .isIsGiven()),
                () ->
                        AdServicesApiConsent.getConsent(
                                mAppSearchConsentManager.getConsent(apiType.toPpApiDatastoreKey())),
                // Only Measurement API is supported on Android R.
                () ->
                        AdServicesApiConsent.getConsent(
                                apiType == AdServicesApiType.MEASUREMENTS
                                        && mAdExtDataManager.getMsmtConsent()),
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /**
     * Returns whether the user is adult user who OTA from R.
     *
     * @return true if user is adult user who OTA from R, otherwise false.
     */
    public boolean isOtaAdultUserFromRvc() {
        if (mFlags.getConsentManagerOTADebugMode()) {
            return true;
        }

        // TODO(313672368) clean up getRvcPostOtaNotifAgeCheck flag after u18 is qualified on R/S
        return mAdExtDataManager != null
                && mAdExtDataManager.getNotificationDisplayed()
                && (mFlags.getRvcPostOtaNotifAgeCheck()
                        ? !mAdExtDataManager.getIsU18Account()
                                && mAdExtDataManager.getIsAdultAccount()
                        : true);
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which could
     * be returned to the {@link TopicsWorker} clients.
     *
     * @return {@link ImmutableList} of {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getKnownTopicsWithConsent() {
        return mTopicsWorker.getKnownTopicsWithConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to get {@link ImmutableList} of {@link Topic}s which were
     * blocked by the user.
     *
     * @return {@link ImmutableList} of blocked {@link Topic}s.
     */
    @NonNull
    public ImmutableList<Topic> getTopicsWithRevokedConsent() {
        return mTopicsWorker.getTopicsWithRevokedConsent();
    }

    /**
     * Proxy call to {@link TopicsWorker} to revoke consent for provided {@link Topic} (block
     * topic).
     *
     * @param topic {@link Topic} to block.
     */
    @NonNull
    public void revokeConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.revokeConsentForTopic(topic);
    }

    /**
     * Proxy call to {@link TopicsWorker} to restore consent for provided {@link Topic} (unblock the
     * topic).
     *
     * @param topic {@link Topic} to restore consent for.
     */
    @NonNull
    public void restoreConsentForTopic(@NonNull Topic topic) {
        mTopicsWorker.restoreConsentForTopic(topic);
    }

    /** Wipes out all the data gathered by Topics API but blocked topics. */
    public void resetTopics() {
        ArrayList<String> tablesToBlock = new ArrayList<>();
        tablesToBlock.add(TopicsTables.BlockedTopicsContract.TABLE);
        mTopicsWorker.clearAllTopicsData(tablesToBlock);
    }

    /** Wipes out all the data gathered by Topics API. */
    public void resetTopicsAndBlockedTopics() {
        mTopicsWorker.clearAllTopicsData(new ArrayList<>());
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have not had user
     *     consent revoked
     */
    public ImmutableList<App> getKnownAppsWithConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ ImmutableList.of(),
                () ->
                        ImmutableList.copyOf(
                                mAppConsentDao.getKnownAppsWithConsent().stream()
                                        .map(App::create)
                                        .collect(Collectors.toList())),
                () ->
                        ImmutableList.copyOf(
                                mAdServicesManager
                                        .getKnownAppsWithConsent(
                                                new ArrayList<>(
                                                        mAppConsentDao.getInstalledPackages()))
                                        .stream()
                                        .map(App::create)
                                        .collect(Collectors.toList())),
                () -> mAppSearchConsentManager.getKnownAppsWithConsent(),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "fetch apps with consent"));
                },
                /* errorLogger= */ null);
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ ImmutableList.of(),
                () ->
                        ImmutableList.copyOf(
                                mAppConsentDao.getAppsWithRevokedConsent().stream()
                                        .map(App::create)
                                        .collect(Collectors.toList())),
                () ->
                        ImmutableList.copyOf(
                                mAdServicesManager
                                        .getAppsWithRevokedConsent(
                                                new ArrayList<>(
                                                        mAppConsentDao.getInstalledPackages()))
                                        .stream()
                                        .map(App::create)
                                        .collect(Collectors.toList())),
                () -> mAppSearchConsentManager.getAppsWithRevokedConsent(),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "fetch apps with revoked consent"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Proxy call to {@link AppConsentDao} to revoke consent for provided {@link App}.
     *
     * <p>Also clears all app data related to the provided {@link App}.
     *
     * @param app {@link App} to block.
     * @throws IOException if the operation fails
     */
    public void revokeConsentForApp(@NonNull App app) throws IOException {
        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.setConsentForApp(app.getPackageName(), true),
                () ->
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                true),
                () -> mAppSearchConsentManager.revokeConsentForApp(app),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "revoke consent for app"));
                },
                /* errorLogger= */ null);

        asyncExecute(
                () -> mCustomAudienceDao.deleteCustomAudienceDataByOwner(app.getPackageName()));
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(() -> mAppInstallDao.deleteByPackageName(app.getPackageName()));
            asyncExecute(
                    () -> mFrequencyCapDao.deleteHistogramDataBySourceApp(app.getPackageName()));
        }
        if (mFlags.getProtectedSignalsCleanupEnabled()) {
            asyncExecute(
                    () ->
                            mProtectedSignalsDao.deleteSignalsByPackage(
                                    Collections.singletonList(app.getPackageName())));
        }
    }

    /**
     * Proxy call to {@link AppConsentDao} to restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     * @throws IOException if the operation fails
     */
    public void restoreConsentForApp(@NonNull App app) throws IOException {
        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.setConsentForApp(app.getPackageName(), false),
                () ->
                        mAdServicesManager.setConsentForApp(
                                app.getPackageName(),
                                mAppConsentDao.getUidForInstalledPackageName(app.getPackageName()),
                                false),
                () -> mAppSearchConsentManager.restoreConsentForApp(app),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "restore consent for app"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     *
     * @throws IOException if the operation fails
     */
    public void resetAppsAndBlockedApps() throws IOException {
        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.clearAllConsentData(),
                () -> mAdServicesManager.clearAllAppConsentData(),
                () -> mAppSearchConsentManager.clearAllAppConsentData(),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "reset consent for apps"));
                },
                /* errorLogger= */ null);

        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
            asyncExecute(mFrequencyCapDao::deleteAllHistogramData);
        }
        if (mFlags.getProtectedSignalsCleanupEnabled()) {
            asyncExecute(mProtectedSignalsDao::deleteAllSignals);
        }
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     *
     * @throws IOException if the operation fails
     */
    public void resetApps() throws IOException {
        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.clearKnownAppsWithConsent(),
                () -> mAdServicesManager.clearKnownAppsWithConsent(),
                () -> mAppSearchConsentManager.clearKnownAppsWithConsent(),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(/* illegalAction= */ "reset apps"));
                },
                /* errorLogger= */ null);

        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
            asyncExecute(mFrequencyCapDao::deleteAllHistogramData);
        }
        if (mFlags.getProtectedSignalsCleanupEnabled()) {
            asyncExecute(mProtectedSignalsDao::deleteAllSignals);
        }
    }

    /**
     * Checks whether a single given installed application (identified by its package name) has had
     * user consent to use the FLEDGE APIs revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * @param packageName String package name that uniquely identifies an installed application to
     *     check
     * @return {@code true} if either the FLEDGE Privacy Sandbox initiative has been opted out or if
     *     the user has revoked consent for the given application to use the FLEDGE APIs
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForApp(@NonNull String packageName)
            throws IllegalArgumentException {
        AdServicesApiConsent consent = getConsent(AdServicesApiType.FLEDGE);

        if (!consent.isGiven()) {
            return true;
        }

        synchronized (LOCK) {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    try {
                        return mAppConsentDao.isConsentRevokedForApp(packageName);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                    }
                    return true;
                case Flags.SYSTEM_SERVER_ONLY:
                    // Intentional fallthrough
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    return mAdServicesManager.isConsentRevokedForApp(
                            packageName, mAppConsentDao.getUidForInstalledPackageName(packageName));
                case Flags.APPSEARCH_ONLY:
                    if (mFlags.getEnableAppsearchConsentData()) {
                        return mAppSearchConsentManager.isFledgeConsentRevokedForApp(packageName);
                    }
                    return true;
                case Flags.PPAPI_AND_ADEXT_SERVICE:
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "check if consent has been revoked for"
                                            + " app"));
                default:
                    LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                    return true;
            }
        }
    }

    /**
     * Persists the use of a FLEDGE API by a single given installed application (identified by its
     * package name) if the app has not already had its consent revoked.
     *
     * <p>This method also checks whether a user has opted out of the FLEDGE Privacy Sandbox
     * initiative.
     *
     * <p>This is only meant to be called by the FLEDGE APIs.
     *
     * @param packageName String package name that uniquely identifies an installed application that
     *     has used a FLEDGE API
     * @return {@code true} if user consent has been revoked for the application or API, {@code
     *     false} otherwise
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     */
    public boolean isFledgeConsentRevokedForAppAfterSettingFledgeUse(@NonNull String packageName)
            throws IllegalArgumentException {
        AdServicesApiConsent consent = getConsent(AdServicesApiType.FLEDGE);

        if (!consent.isGiven()) {
            return true;
        }

        synchronized (LOCK) {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    try {
                        return mAppConsentDao.setConsentForAppIfNew(packageName, false);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                        return true;
                    }
                case Flags.SYSTEM_SERVER_ONLY:
                    return mAdServicesManager.setConsentForAppIfNew(
                            packageName,
                            mAppConsentDao.getUidForInstalledPackageName(packageName),
                            false);
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    try {
                        mAppConsentDao.setConsentForAppIfNew(packageName, false);
                    } catch (IOException exception) {
                        LogUtil.e(exception, "FLEDGE consent check failed due to IOException");
                        return true;
                    }
                    return mAdServicesManager.setConsentForAppIfNew(
                            packageName,
                            mAppConsentDao.getUidForInstalledPackageName(packageName),
                            false);
                case Flags.APPSEARCH_ONLY:
                    if (mFlags.getEnableAppsearchConsentData()) {
                        return mAppSearchConsentManager
                                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(packageName);
                    }
                    return true;
                case Flags.PPAPI_AND_ADEXT_SERVICE:
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "check if consent has been revoked for"
                                            + " app"));
                default:
                    LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                    return true;
            }
        }
    }

    /**
     * Clear consent data after an app was uninstalled.
     *
     * @param packageName the package name that had been uninstalled.
     * @param packageUid the package uid that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.clearConsentForUninstalledApp(packageName, packageUid),
                () -> mAdServicesManager.clearConsentForUninstalledApp(packageName, packageUid),
                () -> mAppSearchConsentManager.clearConsentForUninstalledApp(packageName),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "clear consent for uninstalled app"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Clear consent data after an app was uninstalled, but the package Uid is unavailable. This
     * could happen because the INTERACT_ACROSS_USERS_FULL permission is not available on Android
     * versions prior to T.
     *
     * <p><strong>This method should only be used for R/S back-compat scenarios.</strong>
     *
     * @param packageName the package name that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        Preconditions.checkStringNotEmpty(packageName, "Package name should not be empty");

        executeSettersByConsentSourceOfTruth(
                () -> mAppConsentDao.clearConsentForUninstalledApp(packageName),
                /* systemServiceSetter= */ null,
                () -> mAppSearchConsentManager.clearConsentForUninstalledApp(packageName),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "clear consent for uninstalled app"));
                },
                /* errorLogger= */ null);
    }

    /** Wipes out all the data gathered by Measurement API. */
    public void resetMeasurement() {
        mMeasurementImpl.deleteAllMeasurementData(List.of());
        // Log wipeout event triggered by consent flip to delete data of package
        WipeoutStatus wipeoutStatus = new WipeoutStatus();
        wipeoutStatus.setWipeoutType(WipeoutStatus.WipeoutType.CONSENT_FLIP);
        logWipeoutStats(wipeoutStatus);
    }

    /** Wipes out all the Enrollment data */
    @VisibleForTesting
    void resetEnrollment() {
        mEnrollmentDao.deleteAll();
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        executeSettersByConsentSourceOfTruth(
                () ->
                        mDatastore.put(
                                ConsentConstants.NOTIFICATION_DISPLAYED_ONCE,
                                wasNotificationDisplayed),
                () -> mAdServicesManager.recordNotificationDisplayed(wasNotificationDisplayed),
                () ->
                        mAppSearchConsentManager.recordNotificationDisplayed(
                                wasNotificationDisplayed),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
                    // Beta UX.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "store if beta notif was displayed"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ true,
                () -> mDatastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE),
                () -> mAdServicesManager.wasNotificationDisplayed(),
                () -> mAppSearchConsentManager.wasNotificationDisplayed(),
                () -> false, // Beta UX is never shown on R, so this info is not stored.
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_NOTIFICATION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed(boolean wasGaUxDisplayed) {
        executeSettersByConsentSourceOfTruth(
                () ->
                        mDatastore.put(
                                ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE,
                                wasGaUxDisplayed),
                () -> mAdServicesManager.recordGaUxNotificationDisplayed(wasGaUxDisplayed),
                () -> mAppSearchConsentManager.recordGaUxNotificationDisplayed(wasGaUxDisplayed),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
                    // GA UX.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "store if GA notification was displayed"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public Boolean wasGaUxNotificationDisplayed() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ true,
                () -> mDatastore.get(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE),
                () -> mAdServicesManager.wasGaUxNotificationDisplayed(),
                () -> mAppSearchConsentManager.wasGaUxNotificationDisplayed(),
                () -> false, // GA UX is never shown on R, so this info is not stored.
                /* errorLogger= */ null);
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordPasNotificationDisplayed(boolean wasPasDisplayed) {
        executeSettersByConsentSourceOfTruth(
                () ->
                        mDatastore.put(
                                ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE, wasPasDisplayed),
                () -> mAdServicesManager.recordPasNotificationDisplayed(wasPasDisplayed),
                () -> {
                    // APPSEARCH_ONLY is only set on S which has not implemented PAS updates.
                    throw new IllegalStateException(
                            getAppSearchExceptionMessage(
                                    /* illegalAction */ "store if PAS notification was displayed"));
                },
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which has not implemented PAS
                    // updates.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction */ "store if PAS notification was displayed"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Retrieves if PAS notification has been displayed.
     *
     * @return true if PAS Consent Notification was displayed, otherwise false.
     */
    public Boolean wasPasNotificationDisplayed() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ true,
                () -> mDatastore.get(ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE),
                () -> mAdServicesManager.wasPasNotificationDisplayed(),
                () -> false, // PAS update not supported on S yet
                () -> false, // PAS update not supported on R yet
                /* errorLogger= */ null);
    }

    /**
     * Retrieves the PP API default consent.
     *
     * @return true if the default consent is true, false otherwise.
     */
    public Boolean getDefaultConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.DEFAULT_CONSENT),
                () -> mAdServicesManager.getDefaultConsent(),
                () -> mAppSearchConsentManager.getConsent(ConsentConstants.DEFAULT_CONSENT),
                () -> false, // Beta UX never shown on R, so we don't store this info.
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_DEFAULT_CONSENT,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /**
     * Retrieves the topics default consent.
     *
     * @return true if the topics default consent is true, false otherwise.
     */
    public Boolean getTopicsDefaultConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.TOPICS_DEFAULT_CONSENT),
                () -> mAdServicesManager.getTopicsDefaultConsent(),
                () -> mAppSearchConsentManager.getConsent(ConsentConstants.TOPICS_DEFAULT_CONSENT),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "retrieve default topics consent"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Retrieves the FLEDGE default consent.
     *
     * @return true if the FLEDGE default consent is true, false otherwise.
     */
    public Boolean getFledgeDefaultConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.FLEDGE_DEFAULT_CONSENT),
                () -> mAdServicesManager.getFledgeDefaultConsent(),
                () -> mAppSearchConsentManager.getConsent(ConsentConstants.FLEDGE_DEFAULT_CONSENT),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "retrieve default FLEDGE consent"));
                },
                /* errorLogger= */ null);
    }

    /**
     * Retrieves the measurement default consent.
     *
     * @return true if the measurement default consent is true, false otherwise.
     */
    public Boolean getMeasurementDefaultConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT),
                () -> mAdServicesManager.getMeasurementDefaultConsent(),
                () ->
                        mAppSearchConsentManager.getConsent(
                                ConsentConstants.MEASUREMENT_DEFAULT_CONSENT),
                () -> mDatastore.get(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT),
                /* errorLogger= */ null);
    }

    /**
     * Retrieves the default AdId state.
     *
     * @return true if the AdId is enabled by default, false otherwise.
     */
    public Boolean getDefaultAdIdState() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.DEFAULT_AD_ID_STATE),
                () -> mAdServicesManager.getDefaultAdIdState(),
                () -> mAppSearchConsentManager.getConsent(ConsentConstants.DEFAULT_AD_ID_STATE),
                () -> mDatastore.get(ConsentConstants.DEFAULT_AD_ID_STATE),
                /* errorLogger= */ null);
    }

    /** Saves the default consent bit to data stores based on source of truth. */
    public void recordDefaultConsent(boolean defaultConsent) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.DEFAULT_CONSENT, defaultConsent),
                () -> mAdServicesManager.recordDefaultConsent(defaultConsent),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.DEFAULT_CONSENT, defaultConsent),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
                    // Beta UX.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "record default beta consent"));
                },
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_DEFAULT_CONSENT,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /** Saves the topics default consent bit to data stores based on source of truth. */
    public void recordTopicsDefaultConsent(boolean defaultConsent) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.TOPICS_DEFAULT_CONSENT, defaultConsent),
                () -> mAdServicesManager.recordTopicsDefaultConsent(defaultConsent),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.TOPICS_DEFAULT_CONSENT, defaultConsent),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "record default topics consent"));
                },
                /* errorLogger= */ null);
    }

    /** Saves the FLEDGE default consent bit to data stores based on source of truth. */
    public void recordFledgeDefaultConsent(boolean defaultConsent) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.FLEDGE_DEFAULT_CONSENT, defaultConsent),
                () -> mAdServicesManager.recordFledgeDefaultConsent(defaultConsent),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.FLEDGE_DEFAULT_CONSENT, defaultConsent),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(
                                    /* illegalAction= */ "record default FLEDGE consent"));
                },
                /* errorLogger= */ null);
    }

    /** Saves the measurement default consent bit to data stores based on source of truth. */
    public void recordMeasurementDefaultConsent(boolean defaultConsent) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, defaultConsent),
                () -> mAdServicesManager.recordMeasurementDefaultConsent(defaultConsent),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, defaultConsent),
                () -> // Same as PPAPI_ONLY. Rollback-safety not required.
                mDatastore.put(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, defaultConsent),
                /* errorLogger= */ null);
    }

    /** Saves the default AdId state bit to data stores based on source of truth. */
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.DEFAULT_AD_ID_STATE, defaultAdIdState),
                () -> mAdServicesManager.recordDefaultAdIdState(defaultAdIdState),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.DEFAULT_AD_ID_STATE, defaultAdIdState),
                () -> // Same as PPAPI_ONLY. Rollback-safety not required.
                mDatastore.put(ConsentConstants.DEFAULT_AD_ID_STATE, defaultAdIdState),
                /* errorLogger= */ null);
    }

    private void setPrivacySandboxFeatureTypeInApp(PrivacySandboxFeatureType currentFeatureType)
            throws IOException {
        for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
            if (featureType.name().equals(currentFeatureType.name())) {
                mDatastore.put(featureType.name(), true);
            } else {
                mDatastore.put(featureType.name(), false);
            }
        }
    }

    /** Set the current privacy sandbox feature. */
    public void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType currentFeatureType) {
        executeSettersByConsentSourceOfTruth(
                () -> setPrivacySandboxFeatureTypeInApp(currentFeatureType),
                () -> mAdServicesManager.setCurrentPrivacySandboxFeature(currentFeatureType.name()),
                () -> mAppSearchConsentManager.setCurrentPrivacySandboxFeature(currentFeatureType),
                () -> setPrivacySandboxFeatureTypeInApp(currentFeatureType),
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    /** Saves information to the storage that user interacted with consent manually. */
    public void recordUserManualInteractionWithConsent(@UserManualInteraction int interaction) {
        executeSettersByConsentSourceOfTruth(
                () -> storeUserManualInteractionToPpApi(interaction, mDatastore),
                () -> mAdServicesManager.recordUserManualInteractionWithConsent(interaction),
                () -> mAppSearchConsentManager.recordUserManualInteractionWithConsent(interaction),
                () -> mAdExtDataManager.setManualInteractionWithConsentStatus(interaction),
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_MANUAL_CONSENT_INTERACTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    private PrivacySandboxFeatureType getPrivacySandboxFeatureFromApp() throws IOException {
        for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
            if (Boolean.TRUE.equals(mDatastore.get(featureType.name()))) {
                return featureType;
            }
        }
        return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
    }

    private PrivacySandboxFeatureType getPrivacySandboxFeatureFromSystemService() {
        for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
            if (mAdServicesManager.getCurrentPrivacySandboxFeature().equals(featureType.name())) {
                return featureType;
            }
        }
        return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
    }

    /**
     * Get the current privacy sandbox feature.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED,
                this::getPrivacySandboxFeatureFromApp,
                this::getPrivacySandboxFeatureFromSystemService,
                () -> mAppSearchConsentManager.getCurrentPrivacySandboxFeature(),
                this::getPrivacySandboxFeatureFromApp,
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    private static void storeUserManualInteractionToPpApi(
            @UserManualInteraction int interaction, BooleanFileDatastore datastore)
            throws IOException {
        switch (interaction) {
            case NO_MANUAL_INTERACTIONS_RECORDED:
                datastore.put(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED, false);
                break;
            case UNKNOWN:
                datastore.remove(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                break;
            case MANUAL_INTERACTIONS_RECORDED:
                datastore.put(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED, true);
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("InteractionId < %d > can not be handled.", interaction));
        }
    }

    private int getUserManualInteractionWithConsentInternal() {
        Boolean manualInteractionWithConsent =
                mDatastore.get(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
        if (manualInteractionWithConsent == null) {
            return UNKNOWN;
        } else if (Boolean.TRUE.equals(manualInteractionWithConsent)) {
            return MANUAL_INTERACTIONS_RECORDED;
        } else {
            return NO_MANUAL_INTERACTIONS_RECORDED;
        }
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    public @UserManualInteraction int getUserManualInteractionWithConsent() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ UNKNOWN,
                this::getUserManualInteractionWithConsentInternal,
                () -> mAdServicesManager.getUserManualInteractionWithConsent(),
                () -> mAppSearchConsentManager.getUserManualInteractionWithConsent(),
                () -> mAdExtDataManager.getManualInteractionWithConsentStatus(),
                (e) ->
                        ErrorLogUtil.e(
                                e,
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATASTORE_EXCEPTION_WHILE_RECORDING_MANUAL_CONSENT_INTERACTION,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
    }

    @VisibleForTesting
    static BooleanFileDatastore createAndInitializeDataStore(@NonNull Context context) {
        BooleanFileDatastore booleanFileDatastore =
                new BooleanFileDatastore(
                        context,
                        ConsentConstants.STORAGE_XML_IDENTIFIER,
                        ConsentConstants.STORAGE_VERSION);

        try {
            booleanFileDatastore.initialize();
            // TODO(b/259607624): implement a method in the datastore which would support
            // this exact scenario - if the value is null, return default value provided
            // in the parameter (similar to SP apply etc.)
            if (booleanFileDatastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE) == null) {
                booleanFileDatastore.put(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (booleanFileDatastore.get(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE)
                    == null) {
                booleanFileDatastore.put(ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
            }
            if (booleanFileDatastore.get(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED) == null) {
                booleanFileDatastore.put(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED, false);
            }
            if (booleanFileDatastore.get(ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE)
                    == null) {
                booleanFileDatastore.put(ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE, false);
            }
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Failed to initialize the File Datastore!", e);
        }

        return booleanFileDatastore;
    }

    // Handle different migration requests based on current consent source of Truth
    // PPAPI_ONLY: reset the shared preference to reset status of migrating consent from PPAPI to
    //             system server.
    // PPAPI_AND_SYSTEM_SERVER: migrate consent from PPAPI to system server.
    // SYSTEM_SERVER_ONLY: migrate consent from PPAPI to system server and clear PPAPI consent
    @VisibleForTesting
    static void handleConsentMigrationIfNeeded(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            AdServicesManager adServicesManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger,
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth) {
        Objects.requireNonNull(context);
        // On R/S, handleConsentMigrationIfNeeded should never be executed.
        // It is a T+ feature. On T+, this function should only execute if it's within the
        // AdServices
        // APK and not ExtServices. So check if it's within ExtServices, and bail out if that's the
        // case on any platform.
        String packageName = context.getPackageName();
        if (packageName != null && packageName.endsWith(ADEXTSERVICES_PACKAGE_NAME_SUFFIX)) {
            LogUtil.d("Aborting attempt to migrate consent in ExtServices");
            return;
        }
        Objects.requireNonNull(datastore);
        if (consentSourceOfTruth == Flags.PPAPI_AND_SYSTEM_SERVER
                || consentSourceOfTruth == Flags.SYSTEM_SERVER_ONLY) {
            Objects.requireNonNull(adServicesManager);
        }

        switch (consentSourceOfTruth) {
            case Flags.PPAPI_ONLY:
                // Technically we only need to reset the SHARED_PREFS_KEY_HAS_MIGRATED bit once.
                // What we need is clearIfSet operation which is not available in SP. So here we
                // always reset the bit since otherwise we need to read the SP to read the value and
                // the clear the value.
                // The only flow we would do are:
                // Case 1: DUAL-> PPAPI if there is a bug in System Server
                // Case 2: DUAL -> SYSTEM_SERVER_ONLY: if everything goes smoothly.
                resetSharedPreference(context, ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED);
                break;
            case Flags.PPAPI_AND_SYSTEM_SERVER:
                migratePpApiConsentToSystemService(
                        context, datastore, adServicesManager, statsdAdServicesLogger);
                break;
            case Flags.SYSTEM_SERVER_ONLY:
                migratePpApiConsentToSystemService(
                        context, datastore, adServicesManager, statsdAdServicesLogger);
                clearPpApiConsent(context, datastore);
                break;
                // If this is a S device, the consent source of truth is always APPSEARCH_ONLY.
            case Flags.APPSEARCH_ONLY:
                // If this is a R device, the consent source of truth is always
                // PPAPI_AND_ADEXT_SERVICE.
            case Flags.PPAPI_AND_ADEXT_SERVICE:
                break;
            default:
                break;
        }
    }

    @VisibleForTesting
    void setConsentToPpApi(boolean isGiven) throws IOException {
        mDatastore.put(ConsentConstants.CONSENT_KEY, isGiven);
    }

    @VisibleForTesting
    void setConsentPerApiToPpApi(AdServicesApiType apiType, boolean isGiven) throws IOException {
        mDatastore.put(apiType.toPpApiDatastoreKey(), isGiven);
    }

    @VisibleForTesting
    boolean getConsentFromPpApi() {
        return mDatastore.get(ConsentConstants.CONSENT_KEY);
    }

    @VisibleForTesting
    boolean getConsentPerApiFromPpApi(AdServicesApiType apiType) {
        return Boolean.TRUE.equals(mDatastore.get(apiType.toPpApiDatastoreKey()));
    }

    // Set the aggregated consent so that after the rollback of the module
    // and the flag which controls the consent flow everything works as expected.
    // The problematic edge case which is covered:
    // T1: AdServices is installed in pre-GA UX version and the consent is given
    // T2: AdServices got upgraded to GA UX binary and GA UX feature flag is enabled
    // T3: Consent for the Topics API got revoked
    // T4: AdServices got rolledback and the feature flags which controls consent flow
    // (SYSTEM_SERVER_ONLY and DUAL_WRITE) also got rolledback
    // T5: Restored consent should be revoked
    @VisibleForTesting
    void setAggregatedConsentToPpApi() throws IOException {
        if (getUx() == U18_UX) {
            // The edge case does not apply to U18 UX.
            return;
        }
        if (getConsent(AdServicesApiType.TOPICS).isGiven()
                && getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                && getConsent(AdServicesApiType.FLEDGE).isGiven()) {
            setConsentToPpApi(true);
        } else {
            setConsentToPpApi(false);
        }
    }

    // Reset data for the specific AdServicesApiType
    @VisibleForTesting
    void resetByApi(AdServicesApiType apiType) throws IOException {
        switch (apiType) {
            case TOPICS:
                resetTopicsAndBlockedTopics();
                break;
            case FLEDGE:
                resetAppsAndBlockedApps();
                resetUserProfileId();
                break;
            case MEASUREMENTS:
                resetMeasurement();
                break;
        }
    }

    private void resetUserProfileId() {
        mUserProfileIdManager.deleteId();
    }

    @VisibleForTesting
    static void setConsentToSystemServer(
            @NonNull AdServicesManager adServicesManager, boolean isGiven) {
        Objects.requireNonNull(adServicesManager);

        ConsentParcel consentParcel =
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(isGiven)
                        .build();
        adServicesManager.setConsent(consentParcel);
    }

    static void setPerApiConsentToSystemServer(
            @NonNull AdServicesManager adServicesManager,
            @ConsentParcel.ConsentApiType int consentApiType,
            boolean isGiven) {
        Objects.requireNonNull(adServicesManager);

        if (isGiven) {
            adServicesManager.setConsent(ConsentParcel.createGivenConsent(consentApiType));
        } else {
            adServicesManager.setConsent(ConsentParcel.createRevokedConsent(consentApiType));
        }
    }

    @VisibleForTesting
    static boolean getPerApiConsentFromSystemServer(
            @NonNull AdServicesManager adServicesManager,
            @ConsentParcel.ConsentApiType int consentApiType) {
        Objects.requireNonNull(adServicesManager);
        return adServicesManager.getConsent(consentApiType).isIsGiven();
    }

    @VisibleForTesting
    static boolean getConsentFromSystemServer(@NonNull AdServicesManager adServicesManager) {
        Objects.requireNonNull(adServicesManager);
        return getPerApiConsentFromSystemServer(adServicesManager, ConsentParcel.ALL_API);
    }

    // Perform a one-time migration to migrate existing PPAPI Consent
    @VisibleForTesting
    // Suppress lint warning for context.getUser in R since this code is unused in R
    @SuppressWarnings("NewApi")
    static void migratePpApiConsentToSystemService(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @NonNull AdServicesManager adServicesManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(adServicesManager);

        AppConsents appConsents = null;
        try {
            // Exit if migration has happened.
            SharedPreferences sharedPreferences =
                    context.getSharedPreferences(
                            ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
            // If we migrated data to system server either from PPAPI or from AppSearch, do not
            // attempt another migration of data to system server.
            boolean shouldSkipMigration =
                    sharedPreferences.getBoolean(
                                    ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED,
                                    /* default= */ false)
                            || sharedPreferences.getBoolean(
                                    ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED,
                                    /* default= */ false)
                            || sharedPreferences.getBoolean(
                                    ConsentConstants
                                            .SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER,
                                    /* defValue= */ false);
            if (shouldSkipMigration) {
                LogUtil.v(
                        "Consent migration has happened to user %d, skip...",
                        context.getUser().getIdentifier());
                return;
            }
            LogUtil.d("Started migrating Consent from PPAPI to System Service");

            Boolean consentKey = Boolean.TRUE.equals(datastore.get(ConsentConstants.CONSENT_KEY));

            // Migrate Consent and Notification Displayed to System Service.
            // Set consent enabled only when value is TRUE. FALSE and null are regarded as disabled.
            setConsentToSystemServer(adServicesManager, Boolean.TRUE.equals(consentKey));

            // Set notification displayed only when value is TRUE. FALSE and null are regarded as
            // not displayed.
            if (Boolean.TRUE.equals(datastore.get(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE))) {
                adServicesManager.recordNotificationDisplayed(true);
            }

            Boolean manualInteractionRecorded =
                    datastore.get(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
            if (manualInteractionRecorded != null) {
                adServicesManager.recordUserManualInteractionWithConsent(
                        manualInteractionRecorded ? 1 : -1);
            }

            // Save migration has happened into shared preferences.
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, true);
            appConsents =
                    AppConsents.builder()
                            .setMsmtConsent(consentKey)
                            .setFledgeConsent(consentKey)
                            .setTopicsConsent(consentKey)
                            .build();

            if (editor.commit()) {
                LogUtil.d("Finished migrating Consent from PPAPI to System Service");
                statsdAdServicesLogger.logConsentMigrationStats(
                        getConsentManagerStatsForLogging(
                                appConsents,
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED,
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE,
                                context));
            } else {
                LogUtil.e(
                        "Finished migrating Consent from PPAPI to System Service but shared"
                                + " preference is not updated.");
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
                statsdAdServicesLogger.logConsentMigrationStats(
                        getConsentManagerStatsForLogging(
                                appConsents,
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED,
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE,
                                context));
            }
        } catch (Exception e) {
            LogUtil.e("PPAPI consent data migration failed: ", e);
            statsdAdServicesLogger.logConsentMigrationStats(
                    getConsentManagerStatsForLogging(
                            appConsents,
                            ConsentMigrationStats.MigrationStatus.FAILURE,
                            ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE,
                            context));
        }
    }

    // Clear PPAPI Consent if fully migrated to use system server consent. This is because system
    // consent cannot be migrated back to PPAPI. This data clearing should only happen once.
    @VisibleForTesting
    static void clearPpApiConsent(
            @NonNull Context context, @NonNull BooleanFileDatastore datastore) {
        // Exit if PPAPI consent has cleared.
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(
                        ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(
                ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false)) {
            return;
        }

        LogUtil.d("Started clearing Consent in PPAPI.");

        try {
            datastore.clear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear PPAPI Consent", e);
        }

        // Save that PPAPI consent has cleared into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);

        if (editor.commit()) {
            LogUtil.d("Finished clearing Consent in PPAPI.");
        } else {
            LogUtil.e("Finished clearing Consent in PPAPI but shared preference is not updated.");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
        }
    }

    // Set the shared preference to false for given key.
    @VisibleForTesting
    static void resetSharedPreference(
            @NonNull Context context, @NonNull String sharedPreferenceKey) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(sharedPreferenceKey);

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(
                        ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(sharedPreferenceKey, false);

        if (editor.commit()) {
            LogUtil.d("Finished resetting shared preference for " + sharedPreferenceKey);
        } else {
            LogUtil.e("Failed to reset shared preference for " + sharedPreferenceKey);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_RESET_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
        }
    }

    // To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources.
    // To write to system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
    @VisibleForTesting
    void setConsentToSourceOfTruth(boolean isGiven) {
        executeSettersByConsentSourceOfTruth(
                () -> setConsentToPpApi(isGiven),
                () -> setConsentToSystemServer(mAdServicesManager, isGiven),
                () ->
                        mAppSearchConsentManager.setConsent(
                                ConsentConstants.CONSENT_KEY_FOR_ALL, isGiven),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which should never show
                    // Beta UX.
                    throw new IllegalStateException(
                            getAdExtExceptionMessage(/* illegalAction= */ "set beta consent"));
                },
                /* errorLogger= */ null);
    }

    @VisibleForTesting
    boolean getConsentFromSourceOfTruth() {
        return executeGettersByConsentSourceOfTruth(
                false,
                () -> getConsentFromPpApi(),
                () -> getConsentFromSystemServer(mAdServicesManager),
                () -> mAppSearchConsentManager.getConsent(ConsentConstants.CONSENT_KEY_FOR_ALL),
                () -> false, // Beta UX never shown on R, so we don't store this info.
                /* errorLogger= */ null);
    }

    @VisibleForTesting
    boolean getPerApiConsentFromSourceOfTruth(AdServicesApiType apiType) {
        return executeGettersByConsentSourceOfTruth(
                false,
                () -> getConsentPerApiFromPpApi(apiType),
                () ->
                        getPerApiConsentFromSystemServer(
                                mAdServicesManager, apiType.toConsentApiType()),
                () -> mAppSearchConsentManager.getConsent(apiType.toPpApiDatastoreKey()),
                // Only Measurement API is supported on Android R.
                () ->
                        apiType == AdServicesApiType.MEASUREMENTS
                                && mAdExtDataManager.getMsmtConsent(),
                /* errorLogger= */ null);
    }

    @VisibleForTesting
    void setPerApiConsentToSourceOfTruth(boolean isGiven, AdServicesApiType apiType) {
        executeSettersByConsentSourceOfTruth(
                () -> {
                    setConsentPerApiToPpApi(apiType, isGiven);
                    setAggregatedConsentToPpApi();
                },
                () ->
                        setPerApiConsentToSystemServer(
                                mAdServicesManager, apiType.toConsentApiType(), isGiven),
                () -> mAppSearchConsentManager.setConsent(apiType.toPpApiDatastoreKey(), isGiven),
                () -> {
                    // PPAPI_AND_ADEXT_SERVICE is only set on R which supports only
                    // Measurement. There should never be a call to set consent for other PPAPIs.
                    if (apiType != AdServicesApiType.MEASUREMENTS) {
                        throw new IllegalStateException(
                                getAdExtExceptionMessage(
                                        /* illegalAction= */ "set consent for a non-msmt API"));
                    }
                    mAdExtDataManager.setMsmtConsent(isGiven);
                },
                /* errorLogger= */ null);
    }

    /**
     * This method handles migration of consent data from AppSearch to AdServices. Consent data is
     * written to AppSearch on S- and ported to AdServices after OTA to T. If any new data is
     * written for consent, we need to make sure it is migrated correctly post-OTA in this method.
     */
    @VisibleForTesting
    static void handleConsentMigrationFromAppSearchIfNeeded(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @NonNull AppConsentDao appConsentDao,
            @NonNull AppSearchConsentManager appSearchConsentManager,
            @NonNull AdServicesManager adServicesManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appSearchConsentManager);
        LogUtil.d("Check migrating Consent from AppSearch to PPAPI and System Service");

        // On R/S, this function should never be executed because AppSearch to PPAPI and
        // System Server migration is a T+ feature. On T+, this function should only execute
        // if it's within the AdServices APK and not ExtServices. So check if it's within
        // ExtServices, and bail out if that's the case on any platform.
        String packageName = context.getPackageName();
        if (packageName != null && packageName.endsWith(ADEXTSERVICES_PACKAGE_NAME_SUFFIX)) {
            LogUtil.d(
                    "Aborting attempt to migrate AppSearch to PPAPI and System Service in"
                            + " ExtServices");
            return;
        }

        AppConsents appConsents = null;
        try {
            // This should be called only once after OTA (if flag is enabled). If we did not record
            // showing the notification on T+ yet and we have shown the notification on S- (as
            // recorded
            // in AppSearch), initialize T+ consent data so that we don't show notification twice
            // (after
            // OTA upgrade).
            SharedPreferences sharedPreferences =
                    context.getSharedPreferences(
                            ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
            // If we did not migrate notification data, we should not attempt to migrate anything.
            if (!appSearchConsentManager.migrateConsentDataIfNeeded(
                    sharedPreferences, datastore, adServicesManager, appConsentDao)) {
                LogUtil.d("Skipping consent migration from AppSearch");
                return;
            }

            // Migrate Consent for all APIs and per API to PP API and System Service.
            appConsents =
                    migrateAppSearchConsents(appSearchConsentManager, adServicesManager, datastore);

            // Record interactions data only if we recorded an interaction in AppSearch.
            int manualInteractionRecorded =
                    appSearchConsentManager.getUserManualInteractionWithConsent();
            if (manualInteractionRecorded == MANUAL_INTERACTIONS_RECORDED) {
                // Initialize PP API datastore.
                storeUserManualInteractionToPpApi(manualInteractionRecorded, datastore);
                // Initialize system service.
                adServicesManager.recordUserManualInteractionWithConsent(manualInteractionRecorded);
            }

            // Record that we migrated consent data from AppSearch. We write the notification data
            // to system server and perform migration only if system server did not record any
            // notification having been displayed.
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED, true);
            if (editor.commit()) {
                LogUtil.d("Finished migrating Consent from AppSearch to PPAPI + System Service");
                statsdAdServicesLogger.logConsentMigrationStats(
                        getConsentManagerStatsForLogging(
                                appConsents,
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED,
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE,
                                context));
            } else {
                LogUtil.e(
                        "Finished migrating Consent from AppSearch to PPAPI + System Service "
                                + "but shared preference is not updated.");
                statsdAdServicesLogger.logConsentMigrationStats(
                        getConsentManagerStatsForLogging(
                                appConsents,
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED,
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE,
                                context));
            }
        } catch (IOException e) {
            LogUtil.e("AppSearch consent data migration failed: ", e);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_SEARCH_DATA_MIGRATION_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX);
            statsdAdServicesLogger.logConsentMigrationStats(
                    getConsentManagerStatsForLogging(
                            appConsents,
                            ConsentMigrationStats.MigrationStatus.FAILURE,
                            ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE,
                            context));
        }
    }

    /**
     * This method returns and migrates the consent states (opt in/out) for all PPAPIs, each API and
     * their default consent values.
     */
    @VisibleForTesting
    static AppConsents migrateAppSearchConsents(
            AppSearchConsentManager appSearchConsentManager,
            AdServicesManager adServicesManager,
            BooleanFileDatastore datastore)
            throws IOException {
        boolean consented = appSearchConsentManager.getConsent(ConsentConstants.CONSENT_KEY);
        datastore.put(ConsentConstants.CONSENT_KEY, consented);
        adServicesManager.setConsent(getConsentParcel(ConsentParcel.ALL_API, consented));

        // Record default consents.
        boolean defaultConsent =
                appSearchConsentManager.getConsent(ConsentConstants.DEFAULT_CONSENT);
        datastore.put(ConsentConstants.DEFAULT_CONSENT, defaultConsent);
        adServicesManager.recordDefaultConsent(defaultConsent);
        boolean topicsDefaultConsented =
                appSearchConsentManager.getConsent(ConsentConstants.TOPICS_DEFAULT_CONSENT);
        datastore.put(ConsentConstants.TOPICS_DEFAULT_CONSENT, topicsDefaultConsented);
        adServicesManager.recordTopicsDefaultConsent(topicsDefaultConsented);
        boolean fledgeDefaultConsented =
                appSearchConsentManager.getConsent(ConsentConstants.FLEDGE_DEFAULT_CONSENT);
        datastore.put(ConsentConstants.FLEDGE_DEFAULT_CONSENT, fledgeDefaultConsented);
        adServicesManager.recordFledgeDefaultConsent(fledgeDefaultConsented);
        boolean measurementDefaultConsented =
                appSearchConsentManager.getConsent(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT);
        datastore.put(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT, measurementDefaultConsented);
        adServicesManager.recordMeasurementDefaultConsent(measurementDefaultConsented);

        // Record per API consents.
        boolean topicsConsented =
                appSearchConsentManager.getConsent(AdServicesApiType.TOPICS.toPpApiDatastoreKey());
        datastore.put(AdServicesApiType.TOPICS.toPpApiDatastoreKey(), topicsConsented);
        setPerApiConsentToSystemServer(
                adServicesManager, AdServicesApiType.TOPICS.toConsentApiType(), topicsConsented);
        boolean fledgeConsented =
                appSearchConsentManager.getConsent(AdServicesApiType.FLEDGE.toPpApiDatastoreKey());
        datastore.put(AdServicesApiType.FLEDGE.toPpApiDatastoreKey(), fledgeConsented);
        setPerApiConsentToSystemServer(
                adServicesManager, AdServicesApiType.FLEDGE.toConsentApiType(), fledgeConsented);
        boolean measurementConsented =
                appSearchConsentManager.getConsent(
                        AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey());
        datastore.put(AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(), measurementConsented);
        setPerApiConsentToSystemServer(
                adServicesManager,
                AdServicesApiType.MEASUREMENTS.toConsentApiType(),
                measurementConsented);
        return AppConsents.builder()
                .setMsmtConsent(measurementConsented)
                .setTopicsConsent(topicsConsented)
                .setFledgeConsent(fledgeConsented)
                .build();
    }

    @NonNull
    private static ConsentParcel getConsentParcel(
            @NonNull Integer apiType, @NonNull Boolean consented) {
        return new ConsentParcel.Builder().setConsentApiType(apiType).setIsGiven(consented).build();
    }

    /**
     * Represents revoked consent as internally determined by the PP APIs.
     *
     * <p>This is an internal-only exception and is not meant to be returned to external callers.
     */
    public static class RevokedConsentException extends IllegalStateException {
        public static final String REVOKED_CONSENT_ERROR_MESSAGE =
                "Error caused by revoked user consent";

        /** Creates an instance of a {@link RevokedConsentException}. */
        public RevokedConsentException() {
            super(REVOKED_CONSENT_ERROR_MESSAGE);
        }
    }

    private void asyncExecute(Runnable runnable) {
        AdServicesExecutors.getBackgroundExecutor().execute(runnable);
    }

    private void logWipeoutStats(WipeoutStatus wipeoutStatus) {
        AdServicesLoggerImpl.getInstance()
                .logMeasurementWipeoutStats(
                        new MeasurementWipeoutStats.Builder()
                                .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                                .setWipeoutType(wipeoutStatus.getWipeoutType().getValue())
                                .build());
    }

    /** Returns whether the isAdIdEnabled bit is true based on consent_source_of_truth. */
    public Boolean isAdIdEnabled() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.IS_AD_ID_ENABLED),
                () -> mAdServicesManager.isAdIdEnabled(),
                () -> mAppSearchConsentManager.isAdIdEnabled(),
                () -> mDatastore.get(ConsentConstants.IS_AD_ID_ENABLED),
                /* errorLogger= */ null);
    }

    /** Set the AdIdEnabled bit to storage based on consent_source_of_truth. */
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.IS_AD_ID_ENABLED, isAdIdEnabled),
                () -> mAdServicesManager.setAdIdEnabled(isAdIdEnabled),
                () -> mAppSearchConsentManager.setAdIdEnabled(isAdIdEnabled),
                // Doesn't need to be rollback-safe. Same as PPAPI_ONLY.
                () -> mDatastore.put(ConsentConstants.IS_AD_ID_ENABLED, isAdIdEnabled),
                /* errorLogger= */ null);
    }

    /** Returns whether the isU18Account bit is true based on consent_source_of_truth. */
    public Boolean isU18Account() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.IS_U18_ACCOUNT),
                () -> mAdServicesManager.isU18Account(),
                () -> mAppSearchConsentManager.isU18Account(),
                () -> mAdExtDataManager.getIsU18Account(),
                /* errorLogger= */ null);
    }

    /** Set the U18Account bit to storage based on consent_source_of_truth. */
    public void setU18Account(boolean isU18Account) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.IS_U18_ACCOUNT, isU18Account),
                () -> mAdServicesManager.setU18Account(isU18Account),
                () -> mAppSearchConsentManager.setU18Account(isU18Account),
                () -> mAdExtDataManager.setIsU18Account(isU18Account),
                /* errorLogger= */ null);
    }

    /** Returns whether the isEntryPointEnabled bit is true based on consent_source_of_truth. */
    public Boolean isEntryPointEnabled() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.IS_ENTRY_POINT_ENABLED),
                () -> mAdServicesManager.isEntryPointEnabled(),
                () -> mAppSearchConsentManager.isEntryPointEnabled(),
                () -> mDatastore.get(ConsentConstants.IS_ENTRY_POINT_ENABLED),
                /* errorLogger= */ null);
    }

    /** Set the EntryPointEnabled bit to storage based on consent_source_of_truth. */
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.IS_ENTRY_POINT_ENABLED, isEntryPointEnabled),
                () -> mAdServicesManager.setEntryPointEnabled(isEntryPointEnabled),
                () -> mAppSearchConsentManager.setEntryPointEnabled(isEntryPointEnabled),
                // Doesn't need to be rollback-safe. Same as PPAPI_ONLY.
                () -> mDatastore.put(ConsentConstants.IS_ENTRY_POINT_ENABLED, isEntryPointEnabled),
                /* errorLogger= */ null);
    }

    /** Returns whether the isAdultAccount bit is true based on consent_source_of_truth. */
    public Boolean isAdultAccount() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ false,
                () -> mDatastore.get(ConsentConstants.IS_ADULT_ACCOUNT),
                () -> mAdServicesManager.isAdultAccount(),
                () -> mAppSearchConsentManager.isAdultAccount(),
                () -> mAdExtDataManager.getIsAdultAccount(),
                /* errorLogger= */ null);
    }

    /** Set the AdultAccount bit to storage based on consent_source_of_truth. */
    public void setAdultAccount(boolean isAdultAccount) {
        executeSettersByConsentSourceOfTruth(
                () -> mDatastore.put(ConsentConstants.IS_ADULT_ACCOUNT, isAdultAccount),
                () -> mAdServicesManager.setAdultAccount(isAdultAccount),
                () -> mAppSearchConsentManager.setAdultAccount(isAdultAccount),
                () -> mAdExtDataManager.setIsAdultAccount(isAdultAccount),
                /* errorLogger= */ null);
    }

    /**
     * Returns whether the wasU18NotificationDisplayed bit is true based on consent_source_of_truth.
     */
    public Boolean wasU18NotificationDisplayed() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ true,
                () -> mDatastore.get(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED),
                () -> mAdServicesManager.wasU18NotificationDisplayed(),
                () -> mAppSearchConsentManager.wasU18NotificationDisplayed(),
                () -> // On Android R only U18 notification is allowed to be displayed.
                mAdExtDataManager.getNotificationDisplayed(),
                /* errorLogger= */ null);
    }

    /** Set the U18NotificationDisplayed bit to storage based on consent_source_of_truth. */
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        executeSettersByConsentSourceOfTruth(
                () ->
                        mDatastore.put(
                                ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED,
                                wasU18NotificationDisplayed),
                () -> mAdServicesManager.setU18NotificationDisplayed(wasU18NotificationDisplayed),
                () ->
                        mAppSearchConsentManager.setU18NotificationDisplayed(
                                wasU18NotificationDisplayed),
                () -> // On Android R only U18 notification is allowed to be displayed.
                mAdExtDataManager.setNotificationDisplayed(wasU18NotificationDisplayed),
                /* errorLogger= */ null);
    }

    private PrivacySandboxUxCollection convertUxString(String uxString) {
        return Stream.of(PrivacySandboxUxCollection.values())
                .filter(ux -> uxString.equals(ux.toString()))
                .findFirst()
                .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    /** Returns current UX based on consent_source_of_truth. */
    public PrivacySandboxUxCollection getUx() {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ PrivacySandboxUxCollection.UNSUPPORTED_UX,
                () -> mUxStatesDao.getUx(),
                () -> convertUxString(mAdServicesManager.getUx()),
                () -> mAppSearchConsentManager.getUx(),
                () -> {
                    PrivacySandboxUxCollection ux = mUxStatesDao.getUx();

                    // Workaround to make getUx() rollback safe on R. Rollback could change
                    // ux enum to UNSUPPORTED_UX after daily ping and before the
                    // notification is displayed.

                    if (ux == PrivacySandboxUxCollection.UNSUPPORTED_UX) {
                        // Use getIsU18Account to infer ux enum since getIsU18Account is
                        // rollback safe
                        if (mFlags.getU18UxEnabled() && mAdExtDataManager.getIsU18Account()) {
                            ux = PrivacySandboxUxCollection.U18_UX;
                        } else if (mAdExtDataManager.getIsAdultAccount()) {
                            // Use getIsAdultAccount to infer ux enum since getIsAdultAccount is
                            // rollback safe
                            ux = PrivacySandboxUxCollection.RVC_UX;
                        }
                    }
                    return ux;
                },
                /* errorLogger= */ null);
    }

    /** Set the current UX to storage based on consent_source_of_truth. */
    public void setUx(PrivacySandboxUxCollection ux) {
        executeSettersByConsentSourceOfTruth(
                () -> mUxStatesDao.setUx(ux),
                () -> mAdServicesManager.setUx(ux.toString()),
                () -> mAppSearchConsentManager.setUx(ux),
                () -> // Same as PPAPI_ONLY. Doesn't need to be rollback safe
                mUxStatesDao.setUx(ux),
                /* errorLogger= */ null);
    }

    private PrivacySandboxEnrollmentChannelCollection convertEnrollmentChannelString(
            PrivacySandboxUxCollection ux, String enrollmentChannelString) {
        if (enrollmentChannelString == null) {
            return null;
        }
        return Stream.of(ux.getEnrollmentChannelCollection())
                .filter(channel -> enrollmentChannelString.equals(channel.toString()))
                .findFirst()
                .orElse(null);
    }

    /** Returns current enrollment channel based on consent_source_of_truth. */
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            PrivacySandboxUxCollection ux) {
        return executeGettersByConsentSourceOfTruth(
                /* defaultReturn= */ null,
                () -> mUxStatesDao.getEnrollmentChannel(ux),
                () -> convertEnrollmentChannelString(ux, mAdServicesManager.getEnrollmentChannel()),
                () -> mAppSearchConsentManager.getEnrollmentChannel(ux),
                () -> mUxStatesDao.getEnrollmentChannel(ux),
                /* errorLogger= */ null);
    }

    /** Set the current enrollment channel to storage based on consent_source_of_truth. */
    public void setEnrollmentChannel(
            PrivacySandboxUxCollection ux, PrivacySandboxEnrollmentChannelCollection channel) {
        executeSettersByConsentSourceOfTruth(
                () -> mUxStatesDao.setEnrollmentChannel(ux, channel),
                () -> mAdServicesManager.setEnrollmentChannel(channel.toString()),
                () -> mAppSearchConsentManager.setEnrollmentChannel(ux, channel),
                () -> // Same as PPAPI_ONLY. Doesn't need to be rollback safe
                mUxStatesDao.setEnrollmentChannel(ux, channel),
                /* errorLogger= */ null);
    }

    /**
     * get pas conset for fledge, pasUxEnable flag has checked iseea, thus we don't need to check
     * this again
     */
    public boolean isPasFledgeConsentGiven() {
        if (mFlags.getConsentManagerDebugMode()) {
            return true;
        }

        return mFlags.getPasUxEnabled()
                && wasPasNotificationDisplayed()
                && getConsent(AdServicesApiType.FLEDGE).isGiven();
    }

    /** get pas conset for measurement */
    public boolean isPasMeasurementConsentGiven() {
        if (mFlags.getConsentManagerDebugMode()) {
            return true;
        }

        return mFlags.getPasUxEnabled()
                && wasPasNotificationDisplayed()
                && getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }

    @FunctionalInterface
    interface ThrowableSetter {
        void apply() throws IOException, RuntimeException;
    }

    @FunctionalInterface
    interface ErrorLogger {
        void apply(Exception e);
    }

    /**
     * Generic setter that saves consent data to diffrerent data stores based on the consent source
     * of truth.
     *
     * @param appSetter Function that saves consent data to the app storage.
     * @param systemServiceSetter Function that saves consent data to the system server.
     * @param appSearchSetter Function that saves consent data to the appsearch.
     * @param ppapiAndAdExtDataServiceSetter Function that saves consent data to the
     *     AdServicesExDataService or the app.
     * @param errorLogger Function that logs exceptions during write operations.
     */
    private void executeSettersByConsentSourceOfTruth(
            ThrowableSetter appSetter,
            ThrowableSetter systemServiceSetter, /* MUST pass lambdas instead of method
            references for back compat. */
            ThrowableSetter appSearchSetter, /* MUST pass lambdas instead of method references
            for back compat. */
            ThrowableSetter ppapiAndAdExtDataServiceSetter,
            ErrorLogger errorLogger) {
        Trace.beginSection("ConsentManager#WriteOperation");
        mReadWriteLock.writeLock().lock();
        try {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    appSetter.apply();
                    break;
                case Flags.SYSTEM_SERVER_ONLY:
                    systemServiceSetter.apply();
                    break;
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    appSetter.apply();
                    systemServiceSetter.apply();
                    break;
                case Flags.APPSEARCH_ONLY:
                    if (mFlags.getEnableAppsearchConsentData()) {
                        appSearchSetter.apply();
                    }
                    break;
                case Flags.PPAPI_AND_ADEXT_SERVICE:
                    if (mFlags.getEnableAdExtServiceConsentData()) {
                        ppapiAndAdExtDataServiceSetter.apply();
                    }
                    break;
                default:
                    throw new RuntimeException(
                            ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
            }
        } catch (IOException | RuntimeException e) {
            if (errorLogger != null) {
                errorLogger.apply(e);
            }
            throw new RuntimeException(
                    getClass().getSimpleName() + " failed. " + e.getMessage(), e);
        } finally {
            mReadWriteLock.writeLock().unlock();
            Trace.endSection();
        }
    }

    @FunctionalInterface
    interface ThrowableGetter<T> {
        T apply() throws IOException, RuntimeException;
    }

    /**
     * Generic getter that reads consent data from diffrerent data stores based on the consent
     * source of truth.
     *
     * @param defaultReturn Default return value.
     * @param appGetter Function that reads consent data from the app storage.
     * @param systemServiceGetter Function that reads consent data from the system server.
     * @param appSearchGetter Function that reads consent data from appsearch.
     * @param ppapiAndAdExtDataServiceGetter Function that reads consent data from
     *     AdServicesExDataService or the app.
     * @param errorLogger Function that logs exceptions during read operations.
     */
    private <T> T executeGettersByConsentSourceOfTruth(
            T defaultReturn,
            ThrowableGetter<T> appGetter,
            ThrowableGetter<T> systemServiceGetter, /* MUST pass lambdas instead of method
            references for back compat. */
            ThrowableGetter<T> appSearchGetter, /* MUST pass lambdas instead of method references
             for back compat. */
            ThrowableGetter<T> ppapiAndAdExtDataServiceGetter,
            ErrorLogger errorLogger) {
        Trace.beginSection("ConsentManager#ReadOperation");
        mReadWriteLock.readLock().lock();
        try {
            switch (mConsentSourceOfTruth) {
                case Flags.PPAPI_ONLY:
                    return appGetter.apply();
                case Flags.SYSTEM_SERVER_ONLY:
                    // Intentional fallthrough.
                case Flags.PPAPI_AND_SYSTEM_SERVER:
                    return systemServiceGetter.apply();
                case Flags.APPSEARCH_ONLY:
                    if (mFlags.getEnableAppsearchConsentData()) {
                        return appSearchGetter.apply();
                    }
                    break;
                case Flags.PPAPI_AND_ADEXT_SERVICE:
                    if (mFlags.getEnableAdExtServiceConsentData()) {
                        return ppapiAndAdExtDataServiceGetter.apply();
                    }
                    break;
                default:
                    LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                    return defaultReturn;
            }
        } catch (IOException | RuntimeException e) {
            if (errorLogger != null) {
                errorLogger.apply(e);
            }
            LogUtil.e(getClass().getSimpleName() + " failed. " + e.getMessage());
        } finally {
            mReadWriteLock.readLock().unlock();
            Trace.endSection();
        }

        return defaultReturn;
    }

    /* Returns the region od the device */
    private static int getConsentRegion(@NonNull Context context) {
        return DeviceRegionProvider.isEuDevice(context)
                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }

    /***
     * Returns an object of ConsentMigrationStats for logging
     *
     * @param appConsents AppConsents consents per API (fledge, msmt, topics, default)
     * @param migrationStatus Status of migration ( FAILURE, SUCCESS_WITH_SHARED_PREF_UPDATED,
     *                        SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
     * @param migrationType Type of migration ( PPAPI_TO_SYSTEM_SERVICE,
     *                      APPSEARCH_TO_SYSTEM_SERVICE,
     *                      ADEXT_SERVICE_TO_SYSTEM_SERVICE,
     *                      ADEXT_SERVICE_TO_APPSEARCH)
     * @param context Context of the application
     * @return consentMigrationStats returns ConsentMigrationStats for logging
     */
    public static ConsentMigrationStats getConsentManagerStatsForLogging(
            AppConsents appConsents,
            ConsentMigrationStats.MigrationStatus migrationStatus,
            ConsentMigrationStats.MigrationType migrationType,
            Context context) {
        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setMigrationType(migrationType)
                        // When appConsents is null we log it as a failure
                        .setMigrationStatus(
                                appConsents != null
                                        ? migrationStatus
                                        : ConsentMigrationStats.MigrationStatus.FAILURE)
                        .setMsmtConsent(appConsents == null || appConsents.getMsmtConsent())
                        .setTopicsConsent(appConsents == null || appConsents.getTopicsConsent())
                        .setFledgeConsent(appConsents == null || appConsents.getFledgeConsent())
                        .setRegion(getConsentRegion(context))
                        .build();
        return consentMigrationStats;
    }

    private static String getAppSearchExceptionMessage(String illegalAction) {
        return String.format(
                "Attempting to %s using APPSEARCH_ONLY consent source of truth!", illegalAction);
    }

    private static String getAdExtExceptionMessage(String illegalAction) {
        return String.format(
                "Attempting to %s using PPAPI_AND_ADEXT_SERVICE consent source of truth!",
                illegalAction);
    }
}
