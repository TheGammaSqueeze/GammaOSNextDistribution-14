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

#include "method_handles-inl.h"

#include "android-base/macros.h"
#include "android-base/stringprintf.h"
#include "class_root-inl.h"
#include "common_dex_operations.h"
#include "common_throws.h"
#include "interpreter/shadow_frame-inl.h"
#include "interpreter/shadow_frame.h"
#include "jvalue-inl.h"
#include "mirror/class-inl.h"
#include "mirror/emulated_stack_frame-inl.h"
#include "mirror/emulated_stack_frame.h"
#include "mirror/method_handle_impl-inl.h"
#include "mirror/method_handle_impl.h"
#include "mirror/method_type-inl.h"
#include "mirror/var_handle.h"
#include "reflection-inl.h"
#include "reflection.h"
#include "thread.h"
#include "var_handles.h"
#include "well_known_classes.h"

namespace art HIDDEN {

using android::base::StringPrintf;

namespace {

#define PRIMITIVES_LIST(V) \
  V(Primitive::kPrimBoolean, Boolean, Boolean, Z) \
  V(Primitive::kPrimByte, Byte, Byte, B)          \
  V(Primitive::kPrimChar, Char, Character, C)     \
  V(Primitive::kPrimShort, Short, Short, S)       \
  V(Primitive::kPrimInt, Int, Integer, I)         \
  V(Primitive::kPrimLong, Long, Long, J)          \
  V(Primitive::kPrimFloat, Float, Float, F)       \
  V(Primitive::kPrimDouble, Double, Double, D)

// Assigns |type| to the primitive type associated with |klass|. Returns
// true iff. |klass| was a boxed type (Integer, Long etc.), false otherwise.
bool GetUnboxedPrimitiveType(ObjPtr<mirror::Class> klass, Primitive::Type* type)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  std::string storage;
  const char* descriptor = klass->GetDescriptor(&storage);
  static const char kJavaLangPrefix[] = "Ljava/lang/";
  static const size_t kJavaLangPrefixSize = sizeof(kJavaLangPrefix) - 1;
  if (strncmp(descriptor, kJavaLangPrefix, kJavaLangPrefixSize) != 0) {
    return false;
  }

  descriptor += kJavaLangPrefixSize;
#define LOOKUP_PRIMITIVE(primitive, _, java_name, ___) \
  if (strcmp(descriptor, #java_name ";") == 0) {       \
    *type = primitive;                                 \
    return true;                                       \
  }

  PRIMITIVES_LIST(LOOKUP_PRIMITIVE);
#undef LOOKUP_PRIMITIVE
  return false;
}

ObjPtr<mirror::Class> GetBoxedPrimitiveClass(Primitive::Type type)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  ArtMethod* m = nullptr;
  switch (type) {
#define CASE_PRIMITIVE(primitive, _, java_name, __)              \
    case primitive:                                              \
      m = WellKnownClasses::java_lang_ ## java_name ## _valueOf; \
      break;
    PRIMITIVES_LIST(CASE_PRIMITIVE);
#undef CASE_PRIMITIVE
    case Primitive::Type::kPrimNot:
    case Primitive::Type::kPrimVoid:
      return nullptr;
  }
  return m->GetDeclaringClass();
}

bool GetUnboxedTypeAndValue(ObjPtr<mirror::Object> o, Primitive::Type* type, JValue* value)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  ObjPtr<mirror::Class> klass = o->GetClass();
  ArtField* primitive_field = &klass->GetIFieldsPtr()->At(0);
#define CASE_PRIMITIVE(primitive, abbrev, _, shorthand)         \
  if (klass == GetBoxedPrimitiveClass(primitive)) {             \
    *type = primitive;                                          \
    value->Set ## shorthand(primitive_field->Get ## abbrev(o)); \
    return true;                                                \
  }
  PRIMITIVES_LIST(CASE_PRIMITIVE)
#undef CASE_PRIMITIVE
  return false;
}

inline bool IsReferenceType(Primitive::Type type) {
  return type == Primitive::kPrimNot;
}

inline bool IsPrimitiveType(Primitive::Type type) {
  return !IsReferenceType(type);
}

}  // namespace

