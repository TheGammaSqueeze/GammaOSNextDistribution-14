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

#ifndef ART_RUNTIME_HIDDEN_API_H_
#define ART_RUNTIME_HIDDEN_API_H_

#include "art_field.h"
#include "art_method.h"
#include "base/hiddenapi_domain.h"
#include "base/hiddenapi_flags.h"
#include "base/locks.h"
#include "base/macros.h"
#include "dex/class_accessor.h"
#include "intrinsics_enum.h"
#include "jni/jni_internal.h"
#include "mirror/class.h"
#include "mirror/class_loader.h"
#include "reflection.h"
#include "runtime.h"

namespace art HIDDEN {
namespace hiddenapi {

// Hidden API enforcement policy
// This must be kept in sync with ApplicationInfo.ApiEnforcementPolicy in
// frameworks/base/core/java/android/content/pm/ApplicationInfo.java
enum class EnforcementPolicy {
  kDisabled             = 0,
  kJustWarn             = 1,  // keep checks enabled, but allow everything (enables logging)
  kEnabled              = 2,  // ban conditionally blocked & blocklist
  kMax = kEnabled,
};

inline EnforcementPolicy EnforcementPolicyFromInt(int api_policy_int) {
  DCHECK_GE(api_policy_int, 0);
  DCHECK_LE(api_policy_int, static_cast<int>(EnforcementPolicy::kMax));
  return static_cast<EnforcementPolicy>(api_policy_int);
}

// Hidden API access method
// Thist must be kept in sync with VMRuntime.HiddenApiUsageLogger.ACCESS_METHOD_*
enum class AccessMethod {
  kNone = 0,  // internal test that does not correspond to an actual access by app
  kReflection = 1,
  kJNI = 2,
  kLinking = 3,
};

// Represents the API domain of a caller/callee.
class AccessContext {
 public:
  // Initialize to either the fully-trusted or fully-untrusted domain.
  explicit AccessContext(bool is_trusted)
      : klass_(nullptr),
        dex_file_(nullptr),
        domain_(ComputeDomain(is_trusted)) {}

  // Initialize from class loader and dex file (via dex cache).
  AccessContext(ObjPtr<mirror::ClassLoader> class_loader, ObjPtr<mirror::DexCache> dex_cache)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : klass_(nullptr),
        dex_file_(GetDexFileFromDexCache(dex_cache)),
        domain_(ComputeDomain(class_loader, dex_file_)) {}

  // Initialize from class loader and dex file (only used by tests).
  AccessContext(ObjPtr<mirror::ClassLoader> class_loader, const DexFile* dex_file)
      : klass_(nullptr),
        dex_file_(dex_file),
        domain_(ComputeDomain(class_loader, dex_file_)) {}

  // Initialize from Class.
  explicit AccessContext(ObjPtr<mirror::Class> klass)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : klass_(klass),
        dex_file_(GetDexFileFromDexCache(klass->GetDexCache())),
        domain_(ComputeDomain(klass, dex_file_)) {}

  ObjPtr<mirror::Class> GetClass() const { return klass_; }
  const DexFile* GetDexFile() const { return dex_file_; }
  Domain GetDomain() const { return domain_; }
  bool IsApplicationDomain() const { return domain_ == Domain::kApplication; }

  // Returns true if this domain is always allowed to access the domain of `callee`.
  bool CanAlwaysAccess(const AccessContext& callee) const {
    return IsDomainMoreTrustedThan(domain_, callee.domain_);
  }

 private:
  static const DexFile* GetDexFileFromDexCache(ObjPtr<mirror::DexCache> dex_cache)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return dex_cache.IsNull() ? nullptr : dex_cache->GetDexFile();
  }

  static Domain ComputeDomain(bool is_trusted) {
    return is_trusted ? Domain::kCorePlatform : Domain::kApplication;
  }

  static Domain ComputeDomain(ObjPtr<mirror::ClassLoader> class_loader, const DexFile* dex_file) {
    if (dex_file == nullptr) {
      return ComputeDomain(/* is_trusted= */ class_loader.IsNull());
    }

    return dex_file->GetHiddenapiDomain();
  }

  static Domain ComputeDomain(ObjPtr<mirror::Class> klass, const DexFile* dex_file)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    // Check other aspects of the context.
    Domain domain = ComputeDomain(klass->GetClassLoader(), dex_file);

    if (domain == Domain::kApplication &&
        klass->ShouldSkipHiddenApiChecks() &&
        Runtime::Current()->IsJavaDebuggableAtInit()) {
      // Class is known, it is marked trusted and we are in debuggable mode.
      domain = ComputeDomain(/* is_trusted= */ true);
    }

