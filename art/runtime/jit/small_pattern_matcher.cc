/*
 * Copyright 2023 The Android Open Source Project
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

#include "small_pattern_matcher.h"

#include "art_method-inl.h"
#include "dex/dex_instruction-inl.h"
#include "entrypoints/entrypoint_utils-inl.h"

namespace art HIDDEN {
namespace jit {

// The following methods will be directly invoked by our own JIT/AOT compiled
// code.

static void EmptyMethod() {}
static int32_t ReturnZero() { return 0; }
static int32_t ReturnOne() { return 1; }
static int32_t ReturnFirstArgMethod([[maybe_unused]] ArtMethod* method, int32_t first_arg) {
  return first_arg;
}

template <int offset, typename T>
static std::conditional_t<(sizeof(T) < sizeof(int32_t)), int32_t, T> ReturnFieldAt(
    [[maybe_unused]] ArtMethod* method, mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_) {
  return obj->GetFieldPrimitive<T, /* kIsVolatile= */ false>(
      MemberOffset(offset + sizeof(mirror::Object)));
}

template <int offset, typename unused>
static mirror::Object* ReturnFieldObjectAt([[maybe_unused]] ArtMethod* method, mirror::Object* obj)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return obj->GetFieldObject<mirror::Object>(MemberOffset(offset + sizeof(mirror::Object)));
}

template <int offset, typename T>
static std::conditional_t<(sizeof(T) < sizeof(int32_t)), int32_t, T> ReturnStaticFieldAt(
    ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> cls = method->GetDeclaringClass();
  MemberOffset first_field_offset = cls->GetFirstReferenceStaticFieldOffset(kRuntimePointerSize);
  return cls->GetFieldPrimitive<T, /* kIsVolatile= */ false>(
      MemberOffset(offset + first_field_offset.Int32Value()));
}

template <int offset, typename unused>
static mirror::Object* ReturnStaticFieldObjectAt(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> cls = method->GetDeclaringClass();
  MemberOffset first_field_offset = cls->GetFirstReferenceStaticFieldOffset(kRuntimePointerSize);
  return cls->GetFieldObject<mirror::Object>(
      MemberOffset(offset + first_field_offset.Int32Value()));
}

template <int offset, typename T>
static void SetFieldAt([[maybe_unused]] ArtMethod* method, mirror::Object* obj, T value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  obj->SetFieldPrimitive<T, /* kIsVolatile= */ false>(
      MemberOffset(offset + sizeof(mirror::Object)), value);
}

template <int offset, typename unused>
static void SetFieldObjectAt([[maybe_unused]] ArtMethod* method,
                             mirror::Object* obj,
                             mirror::Object* value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  obj->SetFieldObject</* kTransactionActive */ false>(
      MemberOffset(offset + sizeof(mirror::Object)), value);
}

template <int offset, typename T>
static void ConstructorSetFieldAt([[maybe_unused]] ArtMethod* method, mirror::Object* obj, T value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  obj->SetFieldPrimitive<T, /* kIsVolatile= */ false>(
      MemberOffset(offset + sizeof(mirror::Object)), value);
  QuasiAtomic::ThreadFenceForConstructor();
}

template <int offset, typename unused>
static void ConstructorSetFieldObjectAt([[maybe_unused]] ArtMethod* method,
                                        mirror::Object* obj,
                                        mirror::Object* value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  obj->SetFieldObject</* kTransactionActive */ false>(
      MemberOffset(offset + sizeof(mirror::Object)), value);
  QuasiAtomic::ThreadFenceForConstructor();
}

#define SWITCH_CASE(offset, func, type) \
  case offset:                          \
    return reinterpret_cast<void*>(&func<offset, type>);  // NOLINT [bugprone-macro-parentheses]

#define DO_SWITCH_OFFSET(offset, F, T) \
  switch (offset) { \
    SWITCH_CASE(0, F, T) \
    SWITCH_CASE(4, F, T) \
    SWITCH_CASE(8, F, T) \
    SWITCH_CASE(12, F, T) \
    SWITCH_CASE(16, F, T) \
    SWITCH_CASE(20, F, T) \
    SWITCH_CASE(24, F, T) \
    SWITCH_CASE(28, F, T) \
    SWITCH_CASE(32, F, T) \
    SWITCH_CASE(36, F, T) \
    SWITCH_CASE(40, F, T) \
    SWITCH_CASE(44, F, T) \
    SWITCH_CASE(48, F, T) \
    SWITCH_CASE(52, F, T) \
    SWITCH_CASE(56, F, T) \
    SWITCH_CASE(60, F, T) \
    SWITCH_CASE(64, F, T) \
    default: return nullptr; \
  }

