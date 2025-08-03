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

#ifndef NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_PTHREAD_TRANSLATION_H_
#define NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_PTHREAD_TRANSLATION_H_

#include <pthread.h>
#include <sched.h>
#include <time.h>  // struct timespec

#include "berberis/base/struct_check.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_code.h"
#include "bionic/pthread_internal.h"  // pthread_internal_t

namespace berberis {

#define GUEST_PTHREAD_ATTR_FLAG_DETACHED 1

// ATTENTION: layouts of pthread_internal_t and bionic_tls are verified by
// bionic. See bionic/tests/struct_layout_test.cpp

#if defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)

CHECK_STRUCT_LAYOUT(pthread_attr_t, 192, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, flags, 0, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, stack_base, 32, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, stack_size, 64, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, guard_size, 96, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, sched_policy, 128, 32);
CHECK_FIELD_LAYOUT(pthread_attr_t, sched_priority, 160, 32);

// pthread_barrier_t and appropriate functions were introduced in NYC, they are
// not available on earlier versions of bionic.
CHECK_STRUCT_LAYOUT(pthread_barrier_t, 256, 32);
CHECK_FIELD_LAYOUT(pthread_barrier_t, __private, 0, 256);

CHECK_STRUCT_LAYOUT(pthread_barrierattr_t, 32, 32);

CHECK_STRUCT_LAYOUT(pthread_cond_t, 32, 32);
CHECK_FIELD_LAYOUT(pthread_cond_t, __private, 0, 32);

CHECK_STRUCT_LAYOUT(pthread_condattr_t, 32, 32);

CHECK_STRUCT_LAYOUT(pthread_key_t, 32, 32);

CHECK_STRUCT_LAYOUT(pthread_mutex_t, 32, 32);
CHECK_FIELD_LAYOUT(pthread_mutex_t, __private, 0, 32);

CHECK_STRUCT_LAYOUT(pthread_mutexattr_t, 32, 32);

CHECK_STRUCT_LAYOUT(pthread_once_t, 32, 32);

CHECK_STRUCT_LAYOUT(pthread_rwlock_t, 320, 32);
CHECK_FIELD_LAYOUT(pthread_rwlock_t, __private, 0, 320);

CHECK_STRUCT_LAYOUT(pthread_rwlockattr_t, 32, 32);

// pthread_spinlock_t and appropriate functions were introduced in NYC, they are
// not available on earlier versions of bionic.
CHECK_STRUCT_LAYOUT(pthread_spinlock_t, 64, 32);
CHECK_FIELD_LAYOUT(pthread_spinlock_t, __private, 0, 64);

#endif  // defined(NATIVE_BRIDGE_GUEST_ARCH_ARM)

// Check that host attribute constants coincide with bionic attribute constants
// from pthread.h.
static_assert(PTHREAD_PROCESS_PRIVATE == 0,
              "PTHREAD_PROCESS_PRIVATE must be 0 because it's 0 on guest");
static_assert(PTHREAD_PROCESS_SHARED == 1,
              "PTHREAD_PROCESS_SHARED must be 1 because it's 1 on guest");
static_assert(PTHREAD_MUTEX_NORMAL == 0,
              "PTHREAD_MUTEX_NORMAL must be 0 because it's 0 on guest");
static_assert(PTHREAD_MUTEX_RECURSIVE == 1,
              "PTHREAD_MUTEX_RECURSIVE must be 1 because it's 1 on guest");
static_assert(PTHREAD_MUTEX_ERRORCHECK == 2,
              "PTHREAD_MUTEX_ERRORCHECK must be 2 because it's 2 on guest");
static_assert(PTHREAD_MUTEX_DEFAULT == 0,
              "PTHREAD_MUTEX_DEFAULT must be 0 because it's 0 on guest");
static_assert(PTHREAD_CREATE_JOINABLE == 0,
              "PTHREAD_CREATE_JOINABLE must be 0 because it's 0 on guest");
static_assert(PTHREAD_CREATE_DETACHED == 1,
              "PTHREAD_CREATE_DETACHED must be 1 because it's 1 on guest");

// Check that host attribute constants coincide with bionic attribute constants
// from sched.h.
static_assert(SCHED_NORMAL == 0,
              "SCHED_NORMAL must be 0 because it's 0 on guest");
static_assert(SCHED_FIFO == 1, "SCHED_FIFO must be 1 because it's 1 on guest");
static_assert(SCHED_RR == 2, "SCHED_RR must be 2 because it's 2 on guest");
static_assert(
    SCHED_OTHER == SCHED_NORMAL,
    "SCHED_OTHER must be SCHED_NORMAL because it's SCHED_NORMAL on guest");

// TODO(b/65052237): Currently we don't expose __register_atfork and
// __unregister_atfork to the guest code.  This means that functions registered
// with __register_atfork by guest are not called when host calls fork().
// Investigate and fix the issue.

// int pthread_create(pthread_t *thread, const pthread_attr_t *attr,
//                    void *(*start_routine)(void*), void *arg);
void DoCustomTrampoline_pthread_create(HostCode callee, ProcessState* state);

// int pthread_join(pthread_t thread, void **retval);
void DoCustomTrampoline_pthread_join(HostCode /* callee */,
                                     ProcessState* state);

// int pthread_getattr_np(pthread_t thread, pthread_attr_t* attr);
void DoCustomTrampoline_pthread_getattr_np(HostCode callee,
                                           ProcessState* state);

// int pthread_key_create(pthread_key_t* key, void (*destructor)(void*));
void DoCustomTrampoline_pthread_key_create(HostCode callee,
                                           ProcessState* state);

// void __pthread_cleanup_push(__pthread_cleanup_t* c,
//                           void (*routine)(void *), void *arg);
void DoCustomTrampoline___pthread_cleanup_push(HostCode callee,
                                               ProcessState* state);

// uintptr_t __get_thread_stack_top();
void DoCustomTrampoline___get_thread_stack_top(HostCode callee,
                                               ProcessState* state);

}  // namespace berberis

#endif  // NATIVE_BRIDGE_SUPPORT_LIBC_PROXY_PTHREAD_TRANSLATION_H_
