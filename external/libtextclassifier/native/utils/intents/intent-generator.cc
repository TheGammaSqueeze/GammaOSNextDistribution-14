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

#include "utils/intents/intent-generator.h"

#include <memory>
#include <string>
#include <vector>

#include "utils/base/logging.h"
#include "utils/intents/jni-lua.h"
#include "utils/java/jni-helper.h"
#include "utils/utf8/unicodetext.h"
#include "utils/zlib/zlib.h"

#ifdef __cplusplus
extern "C" {
#endif
#include "lauxlib.h"
#include "lua.h"
#ifdef __cplusplus
}
#endif

namespace libtextclassifier3 {
namespace {

static constexpr const char* kReferenceTimeUsecKey = "reference_time_ms_utc";
static constexpr const char* kEnableAddContactIntent =
    "enable_add_contact_intent";
static constexpr const char* kEnableSearchIntent = "enable_search_intent";

// Lua environment for classfication result intent generation.
class AnnotatorJniEnvironment : public JniLuaEnvironment {
 public:
  AnnotatorJniEnvironment(const Resources& resources, const JniCache* jni_cache,
                          const jobject context,
                          const std::vector<Locale>& device_locales,
                          const std::string& entity_text,
                          const ClassificationResult& classification,
                          const int64 reference_time_ms_utc,
                          const reflection::Schema* entity_data_schema,
                          const bool enable_add_contact_intent,
                          const bool enable_search_intent)
      : JniLuaEnvironment(resources, jni_cache, context, device_locales),
        entity_text_(entity_text),
        classification_(classification),
        reference_time_ms_utc_(reference_time_ms_utc),
        enable_add_contact_intent_(enable_add_contact_intent),
        enable_search_intent_(enable_search_intent),
        entity_data_schema_(entity_data_schema) {}

 protected:
  void SetupExternalHook() override {
    JniLuaEnvironment::SetupExternalHook();
    lua_pushinteger(state_, reference_time_ms_utc_);
    lua_setfield(state_, /*idx=*/-2, kReferenceTimeUsecKey);

    PushAnnotation(classification_, entity_text_, entity_data_schema_);
    lua_setfield(state_, /*idx=*/-2, "entity");

    lua_pushboolean(state_, enable_add_contact_intent_);
    lua_setfield(state_, /*idx=*/-2, kEnableAddContactIntent);

    lua_pushboolean(state_, enable_search_intent_);
    lua_setfield(state_, /*idx=*/-2, kEnableSearchIntent);
  }

  const std::string& entity_text_;
  const ClassificationResult& classification_;
  const int64 reference_time_ms_utc_;
  const bool enable_add_contact_intent_;
  const bool enable_search_intent_;

  // Reflection schema data.
  const reflection::Schema* const entity_data_schema_;
};

// Lua environment for actions intent generation.
class ActionsJniLuaEnvironment : public JniLuaEnvironment {
 public:
  ActionsJniLuaEnvironment(
      const Resources& resources, const JniCache* jni_cache,
      const jobject context, const std::vector<Locale>& device_locales,
      const Conversation& conversation, const ActionSuggestion& action,
      const reflection::Schema* actions_entity_data_schema,
      const reflection::Schema* annotations_entity_data_schema)
      : JniLuaEnvironment(resources, jni_cache, context, device_locales),
        conversation_(conversation),
        action_(action),
        actions_entity_data_schema_(actions_entity_data_schema),
        annotations_entity_data_schema_(annotations_entity_data_schema) {}

 protected:
  void SetupExternalHook() override {
    JniLuaEnvironment::SetupExternalHook();
    PushConversation(&conversation_.messages, annotations_entity_data_schema_);
    lua_setfield(state_, /*idx=*/-2, "conversation");

    PushAction(action_, actions_entity_data_schema_,
               annotations_entity_data_schema_);
    lua_setfield(state_, /*idx=*/-2, "entity");
  }

