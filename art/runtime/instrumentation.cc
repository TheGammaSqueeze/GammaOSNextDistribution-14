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

#include "instrumentation.h"

#include <functional>
#include <optional>
#include <sstream>

#include <android-base/logging.h>

#include "arch/context.h"
#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/atomic.h"
#include "base/callee_save_type.h"
#include "class_linker.h"
#include "debugger.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_types.h"
#include "dex/dex_instruction-inl.h"
#include "entrypoints/quick/quick_alloc_entrypoints.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "gc_root-inl.h"
#include "interpreter/interpreter.h"
#include "interpreter/interpreter_common.h"
#include "jit/jit.h"
#include "jit/jit_code_cache.h"
#include "jvalue-inl.h"
#include "jvalue.h"
#include "mirror/class-inl.h"
#include "mirror/dex_cache.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "nterp_helpers.h"
#include "nth_caller_visitor.h"
#include "oat/oat_file_manager.h"
#include "oat/oat_quick_method_header.h"
#include "runtime-inl.h"
#include "thread.h"
#include "thread_list.h"

namespace art HIDDEN {
extern "C" NO_RETURN void artDeoptimize(Thread* self, bool skip_method_exit_callbacks);
extern "C" NO_RETURN void artDeliverPendingExceptionFromCode(Thread* self);

namespace instrumentation {

constexpr bool kVerboseInstrumentation = false;

void InstrumentationListener::MethodExited(
    Thread* thread,
    ArtMethod* method,
    OptionalFrame frame,
    MutableHandle<mirror::Object>& return_value) {
  DCHECK_EQ(method->GetInterfaceMethodIfProxy(kRuntimePointerSize)->GetReturnTypePrimitive(),
            Primitive::kPrimNot);
  const void* original_ret = return_value.Get();
  JValue v;
  v.SetL(return_value.Get());
  MethodExited(thread, method, frame, v);
  DCHECK(original_ret == v.GetL()) << "Return value changed";
}

void InstrumentationListener::FieldWritten(Thread* thread,
                                           Handle<mirror::Object> this_object,
                                           ArtMethod* method,
                                           uint32_t dex_pc,
                                           ArtField* field,
                                           Handle<mirror::Object> field_value) {
  DCHECK(!field->IsPrimitiveType());
  JValue v;
  v.SetL(field_value.Get());
  FieldWritten(thread, this_object, method, dex_pc, field, v);
}

// Instrumentation works on non-inlined frames by updating returned PCs
// of compiled frames.
static constexpr StackVisitor::StackWalkKind kInstrumentationStackWalk =
    StackVisitor::StackWalkKind::kSkipInlinedFrames;

class InstallStubsClassVisitor : public ClassVisitor {
 public:
  explicit InstallStubsClassVisitor(Instrumentation* instrumentation)
      : instrumentation_(instrumentation) {}

  bool operator()(ObjPtr<mirror::Class> klass) override REQUIRES(Locks::mutator_lock_) {
    instrumentation_->InstallStubsForClass(klass.Ptr());
    return true;  // we visit all classes.
  }

 private:
  Instrumentation* const instrumentation_;
};

Instrumentation::Instrumentation()
    : run_exit_hooks_(false),
      instrumentation_level_(InstrumentationLevel::kInstrumentNothing),
      forced_interpret_only_(false),
      have_method_entry_listeners_(0),
      have_method_exit_listeners_(0),
      have_method_unwind_listeners_(false),
      have_dex_pc_listeners_(false),
      have_field_read_listeners_(false),
      have_field_write_listeners_(false),
      have_exception_thrown_listeners_(false),
      have_watched_frame_pop_listeners_(false),
      have_branch_listeners_(false),
      have_exception_handled_listeners_(false),
      quick_alloc_entry_points_instrumentation_counter_(0),
      alloc_entrypoints_instrumented_(false) {}

bool Instrumentation::ProcessMethodUnwindCallbacks(Thread* self,
                                                   std::queue<ArtMethod*>& methods,
                                                   MutableHandle<mirror::Throwable>& exception) {
  DCHECK(!self->IsExceptionPending());
  if (!HasMethodUnwindListeners()) {
    return true;
  }
  if (kVerboseInstrumentation) {
    LOG(INFO) << "Popping frames for exception " << exception->Dump();
  }
  // The instrumentation events expect the exception to be set.
  self->SetException(exception.Get());
  bool new_exception_thrown = false;

  // Process callbacks for all methods that would be unwound until a new exception is thrown.
  while (!methods.empty()) {
    ArtMethod* method = methods.front();
    methods.pop();
    if (kVerboseInstrumentation) {
      LOG(INFO) << "Popping for unwind " << method->PrettyMethod();
    }

    if (method->IsRuntimeMethod()) {
      continue;
    }

    // Notify listeners of method unwind.
    // TODO: improve the dex_pc information here.
    uint32_t dex_pc = dex::kDexNoIndex;
    MethodUnwindEvent(self, method, dex_pc);
    new_exception_thrown = self->GetException() != exception.Get();
    if (new_exception_thrown) {
      break;
    }
  }

  exception.Assign(self->GetException());
  self->ClearException();
  if (kVerboseInstrumentation && new_exception_thrown) {
    LOG(INFO) << "Did partial pop of frames due to new exception";
  }
  return !new_exception_thrown;
}

void Instrumentation::InstallStubsForClass(ObjPtr<mirror::Class> klass) {
  if (!klass->IsResolved()) {
    // We need the class to be resolved to install/uninstall stubs. Otherwise its methods
    // could not be initialized or linked with regards to class inheritance.
  } else if (klass->IsErroneousResolved()) {
    // We can't execute code in a erroneous class: do nothing.
  } else {
    for (ArtMethod& method : klass->GetMethods(kRuntimePointerSize)) {
      InstallStubsForMethod(&method);
    }
  }
}

static bool CanHandleInitializationCheck(const void* code) {
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  return class_linker->IsQuickResolutionStub(code) ||
         class_linker->IsQuickToInterpreterBridge(code) ||
         class_linker->IsQuickGenericJniStub(code) ||
         (code == interpreter::GetNterpWithClinitEntryPoint());
}

static bool IsProxyInit(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  // Annoyingly this can be called before we have actually initialized WellKnownClasses so therefore
  // we also need to check this based on the declaring-class descriptor. The check is valid because
  // Proxy only has a single constructor.
  ArtMethod* well_known_proxy_init = WellKnownClasses::java_lang_reflect_Proxy_init;
  if (well_known_proxy_init == method) {
    return true;
  }

  if (well_known_proxy_init != nullptr) {
    return false;
  }

  return method->IsConstructor() && !method->IsStatic() &&
      method->GetDeclaringClass()->DescriptorEquals("Ljava/lang/reflect/Proxy;");
}

// Returns true if we need entry exit stub to call entry hooks. JITed code
// directly call entry / exit hooks and don't need the stub.
static bool CodeSupportsEntryExitHooks(const void* entry_point, ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Proxy.init should always run with the switch interpreter where entry / exit hooks are
  // supported.
  if (IsProxyInit(method)) {
    return true;
  }

  // In some tests runtime isn't setup fully and hence the entry points could be nullptr.
  // just be conservative and return false here.
  if (entry_point == nullptr) {
    return false;
  }

  ClassLinker* linker = Runtime::Current()->GetClassLinker();
  // Interpreter supports entry / exit hooks. Resolution stubs fetch code that supports entry / exit
  // hooks when required. So return true for both cases.
  if (linker->IsQuickToInterpreterBridge(entry_point) ||
      linker->IsQuickResolutionStub(entry_point)) {
    return true;
  }

  // When jiting code for debuggable runtimes / instrumentation is active  we generate the code to
  // call method entry / exit hooks when required.
  jit::Jit* jit = Runtime::Current()->GetJit();
  if (jit != nullptr && jit->GetCodeCache()->ContainsPc(entry_point)) {
    // If JITed code was compiled with instrumentation support we support entry / exit hooks.
    OatQuickMethodHeader* header = OatQuickMethodHeader::FromEntryPoint(entry_point);
    return CodeInfo::IsDebuggable(header->GetOptimizedCodeInfoPtr());
  }

  // GenericJni trampoline can handle entry / exit hooks.
  if (linker->IsQuickGenericJniStub(entry_point)) {
    return true;
  }

  // The remaining cases are nterp / oat code / JIT code that isn't compiled with instrumentation
  // support.
  return false;
}

static void UpdateEntryPoints(ArtMethod* method, const void* quick_code)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (kIsDebugBuild) {
    if (method->StillNeedsClinitCheckMayBeDead()) {
      CHECK(CanHandleInitializationCheck(quick_code));
    }
    jit::Jit* jit = Runtime::Current()->GetJit();
    if (jit != nullptr && jit->GetCodeCache()->ContainsPc(quick_code)) {
      // Ensure we always have the thumb entrypoint for JIT on arm32.
      if (kRuntimeISA == InstructionSet::kArm) {
        CHECK_EQ(reinterpret_cast<uintptr_t>(quick_code) & 1, 1u);
      }
    }
    const Instrumentation* instr = Runtime::Current()->GetInstrumentation();
    if (instr->EntryExitStubsInstalled()) {
      CHECK(CodeSupportsEntryExitHooks(quick_code, method));
    }
    if (instr->InterpreterStubsInstalled() && !method->IsNative()) {
      CHECK_EQ(quick_code, GetQuickToInterpreterBridge());
    }
  }
  // If the method is from a boot image, don't dirty it if the entrypoint
  // doesn't change.
  if (method->GetEntryPointFromQuickCompiledCode() != quick_code) {
    method->SetEntryPointFromQuickCompiledCode(quick_code);
  }
}

bool Instrumentation::NeedsDexPcEvents(ArtMethod* method, Thread* thread) {
  return (InterpretOnly(method) || thread->IsForceInterpreter()) && HasDexPcListeners();
}

bool Instrumentation::InterpretOnly(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  if (method->IsNative()) {
    return false;
  }
  return InterpretOnly() || IsDeoptimized(method);
}

static bool CanUseAotCode(const void* quick_code)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (quick_code == nullptr) {
    return false;
  }
  Runtime* runtime = Runtime::Current();
  // For simplicity, we never use AOT code for debuggable.
  if (runtime->IsJavaDebuggable()) {
    return false;
  }

