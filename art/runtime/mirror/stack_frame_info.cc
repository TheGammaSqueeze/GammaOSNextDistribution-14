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

#include "stack_frame_info.h"

#include "class-alloc-inl.h"
#include "class.h"
#include "class_root-inl.h"
#include "gc/accounting/card_table-inl.h"
#include "handle_scope-inl.h"
#include "object-inl.h"
#include "string.h"

namespace art HIDDEN {
namespace mirror {

void StackFrameInfo::AssignFields(Handle<Class> declaring_class,
                                  Handle<MethodType> method_type,
                                  Handle<String> method_name,
                                  Handle<String> file_name,
                                  int32_t line_number,
                                  int32_t dex_pc) {
  if (Runtime::Current()->IsActiveTransaction()) {
    SetFields<true>(declaring_class.Get(), method_type.Get(), method_name.Get(),
                    file_name.Get(), line_number, dex_pc);
  } else {
    SetFields<false>(declaring_class.Get(), method_type.Get(), method_name.Get(),
                     file_name.Get(), line_number, dex_pc);
  }
}

template<bool kTransactionActive>
void StackFrameInfo::SetFields(ObjPtr<Class> declaring_class,
                               ObjPtr<MethodType> method_type,
                               ObjPtr<String> method_name,
                               ObjPtr<String> file_name,
                               int32_t line_number,
                               int32_t bci) {
  SetFieldObject<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, declaring_class_),
                                     declaring_class);
  SetFieldObject<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, method_type_),
                                     method_type);
  SetFieldObject<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, method_name_),
                                     method_name);
  SetFieldObject<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, file_name_),
                                     file_name);
  SetField32<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, line_number_),
                                 line_number);
  SetField32<kTransactionActive>(OFFSET_OF_OBJECT_MEMBER(StackFrameInfo, bci_),
                                 bci);
}

}  // namespace mirror
}  // namespace art
