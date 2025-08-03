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

#include "jit.h"

#include <dlfcn.h>

#include "art_method-inl.h"
#include "base/enums.h"
#include "base/file_utils.h"
#include "base/logging.h"  // For VLOG.
#include "base/memfd.h"
#include "base/memory_tool.h"
#include "base/runtime_debug.h"
#include "base/scoped_flock.h"
#include "base/utils.h"
#include "class_root-inl.h"
#include "compilation_kind.h"
#include "debugger.h"
#include "dex/type_lookup_table.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "gc/space/image_space.h"
#include "interpreter/interpreter.h"
#include "jit-inl.h"
#include "jit_code_cache.h"
#include "jit_create.h"
#include "jni/java_vm_ext.h"
#include "mirror/method_handle_impl.h"
#include "mirror/var_handle.h"
#include "oat/image-inl.h"
#include "oat/oat_file.h"
#include "oat/oat_file_manager.h"
#include "oat/oat_quick_method_header.h"
#include "oat/stack_map.h"
#include "profile/profile_boot_info.h"
#include "profile/profile_compilation_info.h"
#include "profile_saver.h"
#include "runtime.h"
#include "runtime_options.h"
#include "small_pattern_matcher.h"
#include "stack.h"
#include "thread-inl.h"
#include "thread_list.h"

using android::base::unique_fd;