  if (runtime->IsNativeDebuggable()) {
    DCHECK(runtime->UseJitCompilation() && runtime->GetJit()->JitAtFirstUse());
    // If we are doing native debugging, ignore application's AOT code,
    // since we want to JIT it (at first use) with extra stackmaps for native
    // debugging. We keep however all AOT code from the boot image,
    // since the JIT-at-first-use is blocking and would result in non-negligible
    // startup performance impact.
    return runtime->GetHeap()->IsInBootImageOatFile(quick_code);
  }

  return true;
}

static bool CanUseNterp(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  return interpreter::CanRuntimeUseNterp() &&
      CanMethodUseNterp(method) &&
      method->IsDeclaringClassVerifiedMayBeDead();
}

static const void* GetOptimizedCodeFor(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!Runtime::Current()->GetInstrumentation()->InterpretOnly(method));
  CHECK(method->IsInvokable()) << method->PrettyMethod();
  if (method->IsProxyMethod()) {
    return GetQuickProxyInvokeHandler();
  }

  // In debuggable mode, we can only use AOT code for native methods.
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  const void* aot_code = method->GetOatMethodQuickCode(class_linker->GetImagePointerSize());
  if (CanUseAotCode(aot_code)) {
    return aot_code;
  }

  // If the method has been precompiled, there can be a JIT version.
  jit::Jit* jit = Runtime::Current()->GetJit();
  if (jit != nullptr) {
    const void* code = jit->GetCodeCache()->GetSavedEntryPointOfPreCompiledMethod(method);
    if (code != nullptr) {
      return code;
    }
  }

  // We need to check if the class has been verified for setting up nterp, as
  // the verifier could punt the method to the switch interpreter in case we
  // need to do lock counting.
  if (CanUseNterp(method)) {
    return interpreter::GetNterpEntryPoint();
  }

  return method->IsNative() ? GetQuickGenericJniStub() : GetQuickToInterpreterBridge();
}

void Instrumentation::InitializeMethodsCode(ArtMethod* method, const void* aot_code)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (!method->IsInvokable()) {
    DCHECK(method->GetEntryPointFromQuickCompiledCode() == nullptr ||
           Runtime::Current()->GetClassLinker()->IsQuickToInterpreterBridge(
               method->GetEntryPointFromQuickCompiledCode()));
    UpdateEntryPoints(method, GetQuickToInterpreterBridge());
    return;
  }

  // Use instrumentation entrypoints if instrumentation is installed.
  if (UNLIKELY(EntryExitStubsInstalled() || IsForcedInterpretOnly() || IsDeoptimized(method))) {
    UpdateEntryPoints(
        method, method->IsNative() ? GetQuickGenericJniStub() : GetQuickToInterpreterBridge());
    return;
  }

  // Special case if we need an initialization check.
  // The method and its declaring class may be dead when starting JIT GC during managed heap GC.
  if (method->StillNeedsClinitCheckMayBeDead()) {
    // If we have code but the method needs a class initialization check before calling
    // that code, install the resolution stub that will perform the check.
    // It will be replaced by the proper entry point by ClassLinker::FixupStaticTrampolines
    // after initializing class (see ClassLinker::InitializeClass method).
    // Note: this mimics the logic in image_writer.cc that installs the resolution
    // stub only if we have compiled code or we can execute nterp, and the method needs a class
    // initialization check.
    if (aot_code != nullptr || method->IsNative() || CanUseNterp(method)) {
      if (kIsDebugBuild && CanUseNterp(method)) {
        // Adds some test coverage for the nterp clinit entrypoint.
        UpdateEntryPoints(method, interpreter::GetNterpWithClinitEntryPoint());
      } else {
        UpdateEntryPoints(method, GetQuickResolutionStub());
      }
    } else {
      UpdateEntryPoints(method, GetQuickToInterpreterBridge());
    }
    return;
  }

  // Use the provided AOT code if possible.
  if (CanUseAotCode(aot_code)) {
    UpdateEntryPoints(method, aot_code);
    return;
  }

  // We check if the class is verified as we need the slow interpreter for lock verification.
  // If the class is not verified, This will be updated in
  // ClassLinker::UpdateClassAfterVerification.
  if (CanUseNterp(method)) {
    UpdateEntryPoints(method, interpreter::GetNterpEntryPoint());
    return;
  }

  // Use default entrypoints.
  UpdateEntryPoints(
      method, method->IsNative() ? GetQuickGenericJniStub() : GetQuickToInterpreterBridge());
}

void Instrumentation::InstallStubsForMethod(ArtMethod* method) {
  if (!method->IsInvokable() || method->IsProxyMethod()) {
    // Do not change stubs for these methods.
    return;
  }
  // Don't stub Proxy.<init>. Note that the Proxy class itself is not a proxy class.
  // TODO We should remove the need for this since it means we cannot always correctly detect calls
  // to Proxy.<init>
  if (IsProxyInit(method)) {
    return;
  }

  // If the instrumentation needs to go through the interpreter, just update the
  // entrypoint to interpreter.
  if (InterpretOnly(method)) {
    UpdateEntryPoints(method, GetQuickToInterpreterBridge());
    return;
  }

  if (EntryExitStubsInstalled()) {
    // Install interpreter bridge / GenericJni stub if the existing code doesn't support
    // entry / exit hooks.
    if (!CodeSupportsEntryExitHooks(method->GetEntryPointFromQuickCompiledCode(), method)) {
      UpdateEntryPoints(
          method, method->IsNative() ? GetQuickGenericJniStub() : GetQuickToInterpreterBridge());
    }
    return;
  }

  // We're being asked to restore the entrypoints after instrumentation.
  CHECK_EQ(instrumentation_level_, InstrumentationLevel::kInstrumentNothing);
  // We need to have the resolution stub still if the class is not initialized.
  if (method->StillNeedsClinitCheck()) {
    UpdateEntryPoints(method, GetQuickResolutionStub());
    return;
  }
  UpdateEntryPoints(method, GetOptimizedCodeFor(method));
}

void Instrumentation::UpdateEntrypointsForDebuggable() {
  Runtime* runtime = Runtime::Current();
  // If we are transitioning from non-debuggable to debuggable, we patch
  // entry points of methods to remove any aot / JITed entry points.
  InstallStubsClassVisitor visitor(this);
  runtime->GetClassLinker()->VisitClasses(&visitor);
}

