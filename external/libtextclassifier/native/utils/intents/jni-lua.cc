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

#include "utils/intents/jni-lua.h"

#include "utils/hash/farmhash.h"
#include "utils/java/jni-helper.h"
#include "utils/strings/substitute.h"

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

static constexpr const char* kHashKey = "hash";
static constexpr const char* kUrlSchemaKey = "url_schema";
static constexpr const char* kUrlHostKey = "url_host";
static constexpr const char* kUrlEncodeKey = "urlencode";
static constexpr const char* kPackageNameKey = "package_name";
static constexpr const char* kDeviceLocaleKey = "device_locales";
static constexpr const char* kFormatKey = "format";

}  // namespace

JniLuaEnvironment::JniLuaEnvironment(const Resources& resources,
                                     const JniCache* jni_cache,
                                     const jobject context,
                                     const std::vector<Locale>& device_locales)
    : LuaEnvironment(),
      resources_(resources),
      jenv_(jni_cache ? jni_cache->GetEnv() : nullptr),
      jni_cache_(jni_cache),
      context_(context),
      device_locales_(device_locales),
      usermanager_(/*object=*/nullptr,
                   /*jvm=*/(jni_cache ? jni_cache->jvm : nullptr)),
      usermanager_retrieved_(false),
      system_resources_(/*object=*/nullptr,
                        /*jvm=*/(jni_cache ? jni_cache->jvm : nullptr)),
      system_resources_resources_retrieved_(false),
      string_(/*object=*/nullptr,
              /*jvm=*/(jni_cache ? jni_cache->jvm : nullptr)),
      android_(/*object=*/nullptr,
               /*jvm=*/(jni_cache ? jni_cache->jvm : nullptr)) {}

bool JniLuaEnvironment::PreallocateConstantJniStrings() {
  TC3_ASSIGN_OR_RETURN_FALSE(ScopedLocalRef<jstring> string_value,
                             JniHelper::NewStringUTF(jenv_, "string"));
  string_ = MakeGlobalRef(string_value.get(), jenv_, jni_cache_->jvm);
  TC3_ASSIGN_OR_RETURN_FALSE(ScopedLocalRef<jstring> android_value,
                             JniHelper::NewStringUTF(jenv_, "android"));
  android_ = MakeGlobalRef(android_value.get(), jenv_, jni_cache_->jvm);
  if (string_ == nullptr || android_ == nullptr) {
    TC3_LOG(ERROR) << "Could not allocate constant strings references.";
    return false;
  }
  return true;
}

bool JniLuaEnvironment::Initialize() {
  if (!PreallocateConstantJniStrings()) {
    return false;
  }
  return (RunProtected([this] {
            LoadDefaultLibraries();
            SetupExternalHook();
            lua_setglobal(state_, "external");
            return LUA_OK;
          }) == LUA_OK);
}

void JniLuaEnvironment::SetupExternalHook() {
  // This exposes an `external` object with the following fields:
  //   * entity: the bundle with all information about a classification.
  //   * android: callbacks into specific android provided methods.
  //   * android.user_restrictions: callbacks to check user permissions.
  //   * android.R: callbacks to retrieve string resources.
  PushLazyObject(&JniLuaEnvironment::HandleExternalCallback);

  // android
  PushLazyObject(&JniLuaEnvironment::HandleAndroidCallback);
  {
    // android.user_restrictions
    PushLazyObject(&JniLuaEnvironment::HandleUserRestrictionsCallback);
    lua_setfield(state_, /*idx=*/-2, "user_restrictions");

    // android.R
    // Callback to access android string resources.
    PushLazyObject(&JniLuaEnvironment::HandleAndroidStringResources);
    lua_setfield(state_, /*idx=*/-2, "R");
  }
  lua_setfield(state_, /*idx=*/-2, "android");
}

int JniLuaEnvironment::HandleExternalCallback() {
  const StringPiece key = ReadString(kIndexStackTop);
  if (key.Equals(kHashKey)) {
    PushFunction(&JniLuaEnvironment::HandleHash);
    return 1;
  } else if (key.Equals(kFormatKey)) {
    PushFunction(&JniLuaEnvironment::HandleFormat);
    return 1;
  } else {
    TC3_LOG(ERROR) << "Undefined external access " << key;
    lua_error(state_);
    return 0;
  }
}