namespace art HIDDEN {
namespace jit {

static constexpr bool kEnableOnStackReplacement = true;

// JIT compiler
JitCompilerInterface* Jit::jit_compiler_ = nullptr;

void Jit::DumpInfo(std::ostream& os) {
  code_cache_->Dump(os);
  cumulative_timings_.Dump(os);
  MutexLock mu(Thread::Current(), lock_);
  memory_use_.PrintMemoryUse(os);
}

void Jit::DumpForSigQuit(std::ostream& os) {
  DumpInfo(os);
  ProfileSaver::DumpInstanceInfo(os);
}

void Jit::AddTimingLogger(const TimingLogger& logger) {
  cumulative_timings_.AddLogger(logger);
}

Jit::Jit(JitCodeCache* code_cache, JitOptions* options)
    : code_cache_(code_cache),
      options_(options),
      boot_completed_lock_("Jit::boot_completed_lock_"),
      cumulative_timings_("JIT timings"),
      memory_use_("Memory used for compilation", 16),
      lock_("JIT memory use lock"),
      zygote_mapping_methods_(),
      fd_methods_(-1),
      fd_methods_size_(0) {}

std::unique_ptr<Jit> Jit::Create(JitCodeCache* code_cache, JitOptions* options) {
  jit_compiler_ = jit_create();
  std::unique_ptr<Jit> jit(new Jit(code_cache, options));

  // If the code collector is enabled, check if that still holds:
  // With 'perf', we want a 1-1 mapping between an address and a method.
  // We aren't able to keep method pointers live during the instrumentation method entry trampoline
  // so we will just disable jit-gc if we are doing that.
  // JitAtFirstUse compiles the methods synchronously on mutator threads. While this should work
  // in theory it is causing deadlocks in some jvmti tests related to Jit GC. Hence, disabling
  // Jit GC for now (b/147208992).
  if (code_cache->GetGarbageCollectCode()) {
    code_cache->SetGarbageCollectCode(!jit_compiler_->GenerateDebugInfo() &&
        !jit->JitAtFirstUse());
  }

  VLOG(jit) << "JIT created with initial_capacity="
      << PrettySize(options->GetCodeCacheInitialCapacity())
      << ", max_capacity=" << PrettySize(options->GetCodeCacheMaxCapacity())
      << ", warmup_threshold=" << options->GetWarmupThreshold()
      << ", optimize_threshold=" << options->GetOptimizeThreshold()
      << ", profile_saver_options=" << options->GetProfileSaverOptions();

  // We want to know whether the compiler is compiling baseline, as this
  // affects how we GC ProfilingInfos.
  for (const std::string& option : Runtime::Current()->GetCompilerOptions()) {
    if (option == "--baseline") {
      options->SetUseBaselineCompiler();
      break;
    }
  }

  // Notify native debugger about the classes already loaded before the creation of the jit.
  jit->DumpTypeInfoForLoadedTypes(Runtime::Current()->GetClassLinker());

  return jit;
}


bool Jit::TryPatternMatch(ArtMethod* method_to_compile, CompilationKind compilation_kind) {
  // Try to pattern match the method. Only on arm and arm64 for now as we have
  // sufficiently similar calling convention between C++ and managed code.
  if (kRuntimeISA == InstructionSet::kArm || kRuntimeISA == InstructionSet::kArm64) {
    if (!Runtime::Current()->IsJavaDebuggable() &&
        compilation_kind == CompilationKind::kBaseline &&
        !method_to_compile->StillNeedsClinitCheck()) {
      const void* pattern = SmallPatternMatcher::TryMatch(method_to_compile);
      if (pattern != nullptr) {
        VLOG(jit) << "Successfully pattern matched " << method_to_compile->PrettyMethod();
        Runtime::Current()->GetInstrumentation()->UpdateMethodsCode(method_to_compile, pattern);
        return true;
      }
    }
  }
  return false;
}

bool Jit::CompileMethodInternal(ArtMethod* method,
                                Thread* self,
                                CompilationKind compilation_kind,
                                bool prejit) {
  DCHECK(Runtime::Current()->UseJitCompilation());
  DCHECK(!method->IsRuntimeMethod());

  // If the baseline flag was explicitly passed in the compiler options, change the compilation kind
  // from optimized to baseline.
  if (jit_compiler_->IsBaselineCompiler() && compilation_kind == CompilationKind::kOptimized) {
    compilation_kind = CompilationKind::kBaseline;
  }

  if (method->IsPreCompiled() && !prejit) {
    VLOG(jit) << "JIT not compiling " << method->PrettyMethod()
              << " due to method marked pre-compile,"
              << " and the compilation request isn't for pre-compilation.";
    return false;
  }

  // If we're asked to compile baseline, but we cannot allocate profiling infos,
  // change the compilation kind to optimized.
  if ((compilation_kind == CompilationKind::kBaseline) &&
      !GetCodeCache()->CanAllocateProfilingInfo()) {
    compilation_kind = CompilationKind::kOptimized;
  }

  // Don't compile the method if it has breakpoints.
  if (Runtime::Current()->GetInstrumentation()->IsDeoptimized(method)) {
    VLOG(jit) << "JIT not compiling " << method->PrettyMethod()
              << " due to not being safe to jit according to runtime-callbacks. For example, there"
              << " could be breakpoints in this method.";
    return false;
  }

  if (!method->IsCompilable()) {
    DCHECK(method->GetDeclaringClass()->IsObsoleteObject() ||
           method->IsProxyMethod()) << method->PrettyMethod();
    VLOG(jit) << "JIT not compiling " << method->PrettyMethod() << " due to method being made "
              << "obsolete while waiting for JIT task to run. This probably happened due to "
              << "concurrent structural class redefinition.";
    return false;
  }

  // Don't compile the method if we are supposed to be deoptimized.
  instrumentation::Instrumentation* instrumentation = Runtime::Current()->GetInstrumentation();
  if (instrumentation->AreAllMethodsDeoptimized() || instrumentation->IsDeoptimized(method)) {
    VLOG(jit) << "JIT not compiling " << method->PrettyMethod() << " due to deoptimization";
    return false;
  }

  JitMemoryRegion* region = GetCodeCache()->GetCurrentRegion();
  if ((compilation_kind == CompilationKind::kOsr) && GetCodeCache()->IsSharedRegion(*region)) {
    VLOG(jit) << "JIT not osr compiling "
              << method->PrettyMethod()
              << " due to using shared region";
    return false;
  }

  // If we get a request to compile a proxy method, we pass the actual Java method
  // of that proxy method, as the compiler does not expect a proxy method.
  ArtMethod* method_to_compile = method->GetInterfaceMethodIfProxy(kRuntimePointerSize);

  if (TryPatternMatch(method_to_compile, compilation_kind)) {
    return true;
  }

  if (!code_cache_->NotifyCompilationOf(method_to_compile, self, compilation_kind, prejit)) {
    return false;
  }

  VLOG(jit) << "Compiling method "
            << ArtMethod::PrettyMethod(method_to_compile)
            << " kind=" << compilation_kind;
  bool success = jit_compiler_->CompileMethod(self, region, method_to_compile, compilation_kind);
  code_cache_->DoneCompiling(method_to_compile, self);
  if (!success) {
    VLOG(jit) << "Failed to compile method "
              << ArtMethod::PrettyMethod(method_to_compile)
              << " kind=" << compilation_kind;
  }
  if (kIsDebugBuild) {
    if (self->IsExceptionPending()) {
      mirror::Throwable* exception = self->GetException();
      LOG(FATAL) << "No pending exception expected after compiling "
                 << ArtMethod::PrettyMethod(method)
                 << ": "
                 << exception->Dump();
    }
  }
  return success;
}

void Jit::WaitForWorkersToBeCreated() {
  if (thread_pool_ != nullptr) {
    thread_pool_->WaitForWorkersToBeCreated();
  }
}

void Jit::DeleteThreadPool() {
  Thread* self = Thread::Current();
  if (thread_pool_ != nullptr) {
    std::unique_ptr<JitThreadPool> pool;
    {
      ScopedSuspendAll ssa(__FUNCTION__);
      // Clear thread_pool_ field while the threads are suspended.
      // A mutator in the 'AddSamples' method will check against it.
      pool = std::move(thread_pool_);
    }

    // When running sanitized, let all tasks finish to not leak. Otherwise just clear the queue.
    if (!kRunningOnMemoryTool) {
      pool->StopWorkers(self);
      pool->RemoveAllTasks(self);
    }
    // We could just suspend all threads, but we know those threads
    // will finish in a short period, so it's not worth adding a suspend logic
    // here. Besides, this is only done for shutdown.
    pool->Wait(self, false, false);
  }
}

void Jit::StartProfileSaver(const std::string& profile_filename,
                            const std::vector<std::string>& code_paths,
                            const std::string& ref_profile_filename) {
  if (options_->GetSaveProfilingInfo()) {
    ProfileSaver::Start(options_->GetProfileSaverOptions(),
                        profile_filename,
                        code_cache_,
                        code_paths,
                        ref_profile_filename);
  }
}

void Jit::StopProfileSaver() {
  if (options_->GetSaveProfilingInfo() && ProfileSaver::IsStarted()) {
    ProfileSaver::Stop(options_->DumpJitInfoOnShutdown());
  }
}

bool Jit::JitAtFirstUse() {
  return HotMethodThreshold() == 0;
}

bool Jit::CanInvokeCompiledCode(ArtMethod* method) {
  return code_cache_->ContainsPc(method->GetEntryPointFromQuickCompiledCode());
}

Jit::~Jit() {
  DCHECK_IMPLIES(options_->GetSaveProfilingInfo(), !ProfileSaver::IsStarted());
  if (options_->DumpJitInfoOnShutdown()) {
    DumpInfo(LOG_STREAM(INFO));
    Runtime::Current()->DumpDeoptimizations(LOG_STREAM(INFO));
  }
  DeleteThreadPool();
  if (jit_compiler_ != nullptr) {
    delete jit_compiler_;
    jit_compiler_ = nullptr;
  }
}

void Jit::NewTypeLoadedIfUsingJit(mirror::Class* type) {
  if (!Runtime::Current()->UseJitCompilation()) {
    // No need to notify if we only use the JIT to save profiles.
    return;
  }
  jit::Jit* jit = Runtime::Current()->GetJit();
  if (jit->jit_compiler_->GenerateDebugInfo()) {
    jit_compiler_->TypesLoaded(&type, 1);
  }
}

void Jit::DumpTypeInfoForLoadedTypes(ClassLinker* linker) {
  struct CollectClasses : public ClassVisitor {
    bool operator()(ObjPtr<mirror::Class> klass) override REQUIRES_SHARED(Locks::mutator_lock_) {
      classes_.push_back(klass.Ptr());
      return true;
    }
    std::vector<mirror::Class*> classes_;
  };

  if (jit_compiler_->GenerateDebugInfo()) {
    ScopedObjectAccess so(Thread::Current());

    CollectClasses visitor;
    linker->VisitClasses(&visitor);
    jit_compiler_->TypesLoaded(visitor.classes_.data(), visitor.classes_.size());
  }
}

extern "C" void art_quick_osr_stub(void** stack,
                                   size_t stack_size_in_bytes,
                                   const uint8_t* native_pc,
                                   JValue* result,
                                   const char* shorty,
                                   Thread* self);

OsrData* Jit::PrepareForOsr(ArtMethod* method, uint32_t dex_pc, uint32_t* vregs) {
  if (!kEnableOnStackReplacement) {
    return nullptr;
  }

  // Cheap check if the method has been compiled already. That's an indicator that we should
  // osr into it.
  if (!GetCodeCache()->ContainsPc(method->GetEntryPointFromQuickCompiledCode())) {
    return nullptr;
  }

  // Fetch some data before looking up for an OSR method. We don't want thread
  // suspension once we hold an OSR method, as the JIT code cache could delete the OSR
  // method while we are being suspended.
  CodeItemDataAccessor accessor(method->DexInstructionData());
  const size_t number_of_vregs = accessor.RegistersSize();
  std::string method_name(VLOG_IS_ON(jit) ? method->PrettyMethod() : "");
  OsrData* osr_data = nullptr;

  {
    ScopedAssertNoThreadSuspension sts("Holding OSR method");
    const OatQuickMethodHeader* osr_method = GetCodeCache()->LookupOsrMethodHeader(method);
    if (osr_method == nullptr) {
      // No osr method yet, just return to the interpreter.
      return nullptr;
    }

    CodeInfo code_info(osr_method);

    // Find stack map starting at the target dex_pc.
    StackMap stack_map = code_info.GetOsrStackMapForDexPc(dex_pc);
    if (!stack_map.IsValid()) {
      // There is no OSR stack map for this dex pc offset. Just return to the interpreter in the
      // hope that the next branch has one.
      return nullptr;
    }

    // We found a stack map, now fill the frame with dex register values from the interpreter's
    // shadow frame.
    DexRegisterMap vreg_map = code_info.GetDexRegisterMapOf(stack_map);
    DCHECK_EQ(vreg_map.size(), number_of_vregs);

    size_t frame_size = osr_method->GetFrameSizeInBytes();

    // Allocate memory to put shadow frame values. The osr stub will copy that memory to
    // stack.
    // Note that we could pass the shadow frame to the stub, and let it copy the values there,
    // but that is engineering complexity not worth the effort for something like OSR.
    osr_data = reinterpret_cast<OsrData*>(malloc(sizeof(OsrData) + frame_size));
    if (osr_data == nullptr) {
      return nullptr;
    }
    memset(osr_data, 0, sizeof(OsrData) + frame_size);
    osr_data->frame_size = frame_size;

    // Art ABI: ArtMethod is at the bottom of the stack.
    osr_data->memory[0] = method;

    if (vreg_map.empty()) {
      // If we don't have a dex register map, then there are no live dex registers at
      // this dex pc.
    } else {
      for (uint16_t vreg = 0; vreg < number_of_vregs; ++vreg) {
        DexRegisterLocation::Kind location = vreg_map[vreg].GetKind();
        if (location == DexRegisterLocation::Kind::kNone) {
          // Dex register is dead or uninitialized.
          continue;
        }

        if (location == DexRegisterLocation::Kind::kConstant) {
          // We skip constants because the compiled code knows how to handle them.
          continue;
        }

        DCHECK_EQ(location, DexRegisterLocation::Kind::kInStack);

        int32_t vreg_value = vregs[vreg];
        int32_t slot_offset = vreg_map[vreg].GetStackOffsetInBytes();
        DCHECK_LT(slot_offset, static_cast<int32_t>(frame_size));
        DCHECK_GT(slot_offset, 0);
        (reinterpret_cast<int32_t*>(osr_data->memory))[slot_offset / sizeof(int32_t)] = vreg_value;
      }
    }

    osr_data->native_pc = stack_map.GetNativePcOffset(kRuntimeISA) +
        osr_method->GetEntryPoint();
    VLOG(jit) << "Jumping to "
              << method_name
              << "@"
              << std::hex << reinterpret_cast<uintptr_t>(osr_data->native_pc);
  }
  return osr_data;
}

bool Jit::MaybeDoOnStackReplacement(Thread* thread,
                                    ArtMethod* method,
                                    uint32_t dex_pc,
                                    int32_t dex_pc_offset,
                                    JValue* result) {
  Jit* jit = Runtime::Current()->GetJit();
  if (jit == nullptr) {
    return false;
  }

  if (UNLIKELY(__builtin_frame_address(0) < thread->GetStackEnd())) {
    // Don't attempt to do an OSR if we are close to the stack limit. Since
    // the interpreter frames are still on stack, OSR has the potential
    // to stack overflow even for a simple loop.
    // b/27094810.
    return false;
  }

  // Get the actual Java method if this method is from a proxy class. The compiler
  // and the JIT code cache do not expect methods from proxy classes.
  method = method->GetInterfaceMethodIfProxy(kRuntimePointerSize);

  // Before allowing the jump, make sure no code is actively inspecting the method to avoid
  // jumping from interpreter to OSR while e.g. single stepping. Note that we could selectively
  // disable OSR when single stepping, but that's currently hard to know at this point.
  // Currently, HaveLocalsChanged is not frame specific. It is possible to make it frame specific
  // to allow OSR of frames that don't have any locals changed but it isn't worth the additional
  // complexity.
  if (Runtime::Current()->GetInstrumentation()->NeedsSlowInterpreterForMethod(thread, method) ||
      Runtime::Current()->GetRuntimeCallbacks()->HaveLocalsChanged()) {
    return false;
  }

  ShadowFrame* shadow_frame = thread->GetManagedStack()->GetTopShadowFrame();
  OsrData* osr_data = jit->PrepareForOsr(method,
                                         dex_pc + dex_pc_offset,
                                         shadow_frame->GetVRegArgs(0));

  if (osr_data == nullptr) {
    return false;
  }

  {
    thread->PopShadowFrame();
    ManagedStack fragment;
    thread->PushManagedStackFragment(&fragment);
    (*art_quick_osr_stub)(osr_data->memory,
                          osr_data->frame_size,
                          osr_data->native_pc,
                          result,
                          method->GetShorty(),
                          thread);

    if (UNLIKELY(thread->GetException() == Thread::GetDeoptimizationException())) {
      thread->DeoptimizeWithDeoptimizationException(result);
    }
    thread->PopManagedStackFragment(fragment);
  }
  free(osr_data);
  thread->PushShadowFrame(shadow_frame);
  VLOG(jit) << "Done running OSR code for " << method->PrettyMethod();
  return true;
}

void Jit::AddMemoryUsage(ArtMethod* method, size_t bytes) {
  if (bytes > 4 * MB) {
    LOG(INFO) << "Compiler allocated "
              << PrettySize(bytes)
              << " to compile "
              << ArtMethod::PrettyMethod(method);
  }
  MutexLock mu(Thread::Current(), lock_);
  memory_use_.AddValue(bytes);
}

void Jit::NotifyZygoteCompilationDone() {
  if (fd_methods_ == -1) {
    return;
  }

  size_t offset = 0;
  for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
    const ImageHeader& header = space->GetImageHeader();
    const ImageSection& section = header.GetMethodsSection();
    // Because mremap works at page boundaries, we can only handle methods
    // within a page range. For methods that falls above or below the range,
    // the child processes will copy their contents to their private mapping
    // in `child_mapping_methods`. See `MapBootImageMethods`.
    uint8_t* page_start = AlignUp(header.GetImageBegin() + section.Offset(), gPageSize);
    uint8_t* page_end =
        AlignDown(header.GetImageBegin() + section.Offset() + section.Size(), gPageSize);
    if (page_end > page_start) {
      uint64_t capacity = page_end - page_start;
      memcpy(zygote_mapping_methods_.Begin() + offset, page_start, capacity);
      offset += capacity;
    }
  }

