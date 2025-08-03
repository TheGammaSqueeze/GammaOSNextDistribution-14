/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.common;

import static java.util.concurrent.TimeUnit.HOURS;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.text.TextUtils;
import android.view.textclassifier.ConversationAction;
import android.view.textclassifier.TextClassifier;
import androidx.annotation.NonNull;
import com.android.textclassifier.utils.IndentingPrintWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * TextClassifier specific settings.
 *
 * <p>Currently, this class does not guarantee co-diverted flags are updated atomically.
 *
 * <p>Example of setting the values for testing.
 *
 * <pre>
 * adb shell cmd device_config put textclassifier system_textclassifier_enabled true
 * </pre>
 *
 * @see android.provider.DeviceConfig#NAMESPACE_TEXTCLASSIFIER
 */
public final class TextClassifierSettings {
  private static final String TAG = "TextClassifierSettings";
  public static final String NAMESPACE = DeviceConfig.NAMESPACE_TEXTCLASSIFIER;

  private static final String DELIMITER = ":";

  /** Whether the user language profile feature is enabled. */
  private static final String USER_LANGUAGE_PROFILE_ENABLED = "user_language_profile_enabled";

  /** Max length of text that suggestSelection can accept. */
  @VisibleForTesting
  static final String SUGGEST_SELECTION_MAX_RANGE_LENGTH = "suggest_selection_max_range_length";

  /** Max length of text that classifyText can accept. */
  private static final String CLASSIFY_TEXT_MAX_RANGE_LENGTH = "classify_text_max_range_length";

  /** Max length of text that generateLinks can accept. */
  private static final String GENERATE_LINKS_MAX_TEXT_LENGTH = "generate_links_max_text_length";

  /** Sampling rate for generateLinks logging. */
  private static final String GENERATE_LINKS_LOG_SAMPLE_RATE = "generate_links_log_sample_rate";

  /**
   * Extra count that is added to some languages, e.g. system languages, when deducing the frequent
   * languages in {@link
   * com.android.textclassifier.ulp.LanguageProfileAnalyzer#getFrequentLanguages(int)}.
   */

  /**
   * A colon(:) separated string that specifies the default entities types for generateLinks when
   * hint is not given.
   */
  @VisibleForTesting static final String ENTITY_LIST_DEFAULT = "entity_list_default";

  /**
   * A colon(:) separated string that specifies the default entities types for generateLinks when
   * the text is in a not editable UI widget.
   */
  private static final String ENTITY_LIST_NOT_EDITABLE = "entity_list_not_editable";

  /**
   * A colon(:) separated string that specifies the default entities types for generateLinks when
   * the text is in an editable UI widget.
   */
  private static final String ENTITY_LIST_EDITABLE = "entity_list_editable";

  /**
   * A colon(:) separated string that specifies the default action types for
   * suggestConversationActions when the suggestions are used in an app.
   */
  private static final String IN_APP_CONVERSATION_ACTION_TYPES_DEFAULT =
      "in_app_conversation_action_types_default";

  /**
   * A colon(:) separated string that specifies the default action types for
   * suggestConversationActions when the suggestions are used in a notification.
   */
  private static final String NOTIFICATION_CONVERSATION_ACTION_TYPES_DEFAULT =
      "notification_conversation_action_types_default";

  /** Threshold to accept a suggested language from LangID model. */
  @VisibleForTesting static final String LANG_ID_THRESHOLD_OVERRIDE = "lang_id_threshold_override";

  /** Whether to enable {@link com.android.textclassifier.intent.TemplateIntentFactory}. */
  @VisibleForTesting
  static final String TEMPLATE_INTENT_FACTORY_ENABLED = "template_intent_factory_enabled";

  /** Whether to enable "translate" action in classifyText. */
  private static final String TRANSLATE_IN_CLASSIFICATION_ENABLED =
      "translate_in_classification_enabled";