    return domain;
  }

  // Pointer to declaring class of the caller/callee (null if not provided).
  // This is not safe across GC but we're only using this class for passing
  // information about the caller to the access check logic and never retain
  // the AccessContext instance beyond that.
  const ObjPtr<mirror::Class> klass_;

  // DexFile of the caller/callee (null if not provided).
  const DexFile* const dex_file_;

  // Computed domain of the caller/callee.
  const Domain domain_;
};

class ScopedHiddenApiEnforcementPolicySetting {
 public:
  explicit ScopedHiddenApiEnforcementPolicySetting(EnforcementPolicy new_policy)
      : initial_policy_(Runtime::Current()->GetHiddenApiEnforcementPolicy()) {
    Runtime::Current()->SetHiddenApiEnforcementPolicy(new_policy);
  }

  ~ScopedHiddenApiEnforcementPolicySetting() {
    Runtime::Current()->SetHiddenApiEnforcementPolicy(initial_policy_);
  }

 private:
  const EnforcementPolicy initial_policy_;
  DISALLOW_COPY_AND_ASSIGN(ScopedHiddenApiEnforcementPolicySetting);
};

void InitializeCorePlatformApiPrivateFields() REQUIRES(!Locks::mutator_lock_);

// Walks the stack, finds the caller of this reflective call and returns
// a hiddenapi AccessContext formed from its declaring class.
AccessContext GetReflectionCallerAccessContext(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_);

// Implementation details. DO NOT ACCESS DIRECTLY.
namespace detail {

// Class to encapsulate the signature of a member (ArtField or ArtMethod). This
// is used as a helper when matching prefixes, and when logging the signature.
class MemberSignature {
 private:
  enum MemberType {
    kField,
    kMethod,
  };

  std::string class_name_;
  std::string member_name_;
  std::string type_signature_;
  std::string tmp_;
  MemberType type_;

  inline std::vector<const char*> GetSignatureParts() const;

 public:
  explicit MemberSignature(ArtField* field) REQUIRES_SHARED(Locks::mutator_lock_);
  explicit MemberSignature(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_);
  explicit MemberSignature(const ClassAccessor::Field& field);
  explicit MemberSignature(const ClassAccessor::Method& method);

  void Dump(std::ostream& os) const;

  bool Equals(const MemberSignature& other);
  bool MemberNameAndTypeMatch(const MemberSignature& other);

  // Performs prefix match on this member. Since the full member signature is
  // composed of several parts, we match each part in turn (rather than
  // building the entire thing in memory and performing a simple prefix match)
  bool DoesPrefixMatch(const std::string& prefix) const;

  bool DoesPrefixMatchAny(const std::vector<std::string>& exemptions);

  void WarnAboutAccess(AccessMethod access_method, ApiList list, bool access_denied);

  void LogAccessToEventLog(uint32_t sampled_value, AccessMethod access_method, bool access_denied);

  // Calls back into managed code to notify VMRuntime.nonSdkApiUsageConsumer that
  // |member| was accessed. This is usually called when an API is unsupported,
  // conditionally or unconditionally blocked. Given that the callback can execute arbitrary
  // code, a call to this method can result in thread suspension.
  void NotifyHiddenApiListener(AccessMethod access_method);
};

// Locates hiddenapi flags for `member` in the corresponding dex file.
// NB: This is an O(N) operation, linear with the number of members in the class def.
template<typename T>
uint32_t GetDexFlags(T* member) REQUIRES_SHARED(Locks::mutator_lock_);

// Handler of detected core platform API violations. Returns true if access to
// `member` should be denied.
template<typename T>
bool HandleCorePlatformApiViolation(T* member,
                                    const AccessContext& caller_context,
                                    AccessMethod access_method,
                                    EnforcementPolicy policy)
    REQUIRES_SHARED(Locks::mutator_lock_);

template<typename T>
bool ShouldDenyAccessToMemberImpl(T* member, ApiList api_list, AccessMethod access_method)
    REQUIRES_SHARED(Locks::mutator_lock_);

inline ArtField* GetInterfaceMemberIfProxy(ArtField* field) { return field; }

inline ArtMethod* GetInterfaceMemberIfProxy(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return method->GetInterfaceMethodIfProxy(kRuntimePointerSize);
}

// Returns access flags for the runtime representation of a class member (ArtField/ArtMember).
ALWAYS_INLINE inline uint32_t CreateRuntimeFlags_Impl(uint32_t dex_flags) {
  uint32_t runtime_flags = 0u;

  ApiList api_list(dex_flags);
  DCHECK(api_list.IsValid());

  if (api_list.Contains(ApiList::Sdk())) {
    runtime_flags |= kAccPublicApi;
  } else {
    // Only add domain-specific flags for non-public API members.
    // This simplifies hardcoded values for intrinsics.
    if (api_list.Contains(ApiList::CorePlatformApi())) {
      runtime_flags |= kAccCorePlatformApi;
    }
  }

  DCHECK_EQ(runtime_flags & kAccHiddenapiBits, runtime_flags)
      << "Runtime flags not in reserved access flags bits";
  return runtime_flags;
}

}  // namespace detail

