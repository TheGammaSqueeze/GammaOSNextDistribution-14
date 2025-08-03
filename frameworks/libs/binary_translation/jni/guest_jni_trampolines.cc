/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "guest_jni_trampolines.h"

#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <cstddef>

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"
#include "berberis/base/stringprintf.h"
#include "berberis/guest_abi/guest_call.h"
#include "berberis/guest_loader/guest_loader.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

void* DlOpenLibicuNoLoad(const char* libname, GuestLoader* loader) {
  android_dlextinfo extinfo;
  extinfo.flags = ANDROID_DLEXT_USE_NAMESPACE;
  extinfo.library_namespace = loader->GetExportedNamespace("com_android_i18n");
  return loader->DlOpenExt(libname, RTLD_NOLOAD, &extinfo);
}

template <bool kIsStatic = false>
jmethodID GetLocaleMethodId(JNIEnv* env, const char* name, const char* signature) {
  auto jdeleter = [env](jobject obj) { env->DeleteLocalRef(obj); };
  std::unique_ptr<_jclass, decltype(jdeleter)> locale_class(env->FindClass("java/util/Locale"),
                                                            jdeleter);
  if (kIsStatic) {
    return env->GetStaticMethodID(locale_class.get(), name, signature);
  } else {
    return env->GetMethodID(locale_class.get(), name, signature);
  }
}

jmethodID GetLocaleStaticMethodId(JNIEnv* env, const char* name, const char* signature) {
  return GetLocaleMethodId<true>(env, name, signature);
}

void GuestCall_uloc_setDefault(GuestAddr addr, const char* tag) {
  CHECK_NE(addr, berberis::kNullGuestAddr);
  berberis::GuestCall call;
  int err = 0;
#if defined(BERBERIS_GUEST_ILP32)
  call.AddArgInt32(bit_cast<uint32_t>(tag));
  call.AddArgInt32(bit_cast<uint32_t>(&err));
#elif defined(BERBERIS_GUEST_LP64)
  call.AddArgInt64(bit_cast<uint64_t>(tag));
  call.AddArgInt64(bit_cast<uint64_t>(&err));
#else
#error "Unsupported guest arch"
#endif
  call.RunVoid(addr);
  // If error, we just skip guest setDefault.
}

// from external/icu/icu4c/source/common/unicode/uversion.h
using UVersionInfo = uint8_t[4];

void GuestCall_u_getVersion(GuestAddr addr, UVersionInfo version_info) {
  CHECK_NE(addr, berberis::kNullGuestAddr);
  berberis::GuestCall call;
#if defined(BERBERIS_GUEST_ILP32)
  call.AddArgInt32(bit_cast<uint32_t>(version_info));
#elif defined(BERBERIS_GUEST_LP64)
  call.AddArgInt64(bit_cast<uint64_t>(version_info));
#else
#error "Unsupported guest arch"
#endif
  call.RunVoid(addr);
}

bool GuestCall_uloc_canonicalize(GuestAddr addr,
                                 const char* tag,
                                 char* canonical_tag,
                                 size_t size) {
  CHECK_NE(addr, berberis::kNullGuestAddr);
  berberis::GuestCall call;
  int err = 0;
#if defined(BERBERIS_GUEST_ILP32)
  call.AddArgInt32(bit_cast<uint32_t>(tag));
  call.AddArgInt32(bit_cast<uint32_t>(canonical_tag));
  call.AddArgInt32(size);
  call.AddArgInt32(bit_cast<uint32_t>(&err));
#elif defined(BERBERIS_GUEST_LP64)
  call.AddArgInt64(bit_cast<uint64_t>(tag));
  call.AddArgInt64(bit_cast<uint64_t>(canonical_tag));
  call.AddArgInt64(size);
  call.AddArgInt64(bit_cast<uint64_t>(&err));
#else
#error "Unsupported guest arch"
#endif
  call.RunResInt32(addr);
  return err > 0;
}

bool InitLocaleCanonicalTag(JNIEnv* env,
                            GuestAddr uloc_canonicalize_addr,
                            jobject locale,
                            char* canonical_tag,
                            size_t size) {
  static auto Locale_toLanguageTag_method_id =
      GetLocaleMethodId(env, "toLanguageTag", "()Ljava/lang/String;");
  jstring java_tag =
      static_cast<jstring>(env->CallObjectMethod(locale, Locale_toLanguageTag_method_id));
  jboolean is_copy;
  const char* tag = env->GetStringUTFChars(java_tag, &is_copy);
  // It'd be sufficient to call native uloc_canonicalize here, but we don't want
  // to add libicu dependency here just for this purpose.
  bool is_error = GuestCall_uloc_canonicalize(uloc_canonicalize_addr, tag, canonical_tag, size);
  if (is_error) {
    return true;
  }
  env->ReleaseStringUTFChars(java_tag, tag);
  return false;
}

}  // namespace

void JNIEnv_CallStaticVoidMethodV_ForGuest(JNIEnv* env,
                                           jobject /* obj */,
                                           jmethodID method_id,
                                           jvalue* args) {
  // If we are calling Locale_uloc_setDefault, call it for guest as well. We are using the original
  // libicuuc which holds its own copy of the default state. Note that this won't help if java calls
  // setDefault directly. See b/202779669.
  static auto Locale_setDefault_method_id =
      GetLocaleStaticMethodId(env, "setDefault", "(Ljava/util/Locale;)V");
  if (method_id != Locale_setDefault_method_id) {
    return;
  }
  // setDefault has single arg - locale.
  auto locale = args->l;

  auto* loader = GuestLoader::GetInstance();
  void* libicu = DlOpenLibicuNoLoad("libicu.so", loader);
  if (libicu == nullptr) {
    // Skip guest setDefault if the library hasn't been loaded.
    return;
  }

  // Initialize canonical_tag argument for setDefault.

  // from external/icu/libicu/ndk_headers/unicode/uloc.h
  size_t ULOC_FULLNAME_CAPACITY = 157;
  char canonical_tag[ULOC_FULLNAME_CAPACITY];
  bool is_err = InitLocaleCanonicalTag(env,
                                       loader->DlSym(libicu, "uloc_canonicalize"),
                                       locale,
                                       canonical_tag,
                                       sizeof(canonical_tag));
  if (is_err) {
    // Skip guest setDefault if tag cannot be canonicalized.
    return;
  }

  // Stable libicu.so doesn't export uloc_setDefault since it requires the default to be set from
  // java to keep native and java in sync. So we'll call it from versioned libicuuc.so, but first
  // get the version from libicu.so. Note that since ICU is an apex and potentially can be updated
  // dynamically it's disallowed to read its version info from headers during the build time.

  UVersionInfo version_info;
  GuestCall_u_getVersion(loader->DlSym(libicu, "u_getVersion"), version_info);

  void* libicuuc = DlOpenLibicuNoLoad("libicuuc.so", loader);
  if (libicuuc == nullptr) {
    // Skip guest setDefault if the library hasn't been loaded.
    return;
  }

  auto uloc_setDefault_versioned_name = StringPrintf("uloc_setDefault_%u", version_info[0]);
  GuestCall_uloc_setDefault(loader->DlSym(libicuuc, uloc_setDefault_versioned_name.c_str()),
                            canonical_tag);
}

}  // namespace berberis
