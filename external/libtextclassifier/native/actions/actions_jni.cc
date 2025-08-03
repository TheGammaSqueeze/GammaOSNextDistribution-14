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

// JNI wrapper for actions.

#include "actions/actions_jni.h"

#include <jni.h>

#include <map>
#include <type_traits>
#include <vector>

#include "actions/actions-suggestions.h"
#include "annotator/annotator.h"
#include "annotator/annotator_jni_common.h"
#include "utils/base/integral_types.h"
#include "utils/base/status_macros.h"
#include "utils/base/statusor.h"
#include "utils/intents/intent-generator.h"
#include "utils/intents/jni.h"
#include "utils/intents/remote-action-template.h"
#include "utils/java/jni-base.h"
#include "utils/java/jni-cache.h"
#include "utils/java/jni-helper.h"
#include "utils/memory/mmap.h"

using libtextclassifier3::ActionsSuggestions;
using libtextclassifier3::ActionsSuggestionsResponse;
using libtextclassifier3::ActionSuggestionOptions;
using libtextclassifier3::Annotator;
using libtextclassifier3::Conversation;
using libtextclassifier3::IntentGenerator;
using libtextclassifier3::JStringToUtf8String;
using libtextclassifier3::ScopedLocalRef;
using libtextclassifier3::StatusOr;

// When using the Java's ICU, UniLib needs to be instantiated with a JavaVM
// pointer from JNI. When using a standard ICU the pointer is not needed and the
// objects are instantiated implicitly.
#ifdef TC3_UNILIB_JAVAICU
using libtextclassifier3::UniLib;
#endif

namespace libtextclassifier3 {

namespace {

// Cached state for model inference.
// Keeps a jni cache, intent generator and model instance so that they don't
// have to be recreated for each call.
class ActionsSuggestionsJniContext {
 public:
  static ActionsSuggestionsJniContext* Create(
      const std::shared_ptr<libtextclassifier3::JniCache>& jni_cache,
      std::unique_ptr<ActionsSuggestions> model) {
    if (jni_cache == nullptr || model == nullptr) {
      return nullptr;
    }
    std::unique_ptr<IntentGenerator> intent_generator =
        IntentGenerator::Create(model->model()->android_intent_options(),
                                model->model()->resources(), jni_cache);
    if (intent_generator == nullptr) {
      return nullptr;
    }

    TC3_ASSIGN_OR_RETURN_NULL(
        std::unique_ptr<RemoteActionTemplatesHandler> template_handler,
        libtextclassifier3::RemoteActionTemplatesHandler::Create(jni_cache));

    return new ActionsSuggestionsJniContext(jni_cache, std::move(model),
                                            std::move(intent_generator),
                                            std::move(template_handler));
  }

  std::shared_ptr<libtextclassifier3::JniCache> jni_cache() const {
    return jni_cache_;
  }

  ActionsSuggestions* model() const { return model_.get(); }

  IntentGenerator* intent_generator() const { return intent_generator_.get(); }

  RemoteActionTemplatesHandler* template_handler() const {
    return template_handler_.get();
  }

 private:
  ActionsSuggestionsJniContext(
      const std::shared_ptr<libtextclassifier3::JniCache>& jni_cache,
      std::unique_ptr<ActionsSuggestions> model,
      std::unique_ptr<IntentGenerator> intent_generator,
      std::unique_ptr<RemoteActionTemplatesHandler> template_handler)
      : jni_cache_(jni_cache),
        model_(std::move(model)),
        intent_generator_(std::move(intent_generator)),
        template_handler_(std::move(template_handler)) {}

