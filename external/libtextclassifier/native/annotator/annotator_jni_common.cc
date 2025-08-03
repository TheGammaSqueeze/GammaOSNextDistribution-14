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

#include "annotator/annotator_jni_common.h"

#include "annotator/knowledge/knowledge-engine-types.h"
#include "utils/java/jni-base.h"
#include "utils/java/jni-helper.h"

namespace libtextclassifier3 {
namespace {

StatusOr<std::unordered_set<std::string>> EntityTypesFromJObject(
    JNIEnv* env, const jobject& jobject) {
  std::unordered_set<std::string> entity_types;
  jobjectArray jentity_types = reinterpret_cast<jobjectArray>(jobject);
  TC3_ASSIGN_OR_RETURN(const int size,
                       JniHelper::GetArrayLength(env, jentity_types));
  for (int i = 0; i < size; ++i) {
    TC3_ASSIGN_OR_RETURN(
        ScopedLocalRef<jstring> jentity_type,
        JniHelper::GetObjectArrayElement<jstring>(env, jentity_types, i));
    TC3_ASSIGN_OR_RETURN(std::string entity_type,
                         JStringToUtf8String(env, jentity_type.get()));
    entity_types.insert(entity_type);
  }
  return entity_types;
}

template <typename T>
StatusOr<T> FromJavaOptionsInternal(JNIEnv* env, jobject joptions,
                                    const std::string& class_name) {
  if (!joptions) {
    return {Status::UNKNOWN};
  }

  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jclass> options_class,
                       JniHelper::FindClass(env, class_name.c_str()));

  // .getLocale()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_locale,
      JniHelper::GetMethodID(env, options_class.get(), "getLocale",
                             "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> locales,
      JniHelper::CallObjectMethod<jstring>(env, joptions, get_locale));

  // .getReferenceTimeMsUtc()
  TC3_ASSIGN_OR_RETURN(jmethodID get_reference_time_method,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getReferenceTimeMsUtc", "()J"));
  TC3_ASSIGN_OR_RETURN(
      int64 reference_time,
      JniHelper::CallLongMethod(env, joptions, get_reference_time_method));

  // .getReferenceTimezone()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_reference_timezone_method,
      JniHelper::GetMethodID(env, options_class.get(), "getReferenceTimezone",
                             "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> reference_timezone,
                       JniHelper::CallObjectMethod<jstring>(
                           env, joptions, get_reference_timezone_method));

  // .getDetectedTextLanguageTags()
  TC3_ASSIGN_OR_RETURN(jmethodID get_detected_text_language_tags_method,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getDetectedTextLanguageTags",
                                              "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> detected_text_language_tags,
      JniHelper::CallObjectMethod<jstring>(
          env, joptions, get_detected_text_language_tags_method));

  // .getAnnotationUsecase()
  TC3_ASSIGN_OR_RETURN(jmethodID get_annotation_usecase,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getAnnotationUsecase", "()I"));
  TC3_ASSIGN_OR_RETURN(
      int32 annotation_usecase,
      JniHelper::CallIntMethod(env, joptions, get_annotation_usecase));

  // .getUserLocationLat()
  TC3_ASSIGN_OR_RETURN(jmethodID get_user_location_lat,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUserLocationLat", "()D"));
  TC3_ASSIGN_OR_RETURN(
      double user_location_lat,
      JniHelper::CallDoubleMethod(env, joptions, get_user_location_lat));

  // .getUserLocationLng()
  TC3_ASSIGN_OR_RETURN(jmethodID get_user_location_lng,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUserLocationLng", "()D"));
  TC3_ASSIGN_OR_RETURN(
      double user_location_lng,
      JniHelper::CallDoubleMethod(env, joptions, get_user_location_lng));

  // .getUserLocationAccuracyMeters()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_user_location_accuracy_meters,
      JniHelper::GetMethodID(env, options_class.get(),
                             "getUserLocationAccuracyMeters", "()F"));
  TC3_ASSIGN_OR_RETURN(float user_location_accuracy_meters,
                       JniHelper::CallFloatMethod(
                           env, joptions, get_user_location_accuracy_meters));

  // .getUsePodNer()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_use_pod_ner,
      JniHelper::GetMethodID(env, options_class.get(), "getUsePodNer", "()Z"));
  TC3_ASSIGN_OR_RETURN(bool use_pod_ner, JniHelper::CallBooleanMethod(
                                             env, joptions, get_use_pod_ner));

  // .getUseVocabAnnotator()
  TC3_ASSIGN_OR_RETURN(jmethodID get_use_vocab_annotator,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUseVocabAnnotator", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      bool use_vocab_annotator,
      JniHelper::CallBooleanMethod(env, joptions, get_use_vocab_annotator));
  T options;
  TC3_ASSIGN_OR_RETURN(options.locales,
                       JStringToUtf8String(env, locales.get()));
  TC3_ASSIGN_OR_RETURN(options.reference_timezone,
                       JStringToUtf8String(env, reference_timezone.get()));
  options.reference_time_ms_utc = reference_time;
  TC3_ASSIGN_OR_RETURN(
      options.detected_text_language_tags,
      JStringToUtf8String(env, detected_text_language_tags.get()));
  options.annotation_usecase =
      static_cast<AnnotationUsecase>(annotation_usecase);
  options.location_context = {user_location_lat, user_location_lng,
                              user_location_accuracy_meters};
  options.use_pod_ner = use_pod_ner;
  options.use_vocab_annotator = use_vocab_annotator;
  return options;
}
}  // namespace

StatusOr<SelectionOptions> FromJavaSelectionOptions(JNIEnv* env,
                                                    jobject joptions) {
  if (!joptions) {
    // Falling back to default options in case joptions is null
    SelectionOptions default_selection_options;
    return default_selection_options;
  }

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> options_class,
      JniHelper::FindClass(env, TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR
                           "$SelectionOptions"));

