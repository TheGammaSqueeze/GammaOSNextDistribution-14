/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef ART_RUNTIME_METHOD_HANDLES_INL_H_
#define ART_RUNTIME_METHOD_HANDLES_INL_H_

#include "method_handles.h"

#include "common_throws.h"
#include "dex/dex_instruction.h"
#include "interpreter/interpreter_common.h"
#include "interpreter/shadow_frame-inl.h"
#include "jvalue-inl.h"
#include "mirror/class.h"
#include "mirror/method_type-inl.h"
#include "mirror/object.h"
#include "reflection.h"
#include "stack.h"

namespace art HIDDEN {

// A convenience class that allows for iteration through a list of
// input argument registers. This is used to iterate over input
// arguments while performing standard argument conversions.
class ShadowFrameGetter {
 public:
  ShadowFrameGetter(const ShadowFrame& shadow_frame,
                    const InstructionOperands* const operands,
                    size_t operand_index = 0u)
      : shadow_frame_(shadow_frame), operands_(operands), operand_index_(operand_index)  {}

  ALWAYS_INLINE uint32_t Get() REQUIRES_SHARED(Locks::mutator_lock_) {
    return shadow_frame_.GetVReg(Next());
  }

  ALWAYS_INLINE int64_t GetLong() REQUIRES_SHARED(Locks::mutator_lock_) {
    return shadow_frame_.GetVRegLong(NextLong());
  }

  ALWAYS_INLINE ObjPtr<mirror::Object> GetReference() REQUIRES_SHARED(Locks::mutator_lock_) {
    return shadow_frame_.GetVRegReference(Next());
  }

 private:
  uint32_t Next() {
    const uint32_t next = operands_->GetOperand(operand_index_);
    operand_index_ += 1;
    return next;
  }

  uint32_t NextLong() {
    const uint32_t next = operands_->GetOperand(operand_index_);
    operand_index_ += 2;
    return next;
  }

  const ShadowFrame& shadow_frame_;
  const InstructionOperands* const operands_;  // the set of register operands to read
  size_t operand_index_;  // the next register operand to read from frame
};

// A convenience class that allows values to be written to a given shadow frame,
// starting at location |first_dst_reg|.
class ShadowFrameSetter {
 public:
  ShadowFrameSetter(ShadowFrame* shadow_frame, size_t first_dst_reg)
      : shadow_frame_(shadow_frame), arg_index_(first_dst_reg) {}

  ALWAYS_INLINE void Set(uint32_t value) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LT(arg_index_, shadow_frame_->NumberOfVRegs());
    shadow_frame_->SetVReg(arg_index_++, value);
  }

  ALWAYS_INLINE void SetReference(ObjPtr<mirror::Object> value)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LT(arg_index_, shadow_frame_->NumberOfVRegs());
    shadow_frame_->SetVRegReference(arg_index_++, value);
  }

  ALWAYS_INLINE void SetLong(int64_t value) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_LT(arg_index_, shadow_frame_->NumberOfVRegs());
    shadow_frame_->SetVRegLong(arg_index_, value);
    arg_index_ += 2;
  }

  ALWAYS_INLINE bool Done() const {
    return arg_index_ == shadow_frame_->NumberOfVRegs();
  }

 private:
  ShadowFrame* shadow_frame_;
  size_t arg_index_;
};

inline bool ConvertArgumentValue(const ThrowWrongMethodTypeFunction& throw_wmt,
                                 ObjPtr<mirror::Class> from,
                                 ObjPtr<mirror::Class> to,
                                 /*inout*/ JValue* value) {
  if (from == to) {
    return true;
  }

  // `*value` may contain a bare heap pointer which is generally unsafe.
  // `ConvertJValueCommon()` saves `*value`, `from`, and `to` to Handles
  // where necessary to avoid issues if the heap changes.
  if (ConvertJValueCommon(throw_wmt, from, to, value)) {
    DCHECK(!Thread::Current()->IsExceptionPending());
    return true;
  } else {
    DCHECK(Thread::Current()->IsExceptionPending());
    value->SetJ(0);
    return false;
  }
}