  /**
   * Whether to detect the languages of the text in request by using langId for the native model.
   */
  private static final String DETECT_LANGUAGES_FROM_TEXT_ENABLED =
      "detect_languages_from_text_enabled";

  /** Whether to use models downloaded by config updater. */
  private static final String CONFIG_UPDATER_MODEL_ENABLED = "config_updater_model_enabled";

  /** Whether to enable model downloading with ModelDownloadManager */
  @VisibleForTesting
  public static final String MODEL_DOWNLOAD_MANAGER_ENABLED = "model_download_manager_enabled";

  /** Type of network to download model manifest. A String value of androidx.work.NetworkType. */
  private static final String MANIFEST_DOWNLOAD_REQUIRED_NETWORK_TYPE =
      "manifest_download_required_network_type";

  /** Max attempts allowed for a single ModelDownloader downloading task. */
  @VisibleForTesting
  static final String MODEL_DOWNLOAD_WORKER_MAX_ATTEMPTS = "model_download_worker_max_attempts";

  /** Max attempts allowed for a certain manifest url. */
  @VisibleForTesting
  public static final String MANIFEST_DOWNLOAD_MAX_ATTEMPTS = "manifest_download_max_attempts";

  @VisibleForTesting
  static final String MODEL_DOWNLOAD_BACKOFF_DELAY_IN_MILLIS =
      "model_download_backoff_delay_in_millis";

  private static final String MANIFEST_DOWNLOAD_REQUIRES_CHARGING =
      "manifest_download_requires_charging";
  private static final String MANIFEST_DOWNLOAD_REQUIRES_DEVICE_IDLE =
      "manifest_download_requires_device_idle";

  /** Flag name for manifest url is dynamically formatted based on model type and model language. */
  @VisibleForTesting public static final String MANIFEST_URL_TEMPLATE = "manifest_url_%s_%s";

  @VisibleForTesting public static final String MODEL_URL_BLOCKLIST = "model_url_blocklist";
  @VisibleForTesting public static final String MODEL_URL_BLOCKLIST_SEPARATOR = ",";

  /** Flags to control multi-language support settings. */
  @VisibleForTesting
  public static final String MULTI_LANGUAGE_SUPPORT_ENABLED = "multi_language_support_enabled";

  @VisibleForTesting
  public static final String MULTI_LANGUAGE_MODELS_LIMIT = "multi_language_models_limit";

  @VisibleForTesting
  public static final String ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT =
      "enabled_model_types_for_multi_language_support";

  @VisibleForTesting
  public static final String MULTI_ANNOTATOR_CACHE_ENABLED = "multi_annotator_cache_enabled";

  private static final String MULTI_ANNOTATOR_CACHE_SIZE = "multi_annotator_cache_size";

  /** List of locale tags to override LocaleList for TextClassifier. Testing/debugging only. */
  @VisibleForTesting
  public static final String TESTING_LOCALE_LIST_OVERRIDE = "testing_locale_list_override";

  /** Sampling rate for TextClassifier API logging. */
  static final String TEXTCLASSIFIER_API_LOG_SAMPLE_RATE = "textclassifier_api_log_sample_rate";

  /** The size of the cache of the mapping of session id to text classification context. */
  private static final String SESSION_ID_TO_CONTEXT_CACHE_SIZE = "session_id_to_context_cache_size";

  /**
   * A colon(:) separated string that specifies the configuration to use when including surrounding
   * context text in language detection queries.
   *
   * <p>Format= minimumTextSize<int>:penalizeRatio<float>:textScoreRatio<float>
   *
   * <p>e.g. 20:1.0:0.4
   *
   * <p>Accept all text lengths with minimumTextSize=0
   *
   * <p>Reject all text less than minimumTextSize with penalizeRatio=0
   *
   * @see {@code TextClassifierImpl#detectLanguages(String, int, int)} for reference.
   */
  @VisibleForTesting static final String LANG_ID_CONTEXT_SETTINGS = "lang_id_context_settings";

