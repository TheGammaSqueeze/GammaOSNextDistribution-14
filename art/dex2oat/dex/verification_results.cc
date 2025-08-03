/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include "verification_results.h"

#include <android-base/logging.h>

#include "base/mutex-inl.h"
#include "base/stl_util.h"
#include "dex/class_accessor-inl.h"
#include "runtime.h"
#include "thread-current-inl.h"
#include "thread.h"

namespace art {

VerificationResults::VerificationResults()
    : uncompilable_methods_lock_("compiler uncompilable methods lock"),
      rejected_classes_lock_("compiler rejected classes lock") {}

// Non-inline version of the destructor, as it does some implicit work not worth
// inlining.
VerificationResults::~VerificationResults() {}

void VerificationResults::AddRejectedClass(ClassReference ref) {
  {
    WriterMutexLock mu(Thread::Current(), rejected_classes_lock_);
    rejected_classes_.insert(ref);
  }
  DCHECK(IsClassRejected(ref));
}

bool VerificationResults::IsClassRejected(ClassReference ref) const {
  ReaderMutexLock mu(Thread::Current(), rejected_classes_lock_);
  return rejected_classes_.find(ref) != rejected_classes_.end();
}

void VerificationResults::AddUncompilableMethod(MethodReference ref) {
  {
    WriterMutexLock mu(Thread::Current(), uncompilable_methods_lock_);
    uncompilable_methods_.insert(ref);
  }
  DCHECK(IsUncompilableMethod(ref));
}

void VerificationResults::AddUncompilableClass(ClassReference ref) {
  const DexFile& dex_file = *ref.dex_file;
  const dex::ClassDef& class_def = dex_file.GetClassDef(ref.ClassDefIdx());
  WriterMutexLock mu(Thread::Current(), uncompilable_methods_lock_);
  ClassAccessor accessor(dex_file, class_def);
  for (const ClassAccessor::Method& method : accessor.GetMethods()) {
    MethodReference method_ref(&dex_file, method.GetIndex());
    uncompilable_methods_.insert(method_ref);
  }
}

bool VerificationResults::IsUncompilableMethod(MethodReference ref) const {
  ReaderMutexLock mu(Thread::Current(), uncompilable_methods_lock_);
  return uncompilable_methods_.find(ref) != uncompilable_methods_.end();
}


}  // namespace art
