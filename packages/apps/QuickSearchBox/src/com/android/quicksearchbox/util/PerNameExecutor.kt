/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quicksearchbox.util

import kotlin.collections.HashMap

/**
 * Uses a separate executor for each task name.
 * @param executorFactory Used to run the commands.
 */
class PerNameExecutor(private val mExecutorFactory: Factory<NamedTaskExecutor>) :
  NamedTaskExecutor {
  private var mExecutors: HashMap<String, NamedTaskExecutor>? = null

  @Synchronized
  override fun cancelPendingTasks() {
    if (mExecutors == null) return
    for (executor in mExecutors!!.values) {
      executor.cancelPendingTasks()
    }
  }

  @Synchronized
  override fun close() {
    if (mExecutors == null) return
    for (executor in mExecutors!!.values) {
      executor.close()
    }
  }

  @Synchronized
  override fun execute(task: NamedTask?) {
    if (mExecutors == null) {
      mExecutors = HashMap<String, NamedTaskExecutor>()
    }
    val name: String? = task?.name
    var executor: NamedTaskExecutor? = mExecutors?.get(name)
    if (executor == null) {
      executor = mExecutorFactory.create()
      mExecutors?.put(name!!, executor)
    }
    executor.execute(task)
  }
}