int JniLuaEnvironment::HandleAndroidCallback() {
  const StringPiece key = ReadString(kIndexStackTop);
  if (key.Equals(kDeviceLocaleKey)) {
    // Provide the locale as table with the individual fields set.
    lua_newtable(state_);
    for (int i = 0; i < device_locales_.size(); i++) {
      // Adjust index to 1-based indexing for Lua.
      lua_pushinteger(state_, i + 1);
      lua_newtable(state_);
      PushString(device_locales_[i].Language());
      lua_setfield(state_, -2, "language");
      PushString(device_locales_[i].Region());
      lua_setfield(state_, -2, "region");
      PushString(device_locales_[i].Script());
      lua_setfield(state_, -2, "script");
      lua_settable(state_, /*idx=*/-3);
    }
    return 1;
  } else if (key.Equals(kPackageNameKey)) {
    if (context_ == nullptr) {
      TC3_LOG(ERROR) << "Context invalid.";
      lua_error(state_);
      return 0;
    }

    StatusOr<ScopedLocalRef<jstring>> status_or_package_name_str =
        JniHelper::CallObjectMethod<jstring>(
            jenv_, context_, jni_cache_->context_get_package_name);

    if (!status_or_package_name_str.ok()) {
      TC3_LOG(ERROR) << "Error calling Context.getPackageName";
      lua_error(state_);
      return 0;
    }
    StatusOr<std::string> status_or_package_name_std_str = JStringToUtf8String(
        jenv_, status_or_package_name_str.ValueOrDie().get());
    if (!status_or_package_name_std_str.ok()) {
      lua_error(state_);
      return 0;
    }
    PushString(status_or_package_name_std_str.ValueOrDie());
    return 1;
  } else if (key.Equals(kUrlEncodeKey)) {
    PushFunction(&JniLuaEnvironment::HandleUrlEncode);
    return 1;
  } else if (key.Equals(kUrlHostKey)) {
    PushFunction(&JniLuaEnvironment::HandleUrlHost);
    return 1;
  } else if (key.Equals(kUrlSchemaKey)) {
    PushFunction(&JniLuaEnvironment::HandleUrlSchema);
    return 1;
  } else {
    TC3_LOG(ERROR) << "Undefined android reference " << key;
    lua_error(state_);
    return 0;
  }
}

int JniLuaEnvironment::HandleUserRestrictionsCallback() {
  if (jni_cache_->usermanager_class == nullptr ||
      jni_cache_->usermanager_get_user_restrictions == nullptr) {
    // UserManager is only available for API level >= 17 and
    // getUserRestrictions only for API level >= 18, so we just return false
    // normally here.
    lua_pushboolean(state_, false);
    return 1;
  }

  // Get user manager if not previously retrieved.
  if (!RetrieveUserManager()) {
    TC3_LOG(ERROR) << "Error retrieving user manager.";
    lua_error(state_);
    return 0;
  }

  StatusOr<ScopedLocalRef<jobject>> status_or_bundle =
      JniHelper::CallObjectMethod(
          jenv_, usermanager_.get(),
          jni_cache_->usermanager_get_user_restrictions);
  if (!status_or_bundle.ok() || status_or_bundle.ValueOrDie() == nullptr) {
    TC3_LOG(ERROR) << "Error calling getUserRestrictions";
    lua_error(state_);
    return 0;
  }

  const StringPiece key_str = ReadString(kIndexStackTop);
  if (key_str.empty()) {
    TC3_LOG(ERROR) << "Expected string, got null.";
    lua_error(state_);
    return 0;
  }

  const StatusOr<ScopedLocalRef<jstring>> status_or_key =
      jni_cache_->ConvertToJavaString(key_str);
  if (!status_or_key.ok()) {
    lua_error(state_);
    return 0;
  }
  const StatusOr<bool> status_or_permission = JniHelper::CallBooleanMethod(
      jenv_, status_or_bundle.ValueOrDie().get(),
      jni_cache_->bundle_get_boolean, status_or_key.ValueOrDie().get());
  if (!status_or_permission.ok()) {
    TC3_LOG(ERROR) << "Error getting bundle value";
    lua_pushboolean(state_, false);
  } else {
    lua_pushboolean(state_, status_or_permission.ValueOrDie());
  }
  return 1;
}