#define DO_SWITCH(offset, O, P, K)                  \
  DCHECK_EQ(is_object, (K) == Primitive::kPrimNot); \
  switch (K) {                                      \
    case Primitive::kPrimBoolean:                   \
      DO_SWITCH_OFFSET(offset, P, uint8_t);         \
    case Primitive::kPrimInt:                       \
      DO_SWITCH_OFFSET(offset, P, int32_t);         \
    case Primitive::kPrimLong:                      \
      DO_SWITCH_OFFSET(offset, P, int64_t);         \
    case Primitive::kPrimNot:                       \
      DO_SWITCH_OFFSET(offset, O, mirror::Object*); \
    case Primitive::kPrimFloat:                     \
      if (kRuntimeISA == InstructionSet::kArm64) {  \
        DO_SWITCH_OFFSET(offset, P, float);         \
      } else {                                      \
        return nullptr;                             \
      }                                             \
    case Primitive::kPrimDouble:                    \
      if (kRuntimeISA == InstructionSet::kArm64) {  \
        DO_SWITCH_OFFSET(offset, P, double);        \
      } else {                                      \
        return nullptr;                             \
      }                                             \
    default:                                        \
      return nullptr;                               \
  }

const void* SmallPatternMatcher::TryMatch(ArtMethod* method) REQUIRES_SHARED(Locks::mutator_lock_) {
  CodeItemDataAccessor accessor(*method->GetDexFile(), method->GetCodeItem());

  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();

  bool is_recognizable_constructor =
      method->IsConstructor() &&
      !method->IsStatic() &&
      method->GetDeclaringClass()->GetSuperClass() != nullptr &&
      method->GetDeclaringClass()->GetSuperClass()->IsObjectClass();

  size_t insns_size = accessor.InsnsSizeInCodeUnits();
  if (insns_size >= 4u) {
    if (!is_recognizable_constructor) {
      return nullptr;
    }
    // We can recognize a constructor with 6 or 4 code units.
    if (insns_size != 4u && insns_size != 6u) {
      return nullptr;
    }
  }

  auto is_object_init_invoke = [&](const Instruction& instruction)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    uint16_t method_idx = instruction.VRegB_35c();
    Thread* self = Thread::Current();
    ArtMethod* target_method =
        class_linker->ResolveMethod<ClassLinker::ResolveMode::kNoChecks>(self,
                                                                         method_idx,
                                                                         method,
                                                                         kDirect);
    if (target_method == nullptr) {
      self->ClearException();
      return false;
    }
    if (!target_method->GetDeclaringClass()->IsObjectClass()) {
      return false;
    }
    DCHECK(target_method->GetDeclaringClass()->IsVerified());
    CodeItemDataAccessor accessor(*target_method->GetDexFile(), target_method->GetCodeItem());
    DCHECK_EQ(accessor.InsnsSizeInCodeUnits(), 1u);
    DCHECK_EQ(accessor.begin().Inst().Opcode(), Instruction::RETURN_VOID);
    return true;
  };

  // Recognize a constructor of the form:
  //   invoke-direct v0, j.l.Object.<init>
  //   return-void
  if (insns_size == 4u) {
    DCHECK(is_recognizable_constructor);
    const Instruction& instruction = accessor.begin().Inst();
    if (instruction.Opcode() == Instruction::INVOKE_DIRECT &&
        is_object_init_invoke(instruction)) {
      return reinterpret_cast<void*>(&EmptyMethod);
    }
    return nullptr;
  }

  // Recognize:
  //   return-void
  // Or:
  //   return-object v0
  if (insns_size == 1u) {
    const Instruction& instruction = accessor.begin().Inst();
    if (instruction.Opcode() == Instruction::RETURN_VOID) {
      return reinterpret_cast<void*>(&EmptyMethod);
    }

    if (instruction.Opcode() == Instruction::RETURN_OBJECT) {
      uint16_t number_of_vregs = accessor.RegistersSize();
      uint16_t number_of_parameters = accessor.InsSize();
      uint16_t obj_reg = number_of_vregs - number_of_parameters;
      if (obj_reg == instruction.VRegA_11x()) {
        return reinterpret_cast<void*>(&ReturnFirstArgMethod);
      }
    }
    return nullptr;
  }

  // Recognize:
  //   const vX, 0/1
  //   return{-object} vX
  if (insns_size == 2u) {
    if (method->GetReturnTypePrimitive() == Primitive::kPrimFloat) {
      // Too rare to bother.
      return nullptr;
    }
    int32_t register_index = -1;
    int32_t constant = -1;
    for (DexInstructionPcPair pair : accessor) {
      const Instruction& instruction = pair.Inst();
      switch (pair->Opcode()) {
        case Instruction::CONST_4: {
          register_index = instruction.VRegA_11n();
          constant = instruction.VRegB_11n();
          if (constant != 0 && constant != 1) {
            return nullptr;
          }
          break;
        }
        case Instruction::CONST_16: {
          register_index = instruction.VRegA_21s();
          constant = instruction.VRegB_21s();
          if (constant != 0 && constant != 1) {
            return nullptr;
          }
          break;
        }
        case Instruction::RETURN:
        case Instruction::RETURN_OBJECT: {
          if (register_index == instruction.VRegA_11x()) {
            if (constant == 0) {
              return reinterpret_cast<void*>(&ReturnZero);
            } else if (constant == 1) {
              return reinterpret_cast<void*>(&ReturnOne);
            }
          }
          return nullptr;
        }
        default:
          return nullptr;
      }
    }
    return nullptr;
  }

  // Recognize:
  //   iget-{object,wide,boolean} vX, v0, field
  //   return-{object} vX
  // Or:
  //   iput-{object,wide,boolean} v1, v0, field
  //   return-void
  // Or:
  //   sget-object vX, field
  //   return-object vX
  // Or:
  //   iput-{object,wide,boolean} v1, v0, field
  //   invoke-direct v0, j.l.Object.<init>
  //   return-void
  // Or:
  //   invoke-direct v0, j.l.Object.<init>
  //   iput-{object,wide,boolean} v1, v0, field
  //   return-void
  if (insns_size == 3u || insns_size == 6u) {
    DCHECK_IMPLIES(insns_size == 6u, is_recognizable_constructor);
    uint16_t number_of_vregs = accessor.RegistersSize();
    uint16_t number_of_parameters = accessor.InsSize();
    uint16_t obj_reg = number_of_vregs - number_of_parameters;
    uint16_t first_param_reg = number_of_vregs - number_of_parameters + 1;
    uint16_t dest_reg = -1;
    uint32_t offset = -1;
    bool is_object = false;
    bool is_put = false;
    bool is_static = false;
    bool is_final = false;
    Primitive::Type field_type;
    for (DexInstructionPcPair pair : accessor) {
      const Instruction& instruction = pair.Inst();
      switch (pair->Opcode()) {
        case Instruction::INVOKE_DIRECT:
          if (!is_recognizable_constructor || !is_object_init_invoke(instruction)) {
            return nullptr;
          }
          break;
        case Instruction::SGET_OBJECT:
          is_static = true;
          FALLTHROUGH_INTENDED;
        case Instruction::IPUT_OBJECT:
        case Instruction::IGET_OBJECT:
          is_object = true;
          FALLTHROUGH_INTENDED;
        case Instruction::IPUT:
        case Instruction::IGET:
        case Instruction::IGET_BOOLEAN:
        case Instruction::IPUT_BOOLEAN:
        case Instruction::IGET_WIDE:
        case Instruction::IPUT_WIDE: {
          is_put = (pair->Opcode() == Instruction::IPUT ||
                    pair->Opcode() == Instruction::IPUT_OBJECT ||
                    pair->Opcode() == Instruction::IPUT_BOOLEAN ||
                    pair->Opcode() == Instruction::IPUT_WIDE);
          if (!is_static && obj_reg != instruction.VRegB_22c()) {
            // The field access is not on the first parameter.
            return nullptr;
          }
          if (!is_static && method->IsStatic()) {
            // Getting/setting an instance field on an object that can be null.
            // Our stubs cannot handle implicit null checks.
            return nullptr;
          }
          if (is_put) {
            if (first_param_reg != instruction.VRegA_22c()) {
              // The value being stored is not the first parameter after 'this'.
              return nullptr;
            }
          } else {
            dest_reg = is_static ? instruction.VRegA_21c() : instruction.VRegA_22c();
          }
          uint16_t field_index = is_static ? instruction.VRegB_21c() : instruction.VRegC_22c();
          Thread* self = Thread::Current();
          ArtField* field =
              ResolveFieldWithAccessChecks(Thread::Current(),
                                           class_linker,
                                           field_index,
                                           method,
                                           is_static,
                                           is_put,
                                           /* resolve_field_type= */ is_put && is_object);
          if (field == nullptr) {
            self->ClearException();
            return nullptr;
          }
          if (field->IsVolatile()) {
            return nullptr;
          }
          if (is_static && field->GetDeclaringClass() != method->GetDeclaringClass()) {
            return nullptr;
          }
          offset = field->GetOffset().Int32Value();
          if (is_static) {
            // We subtract the start of reference fields to share more stubs.
            MemberOffset first_field_offset =
                field->GetDeclaringClass()->GetFirstReferenceStaticFieldOffset(kRuntimePointerSize);
            offset = offset - first_field_offset.Int32Value();
          } else {
            offset = offset - sizeof(mirror::Object);
          }
          if (offset > 64) {
            return nullptr;
          }
          field_type = field->GetTypeAsPrimitiveType();
          is_final = field->IsFinal();
          break;
        }
        case Instruction::RETURN_OBJECT:
        case Instruction::RETURN_WIDE:
        case Instruction::RETURN: {
          if (is_put || dest_reg != instruction.VRegA_11x()) {
            // The returned value is not the fetched field.
            return nullptr;
          }
          if (is_static) {
            DO_SWITCH(offset, ReturnStaticFieldObjectAt, ReturnStaticFieldAt, field_type);
          } else {
            DO_SWITCH(offset, ReturnFieldObjectAt, ReturnFieldAt, field_type);
          }
        }
        case Instruction::RETURN_VOID: {
          if (!is_put) {
            return nullptr;
          }
          if (is_final) {
            DCHECK(is_recognizable_constructor);
            DO_SWITCH(offset,  ConstructorSetFieldObjectAt, ConstructorSetFieldAt, field_type);
          } else {
            DO_SWITCH(offset, SetFieldObjectAt, SetFieldAt, field_type);
          }
        }
        default:
          return nullptr;
      }
    }
  }

  return nullptr;
}

}  // namespace jit
}  // namespace art