bool IsParameterTypeConvertible(ObjPtr<mirror::Class> from, ObjPtr<mirror::Class> to)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // This function returns true if there's any conceivable conversion
  // between |from| and |to|. It's expected this method will be used
  // to determine if a WrongMethodTypeException should be raised. The
  // decision logic follows the documentation for MethodType.asType().
  if (from == to) {
    return true;
  }

  Primitive::Type from_primitive = from->GetPrimitiveType();
  Primitive::Type to_primitive = to->GetPrimitiveType();
  DCHECK(from_primitive != Primitive::Type::kPrimVoid);
  DCHECK(to_primitive != Primitive::Type::kPrimVoid);

  // If |to| and |from| are references.
  if (IsReferenceType(from_primitive) && IsReferenceType(to_primitive)) {
    // Assignability is determined during parameter conversion when
    // invoking the associated method handle.
    return true;
  }

  // If |to| and |from| are primitives and a widening conversion exists.
  if (Primitive::IsWidenable(from_primitive, to_primitive)) {
    return true;
  }

  // If |to| is a reference and |from| is a primitive, then boxing conversion.
  if (IsReferenceType(to_primitive) && IsPrimitiveType(from_primitive)) {
    return to->IsAssignableFrom(GetBoxedPrimitiveClass(from_primitive));
  }

  // If |from| is a reference and |to| is a primitive, then unboxing conversion.
  if (IsPrimitiveType(to_primitive) && IsReferenceType(from_primitive)) {
    if (from->DescriptorEquals("Ljava/lang/Object;")) {
      // Object might be converted into a primitive during unboxing.
      return true;
    }

    if (Primitive::IsNumericType(to_primitive) && from->DescriptorEquals("Ljava/lang/Number;")) {
      // Number might be unboxed into any of the number primitive types.
      return true;
    }

    Primitive::Type unboxed_type;
    if (GetUnboxedPrimitiveType(from, &unboxed_type)) {
      if (unboxed_type == to_primitive) {
        // Straightforward unboxing conversion such as Boolean => boolean.
        return true;
      }

      // Check if widening operations for numeric primitives would work,
      // such as Byte => byte => long.
      return Primitive::IsWidenable(unboxed_type, to_primitive);
    }
  }

  return false;
}

bool IsReturnTypeConvertible(ObjPtr<mirror::Class> from, ObjPtr<mirror::Class> to)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (to->GetPrimitiveType() == Primitive::Type::kPrimVoid) {
    // Result will be ignored.
    return true;
  } else if (from->GetPrimitiveType() == Primitive::Type::kPrimVoid) {
    // Returned value will be 0 / null.
    return true;
  } else {
    // Otherwise apply usual parameter conversion rules.
    return IsParameterTypeConvertible(from, to);
  }
}

bool ConvertJValueCommon(
    const ThrowWrongMethodTypeFunction& throw_wmt,
    ObjPtr<mirror::Class> from,
    ObjPtr<mirror::Class> to,
    /*inout*/ JValue* value) {
  // The reader maybe concerned about the safety of the heap object
  // that may be in |value|. There is only one case where allocation
  // is obviously needed and that's for boxing. However, in the case
  // of boxing |value| contains a non-reference type.

  const Primitive::Type from_type = from->GetPrimitiveType();
  const Primitive::Type to_type = to->GetPrimitiveType();

  // Put incoming value into |src_value| and set return value to 0.
  // Errors and conversions from void require the return value to be 0.
  const JValue src_value(*value);
  value->SetJ(0);

  // Conversion from void set result to zero.
  if (from_type == Primitive::kPrimVoid) {
    return true;
  }

  // This method must be called only when the types don't match.
  DCHECK(from != to);

  if (IsPrimitiveType(from_type) && IsPrimitiveType(to_type)) {
    // The source and target types are both primitives.
    if (UNLIKELY(!ConvertPrimitiveValueNoThrow(from_type, to_type, src_value, value))) {
      throw_wmt();
      return false;
    }
    return true;
  } else if (IsReferenceType(from_type) && IsReferenceType(to_type)) {
    // They're both reference types. If "from" is null, we can pass it
    // through unchanged. If not, we must generate a cast exception if
    // |to| is not assignable from the dynamic type of |ref|.
    //
    // Playing it safe with StackHandleScope here, not expecting any allocation
    // in mirror::Class::IsAssignable().
    StackHandleScope<2> hs(Thread::Current());
    Handle<mirror::Class> h_to(hs.NewHandle(to));
    Handle<mirror::Object> h_obj(hs.NewHandle(src_value.GetL()));
    if (UNLIKELY(!h_obj.IsNull() && !to->IsAssignableFrom(h_obj->GetClass()))) {
      ThrowClassCastException(h_to.Get(), h_obj->GetClass());
      return false;
    }
    value->SetL(h_obj.Get());
    return true;
  } else if (IsReferenceType(to_type)) {
    DCHECK(IsPrimitiveType(from_type));
    // The source type is a primitive and the target type is a reference, so we must box.
    // The target type maybe a super class of the boxed source type, for example,
    // if the source type is int, it's boxed type is java.lang.Integer, and the target
    // type could be java.lang.Number.
    Primitive::Type type;
    if (!GetUnboxedPrimitiveType(to, &type)) {
      ObjPtr<mirror::Class> boxed_from_class = GetBoxedPrimitiveClass(from_type);
      if (LIKELY(boxed_from_class->IsSubClass(to))) {
        type = from_type;
      } else {
        throw_wmt();
        return false;
      }
    }

    if (UNLIKELY(from_type != type)) {
      throw_wmt();
      return false;
    }

    if (UNLIKELY(!ConvertPrimitiveValueNoThrow(from_type, type, src_value, value))) {
      throw_wmt();
      return false;
    }

    // Then perform the actual boxing, and then set the reference.
    ObjPtr<mirror::Object> boxed = BoxPrimitive(type, src_value);
    value->SetL(boxed);
    return true;
  } else {
    // The source type is a reference and the target type is a primitive, so we must unbox.
    DCHECK(IsReferenceType(from_type));
    DCHECK(IsPrimitiveType(to_type));

    ObjPtr<mirror::Object> from_obj(src_value.GetL());
    if (UNLIKELY(from_obj.IsNull())) {
      ThrowNullPointerException(
          StringPrintf("Expected to unbox a '%s' primitive type but was returned null",
                       from->PrettyDescriptor().c_str()).c_str());
      return false;
    }

    ObjPtr<mirror::Class> from_obj_type = from_obj->GetClass();
    Primitive::Type from_primitive_type;
    if (!GetUnboxedPrimitiveType(from_obj_type, &from_primitive_type)) {
      ThrowClassCastException(from, to);
      return false;
    }

    Primitive::Type unboxed_type;
    JValue unboxed_value;
    if (UNLIKELY(!GetUnboxedTypeAndValue(from_obj, &unboxed_type, &unboxed_value))) {
      throw_wmt();
      return false;
    }

    if (UNLIKELY(!ConvertPrimitiveValueNoThrow(unboxed_type, to_type, unboxed_value, value))) {
      if (from->IsAssignableFrom(GetBoxedPrimitiveClass(to_type))) {
        // CallSite may be Number, but the Number object is
        // incompatible, e.g. Number (Integer) for a short.
        ThrowClassCastException(from, to);
      } else {
        // CallSite is incompatible, e.g. Integer for a short.
        throw_wmt();
      }
      return false;
    }

    return true;
  }
}

