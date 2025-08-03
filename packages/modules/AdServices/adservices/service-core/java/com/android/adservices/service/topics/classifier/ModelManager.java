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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_MISSING_PROPERTY_OR_ASSET_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_ASSET;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_PROPERTY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_CLASSIFIER_MODEL_FILE_LOAD_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_CLASSIFIER_MODEL_FILE_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__INVALID_TOPIC_ID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__NO_CLASSIFIER_MODEL_AVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_BUNDLED_METADATA_FILE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_CLASSIFIER_ASSETS_METADATA_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_LABELS_FILE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_PRECOMUTRED_APP_TOPICS_LIST_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_PRECOMUTRED_LABELS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_TOP_APPS_FILE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.util.ArrayMap;
import android.util.JsonReader;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.LoggerFactory;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.topics.classifier.ClassifierInputConfig.ClassifierInputField;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.MappedByteBufferOpener;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;
import com.google.mobiledatadownload.ClientConfigProto.ClientFileGroup;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Model Manager.
 *
 * <p>Model Manager to manage models used the Classifier. Currently, there are 2 types of models: 1)
 * Bundled Model in the APK. 2) Downloaded Model via MDD.
 *
 * <p>ModelManager will select the right model to serve Classifier.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ModelManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    public static final String BUNDLED_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    public static final String BUNDLED_TOP_APP_FILE_PATH = "classifier/precomputed_app_list.csv";
    public static final String BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String BUNDLED_CLASSIFIER_INPUT_CONFIG_FILE_PATH =
            "classifier/classifier_input_config.txt";
    public static final String BUNDLED_MODEL_FILE_PATH = "classifier/model.tflite";

    private static final String FILE_GROUP_NAME = "topics-classifier-model";

    // Use "\t" as a delimiter to read the precomputed app topics file
    private static final String LIST_COLUMN_DELIMITER = "\t";
    // Use "," as a delimiter to read multi-topics of one app in precomputed app topics file
    private static final String TOPICS_DELIMITER = ",";
    // Arbitrary string representing contents of a classifier input field to validate input format.
    private static final String CLASSIFIER_INPUT_FIELD = "CLASSIFIER_INPUT_FIELD";

    // The key name of asset metadata property in classifier_assets_metadata.json
    private static final String ASSET_PROPERTY_NAME = "property";
    // The key name of asset element in classifier_assets_metadata.json
    private static final String ASSET_ELEMENT_NAME = "asset_name";
    // The attributions of assets property in classifier_assets_metadata.json
    private static final Set<String> ASSETS_PROPERTY_ATTRIBUTIONS =
            new HashSet(
                    Arrays.asList("taxonomy_type", "taxonomy_version", "build_id", "updated_date"));
    // The attributions of assets metadata in classifier_assets_metadata.json
    private static final Set<String> ASSETS_NORMAL_ATTRIBUTIONS =
            new HashSet(Arrays.asList("asset_version", "path", "checksum", "updated_date"));

    private static final String DOWNLOADED_LABEL_FILE_ID = "labels_topics.txt";
    private static final String DOWNLOADED_TOP_APPS_FILE_ID = "precomputed_app_list.csv";
    private static final String DOWNLOADED_CLASSIFIER_ASSETS_METADATA_FILE_ID =
            "classifier_assets_metadata.json";
    private static final String DOWNLOADED_CLASSIFIER_INPUT_CONFIG_FILE_ID =
            "classifier_input_config.txt";
    private static final String DOWNLOADED_MODEL_FILE_ID = "model.tflite";

    private static ModelManager sSingleton;
    private final Context mContext;
    private final AssetManager mAssetManager;
    private final String mLabelsFilePath;
    private final String mTopAppsFilePath;
    private final String mClassifierAssetsMetadataPath;
    private final String mModelFilePath;
    private final String mClassifierInputConfigPath;
    private final SynchronousFileStorage mFileStorage;
    private final Map<String, ClientFile> mDownloadedFiles;

    @VisibleForTesting
    ModelManager(
            @NonNull Context context,
            @NonNull String labelsFilePath,
            @NonNull String topAppsFilePath,
            @NonNull String classifierAssetsMetadataPath,
            @NonNull String classifierInputConfigPath,
            @NonNull String modelFilePath,
            @NonNull SynchronousFileStorage fileStorage,
            @Nullable Map<String, ClientFile> downloadedFiles) {
        mContext = context.getApplicationContext();
        mAssetManager = context.getAssets();
        mLabelsFilePath = labelsFilePath;
        mTopAppsFilePath = topAppsFilePath;
        mClassifierAssetsMetadataPath = classifierAssetsMetadataPath;
        mClassifierInputConfigPath = classifierInputConfigPath;
        mModelFilePath = modelFilePath;
        mFileStorage = fileStorage;
        mDownloadedFiles = downloadedFiles;
    }

    /** Returns the singleton instance of the {@link ModelManager} given a context. */
    @NonNull
    public static ModelManager getInstance(@NonNull Context context) {
        synchronized (ModelManager.class) {
            if (sSingleton == null) {
                sSingleton =
                        new ModelManager(
                                context,
                                BUNDLED_LABELS_FILE_PATH,
                                BUNDLED_TOP_APP_FILE_PATH,
                                BUNDLED_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                                BUNDLED_CLASSIFIER_INPUT_CONFIG_FILE_PATH,
                                BUNDLED_MODEL_FILE_PATH,
                                MobileDataDownloadFactory.getFileStorage(context),
                                getDownloadedFiles(context));
            }
        }
        return sSingleton;
    }

    /**
     * This function populates metadata files to a map.
     *
     * @param context {@link Context}
     * @return A map<FileId, ClientFile> contains downloaded fileId with ClientFile or null if no
     *     downloaded files found.
     */
    @VisibleForTesting
    static @Nullable Map<String, ClientFile> getDownloadedFiles(@NonNull Context context) {
        ClientFileGroup fileGroup = getClientFileGroup(context);
        if (fileGroup == null) {
            sLogger.d("ClientFileGroup is null.");
            return null;
        }
        Map<String, ClientFile> downloadedFiles = new ArrayMap<>();
        sLogger.v("Populating downloadFiles map.");
        fileGroup.getFileList().stream()
                .forEach(file -> downloadedFiles.put(file.getFileId(), file));
        return downloadedFiles;
    }

    /** Returns topics-classifier-model ClientFileGroup */
    @VisibleForTesting
    @Nullable
    static ClientFileGroup getClientFileGroup(@NonNull Context context) {
        MobileDataDownload mobileDataDownload =
                MobileDataDownloadFactory.getMdd(context, FlagsFactory.getFlags());
        GetFileGroupRequest getFileGroupRequest =
                GetFileGroupRequest.newBuilder().setGroupName(FILE_GROUP_NAME).build();
        ClientFileGroup fileGroup = null;
        try {
            // TODO(b/242908564). Remove get()
            fileGroup = mobileDataDownload.getFileGroup(getFileGroupRequest).get();
        } catch (ExecutionException | InterruptedException e) {
            sLogger.e(e, "Unable to load MDD file group.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__LOAD_MDD_FILE_GROUP_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return null;
        }
        return fileGroup;
    }

    /**
     * Returns the build id of model that will be used for classification. This function will
     * compare the build id from bundled asset and the downloaded model and choose the newer build
     * id.
     */
    public long getBuildId() {
        return useDownloadedFiles()
                ? getDownloadedModelBuildId()
                : CommonClassifierHelper.getBundledModelBuildId(
                        mContext, mClassifierAssetsMetadataPath);
    }

    // Return true if Model Manager should use downloaded model. Otherwise, use bundled model.
    @VisibleForTesting
    boolean useDownloadedFiles() {
        if (FlagsFactory.getFlags().getClassifierForceUseBundledFiles()) {
            sLogger.d(
                    "ModelManager uses bundled model because flag"
                            + " classifier_force_use_bundled_files is enabled");
            return false;
        } else if (mDownloadedFiles == null || mDownloadedFiles.size() == 0) {
            // Use bundled model if no downloaded files available.
            sLogger.d(
                    "ModelManager uses bundled model because there is no downloaded files"
                            + " available");
            return false;
        }

        long downloadedModelBuildId = getDownloadedModelBuildId();
        long bundledModelBuildId =
                CommonClassifierHelper.getBundledModelBuildId(
                        mContext, mClassifierAssetsMetadataPath);
        if (downloadedModelBuildId <= bundledModelBuildId) {
            // Mdd has not downloaded new version of model. Use bundled model.
            sLogger.d(
                    "ModelManager uses bundled model build id = %d because downloaded model build"
                            + " id = %d is not the latest version",
                    bundledModelBuildId, downloadedModelBuildId);
            return false;
        }
        sLogger.d("ModelManager uses downloaded model build id = %d", downloadedModelBuildId);
        return true;
    }

    /**
     * Load TFLite model as a ByteBuffer.
     *
     * @throws IOException if failed to read downloaded or bundled model file.
     */
    @NonNull
    public ByteBuffer retrieveModel() throws IOException {
        if (useDownloadedFiles()) {
            ClientFile downloadedFile = mDownloadedFiles.get(DOWNLOADED_MODEL_FILE_ID);
            MappedByteBuffer buffer = null;
            if (downloadedFile == null) {
                sLogger.e("Failed to find downloaded model file");
                ErrorLogUtil.e(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_CLASSIFIER_MODEL_FILE_NOT_FOUND,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                return ByteBuffer.allocate(0);
            } else {
                buffer =
                        mFileStorage.open(
                                Uri.parse(downloadedFile.getFileUri()),
                                MappedByteBufferOpener.createForRead());
                return buffer;
            }
        } else {
            try {
                // Use bundled files.
                AssetFileDescriptor fileDescriptor = mAssetManager.openFd(mModelFilePath);
                FileInputStream inputStream =
                        new FileInputStream(fileDescriptor.getFileDescriptor());
                FileChannel fileChannel = inputStream.getChannel();

                long startOffset = fileDescriptor.getStartOffset();
                long declaredLength = fileDescriptor.getDeclaredLength();
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            } catch (IOException | NullPointerException e) {
                sLogger.e(e, "Error loading the bundled classifier model");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_LOAD_ML_MODEL_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                return ByteBuffer.allocate(0);
            }
        }
    }

    /** Returns true if the classifier model is available for classification. */
    public boolean isModelAvailable() {
        if (useDownloadedFiles()) {
            // Downloaded model is always expected to be available.
            return true;
        } else {
            // Check if the non-zero model file is present in the apk assets.
            try {
                return mAssetManager.openFd(mModelFilePath).getLength() > 0;
            } catch (IOException e) {
                sLogger.e(e, "[ML] No classifier model available.");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__NO_CLASSIFIER_MODEL_AVAILABLE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                return false;
            }
        }
    }

    /**
     * Retrieve a list of topicIDs from labels file.
     *
     * @return The list of topicIDs from downloaded or bundled labels file. Empty list will be
     *     returned for {@link IOException}.
     */
    @NonNull
    public ImmutableList<Integer> retrieveLabels() {
        ImmutableList.Builder<Integer> labels = new ImmutableList.Builder();
        InputStream inputStream = null; // InputStream.nullInputStream() is not available on S-.
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_LABEL_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mLabelsFilePath);
            } catch (IOException e) {
                sLogger.e(e, "Failed to read labels file");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_LABELS_FILE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
        return inputStream == null ? labels.build() : getLabelsList(labels, inputStream);
    }

    @NonNull
    private ImmutableList<Integer> getLabelsList(
            @NonNull ImmutableList.Builder<Integer> labels, @NonNull InputStream inputStream) {
        String line;
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            while ((line = reader.readLine()) != null) {
                // If the line has at least 1 digit, this line will be added to the labels.
                if (line.length() > 0) {
                    labels.add(Integer.parseInt(line));
                }
            }
        } catch (IOException e) {
            sLogger.e(e, "Unable to read precomputed labels");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_PRECOMUTRED_LABELS_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            // When catching IOException -> return empty immutable list
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableList.of();
        }

        return labels.build();
    }

    /**
     * Retrieve the app classification topicIDs.
     *
     * @return The map from App to the list of its classification topicIDs.
     */
    @NonNull
    public Map<String, List<Integer>> retrieveAppClassificationTopics() {
        // appTopicsMap = Map<App, List<Topic>>
        Map<String, List<Integer>> appTopicsMap = new ArrayMap<>();

        // The immutable set of the topics from labels file
        ImmutableList<Integer> validTopics = retrieveLabels();
        InputStream inputStream = null;
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_TOP_APPS_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mTopAppsFilePath);
            } catch (IOException e) {
                sLogger.e(e, "Failed to read top apps file");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_TOP_APPS_FILE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
        return inputStream == null
                ? appTopicsMap
                : getAppsTopicMap(appTopicsMap, validTopics, inputStream);
    }

    @NonNull
    private Map<String, List<Integer>> getAppsTopicMap(
            @NonNull Map<String, List<Integer>> appTopicsMap,
            @NonNull ImmutableList<Integer> validTopics,
            @NonNull InputStream inputStream) {
        String line;
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            // Skip first line (columns name)
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(LIST_COLUMN_DELIMITER);

                // If the line has less than 2 elements, this app contains empty topic
                // and save an empty topic list of this app in appTopicsMap.
                if (columns.length < 2) {
                    // columns[0] if the app's name
                    appTopicsMap.put(columns[0], ImmutableList.of());
                    continue;
                }

                // The first column is app package name
                String app = columns[0];

                // The second column is multi-topics of the app
                String[] appTopics = columns[1].split(TOPICS_DELIMITER);

                // This list is used to temporarily store the allowed topicIDs of one app.
                List<Integer> allowedAppTopics = new ArrayList<>();

                for (String appTopic : appTopics) {
                    // The topic will not save to the app topics map
                    // if it is not a valid topic in labels file
                    if (!validTopics.contains(Integer.parseInt(appTopic))) {
                        sLogger.e(
                                "Unable to load topicID \"%s\" in app \"%s\", "
                                        + "because it is not a valid topic in labels file.",
                                appTopic, app);
                        ErrorLogUtil.e(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__INVALID_TOPIC_ID,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                        continue;
                    }

                    // Add the allowed topic to the list
                    allowedAppTopics.add(Integer.parseInt(appTopic));
                }

                appTopicsMap.put(app, ImmutableList.copyOf(allowedAppTopics));
            }
        } catch (IOException e) {
            sLogger.e(e, "Unable to read precomputed app topics list");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_PRECOMUTRED_APP_TOPICS_LIST_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            // When catching IOException -> return empty hash map
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ImmutableMap.of();
        }

        return appTopicsMap;
    }

    /**
     * Retrieve the assets names and their corresponding metadata.
     *
     * @return The immutable map of assets metadata from {@code mClassifierAssetsMetadataPath}.
     *     Empty map will be returned for {@link IOException}.
     */
    @NonNull
    public ImmutableMap<String, ImmutableMap<String, String>> retrieveClassifierAssetsMetadata() {
        // Initialize a ImmutableMap.Builder to store the classifier assets metadata iteratively.
        // classifierAssetsMetadata = ImmutableMap<AssetName, ImmutableMap<MetadataName, Value>>
        ImmutableMap.Builder<String, ImmutableMap<String, String>> classifierAssetsMetadata =
                new ImmutableMap.Builder<>();
        InputStream inputStream = null;
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_CLASSIFIER_ASSETS_METADATA_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mClassifierAssetsMetadataPath);
            } catch (IOException e) {
                sLogger.e(e, "Failed to read bundled metadata file");
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_BUNDLED_METADATA_FILE_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            }
        }
        return inputStream == null
                ? classifierAssetsMetadata.build()
                : getAssetsMetadataMap(classifierAssetsMetadata, inputStream);
    }

    @NonNull
    private ImmutableMap<String, ImmutableMap<String, String>> getAssetsMetadataMap(
            @NonNull
                    ImmutableMap.Builder<String, ImmutableMap<String, String>>
                            classifierAssetsMetadata,
            @NonNull InputStream inputStream) {
        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            JsonReader reader = new JsonReader(inputStreamReader);

            reader.beginArray();
            while (reader.hasNext()) {
                // Use an immutable map to store the metadata of one asset.
                // assetMetadata = ImmutableMap<MetadataName, Value>
                ImmutableMap.Builder<String, String> assetMetadata = new ImmutableMap.Builder<>();

                // Use jsonElementKey to save the key name of each array element.
                String jsonElementKey = null;

                // Begin to read one json element in the array here.
                reader.beginObject();
                if (reader.hasNext()) {
                    String elementKeyName = reader.nextName();

                    if (elementKeyName.equals(ASSET_PROPERTY_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the property's key set.
                            if (ASSETS_PROPERTY_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSETS_PROPERTY_ATTRIBUTIONS.
                                reader.skipValue();
                                sLogger.e(
                                        "%s is a redundant metadata attribution of "
                                                + "metadata property.",
                                        attribution);
                                ErrorLogUtil.e(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_PROPERTY,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                            }
                        }
                    } else if (elementKeyName.equals(ASSET_ELEMENT_NAME)) {
                        jsonElementKey = reader.nextString();

                        while (reader.hasNext()) {
                            String attribution = reader.nextName();
                            // Check if the attribution name can be found in the asset's key set.
                            if (ASSETS_NORMAL_ATTRIBUTIONS.contains(attribution)) {
                                assetMetadata.put(attribution, reader.nextString());
                            } else {
                                // Skip the redundant metadata name if it can't be found
                                // in the ASSET_NORMAL_ATTRIBUTIONS.
                                reader.skipValue();
                                sLogger.e(
                                        "%s is a redundant metadata attribution of asset.",
                                        attribution);
                                ErrorLogUtil.e(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_REDUNDANT_ASSET,
                                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                            }
                        }
                    } else {
                        // Skip the json element if it doesn't have key "property" or "asset_name".
                        while (reader.hasNext()) {
                            reader.skipValue();
                        }
                        sLogger.e(
                                "Can't load this json element, "
                                        + "because \"property\" or \"asset_name\" "
                                        + "can't be found in the json element.");
                        ErrorLogUtil.e(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CLASSIFIER_METADATA_MISSING_PROPERTY_OR_ASSET_NAME,
                                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
                    }
                }
                reader.endObject();

                // Save the metadata of the asset if and only if the assetName can be retrieved
                // correctly from the metadata json file.
                if (jsonElementKey != null) {
                    classifierAssetsMetadata.put(jsonElementKey, assetMetadata.build());
                }
            }
            reader.endArray();
        } catch (IOException e) {
            sLogger.e(e, "Unable to read classifier assets metadata file");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__READ_CLASSIFIER_ASSETS_METADATA_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            // When catching IOException -> return empty immutable map
            return ImmutableMap.of();
        }
        return classifierAssetsMetadata.build();
    }

    /**
     * Retrieve classifier input configuration from config file.
     *
     * @return A ClassifierInputConfig containing the format string for the classifier input and a
     *     list of fields to populate it. Empty ClassifierInputConfig will be returned for {@link
     *     IOException}.
     */
    @NonNull
    public ClassifierInputConfig retrieveClassifierInputConfig() {
        InputStream inputStream = null; // InputStream.nullInputStream() is not available on S-.
        if (useDownloadedFiles()) {
            inputStream = readDownloadedFile(DOWNLOADED_CLASSIFIER_INPUT_CONFIG_FILE_ID);
        } else {
            // Use bundled files.
            try {
                inputStream = mAssetManager.open(mClassifierInputConfigPath);
            } catch (IOException e) {
                LogUtil.e(e, "Failed to read classifier input config file");
            }
        }
        return inputStream == null
                ? ClassifierInputConfig.getEmptyConfig()
                : getClassifierInputConfig(inputStream);
    }

    @NonNull
    private ClassifierInputConfig getClassifierInputConfig(@NonNull InputStream inputStream) {
        String line;
        String inputFormat;
        ImmutableList.Builder<ClassifierInputField> inputFields = ImmutableList.builder();

        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            BufferedReader reader = new BufferedReader(inputStreamReader);

            if ((line = reader.readLine()) == null || line.length() == 0) {
                return ClassifierInputConfig.getEmptyConfig();
            }
            inputFormat = line;

            while ((line = reader.readLine()) != null) {
                // If the line has at least 1 character, this line will be added to the input
                // fields.
                if (line.length() > 0) {
                    try {
                        inputFields.add(ClassifierInputField.valueOf(line));
                    } catch (IllegalArgumentException e) {
                        LogUtil.e("Invalid input field in classifier input config: %s", line);
                        return ClassifierInputConfig.getEmptyConfig();
                    }
                }
            }
        } catch (IOException e) {
            LogUtil.e(e, "Unable to read classifier input config");
            // When catching IOException -> return empty ClassifierInputConfig
            // TODO(b/226944089): A strategy to handle exceptions
            //  in Classifier and PrecomputedLoader
            return ClassifierInputConfig.getEmptyConfig();
        }

        ClassifierInputConfig classifierInputConfig =
                new ClassifierInputConfig(inputFormat, inputFields.build());

        if (!validateClassifierInputConfig(classifierInputConfig)) {
            return ClassifierInputConfig.getEmptyConfig();
        }

        return classifierInputConfig;
    }

    @NonNull
    private boolean validateClassifierInputConfig(
            @NonNull ClassifierInputConfig classifierInputConfig) {
        String[] inputFields = new String[classifierInputConfig.getInputFields().size()];
        Arrays.fill(inputFields, CLASSIFIER_INPUT_FIELD);
        try {
            String formattedInput =
                    String.format(classifierInputConfig.getInputFormat(), (Object[]) inputFields);
            LogUtil.d("Validated classifier input format: %s", formattedInput);
        } catch (IllegalFormatException e) {
            LogUtil.e("Classifier input config is incorrectly formatted");
            return false;
        }
        return true;
    }

    // Return an InputStream if downloaded model file can be found by
    // ClientFile.file_id.
    @NonNull
    private InputStream readDownloadedFile(String fileId) {
        InputStream inputStream = null;
        ClientFile downloadedFile = mDownloadedFiles.get(fileId);
        if (downloadedFile == null) {
            sLogger.e("Failed to find downloaded %s file", fileId);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_CLASSIFIER_MODEL_FILE_NOT_FOUND,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
            return inputStream;
        }
        try {
            inputStream =
                    mFileStorage.open(
                            Uri.parse(downloadedFile.getFileUri()), ReadStreamOpener.create());
        } catch (IOException e) {
            sLogger.e(e, "Failed to load fileId = %s", fileId);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DOWNLOADED_CLASSIFIER_MODEL_FILE_LOAD_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS);
        }
        return inputStream;
    }

    /**
     * Gets downloaded model build id from topics-classifier-model ClientFileGroup. Returns 0 if
     * there is no downloaded file.
     *
     * @return downloaded model build id.
     */
    private long getDownloadedModelBuildId() {
        ClientFileGroup clientFileGroup = getClientFileGroup(mContext);
        if (clientFileGroup == null) {
            return 0;
        }
        return clientFileGroup.getBuildId();
    }
}