inline bool ConvertReturnValue(const ThrowWrongMethodTypeFunction& throw_wmt,
                               ObjPtr<mirror::Class> from,
                               ObjPtr<mirror::Class> to,
                               /*inout*/ JValue* value) {
  if (to->GetPrimitiveType() == Primitive::kPrimVoid || from == to) {
    return true;
  }

  // `*value` may contain a bare heap pointer which is generally unsafe.
  // `ConvertJValueCommon()` saves `*value`, `from`, and `to` to Handles
  // where necessary to avoid issues if the heap changes.
  if (ConvertJValueCommon(throw_wmt, from, to, value)) {
    DCHECK(!Thread::Current()->IsExceptionPending());
    return true;
  } else {
    DCHECK(Thread::Current()->IsExceptionPending());
    value->SetJ(0);
    return false;
  }
}

template <typename FromPTypes, typename ToPTypes, typename G, typename S>
bool PerformConversions(const ThrowWrongMethodTypeFunction& throw_wmt,
                        FromPTypes from_types,
                        ToPTypes to_types,
                        G* getter,
                        S* setter) {
  DCHECK_EQ(from_types.GetLength(), to_types.GetLength());
  for (int32_t i = 0, length = to_types.GetLength(); i != length; ++i) {
    ObjPtr<mirror::Class> from = from_types.Get(i);
    ObjPtr<mirror::Class> to = to_types.Get(i);
    const Primitive::Type from_type = from->GetPrimitiveType();
    const Primitive::Type to_type = to->GetPrimitiveType();
    if (from == to) {
      // Easy case - the types are identical. Nothing left to do except to pass
      // the arguments along verbatim.
      if (Primitive::Is64BitType(from_type)) {
        setter->SetLong(getter->GetLong());
      } else if (from_type == Primitive::kPrimNot) {
        setter->SetReference(getter->GetReference());
      } else {
        setter->Set(getter->Get());
      }
    } else {
      JValue value;
      if (Primitive::Is64BitType(from_type)) {
        value.SetJ(getter->GetLong());
      } else if (from_type == Primitive::kPrimNot) {
        value.SetL(getter->GetReference());
      } else {
        value.SetI(getter->Get());
      }
      // Caveat emptor - ObjPtr's not guaranteed valid after this call.
      if (!ConvertArgumentValue(throw_wmt, from, to, &value)) {
        DCHECK(Thread::Current()->IsExceptionPending());
        return false;
      }
      if (Primitive::Is64BitType(to_type)) {
        setter->SetLong(value.GetJ());
      } else if (to_type == Primitive::kPrimNot) {
        setter->SetReference(value.GetL());
      } else {
        setter->Set(value.GetI());
      }
    }
  }
  return true;
}

template <typename G, typename S>
bool CopyArguments(Thread* self,
                   Handle<mirror::MethodType> method_type,
                   G* getter,
                   S* setter) REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<2> hs(self);
  Handle<mirror::ObjectArray<mirror::Class>> ptypes(hs.NewHandle(method_type->GetPTypes()));
  int32_t ptypes_length = ptypes->GetLength();

  for (int32_t i = 0; i < ptypes_length; ++i) {
    ObjPtr<mirror::Class> ptype(ptypes->GetWithoutChecks(i));
    Primitive::Type primitive = ptype->GetPrimitiveType();
    if (Primitive::Is64BitType(primitive)) {
      setter->SetLong(getter->GetLong());
    } else if (primitive == Primitive::kPrimNot) {
      setter->SetReference(getter->GetReference());
    } else {
      setter->Set(getter->Get());
    }
  }
  return true;
}

}  // namespace art

#endif  // ART_RUNTIME_METHOD_HANDLES_INL_H_
