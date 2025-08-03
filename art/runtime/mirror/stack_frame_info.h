/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_RUNTIME_MIRROR_STACK_FRAME_INFO_H_
#define ART_RUNTIME_MIRROR_STACK_FRAME_INFO_H_

#include "base/macros.h"
#include "method_type.h"
#include "object.h"
#include "stack_trace_element.h"

namespace art HIDDEN {

template<class T> class Handle;
struct StackFrameInfoOffsets;

namespace mirror {

// C++ mirror of java.lang.StackFrameInfo
class MANAGED StackFrameInfo final : public Object {
 public:
  MIRROR_CLASS("Ljava/lang/StackFrameInfo;");

  void AssignFields(Handle<Class> declaring_class,
                    Handle<MethodType> method_type,
                    Handle<String> method_name,
                    Handle<String> file_name,
                    int32_t line_number,
                    int32_t dex_pc)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  // Field order required by test "ValidateFieldOrderOfJavaCppUnionClasses".
  HeapReference<Class> declaring_class_;
  HeapReference<String> file_name_;
  HeapReference<String> method_name_;
  HeapReference<MethodType> method_type_;
  HeapReference<StackTraceElement> ste_;
  int32_t bci_;
  int32_t line_number_;
  bool retain_class_ref_;

  template<bool kTransactionActive>
  void SetFields(ObjPtr<Class> declaring_class,
                 ObjPtr<MethodType> method_type,
                 ObjPtr<String> method_name,
                 ObjPtr<String> file_name,
                 int32_t line_number,
                 int32_t bci)
      REQUIRES_SHARED(Locks::mutator_lock_);

  friend struct art::StackFrameInfoOffsets;  // for verifying offset information
  DISALLOW_IMPLICIT_CONSTRUCTORS(StackFrameInfo);
};

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_STACK_FRAME_INFO_H_
