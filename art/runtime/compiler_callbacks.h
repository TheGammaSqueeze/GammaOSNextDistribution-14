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

#ifndef ART_RUNTIME_COMPILER_CALLBACKS_H_
#define ART_RUNTIME_COMPILER_CALLBACKS_H_

#include "base/locks.h"
#include "base/macros.h"
#include "class_status.h"
#include "dex/class_reference.h"
#include "dex/method_reference.h"

namespace art HIDDEN {

class CompilerDriver;

namespace mirror {

class Class;

}  // namespace mirror

namespace verifier {

class VerifierDeps;

}  // namespace verifier

class CompilerCallbacks {
 public:
  enum class CallbackMode {  // private
    kCompileBootImage,
    kCompileApp
  };

  virtual ~CompilerCallbacks() { }

  virtual void AddUncompilableMethod(MethodReference ref) = 0;
  virtual void AddUncompilableClass(ClassReference ref) = 0;
  virtual void ClassRejected(ClassReference ref) = 0;

  virtual verifier::VerifierDeps* GetVerifierDeps() const = 0;
  virtual void SetVerifierDeps([[maybe_unused]] verifier::VerifierDeps* deps) {}

  // Return the class status of a previous stage of the compilation. This can be used, for example,
  // when class unloading is enabled during multidex compilation.
  virtual ClassStatus GetPreviousClassState([[maybe_unused]] ClassReference ref) {
    return ClassStatus::kNotReady;
  }

  virtual void SetDoesClassUnloading([[maybe_unused]] bool does_class_unloading,
                                     [[maybe_unused]] CompilerDriver* compiler_driver) {}

  bool IsBootImage() {
    return mode_ == CallbackMode::kCompileBootImage;
  }

  virtual void UpdateClassState([[maybe_unused]] ClassReference ref,
                                [[maybe_unused]] ClassStatus state) {}

  virtual bool CanUseOatStatusForVerification([[maybe_unused]] mirror::Class* klass)
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return false;
  }

 protected:
  explicit CompilerCallbacks(CallbackMode mode) : mode_(mode) { }

 private:
  // Whether the compiler is creating a boot image.
  const CallbackMode mode_;
};

}  // namespace art

#endif  // ART_RUNTIME_COMPILER_CALLBACKS_H_
