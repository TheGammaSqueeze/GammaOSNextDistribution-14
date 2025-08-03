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

#ifndef ART_DEX2OAT_DEX_VERIFICATION_RESULTS_H_
#define ART_DEX2OAT_DEX_VERIFICATION_RESULTS_H_

#include <set>

#include "base/macros.h"
#include "base/mutex.h"
#include "dex/class_reference.h"
#include "dex/method_reference.h"

namespace art {

namespace verifier {
class VerifierDepsTest;
}  // namespace verifier

// Used by CompilerCallbacks to track verification information from the Runtime.
class VerificationResults {
 public:
  VerificationResults();
  ~VerificationResults();

  void AddRejectedClass(ClassReference ref) REQUIRES(!rejected_classes_lock_);
  bool IsClassRejected(ClassReference ref) const REQUIRES(!rejected_classes_lock_);

  void AddUncompilableClass(ClassReference ref) REQUIRES(!uncompilable_methods_lock_);
  void AddUncompilableMethod(MethodReference ref) REQUIRES(!uncompilable_methods_lock_);
  bool IsUncompilableMethod(MethodReference ref) const REQUIRES(!uncompilable_methods_lock_);

 private:
  // TODO: External locking during CompilerDriver::PreCompile(), no locking during compilation.
  mutable ReaderWriterMutex uncompilable_methods_lock_ DEFAULT_MUTEX_ACQUIRED_AFTER;
  std::set<MethodReference> uncompilable_methods_ GUARDED_BY(uncompilable_methods_lock_);

  // Rejected classes.
  // TODO: External locking during CompilerDriver::PreCompile(), no locking during compilation.
  mutable ReaderWriterMutex rejected_classes_lock_ DEFAULT_MUTEX_ACQUIRED_AFTER;
  std::set<ClassReference> rejected_classes_ GUARDED_BY(rejected_classes_lock_);

  friend class verifier::VerifierDepsTest;
};

}  // namespace art

#endif  // ART_DEX2OAT_DEX_VERIFICATION_RESULTS_H_
