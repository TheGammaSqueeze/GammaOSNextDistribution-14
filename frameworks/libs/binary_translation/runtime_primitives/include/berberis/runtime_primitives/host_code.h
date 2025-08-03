/*
 * Copyright (C) 2023 The Android Open Source Project
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
#ifndef BERBERIS_RUNTIME_PRIMITIVES_HOST_CODE_H_
#define BERBERIS_RUNTIME_PRIMITIVES_HOST_CODE_H_

#include <cstdint>

namespace berberis {

// Pointer to host executable machine code.
using HostCode = const void*;

template <typename T>
inline HostCode AsHostCode(T ptr) {
  return reinterpret_cast<HostCode>(ptr);
}

template <typename T>
inline T AsFuncPtr(HostCode ptr) {
  return reinterpret_cast<T>(const_cast<void*>(ptr));
}

// Note: ideally we would like the class to be a local class in the AsFuncPtr function below, but
// C++ doesn't allow that: local classes are not supposed to have template members.
class AsFuncPtrAdaptor;
AsFuncPtrAdaptor AsFuncPtr(HostCode ptr);
class [[nodiscard]] AsFuncPtrAdaptor {
  // Note: we need this helper to describe the operator on the next line. Otherwise the C++ syntax
  // parser becomes very confused. It has to come before the operator to help the parser, though.
  template <typename Result, typename... Args>
  using MakeFunctionType = Result (*)(Args...);

 public:
  template <typename Result, typename... Args>
  operator MakeFunctionType<Result, Args...>() {
    return reinterpret_cast<MakeFunctionType<Result, Args...>>(ptr_);
  }

 private:
  AsFuncPtrAdaptor() = delete;
  AsFuncPtrAdaptor(const AsFuncPtrAdaptor&) = delete;
  AsFuncPtrAdaptor(AsFuncPtrAdaptor&&) = delete;
  AsFuncPtrAdaptor& operator=(const AsFuncPtrAdaptor&) = delete;
  AsFuncPtrAdaptor& operator=(AsFuncPtrAdaptor&&) = delete;
  constexpr explicit AsFuncPtrAdaptor(HostCode ptr) noexcept : ptr_(const_cast<void*>(ptr)) {}
  friend AsFuncPtrAdaptor AsFuncPtr(HostCode);
  void* ptr_;
};

// The result of this function is assumed to be assigned to a non-auto variable type, which will
// involve the conversion operator of AsFuncPtrAdaptor.
inline AsFuncPtrAdaptor AsFuncPtr(HostCode ptr) {
  return AsFuncPtrAdaptor{ptr};
}

struct HostCodePiece {
  HostCode code;
  uint32_t size;
};

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_HOST_CODE_H_
