/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "var_handles.h"

#include "common_throws.h"
#include "dex/dex_instruction.h"
#include "handle.h"
#include "method_handles-inl.h"
#include "mirror/method_type-inl.h"
#include "mirror/var_handle.h"

namespace art HIDDEN {

namespace {

template <typename CallSiteType, typename CalleeType>
class ThrowWrongMethodTypeFunctionImpl final : public ThrowWrongMethodTypeFunction {
 public:
  ThrowWrongMethodTypeFunctionImpl(CallSiteType callsite_type, CalleeType callee_type)
      : callsite_type_(callsite_type),
        callee_type_(callee_type) {}

  ~ThrowWrongMethodTypeFunctionImpl() {}

  void operator()() const override REQUIRES_SHARED(Locks::mutator_lock_) {
    ThrowWrongMethodTypeException(mirror::MethodType::PrettyDescriptor(callee_type_),
                                  mirror::MethodType::PrettyDescriptor(callsite_type_));
  }

 private:
  CallSiteType callsite_type_;
  CalleeType callee_type_;
};

template <typename CallSiteType>
bool VarHandleInvokeAccessorWithConversions(Thread* self,
                                            ShadowFrame& shadow_frame,
                                            Handle<mirror::VarHandle> var_handle,
                                            CallSiteType callsite_type,
                                            mirror::VarHandle::AccessMode access_mode,
                                            const InstructionOperands* operands,
                                            JValue* result)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Use a raw method handle for `accessor_type`, avoid allocating a managed `MethodType`.
  VariableSizedHandleScope accessor_type_hs(self);
  mirror::RawMethodType accessor_type(&accessor_type_hs);
  var_handle->GetMethodTypeForAccessMode(access_mode, accessor_type);
  using HandleScopeType = std::conditional_t<
      std::is_same_v<VariableSizedHandleScope*, CallSiteType>,
      Thread*,  // No handle scope needed, use `Thread*` that can be initialized from `self`.
      StackHandleScope<3>>;
  HandleScopeType hs(self);
  ThrowWrongMethodTypeFunctionImpl throw_wmt(callsite_type, accessor_type);
  auto from_types = mirror::MethodType::NewHandlePTypes(callsite_type, &hs);
  auto to_types = mirror::MethodType::NewHandlePTypes(accessor_type, &hs);
  const size_t num_vregs = mirror::MethodType::NumberOfVRegs(accessor_type);
  ShadowFrameAllocaUniquePtr accessor_frame =
      CREATE_SHADOW_FRAME(num_vregs, shadow_frame.GetMethod(), shadow_frame.GetDexPC());
  ShadowFrameGetter getter(shadow_frame, operands);
  static const uint32_t kFirstDestinationReg = 0;
  ShadowFrameSetter setter(accessor_frame.get(), kFirstDestinationReg);
  if (!PerformConversions(throw_wmt, from_types, to_types, &getter, &setter)) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  RangeInstructionOperands accessor_operands(kFirstDestinationReg,
                                             kFirstDestinationReg + num_vregs);
  if (!var_handle->Access(access_mode, accessor_frame.get(), &accessor_operands, result)) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  if (!ConvertReturnValue(throw_wmt,
                          mirror::MethodType::GetRType(accessor_type),
                          mirror::MethodType::GetRType(callsite_type),
                          result)) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  return true;
}

template <typename CallSiteType>
bool VarHandleInvokeAccessorImpl(Thread* self,
                                 ShadowFrame& shadow_frame,
                                 Handle<mirror::VarHandle> var_handle,
                                 CallSiteType callsite_type,
                                 const mirror::VarHandle::AccessMode access_mode,
                                 const InstructionOperands* const operands,
                                 JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  if (var_handle.IsNull()) {
    ThrowNullPointerExceptionFromDexPC();
    return false;
  }

  if (!var_handle->IsAccessModeSupported(access_mode)) {
    ThrowUnsupportedOperationException();
    return false;
  }

  mirror::VarHandle::MatchKind match_kind =
      var_handle->GetMethodTypeMatchForAccessMode(access_mode, callsite_type);
  if (LIKELY(match_kind == mirror::VarHandle::MatchKind::kExact)) {
    return var_handle->Access(access_mode, &shadow_frame, operands, result);
  } else if (match_kind == mirror::VarHandle::MatchKind::kWithConversions) {
    return VarHandleInvokeAccessorWithConversions(self,
                                                  shadow_frame,
                                                  var_handle,
                                                  callsite_type,
                                                  access_mode,
                                                  operands,
                                                  result);
  } else {
    DCHECK_EQ(match_kind, mirror::VarHandle::MatchKind::kNone);
    ThrowWrongMethodTypeException(var_handle->PrettyDescriptorForAccessMode(access_mode),
                                  mirror::MethodType::PrettyDescriptor(callsite_type));
    return false;
  }
}

}  // namespace

bool VarHandleInvokeAccessor(Thread* self,
                             ShadowFrame& shadow_frame,
                             Handle<mirror::VarHandle> var_handle,
                             Handle<mirror::MethodType> callsite_type,
                             const mirror::VarHandle::AccessMode access_mode,
                             const InstructionOperands* const operands,
                             JValue* result) {
  return VarHandleInvokeAccessorImpl(
      self, shadow_frame, var_handle, callsite_type, access_mode, operands, result);
}

bool VarHandleInvokeAccessor(Thread* self,
                             ShadowFrame& shadow_frame,
                             Handle<mirror::VarHandle> var_handle,
                             mirror::RawMethodType callsite_type,
                             const mirror::VarHandle::AccessMode access_mode,
                             const InstructionOperands* const operands,
                             JValue* result) {
  return VarHandleInvokeAccessorImpl(
      self, shadow_frame, var_handle, callsite_type, access_mode, operands, result);
}

}  // namespace art
