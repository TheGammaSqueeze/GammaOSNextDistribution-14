/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
 
#ifndef TENSORFLOW_CORE_PLATFORM_DEFAULT_MUTEX_H_
#define TENSORFLOW_CORE_PLATFORM_DEFAULT_MUTEX_H_
 
// IWYU pragma: private, include "third_party/tensorflow/core/platform/mutex.h"
// IWYU pragma: friend third_party/tensorflow/core/platform/mutex.h
 
#include <chrono>              // NOLINT
#include <condition_variable>  // NOLINT
#include <mutex>               // NOLINT
 
#include "absl/synchronization/mutex.h"
#include "tensorflow/core/platform/macros.h"
#include "tensorflow/core/platform/mutex.h"
 
namespace tensorflow {
 
inline mutex::mutex() {}
 
inline void mutex::lock() TF_EXCLUSIVE_LOCK_FUNCTION() { mu_.Lock(); }
 
inline bool mutex::try_lock() TF_EXCLUSIVE_TRYLOCK_FUNCTION(true) {
  return mu_.TryLock();
};
 
inline void mutex::unlock() TF_UNLOCK_FUNCTION() { mu_.Unlock(); }
 
inline void mutex::lock_shared() TF_SHARED_LOCK_FUNCTION() { mu_.ReaderLock(); }
 
inline bool mutex::try_lock_shared() TF_SHARED_TRYLOCK_FUNCTION(true) {
  return mu_.ReaderTryLock();
}
 
inline void mutex::unlock_shared() TF_UNLOCK_FUNCTION() { mu_.ReaderUnlock(); }
 
inline void mutex::Await(const Condition& cond) {
  mu_.Await(absl::Condition(&cond, &Condition::Eval));
}
 
inline bool mutex::AwaitWithDeadline(const Condition& cond,
                                     uint64 abs_deadline_ns) {
  return mu_.AwaitWithDeadline(absl::Condition(&cond, &Condition::Eval),
                               absl::FromUnixNanos(abs_deadline_ns));
}
 
inline condition_variable::condition_variable() {}
 
inline void condition_variable::wait(mutex_lock& lock) {
  cv_.Wait(&lock.mutex()->mu_);
}
 
inline void condition_variable::notify_one() { cv_.Signal(); }
 
inline void condition_variable::notify_all() { cv_.SignalAll(); }
 
template <class Rep, class Period>
std::cv_status condition_variable::wait_for(
    mutex_lock& lock, std::chrono::duration<Rep, Period> dur) {
  bool r = cv_.WaitWithTimeout(&lock.mutex()->mu_, ::absl::FromChrono(dur));
  return r ? std::cv_status::timeout : std::cv_status::no_timeout;
}
 
}  // namespace tensorflow
 
#endif  // TENSORFLOW_CORE_PLATFORM_DEFAULT_MUTEX_H_
