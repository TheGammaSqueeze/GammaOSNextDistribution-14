/*
 * Copyright (C) 2020 The Android Open Source Project
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

/*
 * Note: This is not a real pthreads implementation. This is file provides
 * symbols to allow a single-threaded system to pretend to be pthreads
 * supporting, but sans the ability to actually spawn new threads for
 * compatibility with codebases which cannot be configured to not use
 * e.g. mutexes.
 *
 * If we add actual threads, we should switch to musl's implementation,
 * not fix this one.
 */

#include <sys/types.h>
#include <time.h>

/*
 * For musl, Trusty's libc, pthread_t is a unsigned long. There is only one
 * thread, so we provide the constant thread ID of USER_ASPACE_BASE - 1
 * Do not select 0 or -1, as both are used for "invalid thread" by some
 * applications.
 *
 * This value was selected to allow attempts to dereference this to be
 * guaranteed to fault.
 */
pthread_t pthread_self(void) {
    return (pthread_t)(USER_ASPACE_BASE - 1);
}

/* Perform a normal equality test to support invalid threads as input */
int pthread_equal(pthread_t t1, pthread_t t2) {
    return (int)(t1 == t2);
}

/*
 * There is only one thread, so treat locks as no-ops.
 * We could be slightly more correct by treating them as bools and spinning
 * forever, but since we have no way of re-entering the program to unlock
 * the lock, this could not be used for any useful purpose.
 */
int pthread_mutex_init(pthread_mutex_t* mutex,
                       const pthread_mutexattr_t* attr) {
    return 0;
}

int pthread_mutex_destroy(pthread_mutex_t* mutex) {
    return 0;
}

int pthread_mutex_lock(pthread_mutex_t* mutex) {
    return 0;
}

int pthread_mutex_trylock(pthread_mutex_t* mutex) {
    return 0;
}

int pthread_mutex_unlock(pthread_mutex_t* mutex) {
    return 0;
}

int pthread_cond_wait(pthread_cond_t* restrict cond,
                      pthread_mutex_t* restrict mutex) {
    return 0;
}

int pthread_cond_timedwait(pthread_cond_t* restrict cond,
                           pthread_mutex_t* restrict mutex,
                           const struct timespec* restrict abstime) {
    return 0;
}

int pthread_cond_broadcast(pthread_cond_t* cond) {
    return 0;
}

int pthread_cond_signal(pthread_cond_t* cond) {
    return 0;
}

int pthread_atfork(void (*prepare)(void),
                   void (*parent)(void),
                   void (*child)(void)) {
    return 0;
}