  std::shared_ptr<libtextclassifier3::JniCache> jni_cache_;
  std::unique_ptr<ActionsSuggestions> model_;
  std::unique_ptr<IntentGenerator> intent_generator_;
  std::unique_ptr<RemoteActionTemplatesHandler> template_handler_;
};

ActionSuggestionOptions FromJavaActionSuggestionOptions(JNIEnv* env,
                                                        jobject joptions) {
  ActionSuggestionOptions options = ActionSuggestionOptions::Default();
  return options;
}

StatusOr<ScopedLocalRef<jobject>> ActionSuggestionsToJObject(
    JNIEnv* env, const ActionsSuggestionsJniContext* context,
    jobject app_context,
    const reflection::Schema* annotations_entity_data_schema,
    const ActionsSuggestionsResponse& action_response,
    const Conversation& conversation, const jstring device_locales,
    const bool generate_intents) {
  // Find the class ActionSuggestion.
  auto status_or_action_class = JniHelper::FindClass(
      env, TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR "$ActionSuggestion");
  if (!status_or_action_class.ok()) {
    TC3_LOG(ERROR) << "Couldn't find ActionSuggestion class.";
    return status_or_action_class.status();
  }
  ScopedLocalRef<jclass> action_class =
      std::move(status_or_action_class.ValueOrDie());

  // Find the class ActionSuggestions
  auto status_or_result_class = JniHelper::FindClass(
      env, TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR "$ActionSuggestions");
  if (!status_or_result_class.ok()) {
    TC3_LOG(ERROR) << "Couldn't find ActionSuggestions class.";
    return status_or_result_class.status();
  }
  ScopedLocalRef<jclass> result_class =
      std::move(status_or_result_class.ValueOrDie());

  // Find the class Slot.
  auto status_or_slot_class = JniHelper::FindClass(
      env, TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR "$Slot");
  if (!status_or_slot_class.ok()) {
    TC3_LOG(ERROR) << "Couldn't find Slot class.";
    return status_or_slot_class.status();
  }
  ScopedLocalRef<jclass> slot_class =
      std::move(status_or_slot_class.ValueOrDie());

  TC3_ASSIGN_OR_RETURN(
      const jmethodID action_class_constructor,
      JniHelper::GetMethodID(
          env, action_class.get(), "<init>",
          "(Ljava/lang/String;Ljava/lang/String;F[L" TC3_PACKAGE_PATH
              TC3_NAMED_VARIANT_CLASS_NAME_STR
          ";[B[L" TC3_PACKAGE_PATH TC3_REMOTE_ACTION_TEMPLATE_CLASS_NAME_STR
          ";[L" TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR "$Slot;)V"));
  TC3_ASSIGN_OR_RETURN(const jmethodID slot_class_constructor,
                       JniHelper::GetMethodID(env, slot_class.get(), "<init>",
                                              "(Ljava/lang/String;IIIF)V"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jobjectArray> actions,
      JniHelper::NewObjectArray(env, action_response.actions.size(),
                                action_class.get(), nullptr));
  for (int i = 0; i < action_response.actions.size(); i++) {
    ScopedLocalRef<jobjectArray> extras;
    const reflection::Schema* actions_entity_data_schema =
        context->model()->entity_data_schema();
    if (actions_entity_data_schema != nullptr &&
        !action_response.actions[i].serialized_entity_data.empty()) {
      TC3_ASSIGN_OR_RETURN(
          extras, context->template_handler()->EntityDataAsNamedVariantArray(
                      actions_entity_data_schema,
                      action_response.actions[i].serialized_entity_data));
    }

    ScopedLocalRef<jbyteArray> serialized_entity_data;
    if (!action_response.actions[i].serialized_entity_data.empty()) {
      TC3_ASSIGN_OR_RETURN(
          serialized_entity_data,
          JniHelper::NewByteArray(
              env, action_response.actions[i].serialized_entity_data.size()));
      TC3_RETURN_IF_ERROR(JniHelper::SetByteArrayRegion(
          env, serialized_entity_data.get(), 0,
          action_response.actions[i].serialized_entity_data.size(),
          reinterpret_cast<const jbyte*>(
              action_response.actions[i].serialized_entity_data.data())));
    }

    ScopedLocalRef<jobjectArray> remote_action_templates_result;
    if (generate_intents) {
      std::vector<RemoteActionTemplate> remote_action_templates;
      if (context->intent_generator()->GenerateIntents(
              device_locales, action_response.actions[i], conversation,
              app_context,
              /*annotations_entity_data_schema=*/annotations_entity_data_schema,
              /*actions_entity_data_schema=*/actions_entity_data_schema,
              &remote_action_templates)) {
        TC3_ASSIGN_OR_RETURN(
            remote_action_templates_result,
            context->template_handler()->RemoteActionTemplatesToJObjectArray(
                remote_action_templates));
      }
    }

    TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> reply,
                         context->jni_cache()->ConvertToJavaString(
                             action_response.actions[i].response_text));

    TC3_ASSIGN_OR_RETURN(
        ScopedLocalRef<jstring> action_type,
        JniHelper::NewStringUTF(env, action_response.actions[i].type.c_str()));

    ScopedLocalRef<jobjectArray> slots;
    if (!action_response.actions[i].slots.empty()) {
      TC3_ASSIGN_OR_RETURN(slots,
                           JniHelper::NewObjectArray(
                               env, action_response.actions[i].slots.size(),
                               slot_class.get(), nullptr));
      for (int j = 0; j < action_response.actions[i].slots.size(); j++) {
        const Slot& slot_c = action_response.actions[i].slots[j];
        TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> slot_type,
                             JniHelper::NewStringUTF(env, slot_c.type.c_str()));

        TC3_ASSIGN_OR_RETURN(
            ScopedLocalRef<jobject> slot,
            JniHelper::NewObject(
                env, slot_class.get(), slot_class_constructor, slot_type.get(),
                slot_c.span.message_index, slot_c.span.span.first,
                slot_c.span.span.second, slot_c.confidence_score));

        TC3_RETURN_IF_ERROR(
            JniHelper::SetObjectArrayElement(env, slots.get(), j, slot.get()));
      }
    }

    TC3_ASSIGN_OR_RETURN(
        ScopedLocalRef<jobject> action,
        JniHelper::NewObject(
            env, action_class.get(), action_class_constructor, reply.get(),
            action_type.get(),
            static_cast<jfloat>(action_response.actions[i].score), extras.get(),
            serialized_entity_data.get(), remote_action_templates_result.get(),
            slots.get()));
    TC3_RETURN_IF_ERROR(
        JniHelper::SetObjectArrayElement(env, actions.get(), i, action.get()));
  }

