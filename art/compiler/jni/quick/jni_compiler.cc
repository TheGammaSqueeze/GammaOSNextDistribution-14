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

#include "jni_compiler.h"

#include <algorithm>
#include <fstream>
#include <ios>
#include <memory>
#include <vector>

#include "art_method.h"
#include "base/arena_allocator.h"
#include "base/arena_containers.h"
#include "base/enums.h"
#include "base/logging.h"  // For VLOG.
#include "base/macros.h"
#include "base/memory_region.h"
#include "base/utils.h"
#include "calling_convention.h"
#include "class_linker.h"
#include "dwarf/debug_frame_opcode_writer.h"
#include "dex/dex_file-inl.h"
#include "driver/compiler_options.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "instrumentation.h"
#include "jni/jni_env_ext.h"
#include "jni/local_reference_table.h"
#include "runtime.h"
#include "thread.h"
#include "utils/arm/managed_register_arm.h"
#include "utils/arm64/managed_register_arm64.h"
#include "utils/assembler.h"
#include "utils/jni_macro_assembler.h"
#include "utils/managed_register.h"
#include "utils/x86/managed_register_x86.h"

#define __ jni_asm->

namespace art HIDDEN {

template <PointerSize kPointerSize>
static void SetNativeParameter(JNIMacroAssembler<kPointerSize>* jni_asm,
                               JniCallingConvention* jni_conv,
                               ManagedRegister in_reg);

template <PointerSize kPointerSize>
static void CallDecodeReferenceResult(JNIMacroAssembler<kPointerSize>* jni_asm,
                                      JniCallingConvention* jni_conv,
                                      ManagedRegister mr_return_reg,
                                      size_t main_out_arg_size);

template <PointerSize kPointerSize>
static std::unique_ptr<JNIMacroAssembler<kPointerSize>> GetMacroAssembler(
    ArenaAllocator* allocator, InstructionSet isa, const InstructionSetFeatures* features) {
  return JNIMacroAssembler<kPointerSize>::Create(allocator, isa, features);
}


// Generate the JNI bridge for the given method, general contract:
// - Arguments are in the managed runtime format, either on stack or in
//   registers, a reference to the method object is supplied as part of this
//   convention.
//
template <PointerSize kPointerSize>
static JniCompiledMethod ArtJniCompileMethodInternal(const CompilerOptions& compiler_options,
                                                     uint32_t access_flags,
                                                     uint32_t method_idx,
                                                     const DexFile& dex_file,
                                                     ArenaAllocator* allocator) {
  constexpr size_t kRawPointerSize = static_cast<size_t>(kPointerSize);
  const bool is_native = (access_flags & kAccNative) != 0;
  CHECK(is_native);
  const bool is_static = (access_flags & kAccStatic) != 0;
  const bool is_synchronized = (access_flags & kAccSynchronized) != 0;
  const char* shorty = dex_file.GetMethodShorty(dex_file.GetMethodId(method_idx));
  InstructionSet instruction_set = compiler_options.GetInstructionSet();
  const InstructionSetFeatures* instruction_set_features =
      compiler_options.GetInstructionSetFeatures();

  // i.e. if the method was annotated with @FastNative
  const bool is_fast_native = (access_flags & kAccFastNative) != 0u;

  // i.e. if the method was annotated with @CriticalNative
  const bool is_critical_native = (access_flags & kAccCriticalNative) != 0u;

  bool emit_read_barrier = compiler_options.EmitReadBarrier();
  bool is_debuggable = compiler_options.GetDebuggable();
  bool needs_entry_exit_hooks = is_debuggable && compiler_options.IsJitCompiler();
  // We don't support JITing stubs for critical native methods in debuggable runtimes yet.
  // TODO(mythria): Add support required for calling method entry / exit hooks from critical native
  // methods.
  DCHECK_IMPLIES(needs_entry_exit_hooks, !is_critical_native);

  // The fast-path for decoding a reference skips CheckJNI checks, so we do not inline the
  // decoding in debug build or for debuggable apps (both cases enable CheckJNI by default).
  bool inline_decode_reference = !kIsDebugBuild && !is_debuggable;

  // When  walking the stack the top frame doesn't have a pc associated with it. We then depend on
  // the invariant that we don't have JITed code when AOT code is available. In debuggable runtimes
  // this invariant doesn't hold. So we tag the SP for JITed code to indentify if we are executing
  // JITed code or AOT code. Since tagging involves additional instructions we tag only in
  // debuggable runtimes.
  bool should_tag_sp = needs_entry_exit_hooks;

  VLOG(jni) << "JniCompile: Method :: "
              << dex_file.PrettyMethod(method_idx, /* with signature */ true)
              << " :: access_flags = " << std::hex << access_flags << std::dec;

  if (UNLIKELY(is_fast_native)) {
    VLOG(jni) << "JniCompile: Fast native method detected :: "
              << dex_file.PrettyMethod(method_idx, /* with signature */ true);
  }

  if (UNLIKELY(is_critical_native)) {
    VLOG(jni) << "JniCompile: Critical native method detected :: "
              << dex_file.PrettyMethod(method_idx, /* with signature */ true);
  }

  if (kIsDebugBuild) {
    // Don't allow both @FastNative and @CriticalNative. They are mutually exclusive.
    if (UNLIKELY(is_fast_native && is_critical_native)) {
      LOG(FATAL) << "JniCompile: Method cannot be both @CriticalNative and @FastNative"
                 << dex_file.PrettyMethod(method_idx, /* with_signature= */ true);
    }

    // @CriticalNative - extra checks:
    // -- Don't allow virtual criticals
    // -- Don't allow synchronized criticals
    // -- Don't allow any objects as parameter or return value
    if (UNLIKELY(is_critical_native)) {
      CHECK(is_static)
          << "@CriticalNative functions cannot be virtual since that would "
          << "require passing a reference parameter (this), which is illegal "
          << dex_file.PrettyMethod(method_idx, /* with_signature= */ true);
      CHECK(!is_synchronized)
          << "@CriticalNative functions cannot be synchronized since that would "
          << "require passing a (class and/or this) reference parameter, which is illegal "
          << dex_file.PrettyMethod(method_idx, /* with_signature= */ true);
      for (size_t i = 0; i < strlen(shorty); ++i) {
        CHECK_NE(Primitive::kPrimNot, Primitive::GetType(shorty[i]))
            << "@CriticalNative methods' shorty types must not have illegal references "
            << dex_file.PrettyMethod(method_idx, /* with_signature= */ true);
      }
    }
  }

  // Calling conventions used to iterate over parameters to method
  std::unique_ptr<JniCallingConvention> main_jni_conv =
      JniCallingConvention::Create(allocator,
                                   is_static,
                                   is_synchronized,
                                   is_fast_native,
                                   is_critical_native,
                                   shorty,
                                   instruction_set);
  bool reference_return = main_jni_conv->IsReturnAReference();

  std::unique_ptr<ManagedRuntimeCallingConvention> mr_conv(
      ManagedRuntimeCallingConvention::Create(
          allocator, is_static, is_synchronized, shorty, instruction_set));

  // Assembler that holds generated instructions
  std::unique_ptr<JNIMacroAssembler<kPointerSize>> jni_asm =
      GetMacroAssembler<kPointerSize>(allocator, instruction_set, instruction_set_features);
  jni_asm->cfi().SetEnabled(compiler_options.GenerateAnyDebugInfo());
  jni_asm->SetEmitRunTimeChecksInDebugMode(compiler_options.EmitRunTimeChecksInDebugMode());

  // 1. Build and register the native method frame.

  // 1.1. Build the frame saving all callee saves, Method*, and PC return address.
  //      For @CriticalNative, this includes space for out args, otherwise just the managed frame.
  const size_t managed_frame_size = main_jni_conv->FrameSize();
  const size_t main_out_arg_size = main_jni_conv->OutFrameSize();
  size_t current_frame_size = is_critical_native ? main_out_arg_size : managed_frame_size;
  ManagedRegister method_register =
      is_critical_native ? ManagedRegister::NoRegister() : mr_conv->MethodRegister();
  ArrayRef<const ManagedRegister> callee_save_regs = main_jni_conv->CalleeSaveRegisters();
  __ BuildFrame(current_frame_size, method_register, callee_save_regs);
  DCHECK_EQ(jni_asm->cfi().GetCurrentCFAOffset(), static_cast<int>(current_frame_size));

  // 1.2. Check if we need to go to the slow path to emit the read barrier
  //      for the declaring class in the method for a static call.
  //      Skip this for @CriticalNative because we're not passing a `jclass` to the native method.
  std::unique_ptr<JNIMacroLabel> jclass_read_barrier_slow_path;
  std::unique_ptr<JNIMacroLabel> jclass_read_barrier_return;
  if (emit_read_barrier && is_static && LIKELY(!is_critical_native)) {
    jclass_read_barrier_slow_path = __ CreateLabel();
    jclass_read_barrier_return = __ CreateLabel();

    // Check if gc_is_marking is set -- if it's not, we don't need a read barrier.
    __ TestGcMarking(jclass_read_barrier_slow_path.get(), JNIMacroUnaryCondition::kNotZero);

    // If marking, the slow path returns after the check.
    __ Bind(jclass_read_barrier_return.get());
  }

  // 1.3 Spill reference register arguments.
  constexpr FrameOffset kInvalidReferenceOffset =
      JNIMacroAssembler<kPointerSize>::kInvalidReferenceOffset;
  ArenaVector<ArgumentLocation> src_args(allocator->Adapter());
  ArenaVector<ArgumentLocation> dest_args(allocator->Adapter());
  ArenaVector<FrameOffset> refs(allocator->Adapter());
  if (LIKELY(!is_critical_native)) {
    mr_conv->ResetIterator(FrameOffset(current_frame_size));
    for (; mr_conv->HasNext(); mr_conv->Next()) {
      if (mr_conv->IsCurrentParamInRegister() && mr_conv->IsCurrentParamAReference()) {
        // Spill the reference as raw data.
        src_args.emplace_back(mr_conv->CurrentParamRegister(), kObjectReferenceSize);
        dest_args.emplace_back(mr_conv->CurrentParamStackOffset(), kObjectReferenceSize);
        refs.push_back(kInvalidReferenceOffset);
      }
    }
    __ MoveArguments(ArrayRef<ArgumentLocation>(dest_args),
                     ArrayRef<ArgumentLocation>(src_args),
                     ArrayRef<FrameOffset>(refs));
  }

  // 1.4. Write out the end of the quick frames. After this, we can walk the stack.
  // NOTE: @CriticalNative does not need to store the stack pointer to the thread
  //       because garbage collections are disabled within the execution of a
  //       @CriticalNative method.
  if (LIKELY(!is_critical_native)) {
    __ StoreStackPointerToThread(Thread::TopOfManagedStackOffset<kPointerSize>(), should_tag_sp);
  }

  // 1.5. Call any method entry hooks if required.
  // For critical native methods, we don't JIT stubs in debuggable runtimes (see
  // OptimizingCompiler::JitCompile).
  // TODO(mythria): Add support to call method entry / exit hooks for critical native methods too.
  std::unique_ptr<JNIMacroLabel> method_entry_hook_slow_path;
  std::unique_ptr<JNIMacroLabel> method_entry_hook_return;
  if (UNLIKELY(needs_entry_exit_hooks)) {
    uint64_t address = reinterpret_cast64<uint64_t>(Runtime::Current()->GetInstrumentation());
    int offset = instrumentation::Instrumentation::HaveMethodEntryListenersOffset().Int32Value();
    method_entry_hook_slow_path = __ CreateLabel();
    method_entry_hook_return = __ CreateLabel();
    __ TestByteAndJumpIfNotZero(address + offset, method_entry_hook_slow_path.get());
    __ Bind(method_entry_hook_return.get());
  }

  // 2. Lock the object (if synchronized) and transition out of Runnable (if normal native).

  // 2.1. Lock the synchronization object (`this` or class) for synchronized methods.
  if (UNLIKELY(is_synchronized)) {
    // We are using a custom calling convention for locking where the assembly thunk gets
    // the object to lock in a register (even on x86), it can use callee-save registers
    // as temporaries (they were saved above) and must preserve argument registers.
    ManagedRegister to_lock = main_jni_conv->LockingArgumentRegister();
    if (is_static) {
      // Pass the declaring class. It was already marked if needed.
      DCHECK_EQ(ArtMethod::DeclaringClassOffset().SizeValue(), 0u);
      __ Load(to_lock, method_register, MemberOffset(0u), kObjectReferenceSize);
    } else {
      // Pass the `this` argument.
      mr_conv->ResetIterator(FrameOffset(current_frame_size));
      if (mr_conv->IsCurrentParamInRegister()) {
        __ Move(to_lock, mr_conv->CurrentParamRegister(), kObjectReferenceSize);
      } else {
        __ Load(to_lock, mr_conv->CurrentParamStackOffset(), kObjectReferenceSize);
      }
    }
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniLockObject));
  }

  // 2.2. Transition from Runnable to Suspended.
  // Managed callee-saves were already saved, so these registers are now available.
  ArrayRef<const ManagedRegister> callee_save_scratch_regs = UNLIKELY(is_critical_native)
      ? ArrayRef<const ManagedRegister>()
      : main_jni_conv->CalleeSaveScratchRegisters();
  std::unique_ptr<JNIMacroLabel> transition_to_native_slow_path;
  std::unique_ptr<JNIMacroLabel> transition_to_native_resume;
  if (LIKELY(!is_critical_native && !is_fast_native)) {
    transition_to_native_slow_path = __ CreateLabel();
    transition_to_native_resume = __ CreateLabel();
    __ TryToTransitionFromRunnableToNative(transition_to_native_slow_path.get(),
                                           callee_save_scratch_regs);
    __ Bind(transition_to_native_resume.get());
  }

  // 3. Push local reference frame.
  // Skip this for @CriticalNative methods, they cannot use any references.
  ManagedRegister jni_env_reg = ManagedRegister::NoRegister();
  ManagedRegister previous_state_reg = ManagedRegister::NoRegister();
  ManagedRegister current_state_reg = ManagedRegister::NoRegister();
  ManagedRegister callee_save_temp = ManagedRegister::NoRegister();
  if (LIKELY(!is_critical_native)) {
    // To pop the local reference frame later, we shall need the JNI environment pointer
    // as well as the cookie, so we preserve them across calls in callee-save registers.
    CHECK_GE(callee_save_scratch_regs.size(), 3u);  // At least 3 for each supported architecture.
    jni_env_reg = callee_save_scratch_regs[0];
    constexpr size_t kLRTSegmentStateSize = sizeof(jni::LRTSegmentState);
    previous_state_reg = __ CoreRegisterWithSize(callee_save_scratch_regs[1], kLRTSegmentStateSize);
    current_state_reg = __ CoreRegisterWithSize(callee_save_scratch_regs[2], kLRTSegmentStateSize);
    if (callee_save_scratch_regs.size() >= 4) {
      callee_save_temp = callee_save_scratch_regs[3];
    }
    const MemberOffset previous_state_offset = JNIEnvExt::LrtPreviousStateOffset(kPointerSize);

    // Load the JNI environment pointer.
    __ LoadRawPtrFromThread(jni_env_reg, Thread::JniEnvOffset<kPointerSize>());

    // Load the local reference frame states.
    __ LoadLocalReferenceTableStates(jni_env_reg, previous_state_reg, current_state_reg);

    // Store the current state as the previous state (push the LRT frame).
    __ Store(jni_env_reg, previous_state_offset, current_state_reg, kLRTSegmentStateSize);
  }

  // 4. Make the main native call.

  // 4.1. Move frame down to allow space for out going args.
  size_t current_out_arg_size = main_out_arg_size;
  if (UNLIKELY(is_critical_native)) {
    DCHECK_EQ(main_out_arg_size, current_frame_size);
  } else {
    __ IncreaseFrameSize(main_out_arg_size);
    current_frame_size += main_out_arg_size;
  }

  // 4.2. Fill arguments except the `JNIEnv*`.
  // Note: Non-null reference arguments in registers may point to the from-space if we
  // took the slow-path for locking or transition to Native. However, we only need to
  // compare them with null to construct `jobject`s, so we can still use them.
  src_args.clear();
  dest_args.clear();
  refs.clear();
  mr_conv->ResetIterator(FrameOffset(current_frame_size));
  main_jni_conv->ResetIterator(FrameOffset(main_out_arg_size));
  bool check_method_not_clobbered = false;
  if (UNLIKELY(is_critical_native)) {
    // Move the method pointer to the hidden argument register.
    // TODO: Pass this as the last argument, not first. Change ARM assembler
    // not to expect all register destinations at the beginning.
    src_args.emplace_back(mr_conv->MethodRegister(), kRawPointerSize);
    dest_args.emplace_back(main_jni_conv->HiddenArgumentRegister(), kRawPointerSize);
    refs.push_back(kInvalidReferenceOffset);
  } else {
    main_jni_conv->Next();    // Skip JNIEnv*.
    FrameOffset method_offset(current_out_arg_size + mr_conv->MethodStackOffset().SizeValue());
    if (main_jni_conv->IsCurrentParamOnStack()) {
      // This is for x86 only. The method shall not be clobbered by argument moves
      // because all arguments are passed on the stack to the native method.
      check_method_not_clobbered = true;
      DCHECK(callee_save_temp.IsNoRegister());
    } else if (!is_static) {
      // The method shall not be available in the `jclass` argument register.
      // Make sure it is available in `callee_save_temp` for the call below.
      // (The old method register can be clobbered by argument moves.)
      DCHECK(!callee_save_temp.IsNoRegister());
      ManagedRegister new_method_reg = __ CoreRegisterWithSize(callee_save_temp, kRawPointerSize);
      DCHECK(!method_register.IsNoRegister());
      __ Move(new_method_reg, method_register, kRawPointerSize);
      method_register = new_method_reg;
    }
    if (is_static) {
      // For static methods, move/load the method to the `jclass` argument.
      DCHECK_EQ(ArtMethod::DeclaringClassOffset().SizeValue(), 0u);
      if (method_register.IsNoRegister()) {
        DCHECK(main_jni_conv->IsCurrentParamInRegister());
        src_args.emplace_back(method_offset, kRawPointerSize);
      } else {
        src_args.emplace_back(method_register, kRawPointerSize);
      }
      if (main_jni_conv->IsCurrentParamInRegister()) {
        // The `jclass` argument becomes the new method register needed for the call.
        method_register = main_jni_conv->CurrentParamRegister();
        dest_args.emplace_back(method_register, kRawPointerSize);
      } else {
        dest_args.emplace_back(main_jni_conv->CurrentParamStackOffset(), kRawPointerSize);
      }
      refs.push_back(kInvalidReferenceOffset);
      main_jni_conv->Next();
    }
  }
  // Move normal arguments to their locations.
  for (; mr_conv->HasNext(); mr_conv->Next(), main_jni_conv->Next()) {
    DCHECK(main_jni_conv->HasNext());
    static_assert(kObjectReferenceSize == 4u);
    bool is_reference = mr_conv->IsCurrentParamAReference();
    size_t src_size = mr_conv->CurrentParamSize();
    size_t dest_size = main_jni_conv->CurrentParamSize();
    src_args.push_back(mr_conv->IsCurrentParamInRegister()
        ? ArgumentLocation(mr_conv->CurrentParamRegister(), src_size)
        : ArgumentLocation(mr_conv->CurrentParamStackOffset(), src_size));
    dest_args.push_back(main_jni_conv->IsCurrentParamInRegister()
        ? ArgumentLocation(main_jni_conv->CurrentParamRegister(), dest_size)
        : ArgumentLocation(main_jni_conv->CurrentParamStackOffset(), dest_size));
    refs.push_back(is_reference ? mr_conv->CurrentParamStackOffset() : kInvalidReferenceOffset);
  }
  DCHECK(!main_jni_conv->HasNext());
  DCHECK_IMPLIES(check_method_not_clobbered,
                 std::all_of(dest_args.begin(),
                             dest_args.end(),
                             [](const ArgumentLocation& loc) { return !loc.IsRegister(); }));
  __ MoveArguments(ArrayRef<ArgumentLocation>(dest_args),
                   ArrayRef<ArgumentLocation>(src_args),
                   ArrayRef<FrameOffset>(refs));

  // 4.3. Create 1st argument, the JNI environment ptr.
  if (LIKELY(!is_critical_native)) {
    main_jni_conv->ResetIterator(FrameOffset(main_out_arg_size));
    if (main_jni_conv->IsCurrentParamInRegister()) {
      ManagedRegister jni_env_arg = main_jni_conv->CurrentParamRegister();
      __ Move(jni_env_arg, jni_env_reg, kRawPointerSize);
    } else {
      FrameOffset jni_env_arg_offset = main_jni_conv->CurrentParamStackOffset();
      __ Store(jni_env_arg_offset, jni_env_reg, kRawPointerSize);
    }
  }

  // 4.4. Plant call to native code associated with method.
  MemberOffset jni_entrypoint_offset =
      ArtMethod::EntryPointFromJniOffset(InstructionSetPointerSize(instruction_set));
  if (UNLIKELY(is_critical_native)) {
    if (main_jni_conv->UseTailCall()) {
      __ Jump(main_jni_conv->HiddenArgumentRegister(), jni_entrypoint_offset);
    } else {
      __ Call(main_jni_conv->HiddenArgumentRegister(), jni_entrypoint_offset);
    }
  } else {
    DCHECK(method_register.IsRegister());
    __ Call(method_register, jni_entrypoint_offset);
    // We shall not need the method register anymore. And we may clobber it below
    // if it's the `callee_save_temp`, so clear it here to make sure it's not used.
    method_register = ManagedRegister::NoRegister();
  }

  // 4.5. Fix differences in result widths.
  if (main_jni_conv->RequiresSmallResultTypeExtension()) {
    DCHECK(main_jni_conv->HasSmallReturnType());
    CHECK_IMPLIES(is_critical_native, !main_jni_conv->UseTailCall());
    if (main_jni_conv->GetReturnType() == Primitive::kPrimByte ||
        main_jni_conv->GetReturnType() == Primitive::kPrimShort) {
      __ SignExtend(main_jni_conv->ReturnRegister(),
                    Primitive::ComponentSize(main_jni_conv->GetReturnType()));
    } else {
      CHECK(main_jni_conv->GetReturnType() == Primitive::kPrimBoolean ||
            main_jni_conv->GetReturnType() == Primitive::kPrimChar);
      __ ZeroExtend(main_jni_conv->ReturnRegister(),
                    Primitive::ComponentSize(main_jni_conv->GetReturnType()));
    }
  }

  // 4.6. Move the JNI return register into the managed return register (if they don't match).
  if (main_jni_conv->SizeOfReturnValue() != 0) {
    ManagedRegister jni_return_reg = main_jni_conv->ReturnRegister();
    ManagedRegister mr_return_reg = mr_conv->ReturnRegister();

    // Check if the JNI return register matches the managed return register.
    // If they differ, only then do we have to do anything about it.
    // Otherwise the return value is already in the right place when we return.
    if (!jni_return_reg.Equals(mr_return_reg)) {
      CHECK_IMPLIES(is_critical_native, !main_jni_conv->UseTailCall());
      // This is typically only necessary on ARM32 due to native being softfloat
      // while managed is hardfloat.
      // -- For example VMOV {r0, r1} -> D0; VMOV r0 -> S0.
      __ Move(mr_return_reg, jni_return_reg, main_jni_conv->SizeOfReturnValue());
    } else if (jni_return_reg.IsNoRegister() && mr_return_reg.IsNoRegister()) {
      // Check that if the return value is passed on the stack for some reason,
      // that the size matches.
      CHECK_EQ(main_jni_conv->SizeOfReturnValue(), mr_conv->SizeOfReturnValue());
    }
  }

  // 5. Transition to Runnable (if normal native).

  // 5.1. Try transitioning to Runnable with a fast-path implementation.
  //      If fast-path fails, make a slow-path call to `JniMethodEnd()`.
  std::unique_ptr<JNIMacroLabel> transition_to_runnable_slow_path;
  std::unique_ptr<JNIMacroLabel> transition_to_runnable_resume;
  if (LIKELY(!is_critical_native && !is_fast_native)) {
    transition_to_runnable_slow_path = __ CreateLabel();
    transition_to_runnable_resume = __ CreateLabel();
    __ TryToTransitionFromNativeToRunnable(transition_to_runnable_slow_path.get(),
                                           main_jni_conv->ArgumentScratchRegisters(),
                                           mr_conv->ReturnRegister());
    __ Bind(transition_to_runnable_resume.get());
  }

  // 5.2. For methods that return a reference, do an exception check before decoding the reference.
  std::unique_ptr<JNIMacroLabel> exception_slow_path =
      LIKELY(!is_critical_native) ? __ CreateLabel() : nullptr;
  if (reference_return) {
    DCHECK(!is_critical_native);
    __ ExceptionPoll(exception_slow_path.get());
  }

  // 5.3. For @FastNative that returns a reference, do an early suspend check so that we
  //      do not need to encode the decoded reference in a stack map.
  std::unique_ptr<JNIMacroLabel> suspend_check_slow_path =
      UNLIKELY(is_fast_native) ? __ CreateLabel() : nullptr;
  std::unique_ptr<JNIMacroLabel> suspend_check_resume =
      UNLIKELY(is_fast_native) ? __ CreateLabel() : nullptr;
  if (UNLIKELY(is_fast_native) && reference_return) {
    __ SuspendCheck(suspend_check_slow_path.get());
    __ Bind(suspend_check_resume.get());
  }

  // 5.4 For methods with reference return, decode the `jobject`, either directly
  //     or with a call to `JniDecodeReferenceResult()`.
  std::unique_ptr<JNIMacroLabel> decode_reference_slow_path;
  std::unique_ptr<JNIMacroLabel> decode_reference_resume;
  if (reference_return) {
    DCHECK(!is_critical_native);
    if (inline_decode_reference) {
      // Decode local and JNI transition references in the main path.
      decode_reference_slow_path = __ CreateLabel();
      decode_reference_resume = __ CreateLabel();
      __ DecodeJNITransitionOrLocalJObject(mr_conv->ReturnRegister(),
                                           decode_reference_slow_path.get(),
                                           decode_reference_resume.get());
      __ Bind(decode_reference_resume.get());
    } else {
      CallDecodeReferenceResult<kPointerSize>(
          jni_asm.get(), main_jni_conv.get(), mr_conv->ReturnRegister(), main_out_arg_size);
    }
  }  // if (!is_critical_native)

  // 6. Pop local reference frame.
  if (LIKELY(!is_critical_native)) {
    __ StoreLocalReferenceTableStates(jni_env_reg, previous_state_reg, current_state_reg);
    // For x86, the `callee_save_temp` is not valid, so let's simply change it to one
    // of the callee save registers that we don't need anymore for all architectures.
    callee_save_temp = current_state_reg;
  }

  // 7. Return from the JNI stub.

  // 7.1. Move frame up now we're done with the out arg space.
  //      @CriticalNative remove out args together with the frame in RemoveFrame().
  if (LIKELY(!is_critical_native)) {
    __ DecreaseFrameSize(current_out_arg_size);
    current_frame_size -= current_out_arg_size;
  }

  // 7.2 Unlock the synchronization object for synchronized methods.
  //     Do this before exception poll to avoid extra unlocking in the exception slow path.
  if (UNLIKELY(is_synchronized)) {
    ManagedRegister to_lock = main_jni_conv->LockingArgumentRegister();
    mr_conv->ResetIterator(FrameOffset(current_frame_size));
    if (is_static) {
      // Pass the declaring class.
      DCHECK(method_register.IsNoRegister());  // TODO: Preserve the method in `callee_save_temp`.
      ManagedRegister temp = __ CoreRegisterWithSize(callee_save_temp, kRawPointerSize);
      FrameOffset method_offset = mr_conv->MethodStackOffset();
      __ Load(temp, method_offset, kRawPointerSize);
      DCHECK_EQ(ArtMethod::DeclaringClassOffset().SizeValue(), 0u);
      __ LoadGcRootWithoutReadBarrier(to_lock, temp, MemberOffset(0u));
    } else {
      // Pass the `this` argument from its spill slot.
      __ LoadStackReference(to_lock, mr_conv->CurrentParamStackOffset());
    }
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniUnlockObject));
  }

  // 7.3. Process pending exceptions from JNI call or monitor exit.
  //      @CriticalNative methods do not need exception poll in the stub.
  //      Methods with reference return emit the exception poll earlier.
  if (LIKELY(!is_critical_native) && !reference_return) {
    __ ExceptionPoll(exception_slow_path.get());
  }

  // 7.4. For @FastNative, we never transitioned out of runnable, so there is no transition back.
  //      Perform a suspend check if there is a flag raised, unless we have done that above
  //      for reference return.
  if (UNLIKELY(is_fast_native) && !reference_return) {
    __ SuspendCheck(suspend_check_slow_path.get());
    __ Bind(suspend_check_resume.get());
  }

  // 7.5. Check if method exit hooks needs to be called
  // For critical native methods, we don't JIT stubs in debuggable runtimes.
  // TODO(mythria): Add support to call method entry / exit hooks for critical native methods too.
  std::unique_ptr<JNIMacroLabel> method_exit_hook_slow_path;
  std::unique_ptr<JNIMacroLabel> method_exit_hook_return;
  if (UNLIKELY(needs_entry_exit_hooks)) {
    uint64_t address = reinterpret_cast64<uint64_t>(Runtime::Current()->GetInstrumentation());
    int offset = instrumentation::Instrumentation::RunExitHooksOffset().Int32Value();
    method_exit_hook_slow_path = __ CreateLabel();
    method_exit_hook_return = __ CreateLabel();
    __ TestByteAndJumpIfNotZero(address + offset, method_exit_hook_slow_path.get());
    __ Bind(method_exit_hook_return.get());
  }

  // 7.6. Remove activation - need to restore callee save registers since the GC
  //      may have changed them.
  DCHECK_EQ(jni_asm->cfi().GetCurrentCFAOffset(), static_cast<int>(current_frame_size));
  if (LIKELY(!is_critical_native) || !main_jni_conv->UseTailCall()) {
    // We expect the compiled method to possibly be suspended during its
    // execution, except in the case of a CriticalNative method.
    bool may_suspend = !is_critical_native;
    __ RemoveFrame(current_frame_size, callee_save_regs, may_suspend);
    DCHECK_EQ(jni_asm->cfi().GetCurrentCFAOffset(), static_cast<int>(current_frame_size));
  }

  // 8. Emit slow paths.

  // 8.1. Read barrier slow path for the declaring class in the method for a static call.
  //      Skip this for @CriticalNative because we're not passing a `jclass` to the native method.
  if (emit_read_barrier && is_static && !is_critical_native) {
    __ Bind(jclass_read_barrier_slow_path.get());

    // Construct slow path for read barrier:
    //
    // For baker read barrier, do a fast check whether the class is already marked.
    //
    // Call into the runtime's `art_jni_read_barrier` and have it fix up
    // the class address if it was moved.
    //
    // The entrypoint preserves the method register and argument registers.

    if (kUseBakerReadBarrier) {
      // We enter the slow path with the method register unclobbered and callee-save
      // registers already spilled, so we can use callee-save scratch registers.
      method_register = mr_conv->MethodRegister();
      ManagedRegister temp = __ CoreRegisterWithSize(
          main_jni_conv->CalleeSaveScratchRegisters()[0], kObjectReferenceSize);
      // Load the declaring class reference.
      DCHECK_EQ(ArtMethod::DeclaringClassOffset().SizeValue(), 0u);
      __ LoadGcRootWithoutReadBarrier(temp, method_register, MemberOffset(0u));
      // Return to main path if the class object is marked.
      __ TestMarkBit(temp, jclass_read_barrier_return.get(), JNIMacroUnaryCondition::kNotZero);
    }

    ThreadOffset<kPointerSize> read_barrier = QUICK_ENTRYPOINT_OFFSET(kPointerSize,
                                                                      pJniReadBarrier);
    __ CallFromThread(read_barrier);

    // Return to main path.
    __ Jump(jclass_read_barrier_return.get());
  }

  // 8.2. Slow path for transition to Native.
  if (LIKELY(!is_critical_native && !is_fast_native)) {
    __ Bind(transition_to_native_slow_path.get());
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniMethodStart));
    __ Jump(transition_to_native_resume.get());
  }

  // 8.3. Slow path for transition to Runnable.
  if (LIKELY(!is_critical_native && !is_fast_native)) {
    __ Bind(transition_to_runnable_slow_path.get());
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniMethodEnd));
    __ Jump(transition_to_runnable_resume.get());
  }

  // 8.4. Exception poll slow path(s).
  if (LIKELY(!is_critical_native)) {
    __ Bind(exception_slow_path.get());
    if (reference_return) {
      // We performed the exception check early, so we need to adjust SP and pop IRT frame.
      if (main_out_arg_size != 0) {
        jni_asm->cfi().AdjustCFAOffset(main_out_arg_size);
        __ DecreaseFrameSize(main_out_arg_size);
      }
      __ StoreLocalReferenceTableStates(jni_env_reg, previous_state_reg, current_state_reg);
    }
    DCHECK_EQ(jni_asm->cfi().GetCurrentCFAOffset(), static_cast<int>(current_frame_size));
    __ DeliverPendingException();
  }

  // 8.5 Slow path for decoding the `jobject`.
  if (reference_return && inline_decode_reference) {
    __ Bind(decode_reference_slow_path.get());
    if (main_out_arg_size != 0) {
      jni_asm->cfi().AdjustCFAOffset(main_out_arg_size);
    }
    CallDecodeReferenceResult<kPointerSize>(
        jni_asm.get(), main_jni_conv.get(), mr_conv->ReturnRegister(), main_out_arg_size);
    __ Jump(decode_reference_resume.get());
    if (main_out_arg_size != 0) {
      jni_asm->cfi().AdjustCFAOffset(-main_out_arg_size);
    }
  }

  // 8.6. Suspend check slow path.
  if (UNLIKELY(is_fast_native)) {
    __ Bind(suspend_check_slow_path.get());
    if (reference_return && main_out_arg_size != 0) {
      jni_asm->cfi().AdjustCFAOffset(main_out_arg_size);
      __ DecreaseFrameSize(main_out_arg_size);
    }
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pTestSuspend));
    if (reference_return) {
      // Suspend check entry point overwrites top of managed stack and leaves it clobbered.
      // We need to restore the top for subsequent runtime call to `JniDecodeReferenceResult()`.
      __ StoreStackPointerToThread(Thread::TopOfManagedStackOffset<kPointerSize>(), should_tag_sp);
    }
    if (reference_return && main_out_arg_size != 0) {
      __ IncreaseFrameSize(main_out_arg_size);
    }
    __ Jump(suspend_check_resume.get());
    if (reference_return && main_out_arg_size != 0) {
      jni_asm->cfi().AdjustCFAOffset(-main_out_arg_size);
    }
  }

  // 8.7. Method entry / exit hooks slow paths.
  if (UNLIKELY(needs_entry_exit_hooks)) {
    __ Bind(method_entry_hook_slow_path.get());
    // Use Jni specific method entry hook that saves all the arguments. We have only saved the
    // callee save registers at this point. So go through Jni specific stub that saves the rest
    // of the live registers.
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniMethodEntryHook));
    __ ExceptionPoll(exception_slow_path.get());
    __ Jump(method_entry_hook_return.get());

    __ Bind(method_exit_hook_slow_path.get());
    // Method exit hooks is called just before tearing down the frame. So there are no live
    // registers and we can directly call the method exit hook and don't need a Jni specific
    // entrypoint.
    __ Move(mr_conv->ArgumentRegisterForMethodExitHook(), managed_frame_size);
    __ CallFromThread(QUICK_ENTRYPOINT_OFFSET(kPointerSize, pMethodExitHook));
    __ Jump(method_exit_hook_return.get());
  }

  // 9. Finalize code generation.
  __ FinalizeCode();
  size_t cs = __ CodeSize();
  std::vector<uint8_t> managed_code(cs);
  MemoryRegion code(&managed_code[0], managed_code.size());
  __ CopyInstructions(code);

  return JniCompiledMethod(instruction_set,
                           std::move(managed_code),
                           managed_frame_size,
                           main_jni_conv->CoreSpillMask(),
                           main_jni_conv->FpSpillMask(),
                           ArrayRef<const uint8_t>(*jni_asm->cfi().data()));
}

