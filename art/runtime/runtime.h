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

#ifndef ART_RUNTIME_RUNTIME_H_
#define ART_RUNTIME_RUNTIME_H_

#include <jni.h>
#include <stdio.h>

#include <forward_list>
#include <iosfwd>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "app_info.h"
#include "base/locks.h"
#include "base/macros.h"
#include "base/mem_map.h"
#include "base/metrics/metrics.h"
#include "base/os.h"
#include "base/string_view_cpp20.h"
#include "base/unix_file/fd_file.h"
#include "compat_framework.h"
#include "deoptimization_kind.h"
#include "dex/dex_file_types.h"
#include "experimental_flags.h"
#include "gc_root.h"
#include "instrumentation.h"
#include "jdwp_provider.h"
#include "jni/jni_id_manager.h"
#include "jni_id_type.h"
#include "metrics/reporter.h"
#include "obj_ptr.h"
#include "offsets.h"
#include "process_state.h"
#include "quick/quick_method_frame_info.h"
#include "reflective_value_visitor.h"
#include "runtime_stats.h"

namespace art HIDDEN {

namespace gc {
class AbstractSystemWeakHolder;
class Heap;
}  // namespace gc

namespace hiddenapi {
enum class EnforcementPolicy;
}  // namespace hiddenapi

namespace jit {
class Jit;
class JitCodeCache;
class JitOptions;
}  // namespace jit

namespace jni {
class SmallLrtAllocator;
}  // namespace jni

namespace mirror {
class Array;
class ClassLoader;
class DexCache;
template<class T> class ObjectArray;
template<class T> class PrimitiveArray;
using ByteArray = PrimitiveArray<int8_t>;
class String;
class Throwable;
}  // namespace mirror
namespace ti {
class Agent;
class AgentSpec;
}  // namespace ti
namespace verifier {
class MethodVerifier;
enum class VerifyMode : int8_t;
}  // namespace verifier
class ArenaPool;
class ArtMethod;
enum class CalleeSaveType: uint32_t;
class ClassLinker;
class CompilerCallbacks;
class Dex2oatImageTest;
class DexFile;
enum class InstructionSet;
class InternTable;
class IsMarkedVisitor;
class JavaVMExt;
class LinearAlloc;
class MonitorList;
class MonitorPool;
class NullPointerHandler;
class OatFileAssistantTest;
class OatFileManager;
class Plugin;
struct RuntimeArgumentMap;
class RuntimeCallbacks;
class SignalCatcher;
class StackOverflowHandler;
class SuspensionHandler;
class ThreadList;
class ThreadPool;
class Trace;
struct TraceConfig;
class Transaction;

using RuntimeOptions = std::vector<std::pair<std::string, const void*>>;

class Runtime {
 public:
  // Parse raw runtime options.
  EXPORT static bool ParseOptions(const RuntimeOptions& raw_options,
                                  bool ignore_unrecognized,
                                  RuntimeArgumentMap* runtime_options);

  // Creates and initializes a new runtime.
  EXPORT static bool Create(RuntimeArgumentMap&& runtime_options)
      SHARED_TRYLOCK_FUNCTION(true, Locks::mutator_lock_);

  // Creates and initializes a new runtime.
  EXPORT static bool Create(const RuntimeOptions& raw_options, bool ignore_unrecognized)
      SHARED_TRYLOCK_FUNCTION(true, Locks::mutator_lock_);

  enum class RuntimeDebugState {
    // This doesn't support any debug features / method tracing. This is the expected state usually.
    kNonJavaDebuggable,
    // This supports method tracing and a restricted set of debug features (for ex: redefinition
    // isn't supported). We transition to this state when method tracing has started or when the
    // debugger was attached and transition back to NonDebuggable once the tracing has stopped /
    // the debugger agent has detached..
    kJavaDebuggable,
    // The runtime was started as a debuggable runtime. This allows us to support the extended set
    // of debug features (for ex: redefinition). We never transition out of this state.
    kJavaDebuggableAtInit
  };

  bool EnsurePluginLoaded(const char* plugin_name, std::string* error_msg);
  bool EnsurePerfettoPlugin(std::string* error_msg);

  // IsAotCompiler for compilers that don't have a running runtime. Only dex2oat currently.
  bool IsAotCompiler() const {
    return !UseJitCompilation() && IsCompiler();
  }

  // IsCompiler is any runtime which has a running compiler, either dex2oat or JIT.
  bool IsCompiler() const {
    return compiler_callbacks_ != nullptr;
  }

  // If a compiler, are we compiling a boot image?
  bool IsCompilingBootImage() const;

  bool CanRelocate() const;

  bool ShouldRelocate() const {
    return must_relocate_ && CanRelocate();
  }

  bool MustRelocateIfPossible() const {
    return must_relocate_;
  }

  bool IsImageDex2OatEnabled() const {
    return image_dex2oat_enabled_;
  }

  CompilerCallbacks* GetCompilerCallbacks() {
    return compiler_callbacks_;
  }

  void SetCompilerCallbacks(CompilerCallbacks* callbacks) {
    CHECK(callbacks != nullptr);
    compiler_callbacks_ = callbacks;
  }

  bool IsZygote() const {
    return is_zygote_;
  }

  bool IsPrimaryZygote() const {
    return is_primary_zygote_;
  }

  bool IsSystemServer() const {
    return is_system_server_;
  }

  void SetAsSystemServer() {
    is_system_server_ = true;
    is_zygote_ = false;
    is_primary_zygote_ = false;
  }

  void SetAsZygoteChild(bool is_system_server, bool is_zygote) {
    // System server should have been set earlier in SetAsSystemServer.
    CHECK_EQ(is_system_server_, is_system_server);
    is_zygote_ = is_zygote;
    is_primary_zygote_ = false;
  }

  bool IsExplicitGcDisabled() const {
    return is_explicit_gc_disabled_;
  }

  bool IsEagerlyReleaseExplicitGcDisabled() const {
    return is_eagerly_release_explicit_gc_disabled_;
  }

  std::string GetCompilerExecutable() const;

  const std::vector<std::string>& GetCompilerOptions() const {
    return compiler_options_;
  }

  void AddCompilerOption(const std::string& option) {
    compiler_options_.push_back(option);
  }

  const std::vector<std::string>& GetImageCompilerOptions() const {
    return image_compiler_options_;
  }

  const std::vector<std::string>& GetImageLocations() const {
    return image_locations_;
  }

  // Starts a runtime, which may cause threads to be started and code to run.
  bool Start() UNLOCK_FUNCTION(Locks::mutator_lock_);

  EXPORT bool IsShuttingDown(Thread* self);
  bool IsShuttingDownLocked() const REQUIRES(Locks::runtime_shutdown_lock_) {
    return shutting_down_.load(std::memory_order_relaxed);
  }
  bool IsShuttingDownUnsafe() const {
    return shutting_down_.load(std::memory_order_relaxed);
  }
  void SetShuttingDown() REQUIRES(Locks::runtime_shutdown_lock_) {
    shutting_down_.store(true, std::memory_order_relaxed);
  }