bool Instrumentation::MethodSupportsExitEvents(ArtMethod* method,
                                               const OatQuickMethodHeader* header) {
  if (header == nullptr) {
    // Header can be a nullptr for runtime / proxy methods that doesn't support method exit hooks
    // or for native methods that use generic jni stubs. Generic jni stubs support method exit
    // hooks.
    return method->IsNative();
  }

  if (header->IsNterpMethodHeader()) {
    // Nterp doesn't support method exit events
    return false;
  }

  DCHECK(header->IsOptimized());
  if (CodeInfo::IsDebuggable(header->GetOptimizedCodeInfoPtr())) {
    // For optimized code, we only support method entry / exit hooks if they are compiled as
    // debuggable.
    return true;
  }

  return false;
}

// Updates on stack frames to support any changes related to instrumentation.
// For JITed frames, DeoptimizeFlag is updated to enable deoptimization of
// methods when necessary. Shadow frames are updated if dex pc event
// notification has changed. When force_deopt is true then DeoptimizationFlag is
// updated to force a deoptimization.
void InstrumentationInstallStack(Thread* thread, bool deopt_all_frames)
    REQUIRES(Locks::mutator_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  struct InstallStackVisitor final : public StackVisitor {
    InstallStackVisitor(Thread* thread_in,
                        Context* context,
                        bool deopt_all_frames)
        : StackVisitor(thread_in, context, kInstrumentationStackWalk),
          deopt_all_frames_(deopt_all_frames),
          runtime_methods_need_deopt_check_(false) {}

    bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
      ArtMethod* m = GetMethod();
      if (m == nullptr || m->IsRuntimeMethod()) {
        if (kVerboseInstrumentation) {
          LOG(INFO) << "  Skipping upcall / runtime method. Frame " << GetFrameId();
        }
        return true;  // Ignore upcalls and runtime methods.
      }

      bool is_shadow_frame = GetCurrentQuickFrame() == nullptr;
      if (kVerboseInstrumentation) {
        LOG(INFO) << "Processing frame: method: " << m->PrettyMethod()
                  << " is_shadow_frame: " << is_shadow_frame;
      }

      // Handle interpreter frame.
      if (is_shadow_frame) {
        // Since we are updating the instrumentation related information we have to recalculate
        // NeedsDexPcEvents. For example, when a new method or thread is deoptimized / interpreter
        // stubs are installed the NeedsDexPcEvents could change for the shadow frames on the stack.
        // If we don't update it here we would miss reporting dex pc events which is incorrect.
        ShadowFrame* shadow_frame = GetCurrentShadowFrame();
        DCHECK(shadow_frame != nullptr);
        shadow_frame->SetNotifyDexPcMoveEvents(
            Runtime::Current()->GetInstrumentation()->NeedsDexPcEvents(GetMethod(), GetThread()));
        return true;  // Continue.
      }

      DCHECK(!m->IsRuntimeMethod());
      const OatQuickMethodHeader* method_header = GetCurrentOatQuickMethodHeader();
      // If it is a JITed frame then just set the deopt bit if required otherwise continue.
      // We need kForceDeoptForRedefinition to ensure we don't use any JITed code after a
      // redefinition. We support redefinition only if the runtime has started off as a
      // debuggable runtime which makes sure we don't use any AOT or Nterp code.
      // The CheckCallerForDeopt is an optimization which we only do for non-native JITed code for
      // now. We can extend it to native methods but that needs reserving an additional stack slot.
      // We don't do it currently since that wasn't important for debugger performance.
      if (method_header != nullptr && method_header->HasShouldDeoptimizeFlag()) {
        if (deopt_all_frames_) {
          runtime_methods_need_deopt_check_ = true;
          SetShouldDeoptimizeFlag(DeoptimizeFlagValue::kForceDeoptForRedefinition);
        }
        SetShouldDeoptimizeFlag(DeoptimizeFlagValue::kCheckCallerForDeopt);
      }

      return true;  // Continue.
    }
    bool deopt_all_frames_;
    bool runtime_methods_need_deopt_check_;
  };
  if (kVerboseInstrumentation) {
    std::string thread_name;
    thread->GetThreadName(thread_name);
    LOG(INFO) << "Installing exit stubs in " << thread_name;
  }

  std::unique_ptr<Context> context(Context::Create());
  InstallStackVisitor visitor(thread,
                              context.get(),
                              deopt_all_frames);
  visitor.WalkStack(true);

  if (visitor.runtime_methods_need_deopt_check_) {
    thread->SetDeoptCheckRequired(true);
  }

  thread->VerifyStack();
}

void UpdateNeedsDexPcEventsOnStack(Thread* thread) REQUIRES(Locks::mutator_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());

  struct InstallStackVisitor final : public StackVisitor {
    InstallStackVisitor(Thread* thread_in, Context* context)
        : StackVisitor(thread_in, context, kInstrumentationStackWalk) {}

    bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
      ShadowFrame* shadow_frame = GetCurrentShadowFrame();
      if (shadow_frame != nullptr) {
        shadow_frame->SetNotifyDexPcMoveEvents(
            Runtime::Current()->GetInstrumentation()->NeedsDexPcEvents(GetMethod(), GetThread()));
      }
      return true;
    }
  };

  std::unique_ptr<Context> context(Context::Create());
  InstallStackVisitor visitor(thread, context.get());
  visitor.WalkStack(true);
}

void ReportMethodEntryForOnStackMethods(InstrumentationListener* listener, Thread* thread)
    REQUIRES(Locks::mutator_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());

  struct InstallStackVisitor final : public StackVisitor {
    InstallStackVisitor(Thread* thread_in, Context* context)
        : StackVisitor(thread_in, context, kInstrumentationStackWalk) {}

    bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
      ArtMethod* m = GetMethod();
      if (m == nullptr || m->IsRuntimeMethod()) {
        // Skip upcall / runtime methods
        return true;
      }

      if (GetCurrentShadowFrame() != nullptr) {
        stack_methods_.push_back(m);
      } else {
        const OatQuickMethodHeader* method_header = GetCurrentOatQuickMethodHeader();
        if (Runtime::Current()->GetInstrumentation()->MethodSupportsExitEvents(m, method_header)) {
          // It is unexpected to see a method enter event but not a method exit event so record
          // stack methods only for frames that support method exit events. Even if we deoptimize we
          // make sure that we only call method exit event if the frame supported it in the first
          // place. For ex: deoptimizing from JITed code with debug support calls a method exit hook
          // but deoptimizing from nterp doesn't.
          stack_methods_.push_back(m);
        }
      }
      return true;
    }

    std::vector<ArtMethod*> stack_methods_;
  };

  if (kVerboseInstrumentation) {
    std::string thread_name;
    thread->GetThreadName(thread_name);
    LOG(INFO) << "Updating DexPcMoveEvents on shadow frames on stack  " << thread_name;
  }

  std::unique_ptr<Context> context(Context::Create());
  InstallStackVisitor visitor(thread, context.get());
  visitor.WalkStack(true);

  // Create method enter events for all methods currently on the thread's stack.
  for (auto smi = visitor.stack_methods_.rbegin(); smi != visitor.stack_methods_.rend(); smi++) {
    listener->MethodEntered(thread, *smi);
  }
}

void Instrumentation::InstrumentThreadStack(Thread* thread, bool force_deopt) {
  run_exit_hooks_ = true;
  InstrumentationInstallStack(thread, force_deopt);
}

void Instrumentation::InstrumentAllThreadStacks(bool force_deopt) {
  run_exit_hooks_ = true;
  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  for (Thread* thread : Runtime::Current()->GetThreadList()->GetList()) {
    InstrumentThreadStack(thread, force_deopt);
  }
}

static void InstrumentationRestoreStack(Thread* thread) REQUIRES(Locks::mutator_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());

  struct RestoreStackVisitor final : public StackVisitor {
    RestoreStackVisitor(Thread* thread)
        : StackVisitor(thread, nullptr, kInstrumentationStackWalk), thread_(thread) {}

    bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
      if (GetCurrentQuickFrame() == nullptr) {
        return true;
      }

      const OatQuickMethodHeader* method_header = GetCurrentOatQuickMethodHeader();
      if (method_header != nullptr && method_header->HasShouldDeoptimizeFlag()) {
        // We shouldn't restore stack if any of the frames need a force deopt
        DCHECK(!ShouldForceDeoptForRedefinition());
        UnsetShouldDeoptimizeFlag(DeoptimizeFlagValue::kCheckCallerForDeopt);
      }
      return true;  // Continue.
    }
    Thread* const thread_;
  };

  if (kVerboseInstrumentation) {
    std::string thread_name;
    thread->GetThreadName(thread_name);
    LOG(INFO) << "Restoring stack for " << thread_name;
  }
  DCHECK(!thread->IsDeoptCheckRequired());
  RestoreStackVisitor visitor(thread);
  visitor.WalkStack(true);
}