namespace {

inline void CopyArgumentsFromCallerFrame(const ShadowFrame& caller_frame,
                                         ShadowFrame* callee_frame,
                                         const InstructionOperands* const operands,
                                         const size_t first_dst_reg)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  for (size_t i = 0; i < operands->GetNumberOfOperands(); ++i) {
    size_t dst_reg = first_dst_reg + i;
    size_t src_reg = operands->GetOperand(i);
    // Uint required, so that sign extension does not make this wrong on 64-bit systems
    uint32_t src_value = caller_frame.GetVReg(src_reg);
    ObjPtr<mirror::Object> o = caller_frame.GetVRegReference<kVerifyNone>(src_reg);
    // If both register locations contains the same value, the register probably holds a reference.
    // Note: As an optimization, non-moving collectors leave a stale reference value
    // in the references array even after the original vreg was overwritten to a non-reference.
    if (src_value == reinterpret_cast<uintptr_t>(o.Ptr())) {
      callee_frame->SetVRegReference(dst_reg, o);
    } else {
      callee_frame->SetVReg(dst_reg, src_value);
    }
  }
}

// Calculate the number of ins for a proxy or native method, where we
// can't just look at the code item.
static inline size_t GetInsForProxyOrNativeMethod(ArtMethod* method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(method->IsNative() || method->IsProxyMethod());
  method = method->GetInterfaceMethodIfProxy(kRuntimePointerSize);
  uint32_t shorty_length = 0;
  const char* shorty = method->GetShorty(&shorty_length);

  // Static methods do not include the receiver. The receiver isn't included
  // in the shorty_length though the return value is.
  size_t num_ins = method->IsStatic() ? shorty_length - 1 : shorty_length;
  for (const char* c = shorty + 1; *c != '\0'; ++c) {
    if (*c == 'J' || *c == 'D') {
      ++num_ins;
    }
  }
  return num_ins;
}

