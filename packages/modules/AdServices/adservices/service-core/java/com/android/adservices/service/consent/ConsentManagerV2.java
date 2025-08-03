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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_RESET_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

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
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsearch.AppSearchConsentStorageManager;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.compat.FileCompatUtils;
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
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manager all critical user data such as per API consent.
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
 *   <li>APPSEARCH_ONLY: Write and read consent from appSearch only for back compat.
 *   <li>PPAPI_AND_ADEXT_SERVICE: Write and read consent from PPAPI and AdExt service..
 * </ul>
 */
// TODO(b/279042385): move UI logs to UI.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentManagerV2 {
    private static volatile ConsentManagerV2 sConsentManager;

    @IntDef(value = {NO_MANUAL_INTERACTIONS_RECORDED, UNKNOWN, MANUAL_INTERACTIONS_RECORDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserManualInteraction {}

    public static final int NO_MANUAL_INTERACTIONS_RECORDED = -1;
    public static final int UNKNOWN = 0;
    public static final int MANUAL_INTERACTIONS_RECORDED = 1;

    private final Flags mFlags;
    private final TopicsWorker mTopicsWorker;
    private final BooleanFileDatastore mDatastore;
    private final EnrollmentDao mEnrollmentDao;
    private final MeasurementImpl mMeasurementImpl;
    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final AdServicesStorageManager mAdServicesStorageManager;
    private final AppSearchConsentStorageManager mAppSearchConsentStorageManager;
    private final UserProfileIdManager mUserProfileIdManager;

    private final AppConsentForRStorageManager mAppConsentForRStorageManager;

    private static final Object LOCK = new Object();

    private ConsentCompositeStorage mConsentCompositeStorage;

    private AppConsentStorageManager mAppConsentStorageManager;

    ConsentManagerV2(
            @NonNull TopicsWorker topicsWorker,
            @NonNull AppConsentDao appConsentDao,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull MeasurementImpl measurementImpl,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AppConsentStorageManager appConsentStorageManager,
            @NonNull AppInstallDao appInstallDao,
            @NonNull FrequencyCapDao frequencyCapDao,
            @NonNull AdServicesStorageManager adServicesStorageManager,
            @NonNull BooleanFileDatastore booleanFileDatastore,
            @NonNull AppSearchConsentStorageManager appSearchConsentStorageManager,
            @NonNull UserProfileIdManager userProfileIdManager,
            @NonNull AppConsentForRStorageManager appConsentForRStorageManager,
            @NonNull Flags flags,
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth,
            boolean enableAppsearchConsentData,
            boolean enableAdExtServiceConsentData) {
        Objects.requireNonNull(topicsWorker);
        Objects.requireNonNull(appConsentDao);
        Objects.requireNonNull(measurementImpl);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(appInstallDao);
        Objects.requireNonNull(frequencyCapDao);
        Objects.requireNonNull(booleanFileDatastore);
        Objects.requireNonNull(userProfileIdManager);

        if (consentSourceOfTruth != Flags.PPAPI_ONLY
                && consentSourceOfTruth != Flags.APPSEARCH_ONLY) {
            Objects.requireNonNull(adServicesStorageManager);
        }

        if (enableAppsearchConsentData) {
            Objects.requireNonNull(appSearchConsentStorageManager);
        }

        if (enableAdExtServiceConsentData) {
            Objects.requireNonNull(appConsentForRStorageManager);
        }

        mAdServicesStorageManager = adServicesStorageManager;
        mTopicsWorker = topicsWorker;
        mDatastore = booleanFileDatastore;
        mEnrollmentDao = enrollmentDao;
        mMeasurementImpl = measurementImpl;
        mCustomAudienceDao = customAudienceDao;
        mAppInstallDao = appInstallDao;
        mFrequencyCapDao = frequencyCapDao;

        mAppSearchConsentStorageManager = appSearchConsentStorageManager;
        mUserProfileIdManager = userProfileIdManager;

        mFlags = flags;
        mAppConsentStorageManager = appConsentStorageManager;
        mAppConsentForRStorageManager = appConsentForRStorageManager;

        mConsentCompositeStorage =
                new ConsentCompositeStorage(getStorageListBySourceOfTruth(consentSourceOfTruth));
    }

    private ImmutableList<IConsentStorage> getStorageListBySourceOfTruth(
            @Flags.ConsentSourceOfTruth int consentSourceOfTruth) {
        switch (consentSourceOfTruth) {
            case Flags.PPAPI_ONLY:
                return ImmutableList.of(mAppConsentStorageManager);
            case Flags.SYSTEM_SERVER_ONLY:
                return ImmutableList.of(mAdServicesStorageManager);
            case Flags.PPAPI_AND_SYSTEM_SERVER:
                // System storage has higher priority
                return ImmutableList.of(mAdServicesStorageManager, mAppConsentStorageManager);
            case Flags.APPSEARCH_ONLY:
                return ImmutableList.of(mAppSearchConsentStorageManager);
            case Flags.PPAPI_AND_ADEXT_SERVICE:
                return ImmutableList.of(mAppConsentForRStorageManager);
            default:
                LogUtil.e(ConsentConstants.ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH);
                return ImmutableList.of();
        }
    }

    /**
     * Gets an instance of {@link ConsentManagerV2} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static ConsentManagerV2 getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        if (sConsentManager == null) {
            synchronized (LOCK) {
                if (sConsentManager == null) {
                    // Execute one-time consent migration if needed.
                    int consentSourceOfTruth = FlagsFactory.getFlags().getConsentSourceOfTruth();
                    BooleanFileDatastore datastore = createAndInitializeDataStore(context);
                    AdServicesStorageManager adServicesManager =
                            AdServicesStorageManager.getInstance(
                                    AdServicesManager.getInstance(context));
                    AppConsentDao appConsentDao = AppConsentDao.getInstance(context);

                    // It is possible that the old value of the flag lingers after OTA until the
                    // first PH sync. In that case, we should not use the stale value, but use the
                    // default instead. The next PH sync will restore the T+ value.
                    if (SdkLevel.isAtLeastT() && consentSourceOfTruth == Flags.APPSEARCH_ONLY) {
                        consentSourceOfTruth = Flags.DEFAULT_CONSENT_SOURCE_OF_TRUTH;
                    }
                    AppSearchConsentStorageManager appSearchConsentStorageManager = null;
                    StatsdAdServicesLogger statsdAdServicesLogger =
                            StatsdAdServicesLogger.getInstance();
                    // Flag enable_appsearch_consent_data is true on S- and T+ only when we want to
                    // use AppSearch to write to or read from.
                    boolean enableAppsearchConsentData =
                            FlagsFactory.getFlags().getEnableAppsearchConsentData();
                    if (enableAppsearchConsentData) {
                        appSearchConsentStorageManager =
                                AppSearchConsentStorageManager.getInstance();
                        handleConsentMigrationFromAppSearchIfNeeded(
                                context,
                                datastore,
                                appConsentDao,
                                appSearchConsentStorageManager,
                                adServicesManager,
                                statsdAdServicesLogger);
                    }
                    UxStatesDao uxStatesDao = UxStatesDao.getInstance(context);
                    AppConsentForRStorageManager mAppConsentForRStorageManager = null;
                    // Flag enable_adext_service_consent_data is true on R and S+ only when
                    // we want to use AdServicesExtDataStorageService to write to or read from.
                    boolean enableAdExtServiceConsentData =
                            FlagsFactory.getFlags().getEnableAdExtServiceConsentData();
                    if (enableAdExtServiceConsentData) {
                        AdServicesExtDataStorageServiceManager adServicesExtDataManager =
                                AdServicesExtDataStorageServiceManager.getInstance(context);
                        // TODO(b/324273438): Support R->T+ consent migration for Consent Manager
                        //  V2 project.
                        // NOTE: To disable migration from AdExtService to AppSearch on 2024 M03-
                        // builds, use the deprecated flag
                        // enable_adext_service_to_appsearch_migration.
                        if (FlagsFactory.getFlags().getEnableMigrationFromAdExtService()) {
                            AdExtDataConsentMigrationUtilsV2
                                    .handleConsentMigrationFromAdExtDataIfNeededV2(
                                            context,
                                            appSearchConsentStorageManager,
                                            adServicesExtDataManager,
                                            statsdAdServicesLogger,
                                            adServicesManager);
                        }
                        mAppConsentForRStorageManager =
                                new AppConsentForRStorageManager(
                                        datastore,
                                        appConsentDao,
                                        uxStatesDao,
                                        adServicesExtDataManager);
                    }

                    // Attempt to migrate consent data from PPAPI to System server if needed.
                    handleConsentMigrationIfNeeded(
                            context,
                            datastore,
                            adServicesManager,
                            statsdAdServicesLogger,
                            consentSourceOfTruth);

                    AppConsentStorageManager appConsentStorageManager =
                            new AppConsentStorageManager(datastore, appConsentDao, uxStatesDao);
                    sConsentManager =
                            new ConsentManagerV2(
                                    TopicsWorker.getInstance(context),
                                    appConsentDao,
                                    EnrollmentDao.getInstance(context),
                                    MeasurementImpl.getInstance(context),
                                    CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                                    appConsentStorageManager,
                                    SharedStorageDatabase.getInstance(context).appInstallDao(),
                                    SharedStorageDatabase.getInstance(context).frequencyCapDao(),
                                    adServicesManager,
                                    datastore,
                                    appSearchConsentStorageManager,
                                    UserProfileIdManager.getInstance(context),
                                    // TODO(b/260601944): Remove Flag Instance.
                                    mAppConsentForRStorageManager,
                                    FlagsFactory.getFlags(),
                                    consentSourceOfTruth,
                                    enableAppsearchConsentData,
                                    enableAdExtServiceConsentData);
                }
            }
        }
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
            clearAllAppConsentData();
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
            clearAllAppConsentData();
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
        return mConsentCompositeStorage.getConsent(AdServicesApiType.ALL_API);
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
        return mConsentCompositeStorage.getConsent(apiType);
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
        return mAppConsentForRStorageManager != null
                && mAppConsentForRStorageManager.wasU18NotificationDisplayed()
                && (mFlags.getRvcPostOtaNotifAgeCheck()
                        ? !mAppConsentForRStorageManager.isU18Account()
                                && mAppConsentForRStorageManager.isAdultAccount()
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
        return ImmutableList.copyOf(
                mConsentCompositeStorage.getKnownAppsWithConsent().stream()
                        .map(App::create)
                        .collect(Collectors.toList()));
    }

    /**
     * @return an {@link ImmutableList} of all known apps in the database that have had user consent
     *     revoked
     */
    public ImmutableList<App> getAppsWithRevokedConsent() {
        return ImmutableList.copyOf(
                mConsentCompositeStorage.getAppsWithRevokedConsent().stream()
                        .map(App::create)
                        .collect(Collectors.toList()));
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
        mConsentCompositeStorage.setConsentForApp(app.getPackageName(), true);

        asyncExecute(
                () -> mCustomAudienceDao.deleteCustomAudienceDataByOwner(app.getPackageName()));
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(() -> mAppInstallDao.deleteByPackageName(app.getPackageName()));
            asyncExecute(
                    () -> mFrequencyCapDao.deleteHistogramDataBySourceApp(app.getPackageName()));
        }
    }

    /**
     * Proxy call to {@link AppConsentDao} to restore consent for provided {@link App}.
     *
     * @param app {@link App} to restore consent for.
     * @throws IOException if the operation fails
     */
    public void restoreConsentForApp(@NonNull App app) throws IOException {
        mConsentCompositeStorage.setConsentForApp(app.getPackageName(), false);
    }

    /**
     * Deletes all app consent data and all app data gathered or generated by the Privacy Sandbox.
     *
     * <p>This should be called when the Privacy Sandbox has been disabled.
     *
     * @throws IOException if the operation fails
     */
    public void clearAllAppConsentData() throws IOException {
        mConsentCompositeStorage.clearAllAppConsentData();

        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
            asyncExecute(mFrequencyCapDao::deleteAllHistogramData);
        }
    }

    /**
     * Deletes the list of known allowed apps as well as all app data from the Privacy Sandbox.
     *
     * <p>The list of blocked apps is not reset.
     *
     * @throws IOException if the operation fails
     */
    public void clearKnownAppsWithConsent() throws IOException {
        mConsentCompositeStorage.clearKnownAppsWithConsent();
        asyncExecute(mCustomAudienceDao::deleteAllCustomAudienceData);
        if (mFlags.getFledgeAdSelectionFilteringEnabled()) {
            asyncExecute(mAppInstallDao::deleteAllAppInstallData);
            asyncExecute(mFrequencyCapDao::deleteAllHistogramData);
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

        return mConsentCompositeStorage.isConsentRevokedForApp(packageName);
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

        return mConsentCompositeStorage.setConsentForAppIfNew(packageName, false);
    }

    /**
     * Clear consent data after an app was uninstalled.
     *
     * @param packageName the package name that had been uninstalled.
     */
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        mConsentCompositeStorage.clearConsentForUninstalledApp(packageName, packageUid);
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
        mConsentCompositeStorage.clearConsentForUninstalledApp(packageName);
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
        mConsentCompositeStorage.recordNotificationDisplayed(wasNotificationDisplayed);
    }

    /**
     * Retrieves if notification has been displayed.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public Boolean wasNotificationDisplayed() {
        return mConsentCompositeStorage.wasNotificationDisplayed();
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed(boolean wasGaUxDisplayed) {
        mConsentCompositeStorage.recordGaUxNotificationDisplayed(wasGaUxDisplayed);
    }

    /**
     * Retrieves if GA UX notification has been displayed.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public Boolean wasGaUxNotificationDisplayed() {
        return mConsentCompositeStorage.wasGaUxNotificationDisplayed();
    }

    /** Set the current privacy sandbox feature. */
    public void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType currentFeatureType) {
        mConsentCompositeStorage.setCurrentPrivacySandboxFeature(currentFeatureType);
    }

    /** Saves information to the storage that user interacted with consent manually. */
    public void recordUserManualInteractionWithConsent(@UserManualInteraction int interaction) {
        mConsentCompositeStorage.recordUserManualInteractionWithConsent(interaction);
    }

    /**
     * Get the current privacy sandbox feature.
     *
     * <p>To write to PPAPI if consent source of truth is PPAPI_ONLY or dual sources. To write to
     * system server if consent source of truth is SYSTEM_SERVER_ONLY or dual sources.
     */
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() {
        return mConsentCompositeStorage.getCurrentPrivacySandboxFeature();
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return true if the user interacted with the consent manually, otherwise false.
     */
    public @UserManualInteraction int getUserManualInteractionWithConsent() {
        return mConsentCompositeStorage.getUserManualInteractionWithConsent();
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
            AdServicesStorageManager adServicesManager,
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
            case Flags.APPSEARCH_ONLY:
                // If this is an S- device, the consent source of truth is always APPSEARCH_ONLY.
                break;
            default:
                break;
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
                clearAllAppConsentData();
                resetUserProfileId();
                break;
            case MEASUREMENTS:
                resetMeasurement();
                break;
            default:
                break;
        }
    }

    private void resetUserProfileId() {
        mUserProfileIdManager.deleteId();
    }

    // Perform a one-time migration to migrate existing PPAPI Consent
    @VisibleForTesting
    // Suppress lint warning for context.getUser in R since this code is unused in R
    @SuppressWarnings("NewApi")
    static void migratePpApiConsentToSystemService(
            @NonNull Context context,
            @NonNull BooleanFileDatastore datastore,
            @NonNull AdServicesStorageManager adServicesManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(adServicesManager);

        AppConsents appConsents = null;
        try {
            // Exit if migration has happened.
            SharedPreferences sharedPreferences =
                    FileCompatUtils.getSharedPreferencesHelper(
                            context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
            // If we migrated data to system server either from PPAPI or from AppSearch, do not
            // attempt another migration of data to system server.
            boolean shouldSkipMigration =
                    sharedPreferences.getBoolean(
                                    ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED,
                                    /* default= */ false)
                            || sharedPreferences.getBoolean(
                                    ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED,
                                    /* default= */ false);
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
            adServicesManager.setConsent(AdServicesApiType.ALL_API, consentKey);
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
                FileCompatUtils.getSharedPreferencesHelper(
                        context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
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
                FileCompatUtils.getSharedPreferencesHelper(
                        context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
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
        mConsentCompositeStorage.setConsent(AdServicesApiType.ALL_API, isGiven);
    }

    @VisibleForTesting
    boolean getConsentFromSourceOfTruth() {
        return mConsentCompositeStorage.getConsent(AdServicesApiType.ALL_API).isGiven();
    }

    @VisibleForTesting
    boolean getPerApiConsentFromSourceOfTruth(AdServicesApiType apiType) {
        return mConsentCompositeStorage.getConsent(apiType).isGiven();
    }

    @VisibleForTesting
    void setPerApiConsentToSourceOfTruth(boolean isGiven, AdServicesApiType apiType) {
        mConsentCompositeStorage.setConsent(apiType, isGiven);
    }

    private static void storeUserManualInteractionToPpApi(
            @ConsentManagerV2.UserManualInteraction int interaction, BooleanFileDatastore datastore)
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
            @NonNull AppSearchConsentStorageManager appSearchConsentStorageManager,
            @NonNull AdServicesStorageManager adServicesStorageManager,
            @NonNull StatsdAdServicesLogger statsdAdServicesLogger) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(appSearchConsentStorageManager);
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
                    FileCompatUtils.getSharedPreferencesHelper(
                            context, ConsentConstants.SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
            // If we did not migrate notification data, we should not attempt to migrate anything.
            if (!appSearchConsentStorageManager.migrateConsentDataIfNeeded(
                    sharedPreferences, datastore, adServicesStorageManager, appConsentDao)) {
                LogUtil.d("Skipping consent migration from AppSearch");
                return;
            }
            // Migrate Consent for all APIs and per API to PP API and System Service.
            appConsents =
                    migrateAppSearchConsents(
                            appSearchConsentStorageManager, adServicesStorageManager, datastore);
            // Record interactions data only if we recorded an interaction in AppSearch.
            int manualInteractionRecorded =
                    appSearchConsentStorageManager.getUserManualInteractionWithConsent();
            if (manualInteractionRecorded == MANUAL_INTERACTIONS_RECORDED) {
                // Initialize PP API datastore.
                storeUserManualInteractionToPpApi(MANUAL_INTERACTIONS_RECORDED, datastore);
                // Initialize system service.
                adServicesStorageManager.recordUserManualInteractionWithConsent(
                        manualInteractionRecorded);
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
            AppSearchConsentStorageManager appSearchConsentManager,
            AdServicesStorageManager adServicesManager,
            BooleanFileDatastore datastore)
            throws IOException {
        Map<String, Boolean> consentMap = new HashMap<>();
        for (AdServicesApiType apiType : AdServicesApiType.values()) {
            if (apiType == AdServicesApiType.UNKNOWN) {
                continue;
            }
            boolean consented = appSearchConsentManager.getConsent(apiType).isGiven();
            datastore.put(apiType.toPpApiDatastoreKey(), consented);
            adServicesManager.setConsent(apiType, consented);
            consentMap.put(apiType.toPpApiDatastoreKey(), consented);
        }
        return AppConsents.builder()
                .setMsmtConsent(
                        consentMap.get(AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey()))
                .setTopicsConsent(consentMap.get(AdServicesApiType.TOPICS.toPpApiDatastoreKey()))
                .setFledgeConsent(consentMap.get(AdServicesApiType.FLEDGE.toPpApiDatastoreKey()))
                .build();
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
        return mConsentCompositeStorage.isAdIdEnabled();
    }

    /** Set the AdIdEnabled bit to storage based on consent_source_of_truth. */
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        mConsentCompositeStorage.setAdIdEnabled(isAdIdEnabled);
    }

    /** Returns whether the isU18Account bit is true based on consent_source_of_truth. */
    public Boolean isU18Account() {
        return mConsentCompositeStorage.isU18Account();
    }

    /** Set the U18Account bit to storage based on consent_source_of_truth. */
    public void setU18Account(boolean isU18Account) {
        mConsentCompositeStorage.setU18Account(isU18Account);
    }

    /** Returns whether the isEntryPointEnabled bit is true based on consent_source_of_truth. */
    public Boolean isEntryPointEnabled() {
        return mConsentCompositeStorage.isEntryPointEnabled();
    }

    /** Set the EntryPointEnabled bit to storage based on consent_source_of_truth. */
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        mConsentCompositeStorage.setEntryPointEnabled(isEntryPointEnabled);
    }

    /** Returns whether the isAdultAccount bit is true based on consent_source_of_truth. */
    public Boolean isAdultAccount() {
        return mConsentCompositeStorage.isAdultAccount();
    }

    /** Set the AdultAccount bit to storage based on consent_source_of_truth. */
    public void setAdultAccount(boolean isAdultAccount) {
        mConsentCompositeStorage.setAdultAccount(isAdultAccount);
    }

    /**
     * Returns whether the wasU18NotificationDisplayed bit is true based on consent_source_of_truth.
     */
    public Boolean wasU18NotificationDisplayed() {
        return mConsentCompositeStorage.wasU18NotificationDisplayed();
    }

    /** Set the U18NotificationDisplayed bit to storage based on consent_source_of_truth. */
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        mConsentCompositeStorage.setU18NotificationDisplayed(wasU18NotificationDisplayed);
    }

    /** Returns current UX based on consent_source_of_truth. */
    public PrivacySandboxUxCollection getUx() {
        return mConsentCompositeStorage.getUx();
    }

    /** Set the current UX to storage based on consent_source_of_truth. */
    public void setUx(PrivacySandboxUxCollection ux) {
        mConsentCompositeStorage.setUx(ux);
    }

    /** Returns current enrollment channel based on consent_source_of_truth. */
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            PrivacySandboxUxCollection ux) {
        return mConsentCompositeStorage.getEnrollmentChannel(ux);
    }

    /** Set the current enrollment channel to storage based on consent_source_of_truth. */
    public void setEnrollmentChannel(
            PrivacySandboxUxCollection ux, PrivacySandboxEnrollmentChannelCollection channel) {
        mConsentCompositeStorage.setEnrollmentChannel(ux, channel);
    }

    @VisibleForTesting
    void setConsentToPpApi(boolean isGiven) throws IOException {
        mDatastore.put(ConsentConstants.CONSENT_KEY, isGiven);
    }

    /* Returns the region od the device */
    private static int getConsentRegion(@NonNull Context context) {
        return DeviceRegionProvider.isEuDevice(context)
                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }

    /* Returns an object of ConsentMigrationStats */
    private static ConsentMigrationStats getConsentManagerStatsForLogging(
            AppConsents appConsents,
            ConsentMigrationStats.MigrationStatus migrationStatus,
            ConsentMigrationStats.MigrationType migrationType,
            Context context) {
        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setMigrationType(migrationType)
                        .setMigrationStatus(migrationStatus)
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
}