int JniLuaEnvironment::HandleUrlEncode() {
  const StringPiece input = ReadString(/*index=*/1);
  if (input.empty()) {
    TC3_LOG(ERROR) << "Expected string, got null.";
    lua_error(state_);
    return 0;
  }

  // Call Java Uri encode.
  const StatusOr<ScopedLocalRef<jstring>> status_or_input_str =
      jni_cache_->ConvertToJavaString(input);
  if (!status_or_input_str.ok()) {
    lua_error(state_);
    return 0;
  }
  StatusOr<ScopedLocalRef<jstring>> status_or_encoded_str =
      JniHelper::CallStaticObjectMethod<jstring>(
          jenv_, jni_cache_->uri_class.get(), jni_cache_->uri_encode,
          status_or_input_str.ValueOrDie().get());

  if (!status_or_encoded_str.ok()) {
    TC3_LOG(ERROR) << "Error calling Uri.encode";
    lua_error(state_);
    return 0;
  }
  const StatusOr<std::string> status_or_encoded_std_str =
      JStringToUtf8String(jenv_, status_or_encoded_str.ValueOrDie().get());
  if (!status_or_encoded_std_str.ok()) {
    lua_error(state_);
    return 0;
  }
  PushString(status_or_encoded_std_str.ValueOrDie());
  return 1;
}

StatusOr<ScopedLocalRef<jobject>> JniLuaEnvironment::ParseUri(
    StringPiece url) const {
  if (url.empty()) {
    return {Status::UNKNOWN};
  }

  // Call to Java URI parser.
  TC3_ASSIGN_OR_RETURN(
      const StatusOr<ScopedLocalRef<jstring>> status_or_url_str,
      jni_cache_->ConvertToJavaString(url));

  // Try to parse uri and get scheme.
  TC3_ASSIGN_OR_RETURN(
      ScopedLocalRef<jobject> uri,
      JniHelper::CallStaticObjectMethod(jenv_, jni_cache_->uri_class.get(),
                                        jni_cache_->uri_parse,
                                        status_or_url_str.ValueOrDie().get()));
  if (uri == nullptr) {
    TC3_LOG(ERROR) << "Error calling Uri.parse";
    return {Status::UNKNOWN};
  }
  return uri;
}

int JniLuaEnvironment::HandleUrlSchema() {
  StringPiece url = ReadString(/*index=*/1);

  const StatusOr<ScopedLocalRef<jobject>> status_or_parsed_uri = ParseUri(url);
  if (!status_or_parsed_uri.ok()) {
    lua_error(state_);
    return 0;
  }

  const StatusOr<ScopedLocalRef<jstring>> status_or_scheme_str =
      JniHelper::CallObjectMethod<jstring>(
          jenv_, status_or_parsed_uri.ValueOrDie().get(),
          jni_cache_->uri_get_scheme);
  if (!status_or_scheme_str.ok()) {
    TC3_LOG(ERROR) << "Error calling Uri.getScheme";
    lua_error(state_);
    return 0;
  }
  if (status_or_scheme_str.ValueOrDie() == nullptr) {
    lua_pushnil(state_);
  } else {
    const StatusOr<std::string> status_or_scheme_std_str =
        JStringToUtf8String(jenv_, status_or_scheme_str.ValueOrDie().get());
    if (!status_or_scheme_std_str.ok()) {
      lua_error(state_);
      return 0;
    }
    PushString(status_or_scheme_std_str.ValueOrDie());
  }
  return 1;
}