static inline bool MethodHandleInvokeTransform(Thread* self,
                                               ShadowFrame& shadow_frame,
                                               Handle<mirror::MethodHandle> method_handle,
                                               Handle<mirror::MethodType> callsite_type,
                                               const InstructionOperands* const operands,
                                               JValue* result)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // This can be fixed to two, because the method we're calling here
  // (MethodHandle.transformInternal) doesn't have any locals and the signature
  // is known :
  //
  // private MethodHandle.transformInternal(EmulatedStackFrame sf);
  //
  // This means we need only two vregs :
  // - One for the method_handle object.
  // - One for the only method argument (an EmulatedStackFrame).
  static constexpr size_t kNumRegsForTransform = 2;

  ArtMethod* called_method = method_handle->GetTargetMethod();
  CodeItemDataAccessor accessor(called_method->DexInstructionData());
  DCHECK_EQ(kNumRegsForTransform, accessor.RegistersSize());
  DCHECK_EQ(kNumRegsForTransform, accessor.InsSize());

  StackHandleScope<2> hs(self);
  Handle<mirror::MethodType> callee_type(hs.NewHandle(method_handle->GetMethodType()));
  Handle<mirror::EmulatedStackFrame> sf(
      hs.NewHandle<mirror::EmulatedStackFrame>(
          mirror::EmulatedStackFrame::CreateFromShadowFrameAndArgs(
              self, callsite_type, callee_type, shadow_frame, operands)));
  if (sf == nullptr) {
    DCHECK(self->IsExceptionPending());
    return false;
  }

  const char* old_cause = self->StartAssertNoThreadSuspension("MethodHandleInvokeTransform");
  ShadowFrameAllocaUniquePtr shadow_frame_unique_ptr =
      CREATE_SHADOW_FRAME(kNumRegsForTransform, called_method, /* dex pc */ 0);
  ShadowFrame* new_shadow_frame = shadow_frame_unique_ptr.get();
  new_shadow_frame->SetVRegReference(0, method_handle.Get());
  new_shadow_frame->SetVRegReference(1, sf.Get());
  self->EndAssertNoThreadSuspension(old_cause);

  PerformCall(self,
              accessor,
              shadow_frame.GetMethod(),
              0 /* first destination register */,
              new_shadow_frame,
              result,
              interpreter::ShouldStayInSwitchInterpreter(called_method));
  if (self->IsExceptionPending()) {
    return false;
  }

  // If the called transformer method we called has returned a value, then we
  // need to copy it back to |result|.
  sf->GetReturnValue(self, result);
  return true;
}

inline static ObjPtr<mirror::Class> GetAndInitializeDeclaringClass(Thread* self, ArtField* field)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Method handle invocations on static fields should ensure class is
  // initialized. This usually happens when an instance is constructed
  // or class members referenced, but this is not guaranteed when
  // looking up method handles.
  ObjPtr<mirror::Class> klass = field->GetDeclaringClass();
  if (UNLIKELY(!klass->IsInitialized())) {
    StackHandleScope<1> hs(self);
    HandleWrapperObjPtr<mirror::Class> h(hs.NewHandleWrapper(&klass));
    if (!Runtime::Current()->GetClassLinker()->EnsureInitialized(self, h, true, true)) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
  }
  return klass;
}

ArtMethod* RefineTargetMethod(Thread* self,
                              ShadowFrame& shadow_frame,
                              const mirror::MethodHandle::Kind& handle_kind,
                              ObjPtr<mirror::MethodType> handle_type,
                              const uint32_t receiver_reg,
                              ArtMethod* target_method) REQUIRES_SHARED(Locks::mutator_lock_) {
  if (handle_kind == mirror::MethodHandle::Kind::kInvokeVirtual ||
      handle_kind == mirror::MethodHandle::Kind::kInvokeInterface) {
    // For virtual and interface methods ensure target_method points to
    // the actual method to invoke.
    ObjPtr<mirror::Object> receiver(shadow_frame.GetVRegReference(receiver_reg));
    ObjPtr<mirror::Class> declaring_class(target_method->GetDeclaringClass());
    if (receiver == nullptr || receiver->GetClass() != declaring_class) {
      // Verify that _vRegC is an object reference and of the type expected by
      // the receiver.
      if (!VerifyObjectIsClass(receiver, declaring_class)) {
        DCHECK(self->IsExceptionPending());
        return nullptr;
      }
      return receiver->GetClass()->FindVirtualMethodForVirtualOrInterface(
          target_method, kRuntimePointerSize);
    }
  } else if (handle_kind == mirror::MethodHandle::Kind::kInvokeDirect) {
    // String constructors are a special case, they are replaced with
    // StringFactory methods.
    if (target_method->IsStringConstructor()) {
      DCHECK(handle_type->GetRType()->IsStringClass());
      return WellKnownClasses::StringInitToStringFactory(target_method);
    }
  } else if (handle_kind == mirror::MethodHandle::Kind::kInvokeSuper) {
    // Note that we're not dynamically dispatching on the type of the receiver
    // here. We use the static type of the "receiver" object that we've
    // recorded in the method handle's type, which will be the same as the
    // special caller that was specified at the point of lookup.
    ObjPtr<mirror::Class> referrer_class = handle_type->GetPTypes()->Get(0);
    ObjPtr<mirror::Class> declaring_class = target_method->GetDeclaringClass();
    if (referrer_class == declaring_class) {
      return target_method;
    }
    if (declaring_class->IsInterface()) {
      if (target_method->IsAbstract()) {
        std::string msg =
            "Method " + target_method->PrettyMethod() + " is abstract interface method!";
        ThrowIllegalAccessException(msg.c_str());
        return nullptr;
      }
    } else {
      ObjPtr<mirror::Class> super_class = referrer_class->GetSuperClass();
      uint16_t vtable_index = target_method->GetMethodIndex();
      DCHECK(super_class != nullptr);
      DCHECK(super_class->HasVTable());
      // Note that super_class is a super of referrer_class and target_method
      // will always be declared by super_class (or one of its super classes).
      DCHECK_LT(vtable_index, super_class->GetVTableLength());
      return super_class->GetVTableEntry(vtable_index, kRuntimePointerSize);
    }
  }
  return target_method;
}