  // Create the ActionSuggestions object.
  TC3_ASSIGN_OR_RETURN(
      const jmethodID result_class_constructor,
      JniHelper::GetMethodID(env, result_class.get(), "<init>",
                             "([L" TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR
                             "$ActionSuggestion;Z)V"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jobject> result,
      JniHelper::NewObject(env, result_class.get(), result_class_constructor,
                           actions.get(), action_response.is_sensitive));
  return result;
}

StatusOr<ConversationMessage> FromJavaConversationMessage(JNIEnv* env,
                                                          jobject jmessage) {
  if (!jmessage) {
    return {};
  }

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> message_class,
      JniHelper::FindClass(env, TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR
                           "$ConversationMessage"));
  // .getText()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_text_method,
      JniHelper::GetMethodID(env, message_class.get(), "getText",
                             "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> text,
      JniHelper::CallObjectMethod<jstring>(env, jmessage, get_text_method));

  // .getUserId()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_user_id_method,
      JniHelper::GetMethodID(env, message_class.get(), "getUserId", "()I"));
  TC3_ASSIGN_OR_RETURN(int32 user_id, JniHelper::CallIntMethod(
                                          env, jmessage, get_user_id_method));

  // .getReferenceTimeMsUtc()
  TC3_ASSIGN_OR_RETURN(jmethodID get_reference_time_method,
                       JniHelper::GetMethodID(env, message_class.get(),
                                              "getReferenceTimeMsUtc", "()J"));
  TC3_ASSIGN_OR_RETURN(
      int64 reference_time,
      JniHelper::CallLongMethod(env, jmessage, get_reference_time_method));

  // .getReferenceTimezone()
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_reference_timezone_method,
      JniHelper::GetMethodID(env, message_class.get(), "getReferenceTimezone",
                             "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> reference_timezone,
                       JniHelper::CallObjectMethod<jstring>(
                           env, jmessage, get_reference_timezone_method));

