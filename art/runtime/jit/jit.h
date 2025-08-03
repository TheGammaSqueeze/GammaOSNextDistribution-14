/*
 * Copyright 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_JIT_JIT_H_
#define ART_RUNTIME_JIT_JIT_H_

#include <unordered_set>

#include <android-base/unique_fd.h>

#include "base/histogram-inl.h"
#include "base/macros.h"
#include "base/mutex.h"
#include "base/timing_logger.h"
#include "compilation_kind.h"
#include "handle.h"
#include "offsets.h"
#include "interpreter/mterp/nterp.h"
#include "jit/debugger_interface.h"
#include "jit_options.h"
#include "obj_ptr.h"
#include "thread_pool.h"

namespace art HIDDEN {

class ArtMethod;
class ClassLinker;
class DexFile;
class OatDexFile;
class RootVisitor;
struct RuntimeArgumentMap;
union JValue;

namespace mirror {
class Object;
class Class;
class ClassLoader;
class DexCache;
class String;
}   // namespace mirror

namespace jit {

class JitCodeCache;
class JitCompileTask;
class JitMemoryRegion;
class JitOptions;

static constexpr int16_t kJitCheckForOSR = -1;
static constexpr int16_t kJitHotnessDisabled = -2;

// Implemented and provided by the compiler library.
class JitCompilerInterface {
 public:
  virtual ~JitCompilerInterface() {}
  virtual bool CompileMethod(
      Thread* self, JitMemoryRegion* region, ArtMethod* method, CompilationKind compilation_kind)
      REQUIRES_SHARED(Locks::mutator_lock_) = 0;
  virtual void TypesLoaded(mirror::Class**, size_t count)
      REQUIRES_SHARED(Locks::mutator_lock_) = 0;
  virtual bool GenerateDebugInfo() = 0;
  virtual void ParseCompilerOptions() = 0;
  virtual bool IsBaselineCompiler() const = 0;
  virtual void SetDebuggableCompilerOption(bool value) = 0;
  virtual uint32_t GetInlineMaxCodeUnits() const = 0;

  virtual std::vector<uint8_t> PackElfFileForJIT(ArrayRef<const JITCodeEntry*> elf_files,
                                                 ArrayRef<const void*> removed_symbols,
                                                 bool compress,
                                                 /*out*/ size_t* num_symbols) = 0;
};

// Data structure holding information to perform an OSR.
struct OsrData {
  // The native PC to jump to.
  const uint8_t* native_pc;

  // The frame size of the compiled code to jump to.
  size_t frame_size;

  // The dynamically allocated memory of size `frame_size` to copy to stack.
  void* memory[0];

  static constexpr MemberOffset NativePcOffset() {
    return MemberOffset(OFFSETOF_MEMBER(OsrData, native_pc));
  }

  static constexpr MemberOffset FrameSizeOffset() {
    return MemberOffset(OFFSETOF_MEMBER(OsrData, frame_size));
  }

  static constexpr MemberOffset MemoryOffset() {
    return MemberOffset(OFFSETOF_MEMBER(OsrData, memory));
  }
};

/**
 * A customized thread pool for the JIT, to prioritize compilation kinds, and
 * simplify root visiting.
 */
class JitThreadPool : public AbstractThreadPool {
 public:
  static JitThreadPool* Create(const char* name,
                               size_t num_threads,
                               size_t worker_stack_size = ThreadPoolWorker::kDefaultStackSize) {
    JitThreadPool* pool = new JitThreadPool(name, num_threads, worker_stack_size);
    pool->CreateThreads();
    return pool;
  }

  // Add a task to the generic queue. This is for tasks like
  // ZygoteVerificationTask, or JitCompileTask for precompile.
  void AddTask(Thread* self, Task* task) REQUIRES(!task_queue_lock_) override;
  size_t GetTaskCount(Thread* self) REQUIRES(!task_queue_lock_) override;
  void RemoveAllTasks(Thread* self) REQUIRES(!task_queue_lock_) override;
  ~JitThreadPool() override;

  // Remove the task from the list of compiling tasks.
  void Remove(JitCompileTask* task) REQUIRES(!task_queue_lock_);

  // Add a custom compilation task in the right queue.
  void AddTask(Thread* self, ArtMethod* method, CompilationKind kind) REQUIRES(!task_queue_lock_);

