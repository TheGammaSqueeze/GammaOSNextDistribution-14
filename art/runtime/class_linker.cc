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

#include "class_linker.h"

#include <unistd.h>

#include <algorithm>
#include <deque>
#include <forward_list>
#include <iostream>
#include <iterator>
#include <map>
#include <memory>
#include <queue>
#include <string>
#include <string_view>
#include <tuple>
#include <utility>
#include <vector>

#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "art_field-inl.h"
#include "art_method-inl.h"
#include "barrier.h"
#include "base/arena_allocator.h"
#include "base/arena_bit_vector.h"
#include "base/casts.h"
#include "base/file_utils.h"
#include "base/hash_map.h"
#include "base/hash_set.h"
#include "base/leb128.h"
#include "base/logging.h"
#include "base/mem_map_arena_pool.h"
#include "base/membarrier.h"
#include "base/metrics/metrics.h"
#include "base/mutex-inl.h"
#include "base/os.h"
#include "base/quasi_atomic.h"
#include "base/scoped_arena_containers.h"
#include "base/scoped_flock.h"
#include "base/stl_util.h"
#include "base/string_view_cpp20.h"
#include "base/systrace.h"
#include "base/time_utils.h"
#include "base/unix_file/fd_file.h"
#include "base/utils.h"
#include "base/value_object.h"
#include "cha.h"
#include "class_linker-inl.h"
#include "class_loader_utils.h"
#include "class_root-inl.h"
#include "class_table-inl.h"
#include "compiler_callbacks.h"
#include "debug_print.h"
#include "debugger.h"
#include "dex/class_accessor-inl.h"
#include "dex/descriptors_names.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_annotations.h"
#include "dex/dex_file_exception_helpers.h"
#include "dex/dex_file_loader.h"
#include "dex/signature-inl.h"
#include "dex/utf.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "experimental_flags.h"
#include "gc/accounting/card_table-inl.h"
#include "gc/accounting/heap_bitmap-inl.h"
#include "gc/accounting/space_bitmap-inl.h"
#include "gc/heap-visit-objects-inl.h"
#include "gc/heap.h"
#include "gc/scoped_gc_critical_section.h"
#include "gc/space/image_space.h"
#include "gc/space/space-inl.h"
#include "gc_root-inl.h"
#include "handle_scope-inl.h"
#include "hidden_api.h"
#include "imt_conflict_table.h"
#include "imtable-inl.h"
#include "intern_table-inl.h"
#include "interpreter/interpreter.h"
#include "interpreter/mterp/nterp.h"
#include "jit/debugger_interface.h"
#include "jit/jit.h"
#include "jit/jit_code_cache.h"
#include "jni/java_vm_ext.h"
#include "jni/jni_internal.h"
#include "linear_alloc-inl.h"
#include "mirror/array-alloc-inl.h"
#include "mirror/array-inl.h"
#include "mirror/call_site.h"
#include "mirror/class-alloc-inl.h"
#include "mirror/class-inl.h"
#include "mirror/class.h"
#include "mirror/class_ext.h"
#include "mirror/class_loader.h"
#include "mirror/dex_cache-inl.h"
#include "mirror/dex_cache.h"
#include "mirror/emulated_stack_frame.h"
#include "mirror/field.h"
#include "mirror/iftable-inl.h"
#include "mirror/method.h"
#include "mirror/method_handle_impl.h"
#include "mirror/method_handles_lookup.h"
#include "mirror/method_type-inl.h"
#include "mirror/object-inl.h"
#include "mirror/object-refvisitor-inl.h"
#include "mirror/object.h"
#include "mirror/object_array-alloc-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/object_array.h"
#include "mirror/object_reference-inl.h"
#include "mirror/object_reference.h"
#include "mirror/proxy.h"
#include "mirror/reference-inl.h"
#include "mirror/stack_trace_element.h"
#include "mirror/string-inl.h"
#include "mirror/throwable.h"
#include "mirror/var_handle.h"
#include "native/dalvik_system_DexFile.h"
#include "nativehelper/scoped_local_ref.h"
#include "nterp_helpers-inl.h"
#include "nterp_helpers.h"
#include "oat/image-inl.h"
#include "oat/oat.h"
#include "oat/oat_file-inl.h"
#include "oat/oat_file.h"
#include "oat/oat_file_assistant.h"
#include "oat/oat_file_manager.h"
#include "object_lock.h"
#include "profile/profile_compilation_info.h"
#include "runtime.h"
#include "runtime_callbacks.h"
#include "scoped_thread_state_change-inl.h"
#include "startup_completed_task.h"
#include "thread-inl.h"
#include "thread.h"
#include "thread_list.h"
#include "trace.h"
#include "transaction.h"
#include "vdex_file.h"
#include "verifier/class_verifier.h"
#include "verifier/verifier_deps.h"
#include "well_known_classes.h"

namespace art HIDDEN {

using android::base::StringPrintf;

static constexpr bool kCheckImageObjects = kIsDebugBuild;
static constexpr bool kVerifyArtMethodDeclaringClasses = kIsDebugBuild;

static void ThrowNoClassDefFoundError(const char* fmt, ...)
    __attribute__((__format__(__printf__, 1, 2)))
    REQUIRES_SHARED(Locks::mutator_lock_);
static void ThrowNoClassDefFoundError(const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Thread* self = Thread::Current();
  self->ThrowNewExceptionV("Ljava/lang/NoClassDefFoundError;", fmt, args);
  va_end(args);
}

static ObjPtr<mirror::Object> GetErroneousStateError(ObjPtr<mirror::Class> c)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::ClassExt> ext(c->GetExtData());
  if (ext == nullptr) {
    return nullptr;
  } else {
    return ext->GetErroneousStateError();
  }
}

static bool IsVerifyError(ObjPtr<mirror::Object> obj)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // This is slow, but we only use it for rethrowing an error, and for DCHECK.
  return obj->GetClass()->DescriptorEquals("Ljava/lang/VerifyError;");
}

// Helper for ThrowEarlierClassFailure. Throws the stored error.
static void HandleEarlierErroneousStateError(Thread* self,
                                             ClassLinker* class_linker,
                                             ObjPtr<mirror::Class> c)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Object> obj = GetErroneousStateError(c);
  DCHECK(obj != nullptr);
  self->AssertNoPendingException();
  DCHECK(!obj->IsClass());
  ObjPtr<mirror::Class> throwable_class = GetClassRoot<mirror::Throwable>(class_linker);
  ObjPtr<mirror::Class> error_class = obj->GetClass();
  CHECK(throwable_class->IsAssignableFrom(error_class));
  self->SetException(obj->AsThrowable());
  self->AssertPendingException();
}

static void UpdateClassAfterVerification(Handle<mirror::Class> klass,
                                         PointerSize pointer_size,
                                         verifier::FailureKind failure_kind)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  Runtime* runtime = Runtime::Current();
  ClassLinker* class_linker = runtime->GetClassLinker();
  if (klass->IsVerified() && (failure_kind == verifier::FailureKind::kNoFailure)) {
    klass->SetSkipAccessChecksFlagOnAllMethods(pointer_size);
  }

  // Now that the class has passed verification, try to set nterp entrypoints
  // to methods that currently use the switch interpreter.
  if (interpreter::CanRuntimeUseNterp()) {
    for (ArtMethod& m : klass->GetMethods(pointer_size)) {
      if (class_linker->IsQuickToInterpreterBridge(m.GetEntryPointFromQuickCompiledCode())) {
        runtime->GetInstrumentation()->InitializeMethodsCode(&m, /*aot_code=*/nullptr);
      }
    }
  }
}

// Callback responsible for making a batch of classes visibly initialized after ensuring
// visibility for all threads, either by using `membarrier()` or by running a checkpoint.
class ClassLinker::VisiblyInitializedCallback final
    : public Closure, public IntrusiveForwardListNode<VisiblyInitializedCallback> {
 public:
  explicit VisiblyInitializedCallback(ClassLinker* class_linker)
      : class_linker_(class_linker),
        num_classes_(0u),
        thread_visibility_counter_(0),
        barriers_() {
    std::fill_n(classes_, kMaxClasses, nullptr);
  }

  bool IsEmpty() const {
    DCHECK_LE(num_classes_, kMaxClasses);
    return num_classes_ == 0u;
  }

  bool IsFull() const {
    DCHECK_LE(num_classes_, kMaxClasses);
    return num_classes_ == kMaxClasses;
  }

  void AddClass(Thread* self, ObjPtr<mirror::Class> klass) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_EQ(klass->GetStatus(), ClassStatus::kInitialized);
    DCHECK(!IsFull());
    classes_[num_classes_] = self->GetJniEnv()->GetVm()->AddWeakGlobalRef(self, klass);
    ++num_classes_;
  }

  void AddBarrier(Barrier* barrier) {
    barriers_.push_front(barrier);
  }

  std::forward_list<Barrier*> GetAndClearBarriers() {
    std::forward_list<Barrier*> result;
    result.swap(barriers_);
    result.reverse();  // Return barriers in insertion order.
    return result;
  }

  void MakeVisible(Thread* self) {
    if (class_linker_->visibly_initialize_classes_with_membarier_) {
      // If the associated register command succeeded, this command should never fail.
      int membarrier_result = art::membarrier(MembarrierCommand::kPrivateExpedited);
      CHECK_EQ(membarrier_result, 0) << strerror(errno);
      MarkVisiblyInitialized(self);
    } else {
      DCHECK_EQ(thread_visibility_counter_.load(std::memory_order_relaxed), 0);
      size_t count = Runtime::Current()->GetThreadList()->RunCheckpoint(this);
      AdjustThreadVisibilityCounter(self, count);
    }
  }

  void Run(Thread* self) override {
    AdjustThreadVisibilityCounter(self, -1);
  }

 private:
  void AdjustThreadVisibilityCounter(Thread* self, ssize_t adjustment) {
    ssize_t old = thread_visibility_counter_.fetch_add(adjustment, std::memory_order_relaxed);
    if (old + adjustment == 0) {
      // All threads passed the checkpoint. Mark classes as visibly initialized.
      MarkVisiblyInitialized(self);
    }
  }

  void MarkVisiblyInitialized(Thread* self) {
    {
      ScopedObjectAccess soa(self);
      StackHandleScope<1u> hs(self);
      MutableHandle<mirror::Class> klass = hs.NewHandle<mirror::Class>(nullptr);
      JavaVMExt* vm = self->GetJniEnv()->GetVm();
      for (size_t i = 0, num = num_classes_; i != num; ++i) {
        klass.Assign(ObjPtr<mirror::Class>::DownCast(self->DecodeJObject(classes_[i])));
        vm->DeleteWeakGlobalRef(self, classes_[i]);
        if (klass != nullptr) {
          mirror::Class::SetStatus(klass, ClassStatus::kVisiblyInitialized, self);
          class_linker_->FixupStaticTrampolines(self, klass.Get());
        }
      }
      num_classes_ = 0u;
    }
    class_linker_->VisiblyInitializedCallbackDone(self, this);
  }

  // Making classes initialized in bigger batches helps with app startup for apps
  // that initialize a lot of classes by running fewer synchronization functions.
  // (On the other hand, bigger batches make class initialization checks more
  // likely to take a slow path but that is mitigated by making partially
  // filled buffers visibly initialized if we take the slow path many times.
  // See `Thread::kMakeVisiblyInitializedCounterTriggerCount`.)
  static constexpr size_t kMaxClasses = 48;

  ClassLinker* const class_linker_;
  size_t num_classes_;
  jweak classes_[kMaxClasses];

  // The thread visibility counter starts at 0 and it is incremented by the number of
  // threads that need to run this callback (by the thread that request the callback
  // to be run) and decremented once for each `Run()` execution. When it reaches 0,
  // whether after the increment or after a decrement, we know that `Run()` was executed
  // for all threads and therefore we can mark the classes as visibly initialized.
  // Used only if the preferred `membarrier()` command is unsupported.
  std::atomic<ssize_t> thread_visibility_counter_;

  // List of barries to `Pass()` for threads that wait for the callback to complete.
  std::forward_list<Barrier*> barriers_;
};

void ClassLinker::MakeInitializedClassesVisiblyInitialized(Thread* self, bool wait) {
  if (kRuntimeISA == InstructionSet::kX86 || kRuntimeISA == InstructionSet::kX86_64) {
    return;  // Nothing to do. Thanks to the x86 memory model classes skip the initialized status.
  }
  std::optional<Barrier> maybe_barrier;  // Avoid constructing the Barrier for `wait == false`.
  if (wait) {
    Locks::mutator_lock_->AssertNotHeld(self);
    maybe_barrier.emplace(0);
  }
  int wait_count = 0;
  VisiblyInitializedCallback* callback = nullptr;
  {
    MutexLock lock(self, visibly_initialized_callback_lock_);
    if (visibly_initialized_callback_ != nullptr && !visibly_initialized_callback_->IsEmpty()) {
      callback = visibly_initialized_callback_.release();
      running_visibly_initialized_callbacks_.push_front(*callback);
    }
    if (wait) {
      DCHECK(maybe_barrier.has_value());
      Barrier* barrier = std::addressof(*maybe_barrier);
      for (VisiblyInitializedCallback& cb : running_visibly_initialized_callbacks_) {
        cb.AddBarrier(barrier);
        ++wait_count;
      }
    }
  }
  if (callback != nullptr) {
    callback->MakeVisible(self);
  }
  if (wait_count != 0) {
    DCHECK(maybe_barrier.has_value());
    maybe_barrier->Increment(self, wait_count);
  }
}

void ClassLinker::VisiblyInitializedCallbackDone(Thread* self,
                                                 VisiblyInitializedCallback* callback) {
  MutexLock lock(self, visibly_initialized_callback_lock_);
  // Pass the barriers if requested.
  for (Barrier* barrier : callback->GetAndClearBarriers()) {
    barrier->Pass(self);
  }
  // Remove the callback from the list of running callbacks.
  auto before = running_visibly_initialized_callbacks_.before_begin();
  auto it = running_visibly_initialized_callbacks_.begin();
  DCHECK(it != running_visibly_initialized_callbacks_.end());
  while (std::addressof(*it) != callback) {
    before = it;
    ++it;
    DCHECK(it != running_visibly_initialized_callbacks_.end());
  }
  running_visibly_initialized_callbacks_.erase_after(before);
  // Reuse or destroy the callback object.
  if (visibly_initialized_callback_ == nullptr) {
    visibly_initialized_callback_.reset(callback);
  } else {
    delete callback;
  }
}

void ClassLinker::ForceClassInitialized(Thread* self, Handle<mirror::Class> klass) {
  ClassLinker::VisiblyInitializedCallback* cb = MarkClassInitialized(self, klass);
  if (cb != nullptr) {
    cb->MakeVisible(self);
  }
  ScopedThreadSuspension sts(self, ThreadState::kSuspended);
  MakeInitializedClassesVisiblyInitialized(self, /*wait=*/true);
}

ClassLinker::VisiblyInitializedCallback* ClassLinker::MarkClassInitialized(
    Thread* self, Handle<mirror::Class> klass) {
  if (kRuntimeISA == InstructionSet::kX86 || kRuntimeISA == InstructionSet::kX86_64) {
    // Thanks to the x86 memory model, we do not need any memory fences and
    // we can immediately mark the class as visibly initialized.
    mirror::Class::SetStatus(klass, ClassStatus::kVisiblyInitialized, self);
    FixupStaticTrampolines(self, klass.Get());
    return nullptr;
  }
  if (Runtime::Current()->IsActiveTransaction()) {
    // Transactions are single-threaded, so we can mark the class as visibly intialized.
    // (Otherwise we'd need to track the callback's entry in the transaction for rollback.)
    mirror::Class::SetStatus(klass, ClassStatus::kVisiblyInitialized, self);
    FixupStaticTrampolines(self, klass.Get());
    return nullptr;
  }
  mirror::Class::SetStatus(klass, ClassStatus::kInitialized, self);
  MutexLock lock(self, visibly_initialized_callback_lock_);
  if (visibly_initialized_callback_ == nullptr) {
    visibly_initialized_callback_.reset(new VisiblyInitializedCallback(this));
  }
  DCHECK(!visibly_initialized_callback_->IsFull());
  visibly_initialized_callback_->AddClass(self, klass.Get());

  if (visibly_initialized_callback_->IsFull()) {
    VisiblyInitializedCallback* callback = visibly_initialized_callback_.release();
    running_visibly_initialized_callbacks_.push_front(*callback);
    return callback;
  } else {
    return nullptr;
  }
}

const void* ClassLinker::RegisterNative(
    Thread* self, ArtMethod* method, const void* native_method) {
  CHECK(method->IsNative()) << method->PrettyMethod();
  CHECK(native_method != nullptr) << method->PrettyMethod();
  void* new_native_method = nullptr;
  Runtime* runtime = Runtime::Current();
  runtime->GetRuntimeCallbacks()->RegisterNativeMethod(method,
                                                       native_method,
                                                       /*out*/&new_native_method);
  if (method->IsCriticalNative()) {
    MutexLock lock(self, critical_native_code_with_clinit_check_lock_);
    // Remove old registered method if any.
    auto it = critical_native_code_with_clinit_check_.find(method);
    if (it != critical_native_code_with_clinit_check_.end()) {
      critical_native_code_with_clinit_check_.erase(it);
    }
    // To ensure correct memory visibility, we need the class to be visibly
    // initialized before we can set the JNI entrypoint.
    if (method->GetDeclaringClass()->IsVisiblyInitialized()) {
      method->SetEntryPointFromJni(new_native_method);
    } else {
      critical_native_code_with_clinit_check_.emplace(method, new_native_method);
    }
  } else {
    method->SetEntryPointFromJni(new_native_method);
  }
  return new_native_method;
}

void ClassLinker::UnregisterNative(Thread* self, ArtMethod* method) {
  CHECK(method->IsNative()) << method->PrettyMethod();
  // Restore stub to lookup native pointer via dlsym.
  if (method->IsCriticalNative()) {
    MutexLock lock(self, critical_native_code_with_clinit_check_lock_);
    auto it = critical_native_code_with_clinit_check_.find(method);
    if (it != critical_native_code_with_clinit_check_.end()) {
      critical_native_code_with_clinit_check_.erase(it);
    }
    method->SetEntryPointFromJni(GetJniDlsymLookupCriticalStub());
  } else {
    method->SetEntryPointFromJni(GetJniDlsymLookupStub());
  }
}

const void* ClassLinker::GetRegisteredNative(Thread* self, ArtMethod* method) {
  if (method->IsCriticalNative()) {
    MutexLock lock(self, critical_native_code_with_clinit_check_lock_);
    auto it = critical_native_code_with_clinit_check_.find(method);
    if (it != critical_native_code_with_clinit_check_.end()) {
      return it->second;
    }
    const void* native_code = method->GetEntryPointFromJni();
    return IsJniDlsymLookupCriticalStub(native_code) ? nullptr : native_code;
  } else {
    const void* native_code = method->GetEntryPointFromJni();
    return IsJniDlsymLookupStub(native_code) ? nullptr : native_code;
  }
}

void ClassLinker::ThrowEarlierClassFailure(ObjPtr<mirror::Class> c,
                                           bool wrap_in_no_class_def,
                                           bool log) {
  // The class failed to initialize on a previous attempt, so we want to throw
  // a NoClassDefFoundError (v2 2.17.5).  The exception to this rule is if we
  // failed in verification, in which case v2 5.4.1 says we need to re-throw
  // the previous error.
  Runtime* const runtime = Runtime::Current();
  if (!runtime->IsAotCompiler()) {  // Give info if this occurs at runtime.
    std::string extra;
    ObjPtr<mirror::Object> verify_error = GetErroneousStateError(c);
    if (verify_error != nullptr) {
      DCHECK(!verify_error->IsClass());
      extra = verify_error->AsThrowable()->Dump();
    }
    if (log) {
      LOG(INFO) << "Rejecting re-init on previously-failed class " << c->PrettyClass()
                << ": " << extra;
    }
  }

  CHECK(c->IsErroneous()) << c->PrettyClass() << " " << c->GetStatus();
  Thread* self = Thread::Current();
  if (runtime->IsAotCompiler()) {
    // At compile time, accurate errors and NCDFE are disabled to speed compilation.
    ObjPtr<mirror::Throwable> pre_allocated = runtime->GetPreAllocatedNoClassDefFoundError();
    self->SetException(pre_allocated);
  } else {
    ObjPtr<mirror::Object> erroneous_state_error = GetErroneousStateError(c);
    if (erroneous_state_error != nullptr) {
      // Rethrow stored error.
      HandleEarlierErroneousStateError(self, this, c);
    }
    // TODO This might be wrong if we hit an OOME while allocating the ClassExt. In that case we
    // might have meant to go down the earlier if statement with the original error but it got
    // swallowed by the OOM so we end up here.
    if (erroneous_state_error == nullptr ||
        (wrap_in_no_class_def && !IsVerifyError(erroneous_state_error))) {
      // If there isn't a recorded earlier error, or this is a repeat throw from initialization,
      // the top-level exception must be a NoClassDefFoundError. The potentially already pending
      // exception will be a cause.
      self->ThrowNewWrappedException("Ljava/lang/NoClassDefFoundError;",
                                     c->PrettyDescriptor().c_str());
    }
  }
}

static void VlogClassInitializationFailure(Handle<mirror::Class> klass)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (VLOG_IS_ON(class_linker)) {
    std::string temp;
    LOG(INFO) << "Failed to initialize class " << klass->GetDescriptor(&temp) << " from "
              << klass->GetLocation() << "\n" << Thread::Current()->GetException()->Dump();
  }
}

static void WrapExceptionInInitializer(Handle<mirror::Class> klass)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  Thread* self = Thread::Current();

  ObjPtr<mirror::Throwable> cause = self->GetException();
  CHECK(cause != nullptr);

  // Boot classpath classes should not fail initialization. This is a consistency debug check.
  // This cannot in general be guaranteed, but in all likelihood leads to breakage down the line.
  if (klass->GetClassLoader() == nullptr && !Runtime::Current()->IsAotCompiler()) {
    std::string tmp;
    // We want to LOG(FATAL) on debug builds since this really shouldn't be happening but we need to
    // make sure to only do it if we don't have AsyncExceptions being thrown around since those
    // could have caused the error.
    bool known_impossible = kIsDebugBuild && !Runtime::Current()->AreAsyncExceptionsThrown();
    LOG(known_impossible ? FATAL : WARNING) << klass->GetDescriptor(&tmp)
                                            << " failed initialization: "
                                            << self->GetException()->Dump();
  }

  // We only wrap non-Error exceptions; an Error can just be used as-is.
  if (!cause->IsError()) {
    self->ThrowNewWrappedException("Ljava/lang/ExceptionInInitializerError;", nullptr);
  }
  VlogClassInitializationFailure(klass);
}

static bool RegisterMemBarrierForClassInitialization() {
  if (kRuntimeISA == InstructionSet::kX86 || kRuntimeISA == InstructionSet::kX86_64) {
    // Thanks to the x86 memory model, classes skip the initialized status, so there is no need
    // to use `membarrier()` or other synchronization for marking classes visibly initialized.
    return false;
  }
  int membarrier_result = art::membarrier(MembarrierCommand::kRegisterPrivateExpedited);
  return membarrier_result == 0;
}

ClassLinker::ClassLinker(InternTable* intern_table, bool fast_class_not_found_exceptions)
    : boot_class_table_(new ClassTable()),
      failed_dex_cache_class_lookups_(0),
      class_roots_(nullptr),
      find_array_class_cache_next_victim_(0),
      init_done_(false),
      log_new_roots_(false),
      intern_table_(intern_table),
      fast_class_not_found_exceptions_(fast_class_not_found_exceptions),
      jni_dlsym_lookup_trampoline_(nullptr),
      jni_dlsym_lookup_critical_trampoline_(nullptr),
      quick_resolution_trampoline_(nullptr),
      quick_imt_conflict_trampoline_(nullptr),
      quick_generic_jni_trampoline_(nullptr),
      quick_to_interpreter_bridge_trampoline_(nullptr),
      nterp_trampoline_(nullptr),
      image_pointer_size_(kRuntimePointerSize),
      visibly_initialized_callback_lock_("visibly initialized callback lock"),
      visibly_initialized_callback_(nullptr),
      running_visibly_initialized_callbacks_(),
      visibly_initialize_classes_with_membarier_(RegisterMemBarrierForClassInitialization()),
      critical_native_code_with_clinit_check_lock_("critical native code with clinit check lock"),
      critical_native_code_with_clinit_check_(),
      cha_(Runtime::Current()->IsAotCompiler() ? nullptr : new ClassHierarchyAnalysis()) {
  // For CHA disabled during Aot, see b/34193647.

  CHECK(intern_table_ != nullptr);
  static_assert(kFindArrayCacheSize == arraysize(find_array_class_cache_),
                "Array cache size wrong.");
  std::fill_n(find_array_class_cache_, kFindArrayCacheSize, GcRoot<mirror::Class>(nullptr));
}

void ClassLinker::CheckSystemClass(Thread* self, Handle<mirror::Class> c1, const char* descriptor) {
  ObjPtr<mirror::Class> c2 = FindSystemClass(self, descriptor);
  if (c2 == nullptr) {
    LOG(FATAL) << "Could not find class " << descriptor;
    UNREACHABLE();
  }
  if (c1.Get() != c2) {
    std::ostringstream os1, os2;
    c1->DumpClass(os1, mirror::Class::kDumpClassFullDetail);
    c2->DumpClass(os2, mirror::Class::kDumpClassFullDetail);
    LOG(FATAL) << "InitWithoutImage: Class mismatch for " << descriptor
               << ". This is most likely the result of a broken build. Make sure that "
               << "libcore and art projects match.\n\n"
               << os1.str() << "\n\n" << os2.str();
    UNREACHABLE();
  }
}

ObjPtr<mirror::IfTable> AllocIfTable(Thread* self,
                                     size_t ifcount,
                                     ObjPtr<mirror::Class> iftable_class)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(iftable_class->IsArrayClass());
  DCHECK(iftable_class->GetComponentType()->IsObjectClass());
  return ObjPtr<mirror::IfTable>::DownCast(ObjPtr<mirror::ObjectArray<mirror::Object>>(
      mirror::IfTable::Alloc(self, iftable_class, ifcount * mirror::IfTable::kMax)));
}

bool ClassLinker::InitWithoutImage(std::vector<std::unique_ptr<const DexFile>> boot_class_path,
                                   std::string* error_msg) {
  VLOG(startup) << "ClassLinker::Init";

  Thread* const self = Thread::Current();
  Runtime* const runtime = Runtime::Current();
  gc::Heap* const heap = runtime->GetHeap();

  CHECK(!heap->HasBootImageSpace()) << "Runtime has image. We should use it.";
  CHECK(!init_done_);

  // Use the pointer size from the runtime since we are probably creating the image.
  image_pointer_size_ = InstructionSetPointerSize(runtime->GetInstructionSet());

  // java_lang_Class comes first, it's needed for AllocClass
  // The GC can't handle an object with a null class since we can't get the size of this object.
  heap->IncrementDisableMovingGC(self);
  StackHandleScope<64> hs(self);  // 64 is picked arbitrarily.
  auto class_class_size = mirror::Class::ClassClassSize(image_pointer_size_);
  // Allocate the object as non-movable so that there are no cases where Object::IsClass returns
  // the incorrect result when comparing to-space vs from-space.
  Handle<mirror::Class> java_lang_Class(hs.NewHandle(ObjPtr<mirror::Class>::DownCast(
      heap->AllocNonMovableObject(self, nullptr, class_class_size, VoidFunctor()))));
  CHECK(java_lang_Class != nullptr);
  java_lang_Class->SetClassFlags(mirror::kClassFlagClass);
  java_lang_Class->SetClass(java_lang_Class.Get());
  if (kUseBakerReadBarrier) {
    java_lang_Class->AssertReadBarrierState();
  }
  java_lang_Class->SetClassSize(class_class_size);
  java_lang_Class->SetPrimitiveType(Primitive::kPrimNot);
  heap->DecrementDisableMovingGC(self);
  // AllocClass(ObjPtr<mirror::Class>) can now be used

  // Class[] is used for reflection support.
  auto class_array_class_size = mirror::ObjectArray<mirror::Class>::ClassSize(image_pointer_size_);
  Handle<mirror::Class> class_array_class(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(), class_array_class_size)));
  class_array_class->SetComponentType(java_lang_Class.Get());

  // java_lang_Object comes next so that object_array_class can be created.
  Handle<mirror::Class> java_lang_Object(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(), mirror::Object::ClassSize(image_pointer_size_))));
  CHECK(java_lang_Object != nullptr);
  // backfill Object as the super class of Class.
  java_lang_Class->SetSuperClass(java_lang_Object.Get());
  mirror::Class::SetStatus(java_lang_Object, ClassStatus::kLoaded, self);

  java_lang_Object->SetObjectSize(sizeof(mirror::Object));
  // Allocate in non-movable so that it's possible to check if a JNI weak global ref has been
  // cleared without triggering the read barrier and unintentionally mark the sentinel alive.
  runtime->SetSentinel(heap->AllocNonMovableObject(self,
                                                   java_lang_Object.Get(),
                                                   java_lang_Object->GetObjectSize(),
                                                   VoidFunctor()));

  // Initialize the SubtypeCheck bitstring for java.lang.Object and java.lang.Class.
  if (kBitstringSubtypeCheckEnabled) {
    // It might seem the lock here is unnecessary, however all the SubtypeCheck
    // functions are annotated to require locks all the way down.
    //
    // We take the lock here to avoid using NO_THREAD_SAFETY_ANALYSIS.
    MutexLock subtype_check_lock(Thread::Current(), *Locks::subtype_check_lock_);
    SubtypeCheck<ObjPtr<mirror::Class>>::EnsureInitialized(java_lang_Object.Get());
    SubtypeCheck<ObjPtr<mirror::Class>>::EnsureInitialized(java_lang_Class.Get());
  }

  // Object[] next to hold class roots.
  Handle<mirror::Class> object_array_class(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(),
                 mirror::ObjectArray<mirror::Object>::ClassSize(image_pointer_size_))));
  object_array_class->SetComponentType(java_lang_Object.Get());

  // Setup java.lang.String.
  //
  // We make this class non-movable for the unlikely case where it were to be
  // moved by a sticky-bit (minor) collection when using the Generational
  // Concurrent Copying (CC) collector, potentially creating a stale reference
  // in the `klass_` field of one of its instances allocated in the Large-Object
  // Space (LOS) -- see the comment about the dirty card scanning logic in
  // art::gc::collector::ConcurrentCopying::MarkingPhase.
  Handle<mirror::Class> java_lang_String(hs.NewHandle(
      AllocClass</* kMovable= */ false>(
          self, java_lang_Class.Get(), mirror::String::ClassSize(image_pointer_size_))));
  java_lang_String->SetStringClass();
  mirror::Class::SetStatus(java_lang_String, ClassStatus::kResolved, self);

  // Setup java.lang.ref.Reference.
  Handle<mirror::Class> java_lang_ref_Reference(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(), mirror::Reference::ClassSize(image_pointer_size_))));
  java_lang_ref_Reference->SetObjectSize(mirror::Reference::InstanceSize());
  mirror::Class::SetStatus(java_lang_ref_Reference, ClassStatus::kResolved, self);

  // Create storage for root classes, save away our work so far (requires descriptors).
  class_roots_ = GcRoot<mirror::ObjectArray<mirror::Class>>(
      mirror::ObjectArray<mirror::Class>::Alloc(self,
                                                object_array_class.Get(),
                                                static_cast<int32_t>(ClassRoot::kMax)));
  CHECK(!class_roots_.IsNull());
  SetClassRoot(ClassRoot::kJavaLangClass, java_lang_Class.Get());
  SetClassRoot(ClassRoot::kJavaLangObject, java_lang_Object.Get());
  SetClassRoot(ClassRoot::kClassArrayClass, class_array_class.Get());
  SetClassRoot(ClassRoot::kObjectArrayClass, object_array_class.Get());
  SetClassRoot(ClassRoot::kJavaLangString, java_lang_String.Get());
  SetClassRoot(ClassRoot::kJavaLangRefReference, java_lang_ref_Reference.Get());

  // Fill in the empty iftable. Needs to be done after the kObjectArrayClass root is set.
  java_lang_Object->SetIfTable(AllocIfTable(self, 0, object_array_class.Get()));

  // Create array interface entries to populate once we can load system classes.
  object_array_class->SetIfTable(AllocIfTable(self, 2, object_array_class.Get()));
  DCHECK_EQ(GetArrayIfTable(), object_array_class->GetIfTable());

  // Setup the primitive type classes.
  CreatePrimitiveClass(self, Primitive::kPrimBoolean, ClassRoot::kPrimitiveBoolean);
  CreatePrimitiveClass(self, Primitive::kPrimByte, ClassRoot::kPrimitiveByte);
  CreatePrimitiveClass(self, Primitive::kPrimChar, ClassRoot::kPrimitiveChar);
  CreatePrimitiveClass(self, Primitive::kPrimShort, ClassRoot::kPrimitiveShort);
  CreatePrimitiveClass(self, Primitive::kPrimInt, ClassRoot::kPrimitiveInt);
  CreatePrimitiveClass(self, Primitive::kPrimLong, ClassRoot::kPrimitiveLong);
  CreatePrimitiveClass(self, Primitive::kPrimFloat, ClassRoot::kPrimitiveFloat);
  CreatePrimitiveClass(self, Primitive::kPrimDouble, ClassRoot::kPrimitiveDouble);
  CreatePrimitiveClass(self, Primitive::kPrimVoid, ClassRoot::kPrimitiveVoid);

  // Allocate the primitive array classes. We need only the native pointer
  // array at this point (int[] or long[], depending on architecture) but
  // we shall perform the same setup steps for all primitive array classes.
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveBoolean, ClassRoot::kBooleanArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveByte, ClassRoot::kByteArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveChar, ClassRoot::kCharArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveShort, ClassRoot::kShortArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveInt, ClassRoot::kIntArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveLong, ClassRoot::kLongArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveFloat, ClassRoot::kFloatArrayClass);
  AllocPrimitiveArrayClass(self, ClassRoot::kPrimitiveDouble, ClassRoot::kDoubleArrayClass);

  // now that these are registered, we can use AllocClass() and AllocObjectArray

  // Set up DexCache. This cannot be done later since AppendToBootClassPath calls AllocDexCache.
  Handle<mirror::Class> java_lang_DexCache(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(), mirror::DexCache::ClassSize(image_pointer_size_))));
  SetClassRoot(ClassRoot::kJavaLangDexCache, java_lang_DexCache.Get());
  java_lang_DexCache->SetDexCacheClass();
  java_lang_DexCache->SetObjectSize(mirror::DexCache::InstanceSize());
  mirror::Class::SetStatus(java_lang_DexCache, ClassStatus::kResolved, self);


  // Setup dalvik.system.ClassExt
  Handle<mirror::Class> dalvik_system_ClassExt(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(), mirror::ClassExt::ClassSize(image_pointer_size_))));
  SetClassRoot(ClassRoot::kDalvikSystemClassExt, dalvik_system_ClassExt.Get());
  mirror::Class::SetStatus(dalvik_system_ClassExt, ClassStatus::kResolved, self);

  // Set up array classes for string, field, method
  Handle<mirror::Class> object_array_string(hs.NewHandle(
      AllocClass(self, java_lang_Class.Get(),
                 mirror::ObjectArray<mirror::String>::ClassSize(image_pointer_size_))));
  object_array_string->SetComponentType(java_lang_String.Get());
  SetClassRoot(ClassRoot::kJavaLangStringArrayClass, object_array_string.Get());

  LinearAlloc* linear_alloc = runtime->GetLinearAlloc();
  // Create runtime resolution and imt conflict methods.
  runtime->SetResolutionMethod(runtime->CreateResolutionMethod());
  runtime->SetImtConflictMethod(runtime->CreateImtConflictMethod(linear_alloc));
  runtime->SetImtUnimplementedMethod(runtime->CreateImtConflictMethod(linear_alloc));

  // Setup boot_class_path_ and register class_path now that we can use AllocObjectArray to create
  // DexCache instances. Needs to be after String, Field, Method arrays since AllocDexCache uses
  // these roots.
  if (boot_class_path.empty()) {
    *error_msg = "Boot classpath is empty.";
    return false;
  }
  for (auto& dex_file : boot_class_path) {
    if (dex_file == nullptr) {
      *error_msg = "Null dex file.";
      return false;
    }
    AppendToBootClassPath(self, dex_file.get());
    boot_dex_files_.push_back(std::move(dex_file));
  }

  // now we can use FindSystemClass

  // Set up GenericJNI entrypoint. That is mainly a hack for common_compiler_test.h so that
  // we do not need friend classes or a publicly exposed setter.
  quick_generic_jni_trampoline_ = GetQuickGenericJniStub();
  if (!runtime->IsAotCompiler()) {
    // We need to set up the generic trampolines since we don't have an image.
    jni_dlsym_lookup_trampoline_ = GetJniDlsymLookupStub();
    jni_dlsym_lookup_critical_trampoline_ = GetJniDlsymLookupCriticalStub();
    quick_resolution_trampoline_ = GetQuickResolutionStub();
    quick_imt_conflict_trampoline_ = GetQuickImtConflictStub();
    quick_generic_jni_trampoline_ = GetQuickGenericJniStub();
    quick_to_interpreter_bridge_trampoline_ = GetQuickToInterpreterBridge();
    nterp_trampoline_ = interpreter::GetNterpEntryPoint();
  }

  // Object, String, ClassExt and DexCache need to be rerun through FindSystemClass to finish init
  mirror::Class::SetStatus(java_lang_Object, ClassStatus::kNotReady, self);
  CheckSystemClass(self, java_lang_Object, "Ljava/lang/Object;");
  CHECK_EQ(java_lang_Object->GetObjectSize(), mirror::Object::InstanceSize());
  mirror::Class::SetStatus(java_lang_String, ClassStatus::kNotReady, self);
  CheckSystemClass(self, java_lang_String, "Ljava/lang/String;");
  mirror::Class::SetStatus(java_lang_DexCache, ClassStatus::kNotReady, self);
  CheckSystemClass(self, java_lang_DexCache, "Ljava/lang/DexCache;");
  CHECK_EQ(java_lang_DexCache->GetObjectSize(), mirror::DexCache::InstanceSize());
  mirror::Class::SetStatus(dalvik_system_ClassExt, ClassStatus::kNotReady, self);
  CheckSystemClass(self, dalvik_system_ClassExt, "Ldalvik/system/ClassExt;");
  CHECK_EQ(dalvik_system_ClassExt->GetObjectSize(), mirror::ClassExt::InstanceSize());

  // Run Class through FindSystemClass. This initializes the dex_cache_ fields and register it
  // in class_table_.
  CheckSystemClass(self, java_lang_Class, "Ljava/lang/Class;");

  // Setup core array classes, i.e. Object[], String[] and Class[] and primitive
  // arrays - can't be done until Object has a vtable and component classes are loaded.
  FinishCoreArrayClassSetup(ClassRoot::kObjectArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kClassArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kJavaLangStringArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kBooleanArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kByteArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kCharArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kShortArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kIntArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kLongArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kFloatArrayClass);
  FinishCoreArrayClassSetup(ClassRoot::kDoubleArrayClass);

  // Setup the single, global copy of "iftable".
  auto java_lang_Cloneable = hs.NewHandle(FindSystemClass(self, "Ljava/lang/Cloneable;"));
  CHECK(java_lang_Cloneable != nullptr);
  auto java_io_Serializable = hs.NewHandle(FindSystemClass(self, "Ljava/io/Serializable;"));
  CHECK(java_io_Serializable != nullptr);
  // We assume that Cloneable/Serializable don't have superinterfaces -- normally we'd have to
  // crawl up and explicitly list all of the supers as well.
  object_array_class->GetIfTable()->SetInterface(0, java_lang_Cloneable.Get());
  object_array_class->GetIfTable()->SetInterface(1, java_io_Serializable.Get());

  // Check Class[] and Object[]'s interfaces.
  CHECK_EQ(java_lang_Cloneable.Get(), class_array_class->GetDirectInterface(0));
  CHECK_EQ(java_io_Serializable.Get(), class_array_class->GetDirectInterface(1));
  CHECK_EQ(java_lang_Cloneable.Get(), object_array_class->GetDirectInterface(0));
  CHECK_EQ(java_io_Serializable.Get(), object_array_class->GetDirectInterface(1));

  CHECK_EQ(object_array_string.Get(),
           FindSystemClass(self, GetClassRootDescriptor(ClassRoot::kJavaLangStringArrayClass)));

  // End of special init trickery, all subsequent classes may be loaded via FindSystemClass.

  // Create java.lang.reflect.Proxy root.
  SetClassRoot(ClassRoot::kJavaLangReflectProxy,
               FindSystemClass(self, "Ljava/lang/reflect/Proxy;"));

  // Create java.lang.reflect.Field.class root.
  ObjPtr<mirror::Class> class_root = FindSystemClass(self, "Ljava/lang/reflect/Field;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectField, class_root);

  // Create java.lang.reflect.Field array root.
  class_root = FindSystemClass(self, "[Ljava/lang/reflect/Field;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectFieldArrayClass, class_root);

  // Create java.lang.reflect.Constructor.class root and array root.
  class_root = FindSystemClass(self, "Ljava/lang/reflect/Constructor;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectConstructor, class_root);
  class_root = FindSystemClass(self, "[Ljava/lang/reflect/Constructor;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectConstructorArrayClass, class_root);

  // Create java.lang.reflect.Method.class root and array root.
  class_root = FindSystemClass(self, "Ljava/lang/reflect/Method;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectMethod, class_root);
  class_root = FindSystemClass(self, "[Ljava/lang/reflect/Method;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangReflectMethodArrayClass, class_root);

  // Create java.lang.invoke.CallSite.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/CallSite;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeCallSite, class_root);

  // Create java.lang.invoke.MethodType.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/MethodType;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeMethodType, class_root);

  // Create java.lang.invoke.MethodHandleImpl.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/MethodHandleImpl;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeMethodHandleImpl, class_root);
  SetClassRoot(ClassRoot::kJavaLangInvokeMethodHandle, class_root->GetSuperClass());

  // Create java.lang.invoke.MethodHandles.Lookup.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/MethodHandles$Lookup;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeMethodHandlesLookup, class_root);

  // Create java.lang.invoke.VarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/VarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeVarHandle, class_root);

  // Create java.lang.invoke.FieldVarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/FieldVarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeFieldVarHandle, class_root);

  // Create java.lang.invoke.StaticFieldVarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/StaticFieldVarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeStaticFieldVarHandle, class_root);

  // Create java.lang.invoke.ArrayElementVarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/ArrayElementVarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeArrayElementVarHandle, class_root);

  // Create java.lang.invoke.ByteArrayViewVarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/ByteArrayViewVarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeByteArrayViewVarHandle, class_root);

  // Create java.lang.invoke.ByteBufferViewVarHandle.class root
  class_root = FindSystemClass(self, "Ljava/lang/invoke/ByteBufferViewVarHandle;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kJavaLangInvokeByteBufferViewVarHandle, class_root);

  class_root = FindSystemClass(self, "Ldalvik/system/EmulatedStackFrame;");
  CHECK(class_root != nullptr);
  SetClassRoot(ClassRoot::kDalvikSystemEmulatedStackFrame, class_root);

  // java.lang.ref classes need to be specially flagged, but otherwise are normal classes
  // finish initializing Reference class
  mirror::Class::SetStatus(java_lang_ref_Reference, ClassStatus::kNotReady, self);
  CheckSystemClass(self, java_lang_ref_Reference, "Ljava/lang/ref/Reference;");
  CHECK_EQ(java_lang_ref_Reference->GetObjectSize(), mirror::Reference::InstanceSize());
  CHECK_EQ(java_lang_ref_Reference->GetClassSize(),
           mirror::Reference::ClassSize(image_pointer_size_));
  class_root = FindSystemClass(self, "Ljava/lang/ref/FinalizerReference;");
  CHECK_EQ(class_root->GetClassFlags(), mirror::kClassFlagNormal);
  class_root->SetClassFlags(class_root->GetClassFlags() | mirror::kClassFlagFinalizerReference);
  class_root = FindSystemClass(self, "Ljava/lang/ref/PhantomReference;");
  CHECK_EQ(class_root->GetClassFlags(), mirror::kClassFlagNormal);
  class_root->SetClassFlags(class_root->GetClassFlags() | mirror::kClassFlagPhantomReference);
  class_root = FindSystemClass(self, "Ljava/lang/ref/SoftReference;");
  CHECK_EQ(class_root->GetClassFlags(), mirror::kClassFlagNormal);
  class_root->SetClassFlags(class_root->GetClassFlags() | mirror::kClassFlagSoftReference);
  class_root = FindSystemClass(self, "Ljava/lang/ref/WeakReference;");
  CHECK_EQ(class_root->GetClassFlags(), mirror::kClassFlagNormal);
  class_root->SetClassFlags(class_root->GetClassFlags() | mirror::kClassFlagWeakReference);

  // Setup the ClassLoader, verifying the object_size_.
  class_root = FindSystemClass(self, "Ljava/lang/ClassLoader;");
  class_root->SetClassLoaderClass();
  CHECK_EQ(class_root->GetObjectSize(), mirror::ClassLoader::InstanceSize());
  SetClassRoot(ClassRoot::kJavaLangClassLoader, class_root);

  // Set up java.lang.Throwable, java.lang.ClassNotFoundException, and
  // java.lang.StackTraceElement as a convenience.
  SetClassRoot(ClassRoot::kJavaLangThrowable, FindSystemClass(self, "Ljava/lang/Throwable;"));
  SetClassRoot(ClassRoot::kJavaLangClassNotFoundException,
               FindSystemClass(self, "Ljava/lang/ClassNotFoundException;"));
  SetClassRoot(ClassRoot::kJavaLangStackTraceElement,
               FindSystemClass(self, "Ljava/lang/StackTraceElement;"));
  SetClassRoot(ClassRoot::kJavaLangStackTraceElementArrayClass,
               FindSystemClass(self, "[Ljava/lang/StackTraceElement;"));
  SetClassRoot(ClassRoot::kJavaLangClassLoaderArrayClass,
               FindSystemClass(self, "[Ljava/lang/ClassLoader;"));

  // Create conflict tables that depend on the class linker.
  runtime->FixupConflictTables();

  FinishInit(self);

  VLOG(startup) << "ClassLinker::InitFromCompiler exiting";

  return true;
}

static void CreateStringInitBindings(Thread* self, ClassLinker* class_linker)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Find String.<init> -> StringFactory bindings.
  ObjPtr<mirror::Class> string_factory_class =
      class_linker->FindSystemClass(self, "Ljava/lang/StringFactory;");
  CHECK(string_factory_class != nullptr);
  ObjPtr<mirror::Class> string_class = GetClassRoot<mirror::String>(class_linker);
  WellKnownClasses::InitStringInit(string_class, string_factory_class);
  // Update the primordial thread.
  self->InitStringEntryPoints();
}

void ClassLinker::FinishInit(Thread* self) {
  VLOG(startup) << "ClassLinker::FinishInit entering";

  CreateStringInitBindings(self, this);

  // Let the heap know some key offsets into java.lang.ref instances
  // Note: we hard code the field indexes here rather than using FindInstanceField
  // as the types of the field can't be resolved prior to the runtime being
  // fully initialized
  StackHandleScope<3> hs(self);
  Handle<mirror::Class> java_lang_ref_Reference =
      hs.NewHandle(GetClassRoot<mirror::Reference>(this));
  Handle<mirror::Class> java_lang_ref_FinalizerReference =
      hs.NewHandle(FindSystemClass(self, "Ljava/lang/ref/FinalizerReference;"));

  ArtField* pendingNext = java_lang_ref_Reference->GetInstanceField(0);
  CHECK_STREQ(pendingNext->GetName(), "pendingNext");
  CHECK_STREQ(pendingNext->GetTypeDescriptor(), "Ljava/lang/ref/Reference;");

  ArtField* queue = java_lang_ref_Reference->GetInstanceField(1);
  CHECK_STREQ(queue->GetName(), "queue");
  CHECK_STREQ(queue->GetTypeDescriptor(), "Ljava/lang/ref/ReferenceQueue;");

  ArtField* queueNext = java_lang_ref_Reference->GetInstanceField(2);
  CHECK_STREQ(queueNext->GetName(), "queueNext");
  CHECK_STREQ(queueNext->GetTypeDescriptor(), "Ljava/lang/ref/Reference;");

  ArtField* referent = java_lang_ref_Reference->GetInstanceField(3);
  CHECK_STREQ(referent->GetName(), "referent");
  CHECK_STREQ(referent->GetTypeDescriptor(), "Ljava/lang/Object;");

  ArtField* zombie = java_lang_ref_FinalizerReference->GetInstanceField(2);
  CHECK_STREQ(zombie->GetName(), "zombie");
  CHECK_STREQ(zombie->GetTypeDescriptor(), "Ljava/lang/Object;");

  // ensure all class_roots_ are initialized
  for (size_t i = 0; i < static_cast<size_t>(ClassRoot::kMax); i++) {
    ClassRoot class_root = static_cast<ClassRoot>(i);
    ObjPtr<mirror::Class> klass = GetClassRoot(class_root);
    CHECK(klass != nullptr);
    DCHECK(klass->IsArrayClass() || klass->IsPrimitive() || klass->GetDexCache() != nullptr);
    // note SetClassRoot does additional validation.
    // if possible add new checks there to catch errors early
  }

  CHECK(GetArrayIfTable() != nullptr);

  // disable the slow paths in FindClass and CreatePrimitiveClass now
  // that Object, Class, and Object[] are setup
  init_done_ = true;

  // Under sanitization, the small carve-out to handle stack overflow might not be enough to
  // initialize the StackOverflowError class (as it might require running the verifier). Instead,
  // ensure that the class will be initialized.
  if (kMemoryToolIsAvailable && !Runtime::Current()->IsAotCompiler()) {
    ObjPtr<mirror::Class> soe_klass = FindSystemClass(self, "Ljava/lang/StackOverflowError;");
    if (soe_klass == nullptr || !EnsureInitialized(self, hs.NewHandle(soe_klass), true, true)) {
      // Strange, but don't crash.
      LOG(WARNING) << "Could not prepare StackOverflowError.";
      self->ClearException();
    }
  }

  VLOG(startup) << "ClassLinker::FinishInit exiting";
}

static void EnsureRootInitialized(ClassLinker* class_linker,
                                  Thread* self,
                                  ObjPtr<mirror::Class> klass)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (!klass->IsVisiblyInitialized()) {
    DCHECK(!klass->IsArrayClass());
    DCHECK(!klass->IsPrimitive());
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> h_class(hs.NewHandle(klass));
    if (!class_linker->EnsureInitialized(
             self, h_class, /*can_init_fields=*/ true, /*can_init_parents=*/ true)) {
      LOG(FATAL) << "Exception when initializing " << h_class->PrettyClass()
          << ": " << self->GetException()->Dump();
    }
  }
}

void ClassLinker::RunEarlyRootClinits(Thread* self) {
  StackHandleScope<1u> hs(self);
  Handle<mirror::ObjectArray<mirror::Class>> class_roots = hs.NewHandle(GetClassRoots());
  EnsureRootInitialized(this, self, GetClassRoot<mirror::Class>(class_roots.Get()));
  EnsureRootInitialized(this, self, GetClassRoot<mirror::String>(class_roots.Get()));
  // `Field` class is needed for register_java_net_InetAddress in libcore, b/28153851.
  EnsureRootInitialized(this, self, GetClassRoot<mirror::Field>(class_roots.Get()));

  WellKnownClasses::Init(self->GetJniEnv());

  // `FinalizerReference` class is needed for initialization of `java.net.InetAddress`.
  // (Indirectly by constructing a `ObjectStreamField` which uses a `StringBuilder`
  // and, when resizing, initializes the `System` class for `System.arraycopy()`
  // and `System.<clinit> creates a finalizable object.)
  EnsureRootInitialized(
      this, self, WellKnownClasses::java_lang_ref_FinalizerReference_add->GetDeclaringClass());
}

void ClassLinker::RunRootClinits(Thread* self) {
  StackHandleScope<1u> hs(self);
  Handle<mirror::ObjectArray<mirror::Class>> class_roots = hs.NewHandle(GetClassRoots());
  for (size_t i = 0; i < static_cast<size_t>(ClassRoot::kMax); ++i) {
    EnsureRootInitialized(this, self, GetClassRoot(ClassRoot(i), class_roots.Get()));
  }

  // Make sure certain well-known classes are initialized. Note that well-known
  // classes are always in the boot image, so this code is primarily intended
  // for running without boot image but may be needed for boot image if the
  // AOT-initialization fails due to introduction of new code to `<clinit>`.
  ArtMethod* methods_of_classes_to_initialize[] = {
      // Initialize primitive boxing classes (avoid check at runtime).
      WellKnownClasses::java_lang_Boolean_valueOf,
      WellKnownClasses::java_lang_Byte_valueOf,
      WellKnownClasses::java_lang_Character_valueOf,
      WellKnownClasses::java_lang_Double_valueOf,
      WellKnownClasses::java_lang_Float_valueOf,
      WellKnownClasses::java_lang_Integer_valueOf,
      WellKnownClasses::java_lang_Long_valueOf,
      WellKnownClasses::java_lang_Short_valueOf,
      // Initialize `StackOverflowError`.
      WellKnownClasses::java_lang_StackOverflowError_init,
      // Ensure class loader classes are initialized (avoid check at runtime).
      // Superclass `ClassLoader` is a class root and already initialized above.
      // Superclass `BaseDexClassLoader` is initialized implicitly.
      WellKnownClasses::dalvik_system_DelegateLastClassLoader_init,
      WellKnownClasses::dalvik_system_DexClassLoader_init,
      WellKnownClasses::dalvik_system_InMemoryDexClassLoader_init,
      WellKnownClasses::dalvik_system_PathClassLoader_init,
      WellKnownClasses::java_lang_BootClassLoader_init,
      // Ensure `Daemons` class is initialized (avoid check at runtime).
      WellKnownClasses::java_lang_Daemons_start,
      // Ensure `Thread` and `ThreadGroup` classes are initialized (avoid check at runtime).
      WellKnownClasses::java_lang_Thread_init,
      WellKnownClasses::java_lang_ThreadGroup_add,
      // Ensure reference classes are initialized (avoid check at runtime).
      // The `FinalizerReference` class was initialized in `RunEarlyRootClinits()`.
      WellKnownClasses::java_lang_ref_ReferenceQueue_add,
      // Ensure `InvocationTargetException` class is initialized (avoid check at runtime).
      WellKnownClasses::java_lang_reflect_InvocationTargetException_init,
      // Ensure `Parameter` class is initialized (avoid check at runtime).
      WellKnownClasses::java_lang_reflect_Parameter_init,
      // Ensure `MethodHandles` and `MethodType` classes are initialized (avoid check at runtime).
      WellKnownClasses::java_lang_invoke_MethodHandles_lookup,
      WellKnownClasses::java_lang_invoke_MethodType_makeImpl,
      // Ensure `DirectByteBuffer` class is initialized (avoid check at runtime).
      WellKnownClasses::java_nio_DirectByteBuffer_init,
      // Ensure `FloatingDecimal` class is initialized (avoid check at runtime).
      WellKnownClasses::jdk_internal_math_FloatingDecimal_getBinaryToASCIIConverter_D,
      // Ensure reflection annotation classes are initialized (avoid check at runtime).
      WellKnownClasses::libcore_reflect_AnnotationFactory_createAnnotation,
      WellKnownClasses::libcore_reflect_AnnotationMember_init,
      // We're suppressing exceptions from `DdmServer` and we do not want to repeatedly
      // suppress class initialization error (say, due to OOM), so initialize it early.
      WellKnownClasses::org_apache_harmony_dalvik_ddmc_DdmServer_dispatch,
  };
  for (ArtMethod* method : methods_of_classes_to_initialize) {
    EnsureRootInitialized(this, self, method->GetDeclaringClass());
  }
  ArtField* fields_of_classes_to_initialize[] = {
      // Ensure classes used by class loaders are initialized (avoid check at runtime).
      WellKnownClasses::dalvik_system_DexFile_cookie,
      WellKnownClasses::dalvik_system_DexPathList_dexElements,
      WellKnownClasses::dalvik_system_DexPathList__Element_dexFile,
      // Ensure `VMRuntime` is initialized (avoid check at runtime).
      WellKnownClasses::dalvik_system_VMRuntime_nonSdkApiUsageConsumer,
      // Initialize empty arrays needed by `StackOverflowError`.
      WellKnownClasses::java_util_Collections_EMPTY_LIST,
      WellKnownClasses::libcore_util_EmptyArray_STACK_TRACE_ELEMENT,
      // Initialize boxing caches needed by the compiler.
      WellKnownClasses::java_lang_Byte_ByteCache_cache,
      WellKnownClasses::java_lang_Character_CharacterCache_cache,
      WellKnownClasses::java_lang_Integer_IntegerCache_cache,
      WellKnownClasses::java_lang_Long_LongCache_cache,
      WellKnownClasses::java_lang_Short_ShortCache_cache,
  };
  for (ArtField* field : fields_of_classes_to_initialize) {
    EnsureRootInitialized(this, self, field->GetDeclaringClass());
  }
}

ALWAYS_INLINE
static uint32_t ComputeMethodHash(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!method->IsRuntimeMethod());
  DCHECK(!method->IsProxyMethod());
  DCHECK(!method->IsObsolete());
  // Do not use `ArtMethod::GetNameView()` to avoid unnecessary runtime/proxy/obsolete method
  // checks. It is safe to avoid the read barrier here, see `ArtMethod::GetDexFile()`.
  const DexFile& dex_file = method->GetDeclaringClass<kWithoutReadBarrier>()->GetDexFile();
  const dex::MethodId& method_id = dex_file.GetMethodId(method->GetDexMethodIndex());
  std::string_view name = dex_file.GetMethodNameView(method_id);
  return ComputeModifiedUtf8Hash(name);
}

ALWAYS_INLINE
static bool MethodSignatureEquals(ArtMethod* lhs, ArtMethod* rhs)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!lhs->IsRuntimeMethod());
  DCHECK(!lhs->IsProxyMethod());
  DCHECK(!lhs->IsObsolete());
  DCHECK(!rhs->IsRuntimeMethod());
  DCHECK(!rhs->IsProxyMethod());
  DCHECK(!rhs->IsObsolete());
  // Do not use `ArtMethod::GetDexFile()` to avoid unnecessary obsolete method checks.
  // It is safe to avoid the read barrier here, see `ArtMethod::GetDexFile()`.
  const DexFile& lhs_dex_file = lhs->GetDeclaringClass<kWithoutReadBarrier>()->GetDexFile();
  const DexFile& rhs_dex_file = rhs->GetDeclaringClass<kWithoutReadBarrier>()->GetDexFile();
  const dex::MethodId& lhs_mid = lhs_dex_file.GetMethodId(lhs->GetDexMethodIndex());
  const dex::MethodId& rhs_mid = rhs_dex_file.GetMethodId(rhs->GetDexMethodIndex());
  if (&lhs_dex_file == &rhs_dex_file) {
    return lhs_mid.name_idx_ == rhs_mid.name_idx_ &&
           lhs_mid.proto_idx_ == rhs_mid.proto_idx_;
  } else {
    return
        lhs_dex_file.GetMethodNameView(lhs_mid) == rhs_dex_file.GetMethodNameView(rhs_mid) &&
        lhs_dex_file.GetMethodSignature(lhs_mid) == rhs_dex_file.GetMethodSignature(rhs_mid);
  }
}

static void InitializeObjectVirtualMethodHashes(ObjPtr<mirror::Class> java_lang_Object,
                                                PointerSize pointer_size,
                                                /*out*/ ArrayRef<uint32_t> virtual_method_hashes)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ArraySlice<ArtMethod> virtual_methods = java_lang_Object->GetVirtualMethods(pointer_size);
  DCHECK_EQ(virtual_method_hashes.size(), virtual_methods.size());
  for (size_t i = 0; i != virtual_method_hashes.size(); ++i) {
    virtual_method_hashes[i] = ComputeMethodHash(&virtual_methods[i]);
  }
}

struct TrampolineCheckData {
  const void* quick_resolution_trampoline;
  const void* quick_imt_conflict_trampoline;
  const void* quick_generic_jni_trampoline;
  const void* quick_to_interpreter_bridge_trampoline;
  const void* nterp_trampoline;
  PointerSize pointer_size;
  ArtMethod* m;
  bool error;
};

bool ClassLinker::InitFromBootImage(std::string* error_msg) {
  VLOG(startup) << __FUNCTION__ << " entering";
  CHECK(!init_done_);

  Runtime* const runtime = Runtime::Current();
  Thread* const self = Thread::Current();
  gc::Heap* const heap = runtime->GetHeap();
  std::vector<gc::space::ImageSpace*> spaces = heap->GetBootImageSpaces();
  CHECK(!spaces.empty());
  const ImageHeader& image_header = spaces[0]->GetImageHeader();
  uint32_t pointer_size_unchecked = image_header.GetPointerSizeUnchecked();
  if (!ValidPointerSize(pointer_size_unchecked)) {
    *error_msg = StringPrintf("Invalid image pointer size: %u", pointer_size_unchecked);
    return false;
  }
  image_pointer_size_ = image_header.GetPointerSize();
  if (!runtime->IsAotCompiler()) {
    // Only the Aot compiler supports having an image with a different pointer size than the
    // runtime. This happens on the host for compiling 32 bit tests since we use a 64 bit libart
    // compiler. We may also use 32 bit dex2oat on a system with 64 bit apps.
    if (image_pointer_size_ != kRuntimePointerSize) {
      *error_msg = StringPrintf("Runtime must use current image pointer size: %zu vs %zu",
                                static_cast<size_t>(image_pointer_size_),
                                sizeof(void*));
      return false;
    }
  }
  DCHECK(!runtime->HasResolutionMethod());
  runtime->SetResolutionMethod(image_header.GetImageMethod(ImageHeader::kResolutionMethod));
  runtime->SetImtConflictMethod(image_header.GetImageMethod(ImageHeader::kImtConflictMethod));
  runtime->SetImtUnimplementedMethod(
      image_header.GetImageMethod(ImageHeader::kImtUnimplementedMethod));
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveAllCalleeSavesMethod),
      CalleeSaveType::kSaveAllCalleeSaves);
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveRefsOnlyMethod),
      CalleeSaveType::kSaveRefsOnly);
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveRefsAndArgsMethod),
      CalleeSaveType::kSaveRefsAndArgs);
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveEverythingMethod),
      CalleeSaveType::kSaveEverything);
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveEverythingMethodForClinit),
      CalleeSaveType::kSaveEverythingForClinit);
  runtime->SetCalleeSaveMethod(
      image_header.GetImageMethod(ImageHeader::kSaveEverythingMethodForSuspendCheck),
      CalleeSaveType::kSaveEverythingForSuspendCheck);

  std::vector<const OatFile*> oat_files =
      runtime->GetOatFileManager().RegisterImageOatFiles(spaces);
  DCHECK(!oat_files.empty());
  const OatHeader& default_oat_header = oat_files[0]->GetOatHeader();
  jni_dlsym_lookup_trampoline_ = default_oat_header.GetJniDlsymLookupTrampoline();
  jni_dlsym_lookup_critical_trampoline_ = default_oat_header.GetJniDlsymLookupCriticalTrampoline();
  quick_resolution_trampoline_ = default_oat_header.GetQuickResolutionTrampoline();
  quick_imt_conflict_trampoline_ = default_oat_header.GetQuickImtConflictTrampoline();
  quick_generic_jni_trampoline_ = default_oat_header.GetQuickGenericJniTrampoline();
  quick_to_interpreter_bridge_trampoline_ = default_oat_header.GetQuickToInterpreterBridge();
  nterp_trampoline_ = default_oat_header.GetNterpTrampoline();
  if (kIsDebugBuild) {
    // Check that the other images use the same trampoline.
    for (size_t i = 1; i < oat_files.size(); ++i) {
      const OatHeader& ith_oat_header = oat_files[i]->GetOatHeader();
      const void* ith_jni_dlsym_lookup_trampoline_ =
          ith_oat_header.GetJniDlsymLookupTrampoline();
      const void* ith_jni_dlsym_lookup_critical_trampoline_ =
          ith_oat_header.GetJniDlsymLookupCriticalTrampoline();
      const void* ith_quick_resolution_trampoline =
          ith_oat_header.GetQuickResolutionTrampoline();
      const void* ith_quick_imt_conflict_trampoline =
          ith_oat_header.GetQuickImtConflictTrampoline();
      const void* ith_quick_generic_jni_trampoline =
          ith_oat_header.GetQuickGenericJniTrampoline();
      const void* ith_quick_to_interpreter_bridge_trampoline =
          ith_oat_header.GetQuickToInterpreterBridge();
      const void* ith_nterp_trampoline =
          ith_oat_header.GetNterpTrampoline();
      if (ith_jni_dlsym_lookup_trampoline_ != jni_dlsym_lookup_trampoline_ ||
          ith_jni_dlsym_lookup_critical_trampoline_ != jni_dlsym_lookup_critical_trampoline_ ||
          ith_quick_resolution_trampoline != quick_resolution_trampoline_ ||
          ith_quick_imt_conflict_trampoline != quick_imt_conflict_trampoline_ ||
          ith_quick_generic_jni_trampoline != quick_generic_jni_trampoline_ ||
          ith_quick_to_interpreter_bridge_trampoline != quick_to_interpreter_bridge_trampoline_ ||
          ith_nterp_trampoline != nterp_trampoline_) {
        // Make sure that all methods in this image do not contain those trampolines as
        // entrypoints. Otherwise the class-linker won't be able to work with a single set.
        TrampolineCheckData data;
        data.error = false;
        data.pointer_size = GetImagePointerSize();
        data.quick_resolution_trampoline = ith_quick_resolution_trampoline;
        data.quick_imt_conflict_trampoline = ith_quick_imt_conflict_trampoline;
        data.quick_generic_jni_trampoline = ith_quick_generic_jni_trampoline;
        data.quick_to_interpreter_bridge_trampoline = ith_quick_to_interpreter_bridge_trampoline;
        data.nterp_trampoline = ith_nterp_trampoline;
        ReaderMutexLock mu(self, *Locks::heap_bitmap_lock_);
        auto visitor = [&](mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_) {
          if (obj->IsClass()) {
            ObjPtr<mirror::Class> klass = obj->AsClass();
            for (ArtMethod& m : klass->GetMethods(data.pointer_size)) {
              const void* entrypoint =
                  m.GetEntryPointFromQuickCompiledCodePtrSize(data.pointer_size);
              if (entrypoint == data.quick_resolution_trampoline ||
                  entrypoint == data.quick_imt_conflict_trampoline ||
                  entrypoint == data.quick_generic_jni_trampoline ||
                  entrypoint == data.quick_to_interpreter_bridge_trampoline) {
                data.m = &m;
                data.error = true;
                return;
              }
            }
          }
        };
        spaces[i]->GetLiveBitmap()->Walk(visitor);
        if (data.error) {
          ArtMethod* m = data.m;
          LOG(ERROR) << "Found a broken ArtMethod: " << ArtMethod::PrettyMethod(m);
          *error_msg = "Found an ArtMethod with a bad entrypoint";
          return false;
        }
      }
    }
  }

  class_roots_ = GcRoot<mirror::ObjectArray<mirror::Class>>(
      ObjPtr<mirror::ObjectArray<mirror::Class>>::DownCast(
          image_header.GetImageRoot(ImageHeader::kClassRoots)));
  DCHECK_EQ(GetClassRoot<mirror::Class>(this)->GetClassFlags(), mirror::kClassFlagClass);

  DCHECK_EQ(GetClassRoot<mirror::Object>(this)->GetObjectSize(), sizeof(mirror::Object));
  ObjPtr<mirror::ObjectArray<mirror::Object>> boot_image_live_objects =
      ObjPtr<mirror::ObjectArray<mirror::Object>>::DownCast(
          image_header.GetImageRoot(ImageHeader::kBootImageLiveObjects));
  runtime->SetSentinel(boot_image_live_objects->Get(ImageHeader::kClearedJniWeakSentinel));
  DCHECK(runtime->GetSentinel().Read()->GetClass() == GetClassRoot<mirror::Object>(this));

  // Boot class loader, use a null handle.
  if (!AddImageSpaces(ArrayRef<gc::space::ImageSpace*>(spaces),
                      ScopedNullHandle<mirror::ClassLoader>(),
                      /*context=*/nullptr,
                      &boot_dex_files_,
                      error_msg)) {
    return false;
  }
  InitializeObjectVirtualMethodHashes(GetClassRoot<mirror::Object>(this),
                                      image_pointer_size_,
                                      ArrayRef<uint32_t>(object_virtual_method_hashes_));
  FinishInit(self);

  VLOG(startup) << __FUNCTION__ << " exiting";
  return true;
}

void ClassLinker::AddExtraBootDexFiles(
    Thread* self,
    std::vector<std::unique_ptr<const DexFile>>&& additional_dex_files) {
  for (std::unique_ptr<const DexFile>& dex_file : additional_dex_files) {
    AppendToBootClassPath(self, dex_file.get());
    if (kIsDebugBuild) {
      for (const auto& boot_dex_file : boot_dex_files_) {
        DCHECK_NE(boot_dex_file->GetLocation(), dex_file->GetLocation());
      }
    }
    boot_dex_files_.push_back(std::move(dex_file));
  }
}

bool ClassLinker::IsBootClassLoader(ObjPtr<mirror::Object> class_loader) {
  return class_loader == nullptr ||
         WellKnownClasses::java_lang_BootClassLoader == class_loader->GetClass();
}

class CHAOnDeleteUpdateClassVisitor {
 public:
  explicit CHAOnDeleteUpdateClassVisitor(LinearAlloc* alloc)
      : allocator_(alloc), cha_(Runtime::Current()->GetClassLinker()->GetClassHierarchyAnalysis()),
        pointer_size_(Runtime::Current()->GetClassLinker()->GetImagePointerSize()),
        self_(Thread::Current()) {}

  bool operator()(ObjPtr<mirror::Class> klass) REQUIRES_SHARED(Locks::mutator_lock_) {
    // This class is going to be unloaded. Tell CHA about it.
    cha_->ResetSingleImplementationInHierarchy(klass, allocator_, pointer_size_);
    return true;
  }
 private:
  const LinearAlloc* allocator_;
  const ClassHierarchyAnalysis* cha_;
  const PointerSize pointer_size_;
  const Thread* self_;
};

/*
 * A class used to ensure that all references to strings interned in an AppImage have been
 * properly recorded in the interned references list, and is only ever run in debug mode.
 */
class CountInternedStringReferencesVisitor {
 public:
  CountInternedStringReferencesVisitor(const gc::space::ImageSpace& space,
                                       const InternTable::UnorderedSet& image_interns)
      : space_(space),
        image_interns_(image_interns),
        count_(0u) {}

  void TestObject(ObjPtr<mirror::Object> referred_obj) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (referred_obj != nullptr &&
        space_.HasAddress(referred_obj.Ptr()) &&
        referred_obj->IsString()) {
      ObjPtr<mirror::String> referred_str = referred_obj->AsString();
      uint32_t hash = static_cast<uint32_t>(referred_str->GetStoredHashCode());
      // All image strings have the hash code calculated, even if they are not interned.
      DCHECK_EQ(hash, static_cast<uint32_t>(referred_str->ComputeHashCode()));
      auto it = image_interns_.FindWithHash(GcRoot<mirror::String>(referred_str), hash);
      if (it != image_interns_.end() && it->Read() == referred_str) {
        ++count_;
      }
    }
  }

  void VisitRootIfNonNull(
      mirror::CompressedReference<mirror::Object>* root) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    if (!root->IsNull()) {
      VisitRoot(root);
    }
  }

  void VisitRoot(mirror::CompressedReference<mirror::Object>* root) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    TestObject(root->AsMirrorPtr());
  }

  // Visit Class Fields
  void operator()(ObjPtr<mirror::Object> obj,
                  MemberOffset offset,
                  [[maybe_unused]] bool is_static) const REQUIRES_SHARED(Locks::mutator_lock_) {
    // References within image or across images don't need a read barrier.
    ObjPtr<mirror::Object> referred_obj =
        obj->GetFieldObject<mirror::Object, kVerifyNone, kWithoutReadBarrier>(offset);
    TestObject(referred_obj);
  }

  void operator()([[maybe_unused]] ObjPtr<mirror::Class> klass, ObjPtr<mirror::Reference> ref) const
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_) {
    operator()(ref, mirror::Reference::ReferentOffset(), /*is_static=*/ false);
  }

  size_t GetCount() const {
    return count_;
  }

 private:
  const gc::space::ImageSpace& space_;
  const InternTable::UnorderedSet& image_interns_;
  mutable size_t count_;  // Modified from the `const` callbacks.
};

/*
 * This function counts references to strings interned in the AppImage.
 * This is used in debug build to check against the number of the recorded references.
 */
size_t CountInternedStringReferences(gc::space::ImageSpace& space,
                                     const InternTable::UnorderedSet& image_interns)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const gc::accounting::ContinuousSpaceBitmap* bitmap = space.GetMarkBitmap();
  const ImageHeader& image_header = space.GetImageHeader();
  const uint8_t* target_base = space.GetMemMap()->Begin();
  const ImageSection& objects_section = image_header.GetObjectsSection();

  auto objects_begin = reinterpret_cast<uintptr_t>(target_base + objects_section.Offset());
  auto objects_end = reinterpret_cast<uintptr_t>(target_base + objects_section.End());

  CountInternedStringReferencesVisitor visitor(space, image_interns);
  bitmap->VisitMarkedRange(objects_begin,
                           objects_end,
                           [&space, &visitor](mirror::Object* obj)
    REQUIRES_SHARED(Locks::mutator_lock_) {
    if (space.HasAddress(obj)) {
      if (obj->IsDexCache()) {
        obj->VisitReferences</* kVisitNativeRoots= */ true,
                             kVerifyNone,
                             kWithoutReadBarrier>(visitor, visitor);
      } else {
        // Don't visit native roots for non-dex-cache as they can't contain
        // native references to strings.  This is verified during compilation
        // by ImageWriter::VerifyNativeGCRootInvariants.
        obj->VisitReferences</* kVisitNativeRoots= */ false,
                             kVerifyNone,
                             kWithoutReadBarrier>(visitor, visitor);
      }
    }
  });
  return visitor.GetCount();
}

template <typename Visitor>
static void VisitInternedStringReferences(
    gc::space::ImageSpace* space,
    const Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_) {
  const uint8_t* target_base = space->Begin();
  const ImageSection& sro_section =
      space->GetImageHeader().GetImageStringReferenceOffsetsSection();
  const size_t num_string_offsets = sro_section.Size() / sizeof(AppImageReferenceOffsetInfo);

  VLOG(image)
      << "ClassLinker:AppImage:InternStrings:imageStringReferenceOffsetCount = "
      << num_string_offsets;

  const auto* sro_base =
      reinterpret_cast<const AppImageReferenceOffsetInfo*>(target_base + sro_section.Offset());

  for (size_t offset_index = 0; offset_index < num_string_offsets; ++offset_index) {
    uint32_t base_offset = sro_base[offset_index].first;

    uint32_t raw_member_offset = sro_base[offset_index].second;
    DCHECK_ALIGNED(base_offset, 2);

    ObjPtr<mirror::Object> obj_ptr =
        reinterpret_cast<mirror::Object*>(space->Begin() + base_offset);
    if (obj_ptr->IsDexCache() && raw_member_offset >= sizeof(mirror::DexCache)) {
      // Special case for strings referenced from dex cache array: the offset is
      // actually decoded as an index into the dex cache string array.
      uint32_t index = raw_member_offset - sizeof(mirror::DexCache);
      mirror::GcRootArray<mirror::String>* array = obj_ptr->AsDexCache()->GetStringsArray();
      // The array could be concurrently set to null. See `StartupCompletedTask`.
      if (array != nullptr) {
        ObjPtr<mirror::String> referred_string = array->Get(index);
        DCHECK(referred_string != nullptr);
        ObjPtr<mirror::String> visited = visitor(referred_string);
        if (visited != referred_string) {
          array->Set(index, visited.Ptr());
        }
      }
    } else {
      DCHECK_ALIGNED(raw_member_offset, 2);
      MemberOffset member_offset(raw_member_offset);
      ObjPtr<mirror::String> referred_string =
          obj_ptr->GetFieldObject<mirror::String,
                                  kVerifyNone,
                                  kWithoutReadBarrier,
                                  /* kIsVolatile= */ false>(member_offset);
      DCHECK(referred_string != nullptr);

      ObjPtr<mirror::String> visited = visitor(referred_string);
      if (visited != referred_string) {
        obj_ptr->SetFieldObject</* kTransactionActive= */ false,
                                /* kCheckTransaction= */ false,
                                kVerifyNone,
                                /* kIsVolatile= */ false>(member_offset, visited);
      }
    }
  }
}

static void VerifyInternedStringReferences(gc::space::ImageSpace* space)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  InternTable::UnorderedSet image_interns;
  const ImageSection& section = space->GetImageHeader().GetInternedStringsSection();
  if (section.Size() > 0) {
    size_t read_count;
    const uint8_t* data = space->Begin() + section.Offset();
    InternTable::UnorderedSet image_set(data, /*make_copy_of_data=*/ false, &read_count);
    image_set.swap(image_interns);
  }
  size_t num_recorded_refs = 0u;
  VisitInternedStringReferences(
      space,
      [&image_interns, &num_recorded_refs](ObjPtr<mirror::String> str)
          REQUIRES_SHARED(Locks::mutator_lock_) {
        auto it = image_interns.find(GcRoot<mirror::String>(str));
        CHECK(it != image_interns.end());
        CHECK(it->Read() == str);
        ++num_recorded_refs;
        return str;
      });
  size_t num_found_refs = CountInternedStringReferences(*space, image_interns);
  CHECK_EQ(num_recorded_refs, num_found_refs);
}

// new_class_set is the set of classes that were read from the class table section in the image.
// If there was no class table section, it is null.
// Note: using a class here to avoid having to make ClassLinker internals public.
class AppImageLoadingHelper {
 public:
  static void Update(
      ClassLinker* class_linker,
      gc::space::ImageSpace* space,
      Handle<mirror::ClassLoader> class_loader,
      Handle<mirror::ObjectArray<mirror::DexCache>> dex_caches)
      REQUIRES(!Locks::dex_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static void HandleAppImageStrings(gc::space::ImageSpace* space)
      REQUIRES_SHARED(Locks::mutator_lock_);
};

void AppImageLoadingHelper::Update(
    ClassLinker* class_linker,
    gc::space::ImageSpace* space,
    Handle<mirror::ClassLoader> class_loader,
    Handle<mirror::ObjectArray<mirror::DexCache>> dex_caches)
    REQUIRES(!Locks::dex_lock_)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedTrace app_image_timing("AppImage:Updating");

  if (kIsDebugBuild && ClassLinker::kAppImageMayContainStrings) {
    // In debug build, verify the string references before applying
    // the Runtime::LoadAppImageStartupCache() option.
    VerifyInternedStringReferences(space);
  }
  DCHECK(class_loader.Get() != nullptr);
  Thread* const self = Thread::Current();
  Runtime* const runtime = Runtime::Current();
  gc::Heap* const heap = runtime->GetHeap();
  const ImageHeader& header = space->GetImageHeader();
  int32_t number_of_dex_cache_arrays_cleared = 0;
  {
    // Register dex caches with the class loader.
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    for (auto dex_cache : dex_caches.Iterate<mirror::DexCache>()) {
      const DexFile* const dex_file = dex_cache->GetDexFile();
      {
        WriterMutexLock mu2(self, *Locks::dex_lock_);
        CHECK(class_linker->FindDexCacheDataLocked(*dex_file) == nullptr);
        if (runtime->GetStartupCompleted()) {
          number_of_dex_cache_arrays_cleared++;
          // Free up dex cache arrays that we would only allocate at startup.
          // We do this here before registering and within the lock to be
          // consistent with `StartupCompletedTask`.
          dex_cache->UnlinkStartupCaches();
        }
        VLOG(image) << "App image registers dex file " << dex_file->GetLocation();
        class_linker->RegisterDexFileLocked(*dex_file, dex_cache, class_loader.Get());
      }
    }
  }
  if (number_of_dex_cache_arrays_cleared == dex_caches->GetLength()) {
    // Free up dex cache arrays that we would only allocate at startup.
    // If `number_of_dex_cache_arrays_cleared` isn't the number of dex caches in
    // the image, then there is a race with the `StartupCompletedTask`, which
    // will release the space instead.
    space->ReleaseMetadata();
  }

  if (ClassLinker::kAppImageMayContainStrings) {
    HandleAppImageStrings(space);
  }

  if (kVerifyArtMethodDeclaringClasses) {
    ScopedTrace timing("AppImage:VerifyDeclaringClasses");
    ReaderMutexLock rmu(self, *Locks::heap_bitmap_lock_);
    gc::accounting::HeapBitmap* live_bitmap = heap->GetLiveBitmap();
    header.VisitPackedArtMethods([&](ArtMethod& method)
        REQUIRES_SHARED(Locks::mutator_lock_, Locks::heap_bitmap_lock_) {
      ObjPtr<mirror::Class> klass = method.GetDeclaringClassUnchecked();
      if (klass != nullptr) {
        CHECK(live_bitmap->Test(klass.Ptr())) << "Image method has unmarked declaring class";
      }
    }, space->Begin(), kRuntimePointerSize);
  }
}

void AppImageLoadingHelper::HandleAppImageStrings(gc::space::ImageSpace* space) {
  // Iterate over the string reference offsets stored in the image and intern
  // the strings they point to.
  ScopedTrace timing("AppImage:InternString");

  Runtime* const runtime = Runtime::Current();
  InternTable* const intern_table = runtime->GetInternTable();

  // Add the intern table, removing any conflicts. For conflicts, store the new address in a map
  // for faster lookup.
  // TODO: Optimize with a bitmap or bloom filter
  SafeMap<mirror::String*, mirror::String*> intern_remap;
  auto func = [&](InternTable::UnorderedSet& interns)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::intern_table_lock_) {
    const size_t non_boot_image_strings = intern_table->CountInterns(
        /*visit_boot_images=*/false,
        /*visit_non_boot_images=*/true);
    VLOG(image) << "AppImage:stringsInInternTableSize = " << interns.size();
    VLOG(image) << "AppImage:nonBootImageInternStrings = " << non_boot_image_strings;
    // Visit the smaller of the two sets to compute the intersection.
    if (interns.size() < non_boot_image_strings) {
      for (auto it = interns.begin(); it != interns.end(); ) {
        ObjPtr<mirror::String> string = it->Read();
        ObjPtr<mirror::String> existing = intern_table->LookupWeakLocked(string);
        if (existing == nullptr) {
          existing = intern_table->LookupStrongLocked(string);
        }
        if (existing != nullptr) {
          intern_remap.Put(string.Ptr(), existing.Ptr());
          it = interns.erase(it);
        } else {
          ++it;
        }
      }
    } else {
      intern_table->VisitInterns([&](const GcRoot<mirror::String>& root)
          REQUIRES_SHARED(Locks::mutator_lock_)
          REQUIRES(Locks::intern_table_lock_) {
        auto it = interns.find(root);
        if (it != interns.end()) {
          ObjPtr<mirror::String> existing = root.Read();
          intern_remap.Put(it->Read(), existing.Ptr());
          it = interns.erase(it);
        }
      }, /*visit_boot_images=*/false, /*visit_non_boot_images=*/true);
    }
    // Consistency check to ensure correctness.
    if (kIsDebugBuild) {
      for (GcRoot<mirror::String>& root : interns) {
        ObjPtr<mirror::String> string = root.Read();
        CHECK(intern_table->LookupWeakLocked(string) == nullptr) << string->ToModifiedUtf8();
        CHECK(intern_table->LookupStrongLocked(string) == nullptr) << string->ToModifiedUtf8();
      }
    }
  };
  intern_table->AddImageStringsToTable(space, func);
  if (!intern_remap.empty()) {
    VLOG(image) << "AppImage:conflictingInternStrings = " << intern_remap.size();
    VisitInternedStringReferences(
        space,
        [&intern_remap](ObjPtr<mirror::String> str) REQUIRES_SHARED(Locks::mutator_lock_) {
          auto it = intern_remap.find(str.Ptr());
          if (it != intern_remap.end()) {
            return ObjPtr<mirror::String>(it->second);
          }
          return str;
        });
  }
}

static std::unique_ptr<const DexFile> OpenOatDexFile(const OatFile* oat_file,
                                                     const char* location,
                                                     std::string* error_msg)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(error_msg != nullptr);
  std::unique_ptr<const DexFile> dex_file;
  const OatDexFile* oat_dex_file = oat_file->GetOatDexFile(location, error_msg);
  if (oat_dex_file == nullptr) {
    return std::unique_ptr<const DexFile>();
  }
  std::string inner_error_msg;
  dex_file = oat_dex_file->OpenDexFile(&inner_error_msg);
  if (dex_file == nullptr) {
    *error_msg = StringPrintf("Failed to open dex file %s from within oat file %s error '%s'",
                              location,
                              oat_file->GetLocation().c_str(),
                              inner_error_msg.c_str());
    return std::unique_ptr<const DexFile>();
  }

  if (dex_file->GetLocationChecksum() != oat_dex_file->GetDexFileLocationChecksum()) {
    CHECK(dex_file->GetSha1() != oat_dex_file->GetSha1());
    *error_msg = StringPrintf("Checksums do not match for %s: %x vs %x",
                              location,
                              dex_file->GetLocationChecksum(),
                              oat_dex_file->GetDexFileLocationChecksum());
    return std::unique_ptr<const DexFile>();
  }
  CHECK(dex_file->GetSha1() == oat_dex_file->GetSha1());
  return dex_file;
}

bool ClassLinker::OpenImageDexFiles(gc::space::ImageSpace* space,
                                    std::vector<std::unique_ptr<const DexFile>>* out_dex_files,
                                    std::string* error_msg) {
  ScopedAssertNoThreadSuspension nts(__FUNCTION__);
  const ImageHeader& header = space->GetImageHeader();
  ObjPtr<mirror::Object> dex_caches_object = header.GetImageRoot(ImageHeader::kDexCaches);
  DCHECK(dex_caches_object != nullptr);
  ObjPtr<mirror::ObjectArray<mirror::DexCache>> dex_caches =
      dex_caches_object->AsObjectArray<mirror::DexCache>();
  const OatFile* oat_file = space->GetOatFile();
  for (auto dex_cache : dex_caches->Iterate()) {
    std::string dex_file_location(dex_cache->GetLocation()->ToModifiedUtf8());
    std::unique_ptr<const DexFile> dex_file = OpenOatDexFile(oat_file,
                                                             dex_file_location.c_str(),
                                                             error_msg);
    if (dex_file == nullptr) {
      return false;
    }
    dex_cache->SetDexFile(dex_file.get());
    out_dex_files->push_back(std::move(dex_file));
  }
  return true;
}

bool ClassLinker::OpenAndInitImageDexFiles(
    const gc::space::ImageSpace* space,
    Handle<mirror::ClassLoader> class_loader,
    std::vector<std::unique_ptr<const DexFile>>* out_dex_files,
    std::string* error_msg) {
  DCHECK(out_dex_files != nullptr);
  const bool app_image = class_loader != nullptr;
  const ImageHeader& header = space->GetImageHeader();
  ObjPtr<mirror::Object> dex_caches_object = header.GetImageRoot(ImageHeader::kDexCaches);
  DCHECK(dex_caches_object != nullptr);
  Thread* const self = Thread::Current();
  StackHandleScope<3> hs(self);
  Handle<mirror::ObjectArray<mirror::DexCache>> dex_caches(
      hs.NewHandle(dex_caches_object->AsObjectArray<mirror::DexCache>()));
  const OatFile* oat_file = space->GetOatFile();
  if (oat_file->GetOatHeader().GetDexFileCount() !=
      static_cast<uint32_t>(dex_caches->GetLength())) {
    *error_msg =
        "Dex cache count and dex file count mismatch while trying to initialize from image";
    return false;
  }

  for (auto dex_cache : dex_caches.Iterate<mirror::DexCache>()) {
    std::string dex_file_location = dex_cache->GetLocation()->ToModifiedUtf8();
    std::unique_ptr<const DexFile> dex_file =
        OpenOatDexFile(oat_file, dex_file_location.c_str(), error_msg);
    if (dex_file == nullptr) {
      return false;
    }

    {
      // Native fields are all null.  Initialize them.
      WriterMutexLock mu(self, *Locks::dex_lock_);
      dex_cache->Initialize(dex_file.get(), class_loader.Get());
    }
    if (!app_image) {
      // Register dex files, keep track of existing ones that are conflicts.
      AppendToBootClassPath(dex_file.get(), dex_cache);
    }
    out_dex_files->push_back(std::move(dex_file));
  }
  return true;
}

// Helper class for ArtMethod checks when adding an image. Keeps all required functionality
// together and caches some intermediate results.
template <PointerSize kPointerSize>
class ImageChecker final {
 public:
  static void CheckObjects(gc::Heap* heap, gc::space::ImageSpace* space)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    // There can be no GC during boot image initialization, so we do not need read barriers.
    ScopedDebugDisallowReadBarriers sddrb(Thread::Current());

    CHECK_EQ(kPointerSize, space->GetImageHeader().GetPointerSize());
    const ImageSection& objects_section = space->GetImageHeader().GetObjectsSection();
    uintptr_t space_begin = reinterpret_cast<uintptr_t>(space->Begin());
    uintptr_t objects_begin = space_begin + objects_section.Offset();
    uintptr_t objects_end = objects_begin + objects_section.Size();
    ImageChecker ic(heap);
    auto visitor = [&](mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_) {
      DCHECK(obj != nullptr);
      mirror::Class* obj_klass = obj->GetClass<kDefaultVerifyFlags, kWithoutReadBarrier>();
      CHECK(obj_klass != nullptr) << "Null class in object " << obj;
      mirror::Class* class_class = obj_klass->GetClass<kDefaultVerifyFlags, kWithoutReadBarrier>();
      CHECK(class_class != nullptr) << "Null class class " << obj;
      if (obj_klass == class_class) {
        auto klass = obj->AsClass();
        for (ArtField& field : klass->GetIFields()) {
          CHECK_EQ(field.GetDeclaringClass<kWithoutReadBarrier>(), klass);
        }
        for (ArtField& field : klass->GetSFields()) {
          CHECK_EQ(field.GetDeclaringClass<kWithoutReadBarrier>(), klass);
        }
        for (ArtMethod& m : klass->GetMethods(kPointerSize)) {
          ic.CheckArtMethod(&m, klass);
        }
        ObjPtr<mirror::PointerArray> vtable =
            klass->GetVTable<kDefaultVerifyFlags, kWithoutReadBarrier>();
        if (vtable != nullptr) {
          ic.CheckArtMethodPointerArray(vtable);
        }
        if (klass->ShouldHaveImt()) {
          ImTable* imt = klass->GetImt(kPointerSize);
          for (size_t i = 0; i < ImTable::kSize; ++i) {
            ic.CheckArtMethod(imt->Get(i, kPointerSize), /*expected_class=*/ nullptr);
          }
        }
        if (klass->ShouldHaveEmbeddedVTable()) {
          for (int32_t i = 0; i < klass->GetEmbeddedVTableLength(); ++i) {
            ic.CheckArtMethod(klass->GetEmbeddedVTableEntry(i, kPointerSize),
                              /*expected_class=*/ nullptr);
          }
        }
        ObjPtr<mirror::IfTable> iftable =
            klass->GetIfTable<kDefaultVerifyFlags, kWithoutReadBarrier>();
        int32_t iftable_count = (iftable != nullptr) ? iftable->Count() : 0;
        for (int32_t i = 0; i < iftable_count; ++i) {
          ObjPtr<mirror::PointerArray> method_array =
              iftable->GetMethodArrayOrNull<kDefaultVerifyFlags, kWithoutReadBarrier>(i);
          if (method_array != nullptr) {
            ic.CheckArtMethodPointerArray(method_array);
          }
        }
      }
    };
    space->GetLiveBitmap()->VisitMarkedRange(objects_begin, objects_end, visitor);
  }

 private:
  explicit ImageChecker(gc::Heap* heap) {
    ArrayRef<gc::space::ImageSpace* const> spaces(heap->GetBootImageSpaces());
    space_begin_.reserve(spaces.size());
    for (gc::space::ImageSpace* space : spaces) {
      CHECK_EQ(static_cast<const void*>(space->Begin()), &space->GetImageHeader());
      space_begin_.push_back(space->Begin());
    }
  }

  void CheckArtMethod(ArtMethod* m, ObjPtr<mirror::Class> expected_class)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ObjPtr<mirror::Class> declaring_class = m->GetDeclaringClassUnchecked<kWithoutReadBarrier>();
    if (m->IsRuntimeMethod()) {
      CHECK(declaring_class == nullptr) << declaring_class << " " << m->PrettyMethod();
    } else if (m->IsCopied()) {
      CHECK(declaring_class != nullptr) << m->PrettyMethod();
    } else if (expected_class != nullptr) {
      CHECK_EQ(declaring_class, expected_class) << m->PrettyMethod();
    }
    bool contains = false;
    for (const uint8_t* begin : space_begin_) {
      const size_t offset = reinterpret_cast<uint8_t*>(m) - begin;
      const ImageHeader* header = reinterpret_cast<const ImageHeader*>(begin);
      if (header->GetMethodsSection().Contains(offset) ||
          header->GetRuntimeMethodsSection().Contains(offset)) {
        contains = true;
        break;
      }
    }
    CHECK(contains) << m << " not found";
  }

  void CheckArtMethodPointerArray(ObjPtr<mirror::PointerArray> arr)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    CHECK(arr != nullptr);
    for (int32_t j = 0; j < arr->GetLength(); ++j) {
      auto* method = arr->GetElementPtrSize<ArtMethod*>(j, kPointerSize);
      CHECK(method != nullptr);
      CheckArtMethod(method, /*expected_class=*/ nullptr);
    }
  }

  std::vector<const uint8_t*> space_begin_;
};

static void VerifyAppImage(const ImageHeader& header,
                           const Handle<mirror::ClassLoader>& class_loader,
                           ClassTable* class_table,
                           gc::space::ImageSpace* space)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  header.VisitPackedArtMethods([&](ArtMethod& method) REQUIRES_SHARED(Locks::mutator_lock_) {
    ObjPtr<mirror::Class> klass = method.GetDeclaringClass();
    if (klass != nullptr && !Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(klass)) {
      CHECK_EQ(class_table->LookupByDescriptor(klass), klass)
          << mirror::Class::PrettyClass(klass);
    }
  }, space->Begin(), kRuntimePointerSize);
  {
    // Verify that all direct interfaces of classes in the class table are also resolved.
    std::vector<ObjPtr<mirror::Class>> classes;
    auto verify_direct_interfaces_in_table = [&](ObjPtr<mirror::Class> klass)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      if (!klass->IsPrimitive() && klass->GetClassLoader() == class_loader.Get()) {
        classes.push_back(klass);
      }
      return true;
    };
    class_table->Visit(verify_direct_interfaces_in_table);
    for (ObjPtr<mirror::Class> klass : classes) {
      for (uint32_t i = 0, num = klass->NumDirectInterfaces(); i != num; ++i) {
        CHECK(klass->GetDirectInterface(i) != nullptr)
            << klass->PrettyDescriptor() << " iface #" << i;
      }
    }
  }
}

bool ClassLinker::AddImageSpace(gc::space::ImageSpace* space,
                                Handle<mirror::ClassLoader> class_loader,
                                ClassLoaderContext* context,
                                const std::vector<std::unique_ptr<const DexFile>>& dex_files,
                                std::string* error_msg) {
  DCHECK(error_msg != nullptr);
  const uint64_t start_time = NanoTime();
  const bool app_image = class_loader != nullptr;
  const ImageHeader& header = space->GetImageHeader();
  ObjPtr<mirror::Object> dex_caches_object = header.GetImageRoot(ImageHeader::kDexCaches);
  DCHECK(dex_caches_object != nullptr);
  Runtime* const runtime = Runtime::Current();
  gc::Heap* const heap = runtime->GetHeap();
  Thread* const self = Thread::Current();
  // Check that the image is what we are expecting.
  if (image_pointer_size_ != space->GetImageHeader().GetPointerSize()) {
    *error_msg = StringPrintf("Application image pointer size does not match runtime: %zu vs %zu",
                              static_cast<size_t>(space->GetImageHeader().GetPointerSize()),
                              static_cast<size_t>(image_pointer_size_));
    return false;
  }
  size_t expected_image_roots = ImageHeader::NumberOfImageRoots(app_image);
  if (static_cast<size_t>(header.GetImageRoots()->GetLength()) != expected_image_roots) {
    *error_msg = StringPrintf("Expected %zu image roots but got %d",
                              expected_image_roots,
                              header.GetImageRoots()->GetLength());
    return false;
  }
  StackHandleScope<3> hs(self);
  Handle<mirror::ObjectArray<mirror::DexCache>> dex_caches(
      hs.NewHandle(dex_caches_object->AsObjectArray<mirror::DexCache>()));
  Handle<mirror::ObjectArray<mirror::Class>> class_roots(hs.NewHandle(
      header.GetImageRoot(ImageHeader::kClassRoots)->AsObjectArray<mirror::Class>()));
  MutableHandle<mirror::Object> special_root(hs.NewHandle(
      app_image ? header.GetImageRoot(ImageHeader::kSpecialRoots) : nullptr));
  DCHECK(class_roots != nullptr);
  if (class_roots->GetLength() != static_cast<int32_t>(ClassRoot::kMax)) {
    *error_msg = StringPrintf("Expected %d class roots but got %d",
                              class_roots->GetLength(),
                              static_cast<int32_t>(ClassRoot::kMax));
    return false;
  }
  // Check against existing class roots to make sure they match the ones in the boot image.
  ObjPtr<mirror::ObjectArray<mirror::Class>> existing_class_roots = GetClassRoots();
  for (size_t i = 0; i < static_cast<size_t>(ClassRoot::kMax); i++) {
    if (class_roots->Get(i) != GetClassRoot(static_cast<ClassRoot>(i), existing_class_roots)) {
      *error_msg = "App image class roots must have pointer equality with runtime ones.";
      return false;
    }
  }
  const OatFile* oat_file = space->GetOatFile();

  if (app_image) {
    ScopedAssertNoThreadSuspension sants("Checking app image");
    if (special_root == nullptr) {
      *error_msg = "Unexpected null special root in app image";
      return false;
    } else if (special_root->IsByteArray()) {
      OatHeader* oat_header = reinterpret_cast<OatHeader*>(special_root->AsByteArray()->GetData());
      if (!oat_header->IsValid()) {
        *error_msg = "Invalid oat header in special root";
        return false;
      }
      if (oat_file->GetVdexFile()->GetNumberOfDexFiles() != oat_header->GetDexFileCount()) {
        *error_msg = "Checksums count does not match";
        return false;
      }
      if (oat_header->IsConcurrentCopying() != gUseReadBarrier) {
        *error_msg = "GCs do not match";
        return false;
      }

      // Check if the dex checksums match the dex files that we just loaded.
      uint32_t* checksums = reinterpret_cast<uint32_t*>(
          reinterpret_cast<uint8_t*>(oat_header) + oat_header->GetHeaderSize());
      for (uint32_t i = 0; i  < oat_header->GetDexFileCount(); ++i) {
        uint32_t dex_checksum = dex_files.at(i)->GetHeader().checksum_;
        if (checksums[i] != dex_checksum) {
          *error_msg = StringPrintf(
              "Image and dex file checksums did not match for %s: image has %d, dex file has %d",
              dex_files.at(i)->GetLocation().c_str(),
              checksums[i],
              dex_checksum);
          return false;
        }
      }

      // Validate the class loader context.
      const char* stored_context = oat_header->GetStoreValueByKey(OatHeader::kClassPathKey);
      if (stored_context == nullptr) {
        *error_msg = "Missing class loader context in special root";
        return false;
      }
      if (context->VerifyClassLoaderContextMatch(stored_context) ==
              ClassLoaderContext::VerificationResult::kMismatch) {
        *error_msg = StringPrintf("Class loader contexts don't match: %s", stored_context);
        return false;
      }

      // Validate the apex versions.
      if (!gc::space::ImageSpace::ValidateApexVersions(*oat_header,
                                                       runtime->GetApexVersions(),
                                                       space->GetImageLocation(),
                                                       error_msg)) {
        return false;
      }

      // Validate the boot classpath.
      const char* bcp = oat_header->GetStoreValueByKey(OatHeader::kBootClassPathKey);
      if (bcp == nullptr) {
        *error_msg = "Missing boot classpath in special root";
        return false;
      }
      std::string runtime_bcp = android::base::Join(runtime->GetBootClassPathLocations(), ':');
      if (strcmp(bcp, runtime_bcp.c_str()) != 0) {
        *error_msg = StringPrintf("Mismatch boot classpath: image has %s, runtime has %s",
                                  bcp,
                                  runtime_bcp.c_str());
        return false;
      }

      // Validate the dex checksums of the boot classpath.
      const char* bcp_checksums =
          oat_header->GetStoreValueByKey(OatHeader::kBootClassPathChecksumsKey);
      if (bcp_checksums == nullptr) {
        *error_msg = "Missing boot classpath checksums in special root";
        return false;
      }
      if (strcmp(bcp_checksums, runtime->GetBootClassPathChecksums().c_str()) != 0) {
        *error_msg = StringPrintf("Mismatch boot classpath checksums: image has %s, runtime has %s",
                                  bcp_checksums,
                                  runtime->GetBootClassPathChecksums().c_str());
        return false;
      }
    } else if (IsBootClassLoader(special_root.Get())) {
      *error_msg = "Unexpected BootClassLoader in app image";
      return false;
    } else if (!special_root->IsClassLoader()) {
      *error_msg = "Unexpected special root in app image";
      return false;
    }
  }

  if (kCheckImageObjects) {
    if (!app_image) {
      if (image_pointer_size_ == PointerSize::k64) {
        ImageChecker<PointerSize::k64>::CheckObjects(heap, space);
      } else {
        ImageChecker<PointerSize::k32>::CheckObjects(heap, space);
      }
    }
  }

  // Set entry point to interpreter if in InterpretOnly mode.
  if (!runtime->IsAotCompiler() &&
      (runtime->GetInstrumentation()->InterpretOnly() ||
       runtime->IsJavaDebuggable())) {
    // Set image methods' entry point to interpreter.
    header.VisitPackedArtMethods([&](ArtMethod& method) REQUIRES_SHARED(Locks::mutator_lock_) {
      if (!method.IsRuntimeMethod()) {
        DCHECK(method.GetDeclaringClass() != nullptr);
        if (!method.IsNative() && !method.IsResolutionMethod()) {
          method.SetEntryPointFromQuickCompiledCodePtrSize(GetQuickToInterpreterBridge(),
                                                            image_pointer_size_);
        }
      }
    }, space->Begin(), image_pointer_size_);
  }

  if (!runtime->IsAotCompiler()) {
    // If the boot image is not loaded by the zygote, we don't need the shared
    // memory optimization.
    // If we are profiling the boot classpath, we disable the shared memory
    // optimization to make sure boot classpath methods all get properly
    // profiled.
    // For debuggable runtimes we don't use AOT code, so don't use shared memory
    // optimization so the methods can be JITed better.
    //
    // We need to disable the flag before doing ResetCounter below, as counters
    // of shared memory method always hold the "hot" value.
    if (!runtime->IsZygote() ||
        runtime->GetJITOptions()->GetProfileSaverOptions().GetProfileBootClassPath() ||
        runtime->IsJavaDebuggable()) {
      header.VisitPackedArtMethods([&](ArtMethod& method) REQUIRES_SHARED(Locks::mutator_lock_) {
        method.ClearMemorySharedMethod();
      }, space->Begin(), image_pointer_size_);
    }

    ScopedTrace trace("AppImage:UpdateCodeItemAndNterp");
    bool can_use_nterp = interpreter::CanRuntimeUseNterp();
    uint16_t hotness_threshold = runtime->GetJITOptions()->GetWarmupThreshold();
    header.VisitPackedArtMethods([&](ArtMethod& method) REQUIRES_SHARED(Locks::mutator_lock_) {
      // In the image, the `data` pointer field of the ArtMethod contains the code
      // item offset. Change this to the actual pointer to the code item.
      if (method.HasCodeItem()) {
        const dex::CodeItem* code_item = method.GetDexFile()->GetCodeItem(
            reinterpret_cast32<uint32_t>(method.GetDataPtrSize(image_pointer_size_)));
        method.SetCodeItem(code_item, method.GetDexFile()->IsCompactDexFile());
        // The hotness counter may have changed since we compiled the image, so
        // reset it with the runtime value.
        method.ResetCounter(hotness_threshold);
      }
      if (method.GetEntryPointFromQuickCompiledCode() == nterp_trampoline_) {
        if (can_use_nterp) {
          // Set image methods' entry point that point to the nterp trampoline to the
          // nterp entry point. This allows taking the fast path when doing a
          // nterp->nterp call.
          DCHECK(!method.StillNeedsClinitCheck());
          method.SetEntryPointFromQuickCompiledCode(interpreter::GetNterpEntryPoint());
        } else {
          method.SetEntryPointFromQuickCompiledCode(GetQuickToInterpreterBridge());
        }
      }
    }, space->Begin(), image_pointer_size_);
  }

  if (runtime->IsVerificationSoftFail()) {
    header.VisitPackedArtMethods([&](ArtMethod& method) REQUIRES_SHARED(Locks::mutator_lock_) {
      if (method.IsManagedAndInvokable()) {
        method.ClearSkipAccessChecks();
      }
    }, space->Begin(), image_pointer_size_);
  }

  ClassTable* class_table = nullptr;
  {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    class_table = InsertClassTableForClassLoader(class_loader.Get());
  }
  // If we have a class table section, read it and use it for verification in
  // UpdateAppImageClassLoadersAndDexCaches.
  ClassTable::ClassSet temp_set;
  const ImageSection& class_table_section = header.GetClassTableSection();
  const bool added_class_table = class_table_section.Size() > 0u;
  if (added_class_table) {
    const uint64_t start_time2 = NanoTime();
    size_t read_count = 0;
    temp_set = ClassTable::ClassSet(space->Begin() + class_table_section.Offset(),
                                    /*make copy*/false,
                                    &read_count);
    VLOG(image) << "Adding class table classes took " << PrettyDuration(NanoTime() - start_time2);
  }
  if (app_image) {
    AppImageLoadingHelper::Update(this, space, class_loader, dex_caches);

    {
      ScopedTrace trace("AppImage:UpdateClassLoaders");
      // Update class loader and resolved strings. If added_class_table is false, the resolved
      // strings were forwarded UpdateAppImageClassLoadersAndDexCaches.
      ObjPtr<mirror::ClassLoader> loader(class_loader.Get());
      for (const ClassTable::TableSlot& root : temp_set) {
        // Note: We probably don't need the read barrier unless we copy the app image objects into
        // the region space.
        ObjPtr<mirror::Class> klass(root.Read());
        // Do not update class loader for boot image classes where the app image
        // class loader is only the initiating loader but not the defining loader.
        if (space->HasAddress(klass.Ptr())) {
          klass->SetClassLoader(loader);
        } else {
          DCHECK(klass->IsBootStrapClassLoaded());
          DCHECK(Runtime::Current()->GetHeap()->ObjectIsInBootImageSpace(klass.Ptr()));
        }
      }
    }

    if (kBitstringSubtypeCheckEnabled) {
      // Every class in the app image has initially SubtypeCheckInfo in the
      // Uninitialized state.
      //
      // The SubtypeCheck invariants imply that a SubtypeCheckInfo is at least Initialized
      // after class initialization is complete. The app image ClassStatus as-is
      // are almost all ClassStatus::Initialized, and being in the
      // SubtypeCheckInfo::kUninitialized state is violating that invariant.
      //
      // Force every app image class's SubtypeCheck to be at least kIninitialized.
      //
      // See also ImageWriter::FixupClass.
      ScopedTrace trace("AppImage:RecacluateSubtypeCheckBitstrings");
      MutexLock subtype_check_lock(Thread::Current(), *Locks::subtype_check_lock_);
      for (const ClassTable::TableSlot& root : temp_set) {
        SubtypeCheck<ObjPtr<mirror::Class>>::EnsureInitialized(root.Read());
      }
    }
  }
  if (!oat_file->GetBssGcRoots().empty()) {
    // Insert oat file to class table for visiting .bss GC roots.
    class_table->InsertOatFile(oat_file);
  }

  if (added_class_table) {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    class_table->AddClassSet(std::move(temp_set));
  }

  if (kIsDebugBuild && app_image) {
    // This verification needs to happen after the classes have been added to the class loader.
    // Since it ensures classes are in the class table.
    ScopedTrace trace("AppImage:Verify");
    VerifyAppImage(header, class_loader, class_table, space);
  }

  VLOG(class_linker) << "Adding image space took " << PrettyDuration(NanoTime() - start_time);
  return true;
}

bool ClassLinker::AddImageSpaces(ArrayRef<gc::space::ImageSpace*> spaces,
                                 Handle<mirror::ClassLoader> class_loader,
                                 ClassLoaderContext* context,
                                 /*out*/ std::vector<std::unique_ptr<const DexFile>>* dex_files,
                                 /*out*/ std::string* error_msg) {
  std::vector<std::vector<std::unique_ptr<const DexFile>>> dex_files_by_space_index;
  for (const gc::space::ImageSpace* space : spaces) {
    std::vector<std::unique_ptr<const DexFile>> space_dex_files;
    if (!OpenAndInitImageDexFiles(space, class_loader, /*out*/ &space_dex_files, error_msg)) {
      return false;
    }
    dex_files_by_space_index.push_back(std::move(space_dex_files));
  }
  // This must be done in a separate loop after all dex files are initialized because there can be
  // references from an image space to another image space that comes after it.
  for (size_t i = 0u, size = spaces.size(); i != size; ++i) {
    std::vector<std::unique_ptr<const DexFile>>& space_dex_files = dex_files_by_space_index[i];
    if (!AddImageSpace(spaces[i], class_loader, context, space_dex_files, error_msg)) {
      return false;
    }
    // Append opened dex files at the end.
    std::move(space_dex_files.begin(), space_dex_files.end(), std::back_inserter(*dex_files));
  }
  return true;
}

void ClassLinker::VisitClassRoots(RootVisitor* visitor, VisitRootFlags flags) {
  // Acquire tracing_enabled before locking class linker lock to prevent lock order violation. Since
  // enabling tracing requires the mutator lock, there are no race conditions here.
  const bool tracing_enabled = Trace::IsTracingEnabled();
  Thread* const self = Thread::Current();
  WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
  if (gUseReadBarrier) {
    // We do not track new roots for CC.
    DCHECK_EQ(0, flags & (kVisitRootFlagNewRoots |
                          kVisitRootFlagClearRootLog |
                          kVisitRootFlagStartLoggingNewRoots |
                          kVisitRootFlagStopLoggingNewRoots));
  }
  if ((flags & kVisitRootFlagAllRoots) != 0) {
    // Argument for how root visiting deals with ArtField and ArtMethod roots.
    // There is 3 GC cases to handle:
    // Non moving concurrent:
    // This case is easy to handle since the reference members of ArtMethod and ArtFields are held
    // live by the class and class roots.
    //
    // Moving non-concurrent:
    // This case needs to call visit VisitNativeRoots in case the classes or dex cache arrays move.
    // To prevent missing roots, this case needs to ensure that there is no
    // suspend points between the point which we allocate ArtMethod arrays and place them in a
    // class which is in the class table.
    //
    // Moving concurrent:
    // Need to make sure to not copy ArtMethods without doing read barriers since the roots are
    // marked concurrently and we don't hold the classlinker_classes_lock_ when we do the copy.
    //
    // Use an unbuffered visitor since the class table uses a temporary GcRoot for holding decoded
    // ClassTable::TableSlot. The buffered root visiting would access a stale stack location for
    // these objects.
    UnbufferedRootVisitor root_visitor(visitor, RootInfo(kRootStickyClass));
    boot_class_table_->VisitRoots(root_visitor);
    // If tracing is enabled, then mark all the class loaders to prevent unloading.
    if ((flags & kVisitRootFlagClassLoader) != 0 || tracing_enabled) {
      for (const ClassLoaderData& data : class_loaders_) {
        GcRoot<mirror::Object> root(GcRoot<mirror::Object>(self->DecodeJObject(data.weak_root)));
        root.VisitRootIfNonNull(visitor, RootInfo(kRootVMInternal));
      }
    }
  } else if (!gUseReadBarrier && (flags & kVisitRootFlagNewRoots) != 0) {
    for (auto& root : new_roots_) {
      ObjPtr<mirror::Object> old_ref = root.Read<kWithoutReadBarrier>();
      root.VisitRoot(visitor, RootInfo(kRootStickyClass));
      ObjPtr<mirror::Object> new_ref = root.Read<kWithoutReadBarrier>();
      // Concurrent moving GC marked new roots through the to-space invariant.
      DCHECK_EQ(new_ref, old_ref);
    }
    for (const OatFile* oat_file : new_bss_roots_boot_oat_files_) {
      for (GcRoot<mirror::Object>& root : oat_file->GetBssGcRoots()) {
        ObjPtr<mirror::Object> old_ref = root.Read<kWithoutReadBarrier>();
        if (old_ref != nullptr) {
          DCHECK(old_ref->IsClass() || old_ref->IsString());
          root.VisitRoot(visitor, RootInfo(kRootStickyClass));
          ObjPtr<mirror::Object> new_ref = root.Read<kWithoutReadBarrier>();
          // Concurrent moving GC marked new roots through the to-space invariant.
          DCHECK_EQ(new_ref, old_ref);
        }
      }
    }
  }
  if (!gUseReadBarrier && (flags & kVisitRootFlagClearRootLog) != 0) {
    new_roots_.clear();
    new_bss_roots_boot_oat_files_.clear();
  }
  if (!gUseReadBarrier && (flags & kVisitRootFlagStartLoggingNewRoots) != 0) {
    log_new_roots_ = true;
  } else if (!gUseReadBarrier && (flags & kVisitRootFlagStopLoggingNewRoots) != 0) {
    log_new_roots_ = false;
  }
  // We deliberately ignore the class roots in the image since we
  // handle image roots by using the MS/CMS rescanning of dirty cards.
}

// Keep in sync with InitCallback. Anything we visit, we need to
// reinit references to when reinitializing a ClassLinker from a
// mapped image.
void ClassLinker::VisitRoots(RootVisitor* visitor, VisitRootFlags flags, bool visit_class_roots) {
  class_roots_.VisitRootIfNonNull(visitor, RootInfo(kRootVMInternal));
  if (visit_class_roots) {
    VisitClassRoots(visitor, flags);
  }
  // Instead of visiting the find_array_class_cache_ drop it so that it doesn't prevent class
  // unloading if we are marking roots.
  DropFindArrayClassCache();
}

class VisitClassLoaderClassesVisitor : public ClassLoaderVisitor {
 public:
  explicit VisitClassLoaderClassesVisitor(ClassVisitor* visitor)
      : visitor_(visitor),
        done_(false) {}

  void Visit(ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::classlinker_classes_lock_, Locks::mutator_lock_) override {
    ClassTable* const class_table = class_loader->GetClassTable();
    if (!done_ && class_table != nullptr) {
      DefiningClassLoaderFilterVisitor visitor(class_loader, visitor_);
      if (!class_table->Visit(visitor)) {
        // If the visitor ClassTable returns false it means that we don't need to continue.
        done_ = true;
      }
    }
  }

 private:
  // Class visitor that limits the class visits from a ClassTable to the classes with
  // the provided defining class loader. This filter is used to avoid multiple visits
  // of the same class which can be recorded for multiple initiating class loaders.
  class DefiningClassLoaderFilterVisitor : public ClassVisitor {
   public:
    DefiningClassLoaderFilterVisitor(ObjPtr<mirror::ClassLoader> defining_class_loader,
                                     ClassVisitor* visitor)
        : defining_class_loader_(defining_class_loader), visitor_(visitor) { }

    bool operator()(ObjPtr<mirror::Class> klass) override REQUIRES_SHARED(Locks::mutator_lock_) {
      if (klass->GetClassLoader() != defining_class_loader_) {
        return true;
      }
      return (*visitor_)(klass);
    }

    const ObjPtr<mirror::ClassLoader> defining_class_loader_;
    ClassVisitor* const visitor_;
  };

  ClassVisitor* const visitor_;
  // If done is true then we don't need to do any more visiting.
  bool done_;
};

void ClassLinker::VisitClassesInternal(ClassVisitor* visitor) {
  if (boot_class_table_->Visit(*visitor)) {
    VisitClassLoaderClassesVisitor loader_visitor(visitor);
    VisitClassLoaders(&loader_visitor);
  }
}

void ClassLinker::VisitClasses(ClassVisitor* visitor) {
  Thread* const self = Thread::Current();
  ReaderMutexLock mu(self, *Locks::classlinker_classes_lock_);
  // Not safe to have thread suspension when we are holding a lock.
  if (self != nullptr) {
    ScopedAssertNoThreadSuspension nts(__FUNCTION__);
    VisitClassesInternal(visitor);
  } else {
    VisitClassesInternal(visitor);
  }
}

class GetClassesInToVector : public ClassVisitor {
 public:
  bool operator()(ObjPtr<mirror::Class> klass) override {
    classes_.push_back(klass);
    return true;
  }
  std::vector<ObjPtr<mirror::Class>> classes_;
};

class GetClassInToObjectArray : public ClassVisitor {
 public:
  explicit GetClassInToObjectArray(mirror::ObjectArray<mirror::Class>* arr)
      : arr_(arr), index_(0) {}

  bool operator()(ObjPtr<mirror::Class> klass) override REQUIRES_SHARED(Locks::mutator_lock_) {
    ++index_;
    if (index_ <= arr_->GetLength()) {
      arr_->Set(index_ - 1, klass);
      return true;
    }
    return false;
  }

  bool Succeeded() const REQUIRES_SHARED(Locks::mutator_lock_) {
    return index_ <= arr_->GetLength();
  }

 private:
  mirror::ObjectArray<mirror::Class>* const arr_;
  int32_t index_;
};

void ClassLinker::VisitClassesWithoutClassesLock(ClassVisitor* visitor) {
  // TODO: it may be possible to avoid secondary storage if we iterate over dex caches. The problem
  // is avoiding duplicates.
  if (!kMovingClasses) {
    ScopedAssertNoThreadSuspension nts(__FUNCTION__);
    GetClassesInToVector accumulator;
    VisitClasses(&accumulator);
    for (ObjPtr<mirror::Class> klass : accumulator.classes_) {
      if (!visitor->operator()(klass)) {
        return;
      }
    }
  } else {
    Thread* const self = Thread::Current();
    StackHandleScope<1> hs(self);
    auto classes = hs.NewHandle<mirror::ObjectArray<mirror::Class>>(nullptr);
    // We size the array assuming classes won't be added to the class table during the visit.
    // If this assumption fails we iterate again.
    while (true) {
      size_t class_table_size;
      {
        ReaderMutexLock mu(self, *Locks::classlinker_classes_lock_);
        // Add 100 in case new classes get loaded when we are filling in the object array.
        class_table_size = NumZygoteClasses() + NumNonZygoteClasses() + 100;
      }
      ObjPtr<mirror::Class> array_of_class = GetClassRoot<mirror::ObjectArray<mirror::Class>>(this);
      classes.Assign(
          mirror::ObjectArray<mirror::Class>::Alloc(self, array_of_class, class_table_size));
      CHECK(classes != nullptr);  // OOME.
      GetClassInToObjectArray accumulator(classes.Get());
      VisitClasses(&accumulator);
      if (accumulator.Succeeded()) {
        break;
      }
    }
    for (int32_t i = 0; i < classes->GetLength(); ++i) {
      // If the class table shrank during creation of the clases array we expect null elements. If
      // the class table grew then the loop repeats. If classes are created after the loop has
      // finished then we don't visit.
      ObjPtr<mirror::Class> klass = classes->Get(i);
      if (klass != nullptr && !visitor->operator()(klass)) {
        return;
      }
    }
  }
}

ClassLinker::~ClassLinker() {
  Thread* const self = Thread::Current();
  for (const ClassLoaderData& data : class_loaders_) {
    // CHA unloading analysis is not needed. No negative consequences are expected because
    // all the classloaders are deleted at the same time.
    PrepareToDeleteClassLoader(self, data, /*cleanup_cha=*/false);
  }
  for (const ClassLoaderData& data : class_loaders_) {
    delete data.allocator;
    delete data.class_table;
  }
  class_loaders_.clear();
  while (!running_visibly_initialized_callbacks_.empty()) {
    std::unique_ptr<VisiblyInitializedCallback> callback(
        std::addressof(running_visibly_initialized_callbacks_.front()));
    running_visibly_initialized_callbacks_.pop_front();
  }
}

void ClassLinker::PrepareToDeleteClassLoader(Thread* self,
                                             const ClassLoaderData& data,
                                             bool cleanup_cha) {
  Runtime* const runtime = Runtime::Current();
  JavaVMExt* const vm = runtime->GetJavaVM();
  vm->DeleteWeakGlobalRef(self, data.weak_root);
  // Notify the JIT that we need to remove the methods and/or profiling info.
  if (runtime->GetJit() != nullptr) {
    jit::JitCodeCache* code_cache = runtime->GetJit()->GetCodeCache();
    if (code_cache != nullptr) {
      // For the JIT case, RemoveMethodsIn removes the CHA dependencies.
      code_cache->RemoveMethodsIn(self, *data.allocator);
    }
  } else if (cha_ != nullptr) {
    // If we don't have a JIT, we need to manually remove the CHA dependencies manually.
    cha_->RemoveDependenciesForLinearAlloc(self, data.allocator);
  }
  // Cleanup references to single implementation ArtMethods that will be deleted.
  if (cleanup_cha) {
    CHAOnDeleteUpdateClassVisitor visitor(data.allocator);
    data.class_table->Visit<kWithoutReadBarrier>(visitor);
  }
  {
    MutexLock lock(self, critical_native_code_with_clinit_check_lock_);
    auto end = critical_native_code_with_clinit_check_.end();
    for (auto it = critical_native_code_with_clinit_check_.begin(); it != end; ) {
      if (data.allocator->ContainsUnsafe(it->first)) {
        it = critical_native_code_with_clinit_check_.erase(it);
      } else {
        ++it;
      }
    }
  }
}

ObjPtr<mirror::PointerArray> ClassLinker::AllocPointerArray(Thread* self, size_t length) {
  return ObjPtr<mirror::PointerArray>::DownCast(
      image_pointer_size_ == PointerSize::k64
          ? ObjPtr<mirror::Array>(mirror::LongArray::Alloc(self, length))
          : ObjPtr<mirror::Array>(mirror::IntArray::Alloc(self, length)));
}

ObjPtr<mirror::DexCache> ClassLinker::AllocDexCache(Thread* self, const DexFile& dex_file) {
  StackHandleScope<1> hs(self);
  auto dex_cache(hs.NewHandle(ObjPtr<mirror::DexCache>::DownCast(
      GetClassRoot<mirror::DexCache>(this)->AllocObject(self))));
  if (dex_cache == nullptr) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  // Use InternWeak() so that the location String can be collected when the ClassLoader
  // with this DexCache is collected.
  ObjPtr<mirror::String> location = intern_table_->InternWeak(dex_file.GetLocation().c_str());
  if (location == nullptr) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  dex_cache->SetLocation(location);
  return dex_cache.Get();
}

ObjPtr<mirror::DexCache> ClassLinker::AllocAndInitializeDexCache(
    Thread* self, const DexFile& dex_file, ObjPtr<mirror::ClassLoader> class_loader) {
  StackHandleScope<1> hs(self);
  Handle<mirror::ClassLoader> h_class_loader(hs.NewHandle(class_loader));
  ObjPtr<mirror::DexCache> dex_cache = AllocDexCache(self, dex_file);
  if (dex_cache != nullptr) {
    WriterMutexLock mu(self, *Locks::dex_lock_);
    dex_cache->Initialize(&dex_file, h_class_loader.Get());
  }
  return dex_cache;
}

template <bool kMovable, typename PreFenceVisitor>
ObjPtr<mirror::Class> ClassLinker::AllocClass(Thread* self,
                                              ObjPtr<mirror::Class> java_lang_Class,
                                              uint32_t class_size,
                                              const PreFenceVisitor& pre_fence_visitor) {
  DCHECK_GE(class_size, sizeof(mirror::Class));
  gc::Heap* heap = Runtime::Current()->GetHeap();
  ObjPtr<mirror::Object> k = (kMovingClasses && kMovable) ?
      heap->AllocObject(self, java_lang_Class, class_size, pre_fence_visitor) :
      heap->AllocNonMovableObject(self, java_lang_Class, class_size, pre_fence_visitor);
  if (UNLIKELY(k == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  return k->AsClass();
}

template <bool kMovable>
ObjPtr<mirror::Class> ClassLinker::AllocClass(Thread* self,
                                              ObjPtr<mirror::Class> java_lang_Class,
                                              uint32_t class_size) {
  mirror::Class::InitializeClassVisitor visitor(class_size);
  return AllocClass<kMovable>(self, java_lang_Class, class_size, visitor);
}

ObjPtr<mirror::Class> ClassLinker::AllocClass(Thread* self, uint32_t class_size) {
  return AllocClass(self, GetClassRoot<mirror::Class>(this), class_size);
}

void ClassLinker::AllocPrimitiveArrayClass(Thread* self,
                                           ClassRoot primitive_root,
                                           ClassRoot array_root) {
  // We make this class non-movable for the unlikely case where it were to be
  // moved by a sticky-bit (minor) collection when using the Generational
  // Concurrent Copying (CC) collector, potentially creating a stale reference
  // in the `klass_` field of one of its instances allocated in the Large-Object
  // Space (LOS) -- see the comment about the dirty card scanning logic in
  // art::gc::collector::ConcurrentCopying::MarkingPhase.
  ObjPtr<mirror::Class> array_class = AllocClass</* kMovable= */ false>(
      self, GetClassRoot<mirror::Class>(this), mirror::Array::ClassSize(image_pointer_size_));
  ObjPtr<mirror::Class> component_type = GetClassRoot(primitive_root, this);
  DCHECK(component_type->IsPrimitive());
  array_class->SetComponentType(component_type);
  SetClassRoot(array_root, array_class);
}

void ClassLinker::FinishArrayClassSetup(ObjPtr<mirror::Class> array_class) {
  ObjPtr<mirror::Class> java_lang_Object = GetClassRoot<mirror::Object>(this);
  array_class->SetSuperClass(java_lang_Object);
  array_class->SetVTable(java_lang_Object->GetVTable());
  array_class->SetPrimitiveType(Primitive::kPrimNot);
  ObjPtr<mirror::Class> component_type = array_class->GetComponentType();
  array_class->SetClassFlags(component_type->IsPrimitive()
                                 ? mirror::kClassFlagNoReferenceFields
                                 : mirror::kClassFlagObjectArray);
  array_class->SetClassLoader(component_type->GetClassLoader());
  array_class->SetStatusForPrimitiveOrArray(ClassStatus::kLoaded);
  array_class->PopulateEmbeddedVTable(image_pointer_size_);
  ImTable* object_imt = java_lang_Object->GetImt(image_pointer_size_);
  array_class->SetImt(object_imt, image_pointer_size_);
  DCHECK_EQ(array_class->NumMethods(), 0u);

  // don't need to set new_class->SetObjectSize(..)
  // because Object::SizeOf delegates to Array::SizeOf

  // All arrays have java/lang/Cloneable and java/io/Serializable as
  // interfaces.  We need to set that up here, so that stuff like
  // "instanceof" works right.

  // Use the single, global copies of "interfaces" and "iftable"
  // (remember not to free them for arrays).
  {
    ObjPtr<mirror::IfTable> array_iftable = GetArrayIfTable();
    CHECK(array_iftable != nullptr);
    array_class->SetIfTable(array_iftable);
  }

  // Inherit access flags from the component type.
  int access_flags = component_type->GetAccessFlags();
  // Lose any implementation detail flags; in particular, arrays aren't finalizable.
  access_flags &= kAccJavaFlagsMask;
  // Arrays can't be used as a superclass or interface, so we want to add "abstract final"
  // and remove "interface".
  access_flags |= kAccAbstract | kAccFinal;
  access_flags &= ~kAccInterface;

  array_class->SetAccessFlagsDuringLinking(access_flags);

  // Array classes are fully initialized either during single threaded startup,
  // or from a pre-fence visitor, so visibly initialized.
  array_class->SetStatusForPrimitiveOrArray(ClassStatus::kVisiblyInitialized);
}

void ClassLinker::FinishCoreArrayClassSetup(ClassRoot array_root) {
  // Do not hold lock on the array class object, the initialization of
  // core array classes is done while the process is still single threaded.
  ObjPtr<mirror::Class> array_class = GetClassRoot(array_root, this);
  FinishArrayClassSetup(array_class);

  std::string temp;
  const char* descriptor = array_class->GetDescriptor(&temp);
  size_t hash = ComputeModifiedUtf8Hash(descriptor);
  ObjPtr<mirror::Class> existing = InsertClass(descriptor, array_class, hash);
  CHECK(existing == nullptr);
}

ObjPtr<mirror::ObjectArray<mirror::StackTraceElement>> ClassLinker::AllocStackTraceElementArray(
    Thread* self,
    size_t length) {
  return mirror::ObjectArray<mirror::StackTraceElement>::Alloc(
      self, GetClassRoot<mirror::ObjectArray<mirror::StackTraceElement>>(this), length);
}

ObjPtr<mirror::Class> ClassLinker::EnsureResolved(Thread* self,
                                                  const char* descriptor,
                                                  ObjPtr<mirror::Class> klass) {
  DCHECK(klass != nullptr);
  if (kIsDebugBuild) {
    StackHandleScope<1> hs(self);
    HandleWrapperObjPtr<mirror::Class> h = hs.NewHandleWrapper(&klass);
    Thread::PoisonObjectPointersIfDebug();
  }

  // For temporary classes we must wait for them to be retired.
  if (init_done_ && klass->IsTemp()) {
    CHECK(!klass->IsResolved());
    if (klass->IsErroneousUnresolved()) {
      ThrowEarlierClassFailure(klass);
      return nullptr;
    }
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> h_class(hs.NewHandle(klass));
    ObjectLock<mirror::Class> lock(self, h_class);
    // Loop and wait for the resolving thread to retire this class.
    while (!h_class->IsRetired() && !h_class->IsErroneousUnresolved()) {
      lock.WaitIgnoringInterrupts();
    }
    if (h_class->IsErroneousUnresolved()) {
      ThrowEarlierClassFailure(h_class.Get());
      return nullptr;
    }
    CHECK(h_class->IsRetired());
    // Get the updated class from class table.
    klass = LookupClass(self, descriptor, h_class.Get()->GetClassLoader());
  }

  // Wait for the class if it has not already been linked.
  size_t index = 0;
  // Maximum number of yield iterations until we start sleeping.
  static const size_t kNumYieldIterations = 1000;
  // How long each sleep is in us.
  static const size_t kSleepDurationUS = 1000;  // 1 ms.
  while (!klass->IsResolved() && !klass->IsErroneousUnresolved()) {
    StackHandleScope<1> hs(self);
    HandleWrapperObjPtr<mirror::Class> h_class(hs.NewHandleWrapper(&klass));
    {
      ObjectTryLock<mirror::Class> lock(self, h_class);
      // Can not use a monitor wait here since it may block when returning and deadlock if another
      // thread has locked klass.
      if (lock.Acquired()) {
        // Check for circular dependencies between classes, the lock is required for SetStatus.
        if (!h_class->IsResolved() && h_class->GetClinitThreadId() == self->GetTid()) {
          ThrowClassCircularityError(h_class.Get());
          mirror::Class::SetStatus(h_class, ClassStatus::kErrorUnresolved, self);
          return nullptr;
        }
      }
    }
    {
      // Handle wrapper deals with klass moving.
      ScopedThreadSuspension sts(self, ThreadState::kSuspended);
      if (index < kNumYieldIterations) {
        sched_yield();
      } else {
        usleep(kSleepDurationUS);
      }
    }
    ++index;
  }

  if (klass->IsErroneousUnresolved()) {
    ThrowEarlierClassFailure(klass);
    return nullptr;
  }
  // Return the loaded class.  No exceptions should be pending.
  CHECK(klass->IsResolved()) << klass->PrettyClass();
  self->AssertNoPendingException();
  return klass;
}

using ClassPathEntry = std::pair<const DexFile*, const dex::ClassDef*>;

// Search a collection of DexFiles for a descriptor
ClassPathEntry FindInClassPath(const char* descriptor,
                               size_t hash, const std::vector<const DexFile*>& class_path) {
  for (const DexFile* dex_file : class_path) {
    DCHECK(dex_file != nullptr);
    const dex::ClassDef* dex_class_def = OatDexFile::FindClassDef(*dex_file, descriptor, hash);
    if (dex_class_def != nullptr) {
      return ClassPathEntry(dex_file, dex_class_def);
    }
  }
  return ClassPathEntry(nullptr, nullptr);
}

// Helper macro to make sure each class loader lookup call handles the case the
// class loader is not recognized, or the lookup threw an exception.
#define RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(call_, result_, thread_) \
do {                                                                          \
  auto local_call = call_;                                                    \
  if (!local_call) {                                                          \
    return false;                                                             \
  }                                                                           \
  auto local_result = result_;                                                \
  if (local_result != nullptr) {                                              \
    return true;                                                              \
  }                                                                           \
  auto local_thread = thread_;                                                \
  if (local_thread->IsExceptionPending()) {                                   \
    /* Pending exception means there was an error other than */               \
    /* ClassNotFound that must be returned to the caller. */                  \
    return false;                                                             \
  }                                                                           \
} while (0)

bool ClassLinker::FindClassInSharedLibraries(Thread* self,
                                             const char* descriptor,
                                             size_t hash,
                                             Handle<mirror::ClassLoader> class_loader,
                                             /*out*/ ObjPtr<mirror::Class>* result) {
  ArtField* field = WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoaders;
  return FindClassInSharedLibrariesHelper(self, descriptor, hash, class_loader, field, result);
}

bool ClassLinker::FindClassInSharedLibrariesHelper(Thread* self,
                                                   const char* descriptor,
                                                   size_t hash,
                                                   Handle<mirror::ClassLoader> class_loader,
                                                   ArtField* field,
                                                   /*out*/ ObjPtr<mirror::Class>* result) {
  ObjPtr<mirror::Object> raw_shared_libraries = field->GetObject(class_loader.Get());
  if (raw_shared_libraries == nullptr) {
    return true;
  }

  StackHandleScope<2> hs(self);
  Handle<mirror::ObjectArray<mirror::ClassLoader>> shared_libraries(
      hs.NewHandle(raw_shared_libraries->AsObjectArray<mirror::ClassLoader>()));
  MutableHandle<mirror::ClassLoader> temp_loader = hs.NewHandle<mirror::ClassLoader>(nullptr);
  for (auto loader : shared_libraries.Iterate<mirror::ClassLoader>()) {
    temp_loader.Assign(loader);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBaseDexClassLoader(self, descriptor, hash, temp_loader, result),
        *result,
        self);
  }
  return true;
}

bool ClassLinker::FindClassInSharedLibrariesAfter(Thread* self,
                                                  const char* descriptor,
                                                  size_t hash,
                                                  Handle<mirror::ClassLoader> class_loader,
                                                  /*out*/ ObjPtr<mirror::Class>* result) {
  ArtField* field = WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoadersAfter;
  return FindClassInSharedLibrariesHelper(self, descriptor, hash, class_loader, field, result);
}

bool ClassLinker::FindClassInBaseDexClassLoader(Thread* self,
                                                const char* descriptor,
                                                size_t hash,
                                                Handle<mirror::ClassLoader> class_loader,
                                                /*out*/ ObjPtr<mirror::Class>* result) {
  // Termination case: boot class loader.
  if (IsBootClassLoader(class_loader.Get())) {
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBootClassLoaderClassPath(self, descriptor, hash, result), *result, self);
    return true;
  }

  if (IsPathOrDexClassLoader(class_loader) || IsInMemoryDexClassLoader(class_loader)) {
    // For regular path or dex class loader the search order is:
    //    - parent
    //    - shared libraries
    //    - class loader dex files

    // Create a handle as RegisterDexFile may allocate dex caches (and cause thread suspension).
    StackHandleScope<1> hs(self);
    Handle<mirror::ClassLoader> h_parent(hs.NewHandle(class_loader->GetParent()));
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBaseDexClassLoader(self, descriptor, hash, h_parent, result),
        *result,
        self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInSharedLibraries(self, descriptor, hash, class_loader, result),
        *result,
        self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBaseDexClassLoaderClassPath(self, descriptor, hash, class_loader, result),
        *result,
        self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInSharedLibrariesAfter(self, descriptor, hash, class_loader, result),
        *result,
        self);
    // We did not find a class, but the class loader chain was recognized, so we
    // return true.
    return true;
  }

  if (IsDelegateLastClassLoader(class_loader)) {
    // For delegate last, the search order is:
    //    - boot class path
    //    - shared libraries
    //    - class loader dex files
    //    - parent
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBootClassLoaderClassPath(self, descriptor, hash, result), *result, self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInSharedLibraries(self, descriptor, hash, class_loader, result),
        *result,
        self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBaseDexClassLoaderClassPath(self, descriptor, hash, class_loader, result),
        *result,
        self);
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInSharedLibrariesAfter(self, descriptor, hash, class_loader, result),
        *result,
        self);

    // Create a handle as RegisterDexFile may allocate dex caches (and cause thread suspension).
    StackHandleScope<1> hs(self);
    Handle<mirror::ClassLoader> h_parent(hs.NewHandle(class_loader->GetParent()));
    RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION(
        FindClassInBaseDexClassLoader(self, descriptor, hash, h_parent, result),
        *result,
        self);
    // We did not find a class, but the class loader chain was recognized, so we
    // return true.
    return true;
  }

  // Unsupported class loader.
  *result = nullptr;
  return false;
}

#undef RETURN_IF_UNRECOGNIZED_OR_FOUND_OR_EXCEPTION

namespace {

// Matches exceptions caught in DexFile.defineClass.
ALWAYS_INLINE bool MatchesDexFileCaughtExceptions(ObjPtr<mirror::Throwable> throwable,
                                                  ClassLinker* class_linker)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return
      // ClassNotFoundException.
      throwable->InstanceOf(GetClassRoot(ClassRoot::kJavaLangClassNotFoundException,
                                         class_linker))
      ||
      // NoClassDefFoundError. TODO: Reconsider this. b/130746382.
      throwable->InstanceOf(Runtime::Current()->GetPreAllocatedNoClassDefFoundError()->GetClass());
}

// Clear exceptions caught in DexFile.defineClass.
ALWAYS_INLINE void FilterDexFileCaughtExceptions(Thread* self, ClassLinker* class_linker)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (MatchesDexFileCaughtExceptions(self->GetException(), class_linker)) {
    self->ClearException();
  }
}

}  // namespace

// Finds the class in the boot class loader.
// If the class is found the method returns the resolved class. Otherwise it returns null.
bool ClassLinker::FindClassInBootClassLoaderClassPath(Thread* self,
                                                      const char* descriptor,
                                                      size_t hash,
                                                      /*out*/ ObjPtr<mirror::Class>* result) {
  ClassPathEntry pair = FindInClassPath(descriptor, hash, boot_class_path_);
  if (pair.second != nullptr) {
    ObjPtr<mirror::Class> klass = LookupClass(self, descriptor, hash, nullptr);
    if (klass != nullptr) {
      *result = EnsureResolved(self, descriptor, klass);
    } else {
      *result = DefineClass(self,
                            descriptor,
                            hash,
                            ScopedNullHandle<mirror::ClassLoader>(),
                            *pair.first,
                            *pair.second);
    }
    if (*result == nullptr) {
      CHECK(self->IsExceptionPending()) << descriptor;
      FilterDexFileCaughtExceptions(self, this);
    }
  }
  // The boot classloader is always a known lookup.
  return true;
}

bool ClassLinker::FindClassInBaseDexClassLoaderClassPath(
    Thread* self,
    const char* descriptor,
    size_t hash,
    Handle<mirror::ClassLoader> class_loader,
    /*out*/ ObjPtr<mirror::Class>* result) {
  DCHECK(IsPathOrDexClassLoader(class_loader) ||
         IsInMemoryDexClassLoader(class_loader) ||
         IsDelegateLastClassLoader(class_loader))
      << "Unexpected class loader for descriptor " << descriptor;

  const DexFile* dex_file = nullptr;
  const dex::ClassDef* class_def = nullptr;
  ObjPtr<mirror::Class> ret;
  auto find_class_def = [&](const DexFile* cp_dex_file) REQUIRES_SHARED(Locks::mutator_lock_) {
    const dex::ClassDef* cp_class_def = OatDexFile::FindClassDef(*cp_dex_file, descriptor, hash);
    if (cp_class_def != nullptr) {
      dex_file = cp_dex_file;
      class_def = cp_class_def;
      return false;  // Found a class definition, stop visit.
    }
    return true;  // Continue with the next DexFile.
  };
  VisitClassLoaderDexFiles(self, class_loader, find_class_def);

  if (class_def != nullptr) {
    *result = DefineClass(self, descriptor, hash, class_loader, *dex_file, *class_def);
    if (UNLIKELY(*result == nullptr)) {
      CHECK(self->IsExceptionPending()) << descriptor;
      FilterDexFileCaughtExceptions(self, this);
    } else {
      DCHECK(!self->IsExceptionPending());
    }
  }
  // A BaseDexClassLoader is always a known lookup.
  return true;
}

ObjPtr<mirror::Class> ClassLinker::FindClass(Thread* self,
                                             const char* descriptor,
                                             Handle<mirror::ClassLoader> class_loader) {
  DCHECK_NE(*descriptor, '\0') << "descriptor is empty string";
  DCHECK(self != nullptr);
  self->AssertNoPendingException();
  self->PoisonObjectPointers();  // For DefineClass, CreateArrayClass, etc...
  if (descriptor[1] == '\0') {
    // only the descriptors of primitive types should be 1 character long, also avoid class lookup
    // for primitive classes that aren't backed by dex files.
    return FindPrimitiveClass(descriptor[0]);
  }
  const size_t hash = ComputeModifiedUtf8Hash(descriptor);
  // Find the class in the loaded classes table.
  ObjPtr<mirror::Class> klass = LookupClass(self, descriptor, hash, class_loader.Get());
  if (klass != nullptr) {
    return EnsureResolved(self, descriptor, klass);
  }
  // Class is not yet loaded.
  if (descriptor[0] != '[' && class_loader == nullptr) {
    // Non-array class and the boot class loader, search the boot class path.
    ClassPathEntry pair = FindInClassPath(descriptor, hash, boot_class_path_);
    if (pair.second != nullptr) {
      return DefineClass(self,
                         descriptor,
                         hash,
                         ScopedNullHandle<mirror::ClassLoader>(),
                         *pair.first,
                         *pair.second);
    } else {
      // The boot class loader is searched ahead of the application class loader, failures are
      // expected and will be wrapped in a ClassNotFoundException. Use the pre-allocated error to
      // trigger the chaining with a proper stack trace.
      ObjPtr<mirror::Throwable> pre_allocated =
          Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
      self->SetException(pre_allocated);
      return nullptr;
    }
  }
  ObjPtr<mirror::Class> result_ptr;
  bool descriptor_equals;
  if (descriptor[0] == '[') {
    result_ptr = CreateArrayClass(self, descriptor, hash, class_loader);
    DCHECK_EQ(result_ptr == nullptr, self->IsExceptionPending());
    DCHECK(result_ptr == nullptr || result_ptr->DescriptorEquals(descriptor));
    descriptor_equals = true;
  } else {
    ScopedObjectAccessUnchecked soa(self);
    bool known_hierarchy =
        FindClassInBaseDexClassLoader(self, descriptor, hash, class_loader, &result_ptr);
    if (result_ptr != nullptr) {
      // The chain was understood and we found the class. We still need to add the class to
      // the class table to protect from racy programs that can try and redefine the path list
      // which would change the Class<?> returned for subsequent evaluation of const-class.
      DCHECK(known_hierarchy);
      DCHECK(result_ptr->DescriptorEquals(descriptor));
      descriptor_equals = true;
    } else if (!self->IsExceptionPending()) {
      // Either the chain wasn't understood or the class wasn't found.
      // If there is a pending exception we didn't clear, it is a not a ClassNotFoundException and
      // we should return it instead of silently clearing and retrying.
      //
      // If the chain was understood but we did not find the class, let the Java-side
      // rediscover all this and throw the exception with the right stack trace. Note that
      // the Java-side could still succeed for racy programs if another thread is actively
      // modifying the class loader's path list.

      // The runtime is not allowed to call into java from a runtime-thread so just abort.
      if (self->IsRuntimeThread()) {
        // Oops, we can't call into java so we can't run actual class-loader code.
        // This is true for e.g. for the compiler (jit or aot).
        ObjPtr<mirror::Throwable> pre_allocated =
            Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
        self->SetException(pre_allocated);
        return nullptr;
      }

      // Inlined DescriptorToDot(descriptor) with extra validation.
      //
      // Throw NoClassDefFoundError early rather than potentially load a class only to fail
      // the DescriptorEquals() check below and give a confusing error message. For example,
      // when native code erroneously calls JNI GetFieldId() with signature "java/lang/String"
      // instead of "Ljava/lang/String;", the message below using the "dot" names would be
      // "class loader [...] returned class java.lang.String instead of java.lang.String".
      size_t descriptor_length = strlen(descriptor);
      if (UNLIKELY(descriptor[0] != 'L') ||
          UNLIKELY(descriptor[descriptor_length - 1] != ';') ||
          UNLIKELY(memchr(descriptor + 1, '.', descriptor_length - 2) != nullptr)) {
        ThrowNoClassDefFoundError("Invalid descriptor: %s.", descriptor);
        return nullptr;
      }

      std::string class_name_string(descriptor + 1, descriptor_length - 2);
      std::replace(class_name_string.begin(), class_name_string.end(), '/', '.');
      if (known_hierarchy &&
          fast_class_not_found_exceptions_ &&
          !Runtime::Current()->IsJavaDebuggable()) {
        // For known hierarchy, we know that the class is going to throw an exception. If we aren't
        // debuggable, optimize this path by throwing directly here without going back to Java
        // language. This reduces how many ClassNotFoundExceptions happen.
        self->ThrowNewExceptionF("Ljava/lang/ClassNotFoundException;",
                                 "%s",
                                 class_name_string.c_str());
      } else {
        StackHandleScope<1u> hs(self);
        Handle<mirror::String> class_name_object = hs.NewHandle(
            mirror::String::AllocFromModifiedUtf8(self, class_name_string.c_str()));
        if (class_name_object == nullptr) {
          DCHECK(self->IsExceptionPending());  // OOME.
          return nullptr;
        }
        DCHECK(class_loader != nullptr);
        result_ptr = ObjPtr<mirror::Class>::DownCast(
            WellKnownClasses::java_lang_ClassLoader_loadClass->InvokeVirtual<'L', 'L'>(
                self, class_loader.Get(), class_name_object.Get()));
        if (result_ptr == nullptr && !self->IsExceptionPending()) {
          // broken loader - throw NPE to be compatible with Dalvik
          ThrowNullPointerException(StringPrintf("ClassLoader.loadClass returned null for %s",
                                                 class_name_string.c_str()).c_str());
          return nullptr;
        }
        // Check the name of the returned class.
        descriptor_equals = (result_ptr != nullptr) && result_ptr->DescriptorEquals(descriptor);
      }
    } else {
      DCHECK(!MatchesDexFileCaughtExceptions(self->GetException(), this));
    }
  }

  if (self->IsExceptionPending()) {
    // If the ClassLoader threw or array class allocation failed, pass that exception up.
    // However, to comply with the RI behavior, first check if another thread succeeded.
    result_ptr = LookupClass(self, descriptor, hash, class_loader.Get());
    if (result_ptr != nullptr && !result_ptr->IsErroneous()) {
      self->ClearException();
      return EnsureResolved(self, descriptor, result_ptr);
    }
    return nullptr;
  }

  // Try to insert the class to the class table, checking for mismatch.
  ObjPtr<mirror::Class> old;
  {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    ClassTable* const class_table = InsertClassTableForClassLoader(class_loader.Get());
    old = class_table->Lookup(descriptor, hash);
    if (old == nullptr) {
      old = result_ptr;  // For the comparison below, after releasing the lock.
      if (descriptor_equals) {
        class_table->InsertWithHash(result_ptr, hash);
        WriteBarrier::ForEveryFieldWrite(class_loader.Get());
      }  // else throw below, after releasing the lock.
    }
  }
  if (UNLIKELY(old != result_ptr)) {
    // Return `old` (even if `!descriptor_equals`) to mimic the RI behavior for parallel
    // capable class loaders.  (All class loaders are considered parallel capable on Android.)
    ObjPtr<mirror::Class> loader_class = class_loader->GetClass();
    const char* loader_class_name =
        loader_class->GetDexFile().StringByTypeIdx(loader_class->GetDexTypeIndex());
    LOG(WARNING) << "Initiating class loader of type " << DescriptorToDot(loader_class_name)
        << " is not well-behaved; it returned a different Class for racing loadClass(\""
        << DescriptorToDot(descriptor) << "\").";
    return EnsureResolved(self, descriptor, old);
  }
  if (UNLIKELY(!descriptor_equals)) {
    std::string result_storage;
    const char* result_name = result_ptr->GetDescriptor(&result_storage);
    std::string loader_storage;
    const char* loader_class_name = class_loader->GetClass()->GetDescriptor(&loader_storage);
    ThrowNoClassDefFoundError(
        "Initiating class loader of type %s returned class %s instead of %s.",
        DescriptorToDot(loader_class_name).c_str(),
        DescriptorToDot(result_name).c_str(),
        DescriptorToDot(descriptor).c_str());
    return nullptr;
  }
  // Success.
  return result_ptr;
}

// Helper for maintaining DefineClass counting. We need to notify callbacks when we start/end a
// define-class and how many recursive DefineClasses we are at in order to allow for doing  things
// like pausing class definition.
struct ScopedDefiningClass {
 public:
  explicit ScopedDefiningClass(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_)
      : self_(self), returned_(false) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
    Runtime::Current()->GetRuntimeCallbacks()->BeginDefineClass();
    self_->IncrDefineClassCount();
  }
  ~ScopedDefiningClass() REQUIRES_SHARED(Locks::mutator_lock_) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
    CHECK(returned_);
  }

  ObjPtr<mirror::Class> Finish(Handle<mirror::Class> h_klass)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    CHECK(!returned_);
    self_->DecrDefineClassCount();
    Runtime::Current()->GetRuntimeCallbacks()->EndDefineClass();
    Thread::PoisonObjectPointersIfDebug();
    returned_ = true;
    return h_klass.Get();
  }

  ObjPtr<mirror::Class> Finish(ObjPtr<mirror::Class> klass)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    StackHandleScope<1> hs(self_);
    Handle<mirror::Class> h_klass(hs.NewHandle(klass));
    return Finish(h_klass);
  }

  ObjPtr<mirror::Class> Finish([[maybe_unused]] nullptr_t np)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ScopedNullHandle<mirror::Class> snh;
    return Finish(snh);
  }

 private:
  Thread* self_;
  bool returned_;
};

ObjPtr<mirror::Class> ClassLinker::DefineClass(Thread* self,
                                               const char* descriptor,
                                               size_t hash,
                                               Handle<mirror::ClassLoader> class_loader,
                                               const DexFile& dex_file,
                                               const dex::ClassDef& dex_class_def) {
  ScopedDefiningClass sdc(self);
  StackHandleScope<3> hs(self);
  metrics::AutoTimer timer{GetMetrics()->ClassLoadingTotalTime()};
  metrics::AutoTimer timeDelta{GetMetrics()->ClassLoadingTotalTimeDelta()};
  auto klass = hs.NewHandle<mirror::Class>(nullptr);

  // Load the class from the dex file.
  if (UNLIKELY(!init_done_)) {
    // finish up init of hand crafted class_roots_
    if (strcmp(descriptor, "Ljava/lang/Object;") == 0) {
      klass.Assign(GetClassRoot<mirror::Object>(this));
    } else if (strcmp(descriptor, "Ljava/lang/Class;") == 0) {
      klass.Assign(GetClassRoot<mirror::Class>(this));
    } else if (strcmp(descriptor, "Ljava/lang/String;") == 0) {
      klass.Assign(GetClassRoot<mirror::String>(this));
    } else if (strcmp(descriptor, "Ljava/lang/ref/Reference;") == 0) {
      klass.Assign(GetClassRoot<mirror::Reference>(this));
    } else if (strcmp(descriptor, "Ljava/lang/DexCache;") == 0) {
      klass.Assign(GetClassRoot<mirror::DexCache>(this));
    } else if (strcmp(descriptor, "Ldalvik/system/ClassExt;") == 0) {
      klass.Assign(GetClassRoot<mirror::ClassExt>(this));
    }
  }

  // For AOT-compilation of an app, we may use only a public SDK to resolve symbols. If the SDK
  // checks are configured (a non null SdkChecker) and the descriptor is not in the provided
  // public class path then we prevent the definition of the class.
  //
  // NOTE that we only do the checks for the boot classpath APIs. Anything else, like the app
  // classpath is not checked.
  if (class_loader == nullptr &&
      Runtime::Current()->IsAotCompiler() &&
      DenyAccessBasedOnPublicSdk(descriptor)) {
    ObjPtr<mirror::Throwable> pre_allocated =
        Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
    self->SetException(pre_allocated);
    return sdc.Finish(nullptr);
  }

  // This is to prevent the calls to ClassLoad and ClassPrepare which can cause java/user-supplied
  // code to be executed. We put it up here so we can avoid all the allocations associated with
  // creating the class. This can happen with (eg) jit threads.
  if (!self->CanLoadClasses()) {
    // Make sure we don't try to load anything, potentially causing an infinite loop.
    ObjPtr<mirror::Throwable> pre_allocated =
        Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
    self->SetException(pre_allocated);
    return sdc.Finish(nullptr);
  }

  ScopedTrace trace(descriptor);
  if (klass == nullptr) {
    // Allocate a class with the status of not ready.
    // Interface object should get the right size here. Regular class will
    // figure out the right size later and be replaced with one of the right
    // size when the class becomes resolved.
    if (CanAllocClass()) {
      klass.Assign(AllocClass(self, SizeOfClassWithoutEmbeddedTables(dex_file, dex_class_def)));
    } else {
      return sdc.Finish(nullptr);
    }
  }
  if (UNLIKELY(klass == nullptr)) {
    self->AssertPendingOOMException();
    return sdc.Finish(nullptr);
  }
  // Get the real dex file. This will return the input if there aren't any callbacks or they do
  // nothing.
  DexFile const* new_dex_file = nullptr;
  dex::ClassDef const* new_class_def = nullptr;
  // TODO We should ideally figure out some way to move this after we get a lock on the klass so it
  // will only be called once.
  Runtime::Current()->GetRuntimeCallbacks()->ClassPreDefine(descriptor,
                                                            klass,
                                                            class_loader,
                                                            dex_file,
                                                            dex_class_def,
                                                            &new_dex_file,
                                                            &new_class_def);
  // Check to see if an exception happened during runtime callbacks. Return if so.
  if (self->IsExceptionPending()) {
    return sdc.Finish(nullptr);
  }
  ObjPtr<mirror::DexCache> dex_cache = RegisterDexFile(*new_dex_file, class_loader.Get());
  if (dex_cache == nullptr) {
    self->AssertPendingException();
    return sdc.Finish(nullptr);
  }
  klass->SetDexCache(dex_cache);
  SetupClass(*new_dex_file, *new_class_def, klass, class_loader.Get());

  // Mark the string class by setting its access flag.
  if (UNLIKELY(!init_done_)) {
    if (strcmp(descriptor, "Ljava/lang/String;") == 0) {
      klass->SetStringClass();
    }
  }

  ObjectLock<mirror::Class> lock(self, klass);
  klass->SetClinitThreadId(self->GetTid());
  // Make sure we have a valid empty iftable even if there are errors.
  klass->SetIfTable(GetClassRoot<mirror::Object>(this)->GetIfTable());

  // Add the newly loaded class to the loaded classes table.
  ObjPtr<mirror::Class> existing = InsertClass(descriptor, klass.Get(), hash);
  if (existing != nullptr) {
    // We failed to insert because we raced with another thread. Calling EnsureResolved may cause
    // this thread to block.
    return sdc.Finish(EnsureResolved(self, descriptor, existing));
  }

  // Load the fields and other things after we are inserted in the table. This is so that we don't
  // end up allocating unfree-able linear alloc resources and then lose the race condition. The
  // other reason is that the field roots are only visited from the class table. So we need to be
  // inserted before we allocate / fill in these fields.
  LoadClass(self, *new_dex_file, *new_class_def, klass);
  if (self->IsExceptionPending()) {
    VLOG(class_linker) << self->GetException()->Dump();
    // An exception occured during load, set status to erroneous while holding klass' lock in case
    // notification is necessary.
    if (!klass->IsErroneous()) {
      mirror::Class::SetStatus(klass, ClassStatus::kErrorUnresolved, self);
    }
    return sdc.Finish(nullptr);
  }

  // Finish loading (if necessary) by finding parents
  CHECK(!klass->IsLoaded());
  if (!LoadSuperAndInterfaces(klass, *new_dex_file)) {
    // Loading failed.
    if (!klass->IsErroneous()) {
      mirror::Class::SetStatus(klass, ClassStatus::kErrorUnresolved, self);
    }
    return sdc.Finish(nullptr);
  }
  CHECK(klass->IsLoaded());

  // At this point the class is loaded. Publish a ClassLoad event.
  // Note: this may be a temporary class. It is a listener's responsibility to handle this.
  Runtime::Current()->GetRuntimeCallbacks()->ClassLoad(klass);

  // Link the class (if necessary)
  CHECK(!klass->IsResolved());
  // TODO: Use fast jobjects?
  auto interfaces = hs.NewHandle<mirror::ObjectArray<mirror::Class>>(nullptr);

  MutableHandle<mirror::Class> h_new_class = hs.NewHandle<mirror::Class>(nullptr);
  if (!LinkClass(self, descriptor, klass, interfaces, &h_new_class)) {
    // Linking failed.
    if (!klass->IsErroneous()) {
      mirror::Class::SetStatus(klass, ClassStatus::kErrorUnresolved, self);
    }
    return sdc.Finish(nullptr);
  }
  self->AssertNoPendingException();
  CHECK(h_new_class != nullptr) << descriptor;
  CHECK(h_new_class->IsResolved()) << descriptor << " " << h_new_class->GetStatus();

  // Instrumentation may have updated entrypoints for all methods of all
  // classes. However it could not update methods of this class while we
  // were loading it. Now the class is resolved, we can update entrypoints
  // as required by instrumentation.
  if (Runtime::Current()->GetInstrumentation()->EntryExitStubsInstalled()) {
    // We must be in the kRunnable state to prevent instrumentation from
    // suspending all threads to update entrypoints while we are doing it
    // for this class.
    DCHECK_EQ(self->GetState(), ThreadState::kRunnable);
    Runtime::Current()->GetInstrumentation()->InstallStubsForClass(h_new_class.Get());
  }

  /*
   * We send CLASS_PREPARE events to the debugger from here.  The
   * definition of "preparation" is creating the static fields for a
   * class and initializing them to the standard default values, but not
   * executing any code (that comes later, during "initialization").
   *
   * We did the static preparation in LinkClass.
   *
   * The class has been prepared and resolved but possibly not yet verified
   * at this point.
   */
  Runtime::Current()->GetRuntimeCallbacks()->ClassPrepare(klass, h_new_class);

  // Notify native debugger of the new class and its layout.
  jit::Jit::NewTypeLoadedIfUsingJit(h_new_class.Get());

  return sdc.Finish(h_new_class);
}

uint32_t ClassLinker::SizeOfClassWithoutEmbeddedTables(const DexFile& dex_file,
                                                       const dex::ClassDef& dex_class_def) {
  size_t num_ref = 0;
  size_t num_8 = 0;
  size_t num_16 = 0;
  size_t num_32 = 0;
  size_t num_64 = 0;
  ClassAccessor accessor(dex_file, dex_class_def);
  // We allow duplicate definitions of the same field in a class_data_item
  // but ignore the repeated indexes here, b/21868015.
  uint32_t last_field_idx = dex::kDexNoIndex;
  for (const ClassAccessor::Field& field : accessor.GetStaticFields()) {
    uint32_t field_idx = field.GetIndex();
    // Ordering enforced by DexFileVerifier.
    DCHECK(last_field_idx == dex::kDexNoIndex || last_field_idx <= field_idx);
    if (UNLIKELY(field_idx == last_field_idx)) {
      continue;
    }
    last_field_idx = field_idx;
    const dex::FieldId& field_id = dex_file.GetFieldId(field_idx);
    const char* descriptor = dex_file.GetFieldTypeDescriptor(field_id);
    char c = descriptor[0];
    switch (c) {
      case 'L':
      case '[':
        num_ref++;
        break;
      case 'J':
      case 'D':
        num_64++;
        break;
      case 'I':
      case 'F':
        num_32++;
        break;
      case 'S':
      case 'C':
        num_16++;
        break;
      case 'B':
      case 'Z':
        num_8++;
        break;
      default:
        LOG(FATAL) << "Unknown descriptor: " << c;
        UNREACHABLE();
    }
  }
  return mirror::Class::ComputeClassSize(false,
                                         0,
                                         num_8,
                                         num_16,
                                         num_32,
                                         num_64,
                                         num_ref,
                                         image_pointer_size_);
}

void ClassLinker::FixupStaticTrampolines(Thread* self, ObjPtr<mirror::Class> klass) {
  ScopedAssertNoThreadSuspension sants(__FUNCTION__);
  DCHECK(klass->IsVisiblyInitialized()) << klass->PrettyDescriptor();
  size_t num_direct_methods = klass->NumDirectMethods();
  if (num_direct_methods == 0) {
    return;  // No direct methods => no static methods.
  }
  if (UNLIKELY(klass->IsProxyClass())) {
    return;
  }
  PointerSize pointer_size = image_pointer_size_;
  if (std::any_of(klass->GetDirectMethods(pointer_size).begin(),
                  klass->GetDirectMethods(pointer_size).end(),
                  [](const ArtMethod& m) { return m.IsCriticalNative(); })) {
    // Store registered @CriticalNative methods, if any, to JNI entrypoints.
    // Direct methods are a contiguous chunk of memory, so use the ordering of the map.
    ArtMethod* first_method = klass->GetDirectMethod(0u, pointer_size);
    ArtMethod* last_method = klass->GetDirectMethod(num_direct_methods - 1u, pointer_size);
    MutexLock lock(self, critical_native_code_with_clinit_check_lock_);
    auto lb = critical_native_code_with_clinit_check_.lower_bound(first_method);
    while (lb != critical_native_code_with_clinit_check_.end() && lb->first <= last_method) {
      lb->first->SetEntryPointFromJni(lb->second);
      lb = critical_native_code_with_clinit_check_.erase(lb);
    }
  }
  Runtime* runtime = Runtime::Current();
  if (runtime->IsAotCompiler()) {
    // We should not update entrypoints when running the transactional
    // interpreter.
    return;
  }

  instrumentation::Instrumentation* instrumentation = runtime->GetInstrumentation();
  for (size_t method_index = 0; method_index < num_direct_methods; ++method_index) {
    ArtMethod* method = klass->GetDirectMethod(method_index, pointer_size);
    if (method->NeedsClinitCheckBeforeCall()) {
      instrumentation->UpdateMethodsCode(method, instrumentation->GetCodeForInvoke(method));
    }
  }
  // Ignore virtual methods on the iterator.
}

// Does anything needed to make sure that the compiler will not generate a direct invoke to this
// method. Should only be called on non-invokable methods.
inline void EnsureThrowsInvocationError(ClassLinker* class_linker, ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(method != nullptr);
  DCHECK(!method->IsInvokable());
  method->SetEntryPointFromQuickCompiledCodePtrSize(
      class_linker->GetQuickToInterpreterBridgeTrampoline(),
      class_linker->GetImagePointerSize());
}

static void LinkCode(ClassLinker* class_linker,
                     ArtMethod* method,
                     const OatFile::OatClass* oat_class,
                     uint32_t class_def_method_index) REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants(__FUNCTION__);
  Runtime* const runtime = Runtime::Current();
  if (runtime->IsAotCompiler()) {
    // The following code only applies to a non-compiler runtime.
    return;
  }

  // Method shouldn't have already been linked.
  DCHECK_EQ(method->GetEntryPointFromQuickCompiledCode(), nullptr);
  DCHECK(!method->GetDeclaringClass()->IsVisiblyInitialized());  // Actually ClassStatus::Idx.

  if (!method->IsInvokable()) {
    EnsureThrowsInvocationError(class_linker, method);
    return;
  }

  const void* quick_code = nullptr;
  if (oat_class != nullptr) {
    // Every kind of method should at least get an invoke stub from the oat_method.
    // non-abstract methods also get their code pointers.
    const OatFile::OatMethod oat_method = oat_class->GetOatMethod(class_def_method_index);
    quick_code = oat_method.GetQuickCode();
  }
  runtime->GetInstrumentation()->InitializeMethodsCode(method, quick_code);

  if (method->IsNative()) {
    // Set up the dlsym lookup stub. Do not go through `UnregisterNative()`
    // as the extra processing for @CriticalNative is not needed yet.
    method->SetEntryPointFromJni(
        method->IsCriticalNative() ? GetJniDlsymLookupCriticalStub() : GetJniDlsymLookupStub());
  }
}

void ClassLinker::SetupClass(const DexFile& dex_file,
                             const dex::ClassDef& dex_class_def,
                             Handle<mirror::Class> klass,
                             ObjPtr<mirror::ClassLoader> class_loader) {
  CHECK(klass != nullptr);
  CHECK(klass->GetDexCache() != nullptr);
  CHECK_EQ(ClassStatus::kNotReady, klass->GetStatus());
  const char* descriptor = dex_file.GetClassDescriptor(dex_class_def);
  CHECK(descriptor != nullptr);

  klass->SetClass(GetClassRoot<mirror::Class>(this));
  uint32_t access_flags = dex_class_def.GetJavaAccessFlags();
  CHECK_EQ(access_flags & ~kAccJavaFlagsMask, 0U);
  klass->SetAccessFlagsDuringLinking(access_flags);
  klass->SetClassLoader(class_loader);
  DCHECK_EQ(klass->GetPrimitiveType(), Primitive::kPrimNot);
  mirror::Class::SetStatus(klass, ClassStatus::kIdx, nullptr);

  klass->SetDexClassDefIndex(dex_file.GetIndexForClassDef(dex_class_def));
  klass->SetDexTypeIndex(dex_class_def.class_idx_);
}

LengthPrefixedArray<ArtField>* ClassLinker::AllocArtFieldArray(Thread* self,
                                                               LinearAlloc* allocator,
                                                               size_t length) {
  if (length == 0) {
    return nullptr;
  }
  // If the ArtField alignment changes, review all uses of LengthPrefixedArray<ArtField>.
  static_assert(alignof(ArtField) == 4, "ArtField alignment is expected to be 4.");
  size_t storage_size = LengthPrefixedArray<ArtField>::ComputeSize(length);
  void* array_storage = allocator->Alloc(self, storage_size, LinearAllocKind::kArtFieldArray);
  auto* ret = new(array_storage) LengthPrefixedArray<ArtField>(length);
  CHECK(ret != nullptr);
  std::uninitialized_fill_n(&ret->At(0), length, ArtField());
  return ret;
}

LengthPrefixedArray<ArtMethod>* ClassLinker::AllocArtMethodArray(Thread* self,
                                                                 LinearAlloc* allocator,
                                                                 size_t length) {
  if (length == 0) {
    return nullptr;
  }
  const size_t method_alignment = ArtMethod::Alignment(image_pointer_size_);
  const size_t method_size = ArtMethod::Size(image_pointer_size_);
  const size_t storage_size =
      LengthPrefixedArray<ArtMethod>::ComputeSize(length, method_size, method_alignment);
  void* array_storage = allocator->Alloc(self, storage_size, LinearAllocKind::kArtMethodArray);
  auto* ret = new (array_storage) LengthPrefixedArray<ArtMethod>(length);
  CHECK(ret != nullptr);
  for (size_t i = 0; i < length; ++i) {
    new(reinterpret_cast<void*>(&ret->At(i, method_size, method_alignment))) ArtMethod;
  }
  return ret;
}

LinearAlloc* ClassLinker::GetAllocatorForClassLoader(ObjPtr<mirror::ClassLoader> class_loader) {
  if (class_loader == nullptr) {
    return Runtime::Current()->GetLinearAlloc();
  }
  LinearAlloc* allocator = class_loader->GetAllocator();
  DCHECK(allocator != nullptr);
  return allocator;
}

LinearAlloc* ClassLinker::GetOrCreateAllocatorForClassLoader(ObjPtr<mirror::ClassLoader> class_loader) {
  if (class_loader == nullptr) {
    return Runtime::Current()->GetLinearAlloc();
  }
  WriterMutexLock mu(Thread::Current(), *Locks::classlinker_classes_lock_);
  LinearAlloc* allocator = class_loader->GetAllocator();
  if (allocator == nullptr) {
    RegisterClassLoader(class_loader);
    allocator = class_loader->GetAllocator();
    CHECK(allocator != nullptr);
  }
  return allocator;
}

void ClassLinker::LoadClass(Thread* self,
                            const DexFile& dex_file,
                            const dex::ClassDef& dex_class_def,
                            Handle<mirror::Class> klass) {
  ClassAccessor accessor(dex_file,
                         dex_class_def,
                         /* parse_hiddenapi_class_data= */ klass->IsBootStrapClassLoaded());
  if (!accessor.HasClassData()) {
    return;
  }
  Runtime* const runtime = Runtime::Current();
  {
    // Note: We cannot have thread suspension until the field and method arrays are setup or else
    // Class::VisitFieldRoots may miss some fields or methods.
    ScopedAssertNoThreadSuspension nts(__FUNCTION__);
    // Load static fields.
    // We allow duplicate definitions of the same field in a class_data_item
    // but ignore the repeated indexes here, b/21868015.
    LinearAlloc* const allocator = GetAllocatorForClassLoader(klass->GetClassLoader());
    LengthPrefixedArray<ArtField>* sfields = AllocArtFieldArray(self,
                                                                allocator,
                                                                accessor.NumStaticFields());
    LengthPrefixedArray<ArtField>* ifields = AllocArtFieldArray(self,
                                                                allocator,
                                                                accessor.NumInstanceFields());
    size_t num_sfields = 0u;
    size_t num_ifields = 0u;
    uint32_t last_static_field_idx = 0u;
    uint32_t last_instance_field_idx = 0u;

    // Methods
    bool has_oat_class = false;
    const OatFile::OatClass oat_class = (runtime->IsStarted() && !runtime->IsAotCompiler())
        ? OatFile::FindOatClass(dex_file, klass->GetDexClassDefIndex(), &has_oat_class)
        : OatFile::OatClass::Invalid();
    const OatFile::OatClass* oat_class_ptr = has_oat_class ? &oat_class : nullptr;
    klass->SetMethodsPtr(
        AllocArtMethodArray(self, allocator, accessor.NumMethods()),
        accessor.NumDirectMethods(),
        accessor.NumVirtualMethods());
    size_t class_def_method_index = 0;
    uint32_t last_dex_method_index = dex::kDexNoIndex;
    size_t last_class_def_method_index = 0;

    uint16_t hotness_threshold = runtime->GetJITOptions()->GetWarmupThreshold();
    // Use the visitor since the ranged based loops are bit slower from seeking. Seeking to the
    // methods needs to decode all of the fields.
    accessor.VisitFieldsAndMethods([&](
        const ClassAccessor::Field& field) REQUIRES_SHARED(Locks::mutator_lock_) {
          uint32_t field_idx = field.GetIndex();
          DCHECK_GE(field_idx, last_static_field_idx);  // Ordering enforced by DexFileVerifier.
          if (num_sfields == 0 || LIKELY(field_idx > last_static_field_idx)) {
            LoadField(field, klass, &sfields->At(num_sfields));
            ++num_sfields;
            last_static_field_idx = field_idx;
          }
        }, [&](const ClassAccessor::Field& field) REQUIRES_SHARED(Locks::mutator_lock_) {
          uint32_t field_idx = field.GetIndex();
          DCHECK_GE(field_idx, last_instance_field_idx);  // Ordering enforced by DexFileVerifier.
          if (num_ifields == 0 || LIKELY(field_idx > last_instance_field_idx)) {
            LoadField(field, klass, &ifields->At(num_ifields));
            ++num_ifields;
            last_instance_field_idx = field_idx;
          }
        }, [&](const ClassAccessor::Method& method) REQUIRES_SHARED(Locks::mutator_lock_) {
          ArtMethod* art_method = klass->GetDirectMethodUnchecked(class_def_method_index,
              image_pointer_size_);
          LoadMethod(dex_file, method, klass.Get(), art_method);
          LinkCode(this, art_method, oat_class_ptr, class_def_method_index);
          uint32_t it_method_index = method.GetIndex();
          if (last_dex_method_index == it_method_index) {
            // duplicate case
            art_method->SetMethodIndex(last_class_def_method_index);
          } else {
            art_method->SetMethodIndex(class_def_method_index);
            last_dex_method_index = it_method_index;
            last_class_def_method_index = class_def_method_index;
          }
          art_method->ResetCounter(hotness_threshold);
          ++class_def_method_index;
        }, [&](const ClassAccessor::Method& method) REQUIRES_SHARED(Locks::mutator_lock_) {
          ArtMethod* art_method = klass->GetVirtualMethodUnchecked(
              class_def_method_index - accessor.NumDirectMethods(),
              image_pointer_size_);
          art_method->ResetCounter(hotness_threshold);
          LoadMethod(dex_file, method, klass.Get(), art_method);
          LinkCode(this, art_method, oat_class_ptr, class_def_method_index);
          ++class_def_method_index;
        });

    if (UNLIKELY(num_ifields + num_sfields != accessor.NumFields())) {
      LOG(WARNING) << "Duplicate fields in class " << klass->PrettyDescriptor()
          << " (unique static fields: " << num_sfields << "/" << accessor.NumStaticFields()
          << ", unique instance fields: " << num_ifields << "/" << accessor.NumInstanceFields()
          << ")";
      // NOTE: Not shrinking the over-allocated sfields/ifields, just setting size.
      if (sfields != nullptr) {
        sfields->SetSize(num_sfields);
      }
      if (ifields != nullptr) {
        ifields->SetSize(num_ifields);
      }
    }
    // Set the field arrays.
    klass->SetSFieldsPtr(sfields);
    DCHECK_EQ(klass->NumStaticFields(), num_sfields);
    klass->SetIFieldsPtr(ifields);
    DCHECK_EQ(klass->NumInstanceFields(), num_ifields);
  }
  // Ensure that the card is marked so that remembered sets pick up native roots.
  WriteBarrier::ForEveryFieldWrite(klass.Get());
  self->AllowThreadSuspension();
}

void ClassLinker::LoadField(const ClassAccessor::Field& field,
                            Handle<mirror::Class> klass,
                            ArtField* dst) {
  const uint32_t field_idx = field.GetIndex();
  dst->SetDexFieldIndex(field_idx);
  dst->SetDeclaringClass(klass.Get());

  // Get access flags from the DexFile and set hiddenapi runtime access flags.
  dst->SetAccessFlags(field.GetAccessFlags() | hiddenapi::CreateRuntimeFlags(field));
}

void ClassLinker::LoadMethod(const DexFile& dex_file,
                             const ClassAccessor::Method& method,
                             ObjPtr<mirror::Class> klass,
                             ArtMethod* dst) {
  ScopedAssertNoThreadSuspension sants(__FUNCTION__);

  const uint32_t dex_method_idx = method.GetIndex();
  const dex::MethodId& method_id = dex_file.GetMethodId(dex_method_idx);
  uint32_t name_utf16_length;
  const char* method_name = dex_file.StringDataAndUtf16LengthByIdx(method_id.name_idx_,
                                                                   &name_utf16_length);
  std::string_view shorty = dex_file.GetShortyView(dex_file.GetProtoId(method_id.proto_idx_));

  dst->SetDexMethodIndex(dex_method_idx);
  dst->SetDeclaringClass(klass);

  // Get access flags from the DexFile and set hiddenapi runtime access flags.
  uint32_t access_flags = method.GetAccessFlags() | hiddenapi::CreateRuntimeFlags(method);

  auto has_ascii_name = [method_name, name_utf16_length](const char* ascii_name,
                                                         size_t length) ALWAYS_INLINE {
    DCHECK_EQ(strlen(ascii_name), length);
    return length == name_utf16_length &&
           method_name[length] == 0 &&  // Is `method_name` an ASCII string?
           memcmp(ascii_name, method_name, length) == 0;
  };
  if (UNLIKELY(has_ascii_name("finalize", sizeof("finalize") - 1u))) {
    // Set finalizable flag on declaring class.
    if (shorty == "V") {
      // Void return type.
      if (klass->GetClassLoader() != nullptr) {  // All non-boot finalizer methods are flagged.
        klass->SetFinalizable();
      } else {
        std::string_view klass_descriptor =
            dex_file.GetTypeDescriptorView(dex_file.GetTypeId(klass->GetDexTypeIndex()));
        // The Enum class declares a "final" finalize() method to prevent subclasses from
        // introducing a finalizer. We don't want to set the finalizable flag for Enum or its
        // subclasses, so we exclude it here.
        // We also want to avoid setting the flag on Object, where we know that finalize() is
        // empty.
        if (klass_descriptor != "Ljava/lang/Object;" &&
            klass_descriptor != "Ljava/lang/Enum;") {
          klass->SetFinalizable();
        }
      }
    }
  } else if (method_name[0] == '<') {
    // Fix broken access flags for initializers. Bug 11157540.
    bool is_init = has_ascii_name("<init>", sizeof("<init>") - 1u);
    bool is_clinit = has_ascii_name("<clinit>", sizeof("<clinit>") - 1u);
    if (UNLIKELY(!is_init && !is_clinit)) {
      LOG(WARNING) << "Unexpected '<' at start of method name " << method_name;
    } else {
      if (UNLIKELY((access_flags & kAccConstructor) == 0)) {
        LOG(WARNING) << method_name << " didn't have expected constructor access flag in class "
            << klass->PrettyDescriptor() << " in dex file " << dex_file.GetLocation();
        access_flags |= kAccConstructor;
      }
    }
  }

  access_flags |= GetNterpFastPathFlags(shorty, access_flags, kRuntimeISA);

  if (UNLIKELY((access_flags & kAccNative) != 0u)) {
    // Check if the native method is annotated with @FastNative or @CriticalNative.
    const dex::ClassDef& class_def = dex_file.GetClassDef(klass->GetDexClassDefIndex());
    access_flags |=
        annotations::GetNativeMethodAnnotationAccessFlags(dex_file, class_def, dex_method_idx);
    dst->SetAccessFlags(access_flags);
    DCHECK(!dst->IsAbstract());
    DCHECK(!dst->HasCodeItem());
    DCHECK_EQ(method.GetCodeItemOffset(), 0u);
    dst->SetDataPtrSize(nullptr, image_pointer_size_);  // JNI stub/trampoline not linked yet.
  } else if ((access_flags & kAccAbstract) != 0u) {
    dst->SetAccessFlags(access_flags);
    // Must be done after SetAccessFlags since IsAbstract depends on it.
    DCHECK(dst->IsAbstract());
    if (klass->IsInterface()) {
      dst->CalculateAndSetImtIndex();
    }
    DCHECK(!dst->HasCodeItem());
    DCHECK_EQ(method.GetCodeItemOffset(), 0u);
    dst->SetDataPtrSize(nullptr, image_pointer_size_);  // Single implementation not set yet.
  } else {
    const dex::ClassDef& class_def = dex_file.GetClassDef(klass->GetDexClassDefIndex());
    if (annotations::MethodIsNeverCompile(dex_file, class_def, dex_method_idx)) {
      access_flags |= kAccCompileDontBother;
    }
    dst->SetAccessFlags(access_flags);
    DCHECK(!dst->IsAbstract());
    DCHECK(dst->HasCodeItem());
    uint32_t code_item_offset = method.GetCodeItemOffset();
    DCHECK_NE(code_item_offset, 0u);
    if (Runtime::Current()->IsAotCompiler()) {
      dst->SetDataPtrSize(reinterpret_cast32<void*>(code_item_offset), image_pointer_size_);
    } else {
      dst->SetCodeItem(dex_file.GetCodeItem(code_item_offset), dex_file.IsCompactDexFile());
    }
  }

  if (Runtime::Current()->IsZygote() &&
      !Runtime::Current()->GetJITOptions()->GetProfileSaverOptions().GetProfileBootClassPath()) {
    dst->SetMemorySharedMethod();
  }
}

void ClassLinker::AppendToBootClassPath(Thread* self, const DexFile* dex_file) {
  ObjPtr<mirror::DexCache> dex_cache =
      AllocAndInitializeDexCache(self, *dex_file, /* class_loader= */ nullptr);
  CHECK(dex_cache != nullptr) << "Failed to allocate dex cache for " << dex_file->GetLocation();
  AppendToBootClassPath(dex_file, dex_cache);
  WriteBarrierOnClassLoader(self, /*class_loader=*/nullptr, dex_cache);
}

void ClassLinker::AppendToBootClassPath(const DexFile* dex_file,
                                        ObjPtr<mirror::DexCache> dex_cache) {
  CHECK(dex_file != nullptr);
  CHECK(dex_cache != nullptr) << dex_file->GetLocation();
  CHECK_EQ(dex_cache->GetDexFile(), dex_file) << dex_file->GetLocation();
  boot_class_path_.push_back(dex_file);
  WriterMutexLock mu(Thread::Current(), *Locks::dex_lock_);
  RegisterDexFileLocked(*dex_file, dex_cache, /* class_loader= */ nullptr);
}

void ClassLinker::RegisterDexFileLocked(const DexFile& dex_file,
                                        ObjPtr<mirror::DexCache> dex_cache,
                                        ObjPtr<mirror::ClassLoader> class_loader) {
  Thread* const self = Thread::Current();
  Locks::dex_lock_->AssertExclusiveHeld(self);
  CHECK(dex_cache != nullptr) << dex_file.GetLocation();
  CHECK_EQ(dex_cache->GetDexFile(), &dex_file) << dex_file.GetLocation();
  // For app images, the dex cache location may be a suffix of the dex file location since the
  // dex file location is an absolute path.
  const std::string dex_cache_location = dex_cache->GetLocation()->ToModifiedUtf8();
  const size_t dex_cache_length = dex_cache_location.length();
  CHECK_GT(dex_cache_length, 0u) << dex_file.GetLocation();
  std::string dex_file_location = dex_file.GetLocation();
  // The following paths checks don't work on preopt when using boot dex files, where the dex
  // cache location is the one on device, and the dex_file's location is the one on host.
  Runtime* runtime = Runtime::Current();
  if (!(runtime->IsAotCompiler() && class_loader == nullptr && !kIsTargetBuild)) {
    CHECK_GE(dex_file_location.length(), dex_cache_length)
        << dex_cache_location << " " << dex_file.GetLocation();
    const std::string dex_file_suffix = dex_file_location.substr(
        dex_file_location.length() - dex_cache_length,
        dex_cache_length);
    // Example dex_cache location is SettingsProvider.apk and
    // dex file location is /system/priv-app/SettingsProvider/SettingsProvider.apk
    CHECK_EQ(dex_cache_location, dex_file_suffix);
  }

  // Check if we need to initialize OatFile data (.data.bimg.rel.ro and .bss
  // sections) needed for code execution and register the oat code range.
  const OatFile* oat_file =
      (dex_file.GetOatDexFile() != nullptr) ? dex_file.GetOatDexFile()->GetOatFile() : nullptr;
  bool initialize_oat_file_data = (oat_file != nullptr) && oat_file->IsExecutable();
  if (initialize_oat_file_data) {
    for (const auto& entry : dex_caches_) {
      if (!self->IsJWeakCleared(entry.second.weak_root) &&
          entry.first->GetOatDexFile() != nullptr &&
          entry.first->GetOatDexFile()->GetOatFile() == oat_file) {
        initialize_oat_file_data = false;  // Already initialized.
        break;
      }
    }
  }
  if (initialize_oat_file_data) {
    oat_file->InitializeRelocations();
    // Notify the fault handler about the new executable code range if needed.
    size_t exec_offset = oat_file->GetOatHeader().GetExecutableOffset();
    DCHECK_LE(exec_offset, oat_file->Size());
    size_t exec_size = oat_file->Size() - exec_offset;
    if (exec_size != 0u) {
      runtime->AddGeneratedCodeRange(oat_file->Begin() + exec_offset, exec_size);
    }
  }

  // Let hiddenapi assign a domain to the newly registered dex file.
  hiddenapi::InitializeDexFileDomain(dex_file, class_loader);

  jweak dex_cache_jweak = self->GetJniEnv()->GetVm()->AddWeakGlobalRef(self, dex_cache);
  DexCacheData data;
  data.weak_root = dex_cache_jweak;
  data.class_table = ClassTableForClassLoader(class_loader);
  AddNativeDebugInfoForDex(self, &dex_file);
  DCHECK(data.class_table != nullptr);
  // Make sure to hold the dex cache live in the class table. This case happens for the boot class
  // path dex caches without an image.
  data.class_table->InsertStrongRoot(dex_cache);
  // Make sure that the dex cache holds the classloader live.
  dex_cache->SetClassLoader(class_loader);
  if (class_loader != nullptr) {
    // Since we added a strong root to the class table, do the write barrier as required for
    // remembered sets and generational GCs.
    WriteBarrier::ForEveryFieldWrite(class_loader);
  }
  bool inserted = dex_caches_.emplace(&dex_file, std::move(data)).second;
  CHECK(inserted);
}

ObjPtr<mirror::DexCache> ClassLinker::DecodeDexCacheLocked(Thread* self, const DexCacheData* data) {
  return data != nullptr
      ? ObjPtr<mirror::DexCache>::DownCast(self->DecodeJObject(data->weak_root))
      : nullptr;
}

bool ClassLinker::IsSameClassLoader(
    ObjPtr<mirror::DexCache> dex_cache,
    const DexCacheData* data,
    ObjPtr<mirror::ClassLoader> class_loader) {
  CHECK(data != nullptr);
  DCHECK_EQ(FindDexCacheDataLocked(*dex_cache->GetDexFile()), data);
  return data->class_table == ClassTableForClassLoader(class_loader);
}

void ClassLinker::RegisterExistingDexCache(ObjPtr<mirror::DexCache> dex_cache,
                                           ObjPtr<mirror::ClassLoader> class_loader) {
  SCOPED_TRACE << __FUNCTION__ << " " << dex_cache->GetDexFile()->GetLocation();
  Thread* self = Thread::Current();
  StackHandleScope<2> hs(self);
  Handle<mirror::DexCache> h_dex_cache(hs.NewHandle(dex_cache));
  Handle<mirror::ClassLoader> h_class_loader(hs.NewHandle(class_loader));
  const DexFile* dex_file = dex_cache->GetDexFile();
  DCHECK(dex_file != nullptr) << "Attempt to register uninitialized dex_cache object!";
  if (kIsDebugBuild) {
    ReaderMutexLock mu(self, *Locks::dex_lock_);
    const DexCacheData* old_data = FindDexCacheDataLocked(*dex_file);
    ObjPtr<mirror::DexCache> old_dex_cache = DecodeDexCacheLocked(self, old_data);
    DCHECK(old_dex_cache.IsNull()) << "Attempt to manually register a dex cache thats already "
                                   << "been registered on dex file " << dex_file->GetLocation();
  }
  ClassTable* table;
  {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    table = InsertClassTableForClassLoader(h_class_loader.Get());
  }
  // Avoid a deadlock between a garbage collecting thread running a checkpoint,
  // a thread holding the dex lock and blocking on a condition variable regarding
  // weak references access, and a thread blocking on the dex lock.
  gc::ScopedGCCriticalSection gcs(self, gc::kGcCauseClassLinker, gc::kCollectorTypeClassLinker);
  WriterMutexLock mu(self, *Locks::dex_lock_);
  RegisterDexFileLocked(*dex_file, h_dex_cache.Get(), h_class_loader.Get());
  table->InsertStrongRoot(h_dex_cache.Get());
  if (h_class_loader.Get() != nullptr) {
    // Since we added a strong root to the class table, do the write barrier as required for
    // remembered sets and generational GCs.
    WriteBarrier::ForEveryFieldWrite(h_class_loader.Get());
  }
}

static void ThrowDexFileAlreadyRegisteredError(Thread* self, const DexFile& dex_file)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  self->ThrowNewExceptionF("Ljava/lang/InternalError;",
                           "Attempt to register dex file %s with multiple class loaders",
                           dex_file.GetLocation().c_str());
}

void ClassLinker::WriteBarrierOnClassLoaderLocked(ObjPtr<mirror::ClassLoader> class_loader,
                                                  ObjPtr<mirror::Object> root) {
  if (class_loader != nullptr) {
    // Since we added a strong root to the class table, do the write barrier as required for
    // remembered sets and generational GCs.
    WriteBarrier::ForEveryFieldWrite(class_loader);
  } else if (log_new_roots_) {
    new_roots_.push_back(GcRoot<mirror::Object>(root));
  }
}

void ClassLinker::WriteBarrierOnClassLoader(Thread* self,
                                            ObjPtr<mirror::ClassLoader> class_loader,
                                            ObjPtr<mirror::Object> root) {
  if (class_loader != nullptr) {
    // Since we added a strong root to the class table, do the write barrier as required for
    // remembered sets and generational GCs.
    WriteBarrier::ForEveryFieldWrite(class_loader);
  } else {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    if (log_new_roots_) {
      new_roots_.push_back(GcRoot<mirror::Object>(root));
    }
  }
}

ObjPtr<mirror::DexCache> ClassLinker::RegisterDexFile(const DexFile& dex_file,
                                                      ObjPtr<mirror::ClassLoader> class_loader) {
  Thread* self = Thread::Current();
  ObjPtr<mirror::DexCache> old_dex_cache;
  bool registered_with_another_class_loader = false;
  {
    ReaderMutexLock mu(self, *Locks::dex_lock_);
    const DexCacheData* old_data = FindDexCacheDataLocked(dex_file);
    old_dex_cache = DecodeDexCacheLocked(self, old_data);
    if (old_dex_cache != nullptr) {
      if (IsSameClassLoader(old_dex_cache, old_data, class_loader)) {
        return old_dex_cache;
      } else {
        // TODO This is not very clean looking. Should maybe try to make a way to request exceptions
        // be thrown when it's safe to do so to simplify this.
        registered_with_another_class_loader = true;
      }
    }
  }
  // We need to have released the dex_lock_ to allocate safely.
  if (registered_with_another_class_loader) {
    ThrowDexFileAlreadyRegisteredError(self, dex_file);
    return nullptr;
  }
  SCOPED_TRACE << __FUNCTION__ << " " << dex_file.GetLocation();
  LinearAlloc* const linear_alloc = GetOrCreateAllocatorForClassLoader(class_loader);
  DCHECK(linear_alloc != nullptr);
  ClassTable* table;
  {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    table = InsertClassTableForClassLoader(class_loader);
  }
  // Don't alloc while holding the lock, since allocation may need to
  // suspend all threads and another thread may need the dex_lock_ to
  // get to a suspend point.
  StackHandleScope<3> hs(self);
  Handle<mirror::ClassLoader> h_class_loader(hs.NewHandle(class_loader));
  Handle<mirror::DexCache> h_dex_cache(hs.NewHandle(AllocDexCache(self, dex_file)));
  {
    // Avoid a deadlock between a garbage collecting thread running a checkpoint,
    // a thread holding the dex lock and blocking on a condition variable regarding
    // weak references access, and a thread blocking on the dex lock.
    gc::ScopedGCCriticalSection gcs(self, gc::kGcCauseClassLinker, gc::kCollectorTypeClassLinker);
    WriterMutexLock mu(self, *Locks::dex_lock_);
    const DexCacheData* old_data = FindDexCacheDataLocked(dex_file);
    old_dex_cache = DecodeDexCacheLocked(self, old_data);
    if (old_dex_cache == nullptr && h_dex_cache != nullptr) {
      // Do Initialize while holding dex lock to make sure two threads don't call it
      // at the same time with the same dex cache. Since the .bss is shared this can cause failing
      // DCHECK that the arrays are null.
      h_dex_cache->Initialize(&dex_file, h_class_loader.Get());
      RegisterDexFileLocked(dex_file, h_dex_cache.Get(), h_class_loader.Get());
    }
    if (old_dex_cache != nullptr) {
      // Another thread managed to initialize the dex cache faster, so use that DexCache.
      // If this thread encountered OOME, ignore it.
      DCHECK_EQ(h_dex_cache == nullptr, self->IsExceptionPending());
      self->ClearException();
      // We cannot call EnsureSameClassLoader() or allocate an exception while holding the
      // dex_lock_.
      if (IsSameClassLoader(old_dex_cache, old_data, h_class_loader.Get())) {
        return old_dex_cache;
      } else {
        registered_with_another_class_loader = true;
      }
    }
  }
  if (registered_with_another_class_loader) {
    ThrowDexFileAlreadyRegisteredError(self, dex_file);
    return nullptr;
  }
  if (h_dex_cache == nullptr) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  if (table->InsertStrongRoot(h_dex_cache.Get())) {
    WriteBarrierOnClassLoader(self, h_class_loader.Get(), h_dex_cache.Get());
  } else {
    // Write-barrier not required if strong-root isn't inserted.
  }
  VLOG(class_linker) << "Registered dex file " << dex_file.GetLocation();
  PaletteNotifyDexFileLoaded(dex_file.GetLocation().c_str());
  return h_dex_cache.Get();
}

bool ClassLinker::IsDexFileRegistered(Thread* self, const DexFile& dex_file) {
  ReaderMutexLock mu(self, *Locks::dex_lock_);
  return DecodeDexCacheLocked(self, FindDexCacheDataLocked(dex_file)) != nullptr;
}

ObjPtr<mirror::DexCache> ClassLinker::FindDexCache(Thread* self, const DexFile& dex_file) {
  ReaderMutexLock mu(self, *Locks::dex_lock_);
  const DexCacheData* dex_cache_data = FindDexCacheDataLocked(dex_file);
  ObjPtr<mirror::DexCache> dex_cache = DecodeDexCacheLocked(self, dex_cache_data);
  if (dex_cache != nullptr) {
    return dex_cache;
  }
  // Failure, dump diagnostic and abort.
  for (const auto& entry : dex_caches_) {
    const DexCacheData& data = entry.second;
    if (DecodeDexCacheLocked(self, &data) != nullptr) {
      LOG(FATAL_WITHOUT_ABORT) << "Registered dex file " << entry.first->GetLocation();
    }
  }
  LOG(FATAL) << "Failed to find DexCache for DexFile " << dex_file.GetLocation()
             << " " << &dex_file;
  UNREACHABLE();
}

ObjPtr<mirror::DexCache> ClassLinker::FindDexCache(Thread* self, const OatDexFile& oat_dex_file) {
  ReaderMutexLock mu(self, *Locks::dex_lock_);
  const DexCacheData* dex_cache_data = FindDexCacheDataLocked(oat_dex_file);
  ObjPtr<mirror::DexCache> dex_cache = DecodeDexCacheLocked(self, dex_cache_data);
  if (dex_cache != nullptr) {
    return dex_cache;
  }
  // Failure, dump diagnostic and abort.
  if (dex_cache_data == nullptr) {
    LOG(FATAL_WITHOUT_ABORT) << "NULL dex_cache_data";
  } else {
    LOG(FATAL_WITHOUT_ABORT)
        << "dex_cache_data=" << dex_cache_data
        << " weak_root=" << dex_cache_data->weak_root
        << " decoded_weak_root=" << self->DecodeJObject(dex_cache_data->weak_root);
  }
  for (const auto& entry : dex_caches_) {
    const DexCacheData& data = entry.second;
    if (DecodeDexCacheLocked(self, &data) != nullptr) {
      const OatDexFile* other_oat_dex_file = entry.first->GetOatDexFile();
      const OatFile* oat_file =
          (other_oat_dex_file == nullptr) ? nullptr : other_oat_dex_file->GetOatFile();
      LOG(FATAL_WITHOUT_ABORT)
          << "Registered dex file " << entry.first->GetLocation()
          << " oat_dex_file=" << other_oat_dex_file
          << " oat_file=" << oat_file
          << " oat_location=" << (oat_file == nullptr ? "null" : oat_file->GetLocation())
          << " dex_file=" << &entry.first
          << " weak_root=" << data.weak_root
          << " decoded_weak_root=" << self->DecodeJObject(data.weak_root)
          << " dex_cache_data=" << &data;
    }
  }
  LOG(FATAL) << "Failed to find DexCache for OatDexFile "
             << oat_dex_file.GetDexFileLocation()
             << " oat_dex_file=" << &oat_dex_file
             << " oat_file=" << oat_dex_file.GetOatFile()
             << " oat_location=" << oat_dex_file.GetOatFile()->GetLocation();
  UNREACHABLE();
}

ClassTable* ClassLinker::FindClassTable(Thread* self, ObjPtr<mirror::DexCache> dex_cache) {
  const DexFile* dex_file = dex_cache->GetDexFile();
  DCHECK(dex_file != nullptr);
  ReaderMutexLock mu(self, *Locks::dex_lock_);
  auto it = dex_caches_.find(dex_file);
  if (it != dex_caches_.end()) {
    const DexCacheData& data = it->second;
    ObjPtr<mirror::DexCache> registered_dex_cache = DecodeDexCacheLocked(self, &data);
    if (registered_dex_cache != nullptr) {
      CHECK_EQ(registered_dex_cache, dex_cache) << dex_file->GetLocation();
      return data.class_table;
    }
  }
  return nullptr;
}

const ClassLinker::DexCacheData* ClassLinker::FindDexCacheDataLocked(
    const OatDexFile& oat_dex_file) {
  auto it = std::find_if(dex_caches_.begin(), dex_caches_.end(), [&](const auto& entry) {
    return entry.first->GetOatDexFile() == &oat_dex_file;
  });
  return it != dex_caches_.end() ? &it->second : nullptr;
}

const ClassLinker::DexCacheData* ClassLinker::FindDexCacheDataLocked(const DexFile& dex_file) {
  auto it = dex_caches_.find(&dex_file);
  return it != dex_caches_.end() ? &it->second : nullptr;
}

void ClassLinker::CreatePrimitiveClass(Thread* self,
                                       Primitive::Type type,
                                       ClassRoot primitive_root) {
  ObjPtr<mirror::Class> primitive_class =
      AllocClass(self, mirror::Class::PrimitiveClassSize(image_pointer_size_));
  CHECK(primitive_class != nullptr) << "OOM for primitive class " << type;
  // Do not hold lock on the primitive class object, the initialization of
  // primitive classes is done while the process is still single threaded.
  primitive_class->SetAccessFlagsDuringLinking(kAccPublic | kAccFinal | kAccAbstract);
  primitive_class->SetPrimitiveType(type);
  primitive_class->SetIfTable(GetClassRoot<mirror::Object>(this)->GetIfTable());
  DCHECK_EQ(primitive_class->NumMethods(), 0u);
  // Primitive classes are initialized during single threaded startup, so visibly initialized.
  primitive_class->SetStatusForPrimitiveOrArray(ClassStatus::kVisiblyInitialized);
  const char* descriptor = Primitive::Descriptor(type);
  ObjPtr<mirror::Class> existing = InsertClass(descriptor,
                                               primitive_class,
                                               ComputeModifiedUtf8Hash(descriptor));
  CHECK(existing == nullptr) << "InitPrimitiveClass(" << type << ") failed";
  SetClassRoot(primitive_root, primitive_class);
}

inline ObjPtr<mirror::IfTable> ClassLinker::GetArrayIfTable() {
  return GetClassRoot<mirror::ObjectArray<mirror::Object>>(this)->GetIfTable();
}

// Create an array class (i.e. the class object for the array, not the
// array itself).  "descriptor" looks like "[C" or "[[[[B" or
// "[Ljava/lang/String;".
//
// If "descriptor" refers to an array of primitives, look up the
// primitive type's internally-generated class object.
//
// "class_loader" is the class loader of the class that's referring to
// us.  It's used to ensure that we're looking for the element type in
// the right context.  It does NOT become the class loader for the
// array class; that always comes from the base element class.
//
// Returns null with an exception raised on failure.
ObjPtr<mirror::Class> ClassLinker::CreateArrayClass(Thread* self,
                                                    const char* descriptor,
                                                    size_t hash,
                                                    Handle<mirror::ClassLoader> class_loader) {
  // Identify the underlying component type
  CHECK_EQ('[', descriptor[0]);
  StackHandleScope<2> hs(self);

  // This is to prevent the calls to ClassLoad and ClassPrepare which can cause java/user-supplied
  // code to be executed. We put it up here so we can avoid all the allocations associated with
  // creating the class. This can happen with (eg) jit threads.
  if (!self->CanLoadClasses()) {
    // Make sure we don't try to load anything, potentially causing an infinite loop.
    ObjPtr<mirror::Throwable> pre_allocated =
        Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
    self->SetException(pre_allocated);
    return nullptr;
  }

  MutableHandle<mirror::Class> component_type(hs.NewHandle(FindClass(self, descriptor + 1,
                                                                     class_loader)));
  if (component_type == nullptr) {
    DCHECK(self->IsExceptionPending());
    // We need to accept erroneous classes as component types. Under AOT, we
    // don't accept them as we cannot encode the erroneous class in an image.
    const size_t component_hash = ComputeModifiedUtf8Hash(descriptor + 1);
    component_type.Assign(LookupClass(self, descriptor + 1, component_hash, class_loader.Get()));
    if (component_type == nullptr || Runtime::Current()->IsAotCompiler()) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    } else {
      self->ClearException();
    }
  }
  if (UNLIKELY(component_type->IsPrimitiveVoid())) {
    ThrowNoClassDefFoundError("Attempt to create array of void primitive type");
    return nullptr;
  }
  // See if the component type is already loaded.  Array classes are
  // always associated with the class loader of their underlying
  // element type -- an array of Strings goes with the loader for
  // java/lang/String -- so we need to look for it there.  (The
  // caller should have checked for the existence of the class
  // before calling here, but they did so with *their* class loader,
  // not the component type's loader.)
  //
  // If we find it, the caller adds "loader" to the class' initiating
  // loader list, which should prevent us from going through this again.
  //
  // This call is unnecessary if "loader" and "component_type->GetClassLoader()"
  // are the same, because our caller (FindClass) just did the
  // lookup.  (Even if we get this wrong we still have correct behavior,
  // because we effectively do this lookup again when we add the new
  // class to the hash table --- necessary because of possible races with
  // other threads.)
  if (class_loader.Get() != component_type->GetClassLoader()) {
    ObjPtr<mirror::Class> new_class =
        LookupClass(self, descriptor, hash, component_type->GetClassLoader());
    if (new_class != nullptr) {
      return new_class;
    }
  }
  // Core array classes, i.e. Object[], Class[], String[] and primitive
  // arrays, have special initialization and they should be found above.
  DCHECK_IMPLIES(component_type->IsObjectClass(),
                 // Guard from false positives for errors before setting superclass.
                 component_type->IsErroneousUnresolved());
  DCHECK(!component_type->IsStringClass());
  DCHECK(!component_type->IsClassClass());
  DCHECK(!component_type->IsPrimitive());

  // Fill out the fields in the Class.
  //
  // It is possible to execute some methods against arrays, because
  // all arrays are subclasses of java_lang_Object_, so we need to set
  // up a vtable.  We can just point at the one in java_lang_Object_.
  //
  // Array classes are simple enough that we don't need to do a full
  // link step.
  size_t array_class_size = mirror::Array::ClassSize(image_pointer_size_);
  auto visitor = [this, array_class_size, component_type](ObjPtr<mirror::Object> obj,
                                                          size_t usable_size)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ScopedAssertNoNewTransactionRecords sanntr("CreateArrayClass");
    mirror::Class::InitializeClassVisitor init_class(array_class_size);
    init_class(obj, usable_size);
    ObjPtr<mirror::Class> klass = ObjPtr<mirror::Class>::DownCast(obj);
    klass->SetComponentType(component_type.Get());
    // Do not hold lock for initialization, the fence issued after the visitor
    // returns ensures memory visibility together with the implicit consume
    // semantics (for all supported architectures) for any thread that loads
    // the array class reference from any memory locations afterwards.
    FinishArrayClassSetup(klass);
  };
  auto new_class = hs.NewHandle<mirror::Class>(
      AllocClass(self, GetClassRoot<mirror::Class>(this), array_class_size, visitor));
  if (new_class == nullptr) {
    self->AssertPendingOOMException();
    return nullptr;
  }

  ObjPtr<mirror::Class> existing = InsertClass(descriptor, new_class.Get(), hash);
  if (existing == nullptr) {
    // We postpone ClassLoad and ClassPrepare events to this point in time to avoid
    // duplicate events in case of races. Array classes don't really follow dedicated
    // load and prepare, anyways.
    Runtime::Current()->GetRuntimeCallbacks()->ClassLoad(new_class);
    Runtime::Current()->GetRuntimeCallbacks()->ClassPrepare(new_class, new_class);

    jit::Jit::NewTypeLoadedIfUsingJit(new_class.Get());
    return new_class.Get();
  }
  // Another thread must have loaded the class after we
  // started but before we finished.  Abandon what we've
  // done.
  //
  // (Yes, this happens.)

  return existing;
}

ObjPtr<mirror::Class> ClassLinker::LookupPrimitiveClass(char type) {
  ClassRoot class_root;
  switch (type) {
    case 'B': class_root = ClassRoot::kPrimitiveByte; break;
    case 'C': class_root = ClassRoot::kPrimitiveChar; break;
    case 'D': class_root = ClassRoot::kPrimitiveDouble; break;
    case 'F': class_root = ClassRoot::kPrimitiveFloat; break;
    case 'I': class_root = ClassRoot::kPrimitiveInt; break;
    case 'J': class_root = ClassRoot::kPrimitiveLong; break;
    case 'S': class_root = ClassRoot::kPrimitiveShort; break;
    case 'Z': class_root = ClassRoot::kPrimitiveBoolean; break;
    case 'V': class_root = ClassRoot::kPrimitiveVoid; break;
    default:
      return nullptr;
  }
  return GetClassRoot(class_root, this);
}

ObjPtr<mirror::Class> ClassLinker::FindPrimitiveClass(char type) {
  ObjPtr<mirror::Class> result = LookupPrimitiveClass(type);
  if (UNLIKELY(result == nullptr)) {
    std::string printable_type(PrintableChar(type));
    ThrowNoClassDefFoundError("Not a primitive type: %s", printable_type.c_str());
  }
  return result;
}

ObjPtr<mirror::Class> ClassLinker::InsertClass(const char* descriptor,
                                               ObjPtr<mirror::Class> klass,
                                               size_t hash) {
  DCHECK(Thread::Current()->CanLoadClasses());
  if (VLOG_IS_ON(class_linker)) {
    ObjPtr<mirror::DexCache> dex_cache = klass->GetDexCache();
    std::string source;
    if (dex_cache != nullptr) {
      source += " from ";
      source += dex_cache->GetLocation()->ToModifiedUtf8();
    }
    LOG(INFO) << "Loaded class " << descriptor << source;
  }
  {
    WriterMutexLock mu(Thread::Current(), *Locks::classlinker_classes_lock_);
    const ObjPtr<mirror::ClassLoader> class_loader = klass->GetClassLoader();
    ClassTable* const class_table = InsertClassTableForClassLoader(class_loader);
    ObjPtr<mirror::Class> existing = class_table->Lookup(descriptor, hash);
    if (existing != nullptr) {
      return existing;
    }
    VerifyObject(klass);
    class_table->InsertWithHash(klass, hash);
    WriteBarrierOnClassLoaderLocked(class_loader, klass);
  }
  if (kIsDebugBuild) {
    // Test that copied methods correctly can find their holder.
    for (ArtMethod& method : klass->GetCopiedMethods(image_pointer_size_)) {
      CHECK_EQ(GetHoldingClassOfCopiedMethod(&method), klass);
    }
  }
  return nullptr;
}

void ClassLinker::WriteBarrierForBootOatFileBssRoots(const OatFile* oat_file) {
  WriterMutexLock mu(Thread::Current(), *Locks::classlinker_classes_lock_);
  DCHECK(!oat_file->GetBssGcRoots().empty()) << oat_file->GetLocation();
  if (log_new_roots_ && !ContainsElement(new_bss_roots_boot_oat_files_, oat_file)) {
    new_bss_roots_boot_oat_files_.push_back(oat_file);
  }
}

// TODO This should really be in mirror::Class.
void ClassLinker::UpdateClassMethods(ObjPtr<mirror::Class> klass,
                                     LengthPrefixedArray<ArtMethod>* new_methods) {
  klass->SetMethodsPtrUnchecked(new_methods,
                                klass->NumDirectMethods(),
                                klass->NumDeclaredVirtualMethods());
  // Need to mark the card so that the remembered sets and mod union tables get updated.
  WriteBarrier::ForEveryFieldWrite(klass);
}

ObjPtr<mirror::Class> ClassLinker::LookupClass(Thread* self,
                                               const char* descriptor,
                                               ObjPtr<mirror::ClassLoader> class_loader) {
  return LookupClass(self, descriptor, ComputeModifiedUtf8Hash(descriptor), class_loader);
}

ObjPtr<mirror::Class> ClassLinker::LookupClass(Thread* self,
                                               const char* descriptor,
                                               size_t hash,
                                               ObjPtr<mirror::ClassLoader> class_loader) {
  ReaderMutexLock mu(self, *Locks::classlinker_classes_lock_);
  ClassTable* const class_table = ClassTableForClassLoader(class_loader);
  if (class_table != nullptr) {
    ObjPtr<mirror::Class> result = class_table->Lookup(descriptor, hash);
    if (result != nullptr) {
      return result;
    }
  }
  return nullptr;
}

class MoveClassTableToPreZygoteVisitor : public ClassLoaderVisitor {
 public:
  MoveClassTableToPreZygoteVisitor() {}

  void Visit(ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES(Locks::classlinker_classes_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_) override {
    ClassTable* const class_table = class_loader->GetClassTable();
    if (class_table != nullptr) {
      class_table->FreezeSnapshot();
    }
  }
};

void ClassLinker::MoveClassTableToPreZygote() {
  WriterMutexLock mu(Thread::Current(), *Locks::classlinker_classes_lock_);
  boot_class_table_->FreezeSnapshot();
  MoveClassTableToPreZygoteVisitor visitor;
  VisitClassLoaders(&visitor);
}

// Look up classes by hash and descriptor and put all matching ones in the result array.
class LookupClassesVisitor : public ClassLoaderVisitor {
 public:
  LookupClassesVisitor(const char* descriptor,
                       size_t hash,
                       std::vector<ObjPtr<mirror::Class>>* result)
     : descriptor_(descriptor),
       hash_(hash),
       result_(result) {}

  void Visit(ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::classlinker_classes_lock_, Locks::mutator_lock_) override {
    ClassTable* const class_table = class_loader->GetClassTable();
    ObjPtr<mirror::Class> klass = class_table->Lookup(descriptor_, hash_);
    // Add `klass` only if `class_loader` is its defining (not just initiating) class loader.
    if (klass != nullptr && klass->GetClassLoader() == class_loader) {
      result_->push_back(klass);
    }
  }

 private:
  const char* const descriptor_;
  const size_t hash_;
  std::vector<ObjPtr<mirror::Class>>* const result_;
};

void ClassLinker::LookupClasses(const char* descriptor,
                                std::vector<ObjPtr<mirror::Class>>& result) {
  result.clear();
  Thread* const self = Thread::Current();
  ReaderMutexLock mu(self, *Locks::classlinker_classes_lock_);
  const size_t hash = ComputeModifiedUtf8Hash(descriptor);
  ObjPtr<mirror::Class> klass = boot_class_table_->Lookup(descriptor, hash);
  if (klass != nullptr) {
    DCHECK(klass->GetClassLoader() == nullptr);
    result.push_back(klass);
  }
  LookupClassesVisitor visitor(descriptor, hash, &result);
  VisitClassLoaders(&visitor);
}

bool ClassLinker::AttemptSupertypeVerification(Thread* self,
                                               verifier::VerifierDeps* verifier_deps,
                                               Handle<mirror::Class> klass,
                                               Handle<mirror::Class> supertype) {
  DCHECK(self != nullptr);
  DCHECK(klass != nullptr);
  DCHECK(supertype != nullptr);

  if (!supertype->IsVerified() && !supertype->IsErroneous()) {
    VerifyClass(self, verifier_deps, supertype);
  }

  if (supertype->IsVerified()
      || supertype->ShouldVerifyAtRuntime()
      || supertype->IsVerifiedNeedsAccessChecks()) {
    // The supertype is either verified, or we soft failed at AOT time.
    DCHECK(supertype->IsVerified() || Runtime::Current()->IsAotCompiler());
    return true;
  }
  // If we got this far then we have a hard failure.
  std::string error_msg =
      StringPrintf("Rejecting class %s that attempts to sub-type erroneous class %s",
                   klass->PrettyDescriptor().c_str(),
                   supertype->PrettyDescriptor().c_str());
  LOG(WARNING) << error_msg  << " in " << klass->GetDexCache()->GetLocation()->ToModifiedUtf8();
  StackHandleScope<1> hs(self);
  Handle<mirror::Throwable> cause(hs.NewHandle(self->GetException()));
  if (cause != nullptr) {
    // Set during VerifyClass call (if at all).
    self->ClearException();
  }
  // Change into a verify error.
  ThrowVerifyError(klass.Get(), "%s", error_msg.c_str());
  if (cause != nullptr) {
    self->GetException()->SetCause(cause.Get());
  }
  ClassReference ref(klass->GetDexCache()->GetDexFile(), klass->GetDexClassDefIndex());
  if (Runtime::Current()->IsAotCompiler()) {
    Runtime::Current()->GetCompilerCallbacks()->ClassRejected(ref);
  }
  // Need to grab the lock to change status.
  ObjectLock<mirror::Class> super_lock(self, klass);
  mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
  return false;
}

verifier::FailureKind ClassLinker::VerifyClass(Thread* self,
                                               verifier::VerifierDeps* verifier_deps,
                                               Handle<mirror::Class> klass,
                                               verifier::HardFailLogMode log_level) {
  {
    // TODO: assert that the monitor on the Class is held
    ObjectLock<mirror::Class> lock(self, klass);

    // Is somebody verifying this now?
    ClassStatus old_status = klass->GetStatus();
    while (old_status == ClassStatus::kVerifying) {
      lock.WaitIgnoringInterrupts();
      // WaitIgnoringInterrupts can still receive an interrupt and return early, in this
      // case we may see the same status again. b/62912904. This is why the check is
      // greater or equal.
      CHECK(klass->IsErroneous() || (klass->GetStatus() >= old_status))
          << "Class '" << klass->PrettyClass()
          << "' performed an illegal verification state transition from " << old_status
          << " to " << klass->GetStatus();
      old_status = klass->GetStatus();
    }

    // The class might already be erroneous, for example at compile time if we attempted to verify
    // this class as a parent to another.
    if (klass->IsErroneous()) {
      ThrowEarlierClassFailure(klass.Get());
      return verifier::FailureKind::kHardFailure;
    }

    // Don't attempt to re-verify if already verified.
    if (klass->IsVerified()) {
      if (verifier_deps != nullptr &&
          verifier_deps->ContainsDexFile(klass->GetDexFile()) &&
          !verifier_deps->HasRecordedVerifiedStatus(klass->GetDexFile(), *klass->GetClassDef()) &&
          !Runtime::Current()->IsAotCompiler()) {
        // If the klass is verified, but `verifier_deps` did not record it, this
        // means we are running background verification of a secondary dex file.
        // Re-run the verifier to populate `verifier_deps`.
        // No need to run the verification when running on the AOT Compiler, as
        // the driver handles those multithreaded cases already.
        std::string error_msg;
        verifier::FailureKind failure =
            PerformClassVerification(self, verifier_deps, klass, log_level, &error_msg);
        // We could have soft failures, so just check that we don't have a hard
        // failure.
        DCHECK_NE(failure, verifier::FailureKind::kHardFailure) << error_msg;
      }
      return verifier::FailureKind::kNoFailure;
    }

    if (klass->IsVerifiedNeedsAccessChecks()) {
      if (!Runtime::Current()->IsAotCompiler()) {
        // Mark the class as having a verification attempt to avoid re-running
        // the verifier.
        mirror::Class::SetStatus(klass, ClassStatus::kVerified, self);
      }
      return verifier::FailureKind::kAccessChecksFailure;
    }

    // For AOT, don't attempt to re-verify if we have already found we should
    // verify at runtime.
    if (klass->ShouldVerifyAtRuntime()) {
      CHECK(Runtime::Current()->IsAotCompiler());
      return verifier::FailureKind::kSoftFailure;
    }

    DCHECK_EQ(klass->GetStatus(), ClassStatus::kResolved);
    mirror::Class::SetStatus(klass, ClassStatus::kVerifying, self);

    // Skip verification if disabled.
    if (!Runtime::Current()->IsVerificationEnabled()) {
      mirror::Class::SetStatus(klass, ClassStatus::kVerified, self);
      UpdateClassAfterVerification(klass, image_pointer_size_, verifier::FailureKind::kNoFailure);
      return verifier::FailureKind::kNoFailure;
    }
  }

  VLOG(class_linker) << "Beginning verification for class: "
                     << klass->PrettyDescriptor()
                     << " in " << klass->GetDexCache()->GetLocation()->ToModifiedUtf8();

  // Verify super class.
  StackHandleScope<2> hs(self);
  MutableHandle<mirror::Class> supertype(hs.NewHandle(klass->GetSuperClass()));
  // If we have a superclass and we get a hard verification failure we can return immediately.
  if (supertype != nullptr &&
      !AttemptSupertypeVerification(self, verifier_deps, klass, supertype)) {
    CHECK(self->IsExceptionPending()) << "Verification error should be pending.";
    return verifier::FailureKind::kHardFailure;
  }

  // Verify all default super-interfaces.
  //
  // (1) Don't bother if the superclass has already had a soft verification failure.
  //
  // (2) Interfaces shouldn't bother to do this recursive verification because they cannot cause
  //     recursive initialization by themselves. This is because when an interface is initialized
  //     directly it must not initialize its superinterfaces. We are allowed to verify regardless
  //     but choose not to for an optimization. If the interfaces is being verified due to a class
  //     initialization (which would need all the default interfaces to be verified) the class code
  //     will trigger the recursive verification anyway.
  if ((supertype == nullptr || supertype->IsVerified())  // See (1)
      && !klass->IsInterface()) {                              // See (2)
    int32_t iftable_count = klass->GetIfTableCount();
    MutableHandle<mirror::Class> iface(hs.NewHandle<mirror::Class>(nullptr));
    // Loop through all interfaces this class has defined. It doesn't matter the order.
    for (int32_t i = 0; i < iftable_count; i++) {
      iface.Assign(klass->GetIfTable()->GetInterface(i));
      DCHECK(iface != nullptr);
      // We only care if we have default interfaces and can skip if we are already verified...
      if (LIKELY(!iface->HasDefaultMethods() || iface->IsVerified())) {
        continue;
      } else if (UNLIKELY(!AttemptSupertypeVerification(self, verifier_deps, klass, iface))) {
        // We had a hard failure while verifying this interface. Just return immediately.
        CHECK(self->IsExceptionPending()) << "Verification error should be pending.";
        return verifier::FailureKind::kHardFailure;
      } else if (UNLIKELY(!iface->IsVerified())) {
        // We softly failed to verify the iface. Stop checking and clean up.
        // Put the iface into the supertype handle so we know what caused us to fail.
        supertype.Assign(iface.Get());
        break;
      }
    }
  }

  // At this point if verification failed, then supertype is the "first" supertype that failed
  // verification (without a specific order). If verification succeeded, then supertype is either
  // null or the original superclass of klass and is verified.
  DCHECK(supertype == nullptr ||
         supertype.Get() == klass->GetSuperClass() ||
         !supertype->IsVerified());

  // Try to use verification information from the oat file, otherwise do runtime verification.
  const DexFile& dex_file = *klass->GetDexCache()->GetDexFile();
  ClassStatus oat_file_class_status(ClassStatus::kNotReady);
  bool preverified = VerifyClassUsingOatFile(self, dex_file, klass, oat_file_class_status);

  VLOG(class_linker) << "Class preverified status for class "
                     << klass->PrettyDescriptor()
                     << " in " << klass->GetDexCache()->GetLocation()->ToModifiedUtf8()
                     << ": "
                     << preverified
                     << "( " << oat_file_class_status << ")";

  // If the oat file says the class had an error, re-run the verifier. That way we will either:
  // 1) Be successful at runtime, or
  // 2) Get a precise error message.
  DCHECK_IMPLIES(mirror::Class::IsErroneous(oat_file_class_status), !preverified);

  std::string error_msg;
  verifier::FailureKind verifier_failure = verifier::FailureKind::kNoFailure;
  if (!preverified) {
    verifier_failure = PerformClassVerification(self, verifier_deps, klass, log_level, &error_msg);
  } else if (oat_file_class_status == ClassStatus::kVerifiedNeedsAccessChecks) {
    verifier_failure = verifier::FailureKind::kAccessChecksFailure;
  }

  // Verification is done, grab the lock again.
  ObjectLock<mirror::Class> lock(self, klass);
  self->AssertNoPendingException();

  if (verifier_failure == verifier::FailureKind::kHardFailure) {
    VLOG(verifier) << "Verification failed on class " << klass->PrettyDescriptor()
                  << " in " << klass->GetDexCache()->GetLocation()->ToModifiedUtf8()
                  << " because: " << error_msg;
    ThrowVerifyError(klass.Get(), "%s", error_msg.c_str());
    mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
    return verifier_failure;
  }

  // Make sure all classes referenced by catch blocks are resolved.
  ResolveClassExceptionHandlerTypes(klass);

  if (Runtime::Current()->IsAotCompiler()) {
    if (supertype != nullptr && supertype->ShouldVerifyAtRuntime()) {
      // Regardless of our own verification result, we need to verify the class
      // at runtime if the super class is not verified. This is required in case
      // we generate an app/boot image.
      mirror::Class::SetStatus(klass, ClassStatus::kRetryVerificationAtRuntime, self);
    } else if (verifier_failure == verifier::FailureKind::kNoFailure) {
      mirror::Class::SetStatus(klass, ClassStatus::kVerified, self);
    } else if (verifier_failure == verifier::FailureKind::kSoftFailure ||
               verifier_failure == verifier::FailureKind::kTypeChecksFailure) {
      mirror::Class::SetStatus(klass, ClassStatus::kRetryVerificationAtRuntime, self);
    } else {
      mirror::Class::SetStatus(klass, ClassStatus::kVerifiedNeedsAccessChecks, self);
    }
    // Notify the compiler about the verification status, in case the class
    // was verified implicitly (eg super class of a compiled class). When the
    // compiler unloads dex file after compilation, we still want to keep
    // verification states.
    Runtime::Current()->GetCompilerCallbacks()->UpdateClassState(
        ClassReference(&klass->GetDexFile(), klass->GetDexClassDefIndex()), klass->GetStatus());
  } else {
    mirror::Class::SetStatus(klass, ClassStatus::kVerified, self);
  }

  UpdateClassAfterVerification(klass, image_pointer_size_, verifier_failure);
  return verifier_failure;
}

verifier::FailureKind ClassLinker::PerformClassVerification(Thread* self,
                                                            verifier::VerifierDeps* verifier_deps,
                                                            Handle<mirror::Class> klass,
                                                            verifier::HardFailLogMode log_level,
                                                            std::string* error_msg) {
  Runtime* const runtime = Runtime::Current();
  StackHandleScope<2> hs(self);
  Handle<mirror::DexCache> dex_cache(hs.NewHandle(klass->GetDexCache()));
  Handle<mirror::ClassLoader> class_loader(hs.NewHandle(klass->GetClassLoader()));
  return verifier::ClassVerifier::VerifyClass(self,
                                              verifier_deps,
                                              dex_cache->GetDexFile(),
                                              klass,
                                              dex_cache,
                                              class_loader,
                                              *klass->GetClassDef(),
                                              runtime->GetCompilerCallbacks(),
                                              log_level,
                                              Runtime::Current()->GetTargetSdkVersion(),
                                              error_msg);
}

bool ClassLinker::VerifyClassUsingOatFile(Thread* self,
                                          const DexFile& dex_file,
                                          Handle<mirror::Class> klass,
                                          ClassStatus& oat_file_class_status) {
  // If we're compiling, we can only verify the class using the oat file if
  // we are not compiling the image or if the class we're verifying is not part of
  // the compilation unit (app - dependencies). We will let the compiler callback
  // tell us about the latter.
  if (Runtime::Current()->IsAotCompiler()) {
    CompilerCallbacks* callbacks = Runtime::Current()->GetCompilerCallbacks();
    // We are compiling an app (not the image).
    if (!callbacks->CanUseOatStatusForVerification(klass.Get())) {
      return false;
    }
  }

  const OatDexFile* oat_dex_file = dex_file.GetOatDexFile();
  // In case we run without an image there won't be a backing oat file.
  if (oat_dex_file == nullptr || oat_dex_file->GetOatFile() == nullptr) {
    return false;
  }

  uint16_t class_def_index = klass->GetDexClassDefIndex();
  oat_file_class_status = oat_dex_file->GetOatClass(class_def_index).GetStatus();
  if (oat_file_class_status >= ClassStatus::kVerified) {
    return true;
  }
  if (oat_file_class_status >= ClassStatus::kVerifiedNeedsAccessChecks) {
    // We return that the clas has already been verified, and the caller should
    // check the class status to ensure we run with access checks.
    return true;
  }

  // Check the class status with the vdex file.
  const OatFile* oat_file = oat_dex_file->GetOatFile();
  if (oat_file != nullptr) {
    ClassStatus vdex_status = oat_file->GetVdexFile()->ComputeClassStatus(self, klass);
    if (vdex_status >= ClassStatus::kVerifiedNeedsAccessChecks) {
      VLOG(verifier) << "Vdex verification success for " << klass->PrettyClass();
      oat_file_class_status = vdex_status;
      return true;
    }
  }

  // If we only verified a subset of the classes at compile time, we can end up with classes that
  // were resolved by the verifier.
  if (oat_file_class_status == ClassStatus::kResolved) {
    return false;
  }
  // We never expect a .oat file to have kRetryVerificationAtRuntime statuses.
  CHECK_NE(oat_file_class_status, ClassStatus::kRetryVerificationAtRuntime)
      << klass->PrettyClass() << " " << dex_file.GetLocation();

  if (mirror::Class::IsErroneous(oat_file_class_status)) {
    // Compile time verification failed with a hard error. We'll re-run
    // verification, which might be successful at runtime.
    return false;
  }
  if (oat_file_class_status == ClassStatus::kNotReady) {
    // Status is uninitialized if we couldn't determine the status at compile time, for example,
    // not loading the class.
    // TODO: when the verifier doesn't rely on Class-es failing to resolve/load the type hierarchy
    // isn't a problem and this case shouldn't occur
    return false;
  }
  std::string temp;
  LOG(FATAL) << "Unexpected class status: " << oat_file_class_status
             << " " << dex_file.GetLocation() << " " << klass->PrettyClass() << " "
             << klass->GetDescriptor(&temp);
  UNREACHABLE();
}

void ClassLinker::ResolveClassExceptionHandlerTypes(Handle<mirror::Class> klass) {
  for (ArtMethod& method : klass->GetMethods(image_pointer_size_)) {
    ResolveMethodExceptionHandlerTypes(&method);
  }
}

void ClassLinker::ResolveMethodExceptionHandlerTypes(ArtMethod* method) {
  // similar to DexVerifier::ScanTryCatchBlocks and dex2oat's ResolveExceptionsForMethod.
  CodeItemDataAccessor accessor(method->DexInstructionData());
  if (!accessor.HasCodeItem()) {
    return;  // native or abstract method
  }
  if (accessor.TriesSize() == 0) {
    return;  // nothing to process
  }
  const uint8_t* handlers_ptr = accessor.GetCatchHandlerData(0);
  CHECK(method->GetDexFile()->IsInDataSection(handlers_ptr))
      << method->PrettyMethod()
      << "@" << method->GetDexFile()->GetLocation()
      << "@" << reinterpret_cast<const void*>(handlers_ptr)
      << " is_compact_dex=" << method->GetDexFile()->IsCompactDexFile();

  uint32_t handlers_size = DecodeUnsignedLeb128(&handlers_ptr);
  for (uint32_t idx = 0; idx < handlers_size; idx++) {
    CatchHandlerIterator iterator(handlers_ptr);
    for (; iterator.HasNext(); iterator.Next()) {
      // Ensure exception types are resolved so that they don't need resolution to be delivered,
      // unresolved exception types will be ignored by exception delivery
      if (iterator.GetHandlerTypeIndex().IsValid()) {
        ObjPtr<mirror::Class> exception_type = ResolveType(iterator.GetHandlerTypeIndex(), method);
        if (exception_type == nullptr) {
          DCHECK(Thread::Current()->IsExceptionPending());
          Thread::Current()->ClearException();
        }
      }
    }
    handlers_ptr = iterator.EndDataPointer();
  }
}

ObjPtr<mirror::Class> ClassLinker::CreateProxyClass(ScopedObjectAccessAlreadyRunnable& soa,
                                                    jstring name,
                                                    jobjectArray interfaces,
                                                    jobject loader,
                                                    jobjectArray methods,
                                                    jobjectArray throws) {
  Thread* self = soa.Self();

  // This is to prevent the calls to ClassLoad and ClassPrepare which can cause java/user-supplied
  // code to be executed. We put it up here so we can avoid all the allocations associated with
  // creating the class. This can happen with (eg) jit-threads.
  if (!self->CanLoadClasses()) {
    // Make sure we don't try to load anything, potentially causing an infinite loop.
    ObjPtr<mirror::Throwable> pre_allocated =
        Runtime::Current()->GetPreAllocatedNoClassDefFoundError();
    self->SetException(pre_allocated);
    return nullptr;
  }

  StackHandleScope<12> hs(self);
  MutableHandle<mirror::Class> temp_klass(hs.NewHandle(
      AllocClass(self, GetClassRoot<mirror::Class>(this), sizeof(mirror::Class))));
  if (temp_klass == nullptr) {
    CHECK(self->IsExceptionPending());  // OOME.
    return nullptr;
  }
  DCHECK(temp_klass->GetClass() != nullptr);
  temp_klass->SetObjectSize(sizeof(mirror::Proxy));
  // Set the class access flags incl. VerificationAttempted, so we do not try to set the flag on
  // the methods.
  temp_klass->SetAccessFlagsDuringLinking(kAccClassIsProxy | kAccPublic | kAccFinal);
  temp_klass->SetClassLoader(soa.Decode<mirror::ClassLoader>(loader));
  DCHECK_EQ(temp_klass->GetPrimitiveType(), Primitive::kPrimNot);
  temp_klass->SetName(soa.Decode<mirror::String>(name));
  temp_klass->SetDexCache(GetClassRoot<mirror::Proxy>(this)->GetDexCache());
  // Object has an empty iftable, copy it for that reason.
  temp_klass->SetIfTable(GetClassRoot<mirror::Object>(this)->GetIfTable());
  mirror::Class::SetStatus(temp_klass, ClassStatus::kIdx, self);
  std::string storage;
  const char* descriptor = temp_klass->GetDescriptor(&storage);
  const size_t hash = ComputeModifiedUtf8Hash(descriptor);

  // Needs to be before we insert the class so that the allocator field is set.
  LinearAlloc* const allocator = GetOrCreateAllocatorForClassLoader(temp_klass->GetClassLoader());

  // Insert the class before loading the fields as the field roots
  // (ArtField::declaring_class_) are only visited from the class
  // table. There can't be any suspend points between inserting the
  // class and setting the field arrays below.
  ObjPtr<mirror::Class> existing = InsertClass(descriptor, temp_klass.Get(), hash);
  CHECK(existing == nullptr);

  // Instance fields are inherited, but we add a couple of static fields...
  const size_t num_fields = 2;
  LengthPrefixedArray<ArtField>* sfields = AllocArtFieldArray(self, allocator, num_fields);
  temp_klass->SetSFieldsPtr(sfields);

  // 1. Create a static field 'interfaces' that holds the _declared_ interfaces implemented by
  // our proxy, so Class.getInterfaces doesn't return the flattened set.
  ArtField& interfaces_sfield = sfields->At(0);
  interfaces_sfield.SetDexFieldIndex(0);
  interfaces_sfield.SetDeclaringClass(temp_klass.Get());
  interfaces_sfield.SetAccessFlags(kAccStatic | kAccPublic | kAccFinal);

  // 2. Create a static field 'throws' that holds exceptions thrown by our methods.
  ArtField& throws_sfield = sfields->At(1);
  throws_sfield.SetDexFieldIndex(1);
  throws_sfield.SetDeclaringClass(temp_klass.Get());
  throws_sfield.SetAccessFlags(kAccStatic | kAccPublic | kAccFinal);

  // Proxies have 1 direct method, the constructor
  const size_t num_direct_methods = 1;

  // The array we get passed contains all methods, including private and static
  // ones that aren't proxied. We need to filter those out since only interface
  // methods (non-private & virtual) are actually proxied.
  Handle<mirror::ObjectArray<mirror::Method>> h_methods =
      hs.NewHandle(soa.Decode<mirror::ObjectArray<mirror::Method>>(methods));
  DCHECK_EQ(h_methods->GetClass(), GetClassRoot<mirror::ObjectArray<mirror::Method>>())
      << mirror::Class::PrettyClass(h_methods->GetClass());
  // List of the actual virtual methods this class will have.
  std::vector<ArtMethod*> proxied_methods;
  std::vector<size_t> proxied_throws_idx;
  proxied_methods.reserve(h_methods->GetLength());
  proxied_throws_idx.reserve(h_methods->GetLength());
  // Filter out to only the non-private virtual methods.
  for (auto [mirror, idx] : ZipCount(h_methods.Iterate<mirror::Method>())) {
    ArtMethod* m = mirror->GetArtMethod();
    if (!m->IsPrivate() && !m->IsStatic()) {
      proxied_methods.push_back(m);
      proxied_throws_idx.push_back(idx);
    }
  }
  const size_t num_virtual_methods = proxied_methods.size();
  // We also need to filter out the 'throws'. The 'throws' are a Class[][] that
  // contains an array of all the classes each function is declared to throw.
  // This is used to wrap unexpected exceptions in a
  // UndeclaredThrowableException exception. This array is in the same order as
  // the methods array and like the methods array must be filtered to remove any
  // non-proxied methods.
  const bool has_filtered_methods =
      static_cast<int32_t>(num_virtual_methods) != h_methods->GetLength();
  MutableHandle<mirror::ObjectArray<mirror::ObjectArray<mirror::Class>>> original_proxied_throws(
      hs.NewHandle(soa.Decode<mirror::ObjectArray<mirror::ObjectArray<mirror::Class>>>(throws)));
  MutableHandle<mirror::ObjectArray<mirror::ObjectArray<mirror::Class>>> proxied_throws(
      hs.NewHandle<mirror::ObjectArray<mirror::ObjectArray<mirror::Class>>>(
          (has_filtered_methods)
              ? mirror::ObjectArray<mirror::ObjectArray<mirror::Class>>::Alloc(
                    self, original_proxied_throws->GetClass(), num_virtual_methods)
              : original_proxied_throws.Get()));
  if (proxied_throws.IsNull() && !original_proxied_throws.IsNull()) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  if (has_filtered_methods) {
    for (auto [orig_idx, new_idx] : ZipCount(MakeIterationRange(proxied_throws_idx))) {
      DCHECK_LE(new_idx, orig_idx);
      proxied_throws->Set(new_idx, original_proxied_throws->Get(orig_idx));
    }
  }

  // Create the methods array.
  LengthPrefixedArray<ArtMethod>* proxy_class_methods = AllocArtMethodArray(
        self, allocator, num_direct_methods + num_virtual_methods);
  // Currently AllocArtMethodArray cannot return null, but the OOM logic is left there in case we
  // want to throw OOM in the future.
  if (UNLIKELY(proxy_class_methods == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  temp_klass->SetMethodsPtr(proxy_class_methods, num_direct_methods, num_virtual_methods);

  // Create the single direct method.
  CreateProxyConstructor(temp_klass, temp_klass->GetDirectMethodUnchecked(0, image_pointer_size_));

  // Create virtual method using specified prototypes.
  // TODO These should really use the iterators.
  for (size_t i = 0; i < num_virtual_methods; ++i) {
    auto* virtual_method = temp_klass->GetVirtualMethodUnchecked(i, image_pointer_size_);
    auto* prototype = proxied_methods[i];
    CreateProxyMethod(temp_klass, prototype, virtual_method);
    DCHECK(virtual_method->GetDeclaringClass() != nullptr);
    DCHECK(prototype->GetDeclaringClass() != nullptr);
  }

  // The super class is java.lang.reflect.Proxy
  temp_klass->SetSuperClass(GetClassRoot<mirror::Proxy>(this));
  // Now effectively in the loaded state.
  mirror::Class::SetStatus(temp_klass, ClassStatus::kLoaded, self);
  self->AssertNoPendingException();

  // At this point the class is loaded. Publish a ClassLoad event.
  // Note: this may be a temporary class. It is a listener's responsibility to handle this.
  Runtime::Current()->GetRuntimeCallbacks()->ClassLoad(temp_klass);

  MutableHandle<mirror::Class> klass = hs.NewHandle<mirror::Class>(nullptr);
  {
    // Must hold lock on object when resolved.
    ObjectLock<mirror::Class> resolution_lock(self, temp_klass);
    // Link the fields and virtual methods, creating vtable and iftables.
    // The new class will replace the old one in the class table.
    Handle<mirror::ObjectArray<mirror::Class>> h_interfaces(
        hs.NewHandle(soa.Decode<mirror::ObjectArray<mirror::Class>>(interfaces)));
    if (!LinkClass(self, descriptor, temp_klass, h_interfaces, &klass)) {
      if (!temp_klass->IsErroneous()) {
        mirror::Class::SetStatus(temp_klass, ClassStatus::kErrorUnresolved, self);
      }
      return nullptr;
    }
  }
  CHECK(temp_klass->IsRetired());
  CHECK_NE(temp_klass.Get(), klass.Get());

  CHECK_EQ(interfaces_sfield.GetDeclaringClass(), klass.Get());
  interfaces_sfield.SetObject<false>(
      klass.Get(),
      soa.Decode<mirror::ObjectArray<mirror::Class>>(interfaces));
  CHECK_EQ(throws_sfield.GetDeclaringClass(), klass.Get());
  throws_sfield.SetObject<false>(
      klass.Get(),
      proxied_throws.Get());

  Runtime::Current()->GetRuntimeCallbacks()->ClassPrepare(temp_klass, klass);

  // SubtypeCheckInfo::Initialized must happen-before any new-instance for that type.
  // See also ClassLinker::EnsureInitialized().
  if (kBitstringSubtypeCheckEnabled) {
    MutexLock subtype_check_lock(Thread::Current(), *Locks::subtype_check_lock_);
    SubtypeCheck<ObjPtr<mirror::Class>>::EnsureInitialized(klass.Get());
    // TODO: Avoid taking subtype_check_lock_ if SubtypeCheck for j.l.r.Proxy is already assigned.
  }

  VisiblyInitializedCallback* callback = nullptr;
  {
    // Lock on klass is released. Lock new class object.
    ObjectLock<mirror::Class> initialization_lock(self, klass);
    // Conservatively go through the ClassStatus::kInitialized state.
    callback = MarkClassInitialized(self, klass);
  }
  if (callback != nullptr) {
    callback->MakeVisible(self);
  }

  // Consistency checks.
  if (kIsDebugBuild) {
    CHECK(klass->GetIFieldsPtr() == nullptr);
    CheckProxyConstructor(klass->GetDirectMethod(0, image_pointer_size_));

    for (size_t i = 0; i < num_virtual_methods; ++i) {
      auto* virtual_method = klass->GetVirtualMethodUnchecked(i, image_pointer_size_);
      CheckProxyMethod(virtual_method, proxied_methods[i]);
    }

    StackHandleScope<1> hs2(self);
    Handle<mirror::String> decoded_name = hs2.NewHandle(soa.Decode<mirror::String>(name));
    std::string interfaces_field_name(StringPrintf("java.lang.Class[] %s.interfaces",
                                                   decoded_name->ToModifiedUtf8().c_str()));
    CHECK_EQ(ArtField::PrettyField(klass->GetStaticField(0)), interfaces_field_name);

    std::string throws_field_name(StringPrintf("java.lang.Class[][] %s.throws",
                                               decoded_name->ToModifiedUtf8().c_str()));
    CHECK_EQ(ArtField::PrettyField(klass->GetStaticField(1)), throws_field_name);

    CHECK_EQ(klass.Get()->GetProxyInterfaces(),
             soa.Decode<mirror::ObjectArray<mirror::Class>>(interfaces));
    CHECK_EQ(klass.Get()->GetProxyThrows(),
             proxied_throws.Get());
  }
  return klass.Get();
}

void ClassLinker::CreateProxyConstructor(Handle<mirror::Class> klass, ArtMethod* out) {
  // Create constructor for Proxy that must initialize the method.
  ObjPtr<mirror::Class> proxy_class = GetClassRoot<mirror::Proxy>(this);
  CHECK_EQ(proxy_class->NumDirectMethods(), 21u);

  // Find the <init>(InvocationHandler)V method. The exact method offset varies depending
  // on which front-end compiler was used to build the libcore DEX files.
  ArtMethod* proxy_constructor = WellKnownClasses::java_lang_reflect_Proxy_init;
  DCHECK(proxy_constructor != nullptr)
      << "Could not find <init> method in java.lang.reflect.Proxy";

  // Clone the existing constructor of Proxy (our constructor would just invoke it so steal its
  // code_ too)
  DCHECK(out != nullptr);
  out->CopyFrom(proxy_constructor, image_pointer_size_);
  // Make this constructor public and fix the class to be our Proxy version.
  // Mark kAccCompileDontBother so that we don't take JIT samples for the method. b/62349349
  // Note that the compiler calls a ResolveMethod() overload that does not handle a Proxy referrer.
  out->SetAccessFlags((out->GetAccessFlags() & ~kAccProtected) |
                      kAccPublic |
                      kAccCompileDontBother);
  out->SetDeclaringClass(klass.Get());

  // Set the original constructor method.
  out->SetDataPtrSize(proxy_constructor, image_pointer_size_);
}

void ClassLinker::CheckProxyConstructor(ArtMethod* constructor) const {
  CHECK(constructor->IsConstructor());
  auto* np = constructor->GetInterfaceMethodIfProxy(image_pointer_size_);
  CHECK_STREQ(np->GetName(), "<init>");
  CHECK_STREQ(np->GetSignature().ToString().c_str(), "(Ljava/lang/reflect/InvocationHandler;)V");
  DCHECK(constructor->IsPublic());
}

void ClassLinker::CreateProxyMethod(Handle<mirror::Class> klass, ArtMethod* prototype,
                                    ArtMethod* out) {
  // We steal everything from the prototype (such as DexCache, invoke stub, etc.) then specialize
  // as necessary
  DCHECK(out != nullptr);
  out->CopyFrom(prototype, image_pointer_size_);

  // Set class to be the concrete proxy class.
  out->SetDeclaringClass(klass.Get());
  // Clear the abstract and default flags to ensure that defaults aren't picked in
  // preference to the invocation handler.
  const uint32_t kRemoveFlags = kAccAbstract | kAccDefault;
  // Make the method final.
  // Mark kAccCompileDontBother so that we don't take JIT samples for the method. b/62349349
  const uint32_t kAddFlags = kAccFinal | kAccCompileDontBother;
  out->SetAccessFlags((out->GetAccessFlags() & ~kRemoveFlags) | kAddFlags);

  // Set the original interface method.
  out->SetDataPtrSize(prototype, image_pointer_size_);

  // At runtime the method looks like a reference and argument saving method, clone the code
  // related parameters from this method.
  out->SetEntryPointFromQuickCompiledCode(GetQuickProxyInvokeHandler());
}

void ClassLinker::CheckProxyMethod(ArtMethod* method, ArtMethod* prototype) const {
  // Basic consistency checks.
  CHECK(!prototype->IsFinal());
  CHECK(method->IsFinal());
  CHECK(method->IsInvokable());

  // The proxy method doesn't have its own dex cache or dex file and so it steals those of its
  // interface prototype. The exception to this are Constructors and the Class of the Proxy itself.
  CHECK_EQ(prototype->GetDexMethodIndex(), method->GetDexMethodIndex());
  CHECK_EQ(prototype, method->GetInterfaceMethodIfProxy(image_pointer_size_));
}

bool ClassLinker::CanWeInitializeClass(ObjPtr<mirror::Class> klass,
                                       bool can_init_statics,
                                       bool can_init_parents) {
  if (can_init_statics && can_init_parents) {
    return true;
  }
  DCHECK(Runtime::Current()->IsAotCompiler());

  // We currently don't support initializing at AOT time classes that need access
  // checks.
  if (klass->IsVerifiedNeedsAccessChecks()) {
    return false;
  }
  if (!can_init_statics) {
    // Check if there's a class initializer.
    ArtMethod* clinit = klass->FindClassInitializer(image_pointer_size_);
    if (clinit != nullptr) {
      return false;
    }
    // Check if there are encoded static values needing initialization.
    if (klass->NumStaticFields() != 0) {
      const dex::ClassDef* dex_class_def = klass->GetClassDef();
      DCHECK(dex_class_def != nullptr);
      if (dex_class_def->static_values_off_ != 0) {
        return false;
      }
    }
  }
  // If we are a class we need to initialize all interfaces with default methods when we are
  // initialized. Check all of them.
  if (!klass->IsInterface()) {
    size_t num_interfaces = klass->GetIfTableCount();
    for (size_t i = 0; i < num_interfaces; i++) {
      ObjPtr<mirror::Class> iface = klass->GetIfTable()->GetInterface(i);
      if (iface->HasDefaultMethods() && !iface->IsInitialized()) {
        if (!can_init_parents || !CanWeInitializeClass(iface, can_init_statics, can_init_parents)) {
          return false;
        }
      }
    }
  }
  if (klass->IsInterface() || !klass->HasSuperClass()) {
    return true;
  }
  ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
  if (super_class->IsInitialized()) {
    return true;
  }
  return can_init_parents && CanWeInitializeClass(super_class, can_init_statics, can_init_parents);
}

bool ClassLinker::InitializeClass(Thread* self,
                                  Handle<mirror::Class> klass,
                                  bool can_init_statics,
                                  bool can_init_parents) {
  // see JLS 3rd edition, 12.4.2 "Detailed Initialization Procedure" for the locking protocol

  // Are we already initialized and therefore done?
  // Note: we differ from the JLS here as we don't do this under the lock, this is benign as
  // an initialized class will never change its state.
  if (klass->IsInitialized()) {
    return true;
  }

  // Fast fail if initialization requires a full runtime. Not part of the JLS.
  if (!CanWeInitializeClass(klass.Get(), can_init_statics, can_init_parents)) {
    return false;
  }

  self->AllowThreadSuspension();
  Runtime* const runtime = Runtime::Current();
  const bool stats_enabled = runtime->HasStatsEnabled();
  uint64_t t0;
  {
    ObjectLock<mirror::Class> lock(self, klass);

    // Re-check under the lock in case another thread initialized ahead of us.
    if (klass->IsInitialized()) {
      return true;
    }

    // Was the class already found to be erroneous? Done under the lock to match the JLS.
    if (klass->IsErroneous()) {
      ThrowEarlierClassFailure(klass.Get(), true, /* log= */ true);
      VlogClassInitializationFailure(klass);
      return false;
    }

    CHECK(klass->IsResolved() && !klass->IsErroneousResolved())
        << klass->PrettyClass() << ": state=" << klass->GetStatus();

    if (!klass->IsVerified()) {
      VerifyClass(self, /*verifier_deps= */ nullptr, klass);
      if (!klass->IsVerified()) {
        // We failed to verify, expect either the klass to be erroneous or verification failed at
        // compile time.
        if (klass->IsErroneous()) {
          // The class is erroneous. This may be a verifier error, or another thread attempted
          // verification and/or initialization and failed. We can distinguish those cases by
          // whether an exception is already pending.
          if (self->IsExceptionPending()) {
            // Check that it's a VerifyError.
            DCHECK(IsVerifyError(self->GetException()));
          } else {
            // Check that another thread attempted initialization.
            DCHECK_NE(0, klass->GetClinitThreadId());
            DCHECK_NE(self->GetTid(), klass->GetClinitThreadId());
            // Need to rethrow the previous failure now.
            ThrowEarlierClassFailure(klass.Get(), true);
          }
          VlogClassInitializationFailure(klass);
        } else {
          CHECK(Runtime::Current()->IsAotCompiler());
          CHECK(klass->ShouldVerifyAtRuntime() || klass->IsVerifiedNeedsAccessChecks());
          self->AssertNoPendingException();
          self->SetException(Runtime::Current()->GetPreAllocatedNoClassDefFoundError());
        }
        self->AssertPendingException();
        return false;
      } else {
        self->AssertNoPendingException();
      }

      // A separate thread could have moved us all the way to initialized. A "simple" example
      // involves a subclass of the current class being initialized at the same time (which
      // will implicitly initialize the superclass, if scheduled that way). b/28254258
      DCHECK(!klass->IsErroneous()) << klass->GetStatus();
      if (klass->IsInitialized()) {
        return true;
      }
    }

    // If the class is ClassStatus::kInitializing, either this thread is
    // initializing higher up the stack or another thread has beat us
    // to initializing and we need to wait. Either way, this
    // invocation of InitializeClass will not be responsible for
    // running <clinit> and will return.
    if (klass->GetStatus() == ClassStatus::kInitializing) {
      // Could have got an exception during verification.
      if (self->IsExceptionPending()) {
        VlogClassInitializationFailure(klass);
        return false;
      }
      // We caught somebody else in the act; was it us?
      if (klass->GetClinitThreadId() == self->GetTid()) {
        // Yes. That's fine. Return so we can continue initializing.
        return true;
      }
      // No. That's fine. Wait for another thread to finish initializing.
      return WaitForInitializeClass(klass, self, lock);
    }

    // Try to get the oat class's status for this class if the oat file is present. The compiler
    // tries to validate superclass descriptors, and writes the result into the oat file.
    // Runtime correctness is guaranteed by classpath checks done on loading. If the classpath
    // is different at runtime than it was at compile time, the oat file is rejected. So if the
    // oat file is present, the classpaths must match, and the runtime time check can be skipped.
    bool has_oat_class = false;
    const OatFile::OatClass oat_class = (runtime->IsStarted() && !runtime->IsAotCompiler())
        ? OatFile::FindOatClass(klass->GetDexFile(), klass->GetDexClassDefIndex(), &has_oat_class)
        : OatFile::OatClass::Invalid();
    if (oat_class.GetStatus() < ClassStatus::kSuperclassValidated &&
        !ValidateSuperClassDescriptors(klass)) {
      mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
      return false;
    }
    self->AllowThreadSuspension();

    CHECK_EQ(klass->GetStatus(), ClassStatus::kVerified) << klass->PrettyClass()
        << " self.tid=" << self->GetTid() << " clinit.tid=" << klass->GetClinitThreadId();

    // From here out other threads may observe that we're initializing and so changes of state
    // require the a notification.
    klass->SetClinitThreadId(self->GetTid());
    mirror::Class::SetStatus(klass, ClassStatus::kInitializing, self);

    t0 = stats_enabled ? NanoTime() : 0u;
  }

  uint64_t t_sub = 0;

  // Initialize super classes, must be done while initializing for the JLS.
  if (!klass->IsInterface() && klass->HasSuperClass()) {
    ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
    if (!super_class->IsInitialized()) {
      CHECK(!super_class->IsInterface());
      CHECK(can_init_parents);
      StackHandleScope<1> hs(self);
      Handle<mirror::Class> handle_scope_super(hs.NewHandle(super_class));
      uint64_t super_t0 = stats_enabled ? NanoTime() : 0u;
      bool super_initialized = InitializeClass(self, handle_scope_super, can_init_statics, true);
      uint64_t super_t1 = stats_enabled ? NanoTime() : 0u;
      if (!super_initialized) {
        // The super class was verified ahead of entering initializing, we should only be here if
        // the super class became erroneous due to initialization.
        // For the case of aot compiler, the super class might also be initializing but we don't
        // want to process circular dependencies in pre-compile.
        CHECK(self->IsExceptionPending())
            << "Super class initialization failed for "
            << handle_scope_super->PrettyDescriptor()
            << " that has unexpected status " << handle_scope_super->GetStatus()
            << "\nPending exception:\n"
            << (self->GetException() != nullptr ? self->GetException()->Dump() : "");
        ObjectLock<mirror::Class> lock(self, klass);
        // Initialization failed because the super-class is erroneous.
        mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
        return false;
      }
      t_sub = super_t1 - super_t0;
    }
  }

  if (!klass->IsInterface()) {
    // Initialize interfaces with default methods for the JLS.
    size_t num_direct_interfaces = klass->NumDirectInterfaces();
    // Only setup the (expensive) handle scope if we actually need to.
    if (UNLIKELY(num_direct_interfaces > 0)) {
      StackHandleScope<1> hs_iface(self);
      MutableHandle<mirror::Class> handle_scope_iface(hs_iface.NewHandle<mirror::Class>(nullptr));
      for (size_t i = 0; i < num_direct_interfaces; i++) {
        handle_scope_iface.Assign(klass->GetDirectInterface(i));
        CHECK(handle_scope_iface != nullptr) << klass->PrettyDescriptor() << " iface #" << i;
        CHECK(handle_scope_iface->IsInterface());
        if (handle_scope_iface->HasBeenRecursivelyInitialized()) {
          // We have already done this for this interface. Skip it.
          continue;
        }
        // We cannot just call initialize class directly because we need to ensure that ALL
        // interfaces with default methods are initialized. Non-default interface initialization
        // will not affect other non-default super-interfaces.
        // This is not very precise, misses all walking.
        uint64_t inf_t0 = stats_enabled ? NanoTime() : 0u;
        bool iface_initialized = InitializeDefaultInterfaceRecursive(self,
                                                                     handle_scope_iface,
                                                                     can_init_statics,
                                                                     can_init_parents);
        uint64_t inf_t1 = stats_enabled ? NanoTime() : 0u;
        if (!iface_initialized) {
          ObjectLock<mirror::Class> lock(self, klass);
          // Initialization failed because one of our interfaces with default methods is erroneous.
          mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
          return false;
        }
        t_sub += inf_t1 - inf_t0;
      }
    }
  }

  const size_t num_static_fields = klass->NumStaticFields();
  if (num_static_fields > 0) {
    const dex::ClassDef* dex_class_def = klass->GetClassDef();
    CHECK(dex_class_def != nullptr);
    StackHandleScope<3> hs(self);
    Handle<mirror::ClassLoader> class_loader(hs.NewHandle(klass->GetClassLoader()));
    Handle<mirror::DexCache> dex_cache(hs.NewHandle(klass->GetDexCache()));

    // Eagerly fill in static fields so that the we don't have to do as many expensive
    // Class::FindStaticField in ResolveField.
    for (size_t i = 0; i < num_static_fields; ++i) {
      ArtField* field = klass->GetStaticField(i);
      const uint32_t field_idx = field->GetDexFieldIndex();
      ArtField* resolved_field = dex_cache->GetResolvedField(field_idx);
      if (resolved_field == nullptr) {
        // Populating cache of a dex file which defines `klass` should always be allowed.
        DCHECK(!hiddenapi::ShouldDenyAccessToMember(
            field,
            hiddenapi::AccessContext(class_loader.Get(), dex_cache.Get()),
            hiddenapi::AccessMethod::kNone));
        dex_cache->SetResolvedField(field_idx, field);
      } else {
        DCHECK_EQ(field, resolved_field);
      }
    }

    annotations::RuntimeEncodedStaticFieldValueIterator value_it(dex_cache,
                                                                 class_loader,
                                                                 this,
                                                                 *dex_class_def);
    const DexFile& dex_file = *dex_cache->GetDexFile();

    if (value_it.HasNext()) {
      ClassAccessor accessor(dex_file, *dex_class_def);
      CHECK(can_init_statics);
      for (const ClassAccessor::Field& field : accessor.GetStaticFields()) {
        if (!value_it.HasNext()) {
          break;
        }
        ArtField* art_field = ResolveField(field.GetIndex(),
                                           dex_cache,
                                           class_loader,
                                           /* is_static= */ true);
        if (Runtime::Current()->IsActiveTransaction()) {
          value_it.ReadValueToField<true>(art_field);
        } else {
          value_it.ReadValueToField<false>(art_field);
        }
        if (self->IsExceptionPending()) {
          break;
        }
        value_it.Next();
      }
      DCHECK(self->IsExceptionPending() || !value_it.HasNext());
    }
  }


  if (!self->IsExceptionPending()) {
    ArtMethod* clinit = klass->FindClassInitializer(image_pointer_size_);
    if (clinit != nullptr) {
      CHECK(can_init_statics);
      JValue result;
      clinit->Invoke(self, nullptr, 0, &result, "V");
    }
  }
  self->AllowThreadSuspension();
  uint64_t t1 = stats_enabled ? NanoTime() : 0u;

  VisiblyInitializedCallback* callback = nullptr;
  bool success = true;
  {
    ObjectLock<mirror::Class> lock(self, klass);

    if (self->IsExceptionPending()) {
      WrapExceptionInInitializer(klass);
      mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
      success = false;
    } else if (Runtime::Current()->IsTransactionAborted()) {
      // The exception thrown when the transaction aborted has been caught and cleared
      // so we need to throw it again now.
      VLOG(compiler) << "Return from class initializer of "
                     << mirror::Class::PrettyDescriptor(klass.Get())
                     << " without exception while transaction was aborted: re-throw it now.";
      runtime->ThrowTransactionAbortError(self);
      mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
      success = false;
    } else {
      if (stats_enabled) {
        RuntimeStats* global_stats = runtime->GetStats();
        RuntimeStats* thread_stats = self->GetStats();
        ++global_stats->class_init_count;
        ++thread_stats->class_init_count;
        global_stats->class_init_time_ns += (t1 - t0 - t_sub);
        thread_stats->class_init_time_ns += (t1 - t0 - t_sub);
      }
      // Set the class as initialized except if failed to initialize static fields.
      callback = MarkClassInitialized(self, klass);
      if (VLOG_IS_ON(class_linker)) {
        std::string temp;
        LOG(INFO) << "Initialized class " << klass->GetDescriptor(&temp) << " from " <<
            klass->GetLocation();
      }
    }
  }
  if (callback != nullptr) {
    callback->MakeVisible(self);
  }
  return success;
}

// We recursively run down the tree of interfaces. We need to do this in the order they are declared
// and perform the initialization only on those interfaces that contain default methods.
bool ClassLinker::InitializeDefaultInterfaceRecursive(Thread* self,
                                                      Handle<mirror::Class> iface,
                                                      bool can_init_statics,
                                                      bool can_init_parents) {
  CHECK(iface->IsInterface());
  size_t num_direct_ifaces = iface->NumDirectInterfaces();
  // Only create the (expensive) handle scope if we need it.
  if (UNLIKELY(num_direct_ifaces > 0)) {
    StackHandleScope<1> hs(self);
    MutableHandle<mirror::Class> handle_super_iface(hs.NewHandle<mirror::Class>(nullptr));
    // First we initialize all of iface's super-interfaces recursively.
    for (size_t i = 0; i < num_direct_ifaces; i++) {
      ObjPtr<mirror::Class> super_iface = iface->GetDirectInterface(i);
      CHECK(super_iface != nullptr) << iface->PrettyDescriptor() << " iface #" << i;
      if (!super_iface->HasBeenRecursivelyInitialized()) {
        // Recursive step
        handle_super_iface.Assign(super_iface);
        if (!InitializeDefaultInterfaceRecursive(self,
                                                 handle_super_iface,
                                                 can_init_statics,
                                                 can_init_parents)) {
          return false;
        }
      }
    }
  }

  bool result = true;
  // Then we initialize 'iface' if it has default methods. We do not need to (and in fact must not)
  // initialize if we don't have default methods.
  if (iface->HasDefaultMethods()) {
    result = EnsureInitialized(self, iface, can_init_statics, can_init_parents);
  }

  // Mark that this interface has undergone recursive default interface initialization so we know we
  // can skip it on any later class initializations. We do this even if we are not a default
  // interface since we can still avoid the traversal. This is purely a performance optimization.
  if (result) {
    // TODO This should be done in a better way
    // Note: Use a try-lock to avoid blocking when someone else is holding the lock on this
    //       interface. It is bad (Java) style, but not impossible. Marking the recursive
    //       initialization is a performance optimization (to avoid another idempotent visit
    //       for other implementing classes/interfaces), and can be revisited later.
    ObjectTryLock<mirror::Class> lock(self, iface);
    if (lock.Acquired()) {
      iface->SetRecursivelyInitialized();
    }
  }
  return result;
}

bool ClassLinker::WaitForInitializeClass(Handle<mirror::Class> klass,
                                         Thread* self,
                                         ObjectLock<mirror::Class>& lock)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  while (true) {
    self->AssertNoPendingException();
    CHECK(!klass->IsInitialized());
    lock.WaitIgnoringInterrupts();

    // When we wake up, repeat the test for init-in-progress.  If
    // there's an exception pending (only possible if
    // we were not using WaitIgnoringInterrupts), bail out.
    if (self->IsExceptionPending()) {
      WrapExceptionInInitializer(klass);
      mirror::Class::SetStatus(klass, ClassStatus::kErrorResolved, self);
      return false;
    }
    // Spurious wakeup? Go back to waiting.
    if (klass->GetStatus() == ClassStatus::kInitializing) {
      continue;
    }
    if (klass->GetStatus() == ClassStatus::kVerified &&
        Runtime::Current()->IsAotCompiler()) {
      // Compile time initialization failed.
      return false;
    }
    if (klass->IsErroneous()) {
      // The caller wants an exception, but it was thrown in a
      // different thread.  Synthesize one here.
      ThrowNoClassDefFoundError("<clinit> failed for class %s; see exception in other thread",
                                klass->PrettyDescriptor().c_str());
      VlogClassInitializationFailure(klass);
      return false;
    }
    if (klass->IsInitialized()) {
      return true;
    }
    LOG(FATAL) << "Unexpected class status. " << klass->PrettyClass() << " is "
        << klass->GetStatus();
  }
  UNREACHABLE();
}

static void ThrowSignatureCheckResolveReturnTypeException(Handle<mirror::Class> klass,
                                                          Handle<mirror::Class> super_klass,
                                                          ArtMethod* method,
                                                          ArtMethod* m)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(Thread::Current()->IsExceptionPending());
  DCHECK(!m->IsProxyMethod());
  const DexFile* dex_file = m->GetDexFile();
  const dex::MethodId& method_id = dex_file->GetMethodId(m->GetDexMethodIndex());
  const dex::ProtoId& proto_id = dex_file->GetMethodPrototype(method_id);
  dex::TypeIndex return_type_idx = proto_id.return_type_idx_;
  std::string return_type = dex_file->PrettyType(return_type_idx);
  std::string class_loader = mirror::Object::PrettyTypeOf(m->GetDeclaringClass()->GetClassLoader());
  ThrowWrappedLinkageError(klass.Get(),
                           "While checking class %s method %s signature against %s %s: "
                           "Failed to resolve return type %s with %s",
                           mirror::Class::PrettyDescriptor(klass.Get()).c_str(),
                           ArtMethod::PrettyMethod(method).c_str(),
                           super_klass->IsInterface() ? "interface" : "superclass",
                           mirror::Class::PrettyDescriptor(super_klass.Get()).c_str(),
                           return_type.c_str(), class_loader.c_str());
}

static void ThrowSignatureCheckResolveArgException(Handle<mirror::Class> klass,
                                                   Handle<mirror::Class> super_klass,
                                                   ArtMethod* method,
                                                   ArtMethod* m,
                                                   uint32_t index,
                                                   dex::TypeIndex arg_type_idx)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(Thread::Current()->IsExceptionPending());
  DCHECK(!m->IsProxyMethod());
  const DexFile* dex_file = m->GetDexFile();
  std::string arg_type = dex_file->PrettyType(arg_type_idx);
  std::string class_loader = mirror::Object::PrettyTypeOf(m->GetDeclaringClass()->GetClassLoader());
  ThrowWrappedLinkageError(klass.Get(),
                           "While checking class %s method %s signature against %s %s: "
                           "Failed to resolve arg %u type %s with %s",
                           mirror::Class::PrettyDescriptor(klass.Get()).c_str(),
                           ArtMethod::PrettyMethod(method).c_str(),
                           super_klass->IsInterface() ? "interface" : "superclass",
                           mirror::Class::PrettyDescriptor(super_klass.Get()).c_str(),
                           index, arg_type.c_str(), class_loader.c_str());
}

static void ThrowSignatureMismatch(Handle<mirror::Class> klass,
                                   Handle<mirror::Class> super_klass,
                                   ArtMethod* method,
                                   const std::string& error_msg)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ThrowLinkageError(klass.Get(),
                    "Class %s method %s resolves differently in %s %s: %s",
                    mirror::Class::PrettyDescriptor(klass.Get()).c_str(),
                    ArtMethod::PrettyMethod(method).c_str(),
                    super_klass->IsInterface() ? "interface" : "superclass",
                    mirror::Class::PrettyDescriptor(super_klass.Get()).c_str(),
                    error_msg.c_str());
}

static bool HasSameSignatureWithDifferentClassLoaders(Thread* self,
                                                      Handle<mirror::Class> klass,
                                                      Handle<mirror::Class> super_klass,
                                                      ArtMethod* method1,
                                                      ArtMethod* method2)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  {
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> return_type(hs.NewHandle(method1->ResolveReturnType()));
    if (UNLIKELY(return_type == nullptr)) {
      ThrowSignatureCheckResolveReturnTypeException(klass, super_klass, method1, method1);
      return false;
    }
    ObjPtr<mirror::Class> other_return_type = method2->ResolveReturnType();
    if (UNLIKELY(other_return_type == nullptr)) {
      ThrowSignatureCheckResolveReturnTypeException(klass, super_klass, method1, method2);
      return false;
    }
    if (UNLIKELY(other_return_type != return_type.Get())) {
      ThrowSignatureMismatch(klass, super_klass, method1,
                             StringPrintf("Return types mismatch: %s(%p) vs %s(%p)",
                                          return_type->PrettyClassAndClassLoader().c_str(),
                                          return_type.Get(),
                                          other_return_type->PrettyClassAndClassLoader().c_str(),
                                          other_return_type.Ptr()));
      return false;
    }
  }
  const dex::TypeList* types1 = method1->GetParameterTypeList();
  const dex::TypeList* types2 = method2->GetParameterTypeList();
  if (types1 == nullptr) {
    if (types2 != nullptr && types2->Size() != 0) {
      ThrowSignatureMismatch(klass, super_klass, method1,
                             StringPrintf("Type list mismatch with %s",
                                          method2->PrettyMethod(true).c_str()));
      return false;
    }
    return true;
  } else if (UNLIKELY(types2 == nullptr)) {
    if (types1->Size() != 0) {
      ThrowSignatureMismatch(klass, super_klass, method1,
                             StringPrintf("Type list mismatch with %s",
                                          method2->PrettyMethod(true).c_str()));
      return false;
    }
    return true;
  }
  uint32_t num_types = types1->Size();
  if (UNLIKELY(num_types != types2->Size())) {
    ThrowSignatureMismatch(klass, super_klass, method1,
                           StringPrintf("Type list mismatch with %s",
                                        method2->PrettyMethod(true).c_str()));
    return false;
  }
  for (uint32_t i = 0; i < num_types; ++i) {
    StackHandleScope<1> hs(self);
    dex::TypeIndex param_type_idx = types1->GetTypeItem(i).type_idx_;
    Handle<mirror::Class> param_type(hs.NewHandle(
        method1->ResolveClassFromTypeIndex(param_type_idx)));
    if (UNLIKELY(param_type == nullptr)) {
      ThrowSignatureCheckResolveArgException(klass, super_klass, method1,
                                             method1, i, param_type_idx);
      return false;
    }
    dex::TypeIndex other_param_type_idx = types2->GetTypeItem(i).type_idx_;
    ObjPtr<mirror::Class> other_param_type =
        method2->ResolveClassFromTypeIndex(other_param_type_idx);
    if (UNLIKELY(other_param_type == nullptr)) {
      ThrowSignatureCheckResolveArgException(klass, super_klass, method1,
                                             method2, i, other_param_type_idx);
      return false;
    }
    if (UNLIKELY(param_type.Get() != other_param_type)) {
      ThrowSignatureMismatch(klass, super_klass, method1,
                             StringPrintf("Parameter %u type mismatch: %s(%p) vs %s(%p)",
                                          i,
                                          param_type->PrettyClassAndClassLoader().c_str(),
                                          param_type.Get(),
                                          other_param_type->PrettyClassAndClassLoader().c_str(),
                                          other_param_type.Ptr()));
      return false;
    }
  }
  return true;
}


bool ClassLinker::ValidateSuperClassDescriptors(Handle<mirror::Class> klass) {
  if (klass->IsInterface()) {
    return true;
  }
  // Begin with the methods local to the superclass.
  Thread* self = Thread::Current();
  StackHandleScope<1> hs(self);
  MutableHandle<mirror::Class> super_klass(hs.NewHandle<mirror::Class>(nullptr));
  if (klass->HasSuperClass() &&
      klass->GetClassLoader() != klass->GetSuperClass()->GetClassLoader()) {
    super_klass.Assign(klass->GetSuperClass());
    for (int i = klass->GetSuperClass()->GetVTableLength() - 1; i >= 0; --i) {
      auto* m = klass->GetVTableEntry(i, image_pointer_size_);
      auto* super_m = klass->GetSuperClass()->GetVTableEntry(i, image_pointer_size_);
      if (m != super_m) {
        if (UNLIKELY(!HasSameSignatureWithDifferentClassLoaders(self,
                                                                klass,
                                                                super_klass,
                                                                m,
                                                                super_m))) {
          self->AssertPendingException();
          return false;
        }
      }
    }
  }
  for (int32_t i = 0; i < klass->GetIfTableCount(); ++i) {
    super_klass.Assign(klass->GetIfTable()->GetInterface(i));
    if (klass->GetClassLoader() != super_klass->GetClassLoader()) {
      uint32_t num_methods = super_klass->NumVirtualMethods();
      for (uint32_t j = 0; j < num_methods; ++j) {
        auto* m = klass->GetIfTable()->GetMethodArray(i)->GetElementPtrSize<ArtMethod*>(
            j, image_pointer_size_);
        auto* super_m = super_klass->GetVirtualMethod(j, image_pointer_size_);
        if (m != super_m) {
          if (UNLIKELY(!HasSameSignatureWithDifferentClassLoaders(self,
                                                                  klass,
                                                                  super_klass,
                                                                  m,
                                                                  super_m))) {
            self->AssertPendingException();
            return false;
          }
        }
      }
    }
  }
  return true;
}

bool ClassLinker::EnsureInitialized(Thread* self,
                                    Handle<mirror::Class> c,
                                    bool can_init_fields,
                                    bool can_init_parents) {
  DCHECK(c != nullptr);

  if (c->IsInitialized()) {
    // If we've seen an initialized but not visibly initialized class
    // many times, request visible initialization.
    if (kRuntimeISA == InstructionSet::kX86 || kRuntimeISA == InstructionSet::kX86_64) {
      // Thanks to the x86 memory model classes skip the initialized status.
      DCHECK(c->IsVisiblyInitialized());
    } else if (UNLIKELY(!c->IsVisiblyInitialized())) {
      if (self->IncrementMakeVisiblyInitializedCounter()) {
        MakeInitializedClassesVisiblyInitialized(self, /*wait=*/ false);
      }
    }
    return true;
  }
  // SubtypeCheckInfo::Initialized must happen-before any new-instance for that type.
  //
  // Ensure the bitstring is initialized before any of the class initialization
  // logic occurs. Once a class initializer starts running, objects can
  // escape into the heap and use the subtype checking code.
  //
  // Note: A class whose SubtypeCheckInfo is at least Initialized means it
  // can be used as a source for the IsSubClass check, and that all ancestors
  // of the class are Assigned (can be used as a target for IsSubClass check)
  // or Overflowed (can be used as a source for IsSubClass check).
  if (kBitstringSubtypeCheckEnabled) {
    MutexLock subtype_check_lock(Thread::Current(), *Locks::subtype_check_lock_);
    SubtypeCheck<ObjPtr<mirror::Class>>::EnsureInitialized(c.Get());
    // TODO: Avoid taking subtype_check_lock_ if SubtypeCheck is already initialized.
  }
  const bool success = InitializeClass(self, c, can_init_fields, can_init_parents);
  if (!success) {
    if (can_init_fields && can_init_parents) {
      CHECK(self->IsExceptionPending()) << c->PrettyClass();
    } else {
      // There may or may not be an exception pending. If there is, clear it.
      // We propagate the exception only if we can initialize fields and parents.
      self->ClearException();
    }
  } else {
    self->AssertNoPendingException();
  }
  return success;
}

void ClassLinker::FixupTemporaryDeclaringClass(ObjPtr<mirror::Class> temp_class,
                                               ObjPtr<mirror::Class> new_class) {
  DCHECK_EQ(temp_class->NumInstanceFields(), 0u);
  for (ArtField& field : new_class->GetIFields()) {
    if (field.GetDeclaringClass() == temp_class) {
      field.SetDeclaringClass(new_class);
    }
  }

  DCHECK_EQ(temp_class->NumStaticFields(), 0u);
  for (ArtField& field : new_class->GetSFields()) {
    if (field.GetDeclaringClass() == temp_class) {
      field.SetDeclaringClass(new_class);
    }
  }

  DCHECK_EQ(temp_class->NumDirectMethods(), 0u);
  DCHECK_EQ(temp_class->NumVirtualMethods(), 0u);
  for (auto& method : new_class->GetMethods(image_pointer_size_)) {
    if (method.GetDeclaringClass() == temp_class) {
      method.SetDeclaringClass(new_class);
    }
  }

  // Make sure the remembered set and mod-union tables know that we updated some of the native
  // roots.
  WriteBarrier::ForEveryFieldWrite(new_class);
}

void ClassLinker::RegisterClassLoader(ObjPtr<mirror::ClassLoader> class_loader) {
  CHECK(class_loader->GetAllocator() == nullptr);
  CHECK(class_loader->GetClassTable() == nullptr);
  Thread* const self = Thread::Current();
  ClassLoaderData data;
  data.weak_root = self->GetJniEnv()->GetVm()->AddWeakGlobalRef(self, class_loader);
  // Create and set the class table.
  data.class_table = new ClassTable;
  class_loader->SetClassTable(data.class_table);
  // Create and set the linear allocator.
  data.allocator = Runtime::Current()->CreateLinearAlloc();
  class_loader->SetAllocator(data.allocator);
  // Add to the list so that we know to free the data later.
  class_loaders_.push_back(data);
}

ClassTable* ClassLinker::InsertClassTableForClassLoader(ObjPtr<mirror::ClassLoader> class_loader) {
  if (class_loader == nullptr) {
    return boot_class_table_.get();
  }
  ClassTable* class_table = class_loader->GetClassTable();
  if (class_table == nullptr) {
    RegisterClassLoader(class_loader);
    class_table = class_loader->GetClassTable();
    DCHECK(class_table != nullptr);
  }
  return class_table;
}

ClassTable* ClassLinker::ClassTableForClassLoader(ObjPtr<mirror::ClassLoader> class_loader) {
  return class_loader == nullptr ? boot_class_table_.get() : class_loader->GetClassTable();
}

bool ClassLinker::LinkClass(Thread* self,
                            const char* descriptor,
                            Handle<mirror::Class> klass,
                            Handle<mirror::ObjectArray<mirror::Class>> interfaces,
                            MutableHandle<mirror::Class>* h_new_class_out) {
  CHECK_EQ(ClassStatus::kLoaded, klass->GetStatus());

  if (!LinkSuperClass(klass)) {
    return false;
  }
  ArtMethod* imt_data[ImTable::kSize];
  // If there are any new conflicts compared to super class.
  bool new_conflict = false;
  std::fill_n(imt_data, arraysize(imt_data), Runtime::Current()->GetImtUnimplementedMethod());
  if (!LinkMethods(self, klass, interfaces, &new_conflict, imt_data)) {
    return false;
  }
  if (!LinkInstanceFields(self, klass)) {
    return false;
  }
  size_t class_size;
  if (!LinkStaticFields(self, klass, &class_size)) {
    return false;
  }
  CreateReferenceInstanceOffsets(klass);
  CHECK_EQ(ClassStatus::kLoaded, klass->GetStatus());

  ImTable* imt = nullptr;
  if (klass->ShouldHaveImt()) {
    // If there are any new conflicts compared to the super class we can not make a copy. There
    // can be cases where both will have a conflict method at the same slot without having the same
    // set of conflicts. In this case, we can not share the IMT since the conflict table slow path
    // will possibly create a table that is incorrect for either of the classes.
    // Same IMT with new_conflict does not happen very often.
    if (!new_conflict) {
      ImTable* super_imt = klass->FindSuperImt(image_pointer_size_);
      if (super_imt != nullptr) {
        bool imt_equals = true;
        for (size_t i = 0; i < ImTable::kSize && imt_equals; ++i) {
          imt_equals = imt_equals && (super_imt->Get(i, image_pointer_size_) == imt_data[i]);
        }
        if (imt_equals) {
          imt = super_imt;
        }
      }
    }
    if (imt == nullptr) {
      LinearAlloc* allocator = GetAllocatorForClassLoader(klass->GetClassLoader());
      imt = reinterpret_cast<ImTable*>(
          allocator->Alloc(self,
                           ImTable::SizeInBytes(image_pointer_size_),
                           LinearAllocKind::kNoGCRoots));
      if (imt == nullptr) {
        return false;
      }
      imt->Populate(imt_data, image_pointer_size_);
    }
  }

  if (!klass->IsTemp() || (!init_done_ && klass->GetClassSize() == class_size)) {
    // We don't need to retire this class as it has no embedded tables or it was created the
    // correct size during class linker initialization.
    CHECK_EQ(klass->GetClassSize(), class_size) << klass->PrettyDescriptor();

    if (klass->ShouldHaveEmbeddedVTable()) {
      klass->PopulateEmbeddedVTable(image_pointer_size_);
    }
    if (klass->ShouldHaveImt()) {
      klass->SetImt(imt, image_pointer_size_);
    }

    // Update CHA info based on whether we override methods.
    // Have to do this before setting the class as resolved which allows
    // instantiation of klass.
    if (LIKELY(descriptor != nullptr) && cha_ != nullptr) {
      cha_->UpdateAfterLoadingOf(klass);
    }

    // This will notify waiters on klass that saw the not yet resolved
    // class in the class_table_ during EnsureResolved.
    mirror::Class::SetStatus(klass, ClassStatus::kResolved, self);
    h_new_class_out->Assign(klass.Get());
  } else {
    CHECK(!klass->IsResolved());
    // Retire the temporary class and create the correctly sized resolved class.
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> h_new_class =
        hs.NewHandle(mirror::Class::CopyOf(klass, self, class_size, imt, image_pointer_size_));
    // Set arrays to null since we don't want to have multiple classes with the same ArtField or
    // ArtMethod array pointers. If this occurs, it causes bugs in remembered sets since the GC
    // may not see any references to the target space and clean the card for a class if another
    // class had the same array pointer.
    klass->SetMethodsPtrUnchecked(nullptr, 0, 0);
    klass->SetSFieldsPtrUnchecked(nullptr);
    klass->SetIFieldsPtrUnchecked(nullptr);
    if (UNLIKELY(h_new_class == nullptr)) {
      self->AssertPendingOOMException();
      mirror::Class::SetStatus(klass, ClassStatus::kErrorUnresolved, self);
      return false;
    }

    CHECK_EQ(h_new_class->GetClassSize(), class_size);
    ObjectLock<mirror::Class> lock(self, h_new_class);
    FixupTemporaryDeclaringClass(klass.Get(), h_new_class.Get());

    if (LIKELY(descriptor != nullptr)) {
      WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
      const ObjPtr<mirror::ClassLoader> class_loader = h_new_class.Get()->GetClassLoader();
      ClassTable* const table = InsertClassTableForClassLoader(class_loader);
      const ObjPtr<mirror::Class> existing =
          table->UpdateClass(descriptor, h_new_class.Get(), ComputeModifiedUtf8Hash(descriptor));
      CHECK_EQ(existing, klass.Get());
      WriteBarrierOnClassLoaderLocked(class_loader, h_new_class.Get());
    }

    // Update CHA info based on whether we override methods.
    // Have to do this before setting the class as resolved which allows
    // instantiation of klass.
    if (LIKELY(descriptor != nullptr) && cha_ != nullptr) {
      cha_->UpdateAfterLoadingOf(h_new_class);
    }

    // This will notify waiters on temp class that saw the not yet resolved class in the
    // class_table_ during EnsureResolved.
    mirror::Class::SetStatus(klass, ClassStatus::kRetired, self);

    CHECK_EQ(h_new_class->GetStatus(), ClassStatus::kResolving);
    // This will notify waiters on new_class that saw the not yet resolved
    // class in the class_table_ during EnsureResolved.
    mirror::Class::SetStatus(h_new_class, ClassStatus::kResolved, self);
    // Return the new class.
    h_new_class_out->Assign(h_new_class.Get());
  }
  return true;
}

bool ClassLinker::LoadSuperAndInterfaces(Handle<mirror::Class> klass, const DexFile& dex_file) {
  CHECK_EQ(ClassStatus::kIdx, klass->GetStatus());
  const dex::ClassDef& class_def = dex_file.GetClassDef(klass->GetDexClassDefIndex());
  dex::TypeIndex super_class_idx = class_def.superclass_idx_;
  if (super_class_idx.IsValid()) {
    // Check that a class does not inherit from itself directly.
    //
    // TODO: This is a cheap check to detect the straightforward case
    // of a class extending itself (b/28685551), but we should do a
    // proper cycle detection on loaded classes, to detect all cases
    // of class circularity errors (b/28830038).
    if (super_class_idx == class_def.class_idx_) {
      ThrowClassCircularityError(klass.Get(),
                                 "Class %s extends itself",
                                 klass->PrettyDescriptor().c_str());
      return false;
    }

    ObjPtr<mirror::Class> super_class = ResolveType(super_class_idx, klass.Get());
    if (super_class == nullptr) {
      DCHECK(Thread::Current()->IsExceptionPending());
      return false;
    }
    // Verify
    if (!klass->CanAccess(super_class)) {
      ThrowIllegalAccessError(klass.Get(), "Class %s extended by class %s is inaccessible",
                              super_class->PrettyDescriptor().c_str(),
                              klass->PrettyDescriptor().c_str());
      return false;
    }
    CHECK(super_class->IsResolved());
    klass->SetSuperClass(super_class);
  }
  const dex::TypeList* interfaces = dex_file.GetInterfacesList(class_def);
  if (interfaces != nullptr) {
    for (size_t i = 0; i < interfaces->Size(); i++) {
      dex::TypeIndex idx = interfaces->GetTypeItem(i).type_idx_;
      if (idx.IsValid()) {
        // Check that a class does not implement itself directly.
        //
        // TODO: This is a cheap check to detect the straightforward case of a class implementing
        // itself, but we should do a proper cycle detection on loaded classes, to detect all cases
        // of class circularity errors. See b/28685551, b/28830038, and b/301108855
        if (idx == class_def.class_idx_) {
          ThrowClassCircularityError(
              klass.Get(), "Class %s implements itself", klass->PrettyDescriptor().c_str());
          return false;
        }
      }

      ObjPtr<mirror::Class> interface = ResolveType(idx, klass.Get());
      if (interface == nullptr) {
        DCHECK(Thread::Current()->IsExceptionPending());
        return false;
      }
      // Verify
      if (!klass->CanAccess(interface)) {
        // TODO: the RI seemed to ignore this in my testing.
        ThrowIllegalAccessError(klass.Get(),
                                "Interface %s implemented by class %s is inaccessible",
                                interface->PrettyDescriptor().c_str(),
                                klass->PrettyDescriptor().c_str());
        return false;
      }
    }
  }
  // Mark the class as loaded.
  mirror::Class::SetStatus(klass, ClassStatus::kLoaded, nullptr);
  return true;
}

bool ClassLinker::LinkSuperClass(Handle<mirror::Class> klass) {
  CHECK(!klass->IsPrimitive());
  ObjPtr<mirror::Class> super = klass->GetSuperClass();
  ObjPtr<mirror::Class> object_class = GetClassRoot<mirror::Object>(this);
  if (klass.Get() == object_class) {
    if (super != nullptr) {
      ThrowClassFormatError(klass.Get(), "java.lang.Object must not have a superclass");
      return false;
    }
    return true;
  }
  if (super == nullptr) {
    ThrowLinkageError(klass.Get(), "No superclass defined for class %s",
                      klass->PrettyDescriptor().c_str());
    return false;
  }
  // Verify
  if (klass->IsInterface() && super != object_class) {
    ThrowClassFormatError(klass.Get(), "Interfaces must have java.lang.Object as superclass");
    return false;
  }
  if (super->IsFinal()) {
    ThrowVerifyError(klass.Get(),
                     "Superclass %s of %s is declared final",
                     super->PrettyDescriptor().c_str(),
                     klass->PrettyDescriptor().c_str());
    return false;
  }
  if (super->IsInterface()) {
    ThrowIncompatibleClassChangeError(klass.Get(),
                                      "Superclass %s of %s is an interface",
                                      super->PrettyDescriptor().c_str(),
                                      klass->PrettyDescriptor().c_str());
    return false;
  }
  if (!klass->CanAccess(super)) {
    ThrowIllegalAccessError(klass.Get(), "Superclass %s is inaccessible to class %s",
                            super->PrettyDescriptor().c_str(),
                            klass->PrettyDescriptor().c_str());
    return false;
  }
  if (!VerifyRecordClass(klass, super)) {
    DCHECK(Thread::Current()->IsExceptionPending());
    return false;
  }

  // Inherit kAccClassIsFinalizable from the superclass in case this
  // class doesn't override finalize.
  if (super->IsFinalizable()) {
    klass->SetFinalizable();
  }

  // Inherit class loader flag form super class.
  if (super->IsClassLoaderClass()) {
    klass->SetClassLoaderClass();
  }

  // Inherit reference flags (if any) from the superclass.
  uint32_t reference_flags = (super->GetClassFlags() & mirror::kClassFlagReference);
  if (reference_flags != 0) {
    CHECK_EQ(klass->GetClassFlags(), 0u);
    klass->SetClassFlags(klass->GetClassFlags() | reference_flags);
  }
  // Disallow custom direct subclasses of java.lang.ref.Reference.
  if (init_done_ && super == GetClassRoot<mirror::Reference>(this)) {
    ThrowLinkageError(klass.Get(),
                      "Class %s attempts to subclass java.lang.ref.Reference, which is not allowed",
                      klass->PrettyDescriptor().c_str());
    return false;
  }

  if (kIsDebugBuild) {
    // Ensure super classes are fully resolved prior to resolving fields..
    while (super != nullptr) {
      CHECK(super->IsResolved());
      super = super->GetSuperClass();
    }
  }
  return true;
}

// Comparator for name and signature of a method, used in finding overriding methods. Implementation
// avoids the use of handles, if it didn't then rather than compare dex files we could compare dex
// caches in the implementation below.
class MethodNameAndSignatureComparator final : public ValueObject {
 public:
  explicit MethodNameAndSignatureComparator(ArtMethod* method)
      REQUIRES_SHARED(Locks::mutator_lock_) :
      dex_file_(method->GetDexFile()), mid_(&dex_file_->GetMethodId(method->GetDexMethodIndex())),
      name_view_() {
    DCHECK(!method->IsProxyMethod()) << method->PrettyMethod();
  }

  ALWAYS_INLINE std::string_view GetNameView() {
    if (name_view_.empty()) {
      name_view_ = dex_file_->StringViewByIdx(mid_->name_idx_);
    }
    return name_view_;
  }

  bool HasSameNameAndSignature(ArtMethod* other)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(!other->IsProxyMethod()) << other->PrettyMethod();
    const DexFile* other_dex_file = other->GetDexFile();
    const dex::MethodId& other_mid = other_dex_file->GetMethodId(other->GetDexMethodIndex());
    if (dex_file_ == other_dex_file) {
      return mid_->name_idx_ == other_mid.name_idx_ && mid_->proto_idx_ == other_mid.proto_idx_;
    }
    return GetNameView() == other_dex_file->StringViewByIdx(other_mid.name_idx_) &&
           dex_file_->GetMethodSignature(*mid_) == other_dex_file->GetMethodSignature(other_mid);
  }

 private:
  // Dex file for the method to compare against.
  const DexFile* const dex_file_;
  // MethodId for the method to compare against.
  const dex::MethodId* const mid_;
  // Lazily computed name from the dex file's strings.
  std::string_view name_view_;
};

static ObjPtr<mirror::Class> GetImtOwner(ObjPtr<mirror::Class> klass)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ImTable* imt = klass->GetImt(kRuntimePointerSize);
  DCHECK(imt != nullptr);
  while (klass->HasSuperClass()) {
    ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
    if (super_class->ShouldHaveImt() && imt != super_class->GetImt(kRuntimePointerSize)) {
      // IMT not shared with the super class, return the current class.
      return klass;
    }
    klass = super_class;
  }
  return nullptr;
}

ArtMethod* ClassLinker::AddMethodToConflictTable(ObjPtr<mirror::Class> klass,
                                                 ArtMethod* conflict_method,
                                                 ArtMethod* interface_method,
                                                 ArtMethod* method) {
  ImtConflictTable* current_table = conflict_method->GetImtConflictTable(kRuntimePointerSize);
  Runtime* const runtime = Runtime::Current();

  // The IMT may be shared with a super class, in which case we need to use that
  // super class's `LinearAlloc`. The conflict itself should be limited to
  // methods at or higher up the chain of the IMT owner, otherwise class
  // linker would have created a different IMT.
  ObjPtr<mirror::Class> imt_owner = GetImtOwner(klass);
  DCHECK(imt_owner != nullptr);

  LinearAlloc* linear_alloc = GetAllocatorForClassLoader(imt_owner->GetClassLoader());

  // Create a new entry if the existing one is the shared conflict method.
  ArtMethod* new_conflict_method = (conflict_method == runtime->GetImtConflictMethod())
      ? runtime->CreateImtConflictMethod(linear_alloc)
      : conflict_method;

  // Allocate a new table. Note that we will leak this table at the next conflict,
  // but that's a tradeoff compared to making the table fixed size.
  void* data = linear_alloc->Alloc(
      Thread::Current(),
      ImtConflictTable::ComputeSizeWithOneMoreEntry(current_table, image_pointer_size_),
      LinearAllocKind::kNoGCRoots);
  if (data == nullptr) {
    LOG(ERROR) << "Failed to allocate conflict table";
    return conflict_method;
  }
  ImtConflictTable* new_table = new (data) ImtConflictTable(current_table,
                                                            interface_method,
                                                            method,
                                                            image_pointer_size_);

  // Do a fence to ensure threads see the data in the table before it is assigned
  // to the conflict method.
  // Note that there is a race in the presence of multiple threads and we may leak
  // memory from the LinearAlloc, but that's a tradeoff compared to using
  // atomic operations.
  std::atomic_thread_fence(std::memory_order_release);
  new_conflict_method->SetImtConflictTable(new_table, image_pointer_size_);
  return new_conflict_method;
}

void ClassLinker::SetIMTRef(ArtMethod* unimplemented_method,
                            ArtMethod* imt_conflict_method,
                            ArtMethod* current_method,
                            /*out*/bool* new_conflict,
                            /*out*/ArtMethod** imt_ref) {
  // Place method in imt if entry is empty, place conflict otherwise.
  if (*imt_ref == unimplemented_method) {
    *imt_ref = current_method;
  } else if (!(*imt_ref)->IsRuntimeMethod()) {
    // If we are not a conflict and we have the same signature and name as the imt
    // entry, it must be that we overwrote a superclass vtable entry.
    // Note that we have checked IsRuntimeMethod, as there may be multiple different
    // conflict methods.
    MethodNameAndSignatureComparator imt_comparator(
        (*imt_ref)->GetInterfaceMethodIfProxy(image_pointer_size_));
    if (imt_comparator.HasSameNameAndSignature(
          current_method->GetInterfaceMethodIfProxy(image_pointer_size_))) {
      *imt_ref = current_method;
    } else {
      *imt_ref = imt_conflict_method;
      *new_conflict = true;
    }
  } else {
    // Place the default conflict method. Note that there may be an existing conflict
    // method in the IMT, but it could be one tailored to the super class, with a
    // specific ImtConflictTable.
    *imt_ref = imt_conflict_method;
    *new_conflict = true;
  }
}

void ClassLinker::FillIMTAndConflictTables(ObjPtr<mirror::Class> klass) {
  DCHECK(klass->ShouldHaveImt()) << klass->PrettyClass();
  DCHECK(!klass->IsTemp()) << klass->PrettyClass();
  ArtMethod* imt_data[ImTable::kSize];
  Runtime* const runtime = Runtime::Current();
  ArtMethod* const unimplemented_method = runtime->GetImtUnimplementedMethod();
  ArtMethod* const conflict_method = runtime->GetImtConflictMethod();
  std::fill_n(imt_data, arraysize(imt_data), unimplemented_method);
  if (klass->GetIfTable() != nullptr) {
    bool new_conflict = false;
    FillIMTFromIfTable(klass->GetIfTable(),
                       unimplemented_method,
                       conflict_method,
                       klass,
                       /*create_conflict_tables=*/true,
                       /*ignore_copied_methods=*/false,
                       &new_conflict,
                       &imt_data[0]);
  }
  // Compare the IMT with the super class including the conflict methods. If they are equivalent,
  // we can just use the same pointer.
  ImTable* imt = nullptr;
  ImTable* super_imt = klass->FindSuperImt(image_pointer_size_);
  if (super_imt != nullptr) {
    bool same = true;
    for (size_t i = 0; same && i < ImTable::kSize; ++i) {
      ArtMethod* method = imt_data[i];
      ArtMethod* super_method = super_imt->Get(i, image_pointer_size_);
      if (method != super_method) {
        bool is_conflict_table = method->IsRuntimeMethod() &&
                                 method != unimplemented_method &&
                                 method != conflict_method;
        // Verify conflict contents.
        bool super_conflict_table = super_method->IsRuntimeMethod() &&
                                    super_method != unimplemented_method &&
                                    super_method != conflict_method;
        if (!is_conflict_table || !super_conflict_table) {
          same = false;
        } else {
          ImtConflictTable* table1 = method->GetImtConflictTable(image_pointer_size_);
          ImtConflictTable* table2 = super_method->GetImtConflictTable(image_pointer_size_);
          same = same && table1->Equals(table2, image_pointer_size_);
        }
      }
    }
    if (same) {
      imt = super_imt;
    }
  }
  if (imt == nullptr) {
    imt = klass->GetImt(image_pointer_size_);
    DCHECK(imt != nullptr);
    DCHECK_NE(imt, super_imt);
    imt->Populate(imt_data, image_pointer_size_);
  } else {
    klass->SetImt(imt, image_pointer_size_);
  }
}

ImtConflictTable* ClassLinker::CreateImtConflictTable(size_t count,
                                                      LinearAlloc* linear_alloc,
                                                      PointerSize image_pointer_size) {
  void* data = linear_alloc->Alloc(Thread::Current(),
                                   ImtConflictTable::ComputeSize(count, image_pointer_size),
                                   LinearAllocKind::kNoGCRoots);
  return (data != nullptr) ? new (data) ImtConflictTable(count, image_pointer_size) : nullptr;
}

ImtConflictTable* ClassLinker::CreateImtConflictTable(size_t count, LinearAlloc* linear_alloc) {
  return CreateImtConflictTable(count, linear_alloc, image_pointer_size_);
}

void ClassLinker::FillIMTFromIfTable(ObjPtr<mirror::IfTable> if_table,
                                     ArtMethod* unimplemented_method,
                                     ArtMethod* imt_conflict_method,
                                     ObjPtr<mirror::Class> klass,
                                     bool create_conflict_tables,
                                     bool ignore_copied_methods,
                                     /*out*/bool* new_conflict,
                                     /*out*/ArtMethod** imt) {
  uint32_t conflict_counts[ImTable::kSize] = {};
  for (size_t i = 0, length = if_table->Count(); i < length; ++i) {
    ObjPtr<mirror::Class> interface = if_table->GetInterface(i);
    const size_t num_virtuals = interface->NumVirtualMethods();
    const size_t method_array_count = if_table->GetMethodArrayCount(i);
    // Virtual methods can be larger than the if table methods if there are default methods.
    DCHECK_GE(num_virtuals, method_array_count);
    if (kIsDebugBuild) {
      if (klass->IsInterface()) {
        DCHECK_EQ(method_array_count, 0u);
      } else {
        DCHECK_EQ(interface->NumDeclaredVirtualMethods(), method_array_count);
      }
    }
    if (method_array_count == 0) {
      continue;
    }
    ObjPtr<mirror::PointerArray> method_array = if_table->GetMethodArray(i);
    for (size_t j = 0; j < method_array_count; ++j) {
      ArtMethod* implementation_method =
          method_array->GetElementPtrSize<ArtMethod*>(j, image_pointer_size_);
      if (ignore_copied_methods && implementation_method->IsCopied()) {
        continue;
      }
      DCHECK(implementation_method != nullptr);
      // Miranda methods cannot be used to implement an interface method, but they are safe to put
      // in the IMT since their entrypoint is the interface trampoline. If we put any copied methods
      // or interface methods in the IMT here they will not create extra conflicts since we compare
      // names and signatures in SetIMTRef.
      ArtMethod* interface_method = interface->GetVirtualMethod(j, image_pointer_size_);
      const uint32_t imt_index = interface_method->GetImtIndex();

      // There is only any conflicts if all of the interface methods for an IMT slot don't have
      // the same implementation method, keep track of this to avoid creating a conflict table in
      // this case.

      // Conflict table size for each IMT slot.
      ++conflict_counts[imt_index];

      SetIMTRef(unimplemented_method,
                imt_conflict_method,
                implementation_method,
                /*out*/new_conflict,
                /*out*/&imt[imt_index]);
    }
  }

  if (create_conflict_tables) {
    // Create the conflict tables.
    LinearAlloc* linear_alloc = GetAllocatorForClassLoader(klass->GetClassLoader());
    for (size_t i = 0; i < ImTable::kSize; ++i) {
      size_t conflicts = conflict_counts[i];
      if (imt[i] == imt_conflict_method) {
        ImtConflictTable* new_table = CreateImtConflictTable(conflicts, linear_alloc);
        if (new_table != nullptr) {
          ArtMethod* new_conflict_method =
              Runtime::Current()->CreateImtConflictMethod(linear_alloc);
          new_conflict_method->SetImtConflictTable(new_table, image_pointer_size_);
          imt[i] = new_conflict_method;
        } else {
          LOG(ERROR) << "Failed to allocate conflict table";
          imt[i] = imt_conflict_method;
        }
      } else {
        DCHECK_NE(imt[i], imt_conflict_method);
      }
    }

    for (size_t i = 0, length = if_table->Count(); i < length; ++i) {
      ObjPtr<mirror::Class> interface = if_table->GetInterface(i);
      const size_t method_array_count = if_table->GetMethodArrayCount(i);
      // Virtual methods can be larger than the if table methods if there are default methods.
      if (method_array_count == 0) {
        continue;
      }
      ObjPtr<mirror::PointerArray> method_array = if_table->GetMethodArray(i);
      for (size_t j = 0; j < method_array_count; ++j) {
        ArtMethod* implementation_method =
            method_array->GetElementPtrSize<ArtMethod*>(j, image_pointer_size_);
        if (ignore_copied_methods && implementation_method->IsCopied()) {
          continue;
        }
        DCHECK(implementation_method != nullptr);
        ArtMethod* interface_method = interface->GetVirtualMethod(j, image_pointer_size_);
        const uint32_t imt_index = interface_method->GetImtIndex();
        if (!imt[imt_index]->IsRuntimeMethod() ||
            imt[imt_index] == unimplemented_method ||
            imt[imt_index] == imt_conflict_method) {
          continue;
        }
        ImtConflictTable* table = imt[imt_index]->GetImtConflictTable(image_pointer_size_);
        const size_t num_entries = table->NumEntries(image_pointer_size_);
        table->SetInterfaceMethod(num_entries, image_pointer_size_, interface_method);
        table->SetImplementationMethod(num_entries, image_pointer_size_, implementation_method);
      }
    }
  }
}

namespace {

// Simple helper function that checks that no subtypes of 'val' are contained within the 'classes'
// set.
static bool NotSubinterfaceOfAny(
    const ScopedArenaHashSet<mirror::Class*>& classes,
    ObjPtr<mirror::Class> val)
    REQUIRES(Roles::uninterruptible_)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(val != nullptr);
  for (ObjPtr<mirror::Class> c : classes) {
    if (val->IsAssignableFrom(c)) {
      return false;
    }
  }
  return true;
}

// We record new interfaces by the index of the direct interface and the index in the
// direct interface's `IfTable`, or `dex::kDexNoIndex` if it's the direct interface itself.
struct NewInterfaceReference {
  uint32_t direct_interface_index;
  uint32_t direct_interface_iftable_index;
};

class ProxyInterfacesAccessor {
 public:
  explicit ProxyInterfacesAccessor(Handle<mirror::ObjectArray<mirror::Class>> interfaces)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : interfaces_(interfaces) {}

  size_t GetLength() REQUIRES_SHARED(Locks::mutator_lock_) {
    return interfaces_->GetLength();
  }

  ObjPtr<mirror::Class> GetInterface(size_t index) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LT(index, GetLength());
    return interfaces_->GetWithoutChecks(index);
  }

 private:
  Handle<mirror::ObjectArray<mirror::Class>> interfaces_;
};

class NonProxyInterfacesAccessor {
 public:
  NonProxyInterfacesAccessor(ClassLinker* class_linker, Handle<mirror::Class> klass)
      REQUIRES_SHARED(Locks::mutator_lock_)
      : interfaces_(klass->GetInterfaceTypeList()),
        class_linker_(class_linker),
        klass_(klass) {
    DCHECK(!klass->IsProxyClass());
  }

  size_t GetLength() REQUIRES_SHARED(Locks::mutator_lock_) {
    return (interfaces_ != nullptr) ? interfaces_->Size() : 0u;
  }

  ObjPtr<mirror::Class> GetInterface(size_t index) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LT(index, GetLength());
    dex::TypeIndex type_index = interfaces_->GetTypeItem(index).type_idx_;
    return class_linker_->LookupResolvedType(type_index, klass_.Get());
  }

 private:
  const dex::TypeList* interfaces_;
  ClassLinker* class_linker_;
  Handle<mirror::Class> klass_;
};

// Finds new interfaces to add to the interface table in addition to superclass interfaces.
//
// Interfaces in the interface table must satisfy the following constraint:
//     all I, J: Interface | I <: J implies J precedes I
// (note A <: B means that A is a subtype of B). We order this backwards so that we do not need
// to reorder superclass interfaces when new interfaces are added in subclass's interface tables.
//
// This function returns a list of references for all interfaces in the transitive
// closure of the direct interfaces that are not in the superclass interfaces.
// The entries in the list are ordered to satisfy the interface table ordering
// constraint and therefore the interface table formed by appending them to the
// superclass interface table shall also satisfy that constraint.
template <typename InterfaceAccessor>
ALWAYS_INLINE
static ArrayRef<const NewInterfaceReference> FindNewIfTableInterfaces(
    ObjPtr<mirror::IfTable> super_iftable,
    size_t super_ifcount,
    ScopedArenaAllocator* allocator,
    InterfaceAccessor&& interfaces,
    ArrayRef<NewInterfaceReference> initial_storage,
    /*out*/ScopedArenaVector<NewInterfaceReference>* supplemental_storage)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension nts(__FUNCTION__);

  // This is the set of all classes already in the iftable. Used to make checking
  // if a class has already been added quicker.
  constexpr size_t kBufferSize = 32;  // 256 bytes on 64-bit architectures.
  mirror::Class* buffer[kBufferSize];
  ScopedArenaHashSet<mirror::Class*> classes_in_iftable(buffer, kBufferSize, allocator->Adapter());
  // The first super_ifcount elements are from the superclass. We note that they are already added.
  for (size_t i = 0; i < super_ifcount; i++) {
    ObjPtr<mirror::Class> iface = super_iftable->GetInterface(i);
    DCHECK(NotSubinterfaceOfAny(classes_in_iftable, iface)) << "Bad ordering.";
    classes_in_iftable.Put(iface.Ptr());
  }

  ArrayRef<NewInterfaceReference> current_storage = initial_storage;
  DCHECK_NE(current_storage.size(), 0u);
  size_t num_new_interfaces = 0u;
  auto insert_reference = [&](uint32_t direct_interface_index,
                              uint32_t direct_interface_iface_index) {
    if (UNLIKELY(num_new_interfaces == current_storage.size())) {
      bool copy = current_storage.data() != supplemental_storage->data();
      supplemental_storage->resize(2u * num_new_interfaces);
      if (copy) {
        std::copy_n(current_storage.data(), num_new_interfaces, supplemental_storage->data());
      }
      current_storage = ArrayRef<NewInterfaceReference>(*supplemental_storage);
    }
    current_storage[num_new_interfaces] = {direct_interface_index, direct_interface_iface_index};
    ++num_new_interfaces;
  };

  for (size_t i = 0, num_interfaces = interfaces.GetLength(); i != num_interfaces; ++i) {
    ObjPtr<mirror::Class> interface = interfaces.GetInterface(i);

    // Let us call the first filled_ifcount elements of iftable the current-iface-list.
    // At this point in the loop current-iface-list has the invariant that:
    //    for every pair of interfaces I,J within it:
    //      if index_of(I) < index_of(J) then I is not a subtype of J

    // If we have already seen this element then all of its super-interfaces must already be in the
    // current-iface-list so we can skip adding it.
    if (classes_in_iftable.find(interface.Ptr()) == classes_in_iftable.end()) {
      // We haven't seen this interface so add all of its super-interfaces onto the
      // current-iface-list, skipping those already on it.
      int32_t ifcount = interface->GetIfTableCount();
      for (int32_t j = 0; j < ifcount; j++) {
        ObjPtr<mirror::Class> super_interface = interface->GetIfTable()->GetInterface(j);
        if (classes_in_iftable.find(super_interface.Ptr()) == classes_in_iftable.end()) {
          DCHECK(NotSubinterfaceOfAny(classes_in_iftable, super_interface)) << "Bad ordering.";
          classes_in_iftable.Put(super_interface.Ptr());
          insert_reference(i, j);
        }
      }
      // Add this interface reference after all of its super-interfaces.
      DCHECK(NotSubinterfaceOfAny(classes_in_iftable, interface)) << "Bad ordering";
      classes_in_iftable.Put(interface.Ptr());
      insert_reference(i, dex::kDexNoIndex);
    } else if (kIsDebugBuild) {
      // Check all super-interfaces are already in the list.
      int32_t ifcount = interface->GetIfTableCount();
      for (int32_t j = 0; j < ifcount; j++) {
        ObjPtr<mirror::Class> super_interface = interface->GetIfTable()->GetInterface(j);
        DCHECK(classes_in_iftable.find(super_interface.Ptr()) != classes_in_iftable.end())
            << "Iftable does not contain " << mirror::Class::PrettyClass(super_interface)
            << ", a superinterface of " << interface->PrettyClass();
      }
    }
  }
  return ArrayRef<const NewInterfaceReference>(current_storage.data(), num_new_interfaces);
}

template <typename InterfaceAccessor>
static ObjPtr<mirror::IfTable> SetupInterfaceLookupTable(
    Thread* self,
    Handle<mirror::Class> klass,
    ScopedArenaAllocator* allocator,
    InterfaceAccessor&& interfaces)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(klass->HasSuperClass());
  ObjPtr<mirror::IfTable> super_iftable = klass->GetSuperClass()->GetIfTable();
  DCHECK(super_iftable != nullptr);
  const size_t num_interfaces = interfaces.GetLength();

  // If there are no new interfaces, return the interface table from superclass.
  // If any implementation methods are overridden, we shall copy the table and
  // the method arrays that contain any differences (copy-on-write).
  if (num_interfaces == 0) {
    return super_iftable;
  }

  // Check that every class being implemented is an interface.
  for (size_t i = 0; i != num_interfaces; ++i) {
    ObjPtr<mirror::Class> interface = interfaces.GetInterface(i);
    DCHECK(interface != nullptr);
    if (UNLIKELY(!interface->IsInterface())) {
      ThrowIncompatibleClassChangeError(klass.Get(),
                                        "Class %s implements non-interface class %s",
                                        klass->PrettyDescriptor().c_str(),
                                        interface->PrettyDescriptor().c_str());
      return nullptr;
    }
  }

  static constexpr size_t kMaxStackReferences = 16;
  NewInterfaceReference initial_storage[kMaxStackReferences];
  ScopedArenaVector<NewInterfaceReference> supplemental_storage(allocator->Adapter());
  const size_t super_ifcount = super_iftable->Count();
  ArrayRef<const NewInterfaceReference> new_interface_references =
      FindNewIfTableInterfaces(
          super_iftable,
          super_ifcount,
          allocator,
          interfaces,
          ArrayRef<NewInterfaceReference>(initial_storage),
          &supplemental_storage);

  // If all declared interfaces were already present in superclass interface table,
  // return the interface table from superclass. See above.
  if (UNLIKELY(new_interface_references.empty())) {
    return super_iftable;
  }

  // Create the interface table.
  size_t ifcount = super_ifcount + new_interface_references.size();
  ObjPtr<mirror::IfTable> iftable = AllocIfTable(self, ifcount, super_iftable->GetClass());
  if (UNLIKELY(iftable == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  // Fill in table with superclass's iftable.
  if (super_ifcount != 0) {
    // Reload `super_iftable` as it may have been clobbered by the allocation.
    super_iftable = klass->GetSuperClass()->GetIfTable();
    for (size_t i = 0; i != super_ifcount; i++) {
      ObjPtr<mirror::Class> super_interface = super_iftable->GetInterface(i);
      DCHECK(super_interface != nullptr);
      iftable->SetInterface(i, super_interface);
      ObjPtr<mirror::PointerArray> method_array = super_iftable->GetMethodArrayOrNull(i);
      if (method_array != nullptr) {
        iftable->SetMethodArray(i, method_array);
      }
    }
  }
  // Fill in the table with additional interfaces.
  size_t current_index = super_ifcount;
  for (NewInterfaceReference ref : new_interface_references) {
    ObjPtr<mirror::Class> direct_interface = interfaces.GetInterface(ref.direct_interface_index);
    ObjPtr<mirror::Class> new_interface = (ref.direct_interface_iftable_index != dex::kDexNoIndex)
        ? direct_interface->GetIfTable()->GetInterface(ref.direct_interface_iftable_index)
        : direct_interface;
    iftable->SetInterface(current_index, new_interface);
    ++current_index;
  }
  DCHECK_EQ(current_index, ifcount);

  if (kIsDebugBuild) {
    // Check that the iftable is ordered correctly.
    for (size_t i = 0; i < ifcount; i++) {
      ObjPtr<mirror::Class> if_a = iftable->GetInterface(i);
      for (size_t j = i + 1; j < ifcount; j++) {
        ObjPtr<mirror::Class> if_b = iftable->GetInterface(j);
        // !(if_a <: if_b)
        CHECK(!if_b->IsAssignableFrom(if_a))
            << "Bad interface order: " << mirror::Class::PrettyClass(if_a) << " (index " << i
            << ") extends "
            << if_b->PrettyClass() << " (index " << j << ") and so should be after it in the "
            << "interface list.";
      }
    }
  }

  return iftable;
}

// Check that all vtable entries are present in this class's virtuals or are the same as a
// superclasses vtable entry.
void CheckClassOwnsVTableEntries(Thread* self,
                                 Handle<mirror::Class> klass,
                                 PointerSize pointer_size)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<2> hs(self);
  Handle<mirror::PointerArray> check_vtable(hs.NewHandle(klass->GetVTableDuringLinking()));
  ObjPtr<mirror::Class> super_temp = (klass->HasSuperClass()) ? klass->GetSuperClass() : nullptr;
  Handle<mirror::Class> superclass(hs.NewHandle(super_temp));
  int32_t super_vtable_length = (superclass != nullptr) ? superclass->GetVTableLength() : 0;
  for (int32_t i = 0; i < check_vtable->GetLength(); ++i) {
    ArtMethod* m = check_vtable->GetElementPtrSize<ArtMethod*>(i, pointer_size);
    CHECK(m != nullptr);

    if (m->GetMethodIndexDuringLinking() != i) {
      LOG(WARNING) << m->PrettyMethod()
                   << " has an unexpected method index for its spot in the vtable for class"
                   << klass->PrettyClass();
    }
    ArraySlice<ArtMethod> virtuals = klass->GetVirtualMethodsSliceUnchecked(pointer_size);
    auto is_same_method = [m] (const ArtMethod& meth) {
      return &meth == m;
    };
    if (!((super_vtable_length > i && superclass->GetVTableEntry(i, pointer_size) == m) ||
          std::find_if(virtuals.begin(), virtuals.end(), is_same_method) != virtuals.end())) {
      LOG(WARNING) << m->PrettyMethod() << " does not seem to be owned by current class "
                   << klass->PrettyClass() << " or any of its superclasses!";
    }
  }
}

// Check to make sure the vtable does not have duplicates. Duplicates could cause problems when a
// method is overridden in a subclass.
template <PointerSize kPointerSize>
void CheckVTableHasNoDuplicates(Thread* self, Handle<mirror::Class> klass)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1> hs(self);
  Handle<mirror::PointerArray> vtable(hs.NewHandle(klass->GetVTableDuringLinking()));
  int32_t num_entries = vtable->GetLength();

  // Observations:
  //   * The older implementation was O(n^2) and got too expensive for apps with larger classes.
  //   * Many classes do not override Object functions (e.g., equals/hashCode/toString). Thus,
  //     for many classes outside of libcore a cross-dexfile check has to be run anyways.
  //   * In the cross-dexfile case, with the O(n^2), in the best case O(n) cross checks would have
  //     to be done. It is thus OK in a single-pass algorithm to read all data, anyways.
  //   * The single-pass algorithm will trade memory for speed, but that is OK.

  CHECK_GT(num_entries, 0);

  auto log_fn = [&vtable, &klass](int32_t i, int32_t j) REQUIRES_SHARED(Locks::mutator_lock_) {
    ArtMethod* m1 = vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(i);
    ArtMethod* m2 = vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(j);
    LOG(WARNING) << "vtable entries " << i << " and " << j << " are identical for "
                 << klass->PrettyClass() << " in method " << m1->PrettyMethod()
                << " (0x" << std::hex << reinterpret_cast<uintptr_t>(m2) << ") and "
                << m2->PrettyMethod() << "  (0x" << std::hex
                << reinterpret_cast<uintptr_t>(m2) << ")";
  };
  struct BaseHashType {
    static size_t HashCombine(size_t seed, size_t val) {
      return seed ^ (val + 0x9e3779b9 + (seed << 6) + (seed >> 2));
    }
  };

  // Check assuming all entries come from the same dex file.
  {
    // Find the first interesting method and its dex file.
    int32_t start = 0;
    for (; start < num_entries; ++start) {
      ArtMethod* vtable_entry = vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(start);
      // Don't bother if we cannot 'see' the vtable entry (i.e. it is a package-private member
      // maybe).
      if (!klass->CanAccessMember(vtable_entry->GetDeclaringClass(),
                                  vtable_entry->GetAccessFlags())) {
        continue;
      }
      break;
    }
    if (start == num_entries) {
      return;
    }
    const DexFile* dex_file =
        vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(start)->
            GetInterfaceMethodIfProxy(kPointerSize)->GetDexFile();

    // Helper function to avoid logging if we have to run the cross-file checks.
    auto check_fn = [&](bool log_warn) REQUIRES_SHARED(Locks::mutator_lock_) {
      // Use a map to store seen entries, as the storage space is too large for a bitvector.
      using PairType = std::pair<uint32_t, uint16_t>;
      struct PairHash : BaseHashType {
        size_t operator()(const PairType& key) const {
          return BaseHashType::HashCombine(BaseHashType::HashCombine(0, key.first), key.second);
        }
      };
      HashMap<PairType, int32_t, DefaultMapEmptyFn<PairType, int32_t>, PairHash> seen;
      seen.reserve(2 * num_entries);
      bool need_slow_path = false;
      bool found_dup = false;
      for (int i = start; i < num_entries; ++i) {
        // Can use Unchecked here as the start loop already ensured that the arrays are correct
        // wrt/ kPointerSize.
        ArtMethod* vtable_entry = vtable->GetElementPtrSizeUnchecked<ArtMethod*, kPointerSize>(i);
        if (!klass->CanAccessMember(vtable_entry->GetDeclaringClass(),
                                    vtable_entry->GetAccessFlags())) {
          continue;
        }
        ArtMethod* m = vtable_entry->GetInterfaceMethodIfProxy(kPointerSize);
        if (dex_file != m->GetDexFile()) {
          need_slow_path = true;
          break;
        }
        const dex::MethodId* m_mid = &dex_file->GetMethodId(m->GetDexMethodIndex());
        PairType pair = std::make_pair(m_mid->name_idx_.index_, m_mid->proto_idx_.index_);
        auto it = seen.find(pair);
        if (it != seen.end()) {
          found_dup = true;
          if (log_warn) {
            log_fn(it->second, i);
          }
        } else {
          seen.insert(std::make_pair(pair, i));
        }
      }
      return std::make_pair(need_slow_path, found_dup);
    };
    std::pair<bool, bool> result = check_fn(/* log_warn= */ false);
    if (!result.first) {
      if (result.second) {
        check_fn(/* log_warn= */ true);
      }
      return;
    }
  }

  // Need to check across dex files.
  struct Entry {
    size_t cached_hash = 0;
    uint32_t name_len = 0;
    const char* name = nullptr;
    Signature signature = Signature::NoSignature();

    Entry() = default;
    Entry(const Entry& other) = default;
    Entry& operator=(const Entry& other) = default;

    Entry(const DexFile* dex_file, const dex::MethodId& mid)
        : name_len(0),  // Explicit to enforce ordering with -Werror,-Wreorder-ctor.
          // This call writes `name_len` and it is therefore necessary that the
          // initializer for `name_len` comes before it, otherwise the value
          // from the call would be overwritten by that initializer.
          name(dex_file->StringDataAndUtf16LengthByIdx(mid.name_idx_, &name_len)),
          signature(dex_file->GetMethodSignature(mid)) {
      // The `name_len` has been initialized to the UTF16 length. Calculate length in bytes.
      if (name[name_len] != 0) {
        name_len += strlen(name + name_len);
      }
    }

    bool operator==(const Entry& other) const {
      return name_len == other.name_len &&
             memcmp(name, other.name, name_len) == 0 &&
             signature == other.signature;
    }
  };
  struct EntryHash {
    size_t operator()(const Entry& key) const {
      return key.cached_hash;
    }
  };
  HashMap<Entry, int32_t, DefaultMapEmptyFn<Entry, int32_t>, EntryHash> map;
  for (int32_t i = 0; i < num_entries; ++i) {
    // Can use Unchecked here as the first loop already ensured that the arrays are correct
    // wrt/ kPointerSize.
    ArtMethod* vtable_entry = vtable->GetElementPtrSizeUnchecked<ArtMethod*, kPointerSize>(i);
    // Don't bother if we cannot 'see' the vtable entry (i.e. it is a package-private member
    // maybe).
    if (!klass->CanAccessMember(vtable_entry->GetDeclaringClass(),
                                vtable_entry->GetAccessFlags())) {
      continue;
    }
    ArtMethod* m = vtable_entry->GetInterfaceMethodIfProxy(kPointerSize);
    const DexFile* dex_file = m->GetDexFile();
    const dex::MethodId& mid = dex_file->GetMethodId(m->GetDexMethodIndex());

    Entry e(dex_file, mid);

    size_t string_hash = std::hash<std::string_view>()(std::string_view(e.name, e.name_len));
    size_t sig_hash = std::hash<std::string>()(e.signature.ToString());
    e.cached_hash = BaseHashType::HashCombine(BaseHashType::HashCombine(0u, string_hash),
                                              sig_hash);

    auto it = map.find(e);
    if (it != map.end()) {
      log_fn(it->second, i);
    } else {
      map.insert(std::make_pair(e, i));
    }
  }
}

void CheckVTableHasNoDuplicates(Thread* self,
                                Handle<mirror::Class> klass,
                                PointerSize pointer_size)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  switch (pointer_size) {
    case PointerSize::k64:
      CheckVTableHasNoDuplicates<PointerSize::k64>(self, klass);
      break;
    case PointerSize::k32:
      CheckVTableHasNoDuplicates<PointerSize::k32>(self, klass);
      break;
  }
}

static void CheckVTable(Thread* self, Handle<mirror::Class> klass, PointerSize pointer_size)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  CheckClassOwnsVTableEntries(self, klass, pointer_size);
  CheckVTableHasNoDuplicates(self, klass, pointer_size);
}

}  // namespace

template <PointerSize kPointerSize>
class ClassLinker::LinkMethodsHelper {
 public:
  LinkMethodsHelper(ClassLinker* class_linker,
                    Handle<mirror::Class> klass,
                    Thread* self,
                    Runtime* runtime)
      : class_linker_(class_linker),
        klass_(klass),
        self_(self),
        runtime_(runtime),
        stack_(runtime->GetArenaPool()),
        allocator_(&stack_),
        copied_method_records_(copied_method_records_initial_buffer_,
                               kCopiedMethodRecordInitialBufferSize,
                               allocator_.Adapter()),
        num_new_copied_methods_(0u) {
  }

  // Links the virtual and interface methods for the given class.
  //
  // Arguments:
  // * self - The current thread.
  // * klass - class, whose vtable will be filled in.
  // * interfaces - implemented interfaces for a proxy class, otherwise null.
  // * out_new_conflict - whether there is a new conflict compared to the superclass.
  // * out_imt - interface method table to fill.
  bool LinkMethods(
      Thread* self,
      Handle<mirror::Class> klass,
      Handle<mirror::ObjectArray<mirror::Class>> interfaces,
      bool* out_new_conflict,
      ArtMethod** out_imt)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  // Allocate a pointer array.
  static ObjPtr<mirror::PointerArray> AllocPointerArray(Thread* self, size_t length)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Allocate method arrays for interfaces.
  bool AllocateIfTableMethodArrays(Thread* self,
                                   Handle<mirror::Class> klass,
                                   Handle<mirror::IfTable> iftable)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Assign vtable indexes to declared virtual methods for a non-interface class other
  // than `java.lang.Object`. Returns the number of vtable entries on success, 0 on failure.
  // This function also assigns vtable indexes for interface methods in new interfaces
  // and records data for copied methods which shall be referenced by the vtable.
  size_t AssignVTableIndexes(ObjPtr<mirror::Class> klass,
                             ObjPtr<mirror::Class> super_class,
                             bool is_super_abstract,
                             size_t num_virtual_methods,
                             ObjPtr<mirror::IfTable> iftable)
      REQUIRES_SHARED(Locks::mutator_lock_);

  bool FindCopiedMethodsForInterface(ObjPtr<mirror::Class> klass,
                                     size_t num_virtual_methods,
                                     ObjPtr<mirror::IfTable> iftable)
      REQUIRES_SHARED(Locks::mutator_lock_);

  bool LinkJavaLangObjectMethods(Thread* self, Handle<mirror::Class> klass)
      REQUIRES_SHARED(Locks::mutator_lock_) COLD_ATTR;

  void ReallocMethods(ObjPtr<mirror::Class> klass) REQUIRES_SHARED(Locks::mutator_lock_);
  bool FinalizeIfTable(Handle<mirror::Class> klass,
                       MutableHandle<mirror::IfTable> iftable,
                       Handle<mirror::PointerArray> vtable,
                       bool is_klass_abstract,
                       bool is_super_abstract,
                       bool* out_new_conflict,
                       ArtMethod** out_imt)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void ClobberOldMethods(LengthPrefixedArray<ArtMethod>* old_methods,
                         LengthPrefixedArray<ArtMethod>* methods) {
    if (kIsDebugBuild && old_methods != nullptr) {
      CHECK(methods != nullptr);
      // Put some random garbage in old methods to help find stale pointers.
      if (methods != old_methods) {
        // Need to make sure the GC is not running since it could be scanning the methods we are
        // about to overwrite.
        ScopedThreadStateChange tsc(self_, ThreadState::kSuspended);
        gc::ScopedGCCriticalSection gcs(self_,
                                        gc::kGcCauseClassLinker,
                                        gc::kCollectorTypeClassLinker);
        const size_t old_size = LengthPrefixedArray<ArtMethod>::ComputeSize(old_methods->size(),
                                                                            kMethodSize,
                                                                            kMethodAlignment);
        memset(old_methods, 0xFEu, old_size);
        // Set size to 0 to avoid visiting declaring classes.
        if (gUseUserfaultfd) {
          old_methods->SetSize(0);
        }
      }
    }
  }

  NO_INLINE
  void LogNewVirtuals(LengthPrefixedArray<ArtMethod>* methods) const
      REQUIRES_SHARED(Locks::mutator_lock_) {
    ObjPtr<mirror::Class> klass = klass_.Get();
    size_t num_new_copied_methods = num_new_copied_methods_;
    size_t old_method_count = methods->size() - num_new_copied_methods;
    size_t super_vtable_length = klass->GetSuperClass()->GetVTableLength();
    size_t num_miranda_methods = 0u;
    size_t num_overriding_default_methods = 0u;
    size_t num_default_methods = 0u;
    size_t num_overriding_default_conflict_methods = 0u;
    size_t num_default_conflict_methods = 0u;
    for (size_t i = 0; i != num_new_copied_methods; ++i) {
      ArtMethod& m = methods->At(old_method_count + i, kMethodSize, kMethodAlignment);
      if (m.IsDefault()) {
        if (m.GetMethodIndexDuringLinking() < super_vtable_length) {
          ++num_overriding_default_methods;
        } else {
          ++num_default_methods;
        }
      } else if (m.IsDefaultConflicting()) {
        if (m.GetMethodIndexDuringLinking() < super_vtable_length) {
          ++num_overriding_default_conflict_methods;
        } else {
          ++num_default_conflict_methods;
        }
      } else {
        DCHECK(m.IsMiranda());
        ++num_miranda_methods;
      }
    }
    VLOG(class_linker) << klass->PrettyClass() << ": miranda_methods=" << num_miranda_methods
                       << " default_methods=" << num_default_methods
                       << " overriding_default_methods=" << num_overriding_default_methods
                       << " default_conflict_methods=" << num_default_conflict_methods
                       << " overriding_default_conflict_methods="
                       << num_overriding_default_conflict_methods;
  }

  class MethodIndexEmptyFn {
   public:
    void MakeEmpty(uint32_t& item) const {
      item = dex::kDexNoIndex;
    }
    bool IsEmpty(const uint32_t& item) const {
      return item == dex::kDexNoIndex;
    }
  };

  class VTableIndexCheckerDebug {
   protected:
    explicit VTableIndexCheckerDebug(size_t vtable_length)
        : vtable_length_(vtable_length) {}

    void CheckIndex(uint32_t index) const {
      CHECK_LT(index, vtable_length_);
    }

   private:
    uint32_t vtable_length_;
  };

  class VTableIndexCheckerRelease {
   protected:
    explicit VTableIndexCheckerRelease([[maybe_unused]] size_t vtable_length) {}
    void CheckIndex([[maybe_unused]] uint32_t index) const {}
  };

  using VTableIndexChecker =
      std::conditional_t<kIsDebugBuild, VTableIndexCheckerDebug, VTableIndexCheckerRelease>;

  class VTableAccessor : private VTableIndexChecker {
   public:
    VTableAccessor(uint8_t* raw_vtable, size_t vtable_length)
        REQUIRES_SHARED(Locks::mutator_lock_)
        : VTableIndexChecker(vtable_length),
          raw_vtable_(raw_vtable) {}

    ArtMethod* GetVTableEntry(uint32_t index) const REQUIRES_SHARED(Locks::mutator_lock_) {
      this->CheckIndex(index);
      uint8_t* entry = raw_vtable_ + static_cast<size_t>(kPointerSize) * index;
      if (kPointerSize == PointerSize::k64) {
        return reinterpret_cast64<ArtMethod*>(*reinterpret_cast<uint64_t*>(entry));
      } else {
        return reinterpret_cast32<ArtMethod*>(*reinterpret_cast<uint32_t*>(entry));
      }
    }

   private:
    uint8_t* raw_vtable_;
  };

  class VTableSignatureHash {
   public:
    explicit VTableSignatureHash(VTableAccessor accessor)
        REQUIRES_SHARED(Locks::mutator_lock_)
        : accessor_(accessor) {}

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(ArtMethod* method) const NO_THREAD_SAFETY_ANALYSIS {
      return ComputeMethodHash(method);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(uint32_t index) const NO_THREAD_SAFETY_ANALYSIS {
      return ComputeMethodHash(accessor_.GetVTableEntry(index));
    }

   private:
    VTableAccessor accessor_;
  };

  class VTableSignatureEqual {
   public:
    explicit VTableSignatureEqual(VTableAccessor accessor)
        REQUIRES_SHARED(Locks::mutator_lock_)
        : accessor_(accessor) {}

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(uint32_t lhs_index, ArtMethod* rhs) const NO_THREAD_SAFETY_ANALYSIS {
      return MethodSignatureEquals(accessor_.GetVTableEntry(lhs_index), rhs);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(uint32_t lhs_index, uint32_t rhs_index) const NO_THREAD_SAFETY_ANALYSIS {
      return (*this)(lhs_index, accessor_.GetVTableEntry(rhs_index));
    }

   private:
    VTableAccessor accessor_;
  };

  using VTableSignatureSet =
      ScopedArenaHashSet<uint32_t, MethodIndexEmptyFn, VTableSignatureHash, VTableSignatureEqual>;

  class DeclaredVirtualSignatureHash {
   public:
    explicit DeclaredVirtualSignatureHash(ObjPtr<mirror::Class> klass)
        REQUIRES_SHARED(Locks::mutator_lock_)
        : klass_(klass) {}

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(ArtMethod* method) const NO_THREAD_SAFETY_ANALYSIS {
      return ComputeMethodHash(method);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(uint32_t index) const NO_THREAD_SAFETY_ANALYSIS {
      DCHECK_LT(index, klass_->NumDeclaredVirtualMethods());
      ArtMethod* method = klass_->GetVirtualMethodDuringLinking(index, kPointerSize);
      return ComputeMethodHash(method->GetInterfaceMethodIfProxy(kPointerSize));
    }

   private:
    ObjPtr<mirror::Class> klass_;
  };

  class DeclaredVirtualSignatureEqual {
   public:
    explicit DeclaredVirtualSignatureEqual(ObjPtr<mirror::Class> klass)
        REQUIRES_SHARED(Locks::mutator_lock_)
        : klass_(klass) {}

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(uint32_t lhs_index, ArtMethod* rhs) const NO_THREAD_SAFETY_ANALYSIS {
      DCHECK_LT(lhs_index, klass_->NumDeclaredVirtualMethods());
      ArtMethod* lhs = klass_->GetVirtualMethodDuringLinking(lhs_index, kPointerSize);
      return MethodSignatureEquals(lhs->GetInterfaceMethodIfProxy(kPointerSize), rhs);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(uint32_t lhs_index, uint32_t rhs_index) const NO_THREAD_SAFETY_ANALYSIS {
      DCHECK_LT(lhs_index, klass_->NumDeclaredVirtualMethods());
      DCHECK_LT(rhs_index, klass_->NumDeclaredVirtualMethods());
      return lhs_index == rhs_index;
    }

   private:
    ObjPtr<mirror::Class> klass_;
  };

  using DeclaredVirtualSignatureSet = ScopedArenaHashSet<uint32_t,
                                                         MethodIndexEmptyFn,
                                                         DeclaredVirtualSignatureHash,
                                                         DeclaredVirtualSignatureEqual>;

  // Helper class to keep records for determining the correct copied method to create.
  class CopiedMethodRecord {
   public:
    enum class State : uint32_t {
      // Note: The `*Single` values are used when we know that there is only one interface
      // method with the given signature that's not masked; that method is the main method.
      // We use this knowledge for faster masking check, otherwise we need to search for
      // a masking method through methods of all interfaces that could potentially mask it.
      kAbstractSingle,
      kDefaultSingle,
      kAbstract,
      kDefault,
      kDefaultConflict,
      kUseSuperMethod,
    };

    CopiedMethodRecord()
        : main_method_(nullptr),
          method_index_(0u),
          state_(State::kAbstractSingle) {}

    CopiedMethodRecord(ArtMethod* main_method, size_t vtable_index)
        : main_method_(main_method),
          method_index_(vtable_index),
          state_(State::kAbstractSingle) {}

    // Set main method. The new main method must be more specific implementation.
    void SetMainMethod(ArtMethod* main_method) {
      DCHECK(main_method_ != nullptr);
      main_method_ = main_method;
    }

    // The main method is the first encountered default method if any,
    // otherwise the first encountered abstract method.
    ArtMethod* GetMainMethod() const {
      return main_method_;
    }

    void SetMethodIndex(size_t method_index) {
      DCHECK_NE(method_index, dex::kDexNoIndex);
      method_index_ = method_index;
    }

    size_t GetMethodIndex() const {
      DCHECK_NE(method_index_, dex::kDexNoIndex);
      return method_index_;
    }

    void SetState(State state) {
      state_ = state;
    }

    State GetState() const {
      return state_;
    }

    ALWAYS_INLINE
    void UpdateStateForInterface(ObjPtr<mirror::Class> iface,
                                 ArtMethod* interface_method,
                                 ObjPtr<mirror::IfTable> iftable,
                                 size_t ifcount,
                                 size_t index)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      DCHECK_EQ(ifcount, iftable->Count());
      DCHECK_LT(index, ifcount);
      DCHECK(iface == interface_method->GetDeclaringClass());
      DCHECK(iface == iftable->GetInterface(index));
      DCHECK(interface_method->IsDefault());
      if (GetState() != State::kDefaultConflict) {
        DCHECK(GetState() == State::kDefault);
        // We do not record all overriding methods, so we need to walk over all
        // interfaces that could mask the `interface_method`.
        if (ContainsOverridingMethodOf(iftable, index + 1, ifcount, iface, interface_method)) {
          return;  // Found an overriding method that masks `interface_method`.
        }
        // We have a new default method that's not masked by any other method.
        SetState(State::kDefaultConflict);
      }
    }

    ALWAYS_INLINE
    void UpdateState(ObjPtr<mirror::Class> iface,
                     ArtMethod* interface_method,
                     size_t vtable_index,
                     ObjPtr<mirror::IfTable> iftable,
                     size_t ifcount,
                     size_t index)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      DCHECK_EQ(ifcount, iftable->Count());
      DCHECK_LT(index, ifcount);
      if (kIsDebugBuild) {
        if (interface_method->IsCopied()) {
          // Called from `FinalizeState()` for a default method from superclass.
          // The `index` points to the last interface inherited from the superclass
          // as we need to search only the new interfaces for masking methods.
          DCHECK(interface_method->IsDefault());
        } else {
          DCHECK(iface == interface_method->GetDeclaringClass());
          DCHECK(iface == iftable->GetInterface(index));
        }
      }
      DCHECK_EQ(vtable_index, method_index_);
      auto slow_is_masked = [=]() REQUIRES_SHARED(Locks::mutator_lock_) {
        return ContainsImplementingMethod(iftable, index + 1, ifcount, iface, vtable_index);
      };
      UpdateStateImpl(iface, interface_method, slow_is_masked);
    }

    ALWAYS_INLINE
    void FinalizeState(ArtMethod* super_method,
                       size_t vtable_index,
                       ObjPtr<mirror::IfTable> iftable,
                       size_t ifcount,
                       ObjPtr<mirror::IfTable> super_iftable,
                       size_t super_ifcount)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      DCHECK(super_method->IsCopied());
      DCHECK_EQ(vtable_index, method_index_);
      DCHECK_EQ(vtable_index, super_method->GetMethodIndex());
      DCHECK_NE(super_ifcount, 0u);
      if (super_method->IsDefault()) {
        if (UNLIKELY(super_method->IsDefaultConflicting())) {
          // Some of the default methods that contributed to the conflict in the superclass
          // may be masked by new interfaces. Walk over all the interfaces and update state
          // as long as the current state is not `kDefaultConflict`.
          size_t i = super_ifcount;
          while (GetState() != State::kDefaultConflict && i != 0u) {
            --i;
            ObjPtr<mirror::Class> iface = iftable->GetInterface(i);
            DCHECK(iface == super_iftable->GetInterface(i));
            auto [found, index] =
                MethodArrayContains(super_iftable->GetMethodArrayOrNull(i), super_method);
            if (found) {
              ArtMethod* interface_method = iface->GetVirtualMethod(index, kPointerSize);
              auto slow_is_masked = [=]() REQUIRES_SHARED(Locks::mutator_lock_) {
                // Note: The `iftable` has method arrays in range [super_ifcount, ifcount) filled
                // with vtable indexes but the range [0, super_ifcount) is empty, so we need to
                // use the `super_iftable` filled with implementation methods for that range.
                return ContainsImplementingMethod(
                           super_iftable, i + 1u, super_ifcount, iface, super_method) ||
                       ContainsImplementingMethod(
                           iftable, super_ifcount, ifcount, iface, vtable_index);
              };
              UpdateStateImpl(iface, interface_method, slow_is_masked);
            }
          }
          if (GetState() == State::kDefaultConflict) {
            SetState(State::kUseSuperMethod);
          }
        } else {
          // There was exactly one default method in superclass interfaces that was
          // not masked by subinterfaces. Use `UpdateState()` to process it and pass
          // `super_ifcount - 1` as index for checking if it's been masked by new interfaces.
          ObjPtr<mirror::Class> iface = super_method->GetDeclaringClass();
          UpdateState(
              iface, super_method, vtable_index, iftable, ifcount, /*index=*/ super_ifcount - 1u);
          if (GetMainMethod() == super_method) {
            DCHECK(GetState() == State::kDefault) << enum_cast<uint32_t>(GetState());
            SetState(State::kUseSuperMethod);
          }
        }
      } else {
        DCHECK(super_method->IsMiranda());
        // Any default methods with this signature in superclass interfaces have been
        // masked by subinterfaces. Check if we can reuse the miranda method.
        if (GetState() == State::kAbstractSingle || GetState() == State::kAbstract) {
          SetState(State::kUseSuperMethod);
        }
      }
    }

   private:
    template <typename Predicate>
    ALWAYS_INLINE
    void UpdateStateImpl(ObjPtr<mirror::Class> iface,
                         ArtMethod* interface_method,
                         Predicate&& slow_is_masked)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      bool have_default = false;
      switch (GetState()) {
        case State::kDefaultSingle:
          have_default = true;
          FALLTHROUGH_INTENDED;
        case State::kAbstractSingle:
          if (GetMainMethod()->GetDeclaringClass()->Implements(iface)) {
            return;  // The main method masks the `interface_method`.
          }
          if (!interface_method->IsDefault()) {
            SetState(have_default ? State::kDefault : State::kAbstract);
            return;
          }
          break;
        case State::kDefault:
          have_default = true;
          FALLTHROUGH_INTENDED;
        case State::kAbstract:
          if (!interface_method->IsDefault()) {
            return;  // Keep the same state. We do not need to check for masking.
          }
          // We do not record all overriding methods, so we need to walk over all
          // interfaces that could mask the `interface_method`. The provided
          // predicate `slow_is_masked()` does that.
          if (slow_is_masked()) {
            return;  // Found an overriding method that masks `interface_method`.
          }
          break;
        case State::kDefaultConflict:
          return;  // The state cannot change anymore.
        default:
          LOG(FATAL) << "Unexpected state: " << enum_cast<uint32_t>(GetState());
          UNREACHABLE();
      }
      // We have a new default method that's not masked by any other method.
      DCHECK(interface_method->IsDefault());
      if (have_default) {
        SetState(State::kDefaultConflict);
      } else {
        SetMainMethod(interface_method);
        SetState(State::kDefault);
      }
    }

    // Determine if the given `iftable` contains in the given range a subinterface of `iface`
    // that declares a method with the same name and signature as 'interface_method'.
    //
    // Arguments
    // - iftable: The iftable we are searching for an overriding method.
    // - begin:   The start of the range to search.
    // - end:     The end of the range to search.
    // - iface:   The interface we are checking to see if anything overrides.
    // - interface_method:
    //            The interface method providing a name and signature we're searching for.
    //
    // Returns whether an overriding method was found in any subinterface of `iface`.
    static bool ContainsOverridingMethodOf(ObjPtr<mirror::IfTable> iftable,
                                           size_t begin,
                                           size_t end,
                                           ObjPtr<mirror::Class> iface,
                                           ArtMethod* interface_method)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      for (size_t i = begin; i != end; ++i) {
        ObjPtr<mirror::Class> current_iface = iftable->GetInterface(i);
        for (ArtMethod& current_method : current_iface->GetDeclaredVirtualMethods(kPointerSize)) {
          if (MethodSignatureEquals(&current_method, interface_method)) {
            // Check if the i'th interface is a subtype of this one.
            if (current_iface->Implements(iface)) {
              return true;
            }
            break;
          }
        }
      }
      return false;
    }

    // Determine if the given `iftable` contains in the given range a subinterface of `iface`
    // that declares a method implemented by 'target'. This is an optimized version of
    // `ContainsOverridingMethodOf()` that searches implementation method arrays instead
    // of comparing signatures for declared interface methods.
    //
    // Arguments
    // - iftable: The iftable we are searching for an overriding method.
    // - begin:   The start of the range to search.
    // - end:     The end of the range to search.
    // - iface:   The interface we are checking to see if anything overrides.
    // - target:  The implementation method we're searching for.
    //            Note that the new `iftable` is filled with vtable indexes for new interfaces,
    //            so this needs to be the vtable index if we're searching that range.
    //
    // Returns whether the `target` was found in a method array for any subinterface of `iface`.
    template <typename TargetType>
    static bool ContainsImplementingMethod(ObjPtr<mirror::IfTable> iftable,
                                           size_t begin,
                                           size_t end,
                                           ObjPtr<mirror::Class> iface,
                                           TargetType target)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      for (size_t i = begin; i != end; ++i) {
        if (MethodArrayContains(iftable->GetMethodArrayOrNull(i), target).first &&
            iftable->GetInterface(i)->Implements(iface)) {
          return true;
        }
      }
      return false;
    }

    template <typename TargetType>
    static std::pair<bool, size_t> MethodArrayContains(ObjPtr<mirror::PointerArray> method_array,
                                                       TargetType target)
        REQUIRES_SHARED(Locks::mutator_lock_) {
      size_t num_methods = (method_array != nullptr) ? method_array->GetLength() : 0u;
      for (size_t j = 0; j != num_methods; ++j) {
        if (method_array->GetElementPtrSize<TargetType, kPointerSize>(j) == target) {
          return {true, j};
        }
      }
      return {false, 0};
    }

    ArtMethod* main_method_;
    uint32_t method_index_;
    State state_;
  };

  class CopiedMethodRecordEmptyFn {
   public:
    void MakeEmpty(CopiedMethodRecord& item) const {
      item = CopiedMethodRecord();
    }
    bool IsEmpty(const CopiedMethodRecord& item) const {
      return item.GetMainMethod() == nullptr;
    }
  };

  class CopiedMethodRecordHash {
   public:
    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(ArtMethod* method) const NO_THREAD_SAFETY_ANALYSIS {
      DCHECK(method != nullptr);
      return ComputeMethodHash(method);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    size_t operator()(const CopiedMethodRecord& record) const NO_THREAD_SAFETY_ANALYSIS {
      return (*this)(record.GetMainMethod());
    }
  };

  class CopiedMethodRecordEqual {
   public:
    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(const CopiedMethodRecord& lhs_record,
                    ArtMethod* rhs) const NO_THREAD_SAFETY_ANALYSIS {
      ArtMethod* lhs = lhs_record.GetMainMethod();
      DCHECK(lhs != nullptr);
      DCHECK(rhs != nullptr);
      return MethodSignatureEquals(lhs, rhs);
    }

    // NO_THREAD_SAFETY_ANALYSIS: This is called from unannotated `HashSet<>` functions.
    bool operator()(const CopiedMethodRecord& lhs_record,
                    const CopiedMethodRecord& rhs_record) const NO_THREAD_SAFETY_ANALYSIS {
      return (*this)(lhs_record, rhs_record.GetMainMethod());
    }
  };

  using CopiedMethodRecordSet = ScopedArenaHashSet<CopiedMethodRecord,
                                                   CopiedMethodRecordEmptyFn,
                                                   CopiedMethodRecordHash,
                                                   CopiedMethodRecordEqual>;

  static constexpr size_t kMethodAlignment = ArtMethod::Alignment(kPointerSize);
  static constexpr size_t kMethodSize = ArtMethod::Size(kPointerSize);

  ClassLinker* class_linker_;
  Handle<mirror::Class> klass_;
  Thread* const self_;
  Runtime* const runtime_;

  // These are allocated on the heap to begin, we then transfer to linear alloc when we re-create
  // the virtual methods array.
  // Need to use low 4GB arenas for compiler or else the pointers wont fit in 32 bit method array
  // during cross compilation.
  // Use the linear alloc pool since this one is in the low 4gb for the compiler.
  ArenaStack stack_;
  ScopedArenaAllocator allocator_;

  // If there are multiple methods with the same signature in the superclass vtable
  // (which can happen with a new virtual method having the same signature as an
  // inaccessible package-private method from another package in the superclass),
  // we keep singly-linked lists in this single array that maps vtable index to the
  // next vtable index in the list, `dex::kDexNoIndex` denotes the end of a list.
  ArrayRef<uint32_t> same_signature_vtable_lists_;

  // Avoid large allocation for a few copied method records.
  // Keep the initial buffer on the stack to avoid arena allocations
  // if there are no special cases (the first arena allocation is costly).
  static constexpr size_t kCopiedMethodRecordInitialBufferSize = 16u;
  CopiedMethodRecord copied_method_records_initial_buffer_[kCopiedMethodRecordInitialBufferSize];
  CopiedMethodRecordSet copied_method_records_;
  size_t num_new_copied_methods_;
};

template <PointerSize kPointerSize>
NO_INLINE
void ClassLinker::LinkMethodsHelper<kPointerSize>::ReallocMethods(ObjPtr<mirror::Class> klass) {
  // There should be no thread suspension in this function,
  // native allocations do not cause thread suspension.
  ScopedAssertNoThreadSuspension sants(__FUNCTION__);

  size_t num_new_copied_methods = num_new_copied_methods_;
  DCHECK_NE(num_new_copied_methods, 0u);
  const size_t old_method_count = klass->NumMethods();
  const size_t new_method_count = old_method_count + num_new_copied_methods;

  // Attempt to realloc to save RAM if possible.
  LengthPrefixedArray<ArtMethod>* old_methods = klass->GetMethodsPtr();
  // The Realloced virtual methods aren't visible from the class roots, so there is no issue
  // where GCs could attempt to mark stale pointers due to memcpy. And since we overwrite the
  // realloced memory with out->CopyFrom, we are guaranteed to have objects in the to space since
  // CopyFrom has internal read barriers.
  //
  // TODO We should maybe move some of this into mirror::Class or at least into another method.
  const size_t old_size = LengthPrefixedArray<ArtMethod>::ComputeSize(old_method_count,
                                                                      kMethodSize,
                                                                      kMethodAlignment);
  const size_t new_size = LengthPrefixedArray<ArtMethod>::ComputeSize(new_method_count,
                                                                      kMethodSize,
                                                                      kMethodAlignment);
  const size_t old_methods_ptr_size = (old_methods != nullptr) ? old_size : 0;
  LinearAlloc* allocator = class_linker_->GetAllocatorForClassLoader(klass->GetClassLoader());
  auto* methods = reinterpret_cast<LengthPrefixedArray<ArtMethod>*>(allocator->Realloc(
      self_, old_methods, old_methods_ptr_size, new_size, LinearAllocKind::kArtMethodArray));
  CHECK(methods != nullptr);  // Native allocation failure aborts.

  if (methods != old_methods) {
    if (gUseReadBarrier) {
      StrideIterator<ArtMethod> out = methods->begin(kMethodSize, kMethodAlignment);
      // Copy over the old methods. The `ArtMethod::CopyFrom()` is only necessary to not miss
      // read barriers since `LinearAlloc::Realloc()` won't do read barriers when it copies.
      for (auto& m : klass->GetMethods(kPointerSize)) {
        out->CopyFrom(&m, kPointerSize);
        ++out;
      }
    } else if (gUseUserfaultfd) {
      // In order to make compaction code skip updating the declaring_class_ in
      // old_methods, convert it into a 'no GC-root' array.
      allocator->ConvertToNoGcRoots(old_methods, LinearAllocKind::kArtMethodArray);
    }
  }

  // Collect and sort copied method records by the vtable index. This places overriding
  // copied methods first, sorted by the vtable index already assigned in the superclass,
  // followed by copied methods with new signatures in the order in which we encountered
  // them when going over virtual methods of new interfaces.
  // This order is deterministic but implementation-defined.
  //
  // Avoid arena allocation for a few records (the first arena allocation is costly).
  constexpr size_t kSortedRecordsBufferSize = 16;
  CopiedMethodRecord* sorted_records_buffer[kSortedRecordsBufferSize];
  CopiedMethodRecord** sorted_records = (num_new_copied_methods <= kSortedRecordsBufferSize)
      ? sorted_records_buffer
      : allocator_.AllocArray<CopiedMethodRecord*>(num_new_copied_methods);
  size_t filled_sorted_records = 0u;
  for (CopiedMethodRecord& record : copied_method_records_) {
    if (record.GetState() != CopiedMethodRecord::State::kUseSuperMethod) {
      DCHECK_LT(filled_sorted_records, num_new_copied_methods);
      sorted_records[filled_sorted_records] = &record;
      ++filled_sorted_records;
    }
  }
  DCHECK_EQ(filled_sorted_records, num_new_copied_methods);
  std::sort(sorted_records,
            sorted_records + num_new_copied_methods,
            [](const CopiedMethodRecord* lhs, const CopiedMethodRecord* rhs) {
              return lhs->GetMethodIndex() < rhs->GetMethodIndex();
            });

  if (klass->IsInterface()) {
    // Some records may have been pruned. Update method indexes in collected records.
    size_t interface_method_index = klass->NumDeclaredVirtualMethods();
    for (size_t i = 0; i != num_new_copied_methods; ++i) {
      CopiedMethodRecord* record = sorted_records[i];
      DCHECK_LE(interface_method_index, record->GetMethodIndex());
      record->SetMethodIndex(interface_method_index);
      ++interface_method_index;
    }
  }

  // Add copied methods.
  methods->SetSize(new_method_count);
  for (size_t i = 0; i != num_new_copied_methods; ++i) {
    const CopiedMethodRecord* record = sorted_records[i];
    ArtMethod* interface_method = record->GetMainMethod();
    DCHECK(!interface_method->IsCopied());
    ArtMethod& new_method = methods->At(old_method_count + i, kMethodSize, kMethodAlignment);
    new_method.CopyFrom(interface_method, kPointerSize);
    new_method.SetMethodIndex(dchecked_integral_cast<uint16_t>(record->GetMethodIndex()));
    switch (record->GetState()) {
      case CopiedMethodRecord::State::kAbstractSingle:
      case CopiedMethodRecord::State::kAbstract: {
        DCHECK(!klass->IsInterface());  // We do not create miranda methods for interfaces.
        uint32_t access_flags = new_method.GetAccessFlags();
        DCHECK_EQ(access_flags & (kAccAbstract | kAccIntrinsic | kAccDefault), kAccAbstract)
            << "Miranda method should be abstract but not intrinsic or default!";
        new_method.SetAccessFlags(access_flags | kAccCopied);
        break;
      }
      case CopiedMethodRecord::State::kDefaultSingle:
      case CopiedMethodRecord::State::kDefault: {
        DCHECK(!klass->IsInterface());  // We do not copy default methods for interfaces.
        // Clear the kAccSkipAccessChecks flag if it is present. Since this class hasn't been
        // verified yet it shouldn't have methods that are skipping access checks.
        // TODO This is rather arbitrary. We should maybe support classes where only some of its
        // methods are skip_access_checks.
        DCHECK_EQ(new_method.GetAccessFlags() & kAccNative, 0u);
        constexpr uint32_t kSetFlags = kAccDefault | kAccCopied;
        constexpr uint32_t kMaskFlags = ~kAccSkipAccessChecks;
        new_method.SetAccessFlags((new_method.GetAccessFlags() | kSetFlags) & kMaskFlags);
        break;
      }
      case CopiedMethodRecord::State::kDefaultConflict: {
        // This is a type of default method (there are default method impls, just a conflict)
        // so mark this as a default. We use the `kAccAbstract` flag to distinguish it from
        // invokable copied default method without using a separate access flag but the default
        // conflicting method is technically not abstract and ArtMethod::IsAbstract() shall
        // return false. Also clear the kAccSkipAccessChecks bit since this class hasn't been
        // verified yet it shouldn't have methods that are skipping access checks. Also clear
        // potential kAccSingleImplementation to avoid CHA trying to inline the default method.
        uint32_t access_flags = new_method.GetAccessFlags();
        DCHECK_EQ(access_flags & (kAccNative | kAccIntrinsic), 0u);
        constexpr uint32_t kSetFlags = kAccDefault | kAccAbstract | kAccCopied;
        constexpr uint32_t kMaskFlags = ~(kAccSkipAccessChecks | kAccSingleImplementation);
        new_method.SetAccessFlags((access_flags | kSetFlags) & kMaskFlags);
        new_method.SetDataPtrSize(nullptr, kPointerSize);
        DCHECK(new_method.IsDefaultConflicting());
        DCHECK(!new_method.IsAbstract());
        // The actual method might or might not be marked abstract since we just copied it from
        // a (possibly default) interface method. We need to set its entry point to be the bridge
        // so that the compiler will not invoke the implementation of whatever method we copied
        // from.
        EnsureThrowsInvocationError(class_linker_, &new_method);
        break;
      }
      default:
        LOG(FATAL) << "Unexpected state: " << enum_cast<uint32_t>(record->GetState());
        UNREACHABLE();
    }
  }

  if (VLOG_IS_ON(class_linker)) {
    LogNewVirtuals(methods);
  }

  class_linker_->UpdateClassMethods(klass, methods);
}

template <PointerSize kPointerSize>
bool ClassLinker::LinkMethodsHelper<kPointerSize>::FinalizeIfTable(
    Handle<mirror::Class> klass,
    MutableHandle<mirror::IfTable> iftable,
    Handle<mirror::PointerArray> vtable,
    bool is_klass_abstract,
    bool is_super_abstract,
    bool* out_new_conflict,
    ArtMethod** out_imt) {
  size_t ifcount = iftable->Count();
  // We do not need a read barrier here as the length is constant, both from-space and
  // to-space `IfTable`s shall yield the same result. See also `Class::GetIfTableCount()`.
  size_t super_ifcount =
      klass->GetSuperClass<kDefaultVerifyFlags, kWithoutReadBarrier>()->GetIfTableCount();

  ClassLinker* class_linker = nullptr;
  ArtMethod* unimplemented_method = nullptr;
  ArtMethod* imt_conflict_method = nullptr;
  uintptr_t imt_methods_begin = 0u;
  size_t imt_methods_size = 0u;
  DCHECK_EQ(klass->ShouldHaveImt(), !is_klass_abstract);
  DCHECK_EQ(klass->GetSuperClass()->ShouldHaveImt(), !is_super_abstract);
  if (!is_klass_abstract) {
    class_linker = class_linker_;
    unimplemented_method = runtime_->GetImtUnimplementedMethod();
    imt_conflict_method = runtime_->GetImtConflictMethod();
    if (is_super_abstract) {
      // There was no IMT in superclass to copy to `out_imt[]`, so we need
      // to fill it with all implementation methods from superclass.
      DCHECK_EQ(imt_methods_begin, 0u);
      imt_methods_size = std::numeric_limits<size_t>::max();  // No method at the last byte.
    } else {
      // If the superclass has IMT, we have already copied it to `out_imt[]` and
      // we do not need to call `SetIMTRef()` for interfaces from superclass when
      // the implementation method is already in the superclass, only for new methods.
      // For simplicity, use the entire method array including direct methods.
      LengthPrefixedArray<ArtMethod>* const new_methods = klass->GetMethodsPtr();
      if (new_methods != nullptr) {
        DCHECK_NE(new_methods->size(), 0u);
        imt_methods_begin =
            reinterpret_cast<uintptr_t>(&new_methods->At(0, kMethodSize, kMethodAlignment));
        imt_methods_size = new_methods->size() * kMethodSize;
      }
    }
  }

  auto update_imt = [=](ObjPtr<mirror::Class> iface, size_t j, ArtMethod* implementation)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    // Place method in imt if entry is empty, place conflict otherwise.
    ArtMethod** imt_ptr = &out_imt[iface->GetVirtualMethod(j, kPointerSize)->GetImtIndex()];
    class_linker->SetIMTRef(unimplemented_method,
                            imt_conflict_method,
                            implementation,
                            /*out*/out_new_conflict,
                            /*out*/imt_ptr);
  };

  // For interfaces inherited from superclass, the new method arrays are empty,
  // so use vtable indexes from implementation methods from the superclass method array.
  for (size_t i = 0; i != super_ifcount; ++i) {
    ObjPtr<mirror::PointerArray> method_array = iftable->GetMethodArrayOrNull(i);
    DCHECK(method_array == klass->GetSuperClass()->GetIfTable()->GetMethodArrayOrNull(i));
    if (method_array == nullptr) {
      continue;
    }
    size_t num_methods = method_array->GetLength();
    ObjPtr<mirror::Class> iface = iftable->GetInterface(i);
    size_t j = 0;
    // First loop has method array shared with the super class.
    for (; j != num_methods; ++j) {
      ArtMethod* super_implementation =
          method_array->GetElementPtrSize<ArtMethod*, kPointerSize>(j);
      size_t vtable_index = super_implementation->GetMethodIndex();
      ArtMethod* implementation =
          vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(vtable_index);
      // Check if we need to update IMT with this method, see above.
      if (reinterpret_cast<uintptr_t>(implementation) - imt_methods_begin < imt_methods_size) {
        update_imt(iface, j, implementation);
      }
      if (implementation != super_implementation) {
        // Copy-on-write and move to the next loop.
        Thread* self = self_;
        StackHandleScope<2u> hs(self);
        Handle<mirror::PointerArray> old_method_array = hs.NewHandle(method_array);
        HandleWrapperObjPtr<mirror::Class> h_iface = hs.NewHandleWrapper(&iface);
        if (ifcount == super_ifcount && iftable.Get() == klass->GetSuperClass()->GetIfTable()) {
          ObjPtr<mirror::IfTable> new_iftable = ObjPtr<mirror::IfTable>::DownCast(
              mirror::ObjectArray<mirror::Object>::CopyOf(
                  iftable, self, ifcount * mirror::IfTable::kMax));
          if (new_iftable == nullptr) {
            return false;
          }
          iftable.Assign(new_iftable);
        }
        method_array = ObjPtr<mirror::PointerArray>::DownCast(
            mirror::Array::CopyOf(old_method_array, self, num_methods));
        if (method_array == nullptr) {
          return false;
        }
        iftable->SetMethodArray(i, method_array);
        method_array->SetElementPtrSize(j, implementation, kPointerSize);
        ++j;
        break;
      }
    }
    // Second loop (if non-empty) has method array different from the superclass.
    for (; j != num_methods; ++j) {
      ArtMethod* super_implementation =
          method_array->GetElementPtrSize<ArtMethod*, kPointerSize>(j);
      size_t vtable_index = super_implementation->GetMethodIndex();
      ArtMethod* implementation =
          vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(vtable_index);
      method_array->SetElementPtrSize(j, implementation, kPointerSize);
      // Check if we need to update IMT with this method, see above.
      if (reinterpret_cast<uintptr_t>(implementation) - imt_methods_begin < imt_methods_size) {
        update_imt(iface, j, implementation);
      }
    }
  }

  // New interface method arrays contain vtable indexes. Translate them to methods.
  DCHECK_EQ(klass->ShouldHaveImt(), !is_klass_abstract);
  for (size_t i = super_ifcount; i != ifcount; ++i) {
    ObjPtr<mirror::PointerArray> method_array = iftable->GetMethodArrayOrNull(i);
    if (method_array == nullptr) {
      continue;
    }
    size_t num_methods = method_array->GetLength();
    ObjPtr<mirror::Class> iface = iftable->GetInterface(i);
    for (size_t j = 0; j != num_methods; ++j) {
      size_t vtable_index = method_array->GetElementPtrSize<size_t, kPointerSize>(j);
      ArtMethod* implementation =
          vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(vtable_index);
      method_array->SetElementPtrSize(j, implementation, kPointerSize);
      if (!is_klass_abstract) {
        update_imt(iface, j, implementation);
      }
    }
  }

  return true;
}

template <PointerSize kPointerSize>
ObjPtr<mirror::PointerArray> ClassLinker::LinkMethodsHelper<kPointerSize>::AllocPointerArray(
    Thread* self, size_t length) {
  using PointerArrayType = std::conditional_t<
      kPointerSize == PointerSize::k64, mirror::LongArray, mirror::IntArray>;
  ObjPtr<mirror::Array> array = PointerArrayType::Alloc(self, length);
  return ObjPtr<mirror::PointerArray>::DownCast(array);
}

template <PointerSize kPointerSize>
bool ClassLinker::LinkMethodsHelper<kPointerSize>::AllocateIfTableMethodArrays(
    Thread* self,
    Handle<mirror::Class> klass,
    Handle<mirror::IfTable> iftable) {
  DCHECK(!klass->IsInterface());
  DCHECK(klass_->HasSuperClass());
  const size_t ifcount = iftable->Count();
  // We do not need a read barrier here as the length is constant, both from-space and
  // to-space `IfTable`s shall yield the same result. See also `Class::GetIfTableCount()`.
  size_t super_ifcount =
      klass->GetSuperClass<kDefaultVerifyFlags, kWithoutReadBarrier>()->GetIfTableCount();
  if (ifcount == super_ifcount) {
    DCHECK(iftable.Get() == klass_->GetSuperClass()->GetIfTable());
    return true;
  }

  if (kIsDebugBuild) {
    // The method array references for superclass interfaces have been copied.
    // We shall allocate new arrays if needed (copy-on-write) in `FinalizeIfTable()`.
    ObjPtr<mirror::IfTable> super_iftable = klass_->GetSuperClass()->GetIfTable();
    for (size_t i = 0; i != super_ifcount; ++i) {
      CHECK(iftable->GetInterface(i) == super_iftable->GetInterface(i));
      CHECK(iftable->GetMethodArrayOrNull(i) == super_iftable->GetMethodArrayOrNull(i));
    }
  }

  for (size_t i = super_ifcount; i < ifcount; ++i) {
    size_t num_methods = iftable->GetInterface(i)->NumDeclaredVirtualMethods();
    if (num_methods > 0) {
      ObjPtr<mirror::PointerArray> method_array = AllocPointerArray(self, num_methods);
      if (UNLIKELY(method_array == nullptr)) {
        self->AssertPendingOOMException();
        return false;
      }
      iftable->SetMethodArray(i, method_array);
    }
  }
  return true;
}

template <PointerSize kPointerSize>
size_t ClassLinker::LinkMethodsHelper<kPointerSize>::AssignVTableIndexes(
    ObjPtr<mirror::Class> klass,
    ObjPtr<mirror::Class> super_class,
    bool is_super_abstract,
    size_t num_virtual_methods,
    ObjPtr<mirror::IfTable> iftable) {
  DCHECK(!klass->IsInterface());
  DCHECK(klass->HasSuperClass());
  DCHECK(klass->GetSuperClass() == super_class);

  // There should be no thread suspension unless we want to throw an exception.
  // (We are using `ObjPtr<>` and raw vtable pointers that are invalidated by thread suspension.)
  std::optional<ScopedAssertNoThreadSuspension> sants(__FUNCTION__);

  // Prepare a hash table with virtual methods from the superclass.
  // For the unlikely cases that there are multiple methods with the same signature
  // but different vtable indexes, keep an array with indexes of the previous
  // methods with the same signature (walked as singly-linked lists).
  uint8_t* raw_super_vtable;
  size_t super_vtable_length;
  if (is_super_abstract) {
    DCHECK(!super_class->ShouldHaveEmbeddedVTable());
    ObjPtr<mirror::PointerArray> super_vtable = super_class->GetVTableDuringLinking();
    DCHECK(super_vtable != nullptr);
    raw_super_vtable = reinterpret_cast<uint8_t*>(super_vtable.Ptr()) +
                       mirror::Array::DataOffset(static_cast<size_t>(kPointerSize)).Uint32Value();
    super_vtable_length = super_vtable->GetLength();
  } else {
    DCHECK(super_class->ShouldHaveEmbeddedVTable());
    raw_super_vtable = reinterpret_cast<uint8_t*>(super_class.Ptr()) +
                       mirror::Class::EmbeddedVTableOffset(kPointerSize).Uint32Value();
    super_vtable_length = super_class->GetEmbeddedVTableLength();
  }
  VTableAccessor super_vtable_accessor(raw_super_vtable, super_vtable_length);
  static constexpr double kMinLoadFactor = 0.3;
  static constexpr double kMaxLoadFactor = 0.5;
  static constexpr size_t kMaxStackBuferSize = 256;
  const size_t declared_virtuals_buffer_size = num_virtual_methods * 3;
  const size_t super_vtable_buffer_size = super_vtable_length * 3;
  const size_t bit_vector_size = BitVector::BitsToWords(num_virtual_methods);
  const size_t total_size =
      declared_virtuals_buffer_size + super_vtable_buffer_size + bit_vector_size;

  uint32_t* declared_virtuals_buffer_ptr = (total_size <= kMaxStackBuferSize)
      ? reinterpret_cast<uint32_t*>(alloca(total_size * sizeof(uint32_t)))
      : allocator_.AllocArray<uint32_t>(total_size);
  uint32_t* bit_vector_buffer_ptr = declared_virtuals_buffer_ptr + declared_virtuals_buffer_size;

  DeclaredVirtualSignatureSet declared_virtual_signatures(
      kMinLoadFactor,
      kMaxLoadFactor,
      DeclaredVirtualSignatureHash(klass),
      DeclaredVirtualSignatureEqual(klass),
      declared_virtuals_buffer_ptr,
      declared_virtuals_buffer_size,
      allocator_.Adapter());

  ArrayRef<uint32_t> same_signature_vtable_lists;
  const bool is_proxy_class = klass->IsProxyClass();
  size_t vtable_length = super_vtable_length;

  // Record which declared methods are overriding a super method.
  BitVector initialized_methods(/* expandable= */ false,
                                Allocator::GetNoopAllocator(),
                                bit_vector_size,
                                bit_vector_buffer_ptr);

  // Note: our sets hash on the method name, and therefore we pay a high
  // performance price when a class has many overloads.
  //
  // We populate a set of declared signatures instead of signatures from the
  // super vtable (which is only lazy populated in case of interface overriding,
  // see below). This makes sure that we pay the performance price only on that
  // class, and not on its subclasses (except in the case of interface overriding, see below).
  for (size_t i = 0; i != num_virtual_methods; ++i) {
    ArtMethod* virtual_method = klass->GetVirtualMethodDuringLinking(i, kPointerSize);
    DCHECK(!virtual_method->IsStatic()) << virtual_method->PrettyMethod();
    ArtMethod* signature_method = UNLIKELY(is_proxy_class)
        ? virtual_method->GetInterfaceMethodForProxyUnchecked(kPointerSize)
        : virtual_method;
    size_t hash = ComputeMethodHash(signature_method);
    declared_virtual_signatures.PutWithHash(i, hash);
  }

  // Loop through each super vtable method and see if they are overridden by a method we added to
  // the hash table.
  for (size_t j = 0; j < super_vtable_length; ++j) {
    // Search the hash table to see if we are overridden by any method.
    ArtMethod* super_method = super_vtable_accessor.GetVTableEntry(j);
    if (!klass->CanAccessMember(super_method->GetDeclaringClass(),
                                super_method->GetAccessFlags())) {
      // Continue on to the next method since this one is package private and cannot be overridden.
      // Before Android 4.1, the package-private method super_method might have been incorrectly
      // overridden.
      continue;
    }
    size_t hash = (j < mirror::Object::kVTableLength)
        ? class_linker_->object_virtual_method_hashes_[j]
        : ComputeMethodHash(super_method);
    auto it = declared_virtual_signatures.FindWithHash(super_method, hash);
    if (it == declared_virtual_signatures.end()) {
      continue;
    }
    ArtMethod* virtual_method = klass->GetVirtualMethodDuringLinking(*it, kPointerSize);
    if (super_method->IsFinal()) {
      sants.reset();
      ThrowLinkageError(klass, "Method %s overrides final method in class %s",
                        virtual_method->PrettyMethod().c_str(),
                        super_method->GetDeclaringClassDescriptor());
      return 0u;
    }
    if (initialized_methods.IsBitSet(*it)) {
      // The method is overriding more than one method.
      // We record that information in a linked list to later set the method in the vtable
      // locations that are not the method index.
      if (same_signature_vtable_lists.empty()) {
        same_signature_vtable_lists = ArrayRef<uint32_t>(
            allocator_.AllocArray<uint32_t>(super_vtable_length), super_vtable_length);
        std::fill_n(same_signature_vtable_lists.data(), super_vtable_length, dex::kDexNoIndex);
        same_signature_vtable_lists_ = same_signature_vtable_lists;
      }
      same_signature_vtable_lists[j] = virtual_method->GetMethodIndexDuringLinking();
    } else {
      initialized_methods.SetBit(*it);
    }

    // We arbitrarily set to the largest index. This is also expected when
    // iterating over the `same_signature_vtable_lists_`.
    virtual_method->SetMethodIndex(j);
  }

  // Add the non-overridden methods at the end.
  for (size_t i = 0; i < num_virtual_methods; ++i) {
    if (!initialized_methods.IsBitSet(i)) {
      ArtMethod* local_method = klass->GetVirtualMethodDuringLinking(i, kPointerSize);
      local_method->SetMethodIndex(vtable_length);
      vtable_length++;
    }
  }

  // A lazily constructed super vtable set, which we only populate in the less
  // common sittuation of a superclass implementing a method declared in an
  // interface this class inherits.
  // We still try to allocate the set on the stack as using the arena will have
  // a larger cost.
  uint32_t* super_vtable_buffer_ptr = bit_vector_buffer_ptr + bit_vector_size;
  VTableSignatureSet super_vtable_signatures(
      kMinLoadFactor,
      kMaxLoadFactor,
      VTableSignatureHash(super_vtable_accessor),
      VTableSignatureEqual(super_vtable_accessor),
      super_vtable_buffer_ptr,
      super_vtable_buffer_size,
      allocator_.Adapter());

  // Assign vtable indexes for interface methods in new interfaces and store them
  // in implementation method arrays. These shall be replaced by actual method
  // pointers later. We do not need to do this for superclass interfaces as we can
  // get these vtable indexes from implementation methods in superclass iftable.
  // Record data for copied methods which shall be referenced by the vtable.
  const size_t ifcount = iftable->Count();
  ObjPtr<mirror::IfTable> super_iftable = super_class->GetIfTable();
  const size_t super_ifcount = super_iftable->Count();
  for (size_t i = ifcount; i != super_ifcount; ) {
    --i;
    DCHECK_LT(i, ifcount);
    ObjPtr<mirror::Class> iface = iftable->GetInterface(i);
    ObjPtr<mirror::PointerArray> method_array = iftable->GetMethodArrayOrNull(i);
    size_t num_methods = (method_array != nullptr) ? method_array->GetLength() : 0u;
    for (size_t j = 0; j != num_methods; ++j) {
      ArtMethod* interface_method = iface->GetVirtualMethod(j, kPointerSize);
      size_t hash = ComputeMethodHash(interface_method);
      ArtMethod* vtable_method = nullptr;
      auto it1 = declared_virtual_signatures.FindWithHash(interface_method, hash);
      if (it1 != declared_virtual_signatures.end()) {
        ArtMethod* found_method = klass->GetVirtualMethodDuringLinking(*it1, kPointerSize);
        // For interface overriding, we only look at public methods.
        if (found_method->IsPublic()) {
          vtable_method = found_method;
        }
      } else {
        // This situation should be rare (a superclass implements a method
        // declared in an interface this class is inheriting). Only in this case
        // do we lazily populate the super_vtable_signatures.
        if (super_vtable_signatures.empty()) {
          for (size_t k = 0; k < super_vtable_length; ++k) {
            ArtMethod* super_method = super_vtable_accessor.GetVTableEntry(k);
            if (!super_method->IsPublic()) {
              // For interface overriding, we only look at public methods.
              continue;
            }
            size_t super_hash = (k < mirror::Object::kVTableLength)
                ? class_linker_->object_virtual_method_hashes_[k]
                : ComputeMethodHash(super_method);
            auto [it, inserted] = super_vtable_signatures.InsertWithHash(k, super_hash);
            DCHECK(inserted || super_vtable_accessor.GetVTableEntry(*it) == super_method);
          }
        }
        auto it2 = super_vtable_signatures.FindWithHash(interface_method, hash);
        if (it2 != super_vtable_signatures.end()) {
          vtable_method = super_vtable_accessor.GetVTableEntry(*it2);
        }
      }

      uint32_t vtable_index = vtable_length;
      if (vtable_method != nullptr) {
        vtable_index = vtable_method->GetMethodIndexDuringLinking();
        if (!vtable_method->IsOverridableByDefaultMethod()) {
          method_array->SetElementPtrSize(j, vtable_index, kPointerSize);
          continue;
        }
      }

      auto [it, inserted] = copied_method_records_.InsertWithHash(
          CopiedMethodRecord(interface_method, vtable_index), hash);
      if (vtable_method != nullptr) {
        DCHECK_EQ(vtable_index, it->GetMethodIndex());
      } else if (inserted) {
        DCHECK_EQ(vtable_index, it->GetMethodIndex());
        DCHECK_EQ(vtable_index, vtable_length);
        ++vtable_length;
      } else {
        vtable_index = it->GetMethodIndex();
      }
      method_array->SetElementPtrSize(j, it->GetMethodIndex(), kPointerSize);
      if (inserted) {
        it->SetState(interface_method->IsAbstract() ? CopiedMethodRecord::State::kAbstractSingle
                                                    : CopiedMethodRecord::State::kDefaultSingle);
      } else {
        it->UpdateState(iface, interface_method, vtable_index, iftable, ifcount, i);
      }
    }
  }
  // Finalize copied method records and check if we can reuse some methods from superclass vtable.
  size_t num_new_copied_methods = copied_method_records_.size();
  for (CopiedMethodRecord& record : copied_method_records_) {
    uint32_t vtable_index = record.GetMethodIndex();
    if (vtable_index < super_vtable_length) {
      ArtMethod* super_method = super_vtable_accessor.GetVTableEntry(record.GetMethodIndex());
      DCHECK(super_method->IsOverridableByDefaultMethod());
      record.FinalizeState(
          super_method, vtable_index, iftable, ifcount, super_iftable, super_ifcount);
      if (record.GetState() == CopiedMethodRecord::State::kUseSuperMethod) {
        --num_new_copied_methods;
      }
    }
  }
  num_new_copied_methods_ = num_new_copied_methods;

  if (UNLIKELY(!IsUint<16>(vtable_length))) {
    sants.reset();
    ThrowClassFormatError(klass, "Too many methods defined on class: %zd", vtable_length);
    return 0u;
  }

  return vtable_length;
}

template <PointerSize kPointerSize>
bool ClassLinker::LinkMethodsHelper<kPointerSize>::FindCopiedMethodsForInterface(
    ObjPtr<mirror::Class> klass,
    size_t num_virtual_methods,
    ObjPtr<mirror::IfTable> iftable) {
  DCHECK(klass->IsInterface());
  DCHECK(klass->HasSuperClass());
  DCHECK(klass->GetSuperClass()->IsObjectClass());
  DCHECK_EQ(klass->GetSuperClass()->GetIfTableCount(), 0);

  // There should be no thread suspension unless we want to throw an exception.
  // (We are using `ObjPtr<>`s that are invalidated by thread suspension.)
  std::optional<ScopedAssertNoThreadSuspension> sants(__FUNCTION__);

  // Prepare a `HashSet<>` with the declared virtual methods. These mask any methods
  // from superinterfaces, so we can filter out matching superinterface methods.
  static constexpr double kMinLoadFactor = 0.3;
  static constexpr double kMaxLoadFactor = 0.5;
  static constexpr size_t kMaxStackBuferSize = 256;
  const size_t declared_virtuals_buffer_size = num_virtual_methods * 3;
  uint32_t* declared_virtuals_buffer_ptr = (declared_virtuals_buffer_size <= kMaxStackBuferSize)
      ? reinterpret_cast<uint32_t*>(alloca(declared_virtuals_buffer_size * sizeof(uint32_t)))
      : allocator_.AllocArray<uint32_t>(declared_virtuals_buffer_size);
  DeclaredVirtualSignatureSet declared_virtual_signatures(
      kMinLoadFactor,
      kMaxLoadFactor,
      DeclaredVirtualSignatureHash(klass),
      DeclaredVirtualSignatureEqual(klass),
      declared_virtuals_buffer_ptr,
      declared_virtuals_buffer_size,
      allocator_.Adapter());
  for (size_t i = 0; i != num_virtual_methods; ++i) {
    ArtMethod* virtual_method = klass->GetVirtualMethodDuringLinking(i, kPointerSize);
    DCHECK(!virtual_method->IsStatic()) << virtual_method->PrettyMethod();
    size_t hash = ComputeMethodHash(virtual_method);
    declared_virtual_signatures.PutWithHash(i, hash);
  }

  // We do not create miranda methods for interface classes, so we do not need to track
  // non-default (abstract) interface methods. The downside is that we cannot use the
  // optimized code paths with `CopiedMethodRecord::State::kDefaultSingle` and since
  // we do not fill method arrays for interfaces, the method search actually has to
  // compare signatures instead of searching for the implementing method.
  const size_t ifcount = iftable->Count();
  size_t new_method_index = num_virtual_methods;
  for (size_t i = ifcount; i != 0u; ) {
    --i;
    DCHECK_LT(i, ifcount);
    ObjPtr<mirror::Class> iface = iftable->GetInterface(i);
    if (!iface->HasDefaultMethods()) {
      continue;  // No default methods to process.
    }
    size_t num_methods = iface->NumDeclaredVirtualMethods();
    for (size_t j = 0; j != num_methods; ++j) {
      ArtMethod* interface_method = iface->GetVirtualMethod(j, kPointerSize);
      if (!interface_method->IsDefault()) {
        continue;  // Do not process this non-default method.
      }
      size_t hash = ComputeMethodHash(interface_method);
      auto it1 = declared_virtual_signatures.FindWithHash(interface_method, hash);
      if (it1 != declared_virtual_signatures.end()) {
        ArtMethod* virtual_method = klass->GetVirtualMethodDuringLinking(*it1, kPointerSize);
        if (!virtual_method->IsAbstract() && !virtual_method->IsPublic()) {
          sants.reset();
          ThrowIllegalAccessErrorForImplementingMethod(klass, virtual_method, interface_method);
          return false;
        }
        continue;  // This default method is masked by a method declared in this interface.
      }

      CopiedMethodRecord new_record(interface_method, new_method_index);
      auto it = copied_method_records_.FindWithHash(new_record, hash);
      if (it == copied_method_records_.end()) {
        // Pretend that there is another default method and try to update the state.
        // If the `interface_method` is not masked, the state shall change to
        // `kDefaultConflict`; if it is masked, the state remains `kDefault`.
        new_record.SetState(CopiedMethodRecord::State::kDefault);
        new_record.UpdateStateForInterface(iface, interface_method, iftable, ifcount, i);
        if (new_record.GetState() == CopiedMethodRecord::State::kDefaultConflict) {
          // Insert the new record with the state `kDefault`.
          new_record.SetState(CopiedMethodRecord::State::kDefault);
          copied_method_records_.PutWithHash(new_record, hash);
          DCHECK_EQ(new_method_index, new_record.GetMethodIndex());
          ++new_method_index;
        }
      } else {
        it->UpdateStateForInterface(iface, interface_method, iftable, ifcount, i);
      }
    }
  }

  // Prune records without conflict. (Method indexes are updated in `ReallocMethods()`.)
  // We do not copy normal default methods to subinterfaces, instead we find the
  // default method with `Class::FindVirtualMethodForInterfaceSuper()` when needed.
  size_t num_new_copied_methods = copied_method_records_.size();
  for (CopiedMethodRecord& record : copied_method_records_) {
    if (record.GetState() != CopiedMethodRecord::State::kDefaultConflict) {
      DCHECK(record.GetState() == CopiedMethodRecord::State::kDefault);
      record.SetState(CopiedMethodRecord::State::kUseSuperMethod);
      --num_new_copied_methods;
    }
  }
  num_new_copied_methods_ = num_new_copied_methods;

  return true;
}


template <PointerSize kPointerSize>
FLATTEN
bool ClassLinker::LinkMethodsHelper<kPointerSize>::LinkMethods(
    Thread* self,
    Handle<mirror::Class> klass,
    Handle<mirror::ObjectArray<mirror::Class>> interfaces,
    bool* out_new_conflict,
    ArtMethod** out_imt) {
  const size_t num_virtual_methods = klass->NumVirtualMethods();
  if (klass->IsInterface()) {
    // No vtable.
    if (!IsUint<16>(num_virtual_methods)) {
      ThrowClassFormatError(klass.Get(), "Too many methods on interface: %zu", num_virtual_methods);
      return false;
    }
    // Assign each method an interface table index and set the default flag.
    bool has_defaults = false;
    for (size_t i = 0; i < num_virtual_methods; ++i) {
      ArtMethod* m = klass->GetVirtualMethodDuringLinking(i, kPointerSize);
      m->SetMethodIndex(i);
      uint32_t access_flags = m->GetAccessFlags();
      DCHECK(!ArtMethod::IsDefault(access_flags));
      DCHECK_EQ(!ArtMethod::IsAbstract(access_flags), ArtMethod::IsInvokable(access_flags));
      if (ArtMethod::IsInvokable(access_flags)) {
        // If the dex file does not support default methods, throw ClassFormatError.
        // This check is necessary to protect from odd cases, such as native default
        // methods, that the dex file verifier permits for old dex file versions. b/157170505
        // FIXME: This should be `if (!m->GetDexFile()->SupportsDefaultMethods())` but we're
        // currently running CTS tests for default methods with dex file version 035 which
        // does not support default methods. So, we limit this to native methods. b/157718952
        if (ArtMethod::IsNative(access_flags)) {
          DCHECK(!m->GetDexFile()->SupportsDefaultMethods());
          ThrowClassFormatError(klass.Get(),
                                "Dex file does not support default method '%s'",
                                m->PrettyMethod().c_str());
          return false;
        }
        if (!ArtMethod::IsPublic(access_flags)) {
          // The verifier should have caught the non-public method for dex version 37.
          // Just warn and skip it since this is from before default-methods so we don't
          // really need to care that it has code.
          LOG(WARNING) << "Default interface method " << m->PrettyMethod() << " is not public! "
                       << "This will be a fatal error in subsequent versions of android. "
                       << "Continuing anyway.";
        }
        m->SetAccessFlags(access_flags | kAccDefault);
        has_defaults = true;
      }
    }
    // Mark that we have default methods so that we won't need to scan the virtual_methods_ array
    // during initialization. This is a performance optimization. We could simply traverse the
    // virtual_methods_ array again during initialization.
    if (has_defaults) {
      klass->SetHasDefaultMethods();
    }
    ObjPtr<mirror::IfTable> iftable = SetupInterfaceLookupTable(
        self, klass, &allocator_, NonProxyInterfacesAccessor(class_linker_, klass));
    if (UNLIKELY(iftable == nullptr)) {
      self->AssertPendingException();
      return false;
    }
    size_t ifcount = iftable->Count();
    bool have_super_with_defaults = false;
    for (size_t i = 0; i != ifcount; ++i) {
      if (iftable->GetInterface(i)->HasDefaultMethods()) {
        have_super_with_defaults = true;
        break;
      }
    }
    LengthPrefixedArray<ArtMethod>* old_methods = kIsDebugBuild ? klass->GetMethodsPtr() : nullptr;
    if (have_super_with_defaults) {
      if (!FindCopiedMethodsForInterface(klass.Get(), num_virtual_methods, iftable)) {
        self->AssertPendingException();
        return false;
      }
      if (num_new_copied_methods_ != 0u) {
        // Re-check the number of methods.
        size_t final_num_virtual_methods = num_virtual_methods + num_new_copied_methods_;
        if (!IsUint<16>(final_num_virtual_methods)) {
          ThrowClassFormatError(
              klass.Get(), "Too many methods on interface: %zu", final_num_virtual_methods);
          return false;
        }
        ReallocMethods(klass.Get());
      }
    }
    klass->SetIfTable(iftable);
    if (kIsDebugBuild) {
      // May cause thread suspension, so do this after we're done with `ObjPtr<> iftable`.
      ClobberOldMethods(old_methods, klass->GetMethodsPtr());
    }
    return true;
  } else if (LIKELY(klass->HasSuperClass())) {
    // We set up the interface lookup table now because we need it to determine if we need
    // to update any vtable entries with new default method implementations.
    StackHandleScope<3> hs(self);
    MutableHandle<mirror::IfTable> iftable = hs.NewHandle(UNLIKELY(klass->IsProxyClass())
        ? SetupInterfaceLookupTable(self, klass, &allocator_, ProxyInterfacesAccessor(interfaces))
        : SetupInterfaceLookupTable(
              self, klass, &allocator_, NonProxyInterfacesAccessor(class_linker_, klass)));
    if (UNLIKELY(iftable == nullptr)) {
      self->AssertPendingException();
      return false;
    }

    // Copy the IMT from superclass if present and needed. Update with new methods later.
    Handle<mirror::Class> super_class = hs.NewHandle(klass->GetSuperClass());
    bool is_klass_abstract = klass->IsAbstract();
    bool is_super_abstract = super_class->IsAbstract();
    DCHECK_EQ(klass->ShouldHaveImt(), !is_klass_abstract);
    DCHECK_EQ(super_class->ShouldHaveImt(), !is_super_abstract);
    if (!is_klass_abstract && !is_super_abstract) {
      ImTable* super_imt = super_class->GetImt(kPointerSize);
      for (size_t i = 0; i < ImTable::kSize; ++i) {
        out_imt[i] = super_imt->Get(i, kPointerSize);
      }
    }

    // If there are no new virtual methods and no new interfaces, we can simply reuse
    // the vtable from superclass. We may need to make a copy if it's embedded.
    const size_t super_vtable_length = super_class->GetVTableLength();
    if (num_virtual_methods == 0 && iftable.Get() == super_class->GetIfTable()) {
      DCHECK_EQ(is_super_abstract, !super_class->ShouldHaveEmbeddedVTable());
      if (is_super_abstract) {
        DCHECK(super_class->IsAbstract() && !super_class->IsArrayClass());
        ObjPtr<mirror::PointerArray> super_vtable = super_class->GetVTable();
        CHECK(super_vtable != nullptr) << super_class->PrettyClass();
        klass->SetVTable(super_vtable);
        // No IMT in the super class, we need to reconstruct it from the iftable.
        if (!is_klass_abstract && iftable->Count() != 0) {
          class_linker_->FillIMTFromIfTable(iftable.Get(),
                                            runtime_->GetImtUnimplementedMethod(),
                                            runtime_->GetImtConflictMethod(),
                                            klass.Get(),
                                            /*create_conflict_tables=*/false,
                                            /*ignore_copied_methods=*/false,
                                            out_new_conflict,
                                            out_imt);
        }
      } else {
        ObjPtr<mirror::PointerArray> vtable = AllocPointerArray(self, super_vtable_length);
        if (UNLIKELY(vtable == nullptr)) {
          self->AssertPendingOOMException();
          return false;
        }
        for (size_t i = 0; i < super_vtable_length; i++) {
          vtable->SetElementPtrSize(
              i, super_class->GetEmbeddedVTableEntry(i, kPointerSize), kPointerSize);
        }
        klass->SetVTable(vtable);
        // The IMT was already copied from superclass if `klass` is not abstract.
      }
      klass->SetIfTable(iftable.Get());
      return true;
    }

    // Allocate method arrays, so that we can link interface methods without thread suspension,
    // otherwise GC could miss visiting newly allocated copied methods.
    // TODO: Do not allocate copied methods during linking, store only records about what
    // we need to allocate and allocate it at the end. Start with superclass iftable and
    // perform copy-on-write when needed to facilitate maximum memory sharing.
    if (!AllocateIfTableMethodArrays(self, klass, iftable)) {
      self->AssertPendingOOMException();
      return false;
    }

    size_t final_vtable_size = AssignVTableIndexes(
        klass.Get(), super_class.Get(), is_super_abstract, num_virtual_methods, iftable.Get());
    if (final_vtable_size == 0u) {
      self->AssertPendingException();
      return false;
    }
    DCHECK(IsUint<16>(final_vtable_size));

    // Allocate the new vtable.
    Handle<mirror::PointerArray> vtable = hs.NewHandle(AllocPointerArray(self, final_vtable_size));
    if (UNLIKELY(vtable == nullptr)) {
      self->AssertPendingOOMException();
      return false;
    }

    LengthPrefixedArray<ArtMethod>* old_methods = kIsDebugBuild ? klass->GetMethodsPtr() : nullptr;
    if (num_new_copied_methods_ != 0u) {
      ReallocMethods(klass.Get());
    }

    // Store new virtual methods in the new vtable.
    ArrayRef<uint32_t> same_signature_vtable_lists = same_signature_vtable_lists_;
    for (ArtMethod& virtual_method : klass->GetVirtualMethodsSliceUnchecked(kPointerSize)) {
      uint32_t vtable_index = virtual_method.GetMethodIndexDuringLinking();
      vtable->SetElementPtrSize(vtable_index, &virtual_method, kPointerSize);
      if (UNLIKELY(vtable_index < same_signature_vtable_lists.size())) {
        // We may override more than one method according to JLS, see b/211854716.
        while (same_signature_vtable_lists[vtable_index] != dex::kDexNoIndex) {
          DCHECK_LT(same_signature_vtable_lists[vtable_index], vtable_index);
          vtable_index = same_signature_vtable_lists[vtable_index];
          vtable->SetElementPtrSize(vtable_index, &virtual_method, kPointerSize);
          if (kIsDebugBuild) {
            ArtMethod* current_method = super_class->GetVTableEntry(vtable_index, kPointerSize);
            DCHECK(klass->CanAccessMember(current_method->GetDeclaringClass(),
                                          current_method->GetAccessFlags()));
            DCHECK(!current_method->IsFinal());
          }
        }
      }
    }

    // For non-overridden vtable slots, copy a method from `super_class`.
    for (size_t j = 0; j != super_vtable_length; ++j) {
      if (vtable->GetElementPtrSize<ArtMethod*, kPointerSize>(j) == nullptr) {
        ArtMethod* super_method = super_class->GetVTableEntry(j, kPointerSize);
        vtable->SetElementPtrSize(j, super_method, kPointerSize);
      }
    }

    // Update the `iftable` (and IMT) with finalized virtual methods.
    if (!FinalizeIfTable(klass,
                         iftable,
                         vtable,
                         is_klass_abstract,
                         is_super_abstract,
                         out_new_conflict,
                         out_imt)) {
      self->AssertPendingOOMException();
      return false;
    }

    klass->SetVTable(vtable.Get());
    klass->SetIfTable(iftable.Get());
    if (kIsDebugBuild) {
      CheckVTable(self, klass, kPointerSize);
      ClobberOldMethods(old_methods, klass->GetMethodsPtr());
    }
    return true;
  } else {
    return LinkJavaLangObjectMethods(self, klass);
  }
}

template <PointerSize kPointerSize>
bool ClassLinker::LinkMethodsHelper<kPointerSize>::LinkJavaLangObjectMethods(
    Thread* self,
    Handle<mirror::Class> klass) {
  DCHECK_EQ(klass.Get(), GetClassRoot<mirror::Object>(class_linker_));
  DCHECK_EQ(klass->NumVirtualMethods(), mirror::Object::kVTableLength);
  static_assert(IsUint<16>(mirror::Object::kVTableLength));
  ObjPtr<mirror::PointerArray> vtable = AllocPointerArray(self, mirror::Object::kVTableLength);
  if (UNLIKELY(vtable == nullptr)) {
    self->AssertPendingOOMException();
    return false;
  }
  for (size_t i = 0; i < mirror::Object::kVTableLength; ++i) {
    ArtMethod* virtual_method = klass->GetVirtualMethodDuringLinking(i, kPointerSize);
    vtable->SetElementPtrSize(i, virtual_method, kPointerSize);
    virtual_method->SetMethodIndex(i);
  }
  klass->SetVTable(vtable);
  InitializeObjectVirtualMethodHashes(
      klass.Get(),
      kPointerSize,
      ArrayRef<uint32_t>(class_linker_->object_virtual_method_hashes_));
  // The interface table is already allocated but there are no interface methods to link.
  DCHECK(klass->GetIfTable() != nullptr);
  DCHECK_EQ(klass->GetIfTableCount(), 0);
  return true;
}

// Populate the class vtable and itable. Compute return type indices.
bool ClassLinker::LinkMethods(Thread* self,
                              Handle<mirror::Class> klass,
                              Handle<mirror::ObjectArray<mirror::Class>> interfaces,
                              bool* out_new_conflict,
                              ArtMethod** out_imt) {
  self->AllowThreadSuspension();
  // Link virtual methods then interface methods.
  Runtime* const runtime = Runtime::Current();
  if (LIKELY(GetImagePointerSize() == kRuntimePointerSize)) {
    LinkMethodsHelper<kRuntimePointerSize> helper(this, klass, self, runtime);
    return helper.LinkMethods(self, klass, interfaces, out_new_conflict, out_imt);
  } else {
    constexpr PointerSize kOtherPointerSize =
        (kRuntimePointerSize == PointerSize::k64) ? PointerSize::k32 : PointerSize::k64;
    LinkMethodsHelper<kOtherPointerSize> helper(this, klass, self, runtime);
    return helper.LinkMethods(self, klass, interfaces, out_new_conflict, out_imt);
  }
}

class ClassLinker::LinkFieldsHelper {
 public:
  static bool LinkFields(ClassLinker* class_linker,
                         Thread* self,
                         Handle<mirror::Class> klass,
                         bool is_static,
                         size_t* class_size)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  enum class FieldTypeOrder : uint16_t;
  class FieldGaps;

  struct FieldTypeOrderAndIndex {
    FieldTypeOrder field_type_order;
    uint16_t field_index;
  };

  static FieldTypeOrder FieldTypeOrderFromFirstDescriptorCharacter(char first_char);

  template <size_t kSize>
  static MemberOffset AssignFieldOffset(ArtField* field, MemberOffset field_offset)
      REQUIRES_SHARED(Locks::mutator_lock_);
};

// We use the following order of field types for assigning offsets.
// Some fields can be shuffled forward to fill gaps, see
// `ClassLinker::LinkFieldsHelper::LinkFields()`.
enum class ClassLinker::LinkFieldsHelper::FieldTypeOrder : uint16_t {
  kReference = 0u,
  kLong,
  kDouble,
  kInt,
  kFloat,
  kChar,
  kShort,
  kBoolean,
  kByte,

  kLast64BitType = kDouble,
  kLast32BitType = kFloat,
  kLast16BitType = kShort,
};

ALWAYS_INLINE
ClassLinker::LinkFieldsHelper::FieldTypeOrder
ClassLinker::LinkFieldsHelper::FieldTypeOrderFromFirstDescriptorCharacter(char first_char) {
  switch (first_char) {
    case 'J':
      return FieldTypeOrder::kLong;
    case 'D':
      return FieldTypeOrder::kDouble;
    case 'I':
      return FieldTypeOrder::kInt;
    case 'F':
      return FieldTypeOrder::kFloat;
    case 'C':
      return FieldTypeOrder::kChar;
    case 'S':
      return FieldTypeOrder::kShort;
    case 'Z':
      return FieldTypeOrder::kBoolean;
    case 'B':
      return FieldTypeOrder::kByte;
    default:
      DCHECK(first_char == 'L' || first_char == '[') << first_char;
      return FieldTypeOrder::kReference;
  }
}

// Gaps where we can insert fields in object layout.
class ClassLinker::LinkFieldsHelper::FieldGaps {
 public:
  template <uint32_t kSize>
  ALWAYS_INLINE MemberOffset AlignFieldOffset(MemberOffset field_offset) {
    static_assert(kSize == 2u || kSize == 4u || kSize == 8u);
    if (!IsAligned<kSize>(field_offset.Uint32Value())) {
      uint32_t gap_start = field_offset.Uint32Value();
      field_offset = MemberOffset(RoundUp(gap_start, kSize));
      AddGaps<kSize - 1u>(gap_start, field_offset.Uint32Value());
    }
    return field_offset;
  }

  template <uint32_t kSize>
  bool HasGap() const {
    static_assert(kSize == 1u || kSize == 2u || kSize == 4u);
    return (kSize == 1u && gap1_offset_ != kNoOffset) ||
           (kSize <= 2u && gap2_offset_ != kNoOffset) ||
           gap4_offset_ != kNoOffset;
  }

  template <uint32_t kSize>
  MemberOffset ReleaseGap() {
    static_assert(kSize == 1u || kSize == 2u || kSize == 4u);
    uint32_t result;
    if (kSize == 1u && gap1_offset_ != kNoOffset) {
      DCHECK(gap2_offset_ == kNoOffset || gap2_offset_ > gap1_offset_);
      DCHECK(gap4_offset_ == kNoOffset || gap4_offset_ > gap1_offset_);
      result = gap1_offset_;
      gap1_offset_ = kNoOffset;
    } else if (kSize <= 2u && gap2_offset_ != kNoOffset) {
      DCHECK(gap4_offset_ == kNoOffset || gap4_offset_ > gap2_offset_);
      result = gap2_offset_;
      gap2_offset_ = kNoOffset;
      if (kSize < 2u) {
        AddGaps<1u>(result + kSize, result + 2u);
      }
    } else {
      DCHECK_NE(gap4_offset_, kNoOffset);
      result = gap4_offset_;
      gap4_offset_ = kNoOffset;
      if (kSize < 4u) {
        AddGaps<kSize | 2u>(result + kSize, result + 4u);
      }
    }
    return MemberOffset(result);
  }

 private:
  template <uint32_t kGapsToCheck>
  void AddGaps(uint32_t gap_start, uint32_t gap_end) {
    if ((kGapsToCheck & 1u) != 0u) {
      DCHECK_LT(gap_start, gap_end);
      DCHECK_ALIGNED(gap_end, 2u);
      if ((gap_start & 1u) != 0u) {
        DCHECK_EQ(gap1_offset_, kNoOffset);
        gap1_offset_ = gap_start;
        gap_start += 1u;
        if (kGapsToCheck == 1u || gap_start == gap_end) {
          DCHECK_EQ(gap_start, gap_end);
          return;
        }
      }
    }

    if ((kGapsToCheck & 2u) != 0u) {
      DCHECK_LT(gap_start, gap_end);
      DCHECK_ALIGNED(gap_start, 2u);
      DCHECK_ALIGNED(gap_end, 4u);
      if ((gap_start & 2u) != 0u) {
        DCHECK_EQ(gap2_offset_, kNoOffset);
        gap2_offset_ = gap_start;
        gap_start += 2u;
        if (kGapsToCheck <= 3u || gap_start == gap_end) {
          DCHECK_EQ(gap_start, gap_end);
          return;
        }
      }
    }

    if ((kGapsToCheck & 4u) != 0u) {
      DCHECK_LT(gap_start, gap_end);
      DCHECK_ALIGNED(gap_start, 4u);
      DCHECK_ALIGNED(gap_end, 8u);
      DCHECK_EQ(gap_start + 4u, gap_end);
      DCHECK_EQ(gap4_offset_, kNoOffset);
      gap4_offset_ = gap_start;
      return;
    }

    DCHECK(false) << "Remaining gap: " << gap_start << " to " << gap_end
        << " after checking " << kGapsToCheck;
  }

  static constexpr uint32_t kNoOffset = static_cast<uint32_t>(-1);

  uint32_t gap4_offset_ = kNoOffset;
  uint32_t gap2_offset_ = kNoOffset;
  uint32_t gap1_offset_ = kNoOffset;
};

template <size_t kSize>
ALWAYS_INLINE
MemberOffset ClassLinker::LinkFieldsHelper::AssignFieldOffset(ArtField* field,
                                                              MemberOffset field_offset) {
  DCHECK_ALIGNED(field_offset.Uint32Value(), kSize);
  DCHECK_EQ(Primitive::ComponentSize(field->GetTypeAsPrimitiveType()), kSize);
  field->SetOffset(field_offset);
  return MemberOffset(field_offset.Uint32Value() + kSize);
}

bool ClassLinker::LinkFieldsHelper::LinkFields(ClassLinker* class_linker,
                                               Thread* self,
                                               Handle<mirror::Class> klass,
                                               bool is_static,
                                               size_t* class_size) {
  self->AllowThreadSuspension();
  const size_t num_fields = is_static ? klass->NumStaticFields() : klass->NumInstanceFields();
  LengthPrefixedArray<ArtField>* const fields = is_static ? klass->GetSFieldsPtr() :
      klass->GetIFieldsPtr();

  // Initialize field_offset
  MemberOffset field_offset(0);
  if (is_static) {
    field_offset = klass->GetFirstReferenceStaticFieldOffsetDuringLinking(
        class_linker->GetImagePointerSize());
  } else {
    ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
    if (super_class != nullptr) {
      CHECK(super_class->IsResolved())
          << klass->PrettyClass() << " " << super_class->PrettyClass();
      field_offset = MemberOffset(super_class->GetObjectSize());
    }
  }

  CHECK_EQ(num_fields == 0, fields == nullptr) << klass->PrettyClass();

  // we want a relatively stable order so that adding new fields
  // minimizes disruption of C++ version such as Class and Method.
  //
  // The overall sort order order is:
  // 1) All object reference fields, sorted alphabetically.
  // 2) All java long (64-bit) integer fields, sorted alphabetically.
  // 3) All java double (64-bit) floating point fields, sorted alphabetically.
  // 4) All java int (32-bit) integer fields, sorted alphabetically.
  // 5) All java float (32-bit) floating point fields, sorted alphabetically.
  // 6) All java char (16-bit) integer fields, sorted alphabetically.
  // 7) All java short (16-bit) integer fields, sorted alphabetically.
  // 8) All java boolean (8-bit) integer fields, sorted alphabetically.
  // 9) All java byte (8-bit) integer fields, sorted alphabetically.
  //
  // (References are first to increase the chance of reference visiting
  // being able to take a fast path using a bitmap of references at the
  // start of the object, see `Class::reference_instance_offsets_`.)
  //
  // Once the fields are sorted in this order we will attempt to fill any gaps
  // that might be present in the memory layout of the structure.
  // Note that we shall not fill gaps between the superclass fields.

  // Collect fields and their "type order index" (see numbered points above).
  const char* old_no_suspend_cause = self->StartAssertNoThreadSuspension(
      "Using plain ArtField references");
  constexpr size_t kStackBufferEntries = 64;  // Avoid allocations for small number of fields.
  FieldTypeOrderAndIndex stack_buffer[kStackBufferEntries];
  std::vector<FieldTypeOrderAndIndex> heap_buffer;
  ArrayRef<FieldTypeOrderAndIndex> sorted_fields;
  if (num_fields <= kStackBufferEntries) {
    sorted_fields = ArrayRef<FieldTypeOrderAndIndex>(stack_buffer, num_fields);
  } else {
    heap_buffer.resize(num_fields);
    sorted_fields = ArrayRef<FieldTypeOrderAndIndex>(heap_buffer);
  }
  size_t num_reference_fields = 0;
  size_t primitive_fields_start = num_fields;
  DCHECK_LE(num_fields, 1u << 16);
  for (size_t i = 0; i != num_fields; ++i) {
    ArtField* field = &fields->At(i);
    const char* descriptor = field->GetTypeDescriptor();
    FieldTypeOrder field_type_order = FieldTypeOrderFromFirstDescriptorCharacter(descriptor[0]);
    uint16_t field_index = dchecked_integral_cast<uint16_t>(i);
    // Insert references to the start, other fields to the end.
    DCHECK_LT(num_reference_fields, primitive_fields_start);
    if (field_type_order == FieldTypeOrder::kReference) {
      sorted_fields[num_reference_fields] = { field_type_order, field_index };
      ++num_reference_fields;
    } else {
      --primitive_fields_start;
      sorted_fields[primitive_fields_start] = { field_type_order, field_index };
    }
  }
  DCHECK_EQ(num_reference_fields, primitive_fields_start);

  // Reference fields are already sorted by field index (and dex field index).
  DCHECK(std::is_sorted(
      sorted_fields.begin(),
      sorted_fields.begin() + num_reference_fields,
      [fields](const auto& lhs, const auto& rhs) REQUIRES_SHARED(Locks::mutator_lock_) {
        ArtField* lhs_field = &fields->At(lhs.field_index);
        ArtField* rhs_field = &fields->At(rhs.field_index);
        CHECK_EQ(lhs_field->GetTypeAsPrimitiveType(), Primitive::kPrimNot);
        CHECK_EQ(rhs_field->GetTypeAsPrimitiveType(), Primitive::kPrimNot);
        CHECK_EQ(lhs_field->GetDexFieldIndex() < rhs_field->GetDexFieldIndex(),
                 lhs.field_index < rhs.field_index);
        return lhs_field->GetDexFieldIndex() < rhs_field->GetDexFieldIndex();
      }));
  // Primitive fields were stored in reverse order of their field index (and dex field index).
  DCHECK(std::is_sorted(
      sorted_fields.begin() + primitive_fields_start,
      sorted_fields.end(),
      [fields](const auto& lhs, const auto& rhs) REQUIRES_SHARED(Locks::mutator_lock_) {
        ArtField* lhs_field = &fields->At(lhs.field_index);
        ArtField* rhs_field = &fields->At(rhs.field_index);
        CHECK_NE(lhs_field->GetTypeAsPrimitiveType(), Primitive::kPrimNot);
        CHECK_NE(rhs_field->GetTypeAsPrimitiveType(), Primitive::kPrimNot);
        CHECK_EQ(lhs_field->GetDexFieldIndex() > rhs_field->GetDexFieldIndex(),
                 lhs.field_index > rhs.field_index);
        return lhs.field_index > rhs.field_index;
      }));
  // Sort the primitive fields by the field type order, then field index.
  std::sort(sorted_fields.begin() + primitive_fields_start,
            sorted_fields.end(),
            [](const auto& lhs, const auto& rhs) {
              if (lhs.field_type_order != rhs.field_type_order) {
                return lhs.field_type_order < rhs.field_type_order;
              } else {
                return lhs.field_index < rhs.field_index;
              }
            });
  // Primitive fields are now sorted by field size (descending), then type, then field index.
  DCHECK(std::is_sorted(
      sorted_fields.begin() + primitive_fields_start,
      sorted_fields.end(),
      [fields](const auto& lhs, const auto& rhs) REQUIRES_SHARED(Locks::mutator_lock_) {
        ArtField* lhs_field = &fields->At(lhs.field_index);
        ArtField* rhs_field = &fields->At(rhs.field_index);
        Primitive::Type lhs_type = lhs_field->GetTypeAsPrimitiveType();
        CHECK_NE(lhs_type, Primitive::kPrimNot);
        Primitive::Type rhs_type = rhs_field->GetTypeAsPrimitiveType();
        CHECK_NE(rhs_type, Primitive::kPrimNot);
        if (lhs_type != rhs_type) {
          size_t lhs_size = Primitive::ComponentSize(lhs_type);
          size_t rhs_size = Primitive::ComponentSize(rhs_type);
          return (lhs_size != rhs_size) ? (lhs_size > rhs_size) : (lhs_type < rhs_type);
        } else {
          return lhs_field->GetDexFieldIndex() < rhs_field->GetDexFieldIndex();
        }
      }));

  // Process reference fields.
  FieldGaps field_gaps;
  size_t index = 0u;
  if (num_reference_fields != 0u) {
    constexpr size_t kReferenceSize = sizeof(mirror::HeapReference<mirror::Object>);
    field_offset = field_gaps.AlignFieldOffset<kReferenceSize>(field_offset);
    for (; index != num_reference_fields; ++index) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      field_offset = AssignFieldOffset<kReferenceSize>(field, field_offset);
    }
  }
  // Process 64-bit fields.
  if (index != num_fields &&
      sorted_fields[index].field_type_order <= FieldTypeOrder::kLast64BitType) {
    field_offset = field_gaps.AlignFieldOffset<8u>(field_offset);
    while (index != num_fields &&
           sorted_fields[index].field_type_order <= FieldTypeOrder::kLast64BitType) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      field_offset = AssignFieldOffset<8u>(field, field_offset);
      ++index;
    }
  }
  // Process 32-bit fields.
  if (index != num_fields &&
      sorted_fields[index].field_type_order <= FieldTypeOrder::kLast32BitType) {
    field_offset = field_gaps.AlignFieldOffset<4u>(field_offset);
    if (field_gaps.HasGap<4u>()) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      AssignFieldOffset<4u>(field, field_gaps.ReleaseGap<4u>());  // Ignore return value.
      ++index;
      DCHECK(!field_gaps.HasGap<4u>());  // There can be only one gap for a 32-bit field.
    }
    while (index != num_fields &&
           sorted_fields[index].field_type_order <= FieldTypeOrder::kLast32BitType) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      field_offset = AssignFieldOffset<4u>(field, field_offset);
      ++index;
    }
  }
  // Process 16-bit fields.
  if (index != num_fields &&
      sorted_fields[index].field_type_order <= FieldTypeOrder::kLast16BitType) {
    field_offset = field_gaps.AlignFieldOffset<2u>(field_offset);
    while (index != num_fields &&
           sorted_fields[index].field_type_order <= FieldTypeOrder::kLast16BitType &&
           field_gaps.HasGap<2u>()) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      AssignFieldOffset<2u>(field, field_gaps.ReleaseGap<2u>());  // Ignore return value.
      ++index;
    }
    while (index != num_fields &&
           sorted_fields[index].field_type_order <= FieldTypeOrder::kLast16BitType) {
      ArtField* field = &fields->At(sorted_fields[index].field_index);
      field_offset = AssignFieldOffset<2u>(field, field_offset);
      ++index;
    }
  }
  // Process 8-bit fields.
  for (; index != num_fields && field_gaps.HasGap<1u>(); ++index) {
    ArtField* field = &fields->At(sorted_fields[index].field_index);
    AssignFieldOffset<1u>(field, field_gaps.ReleaseGap<1u>());  // Ignore return value.
  }
  for (; index != num_fields; ++index) {
    ArtField* field = &fields->At(sorted_fields[index].field_index);
    field_offset = AssignFieldOffset<1u>(field, field_offset);
  }

  self->EndAssertNoThreadSuspension(old_no_suspend_cause);

  // We lie to the GC about the java.lang.ref.Reference.referent field, so it doesn't scan it.
  DCHECK_IMPLIES(class_linker->init_done_, !klass->DescriptorEquals("Ljava/lang/ref/Reference;"));
  if (!is_static &&
      UNLIKELY(!class_linker->init_done_) &&
      klass->DescriptorEquals("Ljava/lang/ref/Reference;")) {
    // We know there are no non-reference fields in the Reference classes, and we know
    // that 'referent' is alphabetically last, so this is easy...
    CHECK_EQ(num_reference_fields, num_fields) << klass->PrettyClass();
    CHECK_STREQ(fields->At(num_fields - 1).GetName(), "referent")
        << klass->PrettyClass();
    --num_reference_fields;
  }

  size_t size = field_offset.Uint32Value();
  // Update klass
  if (is_static) {
    klass->SetNumReferenceStaticFields(num_reference_fields);
    *class_size = size;
  } else {
    klass->SetNumReferenceInstanceFields(num_reference_fields);
    ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
    if (num_reference_fields == 0 || super_class == nullptr) {
      // object has one reference field, klass, but we ignore it since we always visit the class.
      // super_class is null iff the class is java.lang.Object.
      if (super_class == nullptr ||
          (super_class->GetClassFlags() & mirror::kClassFlagNoReferenceFields) != 0) {
        klass->SetClassFlags(klass->GetClassFlags() | mirror::kClassFlagNoReferenceFields);
      }
    }
    if (kIsDebugBuild) {
      DCHECK_EQ(super_class == nullptr, klass->DescriptorEquals("Ljava/lang/Object;"));
      size_t total_reference_instance_fields = 0;
      ObjPtr<mirror::Class> cur_super = klass.Get();
      while (cur_super != nullptr) {
        total_reference_instance_fields += cur_super->NumReferenceInstanceFieldsDuringLinking();
        cur_super = cur_super->GetSuperClass();
      }
      if (super_class == nullptr) {
        CHECK_EQ(total_reference_instance_fields, 1u) << klass->PrettyDescriptor();
      } else {
        // Check that there is at least num_reference_fields other than Object.class.
        CHECK_GE(total_reference_instance_fields, 1u + num_reference_fields)
            << klass->PrettyClass();
      }
    }
    if (!klass->IsVariableSize()) {
      std::string temp;
      DCHECK_GE(size, sizeof(mirror::Object)) << klass->GetDescriptor(&temp);
      size_t previous_size = klass->GetObjectSize();
      if (previous_size != 0) {
        // Make sure that we didn't originally have an incorrect size.
        CHECK_EQ(previous_size, size) << klass->GetDescriptor(&temp);
      }
      klass->SetObjectSize(size);
    }
  }

  if (kIsDebugBuild) {
    // Make sure that the fields array is ordered by name but all reference
    // offsets are at the beginning as far as alignment allows.
    MemberOffset start_ref_offset = is_static
        ? klass->GetFirstReferenceStaticFieldOffsetDuringLinking(class_linker->image_pointer_size_)
        : klass->GetFirstReferenceInstanceFieldOffset();
    MemberOffset end_ref_offset(start_ref_offset.Uint32Value() +
                                num_reference_fields *
                                    sizeof(mirror::HeapReference<mirror::Object>));
    MemberOffset current_ref_offset = start_ref_offset;
    for (size_t i = 0; i < num_fields; i++) {
      ArtField* field = &fields->At(i);
      VLOG(class_linker) << "LinkFields: " << (is_static ? "static" : "instance")
          << " class=" << klass->PrettyClass() << " field=" << field->PrettyField()
          << " offset=" << field->GetOffsetDuringLinking();
      if (i != 0) {
        ArtField* const prev_field = &fields->At(i - 1);
        // NOTE: The field names can be the same. This is not possible in the Java language
        // but it's valid Java/dex bytecode and for example proguard can generate such bytecode.
        DCHECK_LE(strcmp(prev_field->GetName(), field->GetName()), 0);
      }
      Primitive::Type type = field->GetTypeAsPrimitiveType();
      bool is_primitive = type != Primitive::kPrimNot;
      if (klass->DescriptorEquals("Ljava/lang/ref/Reference;") &&
          strcmp("referent", field->GetName()) == 0) {
        is_primitive = true;  // We lied above, so we have to expect a lie here.
      }
      MemberOffset offset = field->GetOffsetDuringLinking();
      if (is_primitive) {
        if (offset.Uint32Value() < end_ref_offset.Uint32Value()) {
          // Shuffled before references.
          size_t type_size = Primitive::ComponentSize(type);
          CHECK_LT(type_size, sizeof(mirror::HeapReference<mirror::Object>));
          CHECK_LT(offset.Uint32Value(), start_ref_offset.Uint32Value());
          CHECK_LE(offset.Uint32Value() + type_size, start_ref_offset.Uint32Value());
          CHECK(!IsAligned<sizeof(mirror::HeapReference<mirror::Object>)>(offset.Uint32Value()));
        }
      } else {
        CHECK_EQ(current_ref_offset.Uint32Value(), offset.Uint32Value());
        current_ref_offset = MemberOffset(current_ref_offset.Uint32Value() +
                                          sizeof(mirror::HeapReference<mirror::Object>));
      }
    }
    CHECK_EQ(current_ref_offset.Uint32Value(), end_ref_offset.Uint32Value());
  }
  return true;
}

bool ClassLinker::LinkInstanceFields(Thread* self, Handle<mirror::Class> klass) {
  CHECK(klass != nullptr);
  return LinkFieldsHelper::LinkFields(this, self, klass, false, nullptr);
}

bool ClassLinker::LinkStaticFields(Thread* self, Handle<mirror::Class> klass, size_t* class_size) {
  CHECK(klass != nullptr);
  return LinkFieldsHelper::LinkFields(this, self, klass, true, class_size);
}

enum class RecordElementType : uint8_t {
  kNames = 0,
  kTypes = 1,
  kSignatures = 2,
  kAnnotationVisibilities = 3,
  kAnnotations = 4
};

static const char* kRecordElementNames[] = {"componentNames",
                                            "componentTypes",
                                            "componentSignatures",
                                            "componentAnnotationVisibilities",
                                            "componentAnnotations"};

class RecordAnnotationVisitor final : public annotations::AnnotationVisitor {
 public:
  RecordAnnotationVisitor() {}

  bool ValidateCounts() {
    if (is_error_) {
      return false;
    }

    // Verify the counts.
    bool annotation_element_exists =
        (signatures_count_ != UINT32_MAX) || (annotations_count_ != UINT32_MAX);
    if (count_ >= 2) {
      SetErrorMsg("Record class can't have more than one @Record Annotation");
    } else if (names_count_ == UINT32_MAX) {
      SetErrorMsg("componentNames element is required");
    } else if (types_count_ == UINT32_MAX) {
      SetErrorMsg("componentTypes element is required");
    } else if (names_count_ != types_count_) {  // Every component must have a name and a type.
      SetErrorMsg(StringPrintf(
          "componentTypes is expected to have %i, but has %i types", names_count_, types_count_));
      // The other 3 elements are optional, but is expected to have the same count if it exists.
    } else if (signatures_count_ != UINT32_MAX && signatures_count_ != names_count_) {
      SetErrorMsg(StringPrintf("componentSignatures size is %i, but is expected to be %i",
                               signatures_count_,
                               names_count_));
    } else if (annotation_element_exists && visibilities_count_ != names_count_) {
      SetErrorMsg(
          StringPrintf("componentAnnotationVisibilities size is %i, but is expected to be %i",
                       visibilities_count_,
                       names_count_));
    } else if (annotation_element_exists && annotations_count_ != names_count_) {
      SetErrorMsg(StringPrintf("componentAnnotations size is %i, but is expected to be %i",
                               annotations_count_,
                               names_count_));
    }

    return !is_error_;
  }

  const std::string& GetErrorMsg() { return error_msg_; }

  bool IsRecordAnnotationFound() { return count_ != 0; }

  annotations::VisitorStatus VisitAnnotation(const char* descriptor, uint8_t visibility) override {
    if (is_error_) {
      return annotations::VisitorStatus::kVisitBreak;
    }

    if (visibility != DexFile::kDexVisibilitySystem) {
      return annotations::VisitorStatus::kVisitNext;
    }

    if (strcmp(descriptor, "Ldalvik/annotation/Record;") != 0) {
      return annotations::VisitorStatus::kVisitNext;
    }

    count_ += 1;
    if (count_ >= 2) {
      return annotations::VisitorStatus::kVisitBreak;
    }
    return annotations::VisitorStatus::kVisitInner;
  }

  annotations::VisitorStatus VisitAnnotationElement(const char* element_name,
                                                    uint8_t type,
                                                    [[maybe_unused]] const JValue& value) override {
    if (is_error_) {
      return annotations::VisitorStatus::kVisitBreak;
    }

    RecordElementType visiting_type;
    uint32_t* element_count;
    if (strcmp(element_name, "componentNames") == 0) {
      visiting_type = RecordElementType::kNames;
      element_count = &names_count_;
    } else if (strcmp(element_name, "componentTypes") == 0) {
      visiting_type = RecordElementType::kTypes;
      element_count = &types_count_;
    } else if (strcmp(element_name, "componentSignatures") == 0) {
      visiting_type = RecordElementType::kSignatures;
      element_count = &signatures_count_;
    } else if (strcmp(element_name, "componentAnnotationVisibilities") == 0) {
      visiting_type = RecordElementType::kAnnotationVisibilities;
      element_count = &visibilities_count_;
    } else if (strcmp(element_name, "componentAnnotations") == 0) {
      visiting_type = RecordElementType::kAnnotations;
      element_count = &annotations_count_;
    } else {
      // ignore this element that could be introduced in the future ART.
      return annotations::VisitorStatus::kVisitNext;
    }

    if ((*element_count) != UINT32_MAX) {
      SetErrorMsg(StringPrintf("Two %s annotation elements are found but only one is expected",
                               kRecordElementNames[static_cast<uint8_t>(visiting_type)]));
      return annotations::VisitorStatus::kVisitBreak;
    }

    if (type != DexFile::kDexAnnotationArray) {
      SetErrorMsg(StringPrintf("%s must be array type", element_name));
      return annotations::VisitorStatus::kVisitBreak;
    }

    *element_count = 0;
    visiting_type_ = visiting_type;
    return annotations::VisitorStatus::kVisitInner;
  }

  annotations::VisitorStatus VisitArrayElement(uint8_t depth,
                                               uint32_t index,
                                               uint8_t type,
                                               [[maybe_unused]] const JValue& value) override {
    if (is_error_) {
      return annotations::VisitorStatus::kVisitBreak;
    }
    switch (visiting_type_) {
      case RecordElementType::kNames: {
        if (depth == 0) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationString, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          names_count_++;
          return annotations::VisitorStatus::kVisitNext;
        }
        break;
      }
      case RecordElementType::kTypes: {
        if (depth == 0) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationType, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          types_count_++;
          return annotations::VisitorStatus::kVisitNext;
        }
        break;
      }
      case RecordElementType::kSignatures: {
        if (depth == 0) {
          // kDexAnnotationNull implies no generic signature for the component.
          if (type != DexFile::kDexAnnotationNull &&
              !ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationAnnotation, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          signatures_count_++;
          return annotations::VisitorStatus::kVisitNext;
        }
        break;
      }
      case RecordElementType::kAnnotationVisibilities: {
        if (depth == 0) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationArray, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          visibilities_count_++;
          return annotations::VisitorStatus::kVisitInner;
        } else if (depth == 1) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationByte, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          return annotations::VisitorStatus::kVisitNext;
        }
        break;
      }
      case RecordElementType::kAnnotations: {
        if (depth == 0) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationArray, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          annotations_count_++;
          return annotations::VisitorStatus::kVisitInner;
        } else if (depth == 1) {
          if (!ExpectedTypeOrError(
                  type, DexFile::kDexAnnotationAnnotation, visiting_type_, index, depth)) {
            return annotations::VisitorStatus::kVisitBreak;
          }
          return annotations::VisitorStatus::kVisitNext;
        }
        break;
      }
    }

    // Should never happen if every next depth level is handled above whenever kVisitInner is
    // returned.
    DCHECK(false) << StringPrintf("Unexpected depth %i for element %s",
                                  depth,
                                  kRecordElementNames[static_cast<uint8_t>(visiting_type_)]);
    return annotations::VisitorStatus::kVisitBreak;
  }

 private:
  bool is_error_ = false;
  uint32_t count_ = 0;
  uint32_t names_count_ = UINT32_MAX;
  uint32_t types_count_ = UINT32_MAX;
  uint32_t signatures_count_ = UINT32_MAX;
  uint32_t visibilities_count_ = UINT32_MAX;
  uint32_t annotations_count_ = UINT32_MAX;
  std::string error_msg_;
  RecordElementType visiting_type_;

  inline bool ExpectedTypeOrError(uint8_t type,
                                  uint8_t expected,
                                  RecordElementType visiting_type,
                                  uint8_t depth,
                                  uint32_t index) {
    if (type == expected) {
      return true;
    }

    SetErrorMsg(StringPrintf(
        "Expect 0x%02x type but got 0x%02x at the index %i and depth %i for the element %s",
        expected,
        type,
        index,
        depth,
        kRecordElementNames[static_cast<uint8_t>(visiting_type)]));
    return false;
  }

  void SetErrorMsg(const std::string& msg) {
    is_error_ = true;
    error_msg_ = msg;
  }

  DISALLOW_COPY_AND_ASSIGN(RecordAnnotationVisitor);
};

/**
 * Set kClassFlagRecord and verify if klass is a record class.
 * If the verification fails, a pending java exception is thrown.
 *
 * @return false if verification fails. If klass isn't a record class,
 * it should always return true.
 */
bool ClassLinker::VerifyRecordClass(Handle<mirror::Class> klass, ObjPtr<mirror::Class> super) {
  CHECK(klass != nullptr);
  // First, we check the conditions specified in java.lang.Class#isRecord().
  // If any of the conditions isn't fulfilled, it's not a record class and
  // ART should treat it as a normal class even if it's inherited from java.lang.Record.
  if (!klass->IsFinal()) {
    return true;
  }

  if (super == nullptr) {
    return true;
  }

  // Compare the string directly when this ClassLinker is initializing before
  // WellKnownClasses initializes
  if (WellKnownClasses::java_lang_Record == nullptr) {
    if (!super->DescriptorEquals("Ljava/lang/Record;")) {
      return true;
    }
  } else {
    ObjPtr<mirror::Class> java_lang_Record =
        WellKnownClasses::ToClass(WellKnownClasses::java_lang_Record);
    if (super.Ptr() != java_lang_Record.Ptr()) {
      return true;
    }
  }

  // Verify @dalvik.annotation.Record
  // The annotation has a mandatory element componentNames[] and componentTypes[] of the same size.
  // componentSignatures[], componentAnnotationVisibilities[][], componentAnnotations[][] are
  // optional, but should have the same size if it exists.
  RecordAnnotationVisitor visitor;
  annotations::VisitClassAnnotations(klass, &visitor);
  if (!visitor.IsRecordAnnotationFound()) {
    return true;
  }

  if (!visitor.ValidateCounts()) {
    ThrowClassFormatError(klass.Get(), "%s", visitor.GetErrorMsg().c_str());
    return false;
  }

  // Set kClassFlagRecord.
  klass->SetRecordClass();
  return true;
}

//  Set the bitmap of reference instance field offsets.
void ClassLinker::CreateReferenceInstanceOffsets(Handle<mirror::Class> klass) {
  uint32_t reference_offsets = 0;
  ObjPtr<mirror::Class> super_class = klass->GetSuperClass();
  // Leave the reference offsets as 0 for mirror::Object (the class field is handled specially).
  if (super_class != nullptr) {
    reference_offsets = super_class->GetReferenceInstanceOffsets();
    // Compute reference offsets unless our superclass overflowed.
    if (reference_offsets != mirror::Class::kClassWalkSuper) {
      size_t num_reference_fields = klass->NumReferenceInstanceFieldsDuringLinking();
      if (num_reference_fields != 0u) {
        // All of the fields that contain object references are guaranteed be grouped in memory
        // starting at an appropriately aligned address after super class object data.
        uint32_t start_offset = RoundUp(super_class->GetObjectSize(),
                                        sizeof(mirror::HeapReference<mirror::Object>));
        uint32_t start_bit = (start_offset - mirror::kObjectHeaderSize) /
            sizeof(mirror::HeapReference<mirror::Object>);
        if (start_bit + num_reference_fields > 32) {
          reference_offsets = mirror::Class::kClassWalkSuper;
        } else {
          reference_offsets |= (0xffffffffu << start_bit) &
                               (0xffffffffu >> (32 - (start_bit + num_reference_fields)));
        }
      }
    }
  }
  klass->SetReferenceInstanceOffsets(reference_offsets);
}

ObjPtr<mirror::String> ClassLinker::DoResolveString(dex::StringIndex string_idx,
                                                    ObjPtr<mirror::DexCache> dex_cache) {
  StackHandleScope<1> hs(Thread::Current());
  Handle<mirror::DexCache> h_dex_cache(hs.NewHandle(dex_cache));
  return DoResolveString(string_idx, h_dex_cache);
}

ObjPtr<mirror::String> ClassLinker::DoResolveString(dex::StringIndex string_idx,
                                                    Handle<mirror::DexCache> dex_cache) {
  const DexFile& dex_file = *dex_cache->GetDexFile();
  uint32_t utf16_length;
  const char* utf8_data = dex_file.StringDataAndUtf16LengthByIdx(string_idx, &utf16_length);
  ObjPtr<mirror::String> string = intern_table_->InternStrong(utf16_length, utf8_data);
  if (string != nullptr) {
    dex_cache->SetResolvedString(string_idx, string);
  }
  return string;
}

ObjPtr<mirror::String> ClassLinker::DoLookupString(dex::StringIndex string_idx,
                                                   ObjPtr<mirror::DexCache> dex_cache) {
  DCHECK(dex_cache != nullptr);
  const DexFile& dex_file = *dex_cache->GetDexFile();
  uint32_t utf16_length;
  const char* utf8_data = dex_file.StringDataAndUtf16LengthByIdx(string_idx, &utf16_length);
  ObjPtr<mirror::String> string =
      intern_table_->LookupStrong(Thread::Current(), utf16_length, utf8_data);
  if (string != nullptr) {
    dex_cache->SetResolvedString(string_idx, string);
  }
  return string;
}

ObjPtr<mirror::Class> ClassLinker::DoLookupResolvedType(dex::TypeIndex type_idx,
                                                        ObjPtr<mirror::Class> referrer) {
  return DoLookupResolvedType(type_idx, referrer->GetDexCache(), referrer->GetClassLoader());
}

ObjPtr<mirror::Class> ClassLinker::DoLookupResolvedType(dex::TypeIndex type_idx,
                                                        ObjPtr<mirror::DexCache> dex_cache,
                                                        ObjPtr<mirror::ClassLoader> class_loader) {
  DCHECK(dex_cache->GetClassLoader() == class_loader);
  const DexFile& dex_file = *dex_cache->GetDexFile();
  const char* descriptor = dex_file.StringByTypeIdx(type_idx);
  ObjPtr<mirror::Class> type = LookupResolvedType(descriptor, class_loader);
  if (type != nullptr) {
    DCHECK(type->IsResolved());
    dex_cache->SetResolvedType(type_idx, type);
  }
  return type;
}

ObjPtr<mirror::Class> ClassLinker::LookupResolvedType(const char* descriptor,
                                                      ObjPtr<mirror::ClassLoader> class_loader) {
  DCHECK_NE(*descriptor, '\0') << "descriptor is empty string";
  ObjPtr<mirror::Class> type = nullptr;
  if (descriptor[1] == '\0') {
    // only the descriptors of primitive types should be 1 character long, also avoid class lookup
    // for primitive classes that aren't backed by dex files.
    type = LookupPrimitiveClass(descriptor[0]);
  } else {
    Thread* const self = Thread::Current();
    DCHECK(self != nullptr);
    const size_t hash = ComputeModifiedUtf8Hash(descriptor);
    // Find the class in the loaded classes table.
    type = LookupClass(self, descriptor, hash, class_loader);
  }
  return (type != nullptr && type->IsResolved()) ? type : nullptr;
}

template <typename RefType>
ObjPtr<mirror::Class> ClassLinker::DoResolveType(dex::TypeIndex type_idx, RefType referrer) {
  StackHandleScope<2> hs(Thread::Current());
  Handle<mirror::DexCache> dex_cache(hs.NewHandle(referrer->GetDexCache()));
  Handle<mirror::ClassLoader> class_loader(hs.NewHandle(referrer->GetClassLoader()));
  return DoResolveType(type_idx, dex_cache, class_loader);
}

// Instantiate the above.
template ObjPtr<mirror::Class> ClassLinker::DoResolveType(dex::TypeIndex type_idx,
                                                          ArtField* referrer);
template ObjPtr<mirror::Class> ClassLinker::DoResolveType(dex::TypeIndex type_idx,
                                                          ArtMethod* referrer);
template ObjPtr<mirror::Class> ClassLinker::DoResolveType(dex::TypeIndex type_idx,
                                                          ObjPtr<mirror::Class> referrer);

ObjPtr<mirror::Class> ClassLinker::DoResolveType(dex::TypeIndex type_idx,
                                                 Handle<mirror::DexCache> dex_cache,
                                                 Handle<mirror::ClassLoader> class_loader) {
  DCHECK(dex_cache->GetClassLoader() == class_loader.Get());
  Thread* self = Thread::Current();
  const char* descriptor = dex_cache->GetDexFile()->StringByTypeIdx(type_idx);
  ObjPtr<mirror::Class> resolved = FindClass(self, descriptor, class_loader);
  if (resolved != nullptr) {
    // TODO: we used to throw here if resolved's class loader was not the
    //       boot class loader. This was to permit different classes with the
    //       same name to be loaded simultaneously by different loaders
    dex_cache->SetResolvedType(type_idx, resolved);
  } else {
    CHECK(self->IsExceptionPending())
        << "Expected pending exception for failed resolution of: " << descriptor;
    // Convert a ClassNotFoundException to a NoClassDefFoundError.
    StackHandleScope<1> hs(self);
    Handle<mirror::Throwable> cause(hs.NewHandle(self->GetException()));
    if (cause->InstanceOf(GetClassRoot(ClassRoot::kJavaLangClassNotFoundException, this))) {
      DCHECK(resolved == nullptr);  // No Handle needed to preserve resolved.
      self->ClearException();
      ThrowNoClassDefFoundError("Failed resolution of: %s", descriptor);
      self->GetException()->SetCause(cause.Get());
    }
  }
  DCHECK((resolved == nullptr) || resolved->IsResolved())
      << resolved->PrettyDescriptor() << " " << resolved->GetStatus();
  return resolved;
}

ArtMethod* ClassLinker::FindResolvedMethod(ObjPtr<mirror::Class> klass,
                                           ObjPtr<mirror::DexCache> dex_cache,
                                           ObjPtr<mirror::ClassLoader> class_loader,
                                           uint32_t method_idx) {
  DCHECK(dex_cache->GetClassLoader() == class_loader);
  // Search for the method using dex_cache and method_idx. The Class::Find*Method()
  // functions can optimize the search if the dex_cache is the same as the DexCache
  // of the class, with fall-back to name and signature search otherwise.
  ArtMethod* resolved = nullptr;
  if (klass->IsInterface()) {
    resolved = klass->FindInterfaceMethod(dex_cache, method_idx, image_pointer_size_);
  } else {
    resolved = klass->FindClassMethod(dex_cache, method_idx, image_pointer_size_);
  }
  DCHECK(resolved == nullptr || resolved->GetDeclaringClassUnchecked() != nullptr);
  if (resolved != nullptr &&
      // We pass AccessMethod::kNone instead of kLinking to not warn yet on the
      // access, as we'll be looking if the method can be accessed through an
      // interface.
      hiddenapi::ShouldDenyAccessToMember(resolved,
                                          hiddenapi::AccessContext(class_loader, dex_cache),
                                          hiddenapi::AccessMethod::kNone)) {
    // The resolved method that we have found cannot be accessed due to
    // hiddenapi (typically it is declared up the hierarchy and is not an SDK
    // method). Try to find an interface method from the implemented interfaces which is
    // part of the SDK.
    ArtMethod* itf_method = klass->FindAccessibleInterfaceMethod(resolved, image_pointer_size_);
    if (itf_method == nullptr) {
      // No interface method. Call ShouldDenyAccessToMember again but this time
      // with AccessMethod::kLinking to ensure that an appropriate warning is
      // logged.
      hiddenapi::ShouldDenyAccessToMember(resolved,
                                          hiddenapi::AccessContext(class_loader, dex_cache),
                                          hiddenapi::AccessMethod::kLinking);
      resolved = nullptr;
    } else {
      // We found an interface method that is accessible, continue with the resolved method.
    }
  }
  if (resolved != nullptr) {
    // In case of jmvti, the dex file gets verified before being registered, so first
    // check if it's registered before checking class tables.
    const DexFile& dex_file = *dex_cache->GetDexFile();
    DCHECK_IMPLIES(
        IsDexFileRegistered(Thread::Current(), dex_file),
        FindClassTable(Thread::Current(), dex_cache) == ClassTableForClassLoader(class_loader))
        << "DexFile referrer: " << dex_file.GetLocation()
        << " ClassLoader: " << DescribeLoaders(class_loader, "");
    // Be a good citizen and update the dex cache to speed subsequent calls.
    dex_cache->SetResolvedMethod(method_idx, resolved);
    // Disable the following invariant check as the verifier breaks it. b/73760543
    // const DexFile::MethodId& method_id = dex_file.GetMethodId(method_idx);
    // DCHECK(LookupResolvedType(method_id.class_idx_, dex_cache, class_loader) != nullptr)
    //    << "Method: " << resolved->PrettyMethod() << ", "
    //    << "Class: " << klass->PrettyClass() << " (" << klass->GetStatus() << "), "
    //    << "DexFile referrer: " << dex_file.GetLocation();
  }
  return resolved;
}

// Returns true if `method` is either null or hidden.
// Does not print any warnings if it is hidden.
static bool CheckNoSuchMethod(ArtMethod* method,
                              ObjPtr<mirror::DexCache> dex_cache,
                              ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(dex_cache->GetClassLoader().Ptr() == class_loader.Ptr());
  return method == nullptr ||
         hiddenapi::ShouldDenyAccessToMember(method,
                                             hiddenapi::AccessContext(class_loader, dex_cache),
                                             hiddenapi::AccessMethod::kNone);  // no warnings
}

ArtMethod* ClassLinker::FindIncompatibleMethod(ObjPtr<mirror::Class> klass,
                                               ObjPtr<mirror::DexCache> dex_cache,
                                               ObjPtr<mirror::ClassLoader> class_loader,
                                               uint32_t method_idx) {
  DCHECK(dex_cache->GetClassLoader() == class_loader);
  if (klass->IsInterface()) {
    ArtMethod* method = klass->FindClassMethod(dex_cache, method_idx, image_pointer_size_);
    return CheckNoSuchMethod(method, dex_cache, class_loader) ? nullptr : method;
  } else {
    // If there was an interface method with the same signature, we would have
    // found it in the "copied" methods. Only DCHECK that the interface method
    // really does not exist.
    if (kIsDebugBuild) {
      ArtMethod* method =
          klass->FindInterfaceMethod(dex_cache, method_idx, image_pointer_size_);
      CHECK(CheckNoSuchMethod(method, dex_cache, class_loader) ||
            (klass->FindAccessibleInterfaceMethod(method, image_pointer_size_) == nullptr));
    }
    return nullptr;
  }
}

ArtMethod* ClassLinker::ResolveMethodWithoutInvokeType(uint32_t method_idx,
                                                       Handle<mirror::DexCache> dex_cache,
                                                       Handle<mirror::ClassLoader> class_loader) {
  DCHECK(dex_cache->GetClassLoader() == class_loader.Get());
  ArtMethod* resolved = dex_cache->GetResolvedMethod(method_idx);
  Thread::PoisonObjectPointersIfDebug();
  if (resolved != nullptr) {
    DCHECK(!resolved->IsRuntimeMethod());
    DCHECK(resolved->GetDeclaringClassUnchecked() != nullptr) << resolved->GetDexMethodIndex();
    return resolved;
  }
  // Fail, get the declaring class.
  const dex::MethodId& method_id = dex_cache->GetDexFile()->GetMethodId(method_idx);
  ObjPtr<mirror::Class> klass = ResolveType(method_id.class_idx_, dex_cache, class_loader);
  if (klass == nullptr) {
    Thread::Current()->AssertPendingException();
    return nullptr;
  }
  return FindResolvedMethod(klass, dex_cache.Get(), class_loader.Get(), method_idx);
}

ArtField* ClassLinker::LookupResolvedField(uint32_t field_idx,
                                           ObjPtr<mirror::DexCache> dex_cache,
                                           ObjPtr<mirror::ClassLoader> class_loader,
                                           bool is_static) {
  DCHECK(dex_cache->GetClassLoader().Ptr() == class_loader.Ptr());
  const DexFile& dex_file = *dex_cache->GetDexFile();
  const dex::FieldId& field_id = dex_file.GetFieldId(field_idx);
  ObjPtr<mirror::Class> klass = dex_cache->GetResolvedType(field_id.class_idx_);
  if (klass == nullptr) {
    klass = LookupResolvedType(field_id.class_idx_, dex_cache, class_loader);
  }
  if (klass == nullptr) {
    // The class has not been resolved yet, so the field is also unresolved.
    return nullptr;
  }
  DCHECK(klass->IsResolved());

  return FindResolvedField(klass, dex_cache, class_loader, field_idx, is_static);
}

ArtField* ClassLinker::ResolveFieldJLS(uint32_t field_idx,
                                       Handle<mirror::DexCache> dex_cache,
                                       Handle<mirror::ClassLoader> class_loader) {
  DCHECK(dex_cache != nullptr);
  DCHECK(dex_cache->GetClassLoader() == class_loader.Get());
  ArtField* resolved = dex_cache->GetResolvedField(field_idx);
  Thread::PoisonObjectPointersIfDebug();
  if (resolved != nullptr) {
    return resolved;
  }
  const DexFile& dex_file = *dex_cache->GetDexFile();
  const dex::FieldId& field_id = dex_file.GetFieldId(field_idx);
  ObjPtr<mirror::Class> klass = ResolveType(field_id.class_idx_, dex_cache, class_loader);
  if (klass == nullptr) {
    DCHECK(Thread::Current()->IsExceptionPending());
    return nullptr;
  }

  resolved = FindResolvedFieldJLS(klass, dex_cache.Get(), class_loader.Get(), field_idx);
  if (resolved == nullptr) {
    const char* name = dex_file.GetFieldName(field_id);
    const char* type = dex_file.GetFieldTypeDescriptor(field_id);
    ThrowNoSuchFieldError("", klass, type, name);
  }
  return resolved;
}

ArtField* ClassLinker::FindResolvedField(ObjPtr<mirror::Class> klass,
                                         ObjPtr<mirror::DexCache> dex_cache,
                                         ObjPtr<mirror::ClassLoader> class_loader,
                                         uint32_t field_idx,
                                         bool is_static) {
  DCHECK(dex_cache->GetClassLoader() == class_loader);
  ArtField* resolved = is_static ? klass->FindStaticField(dex_cache, field_idx)
                                 : klass->FindInstanceField(dex_cache, field_idx);
  if (resolved != nullptr &&
      hiddenapi::ShouldDenyAccessToMember(resolved,
                                          hiddenapi::AccessContext(class_loader, dex_cache),
                                          hiddenapi::AccessMethod::kLinking)) {
    resolved = nullptr;
  }

  if (resolved != nullptr) {
    dex_cache->SetResolvedField(field_idx, resolved);
  }

  return resolved;
}

ArtField* ClassLinker::FindResolvedFieldJLS(ObjPtr<mirror::Class> klass,
                                            ObjPtr<mirror::DexCache> dex_cache,
                                            ObjPtr<mirror::ClassLoader> class_loader,
                                            uint32_t field_idx) {
  DCHECK(dex_cache->GetClassLoader().Ptr() == class_loader.Ptr());
  ArtField* resolved = klass->FindField(dex_cache, field_idx);

  if (resolved != nullptr &&
      hiddenapi::ShouldDenyAccessToMember(resolved,
                                          hiddenapi::AccessContext(class_loader, dex_cache),
                                          hiddenapi::AccessMethod::kLinking)) {
    resolved = nullptr;
  }

  if (resolved != nullptr) {
    dex_cache->SetResolvedField(field_idx, resolved);
  }

  return resolved;
}

ObjPtr<mirror::MethodType> ClassLinker::ResolveMethodType(
    Thread* self,
    dex::ProtoIndex proto_idx,
    Handle<mirror::DexCache> dex_cache,
    Handle<mirror::ClassLoader> class_loader) {
  DCHECK(Runtime::Current()->IsMethodHandlesEnabled());
  DCHECK(dex_cache != nullptr);
  DCHECK(dex_cache->GetClassLoader() == class_loader.Get());

  ObjPtr<mirror::MethodType> resolved = dex_cache->GetResolvedMethodType(proto_idx);
  if (resolved != nullptr) {
    return resolved;
  }

  VariableSizedHandleScope raw_method_type_hs(self);
  mirror::RawMethodType raw_method_type(&raw_method_type_hs);
  if (!ResolveMethodType(self, proto_idx, dex_cache, class_loader, raw_method_type)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  // The handle scope was filled with return type and paratemer types.
  DCHECK_EQ(raw_method_type_hs.Size(),
            dex_cache->GetDexFile()->GetShortyView(proto_idx).length());
  ObjPtr<mirror::MethodType> method_type = mirror::MethodType::Create(self, raw_method_type);
  if (method_type != nullptr) {
    // Ensure all stores for the newly created MethodType are visible, before we attempt to place
    // it in the DexCache (b/224733324).
    std::atomic_thread_fence(std::memory_order_release);
    dex_cache->SetResolvedMethodType(proto_idx, method_type.Ptr());
  }
  return method_type;
}

bool ClassLinker::ResolveMethodType(Thread* self,
                                    dex::ProtoIndex proto_idx,
                                    Handle<mirror::DexCache> dex_cache,
                                    Handle<mirror::ClassLoader> class_loader,
                                    /*out*/ mirror::RawMethodType method_type) {
  DCHECK(Runtime::Current()->IsMethodHandlesEnabled());
  DCHECK(dex_cache != nullptr);
  DCHECK(dex_cache->GetClassLoader() == class_loader.Get());

  // First resolve the return type.
  const DexFile& dex_file = *dex_cache->GetDexFile();
  const dex::ProtoId& proto_id = dex_file.GetProtoId(proto_idx);
  ObjPtr<mirror::Class> return_type =
      ResolveType(proto_id.return_type_idx_, dex_cache, class_loader);
  if (return_type == nullptr) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  method_type.SetRType(return_type);

  // Then resolve the argument types.
  DexFileParameterIterator it(dex_file, proto_id);
  for (; it.HasNext(); it.Next()) {
    const dex::TypeIndex type_idx = it.GetTypeIdx();
    ObjPtr<mirror::Class> param_type = ResolveType(type_idx, dex_cache, class_loader);
    if (param_type == nullptr) {
      DCHECK(self->IsExceptionPending());
      return false;
    }
    method_type.AddPType(param_type);
  }

  return true;
}

ObjPtr<mirror::MethodType> ClassLinker::ResolveMethodType(Thread* self,
                                                          dex::ProtoIndex proto_idx,
                                                          ArtMethod* referrer) {
  StackHandleScope<2> hs(self);
  Handle<mirror::DexCache> dex_cache(hs.NewHandle(referrer->GetDexCache()));
  Handle<mirror::ClassLoader> class_loader(hs.NewHandle(referrer->GetClassLoader()));
  return ResolveMethodType(self, proto_idx, dex_cache, class_loader);
}

ObjPtr<mirror::MethodHandle> ClassLinker::ResolveMethodHandleForField(
    Thread* self,
    const dex::MethodHandleItem& method_handle,
    ArtMethod* referrer) {
  DexFile::MethodHandleType handle_type =
      static_cast<DexFile::MethodHandleType>(method_handle.method_handle_type_);
  mirror::MethodHandle::Kind kind;
  bool is_put;
  bool is_static;
  int32_t num_params;
  switch (handle_type) {
    case DexFile::MethodHandleType::kStaticPut: {
      kind = mirror::MethodHandle::Kind::kStaticPut;
      is_put = true;
      is_static = true;
      num_params = 1;
      break;
    }
    case DexFile::MethodHandleType::kStaticGet: {
      kind = mirror::MethodHandle::Kind::kStaticGet;
      is_put = false;
      is_static = true;
      num_params = 0;
      break;
    }
    case DexFile::MethodHandleType::kInstancePut: {
      kind = mirror::MethodHandle::Kind::kInstancePut;
      is_put = true;
      is_static = false;
      num_params = 2;
      break;
    }
    case DexFile::MethodHandleType::kInstanceGet: {
      kind = mirror::MethodHandle::Kind::kInstanceGet;
      is_put = false;
      is_static = false;
      num_params = 1;
      break;
    }
    case DexFile::MethodHandleType::kInvokeStatic:
    case DexFile::MethodHandleType::kInvokeInstance:
    case DexFile::MethodHandleType::kInvokeConstructor:
    case DexFile::MethodHandleType::kInvokeDirect:
    case DexFile::MethodHandleType::kInvokeInterface:
      UNREACHABLE();
  }

  ArtField* target_field =
      ResolveField(method_handle.field_or_method_idx_, referrer, is_static);
  if (LIKELY(target_field != nullptr)) {
    ObjPtr<mirror::Class> target_class = target_field->GetDeclaringClass();
    ObjPtr<mirror::Class> referring_class = referrer->GetDeclaringClass();
    if (UNLIKELY(!referring_class->CanAccessMember(target_class, target_field->GetAccessFlags()))) {
      ThrowIllegalAccessErrorField(referring_class, target_field);
      return nullptr;
    }
    if (UNLIKELY(is_put && target_field->IsFinal())) {
      ThrowIllegalAccessErrorField(referring_class, target_field);
      return nullptr;
    }
  } else {
    DCHECK(Thread::Current()->IsExceptionPending());
    return nullptr;
  }

  StackHandleScope<4> hs(self);
  ObjPtr<mirror::Class> array_of_class = GetClassRoot<mirror::ObjectArray<mirror::Class>>(this);
  Handle<mirror::ObjectArray<mirror::Class>> method_params(hs.NewHandle(
      mirror::ObjectArray<mirror::Class>::Alloc(self, array_of_class, num_params)));
  if (UNLIKELY(method_params == nullptr)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  Handle<mirror::Class> constructor_class;
  Handle<mirror::Class> return_type;
  switch (handle_type) {
    case DexFile::MethodHandleType::kStaticPut: {
      method_params->Set(0, target_field->ResolveType());
      return_type = hs.NewHandle(GetClassRoot(ClassRoot::kPrimitiveVoid, this));
      break;
    }
    case DexFile::MethodHandleType::kStaticGet: {
      return_type = hs.NewHandle(target_field->ResolveType());
      break;
    }
    case DexFile::MethodHandleType::kInstancePut: {
      method_params->Set(0, target_field->GetDeclaringClass());
      method_params->Set(1, target_field->ResolveType());
      return_type = hs.NewHandle(GetClassRoot(ClassRoot::kPrimitiveVoid, this));
      break;
    }
    case DexFile::MethodHandleType::kInstanceGet: {
      method_params->Set(0, target_field->GetDeclaringClass());
      return_type = hs.NewHandle(target_field->ResolveType());
      break;
    }
    case DexFile::MethodHandleType::kInvokeStatic:
    case DexFile::MethodHandleType::kInvokeInstance:
    case DexFile::MethodHandleType::kInvokeConstructor:
    case DexFile::MethodHandleType::kInvokeDirect:
    case DexFile::MethodHandleType::kInvokeInterface:
      UNREACHABLE();
  }

  for (int32_t i = 0; i < num_params; ++i) {
    if (UNLIKELY(method_params->Get(i) == nullptr)) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
  }

  if (UNLIKELY(return_type.IsNull())) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  Handle<mirror::MethodType>
      method_type(hs.NewHandle(mirror::MethodType::Create(self, return_type, method_params)));
  if (UNLIKELY(method_type.IsNull())) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  uintptr_t target = reinterpret_cast<uintptr_t>(target_field);
  return mirror::MethodHandleImpl::Create(self, target, kind, method_type);
}

ObjPtr<mirror::MethodHandle> ClassLinker::ResolveMethodHandleForMethod(
    Thread* self,
    const dex::MethodHandleItem& method_handle,
    ArtMethod* referrer) {
  DexFile::MethodHandleType handle_type =
      static_cast<DexFile::MethodHandleType>(method_handle.method_handle_type_);
  mirror::MethodHandle::Kind kind;
  uint32_t receiver_count = 0;
  ArtMethod* target_method = nullptr;
  switch (handle_type) {
    case DexFile::MethodHandleType::kStaticPut:
    case DexFile::MethodHandleType::kStaticGet:
    case DexFile::MethodHandleType::kInstancePut:
    case DexFile::MethodHandleType::kInstanceGet:
      UNREACHABLE();
    case DexFile::MethodHandleType::kInvokeStatic: {
      kind = mirror::MethodHandle::Kind::kInvokeStatic;
      receiver_count = 0;
      target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                            method_handle.field_or_method_idx_,
                                                            referrer,
                                                            InvokeType::kStatic);
      break;
    }
    case DexFile::MethodHandleType::kInvokeInstance: {
      kind = mirror::MethodHandle::Kind::kInvokeVirtual;
      receiver_count = 1;
      target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                            method_handle.field_or_method_idx_,
                                                            referrer,
                                                            InvokeType::kVirtual);
      break;
    }
    case DexFile::MethodHandleType::kInvokeConstructor: {
      // Constructors are currently implemented as a transform. They
      // are special cased later in this method.
      kind = mirror::MethodHandle::Kind::kInvokeTransform;
      receiver_count = 0;
      target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                            method_handle.field_or_method_idx_,
                                                            referrer,
                                                            InvokeType::kDirect);
      break;
    }
    case DexFile::MethodHandleType::kInvokeDirect: {
      kind = mirror::MethodHandle::Kind::kInvokeDirect;
      receiver_count = 1;
      StackHandleScope<2> hs(self);
      // A constant method handle with type kInvokeDirect can refer to
      // a method that is private or to a method in a super class. To
      // disambiguate the two options, we resolve the method ignoring
      // the invocation type to determine if the method is private. We
      // then resolve again specifying the intended invocation type to
      // force the appropriate checks.
      target_method = ResolveMethodWithoutInvokeType(method_handle.field_or_method_idx_,
                                                     hs.NewHandle(referrer->GetDexCache()),
                                                     hs.NewHandle(referrer->GetClassLoader()));
      if (UNLIKELY(target_method == nullptr)) {
        break;
      }

      if (target_method->IsPrivate()) {
        kind = mirror::MethodHandle::Kind::kInvokeDirect;
        target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                              method_handle.field_or_method_idx_,
                                                              referrer,
                                                              InvokeType::kDirect);
      } else {
        kind = mirror::MethodHandle::Kind::kInvokeSuper;
        target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                              method_handle.field_or_method_idx_,
                                                              referrer,
                                                              InvokeType::kSuper);
        if (UNLIKELY(target_method == nullptr)) {
          break;
        }
        // Find the method specified in the parent in referring class
        // so invoke-super invokes the method in the parent of the
        // referrer.
        target_method =
            referrer->GetDeclaringClass()->FindVirtualMethodForVirtual(target_method,
                                                                       kRuntimePointerSize);
      }
      break;
    }
    case DexFile::MethodHandleType::kInvokeInterface: {
      kind = mirror::MethodHandle::Kind::kInvokeInterface;
      receiver_count = 1;
      target_method = ResolveMethod<ResolveMode::kNoChecks>(self,
                                                            method_handle.field_or_method_idx_,
                                                            referrer,
                                                            InvokeType::kInterface);
      break;
    }
  }

  if (UNLIKELY(target_method == nullptr)) {
    DCHECK(Thread::Current()->IsExceptionPending());
    return nullptr;
  }

  ObjPtr<mirror::Class> target_class = target_method->GetDeclaringClass();
  ObjPtr<mirror::Class> referring_class = referrer->GetDeclaringClass();
  uint32_t access_flags = target_method->GetAccessFlags();
  if (UNLIKELY(!referring_class->CanAccessMember(target_class, access_flags))) {
    ThrowIllegalAccessErrorMethod(referring_class, target_method);
    return nullptr;
  }

  // Calculate the number of parameters from the method shorty. We add the
  // receiver count (0 or 1) and deduct one for the return value.
  uint32_t shorty_length;
  target_method->GetShorty(&shorty_length);
  int32_t num_params = static_cast<int32_t>(shorty_length + receiver_count - 1);

  StackHandleScope<5> hs(self);
  ObjPtr<mirror::Class> array_of_class = GetClassRoot<mirror::ObjectArray<mirror::Class>>(this);
  Handle<mirror::ObjectArray<mirror::Class>> method_params(hs.NewHandle(
      mirror::ObjectArray<mirror::Class>::Alloc(self, array_of_class, num_params)));
  if (method_params.Get() == nullptr) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  const DexFile* dex_file = referrer->GetDexFile();
  const dex::MethodId& method_id = dex_file->GetMethodId(method_handle.field_or_method_idx_);
  int32_t index = 0;
  if (receiver_count != 0) {
    // Insert receiver. Use the class identified in the method handle rather than the declaring
    // class of the resolved method which may be super class or default interface method
    // (b/115964401).
    ObjPtr<mirror::Class> receiver_class = LookupResolvedType(method_id.class_idx_, referrer);
    // receiver_class should have been resolved when resolving the target method.
    DCHECK(receiver_class != nullptr);
    method_params->Set(index++, receiver_class);
  }

  const dex::ProtoId& proto_id = dex_file->GetProtoId(method_id.proto_idx_);
  DexFileParameterIterator it(*dex_file, proto_id);
  while (it.HasNext()) {
    DCHECK_LT(index, num_params);
    const dex::TypeIndex type_idx = it.GetTypeIdx();
    ObjPtr<mirror::Class> klass = ResolveType(type_idx, referrer);
    if (nullptr == klass) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
    method_params->Set(index++, klass);
    it.Next();
  }

  Handle<mirror::Class> return_type =
      hs.NewHandle(ResolveType(proto_id.return_type_idx_, referrer));
  if (UNLIKELY(return_type.IsNull())) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  Handle<mirror::MethodType>
      method_type(hs.NewHandle(mirror::MethodType::Create(self, return_type, method_params)));
  if (UNLIKELY(method_type.IsNull())) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  if (UNLIKELY(handle_type == DexFile::MethodHandleType::kInvokeConstructor)) {
    Handle<mirror::Class> constructor_class = hs.NewHandle(target_method->GetDeclaringClass());
    Handle<mirror::MethodHandlesLookup> lookup =
        hs.NewHandle(mirror::MethodHandlesLookup::GetDefault(self));
    return lookup->FindConstructor(self, constructor_class, method_type);
  }

  uintptr_t target = reinterpret_cast<uintptr_t>(target_method);
  return mirror::MethodHandleImpl::Create(self, target, kind, method_type);
}

ObjPtr<mirror::MethodHandle> ClassLinker::ResolveMethodHandle(Thread* self,
                                                              uint32_t method_handle_idx,
                                                              ArtMethod* referrer)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const DexFile* const dex_file = referrer->GetDexFile();
  const dex::MethodHandleItem& method_handle = dex_file->GetMethodHandle(method_handle_idx);
  switch (static_cast<DexFile::MethodHandleType>(method_handle.method_handle_type_)) {
    case DexFile::MethodHandleType::kStaticPut:
    case DexFile::MethodHandleType::kStaticGet:
    case DexFile::MethodHandleType::kInstancePut:
    case DexFile::MethodHandleType::kInstanceGet:
      return ResolveMethodHandleForField(self, method_handle, referrer);
    case DexFile::MethodHandleType::kInvokeStatic:
    case DexFile::MethodHandleType::kInvokeInstance:
    case DexFile::MethodHandleType::kInvokeConstructor:
    case DexFile::MethodHandleType::kInvokeDirect:
    case DexFile::MethodHandleType::kInvokeInterface:
      return ResolveMethodHandleForMethod(self, method_handle, referrer);
  }
}

bool ClassLinker::IsQuickResolutionStub(const void* entry_point) const {
  return (entry_point == GetQuickResolutionStub()) ||
      (quick_resolution_trampoline_ == entry_point);
}

bool ClassLinker::IsQuickToInterpreterBridge(const void* entry_point) const {
  return (entry_point == GetQuickToInterpreterBridge()) ||
      (quick_to_interpreter_bridge_trampoline_ == entry_point);
}

bool ClassLinker::IsQuickGenericJniStub(const void* entry_point) const {
  return (entry_point == GetQuickGenericJniStub()) ||
      (quick_generic_jni_trampoline_ == entry_point);
}

bool ClassLinker::IsJniDlsymLookupStub(const void* entry_point) const {
  return entry_point == GetJniDlsymLookupStub() ||
      (jni_dlsym_lookup_trampoline_ == entry_point);
}

bool ClassLinker::IsJniDlsymLookupCriticalStub(const void* entry_point) const {
  return entry_point == GetJniDlsymLookupCriticalStub() ||
      (jni_dlsym_lookup_critical_trampoline_ == entry_point);
}

const void* ClassLinker::GetRuntimeQuickGenericJniStub() const {
  return GetQuickGenericJniStub();
}

void ClassLinker::SetEntryPointsForObsoleteMethod(ArtMethod* method) const {
  DCHECK(method->IsObsolete());
  // We cannot mess with the entrypoints of native methods because they are used to determine how
  // large the method's quick stack frame is. Without this information we cannot walk the stacks.
  if (!method->IsNative()) {
    method->SetEntryPointFromQuickCompiledCode(GetInvokeObsoleteMethodStub());
  }
}

void ClassLinker::DumpForSigQuit(std::ostream& os) {
  ScopedObjectAccess soa(Thread::Current());
  ReaderMutexLock mu(soa.Self(), *Locks::classlinker_classes_lock_);
  os << "Zygote loaded classes=" << NumZygoteClasses() << " post zygote classes="
     << NumNonZygoteClasses() << "\n";
  ReaderMutexLock mu2(soa.Self(), *Locks::dex_lock_);
  os << "Dumping registered class loaders\n";
  size_t class_loader_index = 0;
  for (const ClassLoaderData& class_loader : class_loaders_) {
    ObjPtr<mirror::ClassLoader> loader =
        ObjPtr<mirror::ClassLoader>::DownCast(soa.Self()->DecodeJObject(class_loader.weak_root));
    if (loader != nullptr) {
      os << "#" << class_loader_index++ << " " << loader->GetClass()->PrettyDescriptor() << ": [";
      bool saw_one_dex_file = false;
      for (const auto& entry : dex_caches_) {
        const DexCacheData& dex_cache = entry.second;
        if (dex_cache.class_table == class_loader.class_table) {
          if (saw_one_dex_file) {
            os << ":";
          }
          saw_one_dex_file = true;
          os << entry.first->GetLocation();
        }
      }
      os << "]";
      bool found_parent = false;
      if (loader->GetParent() != nullptr) {
        size_t parent_index = 0;
        for (const ClassLoaderData& class_loader2 : class_loaders_) {
          ObjPtr<mirror::ClassLoader> loader2 = ObjPtr<mirror::ClassLoader>::DownCast(
              soa.Self()->DecodeJObject(class_loader2.weak_root));
          if (loader2 == loader->GetParent()) {
            os << ", parent #" << parent_index;
            found_parent = true;
            break;
          }
          parent_index++;
        }
        if (!found_parent) {
          os << ", unregistered parent of type "
             << loader->GetParent()->GetClass()->PrettyDescriptor();
        }
      } else {
        os << ", no parent";
      }
      os << "\n";
    }
  }
  os << "Done dumping class loaders\n";
  Runtime* runtime = Runtime::Current();
  os << "Classes initialized: " << runtime->GetStat(KIND_GLOBAL_CLASS_INIT_COUNT) << " in "
     << PrettyDuration(runtime->GetStat(KIND_GLOBAL_CLASS_INIT_TIME)) << "\n";
}

class CountClassesVisitor : public ClassLoaderVisitor {
 public:
  CountClassesVisitor() : num_zygote_classes(0), num_non_zygote_classes(0) {}

  void Visit(ObjPtr<mirror::ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::classlinker_classes_lock_, Locks::mutator_lock_) override {
    ClassTable* const class_table = class_loader->GetClassTable();
    if (class_table != nullptr) {
      num_zygote_classes += class_table->NumZygoteClasses(class_loader);
      num_non_zygote_classes += class_table->NumNonZygoteClasses(class_loader);
    }
  }

  size_t num_zygote_classes;
  size_t num_non_zygote_classes;
};

size_t ClassLinker::NumZygoteClasses() const {
  CountClassesVisitor visitor;
  VisitClassLoaders(&visitor);
  return visitor.num_zygote_classes + boot_class_table_->NumZygoteClasses(nullptr);
}

size_t ClassLinker::NumNonZygoteClasses() const {
  CountClassesVisitor visitor;
  VisitClassLoaders(&visitor);
  return visitor.num_non_zygote_classes + boot_class_table_->NumNonZygoteClasses(nullptr);
}

size_t ClassLinker::NumLoadedClasses() {
  ReaderMutexLock mu(Thread::Current(), *Locks::classlinker_classes_lock_);
  // Only return non zygote classes since these are the ones which apps which care about.
  return NumNonZygoteClasses();
}

pid_t ClassLinker::GetClassesLockOwner() {
  return Locks::classlinker_classes_lock_->GetExclusiveOwnerTid();
}

pid_t ClassLinker::GetDexLockOwner() {
  return Locks::dex_lock_->GetExclusiveOwnerTid();
}

void ClassLinker::SetClassRoot(ClassRoot class_root, ObjPtr<mirror::Class> klass) {
  DCHECK(!init_done_);

  DCHECK(klass != nullptr);
  DCHECK(klass->GetClassLoader() == nullptr);

  mirror::ObjectArray<mirror::Class>* class_roots = class_roots_.Read();
  DCHECK(class_roots != nullptr);
  DCHECK_LT(static_cast<uint32_t>(class_root), static_cast<uint32_t>(ClassRoot::kMax));
  int32_t index = static_cast<int32_t>(class_root);
  DCHECK(class_roots->Get(index) == nullptr);
  class_roots->Set<false>(index, klass);
}

ObjPtr<mirror::ClassLoader> ClassLinker::CreateWellKnownClassLoader(
    Thread* self,
    const std::vector<const DexFile*>& dex_files,
    Handle<mirror::Class> loader_class,
    Handle<mirror::ClassLoader> parent_loader,
    Handle<mirror::ObjectArray<mirror::ClassLoader>> shared_libraries,
    Handle<mirror::ObjectArray<mirror::ClassLoader>> shared_libraries_after) {
  CHECK(loader_class.Get() == WellKnownClasses::dalvik_system_PathClassLoader ||
        loader_class.Get() == WellKnownClasses::dalvik_system_DelegateLastClassLoader ||
        loader_class.Get() == WellKnownClasses::dalvik_system_InMemoryDexClassLoader);

  StackHandleScope<5> hs(self);

  ArtField* dex_elements_field = WellKnownClasses::dalvik_system_DexPathList_dexElements;

  Handle<mirror::Class> dex_elements_class(hs.NewHandle(dex_elements_field->ResolveType()));
  DCHECK(dex_elements_class != nullptr);
  DCHECK(dex_elements_class->IsArrayClass());
  Handle<mirror::ObjectArray<mirror::Object>> h_dex_elements(hs.NewHandle(
      mirror::ObjectArray<mirror::Object>::Alloc(self,
                                                 dex_elements_class.Get(),
                                                 dex_files.size())));
  Handle<mirror::Class> h_dex_element_class =
      hs.NewHandle(dex_elements_class->GetComponentType());

  ArtField* element_file_field = WellKnownClasses::dalvik_system_DexPathList__Element_dexFile;
  DCHECK_EQ(h_dex_element_class.Get(), element_file_field->GetDeclaringClass());

  ArtField* cookie_field = WellKnownClasses::dalvik_system_DexFile_cookie;
  DCHECK_EQ(cookie_field->GetDeclaringClass(), element_file_field->LookupResolvedType());

  ArtField* file_name_field = WellKnownClasses::dalvik_system_DexFile_fileName;
  DCHECK_EQ(file_name_field->GetDeclaringClass(), element_file_field->LookupResolvedType());

  // Fill the elements array.
  int32_t index = 0;
  for (const DexFile* dex_file : dex_files) {
    StackHandleScope<4> hs2(self);

    // CreateWellKnownClassLoader is only used by gtests and compiler.
    // Index 0 of h_long_array is supposed to be the oat file but we can leave it null.
    Handle<mirror::LongArray> h_long_array = hs2.NewHandle(mirror::LongArray::Alloc(
        self,
        kDexFileIndexStart + 1));
    DCHECK(h_long_array != nullptr);
    h_long_array->Set(kDexFileIndexStart, reinterpret_cast64<int64_t>(dex_file));

    // Note that this creates a finalizable dalvik.system.DexFile object and a corresponding
    // FinalizerReference which will never get cleaned up without a started runtime.
    Handle<mirror::Object> h_dex_file = hs2.NewHandle(
        cookie_field->GetDeclaringClass()->AllocObject(self));
    DCHECK(h_dex_file != nullptr);
    cookie_field->SetObject<false>(h_dex_file.Get(), h_long_array.Get());

    Handle<mirror::String> h_file_name = hs2.NewHandle(
        mirror::String::AllocFromModifiedUtf8(self, dex_file->GetLocation().c_str()));
    DCHECK(h_file_name != nullptr);
    file_name_field->SetObject<false>(h_dex_file.Get(), h_file_name.Get());

    Handle<mirror::Object> h_element = hs2.NewHandle(h_dex_element_class->AllocObject(self));
    DCHECK(h_element != nullptr);
    element_file_field->SetObject<false>(h_element.Get(), h_dex_file.Get());

    h_dex_elements->Set(index, h_element.Get());
    index++;
  }
  DCHECK_EQ(index, h_dex_elements->GetLength());

  // Create DexPathList.
  Handle<mirror::Object> h_dex_path_list = hs.NewHandle(
      dex_elements_field->GetDeclaringClass()->AllocObject(self));
  DCHECK(h_dex_path_list != nullptr);
  // Set elements.
  dex_elements_field->SetObject<false>(h_dex_path_list.Get(), h_dex_elements.Get());
  // Create an empty List for the "nativeLibraryDirectories," required for native tests.
  // Note: this code is uncommon(oatdump)/testing-only, so don't add further WellKnownClasses
  //       elements.
  {
    ArtField* native_lib_dirs = dex_elements_field->GetDeclaringClass()->
        FindDeclaredInstanceField("nativeLibraryDirectories", "Ljava/util/List;");
    DCHECK(native_lib_dirs != nullptr);
    ObjPtr<mirror::Class> list_class = FindSystemClass(self, "Ljava/util/ArrayList;");
    DCHECK(list_class != nullptr);
    {
      StackHandleScope<1> h_list_scope(self);
      Handle<mirror::Class> h_list_class(h_list_scope.NewHandle<mirror::Class>(list_class));
      bool list_init = EnsureInitialized(self, h_list_class, true, true);
      DCHECK(list_init);
      list_class = h_list_class.Get();
    }
    ObjPtr<mirror::Object> list_object = list_class->AllocObject(self);
    // Note: we leave the object uninitialized. This must never leak into any non-testing code, but
    //       is fine for testing. While it violates a Java-code invariant (the elementData field is
    //       normally never null), as long as one does not try to add elements, this will still
    //       work.
    native_lib_dirs->SetObject<false>(h_dex_path_list.Get(), list_object);
  }

  // Create the class loader..
  Handle<mirror::ClassLoader> h_class_loader = hs.NewHandle<mirror::ClassLoader>(
      ObjPtr<mirror::ClassLoader>::DownCast(loader_class->AllocObject(self)));
  DCHECK(h_class_loader != nullptr);
  // Set DexPathList.
  ArtField* path_list_field = WellKnownClasses::dalvik_system_BaseDexClassLoader_pathList;
  DCHECK(path_list_field != nullptr);
  path_list_field->SetObject<false>(h_class_loader.Get(), h_dex_path_list.Get());

  // Make a pretend boot-classpath.
  // TODO: Should we scan the image?
  ArtField* const parent_field = WellKnownClasses::java_lang_ClassLoader_parent;
  DCHECK(parent_field != nullptr);
  if (parent_loader.Get() == nullptr) {
    ObjPtr<mirror::Object> boot_loader(
        WellKnownClasses::java_lang_BootClassLoader->AllocObject(self));
    parent_field->SetObject<false>(h_class_loader.Get(), boot_loader);
  } else {
    parent_field->SetObject<false>(h_class_loader.Get(), parent_loader.Get());
  }

  ArtField* shared_libraries_field =
      WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoaders;
  DCHECK(shared_libraries_field != nullptr);
  shared_libraries_field->SetObject<false>(h_class_loader.Get(), shared_libraries.Get());

  ArtField* shared_libraries_after_field =
        WellKnownClasses::dalvik_system_BaseDexClassLoader_sharedLibraryLoadersAfter;
  DCHECK(shared_libraries_after_field != nullptr);
  shared_libraries_after_field->SetObject<false>(h_class_loader.Get(),
                                                 shared_libraries_after.Get());
  return h_class_loader.Get();
}

jobject ClassLinker::CreatePathClassLoader(Thread* self,
                                           const std::vector<const DexFile*>& dex_files) {
  StackHandleScope<3u> hs(self);
  Handle<mirror::Class> d_s_pcl =
      hs.NewHandle(WellKnownClasses::dalvik_system_PathClassLoader.Get());
  auto null_parent = hs.NewHandle<mirror::ClassLoader>(nullptr);
  auto null_libs = hs.NewHandle<mirror::ObjectArray<mirror::ClassLoader>>(nullptr);
  ObjPtr<mirror::ClassLoader> class_loader =
      CreateWellKnownClassLoader(self, dex_files, d_s_pcl, null_parent, null_libs, null_libs);
  return Runtime::Current()->GetJavaVM()->AddGlobalRef(self, class_loader);
}

void ClassLinker::DropFindArrayClassCache() {
  std::fill_n(find_array_class_cache_, kFindArrayCacheSize, GcRoot<mirror::Class>(nullptr));
  find_array_class_cache_next_victim_ = 0;
}

void ClassLinker::VisitClassLoaders(ClassLoaderVisitor* visitor) const {
  Thread* const self = Thread::Current();
  for (const ClassLoaderData& data : class_loaders_) {
    // Need to use DecodeJObject so that we get null for cleared JNI weak globals.
    ObjPtr<mirror::ClassLoader> class_loader = ObjPtr<mirror::ClassLoader>::DownCast(
        self->DecodeJObject(data.weak_root));
    if (class_loader != nullptr) {
      visitor->Visit(class_loader);
    }
  }
}

void ClassLinker::VisitDexCaches(DexCacheVisitor* visitor) const {
  Thread* const self = Thread::Current();
  for (const auto& it : dex_caches_) {
    // Need to use DecodeJObject so that we get null for cleared JNI weak globals.
    ObjPtr<mirror::DexCache> dex_cache = ObjPtr<mirror::DexCache>::DownCast(
        self->DecodeJObject(it.second.weak_root));
    if (dex_cache != nullptr) {
      visitor->Visit(dex_cache);
    }
  }
}

void ClassLinker::VisitAllocators(AllocatorVisitor* visitor) const {
  for (const ClassLoaderData& data : class_loaders_) {
    LinearAlloc* alloc = data.allocator;
    if (alloc != nullptr && !visitor->Visit(alloc)) {
        break;
    }
  }
}

void ClassLinker::InsertDexFileInToClassLoader(ObjPtr<mirror::Object> dex_file,
                                               ObjPtr<mirror::ClassLoader> class_loader) {
  DCHECK(dex_file != nullptr);
  Thread* const self = Thread::Current();
  WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
  ClassTable* const table = ClassTableForClassLoader(class_loader);
  DCHECK(table != nullptr);
  if (table->InsertStrongRoot(dex_file)) {
    WriteBarrierOnClassLoaderLocked(class_loader, dex_file);
  } else {
    // Write-barrier not required if strong-root isn't inserted.
  }
}

void ClassLinker::CleanupClassLoaders() {
  Thread* const self = Thread::Current();
  std::list<ClassLoaderData> to_delete;
  // Do the delete outside the lock to avoid lock violation in jit code cache.
  {
    WriterMutexLock mu(self, *Locks::classlinker_classes_lock_);
    for (auto it = class_loaders_.begin(); it != class_loaders_.end(); ) {
      auto this_it = it;
      ++it;
      const ClassLoaderData& data = *this_it;
      // Need to use DecodeJObject so that we get null for cleared JNI weak globals.
      ObjPtr<mirror::ClassLoader> class_loader =
          ObjPtr<mirror::ClassLoader>::DownCast(self->DecodeJObject(data.weak_root));
      if (class_loader == nullptr) {
        VLOG(class_linker) << "Freeing class loader";
        to_delete.splice(to_delete.end(), class_loaders_, this_it);
      }
    }
  }
  if (to_delete.empty()) {
    return;
  }
  std::set<const OatFile*> unregistered_oat_files;
  JavaVMExt* vm = self->GetJniEnv()->GetVm();
  {
    WriterMutexLock mu(self, *Locks::dex_lock_);
    for (auto it = dex_caches_.begin(), end = dex_caches_.end(); it != end; ) {
      const DexFile* dex_file = it->first;
      const DexCacheData& data = it->second;
      if (self->DecodeJObject(data.weak_root) == nullptr) {
        DCHECK(to_delete.end() != std::find_if(
            to_delete.begin(),
            to_delete.end(),
            [&](const ClassLoaderData& cld) { return cld.class_table == data.class_table; }));
        if (dex_file->GetOatDexFile() != nullptr &&
            dex_file->GetOatDexFile()->GetOatFile() != nullptr &&
            dex_file->GetOatDexFile()->GetOatFile()->IsExecutable()) {
          unregistered_oat_files.insert(dex_file->GetOatDexFile()->GetOatFile());
        }
        vm->DeleteWeakGlobalRef(self, data.weak_root);
        it = dex_caches_.erase(it);
      } else {
        ++it;
      }
    }
  }
  {
    ScopedDebugDisallowReadBarriers sddrb(self);
    for (ClassLoaderData& data : to_delete) {
      // CHA unloading analysis and SingleImplementaion cleanups are required.
      PrepareToDeleteClassLoader(self, data, /*cleanup_cha=*/true);
    }
  }
  for (const ClassLoaderData& data : to_delete) {
    delete data.allocator;
    delete data.class_table;
  }
  Runtime* runtime = Runtime::Current();
  if (!unregistered_oat_files.empty()) {
    for (const OatFile* oat_file : unregistered_oat_files) {
      // Notify the fault handler about removal of the executable code range if needed.
      DCHECK(oat_file->IsExecutable());
      size_t exec_offset = oat_file->GetOatHeader().GetExecutableOffset();
      DCHECK_LE(exec_offset, oat_file->Size());
      size_t exec_size = oat_file->Size() - exec_offset;
      if (exec_size != 0u) {
        runtime->RemoveGeneratedCodeRange(oat_file->Begin() + exec_offset, exec_size);
      }
    }
  }

  if (runtime->GetStartupLinearAlloc() != nullptr) {
    // Because the startup linear alloc can contain dex cache arrays associated
    // to class loaders that got unloaded, we need to delete these
    // arrays.
    StartupCompletedTask::DeleteStartupDexCaches(self, /* called_by_gc= */ true);
    DCHECK_EQ(runtime->GetStartupLinearAlloc(), nullptr);
  }
}

class ClassLinker::FindVirtualMethodHolderVisitor : public ClassVisitor {
 public:
  FindVirtualMethodHolderVisitor(const ArtMethod* method, PointerSize pointer_size)
      : method_(method),
        pointer_size_(pointer_size) {}

  bool operator()(ObjPtr<mirror::Class> klass) REQUIRES_SHARED(Locks::mutator_lock_) override {
    if (klass->GetVirtualMethodsSliceUnchecked(pointer_size_).Contains(method_)) {
      holder_ = klass;
    }
    // Return false to stop searching if holder_ is not null.
    return holder_ == nullptr;
  }

  ObjPtr<mirror::Class> holder_ = nullptr;
  const ArtMethod* const method_;
  const PointerSize pointer_size_;
};

ObjPtr<mirror::Class> ClassLinker::GetHoldingClassOfCopiedMethod(ArtMethod* method) {
  ScopedTrace trace(__FUNCTION__);  // Since this function is slow, have a trace to notify people.
  CHECK(method->IsCopied());
  FindVirtualMethodHolderVisitor visitor(method, image_pointer_size_);
  VisitClasses(&visitor);
  DCHECK(visitor.holder_ != nullptr);
  return visitor.holder_;
}

ObjPtr<mirror::ClassLoader> ClassLinker::GetHoldingClassLoaderOfCopiedMethod(Thread* self,
                                                                             ArtMethod* method) {
  // Note: `GetHoldingClassOfCopiedMethod(method)` is a lot more expensive than finding
  // the class loader, so we're using it only to verify the result in debug mode.
  CHECK(method->IsCopied());
  gc::Heap* heap = Runtime::Current()->GetHeap();
  // Check if the copied method is in the boot class path.
  if (heap->IsBootImageAddress(method) || GetAllocatorForClassLoader(nullptr)->Contains(method)) {
    DCHECK(GetHoldingClassOfCopiedMethod(method)->GetClassLoader() == nullptr);
    return nullptr;
  }
  // Check if the copied method is in an app image.
  // Note: Continuous spaces contain boot image spaces and app image spaces.
  // However, they are sorted by address, so boot images are not trivial to skip.
  ArrayRef<gc::space::ContinuousSpace* const> spaces(heap->GetContinuousSpaces());
  DCHECK_GE(spaces.size(), heap->GetBootImageSpaces().size());
  for (gc::space::ContinuousSpace* space : spaces) {
    if (space->IsImageSpace()) {
      gc::space::ImageSpace* image_space = space->AsImageSpace();
      size_t offset = reinterpret_cast<const uint8_t*>(method) - image_space->Begin();
      const ImageSection& methods_section = image_space->GetImageHeader().GetMethodsSection();
      if (offset - methods_section.Offset() < methods_section.Size()) {
        // Grab the class loader from the first non-BCP class in the app image class table.
        // Note: If we allow classes from arbitrary parent or library class loaders in app
        // images, this shall need to be updated to actually search for the exact class.
        const ImageSection& class_table_section =
            image_space->GetImageHeader().GetClassTableSection();
        CHECK_NE(class_table_section.Size(), 0u);
        const uint8_t* ptr = image_space->Begin() + class_table_section.Offset();
        size_t read_count = 0;
        ClassTable::ClassSet class_set(ptr, /*make_copy_of_data=*/ false, &read_count);
        CHECK(!class_set.empty());
        auto it = class_set.begin();
        // No read barrier needed for references to non-movable image classes.
        while ((*it).Read<kWithoutReadBarrier>()->IsBootStrapClassLoaded()) {
          ++it;
          CHECK(it != class_set.end());
        }
        ObjPtr<mirror::ClassLoader> class_loader =
            (*it).Read<kWithoutReadBarrier>()->GetClassLoader();
        DCHECK(GetHoldingClassOfCopiedMethod(method)->GetClassLoader() == class_loader);
        return class_loader;
      }
    }
  }
  // Otherwise, the method must be in one of the `LinearAlloc` memory areas.
  jweak result = nullptr;
  {
    ReaderMutexLock mu(self, *Locks::classlinker_classes_lock_);
    for (const ClassLoaderData& data : class_loaders_) {
      if (data.allocator->Contains(method)) {
        result = data.weak_root;
        break;
      }
    }
  }
  CHECK(result != nullptr) << "Did not find allocator holding the copied method: " << method
      << " " << method->PrettyMethod();
  // The `method` is alive, so the class loader must also be alive.
  return ObjPtr<mirror::ClassLoader>::DownCast(
      Runtime::Current()->GetJavaVM()->DecodeWeakGlobalAsStrong(result));
}

bool ClassLinker::DenyAccessBasedOnPublicSdk([[maybe_unused]] ArtMethod* art_method) const
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Should not be called on ClassLinker, only on AotClassLinker that overrides this.
  LOG(FATAL) << "UNREACHABLE";
  UNREACHABLE();
}

bool ClassLinker::DenyAccessBasedOnPublicSdk([[maybe_unused]] ArtField* art_field) const
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Should not be called on ClassLinker, only on AotClassLinker that overrides this.
  LOG(FATAL) << "UNREACHABLE";
  UNREACHABLE();
}

bool ClassLinker::DenyAccessBasedOnPublicSdk([[maybe_unused]] const char* type_descriptor) const {
  // Should not be called on ClassLinker, only on AotClassLinker that overrides this.
  LOG(FATAL) << "UNREACHABLE";
  UNREACHABLE();
}

void ClassLinker::SetEnablePublicSdkChecks([[maybe_unused]] bool enabled) {
  // Should not be called on ClassLinker, only on AotClassLinker that overrides this.
  LOG(FATAL) << "UNREACHABLE";
  UNREACHABLE();
}

void ClassLinker::RemoveDexFromCaches(const DexFile& dex_file) {
  ReaderMutexLock mu(Thread::Current(), *Locks::dex_lock_);

  auto it = dex_caches_.find(&dex_file);
  if (it != dex_caches_.end()) {
      dex_caches_.erase(it);
  }
}

// Instantiate ClassLinker::AllocClass.
template ObjPtr<mirror::Class> ClassLinker::AllocClass</* kMovable= */ true>(
    Thread* self,
    ObjPtr<mirror::Class> java_lang_Class,
    uint32_t class_size);
template ObjPtr<mirror::Class> ClassLinker::AllocClass</* kMovable= */ false>(
    Thread* self,
    ObjPtr<mirror::Class> java_lang_Class,
    uint32_t class_size);

}  // namespace art