  // .getDetectedTextLanguageTags()
  TC3_ASSIGN_OR_RETURN(jmethodID get_detected_text_language_tags_method,
                       JniHelper::GetMethodID(env, message_class.get(),
                                              "getDetectedTextLanguageTags",
                                              "()Ljava/lang/String;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jstring> detected_text_language_tags,
      JniHelper::CallObjectMethod<jstring>(
          env, jmessage, get_detected_text_language_tags_method));

  ConversationMessage message;
  TC3_ASSIGN_OR_RETURN(message.text, JStringToUtf8String(env, text.get()));
  message.user_id = user_id;
  message.reference_time_ms_utc = reference_time;
  TC3_ASSIGN_OR_RETURN(message.reference_timezone,
                       JStringToUtf8String(env, reference_timezone.get()));
  TC3_ASSIGN_OR_RETURN(
      message.detected_text_language_tags,
      JStringToUtf8String(env, detected_text_language_tags.get()));
  return message;
}

StatusOr<Conversation> FromJavaConversation(JNIEnv* env,
                                            jobject jconversation) {
  if (!jconversation) {
    return {Status::UNKNOWN};
  }

  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jclass> conversation_class,
      JniHelper::FindClass(
          env, TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR "$Conversation"));

  TC3_ASSIGN_OR_RETURN(
      jmethodID get_conversation_messages_method,
      JniHelper::GetMethodID(env, conversation_class.get(),
                             "getConversationMessages",
                             "()[L" TC3_PACKAGE_PATH TC3_ACTIONS_CLASS_NAME_STR
                             "$ConversationMessage;"));
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jobjectArray> jmessages,
      JniHelper::CallObjectMethod<jobjectArray>(
          env, jconversation, get_conversation_messages_method));

  std::vector<ConversationMessage> messages;
  TC3_ASSIGN_OR_RETURN(const int size,
                       JniHelper::GetArrayLength(env, jmessages.get()));
  for (int i = 0; i < size; i++) {
    TC3_ASSIGN_OR_RETURN(
        ScopedLocalRef<jobject> jmessage,
        JniHelper::GetObjectArrayElement<jobject>(env, jmessages.get(), i));
    TC3_ASSIGN_OR_RETURN(ConversationMessage message,
                         FromJavaConversationMessage(env, jmessage.get()));
    messages.push_back(message);
  }
  Conversation conversation;
  conversation.messages = messages;
  return conversation;
}

StatusOr<ScopedLocalRef<jstring>> GetLocalesFromMmap(
    JNIEnv* env, libtextclassifier3::ScopedMmap* mmap) {
  if (!mmap->handle().ok()) {
    return JniHelper::NewStringUTF(env, "");
  }
  const ActionsModel* model = libtextclassifier3::ViewActionsModel(
      mmap->handle().start(), mmap->handle().num_bytes());
  if (!model || !model->locales()) {
    return JniHelper::NewStringUTF(env, "");
  }
  return JniHelper::NewStringUTF(env, model->locales()->c_str());
}

jint GetVersionFromMmap(JNIEnv* env, libtextclassifier3::ScopedMmap* mmap) {
  if (!mmap->handle().ok()) {
    return 0;
  }
  const ActionsModel* model = libtextclassifier3::ViewActionsModel(
      mmap->handle().start(), mmap->handle().num_bytes());
  if (!model) {
    return 0;
  }
  return model->version();
}

StatusOr<ScopedLocalRef<jstring>> GetNameFromMmap(
    JNIEnv* env, libtextclassifier3::ScopedMmap* mmap) {
  if (!mmap->handle().ok()) {
    return JniHelper::NewStringUTF(env, "");
  }
  const ActionsModel* model = libtextclassifier3::ViewActionsModel(
      mmap->handle().start(), mmap->handle().num_bytes());
  if (!model || !model->name()) {
    return JniHelper::NewStringUTF(env, "");
  }
  return JniHelper::NewStringUTF(env, model->name()->c_str());
}
}  // namespace
}  // namespace libtextclassifier3

using libtextclassifier3::ActionsSuggestionsJniContext;
using libtextclassifier3::ActionSuggestionsToJObject;
using libtextclassifier3::FromJavaActionSuggestionOptions;
using libtextclassifier3::FromJavaConversation;
using libtextclassifier3::JByteArrayToString;