template <PointerSize kPointerSize>
static void SetNativeParameter(JNIMacroAssembler<kPointerSize>* jni_asm,
                               JniCallingConvention* jni_conv,
                               ManagedRegister in_reg) {
  if (jni_conv->IsCurrentParamOnStack()) {
    FrameOffset dest = jni_conv->CurrentParamStackOffset();
    __ StoreRawPtr(dest, in_reg);
  } else {
    if (!jni_conv->CurrentParamRegister().Equals(in_reg)) {
      __ Move(jni_conv->CurrentParamRegister(), in_reg, jni_conv->CurrentParamSize());
    }
  }
}

template <PointerSize kPointerSize>
static void CallDecodeReferenceResult(JNIMacroAssembler<kPointerSize>* jni_asm,
                                      JniCallingConvention* jni_conv,
                                      ManagedRegister mr_return_reg,
                                      size_t main_out_arg_size) {
  // We abuse the JNI calling convention here, that is guaranteed to support passing
  // two pointer arguments, `JNIEnv*` and `jclass`/`jobject`.
  jni_conv->ResetIterator(FrameOffset(main_out_arg_size));
  ThreadOffset<kPointerSize> jni_decode_reference_result =
      QUICK_ENTRYPOINT_OFFSET(kPointerSize, pJniDecodeReferenceResult);
  // Pass result.
  SetNativeParameter(jni_asm, jni_conv, mr_return_reg);
  jni_conv->Next();
  if (jni_conv->IsCurrentParamInRegister()) {
    __ GetCurrentThread(jni_conv->CurrentParamRegister());
    __ Call(jni_conv->CurrentParamRegister(), Offset(jni_decode_reference_result));
  } else {
    __ GetCurrentThread(jni_conv->CurrentParamStackOffset());
    __ CallFromThread(jni_decode_reference_result);
  }
  // Note: If the native ABI returns the pointer in a register different from
  // `mr_return_register`, the `JniDecodeReferenceResult` entrypoint must be
  // a stub that moves the result to `mr_return_register`.
}

JniCompiledMethod ArtQuickJniCompileMethod(const CompilerOptions& compiler_options,
                                           uint32_t access_flags,
                                           uint32_t method_idx,
                                           const DexFile& dex_file,
                                           ArenaAllocator* allocator) {
  if (Is64BitInstructionSet(compiler_options.GetInstructionSet())) {
    return ArtJniCompileMethodInternal<PointerSize::k64>(
        compiler_options, access_flags, method_idx, dex_file, allocator);
  } else {
    return ArtJniCompileMethodInternal<PointerSize::k32>(
        compiler_options, access_flags, method_idx, dex_file, allocator);
  }
}

}  // namespace art
