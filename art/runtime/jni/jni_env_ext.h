/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_JNI_JNI_ENV_EXT_H_
#define ART_RUNTIME_JNI_JNI_ENV_EXT_H_

#include <jni.h>

#include "base/locks.h"
#include "base/macros.h"
#include "local_reference_table.h"
#include "obj_ptr.h"
#include "reference_table.h"

namespace art HIDDEN {

class ArtMethod;
class ArtField;
class JavaVMExt;
class ScopedObjectAccess;
class ScopedObjectAccessAlreadyRunnable;

namespace mirror {
class Object;
}  // namespace mirror

class JNIEnvExt : public JNIEnv {
 public:
  // Creates a new JNIEnvExt. Returns null on error, in which case error_msg
  // will contain a description of the error.
  static JNIEnvExt* Create(Thread* self, JavaVMExt* vm, std::string* error_msg);
  static MemberOffset LrtSegmentStateOffset(PointerSize pointer_size);
  static MemberOffset LrtPreviousStateOffset(PointerSize pointer_size);
  static MemberOffset SelfOffset(PointerSize pointer_size);
  static jint GetEnvHandler(JavaVMExt* vm, /*out*/void** out, jint version);

  ~JNIEnvExt();

  void DumpReferenceTables(std::ostream& os)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::alloc_tracker_lock_);

  void SetCheckJniEnabled(bool enabled) REQUIRES(!Locks::jni_function_table_lock_);

  void PushFrame(int capacity) REQUIRES_SHARED(Locks::mutator_lock_);
  void PopFrame() REQUIRES_SHARED(Locks::mutator_lock_);