int JniLuaEnvironment::HandleUrlHost() {
  const StringPiece url = ReadString(kIndexStackTop);

  const StatusOr<ScopedLocalRef<jobject>> status_or_parsed_uri = ParseUri(url);
  if (!status_or_parsed_uri.ok()) {
    lua_error(state_);
    return 0;
  }

  const StatusOr<ScopedLocalRef<jstring>> status_or_host_str =
      JniHelper::CallObjectMethod<jstring>(
          jenv_, status_or_parsed_uri.ValueOrDie().get(),
          jni_cache_->uri_get_host);
  if (!status_or_host_str.ok()) {
    TC3_LOG(ERROR) << "Error calling Uri.getHost";
    lua_error(state_);
    return 0;
  }

  if (status_or_host_str.ValueOrDie() == nullptr) {
    lua_pushnil(state_);
  } else {
    const StatusOr<std::string> status_or_host_std_str =
        JStringToUtf8String(jenv_, status_or_host_str.ValueOrDie().get());
    if (!status_or_host_std_str.ok()) {
      lua_error(state_);
      return 0;
    }
    PushString(status_or_host_std_str.ValueOrDie());
  }
  return 1;
}

int JniLuaEnvironment::HandleHash() {
  const StringPiece input = ReadString(kIndexStackTop);
  lua_pushinteger(state_, tc3farmhash::Hash32(input.data(), input.length()));
  return 1;
}

int JniLuaEnvironment::HandleFormat() {
  const int num_args = lua_gettop(state_);
  std::vector<StringPiece> args(num_args - 1);
  for (int i = 0; i < num_args - 1; i++) {
    args[i] = ReadString(/*index=*/i + 2);
  }
  PushString(strings::Substitute(ReadString(/*index=*/1), args));
  return 1;
}

bool JniLuaEnvironment::LookupModelStringResource() const {
  // Handle only lookup by name.
  if (lua_type(state_, kIndexStackTop) != LUA_TSTRING) {
    return false;
  }

  const StringPiece resource_name = ReadString(kIndexStackTop);
  std::string resource_content;
  if (!resources_.GetResourceContent(device_locales_, resource_name,
                                     &resource_content)) {
    // Resource cannot be provided by the model.
    return false;
  }

  PushString(resource_content);
  return true;
}

int JniLuaEnvironment::HandleAndroidStringResources() {
  // Check whether the requested resource can be served from the model data.
  if (LookupModelStringResource()) {
    return 1;
  }

  // Get system resources if not previously retrieved.
  if (!RetrieveSystemResources()) {
    TC3_LOG(ERROR) << "Error retrieving system resources.";
    lua_error(state_);
    return 0;
  }

  int resource_id;
  switch (lua_type(state_, kIndexStackTop)) {
    case LUA_TNUMBER:
      resource_id = Read<int>(/*index=*/kIndexStackTop);
      break;
    case LUA_TSTRING: {
      const StringPiece resource_name_str = ReadString(kIndexStackTop);
      if (resource_name_str.empty()) {
        TC3_LOG(ERROR) << "No resource name provided.";
        lua_error(state_);
        return 0;
      }
      const StatusOr<ScopedLocalRef<jstring>> status_or_resource_name =
          jni_cache_->ConvertToJavaString(resource_name_str);
      if (!status_or_resource_name.ok()) {
        TC3_LOG(ERROR) << "Invalid resource name.";
        lua_error(state_);
        return 0;
      }
      StatusOr<int> status_or_resource_id = JniHelper::CallIntMethod(
          jenv_, system_resources_.get(), jni_cache_->resources_get_identifier,
          status_or_resource_name.ValueOrDie().get(), string_.get(),
          android_.get());
      if (!status_or_resource_id.ok()) {
        TC3_LOG(ERROR) << "Error calling getIdentifier.";
        lua_error(state_);
        return 0;
      }
      resource_id = status_or_resource_id.ValueOrDie();
      break;
    }
    default:
      TC3_LOG(ERROR) << "Unexpected type for resource lookup.";
      lua_error(state_);
      return 0;
  }
  if (resource_id == 0) {
    TC3_LOG(ERROR) << "Resource not found.";
    lua_pushnil(state_);
    return 1;
  }
  StatusOr<ScopedLocalRef<jstring>> status_or_resource_str =
      JniHelper::CallObjectMethod<jstring>(jenv_, system_resources_.get(),
                                           jni_cache_->resources_get_string,
                                           resource_id);
  if (!status_or_resource_str.ok()) {
    TC3_LOG(ERROR) << "Error calling getString.";
    lua_error(state_);
    return 0;
  }

  if (status_or_resource_str.ValueOrDie() == nullptr) {
    lua_pushnil(state_);
  } else {
    StatusOr<std::string> status_or_resource_std_str =
        JStringToUtf8String(jenv_, status_or_resource_str.ValueOrDie().get());
    if (!status_or_resource_std_str.ok()) {
      lua_error(state_);
      return 0;
    }
    PushString(status_or_resource_std_str.ValueOrDie());
  }
  return 1;
}