  // Do an msync to ensure we are not affected by writes still being in caches.
  if (msync(zygote_mapping_methods_.Begin(), fd_methods_size_, MS_SYNC) != 0) {
    PLOG(WARNING) << "Failed to sync boot image methods memory";
    code_cache_->GetZygoteMap()->SetCompilationState(ZygoteCompilationState::kNotifiedFailure);
    return;
  }

  // We don't need the shared mapping anymore, and we need to drop it in case
  // the file hasn't been sealed writable.
  zygote_mapping_methods_ = MemMap::Invalid();

  // Seal writes now. Zygote and children will map the memory private in order
  // to write to it.
  if (fcntl(fd_methods_, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_WRITE) == -1) {
    PLOG(WARNING) << "Failed to seal boot image methods file descriptor";
    code_cache_->GetZygoteMap()->SetCompilationState(ZygoteCompilationState::kNotifiedFailure);
    return;
  }

  std::string error_str;
  MemMap child_mapping_methods = MemMap::MapFile(
      fd_methods_size_,
      PROT_READ | PROT_WRITE,
      MAP_PRIVATE,
      fd_methods_,
      /* start= */ 0,
      /* low_4gb= */ false,
      "boot-image-methods",
      &error_str);

  if (!child_mapping_methods.IsValid()) {
    LOG(WARNING) << "Failed to create child mapping of boot image methods: " << error_str;
    code_cache_->GetZygoteMap()->SetCompilationState(ZygoteCompilationState::kNotifiedFailure);
    return;
  }

  // Ensure the contents are the same as before: there was a window between
  // the memcpy and the sealing where other processes could have changed the
  // contents.
  // Note this would not be needed if we could have used F_SEAL_FUTURE_WRITE,
  // see b/143833776.
  offset = 0;
  for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
    const ImageHeader& header = space->GetImageHeader();
    const ImageSection& section = header.GetMethodsSection();
    // Because mremap works at page boundaries, we can only handle methods
    // within a page range. For methods that falls above or below the range,
    // the child processes will copy their contents to their private mapping
    // in `child_mapping_methods`. See `MapBootImageMethods`.
    uint8_t* page_start = AlignUp(header.GetImageBegin() + section.Offset(), gPageSize);
    uint8_t* page_end =
        AlignDown(header.GetImageBegin() + section.Offset() + section.Size(), gPageSize);
    if (page_end > page_start) {
      uint64_t capacity = page_end - page_start;
      if (memcmp(child_mapping_methods.Begin() + offset, page_start, capacity) != 0) {
        LOG(WARNING) << "Contents differ in boot image methods data";
        code_cache_->GetZygoteMap()->SetCompilationState(
            ZygoteCompilationState::kNotifiedFailure);
        return;
      }
      offset += capacity;
    }
  }

  // Future spawned processes don't need the fd anymore.
  fd_methods_.reset();

  // In order to have the zygote and children share the memory, we also remap
  // the memory into the zygote process.
  offset = 0;
  for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
    const ImageHeader& header = space->GetImageHeader();
    const ImageSection& section = header.GetMethodsSection();
    // Because mremap works at page boundaries, we can only handle methods
    // within a page range. For methods that falls above or below the range,
    // the child processes will copy their contents to their private mapping
    // in `child_mapping_methods`. See `MapBootImageMethods`.
    uint8_t* page_start = AlignUp(header.GetImageBegin() + section.Offset(), gPageSize);
    uint8_t* page_end =
        AlignDown(header.GetImageBegin() + section.Offset() + section.Size(), gPageSize);
    if (page_end > page_start) {
      uint64_t capacity = page_end - page_start;
      if (mremap(child_mapping_methods.Begin() + offset,
                 capacity,
                 capacity,
                 MREMAP_FIXED | MREMAP_MAYMOVE,
                 page_start) == MAP_FAILED) {
        // Failing to remap is safe as the process will just use the old
        // contents.
        PLOG(WARNING) << "Failed mremap of boot image methods of " << space->GetImageFilename();
      }
      offset += capacity;
    }
  }

  LOG(INFO) << "Successfully notified child processes on sharing boot image methods";

  // Mark that compilation of boot classpath is done, and memory can now be
  // shared. Other processes will pick up this information.
  code_cache_->GetZygoteMap()->SetCompilationState(ZygoteCompilationState::kNotifiedOk);

  // The private mapping created for this process has been mremaped. We can
  // reset it.
  child_mapping_methods.Reset();
}

class JitCompileTask final : public Task {
 public:
  enum class TaskKind {
    kCompile,
    kPreCompile,
  };

  JitCompileTask(ArtMethod* method,
                 TaskKind task_kind,
                 CompilationKind compilation_kind)
      : method_(method),
        kind_(task_kind),
        compilation_kind_(compilation_kind) {
  }

  void Run(Thread* self) override {
    {
      ScopedObjectAccess soa(self);
      switch (kind_) {
        case TaskKind::kCompile:
        case TaskKind::kPreCompile: {
          Runtime::Current()->GetJit()->CompileMethodInternal(
              method_,
              self,
              compilation_kind_,
              /* prejit= */ (kind_ == TaskKind::kPreCompile));
          break;
        }
      }
    }
    ProfileSaver::NotifyJitActivity();
  }

  void Finalize() override {
    JitThreadPool* thread_pool = Runtime::Current()->GetJit()->GetThreadPool();
    if (thread_pool != nullptr) {
      thread_pool->Remove(this);
    }
    delete this;
  }

  ArtMethod* GetArtMethod() const {
    return method_;
  }

  CompilationKind GetCompilationKind() const {
    return compilation_kind_;
  }