  // Visit the ArtMethods stored in the various queues.
  void VisitRoots(RootVisitor* visitor);

 protected:
  Task* TryGetTaskLocked() REQUIRES(task_queue_lock_) override;

  bool HasOutstandingTasks() const REQUIRES(task_queue_lock_) override {
    return started_ &&
        (!generic_queue_.empty() ||
         !baseline_queue_.empty() ||
         !optimized_queue_.empty() ||
         !osr_queue_.empty());
  }

 private:
  JitThreadPool(const char* name,
                size_t num_threads,
                size_t worker_stack_size)
      // We need peers as we may report the JIT thread, e.g., in the debugger.
      : AbstractThreadPool(name, num_threads, /* create_peers= */ true, worker_stack_size) {}

  // Try to fetch an entry from `methods`. Return null if `methods` is empty.
  Task* FetchFrom(std::deque<ArtMethod*>& methods, CompilationKind kind) REQUIRES(task_queue_lock_);

  std::deque<Task*> generic_queue_ GUARDED_BY(task_queue_lock_);

  std::deque<ArtMethod*> osr_queue_ GUARDED_BY(task_queue_lock_);
  std::deque<ArtMethod*> baseline_queue_ GUARDED_BY(task_queue_lock_);
  std::deque<ArtMethod*> optimized_queue_ GUARDED_BY(task_queue_lock_);

  // We track the methods that are currently enqueued to avoid
  // adding them to the queue multiple times, which could bloat the
  // queues.
  std::set<ArtMethod*> osr_enqueued_methods_ GUARDED_BY(task_queue_lock_);
  std::set<ArtMethod*> baseline_enqueued_methods_ GUARDED_BY(task_queue_lock_);
  std::set<ArtMethod*> optimized_enqueued_methods_ GUARDED_BY(task_queue_lock_);

  // A set to keep track of methods that are currently being compiled. Entries
  // will be removed when JitCompileTask->Finalize is called.
  std::unordered_set<JitCompileTask*> current_compilations_ GUARDED_BY(task_queue_lock_);

  DISALLOW_COPY_AND_ASSIGN(JitThreadPool);
};

class Jit {
 public:
  // How frequently should the interpreter check to see if OSR compilation is ready.
  static constexpr int16_t kJitRecheckOSRThreshold = 101;  // Prime number to avoid patterns.

  virtual ~Jit();

  // Create JIT itself.
  static std::unique_ptr<Jit> Create(JitCodeCache* code_cache, JitOptions* options);

  EXPORT bool CompileMethod(ArtMethod* method,
                            Thread* self,
                            CompilationKind compilation_kind,
                            bool prejit) REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitRoots(RootVisitor* visitor);

  const JitCodeCache* GetCodeCache() const {
    return code_cache_;
  }

  JitCodeCache* GetCodeCache() {
    return code_cache_;
  }

  JitCompilerInterface* GetJitCompiler() const {
    return jit_compiler_;
  }

  void CreateThreadPool();
  void DeleteThreadPool();
  void WaitForWorkersToBeCreated();

  // Dump interesting info: #methods compiled, code vs data size, compile / verify cumulative
  // loggers.
  void DumpInfo(std::ostream& os) REQUIRES(!lock_);
  // Add a timing logger to cumulative_timings_.
  void AddTimingLogger(const TimingLogger& logger);