bool JniLuaEnvironment::RetrieveSystemResources() {
  if (system_resources_resources_retrieved_) {
    return (system_resources_ != nullptr);
  }
  system_resources_resources_retrieved_ = true;
  TC3_ASSIGN_OR_RETURN_FALSE(ScopedLocalRef<jobject> system_resources_ref,
                             JniHelper::CallStaticObjectMethod(
                                 jenv_, jni_cache_->resources_class.get(),
                                 jni_cache_->resources_get_system));
  system_resources_ =
      MakeGlobalRef(system_resources_ref.get(), jenv_, jni_cache_->jvm);
  return (system_resources_ != nullptr);
}

bool JniLuaEnvironment::RetrieveUserManager() {
  if (context_ == nullptr) {
    return false;
  }
  if (usermanager_retrieved_) {
    return (usermanager_ != nullptr);
  }
  usermanager_retrieved_ = true;
  TC3_ASSIGN_OR_RETURN_FALSE(const ScopedLocalRef<jstring> service,
                             JniHelper::NewStringUTF(jenv_, "user"));
  TC3_ASSIGN_OR_RETURN_FALSE(
      const ScopedLocalRef<jobject> usermanager_ref,
      JniHelper::CallObjectMethod(jenv_, context_,
                                  jni_cache_->context_get_system_service,
                                  service.get()));

  usermanager_ = MakeGlobalRef(usermanager_ref.get(), jenv_, jni_cache_->jvm);
  return (usermanager_ != nullptr);
}

RemoteActionTemplate JniLuaEnvironment::ReadRemoteActionTemplateResult() const {
  RemoteActionTemplate result;
  // Read intent template.
  lua_pushnil(state_);
  while (Next(/*index=*/-2)) {
    const StringPiece key = ReadString(/*index=*/-2);
    if (key.Equals("title_without_entity")) {
      result.title_without_entity = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("title_with_entity")) {
      result.title_with_entity = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("description")) {
      result.description = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("description_with_app_name")) {
      result.description_with_app_name =
          Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("action")) {
      result.action = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("data")) {
      result.data = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("type")) {
      result.type = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("flags")) {
      result.flags = Read<int>(/*index=*/kIndexStackTop);
    } else if (key.Equals("package_name")) {
      result.package_name = Read<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("request_code")) {
      result.request_code = Read<int>(/*index=*/kIndexStackTop);
    } else if (key.Equals("category")) {
      result.category = ReadVector<std::string>(/*index=*/kIndexStackTop);
    } else if (key.Equals("extra")) {
      result.extra = ReadExtras();
    } else {
      TC3_LOG(INFO) << "Unknown entry: " << key;
    }
    lua_pop(state_, 1);
  }
  lua_pop(state_, 1);
  return result;
}