 private:
  ArtMethod* const method_;
  const TaskKind kind_;
  const CompilationKind compilation_kind_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(JitCompileTask);
};

static std::string GetProfileFile(const std::string& dex_location) {
  // Hardcoded assumption where the profile file is.
  // TODO(ngeoffray): this is brittle and we would need to change change if we
  // wanted to do more eager JITting of methods in a profile. This is
  // currently only for system server.
  return dex_location + ".prof";
}

static std::string GetBootProfileFile(const std::string& profile) {
  // The boot profile can be found next to the compilation profile, with a
  // different extension.
  return ReplaceFileExtension(profile, "bprof");
}

/**
 * A JIT task to run after all profile compilation is done.
 */
class JitDoneCompilingProfileTask final : public SelfDeletingTask {
 public:
  explicit JitDoneCompilingProfileTask(const std::vector<const DexFile*>& dex_files)
      : dex_files_(dex_files) {}

  void Run([[maybe_unused]] Thread* self) override {
    // Madvise DONTNEED dex files now that we're done compiling methods.
    for (const DexFile* dex_file : dex_files_) {
      if (IsAddressKnownBackedByFileOrShared(dex_file->Begin())) {
        int result = madvise(const_cast<uint8_t*>(AlignDown(dex_file->Begin(), gPageSize)),
                             RoundUp(dex_file->Size(), gPageSize),
                             MADV_DONTNEED);
        if (result == -1) {
          PLOG(WARNING) << "Madvise failed";
        }
      }
    }
  }

 private:
  std::vector<const DexFile*> dex_files_;

  DISALLOW_COPY_AND_ASSIGN(JitDoneCompilingProfileTask);
};

class JitZygoteDoneCompilingTask final : public SelfDeletingTask {
 public:
  JitZygoteDoneCompilingTask() {}

  void Run([[maybe_unused]] Thread* self) override {
    DCHECK(Runtime::Current()->IsZygote());
    Runtime::Current()->GetJit()->GetCodeCache()->GetZygoteMap()->SetCompilationState(
        ZygoteCompilationState::kDone);
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(JitZygoteDoneCompilingTask);
};

/**
 * A JIT task to run Java verification of boot classpath classes that were not
 * verified at compile-time.
 */
class ZygoteVerificationTask final : public Task {
 public:
  ZygoteVerificationTask() {}

  void Run(Thread* self) override {
    // We are going to load class and run verification, which may also need to load
    // classes. If the thread cannot load classes (typically when the runtime is
    // debuggable), then just return.
    if (!self->CanLoadClasses()) {
      return;
    }
    Runtime* runtime = Runtime::Current();
    ClassLinker* linker = runtime->GetClassLinker();
    const std::vector<const DexFile*>& boot_class_path =
        runtime->GetClassLinker()->GetBootClassPath();
    ScopedObjectAccess soa(self);
    StackHandleScope<1> hs(self);
    MutableHandle<mirror::Class> klass = hs.NewHandle<mirror::Class>(nullptr);
    uint64_t start_ns = ThreadCpuNanoTime();
    uint64_t number_of_classes = 0;
    for (const DexFile* dex_file : boot_class_path) {
      for (uint32_t i = 0; i < dex_file->NumClassDefs(); ++i) {
        const dex::ClassDef& class_def = dex_file->GetClassDef(i);
        const char* descriptor = dex_file->GetClassDescriptor(class_def);
        klass.Assign(linker->LookupResolvedType(descriptor, /* class_loader= */ nullptr));
        if (klass == nullptr) {
          // Class not loaded yet.
          DCHECK(!self->IsExceptionPending());
          continue;
        }
        if (klass->IsVerified()) {
          continue;
        }
        if (linker->VerifyClass(self, /* verifier_deps= */ nullptr, klass) ==
                verifier::FailureKind::kHardFailure) {
          CHECK(self->IsExceptionPending());
          LOG(WARNING) << "Methods in the boot classpath failed to verify: "
                       << self->GetException()->Dump();
          self->ClearException();
        } else {
          ++number_of_classes;
        }
        CHECK(!self->IsExceptionPending());
      }
    }
    LOG(INFO) << "Background verification of "
              << number_of_classes
              << " classes from boot classpath took "
              << PrettyDuration(ThreadCpuNanoTime() - start_ns);
  }
};

class ZygoteTask final : public Task {
 public:
  ZygoteTask() {}

  void Run(Thread* self) override {
    Runtime* runtime = Runtime::Current();
    uint32_t added_to_queue = 0;
    for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
      const std::vector<const DexFile*>& boot_class_path =
          runtime->GetClassLinker()->GetBootClassPath();
      ScopedNullHandle<mirror::ClassLoader> null_handle;
      // We avoid doing compilation at boot for the secondary zygote, as apps forked from it are not
      // critical for boot.
      if (Runtime::Current()->IsPrimaryZygote()) {
        for (const std::string& profile_file : space->GetProfileFiles()) {
          std::string boot_profile = GetBootProfileFile(profile_file);
          LOG(INFO) << "JIT Zygote looking at boot profile " << boot_profile;

          // We add to the queue for zygote so that we can fork processes in-between compilations.
          added_to_queue += runtime->GetJit()->CompileMethodsFromBootProfile(
              self, boot_class_path, boot_profile, null_handle, /* add_to_queue= */ true);
        }
      }
      for (const std::string& profile_file : space->GetProfileFiles()) {
        LOG(INFO) << "JIT Zygote looking at profile " << profile_file;

        added_to_queue += runtime->GetJit()->CompileMethodsFromProfile(
            self, boot_class_path, profile_file, null_handle, /* add_to_queue= */ true);
      }
    }
    DCHECK(runtime->GetJit()->InZygoteUsingJit());
    runtime->GetJit()->AddPostBootTask(self, new JitZygoteDoneCompilingTask());

    JitCodeCache* code_cache = runtime->GetJit()->GetCodeCache();
    code_cache->GetZygoteMap()->Initialize(added_to_queue);
  }

  void Finalize() override {
    delete this;
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(ZygoteTask);
};

class JitProfileTask final : public Task {
 public:
  JitProfileTask(const std::vector<std::unique_ptr<const DexFile>>& dex_files,
                 jobject class_loader) {
    ScopedObjectAccess soa(Thread::Current());
    StackHandleScope<1> hs(soa.Self());
    Handle<mirror::ClassLoader> h_loader(hs.NewHandle(
        soa.Decode<mirror::ClassLoader>(class_loader)));
    ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
    for (const auto& dex_file : dex_files) {
      dex_files_.push_back(dex_file.get());
      // Register the dex file so that we can guarantee it doesn't get deleted
      // while reading it during the task.
      class_linker->RegisterDexFile(*dex_file.get(), h_loader.Get());
    }
    // We also create our own global ref to use this class loader later.
    class_loader_ = soa.Vm()->AddGlobalRef(soa.Self(), h_loader.Get());
  }

  void Run(Thread* self) override {
    ScopedObjectAccess soa(self);
    StackHandleScope<1> hs(self);
    Handle<mirror::ClassLoader> loader = hs.NewHandle<mirror::ClassLoader>(
        soa.Decode<mirror::ClassLoader>(class_loader_));

    std::string profile = GetProfileFile(dex_files_[0]->GetLocation());
    std::string boot_profile = GetBootProfileFile(profile);

    Jit* jit = Runtime::Current()->GetJit();

    jit->CompileMethodsFromBootProfile(
        self,
        dex_files_,
        boot_profile,
        loader,
        /* add_to_queue= */ false);

    jit->CompileMethodsFromProfile(
        self,
        dex_files_,
        profile,
        loader,
        /* add_to_queue= */ true);
  }

  void Finalize() override {
    delete this;
  }

  ~JitProfileTask() {
    ScopedObjectAccess soa(Thread::Current());
    soa.Vm()->DeleteGlobalRef(soa.Self(), class_loader_);
  }

 private:
  std::vector<const DexFile*> dex_files_;
  jobject class_loader_;