static bool HasFramesNeedingForceDeopt(Thread* thread) REQUIRES(Locks::mutator_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());

  struct CheckForForceDeoptStackVisitor final : public StackVisitor {
    CheckForForceDeoptStackVisitor(Thread* thread)
        : StackVisitor(thread, nullptr, kInstrumentationStackWalk),
          thread_(thread),
          force_deopt_check_needed_(false) {}

    bool VisitFrame() override REQUIRES_SHARED(Locks::mutator_lock_) {
      if (GetCurrentQuickFrame() == nullptr) {
        return true;
      }

      const OatQuickMethodHeader* method_header = GetCurrentOatQuickMethodHeader();
      if (method_header != nullptr && method_header->HasShouldDeoptimizeFlag()) {
        if (ShouldForceDeoptForRedefinition()) {
          force_deopt_check_needed_ = true;
          return false;
        }
      }
      return true;  // Continue.
    }
    Thread* const thread_;
    bool force_deopt_check_needed_;
  };

  CheckForForceDeoptStackVisitor visitor(thread);
  visitor.WalkStack(true);
  // If there is a frame that requires a force deopt we should have set the IsDeoptCheckRequired
  // bit. We don't check if the bit needs to be reset on every method exit / deoptimization. We
  // only check when we no longer need instrumentation support. So it is possible that the bit is
  // set but we don't find any frames that need a force deopt on the stack so reverse implication
  // doesn't hold.
  DCHECK_IMPLIES(visitor.force_deopt_check_needed_, thread->IsDeoptCheckRequired());
  return visitor.force_deopt_check_needed_;
}

void Instrumentation::DeoptimizeAllThreadFrames() {
  InstrumentAllThreadStacks(/* force_deopt= */ true);
}

static bool HasEvent(Instrumentation::InstrumentationEvent expected, uint32_t events) {
  return (events & expected) != 0;
}

static bool PotentiallyAddListenerTo(Instrumentation::InstrumentationEvent event,
                                     uint32_t events,
                                     std::list<InstrumentationListener*>& list,
                                     InstrumentationListener* listener)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  if (!HasEvent(event, events)) {
    return false;
  }
  // If there is a free slot in the list, we insert the listener in that slot.
  // Otherwise we add it to the end of the list.
  auto it = std::find(list.begin(), list.end(), nullptr);
  if (it != list.end()) {
    *it = listener;
  } else {
    list.push_back(listener);
  }
  return true;
}

static void PotentiallyAddListenerTo(Instrumentation::InstrumentationEvent event,
                                     uint32_t events,
                                     std::list<InstrumentationListener*>& list,
                                     InstrumentationListener* listener,
                                     bool* has_listener)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  if (PotentiallyAddListenerTo(event, events, list, listener)) {
    *has_listener = true;
  }
}

static void PotentiallyAddListenerTo(Instrumentation::InstrumentationEvent event,
                                     uint32_t events,
                                     std::list<InstrumentationListener*>& list,
                                     InstrumentationListener* listener,
                                     uint8_t* has_listener,
                                     uint8_t flag)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  if (PotentiallyAddListenerTo(event, events, list, listener)) {
    *has_listener = *has_listener | flag;
  }
}

void Instrumentation::AddListener(InstrumentationListener* listener,
                                  uint32_t events,
                                  bool is_trace_listener) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  if (is_trace_listener) {
    PotentiallyAddListenerTo(kMethodEntered,
                             events,
                             method_entry_fast_trace_listeners_,
                             listener,
                             &have_method_entry_listeners_,
                             kFastTraceListeners);
  } else {
    PotentiallyAddListenerTo(kMethodEntered,
                             events,
                             method_entry_slow_listeners_,
                             listener,
                             &have_method_entry_listeners_,
                             kSlowMethodEntryExitListeners);
  }
  if (is_trace_listener) {
    PotentiallyAddListenerTo(kMethodExited,
                             events,
                             method_exit_fast_trace_listeners_,
                             listener,
                             &have_method_exit_listeners_,
                             kFastTraceListeners);
  } else {
    PotentiallyAddListenerTo(kMethodExited,
                             events,
                             method_exit_slow_listeners_,
                             listener,
                             &have_method_exit_listeners_,
                             kSlowMethodEntryExitListeners);
  }
  PotentiallyAddListenerTo(kMethodUnwind,
                           events,
                           method_unwind_listeners_,
                           listener,
                           &have_method_unwind_listeners_);
  PotentiallyAddListenerTo(kBranch,
                           events,
                           branch_listeners_,
                           listener,
                           &have_branch_listeners_);
  PotentiallyAddListenerTo(kDexPcMoved,
                           events,
                           dex_pc_listeners_,
                           listener,
                           &have_dex_pc_listeners_);
  PotentiallyAddListenerTo(kFieldRead,
                           events,
                           field_read_listeners_,
                           listener,
                           &have_field_read_listeners_);
  PotentiallyAddListenerTo(kFieldWritten,
                           events,
                           field_write_listeners_,
                           listener,
                           &have_field_write_listeners_);
  PotentiallyAddListenerTo(kExceptionThrown,
                           events,
                           exception_thrown_listeners_,
                           listener,
                           &have_exception_thrown_listeners_);
  PotentiallyAddListenerTo(kWatchedFramePop,
                           events,
                           watched_frame_pop_listeners_,
                           listener,
                           &have_watched_frame_pop_listeners_);
  PotentiallyAddListenerTo(kExceptionHandled,
                           events,
                           exception_handled_listeners_,
                           listener,
                           &have_exception_handled_listeners_);
  if (HasEvent(kDexPcMoved, events)) {
    MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
    for (Thread* thread : Runtime::Current()->GetThreadList()->GetList()) {
      UpdateNeedsDexPcEventsOnStack(thread);
    }
  }
}

static bool PotentiallyRemoveListenerFrom(Instrumentation::InstrumentationEvent event,
                                          uint32_t events,
                                          std::list<InstrumentationListener*>& list,
                                          InstrumentationListener* listener)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  if (!HasEvent(event, events)) {
    return false;
  }
  auto it = std::find(list.begin(), list.end(), listener);
  if (it != list.end()) {
    // Just update the entry, do not remove from the list. Removing entries in the list
    // is unsafe when mutators are iterating over it.
    *it = nullptr;
  }

  // Check if the list contains any non-null listener.
  for (InstrumentationListener* l : list) {
    if (l != nullptr) {
      return false;
    }
  }

  return true;
}

static void PotentiallyRemoveListenerFrom(Instrumentation::InstrumentationEvent event,
                                          uint32_t events,
                                          std::list<InstrumentationListener*>& list,
                                          InstrumentationListener* listener,
                                          bool* has_listener)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  if (PotentiallyRemoveListenerFrom(event, events, list, listener)) {
    *has_listener = false;
  }
}

static void PotentiallyRemoveListenerFrom(Instrumentation::InstrumentationEvent event,
                                          uint32_t events,
                                          std::list<InstrumentationListener*>& list,
                                          InstrumentationListener* listener,
                                          uint8_t* has_listener,
                                          uint8_t flag)
    REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_, !Locks::classlinker_classes_lock_) {
  if (PotentiallyRemoveListenerFrom(event, events, list, listener)) {
    *has_listener = *has_listener & ~flag;
  }
}

