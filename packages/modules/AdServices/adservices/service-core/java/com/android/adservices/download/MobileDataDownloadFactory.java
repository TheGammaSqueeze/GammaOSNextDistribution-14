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

package com.android.adservices.download;

import static com.android.adservices.service.topics.classifier.ModelManager.BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.CommonClassifierHelper;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.downloader.AndroidDownloaderLogger;
import com.google.android.downloader.ConnectivityHandler;
import com.google.android.downloader.DownloadConstraints;
import com.google.android.downloader.Downloader;
import com.google.android.downloader.PlatformUrlEngine;
import com.google.android.downloader.UrlEngine;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TimeSource;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.ExceptionHandler;
import com.google.android.libraries.mobiledatadownload.downloader.offroad.Offroad2FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.backends.AndroidFileBackend;
import com.google.android.libraries.mobiledatadownload.file.backends.JavaFileBackend;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.DownloadMetadataStore;
import com.google.android.libraries.mobiledatadownload.file.integration.downloader.SharedPreferencesDownloadMetadata;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.android.libraries.mobiledatadownload.populator.ManifestConfigFileParser;
import com.google.android.libraries.mobiledatadownload.populator.ManifestConfigOverrider;
import com.google.android.libraries.mobiledatadownload.populator.ManifestFileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.populator.SharedPreferencesManifestFileMetadata;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mobiledatadownload.DownloadConfigProto;
import com.google.mobiledatadownload.DownloadConfigProto.ManifestFileFlag;
import com.google.protobuf.MessageLite;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Mobile Data Download Factory. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MobileDataDownloadFactory {
    private static MobileDataDownload sSingletonMdd;
    private static SynchronousFileStorage sSynchronousFileStorage;

    private static final String MDD_METADATA_SHARED_PREFERENCES = "mdd_metadata_store";
    private static final String TOPICS_MANIFEST_ID = "TopicsManifestId";
    private static final String MEASUREMENT_MANIFEST_ID = "MeasurementManifestId";
    private static final String UI_OTA_STRINGS_MANIFEST_ID = "UiOtaStringsManifestId";

    private static final int MAX_ADB_LOGCAT_SIZE = 4000;

    /** Returns a singleton of MobileDataDownload for the whole PPAPI app. */
    @NonNull
    public static MobileDataDownload getMdd(@NonNull Context context, @NonNull Flags flags) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSingletonMdd == null) {
                // TODO(b/236761740): This only adds the core MDD code. We still need other
                //  components:
                // Add Logger
                // Add Configurator.

                context = context.getApplicationContext();
                SynchronousFileStorage fileStorage = getFileStorage(context);
                FileDownloader fileDownloader = getFileDownloader(context, flags, fileStorage);
                NetworkUsageMonitor networkUsageMonitor =
                        new NetworkUsageMonitor(
                                context,
                            new TimeSource() {
                                @Override
                                public long currentTimeMillis() {
                                    return System.currentTimeMillis();
                                }

                                @Override
                                public long elapsedRealtimeNanos() {
                                    return SystemClock.elapsedRealtimeNanos();
                                }
                            });

                sSingletonMdd =
                        MobileDataDownloadBuilder.newBuilder()
                                .setContext(context)
                                .setControlExecutor(getControlExecutor())
                                .setNetworkUsageMonitor(networkUsageMonitor)
                                .setFileStorage(fileStorage)
                                .setFileDownloaderSupplier(() -> fileDownloader)
                                .addFileGroupPopulator(
                                        getTopicsManifestPopulator(
                                                context, flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getMeasurementManifestPopulator(
                                                context, flags, fileStorage, fileDownloader))
                                .addFileGroupPopulator(
                                        getUiOtaStringsManifestPopulator(
                                                context, flags, fileStorage, fileDownloader))
                                .setLoggerOptional(getMddLogger(flags))
                                .setFlagsOptional(Optional.of(MddFlags.getInstance()))
                                .build();
            }

            return sSingletonMdd;
        }
    }

    // Connectivity constraints will be checked by JobScheduler/WorkManager instead.
    private static class NoOpConnectivityHandler implements ConnectivityHandler {
        @Override
        public ListenableFuture<Void> checkConnectivity(DownloadConstraints constraints) {
            return Futures.immediateVoidFuture();
        }
    }

    /** Return a singleton of {@link SynchronousFileStorage}. */
    @NonNull
    public static SynchronousFileStorage getFileStorage(@NonNull Context context) {
        synchronized (MobileDataDownloadFactory.class) {
            if (sSynchronousFileStorage == null) {
                sSynchronousFileStorage =
                        new SynchronousFileStorage(
                                ImmutableList.of(
                                        /*backends*/ AndroidFileBackend.builder(context).build(),
                                        new JavaFileBackend()),
                                ImmutableList.of(/*transforms*/ ),
                                ImmutableList.of(/*monitors*/ ));
            }
            return sSynchronousFileStorage;
        }
    }

    @NonNull
    @VisibleForTesting
    static ListeningExecutorService getControlExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static Executor getDownloadExecutor() {
        return AdServicesExecutors.getBackgroundExecutor();
    }

    @NonNull
    private static UrlEngine getUrlEngine(@NonNull Flags flags) {
        // TODO(b/219594618): Switch to use CronetUrlEngine.
        return new PlatformUrlEngine(
                AdServicesExecutors.getBlockingExecutor(),
                /* connectTimeoutMs= */ flags.getDownloaderConnectionTimeoutMs(),
                /* readTimeoutMs= */ flags.getDownloaderReadTimeoutMs());
    }

    @NonNull
    private static ExceptionHandler getExceptionHandler() {
        return ExceptionHandler.withDefaultHandling();
    }

    @NonNull
    @VisibleForTesting
    static FileDownloader getFileDownloader(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage) {
        DownloadMetadataStore downloadMetadataStore = getDownloadMetadataStore(context);

        Downloader downloader =
                new Downloader.Builder()
                        .withIOExecutor(AdServicesExecutors.getBlockingExecutor())
                        .withConnectivityHandler(new NoOpConnectivityHandler())
                        .withMaxConcurrentDownloads(flags.getDownloaderMaxDownloadThreads())
                        .withLogger(new AndroidDownloaderLogger())
                        .addUrlEngine("https", getUrlEngine(flags))
                        .build();

        return new Offroad2FileDownloader(
                downloader,
                fileStorage,
                getDownloadExecutor(),
                /* authTokenProvider */ null,
                downloadMetadataStore,
                getExceptionHandler(),
                Optional.absent());
    }

    @NonNull
    private static DownloadMetadataStore getDownloadMetadataStore(@NonNull Context context) {
        SharedPreferences sharedPrefs =
                context.getSharedPreferences(MDD_METADATA_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        DownloadMetadataStore downloadMetadataStore =
                new SharedPreferencesDownloadMetadata(
                        sharedPrefs, AdServicesExecutors.getBackgroundExecutor());
        return downloadMetadataStore;
    }

    // Create the Manifest File Group Populator for Topics Classifier.
    @NonNull
    @VisibleForTesting
    static ManifestFileGroupPopulator getTopicsManifestPopulator(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage,
            @NonNull FileDownloader fileDownloader) {

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(TOPICS_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMddTopicsClassifierManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        // Only download Topics classifier model when Mdd model build_id is greater than bundled
        // model.
        ManifestConfigOverrider manifestConfigOverrider =
                manifestConfig -> {
                    List<DownloadConfigProto.DataFileGroup> groups = new ArrayList<>();
                    for (DownloadConfigProto.ManifestConfig.Entry entry :
                            manifestConfig.getEntryList()) {
                        long dataFileGroupBuildId = entry.getDataFileGroup().getBuildId();
                        long bundledModelBuildId =
                                CommonClassifierHelper.getBundledModelBuildId(
                                        context, BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH);
                        if (dataFileGroupBuildId > bundledModelBuildId) {
                            groups.add(entry.getDataFileGroup());
                            LogUtil.d("Added topics classifier file group to MDD");
                        } else {
                            LogUtil.d(
                                    "Topics Classifier's Bundled BuildId = %d is bigger than or"
                                        + " equal to the BuildId = %d from Server side, skipping"
                                        + " the downloading.",
                                    bundledModelBuildId, dataFileGroupBuildId);
                        }
                    }
                    return Futures.immediateFuture(groups);
                };

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                .setEnabledSupplier(
                        () -> {
                            // Topics is permanently disabled for U18 UX.
                            if (UxStatesManager.getInstance().getUx()
                                    == PrivacySandboxUxCollection.U18_UX) {
                                return false;
                            }

                            // Topics resources should not be downloaded pre-consent.
                            if (flags.getGaUxFeatureEnabled()) {
                                return ConsentManager.getInstance()
                                        .getConsent(AdServicesApiType.TOPICS)
                                        .isGiven();
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable Dedup using Etag.
                .setDedupDownloadWithEtag(false)
                .setOverriderOptional(Optional.of(manifestConfigOverrider))
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    @NonNull
    @VisibleForTesting
    static ManifestFileGroupPopulator getUiOtaStringsManifestPopulator(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage,
            @NonNull FileDownloader fileDownloader) {

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(UI_OTA_STRINGS_MANIFEST_ID)
                        .setManifestFileUrl(flags.getUiOtaStringsManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // OTA resources can be downloaded pre-consent before notification, or with consent
                .setEnabledSupplier(
                        () ->
                                !ConsentManager.getInstance().wasGaUxNotificationDisplayed()
                                        || isAnyConsentGiven(context, flags))
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/236761740): user proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    private static boolean isAnyConsentGiven(@NonNull Context context, Flags flags) {
        ConsentManager instance = ConsentManager.getInstance();
        if (flags.getGaUxFeatureEnabled()
                && (instance.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()
                        || instance.getConsent(AdServicesApiType.TOPICS).isGiven()
                        || instance.getConsent(AdServicesApiType.FLEDGE).isGiven())) {
            return true;
        }

        return instance.getConsent().isGiven();
    }

    @NonNull
    @VisibleForTesting
    static ManifestFileGroupPopulator getMeasurementManifestPopulator(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull SynchronousFileStorage fileStorage,
            @NonNull FileDownloader fileDownloader) {

        ManifestFileFlag manifestFileFlag =
                ManifestFileFlag.newBuilder()
                        .setManifestId(MEASUREMENT_MANIFEST_ID)
                        .setManifestFileUrl(flags.getMeasurementManifestFileUrl())
                        .build();

        ManifestConfigFileParser manifestConfigFileParser =
                new ManifestConfigFileParser(
                        fileStorage, AdServicesExecutors.getBackgroundExecutor());

        return ManifestFileGroupPopulator.builder()
                .setContext(context)
                // measurement resources should not be downloaded pre-consent
                .setEnabledSupplier(
                        () -> {
                            if (flags.getGaUxFeatureEnabled()) {
                                return isAnyConsentGiven(context, flags);
                            } else {
                                return ConsentManager.getInstance().getConsent().isGiven();
                            }
                        })
                .setBackgroundExecutor(AdServicesExecutors.getBackgroundExecutor())
                .setFileDownloader(() -> fileDownloader)
                .setFileStorage(fileStorage)
                .setManifestFileFlagSupplier(() -> manifestFileFlag)
                .setManifestConfigParser(manifestConfigFileParser)
                .setMetadataStore(
                        SharedPreferencesManifestFileMetadata.createFromContext(
                                context, /*InstanceId*/
                                Optional.absent(),
                                AdServicesExecutors.getBackgroundExecutor()))
                // TODO(b/239265537): Enable dedup using etag.
                .setDedupDownloadWithEtag(false)
                // TODO(b/243829623): use proper Logger.
                .setLogger(
                        new Logger() {
                            @Override
                            public void log(MessageLite event, int eventCode) {
                                // A no-op logger.
                            }
                        })
                .build();
    }

    // Check the feature flag is on or off. True means use MddLogger.
    @NonNull
    @VisibleForTesting
    static Optional<Logger> getMddLogger(@NonNull Flags flags) {
        return flags.getMddLoggerEnabled() ? Optional.of(new MddLogger()) : Optional.absent();
    }

    /** Dump MDD Debug Info. */
    public static void dump(Context context, @NonNull PrintWriter writer) {
        String debugString =
                MobileDataDownloadFactory.getMdd(context, FlagsFactory.getFlags())
                        .getDebugInfoAsString();
        writer.println("***====*** MDD Lib dump: ***====***");

        for (int i = 0; i <= debugString.length() / MAX_ADB_LOGCAT_SIZE; i++) {
            int start = i * MAX_ADB_LOGCAT_SIZE;
            int end = (i + 1) * MAX_ADB_LOGCAT_SIZE;
            end = Math.min(debugString.length(), end);
            writer.println(debugString.substring(start, end));
        }
    }
}