// Helper for getters in invoke-polymorphic.
inline static void MethodHandleFieldGet(Thread* self,
                                        const ShadowFrame& shadow_frame,
                                        ObjPtr<mirror::Object>& obj,
                                        ArtField* field,
                                        Primitive::Type field_type,
                                        JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  switch (field_type) {
    case Primitive::kPrimBoolean:
      DoFieldGetCommon<Primitive::kPrimBoolean>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimByte:
      DoFieldGetCommon<Primitive::kPrimByte>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimChar:
      DoFieldGetCommon<Primitive::kPrimChar>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimShort:
      DoFieldGetCommon<Primitive::kPrimShort>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimInt:
      DoFieldGetCommon<Primitive::kPrimInt>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimLong:
      DoFieldGetCommon<Primitive::kPrimLong>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimFloat:
      DoFieldGetCommon<Primitive::kPrimInt>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimDouble:
      DoFieldGetCommon<Primitive::kPrimLong>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimNot:
      DoFieldGetCommon<Primitive::kPrimNot>(self, shadow_frame, obj, field, result);
      break;
    case Primitive::kPrimVoid:
      LOG(FATAL) << "Unreachable: " << field_type;
      UNREACHABLE();
  }
}

// Helper for setters in invoke-polymorphic.
inline bool MethodHandleFieldPut(Thread* self,
                                 ShadowFrame& shadow_frame,
                                 ObjPtr<mirror::Object>& obj,
                                 ArtField* field,
                                 Primitive::Type field_type,
                                 JValue& value) REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!Runtime::Current()->IsActiveTransaction());
  static const bool kTransaction = false;         // Not in a transaction.
  switch (field_type) {
    case Primitive::kPrimBoolean:
      return
          DoFieldPutCommon<Primitive::kPrimBoolean, kTransaction>(
              self, shadow_frame, obj, field, value);
    case Primitive::kPrimByte:
      return DoFieldPutCommon<Primitive::kPrimByte, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimChar:
      return DoFieldPutCommon<Primitive::kPrimChar, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimShort:
      return DoFieldPutCommon<Primitive::kPrimShort, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimInt:
    case Primitive::kPrimFloat:
      return DoFieldPutCommon<Primitive::kPrimInt, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimLong:
    case Primitive::kPrimDouble:
      return DoFieldPutCommon<Primitive::kPrimLong, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimNot:
      return DoFieldPutCommon<Primitive::kPrimNot, kTransaction>(
          self, shadow_frame, obj, field, value);
    case Primitive::kPrimVoid:
      LOG(FATAL) << "Unreachable: " << field_type;
      UNREACHABLE();
  }
}

static JValue GetValueFromShadowFrame(const ShadowFrame& shadow_frame,
                                      Primitive::Type field_type,
                                      uint32_t vreg) REQUIRES_SHARED(Locks::mutator_lock_) {
  JValue field_value;
  switch (field_type) {
    case Primitive::kPrimBoolean:
      field_value.SetZ(static_cast<uint8_t>(shadow_frame.GetVReg(vreg)));
      break;
    case Primitive::kPrimByte:
      field_value.SetB(static_cast<int8_t>(shadow_frame.GetVReg(vreg)));
      break;
    case Primitive::kPrimChar:
      field_value.SetC(static_cast<uint16_t>(shadow_frame.GetVReg(vreg)));
      break;
    case Primitive::kPrimShort:
      field_value.SetS(static_cast<int16_t>(shadow_frame.GetVReg(vreg)));
      break;
    case Primitive::kPrimInt:
    case Primitive::kPrimFloat:
      field_value.SetI(shadow_frame.GetVReg(vreg));
      break;
    case Primitive::kPrimLong:
    case Primitive::kPrimDouble:
      field_value.SetJ(shadow_frame.GetVRegLong(vreg));
      break;
    case Primitive::kPrimNot:
      field_value.SetL(shadow_frame.GetVRegReference(vreg));
      break;
    case Primitive::kPrimVoid:
      LOG(FATAL) << "Unreachable: " << field_type;
      UNREACHABLE();
  }
  return field_value;
}