  /** Default threshold to translate the language of the context the user selects */
  private static final String TRANSLATE_ACTION_THRESHOLD = "translate_action_threshold";

  // Sync this with ConversationAction.TYPE_ADD_CONTACT;
  public static final String TYPE_ADD_CONTACT = "add_contact";
  // Sync this with ConversationAction.COPY;
  public static final String TYPE_COPY = "copy";

  private static final int SUGGEST_SELECTION_MAX_RANGE_LENGTH_DEFAULT = 10 * 1000;
  private static final int CLASSIFY_TEXT_MAX_RANGE_LENGTH_DEFAULT = 10 * 1000;
  private static final int GENERATE_LINKS_MAX_TEXT_LENGTH_DEFAULT = 100 * 1000;
  private static final int GENERATE_LINKS_LOG_SAMPLE_RATE_DEFAULT = 100;

  private static final ImmutableList<String> ENTITY_LIST_DEFAULT_VALUE =
      ImmutableList.of(
          TextClassifier.TYPE_ADDRESS,
          TextClassifier.TYPE_EMAIL,
          TextClassifier.TYPE_PHONE,
          TextClassifier.TYPE_URL,
          TextClassifier.TYPE_DATE,
          TextClassifier.TYPE_DATE_TIME,
          TextClassifier.TYPE_FLIGHT_NUMBER);
  private static final ImmutableList<String> CONVERSATION_ACTIONS_TYPES_DEFAULT_VALUES =
      ImmutableList.of(
          ConversationAction.TYPE_TEXT_REPLY,
          ConversationAction.TYPE_CREATE_REMINDER,
          ConversationAction.TYPE_CALL_PHONE,
          ConversationAction.TYPE_OPEN_URL,
          ConversationAction.TYPE_SEND_EMAIL,
          ConversationAction.TYPE_SEND_SMS,
          ConversationAction.TYPE_TRACK_FLIGHT,
          ConversationAction.TYPE_VIEW_CALENDAR,
          ConversationAction.TYPE_VIEW_MAP,
          TYPE_ADD_CONTACT,
          TYPE_COPY);

  /**
   * < 0 : Not set. Use value from LangId model. 0 - 1: Override value in LangId model.
   *
   * @see EntityConfidence
   */
  private static final float LANG_ID_THRESHOLD_OVERRIDE_DEFAULT = -1f;

  private static final float TRANSLATE_ACTION_THRESHOLD_DEFAULT = 0.5f;

  private static final boolean USER_LANGUAGE_PROFILE_ENABLED_DEFAULT = true;
  private static final boolean TEMPLATE_INTENT_FACTORY_ENABLED_DEFAULT = true;
  private static final boolean TRANSLATE_IN_CLASSIFICATION_ENABLED_DEFAULT = true;
  private static final boolean DETECT_LANGUAGES_FROM_TEXT_ENABLED_DEFAULT = true;
  private static final boolean CONFIG_UPDATER_MODEL_ENABLED_DEFAULT = true;
  private static final boolean MODEL_DOWNLOAD_MANAGER_ENABLED_DEFAULT = false;
  private static final String MANIFEST_DOWNLOAD_REQUIRED_NETWORK_TYPE_DEFAULT = "UNMETERED";
  private static final int MODEL_DOWNLOAD_WORKER_MAX_ATTEMPTS_DEFAULT = 5;
  private static final int MANIFEST_DOWNLOAD_MAX_ATTEMPTS_DEFAULT = 3;
  private static final long MODEL_DOWNLOAD_BACKOFF_DELAY_IN_MILLIS_DEFAULT = HOURS.toMillis(1);
  private static final boolean MANIFEST_DOWNLOAD_REQUIRES_DEVICE_IDLE_DEFAULT = false;
  private static final boolean MANIFEST_DOWNLOAD_REQUIRES_CHARGING_WEAR_DEFAULT = true;
  private static final boolean MANIFEST_DOWNLOAD_REQUIRES_CHARGING_DEFAULT = false;
  private static final boolean MULTI_LANGUAGE_SUPPORT_ENABLED_DEFAULT = false;
  private static final int MULTI_LANGUAGE_MODELS_LIMIT_DEFAULT = 2;
  private static final ImmutableList<String>
      ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT_DEFAULT =
          ImmutableList.of(ModelType.ANNOTATOR);
  private static final boolean MULTI_ANNOTATOR_CACHE_ENABLED_DEFAULT = false;
  private static final int MULTI_ANNOTATOR_CACHE_SIZE_DEFAULT = 2;
  private static final String MANIFEST_URL_DEFAULT = "";
  private static final String TESTING_LOCALE_LIST_OVERRIDE_DEFAULT = "";
  private static final float[] LANG_ID_CONTEXT_SETTINGS_DEFAULT = new float[] {20f, 1.0f, 0.4f};

