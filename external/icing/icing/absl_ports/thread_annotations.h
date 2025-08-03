// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is a port of absl::thread_annotations.
//
// This header file contains macro definitions for thread safety annotations
// that allow developers to document the locking policies of multi-threaded
// code. The annotations can also help program analysis tools to identify
// potential thread safety issues.
//
// These annotations are implemented using compiler attributes. Using the macros
// defined here instead of raw attributes allow for portability and future
// compatibility.
//
// When referring to mutexes in the arguments of the attributes, you should
// use variable names or more complex expressions (e.g. my_object->mutex_)
// that evaluate to a concrete mutex object whenever possible. If the mutex
// you want to refer to is not in scope, you may use a member pointer
// (e.g. &MyClass::mutex_) to refer to a mutex in some (unknown) object.

#ifndef ICING_ABSL_PORTS_THREAD_ANNOTATIONS_H_
#define ICING_ABSL_PORTS_THREAD_ANNOTATIONS_H_

#if defined(__clang__)
#define ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(x) __attribute__((x))
#else
#define ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(x)  // no-op
#endif

// ICING_GUARDED_BY()
//
// Documents if a shared field or global variable needs to be protected by a
// mutex. ICING_GUARDED_BY() allows the user to specify a particular mutex that
// should be held when accessing the annotated variable.
//
// Although this annotation (and ICING_PT_GUARDED_BY, below) cannot be applied
// to local variables, a local variable and its associated mutex can often be
// combined into a small class or struct, thereby allowing the annotation.
//
// Example:
//
//   class Foo {
//     Mutex mu_;
//     int p1_ ICING_GUARDED_BY(mu_);
//     ...
//   };
#define ICING_GUARDED_BY(x) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(guarded_by(x))

// ICING_PT_GUARDED_BY()
//
// Documents if the memory location pointed to by a pointer should be guarded
// by a mutex when dereferencing the pointer.
//
// Example:
//   class Foo {
//     Mutex mu_;
//     int *p1_ ICING_PT_GUARDED_BY(mu_);
//     ...
//   };
//
// Note that a pointer variable to a shared memory location could itself be a
// shared variable.
//
// Example:
//
//   // `q_`, guarded by `mu1_`, points to a shared memory location that is
//   // guarded by `mu2_`:
//   int *q_ ICING_GUARDED_BY(mu1_) ICING_PT_GUARDED_BY(mu2_);
#define ICING_PT_GUARDED_BY(x) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(pt_guarded_by(x))

// ICING_ACQUIRED_AFTER() / ICING_ACQUIRED_BEFORE()
//
// Documents the acquisition order between locks that can be held
// simultaneously by a thread. For any two locks that need to be annotated
// to establish an acquisition order, only one of them needs the annotation.
// (i.e. You don't have to annotate both locks with both ICING_ACQUIRED_AFTER
// and ICING_ACQUIRED_BEFORE.)
//
// As with ICING_GUARDED_BY, this is only applicable to mutexes that are shared
// fields or global variables.
//
// Example:
//
//   Mutex m1_;
//   Mutex m2_ ICING_ACQUIRED_AFTER(m1_);
#define ICING_ACQUIRED_AFTER(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(acquired_after(__VA_ARGS__))

#define ICING_ACQUIRED_BEFORE(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(acquired_before(__VA_ARGS__))

// ICING_EXCLUSIVE_LOCKS_REQUIRED() / ICING_SHARED_LOCKS_REQUIRED()
//
// Documents a function that expects a mutex to be held prior to entry.
// The mutex is expected to be held both on entry to, and exit from, the
// function.
//
// An exclusive lock allows read-write access to the guarded data member(s), and
// only one thread can acquire a lock exclusively at any one time. A shared lock
// allows read-only access, and any number of threads can acquire a shared lock
// concurrently.
//
// Generally, non-const methods should be annotated with
// ICING_EXCLUSIVE_LOCKS_REQUIRED, while const methods should be annotated with
// ICING_SHARED_LOCKS_REQUIRED.
//
// Example:
//
//   Mutex mu1, mu2;
//   int a ICING_GUARDED_BY(mu1);
//   int b ICING_GUARDED_BY(mu2);
//
//   void foo() ICING_EXCLUSIVE_LOCKS_REQUIRED(mu1, mu2) { ... }
//   void bar() const ICING_SHARED_LOCKS_REQUIRED(mu1, mu2) { ... }
#define ICING_EXCLUSIVE_LOCKS_REQUIRED(...)   \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE( \
      exclusive_locks_required(__VA_ARGS__))