// Returns access flags for the runtime representation of a class member (ArtField/ArtMember).
ALWAYS_INLINE inline uint32_t CreateRuntimeFlags(const ClassAccessor::BaseItem& member) {
  return detail::CreateRuntimeFlags_Impl(member.GetHiddenapiFlags());
}

// Returns access flags for the runtime representation of a class member (ArtField/ArtMember).
template<typename T>
ALWAYS_INLINE inline uint32_t CreateRuntimeFlags(T* member) REQUIRES_SHARED(Locks::mutator_lock_) {
  return detail::CreateRuntimeFlags_Impl(detail::GetDexFlags(member));
}

// Extracts hiddenapi runtime flags from access flags of ArtField.
ALWAYS_INLINE inline uint32_t GetRuntimeFlags(ArtField* field)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return field->GetAccessFlags() & kAccHiddenapiBits;
}

// Extracts hiddenapi runtime flags from access flags of ArtMethod.
// Uses hardcoded values for intrinsics.
ALWAYS_INLINE inline uint32_t GetRuntimeFlags(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (UNLIKELY(method->IsIntrinsic())) {
    switch (static_cast<Intrinsics>(method->GetIntrinsic())) {
      case Intrinsics::kSystemArrayCopyChar:
      case Intrinsics::kSystemArrayCopyByte:
      case Intrinsics::kSystemArrayCopyInt:
      case Intrinsics::kStringGetCharsNoCheck:
      case Intrinsics::kReferenceGetReferent:
      case Intrinsics::kReferenceRefersTo:
      case Intrinsics::kMemoryPeekByte:
      case Intrinsics::kMemoryPokeByte:
      case Intrinsics::kCRC32Update:
      case Intrinsics::kCRC32UpdateBytes:
      case Intrinsics::kCRC32UpdateByteBuffer:
      case Intrinsics::kStringNewStringFromBytes:
      case Intrinsics::kStringNewStringFromChars:
      case Intrinsics::kStringNewStringFromString:
      case Intrinsics::kMemoryPeekIntNative:
      case Intrinsics::kMemoryPeekLongNative:
      case Intrinsics::kMemoryPeekShortNative:
      case Intrinsics::kMemoryPokeIntNative:
      case Intrinsics::kMemoryPokeLongNative:
      case Intrinsics::kMemoryPokeShortNative:
      case Intrinsics::kUnsafeCASInt:
      case Intrinsics::kUnsafeCASLong:
      case Intrinsics::kUnsafeCASObject:
      case Intrinsics::kUnsafeGetAndAddInt:
      case Intrinsics::kUnsafeGetAndAddLong:
      case Intrinsics::kUnsafeGetAndSetInt:
      case Intrinsics::kUnsafeGetAndSetLong:
      case Intrinsics::kUnsafeGetAndSetObject:
      case Intrinsics::kUnsafeGetLongVolatile:
      case Intrinsics::kUnsafeGetObjectVolatile:
      case Intrinsics::kUnsafeGetVolatile:
      case Intrinsics::kUnsafePutLongOrdered:
      case Intrinsics::kUnsafePutLongVolatile:
      case Intrinsics::kUnsafePutObjectOrdered:
      case Intrinsics::kUnsafePutObjectVolatile:
      case Intrinsics::kUnsafePutOrdered:
      case Intrinsics::kUnsafePutVolatile:
      case Intrinsics::kUnsafeLoadFence:
      case Intrinsics::kUnsafeStoreFence:
      case Intrinsics::kUnsafeFullFence:
      case Intrinsics::kJdkUnsafeCASInt:
      case Intrinsics::kJdkUnsafeCASLong:
      case Intrinsics::kJdkUnsafeCASObject:
      case Intrinsics::kJdkUnsafeCompareAndSetInt:
      case Intrinsics::kJdkUnsafeCompareAndSetLong:
      case Intrinsics::kJdkUnsafeCompareAndSetReference:
      case Intrinsics::kJdkUnsafeGetAndAddInt:
      case Intrinsics::kJdkUnsafeGetAndAddLong:
      case Intrinsics::kJdkUnsafeGetAndSetInt:
      case Intrinsics::kJdkUnsafeGetAndSetLong:
      case Intrinsics::kJdkUnsafeGetAndSetReference:
      case Intrinsics::kJdkUnsafeGetLongVolatile:
      case Intrinsics::kJdkUnsafeGetLongAcquire:
      case Intrinsics::kJdkUnsafeGetReferenceVolatile:
      case Intrinsics::kJdkUnsafeGetReferenceAcquire:
      case Intrinsics::kJdkUnsafeGetVolatile:
      case Intrinsics::kJdkUnsafeGetAcquire:
      case Intrinsics::kJdkUnsafePutLongOrdered:
      case Intrinsics::kJdkUnsafePutLongVolatile:
      case Intrinsics::kJdkUnsafePutLongRelease:
      case Intrinsics::kJdkUnsafePutObjectOrdered:
      case Intrinsics::kJdkUnsafePutReferenceVolatile:
      case Intrinsics::kJdkUnsafePutReferenceRelease:
      case Intrinsics::kJdkUnsafePutOrdered:
      case Intrinsics::kJdkUnsafePutVolatile:
      case Intrinsics::kJdkUnsafePutRelease:
      case Intrinsics::kJdkUnsafeLoadFence:
      case Intrinsics::kJdkUnsafeStoreFence:
      case Intrinsics::kJdkUnsafeFullFence:
      case Intrinsics::kJdkUnsafeGet:
      case Intrinsics::kJdkUnsafeGetLong:
      case Intrinsics::kJdkUnsafeGetByte:
      case Intrinsics::kJdkUnsafeGetReference:
      case Intrinsics::kJdkUnsafePutLong:
      case Intrinsics::kJdkUnsafePut:
      case Intrinsics::kJdkUnsafePutReference:
      case Intrinsics::kJdkUnsafePutByte:
        return 0u;
      case Intrinsics::kFP16Ceil:
      case Intrinsics::kFP16Compare:
      case Intrinsics::kFP16Floor:
      case Intrinsics::kFP16Greater:
      case Intrinsics::kFP16GreaterEquals:
      case Intrinsics::kFP16Less:
      case Intrinsics::kFP16LessEquals:
      case Intrinsics::kFP16Min:
      case Intrinsics::kFP16Max:
      case Intrinsics::kFP16ToFloat:
      case Intrinsics::kFP16ToHalf:
      case Intrinsics::kFP16Rint:
      case Intrinsics::kUnsafeGet:
      case Intrinsics::kUnsafeGetLong:
      case Intrinsics::kUnsafeGetByte:
      case Intrinsics::kUnsafeGetObject:
      case Intrinsics::kUnsafePutLong:
      case Intrinsics::kUnsafePut:
      case Intrinsics::kUnsafePutObject:
      case Intrinsics::kUnsafePutByte:
        return kAccCorePlatformApi;
      default:
        // Remaining intrinsics are public API. We DCHECK that in SetIntrinsic().
        return kAccPublicApi;
    }
  } else {
    return method->GetAccessFlags() & kAccHiddenapiBits;
  }
}

// Called by class linker when a new dex file has been registered. Assigns
// the AccessContext domain to the newly-registered dex file based on its
// location and class loader.
void InitializeDexFileDomain(const DexFile& dex_file, ObjPtr<mirror::ClassLoader> class_loader);

// Returns true if access to `member` should be denied in the given context.
// The decision is based on whether the caller is in a trusted context or not.
// Because determining the access context can be expensive, a lambda function
// "fn_get_access_context" is lazily invoked after other criteria have been
// considered.
// This function might print warnings into the log if the member is hidden.
template<typename T>
bool ShouldDenyAccessToMember(T* member,
                              const std::function<AccessContext()>& fn_get_access_context,
                              AccessMethod access_method)
    REQUIRES_SHARED(Locks::mutator_lock_);

// Helper method for callers where access context can be determined beforehand.
// Wraps AccessContext in a lambda and passes it to the real ShouldDenyAccessToMember.
template<typename T>
inline bool ShouldDenyAccessToMember(T* member,
                                     const AccessContext& access_context,
                                     AccessMethod access_method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return ShouldDenyAccessToMember(member, [&]() { return access_context; }, access_method);
}

}  // namespace hiddenapi
}  // namespace art

#endif  // ART_RUNTIME_HIDDEN_API_H_