void Instrumentation::RemoveListener(InstrumentationListener* listener,
                                     uint32_t events,
                                     bool is_trace_listener) {
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  if (is_trace_listener) {
    PotentiallyRemoveListenerFrom(kMethodEntered,
                                  events,
                                  method_entry_fast_trace_listeners_,
                                  listener,
                                  &have_method_entry_listeners_,
                                  kFastTraceListeners);
  } else {
    PotentiallyRemoveListenerFrom(kMethodEntered,
                                  events,
                                  method_entry_slow_listeners_,
                                  listener,
                                  &have_method_entry_listeners_,
                                  kSlowMethodEntryExitListeners);
  }
  if (is_trace_listener) {
    PotentiallyRemoveListenerFrom(kMethodExited,
                                  events,
                                  method_exit_fast_trace_listeners_,
                                  listener,
                                  &have_method_exit_listeners_,
                                  kFastTraceListeners);
  } else {
    PotentiallyRemoveListenerFrom(kMethodExited,
                                  events,
                                  method_exit_slow_listeners_,
                                  listener,
                                  &have_method_exit_listeners_,
                                  kSlowMethodEntryExitListeners);
  }
  PotentiallyRemoveListenerFrom(kMethodUnwind,
                                events,
                                method_unwind_listeners_,
                                listener,
                                &have_method_unwind_listeners_);
  PotentiallyRemoveListenerFrom(kBranch,
                                events,
                                branch_listeners_,
                                listener,
                                &have_branch_listeners_);
  PotentiallyRemoveListenerFrom(kDexPcMoved,
                                events,
                                dex_pc_listeners_,
                                listener,
                                &have_dex_pc_listeners_);
  PotentiallyRemoveListenerFrom(kFieldRead,
                                events,
                                field_read_listeners_,
                                listener,
                                &have_field_read_listeners_);
  PotentiallyRemoveListenerFrom(kFieldWritten,
                                events,
                                field_write_listeners_,
                                listener,
                                &have_field_write_listeners_);
  PotentiallyRemoveListenerFrom(kExceptionThrown,
                                events,
                                exception_thrown_listeners_,
                                listener,
                                &have_exception_thrown_listeners_);
  PotentiallyRemoveListenerFrom(kWatchedFramePop,
                                events,
                                watched_frame_pop_listeners_,
                                listener,
                                &have_watched_frame_pop_listeners_);
  PotentiallyRemoveListenerFrom(kExceptionHandled,
                                events,
                                exception_handled_listeners_,
                                listener,
                                &have_exception_handled_listeners_);
  if (HasEvent(kDexPcMoved, events)) {
    MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
    for (Thread* thread : Runtime::Current()->GetThreadList()->GetList()) {
      UpdateNeedsDexPcEventsOnStack(thread);
    }
  }
}

Instrumentation::InstrumentationLevel Instrumentation::GetCurrentInstrumentationLevel() const {
  return instrumentation_level_;
}

void Instrumentation::ConfigureStubs(const char* key,
                                     InstrumentationLevel desired_level,
                                     bool try_switch_to_non_debuggable) {
  // Store the instrumentation level for this key or remove it.
  if (desired_level == InstrumentationLevel::kInstrumentNothing) {
    // The client no longer needs instrumentation.
    requested_instrumentation_levels_.erase(key);
  } else {
    // The client needs instrumentation.
    requested_instrumentation_levels_.Overwrite(key, desired_level);
  }

  UpdateStubs(try_switch_to_non_debuggable);
}

void Instrumentation::UpdateInstrumentationLevel(InstrumentationLevel requested_level) {
  instrumentation_level_ = requested_level;
}

void Instrumentation::EnableEntryExitHooks(const char* key) {
  DCHECK(Runtime::Current()->IsJavaDebuggable());
  ConfigureStubs(key,
                 InstrumentationLevel::kInstrumentWithEntryExitHooks,
                 /*try_switch_to_non_debuggable=*/false);
}

void Instrumentation::MaybeRestoreInstrumentationStack() {
  // Restore stack only if there is no method currently deoptimized.
  if (!IsDeoptimizedMethodsEmpty()) {
    return;
  }

  Thread* self = Thread::Current();
  MutexLock mu(self, *Locks::thread_list_lock_);
  bool no_remaining_deopts = true;
  // Check that there are no other forced deoptimizations. Do it here so we only need to lock
  // thread_list_lock once.
  // The compiler gets confused on the thread annotations, so use
  // NO_THREAD_SAFETY_ANALYSIS. Note that we hold the mutator lock
  // exclusively at this point.
  Locks::mutator_lock_->AssertExclusiveHeld(self);
  Runtime::Current()->GetThreadList()->ForEach([&](Thread* t) NO_THREAD_SAFETY_ANALYSIS {
    bool has_force_deopt_frames = HasFramesNeedingForceDeopt(t);
    if (!has_force_deopt_frames) {
      // We no longer have any frames that require a force deopt check. If the bit was true then we
      // had some frames earlier but they already got deoptimized and are no longer on stack.
      t->SetDeoptCheckRequired(false);
    }
    no_remaining_deopts =
        no_remaining_deopts &&
        !t->IsForceInterpreter() &&
        !t->HasDebuggerShadowFrames() &&
        !has_force_deopt_frames;
  });
  if (no_remaining_deopts) {
    Runtime::Current()->GetThreadList()->ForEach(InstrumentationRestoreStack);
    run_exit_hooks_ = false;
  }
}

void Instrumentation::UpdateStubs(bool try_switch_to_non_debuggable) {
  // Look for the highest required instrumentation level.
  InstrumentationLevel requested_level = InstrumentationLevel::kInstrumentNothing;
  for (const auto& v : requested_instrumentation_levels_) {
    requested_level = std::max(requested_level, v.second);
  }

  if (GetCurrentInstrumentationLevel() == requested_level) {
    // We're already set.
    return;
  }

  Thread* const self = Thread::Current();
  Runtime* runtime = Runtime::Current();
  Locks::mutator_lock_->AssertExclusiveHeld(self);
  Locks::thread_list_lock_->AssertNotHeld(self);
  // The following needs to happen in the same order.
  // 1. Update the instrumentation level
  // 2. Switch the runtime to non-debuggable if requested. We switch to non-debuggable only when
  // the instrumentation level is set to kInstrumentNothing. So this needs to happen only after
  // updating the instrumentation level.
  // 3. Update the entry points. We use AOT code only if we aren't debuggable runtime. So update
  // entrypoints after switching the instrumentation level.
  UpdateInstrumentationLevel(requested_level);
  if (try_switch_to_non_debuggable) {
    MaybeSwitchRuntimeDebugState(self);
  }
  InstallStubsClassVisitor visitor(this);
  runtime->GetClassLinker()->VisitClasses(&visitor);
  if (requested_level > InstrumentationLevel::kInstrumentNothing) {
    InstrumentAllThreadStacks(/* force_deopt= */ false);
  } else {
    MaybeRestoreInstrumentationStack();
  }
}

static void ResetQuickAllocEntryPointsForThread(Thread* thread, [[maybe_unused]] void* arg) {
  thread->ResetQuickAllocEntryPointsForThread();
}

void Instrumentation::SetEntrypointsInstrumented(bool instrumented) {
  Thread* self = Thread::Current();
  Runtime* runtime = Runtime::Current();
  Locks::mutator_lock_->AssertNotHeld(self);
  Locks::instrument_entrypoints_lock_->AssertHeld(self);
  if (runtime->IsStarted()) {
    ScopedSuspendAll ssa(__FUNCTION__);
    MutexLock mu(self, *Locks::runtime_shutdown_lock_);
    SetQuickAllocEntryPointsInstrumented(instrumented);
    ResetQuickAllocEntryPoints();
    alloc_entrypoints_instrumented_ = instrumented;
  } else {
    MutexLock mu(self, *Locks::runtime_shutdown_lock_);
    SetQuickAllocEntryPointsInstrumented(instrumented);

    // Note: ResetQuickAllocEntryPoints only works when the runtime is started. Manually run the
    //       update for just this thread.
    // Note: self may be null. One of those paths is setting instrumentation in the Heap
    //       constructor for gcstress mode.
    if (self != nullptr) {
      ResetQuickAllocEntryPointsForThread(self, nullptr);
    }

    alloc_entrypoints_instrumented_ = instrumented;
  }
}

void Instrumentation::InstrumentQuickAllocEntryPoints() {
  MutexLock mu(Thread::Current(), *Locks::instrument_entrypoints_lock_);
  InstrumentQuickAllocEntryPointsLocked();
}

void Instrumentation::UninstrumentQuickAllocEntryPoints() {
  MutexLock mu(Thread::Current(), *Locks::instrument_entrypoints_lock_);
  UninstrumentQuickAllocEntryPointsLocked();
}

void Instrumentation::InstrumentQuickAllocEntryPointsLocked() {
  Locks::instrument_entrypoints_lock_->AssertHeld(Thread::Current());
  if (quick_alloc_entry_points_instrumentation_counter_ == 0) {
    SetEntrypointsInstrumented(true);
  }
  ++quick_alloc_entry_points_instrumentation_counter_;
}

void Instrumentation::UninstrumentQuickAllocEntryPointsLocked() {
  Locks::instrument_entrypoints_lock_->AssertHeld(Thread::Current());
  CHECK_GT(quick_alloc_entry_points_instrumentation_counter_, 0U);
  --quick_alloc_entry_points_instrumentation_counter_;
  if (quick_alloc_entry_points_instrumentation_counter_ == 0) {
    SetEntrypointsInstrumented(false);
  }
}