  /**
   * Sampling rate for API logging. For example, 100 means there is a 0.01 chance that the API call
   * is the logged.
   */
  private static final int TEXTCLASSIFIER_API_LOG_SAMPLE_RATE_DEFAULT = 10;

  private static final int SESSION_ID_TO_CONTEXT_CACHE_SIZE_DEFAULT = 10;

  // TODO(licha): Consider removing this. We can use real device config for testing.
  /** DeviceConfig interface to facilitate testing. */
  @VisibleForTesting
  public interface IDeviceConfig {
    default Properties getProperties(@NonNull String namespace, @NonNull String... names) {
      return new Properties.Builder(namespace).build();
    }

    default int getInt(@NonNull String namespace, @NonNull String name, @NonNull int defaultValue) {
      return defaultValue;
    }

    default long getLong(
        @NonNull String namespace, @NonNull String name, @NonNull long defaultValue) {
      return defaultValue;
    }

    default float getFloat(
        @NonNull String namespace, @NonNull String name, @NonNull float defaultValue) {
      return defaultValue;
    }

    default String getString(
        @NonNull String namespace, @NonNull String name, @Nullable String defaultValue) {
      return defaultValue;
    }

    default boolean getBoolean(
        @NonNull String namespace, @NonNull String name, boolean defaultValue) {
      return defaultValue;
    }
  }

  private static final IDeviceConfig DEFAULT_DEVICE_CONFIG =
      new IDeviceConfig() {
        @Override
        public Properties getProperties(@NonNull String namespace, @NonNull String... names) {
          return DeviceConfig.getProperties(namespace, names);
        }

        @Override
        public int getInt(
            @NonNull String namespace, @NonNull String name, @NonNull int defaultValue) {
          return DeviceConfig.getInt(namespace, name, defaultValue);
        }

        @Override
        public long getLong(
            @NonNull String namespace, @NonNull String name, @NonNull long defaultValue) {
          return DeviceConfig.getLong(namespace, name, defaultValue);
        }

        @Override
        public float getFloat(
            @NonNull String namespace, @NonNull String name, @NonNull float defaultValue) {
          return DeviceConfig.getFloat(namespace, name, defaultValue);
        }

        @Override
        public String getString(
            @NonNull String namespace, @NonNull String name, @NonNull String defaultValue) {
          return DeviceConfig.getString(namespace, name, defaultValue);
        }

        @Override
        public boolean getBoolean(
            @NonNull String namespace, @NonNull String name, @NonNull boolean defaultValue) {
          return DeviceConfig.getBoolean(namespace, name, defaultValue);
        }
      };

  private final IDeviceConfig deviceConfig;
  private final boolean isWear;