TC3_JNI_METHOD(jlong, TC3_ACTIONS_CLASS_NAME, nativeNewActionsModel)
(JNIEnv* env, jobject clazz, jint fd, jbyteArray jserialized_preconditions) {
  std::shared_ptr<libtextclassifier3::JniCache> jni_cache =
      libtextclassifier3::JniCache::Create(env);
  std::string serialized_preconditions;
  if (jserialized_preconditions != nullptr) {
    TC3_ASSIGN_OR_RETURN_0(
        serialized_preconditions,
        JByteArrayToString(env, jserialized_preconditions),
        TC3_LOG(ERROR) << "Could not convert serialized preconditions.");
  }

#ifdef TC3_UNILIB_JAVAICU
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache, ActionsSuggestions::FromFileDescriptor(
                     fd, std::unique_ptr<UniLib>(new UniLib(jni_cache)),
                     serialized_preconditions)));
#else
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache, ActionsSuggestions::FromFileDescriptor(
                     fd, /*unilib=*/nullptr, serialized_preconditions)));
#endif  // TC3_UNILIB_JAVAICU
}

TC3_JNI_METHOD(jlong, TC3_ACTIONS_CLASS_NAME, nativeNewActionsModelFromPath)
(JNIEnv* env, jobject clazz, jstring path,
 jbyteArray jserialized_preconditions) {
  std::shared_ptr<libtextclassifier3::JniCache> jni_cache =
      libtextclassifier3::JniCache::Create(env);
  TC3_ASSIGN_OR_RETURN_0(const std::string path_str,
                         JStringToUtf8String(env, path));
  std::string serialized_preconditions;
  if (jserialized_preconditions != nullptr) {
    TC3_ASSIGN_OR_RETURN_0(
        serialized_preconditions,
        JByteArrayToString(env, jserialized_preconditions),
        TC3_LOG(ERROR) << "Could not convert serialized preconditions.");
  }
#ifdef TC3_UNILIB_JAVAICU
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache, ActionsSuggestions::FromPath(
                     path_str, std::unique_ptr<UniLib>(new UniLib(jni_cache)),
                     serialized_preconditions)));
#else
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache, ActionsSuggestions::FromPath(path_str, /*unilib=*/nullptr,
                                              serialized_preconditions)));
#endif  // TC3_UNILIB_JAVAICU
}

TC3_JNI_METHOD(jlong, TC3_ACTIONS_CLASS_NAME, nativeNewActionsModelWithOffset)
(JNIEnv* env, jobject clazz, jint fd, jlong offset, jlong size,
 jbyteArray jserialized_preconditions) {
  std::shared_ptr<libtextclassifier3::JniCache> jni_cache =
      libtextclassifier3::JniCache::Create(env);
  std::string serialized_preconditions;
  if (jserialized_preconditions != nullptr) {
    TC3_ASSIGN_OR_RETURN_0(
        serialized_preconditions,
        JByteArrayToString(env, jserialized_preconditions),
        TC3_LOG(ERROR) << "Could not convert serialized preconditions.");
  }
#ifdef TC3_UNILIB_JAVAICU
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache,
      ActionsSuggestions::FromFileDescriptor(
          fd, offset, size, std::unique_ptr<UniLib>(new UniLib(jni_cache)),
          serialized_preconditions)));
#else
  return reinterpret_cast<jlong>(ActionsSuggestionsJniContext::Create(
      jni_cache,
      ActionsSuggestions::FromFileDescriptor(
          fd, offset, size, /*unilib=*/nullptr, serialized_preconditions)));
#endif  // TC3_UNILIB_JAVAICU
}

TC3_JNI_METHOD(jobject, TC3_ACTIONS_CLASS_NAME, nativeSuggestActions)
(JNIEnv* env, jobject thiz, jlong ptr, jobject jconversation, jobject joptions,
 jlong annotatorPtr, jobject app_context, jstring device_locales,
 jboolean generate_intents) {
  if (!ptr) {
    return nullptr;
  }
  TC3_ASSIGN_OR_RETURN_NULL(const Conversation conversation,
                            FromJavaConversation(env, jconversation));
  const ActionSuggestionOptions options =
      FromJavaActionSuggestionOptions(env, joptions);
  const ActionsSuggestionsJniContext* context =
      reinterpret_cast<ActionsSuggestionsJniContext*>(ptr);
  const Annotator* annotator = reinterpret_cast<Annotator*>(annotatorPtr);

  const ActionsSuggestionsResponse response =
      context->model()->SuggestActions(conversation, annotator, options);

  const reflection::Schema* anntotations_entity_data_schema =
      annotator ? annotator->entity_data_schema() : nullptr;

  TC3_ASSIGN_OR_RETURN_NULL(
      ScopedLocalRef<jobject> result,
      ActionSuggestionsToJObject(
          env, context, app_context, anntotations_entity_data_schema, response,
          conversation, device_locales, generate_intents));
  return result.release();
}