void Instrumentation::ResetQuickAllocEntryPoints() {
  Runtime* runtime = Runtime::Current();
  if (runtime->IsStarted()) {
    MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
    runtime->GetThreadList()->ForEach(ResetQuickAllocEntryPointsForThread, nullptr);
  }
}

std::string Instrumentation::EntryPointString(const void* code) {
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  jit::Jit* jit = Runtime::Current()->GetJit();
  if (class_linker->IsQuickToInterpreterBridge(code)) {
    return "interpreter";
  } else if (class_linker->IsQuickResolutionStub(code)) {
    return "resolution";
  } else if (jit != nullptr && jit->GetCodeCache()->ContainsPc(code)) {
    return "jit";
  } else if (code == GetInvokeObsoleteMethodStub()) {
    return "obsolete";
  } else if (code == interpreter::GetNterpEntryPoint()) {
    return "nterp";
  } else if (code == interpreter::GetNterpWithClinitEntryPoint()) {
    return "nterp with clinit";
  } else if (class_linker->IsQuickGenericJniStub(code)) {
    return "generic jni";
  } else if (Runtime::Current()->GetOatFileManager().ContainsPc(code)) {
    return "oat";
  } else if (OatQuickMethodHeader::IsStub(reinterpret_cast<const uint8_t*>(code)).value_or(false)) {
    return "stub";
  }
  return "unknown";
}

void Instrumentation::UpdateMethodsCodeImpl(ArtMethod* method, const void* new_code) {
  if (!EntryExitStubsInstalled()) {
    // Fast path: no instrumentation.
    DCHECK(!IsDeoptimized(method));
    UpdateEntryPoints(method, new_code);
    return;
  }

  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  if (class_linker->IsQuickToInterpreterBridge(new_code)) {
    // It's always OK to update to the interpreter.
    UpdateEntryPoints(method, new_code);
    return;
  }

  if (InterpretOnly(method)) {
    DCHECK(class_linker->IsQuickToInterpreterBridge(method->GetEntryPointFromQuickCompiledCode()))
        << EntryPointString(method->GetEntryPointFromQuickCompiledCode());
    // Don't update, stay deoptimized.
    return;
  }

  if (EntryExitStubsInstalled() && !CodeSupportsEntryExitHooks(new_code, method)) {
    DCHECK(CodeSupportsEntryExitHooks(method->GetEntryPointFromQuickCompiledCode(), method))
        << EntryPointString(method->GetEntryPointFromQuickCompiledCode()) << " "
        << method->PrettyMethod();
    // If we need entry / exit stubs but the new_code doesn't support entry / exit hooks just skip.
    return;
  }

  // At this point, we can update as asked.
  UpdateEntryPoints(method, new_code);
}

void Instrumentation::UpdateNativeMethodsCodeToJitCode(ArtMethod* method, const void* new_code) {
  // We don't do any read barrier on `method`'s declaring class in this code, as the JIT might
  // enter here on a soon-to-be deleted ArtMethod. Updating the entrypoint is OK though, as
  // the ArtMethod is still in memory.
  if (EntryExitStubsInstalled() && !CodeSupportsEntryExitHooks(new_code, method)) {
    // If the new code doesn't support entry exit hooks but we need them don't update with the new
    // code.
    return;
  }
  UpdateEntryPoints(method, new_code);
}

void Instrumentation::UpdateMethodsCode(ArtMethod* method, const void* new_code) {
  DCHECK(method->GetDeclaringClass()->IsResolved());
  UpdateMethodsCodeImpl(method, new_code);
}

bool Instrumentation::AddDeoptimizedMethod(ArtMethod* method) {
  if (IsDeoptimizedMethod(method)) {
    // Already in the map. Return.
    return false;
  }
  // Not found. Add it.
  deoptimized_methods_.insert(method);
  return true;
}

bool Instrumentation::IsDeoptimizedMethod(ArtMethod* method) {
  return deoptimized_methods_.find(method) != deoptimized_methods_.end();
}

bool Instrumentation::RemoveDeoptimizedMethod(ArtMethod* method) {
  auto it = deoptimized_methods_.find(method);
  if (it == deoptimized_methods_.end()) {
    return false;
  }
  deoptimized_methods_.erase(it);
  return true;
}

void Instrumentation::Deoptimize(ArtMethod* method) {
  CHECK(!method->IsNative());
  CHECK(!method->IsProxyMethod());
  CHECK(method->IsInvokable());

  {
    Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
    bool has_not_been_deoptimized = AddDeoptimizedMethod(method);
    CHECK(has_not_been_deoptimized) << "Method " << ArtMethod::PrettyMethod(method)
        << " is already deoptimized";
  }
  if (!InterpreterStubsInstalled()) {
    UpdateEntryPoints(method, GetQuickToInterpreterBridge());

    // Instrument thread stacks to request a check if the caller needs a deoptimization.
    // This isn't a strong deopt. We deopt this method if it is still in the deopt methods list.
    // If by the time we hit this frame we no longer need a deopt it is safe to continue.
    InstrumentAllThreadStacks(/* force_deopt= */ false);
  }
  CHECK_EQ(method->GetEntryPointFromQuickCompiledCode(), GetQuickToInterpreterBridge());
}

void Instrumentation::Undeoptimize(ArtMethod* method) {
  CHECK(!method->IsNative());
  CHECK(!method->IsProxyMethod());
  CHECK(method->IsInvokable());

  {
    Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
    bool found_and_erased = RemoveDeoptimizedMethod(method);
    CHECK(found_and_erased) << "Method " << ArtMethod::PrettyMethod(method)
        << " is not deoptimized";
  }

  // If interpreter stubs are still needed nothing to do.
  if (InterpreterStubsInstalled()) {
    return;
  }

  if (method->IsObsolete()) {
    // Don't update entry points for obsolete methods. The entrypoint should
    // have been set to InvokeObsoleteMethoStub.
    DCHECK_EQ(method->GetEntryPointFromQuickCompiledCodePtrSize(kRuntimePointerSize),
              GetInvokeObsoleteMethodStub());
    return;
  }

  // We are not using interpreter stubs for deoptimization. Restore the code of the method.
  // We still retain interpreter bridge if we need it for other reasons.
  if (InterpretOnly(method)) {
    UpdateEntryPoints(method, GetQuickToInterpreterBridge());
  } else if (method->StillNeedsClinitCheck()) {
    UpdateEntryPoints(method, GetQuickResolutionStub());
  } else {
    UpdateEntryPoints(method, GetMaybeInstrumentedCodeForInvoke(method));
  }

  // If there is no deoptimized method left, we can restore the stack of each thread.
  if (!EntryExitStubsInstalled()) {
    MaybeRestoreInstrumentationStack();
  }
}

bool Instrumentation::IsDeoptimizedMethodsEmpty() const {
  return deoptimized_methods_.empty();
}

bool Instrumentation::IsDeoptimized(ArtMethod* method) {
  DCHECK(method != nullptr);
  return IsDeoptimizedMethod(method);
}

void Instrumentation::DisableDeoptimization(const char* key, bool try_switch_to_non_debuggable) {
  // Remove any instrumentation support added for deoptimization.
  ConfigureStubs(key, InstrumentationLevel::kInstrumentNothing, try_switch_to_non_debuggable);
  Locks::mutator_lock_->AssertExclusiveHeld(Thread::Current());
  // Undeoptimized selected methods.
  while (true) {
    ArtMethod* method;
    {
      if (deoptimized_methods_.empty()) {
        break;
      }
      method = *deoptimized_methods_.begin();
      CHECK(method != nullptr);
    }
    Undeoptimize(method);
  }
}

void Instrumentation::MaybeSwitchRuntimeDebugState(Thread* self) {
  Runtime* runtime = Runtime::Current();
  // Return early if runtime is shutting down.
  if (runtime->IsShuttingDown(self)) {
    return;
  }

  // Don't switch the state if we started off as JavaDebuggable or if we still need entry / exit
  // hooks for other reasons.
  if (EntryExitStubsInstalled() || runtime->IsJavaDebuggableAtInit()) {
    return;
  }

  art::jit::Jit* jit = runtime->GetJit();
  if (jit != nullptr) {
    jit->GetCodeCache()->InvalidateAllCompiledCode();
    jit->GetJitCompiler()->SetDebuggableCompilerOption(false);
  }
  runtime->SetRuntimeDebugState(art::Runtime::RuntimeDebugState::kNonJavaDebuggable);
}

