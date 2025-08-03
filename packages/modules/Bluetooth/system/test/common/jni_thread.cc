/*
 * Copyright 2022 The Android Open Source Project
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

#include "test/common/jni_thread.h"

#include <base/functional/callback.h>

#include <map>

#include "os/log.h"

std::queue<base::OnceClosure> do_in_jni_thread_task_queue;

void run_one_jni_thread_task() {
  ASSERT_LOG(do_in_jni_thread_task_queue.size(),
             "JNI thread has no closures to execute");
  base::OnceCallback callback = std::move(do_in_jni_thread_task_queue.front());
  do_in_jni_thread_task_queue.pop();
  std::move(callback).Run();
}

void run_all_jni_thread_task() {
  while (do_in_jni_thread_task_queue.size()) run_one_jni_thread_task();
}

void reset_mock_jni_thread_queue() {
  while (do_in_jni_thread_task_queue.size()) do_in_jni_thread_task_queue.pop();
}