  // .getLocale()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_locales,
      JniHelper::GetMethodID(env, options_class.get(), "getLocales",
                             "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> locales,
      JniHelper::CallObjectMethod<jstring>(env, joptions, get_locales));

  // .getDetectedTextLanguageTags()
  TC3_ASSIGN_OR_RETURN(jmethodID get_detected_text_language_tags_method,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getDetectedTextLanguageTags",
                                              "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> detected_text_language_tags,
      JniHelper::CallObjectMethod<jstring>(
          env, joptions, get_detected_text_language_tags_method));

  // .getAnnotationUsecase()
  TC3_ASSIGN_OR_RETURN(jmethodID get_annotation_usecase,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getAnnotationUsecase", "()I"));
  TC3_ASSIGN_OR_RETURN(
      int32 annotation_usecase,
      JniHelper::CallIntMethod(env, joptions, get_annotation_usecase));

  // .getUserLocationLat()
  TC3_ASSIGN_OR_RETURN(jmethodID get_user_location_lat,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUserLocationLat", "()D"));
  TC3_ASSIGN_OR_RETURN(
      double user_location_lat,
      JniHelper::CallDoubleMethod(env, joptions, get_user_location_lat));

  // .getUserLocationLng()
  TC3_ASSIGN_OR_RETURN(jmethodID get_user_location_lng,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUserLocationLng", "()D"));
  TC3_ASSIGN_OR_RETURN(
      double user_location_lng,
      JniHelper::CallDoubleMethod(env, joptions, get_user_location_lng));

  // .getUserLocationAccuracyMeters()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_user_location_accuracy_meters,
      JniHelper::GetMethodID(env, options_class.get(),
                             "getUserLocationAccuracyMeters", "()F"));
  TC3_ASSIGN_OR_RETURN(float user_location_accuracy_meters,
                       JniHelper::CallFloatMethod(
                           env, joptions, get_user_location_accuracy_meters));

  // .getUsePodNer()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_use_pod_ner,
      JniHelper::GetMethodID(env, options_class.get(), "getUsePodNer", "()Z"));
  TC3_ASSIGN_OR_RETURN(bool use_pod_ner, JniHelper::CallBooleanMethod(
                                             env, joptions, get_use_pod_ner));

  SelectionOptions options;
  TC3_ASSIGN_OR_RETURN(options.locales,
                       JStringToUtf8String(env, locales.get()));
  options.annotation_usecase =
      static_cast<AnnotationUsecase>(annotation_usecase);
  TC3_ASSIGN_OR_RETURN(
      options.detected_text_language_tags,
      JStringToUtf8String(env, detected_text_language_tags.get()));
  options.location_context = {user_location_lat, user_location_lng,
                              user_location_accuracy_meters};
  options.use_pod_ner = use_pod_ner;
  return options;
}

StatusOr<ClassificationOptions> FromJavaClassificationOptions(
    JNIEnv* env, jobject joptions) {
  if (!joptions) {
    // Falling back to default options in case joptions is null
    ClassificationOptions default_classification_options;
    return default_classification_options;
  }

  TC3_ASSIGN_OR_RETURN(ClassificationOptions classifier_options,
                       FromJavaOptionsInternal<ClassificationOptions>(
                           env, joptions,
                           TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR
                           "$ClassificationOptions"));

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> options_class,
      JniHelper::FindClass(env, TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR
                           "$ClassificationOptions"));
  // .getUserFamiliarLanguageTags()
  TC3_ASSIGN_OR_RETURN(jmethodID get_user_familiar_language_tags,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getUserFamiliarLanguageTags",
                                              "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> user_familiar_language_tags,
                       JniHelper::CallObjectMethod<jstring>(
                           env, joptions, get_user_familiar_language_tags));