TC3_JNI_METHOD(void, TC3_ACTIONS_CLASS_NAME, nativeCloseActionsModel)
(JNIEnv* env, jobject thiz, jlong model_ptr) {
  const ActionsSuggestionsJniContext* context =
      reinterpret_cast<ActionsSuggestionsJniContext*>(model_ptr);
  delete context;
}

TC3_JNI_METHOD(jstring, TC3_ACTIONS_CLASS_NAME, nativeGetLocales)
(JNIEnv* env, jobject clazz, jint fd) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd));
  TC3_ASSIGN_OR_RETURN_NULL(
      ScopedLocalRef<jstring> result,
      libtextclassifier3::GetLocalesFromMmap(env, mmap.get()));
  return result.release();
}

TC3_JNI_METHOD(jstring, TC3_ACTIONS_CLASS_NAME, nativeGetLocalesWithOffset)
(JNIEnv* env, jobject clazz, jint fd, jlong offset, jlong size) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd, offset, size));
  TC3_ASSIGN_OR_RETURN_NULL(
      ScopedLocalRef<jstring> result,
      libtextclassifier3::GetLocalesFromMmap(env, mmap.get()));
  return result.release();
}

TC3_JNI_METHOD(jstring, TC3_ACTIONS_CLASS_NAME, nativeGetName)
(JNIEnv* env, jobject clazz, jint fd) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd));
  TC3_ASSIGN_OR_RETURN_NULL(
      ScopedLocalRef<jstring> result,
      libtextclassifier3::GetNameFromMmap(env, mmap.get()));
  return result.release();
}

TC3_JNI_METHOD(jstring, TC3_ACTIONS_CLASS_NAME, nativeGetNameWithOffset)
(JNIEnv* env, jobject clazz, jint fd, jlong offset, jlong size) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd, offset, size));
  TC3_ASSIGN_OR_RETURN_NULL(
      ScopedLocalRef<jstring> result,
      libtextclassifier3::GetNameFromMmap(env, mmap.get()));
  return result.release();
}

TC3_JNI_METHOD(jint, TC3_ACTIONS_CLASS_NAME, nativeGetVersion)
(JNIEnv* env, jobject clazz, jint fd) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd));
  return libtextclassifier3::GetVersionFromMmap(env, mmap.get());
}

TC3_JNI_METHOD(jint, TC3_ACTIONS_CLASS_NAME, nativeGetVersionWithOffset)
(JNIEnv* env, jobject clazz, jint fd, jlong offset, jlong size) {
  const std::unique_ptr<libtextclassifier3::ScopedMmap> mmap(
      new libtextclassifier3::ScopedMmap(fd, offset, size));
  return libtextclassifier3::GetVersionFromMmap(env, mmap.get());
}

TC3_JNI_METHOD(jlong, TC3_ACTIONS_CLASS_NAME, nativeGetNativeModelPtr)
(JNIEnv* env, jobject thiz, jlong ptr) {
  if (!ptr) {
    return 0L;
  }
  return reinterpret_cast<jlong>(
      reinterpret_cast<ActionsSuggestionsJniContext*>(ptr)->model());
}

TC3_JNI_METHOD(jboolean, TC3_ACTIONS_CLASS_NAME,
               nativeInitializeConversationIntentDetection)
(JNIEnv* env, jobject thiz, jlong ptr, jbyteArray jserialized_config) {
  if (!ptr) {
    return false;
  }

  ActionsSuggestions* model =
      reinterpret_cast<ActionsSuggestionsJniContext*>(ptr)->model();

  std::string serialized_config;
  TC3_ASSIGN_OR_RETURN_0(
      serialized_config, JByteArrayToString(env, jserialized_config),
      TC3_LOG(ERROR) << "Could not convert serialized conversation intent "
                        "detection config.");
  return model->InitializeConversationIntentDetection(serialized_config);
}