bool MethodHandleFieldAccess(Thread* self,
                             ShadowFrame& shadow_frame,
                             Handle<mirror::MethodHandle> method_handle,
                             Handle<mirror::MethodType> callsite_type,
                             const InstructionOperands* const operands,
                             JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1> hs(self);
  const mirror::MethodHandle::Kind handle_kind = method_handle->GetHandleKind();
  ArtField* field = method_handle->GetTargetField();
  Primitive::Type field_type = field->GetTypeAsPrimitiveType();
  switch (handle_kind) {
    case mirror::MethodHandle::kInstanceGet: {
      size_t obj_reg = operands->GetOperand(0);
      ObjPtr<mirror::Object> obj = shadow_frame.GetVRegReference(obj_reg);
      if (obj == nullptr) {
        ThrowNullPointerException("Receiver is null");
        return false;
      }
      MethodHandleFieldGet(self, shadow_frame, obj, field, field_type, result);
      return true;
    }
    case mirror::MethodHandle::kStaticGet: {
      ObjPtr<mirror::Object> obj = GetAndInitializeDeclaringClass(self, field);
      if (obj == nullptr) {
        DCHECK(self->IsExceptionPending());
        return false;
      }
      MethodHandleFieldGet(self, shadow_frame, obj, field, field_type, result);
      return true;
    }
    case mirror::MethodHandle::kInstancePut: {
      size_t obj_reg = operands->GetOperand(0);
      size_t value_reg = operands->GetOperand(1);
      const size_t kPTypeIndex = 1;
      // Use ptypes instead of field type since we may be unboxing a reference for a primitive
      // field. The field type is incorrect for this case.
      JValue value = GetValueFromShadowFrame(
          shadow_frame,
          callsite_type->GetPTypes()->Get(kPTypeIndex)->GetPrimitiveType(),
          value_reg);
      ObjPtr<mirror::Object> obj = shadow_frame.GetVRegReference(obj_reg);
      if (obj == nullptr) {
        ThrowNullPointerException("Receiver is null");
        return false;
      }
      return MethodHandleFieldPut(self, shadow_frame, obj, field, field_type, value);
    }
    case mirror::MethodHandle::kStaticPut: {
      ObjPtr<mirror::Object> obj = GetAndInitializeDeclaringClass(self, field);
      if (obj == nullptr) {
        DCHECK(self->IsExceptionPending());
        return false;
      }
      size_t value_reg = operands->GetOperand(0);
      const size_t kPTypeIndex = 0;
      // Use ptypes instead of field type since we may be unboxing a reference for a primitive
      // field. The field type is incorrect for this case.
      JValue value = GetValueFromShadowFrame(
          shadow_frame,
          callsite_type->GetPTypes()->Get(kPTypeIndex)->GetPrimitiveType(),
          value_reg);
      return MethodHandleFieldPut(self, shadow_frame, obj, field, field_type, value);
    }
    default:
      LOG(FATAL) << "Unreachable: " << handle_kind;
      UNREACHABLE();
  }
}

bool DoVarHandleInvokeTranslation(Thread* self,
                                  ShadowFrame& shadow_frame,
                                  Handle<mirror::MethodHandle> method_handle,
                                  Handle<mirror::MethodType> callsite_type,
                                  const InstructionOperands* const operands,
                                  JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  //
  // Basic checks that apply in all cases.
  //
  StackHandleScope<6> hs(self);
  Handle<mirror::ObjectArray<mirror::Class>>
      callsite_ptypes(hs.NewHandle(callsite_type->GetPTypes()));
  Handle<mirror::ObjectArray<mirror::Class>>
         mh_ptypes(hs.NewHandle(method_handle->GetMethodType()->GetPTypes()));

  // Check that the first parameter is a VarHandle
  if (callsite_ptypes->GetLength() < 1 ||
      !mh_ptypes->Get(0)->IsAssignableFrom(callsite_ptypes->Get(0)) ||
      mh_ptypes->Get(0) != GetClassRoot<mirror::VarHandle>()) {
    ThrowWrongMethodTypeException(method_handle->GetMethodType(), callsite_type.Get());
    return false;
  }

  // Get the receiver
  ObjPtr<mirror::Object> receiver = shadow_frame.GetVRegReference(operands->GetOperand(0));
  if (receiver == nullptr) {
    ThrowNullPointerException("Expected argument 1 to be a non-null VarHandle");
    return false;
  }

  // Cast to VarHandle instance
  Handle<mirror::VarHandle> vh(hs.NewHandle(ObjPtr<mirror::VarHandle>::DownCast(receiver)));
  DCHECK(GetClassRoot<mirror::VarHandle>()->IsAssignableFrom(vh->GetClass()));

  // Determine the accessor kind to dispatch
  ArtMethod* target_method = method_handle->GetTargetMethod();
  int intrinsic_index = target_method->GetIntrinsic();
  mirror::VarHandle::AccessMode access_mode =
      mirror::VarHandle::GetAccessModeByIntrinsic(static_cast<Intrinsics>(intrinsic_index));
  Handle<mirror::MethodType> vh_type =
      hs.NewHandle(vh->GetMethodTypeForAccessMode(self, access_mode));
  Handle<mirror::MethodType> mh_invoke_type = hs.NewHandle(
      mirror::MethodType::CloneWithoutLeadingParameter(self, method_handle->GetMethodType()));
  if (method_handle->GetHandleKind() == mirror::MethodHandle::Kind::kInvokeVarHandleExact) {
    if (!mh_invoke_type->IsExactMatch(vh_type.Get())) {
      ThrowWrongMethodTypeException(vh_type.Get(), mh_invoke_type.Get());
      return false;
    }
  }

  Handle<mirror::MethodType> callsite_type_without_varhandle =
      hs.NewHandle(mirror::MethodType::CloneWithoutLeadingParameter(self, callsite_type.Get()));
  NoReceiverInstructionOperands varhandle_operands(operands);
  return VarHandleInvokeAccessor(self,
                                 shadow_frame,
                                 vh,
                                 callsite_type_without_varhandle,
                                 access_mode,
                                 &varhandle_operands,
                                 result);
}