std::map<std::string, Variant> JniLuaEnvironment::ReadExtras() const {
  if (lua_type(state_, kIndexStackTop) != LUA_TTABLE) {
    TC3_LOG(ERROR) << "Expected extras table, got: "
                   << lua_type(state_, kIndexStackTop);
    lua_pop(state_, 1);
    return {};
  }
  std::map<std::string, Variant> extras;
  lua_pushnil(state_);
  while (Next(/*index=*/-2)) {
    // Each entry is a table specifying name and value.
    // The value is specified via a type specific field as Lua doesn't allow
    // to easily distinguish between different number types.
    if (lua_type(state_, kIndexStackTop) != LUA_TTABLE) {
      TC3_LOG(ERROR) << "Expected a table for an extra, got: "
                     << lua_type(state_, kIndexStackTop);
      lua_pop(state_, 1);
      return {};
    }
    std::string name;
    Variant value;

    lua_pushnil(state_);
    while (Next(/*index=*/-2)) {
      const StringPiece key = ReadString(/*index=*/-2);
      if (key.Equals("name")) {
        name = Read<std::string>(/*index=*/kIndexStackTop);
      } else if (key.Equals("int_value")) {
        value = Variant(Read<int>(/*index=*/kIndexStackTop));
      } else if (key.Equals("long_value")) {
        value = Variant(Read<int64>(/*index=*/kIndexStackTop));
      } else if (key.Equals("float_value")) {
        value = Variant(Read<float>(/*index=*/kIndexStackTop));
      } else if (key.Equals("bool_value")) {
        value = Variant(Read<bool>(/*index=*/kIndexStackTop));
      } else if (key.Equals("string_value")) {
        value = Variant(Read<std::string>(/*index=*/kIndexStackTop));
      } else if (key.Equals("string_array_value")) {
        value = Variant(ReadVector<std::string>(/*index=*/kIndexStackTop));
      } else if (key.Equals("float_array_value")) {
        value = Variant(ReadVector<float>(/*index=*/kIndexStackTop));
      } else if (key.Equals("int_array_value")) {
        value = Variant(ReadVector<int>(/*index=*/kIndexStackTop));
      } else if (key.Equals("named_variant_array_value")) {
        value = Variant(ReadExtras());
      } else {
        TC3_LOG(INFO) << "Unknown extra field: " << key;
      }
      lua_pop(state_, 1);
    }
    if (!name.empty()) {
      extras[name] = value;
    } else {
      TC3_LOG(ERROR) << "Unnamed extra entry. Skipping.";
    }
    lua_pop(state_, 1);
  }
  return extras;
}

int JniLuaEnvironment::ReadRemoteActionTemplates(
    std::vector<RemoteActionTemplate>* result) {
  // Read result.
  if (lua_type(state_, kIndexStackTop) != LUA_TTABLE) {
    TC3_LOG(ERROR) << "Unexpected result for snippet: "
                   << lua_type(state_, kIndexStackTop);
    lua_error(state_);
    return LUA_ERRRUN;
  }

  // Read remote action templates array.
  lua_pushnil(state_);
  while (Next(/*index=*/-2)) {
    if (lua_type(state_, kIndexStackTop) != LUA_TTABLE) {
      TC3_LOG(ERROR) << "Expected intent table, got: "
                     << lua_type(state_, kIndexStackTop);
      lua_pop(state_, 1);
      continue;
    }
    result->push_back(ReadRemoteActionTemplateResult());
  }
  lua_pop(state_, /*n=*/1);
  return LUA_OK;
}

bool JniLuaEnvironment::RunIntentGenerator(
    const std::string& generator_snippet,
    std::vector<RemoteActionTemplate>* remote_actions) {
  int status;
  status = luaL_loadbuffer(state_, generator_snippet.data(),
                           generator_snippet.size(),
                           /*name=*/nullptr);
  if (status != LUA_OK) {
    TC3_LOG(ERROR) << "Couldn't load generator snippet: " << status;
    return false;
  }
  status = lua_pcall(state_, /*nargs=*/0, /*nresults=*/1, /*errfunc=*/0);
  if (status != LUA_OK) {
    TC3_LOG(ERROR) << "Couldn't run generator snippet: " << status;
    return false;
  }
  if (RunProtected(
          [this, remote_actions] {
            return ReadRemoteActionTemplates(remote_actions);
          },
          /*num_args=*/1) != LUA_OK) {
    TC3_LOG(ERROR) << "Could not read results.";
    return false;
  }
  // Check that we correctly cleaned-up the state.
  const int stack_size = lua_gettop(state_);
  if (stack_size > 0) {
    TC3_LOG(ERROR) << "Unexpected stack size.";
    lua_settop(state_, 0);
    return false;
  }
  return true;
}

}  // namespace libtextclassifier3