  template<typename T>
  T AddLocalReference(ObjPtr<mirror::Object> obj)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Locks::alloc_tracker_lock_);

  void UpdateLocal(IndirectRef iref, ObjPtr<mirror::Object> obj)
      REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT jobject NewLocalRef(mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT void DeleteLocalRef(jobject obj) REQUIRES_SHARED(Locks::mutator_lock_);

  void TrimLocals() REQUIRES_SHARED(Locks::mutator_lock_) {
    locals_.Trim();
  }
  void AssertLocalsEmpty() REQUIRES_SHARED(Locks::mutator_lock_) {
    locals_.AssertEmpty();
  }
  size_t GetLocalsCapacity() REQUIRES_SHARED(Locks::mutator_lock_) {
    return locals_.Capacity();
  }

  jni::LRTSegmentState PushLocalReferenceFrame() {
    return locals_.PushFrame();
  }
  void PopLocalReferenceFrame(jni::LRTSegmentState previous_state) {
    locals_.PopFrame(previous_state);
  }

  void VisitJniLocalRoots(RootVisitor* visitor, const RootInfo& root_info)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    locals_.VisitRoots(visitor, root_info);
  }

  Thread* GetSelf() const { return self_; }
  uint32_t GetCritical() const { return critical_; }
  void SetCritical(uint32_t new_critical) { critical_ = new_critical; }
  uint64_t GetCriticalStartUs() const { return critical_start_us_; }
  void SetCriticalStartUs(uint64_t new_critical_start_us) {
    critical_start_us_ = new_critical_start_us;
  }
  const JNINativeInterface* GetUncheckedFunctions() const {
    return unchecked_functions_;
  }
  JavaVMExt* GetVm() const { return vm_; }

  void SetRuntimeDeleted() { runtime_deleted_.store(true, std::memory_order_relaxed); }
  bool IsRuntimeDeleted() const { return runtime_deleted_.load(std::memory_order_relaxed); }
  bool IsCheckJniEnabled() const { return check_jni_; }


  // Functions to keep track of monitor lock and unlock operations. Used to ensure proper locking
  // rules in CheckJNI mode.

  // Record locking of a monitor.
  void RecordMonitorEnter(jobject obj) REQUIRES_SHARED(Locks::mutator_lock_);

  // Check the release, that is, that the release is performed in the same JNI "segment."
  void CheckMonitorRelease(jobject obj) REQUIRES_SHARED(Locks::mutator_lock_);

  // Check that no monitors are held that have been acquired in this JNI "segment."
  void CheckNoHeldMonitors() REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitMonitorRoots(RootVisitor* visitor, const RootInfo& root_info)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    monitors_.VisitRoots(visitor, root_info);
  }

  // Set the functions to the runtime shutdown functions.
  void SetFunctionsToRuntimeShutdownFunctions();

  // Set the functions to the new JNI functions based on Runtime::GetJniIdType.
  void UpdateJniFunctionsPointer();

  // Set the function table override. This will install the override (or original table, if null)
  // to all threads.
  // Note: JNI function table overrides are sensitive to the order of operations wrt/ CheckJNI.
  //       After overriding the JNI function table, CheckJNI toggling is ignored.
  EXPORT static void SetTableOverride(const JNINativeInterface* table_override)
      REQUIRES(!Locks::thread_list_lock_, !Locks::jni_function_table_lock_);

  // Return either the regular, or the CheckJNI function table. Will return table_override_ instead
  // if it is not null.
  EXPORT static const JNINativeInterface* GetFunctionTable(bool check_jni)
      REQUIRES(Locks::jni_function_table_lock_);

  static void ResetFunctionTable()
      REQUIRES(!Locks::thread_list_lock_, !Locks::jni_function_table_lock_);

 private:
  static MemberOffset LocalReferenceTableOffset(PointerSize pointer_size);

  // Override of function tables. This applies to both default as well as instrumented (CheckJNI)
  // function tables.
  static const JNINativeInterface* table_override_ GUARDED_BY(Locks::jni_function_table_lock_);

  // The constructor should not be called directly. Use `Create()` that initializes
  // the new `JNIEnvExt` object by calling `Initialize()`.
  JNIEnvExt(Thread* self, JavaVMExt* vm)
      REQUIRES(!Locks::jni_function_table_lock_);

  // Initialize the `JNIEnvExt` object.
  bool Initialize(std::string* error_msg);

  // Link to Thread::Current().
  Thread* const self_;

  // The invocation interface JavaVM.
  JavaVMExt* const vm_;

  // JNI local references.
  jni::LocalReferenceTable locals_;

  // Stack of cookies corresponding to PushLocalFrame/PopLocalFrame calls.
  // TODO: to avoid leaks (and bugs), we need to clear this vector on entry (or return)
  // to a native method.
  std::vector<jni::LRTSegmentState> stacked_local_ref_cookies_;

  // Entered JNI monitors, for bulk exit on thread detach.
  ReferenceTable monitors_;

  // Used by -Xcheck:jni.
  JNINativeInterface const* unchecked_functions_;

  // All locked objects, with the (Java caller) stack frame that locked them. Used in CheckJNI
  // to ensure that only monitors locked in this native frame are being unlocked, and that at
  // the end all are unlocked.
  std::vector<std::pair<uintptr_t, jobject>> locked_objects_;

  // Start time of "critical" JNI calls to ensure that their use doesn't
  // excessively block the VM with CheckJNI.
  uint64_t critical_start_us_;

  // How many nested "critical" JNI calls are we in? Used by CheckJNI to ensure that criticals are
  uint32_t critical_;

  // Frequently-accessed fields cached from JavaVM.
  bool check_jni_;

  // If we are a JNI env for a daemon thread with a deleted runtime.
  std::atomic<bool> runtime_deleted_;

  template<bool kEnableIndexIds> friend class JNI;
  friend class Thread;
  friend IndirectReferenceTable* GetIndirectReferenceTable(ScopedObjectAccess& soa,
                                                           IndirectRefKind kind);
  friend jni::LocalReferenceTable* GetLocalReferenceTable(ScopedObjectAccess& soa);
  friend void ThreadResetFunctionTable(Thread* thread, void* arg);
  ART_FRIEND_TEST(JniInternalTest, JNIEnvExtOffsets);
};

// Used to save and restore the JNIEnvExt state when not going through code created by the JNI
// compiler.
class ScopedJniEnvLocalRefState {
 public:
  explicit ScopedJniEnvLocalRefState(JNIEnvExt* env) :
      env_(env),
      saved_local_ref_cookie_(env->PushLocalReferenceFrame()) {}

  ~ScopedJniEnvLocalRefState() {
    env_->PopLocalReferenceFrame(saved_local_ref_cookie_);
  }

 private:
  JNIEnvExt* const env_;
  const jni::LRTSegmentState saved_local_ref_cookie_;

  DISALLOW_COPY_AND_ASSIGN(ScopedJniEnvLocalRefState);
};

}  // namespace art

#endif  // ART_RUNTIME_JNI_JNI_ENV_EXT_H_