  DISALLOW_COPY_AND_ASSIGN(JitProfileTask);
};

static void CopyIfDifferent(void* s1, const void* s2, size_t n) {
  if (memcmp(s1, s2, n) != 0) {
    memcpy(s1, s2, n);
  }
}

void Jit::MapBootImageMethods() {
  if (Runtime::Current()->IsJavaDebuggable()) {
    LOG(INFO) << "Not mapping boot image methods due to process being debuggable";
    return;
  }
  CHECK_NE(fd_methods_.get(), -1);
  if (!code_cache_->GetZygoteMap()->CanMapBootImageMethods()) {
    LOG(WARNING) << "Not mapping boot image methods due to error from zygote";
    // We don't need the fd anymore.
    fd_methods_.reset();
    return;
  }

  std::string error_str;
  MemMap child_mapping_methods = MemMap::MapFile(
      fd_methods_size_,
      PROT_READ | PROT_WRITE,
      MAP_PRIVATE,
      fd_methods_,
      /* start= */ 0,
      /* low_4gb= */ false,
      "boot-image-methods",
      &error_str);

  // We don't need the fd anymore.
  fd_methods_.reset();

  if (!child_mapping_methods.IsValid()) {
    LOG(WARNING) << "Failed to create child mapping of boot image methods: " << error_str;
    return;
  }
  //  We are going to mremap the child mapping into the image:
  //
  //                            ImageSection       ChildMappingMethods
  //
  //         section start -->  -----------
  //                            |         |
  //                            |         |
  //            page_start -->  |         |   <-----   -----------
  //                            |         |            |         |
  //                            |         |            |         |
  //                            |         |            |         |
  //                            |         |            |         |
  //                            |         |            |         |
  //                            |         |            |         |
  //                            |         |            |         |
  //             page_end  -->  |         |   <-----   -----------
  //                            |         |
  //         section end   -->  -----------
  //
  size_t offset = 0;
  for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
    const ImageHeader& header = space->GetImageHeader();
    const ImageSection& section = header.GetMethodsSection();
    uint8_t* page_start = AlignUp(header.GetImageBegin() + section.Offset(), gPageSize);
    uint8_t* page_end =
        AlignDown(header.GetImageBegin() + section.Offset() + section.Size(), gPageSize);
    if (page_end <= page_start) {
      // Section doesn't contain one aligned entire page.
      continue;
    }
    uint64_t capacity = page_end - page_start;
    // Walk over methods in the boot image, and check for:
    // 1) methods whose class is not initialized in the process, but are in the
    // zygote process. For such methods, we need their entrypoints to be stubs
    // that do the initialization check.
    // 2) native methods whose data pointer is different than the one in the
    // zygote. Such methods may have had custom native implementation provided
    // by JNI RegisterNatives.
    header.VisitPackedArtMethods([&](ArtMethod& method) NO_THREAD_SAFETY_ANALYSIS {
      // Methods in the boot image should never have their single
      // implementation flag set (and therefore never have a `data_` pointing
      // to an ArtMethod for single implementation).
      CHECK(method.IsIntrinsic() || !method.HasSingleImplementationFlag());
      if (method.IsRuntimeMethod()) {
        return;
      }

      // Pointer to the method we're currently using.
      uint8_t* pointer = reinterpret_cast<uint8_t*>(&method);
      // The data pointer of that method that we want to keep.
      uint8_t* data_pointer = pointer + ArtMethod::DataOffset(kRuntimePointerSize).Int32Value();
      if (method.IsNative() && data_pointer >= page_start && data_pointer < page_end) {
        // The data pointer of the ArtMethod in the shared memory we are going to remap into our
        // own mapping. This is the data that we will see after the remap.
        uint8_t* new_data_pointer =
            child_mapping_methods.Begin() + offset + (data_pointer - page_start);
        CopyIfDifferent(new_data_pointer, data_pointer, sizeof(void*));
      }

      // The entrypoint of the method we're currently using and that we want to
      // keep.
      uint8_t* entry_point_pointer = pointer +
          ArtMethod::EntryPointFromQuickCompiledCodeOffset(kRuntimePointerSize).Int32Value();
      if (!method.GetDeclaringClassUnchecked()->IsVisiblyInitialized() &&
          method.IsStatic() &&
          !method.IsConstructor() &&
          entry_point_pointer >= page_start &&
          entry_point_pointer < page_end) {
        // The entry point of the ArtMethod in the shared memory we are going to remap into our
        // own mapping. This is the entrypoint that we will see after the remap.
        uint8_t* new_entry_point_pointer =
            child_mapping_methods.Begin() + offset + (entry_point_pointer - page_start);
        CopyIfDifferent(new_entry_point_pointer, entry_point_pointer, sizeof(void*));
      }
    }, space->Begin(), kRuntimePointerSize);

    // Map the memory in the boot image range.
    if (mremap(child_mapping_methods.Begin() + offset,
               capacity,
               capacity,
               MREMAP_FIXED | MREMAP_MAYMOVE,
               page_start) == MAP_FAILED) {
      PLOG(WARNING) << "Fail to mremap boot image methods for " << space->GetImageFilename();
    }
    offset += capacity;
  }

  // The private mapping created for this process has been mremaped. We can
  // reset it.
  child_mapping_methods.Reset();
  LOG(INFO) << "Successfully mapped boot image methods";
}

bool Jit::InZygoteUsingJit() {
  Runtime* runtime = Runtime::Current();
  return runtime->IsZygote() && runtime->HasImageWithProfile() && runtime->UseJitCompilation();
}

void Jit::CreateThreadPool() {
  // There is a DCHECK in the 'AddSamples' method to ensure the tread pool
  // is not null when we instrument.

  thread_pool_.reset(JitThreadPool::Create("Jit thread pool", 1));

  Runtime* runtime = Runtime::Current();
  thread_pool_->SetPthreadPriority(
      runtime->IsZygote()
          ? options_->GetZygoteThreadPoolPthreadPriority()
          : options_->GetThreadPoolPthreadPriority());
  Start();

  if (runtime->IsZygote()) {
    // To speed up class lookups, generate a type lookup table for
    // dex files not backed by oat file.
    for (const DexFile* dex_file : runtime->GetClassLinker()->GetBootClassPath()) {
      if (dex_file->GetOatDexFile() == nullptr) {
        TypeLookupTable type_lookup_table = TypeLookupTable::Create(*dex_file);
        type_lookup_tables_.push_back(
            std::make_unique<art::OatDexFile>(std::move(type_lookup_table)));
        dex_file->SetOatDexFile(type_lookup_tables_.back().get());
      }
    }

    // Add a task that will verify boot classpath jars that were not
    // pre-compiled.
    thread_pool_->AddTask(Thread::Current(), new ZygoteVerificationTask());
  }

  if (InZygoteUsingJit()) {
    // If we have an image with a profile, request a JIT task to
    // compile all methods in that profile.
    thread_pool_->AddTask(Thread::Current(), new ZygoteTask());

    // And create mappings to share boot image methods memory from the zygote to
    // child processes.

    // Compute the total capacity required for the boot image methods.
    uint64_t total_capacity = 0;
    for (gc::space::ImageSpace* space : Runtime::Current()->GetHeap()->GetBootImageSpaces()) {
      const ImageHeader& header = space->GetImageHeader();
      const ImageSection& section = header.GetMethodsSection();
      // Mappings need to be at the page level.
      uint8_t* page_start = AlignUp(header.GetImageBegin() + section.Offset(), gPageSize);
      uint8_t* page_end =
          AlignDown(header.GetImageBegin() + section.Offset() + section.Size(), gPageSize);
      if (page_end > page_start) {
        total_capacity += (page_end - page_start);
      }
    }

    // Create the child and zygote mappings to the boot image methods.
    if (total_capacity > 0) {
      // Start with '/boot' and end with '.art' to match the pattern recognized
      // by android_os_Debug.cpp for boot images.
      const char* name = "/boot-image-methods.art";
      unique_fd mem_fd =
          unique_fd(art::memfd_create(name, /* flags= */ MFD_ALLOW_SEALING | MFD_CLOEXEC));
      if (mem_fd.get() == -1) {
        PLOG(WARNING) << "Could not create boot image methods file descriptor";
        return;
      }
      if (ftruncate(mem_fd.get(), total_capacity) != 0) {
        PLOG(WARNING) << "Failed to truncate boot image methods file to " << total_capacity;
        return;
      }
      std::string error_str;

      // Create the shared mapping eagerly, as this prevents other processes
      // from adding the writable seal.
      zygote_mapping_methods_ = MemMap::MapFile(
        total_capacity,
        PROT_READ | PROT_WRITE,
        MAP_SHARED,
        mem_fd,
        /* start= */ 0,
        /* low_4gb= */ false,
        "boot-image-methods",
        &error_str);

      if (!zygote_mapping_methods_.IsValid()) {
        LOG(WARNING) << "Failed to create zygote mapping of boot image methods:  " << error_str;
        return;
      }
      if (zygote_mapping_methods_.MadviseDontFork() != 0) {
        LOG(WARNING) << "Failed to madvise dont fork boot image methods";
        zygote_mapping_methods_ = MemMap();
        return;
      }

      // We should use the F_SEAL_FUTURE_WRITE flag, but this has unexpected
      // behavior on private mappings after fork (the mapping becomes shared between
      // parent and children), see b/143833776.
      // We will seal the write once we are done writing to the shared mapping.
      if (fcntl(mem_fd, F_ADD_SEALS, F_SEAL_SHRINK | F_SEAL_GROW) == -1) {
        PLOG(WARNING) << "Failed to seal boot image methods file descriptor";
        zygote_mapping_methods_ = MemMap();
        return;
      }
      fd_methods_ = unique_fd(mem_fd.release());
      fd_methods_size_ = total_capacity;
    }
  }
}

