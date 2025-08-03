/*
 * Copyright (C) 2019 The Android Open Source Project
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

/*
 * Mterp entry point and support functions.
 */
#include "nterp.h"

#include "arch/instruction_set.h"
#include "base/quasi_atomic.h"
#include "class_linker-inl.h"
#include "dex/dex_instruction_utils.h"
#include "debugger.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "interpreter/interpreter_cache-inl.h"
#include "interpreter/interpreter_common.h"
#include "interpreter/shadow_frame-inl.h"
#include "mirror/string-alloc-inl.h"
#include "nterp_helpers.h"

namespace art HIDDEN {
namespace interpreter {

bool IsNterpSupported() {
  switch (kRuntimeISA) {
    case InstructionSet::kArm:
    case InstructionSet::kThumb2:
    case InstructionSet::kArm64:
      return kReserveMarkingRegister && !kUseTableLookupReadBarrier;
    case InstructionSet::kRiscv64:
      return true;
    case InstructionSet::kX86:
    case InstructionSet::kX86_64:
      return !kUseTableLookupReadBarrier;
    default:
      return false;
  }
}

bool CanRuntimeUseNterp() REQUIRES_SHARED(Locks::mutator_lock_) {
  Runtime* runtime = Runtime::Current();
  instrumentation::Instrumentation* instr = runtime->GetInstrumentation();
  // If the runtime is interpreter only, we currently don't use nterp as some
  // parts of the runtime (like instrumentation) make assumption on an
  // interpreter-only runtime to always be in a switch-like interpreter.
  return IsNterpSupported() && !runtime->IsJavaDebuggable() && !instr->EntryExitStubsInstalled() &&
         !instr->InterpretOnly() && !runtime->IsAotCompiler() &&
         !instr->NeedsSlowInterpreterForListeners() &&
         // An async exception has been thrown. We need to go to the switch interpreter. nterp
         // doesn't know how to deal with these so we could end up never dealing with it if we are
         // in an infinite loop.
         !runtime->AreAsyncExceptionsThrown() &&
         (runtime->GetJit() == nullptr || !runtime->GetJit()->JitAtFirstUse());
}

// The entrypoint for nterp, which ArtMethods can directly point to.
extern "C" void ExecuteNterpImpl() REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void EndExecuteNterpImpl() REQUIRES_SHARED(Locks::mutator_lock_);

const void* GetNterpEntryPoint() {
  return reinterpret_cast<const void*>(interpreter::ExecuteNterpImpl);
}

ArrayRef<const uint8_t> NterpImpl() {
  const uint8_t* entry_point = reinterpret_cast<const uint8_t*>(ExecuteNterpImpl);
  size_t size = reinterpret_cast<const uint8_t*>(EndExecuteNterpImpl) - entry_point;
  const uint8_t* code = reinterpret_cast<const uint8_t*>(EntryPointToCodePointer(entry_point));
  return ArrayRef<const uint8_t>(code, size);
}

// Another entrypoint, which does a clinit check at entry.
extern "C" void ExecuteNterpWithClinitImpl() REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void EndExecuteNterpWithClinitImpl() REQUIRES_SHARED(Locks::mutator_lock_);

const void* GetNterpWithClinitEntryPoint() {
  return reinterpret_cast<const void*>(interpreter::ExecuteNterpWithClinitImpl);
}

ArrayRef<const uint8_t> NterpWithClinitImpl() {
  const uint8_t* entry_point = reinterpret_cast<const uint8_t*>(ExecuteNterpWithClinitImpl);
  size_t size = reinterpret_cast<const uint8_t*>(EndExecuteNterpWithClinitImpl) - entry_point;
  const uint8_t* code = reinterpret_cast<const uint8_t*>(EntryPointToCodePointer(entry_point));
  return ArrayRef<const uint8_t>(code, size);
}

/*
 * Verify some constants used by the nterp interpreter.
 */
void CheckNterpAsmConstants() {
  /*
   * If we're using computed goto instruction transitions, make sure
   * none of the handlers overflows the byte limit.  This won't tell
   * which one did, but if any one is too big the total size will
   * overflow.
   */
  const int width = kNterpHandlerSize;
  ptrdiff_t interp_size = reinterpret_cast<uintptr_t>(artNterpAsmInstructionEnd) -
                          reinterpret_cast<uintptr_t>(artNterpAsmInstructionStart);
  if ((interp_size == 0) || (interp_size != (art::kNumPackedOpcodes * width))) {
    LOG(FATAL) << "ERROR: unexpected asm interp size " << interp_size
               << "(did an instruction handler exceed " << width << " bytes?)";
  }
}

inline void UpdateHotness(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  // The hotness we will add to a method when we perform a
  // field/method/class/string lookup.
  constexpr uint16_t kNterpHotnessLookup = 0xff;
  method->UpdateCounter(kNterpHotnessLookup);
}

template<typename T>
inline void UpdateCache(Thread* self, const uint16_t* dex_pc_ptr, T value) {
  self->GetInterpreterCache()->Set(self, dex_pc_ptr, value);
}

template<typename T>
inline void UpdateCache(Thread* self, const uint16_t* dex_pc_ptr, T* value) {
  UpdateCache(self, dex_pc_ptr, reinterpret_cast<size_t>(value));
}

#ifdef __arm__

extern "C" void NterpStoreArm32Fprs(const char* shorty,
                                    uint32_t* registers,
                                    uint32_t* stack_args,
                                    const uint32_t* fprs) {
  // Note `shorty` has already the returned type removed.
  ScopedAssertNoThreadSuspension sants("In nterp");
  uint32_t arg_index = 0;
  uint32_t fpr_double_index = 0;
  uint32_t fpr_index = 0;
  for (uint32_t shorty_index = 0; shorty[shorty_index] != '\0'; ++shorty_index) {
    char arg_type = shorty[shorty_index];
    switch (arg_type) {
      case 'D': {
        // Double should not overlap with float.
        fpr_double_index = std::max(fpr_double_index, RoundUp(fpr_index, 2));
        if (fpr_double_index < 16) {
          registers[arg_index] = fprs[fpr_double_index++];
          registers[arg_index + 1] = fprs[fpr_double_index++];
        } else {
          registers[arg_index] = stack_args[arg_index];
          registers[arg_index + 1] = stack_args[arg_index + 1];
        }
        arg_index += 2;
        break;
      }
      case 'F': {
        if (fpr_index % 2 == 0) {
          fpr_index = std::max(fpr_double_index, fpr_index);
        }
        if (fpr_index < 16) {
          registers[arg_index] = fprs[fpr_index++];
        } else {
          registers[arg_index] = stack_args[arg_index];
        }
        arg_index++;
        break;
      }
      case 'J': {
        arg_index += 2;
        break;
      }
      default: {
        arg_index++;
        break;
      }
    }
  }
}

extern "C" void NterpSetupArm32Fprs(const char* shorty,
                                    uint32_t dex_register,
                                    uint32_t stack_index,
                                    uint32_t* fprs,
                                    uint32_t* registers,
                                    uint32_t* stack_args) {
  // Note `shorty` has already the returned type removed.
  ScopedAssertNoThreadSuspension sants("In nterp");
  uint32_t fpr_double_index = 0;
  uint32_t fpr_index = 0;
  for (uint32_t shorty_index = 0; shorty[shorty_index] != '\0'; ++shorty_index) {
    char arg_type = shorty[shorty_index];
    switch (arg_type) {
      case 'D': {
        // Double should not overlap with float.
        fpr_double_index = std::max(fpr_double_index, RoundUp(fpr_index, 2));
        if (fpr_double_index < 16) {
          fprs[fpr_double_index++] = registers[dex_register++];
          fprs[fpr_double_index++] = registers[dex_register++];
          stack_index += 2;
        } else {
          stack_args[stack_index++] = registers[dex_register++];
          stack_args[stack_index++] = registers[dex_register++];
        }
        break;
      }
      case 'F': {
        if (fpr_index % 2 == 0) {
          fpr_index = std::max(fpr_double_index, fpr_index);
        }
        if (fpr_index < 16) {
          fprs[fpr_index++] = registers[dex_register++];
          stack_index++;
        } else {
          stack_args[stack_index++] = registers[dex_register++];
        }
        break;
      }
      case 'J': {
        stack_index += 2;
        dex_register += 2;
        break;
      }
      default: {
        stack_index++;
        dex_register++;
        break;
      }
    }
  }
}

#endif

extern "C" const dex::CodeItem* NterpGetCodeItem(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  return method->GetCodeItem();
}

extern "C" const char* NterpGetShorty(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  return method->GetInterfaceMethodIfProxy(kRuntimePointerSize)->GetShorty();
}

extern "C" const char* NterpGetShortyFromMethodId(ArtMethod* caller, uint32_t method_index)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  return caller->GetDexFile()->GetMethodShorty(method_index);
}

extern "C" const char* NterpGetShortyFromInvokePolymorphic(ArtMethod* caller, uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  dex::ProtoIndex proto_idx(inst->Opcode() == Instruction::INVOKE_POLYMORPHIC
      ? inst->VRegH_45cc()
      : inst->VRegH_4rcc());
  return caller->GetDexFile()->GetShorty(proto_idx);
}

extern "C" const char* NterpGetShortyFromInvokeCustom(ArtMethod* caller, uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  uint16_t call_site_index = (inst->Opcode() == Instruction::INVOKE_CUSTOM
      ? inst->VRegB_35c()
      : inst->VRegB_3rc());
  const DexFile* dex_file = caller->GetDexFile();
  dex::ProtoIndex proto_idx = dex_file->GetProtoIndexForCallSite(call_site_index);
  return dex_file->GetShorty(proto_idx);
}

static constexpr uint8_t kInvalidInvokeType = 255u;
static_assert(static_cast<uint8_t>(kMaxInvokeType) < kInvalidInvokeType);

static constexpr uint8_t GetOpcodeInvokeType(uint8_t opcode) {
  switch (opcode) {
    case Instruction::INVOKE_DIRECT:
    case Instruction::INVOKE_DIRECT_RANGE:
      return static_cast<uint8_t>(kDirect);
    case Instruction::INVOKE_INTERFACE:
    case Instruction::INVOKE_INTERFACE_RANGE:
      return static_cast<uint8_t>(kInterface);
    case Instruction::INVOKE_STATIC:
    case Instruction::INVOKE_STATIC_RANGE:
      return static_cast<uint8_t>(kStatic);
    case Instruction::INVOKE_SUPER:
    case Instruction::INVOKE_SUPER_RANGE:
      return static_cast<uint8_t>(kSuper);
    case Instruction::INVOKE_VIRTUAL:
    case Instruction::INVOKE_VIRTUAL_RANGE:
      return static_cast<uint8_t>(kVirtual);

    default:
      return kInvalidInvokeType;
  }
}

static constexpr std::array<uint8_t, 256u> GenerateOpcodeInvokeTypes() {
  std::array<uint8_t, 256u> opcode_invoke_types{};
  for (size_t opcode = 0u; opcode != opcode_invoke_types.size(); ++opcode) {
    opcode_invoke_types[opcode] = GetOpcodeInvokeType(opcode);
  }
  return opcode_invoke_types;
}

static constexpr std::array<uint8_t, 256u> kOpcodeInvokeTypes = GenerateOpcodeInvokeTypes();

FLATTEN
extern "C" size_t NterpGetMethod(Thread* self, ArtMethod* caller, const uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  UpdateHotness(caller);
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  Instruction::Code opcode = inst->Opcode();
  DCHECK(IsUint<8>(static_cast<std::underlying_type_t<Instruction::Code>>(opcode)));
  uint8_t raw_invoke_type = kOpcodeInvokeTypes[opcode];
  DCHECK_LE(raw_invoke_type, kMaxInvokeType);
  InvokeType invoke_type = static_cast<InvokeType>(raw_invoke_type);

  // In release mode, this is just a simple load.
  // In debug mode, this checks that we're using the correct instruction format.
  uint16_t method_index =
      (opcode >= Instruction::INVOKE_VIRTUAL_RANGE) ? inst->VRegB_3rc() : inst->VRegB_35c();

  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();
  ArtMethod* resolved_method = caller->SkipAccessChecks()
      ? class_linker->ResolveMethod<ClassLinker::ResolveMode::kNoChecks>(
            self, method_index, caller, invoke_type)
      : class_linker->ResolveMethod<ClassLinker::ResolveMode::kCheckICCEAndIAE>(
            self, method_index, caller, invoke_type);
  if (resolved_method == nullptr) {
    DCHECK(self->IsExceptionPending());
    return 0;
  }

  if (invoke_type == kSuper) {
    resolved_method = caller->SkipAccessChecks()
        ? FindSuperMethodToCall</*access_check=*/false>(method_index, resolved_method, caller, self)
        : FindSuperMethodToCall</*access_check=*/true>(method_index, resolved_method, caller, self);
    if (resolved_method == nullptr) {
      DCHECK(self->IsExceptionPending());
      return 0;
    }
  }

  if (invoke_type == kInterface) {
    size_t result = 0u;
    if (resolved_method->GetDeclaringClass()->IsObjectClass()) {
      // Set the low bit to notify the interpreter it should do a vtable call.
      DCHECK_LT(resolved_method->GetMethodIndex(), 0x10000);
      result = (resolved_method->GetMethodIndex() << 16) | 1U;
    } else {
      DCHECK(resolved_method->GetDeclaringClass()->IsInterface());
      DCHECK(!resolved_method->IsCopied());
      if (!resolved_method->IsAbstract()) {
        // Set the second bit to notify the interpreter this is a default
        // method.
        result = reinterpret_cast<size_t>(resolved_method) | 2U;
      } else {
        result = reinterpret_cast<size_t>(resolved_method);
      }
    }
    UpdateCache(self, dex_pc_ptr, result);
    return result;
  } else if (resolved_method->IsStringConstructor()) {
    CHECK_NE(invoke_type, kSuper);
    resolved_method = WellKnownClasses::StringInitToStringFactory(resolved_method);
    // Or the result with 1 to notify to nterp this is a string init method. We
    // also don't cache the result as we don't want nterp to have its fast path always
    // check for it, and we expect a lot more regular calls than string init
    // calls.
    return reinterpret_cast<size_t>(resolved_method) | 1;
  } else if (invoke_type == kVirtual) {
    UpdateCache(self, dex_pc_ptr, resolved_method->GetMethodIndex());
    return resolved_method->GetMethodIndex();
  } else {
    UpdateCache(self, dex_pc_ptr, resolved_method);
    return reinterpret_cast<size_t>(resolved_method);
  }
}

extern "C" size_t NterpGetStaticField(Thread* self,
                                      ArtMethod* caller,
                                      const uint16_t* dex_pc_ptr,
                                      size_t resolve_field_type)  // Resolve if not zero
    REQUIRES_SHARED(Locks::mutator_lock_) {
  UpdateHotness(caller);
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  uint16_t field_index = inst->VRegB_21c();
  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();
  Instruction::Code opcode = inst->Opcode();
  ArtField* resolved_field = ResolveFieldWithAccessChecks(
      self,
      class_linker,
      field_index,
      caller,
      /*is_static=*/ true,
      /*is_put=*/ IsInstructionSPut(opcode),
      resolve_field_type);

  if (resolved_field == nullptr) {
    DCHECK(self->IsExceptionPending());
    return 0;
  }
  if (UNLIKELY(!resolved_field->GetDeclaringClass()->IsVisiblyInitialized())) {
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> h_class(hs.NewHandle(resolved_field->GetDeclaringClass()));
    if (UNLIKELY(!class_linker->EnsureInitialized(
                      self, h_class, /*can_init_fields=*/ true, /*can_init_parents=*/ true))) {
      DCHECK(self->IsExceptionPending());
      return 0;
    }
    DCHECK(h_class->IsInitializing());
  }
  if (resolved_field->IsVolatile()) {
    // Or the result with 1 to notify to nterp this is a volatile field. We
    // also don't cache the result as we don't want nterp to have its fast path always
    // check for it.
    return reinterpret_cast<size_t>(resolved_field) | 1;
  } else {
    // For sput-object, try to resolve the field type even if we were not requested to.
    // Only if the field type is successfully resolved can we update the cache. If we
    // fail to resolve the type, we clear the exception to keep interpreter
    // semantics of not throwing when null is stored.
    if (opcode == Instruction::SPUT_OBJECT &&
        resolve_field_type == 0 &&
        resolved_field->ResolveType() == nullptr) {
      DCHECK(self->IsExceptionPending());
      self->ClearException();
    } else {
      UpdateCache(self, dex_pc_ptr, resolved_field);
    }
    return reinterpret_cast<size_t>(resolved_field);
  }
}

extern "C" uint32_t NterpGetInstanceFieldOffset(Thread* self,
                                                ArtMethod* caller,
                                                const uint16_t* dex_pc_ptr,
                                                size_t resolve_field_type)  // Resolve if not zero
    REQUIRES_SHARED(Locks::mutator_lock_) {
  UpdateHotness(caller);
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  uint16_t field_index = inst->VRegC_22c();
  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();
  Instruction::Code opcode = inst->Opcode();
  ArtField* resolved_field = ResolveFieldWithAccessChecks(
      self,
      class_linker,
      field_index,
      caller,
      /*is_static=*/ false,
      /*is_put=*/ IsInstructionIPut(opcode),
      resolve_field_type);
  if (resolved_field == nullptr) {
    DCHECK(self->IsExceptionPending());
    return 0;
  }
  if (resolved_field->IsVolatile()) {
    // Don't cache for a volatile field, and return a negative offset as marker
    // of volatile.
    return -resolved_field->GetOffset().Uint32Value();
  }
  // For iput-object, try to resolve the field type even if we were not requested to.
  // Only if the field type is successfully resolved can we update the cache. If we
  // fail to resolve the type, we clear the exception to keep interpreter
  // semantics of not throwing when null is stored.
  if (opcode == Instruction::IPUT_OBJECT &&
      resolve_field_type == 0 &&
      resolved_field->ResolveType() == nullptr) {
    DCHECK(self->IsExceptionPending());
    self->ClearException();
  } else {
    UpdateCache(self, dex_pc_ptr, resolved_field->GetOffset().Uint32Value());
  }
  return resolved_field->GetOffset().Uint32Value();
}

extern "C" mirror::Object* NterpGetClass(Thread* self, ArtMethod* caller, uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  UpdateHotness(caller);
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  Instruction::Code opcode = inst->Opcode();
  DCHECK(opcode == Instruction::CHECK_CAST ||
         opcode == Instruction::INSTANCE_OF ||
         opcode == Instruction::CONST_CLASS ||
         opcode == Instruction::NEW_ARRAY);

  // In release mode, this is just a simple load.
  // In debug mode, this checks that we're using the correct instruction format.
  dex::TypeIndex index = dex::TypeIndex(
      (opcode == Instruction::CHECK_CAST || opcode == Instruction::CONST_CLASS)
          ? inst->VRegB_21c()
          : inst->VRegC_22c());

  ObjPtr<mirror::Class> c =
      ResolveVerifyAndClinit(index,
                             caller,
                             self,
                             /* can_run_clinit= */ false,
                             /* verify_access= */ !caller->SkipAccessChecks());
  if (UNLIKELY(c == nullptr)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  UpdateCache(self, dex_pc_ptr, c.Ptr());
  return c.Ptr();
}

extern "C" mirror::Object* NterpAllocateObject(Thread* self,
                                               ArtMethod* caller,
                                               uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  UpdateHotness(caller);
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  DCHECK_EQ(inst->Opcode(), Instruction::NEW_INSTANCE);
  dex::TypeIndex index = dex::TypeIndex(inst->VRegB_21c());
  ObjPtr<mirror::Class> c =
      ResolveVerifyAndClinit(index,
                             caller,
                             self,
                             /* can_run_clinit= */ false,
                             /* verify_access= */ !caller->SkipAccessChecks());
  if (UNLIKELY(c == nullptr)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  gc::AllocatorType allocator_type = Runtime::Current()->GetHeap()->GetCurrentAllocator();
  if (UNLIKELY(c->IsStringClass())) {
    // We don't cache the class for strings as we need to special case their
    // allocation.
    return mirror::String::AllocEmptyString(self, allocator_type).Ptr();
  } else {
    if (!c->IsFinalizable() && c->IsInstantiable()) {
      // Cache non-finalizable classes for next calls.
      UpdateCache(self, dex_pc_ptr, c.Ptr());
    }
    return AllocObjectFromCode(c, self, allocator_type).Ptr();
  }
}

extern "C" mirror::Object* NterpLoadObject(Thread* self, ArtMethod* caller, uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  ClassLinker* const class_linker = Runtime::Current()->GetClassLinker();
  switch (inst->Opcode()) {
    case Instruction::CONST_STRING:
    case Instruction::CONST_STRING_JUMBO: {
      UpdateHotness(caller);
      dex::StringIndex string_index(
          (inst->Opcode() == Instruction::CONST_STRING)
              ? inst->VRegB_21c()
              : inst->VRegB_31c());
      ObjPtr<mirror::String> str = class_linker->ResolveString(string_index, caller);
      if (str == nullptr) {
        DCHECK(self->IsExceptionPending());
        return nullptr;
      }
      UpdateCache(self, dex_pc_ptr, str.Ptr());
      return str.Ptr();
    }
    case Instruction::CONST_METHOD_HANDLE: {
      // Don't cache: we don't expect this to be performance sensitive, and we
      // don't want the cache to conflict with a performance sensitive entry.
      return class_linker->ResolveMethodHandle(self, inst->VRegB_21c(), caller).Ptr();
    }
    case Instruction::CONST_METHOD_TYPE: {
      // Don't cache: we don't expect this to be performance sensitive, and we
      // don't want the cache to conflict with a performance sensitive entry.
      return class_linker->ResolveMethodType(
          self, dex::ProtoIndex(inst->VRegB_21c()), caller).Ptr();
    }
    default:
      LOG(FATAL) << "Unreachable";
  }
  return nullptr;
}

extern "C" void NterpUnimplemented() {
  LOG(FATAL) << "Unimplemented";
}

static mirror::Object* DoFilledNewArray(Thread* self,
                                        ArtMethod* caller,
                                        uint16_t* dex_pc_ptr,
                                        uint32_t* regs,
                                        bool is_range)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const Instruction* inst = Instruction::At(dex_pc_ptr);
  if (kIsDebugBuild) {
    if (is_range) {
      DCHECK_EQ(inst->Opcode(), Instruction::FILLED_NEW_ARRAY_RANGE);
    } else {
      DCHECK_EQ(inst->Opcode(), Instruction::FILLED_NEW_ARRAY);
    }
  }
  const int32_t length = is_range ? inst->VRegA_3rc() : inst->VRegA_35c();
  DCHECK_GE(length, 0);
  if (!is_range) {
    // Checks FILLED_NEW_ARRAY's length does not exceed 5 arguments.
    DCHECK_LE(length, 5);
  }
  uint16_t type_idx = is_range ? inst->VRegB_3rc() : inst->VRegB_35c();
  ObjPtr<mirror::Class> array_class =
      ResolveVerifyAndClinit(dex::TypeIndex(type_idx),
                             caller,
                             self,
                             /* can_run_clinit= */ true,
                             /* verify_access= */ !caller->SkipAccessChecks());
  if (UNLIKELY(array_class == nullptr)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }
  DCHECK(array_class->IsArrayClass());
  ObjPtr<mirror::Class> component_class = array_class->GetComponentType();
  const bool is_primitive_int_component = component_class->IsPrimitiveInt();
  if (UNLIKELY(component_class->IsPrimitive() && !is_primitive_int_component)) {
    if (component_class->IsPrimitiveLong() || component_class->IsPrimitiveDouble()) {
      ThrowRuntimeException("Bad filled array request for type %s",
                            component_class->PrettyDescriptor().c_str());
    } else {
      self->ThrowNewExceptionF(
          "Ljava/lang/InternalError;",
          "Found type %s; filled-new-array not implemented for anything but 'int'",
          component_class->PrettyDescriptor().c_str());
    }
    return nullptr;
  }
  ObjPtr<mirror::Object> new_array = mirror::Array::Alloc(
      self,
      array_class,
      length,
      array_class->GetComponentSizeShift(),
      Runtime::Current()->GetHeap()->GetCurrentAllocator());
  if (UNLIKELY(new_array == nullptr)) {
    self->AssertPendingOOMException();
    return nullptr;
  }
  uint32_t arg[Instruction::kMaxVarArgRegs];  // only used in filled-new-array.
  uint32_t vregC = 0;   // only used in filled-new-array-range.
  if (is_range) {
    vregC = inst->VRegC_3rc();
  } else {
    inst->GetVarArgs(arg);
  }
  for (int32_t i = 0; i < length; ++i) {
    size_t src_reg = is_range ? vregC + i : arg[i];
    if (is_primitive_int_component) {
      new_array->AsIntArray()->SetWithoutChecks</* kTransactionActive= */ false>(i, regs[src_reg]);
    } else {
      new_array->AsObjectArray<mirror::Object>()->SetWithoutChecks</* kTransactionActive= */ false>(
          i, reinterpret_cast<mirror::Object*>(regs[src_reg]));
    }
  }
  return new_array.Ptr();
}

extern "C" mirror::Object* NterpFilledNewArray(Thread* self,
                                               ArtMethod* caller,
                                               uint32_t* registers,
                                               uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return DoFilledNewArray(self, caller, dex_pc_ptr, registers, /* is_range= */ false);
}

extern "C" mirror::Object* NterpFilledNewArrayRange(Thread* self,
                                                    ArtMethod* caller,
                                                    uint32_t* registers,
                                                    uint16_t* dex_pc_ptr)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return DoFilledNewArray(self, caller, dex_pc_ptr, registers, /* is_range= */ true);
}

extern "C" jit::OsrData* NterpHotMethod(ArtMethod* method, uint16_t* dex_pc_ptr, uint32_t* vregs)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // It is important this method is not suspended because it can be called on
  // method entry and async deoptimization does not expect runtime methods other than the
  // suspend entrypoint before executing the first instruction of a Java
  // method.
  ScopedAssertNoThreadSuspension sants("In nterp");
  Runtime* runtime = Runtime::Current();
  if (method->IsMemorySharedMethod()) {
    DCHECK_EQ(Thread::Current()->GetSharedMethodHotness(), 0u);
    Thread::Current()->ResetSharedMethodHotness();
  } else {
    method->ResetCounter(runtime->GetJITOptions()->GetWarmupThreshold());
  }
  jit::Jit* jit = runtime->GetJit();
  if (jit != nullptr && jit->UseJitCompilation()) {
    // Nterp passes null on entry where we don't want to OSR.
    if (dex_pc_ptr != nullptr) {
      // This could be a loop back edge, check if we can OSR.
      CodeItemInstructionAccessor accessor(method->DexInstructions());
      uint32_t dex_pc = dex_pc_ptr - accessor.Insns();
      jit::OsrData* osr_data = jit->PrepareForOsr(
          method->GetInterfaceMethodIfProxy(kRuntimePointerSize), dex_pc, vregs);
      if (osr_data != nullptr) {
        return osr_data;
      }
    }
    jit->MaybeEnqueueCompilation(method, Thread::Current());
  }
  return nullptr;
}

