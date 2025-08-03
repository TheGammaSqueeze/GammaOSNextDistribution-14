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

#ifndef ART_RUNTIME_METHOD_HANDLES_H_
#define ART_RUNTIME_METHOD_HANDLES_H_

#include <ostream>

#include "base/macros.h"
#include "dex/dex_instruction.h"
#include "handle.h"
#include "jvalue.h"
#include "mirror/class.h"

namespace art HIDDEN {

class ShadowFrame;

namespace mirror {
class EmulatedStackFrame;
class MethodHandle;
class MethodType;
}  // namespace mirror

// Returns true if there is a possible conversion from |from| to |to|
// for a MethodHandle parameter.
bool IsParameterTypeConvertible(ObjPtr<mirror::Class> from,
                                ObjPtr<mirror::Class> to);

// Returns true if there is a possible conversion from |from| to |to|
// for the return type of a MethodHandle.
bool IsReturnTypeConvertible(ObjPtr<mirror::Class> from,
                             ObjPtr<mirror::Class> to);

// Interface for throwing `WrongMethodTypeException` by conversion functions.
class ThrowWrongMethodTypeFunction {
 public:
  virtual void operator()() const REQUIRES_SHARED(Locks::mutator_lock_) = 0;

 protected:
  ~ThrowWrongMethodTypeFunction() {}
};

// Performs a conversion from type `from` to a distinct type `to`.
// The value to be converted is in `*value`. Returns true on success
// and updates `*value` with the converted value, false otherwise.
bool ConvertJValueCommon(const ThrowWrongMethodTypeFunction& throw_wmt,
                         ObjPtr<mirror::Class> from,
                         ObjPtr<mirror::Class> to,
                         /*inout*/ JValue* value)
    REQUIRES_SHARED(Locks::mutator_lock_);

// Converts the value of the argument from type `from` to type `to`.
// `*value` represents the value to be converted. Returns true on success
// and updates `*value`, false otherwise.
ALWAYS_INLINE bool ConvertArgumentValue(const ThrowWrongMethodTypeFunction& throw_wmt,
                                        ObjPtr<mirror::Class> from,
                                        ObjPtr<mirror::Class> to,
                                        /*inout*/ JValue* value)
    REQUIRES_SHARED(Locks::mutator_lock_);

// Converts the return value from return type `from` to the return type `to`.
// `*value` represents the value to be converted. Returns true on success and
// updates `*value`, false otherwise.
ALWAYS_INLINE bool ConvertReturnValue(const ThrowWrongMethodTypeFunction& throw_wmt,
                                      ObjPtr<mirror::Class> from,
                                      ObjPtr<mirror::Class> to,
                                      /*inout*/ JValue* value)
    REQUIRES_SHARED(Locks::mutator_lock_);

// Perform argument conversions between `from_types` (the types of the incoming
// arguments) and `to_types` (the parameter types of the method being invoked).
// These include widening and narrowing conversions as well as boxing and
// unboxing. Returns true on success, false on failure. A pending exception
// will always be set on failure.
//
// The values to be converted are read from an input source (of type G)
// that provides three methods :
//
// class G {
//   // Used to read the next boolean/short/int or float value from the
//   // source.
//   uint32_t Get();
//
//   // Used to the read the next reference value from the source.
//   ObjPtr<mirror::Object> GetReference();
//
//   // Used to read the next double or long value from the source.
//   int64_t GetLong();
// }
//
// After conversion, the values are written to an output sink (of type S)
// that provides three methods :
//
// class S {
//   void Set(uint32_t);
//   void SetReference(ObjPtr<mirror::Object>)
//   void SetLong(int64_t);
// }
//
// The semantics and usage of the Set methods are analagous to the getter
// class.
//
// This method is instantiated in three different scenarions :
// - <S = ShadowFrameSetter, G = ShadowFrameGetter> : copying from shadow
//   frame to shadow frame, used in a regular polymorphic non-exact invoke.
// - <S = EmulatedShadowFrameAccessor, G = ShadowFrameGetter> : entering into
//   a transformer method from a polymorphic invoke.
// - <S = ShadowFrameStter, G = EmulatedStackFrameAccessor> : entering into
//   a regular poly morphic invoke from a transformer method.
//
// TODO(narayan): If we find that the instantiations of this function take
// up too much space, we can make G / S abstract base classes that are
// overridden by concrete classes.
template <typename FromPTypes, typename ToPTypes, typename G, typename S>
bool PerformConversions(const ThrowWrongMethodTypeFunction& throw_wmt,
                        FromPTypes from_types,
                        ToPTypes to_types,
                        G* getter,
                        S* setter) REQUIRES_SHARED(Locks::mutator_lock_);

template <typename G, typename S>
bool CopyArguments(Thread* self,
                   Handle<mirror::MethodType> method_type,
                   Handle<mirror::MethodType> callee_type,
                   G* getter,
                   S* setter) REQUIRES_SHARED(Locks::mutator_lock_);

bool MethodHandleInvoke(Thread* self,
                        ShadowFrame& shadow_frame,
                        Handle<mirror::MethodHandle> method_handle,
                        Handle<mirror::MethodType> callsite_type,
                        const InstructionOperands* const args,
                        JValue* result)
    REQUIRES_SHARED(Locks::mutator_lock_);

bool MethodHandleInvokeExact(Thread* self,
                             ShadowFrame& shadow_frame,
                             Handle<mirror::MethodHandle> method_handle,
                             Handle<mirror::MethodType> callsite_type,
                             const InstructionOperands* const args,
                             JValue* result)
    REQUIRES_SHARED(Locks::mutator_lock_);

void MethodHandleInvokeExactWithFrame(Thread* self,
                                      Handle<mirror::MethodHandle> method_handle,
                                      Handle<mirror::EmulatedStackFrame> stack_frame)
    REQUIRES_SHARED(Locks::mutator_lock_);

}  // namespace art

#endif  // ART_RUNTIME_METHOD_HANDLES_H_