void Jit::RegisterDexFiles(const std::vector<std::unique_ptr<const DexFile>>& dex_files,
                           jobject class_loader) {
  if (dex_files.empty()) {
    return;
  }
  Runtime* runtime = Runtime::Current();
  // If the runtime is debuggable, don't bother precompiling methods.
  // If system server is being profiled, don't precompile as we are going to use
  // the JIT to count hotness. Note that --count-hotness-in-compiled-code is
  // only forced when we also profile the boot classpath, see
  // AndroidRuntime.cpp.
  if (runtime->IsSystemServer() &&
      UseJitCompilation() &&
      options_->UseProfiledJitCompilation() &&
      runtime->HasImageWithProfile() &&
      !runtime->IsSystemServerProfiled() &&
      !runtime->IsJavaDebuggable()) {
    // Note: this precompilation is currently not running in production because:
    // - UseProfiledJitCompilation() is not set by default.
    // - System server dex files are registered *before* we set the runtime as
    //   system server (though we are in the system server process).
    thread_pool_->AddTask(Thread::Current(), new JitProfileTask(dex_files, class_loader));
  }
}

void Jit::AddCompileTask(Thread* self,
                         ArtMethod* method,
                         CompilationKind compilation_kind) {
  thread_pool_->AddTask(self, method, compilation_kind);
}

bool Jit::CompileMethodFromProfile(Thread* self,
                                   ClassLinker* class_linker,
                                   uint32_t method_idx,
                                   Handle<mirror::DexCache> dex_cache,
                                   Handle<mirror::ClassLoader> class_loader,
                                   bool add_to_queue,
                                   bool compile_after_boot) {
  ArtMethod* method = class_linker->ResolveMethodWithoutInvokeType(
      method_idx, dex_cache, class_loader);
  if (method == nullptr) {
    self->ClearException();
    return false;
  }
  if (!method->IsCompilable() || !method->IsInvokable()) {
    return false;
  }
  if (method->IsPreCompiled()) {
    // Already seen by another profile.
    return false;
  }
  CompilationKind compilation_kind = CompilationKind::kOptimized;
  const void* entry_point = method->GetEntryPointFromQuickCompiledCode();
  if (class_linker->IsQuickToInterpreterBridge(entry_point) ||
      class_linker->IsQuickGenericJniStub(entry_point) ||
      class_linker->IsNterpEntryPoint(entry_point) ||
      // We explicitly check for the resolution stub, and not the resolution trampoline.
      // The trampoline is for methods backed by a .oat file that has a compiled version of
      // the method.
      (entry_point == GetQuickResolutionStub())) {
    VLOG(jit) << "JIT Zygote processing method " << ArtMethod::PrettyMethod(method)
              << " from profile";
    method->SetPreCompiled();
    if (!add_to_queue) {
      CompileMethodInternal(method, self, compilation_kind, /* prejit= */ true);
    } else {
      Task* task = new JitCompileTask(
          method, JitCompileTask::TaskKind::kPreCompile, compilation_kind);
      if (compile_after_boot) {
        AddPostBootTask(self, task);
      } else {
        thread_pool_->AddTask(self, task);
      }
      return true;
    }
  }
  return false;
}

uint32_t Jit::CompileMethodsFromBootProfile(
    Thread* self,
    const std::vector<const DexFile*>& dex_files,
    const std::string& profile_file,
    Handle<mirror::ClassLoader> class_loader,
    bool add_to_queue) {
  unix_file::FdFile profile(profile_file, O_RDONLY, true);

  if (profile.Fd() == -1) {
    PLOG(WARNING) << "No boot profile: " << profile_file;
    return 0u;
  }

  ProfileBootInfo profile_info;
  if (!profile_info.Load(profile.Fd(), dex_files)) {
    LOG(ERROR) << "Could not load profile file: " << profile_file;
    return 0u;
  }

  ScopedObjectAccess soa(self);
  VariableSizedHandleScope handles(self);
  std::vector<Handle<mirror::DexCache>> dex_caches;
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  for (const DexFile* dex_file : profile_info.GetDexFiles()) {
    dex_caches.push_back(handles.NewHandle(class_linker->FindDexCache(self, *dex_file)));
  }

  uint32_t added_to_queue = 0;
  for (const std::pair<uint32_t, uint32_t>& pair : profile_info.GetMethods()) {
    if (CompileMethodFromProfile(self,
                                 class_linker,
                                 pair.second,
                                 dex_caches[pair.first],
                                 class_loader,
                                 add_to_queue,
                                 /*compile_after_boot=*/false)) {
      ++added_to_queue;
    }
  }
  return added_to_queue;
}

uint32_t Jit::CompileMethodsFromProfile(
    Thread* self,
    const std::vector<const DexFile*>& dex_files,
    const std::string& profile_file,
    Handle<mirror::ClassLoader> class_loader,
    bool add_to_queue) {

  if (profile_file.empty()) {
    LOG(WARNING) << "Expected a profile file in JIT zygote mode";
    return 0u;
  }

  // We don't generate boot profiles on device, therefore we don't
  // need to lock the file.
  unix_file::FdFile profile(profile_file, O_RDONLY, true);

  if (profile.Fd() == -1) {
    PLOG(WARNING) << "No profile: " << profile_file;
    return 0u;
  }

  ProfileCompilationInfo profile_info(/* for_boot_image= */ class_loader.IsNull());
  if (!profile_info.Load(profile.Fd())) {
    LOG(ERROR) << "Could not load profile file";
    return 0u;
  }
  ScopedObjectAccess soa(self);
  StackHandleScope<1> hs(self);
  MutableHandle<mirror::DexCache> dex_cache = hs.NewHandle<mirror::DexCache>(nullptr);
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  uint32_t added_to_queue = 0u;
  for (const DexFile* dex_file : dex_files) {
    std::set<dex::TypeIndex> class_types;
    std::set<uint16_t> all_methods;
    if (!profile_info.GetClassesAndMethods(*dex_file,
                                           &class_types,
                                           &all_methods,
                                           &all_methods,
                                           &all_methods)) {
      // This means the profile file did not reference the dex file, which is the case
      // if there's no classes and methods of that dex file in the profile.
      continue;
    }
    dex_cache.Assign(class_linker->FindDexCache(self, *dex_file));
    CHECK(dex_cache != nullptr) << "Could not find dex cache for " << dex_file->GetLocation();

    for (uint16_t method_idx : all_methods) {
      if (CompileMethodFromProfile(self,
                                   class_linker,
                                   method_idx,
                                   dex_cache,
                                   class_loader,
                                   add_to_queue,
                                   /*compile_after_boot=*/true)) {
        ++added_to_queue;
      }
    }
  }

  // Add a task to run when all compilation is done.
  AddPostBootTask(self, new JitDoneCompilingProfileTask(dex_files));
  return added_to_queue;
}

bool Jit::IgnoreSamplesForMethod(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  if (method->IsClassInitializer() || !method->IsCompilable()) {
    // We do not want to compile such methods.
    return true;
  }
  if (method->IsNative()) {
    ObjPtr<mirror::Class> klass = method->GetDeclaringClass();
    if (klass == GetClassRoot<mirror::MethodHandle>() ||
        klass == GetClassRoot<mirror::VarHandle>()) {
      // MethodHandle and VarHandle invocation methods are required to throw an
      // UnsupportedOperationException if invoked reflectively. We achieve this by having native
      // implementations that raise the exception. We need to disable JIT compilation of these JNI
      // methods as it can lead to transitioning between JIT compiled JNI stubs and generic JNI
      // stubs. Since these stubs have different stack representations we can then crash in stack
      // walking (b/78151261).
      return true;
    }
  }
  return false;
}

void Jit::EnqueueOptimizedCompilation(ArtMethod* method, Thread* self) {
  // Note the hotness counter will be reset by the compiled code.

  if (thread_pool_ == nullptr) {
    return;
  }

  // We arrive here after a baseline compiled code has reached its baseline
  // hotness threshold. If we're not only using the baseline compiler, enqueue a compilation
  // task that will compile optimize the method.
  if (!options_->UseBaselineCompiler()) {
    AddCompileTask(self, method, CompilationKind::kOptimized);
  }
}

