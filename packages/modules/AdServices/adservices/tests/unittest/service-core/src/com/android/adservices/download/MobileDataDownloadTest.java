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

import static com.android.adservices.download.EnrollmentDataDownloadManager.DownloadStatus.SUCCESS;
import static com.android.adservices.service.topics.classifier.ModelManager.BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.database.DatabaseUtils;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.topics.classifier.CommonClassifierHelper;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.AddFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.DownloadFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.FileGroupPopulator;
import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.Logger;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.MobileDataDownloadBuilder;
import com.google.android.libraries.mobiledatadownload.TaskScheduler;
import com.google.android.libraries.mobiledatadownload.TimeSource;
import com.google.android.libraries.mobiledatadownload.downloader.FileDownloader;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.monitor.NetworkUsageMonitor;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DataFile;
import com.google.mobiledatadownload.DownloadConfigProto.DataFileGroup;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions;
import com.google.mobiledatadownload.DownloadConfigProto.DownloadConditions.DeviceNetworkPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Unit tests for {@link MobileDataDownloadFactory} */
@RequiresSdkLevelAtLeastS
@SpyStatic(MddLogger.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MobileDataDownloadFactory.class)
@SpyStatic(UxStatesManager.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(EncryptionKeyDao.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(CommonClassifierHelper.class)
public final class MobileDataDownloadTest extends AdServicesExtendedMockitoTestCase {
    private static final int MAX_HANDLE_TASK_WAIT_TIME_SECS = 300;
    private static final long WAIT_FOR_WIFI_CONNECTION_MS = 5 * 1000; // 5 seconds.
    private static boolean sNeedWifiConnectionWait = true;

    // Two files are from cts_test_1 folder.
    // https://source.corp.google.com/piper///depot/google3/wireless/android/adservices/mdd/topics_classifier/cts_test_1/
    private static final String FILE_GROUP_NAME_1 = "test-group-1";
    private static final String FILE_ID_1 = "classifier_assets_metadata.json";
    private static final String FILE_ID_2 = "stopwords.txt";
    private static final String FILE_CHECKSUM_1 = "52633ae715ead32ec6c8ae721ad34ea301336a8e";
    private static final String FILE_URL_1 =
            "https://dl.google.com/mdi-serving/rubidium-adservices-topics-classifier/1489/52633ae715ead32ec6c8ae721ad34ea301336a8e";
    private static final int FILE_SIZE_1 = 1026;

    private static final String FILE_CHECKSUM_2 = "042dc4512fa3d391c5170cf3aa61e6a638f84342";
    private static final String FILE_URL_2 =
            "https://dl.google.com/mdi-serving/rubidium-adservices-topics-classifier/1489/042dc4512fa3d391c5170cf3aa61e6a638f84342";
    private static final int FILE_SIZE_2 = 1;

    // TODO(b/263521464): Use the production topics classifier manifest URL.
    private static final String TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/922/217081737fd739c74dd3ca5c407813d818526577";
    private static final String MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-topics-classifier/1986/9e98784bcdb26a3eb2ab3f65ee811f43177c761f";
    private static final String PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/2998/b35ed340576e8a72385af87b95f1f526abdb7f5f";

    // Prod Test Bed enrollment manifest URL
    private static final String PTB_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1281/a245b0927ba27b3d954b0ca2775651ccfc9a5e84";
    private static final String OEM_ENROLLMENT_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-adtech-enrollment/1760/1460e6aea598fe7a153100d6e2749f45313ef905";
    private static final String UI_OTA_STRINGS_MANIFEST_FILE_URL =
            "https://www.gstatic.com/mdi-serving/rubidium-adservices-ui-ota-strings/1360/d428721d225582922a7fe9d5ad6db7b09cb03209";

    private static final int PRODUCTION_ENROLLMENT_ENTRIES = 64;
    private static final int PTB_ENROLLMENT_ENTRIES = 1;
    private static final int OEM_ENROLLMENT_ENTRIES = 114;

    private static final int PRODUCTION_FILEGROUP_VERSION = 0;
    private static final int PTB_FILEGROUP_VERSION = 0;
    private static final int OEM_FILEGROUP_VERSION = 0;

    public static final String TEST_TOPIC_FILE_GROUP_NAME = "topics-classifier-model";
    public static final String ENROLLMENT_FILE_GROUP_NAME = "adtech_enrollment_data";
    public static final String UI_OTA_STRINGS_FILE_GROUP_NAME = "ui-ota-strings";
    private SynchronousFileStorage mFileStorage;
    private FileDownloader mFileDownloader;
    private SharedDbHelper mDbHelper;
    private MobileDataDownload mMdd;

    @Mock Flags mMockFlags;
    @Mock ConsentManager mConsentManager;
    @Mock UxStatesManager mUxStatesManager;

    @Before
    public void setUp() throws Exception {
        // Add latency to fix the boot up WIFI connection delay. We only need to wait once during
        // the whole test suite run.
        // Checking wifi connection using WifiManager isn't working on low-performance devices.
        if (sNeedWifiConnectionWait) {
            Thread.sleep(WAIT_FOR_WIFI_CONNECTION_MS);
            sNeedWifiConnectionWait = false;
        }

        mockMddFlags();

        mFileStorage = MobileDataDownloadFactory.getFileStorage(mContext);
        mFileDownloader =
                MobileDataDownloadFactory.getFileDownloader(mContext, mMockFlags, mFileStorage);

        mDbHelper = DbTestUtil.getSharedDbHelperForTest();

        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        // Mock static method ConsentManager.getInstance() to return test ConsentManager
        doReturn(mConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(mUxStatesManager).when(() -> UxStatesManager.getInstance());
        overridingMddLoggingLevel("VERBOSE");
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        if (mMdd != null) {
            mMdd.clear().get();
        }
        overridingMddLoggingLevel("INFO");
    }

    @Test
    public void testCreateMddManagerSuccessfully() throws ExecutionException, InterruptedException {
        mMdd =
                getMddForTesting(
                        mContext,
                        FlagsFactory.getFlagsForTest(),
                        // Pass in an empty list of FileGroupPopulator. Add ad hoc DataFileGroup
                        // to MDD manually below.
                        ImmutableList.of());

        DataFileGroup dataFileGroup =
                createDataFileGroup(
                        FILE_GROUP_NAME_1,
                        mContext.getPackageName(),
                        5 /* versionNumber */,
                        new String[] {FILE_ID_1, FILE_ID_2},
                        new int[] {FILE_SIZE_1, FILE_SIZE_2},
                        new String[] {FILE_CHECKSUM_1, FILE_CHECKSUM_2},
                        new String[] {FILE_URL_1, FILE_URL_2},
                        DeviceNetworkPolicy.DOWNLOAD_ONLY_ON_WIFI);
        // Add the DataFileGroup to MDD
        assertThat(
                        mMdd.addFileGroup(
                                        AddFileGroupRequest.newBuilder()
                                                .setDataFileGroup(dataFileGroup)
                                                .build())
                                .get())
                .isTrue();

        // Trigger the download immediately.
        ClientFileGroup clientFileGroup =
                mMdd.downloadFileGroup(
                                DownloadFileGroupRequest.newBuilder()
                                        .setGroupName(FILE_GROUP_NAME_1)
                                        .build())
                        .get();

        // Verify the downloaded DataFileGroup.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(FILE_GROUP_NAME_1);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getVersionNumber()).isEqualTo(5);
        assertThat(clientFileGroup.getFileCount()).isEqualTo(2);
        assertThat(clientFileGroup.hasAccount()).isFalse();
    }

    @Test
    public void testTopicsManifestFileGroupPopulator_ManifestConfigOverrider_NoFileGroup()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForTopics(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
        // The server side test model build_id = 1986, which equals to bundled model build_id =
        // 1986. ManifestConfigOverrider will not add the DataFileGroup in the
        // TopicsManifestFileGroupPopulator and will not download either.
        assertThat(
                        mMdd.getFileGroup(
                                        GetFileGroupRequest.newBuilder()
                                                .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                                .build())
                                .get())
                .isNull();
    }

    /**
     * This method tests topics manifest files. It downloads test classifier model and verifies
     * files downloaded successfully.
     */
    @Test
    public void testTopicsManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Set the bundled build_id to 1 so the server side build_id will be bigger. This will
        // trigger MDD download.
        doReturn(1L)
                .when(
                        () ->
                                CommonClassifierHelper.getBundledModelBuildId(
                                        mContext, BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH));

        createMddForTopics(MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify topics file group.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(TEST_TOPIC_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getVersionNumber())
                .isEqualTo(/* Test filegroup version number */ 0);
        assertThat(clientFileGroup.getFileCount()).isEqualTo(6);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getBuildId()).isEqualTo(/* BuildID generated by Ingress */ 1986);
    }

    /**
     * This method tests MDD production enrollment data, verifies files downloaded successfully and
     * data saved into DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload_Production()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(
                clientFileGroup, PRODUCTION_FILEGROUP_VERSION, PRODUCTION_ENROLLMENT_ENTRIES);
    }

    /**
     * This method tests OEM enrollment data, verifies files downloaded successfully and data saved
     * into DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload_OEM()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForEnrollment(OEM_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(clientFileGroup, OEM_FILEGROUP_VERSION, OEM_ENROLLMENT_ENTRIES);
    }

    /**
     * This method tests Prod Test Bed enrollment data, verifies files downloaded successfully and
     * data saved into DB correctly.
     */
    @Test
    public void testEnrollmentDataDownload_PTB()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForEnrollment(PTB_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        verifyMeasurementFileGroup(clientFileGroup, PTB_FILEGROUP_VERSION, PTB_ENROLLMENT_ENTRIES);
    }

    /**
     * This method tests enrollment data, verifies that the file group doesn't exist if the consent
     * is revoked.
     */
    @Test
    public void testEnrollmentDataDownloadFailOnConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * This method tests enrollment data, verifies that the file group exists if the consent is
     * given.
     */
    @Test
    public void testEnrollmentDataDownloadOnConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForEnrollment(PRODUCTION_ENROLLMENT_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(ENROLLMENT_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests topics data, verifies that the file group doesn't exist if the consent is
     * revoked.
     */
    @Test
    public void testMddTopicsFailsOnConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    /**
     * This method tests topics data, verifies that the file group exists if the consent is given.
     */
    @Test
    public void testMddTopicsOnConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(1L)
                .when(
                        () ->
                                CommonClassifierHelper.getBundledModelBuildId(
                                        mContext, BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH));

        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests OTA data, verifies that the file group exists if the consent is given to at
     * least one of the APIs.
     */
    @Test
    public void testOtaOnTopicsConsentGiven_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);

        createMddForUiOTAString(UI_OTA_STRINGS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNotNull();
    }

    /**
     * This method tests OTA data, verifies that the file group doesn't exist if the consent is
     * revoked for all the APIs.
     */
    @Test
    public void testOtaFailsOnAggregatedConsentRevoked_gaUxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.REVOKED);
        when(mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS))
                .thenReturn(AdServicesApiConsent.REVOKED);
        when(mConsentManager.getConsent(AdServicesApiType.FLEDGE))
                .thenReturn(AdServicesApiConsent.REVOKED);

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    // Topics MFGP should be disabled for U18 UX.
    @Test
    public void topicsDownloadTest_U18UxEnabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(PrivacySandboxUxCollection.U18_UX).when(mUxStatesManager).getUx();

        createMddForTopics(TEST_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(TEST_TOPIC_FILE_GROUP_NAME)
                                        .build())
                        .get();

        assertThat(clientFileGroup).isNull();
    }

    @Test
    public void testMddLoggerFeatureFlagIsOff() {
        // The feature flag is off. MddLogger should be disabled.
        doReturn(false).when(mMockFlags).getMddLoggerEnabled();
        Optional<Logger> mddLogger = MobileDataDownloadFactory.getMddLogger(mMockFlags);
        assertThat(mddLogger).isAbsent();
    }

    @Test
    public void testMddLoggerFeatureFlagIsOn() {
        // The feature flag is on. MddLogger should be enabled.
        doReturn(true).when(mMockFlags).getMddLoggerEnabled();
        Optional<Logger> mddLogger = MobileDataDownloadFactory.getMddLogger(mMockFlags);
        assertThat(mddLogger).isPresent();
    }

    /**
     * This method tests UI OTA Strings manifest files. It downloads test UI Strings file and
     * verifies files downloaded successfully.
     */
    @Test
    public void testUiOtaStringsManifestFileGroupPopulator()
            throws ExecutionException, InterruptedException, TimeoutException {
        createMddForUiOTAString(UI_OTA_STRINGS_MANIFEST_FILE_URL);

        ClientFileGroup clientFileGroup =
                mMdd.getFileGroup(
                                GetFileGroupRequest.newBuilder()
                                        .setGroupName(UI_OTA_STRINGS_FILE_GROUP_NAME)
                                        .build())
                        .get();

        // Verify UI file group.
        assertThat(clientFileGroup.getGroupName()).isEqualTo(UI_OTA_STRINGS_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getBuildId()).isEqualTo(/* BuildID generated by Ingress */ 1360);
    }

    // A helper function to create a DataFilegroup.
    private static DataFileGroup createDataFileGroup(
            String groupName,
            String ownerPackage,
            int versionNumber,
            String[] fileId,
            int[] byteSize,
            String[] checksum,
            String[] url,
            DeviceNetworkPolicy deviceNetworkPolicy) {
        if (fileId.length != byteSize.length
                || fileId.length != checksum.length
                || fileId.length != url.length) {
            throw new IllegalArgumentException();
        }

        DataFileGroup.Builder dataFileGroupBuilder =
                DataFileGroup.newBuilder()
                        .setGroupName(groupName)
                        .setOwnerPackage(ownerPackage)
                        .setFileGroupVersionNumber(versionNumber)
                        .setDownloadConditions(
                                DownloadConditions.newBuilder()
                                        .setDeviceNetworkPolicy(deviceNetworkPolicy));

        for (int i = 0; i < fileId.length; ++i) {
            DataFile file =
                    DataFile.newBuilder()
                            .setFileId(fileId[i])
                            .setByteSize(byteSize[i])
                            .setChecksum(checksum[i])
                            .setUrlToDownload(url[i])
                            .build();
            dataFileGroupBuilder.addFile(file);
        }

        return dataFileGroupBuilder.build();
    }

    /**
     * Returns a MobileDataDownload instance for testing.
     *
     * @param context the context
     * @param flags the flags
     * @param fileGroupPopulators a list of FileGroupPopulator that will be added to the MDD
     * @return a MobileDataDownload instance.
     */
    @NonNull
    private static MobileDataDownload getMddForTesting(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull ImmutableList<FileGroupPopulator> fileGroupPopulators) {
        context = context.getApplicationContext();
        SynchronousFileStorage fileStorage = MobileDataDownloadFactory.getFileStorage(context);
        FileDownloader fileDownloader =
                MobileDataDownloadFactory.getFileDownloader(context, flags, fileStorage);
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

        return MobileDataDownloadBuilder.newBuilder()
                .setContext(context)
                .setControlExecutor(MobileDataDownloadFactory.getControlExecutor())
                .setNetworkUsageMonitor(networkUsageMonitor)
                .setFileStorage(fileStorage)
                .setFileDownloaderSupplier(() -> fileDownloader)
                .addFileGroupPopulators(fileGroupPopulators)
                .setLoggerOptional(MobileDataDownloadFactory.getMddLogger(flags))
                // Use default MDD flags so that it does not need to access DeviceConfig
                // which is inaccessible from Unit Tests.
                .setFlagsOptional(
                        Optional.of(new com.google.android.libraries.mobiledatadownload.Flags() {}))
                .build();
    }

    // Returns MobileDataDownload using passed in enrollment manifest url.
    @NonNull
    private void createMddForEnrollment(String enrollmentManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(enrollmentManifestFileUrl).when(mMockFlags).getMeasurementManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getMeasurementManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains Measurement FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in topics manifest url.
    @NonNull
    private void createMddForTopics(String topicsManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(topicsManifestFileUrl).when(mMockFlags).getMddTopicsClassifierManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getTopicsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains Topics FileGroupPopulator only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    // Returns MobileDataDownload using passed in UI OTA String manifest url.
    @NonNull
    private void createMddForUiOTAString(String uiOtaStringManifestFileUrl)
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(uiOtaStringManifestFileUrl).when(mMockFlags).getUiOtaStringsManifestFileUrl();

        FileGroupPopulator fileGroupPopulator =
                MobileDataDownloadFactory.getUiOtaStringsManifestPopulator(
                        mContext, mMockFlags, mFileStorage, mFileDownloader);

        mMdd =
                getMddForTesting(
                        mContext,
                        mMockFlags,
                        // List of FileGroupPopulator that contains UI OTA String FileGroupPopulator
                        // only.
                        ImmutableList.of(fileGroupPopulator));

        // Calling handleTask directly to trigger the MDD's background download on wifi. This should
        // be done in tests only.
        mMdd.handleTask(TaskScheduler.WIFI_CHARGING_PERIODIC_TASK)
                .get(MAX_HANDLE_TASK_WAIT_TIME_SECS, SECONDS);
    }

    private long getNumEntriesInEnrollmentTable() {
        return DatabaseUtils.queryNumEntries(
                mDbHelper.getReadableDatabase(),
                EnrollmentTables.EnrollmentDataContract.TABLE,
                null);
    }

    private void verifyMeasurementFileGroup(
            ClientFileGroup clientFileGroup, int fileGroupVersion, int enrollmentEntries)
            throws InterruptedException, ExecutionException {
        assertThat(clientFileGroup.getGroupName()).isEqualTo(ENROLLMENT_FILE_GROUP_NAME);
        assertThat(clientFileGroup.getOwnerPackage()).isEqualTo(mContext.getPackageName());
        assertThat(clientFileGroup.getFileCount()).isEqualTo(1);
        assertThat(clientFileGroup.getStatus()).isEqualTo(ClientFileGroup.Status.DOWNLOADED);
        assertThat(clientFileGroup.getVersionNumber()).isEqualTo(fileGroupVersion);

        doReturn(mMdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        EnrollmentDataDownloadManager enrollmentDataDownloadManager =
                new EnrollmentDataDownloadManager(mContext, mMockFlags);
        EnrollmentDao enrollmentDao = new EnrollmentDao(mContext, mDbHelper, mMockFlags);
        doReturn(enrollmentDao).when(() -> EnrollmentDao.getInstance(any(Context.class)));

        EncryptionKeyDao encryptionKeyDao = new EncryptionKeyDao(mDbHelper);
        doReturn(encryptionKeyDao).when(() -> EncryptionKeyDao.getInstance(any(Context.class)));

        assertThat(enrollmentDao.deleteAll()).isTrue();
        // Verify no enrollment data after table cleared.
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(0);
        // Verify enrollment data file read from MDD and insert the data into the enrollment
        // database.
        assertThat(enrollmentDataDownloadManager.readAndInsertEnrollmentDataFromMdd().get())
                .isEqualTo(SUCCESS);
        assertThat(getNumEntriesInEnrollmentTable()).isEqualTo(enrollmentEntries);
        assertThat(enrollmentDao.deleteAll()).isTrue();
    }

    private void mockMddFlags() {
        extendedMockito.mockGetFlags(mMockFlags);

        doReturn(2).when(mMockFlags).getDownloaderMaxDownloadThreads();
        doReturn(false).when(mMockFlags).getEncryptionKeyNewEnrollmentFetchKillSwitch();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkConnectTimeoutMs();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkReadTimeoutMs();
    }

    private static void overridingMddLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.MDD %s", loggingLevel);
    }
}