static bool DoMethodHandleInvokeMethod(Thread* self,
                                       ShadowFrame& shadow_frame,
                                       Handle<mirror::MethodHandle> method_handle,
                                       const InstructionOperands* const operands,
                                       JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtMethod* target_method = method_handle->GetTargetMethod();
  uint32_t receiver_reg = (operands->GetNumberOfOperands() > 0) ? operands->GetOperand(0) : 0u;
  ArtMethod* called_method = RefineTargetMethod(self,
                                                shadow_frame,
                                                method_handle->GetHandleKind(),
                                                method_handle->GetMethodType(),
                                                receiver_reg,
                                                target_method);
  if (called_method == nullptr) {
    DCHECK(self->IsExceptionPending());
    return false;
  }
  // Compute method information.
  CodeItemDataAccessor accessor(called_method->DexInstructionData());
  uint16_t num_regs;
  size_t first_dest_reg;
  if (LIKELY(accessor.HasCodeItem())) {
    num_regs = accessor.RegistersSize();
    first_dest_reg = num_regs - accessor.InsSize();
    // Parameter registers go at the end of the shadow frame.
    DCHECK_NE(first_dest_reg, (size_t)-1);
  } else {
    // No local regs for proxy and native methods.
    DCHECK(called_method->IsNative() || called_method->IsProxyMethod());
    num_regs = GetInsForProxyOrNativeMethod(called_method);
    first_dest_reg = 0;
  }

  const char* old_cause = self->StartAssertNoThreadSuspension("DoMethodHandleInvokeMethod");
  ShadowFrameAllocaUniquePtr shadow_frame_unique_ptr =
      CREATE_SHADOW_FRAME(num_regs, called_method, /* dex pc */ 0);
  ShadowFrame* new_shadow_frame = shadow_frame_unique_ptr.get();
  CopyArgumentsFromCallerFrame(shadow_frame, new_shadow_frame, operands, first_dest_reg);
  self->EndAssertNoThreadSuspension(old_cause);

  PerformCall(self,
              accessor,
              shadow_frame.GetMethod(),
              first_dest_reg,
              new_shadow_frame,
              result,
              interpreter::ShouldStayInSwitchInterpreter(called_method));
  if (self->IsExceptionPending()) {
    return false;
  }
  return true;
}

static bool MethodHandleInvokeExactInternal(Thread* self,
                                            ShadowFrame& shadow_frame,
                                            Handle<mirror::MethodHandle> method_handle,
                                            Handle<mirror::MethodType> callsite_type,
                                            const InstructionOperands* const operands,
                                            JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  if (!callsite_type->IsExactMatch(method_handle->GetMethodType())) {
    ThrowWrongMethodTypeException(method_handle->GetMethodType(), callsite_type.Get());
    return false;
  }

  switch (method_handle->GetHandleKind()) {
    case mirror::MethodHandle::Kind::kInvokeDirect:
    case mirror::MethodHandle::Kind::kInvokeInterface:
    case mirror::MethodHandle::Kind::kInvokeStatic:
    case mirror::MethodHandle::Kind::kInvokeSuper:
    case mirror::MethodHandle::Kind::kInvokeVirtual:
      return DoMethodHandleInvokeMethod(self, shadow_frame, method_handle, operands, result);
    case mirror::MethodHandle::Kind::kInstanceGet:
    case mirror::MethodHandle::Kind::kInstancePut:
    case mirror::MethodHandle::Kind::kStaticGet:
    case mirror::MethodHandle::Kind::kStaticPut:
      return MethodHandleFieldAccess(
          self, shadow_frame, method_handle, callsite_type, operands, result);
    case mirror::MethodHandle::Kind::kInvokeTransform:
      return MethodHandleInvokeTransform(
          self, shadow_frame, method_handle, callsite_type, operands, result);
    case mirror::MethodHandle::Kind::kInvokeVarHandle:
    case mirror::MethodHandle::Kind::kInvokeVarHandleExact:
      return DoVarHandleInvokeTranslation(
          self, shadow_frame, method_handle, callsite_type, operands, result);
  }
}

