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

#include "app_process.h"

#include <pthread.h>

#include <condition_variable>
#include <mutex>

namespace berberis {

namespace {

std::mutex g_guest_loader_mtx;
std::condition_variable g_guest_loader_cv;
bool g_guest_loader_initialized = false;

}  // namespace

void AppProcessPostInit() {
  {
    std::lock_guard<std::mutex> guard(g_guest_loader_mtx);
    g_guest_loader_initialized = true;
  }
  g_guest_loader_cv.notify_all();

  // Expect this call to occur on the main guest thread, after app
  // initialization is done. Force exit since keeping the thread in the
  // background might confuse an app that expects to be single-threaded.
  // Specifically, this scenario happens when guest code is executed in
  // app-zygote before forking children (b/146904103).
  //
  // Other threads may use main thread's stack to access argc/argv/auxvals.
  // We ensure that stack is retained after pthread_exit() by disallowing
  // stack unmap in main guest thread when starting an executable.
  //
  // Note that we cannot just let the thread exit from main(), which would
  // exit the whole process, not just this thread.
  pthread_exit(nullptr);
}

void WaitForAppProcess() {
  std::unique_lock<std::mutex> lock(g_guest_loader_mtx);
  g_guest_loader_cv.wait(lock, [] { return g_guest_loader_initialized; });
}

}  // namespace berberis