class ScopedSetRuntimeThread {
 public:
  explicit ScopedSetRuntimeThread(Thread* self)
      : self_(self), was_runtime_thread_(self_->IsRuntimeThread()) {
    self_->SetIsRuntimeThread(true);
  }

  ~ScopedSetRuntimeThread() {
    self_->SetIsRuntimeThread(was_runtime_thread_);
  }

 private:
  Thread* self_;
  bool was_runtime_thread_;
};

void Jit::MethodEntered(Thread* self, ArtMethod* method) {
  Runtime* runtime = Runtime::Current();
  if (UNLIKELY(runtime->UseJitCompilation() && JitAtFirstUse())) {
    ArtMethod* np_method = method->GetInterfaceMethodIfProxy(kRuntimePointerSize);
    if (np_method->IsCompilable()) {
      CompileMethod(method, self, CompilationKind::kOptimized, /* prejit= */ false);
    }
    return;
  }

  AddSamples(self, method);
}

void Jit::WaitForCompilationToFinish(Thread* self) {
  if (thread_pool_ != nullptr) {
    thread_pool_->Wait(self, false, false);
  }
}

void Jit::Stop() {
  Thread* self = Thread::Current();
  // TODO(ngeoffray): change API to not require calling WaitForCompilationToFinish twice.
  WaitForCompilationToFinish(self);
  GetThreadPool()->StopWorkers(self);
  WaitForCompilationToFinish(self);
}

void Jit::Start() {
  GetThreadPool()->StartWorkers(Thread::Current());
}

ScopedJitSuspend::ScopedJitSuspend() {
  jit::Jit* jit = Runtime::Current()->GetJit();
  was_on_ = (jit != nullptr) && (jit->GetThreadPool() != nullptr);
  if (was_on_) {
    jit->Stop();
  }
}

ScopedJitSuspend::~ScopedJitSuspend() {
  if (was_on_) {
    DCHECK(Runtime::Current()->GetJit() != nullptr);
    DCHECK(Runtime::Current()->GetJit()->GetThreadPool() != nullptr);
    Runtime::Current()->GetJit()->Start();
  }
}

static void* RunPollingThread(void* arg) {
  Jit* jit = reinterpret_cast<Jit*>(arg);
  do {
    sleep(10);
  } while (!jit->GetCodeCache()->GetZygoteMap()->IsCompilationNotified());

  // We will suspend other threads: we can only do that if we're attached to the
  // runtime.
  Runtime* runtime = Runtime::Current();
  bool thread_attached = runtime->AttachCurrentThread(
      "BootImagePollingThread",
      /* as_daemon= */ true,
      /* thread_group= */ nullptr,
      /* create_peer= */ false);
  CHECK(thread_attached);

  {
    // Prevent other threads from running while we are remapping the boot image
    // ArtMethod's. Native threads might still be running, but they cannot
    // change the contents of ArtMethod's.
    ScopedSuspendAll ssa(__FUNCTION__);
    runtime->GetJit()->MapBootImageMethods();
  }

  Runtime::Current()->DetachCurrentThread();
  return nullptr;
}

void Jit::PostForkChildAction(bool is_system_server, bool is_zygote) {
  // Clear the potential boot tasks inherited from the zygote.
  {
    MutexLock mu(Thread::Current(), boot_completed_lock_);
    tasks_after_boot_.clear();
  }

  Runtime* const runtime = Runtime::Current();
  // Check if we'll need to remap the boot image methods.
  if (!is_zygote && fd_methods_ != -1) {
    // Create a thread that will poll the status of zygote compilation, and map
    // the private mapping of boot image methods.
    // For child zygote, we instead query IsCompilationNotified() post zygote fork.
    zygote_mapping_methods_.ResetInForkedProcess();
    pthread_t polling_thread;
    pthread_attr_t attr;
    CHECK_PTHREAD_CALL(pthread_attr_init, (&attr), "new thread");
    CHECK_PTHREAD_CALL(pthread_attr_setdetachstate, (&attr, PTHREAD_CREATE_DETACHED),
                       "PTHREAD_CREATE_DETACHED");
    CHECK_PTHREAD_CALL(
        pthread_create,
        (&polling_thread, &attr, RunPollingThread, reinterpret_cast<void*>(this)),
        "Methods maps thread");
  }

  if (is_zygote || runtime->IsSafeMode()) {
    // Delete the thread pool, we are not going to JIT.
    thread_pool_.reset(nullptr);
    return;
  }
  // At this point, the compiler options have been adjusted to the particular configuration
  // of the forked child. Parse them again.
  jit_compiler_->ParseCompilerOptions();

  // Adjust the status of code cache collection: the status from zygote was to not collect.
  // JitAtFirstUse compiles the methods synchronously on mutator threads. While this should work
  // in theory it is causing deadlocks in some jvmti tests related to Jit GC. Hence, disabling
  // Jit GC for now (b/147208992).
  code_cache_->SetGarbageCollectCode(
      !jit_compiler_->GenerateDebugInfo() &&
      !JitAtFirstUse());

  if (is_system_server && runtime->HasImageWithProfile()) {
    // Disable garbage collection: we don't want it to delete methods we're compiling
    // through boot and system server profiles.
    // TODO(ngeoffray): Fix this so we still collect deoptimized and unused code.
    code_cache_->SetGarbageCollectCode(false);
  }

  // We do this here instead of PostZygoteFork, as NativeDebugInfoPostFork only
  // applies to a child.
  NativeDebugInfoPostFork();
}

void Jit::PreZygoteFork() {
  if (thread_pool_ == nullptr) {
    return;
  }
  thread_pool_->DeleteThreads();

  NativeDebugInfoPreFork();
}

void Jit::PostZygoteFork() {
  Runtime* runtime = Runtime::Current();
  if (thread_pool_ == nullptr) {
    // If this is a child zygote, check if we need to remap the boot image
    // methods.
    if (runtime->IsZygote() &&
        fd_methods_ != -1 &&
        code_cache_->GetZygoteMap()->IsCompilationNotified()) {
      ScopedSuspendAll ssa(__FUNCTION__);
      MapBootImageMethods();
    }
    return;
  }
  if (runtime->IsZygote() && code_cache_->GetZygoteMap()->IsCompilationDoneButNotNotified()) {
    // Copy the boot image methods data to the mappings we created to share
    // with the children. We do this here as we are the only thread running and
    // we don't risk other threads concurrently updating the ArtMethod's.
    CHECK_EQ(GetTaskCount(), 1);
    NotifyZygoteCompilationDone();
    CHECK(code_cache_->GetZygoteMap()->IsCompilationNotified());
  }
  thread_pool_->CreateThreads();
  thread_pool_->SetPthreadPriority(
      runtime->IsZygote()
          ? options_->GetZygoteThreadPoolPthreadPriority()
          : options_->GetThreadPoolPthreadPriority());
}

void Jit::AddPostBootTask(Thread* self, Task* task) {
  MutexLock mu(self, boot_completed_lock_);
  if (boot_completed_) {
    thread_pool_->AddTask(self, task);
  } else {
    tasks_after_boot_.push_back(task);
  }
}

void Jit::BootCompleted() {
  Thread* self = Thread::Current();
  std::deque<Task*> tasks;
  {
    MutexLock mu(self, boot_completed_lock_);
    tasks = std::move(tasks_after_boot_);
    boot_completed_ = true;
  }
  for (Task* task : tasks) {
    thread_pool_->AddTask(self, task);
  }
}

bool Jit::CanEncodeMethod(ArtMethod* method, bool is_for_shared_region) const {
  return !is_for_shared_region ||
      Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(method->GetDeclaringClass());
}

bool Jit::CanEncodeClass(ObjPtr<mirror::Class> cls, bool is_for_shared_region) const {
  return !is_for_shared_region || Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(cls);
}

bool Jit::CanEncodeString(ObjPtr<mirror::String> string, bool is_for_shared_region) const {
  return !is_for_shared_region || Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(string);
}

bool Jit::CanAssumeInitialized(ObjPtr<mirror::Class> cls, bool is_for_shared_region) const {
  if (!is_for_shared_region) {
    return cls->IsInitialized();
  } else {
    // Look up the class status in the oat file.
    const DexFile& dex_file = *cls->GetDexCache()->GetDexFile();
    const OatDexFile* oat_dex_file = dex_file.GetOatDexFile();
    // In case we run without an image there won't be a backing oat file.
    if (oat_dex_file == nullptr || oat_dex_file->GetOatFile() == nullptr) {
      return false;
    }
    uint16_t class_def_index = cls->GetDexClassDefIndex();
    return oat_dex_file->GetOatClass(class_def_index).GetStatus() >= ClassStatus::kInitialized;
  }
}