extern "C" ssize_t NterpDoPackedSwitch(const uint16_t* switchData, int32_t testVal)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  const int kInstrLen = 3;

  /*
   * Packed switch data format:
   *  ushort ident = 0x0100   magic value
   *  ushort size             number of entries in the table
   *  int first_key           first (and lowest) switch case value
   *  int targets[size]       branch targets, relative to switch opcode
   *
   * Total size is (4+size*2) 16-bit code units.
   */
  uint16_t signature = *switchData++;
  DCHECK_EQ(signature, static_cast<uint16_t>(art::Instruction::kPackedSwitchSignature));

  uint16_t size = *switchData++;

  int32_t firstKey = *switchData++;
  firstKey |= (*switchData++) << 16;

  int index = testVal - firstKey;
  if (index < 0 || index >= size) {
    return kInstrLen;
  }

  /*
   * The entries are guaranteed to be aligned on a 32-bit boundary;
   * we can treat them as a native int array.
   */
  const int32_t* entries = reinterpret_cast<const int32_t*>(switchData);
  return entries[index];
}

/*
 * Find the matching case.  Returns the offset to the handler instructions.
 *
 * Returns 3 if we don't find a match (it's the size of the sparse-switch
 * instruction).
 */
extern "C" ssize_t NterpDoSparseSwitch(const uint16_t* switchData, int32_t testVal)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension sants("In nterp");
  const int kInstrLen = 3;
  uint16_t size;
  const int32_t* keys;
  const int32_t* entries;

  /*
   * Sparse switch data format:
   *  ushort ident = 0x0200   magic value
   *  ushort size             number of entries in the table; > 0
   *  int keys[size]          keys, sorted low-to-high; 32-bit aligned
   *  int targets[size]       branch targets, relative to switch opcode
   *
   * Total size is (2+size*4) 16-bit code units.
   */

  uint16_t signature = *switchData++;
  DCHECK_EQ(signature, static_cast<uint16_t>(art::Instruction::kSparseSwitchSignature));

  size = *switchData++;

  /* The keys are guaranteed to be aligned on a 32-bit boundary;
   * we can treat them as a native int array.
   */
  keys = reinterpret_cast<const int32_t*>(switchData);

  /* The entries are guaranteed to be aligned on a 32-bit boundary;
   * we can treat them as a native int array.
   */
  entries = keys + size;

  /*
   * Binary-search through the array of keys, which are guaranteed to
   * be sorted low-to-high.
   */
  int lo = 0;
  int hi = size - 1;
  while (lo <= hi) {
    int mid = (lo + hi) >> 1;

    int32_t foundVal = keys[mid];
    if (testVal < foundVal) {
      hi = mid - 1;
    } else if (testVal > foundVal) {
      lo = mid + 1;
    } else {
      return entries[mid];
    }
  }
  return kInstrLen;
}

extern "C" void NterpFree(void* val) {
  free(val);
}

}  // namespace interpreter
}  // namespace art