#define ICING_SHARED_LOCKS_REQUIRED(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(shared_locks_required(__VA_ARGS__))

// ICING_LOCKS_EXCLUDED()
//
// Documents the locks acquired in the body of the function. These locks
// cannot be held when calling this function.
#define ICING_LOCKS_EXCLUDED(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(locks_excluded(__VA_ARGS__))

// ICING_LOCK_RETURNED()
//
// Documents a function that returns a mutex without acquiring it.  For example,
// a public getter method that returns a pointer to a private mutex should
// be annotated with ICING_LOCK_RETURNED.
#define ICING_LOCK_RETURNED(x) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(lock_returned(x))

// ICING_LOCKABLE
//
// Documents if a class/type is a lockable type.
#define ICING_LOCKABLE \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(lockable)

// ICING_SCOPED_LOCKABLE
//
// Documents if a class does RAII locking.
// The constructor should use `ICING_LOCK_FUNCTION()` to specify the mutex that
// is acquired, and the destructor should use `ICING_UNLOCK_FUNCTION()` with no
// arguments; the analysis will assume that the destructor unlocks whatever the
// constructor locked.
#define ICING_SCOPED_LOCKABLE \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(scoped_lockable)

// ICING_EXCLUSIVE_LOCK_FUNCTION()
//
// Documents functions that acquire a lock in the body of a function, and do
// not release it.
#define ICING_EXCLUSIVE_LOCK_FUNCTION(...)    \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE( \
      exclusive_lock_function(__VA_ARGS__))

// ICING_SHARED_LOCK_FUNCTION()
//
// Documents functions that acquire a shared (reader) lock in the body of a
// function, and do not release it.
#define ICING_SHARED_LOCK_FUNCTION(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(shared_lock_function(__VA_ARGS__))

// ICING_UNLOCK_FUNCTION()
//
// Documents functions that expect a lock to be held on entry to the function,
// and release it in the body of the function.
#define ICING_UNLOCK_FUNCTION(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(unlock_function(__VA_ARGS__))

// ICING_EXCLUSIVE_TRYLOCK_FUNCTION() / ICING_SHARED_TRYLOCK_FUNCTION()
//
// Documents functions that try to acquire a lock, and return success or failure
// (or a non-boolean value that can be interpreted as a boolean).
// The first argument should be `true` for functions that return `true` on
// success, or `false` for functions that return `false` on success. The second
// argument specifies the mutex that is locked on success. If unspecified, this
// mutex is assumed to be `this`.
#define ICING_EXCLUSIVE_TRYLOCK_FUNCTION(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE( \
      exclusive_trylock_function(__VA_ARGS__))

#define ICING_SHARED_TRYLOCK_FUNCTION(...)    \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE( \
      shared_trylock_function(__VA_ARGS__))

// ICING_ASSERT_EXCLUSIVE_LOCK() / ICING_ASSERT_SHARED_LOCK()
//
// Documents functions that dynamically check to see if a lock is held, and fail
// if it is not held.
#define ICING_ASSERT_EXCLUSIVE_LOCK(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(assert_exclusive_lock(__VA_ARGS__))

#define ICING_ASSERT_SHARED_LOCK(...) \
  ICING_INTERNAL_THREAD_ANNOTATION_ATTRIBUTE(assert_shared_lock(__VA_ARGS__))

#endif  // ICING_ABSL_PORTS_THREAD_ANNOTATIONS_H_