void Jit::MaybeEnqueueCompilation(ArtMethod* method, Thread* self) {
  if (thread_pool_ == nullptr) {
    return;
  }

  if (JitAtFirstUse()) {
    // Tests might request JIT on first use (compiled synchronously in the interpreter).
    return;
  }

  if (!UseJitCompilation()) {
    return;
  }

  if (IgnoreSamplesForMethod(method)) {
    return;
  }

  if (GetCodeCache()->ContainsPc(method->GetEntryPointFromQuickCompiledCode())) {
    if (!method->IsNative() && !code_cache_->IsOsrCompiled(method)) {
      // If we already have compiled code for it, nterp may be stuck in a loop.
      // Compile OSR.
      AddCompileTask(self, method, CompilationKind::kOsr);
    }
    return;
  }

  // Check if we have precompiled this method.
  if (UNLIKELY(method->IsPreCompiled())) {
    if (!method->StillNeedsClinitCheck()) {
      const void* entry_point = code_cache_->GetSavedEntryPointOfPreCompiledMethod(method);
      if (entry_point != nullptr) {
        Runtime::Current()->GetInstrumentation()->UpdateMethodsCode(method, entry_point);
      }
    }
    return;
  }

  static constexpr size_t kIndividualSharedMethodHotnessThreshold = 0x3f;
  if (method->IsMemorySharedMethod()) {
    MutexLock mu(self, lock_);
    auto it = shared_method_counters_.find(method);
    if (it == shared_method_counters_.end()) {
      shared_method_counters_[method] = kIndividualSharedMethodHotnessThreshold;
      return;
    } else if (it->second != 0) {
      DCHECK_LE(it->second, kIndividualSharedMethodHotnessThreshold);
      shared_method_counters_[method] = it->second - 1;
      return;
    } else {
      shared_method_counters_[method] = kIndividualSharedMethodHotnessThreshold;
    }
  }

  if (!method->IsNative() && GetCodeCache()->CanAllocateProfilingInfo()) {
    AddCompileTask(self, method, CompilationKind::kBaseline);
  } else {
    AddCompileTask(self, method, CompilationKind::kOptimized);
  }
}

bool Jit::CompileMethod(ArtMethod* method,
                        Thread* self,
                        CompilationKind compilation_kind,
                        bool prejit) {
  // Fake being in a runtime thread so that class-load behavior will be the same as normal jit.
  ScopedSetRuntimeThread ssrt(self);
  // TODO(ngeoffray): For JIT at first use, use kPreCompile. Currently we don't due to
  // conflicts with jitzygote optimizations.
  return CompileMethodInternal(method, self, compilation_kind, prejit);
}

size_t JitThreadPool::GetTaskCount(Thread* self) {
  MutexLock mu(self, task_queue_lock_);
  return generic_queue_.size() +
      baseline_queue_.size() +
      optimized_queue_.size() +
      osr_queue_.size();
}

void JitThreadPool::RemoveAllTasks(Thread* self) {
  // The ThreadPool is responsible for calling Finalize (which usually deletes
  // the task memory) on all the tasks.
  Task* task = nullptr;
  do {
    {
      MutexLock mu(self, task_queue_lock_);
      if (generic_queue_.empty()) {
        break;
      }
      task = generic_queue_.front();
      generic_queue_.pop_front();
    }
    task->Finalize();
  } while (true);

  MutexLock mu(self, task_queue_lock_);
  baseline_queue_.clear();
  optimized_queue_.clear();
  osr_queue_.clear();
}

JitThreadPool::~JitThreadPool() {
  DeleteThreads();
  RemoveAllTasks(Thread::Current());
}

void JitThreadPool::AddTask(Thread* self, Task* task) {
  MutexLock mu(self, task_queue_lock_);
  // We don't want to enqueue any new tasks when thread pool has stopped. This simplifies
  // the implementation of redefinition feature in jvmti.
  if (!started_) {
    task->Finalize();
    return;
  }
  generic_queue_.push_back(task);
  // If we have any waiters, signal one.
  if (waiting_count_ != 0) {
    task_queue_condition_.Signal(self);
  }
}

void JitThreadPool::AddTask(Thread* self, ArtMethod* method, CompilationKind kind) {
  MutexLock mu(self, task_queue_lock_);
  // We don't want to enqueue any new tasks when thread pool has stopped. This simplifies
  // the implementation of redefinition feature in jvmti.
  if (!started_) {
    return;
  }
  switch (kind) {
    case CompilationKind::kOsr:
      if (ContainsElement(osr_enqueued_methods_, method)) {
        return;
      }
      osr_enqueued_methods_.insert(method);
      osr_queue_.push_back(method);
      break;
    case CompilationKind::kBaseline:
      if (ContainsElement(baseline_enqueued_methods_, method)) {
        return;
      }
      baseline_enqueued_methods_.insert(method);
      baseline_queue_.push_back(method);
      break;
    case CompilationKind::kOptimized:
      if (ContainsElement(optimized_enqueued_methods_, method)) {
        return;
      }
      optimized_enqueued_methods_.insert(method);
      optimized_queue_.push_back(method);
      break;
  }
  // If we have any waiters, signal one.
  if (waiting_count_ != 0) {
    task_queue_condition_.Signal(self);
  }
}

Task* JitThreadPool::TryGetTaskLocked() {
  if (!started_) {
    return nullptr;
  }

  // Fetch generic tasks first.
  if (!generic_queue_.empty()) {
    Task* task = generic_queue_.front();
    generic_queue_.pop_front();
    return task;
  }

  // OSR requests second, then baseline and finally optimized.
  Task* task = FetchFrom(osr_queue_, CompilationKind::kOsr);
  if (task == nullptr) {
    task = FetchFrom(baseline_queue_, CompilationKind::kBaseline);
    if (task == nullptr) {
      task = FetchFrom(optimized_queue_, CompilationKind::kOptimized);
    }
  }
  return task;
}

Task* JitThreadPool::FetchFrom(std::deque<ArtMethod*>& methods, CompilationKind kind) {
  if (!methods.empty()) {
    ArtMethod* method = methods.front();
    methods.pop_front();
    JitCompileTask* task = new JitCompileTask(method, JitCompileTask::TaskKind::kCompile, kind);
    current_compilations_.insert(task);
    return task;
  }
  return nullptr;
}

void JitThreadPool::Remove(JitCompileTask* task) {
  MutexLock mu(Thread::Current(), task_queue_lock_);
  current_compilations_.erase(task);
  switch (task->GetCompilationKind()) {
    case CompilationKind::kOsr: {
      osr_enqueued_methods_.erase(task->GetArtMethod());
      break;
    }
    case CompilationKind::kBaseline: {
      baseline_enqueued_methods_.erase(task->GetArtMethod());
      break;
    }
    case CompilationKind::kOptimized: {
      optimized_enqueued_methods_.erase(task->GetArtMethod());
      break;
    }
  }
}

void Jit::VisitRoots(RootVisitor* visitor) {
  if (thread_pool_ != nullptr) {
    thread_pool_->VisitRoots(visitor);
  }
}

void JitThreadPool::VisitRoots(RootVisitor* visitor) {
  if (Runtime::Current()->GetHeap()->IsPerformingUffdCompaction()) {
    // In case of userfaultfd compaction, ArtMethods are updated concurrently
    // via linear-alloc.
    return;
  }
  // Fetch all ArtMethod first, to avoid holding `task_queue_lock_` for too
  // long.
  std::vector<ArtMethod*> methods;
  {
    MutexLock mu(Thread::Current(), task_queue_lock_);
    // We don't look at `generic_queue_` because it contains:
    // - Generic tasks like `ZygoteVerificationTask` which don't hold any root.
    // - `JitCompileTask` for precompiled methods, which we know are live, being
    //   part of the boot classpath or system server classpath.
    methods.insert(methods.end(), osr_queue_.begin(), osr_queue_.end());
    methods.insert(methods.end(), baseline_queue_.begin(), baseline_queue_.end());
    methods.insert(methods.end(), optimized_queue_.begin(), optimized_queue_.end());
    for (JitCompileTask* task : current_compilations_) {
      methods.push_back(task->GetArtMethod());
    }
  }
  UnbufferedRootVisitor root_visitor(visitor, RootInfo(kRootStickyClass));
  for (ArtMethod* method : methods) {
    method->VisitRoots(root_visitor, kRuntimePointerSize);
  }
}

}  // namespace jit
}  // namespace art