  const Conversation& conversation_;
  const ActionSuggestion& action_;
  const reflection::Schema* actions_entity_data_schema_;
  const reflection::Schema* annotations_entity_data_schema_;
};

}  // namespace

std::unique_ptr<IntentGenerator> IntentGenerator::Create(
    const IntentFactoryModel* options, const ResourcePool* resources,
    const std::shared_ptr<JniCache>& jni_cache) {
  std::unique_ptr<IntentGenerator> intent_generator(
      new IntentGenerator(options, resources, jni_cache));

  if (options == nullptr || options->generator() == nullptr) {
    TC3_LOG(ERROR) << "No intent generator options.";
    return nullptr;
  }

  std::unique_ptr<ZlibDecompressor> zlib_decompressor =
      ZlibDecompressor::Instance();
  if (!zlib_decompressor) {
    TC3_LOG(ERROR) << "Cannot initialize decompressor.";
    return nullptr;
  }

  for (const IntentFactoryModel_::IntentGenerator* generator :
       *options->generator()) {
    std::string lua_template_generator;
    if (!zlib_decompressor->MaybeDecompressOptionallyCompressedBuffer(
            generator->lua_template_generator(),
            generator->compressed_lua_template_generator(),
            &lua_template_generator)) {
      TC3_LOG(ERROR) << "Could not decompress generator template.";
      return nullptr;
    }

    std::string lua_code = lua_template_generator;
    if (options->precompile_generators()) {
      if (!Compile(lua_template_generator, &lua_code)) {
        TC3_LOG(ERROR) << "Could not precompile generator template.";
        return nullptr;
      }
    }

    intent_generator->generators_[generator->type()->str()] = lua_code;
  }

  return intent_generator;
}

std::vector<Locale> IntentGenerator::ParseDeviceLocales(
    const jstring device_locales) const {
  if (device_locales == nullptr) {
    TC3_LOG(ERROR) << "No locales provided.";
    return {};
  }
  StatusOr<std::string> status_or_locales_str =
      JStringToUtf8String(jni_cache_->GetEnv(), device_locales);
  if (!status_or_locales_str.ok()) {
    TC3_LOG(ERROR)
        << "JStringToUtf8String failed, cannot retrieve provided locales.";
    return {};
  }
  std::vector<Locale> locales;
  if (!ParseLocales(status_or_locales_str.ValueOrDie(), &locales)) {
    TC3_LOG(ERROR) << "Cannot parse locales.";
    return {};
  }
  return locales;
}

bool IntentGenerator::GenerateIntents(
    const jstring device_locales, const ClassificationResult& classification,
    const int64 reference_time_ms_utc, const std::string& text,
    const CodepointSpan selection_indices, const jobject context,
    const reflection::Schema* annotations_entity_data_schema,
    const bool enable_add_contact_intent, const bool enable_search_intent,
    std::vector<RemoteActionTemplate>* remote_actions) const {
  if (options_ == nullptr) {
    return false;
  }

  // Retrieve generator for specified entity.
  auto it = generators_.find(classification.collection);
  if (it == generators_.end()) {
    TC3_VLOG(INFO) << "Cannot find a generator for the specified collection.";
    return true;
  }

  const std::string entity_text =
      UTF8ToUnicodeText(text, /*do_copy=*/false)
          .UTF8Substring(selection_indices.first, selection_indices.second);

  std::unique_ptr<AnnotatorJniEnvironment> interpreter(
      new AnnotatorJniEnvironment(
          resources_, jni_cache_.get(), context,
          ParseDeviceLocales(device_locales), entity_text, classification,
          reference_time_ms_utc, annotations_entity_data_schema,
          enable_add_contact_intent, enable_search_intent));

  if (!interpreter->Initialize()) {
    TC3_LOG(ERROR) << "Could not create Lua interpreter.";
    return false;
  }

  return interpreter->RunIntentGenerator(it->second, remote_actions);
}

bool IntentGenerator::GenerateIntents(
    const jstring device_locales, const ActionSuggestion& action,
    const Conversation& conversation, const jobject context,
    const reflection::Schema* annotations_entity_data_schema,
    const reflection::Schema* actions_entity_data_schema,
    std::vector<RemoteActionTemplate>* remote_actions) const {
  if (options_ == nullptr) {
    return false;
  }

  // Retrieve generator for specified action.
  auto it = generators_.find(action.type);
  if (it == generators_.end()) {
    return true;
  }

  std::unique_ptr<ActionsJniLuaEnvironment> interpreter(
      new ActionsJniLuaEnvironment(
          resources_, jni_cache_.get(), context,
          ParseDeviceLocales(device_locales), conversation, action,
          actions_entity_data_schema, annotations_entity_data_schema));

  if (!interpreter->Initialize()) {
    TC3_LOG(ERROR) << "Could not create Lua interpreter.";
    return false;
  }

  return interpreter->RunIntentGenerator(it->second, remote_actions);
}

}  // namespace libtextclassifier3