static bool MethodHandleInvokeInternal(Thread* self,
                                       ShadowFrame& shadow_frame,
                                       Handle<mirror::MethodHandle> method_handle,
                                       Handle<mirror::MethodType> callsite_type,
                                       const InstructionOperands* const operands,
                                       JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<2> hs(self);
  Handle<mirror::MethodType> method_handle_type(hs.NewHandle(method_handle->GetMethodType()));
  // Non-exact invoke behaves as calling mh.asType(newType). In ART, asType() is implemented
  // as a transformer and it is expensive to call so check first if it's really necessary.
  //
  // There are two cases where the asType() transformation can be skipped:
  //
  // 1) the call site and type of the MethodHandle match, ie code is calling invoke()
  //    unnecessarily.
  //
  // 2) when the call site can be trivially converted to the MethodHandle type due to how
  //    values are represented in the ShadowFrame, ie all registers in the shadow frame are
  //    32-bit, there is no byte, short, char, etc. So a call site with arguments of these
  //    kinds can be trivially converted to one with int arguments. Similarly if the reference
  //    types are assignable between the call site and MethodHandle type, then as asType()
  //    transformation isn't really doing any work.
  //
  // The following IsInPlaceConvertible check determines if either of these opportunities to
  // skip asType() are true.
  if (callsite_type->IsInPlaceConvertible(method_handle_type.Get())) {
    return MethodHandleInvokeExact(
        self, shadow_frame, method_handle, method_handle_type, operands, result);
  }

  // Use asType() variant of this MethodHandle to adapt callsite to the target.
  MutableHandle<mirror::MethodHandle> atc(hs.NewHandle(method_handle->GetAsTypeCache()));
  if (atc == nullptr || !callsite_type->IsExactMatch(atc->GetMethodType())) {
    // Cached asType adapter does not exist or is for another call site. Call
    // MethodHandle::asType() to get an appropriate adapter.
    ArtMethod* as_type = WellKnownClasses::java_lang_invoke_MethodHandle_asType;
    ObjPtr<mirror::MethodHandle> atc_method_handle = ObjPtr<mirror::MethodHandle>::DownCast(
        as_type->InvokeVirtual<'L', 'L'>(self, method_handle.Get(), callsite_type.Get()));
    if (atc_method_handle == nullptr) {
      DCHECK(self->IsExceptionPending());
      return false;
    }
    atc.Assign(atc_method_handle);
    DCHECK(!atc.IsNull());
  }

  return MethodHandleInvokeExact(self, shadow_frame, atc, callsite_type, operands, result);
}

}  // namespace

bool MethodHandleInvoke(Thread* self,
                        ShadowFrame& shadow_frame,
                        Handle<mirror::MethodHandle> method_handle,
                        Handle<mirror::MethodType> callsite_type,
                        const InstructionOperands* const operands,
                        JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
    return MethodHandleInvokeInternal(
        self, shadow_frame, method_handle, callsite_type, operands, result);
}

bool MethodHandleInvokeExact(Thread* self,
                             ShadowFrame& shadow_frame,
                             Handle<mirror::MethodHandle> method_handle,
                             Handle<mirror::MethodType> callsite_type,
                             const InstructionOperands* const operands,
                             JValue* result) REQUIRES_SHARED(Locks::mutator_lock_) {
    return MethodHandleInvokeExactInternal(
        self, shadow_frame, method_handle, callsite_type, operands, result);
}

void MethodHandleInvokeExactWithFrame(Thread* self,
                                      Handle<mirror::MethodHandle> method_handle,
                                      Handle<mirror::EmulatedStackFrame> emulated_frame)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1> hs(self);
  Handle<mirror::MethodType> callsite_type = hs.NewHandle(emulated_frame->GetType());

  // Copy arguments from the EmalatedStackFrame to a ShadowFrame.
  const uint16_t num_vregs = callsite_type->NumberOfVRegs();

  const char* old_cause = self->StartAssertNoThreadSuspension("EmulatedStackFrame to ShadowFrame");
  ArtMethod* invoke_exact = WellKnownClasses::java_lang_invoke_MethodHandle_invokeExact;
  ShadowFrameAllocaUniquePtr shadow_frame =
      CREATE_SHADOW_FRAME(num_vregs, invoke_exact, /*dex_pc*/ 0);
  emulated_frame->WriteToShadowFrame(self, callsite_type, 0, shadow_frame.get());
  self->EndAssertNoThreadSuspension(old_cause);

  ManagedStack fragment;
  self->PushManagedStackFragment(&fragment);
  self->PushShadowFrame(shadow_frame.get());

  JValue result;
  RangeInstructionOperands operands(0, num_vregs);
  bool success = MethodHandleInvokeExact(self,
                                         *shadow_frame.get(),
                                         method_handle,
                                         callsite_type,
                                         &operands,
                                         &result);
  DCHECK_NE(success, self->IsExceptionPending());
  if (success) {
    emulated_frame->SetReturnValue(self, result);
  }

  self->PopShadowFrame();
  self->PopManagedStackFragment(fragment);
}

}  // namespace art