  TC3_ASSIGN_OR_RETURN(
      classifier_options.user_familiar_language_tags,
      JStringToUtf8String(env, user_familiar_language_tags.get()));

  // .getTriggerDictionaryOnBeginnerWords()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_trigger_dictionary_on_beginner_words,
      JniHelper::GetMethodID(env, options_class.get(),
                             "getTriggerDictionaryOnBeginnerWords", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      classifier_options.trigger_dictionary_on_beginner_words,
      JniHelper::CallBooleanMethod(env, joptions,
                                   get_trigger_dictionary_on_beginner_words));

  // .getEnableAddContactIntent()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_enable_add_contact_intent,
      JniHelper::GetMethodID(env, options_class.get(),
                             "getEnableAddContactIntent", "()Z"));
  TC3_ASSIGN_OR_RETURN(classifier_options.enable_add_contact_intent,
                       JniHelper::CallBooleanMethod(
                           env, joptions, get_enable_add_contact_intent));

  // .getEnableSearchIntent()
  TC3_ASSIGN_OR_RETURN(jmethodID get_enable_search_intent,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getEnableSearchIntent", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      classifier_options.enable_search_intent,
      JniHelper::CallBooleanMethod(env, joptions, get_enable_search_intent));

  return classifier_options;
}

StatusOr<AnnotationOptions> FromJavaAnnotationOptions(JNIEnv* env,
                                                      jobject joptions) {
  if (!joptions) {
    // Falling back to default options in case joptions is null
    AnnotationOptions default_annotation_options;
    return default_annotation_options;
  }

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> options_class,
      JniHelper::FindClass(env, TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR
                           "$AnnotationOptions"));

  // .getEntityTypes()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_entity_types,
      JniHelper::GetMethodID(env, options_class.get(), "getEntityTypes",
                             "()[Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jobject> entity_types,
      JniHelper::CallObjectMethod<jobject>(env, joptions, get_entity_types));

  // .isSerializedEntityDataEnabled()
  TC3_ASSIGN_OR_RETURN(
      jmethodID is_serialized_entity_data_enabled_method,
      JniHelper::GetMethodID(env, options_class.get(),
                             "isSerializedEntityDataEnabled", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      bool is_serialized_entity_data_enabled,
      JniHelper::CallBooleanMethod(env, joptions,
                                   is_serialized_entity_data_enabled_method));

  // .hasLocationPermission()
  TC3_ASSIGN_OR_RETURN(jmethodID has_location_permission_method,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "hasLocationPermission", "()Z"));
  TC3_ASSIGN_OR_RETURN(bool has_location_permission,
                       JniHelper::CallBooleanMethod(
                           env, joptions, has_location_permission_method));

  // .hasPersonalizationPermission()
  TC3_ASSIGN_OR_RETURN(
      jmethodID has_personalization_permission_method,
      JniHelper::GetMethodID(env, options_class.get(),
                             "hasPersonalizationPermission", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      bool has_personalization_permission,
      JniHelper::CallBooleanMethod(env, joptions,
                                   has_personalization_permission_method));
  // .getAnnotateMode()
  TC3_ASSIGN_OR_RETURN(jmethodID get_annotate_mode,
                       JniHelper::GetMethodID(env, options_class.get(),
                                              "getAnnotateMode", "()I"));
  TC3_ASSIGN_OR_RETURN(
      int32 annotate_mode,
      JniHelper::CallIntMethod(env, joptions, get_annotate_mode));

  TC3_ASSIGN_OR_RETURN(
      AnnotationOptions annotation_options,
      FromJavaOptionsInternal<AnnotationOptions>(
          env, joptions,
          TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR "$AnnotationOptions"));
  TC3_ASSIGN_OR_RETURN(annotation_options.entity_types,
                       EntityTypesFromJObject(env, entity_types.get()));
  annotation_options.is_serialized_entity_data_enabled =
      is_serialized_entity_data_enabled;
  annotation_options.permissions.has_location_permission =
      has_location_permission;
  annotation_options.permissions.has_personalization_permission =
      has_personalization_permission;
  annotation_options.annotate_mode = static_cast<AnnotateMode>(annotate_mode);

  // .getTriggerDictionaryOnBeginnerWords()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_trigger_dictionary_on_beginner_words,
      JniHelper::GetMethodID(env, options_class.get(),
                             "getTriggerDictionaryOnBeginnerWords", "()Z"));
  TC3_ASSIGN_OR_RETURN(
      annotation_options.trigger_dictionary_on_beginner_words,
      JniHelper::CallBooleanMethod(env, joptions,
                                   get_trigger_dictionary_on_beginner_words));
  return annotation_options;
}