  public TextClassifierSettings(Context context) {
    this(
        DEFAULT_DEVICE_CONFIG,
        context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));
  }

  @VisibleForTesting
  public TextClassifierSettings(IDeviceConfig deviceConfig, boolean isWear) {
    this.deviceConfig = deviceConfig;
    this.isWear = isWear;
  }

  public int getSuggestSelectionMaxRangeLength() {
    return deviceConfig.getInt(
        NAMESPACE, SUGGEST_SELECTION_MAX_RANGE_LENGTH, SUGGEST_SELECTION_MAX_RANGE_LENGTH_DEFAULT);
  }

  public int getClassifyTextMaxRangeLength() {
    return deviceConfig.getInt(
        NAMESPACE, CLASSIFY_TEXT_MAX_RANGE_LENGTH, CLASSIFY_TEXT_MAX_RANGE_LENGTH_DEFAULT);
  }

  public int getGenerateLinksMaxTextLength() {
    return deviceConfig.getInt(
        NAMESPACE, GENERATE_LINKS_MAX_TEXT_LENGTH, GENERATE_LINKS_MAX_TEXT_LENGTH_DEFAULT);
  }

  public int getGenerateLinksLogSampleRate() {
    return deviceConfig.getInt(
        NAMESPACE, GENERATE_LINKS_LOG_SAMPLE_RATE, GENERATE_LINKS_LOG_SAMPLE_RATE_DEFAULT);
  }

  public List<String> getEntityListDefault() {
    return getDeviceConfigStringList(ENTITY_LIST_DEFAULT, ENTITY_LIST_DEFAULT_VALUE);
  }

  public List<String> getEntityListNotEditable() {
    return getDeviceConfigStringList(ENTITY_LIST_NOT_EDITABLE, ENTITY_LIST_DEFAULT_VALUE);
  }

  public List<String> getEntityListEditable() {
    return getDeviceConfigStringList(ENTITY_LIST_EDITABLE, ENTITY_LIST_DEFAULT_VALUE);
  }

  public List<String> getInAppConversationActionTypes() {
    return getDeviceConfigStringList(
        IN_APP_CONVERSATION_ACTION_TYPES_DEFAULT, CONVERSATION_ACTIONS_TYPES_DEFAULT_VALUES);
  }

  public List<String> getNotificationConversationActionTypes() {
    return getDeviceConfigStringList(
        NOTIFICATION_CONVERSATION_ACTION_TYPES_DEFAULT, CONVERSATION_ACTIONS_TYPES_DEFAULT_VALUES);
  }

  public float getLangIdThresholdOverride() {
    return deviceConfig.getFloat(
        NAMESPACE, LANG_ID_THRESHOLD_OVERRIDE, LANG_ID_THRESHOLD_OVERRIDE_DEFAULT);
  }

  public float getTranslateActionThreshold() {
    return deviceConfig.getFloat(
        NAMESPACE, TRANSLATE_ACTION_THRESHOLD, TRANSLATE_ACTION_THRESHOLD_DEFAULT);
  }

  public boolean isUserLanguageProfileEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, USER_LANGUAGE_PROFILE_ENABLED, USER_LANGUAGE_PROFILE_ENABLED_DEFAULT);
  }

  public boolean isTemplateIntentFactoryEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, TEMPLATE_INTENT_FACTORY_ENABLED, TEMPLATE_INTENT_FACTORY_ENABLED_DEFAULT);
  }

  public boolean isTranslateInClassificationEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE,
        TRANSLATE_IN_CLASSIFICATION_ENABLED,
        TRANSLATE_IN_CLASSIFICATION_ENABLED_DEFAULT);
  }

  public boolean isDetectLanguagesFromTextEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, DETECT_LANGUAGES_FROM_TEXT_ENABLED, DETECT_LANGUAGES_FROM_TEXT_ENABLED_DEFAULT);
  }

  public float[] getLangIdContextSettings() {
    return getDeviceConfigFloatArray(LANG_ID_CONTEXT_SETTINGS, LANG_ID_CONTEXT_SETTINGS_DEFAULT);
  }

  public boolean isConfigUpdaterModelEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, CONFIG_UPDATER_MODEL_ENABLED, CONFIG_UPDATER_MODEL_ENABLED_DEFAULT);
  }

  public boolean isModelDownloadManagerEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, MODEL_DOWNLOAD_MANAGER_ENABLED, MODEL_DOWNLOAD_MANAGER_ENABLED_DEFAULT);
  }

  /** Returns a string which represents a androidx.work.NetworkType enum. */
  public String getManifestDownloadRequiredNetworkType() {
    return deviceConfig.getString(
        NAMESPACE,
        MANIFEST_DOWNLOAD_REQUIRED_NETWORK_TYPE,
        MANIFEST_DOWNLOAD_REQUIRED_NETWORK_TYPE_DEFAULT);
  }

  public int getModelDownloadWorkerMaxAttempts() {
    return deviceConfig.getInt(
        NAMESPACE, MODEL_DOWNLOAD_WORKER_MAX_ATTEMPTS, MODEL_DOWNLOAD_WORKER_MAX_ATTEMPTS_DEFAULT);
  }

  public int getManifestDownloadMaxAttempts() {
    return deviceConfig.getInt(
        NAMESPACE, MANIFEST_DOWNLOAD_MAX_ATTEMPTS, MANIFEST_DOWNLOAD_MAX_ATTEMPTS_DEFAULT);
  }

  public long getModelDownloadBackoffDelayInMillis() {
    return deviceConfig.getLong(
        NAMESPACE,
        MODEL_DOWNLOAD_BACKOFF_DELAY_IN_MILLIS,
        MODEL_DOWNLOAD_BACKOFF_DELAY_IN_MILLIS_DEFAULT);
  }

  public boolean getManifestDownloadRequiresDeviceIdle() {
    return deviceConfig.getBoolean(
        NAMESPACE,
        MANIFEST_DOWNLOAD_REQUIRES_DEVICE_IDLE,
        MANIFEST_DOWNLOAD_REQUIRES_DEVICE_IDLE_DEFAULT);
  }

  public boolean getManifestDownloadRequiresCharging() {
    return deviceConfig.getBoolean(
        NAMESPACE,
        MANIFEST_DOWNLOAD_REQUIRES_CHARGING,
        isWear
            ? MANIFEST_DOWNLOAD_REQUIRES_CHARGING_WEAR_DEFAULT
            : MANIFEST_DOWNLOAD_REQUIRES_CHARGING_DEFAULT);
  }

  /* Gets a list of models urls that should not be used. Usually used for a quick rollback.  */
  public ImmutableList<String> getModelUrlBlocklist() {
    return ImmutableList.copyOf(
        Splitter.on(MODEL_URL_BLOCKLIST_SEPARATOR)
            .split(deviceConfig.getString(NAMESPACE, MODEL_URL_BLOCKLIST, "")));
  }

  public boolean isMultiLanguageSupportEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, MULTI_LANGUAGE_SUPPORT_ENABLED, MULTI_LANGUAGE_SUPPORT_ENABLED_DEFAULT);
  }

  public int getMultiLanguageModelsLimit() {
    return deviceConfig.getInt(
        NAMESPACE, MULTI_LANGUAGE_MODELS_LIMIT, MULTI_LANGUAGE_MODELS_LIMIT_DEFAULT);
  }

  public List<String> getEnabledModelTypesForMultiLanguageSupport() {
    return getDeviceConfigStringList(
        ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT,
        ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT_DEFAULT);
  }

  public boolean getMultiAnnotatorCacheEnabled() {
    return deviceConfig.getBoolean(
        NAMESPACE, MULTI_ANNOTATOR_CACHE_ENABLED, MULTI_ANNOTATOR_CACHE_ENABLED_DEFAULT);
  }

  public int getMultiAnnotatorCacheSize() {
    return deviceConfig.getInt(
        NAMESPACE, MULTI_ANNOTATOR_CACHE_SIZE, MULTI_ANNOTATOR_CACHE_SIZE_DEFAULT);
  }

  /**
   * Gets all language variants and associated manifest url configured for a specific ModelType.
   *
   * <p>For a specific language, there can be many variants: de-CH, de-LI, zh-Hans, zh-Hant. There
   * is no easy way to hardcode the list in client. Therefore, we parse all configured flag's name
   * in DeviceConfig, and let the client to choose the best variant to download.
   *
   * <p>If one flag's value is empty, it will be ignored.
   *
   * @param modelType the type of model for the target url
   * @return <localeTag, flagValue> map.
   */
  public ImmutableMap<String, String> getLanguageTagAndManifestUrlMap(
      @ModelType.ModelTypeDef String modelType) {
    String urlFlagBaseName = String.format(MANIFEST_URL_TEMPLATE, modelType, /* language */ "");
    Properties properties = deviceConfig.getProperties(NAMESPACE);
    ImmutableMap.Builder<String, String> variantsMapBuilder = ImmutableMap.builder();
    for (String name : properties.getKeyset()) {
      if (!name.startsWith(urlFlagBaseName)) {
        continue;
      }
      String value = properties.getString(name, /* defaultValue= */ null);
      if (!TextUtils.isEmpty(value)) {
        String modelLanguageTag = name.substring(urlFlagBaseName.length());
        String urlFlagName = String.format(MANIFEST_URL_TEMPLATE, modelType, modelLanguageTag);
        String urlFlagValue = deviceConfig.getString(NAMESPACE, urlFlagName, MANIFEST_URL_DEFAULT);
        variantsMapBuilder.put(modelLanguageTag, urlFlagValue);
      }
    }
    return variantsMapBuilder.buildOrThrow();
  }

  public String getTestingLocaleListOverride() {
    return deviceConfig.getString(
        NAMESPACE, TESTING_LOCALE_LIST_OVERRIDE, TESTING_LOCALE_LIST_OVERRIDE_DEFAULT);
  }

  public int getTextClassifierApiLogSampleRate() {
    return deviceConfig.getInt(
        NAMESPACE, TEXTCLASSIFIER_API_LOG_SAMPLE_RATE, TEXTCLASSIFIER_API_LOG_SAMPLE_RATE_DEFAULT);
  }

  public int getSessionIdToContextCacheSize() {
    return deviceConfig.getInt(
        NAMESPACE, SESSION_ID_TO_CONTEXT_CACHE_SIZE, SESSION_ID_TO_CONTEXT_CACHE_SIZE_DEFAULT);
  }

  public void dump(IndentingPrintWriter pw) {
    pw.println("TextClassifierSettings:");
    pw.increaseIndent();
    pw.printPair(CLASSIFY_TEXT_MAX_RANGE_LENGTH, getClassifyTextMaxRangeLength());
    pw.printPair(DETECT_LANGUAGES_FROM_TEXT_ENABLED, isDetectLanguagesFromTextEnabled());
    pw.printPair(ENTITY_LIST_DEFAULT, getEntityListDefault());
    pw.printPair(ENTITY_LIST_EDITABLE, getEntityListEditable());
    pw.printPair(ENTITY_LIST_NOT_EDITABLE, getEntityListNotEditable());
    pw.printPair(GENERATE_LINKS_LOG_SAMPLE_RATE, getGenerateLinksLogSampleRate());
    pw.printPair(GENERATE_LINKS_MAX_TEXT_LENGTH, getGenerateLinksMaxTextLength());
    pw.printPair(IN_APP_CONVERSATION_ACTION_TYPES_DEFAULT, getInAppConversationActionTypes());
    pw.printPair(LANG_ID_CONTEXT_SETTINGS, Arrays.toString(getLangIdContextSettings()));
    pw.printPair(LANG_ID_THRESHOLD_OVERRIDE, getLangIdThresholdOverride());
    pw.printPair(TRANSLATE_ACTION_THRESHOLD, getTranslateActionThreshold());
    pw.printPair(
        NOTIFICATION_CONVERSATION_ACTION_TYPES_DEFAULT, getNotificationConversationActionTypes());
    pw.printPair(SUGGEST_SELECTION_MAX_RANGE_LENGTH, getSuggestSelectionMaxRangeLength());
    pw.printPair(USER_LANGUAGE_PROFILE_ENABLED, isUserLanguageProfileEnabled());
    pw.printPair(TEMPLATE_INTENT_FACTORY_ENABLED, isTemplateIntentFactoryEnabled());
    pw.printPair(TRANSLATE_IN_CLASSIFICATION_ENABLED, isTranslateInClassificationEnabled());
    pw.printPair(CONFIG_UPDATER_MODEL_ENABLED, isConfigUpdaterModelEnabled());
    pw.printPair(MODEL_DOWNLOAD_MANAGER_ENABLED, isModelDownloadManagerEnabled());
    pw.printPair(MULTI_LANGUAGE_SUPPORT_ENABLED, isMultiLanguageSupportEnabled());
    pw.printPair(MULTI_LANGUAGE_MODELS_LIMIT, getMultiLanguageModelsLimit());
    pw.printPair(
        ENABLED_MODEL_TYPES_FOR_MULTI_LANGUAGE_SUPPORT,
        getEnabledModelTypesForMultiLanguageSupport());
    pw.printPair(MULTI_ANNOTATOR_CACHE_ENABLED, getMultiAnnotatorCacheEnabled());
    pw.printPair(MULTI_ANNOTATOR_CACHE_SIZE, getMultiAnnotatorCacheSize());
    pw.printPair(MANIFEST_DOWNLOAD_REQUIRED_NETWORK_TYPE, getManifestDownloadRequiredNetworkType());
    pw.printPair(MODEL_DOWNLOAD_WORKER_MAX_ATTEMPTS, getModelDownloadWorkerMaxAttempts());
    pw.printPair(MANIFEST_DOWNLOAD_MAX_ATTEMPTS, getManifestDownloadMaxAttempts());
    pw.printPair(MANIFEST_DOWNLOAD_REQUIRES_CHARGING, getManifestDownloadRequiresCharging());
    pw.printPair(MANIFEST_DOWNLOAD_REQUIRES_DEVICE_IDLE, getManifestDownloadRequiresDeviceIdle());
    pw.printPair(TESTING_LOCALE_LIST_OVERRIDE, getTestingLocaleListOverride());
    pw.decreaseIndent();
    pw.printPair(TEXTCLASSIFIER_API_LOG_SAMPLE_RATE, getTextClassifierApiLogSampleRate());
    pw.printPair(SESSION_ID_TO_CONTEXT_CACHE_SIZE, getSessionIdToContextCacheSize());
    pw.decreaseIndent();
  }

  private List<String> getDeviceConfigStringList(String key, List<String> defaultValue) {
    return parse(deviceConfig.getString(NAMESPACE, key, null), defaultValue);
  }

  private float[] getDeviceConfigFloatArray(String key, float[] defaultValue) {
    return parse(deviceConfig.getString(NAMESPACE, key, null), defaultValue);
  }

  private static List<String> parse(@Nullable String listStr, List<String> defaultValue) {
    if (listStr != null) {
      return Collections.unmodifiableList(Arrays.asList(listStr.split(DELIMITER)));
    }
    return defaultValue;
  }

  private static float[] parse(@Nullable String arrayStr, float[] defaultValue) {
    if (arrayStr != null) {
      final List<String> split = Splitter.onPattern(DELIMITER).splitToList(arrayStr);
      if (split.size() != defaultValue.length) {
        return defaultValue;
      }
      final float[] result = new float[split.size()];
      for (int i = 0; i < split.size(); i++) {
        try {
          result[i] = Float.parseFloat(split.get(i));
        } catch (NumberFormatException e) {
          return defaultValue;
        }
      }
      return result;
    } else {
      return defaultValue;
    }
  }
}