  void AddMemoryUsage(ArtMethod* method, size_t bytes)
      REQUIRES(!lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  int GetThreadPoolPthreadPriority() const {
    return options_->GetThreadPoolPthreadPriority();
  }

  int GetZygoteThreadPoolPthreadPriority() const {
    return options_->GetZygoteThreadPoolPthreadPriority();
  }

  uint16_t HotMethodThreshold() const {
    return options_->GetOptimizeThreshold();
  }

  uint16_t WarmMethodThreshold() const {
    return options_->GetWarmupThreshold();
  }

  uint16_t PriorityThreadWeight() const {
    return options_->GetPriorityThreadWeight();
  }

  // Return whether we should do JIT compilation. Note this will returns false
  // if we only need to save profile information and not compile methods.
  bool UseJitCompilation() const {
    return options_->UseJitCompilation();
  }

  bool GetSaveProfilingInfo() const {
    return options_->GetSaveProfilingInfo();
  }

  // Wait until there is no more pending compilation tasks.
  EXPORT void WaitForCompilationToFinish(Thread* self);

  // Profiling methods.
  void MethodEntered(Thread* thread, ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE void AddSamples(Thread* self, ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void NotifyInterpreterToCompiledCodeTransition(Thread* self, ArtMethod* caller)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    AddSamples(self, caller);
  }

  void NotifyCompiledCodeToInterpreterTransition(Thread* self, ArtMethod* callee)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    AddSamples(self, callee);
  }

  // Starts the profile saver if the config options allow profile recording.
  // The profile will be stored in the specified `profile_filename` and will contain
  // information collected from the given `code_paths` (a set of dex locations).
  //
  // The `ref_profile_filename` denotes the path to the reference profile which
  // might be queried to determine if an initial save should be done earlier.
  // It can be empty indicating there is no reference profile.
  void StartProfileSaver(const std::string& profile_filename,
                         const std::vector<std::string>& code_paths,
                         const std::string& ref_profile_filename);
  void StopProfileSaver();

  void DumpForSigQuit(std::ostream& os) REQUIRES(!lock_);

  static void NewTypeLoadedIfUsingJit(mirror::Class* type)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // If debug info generation is turned on then write the type information for types already loaded
  // into the specified class linker to the jit debug interface,
  void DumpTypeInfoForLoadedTypes(ClassLinker* linker);

  // Return whether we should try to JIT compiled code as soon as an ArtMethod is invoked.
  EXPORT bool JitAtFirstUse();

  // Return whether we can invoke JIT code for `method`.
  bool CanInvokeCompiledCode(ArtMethod* method);

  // Return the information required to do an OSR jump. Return null if the OSR
  // cannot be done.
  OsrData* PrepareForOsr(ArtMethod* method, uint32_t dex_pc, uint32_t* vregs)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // If an OSR compiled version is available for `method`,
  // and `dex_pc + dex_pc_offset` is an entry point of that compiled
  // version, this method will jump to the compiled code, let it run,
  // and return true afterwards. Return false otherwise.
  static bool MaybeDoOnStackReplacement(Thread* thread,
                                        ArtMethod* method,
                                        uint32_t dex_pc,
                                        int32_t dex_pc_offset,
                                        JValue* result)
      REQUIRES_SHARED(Locks::mutator_lock_);

  JitThreadPool* GetThreadPool() const {
    return thread_pool_.get();
  }

  // Stop the JIT by waiting for all current compilations and enqueued compilations to finish.
  EXPORT void Stop();

  // Start JIT threads.
  EXPORT void Start();

  // Transition to a child state.
  EXPORT void PostForkChildAction(bool is_system_server, bool is_zygote);

  // Prepare for forking.
  EXPORT void PreZygoteFork();

  // Adjust state after forking.
  void PostZygoteFork();

  // Add a task to the queue, ensuring it runs after boot is finished.
  void AddPostBootTask(Thread* self, Task* task);

  // Called when system finishes booting.
  void BootCompleted();

  // Are we in a zygote using JIT compilation?
  static bool InZygoteUsingJit();

  // Compile methods from the given profile (.prof extension). If `add_to_queue`
  // is true, methods in the profile are added to the JIT queue. Otherwise they are compiled
  // directly.
  // Return the number of methods added to the queue.
  uint32_t CompileMethodsFromProfile(Thread* self,
                                     const std::vector<const DexFile*>& dex_files,
                                     const std::string& profile_path,
                                     Handle<mirror::ClassLoader> class_loader,
                                     bool add_to_queue);

  // Compile methods from the given boot profile (.bprof extension). If `add_to_queue`
  // is true, methods in the profile are added to the JIT queue. Otherwise they are compiled
  // directly.
  // Return the number of methods added to the queue.
  uint32_t CompileMethodsFromBootProfile(Thread* self,
                                         const std::vector<const DexFile*>& dex_files,
                                         const std::string& profile_path,
                                         Handle<mirror::ClassLoader> class_loader,
                                         bool add_to_queue);

  // Register the dex files to the JIT. This is to perform any compilation/optimization
  // at the point of loading the dex files.
  void RegisterDexFiles(const std::vector<std::unique_ptr<const DexFile>>& dex_files,
                        jobject class_loader);

  // Called by the compiler to know whether it can directly encode the
  // method/class/string.
  bool CanEncodeMethod(ArtMethod* method, bool is_for_shared_region) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  bool CanEncodeClass(ObjPtr<mirror::Class> cls, bool is_for_shared_region) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  bool CanEncodeString(ObjPtr<mirror::String> string, bool is_for_shared_region) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  bool CanAssumeInitialized(ObjPtr<mirror::Class> cls, bool is_for_shared_region) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Map boot image methods after all compilation in zygote has been done.
  void MapBootImageMethods() REQUIRES(Locks::mutator_lock_);

  // Notify to other processes that the zygote is done profile compiling boot
  // class path methods.
  void NotifyZygoteCompilationDone();

  EXPORT void EnqueueOptimizedCompilation(ArtMethod* method, Thread* self);

  EXPORT void MaybeEnqueueCompilation(ArtMethod* method, Thread* self)
      REQUIRES_SHARED(Locks::mutator_lock_);

  EXPORT static bool TryPatternMatch(ArtMethod* method, CompilationKind compilation_kind)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  Jit(JitCodeCache* code_cache, JitOptions* options);

  // Whether we should not add hotness counts for the given method.
  bool IgnoreSamplesForMethod(ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Compile an individual method listed in a profile. If `add_to_queue` is
  // true and the method was resolved, return true. Otherwise return false.
  bool CompileMethodFromProfile(Thread* self,
                                ClassLinker* linker,
                                uint32_t method_idx,
                                Handle<mirror::DexCache> dex_cache,
                                Handle<mirror::ClassLoader> class_loader,
                                bool add_to_queue,
                                bool compile_after_boot)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static bool BindCompilerMethods(std::string* error_msg);

  void AddCompileTask(Thread* self,
                      ArtMethod* method,
                      CompilationKind compilation_kind);

  bool CompileMethodInternal(ArtMethod* method,
                             Thread* self,
                             CompilationKind compilation_kind,
                             bool prejit)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // JIT compiler
  EXPORT static JitCompilerInterface* jit_compiler_;

  // JIT resources owned by runtime.
  jit::JitCodeCache* const code_cache_;
  const JitOptions* const options_;

  std::unique_ptr<JitThreadPool> thread_pool_;
  std::vector<std::unique_ptr<OatDexFile>> type_lookup_tables_;

  Mutex boot_completed_lock_;
  bool boot_completed_ GUARDED_BY(boot_completed_lock_) = false;
  std::deque<Task*> tasks_after_boot_ GUARDED_BY(boot_completed_lock_);

  // Performance monitoring.
  CumulativeLogger cumulative_timings_;
  Histogram<uint64_t> memory_use_ GUARDED_BY(lock_);
  Mutex lock_ DEFAULT_MUTEX_ACQUIRED_AFTER;

  // In the JIT zygote configuration, after all compilation is done, the zygote
  // will copy its contents of the boot image to the zygote_mapping_methods_,
  // which will be picked up by processes that will map the memory
  // in-place within the boot image mapping.
  //
  // zygote_mapping_methods_ is shared memory only usable by the zygote and not
  // inherited by child processes. We create it eagerly to ensure other
  // processes cannot seal writable the file.
  MemMap zygote_mapping_methods_;

  // The file descriptor created through memfd_create pointing to memory holding
  // boot image methods. Created by the zygote, and inherited by child
  // processes. The descriptor will be closed in each process (including the
  // zygote) once they don't need it.
  android::base::unique_fd fd_methods_;

  // The size of the memory pointed by `fd_methods_`. Cached here to avoid
  // recomputing it.
  size_t fd_methods_size_;

  // Map of hotness counters for methods which we want to share the memory
  // between the zygote and apps.
  std::map<ArtMethod*, uint16_t> shared_method_counters_;

  friend class art::jit::JitCompileTask;

  DISALLOW_COPY_AND_ASSIGN(Jit);
};

// Helper class to stop the JIT for a given scope. This will wait for the JIT to quiesce.
class EXPORT ScopedJitSuspend {
 public:
  ScopedJitSuspend();
  ~ScopedJitSuspend();

 private:
  bool was_on_;
};

}  // namespace jit
}  // namespace art

#endif  // ART_RUNTIME_JIT_JIT_H_