StatusOr<InputFragment> FromJavaInputFragment(JNIEnv* env, jobject jfragment) {
  if (!jfragment) {
    return Status(StatusCode::INTERNAL, "Called with null input fragment.");
  }
  InputFragment fragment;

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> fragment_class,
      JniHelper::FindClass(
          env, TC3_PACKAGE_PATH TC3_ANNOTATOR_CLASS_NAME_STR "$InputFragment"));

  // .getText()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_text,
      JniHelper::GetMethodID(env, fragment_class.get(), "getText",
                             "()Ljava/lang/String;"));

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> text,
      JniHelper::CallObjectMethod<jstring>(env, jfragment, get_text));

  TC3_ASSIGN_OR_RETURN(fragment.text, JStringToUtf8String(env, text.get()));

  // .hasDatetimeOptions()
  TC3_ASSIGN_OR_RETURN(jmethodID has_date_time_options_method,
                       JniHelper::GetMethodID(env, fragment_class.get(),
                                              "hasDatetimeOptions", "()Z"));

  TC3_ASSIGN_OR_RETURN(bool has_date_time_options,
                       JniHelper::CallBooleanMethod(
                           env, jfragment, has_date_time_options_method));

  if (has_date_time_options) {
    // .getReferenceTimeMsUtc()
    TC3_ASSIGN_OR_RETURN(
        jmethodID get_reference_time_method,
        JniHelper::GetMethodID(env, fragment_class.get(),
                               "getReferenceTimeMsUtc", "()J"));

    TC3_ASSIGN_OR_RETURN(
        int64 reference_time,
        JniHelper::CallLongMethod(env, jfragment, get_reference_time_method));

    // .getReferenceTimezone()
    TC3_ASSIGN_OR_RETURN(
        jmethodID get_reference_timezone_method,
        JniHelper::GetMethodID(env, fragment_class.get(),
                               "getReferenceTimezone", "()Ljava/lang/String;"));

    TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> jreference_timezone,
                         JniHelper::CallObjectMethod<jstring>(
                             env, jfragment, get_reference_timezone_method));

    TC3_ASSIGN_OR_RETURN(std::string reference_timezone,
                         JStringToUtf8String(env, jreference_timezone.get()));

    fragment.datetime_options =
        DatetimeOptions{.reference_time_ms_utc = reference_time,
                        .reference_timezone = reference_timezone};
  }

  // .getBoundingBoxHeight()
  TC3_ASSIGN_OR_RETURN(jmethodID get_bounding_box_height,
                       JniHelper::GetMethodID(env, fragment_class.get(),
                                              "getBoundingBoxHeight", "()F"));
  TC3_ASSIGN_OR_RETURN(
      float bounding_box_height,
      JniHelper::CallFloatMethod(env, jfragment, get_bounding_box_height));

  fragment.bounding_box_height = bounding_box_height;

  // .getBoundingBoxTop()
  TC3_ASSIGN_OR_RETURN(jmethodID get_bounding_box_top,
                       JniHelper::GetMethodID(env, fragment_class.get(),
                                              "getBoundingBoxTop", "()F"));
  TC3_ASSIGN_OR_RETURN(
      float bounding_box_top,
      JniHelper::CallFloatMethod(env, jfragment, get_bounding_box_top));
  fragment.bounding_box_top = bounding_box_top;
  return fragment;
}
}  // namespace libtextclassifier3