void Instrumentation::DeoptimizeEverything(const char* key) {
  // We want to switch to non-debuggable only when the debugger / profile tools are detaching.
  // This call is used for supporting debug related features (ex: single stepping across all
  // threads) while the debugger is still connected.
  ConfigureStubs(key,
                 InstrumentationLevel::kInstrumentWithInterpreter,
                 /*try_switch_to_non_debuggable=*/false);
}

void Instrumentation::UndeoptimizeEverything(const char* key) {
  CHECK(InterpreterStubsInstalled());
  // We want to switch to non-debuggable only when the debugger / profile tools are detaching.
  // This is used when we no longer need to run in interpreter. The debugger is still connected
  // so don't switch the runtime. We use "DisableDeoptimization" when detaching the debugger.
  ConfigureStubs(key,
                 InstrumentationLevel::kInstrumentNothing,
                 /*try_switch_to_non_debuggable=*/false);
}

void Instrumentation::EnableMethodTracing(const char* key,
                                          InstrumentationListener* listener,
                                          bool needs_interpreter) {
  InstrumentationLevel level;
  if (needs_interpreter) {
    level = InstrumentationLevel::kInstrumentWithInterpreter;
  } else {
    level = InstrumentationLevel::kInstrumentWithEntryExitHooks;
  }
  // We are enabling method tracing here and need to stay in debuggable.
  ConfigureStubs(key, level, /*try_switch_to_non_debuggable=*/false);

  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  for (Thread* thread : Runtime::Current()->GetThreadList()->GetList()) {
    ReportMethodEntryForOnStackMethods(listener, thread);
  }
}

void Instrumentation::DisableMethodTracing(const char* key) {
  // We no longer need to be in debuggable runtime since we are stopping method tracing. If no
  // other debugger / profiling tools are active switch back to non-debuggable.
  ConfigureStubs(key,
                 InstrumentationLevel::kInstrumentNothing,
                 /*try_switch_to_non_debuggable=*/true);
}

const void* Instrumentation::GetCodeForInvoke(ArtMethod* method) {
  // This is called by instrumentation and resolution trampolines
  // and that should never be getting proxy methods.
  DCHECK(!method->IsProxyMethod()) << method->PrettyMethod();
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  const void* code = method->GetEntryPointFromQuickCompiledCodePtrSize(kRuntimePointerSize);
  // If we don't have the instrumentation, the resolution stub, or the
  // interpreter, just return the current entrypoint,
  // assuming it's the most optimized.
  if (!class_linker->IsQuickResolutionStub(code) &&
      !class_linker->IsQuickToInterpreterBridge(code)) {
    return code;
  }

  if (InterpretOnly(method)) {
    // If we're forced into interpreter just use it.
    return GetQuickToInterpreterBridge();
  }

  return GetOptimizedCodeFor(method);
}

const void* Instrumentation::GetMaybeInstrumentedCodeForInvoke(ArtMethod* method) {
  // This is called by resolution trampolines and that should never be getting proxy methods.
  DCHECK(!method->IsProxyMethod()) << method->PrettyMethod();
  const void* code = GetCodeForInvoke(method);
  if (EntryExitStubsInstalled() && !CodeSupportsEntryExitHooks(code, method)) {
    return method->IsNative() ? GetQuickGenericJniStub() : GetQuickToInterpreterBridge();
  }
  return code;
}

void Instrumentation::MethodEnterEventImpl(Thread* thread, ArtMethod* method) const {
  DCHECK(!method->IsRuntimeMethod());
  if (HasMethodEntryListeners()) {
    for (InstrumentationListener* listener : method_entry_slow_listeners_) {
      if (listener != nullptr) {
        listener->MethodEntered(thread, method);
      }
    }
    for (InstrumentationListener* listener : method_entry_fast_trace_listeners_) {
      if (listener != nullptr) {
        listener->MethodEntered(thread, method);
      }
    }
  }
}

template <>
void Instrumentation::MethodExitEventImpl(Thread* thread,
                                          ArtMethod* method,
                                          OptionalFrame frame,
                                          MutableHandle<mirror::Object>& return_value) const {
  if (HasMethodExitListeners()) {
    for (InstrumentationListener* listener : method_exit_slow_listeners_) {
      if (listener != nullptr) {
        listener->MethodExited(thread, method, frame, return_value);
      }
    }
    for (InstrumentationListener* listener : method_exit_fast_trace_listeners_) {
      if (listener != nullptr) {
        listener->MethodExited(thread, method, frame, return_value);
      }
    }
  }
}

template<> void Instrumentation::MethodExitEventImpl(Thread* thread,
                                                     ArtMethod* method,
                                                     OptionalFrame frame,
                                                     JValue& return_value) const {
  if (HasMethodExitListeners()) {
    Thread* self = Thread::Current();
    StackHandleScope<1> hs(self);
    if (method->GetInterfaceMethodIfProxy(kRuntimePointerSize)->GetReturnTypePrimitive() !=
        Primitive::kPrimNot) {
      for (InstrumentationListener* listener : method_exit_slow_listeners_) {
        if (listener != nullptr) {
          listener->MethodExited(thread, method, frame, return_value);
        }
      }
      for (InstrumentationListener* listener : method_exit_fast_trace_listeners_) {
        if (listener != nullptr) {
          listener->MethodExited(thread, method, frame, return_value);
        }
      }
    } else {
      MutableHandle<mirror::Object> ret(hs.NewHandle(return_value.GetL()));
      MethodExitEventImpl(thread, method, frame, ret);
      return_value.SetL(ret.Get());
    }
  }
}

void Instrumentation::MethodUnwindEvent(Thread* thread,
                                        ArtMethod* method,
                                        uint32_t dex_pc) const {
  if (HasMethodUnwindListeners()) {
    for (InstrumentationListener* listener : method_unwind_listeners_) {
      if (listener != nullptr) {
        listener->MethodUnwind(thread, method, dex_pc);
      }
    }
  }
}

void Instrumentation::DexPcMovedEventImpl(Thread* thread,
                                          ObjPtr<mirror::Object> this_object,
                                          ArtMethod* method,
                                          uint32_t dex_pc) const {
  Thread* self = Thread::Current();
  StackHandleScope<1> hs(self);
  Handle<mirror::Object> thiz(hs.NewHandle(this_object));
  for (InstrumentationListener* listener : dex_pc_listeners_) {
    if (listener != nullptr) {
      listener->DexPcMoved(thread, thiz, method, dex_pc);
    }
  }
}

void Instrumentation::BranchImpl(Thread* thread,
                                 ArtMethod* method,
                                 uint32_t dex_pc,
                                 int32_t offset) const {
  for (InstrumentationListener* listener : branch_listeners_) {
    if (listener != nullptr) {
      listener->Branch(thread, method, dex_pc, offset);
    }
  }
}

void Instrumentation::WatchedFramePopImpl(Thread* thread, const ShadowFrame& frame) const {
  for (InstrumentationListener* listener : watched_frame_pop_listeners_) {
    if (listener != nullptr) {
      listener->WatchedFramePop(thread, frame);
    }
  }
}

void Instrumentation::FieldReadEventImpl(Thread* thread,
                                         ObjPtr<mirror::Object> this_object,
                                         ArtMethod* method,
                                         uint32_t dex_pc,
                                         ArtField* field) const {
  Thread* self = Thread::Current();
  StackHandleScope<1> hs(self);
  Handle<mirror::Object> thiz(hs.NewHandle(this_object));
  for (InstrumentationListener* listener : field_read_listeners_) {
    if (listener != nullptr) {
      listener->FieldRead(thread, thiz, method, dex_pc, field);
    }
  }
}

void Instrumentation::FieldWriteEventImpl(Thread* thread,
                                          ObjPtr<mirror::Object> this_object,
                                          ArtMethod* method,
                                          uint32_t dex_pc,
                                          ArtField* field,
                                          const JValue& field_value) const {
  Thread* self = Thread::Current();
  StackHandleScope<2> hs(self);
  Handle<mirror::Object> thiz(hs.NewHandle(this_object));
  if (field->IsPrimitiveType()) {
    for (InstrumentationListener* listener : field_write_listeners_) {
      if (listener != nullptr) {
        listener->FieldWritten(thread, thiz, method, dex_pc, field, field_value);
      }
    }
  } else {
    Handle<mirror::Object> val(hs.NewHandle(field_value.GetL()));
    for (InstrumentationListener* listener : field_write_listeners_) {
      if (listener != nullptr) {
        listener->FieldWritten(thread, thiz, method, dex_pc, field, val);
      }
    }
  }
}