  size_t NumberOfThreadsBeingBorn() const REQUIRES(Locks::runtime_shutdown_lock_) {
    return threads_being_born_;
  }

  void StartThreadBirth() REQUIRES(Locks::runtime_shutdown_lock_) {
    threads_being_born_++;
  }

  EXPORT void EndThreadBirth() REQUIRES(Locks::runtime_shutdown_lock_);

  bool IsStarted() const {
    return started_;
  }

  bool IsFinishedStarting() const {
    return finished_starting_;
  }

  EXPORT void RunRootClinits(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_);

  static Runtime* Current() {
    return instance_;
  }

  // Set the current runtime to be the given instance.
  // Note that this function is not responsible for cleaning up the old instance or taking the
  // ownership of the new instance.
  //
  // For test use only.
  static void TestOnlySetCurrent(Runtime* instance) { instance_ = instance; }

  // Set whichever abort message locations are appropriate to copies of the argument. Used by
  // Abort() and Thread::AbortInThis().
  static void SetAbortMessage(const char* msg) REQUIRES(!Locks::abort_lock_);

  // Aborts semi-cleanly. Used in the implementation of LOG(FATAL), which most
  // callers should prefer.
  NO_RETURN EXPORT static void Abort(const char* msg) REQUIRES(!Locks::abort_lock_);

  // Returns the "main" ThreadGroup, used when attaching user threads.
  jobject GetMainThreadGroup() const;

  // Returns the "system" ThreadGroup, used when attaching our internal threads.
  EXPORT jobject GetSystemThreadGroup() const;

  // Returns the system ClassLoader which represents the CLASSPATH.
  EXPORT jobject GetSystemClassLoader() const;

  // Attaches the calling native thread to the runtime.
  EXPORT bool AttachCurrentThread(const char* thread_name,
                                  bool as_daemon,
                                  jobject thread_group,
                                  bool create_peer,
                                  bool should_run_callbacks = true);

  EXPORT void CallExitHook(jint status);

  // Detaches the current native thread from the runtime.
  void DetachCurrentThread(bool should_run_callbacks = true) REQUIRES(!Locks::mutator_lock_);

  // If we are handling SIQQUIT return the time when we received it.
  std::optional<uint64_t> SiqQuitNanoTime() const;

  void DumpDeoptimizations(std::ostream& os);
  void DumpForSigQuit(std::ostream& os);
  void DumpLockHolders(std::ostream& os);

  EXPORT ~Runtime();

  const std::vector<std::string>& GetBootClassPath() const {
    return boot_class_path_;
  }

  const std::vector<std::string>& GetBootClassPathLocations() const {
    DCHECK(boot_class_path_locations_.empty() ||
           boot_class_path_locations_.size() == boot_class_path_.size());
    return boot_class_path_locations_.empty() ? boot_class_path_ : boot_class_path_locations_;
  }

  // Dynamically adds an element to boot class path.
  EXPORT void AppendToBootClassPath(
      const std::string& filename,
      const std::string& location,
      const std::vector<std::unique_ptr<const art::DexFile>>& dex_files);

  // Same as above, but takes raw pointers.
  EXPORT void AppendToBootClassPath(const std::string& filename,
                                    const std::string& location,
                                    const std::vector<const art::DexFile*>& dex_files);

  // Same as above, but also takes a dex cache for each dex file.
  EXPORT void AppendToBootClassPath(
      const std::string& filename,
      const std::string& location,
      const std::vector<std::pair<const art::DexFile*, ObjPtr<mirror::DexCache>>>&
          dex_files_and_cache);

  // Dynamically adds an element to boot class path and takes ownership of the dex files.
  EXPORT void AddExtraBootDexFiles(const std::string& filename,
                                   const std::string& location,
                                   std::vector<std::unique_ptr<const art::DexFile>>&& dex_files);

  ArrayRef<File> GetBootClassPathFiles() { return ArrayRef<File>(boot_class_path_files_); }

  ArrayRef<File> GetBootClassPathImageFiles() {
    return ArrayRef<File>(boot_class_path_image_files_);
  }

  ArrayRef<File> GetBootClassPathVdexFiles() { return ArrayRef<File>(boot_class_path_vdex_files_); }

  ArrayRef<File> GetBootClassPathOatFiles() { return ArrayRef<File>(boot_class_path_oat_files_); }

  // Returns the checksums for the boot image, extensions and extra boot class path dex files,
  // based on the image spaces and boot class path dex files loaded in memory.
  const std::string& GetBootClassPathChecksums() const {
    return boot_class_path_checksums_;
  }

  const std::string& GetClassPathString() const {
    return class_path_string_;
  }

  ClassLinker* GetClassLinker() const {
    return class_linker_;
  }

  jni::SmallLrtAllocator* GetSmallLrtAllocator() const {
    return small_lrt_allocator_;
  }

  jni::JniIdManager* GetJniIdManager() const {
    return jni_id_manager_.get();
  }

  size_t GetDefaultStackSize() const {
    return default_stack_size_;
  }

  unsigned int GetFinalizerTimeoutMs() const {
    return finalizer_timeout_ms_;
  }

  gc::Heap* GetHeap() const {
    return heap_;
  }

  InternTable* GetInternTable() const {
    DCHECK(intern_table_ != nullptr);
    return intern_table_;
  }

  JavaVMExt* GetJavaVM() const {
    return java_vm_.get();
  }

  size_t GetMaxSpinsBeforeThinLockInflation() const {
    return max_spins_before_thin_lock_inflation_;
  }

  MonitorList* GetMonitorList() const {
    return monitor_list_;
  }

  MonitorPool* GetMonitorPool() const {
    return monitor_pool_;
  }

  // Is the given object the special object used to mark a cleared JNI weak global?
  bool IsClearedJniWeakGlobal(ObjPtr<mirror::Object> obj) REQUIRES_SHARED(Locks::mutator_lock_);

  // Get the special object used to mark a cleared JNI weak global.
  mirror::Object* GetClearedJniWeakGlobal() REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT mirror::Throwable* GetPreAllocatedOutOfMemoryErrorWhenThrowingException()
      REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT mirror::Throwable* GetPreAllocatedOutOfMemoryErrorWhenThrowingOOME()
      REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT mirror::Throwable* GetPreAllocatedOutOfMemoryErrorWhenHandlingStackOverflow()
      REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT mirror::Throwable* GetPreAllocatedNoClassDefFoundError()
      REQUIRES_SHARED(Locks::mutator_lock_);

  const std::vector<std::string>& GetProperties() const {
    return properties_;
  }

  ThreadList* GetThreadList() const {
    return thread_list_;
  }

  static const char* GetVersion() {
    return "2.1.0";
  }

