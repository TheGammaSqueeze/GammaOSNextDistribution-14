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

import android.util.Log

/**
 * Executes NamedTasks in batches of a given size. Tasks are queued until executeNextBatch is
 * called.
 * @param executor A SourceTaskExecutor for actually executing the tasks.
 */
class BatchingNamedTaskExecutor(private val mExecutor: NamedTaskExecutor) : NamedTaskExecutor {
  /** Queue of tasks waiting to be dispatched to mExecutor */
  private val mQueuedTasks: ArrayList<NamedTask?> = arrayListOf()
  override fun execute(task: NamedTask?) {
    synchronized(mQueuedTasks) {
      if (DBG) Log.d(TAG, "Queuing $task")
      mQueuedTasks.add(task)
    }
  }

  private fun dispatch(task: NamedTask?) {
    if (DBG) Log.d(TAG, "Dispatching $task")
    mExecutor.execute(task)
  }

  /**
   * Instructs the executor to submit the next batch of results.
   * @param batchSize the maximum number of entries to execute.
   */
  fun executeNextBatch(batchSize: Int) {
    var batch = arrayOfNulls<NamedTask?>(0)
    synchronized(mQueuedTasks) {
      val count: Int = Math.min(mQueuedTasks.size, batchSize)
      val nextTasks: ArrayList<NamedTask?> = mQueuedTasks.subList(0, count) as ArrayList<NamedTask?>
      batch = nextTasks.toArray(batch)
      nextTasks.clear()
      if (DBG) Log.d(TAG, "Dispatching batch of $count")
    }
    for (task in batch) {
      dispatch(task)
    }
  }

  /**
   * Cancel any un-started tasks running in this executor. This instance should not be re-used after
   * calling this method.
   */
  override fun cancelPendingTasks() {
    synchronized(mQueuedTasks) { mQueuedTasks.clear() }
  }

  override fun close() {
    cancelPendingTasks()
    mExecutor.close()
  }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.BatchingNamedTaskExecutor"
  }
}