void Instrumentation::ExceptionThrownEvent(Thread* thread,
                                           ObjPtr<mirror::Throwable> exception_object) const {
  Thread* self = Thread::Current();
  StackHandleScope<1> hs(self);
  Handle<mirror::Throwable> h_exception(hs.NewHandle(exception_object));
  if (HasExceptionThrownListeners()) {
    DCHECK_EQ(thread->GetException(), h_exception.Get());
    thread->ClearException();
    for (InstrumentationListener* listener : exception_thrown_listeners_) {
      if (listener != nullptr) {
        listener->ExceptionThrown(thread, h_exception);
      }
    }
    // See b/65049545 for discussion about this behavior.
    thread->AssertNoPendingException();
    thread->SetException(h_exception.Get());
  }
}

void Instrumentation::ExceptionHandledEvent(Thread* thread,
                                            ObjPtr<mirror::Throwable> exception_object) const {
  Thread* self = Thread::Current();
  StackHandleScope<1> hs(self);
  Handle<mirror::Throwable> h_exception(hs.NewHandle(exception_object));
  if (HasExceptionHandledListeners()) {
    // We should have cleared the exception so that callers can detect a new one.
    DCHECK(thread->GetException() == nullptr);
    for (InstrumentationListener* listener : exception_handled_listeners_) {
      if (listener != nullptr) {
        listener->ExceptionHandled(thread, h_exception);
      }
    }
  }
}

DeoptimizationMethodType Instrumentation::GetDeoptimizationMethodType(ArtMethod* method) {
  if (method->IsRuntimeMethod()) {
    // Certain methods have strict requirement on whether the dex instruction
    // should be re-executed upon deoptimization.
    if (method == Runtime::Current()->GetCalleeSaveMethod(
        CalleeSaveType::kSaveEverythingForClinit)) {
      return DeoptimizationMethodType::kKeepDexPc;
    }
    if (method == Runtime::Current()->GetCalleeSaveMethod(
        CalleeSaveType::kSaveEverythingForSuspendCheck)) {
      return DeoptimizationMethodType::kKeepDexPc;
    }
  }
  return DeoptimizationMethodType::kDefault;
}

JValue Instrumentation::GetReturnValue(ArtMethod* method,
                                       bool* is_ref,
                                       uint64_t* gpr_result,
                                       uint64_t* fpr_result) {
  uint32_t length;
  const PointerSize pointer_size = Runtime::Current()->GetClassLinker()->GetImagePointerSize();

  // Runtime method does not call into MethodExitEvent() so there should not be
  // suspension point below.
  ScopedAssertNoThreadSuspension ants(__FUNCTION__, method->IsRuntimeMethod());
  DCHECK(!method->IsRuntimeMethod());
  char return_shorty = method->GetInterfaceMethodIfProxy(pointer_size)->GetShorty(&length)[0];

  *is_ref = return_shorty == '[' || return_shorty == 'L';
  JValue return_value;
  if (return_shorty == 'V') {
    return_value.SetJ(0);
  } else if (return_shorty == 'F' || return_shorty == 'D') {
    return_value.SetJ(*fpr_result);
  } else {
    return_value.SetJ(*gpr_result);
  }
  return return_value;
}

bool Instrumentation::PushDeoptContextIfNeeded(Thread* self,
                                               DeoptimizationMethodType deopt_type,
                                               bool is_ref,
                                               const JValue& return_value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (self->IsExceptionPending()) {
    return false;
  }

  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrame();
  DCHECK(sp != nullptr && (*sp)->IsRuntimeMethod());
  if (!ShouldDeoptimizeCaller(self, sp)) {
    return false;
  }

  // TODO(mythria): The current deopt behaviour is we just re-execute the
  // alloc instruction so we don't need the return value. For instrumentation
  // related deopts, we actually don't need to and can use the result we got
  // here. Since this is a debug only feature it is not very important but
  // consider reusing the result in future.
  self->PushDeoptimizationContext(
      return_value, is_ref, nullptr, /* from_code= */ false, deopt_type);
  self->SetException(Thread::GetDeoptimizationException());
  return true;
}

void Instrumentation::DeoptimizeIfNeeded(Thread* self,
                                         ArtMethod** sp,
                                         DeoptimizationMethodType type,
                                         JValue return_value,
                                         bool is_reference) {
  if (self->IsAsyncExceptionPending() || ShouldDeoptimizeCaller(self, sp)) {
    self->PushDeoptimizationContext(return_value,
                                    is_reference,
                                    nullptr,
                                    /* from_code= */ false,
                                    type);
    // This is requested from suspend points or when returning from runtime methods so exit
    // callbacks wouldn't be run yet. So don't skip method callbacks.
    artDeoptimize(self, /* skip_method_exit_callbacks= */ false);
  }
}

bool Instrumentation::NeedsSlowInterpreterForMethod(Thread* self, ArtMethod* method) {
  return (method != nullptr) &&
         (InterpreterStubsInstalled() ||
          IsDeoptimized(method) ||
          self->IsForceInterpreter() ||
          // NB Since structurally obsolete compiled methods might have the offsets of
          // methods/fields compiled in we need to go back to interpreter whenever we hit
          // them.
          method->GetDeclaringClass()->IsObsoleteObject() ||
          Dbg::IsForcedInterpreterNeededForUpcall(self, method));
}

bool Instrumentation::ShouldDeoptimizeCaller(Thread* self, ArtMethod** sp) {
  // When exit stubs aren't called we don't need to check for any instrumentation related
  // deoptimizations.
  if (!RunExitHooks()) {
    return false;
  }

  ArtMethod* runtime_method = *sp;
  DCHECK(runtime_method->IsRuntimeMethod());
  QuickMethodFrameInfo frame_info = Runtime::Current()->GetRuntimeMethodFrameInfo(runtime_method);
  return ShouldDeoptimizeCaller(self, sp, frame_info.FrameSizeInBytes());
}

bool Instrumentation::ShouldDeoptimizeCaller(Thread* self, ArtMethod** sp, size_t frame_size) {
  uintptr_t caller_sp = reinterpret_cast<uintptr_t>(sp) + frame_size;
  ArtMethod* caller = *(reinterpret_cast<ArtMethod**>(caller_sp));
  uintptr_t caller_pc_addr = reinterpret_cast<uintptr_t>(sp) + (frame_size - sizeof(void*));
  uintptr_t caller_pc = *reinterpret_cast<uintptr_t*>(caller_pc_addr);

  if (caller == nullptr ||
      caller->IsNative() ||
      caller->IsRuntimeMethod()) {
    // We need to check for a deoptimization here because when a redefinition happens it is
    // not safe to use any compiled code because the field offsets might change. For native
    // methods, we don't embed any field offsets so no need to check for a deoptimization.
    // If the caller is null we don't need to do anything. This can happen when the caller
    // is being interpreted by the switch interpreter (when called from
    // artQuickToInterpreterBridge) / during shutdown / early startup.
    return false;
  }

  bool needs_deopt = NeedsSlowInterpreterForMethod(self, caller);

  // Non java debuggable apps don't support redefinition and hence it isn't required to check if
  // frame needs to be deoptimized. Even in debuggable apps, we only need this check when a
  // redefinition has actually happened. This is indicated by IsDeoptCheckRequired flag. We also
  // want to avoid getting method header when we need a deopt anyway.
  if (Runtime::Current()->IsJavaDebuggable() && !needs_deopt && self->IsDeoptCheckRequired()) {
    const OatQuickMethodHeader* header = caller->GetOatQuickMethodHeader(caller_pc);
    if (header != nullptr && header->HasShouldDeoptimizeFlag()) {
      DCHECK(header->IsOptimized());
      uint8_t* should_deopt_flag_addr =
          reinterpret_cast<uint8_t*>(caller_sp) + header->GetShouldDeoptimizeFlagOffset();
      if ((*should_deopt_flag_addr &
           static_cast<uint8_t>(DeoptimizeFlagValue::kForceDeoptForRedefinition)) != 0) {
        needs_deopt = true;
      }
    }
  }

  if (needs_deopt) {
    if (!Runtime::Current()->IsAsyncDeoptimizeable(caller, caller_pc)) {
      LOG(WARNING) << "Got a deoptimization request on un-deoptimizable method "
                   << caller->PrettyMethod();
      return false;
    }
    return true;
  }

  return false;
}

}  // namespace instrumentation
}  // namespace art