  bool IsMethodHandlesEnabled() const {
    return true;
  }

  void DisallowNewSystemWeaks() REQUIRES_SHARED(Locks::mutator_lock_);
  void AllowNewSystemWeaks() REQUIRES_SHARED(Locks::mutator_lock_);
  // broadcast_for_checkpoint is true when we broadcast for making blocking threads to respond to
  // checkpoint requests. It's false when we broadcast to unblock blocking threads after system weak
  // access is reenabled.
  void BroadcastForNewSystemWeaks(bool broadcast_for_checkpoint = false);

  // Visit all the roots. If only_dirty is true then non-dirty roots won't be visited. If
  // clean_dirty is true then dirty roots will be marked as non-dirty after visiting.
  EXPORT void VisitRoots(RootVisitor* visitor, VisitRootFlags flags = kVisitRootFlagAllRoots)
      REQUIRES(!Locks::classlinker_classes_lock_, !Locks::trace_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit image roots, only used for hprof since the GC uses the image space mod union table
  // instead.
  EXPORT void VisitImageRoots(RootVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit all of the roots we can safely visit concurrently.
  void VisitConcurrentRoots(RootVisitor* visitor,
                            VisitRootFlags flags = kVisitRootFlagAllRoots)
      REQUIRES(!Locks::classlinker_classes_lock_, !Locks::trace_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit all of the non thread roots, we can do this with mutators unpaused.
  void VisitNonThreadRoots(RootVisitor* visitor)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitTransactionRoots(RootVisitor* visitor)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Sweep system weaks, the system weak is deleted if the visitor return null. Otherwise, the
  // system weak is updated to be the visitor's returned value.
  EXPORT void SweepSystemWeaks(IsMarkedVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  // Walk all reflective objects and visit their targets as well as any method/fields held by the
  // runtime threads that are marked as being reflective.
  EXPORT void VisitReflectiveTargets(ReflectiveValueVisitor* visitor)
      REQUIRES(Locks::mutator_lock_);
  // Helper for visiting reflective targets with lambdas for both field and method reflective
  // targets.
  template <typename FieldVis, typename MethodVis>
  void VisitReflectiveTargets(FieldVis&& fv, MethodVis&& mv) REQUIRES(Locks::mutator_lock_) {
    FunctionReflectiveValueVisitor frvv(fv, mv);
    VisitReflectiveTargets(&frvv);
  }

  // Returns a special method that calls into a trampoline for runtime method resolution
  ArtMethod* GetResolutionMethod();

  bool HasResolutionMethod() const {
    return resolution_method_ != nullptr;
  }

  void SetResolutionMethod(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_);
  void ClearResolutionMethod() {
    resolution_method_ = nullptr;
  }

  ArtMethod* CreateResolutionMethod() REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns a special method that calls into a trampoline for runtime imt conflicts.
  ArtMethod* GetImtConflictMethod();
  ArtMethod* GetImtUnimplementedMethod();

  bool HasImtConflictMethod() const {
    return imt_conflict_method_ != nullptr;
  }

  void ClearImtConflictMethod() {
    imt_conflict_method_ = nullptr;
  }

  void FixupConflictTables() REQUIRES_SHARED(Locks::mutator_lock_);
  void SetImtConflictMethod(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_);
  void SetImtUnimplementedMethod(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_);

  ArtMethod* CreateImtConflictMethod(LinearAlloc* linear_alloc)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void ClearImtUnimplementedMethod() {
    imt_unimplemented_method_ = nullptr;
  }

  bool HasCalleeSaveMethod(CalleeSaveType type) const {
    return callee_save_methods_[static_cast<size_t>(type)] != 0u;
  }

  ArtMethod* GetCalleeSaveMethod(CalleeSaveType type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ArtMethod* GetCalleeSaveMethodUnchecked(CalleeSaveType type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  QuickMethodFrameInfo GetRuntimeMethodFrameInfo(ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static constexpr size_t GetCalleeSaveMethodOffset(CalleeSaveType type) {
    return OFFSETOF_MEMBER(Runtime, callee_save_methods_[static_cast<size_t>(type)]);
  }

  static constexpr MemberOffset GetInstrumentationOffset() {
    return MemberOffset(OFFSETOF_MEMBER(Runtime, instrumentation_));
  }

  InstructionSet GetInstructionSet() const {
    return instruction_set_;
  }

  EXPORT void SetInstructionSet(InstructionSet instruction_set);
  void ClearInstructionSet();

  EXPORT void SetCalleeSaveMethod(ArtMethod* method, CalleeSaveType type);
  void ClearCalleeSaveMethods();

  EXPORT ArtMethod* CreateCalleeSaveMethod() REQUIRES_SHARED(Locks::mutator_lock_);

  uint64_t GetStat(int kind);

  RuntimeStats* GetStats() {
    return &stats_;
  }

  bool HasStatsEnabled() const {
    return stats_enabled_;
  }

  void ResetStats(int kinds);

  void SetStatsEnabled(bool new_state)
      REQUIRES(!Locks::instrument_entrypoints_lock_, !Locks::mutator_lock_);

  enum class NativeBridgeAction {  // private
    kUnload,
    kInitialize
  };

  jit::Jit* GetJit() const {
    return jit_.get();
  }

  jit::JitCodeCache* GetJitCodeCache() const {
    return jit_code_cache_.get();
  }

  // Returns true if JIT compilations are enabled. GetJit() will be not null in this case.
  EXPORT bool UseJitCompilation() const;

  void PreZygoteFork();
  void PostZygoteFork();
  void InitNonZygoteOrPostFork(
      JNIEnv* env,
      bool is_system_server,
      bool is_child_zygote,
      NativeBridgeAction action,
      const char* isa,
      bool profile_system_server = false);

  const instrumentation::Instrumentation* GetInstrumentation() const {
    return &instrumentation_;
  }

  instrumentation::Instrumentation* GetInstrumentation() {
    return &instrumentation_;
  }

  void RegisterAppInfo(const std::string& package_name,
                       const std::vector<std::string>& code_paths,
                       const std::string& profile_output_filename,
                       const std::string& ref_profile_filename,
                       int32_t code_type);

  // Transaction support.
  EXPORT bool IsActiveTransaction() const;
  // EnterTransactionMode may suspend.
  EXPORT void EnterTransactionMode(bool strict, mirror::Class* root)
      REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT void ExitTransactionMode();
  EXPORT void RollbackAllTransactions() REQUIRES_SHARED(Locks::mutator_lock_);
  // Transaction rollback and exit transaction are always done together, it's convenience to
  // do them in one function.
  void RollbackAndExitTransactionMode() REQUIRES_SHARED(Locks::mutator_lock_);
  bool IsTransactionAborted() const;
  const Transaction* GetTransaction() const;
  Transaction* GetTransaction();
  bool IsActiveStrictTransactionMode() const;

  void AbortTransactionAndThrowAbortError(Thread* self, const std::string& abort_message)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void ThrowTransactionAbortError(Thread* self)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void RecordWriteFieldBoolean(mirror::Object* obj,
                               MemberOffset field_offset,
                               uint8_t value,
                               bool is_volatile);
  void RecordWriteFieldByte(mirror::Object* obj,
                            MemberOffset field_offset,
                            int8_t value,
                            bool is_volatile);
  void RecordWriteFieldChar(mirror::Object* obj,
                            MemberOffset field_offset,
                            uint16_t value,
                            bool is_volatile);
  void RecordWriteFieldShort(mirror::Object* obj,
                             MemberOffset field_offset,
                             int16_t value,
                             bool is_volatile);
  EXPORT void RecordWriteField32(mirror::Object* obj,
                                 MemberOffset field_offset,
                                 uint32_t value,
                                 bool is_volatile);
  void RecordWriteField64(mirror::Object* obj,
                          MemberOffset field_offset,
                          uint64_t value,
                          bool is_volatile);
  EXPORT void RecordWriteFieldReference(mirror::Object* obj,
                                        MemberOffset field_offset,
                                        ObjPtr<mirror::Object> value,
                                        bool is_volatile) REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT void RecordWriteArray(mirror::Array* array, size_t index, uint64_t value)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void RecordStrongStringInsertion(ObjPtr<mirror::String> s)
      REQUIRES(Locks::intern_table_lock_);
  void RecordWeakStringInsertion(ObjPtr<mirror::String> s)
      REQUIRES(Locks::intern_table_lock_);
  void RecordStrongStringRemoval(ObjPtr<mirror::String> s)
      REQUIRES(Locks::intern_table_lock_);
  void RecordWeakStringRemoval(ObjPtr<mirror::String> s)
      REQUIRES(Locks::intern_table_lock_);
  void RecordResolveString(ObjPtr<mirror::DexCache> dex_cache, dex::StringIndex string_idx)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void RecordResolveMethodType(ObjPtr<mirror::DexCache> dex_cache, dex::ProtoIndex proto_idx)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void SetFaultMessage(const std::string& message);

  void AddCurrentRuntimeFeaturesAsDex2OatArguments(std::vector<std::string>* arg_vector) const;

  bool GetImplicitStackOverflowChecks() const {
    return implicit_so_checks_;
  }

  bool GetImplicitSuspendChecks() const {
    return implicit_suspend_checks_;
  }

  bool GetImplicitNullChecks() const {
    return implicit_null_checks_;
  }

  void DisableVerifier();
  bool IsVerificationEnabled() const;
  EXPORT bool IsVerificationSoftFail() const;

  void SetHiddenApiEnforcementPolicy(hiddenapi::EnforcementPolicy policy) {
    hidden_api_policy_ = policy;
  }

  hiddenapi::EnforcementPolicy GetHiddenApiEnforcementPolicy() const {
    return hidden_api_policy_;
  }

  void SetCorePlatformApiEnforcementPolicy(hiddenapi::EnforcementPolicy policy) {
    core_platform_api_policy_ = policy;
  }

  hiddenapi::EnforcementPolicy GetCorePlatformApiEnforcementPolicy() const {
    return core_platform_api_policy_;
  }

  void SetTestApiEnforcementPolicy(hiddenapi::EnforcementPolicy policy) {
    test_api_policy_ = policy;
  }

  hiddenapi::EnforcementPolicy GetTestApiEnforcementPolicy() const {
    return test_api_policy_;
  }

  void SetHiddenApiExemptions(const std::vector<std::string>& exemptions) {
    hidden_api_exemptions_ = exemptions;
  }

  const std::vector<std::string>& GetHiddenApiExemptions() {
    return hidden_api_exemptions_;
  }

  void SetDedupeHiddenApiWarnings(bool value) {
    dedupe_hidden_api_warnings_ = value;
  }

  bool ShouldDedupeHiddenApiWarnings() {
    return dedupe_hidden_api_warnings_;
  }

  void SetHiddenApiEventLogSampleRate(uint32_t rate) {
    hidden_api_access_event_log_rate_ = rate;
  }

  uint32_t GetHiddenApiEventLogSampleRate() const {
    return hidden_api_access_event_log_rate_;
  }

  const std::string& GetProcessPackageName() const {
    return process_package_name_;
  }

  void SetProcessPackageName(const char* package_name) {
    if (package_name == nullptr) {
      process_package_name_.clear();
    } else {
      process_package_name_ = package_name;
    }
  }

  const std::string& GetProcessDataDirectory() const {
    return process_data_directory_;
  }

  void SetProcessDataDirectory(const char* data_dir) {
    if (data_dir == nullptr) {
      process_data_directory_.clear();
    } else {
      process_data_directory_ = data_dir;
    }
  }

  const std::vector<std::string>& GetCpuAbilist() const {
    return cpu_abilist_;
  }

  bool IsRunningOnMemoryTool() const {
    return is_running_on_memory_tool_;
  }

  void SetTargetSdkVersion(uint32_t version) {
    target_sdk_version_ = version;
  }

  uint32_t GetTargetSdkVersion() const {
    return target_sdk_version_;
  }

  CompatFramework& GetCompatFramework() {
    return compat_framework_;
  }

  uint32_t GetZygoteMaxFailedBoots() const {
    return zygote_max_failed_boots_;
  }

  bool AreExperimentalFlagsEnabled(ExperimentalFlags flags) {
    return (experimental_flags_ & flags) != ExperimentalFlags::kNone;
  }

  void CreateJitCodeCache(bool rwx_memory_allowed);

  // Create the JIT and instrumentation and code cache.
  void CreateJit();

  ArenaPool* GetLinearAllocArenaPool() {
    return linear_alloc_arena_pool_.get();
  }
  ArenaPool* GetArenaPool() {
    return arena_pool_.get();
  }
  const ArenaPool* GetArenaPool() const {
    return arena_pool_.get();
  }
  ArenaPool* GetJitArenaPool() {
    return jit_arena_pool_.get();
  }

  EXPORT void ReclaimArenaPoolMemory();

  LinearAlloc* GetLinearAlloc() {
    return linear_alloc_.get();
  }

  LinearAlloc* GetStartupLinearAlloc() {
    return startup_linear_alloc_.load(std::memory_order_relaxed);
  }

  jit::JitOptions* GetJITOptions() {
    return jit_options_.get();
  }

  bool IsJavaDebuggable() const {
    return runtime_debug_state_ == RuntimeDebugState::kJavaDebuggable ||
           runtime_debug_state_ == RuntimeDebugState::kJavaDebuggableAtInit;
  }

  bool IsJavaDebuggableAtInit() const {
    return runtime_debug_state_ == RuntimeDebugState::kJavaDebuggableAtInit;
  }

  void SetProfileableFromShell(bool value) {
    is_profileable_from_shell_ = value;
  }

  bool IsProfileableFromShell() const {
    return is_profileable_from_shell_;
  }

  void SetProfileable(bool value) {
    is_profileable_ = value;
  }

  bool IsProfileable() const {
    return is_profileable_;
  }

  EXPORT void SetRuntimeDebugState(RuntimeDebugState state);

  // Deoptimize the boot image, called for Java debuggable apps.
  EXPORT void DeoptimizeBootImage() REQUIRES(Locks::mutator_lock_);

  bool IsNativeDebuggable() const {
    return is_native_debuggable_;
  }

  void SetNativeDebuggable(bool value) {
    is_native_debuggable_ = value;
  }

  void SetSignalHookDebuggable(bool value);

  bool AreNonStandardExitsEnabled() const {
    return non_standard_exits_enabled_;
  }

  void SetNonStandardExitsEnabled() {
    non_standard_exits_enabled_ = true;
  }

  bool AreAsyncExceptionsThrown() const {
    return async_exceptions_thrown_;
  }

  void SetAsyncExceptionsThrown() {
    async_exceptions_thrown_ = true;
  }

  // Returns the build fingerprint, if set. Otherwise an empty string is returned.
  std::string GetFingerprint() {
    return fingerprint_;
  }

  // Called from class linker.
  void SetSentinel(ObjPtr<mirror::Object> sentinel) REQUIRES_SHARED(Locks::mutator_lock_);
  // For testing purpose only.
  // TODO: Remove this when this is no longer needed (b/116087961).
  EXPORT GcRoot<mirror::Object> GetSentinel() REQUIRES_SHARED(Locks::mutator_lock_);

  // Use a sentinel for marking entries in a table that have been cleared.
  // This helps diagnosing in case code tries to wrongly access such
  // entries.
  static mirror::Class* GetWeakClassSentinel() {
    return reinterpret_cast<mirror::Class*>(0xebadbeef);
  }

  // Create a normal LinearAlloc or low 4gb version if we are 64 bit AOT compiler.
  EXPORT LinearAlloc* CreateLinearAlloc();
  // Setup linear-alloc allocators to stop using the current arena so that the
  // next allocations, which would be after zygote fork, happens in userfaultfd
  // visited space.
  void SetupLinearAllocForPostZygoteFork(Thread* self)
      REQUIRES(!Locks::mutator_lock_, !Locks::classlinker_classes_lock_);

  OatFileManager& GetOatFileManager() const {
    DCHECK(oat_file_manager_ != nullptr);
    return *oat_file_manager_;
  }

  double GetHashTableMinLoadFactor() const;
  double GetHashTableMaxLoadFactor() const;

  bool IsSafeMode() const {
    return safe_mode_;
  }

  void SetSafeMode(bool mode) {
    safe_mode_ = mode;
  }

  bool GetDumpNativeStackOnSigQuit() const {
    return dump_native_stack_on_sig_quit_;
  }

  EXPORT void UpdateProcessState(ProcessState process_state);

  // Returns true if we currently care about long mutator pause.
  bool InJankPerceptibleProcessState() const {
    return process_state_ == kProcessStateJankPerceptible;
  }

  void RegisterSensitiveThread() const;

  void SetZygoteNoThreadSection(bool val) {
    zygote_no_threads_ = val;
  }

  bool IsZygoteNoThreadSection() const {
    return zygote_no_threads_;
  }

  // Returns if the code can be deoptimized asynchronously. Code may be compiled with some
  // optimization that makes it impossible to deoptimize.
  EXPORT bool IsAsyncDeoptimizeable(ArtMethod* method, uintptr_t code) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns a saved copy of the environment (getenv/setenv values).
  // Used by Fork to protect against overwriting LD_LIBRARY_PATH, etc.
  char** GetEnvSnapshot() const {
    return env_snapshot_.GetSnapshot();
  }

  EXPORT void AddSystemWeakHolder(gc::AbstractSystemWeakHolder* holder);
  EXPORT void RemoveSystemWeakHolder(gc::AbstractSystemWeakHolder* holder);

  EXPORT void AttachAgent(JNIEnv* env, const std::string& agent_arg, jobject class_loader);

  const std::list<std::unique_ptr<ti::Agent>>& GetAgents() const {
    return agents_;
  }

  EXPORT RuntimeCallbacks* GetRuntimeCallbacks();

  bool HasLoadedPlugins() const {
    return !plugins_.empty();
  }

  void InitThreadGroups(Thread* self);

  void SetDumpGCPerformanceOnShutdown(bool value) {
    dump_gc_performance_on_shutdown_ = value;
  }

  bool GetDumpGCPerformanceOnShutdown() const {
    return dump_gc_performance_on_shutdown_;
  }

  void IncrementDeoptimizationCount(DeoptimizationKind kind) {
    DCHECK_LE(kind, DeoptimizationKind::kLast);
    deoptimization_counts_[static_cast<size_t>(kind)]++;
  }

  uint32_t GetNumberOfDeoptimizations() const {
    uint32_t result = 0;
    for (size_t i = 0; i <= static_cast<size_t>(DeoptimizationKind::kLast); ++i) {
      result += deoptimization_counts_[i];
    }
    return result;
  }

  bool DenyArtApexDataFiles() const {
    return deny_art_apex_data_files_;
  }

  size_t GetMadviseWillNeedTotalDexSize() const {
    return madvise_willneed_total_dex_size_;
  }

  size_t GetMadviseWillNeedSizeOdex() const {
    return madvise_willneed_odex_filesize_;
  }

  size_t GetMadviseWillNeedSizeArt() const {
    return madvise_willneed_art_filesize_;
  }

  const std::string& GetJdwpOptions() {
    return jdwp_options_;
  }

  JdwpProvider GetJdwpProvider() const {
    return jdwp_provider_;
  }

  JniIdType GetJniIdType() const {
    return jni_ids_indirection_;
  }

  bool CanSetJniIdType() const {
    return GetJniIdType() == JniIdType::kSwapablePointer;
  }

  // Changes the JniIdType to the given type. Only allowed if CanSetJniIdType(). All threads must be
  // suspended to call this function.
  EXPORT void SetJniIdType(JniIdType t);

  uint32_t GetVerifierLoggingThresholdMs() const {
    return verifier_logging_threshold_ms_;
  }

  // Atomically delete the thread pool if the reference count is 0.
  bool DeleteThreadPool() REQUIRES(!Locks::runtime_thread_pool_lock_);

  // Wait for all the thread workers to be attached.
  void WaitForThreadPoolWorkersToStart() REQUIRES(!Locks::runtime_thread_pool_lock_);

  // Scoped usage of the runtime thread pool. Prevents the pool from being
  // deleted. Note that the thread pool is only for startup and gets deleted after.
  class ScopedThreadPoolUsage {
   public:
    ScopedThreadPoolUsage();
    ~ScopedThreadPoolUsage();

    // Return the thread pool.
    ThreadPool* GetThreadPool() const {
      return thread_pool_;
    }

   private:
    ThreadPool* const thread_pool_;
  };

  LinearAlloc* ReleaseStartupLinearAlloc() {
    return startup_linear_alloc_.exchange(nullptr, std::memory_order_relaxed);
  }

  bool LoadAppImageStartupCache() const {
    return load_app_image_startup_cache_;
  }

  void SetLoadAppImageStartupCacheEnabled(bool enabled) {
    load_app_image_startup_cache_ = enabled;
  }

  // Reset the startup completed status so that we can call NotifyStartupCompleted again. Should
  // only be used for testing.
  EXPORT void ResetStartupCompleted();

  // Notify the runtime that application startup is considered completed. Only has effect for the
  // first call. Returns whether this was the first call.
  bool NotifyStartupCompleted();

  // Notify the runtime that the application finished loading some dex/odex files. This is
  // called everytime we load a set of dex files in a class loader.
  void NotifyDexFileLoaded();

  // Return true if startup is already completed.
  EXPORT bool GetStartupCompleted() const;

  bool IsVerifierMissingKThrowFatal() const {
    return verifier_missing_kthrow_fatal_;
  }

  bool IsJavaZygoteForkLoopRequired() const {
    return force_java_zygote_fork_loop_;
  }

  bool IsPerfettoHprofEnabled() const {
    return perfetto_hprof_enabled_;
  }

  bool IsPerfettoJavaHeapStackProfEnabled() const {
    return perfetto_javaheapprof_enabled_;
  }

  bool IsMonitorTimeoutEnabled() const {
    return monitor_timeout_enable_;
  }

  uint64_t GetMonitorTimeoutNs() const {
    return monitor_timeout_ns_;
  }

  // Return whether this is system server and it is being profiled.
  bool IsSystemServerProfiled() const;

  // Return whether we should load oat files as executable or not.
  bool GetOatFilesExecutable() const;

  metrics::ArtMetrics* GetMetrics() { return &metrics_; }

  AppInfo* GetAppInfo() { return &app_info_; }

  void RequestMetricsReport(bool synchronous = true);

  static void MadviseFileForRange(size_t madvise_size_limit_bytes,
                                  size_t map_size_bytes,
                                  const uint8_t* map_begin,
                                  const uint8_t* map_end,
                                  const std::string& file_name);

  const std::string& GetApexVersions() const {
    return apex_versions_;
  }

  // Return whether a boot image has a profile. This means it's an in-memory
  // image rather that an image loaded from disk.
  bool HasImageWithProfile() const;

  bool GetNoSigChain() const {
    return no_sig_chain_;
  }

  void AddGeneratedCodeRange(const void* start, size_t size);
  void RemoveGeneratedCodeRange(const void* start, size_t size)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Trigger a flag reload from system properties or device congfigs.
  //
  // Should only be called from runtime init and zygote post fork as
  // we don't want to change the runtime config midway during execution.
  //
  // The caller argument should be the name of the function making this call
  // and will be used to enforce the appropriate names.
  //
  // See Flags::ReloadAllFlags as well.
  static void ReloadAllFlags(const std::string& caller);

  // Parses /apex/apex-info-list.xml to build a string containing apex versions of boot classpath
  // jars, which is encoded into .oat files.
  static std::string GetApexVersions(ArrayRef<const std::string> boot_class_path_locations);

  bool AllowInMemoryCompilation() const { return allow_in_memory_compilation_; }

  // Used by plugin code to attach a hook for OOME.
  void SetOutOfMemoryErrorHook(void (*hook)()) {
    out_of_memory_error_hook_ = hook;
  }

  void OutOfMemoryErrorHook() {
    if (out_of_memory_error_hook_ != nullptr) {
      out_of_memory_error_hook_();
    }
  }

 private:
  static void InitPlatformSignalHandlers();

  Runtime();

  bool HandlesSignalsInCompiledCode() const {
    return !no_sig_chain_ &&
           (implicit_null_checks_ || implicit_so_checks_ || implicit_suspend_checks_);
  }

  void BlockSignals();

  bool Init(RuntimeArgumentMap&& runtime_options)
      SHARED_TRYLOCK_FUNCTION(true, Locks::mutator_lock_);
  void InitNativeMethods() REQUIRES(!Locks::mutator_lock_);
  void RegisterRuntimeNativeMethods(JNIEnv* env);
  void InitMetrics();

  void StartDaemonThreads() REQUIRES_SHARED(Locks::mutator_lock_);
  void StartSignalCatcher();

  void MaybeSaveJitProfilingInfo();

  // Visit all of the thread roots.
  void VisitThreadRoots(RootVisitor* visitor, VisitRootFlags flags)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit all other roots which must be done with mutators suspended.
  void VisitNonConcurrentRoots(RootVisitor* visitor, VisitRootFlags flags)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Constant roots are the roots which never change after the runtime is initialized, they only
  // need to be visited once per GC cycle.
  void VisitConstantRoots(RootVisitor* visitor)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Note: To be lock-free, GetFaultMessage temporarily replaces the lock message with null.
  //       As such, there is a window where a call will return an empty string. In general,
  //       only aborting code should retrieve this data (via GetFaultMessageForAbortLogging
  //       friend).
  std::string GetFaultMessage();

  ThreadPool* AcquireThreadPool() REQUIRES(!Locks::runtime_thread_pool_lock_);
  void ReleaseThreadPool() REQUIRES(!Locks::runtime_thread_pool_lock_);

  // Caches the apex versions produced by `GetApexVersions`.
  void InitializeApexVersions();

  void AppendToBootClassPath(const std::string& filename, const std::string& location);

  // Don't use EXPORT ("default" visibility), because quick_entrypoints_x86.o
  // refers to this symbol and it can't link with R_386_PC32 relocation.
  // A pointer to the active runtime or null.
  LIBART_PROTECTED static Runtime* instance_;

  // NOTE: these must match the gc::ProcessState values as they come directly from the framework.
  static constexpr int kProfileForground = 0;
  static constexpr int kProfileBackground = 1;

  static constexpr uint32_t kCalleeSaveSize = 6u;

  // 64 bit so that we can share the same asm offsets for both 32 and 64 bits.
  uint64_t callee_save_methods_[kCalleeSaveSize];
  // Pre-allocated exceptions (see Runtime::Init).
  GcRoot<mirror::Throwable> pre_allocated_OutOfMemoryError_when_throwing_exception_;
  GcRoot<mirror::Throwable> pre_allocated_OutOfMemoryError_when_throwing_oome_;
  GcRoot<mirror::Throwable> pre_allocated_OutOfMemoryError_when_handling_stack_overflow_;
  GcRoot<mirror::Throwable> pre_allocated_NoClassDefFoundError_;
  ArtMethod* resolution_method_;
  ArtMethod* imt_conflict_method_;
  // Unresolved method has the same behavior as the conflict method, it is used by the class linker
  // for differentiating between unfilled imt slots vs conflict slots in superclasses.
  ArtMethod* imt_unimplemented_method_;

  // Special sentinel object used to invalid conditions in JNI (cleared weak references) and
  // JDWP (invalid references).
  GcRoot<mirror::Object> sentinel_;

  InstructionSet instruction_set_;

  CompilerCallbacks* compiler_callbacks_;
  bool is_zygote_;
  bool is_primary_zygote_;
  bool is_system_server_;
  bool must_relocate_;
  bool is_concurrent_gc_enabled_;
  bool is_explicit_gc_disabled_;
  bool is_eagerly_release_explicit_gc_disabled_;
  bool image_dex2oat_enabled_;

  std::string compiler_executable_;
  std::vector<std::string> compiler_options_;
  std::vector<std::string> image_compiler_options_;
  std::vector<std::string> image_locations_;

  std::vector<std::string> boot_class_path_;
  std::vector<std::string> boot_class_path_locations_;
  std::string boot_class_path_checksums_;
  std::vector<File> boot_class_path_files_;
  std::vector<File> boot_class_path_image_files_;
  std::vector<File> boot_class_path_vdex_files_;
  std::vector<File> boot_class_path_oat_files_;
  std::string class_path_string_;
  std::vector<std::string> properties_;

  std::list<ti::AgentSpec> agent_specs_;
  std::list<std::unique_ptr<ti::Agent>> agents_;
  std::vector<Plugin> plugins_;

  // The default stack size for managed threads created by the runtime.
  size_t default_stack_size_;

  // Finalizers running for longer than this many milliseconds abort the runtime.
  unsigned int finalizer_timeout_ms_;

  gc::Heap* heap_;

  std::unique_ptr<ArenaPool> jit_arena_pool_;
  std::unique_ptr<ArenaPool> arena_pool_;
  // This pool is used for linear alloc if we are using userfaultfd GC, or if
  // low 4gb pool is required for compiler linear alloc. Otherwise, use
  // arena_pool_.
  // We need ArtFields to be in low 4gb if we are compiling using a 32 bit image
  // on a 64 bit compiler in case we resolve things in the image since the field
  // arrays are int arrays in this case.
  std::unique_ptr<ArenaPool> linear_alloc_arena_pool_;

  // Shared linear alloc for now.
  std::unique_ptr<LinearAlloc> linear_alloc_;

  // Linear alloc used for allocations during startup. Will be deleted after
  // startup. Atomic because the pointer can be concurrently updated to null.
  std::atomic<LinearAlloc*> startup_linear_alloc_;

  // The number of spins that are done before thread suspension is used to forcibly inflate.
  size_t max_spins_before_thin_lock_inflation_;
  MonitorList* monitor_list_;
  MonitorPool* monitor_pool_;

  ThreadList* thread_list_;

  InternTable* intern_table_;

  ClassLinker* class_linker_;

  SignalCatcher* signal_catcher_;

  jni::SmallLrtAllocator* small_lrt_allocator_;

  std::unique_ptr<jni::JniIdManager> jni_id_manager_;

  std::unique_ptr<JavaVMExt> java_vm_;

  std::unique_ptr<jit::Jit> jit_;
  std::unique_ptr<jit::JitCodeCache> jit_code_cache_;
  std::unique_ptr<jit::JitOptions> jit_options_;

  // Runtime thread pool. The pool is only for startup and gets deleted after.
  std::unique_ptr<ThreadPool> thread_pool_ GUARDED_BY(Locks::runtime_thread_pool_lock_);
  size_t thread_pool_ref_count_ GUARDED_BY(Locks::runtime_thread_pool_lock_);

  // Fault message, printed when we get a SIGSEGV. Stored as a native-heap object and accessed
  // lock-free, so needs to be atomic.
  std::atomic<std::string*> fault_message_;

  // A non-zero value indicates that a thread has been created but not yet initialized. Guarded by
  // the shutdown lock so that threads aren't born while we're shutting down.
  size_t threads_being_born_ GUARDED_BY(Locks::runtime_shutdown_lock_);

  // Waited upon until no threads are being born.
  std::unique_ptr<ConditionVariable> shutdown_cond_ GUARDED_BY(Locks::runtime_shutdown_lock_);

  // Set when runtime shutdown is past the point that new threads may attach.  Usually
  // GUARDED_BY(Locks::runtime_shutdown_lock_). But we need to check it in Abort without the
  // lock, because we may already own it.
  std::atomic<bool> shutting_down_;

  // The runtime is starting to shutdown but is blocked waiting on shutdown_cond_.
  bool shutting_down_started_ GUARDED_BY(Locks::runtime_shutdown_lock_);

  bool started_;

  // New flag added which tells us if the runtime has finished starting. If
  // this flag is set then the Daemon threads are created and the class loader
  // is created. This flag is needed for knowing if its safe to request CMS.
  bool finished_starting_;

  // Hooks supported by JNI_CreateJavaVM
  jint (*vfprintf_)(FILE* stream, const char* format, va_list ap);
  void (*exit_)(jint status);
  void (*abort_)();

  bool stats_enabled_;
  RuntimeStats stats_;

  const bool is_running_on_memory_tool_;

  std::unique_ptr<TraceConfig> trace_config_;

  instrumentation::Instrumentation instrumentation_;

  jobject main_thread_group_;
  jobject system_thread_group_;

  // As returned by ClassLoader.getSystemClassLoader().
  jobject system_class_loader_;

  // If true, then we dump the GC cumulative timings on shutdown.
  bool dump_gc_performance_on_shutdown_;

  // Transactions used for pre-initializing classes at compilation time.
  // Support nested transactions, maintain a list containing all transactions. Transactions are
  // handled under a stack discipline. Because GC needs to go over all transactions, we choose list
  // as substantial data structure instead of stack.
  std::forward_list<Transaction> preinitialization_transactions_;

  // If kNone, verification is disabled. kEnable by default.
  verifier::VerifyMode verify_;

  // List of supported cpu abis.
  std::vector<std::string> cpu_abilist_;

  // Specifies target SDK version to allow workarounds for certain API levels.
  uint32_t target_sdk_version_;

  // ART counterpart for the compat framework (go/compat-framework).
  CompatFramework compat_framework_;

  // Implicit checks flags.
  bool implicit_null_checks_;       // NullPointer checks are implicit.
  bool implicit_so_checks_;         // StackOverflow checks are implicit.
  bool implicit_suspend_checks_;    // Thread suspension checks are implicit.

  // Whether or not the sig chain (and implicitly the fault handler) should be
  // disabled. Tools like dex2oat don't need them. This enables
  // building a statically link version of dex2oat.
  bool no_sig_chain_;

  // Force the use of native bridge even if the app ISA matches the runtime ISA.
  bool force_native_bridge_;

  // Whether or not a native bridge has been loaded.
  //
  // The native bridge allows running native code compiled for a foreign ISA. The way it works is,
  // if standard dlopen fails to load native library associated with native activity, it calls to
  // the native bridge to load it and then gets the trampoline for the entry to native activity.
  //
  // The option 'native_bridge_library_filename' specifies the name of the native bridge.
  // When non-empty the native bridge will be loaded from the given file. An empty value means
  // that there's no native bridge.
  bool is_native_bridge_loaded_;

  // Whether we are running under native debugger.
  bool is_native_debuggable_;

  // whether or not any async exceptions have ever been thrown. This is used to speed up the
  // MterpShouldSwitchInterpreters function.
  bool async_exceptions_thrown_;

  // Whether anything is going to be using the shadow-frame APIs to force a function to return
  // early. Doing this requires that (1) we be debuggable and (2) that mterp is exited.
  bool non_standard_exits_enabled_;

  // Whether Java code needs to be debuggable.
  RuntimeDebugState runtime_debug_state_;

  bool monitor_timeout_enable_;
  uint64_t monitor_timeout_ns_;

  // Whether or not this application can be profiled by the shell user,
  // even when running on a device that is running in user mode.
  bool is_profileable_from_shell_ = false;

  // Whether or not this application can be profiled by system services on a
  // device running in user mode, but not necessarily by the shell user.
  bool is_profileable_ = false;

  // The maximum number of failed boots we allow before pruning the dalvik cache
  // and trying again. This option is only inspected when we're running as a
  // zygote.
  uint32_t zygote_max_failed_boots_;

  // Enable experimental opcodes that aren't fully specified yet. The intent is to
  // eventually publish them as public-usable opcodes, but they aren't ready yet.
  //
  // Experimental opcodes should not be used by other production code.
  ExperimentalFlags experimental_flags_;

  // Contains the build fingerprint, if given as a parameter.
  std::string fingerprint_;

  // Oat file manager, keeps track of what oat files are open.
  OatFileManager* oat_file_manager_;

  // Whether or not we are on a low RAM device.
  bool is_low_memory_mode_;

  // Limiting size (in bytes) for applying MADV_WILLNEED on vdex files
  // or uncompressed dex files in APKs.
  // A 0 for this will turn off madvising to MADV_WILLNEED
  size_t madvise_willneed_total_dex_size_;

  // Limiting size (in bytes) for applying MADV_WILLNEED on odex files
  // A 0 for this will turn off madvising to MADV_WILLNEED
  size_t madvise_willneed_odex_filesize_;

  // Limiting size (in bytes) for applying MADV_WILLNEED on art files
  // A 0 for this will turn off madvising to MADV_WILLNEED
  size_t madvise_willneed_art_filesize_;

  // Whether the application should run in safe mode, that is, interpreter only.
  bool safe_mode_;

  // Whether access checks on hidden API should be performed.
  hiddenapi::EnforcementPolicy hidden_api_policy_;

  // Whether access checks on core platform API should be performed.
  hiddenapi::EnforcementPolicy core_platform_api_policy_;

  // Whether access checks on test API should be performed.
  hiddenapi::EnforcementPolicy test_api_policy_;

  // List of signature prefixes of methods that have been removed from the blocklist, and treated
  // as if SDK.
  std::vector<std::string> hidden_api_exemptions_;

  // Do not warn about the same hidden API access violation twice.
  // This is only used for testing.
  bool dedupe_hidden_api_warnings_;

  // How often to log hidden API access to the event log. An integer between 0
  // (never) and 0x10000 (always).
  uint32_t hidden_api_access_event_log_rate_;

  // The package of the app running in this process.
  std::string process_package_name_;

  // The data directory of the app running in this process.
  std::string process_data_directory_;

  // Whether threads should dump their native stack on SIGQUIT.
  bool dump_native_stack_on_sig_quit_;

  // Whether or not we currently care about pause times.
  ProcessState process_state_;

  // Whether zygote code is in a section that should not start threads.
  bool zygote_no_threads_;

  // The string containing requested jdwp options
  std::string jdwp_options_;

  // The jdwp provider we were configured with.
  JdwpProvider jdwp_provider_;

  // True if jmethodID and jfieldID are opaque Indices. When false (the default) these are simply
  // pointers. This is set by -Xopaque-jni-ids:{true,false}.
  JniIdType jni_ids_indirection_;

  // Set to false in cases where we want to directly control when jni-id
  // indirection is changed. This is intended only for testing JNI id swapping.
  bool automatically_set_jni_ids_indirection_;

  // True if files in /data/misc/apexdata/com.android.art are considered untrustworthy.
  bool deny_art_apex_data_files_;

  // Whether to allow compiling the boot classpath in memory when the given boot image is unusable.
  bool allow_in_memory_compilation_ = false;

  // Saved environment.
  class EnvSnapshot {
   public:
    EnvSnapshot() = default;
    void TakeSnapshot();
    char** GetSnapshot() const;

   private:
    std::unique_ptr<char*[]> c_env_vector_;
    std::vector<std::unique_ptr<std::string>> name_value_pairs_;

    DISALLOW_COPY_AND_ASSIGN(EnvSnapshot);
  } env_snapshot_;

  // Generic system-weak holders.
  std::vector<gc::AbstractSystemWeakHolder*> system_weak_holders_;

  std::unique_ptr<RuntimeCallbacks> callbacks_;

  std::atomic<uint32_t> deoptimization_counts_[
      static_cast<uint32_t>(DeoptimizationKind::kLast) + 1];

  MemMap protected_fault_page_;

  uint32_t verifier_logging_threshold_ms_;

  bool load_app_image_startup_cache_ = false;

  // If startup has completed, must happen at most once.
  std::atomic<bool> startup_completed_ = false;

  bool verifier_missing_kthrow_fatal_;
  bool force_java_zygote_fork_loop_;
  bool perfetto_hprof_enabled_;
  bool perfetto_javaheapprof_enabled_;

  // Called on out of memory error
  void (*out_of_memory_error_hook_)();

  metrics::ArtMetrics metrics_;
  std::unique_ptr<metrics::MetricsReporter> metrics_reporter_;

  // Apex versions of boot classpath jars concatenated in a string. The format
  // is of the type:
  // '/apex1_version/apex2_version//'
  //
  // When the apex is the factory version, we don't encode it (for example in
  // the third entry in the example above).
  std::string apex_versions_;

  // The info about the application code paths.
  AppInfo app_info_;

  // Note: See comments on GetFaultMessage.
  friend std::string GetFaultMessageForAbortLogging();
  friend class Dex2oatImageTest;
  friend class ScopedThreadPoolUsage;
  friend class OatFileAssistantTest;
  class SetupLinearAllocForZygoteFork;

  DISALLOW_COPY_AND_ASSIGN(Runtime);
};

inline metrics::ArtMetrics* GetMetrics() { return Runtime::Current()->GetMetrics(); }

}  // namespace art

#endif  // ART_RUNTIME_RUNTIME_H_
