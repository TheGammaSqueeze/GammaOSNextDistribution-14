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

package com.android.adservices.service.common;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static com.android.adservices.AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementWipeoutStats;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Receiver to receive a com.android.adservices.PACKAGE_CHANGED broadcast from the AdServices system
 * service when package install/uninstalls occur.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class PackageChangedReceiver extends BroadcastReceiver {

    /**
     * Broadcast send from the system service to the AdServices module when a package has been
     * installed/uninstalled.
     */
    public static final String PACKAGE_CHANGED_BROADCAST = "com.android.adservices.PACKAGE_CHANGED";

    /** Key for designating if the action was an installation or an uninstallation. */
    public static final String ACTION_KEY = "action";

    /** Value if the package change was an uninstallation. */
    public static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";

    /** Value if the package change was an installation. */
    public static final String PACKAGE_ADDED = "package_added";

    /** Value if the package had its data cleared. */
    public static final String PACKAGE_DATA_CLEARED = "package_data_cleared";

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    private static final int DEFAULT_PACKAGE_UID = -1;
    private static boolean sFilteringEnabled;

    private static final Object LOCK = new Object();

    /** Enable the PackageChangedReceiver */
    public static boolean enableReceiver(@NonNull Context context, @NonNull Flags flags) {
        return changeReceiverState(context, flags, COMPONENT_ENABLED_STATE_ENABLED);
    }

    /** Disable the PackageChangedReceiver */
    public static boolean disableReceiver(@NonNull Context context, @NonNull Flags flags) {
        return changeReceiverState(context, flags, COMPONENT_ENABLED_STATE_DISABLED);
    }

    private static boolean changeReceiverState(
            @NonNull Context context, @NonNull Flags flags, int state) {
        synchronized (LOCK) {
            sFilteringEnabled =
                    BinderFlagReader.readFlag(flags::getFledgeAdSelectionFilteringEnabled);
            try {
                context.getPackageManager()
                        .setComponentEnabledSetting(
                                new ComponentName(context, PackageChangedReceiver.class),
                                state,
                                PackageManager.DONT_KILL_APP);
            } catch (IllegalArgumentException e) {
                LogUtil.e("enableService failed for %s", context.getPackageName());
                return false;
            }
            return true;
        }
    }

    /**
     * This receiver will be used for both T+ and S-. For T+, the AdServices System Service will
     * listen to the system broadcasts and rebroadcast to this receiver. For S-, since we don't have
     * AdServices in System Service, we have to listen to system broadcasts directly. Note: This is
     * best effort since AdServices process is not a persistent process, so any processing that
     * happens here should be verified in a background job. TODO(b/263904417): Register for
     * PACKAGE_ADDED receiver for S-.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d("PackageChangedReceiver received a broadcast: " + intent.getAction());

        // On T+, this should never be executed from ext services module
        String packageName = context.getPackageName();
        if (SdkLevel.isAtLeastT()
                && packageName != null
                && packageName.endsWith(ADEXTSERVICES_PACKAGE_NAME_SUFFIX)) {
            LogUtil.d(
                    "Aborting attempt to receive in PackageChangedReceiver on T+ for"
                            + " ExtServices");
            return;
        }
        synchronized (LOCK) {
            Uri packageUri = Uri.parse(intent.getData().getSchemeSpecificPart());
            int packageUid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            switch (intent.getAction()) {
                    // The broadcast is received from the system. On S- devices, we do this because
                    // there is no service running in the system server.
                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                    handlePackageFullyRemoved(context, packageUri, packageUid);
                    break;
                case Intent.ACTION_PACKAGE_DATA_CLEARED:
                    handlePackageDataCleared(context, packageUri);
                    break;
                    // The broadcast is received from the system service. On T+ devices, we do this
                    // so
                    // that the PP API process is not woken up if the flag is disabled.
                case PACKAGE_CHANGED_BROADCAST:
                    switch (intent.getStringExtra(ACTION_KEY)) {
                        case PACKAGE_FULLY_REMOVED:
                            handlePackageFullyRemoved(context, packageUri, packageUid);
                            break;
                        case PACKAGE_ADDED:
                            handlePackageAdded(context, packageUri);
                            break;
                        case PACKAGE_DATA_CLEARED:
                            handlePackageDataCleared(context, packageUri);
                            break;
                    }
                    break;
            }
        }
    }

    private void handlePackageFullyRemoved(Context context, Uri packageUri, int packageUid) {
        measurementOnPackageFullyRemoved(context, packageUri);
        topicsOnPackageFullyRemoved(context, packageUri);
        fledgeOnPackageFullyRemovedOrDataCleared(context, packageUri);
        consentOnPackageFullyRemoved(context, packageUri, packageUid);
    }

    private void handlePackageAdded(Context context, Uri packageUri) {
        measurementOnPackageAdded(context, packageUri);
        topicsOnPackageAdded(context, packageUri);
    }

    private void handlePackageDataCleared(Context context, Uri packageUri) {
        measurementOnPackageDataCleared(context, packageUri);
        fledgeOnPackageFullyRemovedOrDataCleared(context, packageUri);
    }

    @VisibleForTesting
    void measurementOnPackageFullyRemoved(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch()) {
            LogUtil.e("Measurement Delete Packages Receiver is disabled");
            return;
        }

        LogUtil.d("Package Fully Removed:" + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        MeasurementImpl.getInstance(
                                        SdkLevel.isAtLeastS()
                                                ? context
                                                : context.getApplicationContext())
                                .deletePackageRecords(packageUri));

        // Log wipeout event triggered by request to uninstall package on device
        WipeoutStatus wipeoutStatus = new WipeoutStatus();
        wipeoutStatus.setWipeoutType(WipeoutStatus.WipeoutType.UNINSTALL);
        logWipeoutStats(wipeoutStatus, packageUri.toString());
    }

    @VisibleForTesting
    void measurementOnPackageDataCleared(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverDeletePackagesKillSwitch()) {
            LogUtil.e("Measurement Delete Packages Receiver is disabled");
            return;
        }

        LogUtil.d("Package Data Cleared: " + packageUri);
        sBackgroundExecutor.execute(
                () -> {
                    MeasurementImpl.getInstance(context).deletePackageRecords(packageUri);
                });

        WipeoutStatus wipeoutStatus = new WipeoutStatus();
        wipeoutStatus.setWipeoutType(WipeoutStatus.WipeoutType.CLEAR_DATA);
        logWipeoutStats(wipeoutStatus, packageUri.toString());
    }

    @VisibleForTesting
    void measurementOnPackageAdded(Context context, Uri packageUri) {
        if (FlagsFactory.getFlags().getMeasurementReceiverInstallAttributionKillSwitch()) {
            LogUtil.e("Measurement Install Attribution Receiver is disabled");
            return;
        }

        LogUtil.d("Package Added: " + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        MeasurementImpl.getInstance(context)
                                .doInstallAttribution(packageUri, System.currentTimeMillis()));
    }

    @VisibleForTesting
    void topicsOnPackageFullyRemoved(Context context, @NonNull Uri packageUri) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            return;
        }

        LogUtil.d(
                "Handling App Uninstallation in Topics API for package: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).handleAppUninstallation(packageUri));
    }

    @VisibleForTesting
    void topicsOnPackageAdded(Context context, @NonNull Uri packageUri) {
        LogUtil.d("Package Added for topics API: " + packageUri.toString());
        sBackgroundExecutor.execute(
                () -> TopicsWorker.getInstance(context).handleAppInstallation(packageUri));
    }

    /** Deletes FLEDGE custom audience data belonging to the given application. */
    @VisibleForTesting
    void fledgeOnPackageFullyRemovedOrDataCleared(
            @NonNull Context context, @NonNull Uri packageUri) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageUri);

        if (FlagsFactory.getFlags().getFledgeCustomAudienceServiceKillSwitch()) {
            LogUtil.v("FLEDGE CA API is disabled");
            return;
        }

        LogUtil.d("Deleting custom audience data for package: " + packageUri);
        sBackgroundExecutor.execute(
                () ->
                        getCustomAudienceDatabase(context)
                                .customAudienceDao()
                                .deleteCustomAudienceDataByOwner(packageUri.toString()));
        if (sFilteringEnabled) {
            LogUtil.d("Deleting app install data for package: " + packageUri);
            sBackgroundExecutor.execute(
                    () ->
                            getSharedStorageDatabase(context)
                                    .appInstallDao()
                                    .deleteByPackageName(packageUri.toString()));
            LogUtil.d("Deleting frequency cap histogram data for package: " + packageUri);
            sBackgroundExecutor.execute(
                    () ->
                            getSharedStorageDatabase(context)
                                    .frequencyCapDao()
                                    .deleteHistogramDataBySourceApp(packageUri.toString()));
        }
    }

    /**
     * Deletes a consent setting for the given application and UID. If the UID is equal to
     * DEFAULT_PACKAGE_UID, all consent data is deleted.
     */
    @VisibleForTesting
    void consentOnPackageFullyRemoved(
            @NonNull Context context, @NonNull Uri packageUri, int packageUid) {
        if (!SdkLevel.isAtLeastS()) {
            LogUtil.d("consentOnPackageFullyRemoved is not needed on Android R, returning...");
            return;
        }
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageUri);

        String packageName = packageUri.toString();
        LogUtil.d("Deleting consent data for package %s with UID %d", packageName, packageUid);
        sBackgroundExecutor.execute(
                () -> {
                    ConsentManager instance = ConsentManager.getInstance();
                    if (packageUid == DEFAULT_PACKAGE_UID) {
                        // There can be multiple instances of PackageChangedReceiver, e.g. in
                        // different user profiles. The system broadcasts a package change
                        // notification when any package is installed/uninstalled/cleared on any
                        // profile, to all PackageChangedReceivers. However, if the
                        // uninstallation is in a different user profile than the one this
                        // instance of PackageChangedReceiver is in, it should ignore that
                        // notification.
                        // Because the Package UID is absent, we need to figure out
                        // if this package was deleted in the current profile or a different one.
                        // We can do that by querying the list of installed packages and checking
                        // if the package name appears there. If it does, then this package was
                        // uninstalled in a different profile, and so the method should no-op.

                        if (!isPackageStillInstalled(context, packageName)) {
                            instance.clearConsentForUninstalledApp(packageName);
                            LogUtil.d("Deleted all consent data for package %s", packageName);
                        } else {
                            LogUtil.d(
                                    "Uninstalled package %s is present in list of installed"
                                            + " packages; ignoring",
                                    packageName);
                        }
                    } else {
                        instance.clearConsentForUninstalledApp(packageName, packageUid);
                        LogUtil.d(
                                "Deleted consent data for package %s with UID %d",
                                packageName, packageUid);
                    }
                });
    }

    /**
     * Checks if the removed package name is still present in the list of installed packages
     *
     * @param context the context passed along with the package notification
     * @param packageName the name of the package that was removed
     * @return {@code true} if the removed package name still exists in the list of installed
     *     packages on the system retrieved from {@code PackageManager.getInstalledPackages}; {@code
     *     false} otherwise.
     */
    @VisibleForTesting
    boolean isPackageStillInstalled(@NonNull Context context, @NonNull String packageName) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(packageName);
        PackageManager packageManager = context.getPackageManager();
        return PackageManagerCompatUtils.getInstalledPackages(packageManager, 0).stream()
                .anyMatch(s -> packageName.equals(s.packageName));
    }

    /**
     * Returns an instance of the {@link CustomAudienceDatabase}.
     *
     * <p>This is split out for testing/mocking purposes only, since the {@link
     * CustomAudienceDatabase} is abstract and therefore unmockable.
     */
    @VisibleForTesting
    CustomAudienceDatabase getCustomAudienceDatabase(@NonNull Context context) {
        Objects.requireNonNull(context);
        return CustomAudienceDatabase.getInstance(context);
    }

    /**
     * Returns an instance of the {@link SharedStorageDatabase}.
     *
     * <p>This is split out for testing/mocking purposes only, since the {@link
     * SharedStorageDatabase} is abstract and therefore unmockable.
     */
    @VisibleForTesting
    SharedStorageDatabase getSharedStorageDatabase(@NonNull Context context) {
        Objects.requireNonNull(context);
        return SharedStorageDatabase.getInstance(context);
    }

    private void logWipeoutStats(WipeoutStatus wipeoutStatus, String appPackageName) {
        AdServicesLoggerImpl.getInstance()
                .logMeasurementWipeoutStats(
                        new MeasurementWipeoutStats.Builder()
                                .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                                .setWipeoutType(wipeoutStatus.getWipeoutType().getValue())
                                .setSourceRegistrant(appPackageName)
                                .build());
    }
}
