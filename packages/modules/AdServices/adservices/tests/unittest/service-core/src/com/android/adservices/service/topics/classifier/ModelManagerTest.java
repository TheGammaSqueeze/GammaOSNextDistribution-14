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

package com.android.adservices.service.topics.classifier;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlagsForTest;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilErrorWithAnyException;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_ASSET;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__INVALID_TOPIC_ID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__NO_CLASSIFIER_MODEL_AVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_LABELS_FILE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Model Manager Test {@link ModelManager}. */
public class ModelManagerTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    // Change this to a higher number when classifier_test_assets_metadata build_id changed.
    private static final int CLIENT_FILE_GROUP_BUILD_ID = 9;
    private ImmutableList<Integer> mProductionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> mProductionClassifierAssetsMetadata;
    private ImmutableMap<String, ImmutableMap<String, String>> mTestClassifierAssetsMetadata;
    private ModelManager mTestModelManager;
    private ModelManager mProductionModelManager;
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH = "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String TEST_CLASSIFIER_INPUT_CONFIG_PATH =
            "classifier/classifier_input_config.txt";
    private static final String TEST_CLASSIFIER_MODEL_PATH = "classifier/test_model.tflite";

    private static final String PRODUCTION_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String PRODUCTION_APPS_FILE_PATH = "classifier/precomputed_app_list.csv";
    private static final String PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH =
            "classifier/classifier_input_config.txt";
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";
    private static final String DOWNLOADED_MODEL_FILE_ID = "model.tflite";
    private static final String MODEL_MANAGER_CLASS_NAME = "ModelManager";
    private static final String GET_ASSETS_METADATA_MAP_METHOD_NAME = "getAssetsMetadataMap";
    private static final String GET_APPS_TOPIC_MAP_METHOD_NAME = "getAppsTopicMap";

    @Mock SynchronousFileStorage mMockFileStorage;
    @Mock Map<String, ClientFile> mMockDownloadedFiles;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule adServicesExtendedMock =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(ModelManager.class)
                    .spyStatic(ErrorLogUtil.class)
                    .build();

    @Before
    public void setUp() {
        mockGetFlagsForTest();
    }

    @Test
    public void testGetInstance() {
        ModelManager firstInstance = ModelManager.getInstance(sContext);
        ModelManager secondInstance = ModelManager.getInstance(sContext);

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testRetrieveModel_bundled_emptyDownloadedFiles() throws IOException {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ByteBuffer byteBuffer = mProductionModelManager.retrieveModel();
        // Check byteBuffer capacity greater than 0 when retrieveModel() finds bundled TFLite model
        // and loads file as a ByteBuffer.
        assertThat(mProductionModelManager.useDownloadedFiles()).isFalse();
        assertThat(byteBuffer.capacity()).isGreaterThan(0);
    }

    @Test
    public void testRetrieveModel_bundled_forceUsingBundledFiles() throws IOException {
        // Pass in a non-null and non-empty downloadedFiles to Model Manager.
        Map<String, ClientFile> downloadedFiles = new HashMap<>();
        downloadedFiles.put(DOWNLOADED_MODEL_FILE_ID, ClientFile.newBuilder().build());

        Flags mockedFlags = mock(Flags.class);
        doReturn(true).when(mockedFlags).getClassifierForceUseBundledFiles();
        // Force using bundled file
        doReturn(mockedFlags).when(FlagsFactory::getFlags);

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        downloadedFiles);

        ByteBuffer byteBuffer = mProductionModelManager.retrieveModel();
        assertThat(mProductionModelManager.useDownloadedFiles()).isFalse();
        // Check byteBuffer capacity greater than 0 when retrieveModel() finds bundled TFLite model
        // and loads file as a ByteBuffer.
        assertThat(byteBuffer.capacity()).isGreaterThan(0);
    }

    @Test
    public void testRetrieveModel_bundled_incorrectFilePath() throws IOException {
        doNothingOnErrorLogUtilError();

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        "IncorrectPathWithNoModel",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ByteBuffer byteBuffer = mProductionModelManager.retrieveModel();
        // Check byteBuffer capacity is 0 when failed to read a model.
        assertThat(byteBuffer.capacity()).isEqualTo(0);

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testRetrieveModel_downloaded() throws IOException {
        // Pass in a non-null and non-empty downloadedFiles to Model Manager
        Map<String, ClientFile> downloadedFiles = new HashMap<>();
        downloadedFiles.put(DOWNLOADED_MODEL_FILE_ID, ClientFile.newBuilder().build());

        // Mock File Storage to return null when gets invoked.
        doReturn(null).when(mMockFileStorage).open(any(), any());

        // Mocks a ClientFileGroup with build id = 9 as downloaded model, which is bigger than test
        // bundled model build id = 8. ModelManager will choose the downloaded model for
        // classification because downloaded model build id is bigger.
        ClientFileGroup clientFileGroup =
                ClientFileGroup.newBuilder().setBuildId(CLIENT_FILE_GROUP_BUILD_ID).build();
        doReturn(clientFileGroup).when(() -> ModelManager.getClientFileGroup(any(Context.class)));

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        downloadedFiles);

        // The invocation should return null according to above mock.
        assertThat(mProductionModelManager.retrieveModel()).isNull();

        var unused = verify(mMockFileStorage).open(any(), any());

        assertThat(mProductionModelManager.useDownloadedFiles()).isTrue();
        assertThat(mProductionModelManager.getBuildId()).isEqualTo(CLIENT_FILE_GROUP_BUILD_ID);
    }

    @Test
    public void testRetrieveLabels_bundled_successfulRead() {
        // Test the labels list in test assets with build id = 8.
        // Check size of list.
        // The labels_topics.txt contains 446 topics.
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionLabels = mProductionModelManager.retrieveLabels();
        assertThat(mProductionLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10010, 10200, 10270, 10432);
        // Verify ModelManager chooses bundled model because mMockDownloadedFiles is empty.
        assertThat(mProductionModelManager.useDownloadedFiles()).isFalse();
        // Verify ModelManager returns test bundled model build id = 8.
        assertThat(mProductionModelManager.getBuildId()).isEqualTo(8);
    }

    @Test
    public void testRetrieveLabels_downloaded_successfulRead() throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(PRODUCTION_LABELS_FILE_PATH));

        // Test the labels list in MDD downloaded label.
        // Check size of list.
        // The labels_topics.txt contains 446 topics.
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);
        mProductionLabels = mProductionModelManager.retrieveLabels();
        assertThat(mProductionLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10010, 10200, 10270, 10432);
    }

    @Test
    public void testRetrieveLabels_bundled_emptyListReturnedOnException() {
        doNothingOnErrorLogUtilError();

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ImmutableList<Integer> labels = mProductionModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_LABELS_FILE_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testRetrieveLabels_downloaded_emptyListReturnedOnException() throws IOException {
        doNothingOnErrorLogUtilError();

        InputStream inputStream = SdkLevel.isAtLeastT() ? FileInputStream.nullInputStream() : null;
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any())).thenReturn(inputStream);
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ImmutableList<Integer> labels = mProductionModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_LABELS_FILE_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testLoadedAppTopics_bundled() {
        doNothingOnErrorLogUtilError();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        Map<String, List<Integer>> appTopic = mTestModelManager.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 10000 apps + 11 sample apps + 2 test valid topics' apps
        // + 1 end2end test app + 1 cts test app + 1 empty app.
        assertThat(appTopic.size()).isEqualTo(10016);

        // Check android messaging, chrome and a sample app topics in map
        // The topicId of "com.google.android.apps.messaging" in
        // assets/precomputed_test_app_list.csv is 10281, 10280
        List<Integer> androidMessagingTopics = Arrays.asList(10281, 10280);
        assertThat(appTopic.get("com.google.android.apps.messaging"))
                .isEqualTo(androidMessagingTopics);

        // The topicId of "com.android.chrome" in assets/precomputed_test_app_list.csv
        // is 10185
        List<Integer> chromeTopics = Arrays.asList(10185);
        assertThat(appTopic.get("com.android.chrome")).isEqualTo(chromeTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp" in
        // assets/precomputed_test_app_list.csv are 10222, 10223, 10116, 10243, 10254
        String sampleAppPrefix = "com.example.adservices.samples.topics.sampleapp";
        List<Integer> sampleAppTopics = Arrays.asList(10222, 10223, 10116, 10243, 10254);
        assertThat(appTopic.get(sampleAppPrefix)).isEqualTo(sampleAppTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp4" in
        // assets/precomputed_test_app_list.csv are 10253, 10146, 10227, 10390, 10413
        List<Integer> sampleApp4Topics = Arrays.asList(10253, 10146, 10227, 10390, 10413);
        assertThat(appTopic.get(sampleAppPrefix + "4")).isEqualTo(sampleApp4Topics);

        // Check if all sample apps have 5 unique topics
        for (int appIndex = 1; appIndex <= 10; appIndex++) {
            assertThat(new HashSet<>(appTopic.get(sampleAppPrefix + appIndex)).size()).isEqualTo(5);
        }

        // Verify that the topics from the file are valid:
        // the valid topic is one of the topic in the labels file.
        // The invalid topics will not be loaded in the app topics map.
        String validTestAppPrefix = "com.example.adservices.valid.topics.testapp";

        // The valid topicIds of "com.example.adservices.valid.topics.testapp1" in
        // assets/precomputed_test_app_list.csv are 10147, 10253, 10254
        List<Integer> validTestApp1Topics = Arrays.asList(10147, 10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "1")).isEqualTo(validTestApp1Topics);

        // The valid topicIds of "com.example.adservices.valid.topics.testapp2" in
        // assets/precomputed_test_app_list.csv are 143, 15
        List<Integer> validTestApp2Topics = Arrays.asList(10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "2")).isEqualTo(validTestApp2Topics);

        // The precomputed_test_app_list contains 5 invalid topics: 2, 30, 20001, 20003, 50001
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__INVALID_TOPIC_ID,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
                times(5));
    }

    @Test
    public void testAppsWithOnlyEmptyTopics() {
        doNothingOnErrorLogUtilError();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);
        // Load precomputed labels from the test source `assets/precomputed_test_app_list.csv`
        Map<String, List<Integer>> appTopic = mTestModelManager.retrieveAppClassificationTopics();

        // The app com.emptytopics has empty topic in `assets/precomputed_test_app_list.csv`
        String emptyTopicsAppId = "com.emptytopics";

        // Verify the topic list of this app is empty.
        assertThat(appTopic.get(emptyTopicsAppId)).isEmpty();

        // Check app com.google.chromeremotedesktop has empty topic
        // in `assets/precomputed_test_app_list.csv`
        String chromeRemoteDesktopAppId = "com.google.chromeremotedesktop";

        // Verify the topic list of this app is empty.
        assertThat(appTopic.get(chromeRemoteDesktopAppId)).isEmpty();

        // The precomputed_test_app_list contains 5 invalid topics: 2, 30, 20001, 20003, 50001
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__INVALID_TOPIC_ID,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
                times(5));
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_correctFormat() {
        doNothingOnErrorLogUtilError();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);
        // There should contain 6 assets and 1 property in classifier_test_assets_metadata.json.
        // The asset without "asset_name" or "property" will not be stored in the map.
        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        assertThat(mTestClassifierAssetsMetadata).hasSize(7);

        // The property of metadata with correct format should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date", "build_id".
        // The key name of property is "version_info"
        assertThat(mTestClassifierAssetsMetadata.get("version_info")).hasSize(4);
        assertThat(mTestClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "updated_date", "build_id");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "12".
        assertThat(mTestClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("12");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mTestClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset with correct format should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "34"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_test_topics.txt"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_test_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-07-29"
        assertThat(mTestClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-07-29");

        // There should contain 4 metadata attributions in asset "topic_id_to_name"
        assertThat(mTestClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mTestClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "6c4fa0e24cf67c0e830d05196f2b8e66824ca0ebf6ade3229cdd3dedf63cbb96"
        assertThat(mTestClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("6c4fa0e24cf67c0e830d05196f2b8e66824ca0ebf6ade3229cdd3dedf63cbb96");

        // The asset "test_asset2" has two redundant fields. "CLASSIFIER_METADATA_REDUNDANT_ASSET"
        // should be logged twice.
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_ASSET,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
                times(2));
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_bundled_correctFormat() {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "build_id", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "2".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("2");

        // The property "version_info" should have attribution "build_id"
        // and its value should be "1986". This is used for comparing the model version with MDD
        // downloaded model.
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("build_id"))
                .isEqualTo("1986");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "2"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-09-10"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-09-10");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "12a8b7da9566c800e2422543267fa63a2484849b5afeffddd9177825d2e2e157"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("12a8b7da9566c800e2422543267fa63a2484849b5afeffddd9177825d2e2e157");
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_downloaded_correctFormat()
            throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        sContext.getAssets().open(PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH));
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "build_id", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "2".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("2");

        // The property "version_info" should have attribution "build_id"
        // and its value should be "1986". This is used for comparing the model version with MDD
        // downloaded model.
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("build_id"))
                .isEqualTo("1986");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "2"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-09-10"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-09-10");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "12a8b7da9566c800e2422543267fa63a2484849b5afeffddd9177825d2e2e157"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("12a8b7da9566c800e2422543267fa63a2484849b5afeffddd9177825d2e2e157");
    }

    @Test
    public void testRetrieveClassifierInputConfig_bundled_successfulRead() {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig.getInputFormat()).isEqualTo("%s. %s");
        assertThat(classifierInputConfig.getInputFields())
                .containsExactly(
                        ClassifierInputConfig.ClassifierInputField.APP_NAME,
                        ClassifierInputConfig.ClassifierInputField.SPLIT_PACKAGE_NAME);
    }

    @Test
    public void testRetrieveClassifierInputConfig_downloaded_successfulRead() throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH));

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        PRODUCTION_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig.getInputFormat()).isEqualTo("%s. %s");
        assertThat(classifierInputConfig.getInputFields())
                .containsExactly(
                        ClassifierInputConfig.ClassifierInputField.APP_NAME,
                        ClassifierInputConfig.ClassifierInputField.SPLIT_PACKAGE_NAME);
    }

    @Test
    public void testRetrieveClassifierInputConfig_bundled_emptyConfigReturnedOnException() {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig).isEqualTo(ClassifierInputConfig.getEmptyConfig());
    }

    @Test
    public void testRetrieveClassifierInputConfig_downloaded_emptyConfigReturnedOnException()
            throws IOException {
        // Mock a MDD FileGroup and FileStorage
        InputStream inputStream = SdkLevel.isAtLeastT() ? FileInputStream.nullInputStream() : null;
        when(mMockFileStorage.open(any(), any())).thenReturn(inputStream);

        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig).isEqualTo(ClassifierInputConfig.getEmptyConfig());
    }

    @Test
    public void testRetrieveClassifierInputConfig_emptyConfigReturnedOnInvalidConfigField()
            throws IOException {
        String invalidClassifierInputConfig = "%s\nINVALID_FIELD";
        InputStream inputStream =
                new ByteArrayInputStream(
                        invalidClassifierInputConfig.getBytes(StandardCharsets.UTF_8));

        Context mockContext = mock(Context.class);
        AssetManager mockAssetManager = mock(AssetManager.class);

        when(mockContext.getAssets()).thenReturn(mockAssetManager);
        when(mockAssetManager.open(any())).thenReturn(inputStream);

        mProductionModelManager =
                new ModelManager(
                        mockContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig).isEqualTo(ClassifierInputConfig.getEmptyConfig());
    }

    @Test
    public void testRetrieveClassifierInputConfig_emptyConfigReturnedOnInvalidConfigFormat()
            throws IOException {
        String invalidClassifierInputConfig = "%s -> %s\nAPP_DESCRIPTION";
        InputStream inputStream =
                new ByteArrayInputStream(
                        invalidClassifierInputConfig.getBytes(StandardCharsets.UTF_8));

        Context mockContext = mock(Context.class);
        AssetManager mockAssetManager = mock(AssetManager.class);

        when(mockContext.getAssets()).thenReturn(mockAssetManager);
        when(mockAssetManager.open(any())).thenReturn(inputStream);

        mProductionModelManager =
                new ModelManager(
                        mockContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ClassifierInputConfig classifierInputConfig =
                mProductionModelManager.retrieveClassifierInputConfig();

        assertThat(classifierInputConfig).isEqualTo(ClassifierInputConfig.getEmptyConfig());
    }

    @Test
    public void testIsModelAvailable_downloadedModelIsAvailable() {
        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        // If the downloaded model is available, return true.
        assertThat(mTestModelManager.isModelAvailable()).isTrue();
    }

    @Test
    public void testIsModelAvailable_nonNullBundledModel() {
        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        TEST_CLASSIFIER_MODEL_PATH,
                        mMockFileStorage,
                        null /*No downloaded files.*/);

        // If the bundled model is available and non-null, return true.
        assertThat(mTestModelManager.isModelAvailable()).isTrue();
    }

    @Test
    public void testIsModelAvailable_nullBundledModel() {
        doNothingOnErrorLogUtilError();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        "ModelWrongPath",
                        mMockFileStorage,
                        /* downloadedFiles= */ null);

        // If the bundled model is available but null, return false.
        assertThat(mTestModelManager.isModelAvailable()).isFalse();

        verifyErrorLogUtilErrorWithAnyException(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__NO_CLASSIFIER_MODEL_AVAILABLE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_wrongFormat() {
        doNothingOnErrorLogUtilError();

        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        TEST_CLASSIFIER_INPUT_CONFIG_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 1 metadata attributions in asset "test_asset1",
        // because it doesn't have "checksum" and "updated_date"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1")).hasSize(1);

        // The asset "test_asset1" should have attribution "path" and its value should be
        // "assets/classifier/test1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1").get("path"))
                .isEqualTo("assets/classifier/test1");

        // There should contain 4 metadata attributions in asset "test_asset2",
        // because "redundant_field1" and "redundant_field2" are not correct attributions.
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2")).hasSize(4);

        // The asset "test_asset2" should have attribution "path" and its value should be
        // "assets/classifier/test2"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2").get("path"))
                .isEqualTo("assets/classifier/test2");

        // The asset "test_asset2" shouldn't have redundant attribution "redundant_field1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2"))
                .doesNotContainKey("redundant_field1");

        // The asset "test_asset2" has two redundant fields. "CLASSIFIER_METADATA_REDUNDANT_ASSET"
        // should be logged twice.
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_ASSET,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS,
                times(2));
    }
}
